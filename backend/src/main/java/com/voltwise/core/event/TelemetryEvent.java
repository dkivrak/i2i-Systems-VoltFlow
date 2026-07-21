package com.voltwise.core.event;

import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryEvent(
        UUID eventId,
        int eventVersion,
        String eventType,
        Instant occurredAt,
        Long homeId,
        Long applianceId,
        ApplianceType applianceType,
        BigDecimal powerWatts,
        OperatingState operatingState
) {}
