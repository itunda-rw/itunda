-- Weekly collection, maturity, cancellation, and withdrawal share plan state.
ALTER TABLE weekly_savings_plans
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
