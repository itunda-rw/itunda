-- A P2P request can be paid while expiry processing observes it; only one resolution wins.
ALTER TABLE p2p_payment_requests
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
