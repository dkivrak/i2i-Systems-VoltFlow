package com.voltwise.core;

import com.voltwise.core.config.VoltWiseProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableAsync
@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(VoltWiseProperties.class)
public class VoltWiseApplication {
    public static void main(String[] args) {
        SpringApplication.run(VoltWiseApplication.class, args);
    }
}
