package com.voltwise.core.api;

import com.voltwise.core.api.HomeDtos.ApplianceStatusResponse;
import com.voltwise.core.api.HomeDtos.HomeEventResponse;
import com.voltwise.core.api.HomeDtos.HomeStatusResponse;
import com.voltwise.core.api.HomeDtos.PagedResponse;
import com.voltwise.core.api.HomeDtos.RecommendationResponse;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.live.LiveStateStore;
import com.voltwise.core.persistence.entity.AnomalyEventEntity;
import com.voltwise.core.persistence.entity.QuotaEventEntity;
import com.voltwise.core.persistence.entity.TariffChangeEventEntity;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.persistence.repository.QuotaEventRepository;
import com.voltwise.core.persistence.repository.RecommendationRepository;
import com.voltwise.core.persistence.repository.TariffChangeEventRepository;
import com.voltwise.core.auth.UserContext;
import com.voltwise.core.registration.HomeAccessDeniedException;
import com.voltwise.core.registration.ResourceNotFoundException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;

@Service
public class HomeQueryService {
    private final HomeRepository homes;
    private final LiveStateInitializer initializer;
    private final LiveStateStore liveStates;
    private final QuotaEventRepository quotaEvents;
    private final AnomalyEventRepository anomalyEvents;
    private final TariffChangeEventRepository tariffEvents;
    private final RecommendationRepository recommendations;

    public HomeQueryService(HomeRepository homes, LiveStateInitializer initializer, LiveStateStore liveStates,
                            QuotaEventRepository quotaEvents, AnomalyEventRepository anomalyEvents,
                            TariffChangeEventRepository tariffEvents, RecommendationRepository recommendations) {
        this.homes = homes;
        this.initializer = initializer;
        this.liveStates = liveStates;
        this.quotaEvents = quotaEvents;
        this.anomalyEvents = anomalyEvents;
        this.tariffEvents = tariffEvents;
        this.recommendations = recommendations;
    }

    public PagedResponse<HomeStatusResponse> statuses(int page, int size) {
        String ownerEmail = UserContext.getCurrentUserEmail();
        var ownedHomeIds = ownerEmail == null
                ? null
                : new java.util.HashSet<>(homes.findIdsByOwnerEmail(ownerEmail));
        var result = liveStates.getAll().stream()
                .filter(state -> ownedHomeIds == null || ownedHomeIds.contains(state.homeId()))
                .sorted(Comparator.comparing(HomeLiveState::homeId))
                .map(this::map)
                .toList();
        return PagedResponse.slice(result, page, size);
    }

