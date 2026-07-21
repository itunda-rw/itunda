ALTER TABLE interest_jars ADD COLUMN last_nudged_at TIMESTAMP NULL AFTER next_payout_at;
