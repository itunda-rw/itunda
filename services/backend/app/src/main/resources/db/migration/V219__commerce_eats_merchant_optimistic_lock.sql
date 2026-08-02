-- Real optimistic locks for four real check-then-act create-or-extend flows found
-- live in a 2026-08-02 audit pass: DailyStepReward.reportSteps (a tier can be
-- double-credited by two concurrent step reports racing on the same day's row),
-- EatsMembershipService.subscribe / PlatformMembershipService.subscribe / MerchantAdService
-- .createOrExtendAd (each already has a real DB unique constraint that protects the very
-- first subscribe/ad's INSERT, but not two concurrent EXTENSIONS of an existing row, which
-- would both charge the real wallet but only actually extend `activeUntil` once). See each
-- entity's own doc comment for the full account.
ALTER TABLE daily_step_rewards ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE eats_memberships ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE platform_memberships ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE merchant_ads ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
