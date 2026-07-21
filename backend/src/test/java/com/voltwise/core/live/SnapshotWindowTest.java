package com.voltwise.core.live;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voltwise.core.domain.ApplianceHealthStatus;
import com.voltwise.core.domain.ApplianceType;
import com.voltwise.core.domain.OperatingState;
import com.voltwise.core.domain.TariffState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SnapshotWindowTest {
    @Test
    void accumulatesAndMergesIntervalMetrics() {
        Instant start = Instant.parse("2026-07-21T10:00:00Z");
        SnapshotWindow first = SnapshotWindow.empty().add(start,
                new BigDecimal("0.1"), new BigDecimal("0.04"), new BigDecimal("100"));
        SnapshotWindow second = SnapshotWindow.empty(start.plusSeconds(1)).add(start.plusSeconds(1),
                new BigDecimal("0.2"), new BigDecimal("0.06"), new BigDecimal("300"));

        SnapshotWindow merged = first.merge(second);

        assertThat(merged.startedAt()).isEqualTo(start);
        assertThat(merged.energyKwh()).isEqualByComparingTo("0.3");
        assertThat(merged.cost()).isEqualByComparingTo("0.10");
        assertThat(merged.powerSampleCount()).isEqualTo(2);
        assertThat(merged.powerSampleSumWatts()).isEqualByComparingTo("400");
        assertThat(merged.averagePowerWatts()).isEqualByComparingTo("200");
        assertThat(merged.maximumPowerWatts()).isEqualByComparingTo("300");
    }

    @Test
    void missingWindowFieldsFromAnOlderIgniteJsonDefaultToEmpty() throws Exception {
        var mapper = JsonMapper.builder().findAndAddModules().build();
        var appliance = new ApplianceLiveState(10L, "Kettle", ApplianceType.KETTLE,
                new BigDecimal("100"), BigDecimal.ONE, BigDecimal.ONE, OperatingState.ON,
                new BigDecimal("2000"), 0, ApplianceHealthStatus.NORMAL, Instant.EPOCH);
        var home = new HomeLiveState(1L, "Home", new BigDecimal("100"), BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("500"), BigDecimal.ONE, TariffState.NORMAL,
                Instant.EPOCH, Map.of(10L, appliance));
        ObjectNode json = mapper.valueToTree(home);
        json.remove("snapshotWindow");
        ((ObjectNode) json.path("appliances").path("10")).remove("snapshotWindow");

        HomeLiveState restored = mapper.treeToValue(json, HomeLiveState.class);

        assertThat(restored.snapshotWindow()).isEqualTo(SnapshotWindow.empty());
        assertThat(restored.appliances().get(10L).snapshotWindow()).isEqualTo(SnapshotWindow.empty());
    }

    @Test
    void compareAndSetNeverClaimsAnExpectedStateMismatch() {
        InMemoryLiveStateStore store = new InMemoryLiveStateStore();
        HomeLiveState expected = state("100");
        HomeLiveState concurrentlyChanged = state("200");
        HomeLiveState replacement = state("300");
        store.putIfAbsent(1L, expected);
        store.update(1L, ignored -> concurrentlyChanged);

        assertThat(store.compareAndSet(1L, expected, replacement)).isFalse();
        assertThat(store.get(1L)).contains(concurrentlyChanged);
    }

    private HomeLiveState state(String power) {
        return new HomeLiveState(1L, "Home", new BigDecimal(power), BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("500"), BigDecimal.ONE, TariffState.NORMAL,
                Instant.EPOCH, Map.of());
    }
}
