package com.voltwise.core.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI voltWiseOpenApi() {
        return new OpenAPI().info(new Info().title("VoltWise Core API").version("v1")
                .description("Real-time household energy, billing, quota and anomaly API"));
    }
}
