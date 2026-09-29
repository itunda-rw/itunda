-- Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) daily mission row --
-- see ShoppingMissionReward.kt's own doc comment. One real row per user per real
-- calendar day, same shape as daily_step_rewards (V144).

CREATE TABLE shopping_mission_rewards (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    mission_date VARCHAR(10) NOT NULL,
    checked_in TINYINT(1) NOT NULL DEFAULT 0,
    scrolled TINYINT(1) NOT NULL DEFAULT 0,
    spun TINYINT(1) NOT NULL DEFAULT 0,
    cat_fed TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uq_shopping_mission_rewards_user_date (user_id, mission_date)
);

-- Real one-time-ever "welcome" claim (the reference screenshot's "Claim re..." icon) --
-- a plain PK-per-user table, so the primary key itself is the real once-ever guard,
-- no date column needed.
CREATE TABLE shopping_welcome_bonus_claims (
    user_id VARCHAR(64) NOT NULL PRIMARY KEY,
    claimed_at DATETIME(6) NOT NULL
);