    @Transactional(readOnly = true)
    public HomeStatusResponse status(Long homeId) {
        requireOwned(homeId);
        return map(initializer.ensure(homeId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<HomeEventResponse> events(Long homeId, int page, int size) {
        requireOwned(homeId);
        var result = new ArrayList<HomeEventResponse>();
        quotaEvents.findByHomeIdOrderByOccurredAtDesc(homeId).forEach(e -> result.add(map(e)));
        anomalyEvents.findByHomeIdOrderByDetectedAtDesc(homeId).forEach(e -> result.add(map(e)));
        tariffEvents.findByHomeIdOrderByChangedAtDesc(homeId).forEach(e -> result.add(map(e)));
        result.sort(Comparator.comparing(HomeEventResponse::occurredAt).reversed());
        return PagedResponse.slice(result, page, size);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RecommendationResponse> recommendations(Long homeId, int page, int size) {
        requireOwned(homeId);
        return PagedResponse.of(recommendations.findByHomeIdOrderByCreatedAtDesc(homeId, PageRequest.of(page, size))
                .map(r -> new RecommendationResponse(r.getId(), r.getTriggerType(), r.getTriggerReferenceId(),
                        cleanRecommendationText(r), r.getModelName(), r.isFallbackUsed(), r.getCreatedAt())));
    }

    private String cleanRecommendationText(com.voltwise.core.persistence.entity.RecommendationEntity r) {
        if (r.getTriggerType() == com.voltwise.core.domain.TriggerType.APPLIANCE_ANOMALY && r.getTriggerReferenceId() != null) {
            var anomalyOpt = anomalyEvents.findById(r.getTriggerReferenceId());
            if (anomalyOpt.isPresent()) {
                var appliance = anomalyOpt.get().getAppliance();
                if (appliance != null) {
                    String devName = cleanApplianceName(appliance.getName());
                    return devName + ": " + getApplianceAdvice(devName, appliance.getType());
                }
            }
        }

        String text = r.getRecommendationText();
        if (text == null || text.isBlank()) return "Cihaz: Tüketim değerlerini ve elektrik bağlantılarını düzenli olarak kontrol ediniz.";

        String devName = extractDeviceNameFromText(text);

        int idx = text.indexOf("Öneri:");
        if (idx != -1) {
            String sub = text.substring(idx + "Öneri:".length()).trim();
            int endIdx = sub.indexOf("\n\n");
            if (endIdx != -1) sub = sub.substring(0, endIdx).trim();
            int endIdx2 = sub.indexOf("Bu bildirim");
            if (endIdx2 != -1) sub = sub.substring(0, endIdx2).trim();
            if (!sub.isBlank()) {
                return (devName != null ? devName + ": " : "") + sub;
            }
        }

        text = text.replaceAll("(?i)^Merhaba,\\s*", "")
                   .replaceAll("(?i)VoltFlow AI sistemi[^\n]*\n?", "")
                   .replaceAll("📊[^\n]*\n?", "")
                   .replaceAll("⚠️[^\n]*\n?", "")
                   .replaceAll("(?i)Bu bildirim VoltFlow[^\n]*", "")
                   .trim();

        if (text.contains("Enerji kullanımınız tanımlanan sınıra ulaşmış") || text.contains("olağan dışı tüketim")) {
            return (devName != null ? devName + ": " : "") + "Yüksek güçlü cihazlarınızı aynı hatta çalıştırmaktan kaçının ve düzenli filtre/bakım kontrollerini gerçekleştirin.";
        }

        return (devName != null && !text.startsWith(devName) ? devName + ": " : "") + text;
    }

    private String extractDeviceNameFromText(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(java.util.Locale.forLanguageTag("tr"));
        if (lower.contains("çamaşır")) return "Çamaşır makinesi";
        if (lower.contains("buzdolab")) return "Buzdolabı";
        if (lower.contains("çaydanlık") || lower.contains("kettle") || lower.contains("ısıtıcı")) return "Çaydanlık";
        if (lower.contains("fırın")) return "Fırın";
        if (lower.contains("televizyon") || lower.contains("tv")) return "Televizyon";
        if (lower.contains("klima")) return "Klima";
        if (lower.contains("mikrodalga")) return "Mikrodalga";
        if (lower.contains("lamba") || lower.contains("aydınlatma") || lower.contains("avize")) return "Aydınlatma";
        if (lower.contains("bilgisayar")) return "Bilgisayar";
        return null;
    }

    private String cleanApplianceName(String rawName) {
        if (rawName == null || rawName.isBlank()) return "Cihaz";
        String cleaned = rawName.replaceAll("\\s*\\([^)]*\\)", "").trim();
        cleaned = cleaned.replaceAll("(?i)\\b(mutfak|salon|ofis)\\b\\s*", "").trim();
        if (cleaned.equalsIgnoreCase("lambası") || cleaned.equalsIgnoreCase("lamba")) return "Lamba";
        if (cleaned.isBlank()) return rawName;
        return cleaned.substring(0, 1).toUpperCase(java.util.Locale.forLanguageTag("tr")) + cleaned.substring(1);
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
        String lower = applianceName != null ? applianceName.toLowerCase(java.util.Locale.forLanguageTag("tr")) : "";
        if (lower.contains("çamaşır") || lower.contains("washer")) {
            return "Makinenin aşırı yüklenip yüklenmediğini kontrol edin. Su giriş vanasını gözden geçirin.";
        }
        return "Cihazın aşırı yüklenip yüklenmediğini kontrol edin ve elektrik bağlantılarını gözden geçirin.";
    }

    private HomeStatusResponse map(HomeLiveState state) {
        var applianceStatuses = state.appliances().values().stream()
                .sorted(Comparator.comparing(com.voltwise.core.live.ApplianceLiveState::applianceId))
                .map(a -> new ApplianceStatusResponse(
                a.applianceId(), a.name(), a.type(), a.currentPowerWatts(), a.accumulatedEnergyKwh(),
                a.accumulatedCost(), a.operatingState(), a.safePowerLimitWatts(), a.consecutiveBreachCount(),
                a.healthStatus(), a.lastUpdatedAt())).toList();
        int anomalyCount = (int) applianceStatuses.stream()
                .filter(a -> a.healthStatus() == ApplianceHealthStatus.ANOMALOUS).count();
        return new HomeStatusResponse(state.homeId(), state.homeName(), "İstanbul", state.currentPowerWatts(),
                state.accumulatedEnergyKwh(), state.currentCost(), state.monthlyBudget(),
                state.budgetUsagePercent(), state.tariffState(), anomalyCount,
                state.lastUpdatedAt(), applianceStatuses);
    }

    private void requireOwned(Long homeId) {
        String ownerEmail = UserContext.getCurrentUserEmail();
        if (ownerEmail == null) {
            if (!homes.existsById(homeId)) {
                throw new ResourceNotFoundException("Home not found: " + homeId);
            }
            return;
        }
        var home = homes.findById(homeId)
                .orElseThrow(() -> new ResourceNotFoundException("Home not found: " + homeId));
        if (!home.getOwnerEmail().equalsIgnoreCase(ownerEmail)) {
            throw new HomeAccessDeniedException();
        }
    }

    private HomeEventResponse map(QuotaEventEntity e) {
        return new HomeEventResponse("QUOTA_EVENT", e.getId(), e.getOccurredAt(), e.getThreshold().name(),
                e.getUsagePercent(), null, null, "Maliyet %s / bütçe %s".formatted(e.getCurrentCost(), e.getMonthlyBudget()));
    }
    private HomeEventResponse map(AnomalyEventEntity e) {
        return new HomeEventResponse("ANOMALY_EVENT", e.getId(), e.getDetectedAt(), e.getStatus().name(),
                null, e.getMeasuredPowerWatts(), e.getAppliance().getId(),
                "Güvenli limit %s W; ardışık ihlal %d".formatted(e.getSafePowerLimitWatts(), e.getConsecutiveBreachCount()));
    }
    private HomeEventResponse map(TariffChangeEventEntity e) {
        return new HomeEventResponse("TARIFF_CHANGE_EVENT", e.getId(), e.getChangedAt(), e.getNewTariff().name(),
                e.getTriggerUsagePercent(), null, null,
                "Birim fiyat %s değerinden %s değerine değişti".formatted(e.getPreviousRate(), e.getNewRate()));
    }
}
