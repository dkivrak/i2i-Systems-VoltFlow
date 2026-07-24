package com.voltflow.simulator.config;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;

public class WattRange {

    private Double minWatts;
    private Double maxWatts;

    public WattRange() {
    }

    public WattRange(double minWatts, double maxWatts) {
        this.minWatts = minWatts;
        this.maxWatts = maxWatts;
        validate();
    }

    public BigDecimal sample(RandomGenerator random) {
        validate();
        double sampled = minWatts.equals(maxWatts)
                ? minWatts
                : minWatts + random.nextDouble() * (maxWatts - minWatts);
        return BigDecimal.valueOf(sampled).setScale(1, RoundingMode.HALF_UP);
    }

    public boolean contains(BigDecimal watts) {
        validate();
        double value = watts.doubleValue();
        double roundingTolerance = 0.051;
        return value >= minWatts - roundingTolerance && value <= maxWatts + roundingTolerance;
    }

    public void validate() {
        if (minWatts == null || maxWatts == null
                || !Double.isFinite(minWatts) || !Double.isFinite(maxWatts)
                || minWatts < 0 || maxWatts < minWatts) {
            throw new IllegalArgumentException("Invalid Watt range: " + minWatts + ".." + maxWatts);
        }
    }

    public Double getMinWatts() {
        return minWatts;
    }

    public void setMinWatts(Double minWatts) {
        this.minWatts = minWatts;
    }

    public Double getMaxWatts() {
        return maxWatts;
    }

    public void setMaxWatts(Double maxWatts) {
        this.maxWatts = maxWatts;
    }

    void mergeFrom(WattRange override) {
        if (override.minWatts != null) {
            minWatts = override.minWatts;
        }
        if (override.maxWatts != null) {
            maxWatts = override.maxWatts;
        }
    }
}
