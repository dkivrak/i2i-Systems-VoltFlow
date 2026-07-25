package com.voltwise.simulator.generator;

import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.OperatingState;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class WashingMachineTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String IDLE = "IDLE";
    private static final String FILLING = "FILLING";
    private static final String WASHING = "WASHING";
    private static final String HEATING = "HEATING";
    private static final String RINSE = "RINSE";
    private static final String SPINNING = "SPINNING";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.WASHING_MACHINE;
    }

    @Override
    public GeneratedTelemetry next(
            ApplianceSimulationState state,
            RandomGenerator random,
            ApplianceProfile profile,
            Instant now,
            BigDecimal safePowerLimitWatts
    ) {
        state.initialize(OperationalState.OFF, IDLE, now);

        if (state.getOperationalState() == OperationalState.FAULT) {
            BigDecimal base = state.getSessionBaseWatts().signum() > 0
                    ? state.getSessionBaseWatts()
                    : safePowerLimitWatts.multiply(BigDecimal.valueOf(0.70));
            return reading(addNoise(base, 0.02, random), OperatingState.HIGH_LOAD);
        }

        // Transitions
        String currentPhase = state.getPhase();
        switch (currentPhase) {
            case IDLE -> {
                double prob = profile.probability("start");
                if (prob < 1.0) {
                    double factor = isDaytime(now) ? 0.0002 : (isMorning(now) || isEvening(now) ? 0.00005 : 0.0);
                    prob = prob * factor;
                }
                if (chance(random, prob)) {
                    state.transitionTo(OperationalState.STANDBY, FILLING, now);
                    state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(0.15)));
                }
            }
            case FILLING -> {
                if (state.getCyclesInPhase() >= profile.duration("filling")) {
                    state.transitionTo(OperationalState.ACTIVE, WASHING, now);
                }
            }
            case WASHING -> {
                if (state.getCyclesInPhase() >= profile.duration("washing")) {
                    state.transitionTo(OperationalState.TEMPORARY_PEAK, HEATING, now);
                    state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(0.90)));
                }
            }
            case HEATING -> {
                if (state.getCyclesInPhase() >= profile.duration("heating")) {
                    state.transitionTo(OperationalState.ACTIVE, RINSE, now);
                    state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(0.15)));
                }
            }
            case RINSE -> {
                if (state.getCyclesInPhase() >= profile.duration("rinse")) {
                    state.transitionTo(OperationalState.ACTIVE, SPINNING, now);
                    state.setSessionBaseWatts(safePowerLimitWatts.multiply(BigDecimal.valueOf(0.45)));
                }
            }
            case SPINNING -> {
                if (state.getCyclesInPhase() >= profile.duration("spinning")) {
                    state.transitionTo(OperationalState.OFF, IDLE, now);
                }
            }
        }

        // Output based on phase/state
        switch (state.getPhase()) {
            case FILLING -> {
                BigDecimal fillingBase = safePowerLimitWatts.multiply(BigDecimal.valueOf(0.025));
                return reading(addNoise(fillingBase, 0.05, random), OperatingState.ON);
            }
            case WASHING, RINSE -> {
                return reading(addNoise(state.getSessionBaseWatts(), 0.02, random), OperatingState.ON);
            }
            case HEATING -> {
                return reading(addNoise(state.getSessionBaseWatts(), 0.015, random), OperatingState.HIGH_LOAD);
            }
            case SPINNING -> {
                return reading(addNoise(state.getSessionBaseWatts(), 0.02, random), OperatingState.HIGH_LOAD);
            }
            default -> {
                return reading(BigDecimal.ZERO, OperatingState.OFF);
            }
        }
    }
}
