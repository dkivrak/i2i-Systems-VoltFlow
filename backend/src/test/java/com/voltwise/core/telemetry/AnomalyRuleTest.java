package com.voltwise.core.telemetry;

import com.voltwise.core.domain.ApplianceHealthStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyRuleTest {
    private final AnomalyRule rule = new AnomalyRule();

    @Test
    void thirdConsecutiveBreachTransitionsNormalToAnomalous() {
        var first = evaluate(101, 0, ApplianceHealthStatus.NORMAL);
        var second = evaluate(101, first.breachCount(), first.healthStatus());
        var third = evaluate(101, second.breachCount(), second.healthStatus());
        assertThat(first.anomalyDetected()).isFalse();
        assertThat(second.anomalyDetected()).isFalse();
        assertThat(third.anomalyDetected()).isTrue();
        assertThat(third.healthStatus()).isEqualTo(ApplianceHealthStatus.ANOMALOUS);
    }

    @Test
    void normalReadingResetsBreachCount() {
        var outcome = evaluate(90, 2, ApplianceHealthStatus.NORMAL);
        assertThat(outcome.breachCount()).isZero();
        assertThat(outcome.healthStatus()).isEqualTo(ApplianceHealthStatus.NORMAL);
    }

    @Test
    void anomalousStateDoesNotEmitDuplicateDetection() {
        var outcome = evaluate(101, 3, ApplianceHealthStatus.ANOMALOUS);
        assertThat(outcome.healthStatus()).isEqualTo(ApplianceHealthStatus.ANOMALOUS);
        assertThat(outcome.anomalyDetected()).isFalse();
    }

    @Test
    void normalReadingResolvesAnomalousState() {
        var outcome = evaluate(90, 4, ApplianceHealthStatus.ANOMALOUS);
        assertThat(outcome.anomalyResolved()).isTrue();
        assertThat(outcome.healthStatus()).isEqualTo(ApplianceHealthStatus.NORMAL);
        assertThat(outcome.breachCount()).isZero();
    }

    private AnomalyRule.Outcome evaluate(int power, int count, ApplianceHealthStatus health) {
        return rule.evaluate(BigDecimal.valueOf(power), BigDecimal.valueOf(100), count, health);
    }
}
