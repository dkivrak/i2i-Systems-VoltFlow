package com.voltwise.core.telemetry;

import com.voltwise.core.api.HomeDtos.ApplianceRequest;
import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.domain.AnomalyStatus;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.event.TelemetryEvent;
import com.voltwise.core.live.LiveStateStore;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.BillingLedgerRepository;
import com.voltwise.core.persistence.repository.ProcessedEventRepository;
import com.voltwise.core.persistence.repository.QuotaEventRepository;
import com.voltwise.core.persistence.repository.TariffChangeEventRepository;
import com.voltwise.core.registration.HomeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class TelemetryProcessingIntegrationTest {
    @Autowired HomeService homeService;
    @Autowired TelemetryProcessingService processor;
    @Autowired LiveStateStore liveStates;
    @Autowired AnomalyEventRepository anomalyEvents;
    @Autowired QuotaEventRepository quotaEvents;
    @Autowired TariffChangeEventRepository tariffEvents;
    @Autowired BillingLedgerRepository ledgers;
    @Autowired ProcessedEventRepository processedEvents;
    @Autowired PlatformTransactionManager transactionManager;

    @Test
    void thirdBreachCreatesOneAnomalyAndNormalReadingResolvesIt() {
        var home = createHome(new BigDecimal("100"), BigDecimal.ONE, new BigDecimal("100"));
        long applianceId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(10);
        processor.process(event(home.id(), applianceId, "101", start, UUID.randomUUID()));
        processor.process(event(home.id(), applianceId, "101", start.plusSeconds(1), UUID.randomUUID()));
        processor.process(event(home.id(), applianceId, "101", start.plusSeconds(2), UUID.randomUUID()));
        processor.process(event(home.id(), applianceId, "101", start.plusSeconds(3), UUID.randomUUID()));

        var live = liveStates.get(home.id()).orElseThrow().appliances().get(applianceId);
        assertThat(live.healthStatus()).isEqualTo(ApplianceHealthStatus.ANOMALOUS);
        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id())).hasSize(1);

        processor.process(new TelemetryEvent(UUID.randomUUID(), 1, "APPLIANCE_TELEMETRY_RECORDED",
                start.plusSeconds(4), home.id(), applianceId, ApplianceType.KETTLE,
                new BigDecimal("50"), OperatingState.ON));
        live = liveStates.get(home.id()).orElseThrow().appliances().get(applianceId);
        assertThat(live.healthStatus()).isEqualTo(ApplianceHealthStatus.NORMAL);
        assertThat(live.consecutiveBreachCount()).isZero();
        assertThat(anomalyEvents.findFirstByApplianceIdAndStatus(applianceId, AnomalyStatus.RESOLVED)).isPresent();
    }

    @Test
    void oneLargeDeltaCrossesThresholdsOnceAndSplitsPenaltyCost() {
        var home = createHome(new BigDecimal("0.5"), BigDecimal.ONE, new BigDecimal("10000000"));
        long applianceId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(10);
        processor.process(event(home.id(), applianceId, "0", start, UUID.randomUUID()));
        UUID billedId = UUID.randomUUID();
        processor.process(event(home.id(), applianceId, "3600000", start.plusSeconds(1), billedId));
        processor.process(event(home.id(), applianceId, "3600000", start.plusSeconds(1), billedId));

        var live = liveStates.get(home.id()).orElseThrow();
        assertThat(live.tariffState()).isEqualTo(TariffState.PENALTY);
        assertThat(live.currentCost()).isEqualByComparingTo("1.50");
        assertThat(quotaEvents.findByHomeIdOrderByOccurredAtDesc(home.id())).hasSize(2);
        assertThat(tariffEvents.findByHomeIdOrderByChangedAtDesc(home.id())).hasSize(1);
        assertThat(processedEvents.existsById(billedId)).isTrue();
    }

    @Test
    void staleTelemetryIsIdempotentlyRecordedWithoutOverwritingLiveReading() {
        var home = createHome(new BigDecimal("100"), BigDecimal.ONE, new BigDecimal("5000"));
        long applianceId = home.appliances().getFirst().id();
        Instant now = Instant.now().minusSeconds(2);
        processor.process(event(home.id(), applianceId, "100", now, UUID.randomUUID()));
        UUID staleId = UUID.randomUUID();
        processor.process(event(home.id(), applianceId, "999", now.minusSeconds(1), staleId));
        assertThat(liveStates.get(home.id()).orElseThrow().appliances().get(applianceId).currentPowerWatts())
                .isEqualByComparingTo("100");
        assertThat(processedEvents.existsById(staleId)).isTrue();
    }

    @Test
    void committedReadingsAccumulateHomeAndApplianceIntervalMetrics() {
        var home = createHome(new BigDecimal("100"), new BigDecimal("2"), new BigDecimal("5000"));
        long applianceId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(120);
        processor.process(event(home.id(), applianceId, "100", start, UUID.randomUUID()));
        processor.process(event(home.id(), applianceId, "300", start.plusSeconds(60), UUID.randomUUID()));

        var live = liveStates.get(home.id()).orElseThrow();
        var homeWindow = live.snapshotWindow();
        var applianceWindow = live.appliances().get(applianceId).snapshotWindow();
        assertThat(homeWindow.powerSampleCount()).isEqualTo(2);
        assertThat(homeWindow.powerSampleSumWatts()).isEqualByComparingTo("400");
        assertThat(homeWindow.averagePowerWatts()).isEqualByComparingTo("200");
        assertThat(homeWindow.maximumPowerWatts()).isEqualByComparingTo("300");
        assertThat(homeWindow.energyKwh()).isEqualByComparingTo("0.005");
        assertThat(homeWindow.cost()).isEqualByComparingTo("0.01");
        assertThat(applianceWindow).isEqualTo(homeWindow);
    }

    @Test
    void rollbackNeverAdvancesLiveStateAndRetryCanBillTheEvent() {
        var home = createHome(new BigDecimal("100"), BigDecimal.ONE, new BigDecimal("5000"));
        long applianceId = home.appliances().getFirst().id();
        var before = liveStates.get(home.id()).orElseThrow();
        UUID eventId = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            processor.process(event(home.id(), applianceId, "250", occurredAt, eventId));
            assertThat(liveStates.get(home.id()).orElseThrow()).isEqualTo(before);
            status.setRollbackOnly();
        });

        assertThat(liveStates.get(home.id()).orElseThrow()).isEqualTo(before);
        assertThat(processedEvents.existsById(eventId)).isFalse();

        processor.process(event(home.id(), applianceId, "250", occurredAt, eventId));
        assertThat(processedEvents.existsById(eventId)).isTrue();
        assertThat(liveStates.get(home.id()).orElseThrow().appliances().get(applianceId).currentPowerWatts())
                .isEqualByComparingTo("250");
    }

    private com.voltwise.core.api.HomeDtos.HomeResponse createHome(BigDecimal budget, BigDecimal tariff, BigDecimal safeLimit) {
        return homeService.create(new CreateHomeRequest("Test Home " + UUID.randomUUID(), "owner@example.com",
                budget, tariff, new BigDecimal("1.5"),
                List.of(new ApplianceRequest("Kettle", ApplianceType.KETTLE, safeLimit))));
    }

    private TelemetryEvent event(long homeId, long applianceId, String power, Instant at, UUID id) {
        return new TelemetryEvent(id, 1, "APPLIANCE_TELEMETRY_RECORDED", at,
                homeId, applianceId, ApplianceType.KETTLE, new BigDecimal(power), OperatingState.ON);
    }
}
