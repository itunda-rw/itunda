-- Ownership verification must have one definitive human-review outcome.
ALTER TABLE property_ownership_submissions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
