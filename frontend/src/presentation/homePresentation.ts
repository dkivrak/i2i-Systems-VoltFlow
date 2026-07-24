import type { HomeStatus } from '../types';
import { getTelemetryFreshness } from './appliancePresentation';

export interface HomeAttentionSummary {
  needsAttention: boolean;
  hasConnectivityRisk: boolean;
  hasBudgetRisk: boolean;
  hasApplianceRisk: boolean;
}

export function summarizeHomeAttention(
  home: HomeStatus,
  now = Date.now(),
): HomeAttentionSummary {
  const hasConnectivityRisk =
    getTelemetryFreshness(home.lastUpdatedAt, { now }) !== 'live';
  const hasBudgetRisk =
    home.budgetUsagePercent >= 80 || home.tariffState === 'PENALTY';
  const hasApplianceRisk =
    home.anomalyCount > 0 ||
    home.appliances.some(
      (appliance) =>
        appliance.healthStatus === 'ANOMALOUS' ||
        appliance.consecutiveBreachCount >= 3 ||
        (appliance.safePowerLimitWatts > 0 &&
          appliance.currentPowerWatts > appliance.safePowerLimitWatts),
    );

  return {
    needsAttention:
      hasConnectivityRisk || hasBudgetRisk || hasApplianceRisk,
    hasConnectivityRisk,
    hasBudgetRisk,
    hasApplianceRisk,
  };
}
