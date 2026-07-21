package com.voltwise.simulator.service;

import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.domain.OperatingState;
import com.voltwise.simulator.event.RegisteredAppliance;
import com.voltwise.simulator.generator.ApplianceSimulationState;
import com.voltwise.simulator.generator.GeneratedTelemetry;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class AnomalyInjector {

    private final SimulationProperties.Anomaly properties;

    public AnomalyInjector(SimulationProperties simulationProperties) {
        this.properties = simulationProperties.getAnomaly();
    }

    public GeneratedTelemetry apply(
            RegisteredAppliance appliance,
            ApplianceSimulationState state,
            RandomGenerator random,
            GeneratedTelemetry normalReading
    ) {
        if (state.getAnomalyCyclesRemaining() == 0 && shouldStartBurst(appliance, state, random)) {
            state.beginAnomalyBurst(properties.getBurstCycles());
        }

        if (state.getAnomalyCyclesRemaining() == 0) {
            return normalReading;
        }

        double spread = properties.getMaxPowerMultiplier() - properties.getMinPowerMultiplier();
        double multiplier = properties.getMinPowerMultiplier()
                + (spread == 0 ? 0 : random.nextDouble() * spread);
        BigDecimal anomalousPower = appliance.safePowerLimitWatts()
                .multiply(BigDecimal.valueOf(multiplier))
                .setScale(1, RoundingMode.HALF_UP);
        if (anomalousPower.compareTo(appliance.safePowerLimitWatts()) <= 0) {
            anomalousPower = appliance.safePowerLimitWatts().add(new BigDecimal("0.1"));
        }
        state.consumeAnomalyCycle(properties.getCooldownCycles());
        return new GeneratedTelemetry(anomalousPower, OperatingState.HIGH_LOAD);
    }

    private boolean shouldStartBurst(
            RegisteredAppliance appliance,
            ApplianceSimulationState state,
            RandomGenerator random
    ) {
        long nextCycle = state.getGeneratedCycles() + 1;
        if (isDemoCycle(appliance.applianceId(), nextCycle)) {
            return true;
        }
        return state.getAnomalyCooldownRemaining() == 0
                && properties.getProbability() > 0
                && (properties.getProbability() >= 1 || random.nextDouble() < properties.getProbability());
    }

    private boolean isDemoCycle(long applianceId, long cycle) {
        if (!properties.targetsForDemo(applianceId) || cycle < properties.getDemoStartCycle()) {
            return false;
        }
        if (cycle == properties.getDemoStartCycle()) {
            return true;
        }
        long repeat = properties.getDemoRepeatEveryCycles();
        return repeat > 0 && (cycle - properties.getDemoStartCycle()) % repeat == 0;
    }
}
