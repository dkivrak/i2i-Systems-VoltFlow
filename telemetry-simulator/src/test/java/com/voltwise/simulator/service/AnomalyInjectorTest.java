package com.voltwise.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.OperatingState;
import com.voltwise.simulator.event.RegisteredAppliance;
import com.voltwise.simulator.generator.ApplianceSimulationState;
import com.voltwise.simulator.generator.GeneratedTelemetry;
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

    @Test
    void effectiveProbabilityUsesRawProbabilityWhenRatePerHourIsZero() {
        SimulationProperties properties = new SimulationProperties();
        properties.getAnomaly().setProbability(0.00001);
        properties.getAnomaly().setRatePerHour(0.0);   // disabled
        properties.getAnomaly().setIntervalMs(1000);

        assertThat(properties.getAnomaly().effectiveProbability())
                .as("raw probability used when ratePerHour=0")
                .isEqualTo(0.00001);
    }

    @Test
    void ratePerHourOverrideNormalizesCorrectlyAcrossIntervals() {
        SimulationProperties properties = new SimulationProperties();
        properties.getAnomaly().setProbability(0.0); // overridden by rate
        properties.getAnomaly().setRatePerHour(3.6); // 3.6 faults/hour

        // At 1000 ms interval: p = 3.6 / 3600 * 1.0 = 0.001
        properties.getAnomaly().setIntervalMs(1000);
        assertThat(properties.getAnomaly().effectiveProbability())
                .as("effective probability at 1 s/cycle")
                .isEqualTo(0.001, org.assertj.core.data.Offset.offset(1e-9));

        // At 100 ms interval: p = 3.6 / 3600 * 0.1 = 0.0001
        properties.getAnomaly().setIntervalMs(100);
        assertThat(properties.getAnomaly().effectiveProbability())
                .as("effective probability scales down at 100 ms/cycle")
                .isEqualTo(0.0001, org.assertj.core.data.Offset.offset(1e-9));

        // At 10 000 ms interval: p = 3.6 / 3600 * 10 = 0.01
        properties.getAnomaly().setIntervalMs(10000);
        assertThat(properties.getAnomaly().effectiveProbability())
                .as("effective probability scales up at 10 s/cycle")
                .isEqualTo(0.01, org.assertj.core.data.Offset.offset(1e-9));
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
