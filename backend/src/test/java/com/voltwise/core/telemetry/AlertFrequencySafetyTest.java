package com.voltwise.core.telemetry;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.core.api.HomeDtos.ApplianceRequest;
import com.voltwise.core.api.HomeDtos.CreateHomeRequest;
import com.voltwise.core.domain.AnomalyStatus;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.QuotaThreshold;
import com.voltwise.core.event.TelemetryEvent;
import com.voltwise.core.live.LiveStateStore;
import com.voltwise.core.persistence.repository.AnomalyEventRepository;
import com.voltwise.core.persistence.repository.QuotaEventRepository;
import com.voltwise.core.registration.HomeService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Deterministic end-to-end tests for alert-frequency safety.
 *
 * <p>Verifies that quota and anomaly alerts are emitted exactly once per
 * threshold crossing / state transition and never repeated while the system
 * remains in the same state.
 */
@SpringBootTest
@ActiveProfiles("test")
class AlertFrequencySafetyTest {

    @Autowired HomeService homeService;
    @Autowired TelemetryProcessingService processor;
    @Autowired LiveStateStore liveStates;
    @Autowired QuotaEventRepository quotaEvents;
    @Autowired AnomalyEventRepository anomalyEvents;

    // ─── Quota alert idempotency ────────────────────────────────────────────

    @Test
    void crossingEightyPercentSendsExactlyOneQuotaAlert() {
        // Budget = 1 TL/kWh so 1 Wh of energy costs 0.001 TL.
        // Safe limit = 10 000 W so anomaly detection never fires.
        var home = createHome("1.00", "1.0", "1.0", "10000");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(300);

        // Push usage to ~79 % (below 80 % threshold): 0 + 79 = 79 TL of 100 TL budget
        // 79 TL at 1 TL/kWh = 79 kWh. At 1 s intervals: power = 79 * 3600 * 1000 W for 1 s.
        // Simpler: send one reading at 0 W (anchor), then a large reading that crosses 80 %.
        // Baseline reading to set lastUpdatedAt:
        processor.process(event(home.id(), appId, "0", start, UUID.randomUUID()));

        // This reading carries enough energy to move from 0 % → ~81 % usage in one event.
        // Budget = 100 TL, rate = 1 TL/kWh.
        // 81 kWh * 1 TL = 81 TL = 81 % of 100 TL budget.
        // 81 kWh in 1 second = 81 * 3600 * 1000 W = 291,600,000 W — extreme but valid for test.
        // Use smaller numbers: budget=1 TL, rate=1 TL/kWh, energy cross target is 0.80 kWh.
        // Achieved by: budget=1.00, rate=1.0 TL/kWh, power=2880000 W for 1 s = 0.8 kWh.
        processor.process(event(home.id(), appId, "2880000", start.plusSeconds(1), UUID.randomUUID()));

        var events80 = quotaEvents.findByHomeIdOrderByOccurredAtDesc(home.id())
                .stream().filter(e -> e.getThreshold() == QuotaThreshold.EIGHTY_PERCENT).toList();
        assertThat(events80).as("exactly one EIGHTY_PERCENT quota event").hasSize(1);
    }

    @Test
    void remainingAboveEightyPercentDoesNotRepeatAlert() {
        var home = createHome("1.00", "1.0", "1.0", "10000");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(300);

        // Anchor
        processor.process(event(home.id(), appId, "0", start, UUID.randomUUID()));
        // Cross 80 %
        processor.process(event(home.id(), appId, "2880000", start.plusSeconds(1), UUID.randomUUID()));
        // More energy at 90 % — must not add another 80 % alert
        processor.process(event(home.id(), appId, "360000", start.plusSeconds(2), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "360000", start.plusSeconds(3), UUID.randomUUID()));

        var events80 = quotaEvents.findByHomeIdOrderByOccurredAtDesc(home.id())
                .stream().filter(e -> e.getThreshold() == QuotaThreshold.EIGHTY_PERCENT).toList();
        assertThat(events80).as("still exactly one EIGHTY_PERCENT quota event after staying above 80 %").hasSize(1);
    }

    @Test
    void crossingOneHundredPercentSendsExactlyOneQuotaAlert() {
        var home = createHome("1.00", "1.0", "1.0", "10000");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(300);

        processor.process(event(home.id(), appId, "0", start, UUID.randomUUID()));
        // Cross both 80 % and 100 % in a single event
        processor.process(event(home.id(), appId, "3600000", start.plusSeconds(1), UUID.randomUUID()));

        var events100 = quotaEvents.findByHomeIdOrderByOccurredAtDesc(home.id())
                .stream().filter(e -> e.getThreshold() == QuotaThreshold.ONE_HUNDRED_PERCENT).toList();
        assertThat(events100).as("exactly one ONE_HUNDRED_PERCENT quota event").hasSize(1);
    }

