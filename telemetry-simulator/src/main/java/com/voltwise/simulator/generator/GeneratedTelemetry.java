package com.voltwise.simulator.generator;

import com.voltwise.simulator.domain.OperatingState;
import java.math.BigDecimal;
import java.util.Objects;

public record GeneratedTelemetry(BigDecimal powerWatts, OperatingState operatingState) {

    public GeneratedTelemetry {
        Objects.requireNonNull(powerWatts, "powerWatts");
        Objects.requireNonNull(operatingState, "operatingState");
        if (powerWatts.signum() < 0) {
            throw new IllegalArgumentException("Power cannot be negative");
        }
    }
}
