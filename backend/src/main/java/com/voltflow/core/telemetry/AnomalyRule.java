package com.voltflow.core.telemetry;

import com.voltflow.core.domain.ApplianceHealthStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class AnomalyRule {
    public static final int REQUIRED_CONSECUTIVE_BREACHES = 3;

    public Outcome evaluate(BigDecimal powerWatts, BigDecimal safeLimit, int previousBreachCount,
                            ApplianceHealthStatus previousHealth) {
        if (powerWatts.compareTo(safeLimit) > 0) {
            int breaches = previousBreachCount + 1;
            boolean detected = previousHealth == ApplianceHealthStatus.NORMAL
                    && breaches >= REQUIRED_CONSECUTIVE_BREACHES;
            ApplianceHealthStatus health = detected ? ApplianceHealthStatus.ANOMALOUS : previousHealth;
            return new Outcome(breaches, health, detected, false);
        }
        boolean resolved = previousHealth == ApplianceHealthStatus.ANOMALOUS;
        return new Outcome(0, ApplianceHealthStatus.NORMAL, false, resolved);
    }

    public record Outcome(int breachCount, ApplianceHealthStatus healthStatus,
                          boolean anomalyDetected, boolean anomalyResolved) {}
}
