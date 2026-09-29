-- User cancellation and the execution scheduler must not resolve a transfer twice.
ALTER TABLE scheduled_transfers ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
