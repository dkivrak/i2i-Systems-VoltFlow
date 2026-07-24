package com.voltflow.core.registration;

import com.voltflow.core.event.AssetRegistrationEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(prefix = "voltflow.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaRegistrationPublisher implements RegistrationPublisher {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaRegistrationPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Override
    public CompletableFuture<Void> publish(String topic, AssetRegistrationEvent event) {
        return kafkaTemplate.send(topic, event.homeId().toString(), event).thenApply(result -> null);
    }
}
