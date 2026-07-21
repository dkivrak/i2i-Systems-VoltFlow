package com.voltwise.core.registration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.registration.RegistrationPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class RegistrationOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(RegistrationOutboxDispatcher.class);
    private final RegistrationOutboxPersistenceService persistence;
    private final RegistrationPublisher publisher;
    private final ObjectMapper objectMapper;
    private final VoltWiseProperties properties;
    private final Executor dispatchExecutor;
    private final Executor callbackExecutor;

    public RegistrationOutboxDispatcher(RegistrationOutboxPersistenceService persistence,
                                        RegistrationPublisher publisher, ObjectMapper objectMapper,
                                        VoltWiseProperties properties,
                                        @Qualifier("registrationOutboxExecutor") Executor dispatchExecutor,
                                        @Qualifier("registrationOutboxCallbackExecutor") Executor callbackExecutor) {
        this.persistence = persistence;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.dispatchExecutor = dispatchExecutor;
        this.callbackExecutor = callbackExecutor;
    }

    @Async("registrationOutboxExecutor")
    public void requestImmediateDispatch(UUID eventId) {
        dispatchOne(eventId);
    }

    @Scheduled(fixedDelayString = "${voltwise.registration-outbox.retry-interval-ms:5000}",
            initialDelayString = "${voltwise.registration-outbox.startup-delay-ms:1000}")
    public void retryDue() {
        persistence.dueEventIds().forEach(eventId -> {
            try {
                dispatchExecutor.execute(() -> dispatchOne(eventId));
            } catch (RuntimeException ex) {
                log.warn("Asset registration outbox dispatch queue is full; event remains pending eventId={}",
                        eventId);
            }
        });
    }

    public CompletableFuture<Boolean> dispatchOne(UUID eventId) {
        var claim = persistence.claim(eventId);
        if (claim.isEmpty()) return CompletableFuture.completedFuture(false);
        var item = claim.orElseThrow();
        AssetRegistrationEvent event;
        try {
            event = objectMapper.readValue(item.payload(), AssetRegistrationEvent.class);
        } catch (JsonProcessingException ex) {
            persistence.markFailed(eventId, "InvalidOutboxPayload");
            return CompletableFuture.completedFuture(false);
        }

        CompletableFuture<Void> acknowledgement;
        try {
            acknowledgement = publisher.publish(item.topic(), event);
        } catch (Exception ex) {
            persistence.markFailed(eventId, sanitized(ex));
            return CompletableFuture.completedFuture(false);
        }
        return acknowledgement
                .orTimeout(properties.getRegistrationOutbox().getAcknowledgementTimeoutMs(), TimeUnit.MILLISECONDS)
                .handleAsync((ignored, failure) -> {
                    if (failure == null) {
                        persistence.markPublished(eventId);
                        log.info("Asset registration outbox acknowledged eventId={} homeId={} attempt={}",
                                eventId, item.homeId(), item.attemptCount());
                        return true;
                    }
                    String sanitized = sanitized(failure);
                    persistence.markFailed(eventId, sanitized);
                    log.warn("Asset registration outbox delivery deferred eventId={} homeId={} attempt={} failureType={}",
                            eventId, item.homeId(), item.attemptCount(), sanitized);
                    return false;
                }, callbackExecutor);
    }

    private String sanitized(Throwable failure) {
        Throwable current = failure;
        while (current instanceof CompletionException && current.getCause() != null) current = current.getCause();
        String type = current.getClass().getSimpleName();
        return type == null || type.isBlank() ? "DeliveryFailure" : type;
    }
}
