package com.voltwise.core.registration.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.api.HomeDtos.ApplianceRequest;
import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.event.AssetRegistrationEvent;
import com.voltwise.core.persistence.entity.RegistrationOutboxEntity;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.persistence.repository.RegistrationOutboxRepository;
import com.voltwise.core.registration.HomeService;
import com.voltwise.core.registration.RegistrationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:voltwise_outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "voltwise.registration-outbox.retry-interval-ms=3600000",
        "voltwise.registration-outbox.startup-delay-ms=3600000",
        "voltwise.registration-outbox.acknowledgement-timeout-ms=5000",
        "voltwise.registration-outbox.initial-backoff-ms=100",
        "voltwise.registration-outbox.maximum-backoff-ms=500"
})
@ActiveProfiles("test")
@Import(RegistrationOutboxIntegrationTest.PublisherConfiguration.class)
class RegistrationOutboxIntegrationTest {
    @Autowired HomeService homes;
    @Autowired HomeRepository homeRepository;
    @Autowired RegistrationOutboxRepository outbox;
    @Autowired RegistrationOutboxDispatcher dispatcher;
    @Autowired ControllablePublisher publisher;
    @Autowired ObjectMapper objectMapper;
    @Autowired PlatformTransactionManager transactionManager;

    @BeforeEach
    void clearPublisherInvocations() {
        publisher.clear();
    }

    @Test
    void homeAndOutboxRollbackTogether() {
        long homesBefore = homeRepository.count();
        long outboxBefore = outbox.count();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            homes.create(request("Rollback"));
            assertThat(homeRepository.count()).isEqualTo(homesBefore + 1);
            assertThat(outbox.count()).isEqualTo(outboxBefore + 1);
            status.setRollbackOnly();
        });

        assertThat(homeRepository.count()).isEqualTo(homesBefore);
        assertThat(outbox.count()).isEqualTo(outboxBefore);
        assertThat(publisher.poll(Duration.ofMillis(100))).isNull();
    }

    @Test
    void committedRegistrationStoresStablePayloadAndAcknowledgementMarksPublished() throws Exception {
        var home = homes.create(request("Acknowledged"));
        Invocation invocation = publisher.take();
        RegistrationOutboxEntity stored = singleOutbox(home.id());
        AssetRegistrationEvent payload = objectMapper.readValue(stored.getEventPayload(), AssetRegistrationEvent.class);

        assertThat(stored.getStatus()).isEqualTo(RegistrationOutboxStatus.PENDING);
        assertThat(stored.getAttemptCount()).isEqualTo(1);
        assertThat(stored.getEventId()).isEqualTo(invocation.event().eventId()).isEqualTo(payload.eventId());
        assertThat(payload.homeId()).isEqualTo(home.id());
        assertThat(payload.appliances()).hasSize(1);
        assertThat(invocation.topic()).isEqualTo("voltwise.asset-registration");

        invocation.acknowledgement().complete(null);
        await(() -> singleOutbox(home.id()).getStatus() == RegistrationOutboxStatus.PUBLISHED);
        RegistrationOutboxEntity published = singleOutbox(home.id());
        assertThat(published.getPublishedAt()).isNotNull();
        assertThat(published.getLastFailure()).isNull();
    }

    @Test
    void failedAcknowledgementStoresSanitizedStateAndDueRetryReusesEventId() throws Exception {
        var home = homes.create(request("Retry"));
        Invocation first = publisher.take();
        first.acknowledgement().completeExceptionally(
                new IllegalStateException("confidential-marker-must-not-be-persisted"));

        await(() -> singleOutbox(home.id()).getLastFailure() != null);
        RegistrationOutboxEntity failed = singleOutbox(home.id());
        assertThat(failed.getStatus()).isEqualTo(RegistrationOutboxStatus.PENDING);
        assertThat(failed.getAttemptCount()).isEqualTo(1);
        assertThat(failed.getLastFailure()).isEqualTo("IllegalStateException")
                .doesNotContain("confidential-marker");
        assertThat(failed.getNextAttemptAt()).isAfter(failed.getLastAttemptAt());

        long waitMillis = Math.max(0, Duration.between(Instant.now(), failed.getNextAttemptAt()).toMillis()) + 25;
        Thread.sleep(waitMillis);
        dispatcher.retryDue();
        Invocation second = publisher.take();
        assertThat(second.event().eventId()).isEqualTo(first.event().eventId());
        second.acknowledgement().complete(null);
        await(() -> singleOutbox(home.id()).getStatus() == RegistrationOutboxStatus.PUBLISHED);

        RegistrationOutboxEntity published = singleOutbox(home.id());
        assertThat(published.getStatus()).isEqualTo(RegistrationOutboxStatus.PUBLISHED);
        assertThat(published.getAttemptCount()).isEqualTo(2);
    }

    private RegistrationOutboxEntity singleOutbox(Long homeId) {
        return outbox.findByHomeIdOrderByCreatedAtAsc(homeId).getFirst();
    }

    private CreateHomeRequest request(String suffix) {
        return new CreateHomeRequest("Outbox " + suffix + " " + UUID.randomUUID(), "outbox@example.com",
                new BigDecimal("100"), new BigDecimal("2.5"), new BigDecimal("1.5"),
                List.of(new ApplianceRequest("Lamp", ApplianceType.LAMP, new BigDecimal("100"))));
    }

    private void await(BooleanSupplier condition) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) Thread.sleep(20);
        assertThat(condition.getAsBoolean()).isTrue();
    }

    @TestConfiguration
    static class PublisherConfiguration {
        @Bean
        @Primary
        ControllablePublisher controllableRegistrationPublisher() {
            return new ControllablePublisher();
        }
    }

    static class ControllablePublisher implements RegistrationPublisher {
        private final BlockingQueue<Invocation> invocations = new LinkedBlockingQueue<>();

        @Override
        public CompletableFuture<Void> publish(String topic, AssetRegistrationEvent event) {
            CompletableFuture<Void> acknowledgement = new CompletableFuture<>();
            invocations.add(new Invocation(topic, event, acknowledgement));
            return acknowledgement;
        }

        Invocation take() throws InterruptedException {
            Invocation invocation = invocations.poll(5, TimeUnit.SECONDS);
            assertThat(invocation).isNotNull();
            return invocation;
        }

        Invocation poll(Duration timeout) {
            try {
                return invocations.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(ex);
            }
        }

        void clear() {
            invocations.clear();
        }
    }

    record Invocation(String topic, AssetRegistrationEvent event, CompletableFuture<Void> acknowledgement) {}
}
