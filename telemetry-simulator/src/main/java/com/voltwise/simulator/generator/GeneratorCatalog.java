package com.voltwise.simulator.generator;

import com.voltwise.simulator.domain.ApplianceType;
import jakarta.annotation.PostConstruct;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class GeneratorCatalog {

    private final Map<ApplianceType, ApplianceTelemetryGenerator> generators;

    public GeneratorCatalog(List<ApplianceTelemetryGenerator> availableGenerators) {
        generators = new EnumMap<>(ApplianceType.class);
        for (ApplianceTelemetryGenerator generator : availableGenerators) {
            ApplianceTelemetryGenerator duplicate = generators.put(generator.supportedType(), generator);
            if (duplicate != null) {
                throw new IllegalStateException("Duplicate telemetry generator for " + generator.supportedType());
            }
        }
    }

    @PostConstruct
    void verifyCoverage() {
        if (!generators.keySet().containsAll(Set.of(ApplianceType.values()))) {
            throw new IllegalStateException("A telemetry generator is required for every appliance type");
        }
    }

    public ApplianceTelemetryGenerator generatorFor(ApplianceType type) {
        ApplianceTelemetryGenerator generator = generators.get(type);
        if (generator == null) {
            throw new IllegalArgumentException("Unsupported appliance type " + type);
        }
        return generator;
    }
}
