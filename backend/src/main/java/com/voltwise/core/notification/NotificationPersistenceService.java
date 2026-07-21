package com.voltwise.core.notification;

import com.voltwise.core.domain.NotificationChannel;
import com.voltwise.core.domain.NotificationStatus;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.entity.NotificationEntity;
import com.voltwise.core.persistence.entity.RecommendationEntity;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.persistence.repository.NotificationRepository;
import com.voltwise.core.persistence.repository.RecommendationRepository;
import com.voltwise.core.registration.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
public class NotificationPersistenceService {
    private final HomeRepository homes;
    private final RecommendationRepository recommendations;
    private final NotificationRepository notifications;

    public NotificationPersistenceService(HomeRepository homes, RecommendationRepository recommendations,
                                          NotificationRepository notifications) {
        this.homes = homes;
        this.recommendations = recommendations;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<PendingDelivery> createPending(DomainNotificationRequest request,
            RecommendationGenerator.GeneratedRecommendation generated, String subject) {
        if (recommendations.existsByHomeIdAndTriggerTypeAndTriggerReferenceId(
                request.homeId(), request.triggerType(), request.triggerReferenceId())) return Optional.empty();
        HomeEntity home = homes.findById(request.homeId())
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + request.homeId()));
        RecommendationEntity recommendation = new RecommendationEntity();
        recommendation.setHome(home);
        recommendation.setTriggerType(request.triggerType());
        recommendation.setTriggerReferenceId(request.triggerReferenceId());
        recommendation.setRecommendationText(generated.text());
        recommendation.setModelName(generated.modelName());
        recommendation.setFallbackUsed(generated.fallbackUsed());
        recommendation.setCreatedAt(Instant.now());
        recommendation = recommendations.save(recommendation);

        NotificationEntity notification = new NotificationEntity();
        notification.setHome(home);
        notification.setRecommendation(recommendation);
        notification.setChannel(NotificationChannel.EMAIL);
        notification.setRecipient(home.getContactEmail());
        notification.setSubject(subject);
        notification.setStatus(NotificationStatus.PENDING);
        notification.setCreatedAt(Instant.now());
        notification = notifications.save(notification);
        return Optional.of(new PendingDelivery(notification.getId(), home.getContactEmail(), subject, generated.text()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long notificationId) {
        NotificationEntity notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(Instant.now());
        notification.setFailureReason(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long notificationId, String reason) {
        NotificationEntity notification = notifications.findById(notificationId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found: " + notificationId));
        notification.setStatus(NotificationStatus.FAILED);
        String safe = reason == null ? "Unknown delivery failure" : reason;
        notification.setFailureReason(safe.length() > 1000 ? safe.substring(0, 1000) : safe);
    }

    public record PendingDelivery(Long notificationId, String recipient, String subject, String body) {}
}
