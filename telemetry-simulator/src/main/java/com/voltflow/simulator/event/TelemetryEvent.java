package com.voltflow.simulator.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.EventType;
import com.voltflow.simulator.domain.OperatingState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record TelemetryEvent(
        UUID eventId,
        int eventVersion,
        EventType eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long homeId,
        Long applianceId,
        ApplianceType applianceType,
        BigDecimal powerWatts,
        OperatingState operatingState
) {
}
