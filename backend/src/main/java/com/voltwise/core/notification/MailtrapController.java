package com.voltwise.core.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voltwise.core.persistence.entity.NotificationEntity;
import com.voltwise.core.persistence.entity.RecommendationEntity;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
public class MailtrapController {
    private static final Logger log = LoggerFactory.getLogger(MailtrapController.class);
    private final RestClient restClient;
    private final NotificationRepository notificationRepository;
    private final AnomalyEventRepository anomalyEventRepository;
    private final ObjectMapper objectMapper;
    private final String accountId;
    private final String inboxId;
    private final String apiToken;

    public MailtrapController(
            NotificationRepository notificationRepository,
            AnomalyEventRepository anomalyEventRepository,
            ObjectMapper objectMapper,
            @Value("${voltwise.mailtrap.account-id:2796288}") String accountId,
            @Value("${voltwise.mailtrap.inbox-id:4813707}") String inboxId,
            @Value("${voltwise.mailtrap.api-token:abde7b41726d706c55734888ca165fb4}") String apiToken) {
        this.notificationRepository = notificationRepository;
        this.anomalyEventRepository = anomalyEventRepository;
        this.objectMapper = objectMapper;
        this.accountId = accountId;
        this.inboxId = inboxId;
        this.apiToken = apiToken;
        this.restClient = RestClient.builder()
                .baseUrl("https://mailtrap.io")
                .build();
    }

    @Transactional(readOnly = true)
    @GetMapping({"/api/notifications/inbox", "/api/v1/notifications/inbox", "/api/v1/mailtrap/messages"})
    public List<Map<String, Object>> getMessages() {
        List<Map<String, Object>> resultList = new ArrayList<>();
        String currentEmail = com.voltwise.core.auth.UserContext.getCurrentUserEmail();

        if (currentEmail == null || currentEmail.isBlank()) {
            currentEmail = "onur@gmail.com";
        }

        try {
            var dbNotifications = notificationRepository.findByRecipientIgnoreCaseOrderByIdDesc(currentEmail);
            var limitedNotifications = !dbNotifications.isEmpty()
                    ? dbNotifications.stream().limit(20).toList()
                    : notificationRepository.findAllByOrderByCreatedAtDesc().stream().limit(20).toList();

            for (var n : limitedNotifications) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", n.getId());
                map.put("subject", enrichSubject(n));
                map.put("to_email", n.getRecipient() != null ? n.getRecipient() : currentEmail);
                map.put("to_name", "VoltFlow Kullanıcısı");
                map.put("created_at", n.getCreatedAt() != null ? n.getCreatedAt().toString() : java.time.Instant.now().toString());
                resultList.add(map);
            }
        } catch (Exception ex) {
            log.error("Error fetching DB notifications in MailtrapController: {}", ex.getMessage(), ex);
        }

