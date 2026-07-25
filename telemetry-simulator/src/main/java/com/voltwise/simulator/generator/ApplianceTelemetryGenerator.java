package com.voltwise.simulator.generator;

import com.voltwise.simulator.config.ApplianceProfile;
import com.voltwise.simulator.domain.ApplianceType;
import java.util.random.RandomGenerator;

public interface ApplianceTelemetryGenerator {

    ApplianceType supportedType();

    GeneratedTelemetry next(
            ApplianceSimulationState state,
            RandomGenerator random,
            ApplianceProfile profile,
            java.time.Instant now,
            java.math.BigDecimal safePowerLimitWatts
    );
}
