-- Real round-up auto-saving (rw.itunda.savings.RoundUpService, 2026-07-25) -- closes
-- the gap named in Kakao Pay's own real 머니굴리기 ("rolling money") product.

CREATE TABLE round_up_settings (
    id                VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id           VARCHAR(64)    NOT NULL,
    enabled           BOOLEAN        NOT NULL DEFAULT FALSE,
    round_to_nearest  DECIMAL(18, 2) NOT NULL,
    target_goal_id    VARCHAR(64),
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL,
    CONSTRAINT uq_round_up_settings_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
