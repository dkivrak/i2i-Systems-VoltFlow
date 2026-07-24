package com.voltflow.simulator.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.voltflow.simulator.domain.EventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AssetRegistrationEvent(
        UUID eventId,
        int eventVersion,
        EventType eventType,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        Long homeId,
        String homeName,
        List<RegisteredAppliance> appliances
) {
}
