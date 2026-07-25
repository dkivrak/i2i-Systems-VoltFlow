package com.voltwise.simulator.generator;

import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.OperatingState;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class FallbackTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.UNKNOWN;
    }

    @Override
    public GeneratedTelemetry next(
            ApplianceSimulationState state,
            RandomGenerator random,
            ApplianceProfile profile,
            Instant now,
            BigDecimal safePowerLimitWatts
    ) {
        state.initialize(OperationalState.STANDBY, "STANDBY", now);

        if (state.getOperationalState() == OperationalState.FAULT) {
            // Keep current FAULT behavior (it is managed by AnomalyInjector)
            BigDecimal base = state.getSessionBaseWatts().signum() > 0
                    ? state.getSessionBaseWatts()
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.5));
            return reading(addNoise(base, 0.015, random), OperatingState.HIGH_LOAD);
        }

        // Check state transitions
        if (state.getOperationalState() == OperationalState.STANDBY) {
            // Chance to start (0.00005 per simulated second)
            double hourlyProb = 0.05; // 5% chance of starting per hour under normal conditions
            double secondsPerCycle = 1.0;
            // In case we want to scale with simulation speed:
            double prob = 0.00005;
            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "ACTIVE", now);
                // Set stable active nominal watt value (e.g. 50% to 70% of safe limit)
                double factor = 0.50 + random.nextDouble() * 0.20;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(factor)));
            }
        } else if (state.getOperationalState() == OperationalState.ACTIVE) {
            // Active session lasts 30 minutes (1800 simulated seconds)
            if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 1800) {
                state.transitionTo(OperationalState.STANDBY, "STANDBY", now);
            }
        }

        // Generate reading based on state
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.015, random), OperatingState.ON);
        } else {
            // Standby uses 1% to 2% of safePowerLimitWatts
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.015));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        }
    }
}
