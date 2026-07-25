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
public class ComputerTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.COMPUTER;
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
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.50));
            return reading(addNoise(base, 0.02, random), OperatingState.HIGH_LOAD);
        }

        // Check active session expiration
        boolean inSession = state.getLastActiveAt() != null;
        if (inSession) {
            double turnOffProb = profile.probability("power-off");
            if (turnOffProb < 1.0) {
                long elapsedSeconds = Duration.between(state.getLastActiveAt(), now).getSeconds();
                turnOffProb = (elapsedSeconds >= 10800) ? 1.0 : 0.00005;
            }
            if (chance(random, turnOffProb)) {
                state.transitionTo(OperationalState.STANDBY, "STANDBY", now);
                state.setLastActiveAt(null);
                inSession = false;
            }
        }

        // Transitions
        if (!inSession && state.getOperationalState() == OperationalState.STANDBY) {
            double prob = profile.probability("power-on");
            if (prob < 1.0) {
                double factor = isDaytime(now) || isEvening(now) ? 0.0002 : (isMorning(now) ? 0.00006 : 0.00002);
                prob = prob * factor;
            }

            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "NORMAL", now);
                state.setLastActiveAt(now);
                double activeFactor = 0.30 + random.nextDouble() * 0.20;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)));
                inSession = true;
            }
        } else if (inSession) {
            if (state.getOperationalState() == OperationalState.ACTIVE) {
                double highLoadProb = profile.probability("high-load");
                if (highLoadProb < 1.0) {
                    highLoadProb = highLoadProb * 0.001; // Scale down for real-time
                }
                if (chance(random, highLoadProb)) {
                    state.transitionTo(OperationalState.TEMPORARY_PEAK, "HIGH_LOAD", now);
                }
            } else if (state.getOperationalState() == OperationalState.TEMPORARY_PEAK) {
                // High load peak lasts 20 minutes (1200 seconds)
                if (Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 1200) {
                    state.transitionTo(OperationalState.ACTIVE, "NORMAL", now);
                }
            }
        }

        // Output
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.02, random), OperatingState.ON);
        } else if (state.getOperationalState() == OperationalState.TEMPORARY_PEAK) {
            BigDecimal peakBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.75));
            return reading(addNoise(peakBase, 0.02, random), OperatingState.HIGH_LOAD);
        } else {
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.035));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        }
    }
}
