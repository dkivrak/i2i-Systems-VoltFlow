import { AlertTriangle, PlugZap, Save, X } from 'lucide-react';
import {
  useEffect,
  useId,
  useRef,
  useState,
  type FormEvent,
} from 'react';
import { ApiError, api, getUserFacingError } from '../../api/client';
import type {
  ApplianceStatus,
  ApplianceType,
  FieldErrors,
} from '../../types';
import { APPLIANCE_TYPES } from '../../types';
import { applianceTypeLabels } from '../../utils/format';
import { InlineSpinner } from '../PageStates';

interface AddApplianceFormProps {
  homeId: number;
  onCancel: () => void;
  onSuccess: (appliance: ApplianceStatus) => void;
}

interface FormState {
  name: string;
  type: ApplianceType;
  safePowerLimitWatts: string;
}

function validate(form: FormState): FieldErrors {
  const errors: FieldErrors = {};
  if (!form.name.trim()) {
    errors.name = 'Cihaz adı zorunludur.';
  } else if (form.name.trim().length > 160) {
    errors.name = 'Cihaz adı en fazla 160 karakter olabilir.';
  }
  if (!APPLIANCE_TYPES.includes(form.type)) {
    errors.type = 'Geçerli bir cihaz türü seçin.';
  }
  const power = Number(form.safePowerLimitWatts);
  if (!form.safePowerLimitWatts.trim()) {
    errors.safePowerLimitWatts = 'Güvenli güç sınırı zorunludur.';
  } else if (!Number.isFinite(power)) {
    errors.safePowerLimitWatts = 'Geçerli bir güç değeri girin.';
  } else if (power <= 0) {
    errors.safePowerLimitWatts = 'Güvenli güç sınırı pozitif olmalıdır.';
  } else if (power > 50_000) {
    errors.safePowerLimitWatts =
      'Güvenli güç sınırı en fazla 50.000 W olabilir.';
  }
  return errors;
}

export function AddApplianceForm({
  homeId,
  onCancel,
  onSuccess,
}: AddApplianceFormProps) {
  const [form, setForm] = useState<FormState>({
    name: '',
    type: 'REFRIGERATOR',
    safePowerLimitWatts: '',
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const requestController = useRef<AbortController | null>(null);
  const nameRef = useRef<HTMLInputElement>(null);
  const titleId = useId();

  useEffect(() => {
    nameRef.current?.focus();
    return () => requestController.current?.abort();
  }, []);

  useEffect(() => {
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      event.preventDefault();
      event.stopPropagation();
      if (!submittingRef.current) onCancel();
    };
    document.addEventListener('keydown', closeOnEscape, true);
    return () => document.removeEventListener('keydown', closeOnEscape, true);
  }, [onCancel]);

  const update = <K extends keyof FormState>(
    field: K,
    value: FormState[K],
  ) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      if (!current[field]) return current;
      const next = { ...current };
      delete next[field];
      return next;
    });
    setSubmitError('');
  };

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (submittingRef.current) return;

    const validationErrors = validate(form);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      setSubmitError('Lütfen işaretli alanları kontrol edin.');
      return;
    }

    submittingRef.current = true;
    setIsSubmitting(true);
    setSubmitError('');
    requestController.current?.abort();
    requestController.current = new AbortController();

    try {
      const appliance = await api.addAppliance(
        homeId,
        {
          name: form.name.trim(),
          type: form.type,
          safePowerLimitWatts: Number(form.safePowerLimitWatts),
        },
        requestController.current.signal,
      );
      onSuccess(appliance);
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors);
      }
      setSubmitError(getUserFacingError(error));
    } finally {
      submittingRef.current = false;
      setIsSubmitting(false);
    }
  };

  return (
    <section
      className="add-appliance-panel"
      aria-labelledby={titleId}
      data-testid="add-appliance-form"
    >
      <header className="add-appliance-panel__header">
        <span className="add-appliance-panel__icon" aria-hidden="true">
          <PlugZap size={19} />
        </span>
        <div>
          <p className="eyebrow">Yeni cihaz</p>
          <h4 id={titleId}>Cihazı telemetri ağına ekle</h4>
          <p>İlk ölçüm cihaz kaydından kısa süre sonra otomatik başlayacak.</p>
        </div>
        <button
          className="icon-button icon-button--small"
          type="button"
          onClick={onCancel}
          disabled={isSubmitting}
          aria-label="Cihaz ekleme formunu kapat"
        >
          <X aria-hidden="true" size={17} />
        </button>
      </header>

      <form className="add-appliance-form" onSubmit={submit} noValidate>
        {submitError && (
          <div className="mutation-feedback" role="alert">
            <AlertTriangle aria-hidden="true" size={17} />
            <span>{submitError}</span>
          </div>
        )}

        <label className="field">
          <span>Cihaz adı</span>
          <input
            ref={nameRef}
            type="text"
            value={form.name}
            onChange={(event) => update('name', event.target.value)}
            disabled={isSubmitting}
            autoComplete="off"
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? `${titleId}-name-error` : undefined}
            placeholder="Örn. Salon televizyonu"
          />
          {errors.name && (
            <small className="field-error" id={`${titleId}-name-error`}>
              {errors.name}
            </small>
          )}
        </label>

        <label className="field">
          <span>Cihaz türü</span>
          <select
            value={form.type}
            onChange={(event) =>
              update('type', event.target.value as ApplianceType)
            }
            disabled={isSubmitting}
            aria-invalid={Boolean(errors.type)}
            aria-describedby={errors.type ? `${titleId}-type-error` : undefined}
          >
            {APPLIANCE_TYPES.map((type) => (
              <option value={type} key={type}>
                {applianceTypeLabels[type]}
              </option>
            ))}
          </select>
          {errors.type && (
            <small className="field-error" id={`${titleId}-type-error`}>
              {errors.type}
            </small>
          )}
        </label>

        <label className="field">
          <span>Güvenli maksimum güç (W)</span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Güvenli maksimum güç (W)"
            value={form.safePowerLimitWatts}
            onChange={(event) =>
              update('safePowerLimitWatts', event.target.value)
            }
            disabled={isSubmitting}
            aria-invalid={Boolean(errors.safePowerLimitWatts)}
            aria-describedby={
              errors.safePowerLimitWatts
                ? `${titleId}-power-error`
                : `${titleId}-power-help`
            }
            placeholder="Örn. 450"
          />
          <small id={`${titleId}-power-help`}>
            Cihaz için güvenli kabul edilen üst güç sınırı.
          </small>
          {errors.safePowerLimitWatts && (
            <small className="field-error" id={`${titleId}-power-error`}>
              {errors.safePowerLimitWatts}
            </small>
          )}
        </label>

        <div className="add-appliance-form__actions">
          <button
            className="button button--ghost"
            type="button"
            onClick={onCancel}
            disabled={isSubmitting}
          >
            Vazgeç
          </button>
          <button
            className="button button--primary"
            type="submit"
            disabled={isSubmitting}
          >
            {isSubmitting ? (
              <InlineSpinner label="Cihaz kaydediliyor" />
            ) : (
              <>
                <Save aria-hidden="true" size={16} /> Cihazı kaydet
              </>
            )}
          </button>
        </div>
      </form>
    </section>
  );
}
