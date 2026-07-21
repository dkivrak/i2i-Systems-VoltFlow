package com.voltwise.core.telemetry;

import com.voltwise.core.domain.TariffState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class BillingCalculator {
    private static final int MONEY_SCALE = 6;
    private static final int ENERGY_SCALE = 9;

    public BillingResult apply(BigDecimal accumulatedEnergy, BigDecimal accumulatedCost,
                               TariffState currentTariff, BigDecimal energyDelta,
                               BigDecimal monthlyBudget, BigDecimal normalRate,
                               BigDecimal penaltyMultiplier) {
        requireNonNegative(accumulatedEnergy, "accumulatedEnergy");
        requireNonNegative(accumulatedCost, "accumulatedCost");
        requireNonNegative(energyDelta, "energyDelta");
        requirePositive(monthlyBudget, "monthlyBudget");
        requirePositive(normalRate, "normalRate");
        if (penaltyMultiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("penaltyMultiplier must be at least one");
        }

        BigDecimal previousUsage = percentage(accumulatedCost, monthlyBudget);
        BigDecimal penaltyRate = normalRate.multiply(penaltyMultiplier);
        BigDecimal normalEnergy = BigDecimal.ZERO;
        BigDecimal penaltyEnergy = BigDecimal.ZERO;
        BigDecimal costDelta;
        TariffState resultingTariff = currentTariff;

        if (currentTariff == TariffState.PENALTY || accumulatedCost.compareTo(monthlyBudget) >= 0) {
            resultingTariff = TariffState.PENALTY;
            penaltyEnergy = energyDelta;
            costDelta = energyDelta.multiply(penaltyRate);
        } else {
            BigDecimal remainingNormalCost = monthlyBudget.subtract(accumulatedCost).max(BigDecimal.ZERO);
            BigDecimal allAtNormalCost = energyDelta.multiply(normalRate);
            if (allAtNormalCost.compareTo(remainingNormalCost) <= 0) {
                normalEnergy = energyDelta;
                costDelta = allAtNormalCost;
                if (allAtNormalCost.compareTo(remainingNormalCost) == 0) {
                    resultingTariff = TariffState.PENALTY;
                }
            } else {
                normalEnergy = remainingNormalCost.divide(normalRate, ENERGY_SCALE, RoundingMode.HALF_UP)
                        .min(energyDelta);
                penaltyEnergy = energyDelta.subtract(normalEnergy).max(BigDecimal.ZERO);
                costDelta = remainingNormalCost.add(penaltyEnergy.multiply(penaltyRate));
                resultingTariff = TariffState.PENALTY;
            }
        }

        BigDecimal newEnergy = accumulatedEnergy.add(energyDelta).setScale(ENERGY_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundedCostDelta = costDelta.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newCost = accumulatedCost.add(roundedCostDelta).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newUsage = percentage(newCost, monthlyBudget);
        boolean transitioned = currentTariff == TariffState.NORMAL && resultingTariff == TariffState.PENALTY;
        return new BillingResult(energyDelta.setScale(ENERGY_SCALE, RoundingMode.HALF_UP), roundedCostDelta,
                newEnergy, newCost, currentTariff, resultingTariff, transitioned,
                previousUsage, newUsage, normalEnergy, penaltyEnergy, normalRate, penaltyRate);
    }

    public BigDecimal percentage(BigDecimal cost, BigDecimal budget) {
        return cost.multiply(BigDecimal.valueOf(100)).divide(budget, 4, RoundingMode.HALF_UP);
    }

    private void requireNonNegative(BigDecimal value, String field) {
        if (value == null || value.signum() < 0) throw new IllegalArgumentException(field + " must not be negative");
    }
    private void requirePositive(BigDecimal value, String field) {
        if (value == null || value.signum() <= 0) throw new IllegalArgumentException(field + " must be positive");
    }

    public record BillingResult(
            BigDecimal energyDeltaKwh, BigDecimal costDelta,
            BigDecimal accumulatedEnergyKwh, BigDecimal accumulatedCost,
            TariffState previousTariff, TariffState tariffState, boolean tariffTransitioned,
            BigDecimal previousUsagePercent, BigDecimal usagePercent,
            BigDecimal normalRateEnergyKwh, BigDecimal penaltyRateEnergyKwh,
            BigDecimal normalRate, BigDecimal penaltyRate
    ) {}
}
