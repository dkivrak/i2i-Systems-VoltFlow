package com.voltflow.core.registration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayMigrationContractTest {
    @Test
    void migrationDefinesAllPermanentStoresAndDeduplicationConstraints() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V1__create_voltflow_schema.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("CREATE TABLE homes", "CREATE TABLE appliances",
                    "CREATE TABLE billing_ledgers", "CREATE TABLE quota_events",
                    "CREATE TABLE anomaly_events", "CREATE TABLE tariff_change_events",
                    "CREATE TABLE consumption_snapshots", "CREATE TABLE recommendations",
                    "CREATE TABLE notifications", "CREATE TABLE processed_events",
                    "uk_quota_home_period_threshold", "uk_active_anomaly_appliance",
                    "uk_recommendation_trigger");
        }
    }

    @Test
    void secondMigrationDefinesDurableRegistrationOutboxAndDueIndex() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V2__add_asset_registration_outbox.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains("CREATE TABLE asset_registration_outbox",
                    "event_id UUID NOT NULL", "event_payload TEXT NOT NULL",
                    "attempt_count INTEGER NOT NULL", "next_attempt_at TIMESTAMPTZ NOT NULL",
                    "published_at TIMESTAMPTZ", "last_failure VARCHAR(500)",
                    "uk_registration_outbox_event_id", "idx_registration_outbox_due");
        }
    }

    @Test
    void fourthMigrationDefinesPasswordBackedApplicationUsers() throws Exception {
        try (var stream = getClass().getResourceAsStream("/db/migration/V4__add_application_users.sql")) {
            assertThat(stream).isNotNull();
            String sql = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(sql).contains(
                    "CREATE TABLE app_users",
                    "email VARCHAR(320) NOT NULL",
                    "password_hash VARCHAR(100) NOT NULL",
                    "enabled BOOLEAN NOT NULL",
                    "uk_app_users_email UNIQUE"
            );
        }
    }
}
