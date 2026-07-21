CREATE TABLE homes (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    contact_email VARCHAR(320) NOT NULL,
    monthly_budget NUMERIC(19,4) NOT NULL CHECK (monthly_budget > 0),
    normal_tariff_per_kwh NUMERIC(19,6) NOT NULL CHECK (normal_tariff_per_kwh > 0),
    penalty_multiplier NUMERIC(10,4) NOT NULL CHECK (penalty_multiplier >= 1),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE appliances (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    type VARCHAR(40) NOT NULL,
    safe_power_limit_watts NUMERIC(19,3) NOT NULL CHECK (safe_power_limit_watts > 0),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX idx_appliances_home_id ON appliances(home_id);

CREATE TABLE billing_ledgers (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    billing_period DATE NOT NULL,
    accumulated_energy_kwh NUMERIC(24,9) NOT NULL DEFAULT 0,
    accumulated_cost NUMERIC(19,6) NOT NULL DEFAULT 0,
    tariff_state VARCHAR(20) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_billing_home_period UNIQUE(home_id, billing_period)
);
CREATE INDEX idx_billing_home_id ON billing_ledgers(home_id);

CREATE TABLE quota_events (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    billing_period DATE NOT NULL,
    threshold VARCHAR(30) NOT NULL,
    usage_percent NUMERIC(12,4) NOT NULL,
    current_cost NUMERIC(19,6) NOT NULL,
    monthly_budget NUMERIC(19,4) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_quota_home_period_threshold UNIQUE(home_id, billing_period, threshold)
);
CREATE INDEX idx_quota_home_occurred ON quota_events(home_id, occurred_at DESC);

CREATE TABLE anomaly_events (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_id BIGINT NOT NULL REFERENCES appliances(id) ON DELETE CASCADE,
    measured_power_watts NUMERIC(19,3) NOT NULL,
    safe_power_limit_watts NUMERIC(19,3) NOT NULL,
    consecutive_breach_count INTEGER NOT NULL,
    status VARCHAR(20) NOT NULL,
    detected_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_anomaly_home_detected ON anomaly_events(home_id, detected_at DESC);
CREATE INDEX idx_anomaly_appliance_status ON anomaly_events(appliance_id, status);
CREATE UNIQUE INDEX uk_active_anomaly_appliance ON anomaly_events(appliance_id) WHERE status = 'ACTIVE';

CREATE TABLE tariff_change_events (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    billing_period DATE NOT NULL,
    previous_tariff VARCHAR(20) NOT NULL,
    new_tariff VARCHAR(20) NOT NULL,
    previous_rate NUMERIC(19,6) NOT NULL,
    new_rate NUMERIC(19,6) NOT NULL,
    trigger_usage_percent NUMERIC(12,4) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_tariff_home_period_state UNIQUE(home_id, billing_period, new_tariff)
);
CREATE INDEX idx_tariff_home_changed ON tariff_change_events(home_id, changed_at DESC);

CREATE TABLE consumption_snapshots (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    appliance_id BIGINT REFERENCES appliances(id) ON DELETE CASCADE,
    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,
    energy_kwh NUMERIC(24,9) NOT NULL,
    average_power_watts NUMERIC(19,3) NOT NULL,
    maximum_power_watts NUMERIC(19,3) NOT NULL,
    cost NUMERIC(19,6) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK (period_end > period_start)
);
CREATE INDEX idx_snapshot_home_period ON consumption_snapshots(home_id, period_start DESC);
CREATE INDEX idx_snapshot_appliance_period ON consumption_snapshots(appliance_id, period_start DESC);
CREATE UNIQUE INDEX uk_snapshot_period_scope ON consumption_snapshots(home_id, COALESCE(appliance_id, 0), period_start, period_end);

CREATE TABLE recommendations (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    trigger_type VARCHAR(40) NOT NULL,
    trigger_reference_id BIGINT NOT NULL,
    recommendation_text TEXT NOT NULL,
    model_name VARCHAR(120) NOT NULL,
    fallback_used BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_recommendation_trigger UNIQUE(home_id, trigger_type, trigger_reference_id)
);
CREATE INDEX idx_recommendation_home_created ON recommendations(home_id, created_at DESC);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    recommendation_id BIGINT REFERENCES recommendations(id) ON DELETE SET NULL,
    channel VARCHAR(20) NOT NULL,
    recipient VARCHAR(320) NOT NULL,
    subject VARCHAR(255) NOT NULL,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL,
    sent_at TIMESTAMPTZ
);
CREATE INDEX idx_notification_home_status ON notifications(home_id, status);

CREATE TABLE processed_events (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(80) NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL
);
