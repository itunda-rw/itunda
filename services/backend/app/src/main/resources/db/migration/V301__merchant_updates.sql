-- Real business news/updates feed (itunda Maps redesign, 2026-08-28, direct Naver Map
-- reference: the place-detail 소식 tab) -- genuinely new, no prior data model existed.

CREATE TABLE merchant_updates (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    merchant_id  VARCHAR(64) NOT NULL,
    label        VARCHAR(16) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    body         VARCHAR(2000) NOT NULL,
    period_start DATETIME(6) NULL,
    period_end   DATETIME(6) NULL,
    like_count   BIGINT NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_merchant_updates_merchant_id ON merchant_updates (merchant_id);

CREATE TABLE merchant_update_likes (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    update_id  VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_merchant_update_likes_update_user ON merchant_update_likes (update_id, user_id);
