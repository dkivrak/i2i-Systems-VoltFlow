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
public class MicrowaveTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.MICROWAVE;
    }

    @Override
    public GeneratedTelemetry next(
            ApplianceSimulationState state,
            RandomGenerator random,
            ApplianceProfile profile,
            Instant now,
            BigDecimal safePowerLimitWatts
    ) {
        state.initialize(OperationalState.OFF, "OFF", now);

        if (state.getOperationalState() == OperationalState.FAULT) {
            BigDecimal base = state.getSessionBaseWatts().signum() > 0
                    ? state.getSessionBaseWatts()
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.80));
            return reading(addNoise(base, 0.015, random), OperatingState.HIGH_LOAD);
        }

        // Transitions
        if (state.getOperationalState() == OperationalState.OFF) {
            double prob = profile.probability("start");
            if (prob < 1.0) {
                double factor;
                if (isEvening(now)) {
                    factor = 0.0005;
                } else if (isMorning(now)) {
                    factor = 0.0003;
                } else if (isDaytime(now)) {
                    factor = 0.0001;
                } else {
                    factor = 0.00002;
                }
                prob = prob * factor;
            }

            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "ACTIVE", now);
                double activeFactor = 0.70 + random.nextDouble() * 0.15;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)));
            }
        } else if (state.getOperationalState() == OperationalState.ACTIVE) {
            if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 90) {
                state.transitionTo(OperationalState.OFF, "OFF", now);
            }
        }

        // Output
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.015, random), OperatingState.ON);
        } else {
            return reading(BigDecimal.ZERO, OperatingState.OFF);
        }
    }
}
