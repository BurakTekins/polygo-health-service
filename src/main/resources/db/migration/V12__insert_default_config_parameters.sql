INSERT INTO health.config_parameters (config_key, config_value, description, service_name, created_at, updated_at)
VALUES
    ('fixed-rate-ms', '400000', 'Fixed interval in milliseconds for scheduled tasks', 'scheduling', NOW(), NOW()),
    ('initial-delay-ms', '10000', 'Initial delay in milliseconds before starting scheduled tasks', 'scheduling', NOW(), NOW()),

    ('threshold-minutes', '15', 'Threshold in minutes for health check polling', 'health-check', NOW(), NOW()),
    ('notification-threshold-minutes', '60', 'Threshold in minutes to trigger notifications', 'health-check', NOW(), NOW()),
    ('service-urls', '/bot/actuator/health,/market-data/actuator/health,/notify/actuator/health,/strategy/actuator/health,/wallet/actuator/health,/audit/actuator/health', 'PolyGo service health endpoints exposed through the gateway', 'health-check', NOW(), NOW()),

    ('venues-endpoint', '/market-data/api/v1/venues', 'Endpoint that lists configured Polymarket venues', 'market-fee', NOW(), NOW()),
    ('market-fee-endpoint', '/market-data/api/v1/market-fees', 'Endpoint that returns current Polymarket venue fees', 'market-fee', NOW(), NOW()),
    ('threshold-minutes', '60', 'Maximum age in minutes for market fee data', 'market-fee', NOW(), NOW()),
    ('notification-threshold-minutes', '60', 'Minimum interval in minutes between market fee alerts', 'market-fee', NOW(), NOW()),

    ('monitored-services', 'bot-service,market-data-service,notification-service,strategy-service,wallet-service', 'List of services monitored by the audit service', 'audit', NOW(), NOW()),
    ('endpoint', '/audit/api/v1/audit-events', 'Audit event endpoint exposed through the gateway', 'audit', NOW(), NOW()),
    ('bot-service-threshold-minutes', '720', 'Audit threshold in minutes for bot-service', 'audit', NOW(), NOW()),
    ('bot-service-notification-threshold-minutes', '60', 'Minimum interval in minutes between bot-service audit alerts', 'audit', NOW(), NOW()),
    ('market-data-service-threshold-minutes', '720', 'Audit threshold in minutes for market-data-service', 'audit', NOW(), NOW()),
    ('market-data-service-notification-threshold-minutes', '60', 'Minimum interval in minutes between market-data-service audit alerts', 'audit', NOW(), NOW()),
    ('notification-service-threshold-minutes', '720', 'Audit threshold in minutes for notification-service', 'audit', NOW(), NOW()),
    ('notification-service-notification-threshold-minutes', '60', 'Minimum interval in minutes between notification-service audit alerts', 'audit', NOW(), NOW()),
    ('strategy-service-threshold-minutes', '720', 'Audit threshold in minutes for strategy-service', 'audit', NOW(), NOW()),
    ('strategy-service-notification-threshold-minutes', '60', 'Minimum interval in minutes between strategy-service audit alerts', 'audit', NOW(), NOW()),
    ('wallet-service-threshold-minutes', '720', 'Audit threshold in minutes for wallet-service', 'audit', NOW(), NOW()),
    ('wallet-service-notification-threshold-minutes', '60', 'Minimum interval in minutes between wallet-service audit alerts', 'audit', NOW(), NOW()),

    ('endpoint', '/market-data/api/v1/markets', 'Endpoint that returns monitored BTC binary markets', 'market-update', NOW(), NOW()),
    ('threshold-minutes', '15', 'Maximum age in minutes for market data', 'market-update', NOW(), NOW()),
    ('notification-threshold-minutes', '60', 'Minimum interval in minutes between stale market alerts', 'market-update', NOW(), NOW()),

    ('endpoint', '/strategy/api/v1/signals/opportunities', 'Endpoint that returns recent strategy signal opportunities', 'signal-opportunity', NOW(), NOW()),
    ('threshold-minutes', '15', 'Maximum interval in minutes without a signal opportunity', 'signal-opportunity', NOW(), NOW()),
    ('notification-threshold-minutes', '60', 'Minimum interval in minutes between signal opportunity alerts', 'signal-opportunity', NOW(), NOW()),

    ('endpoint', '/bot/api/v1/trades/executions', 'Endpoint that returns recent bot trade executions', 'trade-execution', NOW(), NOW()),
    ('threshold-minutes', '15', 'Maximum interval in minutes without a trade execution', 'trade-execution', NOW(), NOW()),
    ('notification-threshold-minutes', '60', 'Minimum interval in minutes between trade execution alerts', 'trade-execution', NOW(), NOW()),

    ('cron', '0 0 10 * * *', 'Cron expression for daily summary job', 'daily-summary', NOW(), NOW()),
    ('telegram-chat-id', '-1', 'Telegram chat ID for sending notifications', 'notification', NOW(), NOW())
    ON CONFLICT (service_name, config_key) DO NOTHING;
