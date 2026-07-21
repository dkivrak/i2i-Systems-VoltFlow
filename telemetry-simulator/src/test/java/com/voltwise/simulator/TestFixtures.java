package com.voltwise.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.event.AssetRegistrationEvent;
import com.voltwise.simulator.event.RegisteredAppliance;
import com.voltwise.simulator.generator.AirConditionerTelemetryGenerator;
import com.voltwise.simulator.generator.ApplianceTelemetryGenerator;
import com.voltwise.simulator.generator.ComputerTelemetryGenerator;
import com.voltwise.simulator.generator.GeneratorCatalog;
import com.voltwise.simulator.generator.KettleTelemetryGenerator;
import com.voltwise.simulator.generator.LampTelemetryGenerator;
import com.voltwise.simulator.generator.MicrowaveTelemetryGenerator;
import com.voltwise.simulator.generator.OvenTelemetryGenerator;
import com.voltwise.simulator.generator.RefrigeratorTelemetryGenerator;
import com.voltwise.simulator.generator.TelevisionTelemetryGenerator;
import com.voltwise.simulator.generator.WashingMachineTelemetryGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static ObjectMapper objectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }

    public static List<ApplianceTelemetryGenerator> generators() {
        return List.of(
                new RefrigeratorTelemetryGenerator(),
                new KettleTelemetryGenerator(),
                new OvenTelemetryGenerator(),
                new TelevisionTelemetryGenerator(),
                new WashingMachineTelemetryGenerator(),
                new AirConditionerTelemetryGenerator(),
                new MicrowaveTelemetryGenerator(),
                new LampTelemetryGenerator(),
                new ComputerTelemetryGenerator()
        );
    }

    public static GeneratorCatalog generatorCatalog() {
        return new GeneratorCatalog(generators());
    }

    public static AssetRegistrationEvent registrationEvent(UUID eventId, long homeId) {
        List<RegisteredAppliance> appliances = Arrays.stream(ApplianceType.values())
                .map(type -> new RegisteredAppliance(
                        100L + type.ordinal(),
                        type.name() + " appliance",
                        type,
                        new BigDecimal("10000")
                ))
                .toList();
        return new AssetRegistrationEvent(
                eventId,
                1,
                EventType.HOME_REGISTERED,
                Instant.parse("2026-07-21T12:00:00Z"),
                homeId,
                "Test Home",
                appliances
        );
    }
}
