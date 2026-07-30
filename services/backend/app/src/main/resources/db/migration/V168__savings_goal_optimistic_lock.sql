-- Manual and scheduled contributions share the goal balance.
ALTER TABLE savings_goals
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
