package com.voltwise.core.registration;

import com.voltwise.core.event.AssetRegistrationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
@ConditionalOnProperty(prefix = "voltwise.kafka", name = "enabled", havingValue = "false")
public class DisabledRegistrationPublisher implements RegistrationPublisher {
    private static final Logger log = LoggerFactory.getLogger(DisabledRegistrationPublisher.class);
    @Override public CompletableFuture<Void> publish(String topic, AssetRegistrationEvent event) {
        log.debug("Kafka disabled; registration event {} remains pending", event.eventId());
        return CompletableFuture.failedFuture(new IllegalStateException("KafkaPublishingDisabled"));
    }
}
