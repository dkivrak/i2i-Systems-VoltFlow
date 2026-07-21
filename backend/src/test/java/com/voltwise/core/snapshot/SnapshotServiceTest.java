package com.voltwise.core.snapshot;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.InMemoryLiveStateStore;
import com.voltwise.core.live.SnapshotWindow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotServiceTest {
    private static final Instant START = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant END = Instant.parse("2026-07-21T10:01:00Z");

    @Test
    void atomicallyRotatesMetricsAndDoesNotWriteEmptyOrDuplicateWindows() {
        InMemoryLiveStateStore store = new InMemoryLiveStateStore();
        store.putIfAbsent(1L, populatedState());
        store.putIfAbsent(2L, emptyState(2L));
        SnapshotWriter writer = mock(SnapshotWriter.class);
        when(writer.persist(any())).thenReturn(true);
        SnapshotService service = new SnapshotService(store, writer);

        service.captureAt(END);
        service.captureAt(END.plusSeconds(60));

        ArgumentCaptor<CapturedSnapshot> captured = ArgumentCaptor.forClass(CapturedSnapshot.class);
        verify(writer).persist(captured.capture());
        verify(writer, never()).alreadyPersisted(any());
        assertThat(captured.getValue().homeWindow().energyKwh()).isEqualByComparingTo("0.3");
        assertThat(captured.getValue().homeWindow().averagePowerWatts()).isEqualByComparingTo("200");
        assertThat(captured.getValue().homeWindow().maximumPowerWatts()).isEqualByComparingTo("300");

        HomeLiveState reset = store.get(1L).orElseThrow();
        assertThat(reset.snapshotWindow().hasSamples()).isFalse();
        assertThat(reset.snapshotWindow().startedAt()).isEqualTo(END);
        assertThat(reset.appliances().get(10L).snapshotWindow().hasSamples()).isFalse();
        assertThat(store.get(2L).orElseThrow().snapshotWindow().startedAt()).isNull();
    }

    @Test
    void restoresCapturedMetricsAndMergesTelemetryArrivingDuringPersistenceFailure() {
        InMemoryLiveStateStore store = new InMemoryLiveStateStore();
        store.putIfAbsent(1L, populatedState());
        SnapshotWriter writer = mock(SnapshotWriter.class);
        doAnswer(invocation -> {
            store.update(1L, current -> addConcurrentSample(current,
                    new BigDecimal("0.4"), new BigDecimal("0.7"), new BigDecimal("500")));
            throw new IllegalStateException("database unavailable");
        }).when(writer).persist(any());
        when(writer.alreadyPersisted(any())).thenReturn(false);

        new SnapshotService(store, writer).captureAt(END);

        HomeLiveState restored = store.get(1L).orElseThrow();
        assertThat(restored.snapshotWindow().energyKwh()).isEqualByComparingTo("0.7");
        assertThat(restored.snapshotWindow().cost()).isEqualByComparingTo("1.2");
        assertThat(restored.snapshotWindow().powerSampleCount()).isEqualTo(3);
        assertThat(restored.snapshotWindow().maximumPowerWatts()).isEqualByComparingTo("500");
        assertThat(restored.appliances().get(10L).snapshotWindow().energyKwh()).isEqualByComparingTo("0.7");
        assertThat(restored.currentPowerWatts()).isEqualByComparingTo("500");
        assertThat(restored.accumulatedEnergyKwh()).isEqualByComparingTo("5.4");
        assertThat(restored.currentCost()).isEqualByComparingTo("8.7");
        assertThat(restored.lastUpdatedAt()).isEqualTo(END.plusSeconds(1));
    }

    private HomeLiveState populatedState() {
        SnapshotWindow window = SnapshotWindow.empty()
                .add(START, new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("100"))
                .add(START.plusSeconds(30), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("300"));
        ApplianceLiveState appliance = appliance(window, new BigDecimal("300"));
        return new HomeLiveState(1L, "Home", new BigDecimal("300"), new BigDecimal("5"),
                new BigDecimal("8"), new BigDecimal("100"), new BigDecimal("8"), TariffState.NORMAL,
                START.plusSeconds(30), Map.of(10L, appliance), window);
    }

    private HomeLiveState emptyState(long id) {
        return new HomeLiveState(id, "Empty", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("100"), BigDecimal.ZERO, TariffState.NORMAL, null, Map.of());
    }

    private HomeLiveState addConcurrentSample(HomeLiveState current, BigDecimal energy, BigDecimal cost,
                                             BigDecimal power) {
        Instant occurredAt = END.plusSeconds(1);
        var appliances = new LinkedHashMap<>(current.appliances());
        ApplianceLiveState old = appliances.get(10L);
        appliances.put(10L, new ApplianceLiveState(old.applianceId(), old.name(), old.type(), power,
                old.accumulatedEnergyKwh().add(energy), old.accumulatedCost().add(cost), old.operatingState(),
                old.safePowerLimitWatts(), old.consecutiveBreachCount(), old.healthStatus(), occurredAt,
                old.snapshotWindow().add(occurredAt, energy, cost, power)));
        return new HomeLiveState(current.homeId(), current.homeName(), power,
                current.accumulatedEnergyKwh().add(energy), current.currentCost().add(cost),
                current.monthlyBudget(), current.budgetUsagePercent(), current.tariffState(), occurredAt, appliances,
                current.snapshotWindow().add(occurredAt, energy, cost, power));
    }

    private ApplianceLiveState appliance(SnapshotWindow window, BigDecimal currentPower) {
        return new ApplianceLiveState(10L, "Kettle", ApplianceType.KETTLE, currentPower,
                new BigDecimal("5"), new BigDecimal("8"), OperatingState.ON, new BigDecimal("2000"),
                0, ApplianceHealthStatus.NORMAL, START.plusSeconds(30), window);
    }
}
