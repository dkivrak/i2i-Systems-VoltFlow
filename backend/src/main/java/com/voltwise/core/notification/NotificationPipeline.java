package com.voltwise.core.notification;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.TriggerType;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.registration.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
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
    private final AnomalyEventRepository anomalyEvents;
    private final Map<String, Instant> cooldownMap = new ConcurrentHashMap<>();

    public NotificationPipeline(HomeRepository homes, LiveStateInitializer initializer,
                                 RecommendationGenerator generator, NotificationPersistenceService persistence,
                                 EmailService emailService, AnomalyEventRepository anomalyEvents) {
        this.homes = homes;
        this.initializer = initializer;
        this.generator = generator;
        this.persistence = persistence;
        this.emailService = emailService;
        this.anomalyEvents = anomalyEvents;
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
            String subject;
            RecommendationGenerator.GeneratedRecommendation generated;

            if (request.triggerType() == TriggerType.APPLIANCE_ANOMALY && request.triggerReferenceId() != null) {
                var anomalyOpt = anomalyEvents.findById(request.triggerReferenceId());
                if (anomalyOpt.isPresent()) {
                    var entity = anomalyOpt.get();
                    var appliance = entity.getAppliance();
                    String rawName = appliance != null ? appliance.getName() : "Cihaz";
                    String applianceName = cleanApplianceName(rawName);
                    ApplianceType type = appliance != null ? appliance.getType() : null;

                    subject = "VoltFlow: " + applianceName + " güvenli sınırı aştı";

                    long measuredWatts = entity.getMeasuredPowerWatts() != null ? entity.getMeasuredPowerWatts().longValue() : 0;
                    long safeLimitWatts = entity.getSafePowerLimitWatts() != null ? entity.getSafePowerLimitWatts().longValue() : 1;
                    long excessDiff = Math.max(0, measuredWatts - safeLimitWatts);
                    long excessPercent = safeLimitWatts > 0 ? Math.round((double) excessDiff / safeLimitWatts * 100) : 0;

                    String advice = getApplianceAdvice(applianceName, type);
                    String possessiveName = toPossessive(applianceName);

                    String bodyText = """
                            Merhaba,

                            VoltFlow AI sistemi, evinize kayıtlı %s güvenli güç sınırını aştığını tespit etti.

                            📊 Anlık Tüketim: %dW (Güvenli sınır: %dW)
                            ⚠️ Durum: Güvenli Watt sınırı %%%d aşıldı

                            🔍 Öneri: %s

                            Bu bildirim VoltFlow AI tarafından otomatik oluşturulmuştur.
                            """.formatted(possessiveName, measuredWatts, safeLimitWatts, excessPercent, advice).strip();

                    generated = new RecommendationGenerator.GeneratedRecommendation(bodyText, "voltflow-template", false);
                } else {
                    subject = subject(request);
                    generated = generator.generate(context);
                }
            } else {
                subject = subject(request);
                generated = generator.generate(context);
            }

            cooldownMap.put(cooldownKey, now);

            persistence.createPending(request, generated, subject).ifPresent(pending -> {
                log.info("AI Notification Module tetiklendi -> Recipient: {}, Subject: {}", pending.recipient(), pending.subject());
                boolean sent = emailService.sendEmail(pending.recipient(), pending.subject(), pending.body());
                if (sent) {
                    persistence.markSent(pending.notificationId());
                    log.info("Gönderim Başarılı (Notification ID: {})", pending.notificationId());
                } else {
                    persistence.markSent(pending.notificationId());
                    log.warn("SMTP sessiz hata — kayıt DB'de tutuldu (Notification ID: {})", pending.notificationId());
                }
            });
        } catch (Exception ex) {
            log.error("Notification pipeline failed for trigger={} referenceId={}",
                    request.triggerType(), request.triggerReferenceId(), ex);
        }
    }

    private String cleanApplianceName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "Cihaz";
        String cleaned = rawName.replaceAll("\\s*\\([^)]*\\)", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\b(mutfak|salon|ofis)\\b\\s*", "").trim();
        if (cleaned.equalsIgnoreCase("lambası") || cleaned.equalsIgnoreCase("lamba")) return "Lamba";
        if (cleaned.isBlank()) return rawName;
        return cleaned.substring(0, 1).toUpperCase(Locale.forLanguageTag("tr")) + cleaned.substring(1);
    }

    private String getApplianceAdvice(String applianceName, ApplianceType type) {
        if (type != null) {
            switch (type) {
                case WASHING_MACHINE:
                    return "Makinenin aşırı yüklenip yüklenmediğini kontrol edin. Su giriş vanasını gözden geçirin.";
                case REFRIGERATOR:
                    return "Kapı contasını, hava dolaşımını ve termostat ayarını kontrol edin; sorun sürerse teknik servis desteği alın.";
                case KETTLE:
                    return "Cihazı kapatın, rezistans çevresindeki kireci ve elektrik bağlantısını kontrol edin.";
                case OVEN:
                    return "Fırını kapatın; aynı hatta çalışan yüksek güçlü cihazları ve ısıtma elemanlarını kontrol edin.";
                case TELEVISION:
                    return "Bağlı çevre birimlerini çıkarın, güç tasarrufu ayarlarını kontrol edin ve cihazı yeniden başlatın.";
                case AIR_CONDITIONER:
                    return "Filtreleri ve hava akışını kontrol edin; kompresör yükü yüksek kalırsa cihazı kapatıp servis çağırın.";
                case MICROWAVE:
                    return "Cihazı kapatın, içinde metal cisim bulunmadığını doğrulayın ve güvenli elektrik bağlantısını kontrol edin.";
                case LAMP:
                    return "Armatürü kapatın; ampul gücünü, sürücüyü ve bağlantıları güvenli biçimde kontrol edin.";
                case COMPUTER:
                    return "Yüksek kaynak kullanan uygulamaları kapatın, soğutmayı ve güç kaynağını kontrol edin.";
            }
        }
        String lower = applianceName != null ? applianceName.toLowerCase(Locale.forLanguageTag("tr")) : "";
        if (lower.contains("çamaşır") || lower.contains("washer")) {
            return "Makinenin aşırı yüklenip yüklenmediğini kontrol edin. Su giriş vanasını gözden geçirin.";
        }
        return "Cihazın aşırı yüklenip yüklenmediğini kontrol edin ve elektrik bağlantılarını gözden geçirin.";
    }

    private String toPossessive(String name) {
        if (name == null || name.isBlank()) return "cihazınızın";
        String lower = name.trim().toLowerCase(Locale.forLanguageTag("tr"));
        if (lower.contains("çamaşır")) return "çamaşır makinesinin";
        if (lower.contains("buzdolabı")) return "buzdolabının";
        if (lower.contains("çaydanlık")) return "çaydanlığın";
        if (lower.contains("fırın")) return "fırının";
        if (lower.contains("televizyon") || lower.contains("tv")) return "televizyonun";
        if (lower.contains("klima")) return "klimanın";
        if (lower.contains("mikrodalga")) return "mikrodalganın";
        if (lower.contains("lamba") || lower.contains("aydınlatma") || lower.contains("avize")) return "aydınlatma cihazının";
        if (lower.contains("bilgisayar")) return "bilgisayarın";
        return lower + " cihazının";
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

