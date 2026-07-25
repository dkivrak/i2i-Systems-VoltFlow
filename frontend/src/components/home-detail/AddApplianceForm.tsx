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

const safeLimitDefaults: Record<ApplianceType, number> = {
  REFRIGERATOR: 300,
  KETTLE: 2400,
  OVEN: 3000,
  TELEVISION: 200,
  WASHING_MACHINE: 2200,
  AIR_CONDITIONER: 2200,
  MICROWAVE: 1200,
  LAMP: 60,
  COMPUTER: 400,
};

const safeLimitBounds: Record<ApplianceType, { min: number; max: number; desc: string }> = {
  REFRIGERATOR: { min: 100, max: 500, desc: '100–500 W' },
  KETTLE: { min: 1500, max: 3000, desc: '1500–3000 W' },
  OVEN: { min: 1000, max: 4000, desc: '1000–4000 W' },
  TELEVISION: { min: 50, max: 600, desc: '50–600 W' },
  WASHING_MACHINE: { min: 500, max: 3000, desc: '500–3000 W' },
  AIR_CONDITIONER: { min: 800, max: 4000, desc: '800–4000 W' },
  MICROWAVE: { min: 600, max: 2000, desc: '600–2000 W' },
  LAMP: { min: 5, max: 300, desc: '5–300 W' },
  COMPUTER: { min: 50, max: 1200, desc: '50–1200 W' },
};

function validate(form: FormState): FieldErrors {
  const errors: FieldErrors = {};
  if (form.name.trim().length > 160) {
    errors.name = 'Cihaz adı en fazla 160 karakter olabilir.';
  }
  if (!APPLIANCE_TYPES.includes(form.type)) {
    errors.type = 'Geçerli bir cihaz türü seçin.';
  }
  const bounds = safeLimitBounds[form.type];
  const power = Number(form.safePowerLimitWatts);
  if (!form.safePowerLimitWatts.trim()) {
    errors.safePowerLimitWatts = 'Güvenli Watt sınırı zorunludur.';
  } else if (!Number.isFinite(power)) {
    errors.safePowerLimitWatts = 'Geçerli bir güç değeri girin.';
  } else if (bounds && (power < bounds.min || power > bounds.max)) {
    errors.safePowerLimitWatts = `Güvenli Watt sınırı ${bounds.desc} arasında olmalıdır.`;
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
    safePowerLimitWatts: String(safeLimitDefaults.REFRIGERATOR),
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
    setForm((current) => {
      const next = { ...current, [field]: value };
      if (field === 'type') {
        const newType = value as ApplianceType;
        next.safePowerLimitWatts = String(safeLimitDefaults[newType] || '');
      }
      return next;
    });
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

    const finalName = form.name.trim() || applianceTypeLabels[form.type];

    try {
      const appliance = await api.addAppliance(
        homeId,
        {
          name: finalName,
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
          <span>
            Özel ad <small style={{ opacity: 0.7, fontWeight: 400 }}>(isteğe bağlı)</small>
          </span>
          <input
            ref={nameRef}
            type="text"
            value={form.name}
            onChange={(event) => update('name', event.target.value)}
            disabled={isSubmitting}
            autoComplete="off"
            aria-invalid={Boolean(errors.name)}
            aria-describedby={errors.name ? `${titleId}-name-error` : undefined}
            placeholder={applianceTypeLabels[form.type]}
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
          <span>
            Güvenli Watt (İzin: {safeLimitBounds[form.type]?.desc || '100–500 W'})
          </span>
          <input
            type="text"
            inputMode="decimal"
            aria-label="Güvenli Watt"
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
            placeholder={String(safeLimitDefaults[form.type])}
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
