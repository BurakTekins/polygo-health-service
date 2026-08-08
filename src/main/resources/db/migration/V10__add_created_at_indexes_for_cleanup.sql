CREATE INDEX IF NOT EXISTS idx_notification_messages_created_at ON health.notification_messages (created_at);
CREATE INDEX IF NOT EXISTS idx_daily_summary_messages_created_at ON health.daily_summary_messages (created_at);
