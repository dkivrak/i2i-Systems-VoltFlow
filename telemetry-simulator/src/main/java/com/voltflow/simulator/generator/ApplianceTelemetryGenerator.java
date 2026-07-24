package com.voltflow.simulator.generator;

import com.voltflow.simulator.config.ApplianceProfile;
import com.voltflow.simulator.domain.ApplianceType;
import java.util.random.RandomGenerator;

public interface ApplianceTelemetryGenerator {

    ApplianceType supportedType();

    GeneratedTelemetry next(
            ApplianceSimulationState state,
            RandomGenerator random,
            ApplianceProfile profile
    );
}
