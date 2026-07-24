package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class AirConditionerTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String STANDBY = "STANDBY";
    private static final String FAN = "FAN";
    private static final String COMPRESSOR = "COMPRESSOR";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.AIR_CONDITIONER;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(STANDBY);
        if (STANDBY.equals(state.getPhase()) && chance(random, profile.probability("turn-on"))) {
            state.transitionTo(FAN);
        } else if (FAN.equals(state.getPhase()) && durationReached(state, profile, "fan")) {
            state.transitionTo(chance(random, profile.probability("turn-off")) ? STANDBY : COMPRESSOR);
        } else if (COMPRESSOR.equals(state.getPhase()) && durationReached(state, profile, "compressor")) {
            state.transitionTo(FAN);
        }

        return switch (state.getPhase()) {
            case FAN -> reading(profile, "fan", OperatingState.ON, random);
            case COMPRESSOR -> reading(profile, "compressor", OperatingState.HIGH_LOAD, random);
            default -> reading(profile, "standby", OperatingState.STANDBY, random);
        };
    }
}
