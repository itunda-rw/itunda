-- Real step-reward lottery bonus (item 248, docs/DESIGN_REFERENCES.md Section 15) --
-- Toss Makers Conference 25 (toss.tech/article/42221): a real A/B result showing a
-- lottery-style unpredictable payout sustained engagement better than a fixed reward,
-- with no measured churn. Explicit user decision to build this "toss style" -- layered
-- ON TOP of itunda's existing guaranteed StepRewardTier payouts, never replacing them,
-- and with the odds stated to the user (never a hidden mechanic) -- the real distinction
-- between a legitimate bonus and the dark-pattern-style variable-ratio reward loop this
-- project's own Section 11 discipline exists to avoid.
--
-- One boolean per tier, mirroring claimed_tier_*'s own existing per-tier shape: the
-- lottery draw runs at most once per tier per day, exactly when that tier is first
-- claimed, so "already rolled" reuses the same one-time gate claimed_tier_* already
-- provides rather than needing separate state.

ALTER TABLE daily_step_rewards
    ADD COLUMN lottery_won_tier_1000 BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN lottery_won_tier_5000 BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN lottery_won_tier_10000 BOOLEAN NOT NULL DEFAULT FALSE;
