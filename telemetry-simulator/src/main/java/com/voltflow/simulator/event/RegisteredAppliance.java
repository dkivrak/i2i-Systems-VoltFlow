package com.voltflow.simulator.event;

import com.voltflow.simulator.domain.ApplianceType;
import java.math.BigDecimal;

public record RegisteredAppliance(
        Long applianceId,
        String name,
        ApplianceType type,
        BigDecimal safePowerLimitWatts
) {
}
