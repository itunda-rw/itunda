-- Real Kakao Map "친구위치" (Friend Location) live location sharing
-- (rw.itunda.maps.LiveLocationShareService, 2026-08-19) -- a time-bounded, one-to-one
-- live position share, distinct from MapBookmark's own static folder share/subscribe.
-- Always time-bounded (no "unlimited" option, unlike Kakao's own real earlier design)
-- -- a deliberate safety choice, see LiveLocationShare.kt's own doc comment.

CREATE TABLE live_location_shares (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    sharer_user_id      VARCHAR(64)  NOT NULL,
    recipient_user_id   VARCHAR(64)  NOT NULL,
    latitude            DOUBLE,
    longitude           DOUBLE,
    location_updated_at DATETIME(6),
    expires_at          DATETIME(6)  NOT NULL,
    revoked             BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at          DATETIME(6)  NOT NULL,
    version             BIGINT       NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_live_location_shares_sharer_user_id ON live_location_shares (sharer_user_id);
CREATE INDEX idx_live_location_shares_recipient_user_id ON live_location_shares (recipient_user_id);
