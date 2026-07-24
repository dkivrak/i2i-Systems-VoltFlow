package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class ComputerTelemetryGenerator extends GeneratorSupport implements ApplianceTelemetryGenerator {

    private static final String OFF = "OFF";
    private static final String BOOT = "BOOT";
    private static final String STANDBY = "STANDBY";
    private static final String IDLE = "IDLE";
    private static final String HIGH_LOAD = "HIGH_LOAD";

    @Override
    public ApplianceType supportedType() {
        return ApplianceType.COMPUTER;
    }

    @Override
    public GeneratedTelemetry next(ApplianceSimulationState state, RandomGenerator random, ApplianceProfile profile) {
        state.initializePhase(OFF);
        switch (state.getPhase()) {
            case OFF -> {
                if (chance(random, profile.probability("power-on"))) {
                    state.transitionTo(BOOT);
                }
            }
            case BOOT -> {
                if (durationReached(state, profile, "boot")) {
                    state.transitionTo(IDLE);
                }
            }
            case STANDBY -> {
                if (chance(random, profile.probability("power-off"))) {
                    state.transitionTo(OFF);
                } else if (chance(random, profile.probability("wake"))) {
                    state.transitionTo(IDLE);
                }
            }
            case IDLE -> {
                if (chance(random, profile.probability("sleep"))) {
                    state.transitionTo(STANDBY);
                } else if (chance(random, profile.probability("high-load"))) {
                    state.transitionTo(HIGH_LOAD);
                }
            }
            case HIGH_LOAD -> {
                if (durationReached(state, profile, "high-load")) {
                    state.transitionTo(IDLE);
                }
            }
            default -> throw new IllegalStateException("Unknown computer phase " + state.getPhase());
        }

        return switch (state.getPhase()) {
            case BOOT, HIGH_LOAD -> reading(profile, "high-load", OperatingState.HIGH_LOAD, random);
            case IDLE -> reading(profile, "idle", OperatingState.ON, random);
            case STANDBY -> reading(profile, "standby", OperatingState.STANDBY, random);
            default -> reading(profile, "off", OperatingState.OFF, random);
        };
    }
}
