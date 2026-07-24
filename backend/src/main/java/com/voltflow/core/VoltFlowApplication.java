package com.voltflow.core;

import com.voltflow.core.config.VoltFlowProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(VoltFlowProperties.class)
public class VoltFlowApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoltFlowApplication.class, args);
    }
}
