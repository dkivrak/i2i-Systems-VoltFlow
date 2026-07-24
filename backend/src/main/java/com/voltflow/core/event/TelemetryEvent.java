package com.voltflow.core.event;

import com.voltflow.core.domain.ApplianceType;
import com.voltflow.core.domain.OperatingState;

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
