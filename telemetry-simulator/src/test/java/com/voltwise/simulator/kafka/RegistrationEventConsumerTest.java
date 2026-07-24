package com.voltwise.simulator.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.simulator.TestFixtures;
import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.runtime.SimulationRegistry;
import com.voltwise.simulator.service.InvalidRegistrationEventException;
import com.voltwise.simulator.service.RegistrationEventValidator;
import java.util.UUID;
import java.util.ArrayList;
import java.math.BigDecimal;
import java.time.Instant;
import com.voltwise.simulator.event.AssetRegistrationEvent;
import com.voltwise.simulator.event.RegisteredAppliance;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.EventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RegistrationEventConsumerTest {

    private ObjectMapper objectMapper;
    private SimulationRegistry registry;
    private RegistrationEventConsumer consumer;

    @BeforeEach
    void setUp() {
        objectMapper = TestFixtures.objectMapper();
        registry = new SimulationRegistry(new SimulationProperties());
        consumer = new RegistrationEventConsumer(objectMapper, new RegistrationEventValidator(), registry);
    }

    @Test
    void consumesContractJsonAndDynamicallyRegistersEveryAppliance() throws Exception {
        var event = TestFixtures.registrationEvent(UUID.randomUUID(), 42L);
        String json = objectMapper.writeValueAsString(event);

        consumer.consume(json, "42");

        assertThat(registry.applianceCount()).isEqualTo(9);
        assertThat(registry.homeName(42L)).isEqualTo("Test Home");
        assertThat(registry.snapshot()).extracting(runtime -> runtime.appliance().type())
                .containsExactlyInAnyOrder(com.voltwise.simulator.domain.ApplianceType.values());
    }

    @Test
    void duplicateEventIdIsIdempotentAndDoesNotResetRuntimeState() throws Exception {
        var event = TestFixtures.registrationEvent(UUID.randomUUID(), 42L);
        String json = objectMapper.writeValueAsString(event);
        consumer.consume(json, "42");
        var firstRuntime = registry.snapshot().get(0);

        consumer.consume(json, "42");

        assertThat(registry.applianceCount()).isEqualTo(9);
        assertThat(registry.snapshot().get(0)).isSameAs(firstRuntime);
    }

    @Test
    void discoversNewApplianceFromLaterFullHomeEventWithoutRestarting() throws Exception {
        var initial = TestFixtures.registrationEvent(UUID.randomUUID(), 42L);
        consumer.consume(objectMapper.writeValueAsString(initial), "42");
        var existingRuntime = registry.snapshot().get(0);

        var updatedAppliances = new ArrayList<>(initial.appliances());
        updatedAppliances.add(new RegisteredAppliance(
                999L,
                "New office computer",
                ApplianceType.COMPUTER,
                new BigDecimal("700")));
        var updated = new AssetRegistrationEvent(
                UUID.randomUUID(),
                1,
                EventType.HOME_REGISTERED,
                Instant.parse("2026-07-21T12:01:00Z"),
                42L,
                "Test Home",
                updatedAppliances);

        consumer.consume(objectMapper.writeValueAsString(updated), "42");

        assertThat(registry.applianceCount()).isEqualTo(10);
        assertThat(registry.snapshot()).contains(existingRuntime);
        assertThat(registry.snapshot())
                .filteredOn(runtime -> runtime.appliance().applianceId().equals(999L))
                .singleElement()
                .satisfies(runtime ->
                        assertThat(runtime.appliance().name()).isEqualTo("New office computer"));
    }

    @Test
    void rejectsMalformedOrContractInvalidPayloads() {
        assertThatThrownBy(() -> consumer.consume("{not-json", null))
                .isInstanceOf(InvalidRegistrationEventException.class)
                .hasMessage("Invalid asset registration JSON");
        assertThatThrownBy(() -> consumer.consume("{}", null))
                .isInstanceOf(InvalidRegistrationEventException.class)
                .hasMessage("eventId is required");
    }
}
