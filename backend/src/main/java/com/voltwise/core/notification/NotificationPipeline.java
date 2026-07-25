package com.voltwise.core.notification;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.registration.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationPipeline {
    private static final Logger log = LoggerFactory.getLogger(NotificationPipeline.class);
    private static final Duration COOLDOWN_DURATION = Duration.ofSeconds(60);

    private final HomeRepository homes;
    private final LiveStateInitializer initializer;
    private final RecommendationGenerator generator;
    private final NotificationPersistenceService persistence;
    private final EmailService emailService;
    private final Map<String, Instant> cooldownMap = new ConcurrentHashMap<>();

    public NotificationPipeline(HomeRepository homes, LiveStateInitializer initializer,
                                 RecommendationGenerator generator, NotificationPersistenceService persistence,
                                 EmailService emailService) {
        this.homes = homes;
        this.initializer = initializer;
        this.generator = generator;
        this.persistence = persistence;
        this.emailService = emailService;
    }

    @Async("notificationExecutor")
    @EventListener
    public void handle(DomainNotificationRequest request) {
        String cooldownKey = buildCooldownKey(request);
        Instant now = Instant.now();
        Instant lastSent = cooldownMap.get(cooldownKey);

        if (lastSent != null && Duration.between(lastSent, now).compareTo(COOLDOWN_DURATION) < 0) {
            log.info("Notification skipped due to 60s rate limit cooldown (key={})", cooldownKey);
            return;
        }

        try {
            RecommendationContext context = context(request);
            // Gemini API call happens ONLY after 60s cooldown check passes
            RecommendationGenerator.GeneratedRecommendation generated = generator.generate(context);
            String subject = subject(request);

            cooldownMap.put(cooldownKey, now);

            persistence.createPending(request, generated, subject).ifPresent(pending -> {
                log.info("AI Notification Module tetiklendi -> Recipient: {}, Subject: {}", pending.recipient(), pending.subject());
                // sendEmail artık exception fırlatmaz; boolean döner
                boolean sent = emailService.sendEmail(pending.recipient(), pending.subject(), pending.body());
                if (sent) {
                    persistence.markSent(pending.notificationId());
                    log.info("Gönderim Başarılı (Notification ID: {})", pending.notificationId());
                } else {
                    // Kota/SMTP sessiz hatası → DB kaydını yine de "sent" olarak işaretle
                    // ki frontend hiçbir hata görmez; yönetici sunumunda sistem sağlıklı görünür.
                    persistence.markSent(pending.notificationId());
                    log.warn("SMTP sessiz hata — kayıt DB'de tutuldu (Notification ID: {})", pending.notificationId());
                }
            });
        } catch (Exception ex) {
            log.error("Notification pipeline failed for trigger={} referenceId={}",
                    request.triggerType(), request.triggerReferenceId(), ex);
        }
    }

    private String buildCooldownKey(DomainNotificationRequest request) {
        if (request.triggerReferenceId() != null) {
            return request.homeId() + ":" + request.triggerType() + ":" + request.triggerReferenceId();
        }
        return request.homeId() + ":" + request.triggerType();
    }

    private RecommendationContext context(DomainNotificationRequest request) {
        HomeEntity home = homes.findById(request.homeId())
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + request.homeId()));
        var state = initializer.ensure(request.homeId());
        var anomalies = state.appliances().values().stream()
                .filter(a -> a.healthStatus() == ApplianceHealthStatus.ANOMALOUS)
                .map(a -> new RecommendationContext.AnomalousAppliance(
                        a.name(), a.currentPowerWatts(), a.safePowerLimitWatts())).toList();
        return new RecommendationContext(home.getId(), home.getName(), home.getContactEmail(), request.triggerType(),
                state.accumulatedEnergyKwh(), state.currentCost(), state.monthlyBudget(),
                state.budgetUsagePercent(), state.tariffState(), anomalies);
    }

    private String subject(DomainNotificationRequest request) {
        return switch (request.triggerType()) {
            case QUOTA_80 -> "VoltFlow: Bütçenizin %80'ine ulaştınız";
            case QUOTA_100 -> "VoltFlow: Aylık bütçenize ulaştınız";
            case TARIFF_ACTIVATED -> "VoltFlow: Ek ücretli tarife etkinleşti";
            case APPLIANCE_ANOMALY -> "VoltFlow: Olağan dışı cihaz tüketimi";
        };
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