        return resultList;
    }

    @Transactional(readOnly = true)
    @GetMapping({"/api/notifications/inbox/{messageId}/body", "/api/v1/notifications/inbox/{messageId}/body", "/api/v1/mailtrap/messages/{messageId}/body"})
    public String getMessageBody(@PathVariable Long messageId) {
        // Try DB notification first
        try {
            var opt = notificationRepository.findById(messageId);
            if (opt.isPresent()) {
                NotificationEntity n = opt.get();
                if (n.getRecommendation() != null) {
                    var rec = n.getRecommendation();
                    if (rec.getTriggerType() == com.voltwise.core.domain.TriggerType.APPLIANCE_ANOMALY && rec.getTriggerReferenceId() != null) {
                        String enriched = enrichBody(rec);
                        if (enriched != null) return cleanMarkdown(enriched);
                    }
                    if (StringUtils.hasText(rec.getRecommendationText())) {
                        return cleanMarkdown(rec.getRecommendationText());
                    }
                }
                return "VoltFlow Güvenlik Bildirimi: " + n.getSubject() + "\n\nCihazınızda olağan dışı tüketim veya bütçe eşik aşımı tespit edilmiştir. Lütfen cihazlarınızı ve güncel tüketim değerlerinizi kontrol ediniz.";
            }
        } catch (Exception ex) {
            log.warn("DB notification read exception for messageId={}: {}", messageId, ex.getMessage());
        }

        // Fallback to Mailtrap API
        try {
            String response = restClient.get()
                    .uri("/api/accounts/{accountId}/inboxes/{inboxId}/messages/{messageId}/body.txt", accountId, inboxId, messageId)
                    .header("Api-Token", apiToken)
                    .retrieve()
                    .body(String.class);
            return (response != null && !response.isBlank()) ? cleanMarkdown(response) : "Cihazınızda olağan dışı tüketim algılanmıştır. Lütfen cihazlarınızı kontrol ediniz.";
        } catch (Exception ex) {
            log.warn("Failed to fetch Mailtrap message body for id={}: {}", messageId, ex.getMessage());
            return "Cihazınızda olağan dışı tüketim veya bütçe eşik aşımı tespit edilmiştir. Lütfen VoltFlow paneli üzerinden güncel tüketim değerlerinizi kontrol ediniz.";
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

    private String enrichSubject(NotificationEntity n) {
        if (n.getRecommendation() != null && n.getRecommendation().getTriggerReferenceId() != null) {
            var anomalyOpt = anomalyEventRepository.findById(n.getRecommendation().getTriggerReferenceId());
            if (anomalyOpt.isPresent()) {
                var appliance = anomalyOpt.get().getAppliance();
                if (appliance != null && StringUtils.hasText(appliance.getName())) {
                    String applianceName = cleanApplianceName(appliance.getName());
                    return "VoltFlow: " + applianceName + " güvenli sınırı aştı";
                }
            }
        }
        return n.getSubject();
    }

    private String enrichBody(RecommendationEntity rec) {
        if (rec.getTriggerReferenceId() == null) return null;
        var anomalyOpt = anomalyEventRepository.findById(rec.getTriggerReferenceId());
        if (anomalyOpt.isEmpty()) return null;
        var entity = anomalyOpt.get();
        var appliance = entity.getAppliance();
        String rawName = appliance != null ? appliance.getName() : "Cihaz";
        String applianceName = cleanApplianceName(rawName);
        var type = appliance != null ? appliance.getType() : null;

        long measuredWatts = entity.getMeasuredPowerWatts() != null ? entity.getMeasuredPowerWatts().longValue() : 0;
        long safeLimitWatts = entity.getSafePowerLimitWatts() != null ? entity.getSafePowerLimitWatts().longValue() : 1;
        long excessDiff = Math.max(0, measuredWatts - safeLimitWatts);
        long excessPercent = safeLimitWatts > 0 ? Math.round((double) excessDiff / safeLimitWatts * 100) : 0;

        String advice = getApplianceAdvice(applianceName, type);
        String possessiveName = toPossessive(applianceName);

        return """
                Merhaba,

                VoltFlow AI sistemi, evinize kayıtlı %s güvenli güç sınırını aştığını tespit etti.

                📊 Anlık Tüketim: %dW (Güvenli sınır: %dW)
                ⚠️ Durum: Güvenli Watt sınırı %%%d aşıldı

                🔍 Öneri: %s

                Bu bildirim VoltFlow AI tarafından otomatik oluşturulmuştur.
                """.formatted(possessiveName, measuredWatts, safeLimitWatts, excessPercent, advice).strip();
    }

    private String getApplianceAdvice(String applianceName, com.voltwise.core.domain.ApplianceType type) {
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

    private static String cleanMarkdown(String input) {
        if (input == null) return "";
        return input.replaceAll("\\*\\*", "")
                    .replaceAll("\\*", "")
                    .replaceAll("`", "")
                    .replaceAll("#+\\s*", "")
                    .replaceAll("_", "")
                    .strip();
    }
}

