package com.voltwise.simulator.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.simulator.domain.ApplianceType;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class SimulationPropertiesBindingTest {

    @Test
    void hierarchicalPropertiesOverrideDefaultsWithoutRemovingOtherProfiles() {
        MapConfigurationPropertySource source = new MapConfigurationPropertySource(Map.of(
                "simulation.random-seed", "99",
                "simulation.interval-ms", "250",
                "simulation.profiles.KETTLE.ranges.active.min-watts", "1600",
                "simulation.profiles.KETTLE.probabilities.start", "0.10"
        ));

        SimulationProperties properties = new Binder(source)
                .bind("simulation", Bindable.of(SimulationProperties.class))
                .orElseThrow(() -> new AssertionError("Simulation properties did not bind"));
        properties.validateConfiguration();

        assertThat(properties.getRandomSeed()).isEqualTo(99L);
        assertThat(properties.getIntervalMs()).isEqualTo(250L);
        assertThat(properties.profile(ApplianceType.KETTLE).requiredRange("active").getMinWatts())
                .isEqualTo(1600.0);
        assertThat(properties.profile(ApplianceType.KETTLE).probability("start")).isEqualTo(0.10);
        assertThat(properties.getProfiles()).containsKeys(ApplianceType.values());
    }
}
