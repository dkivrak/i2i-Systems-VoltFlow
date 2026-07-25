package com.voltwise.simulator.service;

import com.voltwise.simulator.config.SimulationProperties;
import com.voltwise.simulator.domain.EventType;
import com.voltwise.simulator.event.TelemetryEvent;
import com.voltwise.simulator.generator.ApplianceTelemetryGenerator;
import com.voltwise.simulator.generator.GeneratedTelemetry;
import com.voltwise.simulator.generator.GeneratorCatalog;
import com.voltwise.simulator.kafka.TelemetryPublisher;
import com.voltwise.simulator.runtime.ApplianceRuntime;
import com.voltwise.simulator.runtime.SimulationRegistry;
import java.time.Clock;
import java.time.Instant;
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

    private Instant simulatedTime;

    @Scheduled(fixedDelayString = "${simulation.interval-ms:1000}")
    public void generateCycle() {
        if (simulatedTime == null) {
            simulatedTime = clock.instant();
        } else {
            long stepMs = simulationProperties.getIntervalMs() * simulationProperties.getSimulationSpeed();
            simulatedTime = simulatedTime.plusMillis(stepMs);
        }

        for (ApplianceRuntime runtime : registry.snapshot()) {
            try {
                ApplianceTelemetryGenerator generator = generatorCatalog.generatorFor(runtime.appliance().type());
                GeneratedTelemetry reading = runtime.generate(generator, simulationProperties, anomalyInjector, simulatedTime);
                TelemetryEvent event = new TelemetryEvent(
                        UUID.randomUUID(),
                        EVENT_VERSION,
                        EventType.APPLIANCE_TELEMETRY_RECORDED,
                        simulatedTime,
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
