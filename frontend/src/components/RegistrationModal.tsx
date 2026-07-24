import { Plus, Save, Trash2, Info, ShieldAlert } from 'lucide-react';
import { useCallback, useEffect, useRef, useState, type FormEvent } from 'react';
import { ApiError, api, getUserFacingError } from '../api/client';
import { APPLIANCE_TYPES, type ApplianceType, type FieldErrors, type RegistrationApplianceRow } from '../types';
import { applianceTypeLabels } from '../utils/format';
import { Dialog } from './Dialog';
import { InlineSpinner } from './PageStates';
import { useToast } from './ToastProvider';

interface RegistrationModalProps {
  onClose: () => void;
  onCreated: () => void;
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

const CITIES = ['İstanbul', 'Ankara', 'İzmir', 'Bursa', 'Antalya', 'Adana', 'Konya', 'Gaziantep'];

interface RegistrationFormState {
  name: string;
  city: string;
  contactEmail: string;
  monthlyBudget: number;
  normalTariffPerKwh: number;
  penaltyMultiplier: number;
  appliances: RegistrationApplianceRow[];
}

function newAppliance(rowId: string, type: ApplianceType = 'REFRIGERATOR'): RegistrationApplianceRow {
  return {
    rowId,
    type,
    name: '',
    quantity: 1,
    safePowerLimitWatts: safeLimitDefaults[type],
  };
}

function validate(form: RegistrationFormState): FieldErrors {
  const errors: FieldErrors = {};
  const normalizedName = form.name.trim();
  const normalizedEmail = form.contactEmail.trim();
  if (normalizedName.length < 2) errors.name = 'Ev adı en az 2 karakter olmalıdır.';
  else if (normalizedName.length > 160) errors.name = 'Ev adı en fazla 160 karakter olabilir.';
  if (form.city.length > 100) errors.city = 'Şehir adı en fazla 100 karakter olabilir.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(normalizedEmail) || normalizedEmail.length > 320) {
    errors.contactEmail = 'Geçerli bir e-posta adresi girin.';
  }
  if (!(form.monthlyBudget >= 1 && form.monthlyBudget <= 1000000)) {
    errors.monthlyBudget = 'Aylık bütçe 1 ₺ ile 1.000.000 ₺ arasında olmalıdır.';
  }
  const applianceCount = form.appliances.reduce(
    (total, appliance) => total + (Number.isFinite(appliance.quantity) ? appliance.quantity : 0),
    0,
  );
  if (applianceCount > 20) errors.appliances = 'Bir eve en fazla 20 cihaz ekleyebilirsiniz.';
  form.appliances.forEach((appliance, index) => {
    const baseName =
      appliance.name.trim() || applianceTypeLabels[appliance.type];
    const longestExpandedName =
      appliance.quantity > 1
        ? `${baseName} ${appliance.quantity}`
        : baseName;
    if (longestExpandedName.length > 160) {
      errors[`appliances.${index}.name`] = 'Cihaz adı en fazla 160 karakter olabilir.';
    }
    if (!Number.isInteger(appliance.quantity) || appliance.quantity < 1 || appliance.quantity > 20) {
      errors[`appliances.${index}.quantity`] = 'Adet 1–20 arasında olmalıdır.';
    }
    const bounds = safeLimitBounds[appliance.type];
    if (bounds && (appliance.safePowerLimitWatts < bounds.min || appliance.safePowerLimitWatts > bounds.max)) {
      errors[`appliances.${index}.safePowerLimitWatts`] = `Güvenli Watt sınırı ${bounds.desc} arasında olmalıdır.`;
    }
  });
  return errors;
}

