package com.voltwise.core.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.math.BigDecimal;

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

    public Kafka getKafka() { return kafka; }
    public void setKafka(Kafka kafka) { this.kafka = kafka; }

    public RegistrationOutbox getRegistrationOutbox() { return registrationOutbox; }
    public void setRegistrationOutbox(RegistrationOutbox registrationOutbox) { this.registrationOutbox = registrationOutbox; }

    public Ignite getIgnite() { return ignite; }
    public void setIgnite(Ignite ignite) { this.ignite = ignite; }

    public Billing getBilling() { return billing; }
    public void setBilling(Billing billing) { this.billing = billing; }

    public Snapshots getSnapshots() { return snapshots; }
    public void setSnapshots(Snapshots snapshots) { this.snapshots = snapshots; }

    public Gemini getGemini() { return gemini; }
    public void setGemini(Gemini gemini) { this.gemini = gemini; }

    public Mail getMail() { return mail; }
    public void setMail(Mail mail) { this.mail = mail; }

    public static class Kafka {
        private boolean enabled = true;
        @NotBlank private String assetRegistrationTopic = "voltwise.asset-registration";
        @NotBlank private String telemetryTopic = "voltwise.telemetry";
        @NotBlank private String telemetryDltTopic = "voltwise.telemetry.dlt";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAssetRegistrationTopic() { return assetRegistrationTopic; }
        public void setAssetRegistrationTopic(String assetRegistrationTopic) { this.assetRegistrationTopic = assetRegistrationTopic; }
        public String getTelemetryTopic() { return telemetryTopic; }
        public void setTelemetryTopic(String telemetryTopic) { this.telemetryTopic = telemetryTopic; }
        public String getTelemetryDltTopic() { return telemetryDltTopic; }
        public void setTelemetryDltTopic(String telemetryDltTopic) { this.telemetryDltTopic = telemetryDltTopic; }
    }

    public static class RegistrationOutbox {
        @Min(100) private long retryIntervalMs = 5_000;
        @Min(0) private long startupDelayMs = 1_000;
        @Min(1) private int batchSize = 50;
        @Min(100) private long acknowledgementTimeoutMs = 35_000;
        @Min(100) private long initialBackoffMs = 2_000;
        @Min(100) private long maximumBackoffMs = 60_000;

        public long getRetryIntervalMs() { return retryIntervalMs; }
        public void setRetryIntervalMs(long retryIntervalMs) { this.retryIntervalMs = retryIntervalMs; }
        public long getStartupDelayMs() { return startupDelayMs; }
        public void setStartupDelayMs(long startupDelayMs) { this.startupDelayMs = startupDelayMs; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public long getAcknowledgementTimeoutMs() { return acknowledgementTimeoutMs; }
        public void setAcknowledgementTimeoutMs(long acknowledgementTimeoutMs) { this.acknowledgementTimeoutMs = acknowledgementTimeoutMs; }
        public long getInitialBackoffMs() { return initialBackoffMs; }
        public void setInitialBackoffMs(long initialBackoffMs) { this.initialBackoffMs = initialBackoffMs; }
        public long getMaximumBackoffMs() { return maximumBackoffMs; }
        public void setMaximumBackoffMs(long maximumBackoffMs) { this.maximumBackoffMs = maximumBackoffMs; }
    }

    public static class Ignite {
        private boolean enabled = true;
        @NotBlank private String addresses = "127.0.0.1:10800";
        @NotBlank private String cacheName = "voltwise-live-state";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAddresses() { return addresses; }
        public void setAddresses(String addresses) { this.addresses = addresses; }
        public String getCacheName() { return cacheName; }
        public void setCacheName(String cacheName) { this.cacheName = cacheName; }
    }

    public static class Billing {
        @DecimalMin("0.01") private BigDecimal defaultMonthlyBudget = new BigDecimal("1000.00");
        @DecimalMin("0.000001") private BigDecimal normalTariffPerKwh = new BigDecimal("2.50");
        @DecimalMin("1.0") private BigDecimal penaltyTariffMultiplier = new BigDecimal("1.50");
        @Min(1) private long maximumTelemetryGapSeconds = 300;

        public BigDecimal getDefaultMonthlyBudget() { return defaultMonthlyBudget; }
        public void setDefaultMonthlyBudget(BigDecimal defaultMonthlyBudget) { this.defaultMonthlyBudget = defaultMonthlyBudget; }
        public BigDecimal getNormalTariffPerKwh() { return normalTariffPerKwh; }
        public void setNormalTariffPerKwh(BigDecimal normalTariffPerKwh) { this.normalTariffPerKwh = normalTariffPerKwh; }
        public BigDecimal getPenaltyTariffMultiplier() { return penaltyTariffMultiplier; }
        public void setPenaltyTariffMultiplier(BigDecimal penaltyTariffMultiplier) { this.penaltyTariffMultiplier = penaltyTariffMultiplier; }
        public long getMaximumTelemetryGapSeconds() { return maximumTelemetryGapSeconds; }
        public void setMaximumTelemetryGapSeconds(long maximumTelemetryGapSeconds) { this.maximumTelemetryGapSeconds = maximumTelemetryGapSeconds; }
    }

    public static class Snapshots {
        @Min(1000) private long intervalMs = 60_000;

        public long getIntervalMs() { return intervalMs; }
        public void setIntervalMs(long intervalMs) { this.intervalMs = intervalMs; }
    }

    public static class Gemini {
        private String apiKey = "";
        @NotBlank private String model = "gemini-2.0-flash";
        @Min(100) private int connectTimeoutMs = 3000;
        @Min(100) private int readTimeoutMs = 8000;

        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getReadTimeoutMs() { return readTimeoutMs; }
        public void setReadTimeoutMs(int readTimeoutMs) { this.readTimeoutMs = readTimeoutMs; }
    }

    public static class Mail {
        private boolean enabled = true;
        @NotBlank private String from = "noreply@voltflow.space";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getFrom() { return from; }
        public void setFrom(String from) { this.from = from; }
    }
}
