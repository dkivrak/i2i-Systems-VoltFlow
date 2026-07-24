package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class RefrigeratorTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String IDLE = "IDLE";
    private static final String STARTUP = "STARTUP";
    private static final String COMPRESSOR = "COMPRESSOR";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.REFRIGERATOR;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(IDLE);
        if (STARTUP.equals(state.getPhase()) && durationReached(state, profile, "startup")) {
            state.transitionTo(COMPRESSOR);
        } else if (COMPRESSOR.equals(state.getPhase()) && durationReached(state, profile, "compressor")) {
            state.transitionTo(IDLE);
        } else if (IDLE.equals(state.getPhase())
                && chance(random, profile.probability("compressor-start"))) {
            state.transitionTo(STARTUP);
        }

        return switch (state.getPhase()) {
            case STARTUP -> reading(profile, "startup", OperatingState.HIGH_LOAD, random);
            case COMPRESSOR -> reading(profile, "compressor", OperatingState.ON, random);
            default -> reading(profile, "idle", OperatingState.STANDBY, random);
        };
    }
}
