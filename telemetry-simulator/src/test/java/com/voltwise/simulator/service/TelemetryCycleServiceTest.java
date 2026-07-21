package com.voltwise.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.voltwise.simulator.TestFixtures;
import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.event.TelemetryEvent;
import com.voltwise.simulator.generator.GeneratorCatalog;
import com.voltwise.simulator.kafka.TelemetryPublisher;
import com.voltwise.simulator.runtime.SimulationRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TelemetryCycleServiceTest {

    @Test
    void publishesOneContractEventPerRegisteredAppliance() {
        SimulationProperties properties = new SimulationProperties();
        properties.getAnomaly().setProbability(0);
        SimulationRegistry registry = new SimulationRegistry(properties);
        registry.register(TestFixtures.registrationEvent(UUID.randomUUID(), 5L));
        GeneratorCatalog catalog = TestFixtures.generatorCatalog();
        TelemetryPublisher publisher = mock(TelemetryPublisher.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-21T12:00:01Z"), ZoneOffset.UTC);
        TelemetryCycleService service = new TelemetryCycleService(
                registry, catalog, properties, new AnomalyInjector(properties), publisher, clock
        );

        service.generateCycle();

        ArgumentCaptor<TelemetryEvent> events = ArgumentCaptor.forClass(TelemetryEvent.class);
        verify(publisher, org.mockito.Mockito.times(9)).publish(events.capture());
        assertThat(events.getAllValues()).allSatisfy(event -> {
            assertThat(event.eventId()).isNotNull();
            assertThat(event.eventVersion()).isEqualTo(1);
            assertThat(event.eventType()).isEqualTo(EventType.APPLIANCE_TELEMETRY_RECORDED);
            assertThat(event.occurredAt()).isEqualTo(clock.instant());
            assertThat(event.homeId()).isEqualTo(5L);
            assertThat(event.powerWatts()).isNotNegative();
            assertThat(event.operatingState()).isNotNull();
        });
        assertThat(events.getAllValues()).extracting(TelemetryEvent::applianceType)
                .containsExactlyInAnyOrder(com.voltwise.simulator.domain.ApplianceType.values());
    }
}
