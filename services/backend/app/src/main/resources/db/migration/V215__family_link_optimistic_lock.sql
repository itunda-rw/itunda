-- Real bug found live (2026-08-02): respondToInvite/revokeLink both read-then-mutate a
-- FamilyLink's status with no @Version guard -- two concurrent respondToInvite calls
-- (e.g. an accept and a decline racing from a flaky client retry) can both read PENDING
-- and both commit, with whichever writes last silently winning instead of the loser
-- getting a real 409. Every sibling entity in this same money-adjacent family
-- (P2pPaymentRequest, Gift, GiftVoucher, ScheduledTransfer, AutoTransfer) already has
-- this guard; FamilyLink was the one left out.
ALTER TABLE family_links
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
