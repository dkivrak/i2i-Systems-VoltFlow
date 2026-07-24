package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.OperatingState;
import java.util.random.RandomGenerator;

abstract class GeneratorSupport {

    protected boolean chance(RandomGenerator random, double probability) {
        return probability > 0 && (probability >= 1 || random.nextDouble() < probability);
    }

    protected boolean durationReached(ApplianceSimulationState state, ApplianceProfile profile, String key) {
        return state.getCyclesInPhase() >= profile.duration(key);
    }

    protected GeneratedTelemetry reading(
            ApplianceProfile profile,
            String range,
            OperatingState operatingState,
            RandomGenerator random
    ) {
        return new GeneratedTelemetry(profile.sample(range, random), operatingState);
    }
}