    @Test
    void remainingAboveOneHundredPercentDoesNotRepeatAlert() {
        var home = createHome("1.00", "1.0", "1.0", "10000");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(300);

        processor.process(event(home.id(), appId, "0", start, UUID.randomUUID()));
        processor.process(event(home.id(), appId, "3600000", start.plusSeconds(1), UUID.randomUUID()));
        // Continued consumption over budget — no additional 100 % event
        processor.process(event(home.id(), appId, "360000", start.plusSeconds(2), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "360000", start.plusSeconds(3), UUID.randomUUID()));

        var events100 = quotaEvents.findByHomeIdOrderByOccurredAtDesc(home.id())
                .stream().filter(e -> e.getThreshold() == QuotaThreshold.ONE_HUNDRED_PERCENT).toList();
        assertThat(events100).as("still exactly one ONE_HUNDRED_PERCENT quota event after staying above 100 %").hasSize(1);
    }

    // ─── Anomaly alert idempotency ──────────────────────────────────────────

    @Test
    void exactlyThreeConsecutiveBreachesSendOneAnomalyAlert() {
        var home = createHome("1000.00", "1.0", "1.5", "100");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(10);

        // Two breaches: not yet anomalous
        processor.process(event(home.id(), appId, "101", start, UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(1), UUID.randomUUID()));
        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id())).as("no alert yet after 2 breaches").isEmpty();

        // Third breach: anomaly detected
        processor.process(event(home.id(), appId, "101", start.plusSeconds(2), UUID.randomUUID()));
        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id())).as("one alert after 3rd breach").hasSize(1);
    }

    @Test
    void continuedBreachCyclesDoNotResendSameAnomalyAlert() {
        var home = createHome("1000.00", "1.0", "1.5", "100");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(20);

        // Trigger anomaly
        processor.process(event(home.id(), appId, "101", start, UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(1), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(2), UUID.randomUUID()));

        // Continue breaching — must NOT create additional anomaly events
        processor.process(event(home.id(), appId, "101", start.plusSeconds(3), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(4), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(5), UUID.randomUUID()));

        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id()))
                .as("still only one anomaly alert despite continued breach").hasSize(1);
        assertThat(liveStates.get(home.id()).orElseThrow().appliances().get(appId).healthStatus())
                .isEqualTo(ApplianceHealthStatus.ANOMALOUS);
    }

    @Test
    void recoveryFollowedByNewThreeBreachesSendsSecondAnomalyAlert() {
        var home = createHome("1000.00", "1.0", "1.5", "100");
        long appId = home.appliances().getFirst().id();
        Instant start = Instant.now().minusSeconds(30);

        // First anomaly
        processor.process(event(home.id(), appId, "101", start, UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(1), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(2), UUID.randomUUID()));
        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id())).hasSize(1);

        // Recovery — normal reading resolves the anomaly
        processor.process(event(home.id(), appId, "50", start.plusSeconds(3), UUID.randomUUID()));
        assertThat(liveStates.get(home.id()).orElseThrow().appliances().get(appId).healthStatus())
                .isEqualTo(ApplianceHealthStatus.NORMAL);

        // Genuinely new 3-cycle breach after recovery → must generate a second anomaly alert
        processor.process(event(home.id(), appId, "101", start.plusSeconds(4), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(5), UUID.randomUUID()));
        processor.process(event(home.id(), appId, "101", start.plusSeconds(6), UUID.randomUUID()));

        assertThat(anomalyEvents.findByHomeIdOrderByDetectedAtDesc(home.id()))
                .as("second anomaly alert allowed after genuine recovery + new 3-cycle breach").hasSize(2);
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    private com.voltwise.core.api.HomeDtos.HomeResponse createHome(
            String budget, String tariff, String penaltyMultiplier, String safeLimit) {
        return homeService.create(new CreateHomeRequest(
                "Alert Safety Test Home " + UUID.randomUUID(),
                "test@example.com",
                new BigDecimal(budget),
                new BigDecimal(tariff),
                new BigDecimal(penaltyMultiplier),
                List.of(new ApplianceRequest("Kettle", ApplianceType.KETTLE, new BigDecimal(safeLimit)))));
    }

    private TelemetryEvent event(long homeId, long appId, String power, Instant at, UUID id) {
        return new TelemetryEvent(id, 1, "APPLIANCE_TELEMETRY_RECORDED", at,
                homeId, appId, ApplianceType.KETTLE, new BigDecimal(power), OperatingState.ON);
    }
}
