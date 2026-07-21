package com.voltwise.simulator;

import com.voltwise.simulator.config.KafkaTopicProperties;
import com.voltwise.simulator.config.SimulationProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({SimulationProperties.class, KafkaTopicProperties.class})
public class TelemetrySimulatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(TelemetrySimulatorApplication.class, args);
    }
}
