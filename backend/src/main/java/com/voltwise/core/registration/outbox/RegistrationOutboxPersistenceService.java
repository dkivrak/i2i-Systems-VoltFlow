package com.voltwise.core.registration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.entity.RegistrationOutboxEntity;
import com.voltwise.core.persistence.repository.RegistrationOutboxRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class RegistrationOutboxPersistenceService {
    private final RegistrationOutboxRepository outbox;
    private final ObjectMapper objectMapper;
    private final VoltWiseProperties properties;

    public RegistrationOutboxPersistenceService(RegistrationOutboxRepository outbox, ObjectMapper objectMapper,
                                                 VoltWiseProperties properties) {
        this.outbox = outbox;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(HomeEntity home, AssetRegistrationEvent event) {
        RegistrationOutboxEntity entity = new RegistrationOutboxEntity();
        entity.setEventId(event.eventId());
        entity.setHome(home);
        entity.setEventVersion(event.eventVersion());
        entity.setEventType(event.eventType());
        entity.setOccurredAt(event.occurredAt());
        entity.setTopic(properties.getKafka().getAssetRegistrationTopic());
        entity.setEventPayload(serialize(event));
        entity.setStatus(RegistrationOutboxStatus.PENDING);
        entity.setAttemptCount(0);
        entity.setNextAttemptAt(Instant.now());
        outbox.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DispatchClaim> claim(UUID eventId) {
        Instant now = Instant.now();
        RegistrationOutboxEntity entity = outbox.findByEventIdForUpdate(eventId).orElse(null);
        if (entity == null || entity.getStatus() != RegistrationOutboxStatus.PENDING
                || entity.getNextAttemptAt().isAfter(now)) {
            return Optional.empty();
        }
        entity.setAttemptCount(entity.getAttemptCount() + 1);
        entity.setLastAttemptAt(now);
        entity.setNextAttemptAt(now.plusMillis(properties.getRegistrationOutbox().getAcknowledgementTimeoutMs()));
        return Optional.of(new DispatchClaim(entity.getEventId(), entity.getHome().getId(), entity.getTopic(),
                entity.getEventPayload(), entity.getAttemptCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPublished(UUID eventId) {
        outbox.findByEventIdForUpdate(eventId).ifPresent(entity -> {
            if (entity.getStatus() == RegistrationOutboxStatus.PUBLISHED) return;
            entity.setStatus(RegistrationOutboxStatus.PUBLISHED);
            entity.setPublishedAt(Instant.now());
            entity.setLastFailure(null);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(UUID eventId, String sanitizedFailure) {
        outbox.findByEventIdForUpdate(eventId).ifPresent(entity -> {
            if (entity.getStatus() == RegistrationOutboxStatus.PUBLISHED) return;
            entity.setStatus(RegistrationOutboxStatus.PENDING);
            entity.setLastFailure(limit(sanitizedFailure));
            entity.setNextAttemptAt(Instant.now().plusMillis(backoffMs(entity.getAttemptCount())));
        });
    }

    @Transactional(readOnly = true)
    public List<UUID> dueEventIds() {
        return outbox.findDueEventIds(RegistrationOutboxStatus.PENDING, Instant.now(),
                PageRequest.of(0, properties.getRegistrationOutbox().getBatchSize()));
    }

    private String serialize(AssetRegistrationEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Asset registration event could not be serialized", ex);
        }
    }

    private long backoffMs(int attemptCount) {
        long maximum = properties.getRegistrationOutbox().getMaximumBackoffMs();
        long delay = Math.min(properties.getRegistrationOutbox().getInitialBackoffMs(), maximum);
        for (int attempt = 1; attempt < attemptCount && delay < maximum; attempt++) {
            delay = delay > maximum / 2 ? maximum : Math.min(delay * 2, maximum);
        }
        return delay;
    }

    private String limit(String failure) {
        String safe = failure == null || failure.isBlank() ? "DeliveryFailure" : failure;
        return safe.length() <= 500 ? safe : safe.substring(0, 500);
    }

    public record DispatchClaim(UUID eventId, Long homeId, String topic, String payload, int attemptCount) {}
}
