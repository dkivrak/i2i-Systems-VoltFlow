package com.voltwise.core.telemetry;

import com.voltwise.core.domain.TariffState;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BillingCalculatorTest {
    private final BillingCalculator calculator = new BillingCalculator();

    @Test
    void chargesNormalRateBeforeBudgetBoundary() {
        var result = apply("10", "20", TariffState.NORMAL, "2", "100", "3", "1.5");
        assertThat(result.costDelta()).isEqualByComparingTo("6");
        assertThat(result.accumulatedCost()).isEqualByComparingTo("26");
        assertThat(result.tariffState()).isEqualTo(TariffState.NORMAL);
        assertThat(result.tariffTransitioned()).isFalse();
    }

    @Test
    void splitsOnlyPostBoundaryEnergyAtPenaltyRate() {
        var result = apply("0", "90", TariffState.NORMAL, "20", "100", "1", "1.5");
        assertThat(result.normalRateEnergyKwh()).isEqualByComparingTo("10");
        assertThat(result.penaltyRateEnergyKwh()).isEqualByComparingTo("10");
        assertThat(result.costDelta()).isEqualByComparingTo("25");
        assertThat(result.accumulatedCost()).isEqualByComparingTo("115");
        assertThat(result.tariffState()).isEqualTo(TariffState.PENALTY);
        assertThat(result.tariffTransitioned()).isTrue();
    }

    @Test
    void chargesAllSubsequentEnergyAtPenaltyRate() {
        var result = apply("0", "100", TariffState.PENALTY, "2", "100", "3", "1.5");
        assertThat(result.costDelta()).isEqualByComparingTo("9");
        assertThat(result.normalRateEnergyKwh()).isZero();
        assertThat(result.penaltyRateEnergyKwh()).isEqualByComparingTo("2");
    }

    @Test
    void activatesPenaltyWhenNormalChargeExactlyReachesBudget() {
        var result = apply("0", "95", TariffState.NORMAL, "5", "100", "1", "1.5");
        assertThat(result.accumulatedCost()).isEqualByComparingTo("100");
        assertThat(result.tariffState()).isEqualTo(TariffState.PENALTY);
        assertThat(result.penaltyRateEnergyKwh()).isZero();
    }

    private BillingCalculator.BillingResult apply(String energy, String cost, TariffState tariff,
            String delta, String budget, String normalRate, String multiplier) {
        return calculator.apply(new BigDecimal(energy), new BigDecimal(cost), tariff, new BigDecimal(delta),
                new BigDecimal(budget), new BigDecimal(normalRate), new BigDecimal(multiplier));
    }
}
