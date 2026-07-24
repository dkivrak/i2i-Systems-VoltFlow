package com.voltflow.simulator.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltflow.simulator.TestFixtures;
import com.voltflow.simulator.config.SimulationProperties;
import com.voltflow.simulator.runtime.SimulationRegistry;
import com.voltflow.simulator.service.InvalidRegistrationEventException;
import com.voltflow.simulator.service.RegistrationEventValidator;
import java.util.UUID;
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
                .containsExactlyInAnyOrder(com.voltflow.simulator.domain.ApplianceType.values());
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
    void rejectsMalformedOrContractInvalidPayloads() {
        assertThatThrownBy(() -> consumer.consume("{not-json", null))
                .isInstanceOf(InvalidRegistrationEventException.class)
                .hasMessage("Invalid asset registration JSON");
        assertThatThrownBy(() -> consumer.consume("{}", null))
                .isInstanceOf(InvalidRegistrationEventException.class)
                .hasMessage("eventId is required");
    }
}
