-- Claim and automatic expiry both settle Gift holding funds.
ALTER TABLE gifts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
