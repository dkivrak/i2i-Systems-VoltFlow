package com.voltflow.simulator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.EventType;
import com.voltflow.simulator.event.AssetRegistrationEvent;
import com.voltflow.simulator.event.RegisteredAppliance;
import com.voltflow.simulator.generator.AirConditionerTelemetryGenerator;
import com.voltflow.simulator.generator.ApplianceTelemetryGenerator;
import com.voltflow.simulator.generator.ComputerTelemetryGenerator;
import com.voltflow.simulator.generator.GeneratorCatalog;
import com.voltflow.simulator.generator.KettleTelemetryGenerator;
import com.voltflow.simulator.generator.LampTelemetryGenerator;
import com.voltflow.simulator.generator.MicrowaveTelemetryGenerator;
import com.voltflow.simulator.generator.OvenTelemetryGenerator;
import com.voltflow.simulator.generator.RefrigeratorTelemetryGenerator;
import com.voltflow.simulator.generator.TelevisionTelemetryGenerator;
import com.voltflow.simulator.generator.WashingMachineTelemetryGenerator;
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
