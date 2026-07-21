CREATE TABLE agent_withdrawal_authorizations (
    id         VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id    VARCHAR(64)    NOT NULL,
    wallet_id  VARCHAR(64)    NOT NULL,
    code       VARCHAR(12)    NOT NULL,
    amount     DECIMAL(18, 2) NOT NULL,
    expires_at DATETIME(6)    NOT NULL,
    consumed_at DATETIME(6)   NULL,
    created_at DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agent_withdrawal_authorizations_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_withdrawal_authorizations_wallet_id ON agent_withdrawal_authorizations (wallet_id);
