import { Plus, Save, Trash2 } from 'lucide-react';
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
  REFRIGERATOR: 250,
  KETTLE: 2400,
  OVEN: 3200,
  TELEVISION: 250,
  WASHING_MACHINE: 2400,
  AIR_CONDITIONER: 2800,
  MICROWAVE: 1800,
  LAMP: 100,
  COMPUTER: 900,
};

interface RegistrationFormState {
  name: string;
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
  if (form.name.trim().length < 2) errors.name = 'Ev adı en az 2 karakter olmalıdır.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.contactEmail.trim())) {
    errors.contactEmail = 'Geçerli bir e-posta adresi girin.';
  }
  if (!(form.monthlyBudget > 0)) errors.monthlyBudget = 'Aylık bütçe sıfırdan büyük olmalıdır.';
  if (!(form.normalTariffPerKwh > 0)) errors.normalTariffPerKwh = 'Tarife sıfırdan büyük olmalıdır.';
  if (!(form.penaltyMultiplier > 1)) errors.penaltyMultiplier = 'Ek tarife çarpanı 1’den büyük olmalıdır.';
  if (!form.appliances.length) errors.appliances = 'En az bir cihaz ekleyin.';
  form.appliances.forEach((appliance, index) => {
    if (!Number.isInteger(appliance.quantity) || appliance.quantity < 1 || appliance.quantity > 20) {
      errors[`appliances.${index}.quantity`] = 'Adet 1–20 arasında bir tam sayı olmalıdır.';
    }
    if (!(appliance.safePowerLimitWatts > 0)) {
      errors[`appliances.${index}.safePowerLimitWatts`] = 'Güvenli sınır sıfırdan büyük olmalıdır.';
    }
  });
  return errors;
}

export function RegistrationModal({ onClose, onCreated }: RegistrationModalProps) {
  const nextRowId = useRef(2);
  const requestController = useRef<AbortController>();
  const [form, setForm] = useState<RegistrationFormState>({
    name: '',
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
  };

  const close = useCallback(() => {
    requestController.current?.abort();
    onClose();
  }, [onClose]);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    const validationErrors = validate(form);
    if (Object.keys(validationErrors).length) {
      setErrors(validationErrors);
      setSubmitError('Lütfen işaretli alanları kontrol edin.');
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
        message: 'Simülatör cihazları birkaç saniye içinde canlı takibe alacak.',
      });
      onCreated();
      onClose();
    } catch (error) {
      if (error instanceof DOMException && error.name === 'AbortError') return;
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length) setErrors(error.fieldErrors);
      const message = getUserFacingError(error);
      setSubmitError(message);
      showToast({ tone: 'error', title: 'Ev eklenemedi', message });
    } finally {
      setIsSubmitting(false);
    }
  };

  const fieldError = (key: string) => (errors[key] ? <span className="field-error">{errors[key]}</span> : null);

  return (
    <Dialog
      title="Yeni ev kaydı"
      eyebrow="Varlık yönetimi"
      description="Ev bilgilerini ve canlı izlenecek cihazları tanımlayın."
      onClose={close}
      footer={
        <>
          <button className="button button--ghost" type="button" onClick={close} disabled={isSubmitting}>
            Vazgeç
          </button>
          <button className="button button--primary" type="submit" form="registration-form" disabled={isSubmitting}>
            {isSubmitting ? <InlineSpinner label="Kaydediliyor" /> : <><Save aria-hidden="true" size={17} /> Evi kaydet</>}
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
          <legend>Ev bilgileri</legend>
          <div className="form-grid form-grid--two">
            <label className="field">
              <span>Ev adı</span>
              <input
                value={form.name}
                onChange={(event) => updateField('name', event.target.value)}
                aria-invalid={Boolean(errors.name)}
                placeholder="Örn. Kadıköy Evim"
                autoComplete="organization"
              />
              {fieldError('name')}
            </label>
            <label className="field">
              <span>İletişim e-postası</span>
              <input
                type="email"
                value={form.contactEmail}
                onChange={(event) => updateField('contactEmail', event.target.value)}
                aria-invalid={Boolean(errors.contactEmail)}
                placeholder="siz@example.com"
                autoComplete="email"
              />
              {fieldError('contactEmail')}
            </label>
          </div>
          <div className="form-grid form-grid--three">
            <label className="field">
              <span>Aylık bütçe (₺)</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.monthlyBudget}
                onChange={(event) => updateField('monthlyBudget', Number(event.target.value))}
                aria-invalid={Boolean(errors.monthlyBudget)}
              />
              {fieldError('monthlyBudget')}
            </label>
            <label className="field">
              <span>Normal tarife (₺/kWh)</span>
              <input
                type="number"
                min="0.01"
                step="0.01"
                value={form.normalTariffPerKwh}
                onChange={(event) => updateField('normalTariffPerKwh', Number(event.target.value))}
                aria-invalid={Boolean(errors.normalTariffPerKwh)}
              />
              {fieldError('normalTariffPerKwh')}
            </label>
            <label className="field">
              <span>Ek tarife çarpanı</span>
              <input
                type="number"
                min="1.01"
                step="0.05"
                value={form.penaltyMultiplier}
                onChange={(event) => updateField('penaltyMultiplier', Number(event.target.value))}
                aria-invalid={Boolean(errors.penaltyMultiplier)}
              />
              {fieldError('penaltyMultiplier')}
            </label>
          </div>
        </fieldset>

        <fieldset className="form-section appliance-form-section">
          <div className="fieldset-heading">
            <div>
              <legend>Cihazlar</legend>
              <p>Aynı türü birden fazla satırda veya adet alanıyla ekleyebilirsiniz.</p>
            </div>
            <button className="button button--small button--secondary" type="button" onClick={addAppliance}>
              <Plus aria-hidden="true" size={15} /> Cihaz ekle
            </button>
          </div>
          {errors.appliances && <span className="field-error field-error--block">{errors.appliances}</span>}
          <div className="appliance-form-list">
            {form.appliances.map((appliance, index) => (
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
                    onChange={(event) => updateAppliance(index, 'name', event.target.value)}
                    placeholder={applianceTypeLabels[appliance.type]}
                  />
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
                    onChange={(event) => updateAppliance(index, 'quantity', Number(event.target.value))}
                  />
                  {fieldError(`appliances.${index}.quantity`)}
                </label>
                <label className="field field--limit">
                  <span>Güvenli sınır (W)</span>
                  <input
                    type="number"
                    min="1"
                    step="1"
                    value={appliance.safePowerLimitWatts}
                    aria-label={`${index + 1}. cihaz güvenli güç sınırı`}
                    aria-invalid={Boolean(errors[`appliances.${index}.safePowerLimitWatts`])}
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
            ))}
          </div>
        </fieldset>
      </form>
    </Dialog>
  );
}
