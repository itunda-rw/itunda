-- Escrow is settled by independent request paths (buyer confirmation, scheduled
-- auto-release, and human dispute resolution).  A version column makes those state
-- transitions compare-and-swap operations instead of allowing a duplicate payout.
ALTER TABLE marketplace_escrows
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
