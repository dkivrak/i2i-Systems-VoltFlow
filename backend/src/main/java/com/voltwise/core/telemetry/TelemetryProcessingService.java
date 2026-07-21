package com.voltwise.core.telemetry;

import com.voltwise.core.config.VoltWiseProperties;
import com.voltwise.core.domain.AnomalyStatus;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.QuotaThreshold;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.domain.TriggerType;
import com.voltwise.core.event.TelemetryEvent;
import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.LiveStateInitializer;
import com.voltwise.core.notification.DomainNotificationRequest;
import com.voltwise.core.persistence.entity.AnomalyEventEntity;
import com.voltwise.core.persistence.entity.ApplianceEntity;
import com.voltwise.core.persistence.entity.BillingLedgerEntity;
import com.voltwise.core.persistence.entity.ProcessedEventEntity;
import com.voltwise.core.persistence.entity.QuotaEventEntity;
import com.voltwise.core.persistence.entity.TariffChangeEventEntity;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.ApplianceRepository;
import com.voltwise.core.persistence.repository.BillingLedgerRepository;
import com.voltwise.core.persistence.repository.ProcessedEventRepository;
import com.voltwise.core.persistence.repository.QuotaEventRepository;
import com.voltwise.core.persistence.repository.TariffChangeEventRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class TelemetryProcessingService {
    private static final String TELEMETRY_EVENT_TYPE = "APPLIANCE_TELEMETRY_RECORDED";
    private final ProcessedEventRepository processedEvents;
    private final ApplianceRepository appliances;
    private final BillingLedgerRepository ledgers;
    private final QuotaEventRepository quotaEvents;
    private final AnomalyEventRepository anomalyEvents;
    private final TariffChangeEventRepository tariffEvents;
    private final LiveStateInitializer initializer;
    private final EnergyCalculator energyCalculator;
    private final BillingCalculator billingCalculator;
    private final QuotaRule quotaRule;
    private final AnomalyRule anomalyRule;
    private final VoltWiseProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public TelemetryProcessingService(ProcessedEventRepository processedEvents, ApplianceRepository appliances,
            BillingLedgerRepository ledgers, QuotaEventRepository quotaEvents,
            AnomalyEventRepository anomalyEvents, TariffChangeEventRepository tariffEvents,
            LiveStateInitializer initializer,
            EnergyCalculator energyCalculator, BillingCalculator billingCalculator,
            QuotaRule quotaRule, AnomalyRule anomalyRule, VoltWiseProperties properties,
            ApplicationEventPublisher eventPublisher) {
        this.processedEvents = processedEvents;
        this.appliances = appliances;
        this.ledgers = ledgers;
        this.quotaEvents = quotaEvents;
        this.anomalyEvents = anomalyEvents;
        this.tariffEvents = tariffEvents;
        this.initializer = initializer;
        this.energyCalculator = energyCalculator;
        this.billingCalculator = billingCalculator;
        this.quotaRule = quotaRule;
        this.anomalyRule = anomalyRule;
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void process(TelemetryEvent event) {
        validate(event);
        if (processedEvents.existsById(event.eventId())) return;

        ApplianceEntity appliance = appliances.findById(event.applianceId())
                .orElseThrow(() -> new IllegalArgumentException("Unknown appliance: " + event.applianceId()));
        if (!appliance.getHome().getId().equals(event.homeId()) || appliance.getType() != event.applianceType()) {
            throw new IllegalArgumentException("Telemetry asset identity does not match registered appliance");
        }

        HomeLiveState previousHome = initializer.ensure(event.homeId());
        ApplianceLiveState previousAppliance = previousHome.appliances().get(event.applianceId());
        if (previousAppliance == null) throw new IllegalArgumentException("Appliance is not initialized in live state");
        if (previousAppliance.lastUpdatedAt() != null && !event.occurredAt().isAfter(previousAppliance.lastUpdatedAt())) {
            markProcessed(event);
            return;
        }

        BigDecimal energyDelta = energyCalculator.calculateDeltaKwh(event.powerWatts(),
                previousAppliance.lastUpdatedAt(), event.occurredAt(),
                properties.getBilling().getMaximumTelemetryGapSeconds());
        LocalDate period = event.occurredAt().atZone(ZoneOffset.UTC).toLocalDate()
                .with(TemporalAdjusters.firstDayOfMonth());
        BillingLedgerEntity ledger = ledgers.findForUpdate(event.homeId(), period)
                .orElseGet(() -> newLedger(appliance, period));
        var billing = billingCalculator.apply(ledger.getAccumulatedEnergyKwh(), ledger.getAccumulatedCost(),
                ledger.getTariffState(), energyDelta, appliance.getHome().getMonthlyBudget(),
                appliance.getHome().getNormalTariffPerKwh(), appliance.getHome().getPenaltyMultiplier());
        ledger.setAccumulatedEnergyKwh(billing.accumulatedEnergyKwh());
        ledger.setAccumulatedCost(billing.accumulatedCost());
        ledger.setTariffState(billing.tariffState());
        ledgers.save(ledger);

        List<DomainNotificationRequest> notifications = new ArrayList<>(4);
        persistQuotaTransitions(appliance, period, event.occurredAt(), billing, notifications);
        persistTariffTransition(appliance, period, event.occurredAt(), billing, notifications);

        AnomalyRule.Outcome anomaly = anomalyRule.evaluate(event.powerWatts(), appliance.getSafePowerLimitWatts(),
                previousAppliance.consecutiveBreachCount(), previousAppliance.healthStatus());
        handleAnomaly(appliance, event, anomaly, notifications);
        HomeLiveState committedState = updatedLiveState(previousHome, previousAppliance, appliance,
                event, energyDelta, billing, anomaly);
        eventPublisher.publishEvent(new TelemetryCommitAction(event.homeId(), previousHome,
                committedState, appliance.getId(), event.occurredAt(), energyDelta, billing.costDelta(),
                notifications));
        markProcessed(event);
    }

    private void persistQuotaTransitions(ApplianceEntity appliance, LocalDate period, Instant occurredAt,
                                         BillingCalculator.BillingResult billing,
                                         List<DomainNotificationRequest> notifications) {
        for (QuotaThreshold threshold : quotaRule.crossedThresholds(
                billing.previousUsagePercent(), billing.usagePercent())) {
            if (quotaEvents.existsByHomeIdAndBillingPeriodAndThreshold(appliance.getHome().getId(), period, threshold)) continue;
            QuotaEventEntity entity = new QuotaEventEntity();
            entity.setHome(appliance.getHome());
            entity.setBillingPeriod(period);
            entity.setThreshold(threshold);
            entity.setUsagePercent(billing.usagePercent());
            entity.setCurrentCost(billing.accumulatedCost());
            entity.setMonthlyBudget(appliance.getHome().getMonthlyBudget());
            entity.setOccurredAt(occurredAt);
            entity = quotaEvents.save(entity);
            TriggerType trigger = threshold == QuotaThreshold.EIGHTY_PERCENT ? TriggerType.QUOTA_80 : TriggerType.QUOTA_100;
            notifications.add(new DomainNotificationRequest(appliance.getHome().getId(), trigger, entity.getId()));
        }
    }

    private void persistTariffTransition(ApplianceEntity appliance, LocalDate period, Instant occurredAt,
                                         BillingCalculator.BillingResult billing,
                                         List<DomainNotificationRequest> notifications) {
        if (!billing.tariffTransitioned() || tariffEvents.existsByHomeIdAndBillingPeriodAndNewTariff(
                appliance.getHome().getId(), period, TariffState.PENALTY)) return;
        TariffChangeEventEntity entity = new TariffChangeEventEntity();
        entity.setHome(appliance.getHome());
        entity.setBillingPeriod(period);
        entity.setPreviousTariff(TariffState.NORMAL);
        entity.setNewTariff(TariffState.PENALTY);
        entity.setPreviousRate(billing.normalRate());
        entity.setNewRate(billing.penaltyRate());
        entity.setTriggerUsagePercent(billing.usagePercent());
        entity.setChangedAt(occurredAt);
        entity = tariffEvents.save(entity);
        notifications.add(new DomainNotificationRequest(appliance.getHome().getId(),
                TriggerType.TARIFF_ACTIVATED, entity.getId()));
    }

    private void handleAnomaly(ApplianceEntity appliance, TelemetryEvent event, AnomalyRule.Outcome outcome,
                               List<DomainNotificationRequest> notifications) {
        if (outcome.anomalyDetected()) {
            if (anomalyEvents.findFirstByApplianceIdAndStatus(appliance.getId(), AnomalyStatus.ACTIVE).isEmpty()) {
                AnomalyEventEntity entity = new AnomalyEventEntity();
                entity.setHome(appliance.getHome());
                entity.setAppliance(appliance);
                entity.setMeasuredPowerWatts(event.powerWatts());
                entity.setSafePowerLimitWatts(appliance.getSafePowerLimitWatts());
                entity.setConsecutiveBreachCount(outcome.breachCount());
                entity.setStatus(AnomalyStatus.ACTIVE);
                entity.setDetectedAt(event.occurredAt());
                entity = anomalyEvents.save(entity);
                notifications.add(new DomainNotificationRequest(appliance.getHome().getId(),
                        TriggerType.APPLIANCE_ANOMALY, entity.getId()));
            }
        } else if (outcome.anomalyResolved()) {
            anomalyEvents.findFirstByApplianceIdAndStatus(appliance.getId(), AnomalyStatus.ACTIVE).ifPresent(active -> {
                active.setStatus(AnomalyStatus.RESOLVED);
                active.setResolvedAt(event.occurredAt());
                anomalyEvents.save(active);
            });
        }
    }

    private HomeLiveState updatedLiveState(HomeLiveState previousHome, ApplianceLiveState oldAppliance,
                                           ApplianceEntity appliance, TelemetryEvent event, BigDecimal energyDelta,
                                           BillingCalculator.BillingResult billing, AnomalyRule.Outcome anomaly) {
        BigDecimal applianceCost = oldAppliance.accumulatedCost().add(billing.costDelta());
        ApplianceLiveState updatedAppliance = new ApplianceLiveState(appliance.getId(), appliance.getName(),
                appliance.getType(), event.powerWatts(), oldAppliance.accumulatedEnergyKwh().add(energyDelta),
                applianceCost, event.operatingState(), appliance.getSafePowerLimitWatts(), anomaly.breachCount(),
                anomaly.healthStatus(), event.occurredAt(), oldAppliance.snapshotWindow().add(
                        event.occurredAt(), energyDelta, billing.costDelta(), event.powerWatts()));
        var updatedAppliances = new LinkedHashMap<>(previousHome.appliances());
        updatedAppliances.put(appliance.getId(), updatedAppliance);
        BigDecimal currentPower = updatedAppliances.values().stream()
                .map(ApplianceLiveState::currentPowerWatts).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new HomeLiveState(previousHome.homeId(), previousHome.homeName(), currentPower,
                billing.accumulatedEnergyKwh(), billing.accumulatedCost(), previousHome.monthlyBudget(),
                billing.usagePercent(), billing.tariffState(), event.occurredAt(), updatedAppliances,
                previousHome.snapshotWindow().add(
                        event.occurredAt(), energyDelta, billing.costDelta(), currentPower));
    }

    private BillingLedgerEntity newLedger(ApplianceEntity appliance, LocalDate period) {
        BillingLedgerEntity ledger = new BillingLedgerEntity();
        ledger.setHome(appliance.getHome());
        ledger.setBillingPeriod(period);
        ledger.setAccumulatedEnergyKwh(BigDecimal.ZERO);
        ledger.setAccumulatedCost(BigDecimal.ZERO);
        ledger.setTariffState(TariffState.NORMAL);
        return ledger;
    }

    private void markProcessed(TelemetryEvent event) {
        processedEvents.save(new ProcessedEventEntity(event.eventId(), event.eventType(), Instant.now()));
    }

    private void validate(TelemetryEvent event) {
        if (event == null || event.eventId() == null || event.occurredAt() == null || event.homeId() == null
                || event.applianceId() == null || event.applianceType() == null || event.operatingState() == null
                || event.powerWatts() == null) throw new IllegalArgumentException("Telemetry envelope is incomplete");
        if (event.eventVersion() != 1) throw new IllegalArgumentException("Unsupported telemetry eventVersion");
        if (!TELEMETRY_EVENT_TYPE.equals(event.eventType())) throw new IllegalArgumentException("Unsupported telemetry eventType");
        if (event.powerWatts().signum() < 0) throw new IllegalArgumentException("powerWatts cannot be negative");
    }
}
