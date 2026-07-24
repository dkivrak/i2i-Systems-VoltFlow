package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class MicrowaveTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String OFF = "OFF";
    private static final String ACTIVE = "ACTIVE";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.MICROWAVE;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(OFF);
        if (ACTIVE.equals(state.getPhase()) && durationReached(state, profile, "active")) {
            state.transitionTo(OFF);
        } else if (OFF.equals(state.getPhase()) && chance(random, profile.probability("start"))) {
            state.transitionTo(ACTIVE);
        }
        return ACTIVE.equals(state.getPhase())
                ? reading(profile, "active", OperatingState.ON, random)
                : reading(profile, "off", OperatingState.OFF, random);
    }
}
