import type { HomeStatus } from '../types';

export const normalHome: HomeStatus = {
  homeId: 1,
  homeName: 'Kadıköy Evi',
  currentPowerWatts: 1842,
  accumulatedEnergyKwh: 42.75,
  currentCost: 284.5,
  monthlyBudget: 1000,
  budgetUsagePercent: 28.45,
  tariffState: 'NORMAL',
  anomalyCount: 0,
  lastUpdatedAt: new Date().toISOString(),
  appliances: [
    {
      applianceId: 10,
      name: 'Salon Televizyonu',
      type: 'TELEVISION',
      currentPowerWatts: 122,
      accumulatedEnergyKwh: 8.2,
      accumulatedCost: 20.5,
      operatingState: 'ON',
      safePowerLimitWatts: 250,
      consecutiveBreachCount: 0,
      healthStatus: 'NORMAL',
      lastUpdatedAt: new Date().toISOString(),
    },
  ],
};

export const warningHome: HomeStatus = {
  ...normalHome,
  homeId: 2,
  homeName: 'Beşiktaş Evi',
  currentCost: 825,
  budgetUsagePercent: 82.5,
};

export const anomalousHome: HomeStatus = {
  ...normalHome,
  homeId: 3,
  homeName: 'Moda Evi',
  currentCost: 1125,
  budgetUsagePercent: 112.5,
  tariffState: 'PENALTY',
  anomalyCount: 1,
  appliances: [
    {
      ...normalHome.appliances[0],
      applianceId: 30,
      name: 'Çalışma Bilgisayarı',
      type: 'COMPUTER',
      currentPowerWatts: 1100,
      safePowerLimitWatts: 900,
      consecutiveBreachCount: 3,
      healthStatus: 'ANOMALOUS',
      operatingState: 'HIGH_LOAD',
    },
  ],
};
