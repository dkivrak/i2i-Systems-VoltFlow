package com.voltwise.core.live;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/** Immutable metrics accumulated since the last snapshot rotation. */
public record SnapshotWindow(
        Instant startedAt,
        BigDecimal energyKwh,
        BigDecimal cost,
        BigDecimal powerSampleSumWatts,
        long powerSampleCount,
        BigDecimal maximumPowerWatts
) {
    public SnapshotWindow {
        energyKwh = valueOrZero(energyKwh);
        cost = valueOrZero(cost);
        powerSampleSumWatts = valueOrZero(powerSampleSumWatts);
        maximumPowerWatts = valueOrZero(maximumPowerWatts);
        if (powerSampleCount < 0) {
            throw new IllegalArgumentException("powerSampleCount must be non-negative");
        }
    }

    public static SnapshotWindow empty() {
        return empty(null);
    }

    public static SnapshotWindow empty(Instant startedAt) {
        return new SnapshotWindow(startedAt, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, BigDecimal.ZERO);
    }

    public SnapshotWindow add(
            Instant sampleTime,
            BigDecimal energyDeltaKwh,
            BigDecimal costDelta,
            BigDecimal powerWatts
    ) {
        BigDecimal samplePower = valueOrZero(powerWatts);
        return new SnapshotWindow(
                startedAt == null ? sampleTime : startedAt,
                energyKwh.add(valueOrZero(energyDeltaKwh)),
                cost.add(valueOrZero(costDelta)),
                powerSampleSumWatts.add(samplePower),
                powerSampleCount + 1,
                maximumPowerWatts.max(samplePower)
        );
    }

    public SnapshotWindow merge(SnapshotWindow other) {
        if (other == null) {
            return this;
        }
        return new SnapshotWindow(
                earliest(startedAt, other.startedAt),
                energyKwh.add(other.energyKwh),
                cost.add(other.cost),
                powerSampleSumWatts.add(other.powerSampleSumWatts),
                powerSampleCount + other.powerSampleCount,
                maximumPowerWatts.max(other.maximumPowerWatts)
        );
    }

    public boolean hasSamples() {
        return powerSampleCount > 0;
    }

    public BigDecimal averagePowerWatts() {
        if (!hasSamples()) {
            return BigDecimal.ZERO;
        }
        return powerSampleSumWatts.divide(BigDecimal.valueOf(powerSampleCount), 3, RoundingMode.HALF_UP);
    }

    private static BigDecimal valueOrZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static Instant earliest(Instant left, Instant right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.isBefore(right) ? left : right;
    }
}
