CREATE TABLE IF NOT EXISTS health.market_status (
    id BIGSERIAL PRIMARY KEY,
    market_name VARCHAR(255) NOT NULL,
    venue_code VARCHAR(255) NOT NULL,
    healthy BOOLEAN NOT NULL,
    last_status_change_at TIMESTAMPTZ NOT NULL,
    last_notification_sent_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT unique_market_venue UNIQUE (market_name, venue_code)
    );
