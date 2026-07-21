package com.voltwise.core.notification;

import com.voltwise.core.api.HomeDtos.ApplianceRequest;
import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.NotificationStatus;
import com.voltwise.core.domain.TriggerType;
import com.voltwise.core.persistence.repository.NotificationRepository;
import com.voltwise.core.persistence.repository.RecommendationRepository;
import com.voltwise.core.registration.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class NotificationPersistenceIntegrationTest {
    @Autowired HomeService homes;
    @Autowired NotificationPersistenceService persistence;
    @Autowired RecommendationRepository recommendations;
    @Autowired NotificationRepository notifications;

    @Test
    void persistsPendingBeforeDeliveryAndDeduplicatesTrigger() {
        var home = homes.create(new CreateHomeRequest("Notify " + UUID.randomUUID(), "notify@example.com",
                new BigDecimal("100"), new BigDecimal("2"), new BigDecimal("1.5"),
                List.of(new ApplianceRequest("Lamp", ApplianceType.LAMP, new BigDecimal("100")))));
        var request = new DomainNotificationRequest(home.id(), TriggerType.QUOTA_80, 99123L + home.id());
        var generated = new RecommendationGenerator.GeneratedRecommendation(
                GeminiRecommendationGenerator.FALLBACK_TEXT, "test-model", true);

        var pending = persistence.createPending(request, generated, "VoltWise Test");
        assertThat(pending).isPresent();
        assertThat(notifications.findById(pending.orElseThrow().notificationId()).orElseThrow().getStatus())
                .isEqualTo(NotificationStatus.PENDING);
        assertThat(persistence.createPending(request, generated, "VoltWise Test")).isEmpty();

        persistence.markFailed(pending.orElseThrow().notificationId(), "SMTP unavailable");
        var failed = notifications.findById(pending.orElseThrow().notificationId()).orElseThrow();
        assertThat(failed.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failed.getFailureReason()).isEqualTo("SMTP unavailable");
        assertThat(recommendations.existsByHomeIdAndTriggerTypeAndTriggerReferenceId(
                home.id(), TriggerType.QUOTA_80, request.triggerReferenceId())).isTrue();
    }
}
