package com.voltwise.simulator.service;

import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.event.AssetRegistrationEvent;
import com.voltwise.simulator.event.RegisteredAppliance;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RegistrationEventValidator {

    public void validate(AssetRegistrationEvent event) {
        require(event != null, "Registration payload is required");
        require(event.eventId() != null, "eventId is required");
        require(event.eventVersion() == 1, "Unsupported registration eventVersion: " + event.eventVersion());
        require(event.eventType() == EventType.HOME_REGISTERED, "eventType must be HOME_REGISTERED");
        require(event.occurredAt() != null, "occurredAt is required");
        require(event.homeId() != null && event.homeId() > 0, "homeId must be positive");
        require(event.homeName() != null && !event.homeName().isBlank(), "homeName is required");
        require(event.appliances() != null && !event.appliances().isEmpty(), "At least one appliance is required");

        Set<Long> applianceIds = new HashSet<>();
        for (RegisteredAppliance appliance : event.appliances()) {
            require(appliance != null, "Appliance entries cannot be null");
            require(appliance.applianceId() != null && appliance.applianceId() > 0,
                    "applianceId must be positive");
            require(applianceIds.add(appliance.applianceId()),
                    "Duplicate applianceId in registration: " + appliance.applianceId());
            require(appliance.name() != null && !appliance.name().isBlank(), "Appliance name is required");
            require(appliance.type() != null, "Appliance type is required");
            require(appliance.safePowerLimitWatts() != null
                            && appliance.safePowerLimitWatts().signum() > 0,
                    "safePowerLimitWatts must be positive");
        }
    }

    private void require(boolean condition, String message) {
        if (!condition) {
            throw new InvalidRegistrationEventException(message);
        }
    }
}
