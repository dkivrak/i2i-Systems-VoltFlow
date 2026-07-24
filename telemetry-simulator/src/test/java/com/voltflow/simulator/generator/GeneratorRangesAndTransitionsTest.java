package com.voltflow.simulator.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltflow.simulator.TestFixtures;
import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.config.DefaultApplianceProfiles;
import com.voltflow.simulator.domain.ApplianceType;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GeneratorRangesAndTransitionsTest {

    @ParameterizedTest(name = "{0} readings stay in configured ranges and change phase")
    @MethodSource("generators")
    void readingsStayInsideCentralizedRangesAndGeneratorsAreStateful(
            ApplianceType type,
            ApplianceTelemetryGenerator generator
    ) {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(type);
        ApplianceSimulationState state = new ApplianceSimulationState();
        Random random = new Random(1000L + type.ordinal());
        Set<String> visitedPhases = new HashSet<>();

        for (int cycle = 0; cycle < 2_000; cycle++) {
            GeneratedTelemetry reading = generator.next(state, random, profile);
            assertThat(reading.powerWatts()).isNotNegative();
            assertThat(profile.contains(reading.powerWatts()))
                    .as("%s power %s is in one configured range", type, reading.powerWatts())
                    .isTrue();
            visitedPhases.add(state.getPhase());
            state.completeCycle(reading.powerWatts());
        }

        assertThat(visitedPhases).as("%s has state transitions", type).hasSizeGreaterThan(1);
    }

    @Test
    void kettleRemainsActiveForConfiguredCyclesThenReturnsOff() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.KETTLE);
        profile.getProbabilities().put("start", 1.0);
        profile.getDurations().put("active", 2);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new KettleTelemetryGenerator();
        Random random = new Random(1);

        GeneratedTelemetry first = nextAndComplete(generator, state, random, profile);
        GeneratedTelemetry second = nextAndComplete(generator, state, random, profile);
        GeneratedTelemetry third = nextAndComplete(generator, state, random, profile);

        assertThat(first.operatingState()).isEqualTo(com.voltflow.simulator.domain.OperatingState.ON);
        assertThat(second.operatingState()).isEqualTo(com.voltflow.simulator.domain.OperatingState.ON);
        assertThat(third.operatingState()).isEqualTo(com.voltflow.simulator.domain.OperatingState.OFF);
        assertThat(state.getPhase()).isEqualTo("OFF");
    }

    @Test
    void washingMachineProgressesThroughProgramPhases() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.WASHING_MACHINE);
        profile.getProbabilities().put("start", 1.0);
        profile.getDurations().replaceAll((ignored, value) -> 1);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new WashingMachineTelemetryGenerator();
        Random random = new Random(2);
        Set<String> phases = new java.util.LinkedHashSet<>();

        for (int cycle = 0; cycle < 6; cycle++) {
            nextAndComplete(generator, state, random, profile);
            phases.add(state.getPhase());
        }

        assertThat(phases).containsExactly("FILLING", "WASHING", "HEATING", "RINSE", "SPINNING", "IDLE");
    }

    @Test
    void lampConsumptionStaysStableForAnOnSession() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.LAMP);
        profile.getProbabilities().put("turn-on", 1.0);
        profile.getProbabilities().put("turn-off", 0.0);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new LampTelemetryGenerator();
        Random random = new Random(22);

        GeneratedTelemetry first = nextAndComplete(generator, state, random, profile);
        GeneratedTelemetry second = nextAndComplete(generator, state, random, profile);
        GeneratedTelemetry third = nextAndComplete(generator, state, random, profile);

        assertThat(first.powerWatts()).isPositive();
        assertThat(second.powerWatts()).isEqualByComparingTo(first.powerWatts());
        assertThat(third.powerWatts()).isEqualByComparingTo(first.powerWatts());
    }

    static Stream<Arguments> generators() {
        return TestFixtures.generators().stream()
                .map(generator -> Arguments.of(generator.supportedType(), generator));
    }

    private GeneratedTelemetry nextAndComplete(
            ApplianceTelemetryGenerator generator,
            ApplianceSimulationState state,
            Random random,
            ApplianceProfile profile
    ) {
        GeneratedTelemetry result = generator.next(state, random, profile);
        state.completeCycle(result.powerWatts());
        return result;
    }
}
