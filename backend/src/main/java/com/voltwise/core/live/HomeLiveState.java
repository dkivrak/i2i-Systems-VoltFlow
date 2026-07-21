package com.voltwise.core.live;

import com.voltwise.core.domain.TariffState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record HomeLiveState(
        Long homeId,
        String homeName,
        BigDecimal currentPowerWatts,
        BigDecimal accumulatedEnergyKwh,
        BigDecimal currentCost,
        BigDecimal monthlyBudget,
        BigDecimal budgetUsagePercent,
        TariffState tariffState,
        Instant lastUpdatedAt,
        Map<Long, ApplianceLiveState> appliances,
        SnapshotWindow snapshotWindow
) {
    public HomeLiveState {
        appliances = appliances == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(appliances));
        snapshotWindow = snapshotWindow == null ? SnapshotWindow.empty() : snapshotWindow;
    }

    public HomeLiveState(
            Long homeId,
            String homeName,
            BigDecimal currentPowerWatts,
            BigDecimal accumulatedEnergyKwh,
            BigDecimal currentCost,
            BigDecimal monthlyBudget,
            BigDecimal budgetUsagePercent,
            TariffState tariffState,
            Instant lastUpdatedAt,
            Map<Long, ApplianceLiveState> appliances
    ) {
        this(homeId, homeName, currentPowerWatts, accumulatedEnergyKwh, currentCost, monthlyBudget,
                budgetUsagePercent, tariffState, lastUpdatedAt, appliances, SnapshotWindow.empty());
    }

    public HomeLiveState withSnapshotWindows(
            SnapshotWindow homeWindow,
            Map<Long, ApplianceLiveState> applianceStates
    ) {
        return new HomeLiveState(homeId, homeName, currentPowerWatts, accumulatedEnergyKwh, currentCost,
                monthlyBudget, budgetUsagePercent, tariffState, lastUpdatedAt, applianceStates, homeWindow);
    }

    public boolean sameOperationalStateAs(HomeLiveState other) {
        if (other == null
                || !java.util.Objects.equals(homeId, other.homeId)
                || !java.util.Objects.equals(homeName, other.homeName)
                || !java.util.Objects.equals(currentPowerWatts, other.currentPowerWatts)
                || !java.util.Objects.equals(accumulatedEnergyKwh, other.accumulatedEnergyKwh)
                || !java.util.Objects.equals(currentCost, other.currentCost)
                || !java.util.Objects.equals(monthlyBudget, other.monthlyBudget)
                || !java.util.Objects.equals(budgetUsagePercent, other.budgetUsagePercent)
                || tariffState != other.tariffState
                || !java.util.Objects.equals(lastUpdatedAt, other.lastUpdatedAt)
                || !appliances.keySet().equals(other.appliances.keySet())) {
            return false;
        }
        return appliances.entrySet().stream()
                .allMatch(entry -> entry.getValue().sameOperationalStateAs(other.appliances.get(entry.getKey())));
    }
}
