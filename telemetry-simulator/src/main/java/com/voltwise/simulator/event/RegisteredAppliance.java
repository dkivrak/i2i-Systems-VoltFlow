package com.voltwise.simulator.event;

import com.voltwise.simulator.domain.ApplianceType;
import java.math.BigDecimal;

public record RegisteredAppliance(
        Long applianceId,
        String name,
        ApplianceType type,
        BigDecimal safePowerLimitWatts
) {
}