export function RegistrationModal({ onClose, onCreated }: RegistrationModalProps) {
  const nextRowId = useRef(2);
  const requestController = useRef<AbortController>();
  const addApplianceButtonRef = useRef<HTMLButtonElement>(null);
  const [form, setForm] = useState<RegistrationFormState>({
    name: '',
    city: 'İstanbul',
    contactEmail: '',
    monthlyBudget: 1500,
    normalTariffPerKwh: 2.5,
    penaltyMultiplier: 1.5,
    appliances: [newAppliance('appliance-1')],
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [submitError, setSubmitError] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const { showToast } = useToast();

  useEffect(() => () => requestController.current?.abort(), []);

  const updateField = <K extends keyof Omit<RegistrationFormState, 'appliances'>>(
    field: K,
    value: RegistrationFormState[K],
  ) => {
    setForm((current) => ({ ...current, [field]: value }));
    setErrors((current) => {
      const next = { ...current };
      delete next[field];
      return next;
    });
  };

  const updateAppliance = <K extends keyof RegistrationApplianceRow>(
    index: number,
    field: K,
    value: RegistrationApplianceRow[K],
  ) => {
    setForm((current) => ({
      ...current,
      appliances: current.appliances.map((appliance, applianceIndex) => {
        if (applianceIndex !== index) return appliance;
        if (field === 'type') {
          const type = value as ApplianceType;
          return { ...appliance, type, safePowerLimitWatts: safeLimitDefaults[type] };
        }
        return { ...appliance, [field]: value };
      }),
    }));
    setErrors((current) => {
      const next = { ...current };
      delete next[`appliances.${index}.${String(field)}`];
      delete next.appliances;
      return next;
    });
  };

  const addAppliance = () => {
    const rowId = `appliance-${nextRowId.current++}`;
    setForm((current) => ({ ...current, appliances: [...current.appliances, newAppliance(rowId)] }));
    setErrors((current) => {
      const next = { ...current };
      delete next.appliances;
      return next;
    });
  };

  const removeAppliance = (index: number) => {
    setForm((current) => ({
      ...current,
      appliances: current.appliances.filter((_, applianceIndex) => applianceIndex !== index),
    }));
    window.requestAnimationFrame(() => addApplianceButtonRef.current?.focus());
  };

  const close = useCallback(() => {
    requestController.current?.abort();
    onClose();
  }, [onClose]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (isSubmitting) return;

    const validationErrors = validate(form);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      setSubmitError('Lütfen işaretli alanları kontrol edin.');
      window.requestAnimationFrame(() => {
        document
          .querySelector<HTMLElement>(
            '#registration-form [aria-invalid="true"]',
          )
          ?.focus();
      });
      return;
    }

    setIsSubmitting(true);
    setSubmitError('');
    requestController.current?.abort();
    requestController.current = new AbortController();

    const appliances = form.appliances.flatMap((appliance) => {
      const baseName = appliance.name.trim() || applianceTypeLabels[appliance.type];
      return Array.from({ length: appliance.quantity }, (_, index) => ({
        name: appliance.quantity > 1 ? `${baseName} ${index + 1}` : baseName,
        type: appliance.type,
        safePowerLimitWatts: appliance.safePowerLimitWatts,
      }));
    });

    try {
      await api.registerHome(
        {
          name: form.name.trim(),
          city: form.city,
          contactEmail: form.contactEmail.trim(),
          monthlyBudget: form.monthlyBudget,
          normalTariffPerKwh: form.normalTariffPerKwh,
          penaltyMultiplier: form.penaltyMultiplier,
          appliances,
        },
        requestController.current.signal,
      );

      showToast({
        tone: 'success',
        title: 'Ev başarıyla eklendi',
        message: 'Simülatör cihazları canlı takibe aldı.',
      });
      onCreated();
      onClose();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
        setErrors(error.fieldErrors);
        window.requestAnimationFrame(() => {
          document
            .querySelector<HTMLElement>(
              '#registration-form [aria-invalid="true"]',
            )
            ?.focus();
        });
      }
      const message = getUserFacingError(error);
      setSubmitError(message);
    } finally {
      setIsSubmitting(false);
    }
  };

  const errorId = (key: string) =>
    `registration-${key.replace(/[^a-z0-9_-]/gi, '-')}-error`;
  const fieldError = (key: string) =>
    errors[key] ? (
      <span className="field-error" id={errorId(key)}>
        {errors[key]}
      </span>
    ) : null;

  return (
    <Dialog
      title="Yeni ev kaydı"
      eyebrow="Varlık yönetimi"
      description="Ev lokasyonunu, aylık bütçeyi ve canlı izlenecek cihazları tanımlayın."
      onClose={close}
      closeDisabled={isSubmitting}
      footer={
        <>
          <button className="button button--ghost" type="button" onClick={close} disabled={isSubmitting}>
            Vazgeç
          </button>
          <button className="button button--primary" type="submit" form="registration-form" disabled={isSubmitting}>
            {isSubmitting ? <InlineSpinner label="Kaydediliyor..." /> : <><Save aria-hidden="true" size={17} /> Evi kaydet</>}
          </button>
        </>
      }
    >
      <form id="registration-form" className="registration-form" onSubmit={submit} noValidate>
        {submitError && (
          <div className="form-alert" role="alert">
            {submitError}
          </div>
        )}

        <fieldset className="form-section">
          <legend>Ev & Lokasyon Bilgileri</legend>
          <div className="form-grid form-grid--three">
            <label className="field">
              <span>Ev adı</span>
              <input
                value={form.name}
                onChange={(event) => updateField('name', event.target.value)}
                aria-invalid={Boolean(errors.name)}
                aria-describedby={errors.name ? errorId('name') : undefined}
                placeholder="Örn. Kadıköy Evim"
              />
              {fieldError('name')}
            </label>

            <label className="field">
              <span>Şehir</span>
              <select
                value={form.city}
                onChange={(event) => updateField('city', event.target.value)}
                aria-invalid={Boolean(errors.city)}
                aria-describedby={errors.city ? errorId('city') : undefined}
              >
                {CITIES.map((c) => (
                  <option key={c} value={c}>{c}</option>
                ))}
              </select>
              {fieldError('city')}
            </label>

            <label className="field">
              <span>İletişim e-postası</span>
              <input
                type="email"
                value={form.contactEmail}
                onChange={(event) => updateField('contactEmail', event.target.value)}
                aria-invalid={Boolean(errors.contactEmail)}
                aria-describedby={errors.contactEmail ? errorId('contactEmail') : undefined}
                autoComplete="email"
                placeholder="siz@example.com"
              />
              {fieldError('contactEmail')}
            </label>
          </div>

          <div className="form-grid form-grid--two">
            <label className="field">
              <span>Aylık bütçe (₺)</span>
              <input
                type="number"
                min="1"
                max="1000000"
                step="100"
                value={form.monthlyBudget}
                onChange={(event) => updateField('monthlyBudget', Number(event.target.value))}
                aria-invalid={Boolean(errors.monthlyBudget)}
                aria-describedby={errors.monthlyBudget ? errorId('monthlyBudget') : undefined}
              />
              {fieldError('monthlyBudget')}
            </label>

            <div className="tariff-explainer">
              <div className="tariff-explainer__heading">
                <Info aria-hidden="true" size={16} />
                <span>Sistem Aşamalı Ceza Tarifesi</span>
              </div>
              <p>
                Normal Tarife: <strong>2,50 ₺/kWh</strong> | Çarpan: <strong>1,50x</strong>
              </p>
              <div className="tariff-explainer__tiers">
                • Bütçe ≤ %100: 2,50 ₺/kWh<br />
                • %100 - %150: 3,75 ₺/kWh<br />
                • &gt; %150: 5,625 ₺/kWh
              </div>
            </div>
          </div>
        </fieldset>

        <fieldset
          className="form-section appliance-form-section"
          aria-describedby={errors.appliances ? errorId('appliances') : undefined}
        >
          <legend>Cihazlar</legend>
          <div className="fieldset-heading">
            <p>
              Cihazları şimdi ekleyebilir veya evi kaydettikten sonra cihaz
              sekmesinden tanımlayabilirsiniz.
            </p>
            <button
              className="button button--small button--secondary"
              type="button"
              onClick={addAppliance}
              ref={addApplianceButtonRef}
            >
              <Plus aria-hidden="true" size={15} /> Cihaz ekle
            </button>
          </div>
          {errors.appliances && (
            <span
              className="field-error field-error--block"
              id={errorId('appliances')}
            >
              {errors.appliances}
            </span>
          )}

          <div className="appliance-form-list">
            {!form.appliances.length && (
              <p className="appliance-form-list__empty" role="status">
                Bu ev cihazsız kaydedilecek. İlk telemetri, daha sonra bir cihaz
                eklediğinizde başlayacak.
              </p>
            )}
            {form.appliances.map((appliance, index) => {
              const bounds = safeLimitBounds[appliance.type];
              return (
                <div className="appliance-form-row" key={appliance.rowId}>
                  <span className="appliance-form-row__number">{index + 1}</span>

                  <label className="field">
                    <span>Tür</span>
                    <select
                      value={appliance.type}
                      aria-label={`${index + 1}. cihaz türü`}
                      onChange={(event) => updateAppliance(index, 'type', event.target.value as ApplianceType)}
                    >
                      {APPLIANCE_TYPES.map((type) => (
                        <option value={type} key={type}>
                          {applianceTypeLabels[type]}
                        </option>
                      ))}
                    </select>
                  </label>

                  <label className="field appliance-name-field">
                    <span>Özel ad <em>isteğe bağlı</em></span>
                    <input
                      value={appliance.name}
                      aria-label={`${index + 1}. cihaz adı`}
                      aria-invalid={Boolean(errors[`appliances.${index}.name`])}
                      aria-describedby={
                        errors[`appliances.${index}.name`]
                          ? errorId(`appliances.${index}.name`)
                          : undefined
                      }
                      onChange={(event) => updateAppliance(index, 'name', event.target.value)}
                      placeholder={applianceTypeLabels[appliance.type]}
                    />
                    {fieldError(`appliances.${index}.name`)}
                  </label>

                  <label className="field field--quantity">
                    <span>Adet</span>
                    <input
                      type="number"
                      min="1"
                      max="20"
                      step="1"
                      value={appliance.quantity}
                      aria-label={`${index + 1}. cihaz adedi`}
                      aria-invalid={Boolean(errors[`appliances.${index}.quantity`])}
                      aria-describedby={
                        errors[`appliances.${index}.quantity`]
                          ? errorId(`appliances.${index}.quantity`)
                          : undefined
                      }
                      onChange={(event) => updateAppliance(index, 'quantity', Number(event.target.value))}
                    />
                    {fieldError(`appliances.${index}.quantity`)}
                  </label>

                  <label className="field field--limit">
                    <span>Güvenli Watt (İzin: {bounds.desc})</span>
                    <input
                      type="number"
                      min={bounds.min}
                      max={bounds.max}
                      step="10"
                      value={appliance.safePowerLimitWatts}
                      aria-label={`${index + 1}. cihaz güvenli güç sınırı`}
                      aria-invalid={Boolean(
                        errors[`appliances.${index}.safePowerLimitWatts`],
                      )}
                      aria-describedby={
                        errors[`appliances.${index}.safePowerLimitWatts`]
                          ? errorId(`appliances.${index}.safePowerLimitWatts`)
                          : undefined
                      }
                      onChange={(event) => updateAppliance(index, 'safePowerLimitWatts', Number(event.target.value))}
                    />
                    {fieldError(`appliances.${index}.safePowerLimitWatts`)}
                  </label>

                  <button
                    className="icon-button appliance-form-row__remove"
                    type="button"
                    onClick={() => removeAppliance(index)}
                    aria-label={`${index + 1}. cihazı kaldır`}
                  >
                    <Trash2 aria-hidden="true" size={17} />
                  </button>
                </div>
              );
            })}
          </div>
        </fieldset>
      </form>
    </Dialog>
  );
}
