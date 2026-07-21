package com.voltwise.core.snapshot;

import com.voltwise.core.live.SnapshotWindow;
import com.voltwise.core.persistence.entity.ApplianceEntity;
import com.voltwise.core.persistence.entity.ConsumptionSnapshotEntity;
import com.voltwise.core.persistence.entity.HomeEntity;
import com.voltwise.core.persistence.repository.ApplianceRepository;
import com.voltwise.core.persistence.repository.ConsumptionSnapshotRepository;
import com.voltwise.core.persistence.repository.HomeRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SnapshotWriterTest {
    private static final Instant START = Instant.parse("2026-07-21T10:00:00Z");
    private static final Instant END = START.plusSeconds(60);

    @Test
    void writesIntervalDeltasAverageAndMaximumAndSkipsEmptyAppliances() {
        HomeRepository homes = mock(HomeRepository.class);
        ApplianceRepository appliances = mock(ApplianceRepository.class);
        ConsumptionSnapshotRepository snapshots = mock(ConsumptionSnapshotRepository.class);
        HomeEntity home = new HomeEntity();
        home.setId(1L);
        ApplianceEntity appliance = new ApplianceEntity();
        appliance.setId(10L);
        appliance.setHome(home);
        when(homes.findById(1L)).thenReturn(Optional.of(home));
        when(appliances.findById(10L)).thenReturn(Optional.of(appliance));
        AtomicReference<List<ConsumptionSnapshotEntity>> saved = new AtomicReference<>();
        when(snapshots.saveAll(any())).thenAnswer(invocation -> {
            var values = new ArrayList<ConsumptionSnapshotEntity>();
            ((Iterable<ConsumptionSnapshotEntity>) invocation.getArgument(0)).forEach(values::add);
            saved.set(values);
            return values;
        });
        SnapshotWindow window = SnapshotWindow.empty()
                .add(START, new BigDecimal("0.1"), new BigDecimal("0.2"), new BigDecimal("100"))
                .add(START.plusSeconds(30), new BigDecimal("0.2"), new BigDecimal("0.3"), new BigDecimal("300"));
        CapturedSnapshot captured = new CapturedSnapshot(1L, START, END, window,
                Map.of(10L, window, 11L, SnapshotWindow.empty(START)));

        boolean written = new SnapshotWriter(homes, appliances, snapshots).persist(captured);

        assertThat(written).isTrue();
        assertThat(saved.get()).hasSize(2);
        assertThat(saved.get()).allSatisfy(entity -> {
            assertThat(entity.getEnergyKwh()).isEqualByComparingTo("0.3");
            assertThat(entity.getCost()).isEqualByComparingTo("0.5");
            assertThat(entity.getAveragePowerWatts()).isEqualByComparingTo("200");
            assertThat(entity.getMaximumPowerWatts()).isEqualByComparingTo("300");
        });
        verify(appliances, never()).findById(11L);
    }

    @Test
    void existingHomePeriodPreventsDuplicateRows() {
        HomeRepository homes = mock(HomeRepository.class);
        ApplianceRepository appliances = mock(ApplianceRepository.class);
        ConsumptionSnapshotRepository snapshots = mock(ConsumptionSnapshotRepository.class);
        when(snapshots.existsByHomeIdAndApplianceIsNullAndPeriodStartAndPeriodEnd(1L, START, END))
                .thenReturn(true);
        SnapshotWindow window = SnapshotWindow.empty().add(START,
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE);

        boolean written = new SnapshotWriter(homes, appliances, snapshots).persist(
                new CapturedSnapshot(1L, START, END, window, Map.of()));

        assertThat(written).isFalse();
        verify(homes, never()).findById(1L);
        verify(snapshots, never()).saveAll(any());
    }
}
