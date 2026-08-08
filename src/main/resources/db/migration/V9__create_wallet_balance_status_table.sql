CREATE TABLE health.wallet_balance_status (
    id BIGSERIAL PRIMARY KEY,
    venue_code VARCHAR(50) NOT NULL,
    last_snapshot_time TIMESTAMPTZ,
    is_healthy BOOLEAN NOT NULL DEFAULT FALSE,
    checked_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_wallet_balance_status_venue_code UNIQUE (venue_code)
);

CREATE INDEX idx_wallet_balance_status_venue_code ON health.wallet_balance_status(venue_code);
