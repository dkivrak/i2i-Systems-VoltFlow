package com.voltflow.core.live;

import com.voltflow.core.domain.ApplianceHealthStatus;
import com.voltflow.core.domain.ApplianceType;
import com.voltflow.core.domain.OperatingState;

import java.math.BigDecimal;
import java.time.Instant;

public record ApplianceLiveState(
        Long applianceId,
        String name,
        ApplianceType type,
        BigDecimal currentPowerWatts,
        BigDecimal accumulatedEnergyKwh,
        BigDecimal accumulatedCost,
        OperatingState operatingState,
        BigDecimal safePowerLimitWatts,
        int consecutiveBreachCount,
        ApplianceHealthStatus healthStatus,
        Instant lastUpdatedAt,
        SnapshotWindow snapshotWindow
) {
    public ApplianceLiveState {
        snapshotWindow = snapshotWindow == null ? SnapshotWindow.empty() : snapshotWindow;
    }

    public ApplianceLiveState(
            Long applianceId,
            String name,
            ApplianceType type,
            BigDecimal currentPowerWatts,
            BigDecimal accumulatedEnergyKwh,
            BigDecimal accumulatedCost,
            OperatingState operatingState,
            BigDecimal safePowerLimitWatts,
            int consecutiveBreachCount,
            ApplianceHealthStatus healthStatus,
            Instant lastUpdatedAt
    ) {
        this(applianceId, name, type, currentPowerWatts, accumulatedEnergyKwh, accumulatedCost,
                operatingState, safePowerLimitWatts, consecutiveBreachCount, healthStatus, lastUpdatedAt,
                SnapshotWindow.empty());
    }

    public static ApplianceLiveState empty(Long id, String name, ApplianceType type, BigDecimal safeLimit) {
        return new ApplianceLiveState(id, name, type, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                OperatingState.OFF, safeLimit, 0, ApplianceHealthStatus.NORMAL, null);
    }

    public ApplianceLiveState withSnapshotWindow(SnapshotWindow window) {
        return new ApplianceLiveState(applianceId, name, type, currentPowerWatts, accumulatedEnergyKwh,
                accumulatedCost, operatingState, safePowerLimitWatts, consecutiveBreachCount, healthStatus,
                lastUpdatedAt, window);
    }

    public boolean sameOperationalStateAs(ApplianceLiveState other) {
        return other != null
                && java.util.Objects.equals(applianceId, other.applianceId)
                && java.util.Objects.equals(name, other.name)
                && type == other.type
                && java.util.Objects.equals(currentPowerWatts, other.currentPowerWatts)
                && java.util.Objects.equals(accumulatedEnergyKwh, other.accumulatedEnergyKwh)
                && java.util.Objects.equals(accumulatedCost, other.accumulatedCost)
                && operatingState == other.operatingState
                && java.util.Objects.equals(safePowerLimitWatts, other.safePowerLimitWatts)
                && consecutiveBreachCount == other.consecutiveBreachCount
                && healthStatus == other.healthStatus
                && java.util.Objects.equals(lastUpdatedAt, other.lastUpdatedAt);
    }
}
