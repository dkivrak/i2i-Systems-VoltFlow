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
public class LampTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.LAMP;
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
            return reading(base, OperatingState.HIGH_LOAD);
        }

        // Transitions
        if (state.getOperationalState() == OperationalState.OFF) {
            double prob = profile.probability("turn-on");
            if (prob < 1.0) {
                int hour = getHourOfDay(now);
                double timeFactor = 0.0;
                if (isEvening(now)) {
                    timeFactor = 0.0003;
                } else if (isMorning(now)) {
                    timeFactor = 0.00005;
                } else if (hour >= 23 || hour < 1) { // Late night before sleeping
                    timeFactor = 0.0001;
                }
                prob = prob * timeFactor;
            }

            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "ON", now);
                // Stable low consumption: 0.60 to 0.85 of safe limit
                double activeFactor = 0.60 + random.nextDouble() * 0.25;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)).setScale(1, java.math.RoundingMode.HALF_UP));
            }
        } else if (state.getOperationalState() == OperationalState.ACTIVE) {
            double turnOffProb = profile.probability("turn-off");
            if (turnOffProb < 1.0) {
                // Determine if it should turn off based on duration or daytime
                boolean durationLimit = Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 14400;
                boolean isDay = isDaytime(now);
                turnOffProb = (durationLimit || isDay) ? 1.0 : 0.00005;
            }

            if (chance(random, turnOffProb)) {
                state.transitionTo(OperationalState.OFF, "OFF", now);
            }
        }

        // Output (Lamp has stable zero-noise consumption during session)
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(state.getSessionBaseWatts(), OperatingState.ON);
        } else {
            return reading(BigDecimal.ZERO, OperatingState.OFF);
        }
    }
}
