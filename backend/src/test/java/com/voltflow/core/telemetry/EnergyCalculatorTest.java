package com.voltflow.core.telemetry;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnergyCalculatorTest {
    private final EnergyCalculator calculator = new EnergyCalculator();

    @Test
    void calculatesEnergyFromPowerAndElapsedSeconds() {
        Instant start = Instant.parse("2026-07-21T12:00:00Z");
        assertThat(calculator.calculateDeltaKwh(new BigDecimal("1800"), start, start.plusSeconds(2), 300))
                .isEqualByComparingTo("0.001000000");
    }

    @Test
    void returnsZeroForFirstOrOutOfOrderReading() {
        Instant now = Instant.parse("2026-07-21T12:00:00Z");
        assertThat(calculator.calculateDeltaKwh(BigDecimal.TEN, null, now, 300)).isZero();
        assertThat(calculator.calculateDeltaKwh(BigDecimal.TEN, now, now.minusSeconds(1), 300)).isZero();
    }

    @Test
    void capsLongTelemetryGaps() {
        Instant start = Instant.parse("2026-07-21T12:00:00Z");
        assertThat(calculator.calculateDeltaKwh(new BigDecimal("1000"), start, start.plusSeconds(1000), 60))
                .isEqualByComparingTo("0.016666667");
    }

    @Test
    void rejectsNegativePower() {
        assertThatThrownBy(() -> calculator.calculateDeltaKwh(BigDecimal.ONE.negate(), Instant.EPOCH,
                Instant.EPOCH.plusSeconds(1), 300)).isInstanceOf(IllegalArgumentException.class);
    }
}
