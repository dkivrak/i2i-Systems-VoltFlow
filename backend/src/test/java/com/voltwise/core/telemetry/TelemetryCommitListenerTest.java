package com.voltwise.core.telemetry;

import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.InMemoryLiveStateStore;
import com.voltwise.core.live.SnapshotWindow;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class TelemetryCommitListenerTest {
    @Test
    void rebasesCommittedSampleOntoAConcurrentlyRotatedWindow() {
        Instant firstAt = Instant.parse("2026-07-21T10:00:00Z");
        Instant secondAt = firstAt.plusSeconds(30);
        Instant rotatedAt = firstAt.plusSeconds(20);
        SnapshotWindow firstWindow = SnapshotWindow.empty().add(firstAt,
                new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("100"));
        ApplianceLiveState expectedAppliance = appliance(new BigDecimal("100"), new BigDecimal("1"),
                new BigDecimal("2"), firstAt, firstWindow);
        HomeLiveState expected = home(new BigDecimal("100"), new BigDecimal("1"), new BigDecimal("2"),
                firstAt, expectedAppliance, firstWindow);

        SnapshotWindow committedWindow = firstWindow.add(secondAt,
                new BigDecimal("0.3"), new BigDecimal("0.4"), new BigDecimal("300"));
        ApplianceLiveState committedAppliance = appliance(new BigDecimal("300"), new BigDecimal("1.3"),
                new BigDecimal("2.4"), secondAt, committedWindow);
        HomeLiveState committed = home(new BigDecimal("300"), new BigDecimal("1.3"), new BigDecimal("2.4"),
                secondAt, committedAppliance, committedWindow);
        TelemetryCommitAction action = new TelemetryCommitAction(1L, expected, committed, 10L, secondAt,
                new BigDecimal("0.3"), new BigDecimal("0.4"), java.util.List.of());

        InMemoryLiveStateStore store = new InMemoryLiveStateStore();
        store.putIfAbsent(1L, expected);
        HomeLiveState rotated = expected.withSnapshotWindows(SnapshotWindow.empty(rotatedAt),
                Map.of(10L, expectedAppliance.withSnapshotWindow(SnapshotWindow.empty(rotatedAt))));
        assertThat(store.compareAndSet(1L, expected, rotated)).isTrue();

        new TelemetryCommitListener(store, mock(ApplicationEventPublisher.class)).apply(action);

        HomeLiveState applied = store.get(1L).orElseThrow();
        assertThat(applied.sameOperationalStateAs(committed)).isTrue();
        assertThat(applied.snapshotWindow().startedAt()).isEqualTo(rotatedAt);
        assertThat(applied.snapshotWindow().powerSampleCount()).isEqualTo(1);
        assertThat(applied.snapshotWindow().energyKwh()).isEqualByComparingTo("0.3");
        assertThat(applied.snapshotWindow().maximumPowerWatts()).isEqualByComparingTo("300");
        assertThat(applied.appliances().get(10L).snapshotWindow().powerSampleCount()).isEqualTo(1);
    }

    private HomeLiveState home(BigDecimal power, BigDecimal energy, BigDecimal cost, Instant updatedAt,
                               ApplianceLiveState appliance, SnapshotWindow window) {
        return new HomeLiveState(1L, "Home", power, energy, cost, new BigDecimal("100"),
                BigDecimal.ONE, TariffState.NORMAL, updatedAt, Map.of(10L, appliance), window);
    }

    private ApplianceLiveState appliance(BigDecimal power, BigDecimal energy, BigDecimal cost,
                                         Instant updatedAt, SnapshotWindow window) {
        return new ApplianceLiveState(10L, "Kettle", ApplianceType.KETTLE, power, energy, cost,
                OperatingState.ON, new BigDecimal("2000"), 0, ApplianceHealthStatus.NORMAL, updatedAt, window);
    }
}
