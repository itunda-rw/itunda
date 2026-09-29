-- Real Toss 만보기 (pedometer) walking-rewards feature -- see DailyStepReward.kt's own
-- doc comment for the full sourced account.

CREATE TABLE daily_step_rewards (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    reward_date VARCHAR(10) NOT NULL,
    steps INT NOT NULL DEFAULT 0,
    claimed_tier_1000 TINYINT(1) NOT NULL DEFAULT 0,
    claimed_tier_5000 TINYINT(1) NOT NULL DEFAULT 0,
    claimed_tier_10000 TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_daily_step_rewards_user_date (user_id, reward_date)
);
