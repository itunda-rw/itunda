-- Durable, owner-bound transfer quotes. Never edit an applied migration.
CREATE TABLE transfer_quotes (
    id              VARCHAR(80)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL,
    from_account_id VARCHAR(64)    NOT NULL,
    recipient       VARCHAR(255)   NOT NULL,
    amount          DECIMAL(18, 2) NOT NULL,
    fee             DECIMAL(18, 2) NOT NULL,
    total_debit     DECIMAL(18, 2) NOT NULL,
    currency        VARCHAR(8)     NOT NULL,
    status          VARCHAR(16)    NOT NULL,
    expires_at      DATETIME(6)    NOT NULL,
    created_at      DATETIME(6)    NOT NULL,
    claimed_at      DATETIME(6)    NULL,
    confirmed_at    DATETIME(6)    NULL,
    CONSTRAINT chk_transfer_quotes_amount CHECK (amount > 0),
    CONSTRAINT chk_transfer_quotes_fee CHECK (fee >= 0),
    CONSTRAINT chk_transfer_quotes_total CHECK (total_debit = amount + fee)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_transfer_quotes_owner_status_expiry
    ON transfer_quotes (user_id, status, expires_at);
CREATE INDEX idx_transfer_quotes_expiry
    ON transfer_quotes (expires_at);
