package com.voltwise.core.notification;

import com.voltwise.core.domain.TariffState;
import com.voltwise.core.domain.TriggerType;

import java.math.BigDecimal;
import java.util.List;

public record RecommendationContext(
        Long homeId,
        String homeName,
        String contactEmail,
        TriggerType triggerType,
        BigDecimal accumulatedEnergyKwh,
        BigDecimal currentCost,
        BigDecimal monthlyBudget,
        BigDecimal budgetUsagePercent,
        TariffState tariffState,
        List<AnomalousAppliance> anomalousAppliances
) {
    public record AnomalousAppliance(String name, BigDecimal currentPowerWatts, BigDecimal safePowerLimitWatts) {}
}
