package com.voltwise.simulator.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.domain.OperatingState;
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
