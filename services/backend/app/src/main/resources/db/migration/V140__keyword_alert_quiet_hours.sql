-- Real 당근마켓 방해금지 시간 (do-not-disturb window) for keyword alerts
-- (rw.itunda.marketplace.KeywordAlertService, 2026-07-27). See
-- KeywordAlertQuietHours.kt's own doc comment for the full sourced account.

CREATE TABLE keyword_alert_quiet_hours (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL UNIQUE,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
