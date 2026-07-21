package com.voltwise.simulator.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.simulator.TestFixtures;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.domain.OperatingState;
import com.voltwise.simulator.event.TelemetryEvent;
import com.voltwise.simulator.runtime.SimulationRegistry;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest(properties = {
        "debug=false",
        "simulation.enabled=false",
        "spring.kafka.consumer.group-id=simulator-contract-integration",
        "voltwise.kafka.partitions=1",
        "voltwise.kafka.replicas=1",
        "logging.level.org.apache.kafka=ERROR",
        "logging.level.org.springframework.kafka=WARN"
})
@EmbeddedKafka(
        kraft = true,
        partitions = 1,
        topics = {
                "voltwise.asset-registration",
                "voltwise.asset-registration.dlt",
                "voltwise.telemetry"
        },
        brokerProperties = {
                "offsets.topic.num.partitions=1",
                "transaction.state.log.num.partitions=1",
                "transaction.state.log.replication.factor=1"
        },
        bootstrapServersProperty = "spring.kafka.bootstrap-servers"
)
@DirtiesContext
class KafkaContractIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TelemetryPublisher telemetryPublisher;

    @Autowired
    private SimulationRegistry registry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    void consumesRegistrationAndProducesTelemetryOverRealKafkaWireContract() throws Exception {
        var consumerProperties = KafkaTestUtils.consumerProps("telemetry-contract-reader", "false", broker);
        try (Consumer<String, String> telemetryConsumer = new DefaultKafkaConsumerFactory<>(
                consumerProperties, new StringDeserializer(), new StringDeserializer()
        ).createConsumer()) {
            telemetryConsumer.subscribe(List.of("voltwise.telemetry"));

            var registration = TestFixtures.registrationEvent(UUID.randomUUID(), 77L);
            kafkaTemplate.send(
                    "voltwise.asset-registration",
                    "77",
                    objectMapper.writeValueAsString(registration)
            ).get(5, TimeUnit.SECONDS);
            awaitRegisteredAppliances(9, Duration.ofSeconds(5));

            TelemetryEvent telemetry = new TelemetryEvent(
                    UUID.randomUUID(),
                    1,
                    EventType.APPLIANCE_TELEMETRY_RECORDED,
                    Instant.parse("2026-07-21T12:00:01Z"),
                    77L,
                    101L,
                    ApplianceType.KETTLE,
                    new BigDecimal("1850.4"),
                    OperatingState.ON
            );
            telemetryPublisher.publish(telemetry).get(5, TimeUnit.SECONDS);

            ConsumerRecord<String, String> record = KafkaTestUtils.getSingleRecord(
                    telemetryConsumer, "voltwise.telemetry", Duration.ofSeconds(5)
            );
            JsonNode payload = objectMapper.readTree(record.value());
            assertThat(record.key()).isEqualTo("77");
            assertThat(payload.path("eventType").asText()).isEqualTo("APPLIANCE_TELEMETRY_RECORDED");
            assertThat(payload.path("occurredAt").asText()).isEqualTo("2026-07-21T12:00:01Z");
            assertThat(payload.path("powerWatts").decimalValue()).isEqualByComparingTo("1850.4");
        }
    }

    private void awaitRegisteredAppliances(int expectedCount, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (registry.applianceCount() != expectedCount && System.nanoTime() < deadline) {
            LockSupport.parkNanos(Duration.ofMillis(10).toNanos());
        }
        assertThat(registry.applianceCount()).isEqualTo(expectedCount);
    }
}
