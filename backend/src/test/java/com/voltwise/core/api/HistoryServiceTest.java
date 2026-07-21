package com.voltwise.core.api;

import com.voltwise.core.domain.HistoryBucket;
import com.voltwise.core.persistence.entity.ConsumptionSnapshotEntity;
import com.voltwise.core.persistence.repository.ConsumptionSnapshotRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import com.voltwise.core.snapshot.HistoryService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HistoryServiceTest {
    @Test
    void bucketsSnapshotsAndPaginatesAggregatedResults() {
        HomeRepository homes = mock(HomeRepository.class);
        ConsumptionSnapshotRepository snapshots = mock(ConsumptionSnapshotRepository.class);
        HistoryService service = new HistoryService(homes, snapshots);
        Instant from = Instant.parse("2026-07-21T10:00:00Z");
        Instant to = Instant.parse("2026-07-21T13:00:00Z");
        when(homes.existsById(1L)).thenReturn(true);
        when(snapshots.findByHomeIdAndApplianceIsNullAndPeriodStartLessThanAndPeriodEndGreaterThanOrderByPeriodStartAsc(
                1L, to, from)).thenReturn(List.of(
                snapshot("2026-07-21T10:01:00Z", "1", "100", "100", "2"),
                snapshot("2026-07-21T10:31:00Z", "2", "200", "250", "4"),
                snapshot("2026-07-21T11:01:00Z", "3", "300", "350", "6")));

        var page = service.history(1L, from, to, HistoryBucket.HOUR, 0, 1);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().energyKwh()).isEqualByComparingTo("3");
        assertThat(page.content().getFirst().averagePowerWatts()).isEqualByComparingTo("150");
        assertThat(page.content().getFirst().maximumPowerWatts()).isEqualByComparingTo("250");
        assertThat(page.content().getFirst().cost()).isEqualByComparingTo("6");
    }

    @Test
    void weightsIntervalAveragesByDurationWhenWindowsAreUneven() {
        HomeRepository homes = mock(HomeRepository.class);
        ConsumptionSnapshotRepository snapshots = mock(ConsumptionSnapshotRepository.class);
        HistoryService service = new HistoryService(homes, snapshots);
        Instant from = Instant.parse("2026-07-21T10:00:00Z");
        Instant to = Instant.parse("2026-07-21T11:00:00Z");
        when(homes.existsById(1L)).thenReturn(true);
        when(snapshots.findByHomeIdAndApplianceIsNullAndPeriodStartLessThanAndPeriodEndGreaterThanOrderByPeriodStartAsc(
                1L, to, from)).thenReturn(List.of(
                snapshot("2026-07-21T10:00:00Z", 600, "1", "100", "100", "2"),
                snapshot("2026-07-21T10:10:00Z", 1200, "2", "200", "250", "4")));

        var point = service.history(1L, from, to, HistoryBucket.HOUR, 0, 10).content().getFirst();

        assertThat(point.energyKwh()).isEqualByComparingTo("3");
        assertThat(point.averagePowerWatts()).isEqualByComparingTo("166.667");
        assertThat(point.maximumPowerWatts()).isEqualByComparingTo("250");
        assertThat(point.cost()).isEqualByComparingTo("6");
    }

    @Test
    void splitsIntervalsThatCrossBucketBoundaries() {
        HomeRepository homes = mock(HomeRepository.class);
        ConsumptionSnapshotRepository snapshots = mock(ConsumptionSnapshotRepository.class);
        HistoryService service = new HistoryService(homes, snapshots);
        Instant from = Instant.parse("2026-07-21T10:00:00Z");
        Instant to = Instant.parse("2026-07-21T12:00:00Z");
        when(homes.existsById(1L)).thenReturn(true);
        when(snapshots.findByHomeIdAndApplianceIsNullAndPeriodStartLessThanAndPeriodEndGreaterThanOrderByPeriodStartAsc(
                1L, to, from)).thenReturn(List.of(
                snapshot("2026-07-21T10:59:40Z", 40, "4", "100", "150", "2")));

        var points = service.history(1L, from, to, HistoryBucket.HOUR, 0, 10).content();

        assertThat(points).hasSize(2);
        assertThat(points).allSatisfy(point -> {
            assertThat(point.energyKwh()).isEqualByComparingTo("2");
            assertThat(point.averagePowerWatts()).isEqualByComparingTo("100");
            assertThat(point.maximumPowerWatts()).isEqualByComparingTo("150");
            assertThat(point.cost()).isEqualByComparingTo("1");
        });
        assertThat(points.getFirst().periodEnd()).isEqualTo(Instant.parse("2026-07-21T11:00:00Z"));
        assertThat(points.getLast().periodEnd()).isEqualTo(Instant.parse("2026-07-21T11:00:20Z"));
    }

    private ConsumptionSnapshotEntity snapshot(String start, String energy, String average, String max, String cost) {
        return snapshot(start, 60, energy, average, max, cost);
    }

    private ConsumptionSnapshotEntity snapshot(String start, long durationSeconds, String energy, String average,
                                               String max, String cost) {
        ConsumptionSnapshotEntity entity = new ConsumptionSnapshotEntity();
        entity.setPeriodStart(Instant.parse(start));
        entity.setPeriodEnd(Instant.parse(start).plusSeconds(durationSeconds));
        entity.setEnergyKwh(new BigDecimal(energy));
        entity.setAveragePowerWatts(new BigDecimal(average));
        entity.setMaximumPowerWatts(new BigDecimal(max));
        entity.setCost(new BigDecimal(cost));
        return entity;
    }
}
