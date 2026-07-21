package com.voltwise.core.snapshot;

import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.live.LiveStateStore;
import com.voltwise.core.live.SnapshotWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Optional;

@Service
public class SnapshotService {
    private static final int MAX_CAS_ATTEMPTS = 100;
    private static final Logger log = LoggerFactory.getLogger(SnapshotService.class);
    private final LiveStateStore liveStates;
    private final SnapshotWriter writer;

    public SnapshotService(LiveStateStore liveStates, SnapshotWriter writer) {
        this.liveStates = liveStates;
        this.writer = writer;
    }

    @Scheduled(fixedDelayString = "${voltwise.snapshots.interval-ms:60000}")
    public void capture() {
        captureAt(Instant.now().truncatedTo(ChronoUnit.SECONDS));
    }

    void captureAt(Instant periodEnd) {
        for (HomeLiveState state : liveStates.getAll()) {
            rotate(state.homeId(), periodEnd).ifPresent(captured -> persistOrRestore(captured));
        }
    }

    private Optional<CapturedSnapshot> rotate(Long homeId, Instant periodEnd) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            HomeLiveState current = liveStates.get(homeId).orElse(null);
            if (current == null || !current.snapshotWindow().hasSamples()) {
                return Optional.empty();
            }
            Instant periodStart = current.snapshotWindow().startedAt();
            if (periodStart == null || !periodStart.isBefore(periodEnd)) {
                return Optional.empty();
            }

            var capturedAppliances = new LinkedHashMap<Long, SnapshotWindow>();
            var resetAppliances = new LinkedHashMap<Long, ApplianceLiveState>();
            current.appliances().forEach((id, appliance) -> {
                capturedAppliances.put(id, appliance.snapshotWindow());
                resetAppliances.put(id, appliance.withSnapshotWindow(SnapshotWindow.empty(periodEnd)));
            });
            HomeLiveState reset = current.withSnapshotWindows(SnapshotWindow.empty(periodEnd), resetAppliances);
            if (liveStates.compareAndSet(homeId, current, reset)) {
                return Optional.of(new CapturedSnapshot(homeId, periodStart, periodEnd,
                        current.snapshotWindow(), capturedAppliances));
            }
        }
        throw new IllegalStateException("Could not rotate snapshot window for home " + homeId);
    }

    private void persistOrRestore(CapturedSnapshot captured) {
        try {
            boolean written = writer.persist(captured);
            log.debug("{} live-state snapshot for home {} and period {} - {}",
                    written ? "Captured" : "Skipped duplicate", captured.homeId(),
                    captured.periodStart(), captured.periodEnd());
        } catch (RuntimeException persistenceFailure) {
            if (becameDuplicate(captured)) {
                log.debug("Snapshot period was persisted concurrently for home {} and period {} - {}",
                        captured.homeId(), captured.periodStart(), captured.periodEnd());
                return;
            }
            restore(captured);
            log.error("Snapshot persistence failed; restored metrics for home {} and period {} - {}",
                    captured.homeId(), captured.periodStart(), captured.periodEnd(), persistenceFailure);
        }
    }

    private boolean becameDuplicate(CapturedSnapshot captured) {
        try {
            return writer.alreadyPersisted(captured);
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private void restore(CapturedSnapshot captured) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            HomeLiveState current = liveStates.get(captured.homeId()).orElse(null);
            if (current == null) {
                log.error("Cannot restore snapshot metrics because live state disappeared for home {}",
                        captured.homeId());
                return;
            }

            var restoredAppliances = new LinkedHashMap<>(current.appliances());
            captured.applianceWindows().forEach((id, capturedWindow) -> {
                ApplianceLiveState appliance = restoredAppliances.get(id);
                if (appliance != null) {
                    restoredAppliances.put(id, appliance.withSnapshotWindow(
                            capturedWindow.merge(appliance.snapshotWindow())));
                }
            });
            HomeLiveState restored = current.withSnapshotWindows(
                    captured.homeWindow().merge(current.snapshotWindow()), restoredAppliances);
            if (liveStates.compareAndSet(captured.homeId(), current, restored)) {
                return;
            }
        }
        throw new IllegalStateException("Could not restore snapshot metrics for home " + captured.homeId());
    }
}
