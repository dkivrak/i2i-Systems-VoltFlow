package com.voltwise.simulator;

import static org.assertj.core.api.Assertions.assertThat;

import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.generator.GeneratorCatalog;
import com.voltwise.simulator.runtime.SimulationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "debug=false",
        "logging.level.org.apache.kafka=OFF",
        "logging.level.org.springframework.kafka=OFF",
        "simulation.enabled=false",
        "spring.kafka.listener.auto-startup=false",
        "spring.kafka.admin.fail-fast=false",
        "spring.kafka.admin.properties.request.timeout.ms=100",
        "spring.kafka.admin.properties.default.api.timeout.ms=100",
        "spring.kafka.bootstrap-servers=127.0.0.1:1"
})
class TelemetrySimulatorApplicationTest {

    @Autowired
    private SimulationProperties properties;

    @Autowired
    private GeneratorCatalog generatorCatalog;

    @Autowired
    private SimulationRegistry registry;

    @Test
    void applicationContextLoadsWithAllGeneratorAndConfigurationBindings() {
        assertThat(properties.getIntervalMs()).isEqualTo(1000);
        assertThat(com.voltwise.simulator.domain.ApplianceType.values())
                .allSatisfy(type -> assertThat(generatorCatalog.generatorFor(type)).isNotNull());
        assertThat(registry.applianceCount()).isZero();
    }
}
