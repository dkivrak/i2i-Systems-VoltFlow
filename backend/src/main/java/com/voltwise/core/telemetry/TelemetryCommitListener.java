package com.voltwise.core.telemetry;

import com.voltwise.core.live.LiveStateStore;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class TelemetryCommitListener {
    private static final int MAX_CAS_ATTEMPTS = 100;
    private final LiveStateStore liveStates;
    private final ApplicationEventPublisher eventPublisher;

    public TelemetryCommitListener(LiveStateStore liveStates, ApplicationEventPublisher eventPublisher) {
        this.liveStates = liveStates;
        this.eventPublisher = eventPublisher;
    }

    @Order(Ordered.HIGHEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void apply(TelemetryCommitAction action) {
        if (!liveStates.compareAndSet(action.homeId(), action.expectedState(), action.committedState())) {
            applyAfterSnapshotRotation(action);
        }
        action.notifications().forEach(eventPublisher::publishEvent);
    }

    private void applyAfterSnapshotRotation(TelemetryCommitAction action) {
        for (int attempt = 0; attempt < MAX_CAS_ATTEMPTS; attempt++) {
            var current = liveStates.get(action.homeId())
                    .orElseThrow(() -> new IllegalStateException("Live state disappeared for home " + action.homeId()));
            var rebased = action.rebaseOnto(current);
            if (liveStates.compareAndSet(action.homeId(), current, rebased)) {
                return;
            }
        }
        throw new IllegalStateException("Snapshot rotation kept changing live state for home " + action.homeId());
    }
}
