package com.voltflow.simulator.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltflow.simulator.event.AssetRegistrationEvent;
import com.voltflow.simulator.runtime.RegistrationResult;
import com.voltflow.simulator.runtime.SimulationRegistry;
import com.voltflow.simulator.service.InvalidRegistrationEventException;
import com.voltflow.simulator.service.RegistrationEventValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
public class RegistrationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(RegistrationEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final RegistrationEventValidator validator;
    private final SimulationRegistry registry;

    public RegistrationEventConsumer(
            ObjectMapper objectMapper,
            RegistrationEventValidator validator,
            SimulationRegistry registry
    ) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.registry = registry;
    }

    @KafkaListener(
            topics = "${voltflow.kafka.asset-registration-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(
            String payload,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String messageKey
    ) {
        AssetRegistrationEvent event;
        try {
            event = objectMapper.readValue(payload, AssetRegistrationEvent.class);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new InvalidRegistrationEventException("Invalid asset registration JSON", exception);
        }

        validator.validate(event);
        RegistrationResult result = registry.register(event);
        if (result.duplicateEvent()) {
            log.info("Ignored duplicate asset registration event eventId={} key={}", event.eventId(), messageKey);
            return;
        }
        log.info(
                "Registered simulation assets eventId={} homeId={} added={} updated={}",
                event.eventId(), event.homeId(), result.addedAppliances(), result.updatedAppliances()
        );
    }
}
