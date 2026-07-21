export const APPLIANCE_TYPES = [
  'REFRIGERATOR',
  'KETTLE',
  'OVEN',
  'TELEVISION',
  'WASHING_MACHINE',
  'AIR_CONDITIONER',
  'MICROWAVE',
  'LAMP',
  'COMPUTER',
] as const;

export type ApplianceType = (typeof APPLIANCE_TYPES)[number];
export type OperatingState = 'OFF' | 'STANDBY' | 'ON' | 'HIGH_LOAD';
export type ApplianceHealthStatus = 'NORMAL' | 'ANOMALOUS';
export type TariffState = 'NORMAL' | 'PENALTY';

export interface ApplianceStatus {
  applianceId: number;
  name: string;
  type: ApplianceType;
  currentPowerWatts: number;
  accumulatedEnergyKwh: number;
  accumulatedCost: number;
  operatingState: OperatingState;
  safePowerLimitWatts: number;
  consecutiveBreachCount: number;
  healthStatus: ApplianceHealthStatus;
  lastUpdatedAt?: string;
}

export interface HomeStatus {
  homeId: number;
  homeName: string;
  currentPowerWatts: number;
  accumulatedEnergyKwh: number;
  currentCost: number;
  monthlyBudget: number;
  budgetUsagePercent: number;
  tariffState: TariffState;
  anomalyCount: number;
  lastUpdatedAt?: string;
  appliances: ApplianceStatus[];
}

export interface HistoryPoint {
  id?: number | string;
  periodStart: string;
  periodEnd?: string;
  energyKwh: number;
  cost: number;
  averagePowerWatts?: number;
  maximumPowerWatts?: number;
  applianceId?: number;
}

export type HomeEventType = 'QUOTA' | 'ANOMALY' | 'TARIFF' | 'UNKNOWN';

export interface HomeEvent {
  id: number | string;
  type: HomeEventType;
  title: string;
  description: string;
  occurredAt: string;
  resolvedAt?: string;
  status?: string;
}

export interface Recommendation {
  id: number | string;
  text: string;
  triggerType?: string;
  createdAt: string;
  fallbackUsed?: boolean;
}

export interface RegistrationApplianceRow {
  rowId: string;
  type: ApplianceType;
  name: string;
  quantity: number;
  safePowerLimitWatts: number;
}

export interface HomeRegistrationRequest {
  name: string;
  contactEmail: string;
  monthlyBudget: number;
  normalTariffPerKwh: number;
  penaltyMultiplier: number;
  appliances: Array<{
    name: string;
    type: ApplianceType;
    safePowerLimitWatts: number;
  }>;
}

export type FieldErrors = Record<string, string>;
