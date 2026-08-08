CREATE TABLE IF NOT EXISTS health.config_parameters (
    id SERIAL PRIMARY KEY,
    service_name VARCHAR(255) NOT NULL,
    config_key VARCHAR(255) NOT NULL,
    description TEXT,
    config_value TEXT NOT NULL,
    UNIQUE(service_name, config_key),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);