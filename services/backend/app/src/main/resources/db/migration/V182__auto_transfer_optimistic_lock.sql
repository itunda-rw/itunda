-- Scheduled execution must not race with a customer's pause, edit, or cancellation.
ALTER TABLE auto_transfers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
