package com.voltflow.core.telemetry;

import com.voltflow.core.event.TelemetryEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "voltflow.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryListener {
    private final TelemetryProcessingService processingService;

    public TelemetryListener(TelemetryProcessingService processingService) {
        this.processingService = processingService;
    }

    @KafkaListener(topics = "${voltflow.kafka.telemetry-topic:voltflow.telemetry}",
            groupId = "voltflow-core-telemetry-v1")
    public void consume(TelemetryEvent event) {
        processingService.process(event);
    }
}
