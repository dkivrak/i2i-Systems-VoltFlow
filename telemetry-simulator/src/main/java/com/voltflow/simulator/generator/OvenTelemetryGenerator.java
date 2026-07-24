package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class OvenTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String STANDBY = "STANDBY";
    private static final String HEATING = "HEATING";
    private static final String THERMOSTAT_ON = "THERMOSTAT_ON";
    private static final String THERMOSTAT_OFF = "THERMOSTAT_OFF";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.OVEN;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(STANDBY);
        if (STANDBY.equals(state.getPhase()) && chance(random, profile.probability("start"))) {
            state.transitionTo(HEATING);
        } else if (HEATING.equals(state.getPhase()) && durationReached(state, profile, "preheat")) {
            state.transitionTo(THERMOSTAT_OFF);
        } else if (THERMOSTAT_OFF.equals(state.getPhase())
                && durationReached(state, profile, "thermostat-off")) {
            state.transitionTo(chance(random, profile.probability("stop")) ? STANDBY : THERMOSTAT_ON);
        } else if (THERMOSTAT_ON.equals(state.getPhase())
                && durationReached(state, profile, "thermostat-on")) {
            state.transitionTo(chance(random, profile.probability("stop")) ? STANDBY : THERMOSTAT_OFF);
        }

        return switch (state.getPhase()) {
            case HEATING -> reading(profile, "heating", OperatingState.HIGH_LOAD, random);
            case THERMOSTAT_ON -> reading(profile, "thermostat-on", OperatingState.ON, random);
            case THERMOSTAT_OFF -> reading(profile, "thermostat-off", OperatingState.STANDBY, random);
            default -> reading(profile, "standby", OperatingState.STANDBY, random);
        };
    }
}
