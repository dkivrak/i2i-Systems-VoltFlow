import type { ApplianceType, OperatingState, TariffState } from '../types';

const moneyFormatter = new Intl.NumberFormat('tr-TR', {
  style: 'currency',
  currency: 'TRY',
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const numberFormatter = new Intl.NumberFormat('tr-TR', {
  maximumFractionDigits: 2,
});

const integerFormatter = new Intl.NumberFormat('tr-TR', {
  maximumFractionDigits: 0,
});

export const applianceTypeLabels: Record<ApplianceType, string> = {
  REFRIGERATOR: 'Buzdolabı',
  KETTLE: 'Su ısıtıcısı',
  OVEN: 'Fırın',
  TELEVISION: 'Televizyon',
  WASHING_MACHINE: 'Çamaşır makinesi',
  AIR_CONDITIONER: 'Klima',
  MICROWAVE: 'Mikrodalga',
  LAMP: 'Aydınlatma',
  COMPUTER: 'Bilgisayar',
};

export const operatingStateLabels: Record<OperatingState, string> = {
  OFF: 'Kapalı',
  STANDBY: 'Beklemede',
  ON: 'Çalışıyor',
  HIGH_LOAD: 'Yüksek yük',
};

export const tariffLabels: Record<TariffState, string> = {
  NORMAL: 'Standart tarife',
  PENALTY: 'Ek tarife',
};

export function formatMoney(value: number): string {
  return moneyFormatter.format(Number.isFinite(value) ? value : 0);
}

export function formatEnergy(value: number): string {
  return `${numberFormatter.format(Number.isFinite(value) ? value : 0)} kWh`;
}

export function formatPower(value: number): string {
  const safeValue = Number.isFinite(value) ? value : 0;
  if (Math.abs(safeValue) >= 1000) return `${numberFormatter.format(safeValue / 1000)} kW`;
  return `${integerFormatter.format(safeValue)} W`;
}

export function formatPercent(value: number): string {
  return `%${numberFormatter.format(Number.isFinite(value) ? value : 0)}`;
}

export function formatDateTime(value?: string): string {
  if (!value) return 'Henüz veri yok';
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return 'Henüz veri yok';
  return new Intl.DateTimeFormat('tr-TR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(date);
}

export function formatRelativeTime(value?: string, now = Date.now()): string {
  if (!value) return 'veri bekleniyor';
  const timestamp = Date.parse(value);
  if (!Number.isFinite(timestamp)) return 'veri bekleniyor';
  const seconds = Math.max(0, Math.floor((now - timestamp) / 1000));
  if (seconds < 10) return 'az önce';
  if (seconds < 60) return `${seconds} sn önce`;
  const minutes = Math.floor(seconds / 60);
  if (minutes < 60) return `${minutes} dk önce`;
  const hours = Math.floor(minutes / 60);
  return `${hours} sa önce`;
}

export function formatChartTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return '—';
  return new Intl.DateTimeFormat('tr-TR', {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}
