package com.voltwise.core.telemetry;

import com.voltwise.core.domain.QuotaThreshold;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class QuotaRuleTest {
    private final QuotaRule rule = new QuotaRule();

    @Test
    void detectsEightyPercentCrossingOnce() {
        assertThat(rule.crossedThresholds(decimal("79.9"), decimal("80")))
                .containsExactly(QuotaThreshold.EIGHTY_PERCENT);
        assertThat(rule.crossedThresholds(decimal("80"), decimal("85"))).isEmpty();
    }

    @Test
    void detectsOneHundredPercentCrossingOnce() {
        assertThat(rule.crossedThresholds(decimal("99.9"), decimal("100")))
                .containsExactly(QuotaThreshold.ONE_HUNDRED_PERCENT);
        assertThat(rule.crossedThresholds(decimal("100"), decimal("120"))).isEmpty();
    }

    @Test
    void emitsBothWhenOneDeltaCrossesBothBoundaries() {
        assertThat(rule.crossedThresholds(decimal("70"), decimal("105")))
                .containsExactly(QuotaThreshold.EIGHTY_PERCENT, QuotaThreshold.ONE_HUNDRED_PERCENT);
    }

    private BigDecimal decimal(String value) { return new BigDecimal(value); }
}
