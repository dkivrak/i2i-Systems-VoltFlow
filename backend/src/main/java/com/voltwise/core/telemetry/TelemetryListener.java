package com.voltwise.core.telemetry;

import com.voltwise.core.event.TelemetryEvent;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "voltwise.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryListener {
    private final TelemetryProcessingService processingService;

    public TelemetryListener(TelemetryProcessingService processingService) {
        this.processingService = processingService;
    }

    @KafkaListener(topics = "${voltwise.kafka.telemetry-topic:voltwise.telemetry}",
            groupId = "voltwise-core-telemetry-v1")
    public void consume(TelemetryEvent event) {
        processingService.process(event);
    }
}
