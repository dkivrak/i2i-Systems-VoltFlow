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
public class AirConditionerTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.AIR_CONDITIONER;
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
            BigDecimal base = state.getSessionBaseWatts().signum() > 0
                    ? state.getSessionBaseWatts()
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.70));
            return reading(addNoise(base, 0.02, random), OperatingState.HIGH_LOAD);
        }

        // Session expiration check
        boolean inSession = state.getLastActiveAt() != null;
        if (inSession) {
            double turnOffProb = profile.probability("turn-off");
            if (turnOffProb < 1.0) {
                long elapsedSeconds = Duration.between(state.getLastActiveAt(), now).getSeconds();
                turnOffProb = (elapsedSeconds >= 14400) ? 1.0 : 0.00005;
            }
            if (chance(random, turnOffProb)) {
                state.transitionTo(OperationalState.STANDBY, "STANDBY", now);
                state.setLastActiveAt(null);
                inSession = false;
            }
        }

        // Transitions
        if (!inSession && state.getOperationalState() == OperationalState.STANDBY) {
            double prob = profile.probability("turn-on");
            if (prob < 1.0) {
                double factor = isDaytime(now) || isEvening(now) ? 0.0001 : 0.00004;
                prob = prob * factor;
            }
            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "COMPRESSOR", now);
                state.setLastActiveAt(now);
                double activeFactor = 0.60 + random.nextDouble() * 0.20;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)));
                inSession = true;
            }
        } else if (inSession) {
            if (state.getOperationalState() == OperationalState.ACTIVE) {
                // Compressor runs for 15 minutes (900 seconds)
                if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 900) {
                    state.transitionTo(OperationalState.COOLDOWN, "FAN", now);
                }
            } else if (state.getOperationalState() == OperationalState.COOLDOWN) {
                // Fan runs for 15 minutes (900 seconds)
                if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 900) {
                    state.transitionTo(OperationalState.ACTIVE, "COMPRESSOR", now);
                }
            }
        }

        // Output
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.02, random), OperatingState.ON);
        } else if (state.getOperationalState() == OperationalState.COOLDOWN) {
            BigDecimal fanBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.08));
            return reading(addNoise(fanBase, 0.05, random), OperatingState.ON);
        } else {
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.015));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        }
    }
}
