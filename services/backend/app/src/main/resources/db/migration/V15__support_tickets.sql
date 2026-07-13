-- Real customer support ticket workflow -- previously docs/TOSS_PARITY_MATRIX.md's
-- "Non-Negotiable Gates" section falsely claimed this gate was already met ("real ticket
-- creation/listing... and a real refund action"); a repo-wide grep found no support/
-- ticket module anywhere in services/backend (corrected 2026-07-13). This is that real
-- table.
CREATE TABLE support_tickets (
    id                   VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id              VARCHAR(64)  NOT NULL,
    transaction_id       VARCHAR(64)  NOT NULL,
    category             VARCHAR(24)  NOT NULL,
    description          TEXT         NOT NULL,
    status               VARCHAR(16)  NOT NULL,
    resolution           VARCHAR(16),
    resolution_notes     TEXT,
    refund_transaction_id VARCHAR(64),
    froze_wallet_id      VARCHAR(64),
    due_by               DATETIME(6)  NOT NULL,
    reviewed_by          VARCHAR(64),
    created_at           DATETIME(6)  NOT NULL,
    resolved_at          DATETIME(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_support_tickets_user ON support_tickets (user_id);
CREATE INDEX idx_support_tickets_status_due_by ON support_tickets (status, due_by);
