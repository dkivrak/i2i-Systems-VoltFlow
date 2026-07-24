package com.voltflow.simulator.runtime;

import com.voltflow.simulator.config.SimulationProperties;
import com.voltflow.simulator.event.RegisteredAppliance;
import com.voltflow.simulator.generator.ApplianceSimulationState;
import com.voltflow.simulator.generator.ApplianceTelemetryGenerator;
import com.voltflow.simulator.generator.GeneratedTelemetry;
import com.voltflow.simulator.service.AnomalyInjector;
import java.util.Random;

public final class ApplianceRuntime {

    private final long homeId;
    private final Random random;
    private final ApplianceSimulationState state = new ApplianceSimulationState();
    private volatile RegisteredAppliance appliance;

    ApplianceRuntime(long homeId, RegisteredAppliance appliance, long randomSeed) {
        this.homeId = homeId;
        this.appliance = appliance;
        this.random = new Random(randomSeed);
    }

    public synchronized GeneratedTelemetry generate(
            ApplianceTelemetryGenerator generator,
            SimulationProperties properties,
            AnomalyInjector anomalyInjector
    ) {
        GeneratedTelemetry normal = generator.next(state, random, properties.profile(appliance.type()));
        state.setNominalPowerWatts(normal.powerWatts());
        GeneratedTelemetry result = anomalyInjector.apply(appliance, state, random, normal);
        state.completeCycle(result.powerWatts());
        return result;
    }

    void update(RegisteredAppliance updated) {
        if (updated.type() != appliance.type()) {
            throw new IllegalArgumentException("An appliance type cannot change after registration");
        }
        appliance = updated;
    }

    public long homeId() {
        return homeId;
    }

    public RegisteredAppliance appliance() {
        return appliance;
    }

    public ApplianceSimulationState state() {
        return state;
    }
}
