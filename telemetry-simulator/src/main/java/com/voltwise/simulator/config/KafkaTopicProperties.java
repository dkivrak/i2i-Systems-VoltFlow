package com.voltwise.simulator.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "voltwise.kafka")
public class KafkaTopicProperties {

    @NotBlank
    private String assetRegistrationTopic = "voltwise.asset-registration";

    @NotBlank
    private String telemetryTopic = "voltwise.telemetry";

    @NotBlank
    private String registrationDltTopic = "voltwise.asset-registration.dlt";

    @Min(1)
    private int partitions = 3;

    @Min(1)
    private int replicas = 1;

    @Min(0)
    private long retryIntervalMs = 1000;

    @Min(0)
    private long retryAttempts = 2;

    public String getAssetRegistrationTopic() {
        return assetRegistrationTopic;
    }

    public void setAssetRegistrationTopic(String assetRegistrationTopic) {
        this.assetRegistrationTopic = assetRegistrationTopic;
    }

    public String getTelemetryTopic() {
        return telemetryTopic;
    }

    public void setTelemetryTopic(String telemetryTopic) {
        this.telemetryTopic = telemetryTopic;
    }

    public String getRegistrationDltTopic() {
        return registrationDltTopic;
    }

    public void setRegistrationDltTopic(String registrationDltTopic) {
        this.registrationDltTopic = registrationDltTopic;
    }

    public int getPartitions() {
        return partitions;
    }

    public void setPartitions(int partitions) {
        this.partitions = partitions;
    }

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

    public long getRetryAttempts() {
        return retryAttempts;
    }

    public void setRetryAttempts(long retryAttempts) {
        this.retryAttempts = retryAttempts;
    }
}
