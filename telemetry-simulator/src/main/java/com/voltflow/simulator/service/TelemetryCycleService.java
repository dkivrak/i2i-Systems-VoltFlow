package com.voltflow.simulator.service;

import com.voltflow.simulator.config.SimulationProperties;
import com.voltflow.simulator.domain.EventType;
import com.voltflow.simulator.event.TelemetryEvent;
import com.voltflow.simulator.generator.ApplianceTelemetryGenerator;
import com.voltflow.simulator.generator.GeneratedTelemetry;
import com.voltflow.simulator.generator.GeneratorCatalog;
import com.voltflow.simulator.kafka.TelemetryPublisher;
import com.voltflow.simulator.runtime.ApplianceRuntime;
import com.voltflow.simulator.runtime.SimulationRegistry;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "simulation", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryCycleService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryCycleService.class);
    private static final int EVENT_VERSION = 1;

    private final SimulationRegistry registry;
    private final GeneratorCatalog generatorCatalog;
    private final SimulationProperties simulationProperties;
    private final AnomalyInjector anomalyInjector;
    private final TelemetryPublisher publisher;
    private final Clock clock;

    public TelemetryCycleService(
            SimulationRegistry registry,
            GeneratorCatalog generatorCatalog,
            SimulationProperties simulationProperties,
            AnomalyInjector anomalyInjector,
            TelemetryPublisher publisher,
            Clock clock
    ) {
        this.registry = registry;
        this.generatorCatalog = generatorCatalog;
        this.simulationProperties = simulationProperties;
        this.anomalyInjector = anomalyInjector;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${simulation.interval-ms:1000}")
    public void generateCycle() {
        for (ApplianceRuntime runtime : registry.snapshot()) {
            try {
                ApplianceTelemetryGenerator generator = generatorCatalog.generatorFor(runtime.appliance().type());
                GeneratedTelemetry reading = runtime.generate(generator, simulationProperties, anomalyInjector);
                TelemetryEvent event = new TelemetryEvent(
                        UUID.randomUUID(),
                        EVENT_VERSION,
                        EventType.APPLIANCE_TELEMETRY_RECORDED,
                        clock.instant(),
                        runtime.homeId(),
                        runtime.appliance().applianceId(),
                        runtime.appliance().type(),
                        reading.powerWatts(),
                        reading.operatingState()
                );
                publisher.publish(event);
            } catch (RuntimeException exception) {
                log.error(
                        "Telemetry cycle failed for homeId={} applianceId={}",
                        runtime.homeId(), runtime.appliance().applianceId(), exception
                );
            }
        }
    }
}
