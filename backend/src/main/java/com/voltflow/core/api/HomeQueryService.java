package com.voltflow.core.api;

import com.voltflow.core.api.HomeDtos.ApplianceStatusResponse;
import com.voltflow.core.api.HomeDtos.HomeEventResponse;
import com.voltflow.core.api.HomeDtos.HomeStatusResponse;
import com.voltflow.core.api.HomeDtos.PagedResponse;
import com.voltflow.core.api.HomeDtos.RecommendationResponse;
import com.voltflow.core.domain.ApplianceHealthStatus;
import com.voltflow.core.live.HomeLiveState;
import com.voltflow.core.live.LiveStateInitializer;
import com.voltflow.core.live.LiveStateStore;
import com.voltflow.core.persistence.entity.AnomalyEventEntity;
import com.voltflow.core.persistence.entity.QuotaEventEntity;
import com.voltflow.core.persistence.entity.TariffChangeEventEntity;
import com.voltflow.core.persistence.repository.AnomalyEventRepository;
import com.voltflow.core.persistence.repository.HomeRepository;
import com.voltflow.core.persistence.repository.QuotaEventRepository;
import com.voltflow.core.persistence.repository.RecommendationRepository;
import com.voltflow.core.persistence.repository.TariffChangeEventRepository;
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
        var result = liveStates.getAll().stream()
                .sorted(Comparator.comparing(HomeLiveState::homeId))
                .map(this::map)
                .toList();
        return PagedResponse.slice(result, page, size);
    }

    @Transactional(readOnly = true)
    public HomeStatusResponse status(Long homeId) {
        return map(initializer.ensure(homeId));
    }

    @Transactional(readOnly = true)
    public PagedResponse<HomeEventResponse> events(Long homeId, int page, int size) {
        if (!homes.existsById(homeId)) throw new com.voltflow.core.registration.ResourceNotFoundException("Home not found: " + homeId);
        var result = new ArrayList<HomeEventResponse>();
        quotaEvents.findByHomeIdOrderByOccurredAtDesc(homeId).forEach(e -> result.add(map(e)));
        anomalyEvents.findByHomeIdOrderByDetectedAtDesc(homeId).forEach(e -> result.add(map(e)));
        tariffEvents.findByHomeIdOrderByChangedAtDesc(homeId).forEach(e -> result.add(map(e)));
        result.sort(Comparator.comparing(HomeEventResponse::occurredAt).reversed());
        return PagedResponse.slice(result, page, size);
    }

    @Transactional(readOnly = true)
    public PagedResponse<RecommendationResponse> recommendations(Long homeId, int page, int size) {
        if (!homes.existsById(homeId)) throw new com.voltflow.core.registration.ResourceNotFoundException("Home not found: " + homeId);
        return PagedResponse.of(recommendations.findByHomeIdOrderByCreatedAtDesc(homeId, PageRequest.of(page, size))
                .map(r -> new RecommendationResponse(r.getId(), r.getTriggerType(), r.getTriggerReferenceId(),
                        r.getRecommendationText(), r.getModelName(), r.isFallbackUsed(), r.getCreatedAt())));
    }

    private HomeStatusResponse map(HomeLiveState state) {
        var applianceStatuses = state.appliances().values().stream()
                .sorted(Comparator.comparing(com.voltflow.core.live.ApplianceLiveState::applianceId))
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

    private HomeEventResponse map(QuotaEventEntity e) {
        return new HomeEventResponse("QUOTA_EVENT", e.getId(), e.getOccurredAt(), e.getThreshold().name(),
                e.getUsagePercent(), null, null, "Cost %s / budget %s".formatted(e.getCurrentCost(), e.getMonthlyBudget()));
    }
    private HomeEventResponse map(AnomalyEventEntity e) {
        return new HomeEventResponse("ANOMALY_EVENT", e.getId(), e.getDetectedAt(), e.getStatus().name(),
                null, e.getMeasuredPowerWatts(), e.getAppliance().getId(),
                "Safe limit %s W; consecutive breaches %d".formatted(e.getSafePowerLimitWatts(), e.getConsecutiveBreachCount()));
    }
    private HomeEventResponse map(TariffChangeEventEntity e) {
        return new HomeEventResponse("TARIFF_CHANGE_EVENT", e.getId(), e.getChangedAt(), e.getNewTariff().name(),
                e.getTriggerUsagePercent(), null, null,
                "Rate changed from %s to %s".formatted(e.getPreviousRate(), e.getNewRate()));
    }
}
