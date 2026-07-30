-- Maturity and principal withdrawal are mutually exclusive settlement transitions.
ALTER TABLE upfront_interest_deposits
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
