-- Real person-to-person QR -- previously only merchant QR existed (see
-- docs/TOSS_PARITY_MATRIX.md's QR Pay row, corrected 2026-07-13). Deliberately a separate
-- table from payment_intents (merchant_id there is NOT NULL and merchant collection charges
-- a real fee; this doesn't -- direct wallet-to-wallet, no fee, no rail_suspense hop).
CREATE TABLE p2p_payment_requests (
    id                       VARCHAR(64)    NOT NULL PRIMARY KEY,
    requester_user_id        VARCHAR(64)    NOT NULL,
    amount                   DECIMAL(18, 2) NOT NULL,
    description              VARCHAR(255)   NOT NULL,
    status                   VARCHAR(16)    NOT NULL,
    expires_at               DATETIME(6)    NOT NULL,
    completed_transaction_id VARCHAR(64)    NULL,
    paid_by_user_id          VARCHAR(64)    NULL,
    created_at               DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_p2p_payment_requests_requester_created_at ON p2p_payment_requests (requester_user_id, created_at);
