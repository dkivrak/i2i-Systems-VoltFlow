package com.voltflow.simulator.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltflow.simulator.config.KafkaTopicProperties;
import com.voltflow.simulator.event.TelemetryEvent;
import com.voltflow.simulator.service.TelemetryPublicationException;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

@Component
public class TelemetryPublisher {

    private static final Logger log = LoggerFactory.getLogger(TelemetryPublisher.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final KafkaTopicProperties topicProperties;

    public TelemetryPublisher(
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            KafkaTopicProperties topicProperties
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.topicProperties = topicProperties;
    }

    public CompletableFuture<SendResult<String, String>> publish(TelemetryEvent event) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException exception) {
            throw new TelemetryPublicationException("Could not serialize telemetry event", exception);
        }

        CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(
                topicProperties.getTelemetryTopic(),
                event.homeId().toString(),
                payload
        );
        future.whenComplete((result, failure) -> {
            if (failure != null) {
                log.error(
                        "Telemetry publication failed eventId={} applianceId={}",
                        event.eventId(), event.applianceId(), failure
                );
            } else {
                log.debug(
                        "Published telemetry eventId={} applianceId={} partition={} offset={}",
                        event.eventId(), event.applianceId(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset()
                );
            }
        });
        return future;
    }
}
