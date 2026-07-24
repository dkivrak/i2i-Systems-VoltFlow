import type { CharacterState } from '../characters';
import type { ApplianceStatus, ApplianceType } from '../types';
import {
  applianceTypeLabels,
  formatPower,
  operatingStateLabels,
} from '../utils/format';

export const STALE_TELEMETRY_AFTER_MS = 15_000;

export type TelemetryFreshness = 'live' | 'stale' | 'missing' | 'disconnected';
export type ApplianceTone = 'normal' | 'warning' | 'critical' | 'offline';

export interface AppliancePresentation {
  appliance: ApplianceStatus;
  characterState: CharacterState;
  freshness: TelemetryFreshness;
  freshnessLabel: string;
  statusLabel: string;
  tone: ApplianceTone;
  accessibleLabel: string;
  thresholdExceeded: boolean;
  consecutiveViolation: boolean;
  anomalous: boolean;
  overLimitWatts: number;
  warningTitle?: string;
  warningDescription?: string;
  recommendedAction?: string;
}

interface PresentationOptions {
  now?: number;
  connectionError?: boolean;
}

const applianceActions: Record<ApplianceType, string> = {
  REFRIGERATOR:
    'Kapı contasını, hava dolaşımını ve termostat ayarını kontrol edin; sorun sürerse teknik servis desteği alın.',
  KETTLE:
    'Cihazı kapatın, rezistans çevresindeki kireci ve elektrik bağlantısını kontrol edin.',
  OVEN:
    'Fırını kapatın; aynı hatta çalışan yüksek güçlü cihazları ve ısıtma elemanlarını kontrol edin.',
  TELEVISION:
    'Bağlı çevre birimlerini çıkarın, güç tasarrufu ayarlarını kontrol edin ve cihazı yeniden başlatın.',
  WASHING_MACHINE:
    'Programı duraklatın; tambur yükünü, su girişini ve motorun serbest hareketini kontrol edin.',
  AIR_CONDITIONER:
    'Filtreleri ve hava akışını kontrol edin; kompresör yükü yüksek kalırsa cihazı kapatıp servis çağırın.',
  MICROWAVE:
    'Cihazı kapatın, içinde metal cisim bulunmadığını doğrulayın ve güvenli elektrik bağlantısını kontrol edin.',
  LAMP:
    'Armatürü kapatın; ampul gücünü, sürücüyü ve bağlantıları güvenli biçimde kontrol edin.',
  COMPUTER:
    'Yüksek kaynak kullanan uygulamaları kapatın, soğutmayı ve güç kaynağını kontrol edin.',
};

function timestampAge(value: string | undefined, now: number): number | undefined {
  if (!value) return undefined;
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return undefined;
  return Math.max(0, now - timestamp);
}

export function getTelemetryFreshness(
  lastUpdatedAt: string | undefined,
  options: PresentationOptions = {},
): TelemetryFreshness {
  const now = options.now ?? Date.now();
  const age = timestampAge(lastUpdatedAt, now);
  if (options.connectionError && (age === undefined || age > STALE_TELEMETRY_AFTER_MS)) {
    return 'disconnected';
  }
  if (age === undefined) return 'missing';
  if (age > STALE_TELEMETRY_AFTER_MS) return 'stale';
  return 'live';
}

function freshnessCopy(freshness: TelemetryFreshness): string {
  if (freshness === 'disconnected') return 'Bağlantı kesildi';
  if (freshness === 'stale') return 'Telemetri gecikmiş';
  if (freshness === 'missing') return 'Telemetri bekleniyor';
  return 'Canlı telemetri';
}

function visualState(
  appliance: ApplianceStatus,
  freshness: TelemetryFreshness,
  thresholdExceeded: boolean,
  consecutiveViolation: boolean,
): CharacterState {
  if (freshness === 'disconnected' || freshness === 'stale') {
    return 'disconnected' as CharacterState;
  }
  if (freshness === 'missing') return 'sleeping' as CharacterState;
  if (appliance.healthStatus === 'ANOMALOUS') return 'anomalous' as CharacterState;
  if (
    thresholdExceeded ||
    consecutiveViolation ||
    appliance.operatingState === 'HIGH_LOAD'
  ) {
    return 'warning' as CharacterState;
  }
  if (appliance.operatingState === 'ON') return 'active' as CharacterState;
  if (appliance.operatingState === 'STANDBY') return 'idle' as CharacterState;
  return 'sleeping' as CharacterState;
}

