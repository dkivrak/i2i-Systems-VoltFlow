package com.voltflow.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltflow.simulator.config.SimulationProperties;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import com.voltflow.simulator.event.RegisteredAppliance;
import com.voltflow.simulator.generator.ApplianceSimulationState;
import com.voltflow.simulator.generator.GeneratedTelemetry;
import java.math.BigDecimal;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AnomalyInjectorTest {

    @Test
    void deterministicDemoCreatesExactlyThreeConsecutiveOverLimitReadings() {
        SimulationProperties properties = new SimulationProperties();
        properties.getAnomaly().setProbability(0);
        properties.getAnomaly().setBurstCycles(3);
        properties.getAnomaly().setDemoEnabled(true);
        properties.getAnomaly().setDemoStartCycle(2);
        properties.getAnomaly().setDemoApplianceIds(Set.of(10L));
        AnomalyInjector injector = new AnomalyInjector(properties);
        RegisteredAppliance appliance = new RegisteredAppliance(
                10L, "Kettle", ApplianceType.KETTLE, new BigDecimal("2200")
        );
        ApplianceSimulationState state = new ApplianceSimulationState();
        GeneratedTelemetry normal = new GeneratedTelemetry(new BigDecimal("1800"), OperatingState.ON);
        Random random = new Random(123);

        GeneratedTelemetry cycle1 = applyAndComplete(injector, appliance, state, random, normal);
        GeneratedTelemetry cycle2 = applyAndComplete(injector, appliance, state, random, normal);
        GeneratedTelemetry cycle3 = applyAndComplete(injector, appliance, state, random, normal);
        GeneratedTelemetry cycle4 = applyAndComplete(injector, appliance, state, random, normal);
        GeneratedTelemetry cycle5 = applyAndComplete(injector, appliance, state, random, normal);

        assertThat(cycle1.powerWatts()).isLessThanOrEqualTo(appliance.safePowerLimitWatts());
        assertThat(cycle2.powerWatts()).isGreaterThan(appliance.safePowerLimitWatts());
        assertThat(cycle3.powerWatts()).isGreaterThan(appliance.safePowerLimitWatts());
        assertThat(cycle4.powerWatts()).isGreaterThan(appliance.safePowerLimitWatts());
        assertThat(cycle2.operatingState()).isEqualTo(OperatingState.HIGH_LOAD);
        assertThat(cycle5).isEqualTo(normal);
    }

    @Test
    void demoSequenceDoesNotAffectUntargetedAppliances() {
        SimulationProperties properties = new SimulationProperties();
        properties.getAnomaly().setProbability(0);
        properties.getAnomaly().setDemoEnabled(true);
        properties.getAnomaly().setDemoStartCycle(1);
        properties.getAnomaly().setDemoApplianceIds(Set.of(99L));
        AnomalyInjector injector = new AnomalyInjector(properties);
        RegisteredAppliance appliance = new RegisteredAppliance(
                10L, "Kettle", ApplianceType.KETTLE, new BigDecimal("2200")
        );
        GeneratedTelemetry normal = new GeneratedTelemetry(new BigDecimal("1800"), OperatingState.ON);

        assertThat(injector.apply(appliance, new ApplianceSimulationState(), new Random(1), normal))
                .isEqualTo(normal);
    }

    private GeneratedTelemetry applyAndComplete(
            AnomalyInjector injector,
            RegisteredAppliance appliance,
            ApplianceSimulationState state,
            Random random,
            GeneratedTelemetry normal
    ) {
        GeneratedTelemetry result = injector.apply(appliance, state, random, normal);
        state.completeCycle(result.powerWatts());
        return result;
    }
}
