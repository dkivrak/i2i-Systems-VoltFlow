package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
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
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(IDLE);
        switch (state.getPhase()) {
            case IDLE -> {
                if (chance(random, profile.probability("start"))) {
                    state.transitionTo(FILLING);
                }
            }
            case FILLING -> transitionAfter(state, profile, "filling", WASHING);
            case WASHING -> transitionAfter(state, profile, "washing", HEATING);
            case HEATING -> transitionAfter(state, profile, "heating", RINSE);
            case RINSE -> transitionAfter(state, profile, "rinse", SPINNING);
            case SPINNING -> transitionAfter(state, profile, "spinning", IDLE);
            default -> throw new IllegalStateException("Unknown washing machine phase " + state.getPhase());
        }

        return switch (state.getPhase()) {
            case FILLING -> reading(profile, "filling", OperatingState.ON, random);
            case WASHING -> reading(profile, "washing", OperatingState.ON, random);
            case HEATING -> reading(profile, "heating", OperatingState.HIGH_LOAD, random);
            case RINSE -> reading(profile, "washing", OperatingState.ON, random);
            case SPINNING -> reading(profile, "spinning", OperatingState.HIGH_LOAD, random);
            default -> reading(profile, "idle", OperatingState.STANDBY, random);
        };
    }

    private void transitionAfter(
            ApplianceSimulationState state,
            ApplianceProfile profile,
            String duration,
            String nextPhase
    ) {
        if (durationReached(state, profile, duration)) {
            state.transitionTo(nextPhase);
        }
    }
}
