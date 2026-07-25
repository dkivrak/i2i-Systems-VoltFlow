package com.voltwise.simulator.generator;

import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;

abstract class GeneratorSupport {

    protected boolean chance(RandomGenerator random, double probability) {
        return probability > 0 && (probability >= 1 || random.nextDouble() < probability);
    }

    protected boolean durationReached(ApplianceSimulationState state, ApplianceProfile profile, String key) {
        return state.getCyclesInPhase() >= profile.duration(key);
    }

    protected GeneratedTelemetry reading(
            ApplianceProfile profile,
            String range,
            OperatingState operatingState,
            RandomGenerator random
    ) {
        return new GeneratedTelemetry(profile.sample(range, random), operatingState);
    }

    protected GeneratedTelemetry reading(java.math.BigDecimal powerWatts, OperatingState operatingState) {
        return new GeneratedTelemetry(powerWatts, operatingState);
    }

    protected int getHourOfDay(java.time.Instant now) {
        return now.atZone(java.time.ZoneOffset.UTC).getHour();
    }

    protected boolean isMorning(java.time.Instant now) {
        int hour = getHourOfDay(now);
        return hour >= 6 && hour < 10;
    }

    protected boolean isDaytime(java.time.Instant now) {
        int hour = getHourOfDay(now);
        return hour >= 10 && hour < 17;
    }

    protected boolean isEvening(java.time.Instant now) {
        int hour = getHourOfDay(now);
        return hour >= 17 && hour < 23;
    }

    protected boolean isNight(java.time.Instant now) {
        int hour = getHourOfDay(now);
        return hour >= 23 || hour < 6;
    }

    protected java.math.BigDecimal addNoise(java.math.BigDecimal baseWatts, double noisePercent, RandomGenerator random) {
        if (baseWatts.compareTo(java.math.BigDecimal.ZERO) <= 0) {
            return java.math.BigDecimal.ZERO;
        }
        double factor = 1.0 + (random.nextDouble() * 2.0 - 1.0) * noisePercent;
        java.math.BigDecimal withNoise = baseWatts.multiply(java.math.BigDecimal.valueOf(factor));
        if (withNoise.compareTo(java.math.BigDecimal.ZERO) < 0) {
            return java.math.BigDecimal.ZERO;
        }
        return withNoise.setScale(1, java.math.RoundingMode.HALF_UP);
    }
}
