package com.voltwise.simulator.generator;

import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.domain.ApplianceType;
import com.voltwise.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class TelevisionTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String STANDBY = "STANDBY";
    private static final String ON = "ON";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.TELEVISION;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(STANDBY);
        if (STANDBY.equals(state.getPhase()) && chance(random, profile.probability("turn-on"))) {
            state.transitionTo(ON);
        } else if (ON.equals(state.getPhase()) && chance(random, profile.probability("turn-off"))) {
            state.transitionTo(STANDBY);
        }
        if (!ON.equals(state.getPhase())) {
            return reading(profile, "standby", OperatingState.STANDBY, random);
        }
        if (state.getCyclesInPhase() > 0 && state.getNominalPowerWatts().signum() > 0) {
            return new GeneratedTelemetry(state.getNominalPowerWatts(), OperatingState.ON);
        }
        GeneratedTelemetry sampled = reading(profile, "on", OperatingState.ON, random);
        state.setNominalPowerWatts(sampled.powerWatts());
        return sampled;
    }
}
