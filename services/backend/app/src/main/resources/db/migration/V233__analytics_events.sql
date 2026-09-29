-- Real, minimal product-analytics table (2026-08-10) -- see AnalyticsEvent.kt's own
-- doc comment. itunda had zero user-behavior events recorded anywhere before this;
-- every "which feature actually matters" decision was a judgment call, not a measured
-- one. Deliberately small and closed-vocabulary, not a general event-schema platform.
CREATE TABLE analytics_events (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    event_name VARCHAR(64) NOT NULL,
    platform VARCHAR(16) NOT NULL,
    metadata_json VARCHAR(512) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_analytics_events_event_name_created_at (event_name, created_at),
    INDEX idx_analytics_events_user_id_created_at (user_id, created_at)
);
