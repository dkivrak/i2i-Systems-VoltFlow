package com.voltwise.core.telemetry;

import com.voltwise.core.domain.TariffState;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
public class BillingCalculator {
    private static final int MONEY_SCALE = 6;
    private static final int ENERGY_SCALE = 9;
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal LEVEL_1_CAP_MULTIPLIER = new BigDecimal("1.50");
    private static final BigDecimal LEVEL_2_PENALTY_FACTOR = new BigDecimal("1.50");

    public BillingResult apply(BigDecimal accumulatedEnergy, BigDecimal accumulatedCost,
                               TariffState currentTariff, BigDecimal energyDelta,
                               BigDecimal monthlyBudget, BigDecimal normalRate,
                               BigDecimal penaltyMultiplier) {
        validateInputs(accumulatedEnergy, accumulatedCost, energyDelta, monthlyBudget, normalRate, penaltyMultiplier);

        BigDecimal previousUsage = calculateUsagePercentage(accumulatedCost, monthlyBudget);
        BigDecimal level1Rate = normalRate.multiply(penaltyMultiplier).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal level2Rate = level1Rate.multiply(LEVEL_2_PENALTY_FACTOR).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal level1Cap = monthlyBudget.multiply(LEVEL_1_CAP_MULTIPLIER).setScale(MONEY_SCALE, RoundingMode.HALF_UP);

        BigDecimal remainingEnergy = energyDelta;
        BigDecimal costDelta = BigDecimal.ZERO;
        BigDecimal currentCost = accumulatedCost;

        BigDecimal normalEnergyUsed = BigDecimal.ZERO;
        BigDecimal penaltyEnergyUsed = BigDecimal.ZERO;

        // 1. Normal Tier: Cost <= monthlyBudget
        if (currentCost.compareTo(monthlyBudget) < 0 && remainingEnergy.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal normalBudgetSpace = monthlyBudget.subtract(currentCost);
            BigDecimal maxNormalEnergy = normalBudgetSpace.divide(normalRate, ENERGY_SCALE, RoundingMode.HALF_UP);
            BigDecimal normalEnergyToApply = remainingEnergy.min(maxNormalEnergy);

            BigDecimal costForNormal = normalEnergyToApply.multiply(normalRate);
            costDelta = costDelta.add(costForNormal);
            currentCost = currentCost.add(costForNormal);
            remainingEnergy = remainingEnergy.subtract(normalEnergyToApply);
            normalEnergyUsed = normalEnergyUsed.add(normalEnergyToApply);
        }

        // 2. Progressive Penalty Level 1 Tier: monthlyBudget < Cost <= monthlyBudget * 1.5
        if (currentCost.compareTo(level1Cap) < 0 && remainingEnergy.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal level1Space = level1Cap.subtract(currentCost);
            BigDecimal maxLevel1Energy = level1Space.divide(level1Rate, ENERGY_SCALE, RoundingMode.HALF_UP);
            BigDecimal level1EnergyToApply = remainingEnergy.min(maxLevel1Energy);

            BigDecimal costForLevel1 = level1EnergyToApply.multiply(level1Rate);
            costDelta = costDelta.add(costForLevel1);
            currentCost = currentCost.add(costForLevel1);
            remainingEnergy = remainingEnergy.subtract(level1EnergyToApply);
            penaltyEnergyUsed = penaltyEnergyUsed.add(level1EnergyToApply);
        }

        // 3. Progressive Penalty Level 2 Tier: Cost > monthlyBudget * 1.5
        if (remainingEnergy.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal costForLevel2 = remainingEnergy.multiply(level2Rate);
            costDelta = costDelta.add(costForLevel2);
            currentCost = currentCost.add(costForLevel2);
            penaltyEnergyUsed = penaltyEnergyUsed.add(remainingEnergy);
        }

        BigDecimal newEnergy = accumulatedEnergy.add(energyDelta).setScale(ENERGY_SCALE, RoundingMode.HALF_UP);
        BigDecimal roundedCostDelta = costDelta.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newCost = accumulatedCost.add(roundedCostDelta).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        BigDecimal newUsage = calculateUsagePercentage(newCost, monthlyBudget);

        TariffState resultingTariff = newCost.compareTo(monthlyBudget) >= 0 ? TariffState.PENALTY : TariffState.NORMAL;
        boolean transitioned = currentTariff == TariffState.NORMAL && resultingTariff == TariffState.PENALTY;

        BigDecimal activeEffectiveRate = currentCost.compareTo(level1Cap) > 0 ? level2Rate : (currentCost.compareTo(monthlyBudget) > 0 ? level1Rate : normalRate);

        return new BillingResult(
                energyDelta.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                roundedCostDelta,
                newEnergy,
                newCost,
                currentTariff,
                resultingTariff,
                transitioned,
                previousUsage,
                newUsage,
                normalEnergyUsed.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                penaltyEnergyUsed.setScale(ENERGY_SCALE, RoundingMode.HALF_UP),
                normalRate,
                activeEffectiveRate
        );
    }

    public BigDecimal calculateUsagePercentage(BigDecimal cost, BigDecimal budget) {
        if (budget == null || budget.signum() <= 0) return BigDecimal.ZERO;
        return cost.multiply(HUNDRED).divide(budget, 4, RoundingMode.HALF_UP);
    }

    private void validateInputs(BigDecimal energy, BigDecimal cost, BigDecimal delta,
                                BigDecimal budget, BigDecimal rate, BigDecimal multiplier) {
        if (energy == null || energy.signum() < 0) throw new IllegalArgumentException("accumulatedEnergy must not be negative");
        if (cost == null || cost.signum() < 0) throw new IllegalArgumentException("accumulatedCost must not be negative");
        if (delta == null || delta.signum() < 0) throw new IllegalArgumentException("energyDelta must not be negative");
        if (budget == null || budget.signum() <= 0) throw new IllegalArgumentException("monthlyBudget must be positive");
        if (rate == null || rate.signum() <= 0) throw new IllegalArgumentException("normalRate must be positive");
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("penaltyMultiplier must be at least 1.0");
        }
    }

    public record BillingResult(
            BigDecimal energyDeltaKwh,
            BigDecimal costDelta,
            BigDecimal accumulatedEnergyKwh,
            BigDecimal accumulatedCost,
            TariffState previousTariff,
            TariffState tariffState,
            boolean tariffTransitioned,
            BigDecimal previousUsagePercent,
            BigDecimal usagePercent,
            BigDecimal normalRateEnergyKwh,
            BigDecimal penaltyRateEnergyKwh,
            BigDecimal normalRate,
            BigDecimal penaltyRate
    ) {}
}
