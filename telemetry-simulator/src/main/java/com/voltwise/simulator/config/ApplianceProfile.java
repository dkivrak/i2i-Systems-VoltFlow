package com.voltwise.simulator.config;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.random.RandomGenerator;

public class ApplianceProfile {

    private Map<String, WattRange> ranges = new LinkedHashMap<>();
    private Map<String, Double> probabilities = new LinkedHashMap<>();
    private Map<String, Integer> durations = new LinkedHashMap<>();

    public ApplianceProfile range(String name, double minWatts, double maxWatts) {
        ranges.put(name, new WattRange(minWatts, maxWatts));
        return this;
    }

    public ApplianceProfile probability(String name, double probability) {
        probabilities.put(name, probability);
        return this;
    }

    public ApplianceProfile duration(String name, int cycles) {
        durations.put(name, cycles);
        return this;
    }

    public BigDecimal sample(String range, RandomGenerator random) {
        return requiredRange(range).sample(random);
    }

    public WattRange requiredRange(String name) {
        WattRange value = ranges.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing configured Watt range: " + name);
        }
        return value;
    }

    public double probability(String name) {
        Double value = probabilities.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing configured transition probability: " + name);
        }
        return value;
    }

    public int duration(String name) {
        Integer value = durations.get(name);
        if (value == null) {
            throw new IllegalStateException("Missing configured state duration: " + name);
        }
        return value;
    }

    public void validate() {
        if (ranges.isEmpty()) {
            throw new IllegalArgumentException("Each appliance profile must define Watt ranges");
        }
        ranges.values().forEach(WattRange::validate);
        probabilities.forEach((name, value) -> {
            if (value == null || !Double.isFinite(value) || value < 0 || value > 1) {
                throw new IllegalArgumentException("Probability " + name + " must be between 0 and 1");
            }
        });
        durations.forEach((name, value) -> {
            if (value == null || value < 1) {
                throw new IllegalArgumentException("Duration " + name + " must be at least one cycle");
            }
        });
    }

    public boolean contains(BigDecimal watts) {
        return ranges.values().stream().anyMatch(range -> range.contains(watts));
    }

    public Map<String, WattRange> getRanges() {
        return ranges;
    }

    public void setRanges(Map<String, WattRange> ranges) {
        this.ranges = ranges;
    }

    public Map<String, Double> getProbabilities() {
        return probabilities;
    }

    public void setProbabilities(Map<String, Double> probabilities) {
        this.probabilities = probabilities;
    }

    public Map<String, Integer> getDurations() {
        return durations;
    }

    public void setDurations(Map<String, Integer> durations) {
        this.durations = durations;
    }

    void mergeFrom(ApplianceProfile override) {
        override.ranges.forEach((name, range) -> {
            WattRange existing = ranges.get(name);
            if (existing == null) {
                ranges.put(name, range);
            } else {
                existing.mergeFrom(range);
            }
        });
        probabilities.putAll(override.probabilities);
        durations.putAll(override.durations);
    }
}
