-- Real push-notification device-token registration (rw.itunda.core.push,
-- 2026-07-26) -- see DeviceToken.kt's own doc comment. Closes the "push notifications
-- on new bookings" half of Naver Smart Place's real, sourced feature set.

CREATE TABLE device_tokens (
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    platform   VARCHAR(16)  NOT NULL,
    token      VARCHAR(512) NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    updated_at DATETIME(6)  NOT NULL,
    CONSTRAINT uq_device_tokens_token UNIQUE (token),
    INDEX idx_device_tokens_user_id (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
