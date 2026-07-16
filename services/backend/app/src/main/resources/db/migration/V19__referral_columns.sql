-- Real referral subsystem (2026-07-17), closing the last of the two gaps
-- RewardsService.isEligible's own comment named as honor-system on purpose: "no
-- referral subsystem -- codes, attribution, completion detection -- exists at all."
-- referral_code is nullable and unique -- InnoDB unique indexes allow multiple NULLs,
-- so accounts created before this migration simply have no code until they register
-- again isn't required; AuthService issues one lazily the next time it's needed is not
-- implemented here on purpose (out of scope for this pass, same "manual backfill, not a
-- fabricated flow" convention V4__user_role.sql already established for `role`).
ALTER TABLE users ADD COLUMN referral_code VARCHAR(16) NULL;
ALTER TABLE users ADD COLUMN referred_by_user_id VARCHAR(64) NULL;
CREATE UNIQUE INDEX idx_users_referral_code ON users(referral_code);
CREATE INDEX idx_users_referred_by ON users(referred_by_user_id);
