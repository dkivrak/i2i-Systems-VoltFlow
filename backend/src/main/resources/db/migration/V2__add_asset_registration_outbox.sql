CREATE TABLE asset_registration_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL,
    home_id BIGINT NOT NULL REFERENCES homes(id) ON DELETE CASCADE,
    event_version INTEGER NOT NULL CHECK (event_version > 0),
    event_type VARCHAR(80) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    topic VARCHAR(160) NOT NULL,
    event_payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL,
    last_attempt_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    last_failure VARCHAR(500),
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_registration_outbox_event_id UNIQUE(event_id),
    CONSTRAINT ck_registration_outbox_status CHECK (status IN ('PENDING', 'PUBLISHED'))
);

CREATE INDEX idx_registration_outbox_home ON asset_registration_outbox(home_id);
CREATE INDEX idx_registration_outbox_due
    ON asset_registration_outbox(status, next_attempt_at)
    WHERE status = 'PENDING';
