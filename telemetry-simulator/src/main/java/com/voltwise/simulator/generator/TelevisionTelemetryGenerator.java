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
public class TelevisionTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.TELEVISION;
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
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.60));
            return reading(addNoise(base, 0.01, random), OperatingState.HIGH_LOAD);
        }

        // Transitions
        if (state.getOperationalState() == OperationalState.STANDBY) {
            double prob = profile.probability("turn-on");
            if (prob < 1.0) {
                double factor;
                if (isEvening(now)) {
                    factor = 0.0003;
                } else if (isMorning(now) || isDaytime(now)) {
                    factor = 0.00005;
                } else {
                    factor = 0.00002;
                }
                prob = prob * factor;
            }

            if (chance(random, prob)) {
                state.transitionTo(OperationalState.ACTIVE, "ON", now);
                double activeFactor = 0.40 + random.nextDouble() * 0.30;
                state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(activeFactor)));
            }
        } else if (state.getOperationalState() == OperationalState.ACTIVE) {
            double turnOffProb = profile.probability("turn-off");
            if (turnOffProb < 1.0) {
                boolean limitReached = Duration.between(state.getPhaseStartedAt(), now).getSeconds() >= 7200;
                turnOffProb = limitReached ? 1.0 : 0.0001;
            }
            if (chance(random, turnOffProb)) {
                state.transitionTo(OperationalState.STANDBY, "STANDBY", now);
            }
        }

        // Output
        if (state.getOperationalState() == OperationalState.ACTIVE) {
            return reading(addNoise(state.getSessionBaseWatts(), 0.01, random), OperatingState.ON);
        } else {
            BigDecimal standbyBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.05));
            return reading(addNoise(standbyBase, 0.05, random), OperatingState.STANDBY);
        }
    }
}
