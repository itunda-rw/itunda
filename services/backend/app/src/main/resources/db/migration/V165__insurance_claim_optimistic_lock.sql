-- A claim approval posts a payout.  Require a compare-and-swap update so concurrent
-- reviewers cannot settle a submitted claim twice.
ALTER TABLE insurance_claims
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
