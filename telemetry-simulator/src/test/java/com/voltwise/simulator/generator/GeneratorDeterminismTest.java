package com.voltwise.simulator.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.simulator.TestFixtures;
import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.runtime.ApplianceRuntime;
import com.voltwise.simulator.runtime.SimulationRegistry;
import com.voltwise.simulator.service.AnomalyInjector;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GeneratorDeterminismTest {

    @Test
    void sameSeedProducesSameStatefulSequenceForEveryAppliance() {
        SimulationProperties firstProperties = propertiesWithSeed(987654321L);
        SimulationProperties secondProperties = propertiesWithSeed(987654321L);
        SimulationRegistry firstRegistry = new SimulationRegistry(firstProperties);
        SimulationRegistry secondRegistry = new SimulationRegistry(secondProperties);
        var registration = TestFixtures.registrationEvent(UUID.randomUUID(), 7L);
        firstRegistry.register(registration);
        secondRegistry.register(new com.voltwise.simulator.event.AssetRegistrationEvent(
                UUID.randomUUID(), registration.eventVersion(), registration.eventType(), registration.occurredAt(),
                registration.homeId(), registration.homeName(), registration.appliances()
        ));

        GeneratorCatalog catalog = TestFixtures.generatorCatalog();
        AnomalyInjector firstInjector = new AnomalyInjector(firstProperties);
        AnomalyInjector secondInjector = new AnomalyInjector(secondProperties);
        List<GeneratedTelemetry> firstReadings = new ArrayList<>();
        List<GeneratedTelemetry> secondReadings = new ArrayList<>();

        for (int cycle = 0; cycle < 100; cycle++) {
            generateAll(firstRegistry, catalog, firstProperties, firstInjector, firstReadings);
            generateAll(secondRegistry, catalog, secondProperties, secondInjector, secondReadings);
        }

        assertThat(firstReadings).hasSize(900).isEqualTo(secondReadings);
    }

    @Test
    void changingSeedChangesTheGeneratedSequence() {
        List<GeneratedTelemetry> first = sequenceForSeed(11L);
        List<GeneratedTelemetry> second = sequenceForSeed(12L);
        assertThat(first).isNotEqualTo(second);
    }

    private List<GeneratedTelemetry> sequenceForSeed(long seed) {
        SimulationProperties properties = propertiesWithSeed(seed);
        SimulationRegistry registry = new SimulationRegistry(properties);
        registry.register(TestFixtures.registrationEvent(UUID.randomUUID(), 2L));
        List<GeneratedTelemetry> result = new ArrayList<>();
        GeneratorCatalog catalog = TestFixtures.generatorCatalog();
        AnomalyInjector injector = new AnomalyInjector(properties);
        for (int cycle = 0; cycle < 20; cycle++) {
            generateAll(registry, catalog, properties, injector, result);
        }
        return result;
    }

    private void generateAll(
            SimulationRegistry registry,
            GeneratorCatalog catalog,
            SimulationProperties properties,
            AnomalyInjector injector,
            List<GeneratedTelemetry> target
    ) {
        for (ApplianceRuntime runtime : registry.snapshot()) {
            target.add(runtime.generate(catalog.generatorFor(runtime.appliance().type()), properties, injector));
        }
    }

    private SimulationProperties propertiesWithSeed(long seed) {
        SimulationProperties properties = new SimulationProperties();
        properties.setRandomSeed(seed);
        properties.getAnomaly().setProbability(0);
        properties.validateConfiguration();
        return properties;
    }
}
