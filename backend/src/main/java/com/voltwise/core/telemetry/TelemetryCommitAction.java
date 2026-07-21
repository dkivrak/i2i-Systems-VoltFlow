package com.voltwise.core.telemetry;

import com.voltwise.core.live.ApplianceLiveState;
import com.voltwise.core.live.HomeLiveState;
import com.voltwise.core.notification.DomainNotificationRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

public record TelemetryCommitAction(
        Long homeId,
        HomeLiveState expectedState,
        HomeLiveState committedState,
        Long applianceId,
        Instant occurredAt,
        BigDecimal energyDeltaKwh,
        BigDecimal costDelta,
        List<DomainNotificationRequest> notifications
) {
    public TelemetryCommitAction {
        notifications = notifications == null ? List.of() : List.copyOf(notifications);
    }

    /** Re-applies this committed sample when only snapshot windows changed concurrently. */
    public HomeLiveState rebaseOnto(HomeLiveState currentState) {
        if (!expectedState.sameOperationalStateAs(currentState)) {
            throw new IllegalStateException("Live operational state changed concurrently for home " + homeId);
        }

        var rebasedAppliances = new LinkedHashMap<Long, ApplianceLiveState>();
        committedState.appliances().forEach((id, committedAppliance) -> {
            ApplianceLiveState currentAppliance = currentState.appliances().get(id);
            var window = currentAppliance.snapshotWindow();
            if (id.equals(applianceId)) {
                window = window.add(occurredAt, energyDeltaKwh, costDelta, committedAppliance.currentPowerWatts());
            }
            rebasedAppliances.put(id, committedAppliance.withSnapshotWindow(window));
        });

        return committedState.withSnapshotWindows(
                currentState.snapshotWindow().add(occurredAt, energyDeltaKwh, costDelta,
                        committedState.currentPowerWatts()),
                rebasedAppliances
        );
    }
}
