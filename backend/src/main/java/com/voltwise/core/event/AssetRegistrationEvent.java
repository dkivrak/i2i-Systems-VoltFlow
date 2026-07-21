package com.voltwise.core.event;

import com.voltwise.core.domain.ApplianceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetRegistrationEvent(
        UUID eventId,
        int eventVersion,
        String eventType,
        Instant occurredAt,
        Long homeId,
        String homeName,
        List<RegisteredAppliance> appliances
) {
    public record RegisteredAppliance(
            Long applianceId,
            String name,
            ApplianceType type,
            BigDecimal safePowerLimitWatts
    ) {}
}
