CREATE TABLE IF NOT EXISTS health.audit_status (
    id BIGSERIAL PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL UNIQUE,
    healthy BOOLEAN NOT NULL,
    last_status_change_at TIMESTAMPTZ NOT NULL,
    last_notification_sent_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
