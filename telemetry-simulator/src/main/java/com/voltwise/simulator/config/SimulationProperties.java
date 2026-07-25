package com.voltwise.simulator.config;

import com.voltwise.simulator.domain.ApplianceType;
import jakarta.annotation.PostConstruct;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "simulation")
public class SimulationProperties {

    private boolean enabled = true;

    @Min(1)
    private long intervalMs = 1000;

    private long randomSeed = 42;

    @Min(1)
    private int simulationSpeed = 1;

    @Min(100)
    private int processedEventCacheSize = 10_000;

    @Valid
    private Anomaly anomaly = new Anomaly();

    private Map<ApplianceType, ApplianceProfile> profiles = DefaultApplianceProfiles.create();

    @PostConstruct
    public void validateConfiguration() {
        if (!profiles.keySet().containsAll(Set.of(ApplianceType.values()))) {
            throw new IllegalArgumentException("A simulation profile is required for every ApplianceType");
        }
        profiles.values().forEach(ApplianceProfile::validate);
        anomaly.setIntervalMs(intervalMs);
        anomaly.validate();
    }

    public ApplianceProfile profile(ApplianceType type) {
        ApplianceProfile profile = profiles.get(type);
        if (profile == null) {
            throw new IllegalStateException("No simulation profile configured for " + type);
        }
        return profile;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getIntervalMs() {
        return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
        this.intervalMs = intervalMs;
    }

    public long getRandomSeed() {
        return randomSeed;
    }

    public void setRandomSeed(long randomSeed) {
        this.randomSeed = randomSeed;
    }

    public int getSimulationSpeed() {
        return simulationSpeed;
    }

    public void setSimulationSpeed(int simulationSpeed) {
        this.simulationSpeed = simulationSpeed;
    }

    public int getProcessedEventCacheSize() {
        return processedEventCacheSize;
    }

    public void setProcessedEventCacheSize(int processedEventCacheSize) {
        this.processedEventCacheSize = processedEventCacheSize;
    }

    public Anomaly getAnomaly() {
        return anomaly;
    }

    public void setAnomaly(Anomaly anomaly) {
        this.anomaly = anomaly;
    }

    public Map<ApplianceType, ApplianceProfile> getProfiles() {
        return profiles;
    }

    public void setProfiles(Map<ApplianceType, ApplianceProfile> profiles) {
        Map<ApplianceType, ApplianceProfile> merged = DefaultApplianceProfiles.create();
        profiles.forEach((type, profile) -> merged.get(type).mergeFrom(profile));
        this.profiles = new EnumMap<>(merged);
    }

    public static class Anomaly {

        @DecimalMin("0.0")
        @DecimalMax("1.0")
        private double probability = 0.00001;

        /**
         * Optional: when positive, takes precedence over {@code probability} and is normalised
         * to a per-cycle value using the configured {@code intervalMs}.
         * Bind via {@code TELEMETRY_ANOMALY_RATE_PER_HOUR}.
         * Example: 1.0 → one fault event per appliance per hour on average.
         */
        @DecimalMin("0.0")
        private double ratePerHour = 0.0;

        /** intervalMs is injected by SimulationProperties so effectiveProbability() can normalise. */
        private long intervalMs = 1000;

        @Min(3)
        private int burstCycles = 3;

        @Min(0)
        private int cooldownCycles = 20;

        @DecimalMin(value = "1.0", inclusive = false)
        private double minPowerMultiplier = 1.05;

        @DecimalMin(value = "1.0", inclusive = false)
        private double maxPowerMultiplier = 1.25;

        private boolean demoEnabled;

        @Min(1)
        private long demoStartCycle = 5;

        @Min(0)
        private long demoRepeatEveryCycles;

        private Set<Long> demoApplianceIds = new LinkedHashSet<>();

        public void validate() {
            if (maxPowerMultiplier < minPowerMultiplier) {
                throw new IllegalArgumentException("Anomaly max multiplier must be >= min multiplier");
            }
            if (demoApplianceIds.stream().anyMatch(id -> id == null || id < 1)) {
                throw new IllegalArgumentException("Demo appliance IDs must be positive");
            }
        }

        public boolean targetsForDemo(long applianceId) {
            return demoEnabled && demoApplianceIds.contains(applianceId);
        }

        /**
         * Returns the per-cycle anomaly start probability, normalised for the current telemetry
         * interval. When {@code ratePerHour > 0} it overrides {@code probability}:
         * <pre>p = ratePerHour / 3600 * (intervalMs / 1000.0)</pre>
         * Otherwise the raw {@code probability} value is used as-is.
         */
        public double effectiveProbability() {
            if (ratePerHour > 0) {
                return Math.min(1.0, ratePerHour / 3600.0 * (intervalMs / 1000.0));
            }
            return probability;
        }

        public double getProbability() {
            return probability;
        }

        public void setProbability(double probability) {
            this.probability = probability;
        }

        public double getRatePerHour() {
            return ratePerHour;
        }

        public void setRatePerHour(double ratePerHour) {
            this.ratePerHour = ratePerHour;
        }

        public long getIntervalMs() {
            return intervalMs;
        }

        public void setIntervalMs(long intervalMs) {
            this.intervalMs = intervalMs;
        }

        public int getBurstCycles() {
            return burstCycles;
        }

        public void setBurstCycles(int burstCycles) {
            this.burstCycles = burstCycles;
        }

        public int getCooldownCycles() {
            return cooldownCycles;
        }

        public void setCooldownCycles(int cooldownCycles) {
            this.cooldownCycles = cooldownCycles;
        }

        public double getMinPowerMultiplier() {
            return minPowerMultiplier;
        }

        public void setMinPowerMultiplier(double minPowerMultiplier) {
            this.minPowerMultiplier = minPowerMultiplier;
        }

        public double getMaxPowerMultiplier() {
            return maxPowerMultiplier;
        }

        public void setMaxPowerMultiplier(double maxPowerMultiplier) {
            this.maxPowerMultiplier = maxPowerMultiplier;
        }

        public boolean isDemoEnabled() {
            return demoEnabled;
        }

        public void setDemoEnabled(boolean demoEnabled) {
            this.demoEnabled = demoEnabled;
        }

        public long getDemoStartCycle() {
            return demoStartCycle;
        }

        public void setDemoStartCycle(long demoStartCycle) {
            this.demoStartCycle = demoStartCycle;
        }

        public long getDemoRepeatEveryCycles() {
            return demoRepeatEveryCycles;
        }

        public void setDemoRepeatEveryCycles(long demoRepeatEveryCycles) {
            this.demoRepeatEveryCycles = demoRepeatEveryCycles;
        }

        public Set<Long> getDemoApplianceIds() {
            return Collections.unmodifiableSet(demoApplianceIds);
        }

        public void setDemoApplianceIds(Set<Long> demoApplianceIds) {
            this.demoApplianceIds = demoApplianceIds == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(demoApplianceIds);
        }
    }
}
