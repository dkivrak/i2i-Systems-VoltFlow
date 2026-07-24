package com.voltwise.core.notification;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.registration.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class NotificationPipeline {
    private static final Logger log = LoggerFactory.getLogger(NotificationPipeline.class);
    private final HomeRepository homes;
    private final LiveStateInitializer initializer;
    private final RecommendationGenerator generator;
    private final NotificationPersistenceService persistence;

    public NotificationPipeline(HomeRepository homes, LiveStateInitializer initializer,
                                RecommendationGenerator generator, NotificationPersistenceService persistence) {
        this.homes = homes;
        this.initializer = initializer;
        this.generator = generator;
        this.persistence = persistence;
    }

    @Async("notificationExecutor")
    @EventListener
    public void handle(DomainNotificationRequest request) {
        try {
            RecommendationContext context = context(request);
            RecommendationGenerator.GeneratedRecommendation generated = generator.generate(context);
            String subject = subject(request);
            persistence.createPending(request, generated, subject).ifPresent(pending -> {
                persistence.markSent(pending.notificationId());
            });
        } catch (Exception ex) {
            log.error("Notification pipeline failed for trigger={} referenceId={}",
                    request.triggerType(), request.triggerReferenceId(), ex);
        }
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
            case QUOTA_80 -> "VoltWise: Bütçenizin %80'ine ulaştınız";
            case QUOTA_100 -> "VoltWise: Aylık bütçenize ulaştınız";
            case TARIFF_ACTIVATED -> "VoltWise: Ek ücretli tarife etkinleşti";
            case APPLIANCE_ANOMALY -> "VoltWise: Olağan dışı cihaz tüketimi";
        };
    }

    private String safeMessage(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return ex.getClass().getSimpleName();
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }
}
