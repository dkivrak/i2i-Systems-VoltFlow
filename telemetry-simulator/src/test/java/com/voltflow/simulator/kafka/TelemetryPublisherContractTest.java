package com.voltflow.simulator.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltflow.simulator.TestFixtures;
import com.voltflow.simulator.config.KafkaTopicProperties;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.EventType;
import com.voltflow.simulator.domain.OperatingState;
import com.voltflow.simulator.event.TelemetryEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class TelemetryPublisherContractTest {

    @SuppressWarnings("unchecked")
    @Test
    void publishesPlainJsonWithStableKeyAndCompleteEventEnvelope() throws Exception {
        KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
        ObjectMapper objectMapper = TestFixtures.objectMapper();
        KafkaTopicProperties topics = new KafkaTopicProperties();
        CompletableFuture<SendResult<String, String>> pending = new CompletableFuture<>();
        when(kafkaTemplate.send(eq("voltflow.telemetry"), eq("1"), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(pending);
        TelemetryPublisher publisher = new TelemetryPublisher(kafkaTemplate, objectMapper, topics);
        TelemetryEvent event = new TelemetryEvent(
                UUID.fromString("a5a14b85-73c8-428b-bc0f-b1c3548e58fe"),
                1,
                EventType.APPLIANCE_TELEMETRY_RECORDED,
                Instant.parse("2026-07-21T12:00:01Z"),
                1L,
                10L,
                ApplianceType.KETTLE,
                new BigDecimal("1850.4"),
                OperatingState.ON
        );

        publisher.publish(event);

        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(kafkaTemplate).send(eq("voltflow.telemetry"), eq("1"), payload.capture());
        JsonNode json = objectMapper.readTree(payload.getValue());
        assertThat(json.path("eventId").asText()).isEqualTo("a5a14b85-73c8-428b-bc0f-b1c3548e58fe");
        assertThat(json.path("eventVersion").asInt()).isEqualTo(1);
        assertThat(json.path("eventType").asText()).isEqualTo("APPLIANCE_TELEMETRY_RECORDED");
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-07-21T12:00:01Z");
        assertThat(json.path("homeId").asLong()).isEqualTo(1L);
        assertThat(json.path("applianceId").asLong()).isEqualTo(10L);
        assertThat(json.path("applianceType").asText()).isEqualTo("KETTLE");
        assertThat(json.path("powerWatts").decimalValue()).isEqualByComparingTo("1850.4");
        assertThat(json.path("operatingState").asText()).isEqualTo("ON");
        assertThat(toSet(json)).containsExactlyInAnyOrder(
                "eventId", "eventVersion", "eventType", "occurredAt", "homeId", "applianceId",
                "applianceType", "powerWatts", "operatingState"
        );
    }

    private Set<String> toSet(JsonNode node) {
        java.util.Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
