package com.voltwise.core.telemetry;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;

@Component
public class EnergyCalculator {
    private static final BigDecimal WATT_SECONDS_PER_KWH = new BigDecimal("3600000");

    public BigDecimal calculateDeltaKwh(BigDecimal powerWatts, Instant previousAt, Instant currentAt,
                                        long maximumGapSeconds) {
        if (powerWatts == null || powerWatts.signum() < 0) {
            throw new IllegalArgumentException("powerWatts must be zero or greater");
        }
        if (previousAt == null || currentAt == null || !currentAt.isAfter(previousAt)) {
            return BigDecimal.ZERO.setScale(9, RoundingMode.HALF_UP);
        }
        long nanos = Duration.between(previousAt, currentAt).toNanos();
        BigDecimal elapsedSeconds = BigDecimal.valueOf(nanos, 9)
                .min(BigDecimal.valueOf(maximumGapSeconds));
        return powerWatts.multiply(elapsedSeconds)
                .divide(WATT_SECONDS_PER_KWH, 9, RoundingMode.HALF_UP);
    }
}
