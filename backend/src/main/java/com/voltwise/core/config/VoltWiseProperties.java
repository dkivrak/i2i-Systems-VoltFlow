package com.voltwise.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "voltwise")
public class VoltWiseProperties {
    @Valid private Kafka kafka = new Kafka();
    @Valid private RegistrationOutbox registrationOutbox = new RegistrationOutbox();
    @Valid private Ignite ignite = new Ignite();
    @Valid private Billing billing = new Billing();
    @Valid private Snapshots snapshots = new Snapshots();
    @Valid private Gemini gemini = new Gemini();
    @Valid private Mail mail = new Mail();

    @Getter @Setter
    public static class Kafka {
        private boolean enabled = true;
        @NotBlank private String assetRegistrationTopic = "voltwise.asset-registration";
        @NotBlank private String telemetryTopic = "voltwise.telemetry";
        @NotBlank private String telemetryDltTopic = "voltwise.telemetry.dlt";
    }

    @Getter @Setter
    public static class RegistrationOutbox {
        @Min(100) private long retryIntervalMs = 5_000;
        @Min(0) private long startupDelayMs = 1_000;
        @Min(1) private int batchSize = 50;
        @Min(100) private long acknowledgementTimeoutMs = 35_000;
        @Min(100) private long initialBackoffMs = 2_000;
        @Min(100) private long maximumBackoffMs = 60_000;
    }

    @Getter @Setter
    public static class Ignite {
        private boolean enabled = true;
        @NotBlank private String addresses = "127.0.0.1:10800";
        @NotBlank private String cacheName = "voltwise-live-state";
    }

    @Getter @Setter
    public static class Billing {
        @DecimalMin("0.01") private BigDecimal defaultMonthlyBudget = new BigDecimal("1000.00");
        @DecimalMin("0.000001") private BigDecimal normalTariffPerKwh = new BigDecimal("2.50");
        @DecimalMin("1.0") private BigDecimal penaltyTariffMultiplier = new BigDecimal("1.50");
        @Min(1) private long maximumTelemetryGapSeconds = 300;
    }

    @Getter @Setter
    public static class Snapshots {
        @Min(1000) private long intervalMs = 60_000;
    }

    @Getter @Setter
    public static class Gemini {
        private String apiKey = "";
        @NotBlank private String model = "gemini-2.0-flash";
        @Min(100) private int connectTimeoutMs = 3000;
        @Min(100) private int readTimeoutMs = 8000;
    }

    @Getter @Setter
    public static class Mail {
        private boolean enabled = true;
        @NotBlank private String from = "noreply@voltwise.local";
    }
}
