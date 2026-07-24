package com.voltflow.core.snapshot;

import com.voltflow.core.live.SnapshotWindow;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

record CapturedSnapshot(
        Long homeId,
        Instant periodStart,
        Instant periodEnd,
        SnapshotWindow homeWindow,
        Map<Long, SnapshotWindow> applianceWindows
) {
    CapturedSnapshot {
        applianceWindows = applianceWindows == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(applianceWindows));
    }
}
