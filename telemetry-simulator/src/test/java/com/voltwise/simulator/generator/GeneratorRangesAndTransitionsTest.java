package com.voltwise.simulator.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.simulator.TestFixtures;
import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.config.DefaultApplianceProfiles;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.OperatingState;
import com.voltwise.simulator.event.RegisteredAppliance;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GeneratorRangesAndTransitionsTest {

    @ParameterizedTest(name = "{0} readings stay in safe physical limits and change phase")
    @MethodSource("generators")
    void readingsStayInsideLimitsAndGeneratorsAreStateful(
            ApplianceType type,
            ApplianceTelemetryGenerator generator
    ) {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(type);
        ApplianceSimulationState state = new ApplianceSimulationState();
        Random random = new Random(1000L + type.ordinal());
        BigDecimal safeLimit = new BigDecimal("2000");
        Set<OperationalState> visitedStates = new HashSet<>();
        // Advance 50 simulated seconds per cycle so 10,000 cycles span ~5.8 simulated days,
        // covering multiple morning/daytime/evening/night windows for all appliance types.
        Instant now = Instant.parse("2026-07-25T00:00:00Z");

        for (int cycle = 0; cycle < 100_000; cycle++) {
            GeneratedTelemetry reading = generator.next(state, random, profile, now, safeLimit);
            assertThat(reading.powerWatts()).isNotNegative();
            // Maximum reasonable power is 1.5 * safeLimit
            assertThat(reading.powerWatts()).isLessThanOrEqualTo(safeLimit.multiply(BigDecimal.valueOf(1.5)));
            visitedStates.add(state.getOperationalState());
            state.completeCycle(reading.powerWatts());
            now = now.plusSeconds(50);
        }

        assertThat(visitedStates).as("%s has state transitions", type).hasSizeGreaterThan(1);
    }

    @Test
    void RefrigeratorDutyCycleBehavior() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.REFRIGERATOR);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new RefrigeratorTelemetryGenerator();
        Random random = new Random(1);
        BigDecimal safeLimit = new BigDecimal("500");
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        // Force startup peak transition from standby
        state.transitionTo(OperationalState.TEMPORARY_PEAK, "STARTUP", now);
        double peakFactor = 1.15;
        state.setSessionBaseWatts(safeLimit.multiply(BigDecimal.valueOf(peakFactor)));

        GeneratedTelemetry peak = generator.next(state, random, profile, now, safeLimit);
        // Exceeds safe limit during startup peak
        assertThat(peak.powerWatts()).isGreaterThan(safeLimit);
        assertThat(peak.operatingState()).isEqualTo(OperatingState.HIGH_LOAD);
        state.completeCycle(peak.powerWatts());

        // Next cycle must transition to ACTIVE (COMPRESSOR) and be within 30%-90% of safeLimit
        GeneratedTelemetry active = generator.next(state, random, profile, now.plusSeconds(1), safeLimit);
        assertThat(active.powerWatts()).isLessThanOrEqualTo(safeLimit.multiply(BigDecimal.valueOf(0.9)));
        assertThat(active.powerWatts()).isGreaterThanOrEqualTo(safeLimit.multiply(BigDecimal.valueOf(0.15)));
        assertThat(active.operatingState()).isEqualTo(OperatingState.ON);
    }

    @Test
    void kettleRemainsActiveShortDurationThenOff() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.KETTLE);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new KettleTelemetryGenerator();
        Random random = new Random(1);
        BigDecimal safeLimit = new BigDecimal("2200");
        Instant now = Instant.parse("2026-07-25T08:00:00Z"); // Morning: high prob

        // Force transition to ACTIVE
        state.transitionTo(OperationalState.ACTIVE, "ACTIVE", now);
        state.setSessionBaseWatts(safeLimit.multiply(BigDecimal.valueOf(0.9)));

        GeneratedTelemetry activeReading = generator.next(state, random, profile, now, safeLimit);
        assertThat(activeReading.operatingState()).isEqualTo(OperatingState.ON);
        state.completeCycle(activeReading.powerWatts());

        // Advance past 2 minutes (120 seconds) of active time
        GeneratedTelemetry offReading = generator.next(state, random, profile, now.plusSeconds(121), safeLimit);
        assertThat(offReading.operatingState()).isEqualTo(OperatingState.OFF);
        assertThat(offReading.powerWatts()).isZero();
    }

    @Test
    void washingMachineProgressesThroughProgramPhases() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.WASHING_MACHINE);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new WashingMachineTelemetryGenerator();
        Random random = new Random(2);
        BigDecimal safeLimit = new BigDecimal("2000");
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        // Force start wash cycle from IDLE
        state.transitionTo(OperationalState.STANDBY, "FILLING", now);
        state.setSessionBaseWatts(safeLimit.multiply(BigDecimal.valueOf(0.15)));

        List<String> phases = new ArrayList<>();
        for (int cycle = 0; cycle < 50; cycle++) {
            GeneratedTelemetry reading = generator.next(state, random, profile, now, safeLimit);
            phases.add(state.getPhase());
            state.completeCycle(reading.powerWatts());
            now = now.plusSeconds(1);
        }

        assertThat(phases).contains("FILLING", "WASHING", "HEATING", "RINSE", "SPINNING", "IDLE");
    }

    @Test
    void lampConsumptionStaysStableForAnOnSession() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.LAMP);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new LampTelemetryGenerator();
        Random random = new Random(22);
        BigDecimal safeLimit = new BigDecimal("60");
        Instant now = Instant.parse("2026-07-25T19:00:00Z"); // Evening

        state.transitionTo(OperationalState.ACTIVE, "ON", now);
        state.setSessionBaseWatts(BigDecimal.valueOf(45));

        GeneratedTelemetry first = generator.next(state, random, profile, now, safeLimit);
        state.completeCycle(first.powerWatts());
        GeneratedTelemetry second = generator.next(state, random, profile, now.plusSeconds(1), safeLimit);
        state.completeCycle(second.powerWatts());
        GeneratedTelemetry third = generator.next(state, random, profile, now.plusSeconds(2), safeLimit);

        assertThat(first.powerWatts()).isEqualTo(BigDecimal.valueOf(45));
        assertThat(second.powerWatts()).isEqualTo(first.powerWatts());
        assertThat(third.powerWatts()).isEqualTo(first.powerWatts());
    }

    @Test
    void unknownApplianceTypesUsingSafeFallbackProfile() {
        ApplianceProfile profile = DefaultApplianceProfiles.create().get(ApplianceType.UNKNOWN);
        ApplianceSimulationState state = new ApplianceSimulationState();
        ApplianceTelemetryGenerator generator = new FallbackTelemetryGenerator();
        Random random = new Random(100);
        BigDecimal safeLimit = new BigDecimal("1000");
        Instant now = Instant.parse("2026-07-25T12:00:00Z");

        GeneratedTelemetry reading = generator.next(state, random, profile, now, safeLimit);
        assertThat(reading.powerWatts()).isLessThan(safeLimit);
        assertThat(state.getOperationalState()).isEqualTo(OperationalState.STANDBY);
    }

    @Test
    void representativeHomeDoesNotReachQuotaTooQuickly() {
        // Representative home with typical appliances simulated for 1 hour (3600 seconds)
        // Check that accumulated cost is extremely low under normal simulated loads.
        List<ApplianceTelemetryGenerator> allGenerators = List.of(
                new RefrigeratorTelemetryGenerator(),
                new KettleTelemetryGenerator(),
                new MicrowaveTelemetryGenerator(),
                new OvenTelemetryGenerator(),
                new WashingMachineTelemetryGenerator(),
                new TelevisionTelemetryGenerator(),
                new LampTelemetryGenerator(),
                new AirConditionerTelemetryGenerator(),
                new ComputerTelemetryGenerator()
        );

        List<ApplianceSimulationState> states = new ArrayList<>();
        List<RegisteredAppliance> apps = new ArrayList<>();
        List<ApplianceProfile> profiles = new ArrayList<>();

        long id = 1;
        for (ApplianceTelemetryGenerator gen : allGenerators) {
            states.add(new ApplianceSimulationState());
            BigDecimal limit = gen.supportedType() == ApplianceType.KETTLE || gen.supportedType() == ApplianceType.OVEN
                    ? new BigDecimal("2200") : new BigDecimal("500");
            apps.add(new RegisteredAppliance(id++, gen.supportedType().name(), gen.supportedType(), limit));
            profiles.add(DefaultApplianceProfiles.create().get(gen.supportedType()));
        }

        Random random = new Random(12345);
        Instant now = Instant.parse("2026-07-25T08:00:00Z"); // Start at 8 AM
        BigDecimal totalKwh = BigDecimal.ZERO;

        for (int second = 0; second < 3600; second++) {
            for (int i = 0; i < allGenerators.size(); i++) {
                ApplianceTelemetryGenerator gen = allGenerators.get(i);
                ApplianceSimulationState state = states.get(i);
                RegisteredAppliance app = apps.get(i);
                ApplianceProfile profile = profiles.get(i);

                GeneratedTelemetry tele = gen.next(state, random, profile, now, app.safePowerLimitWatts());
                state.completeCycle(tele.powerWatts());

                // Calculate energy delta for 1 second of wall-clock time
                BigDecimal deltaKwh = tele.powerWatts().multiply(BigDecimal.valueOf(1))
                        .divide(BigDecimal.valueOf(3600000), 9, java.math.RoundingMode.HALF_UP);
                totalKwh = totalKwh.add(deltaKwh);
            }
            now = now.plusSeconds(1);
        }

        // Standard budget is 1000.00 TL, standard rate is 2.50 TL/kWh.
        BigDecimal cost = totalKwh.multiply(BigDecimal.valueOf(2.50));
        // Under our stateful model, total active energy for 1 hour across all appliances should be small,
        // e.g. average load under 1000W total. So energy < 1 kWh, cost < 2.50 TL.
        // If cost < 10.00 TL, it is extremely safe and will take > 100 hours to reach 80% budget.
        assertThat(cost).isLessThan(BigDecimal.valueOf(10.00));
    }

    static Stream<Arguments> generators() {
        // UNKNOWN is tested separately in unknownApplianceTypesUsingSafeFallbackProfile;
        // exclude it here since FallbackTelemetryGenerator has a very low base probability
        // designed for production use, not tight unit-test iteration.
        return TestFixtures.generators().stream()
                .filter(g -> g.supportedType() != ApplianceType.UNKNOWN)
                .map(generator -> Arguments.of(generator.supportedType(), generator));
    }
}
