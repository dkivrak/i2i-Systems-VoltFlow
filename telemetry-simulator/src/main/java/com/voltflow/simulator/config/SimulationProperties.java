package com.voltflow.simulator.config;

import com.voltflow.simulator.domain.ApplianceType;
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
        private double probability = 0.02;

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

        public double getProbability() {
            return probability;
        }

        public void setProbability(double probability) {
            this.probability = probability;
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
