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
                        r.getRecommendationText(), r.getModelName(), r.isFallbackUsed(), r.getCreatedAt())));
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
