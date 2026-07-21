package com.voltwise.simulator.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RuntimeConfiguration {

    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }
}
