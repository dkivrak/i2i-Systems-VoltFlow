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
public class OvenTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.OVEN;
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
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.85));
            return reading(addNoise(base, 0.015, random), OperatingState.HIGH_LOAD);
        }

        // Check if we are currently in a cooking session
        boolean inSession = state.getLastActiveAt() != null;
        if (inSession) {
            long elapsedSeconds = Duration.between(state.getLastActiveAt(), now).getSeconds();
            if (elapsedSeconds >= 2700) {
                // Session ended
                state.transitionTo(OperationalState.STANDBY, "STANDBY", now);
                state.setLastActiveAt(null);
                inSession = false;
            }
        }

        // Transitions
        if (!inSession && state.getOperationalState() == OperationalState.STANDBY) {
            double prob = profile.probability("start");
            if (prob < 1.0) {
                double factor;
                if (isEvening(now)) {
                    factor = 0.0003;
                } else if (isMorning(now)) {
                    factor = 0.00007;
                } else if (isDaytime(now)) {
                    factor = 0.00003;
                } else {
                    factor = 0.0;
                }
                prob = prob * factor;
            }

            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "HEATING", now);
                state.setLastActiveAt(now);
                double activeFactor = 0.75 + random.nextDouble() * 0.15;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)));
                inSession = true;
            }
        } else if (inSession) {
            if (state.getOperationalState() == OperationalState.ACTIVE) {
                if ("HEATING".equals(state.getPhase())) {
                    if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 600) {
                        state.transitionTo(OperationalState.COOLDOWN, "THERMOSTAT_OFF", now);
                    }
                } else if ("THERMOSTAT_ON".equals(state.getPhase())) {
                    if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 300) {
                        state.transitionTo(OperationalState.COOLDOWN, "THERMOSTAT_OFF", now);
                    }
                }
            } else if (state.getOperationalState() == OperationalState.COOLDOWN) {
                if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 180) {
                    state.transitionTo(OperationalState.ACTIVE, "THERMOSTAT_ON", now);
                }
            }
        }

        // Output
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.015, random), OperatingState.HIGH_LOAD);
        } else if (state.getOperationalState() == OperationalState.COOLDOWN) {
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.015));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        } else {
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.015));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        }
    }
}