export function presentAppliance(
  appliance: ApplianceStatus,
  options: PresentationOptions = {},
): AppliancePresentation {
  const freshness = getTelemetryFreshness(appliance.lastUpdatedAt, options);
  const thresholdExceeded =
    appliance.safePowerLimitWatts > 0 &&
    appliance.currentPowerWatts > appliance.safePowerLimitWatts;
  const consecutiveViolation = appliance.consecutiveBreachCount >= 3;
  const anomalous = appliance.healthStatus === 'ANOMALOUS';
  const overLimitWatts = thresholdExceeded
    ? appliance.currentPowerWatts - appliance.safePowerLimitWatts
    : 0;

  let statusLabel = operatingStateLabels[appliance.operatingState];
  let tone: ApplianceTone = 'normal';
  let warningTitle: string | undefined;
  let warningDescription: string | undefined;
  let recommendedAction: string | undefined;

  if (freshness === 'disconnected') {
    statusLabel = 'Bağlantı kesildi';
    tone = 'offline';
    warningTitle = 'Cihazdan güncel veri alınamıyor';
    warningDescription =
      'Son ölçüm artık canlı kabul edilmiyor. Gösterilen değerler en son güvenilir telemetri kaydına aittir.';
    recommendedAction =
      'Cihazın enerjisini, ağ bağlantısını ve evdeki telemetri geçidini kontrol edin.';
  } else if (freshness === 'stale') {
    statusLabel = 'Telemetri gecikmiş';
    tone = 'offline';
    warningTitle = 'Telemetri güncelliğini kaybetti';
    warningDescription =
      'Bu cihazın son ölçümü beklenen canlı güncelleme aralığının dışında kaldı.';
    recommendedAction =
      'Cihaz bağlantısını kontrol edin; sorun sürerse telemetri simülatörü veya ağ geçidini yeniden başlatın.';
  } else if (freshness === 'missing') {
    statusLabel = 'Telemetri bekleniyor';
    tone = 'offline';
    warningTitle = 'Henüz telemetri alınmadı';
    warningDescription =
      'Cihaz kayıtlı, ancak ilk canlı ölçüm henüz ulaşmadı. Değerler gelene kadar tüketim durumu doğrulanamaz.';
    recommendedAction =
      'Cihazın açık ve telemetri ağına bağlı olduğunu doğrulayın; ilk ölçüm için kısa bir süre bekleyin.';
  }

  if (anomalous || consecutiveViolation || thresholdExceeded) {
    tone = anomalous || consecutiveViolation ? 'critical' : 'warning';
    warningTitle = anomalous
      ? 'Olağan dışı tüketim algılandı'
      : consecutiveViolation
        ? 'Ardışık güç ihlali algılandı'
        : 'Güvenli güç sınırı aşıldı';
    warningDescription = thresholdExceeded
      ? `${formatPower(appliance.currentPowerWatts)} ölçümü, ${formatPower(
          appliance.safePowerLimitWatts,
        )} güvenli sınırını ${formatPower(overLimitWatts)} aşıyor.${
          appliance.consecutiveBreachCount > 0
            ? ` İhlal ${appliance.consecutiveBreachCount} ardışık değerlendirme döngüsünde görüldü.`
            : ''
        }`
      : appliance.consecutiveBreachCount > 0
        ? `${appliance.consecutiveBreachCount} ardışık değerlendirme döngüsü güvenli çalışma düzeninin dışında kaldı.`
        : `Cihaz sağlık durumu olağan dışı olarak işaretlendi. Son ölçüm ${formatPower(
            appliance.currentPowerWatts,
          )}, tanımlı güvenli sınır ${formatPower(
            appliance.safePowerLimitWatts,
          )}.`;
    recommendedAction = applianceActions[appliance.type];
    statusLabel = anomalous
      ? 'Anomali algılandı'
      : consecutiveViolation
        ? 'Ardışık ihlal'
        : 'Sınır aşıldı';
  } else if (appliance.operatingState === 'HIGH_LOAD') {
    tone = 'warning';
    warningTitle = 'Cihaz yüksek yükte çalışıyor';
    warningDescription = `${formatPower(
      appliance.currentPowerWatts,
    )} anlık güç ölçülüyor. Değer güvenli sınır içinde olsa da yüksek yük durumu yakından izlenmelidir.`;
    recommendedAction =
      'Yüksek tüketimin beklenen çalışma programıyla uyumlu olduğunu doğrulayın.';
    statusLabel = 'Yüksek tüketim';
  }

  const freshnessLabel = freshnessCopy(freshness);
  const accessibleLabel = [
    appliance.name,
    applianceTypeLabels[appliance.type],
    statusLabel,
    formatPower(appliance.currentPowerWatts),
    freshnessLabel,
    anomalous ? 'anomali uyarısı var' : '',
  ]
    .filter(Boolean)
    .join(', ');

  return {
    appliance,
    characterState: visualState(
      appliance,
      freshness,
      thresholdExceeded,
      consecutiveViolation,
    ),
    freshness,
    freshnessLabel,
    statusLabel,
    tone,
    accessibleLabel,
    thresholdExceeded,
    consecutiveViolation,
    anomalous,
    overLimitWatts,
    warningTitle,
    warningDescription,
    recommendedAction,
  };
}
