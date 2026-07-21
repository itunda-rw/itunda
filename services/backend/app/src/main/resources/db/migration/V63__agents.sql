CREATE TABLE agents (
    id                  VARCHAR(64)    NOT NULL PRIMARY KEY,
    display_name        VARCHAR(160)   NOT NULL,
    cash_account_id     VARCHAR(64)    NOT NULL,
    status              VARCHAR(16)    NOT NULL,
    daily_cash_in_limit DECIMAL(18, 2) NOT NULL,
    created_at          DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agents_cash_account_id UNIQUE (cash_account_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE agent_cash_ins (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    agent_id              VARCHAR(64)    NOT NULL,
    wallet_id             VARCHAR(64)    NOT NULL,
    receipt_number        VARCHAR(80)    NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    amount                DECIMAL(18, 2) NOT NULL,
    accepted_by_user_id   VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agent_cash_ins_receipt UNIQUE (receipt_number),
    CONSTRAINT uq_agent_cash_ins_ledger_transaction UNIQUE (ledger_transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_cash_ins_agent_created_at ON agent_cash_ins (agent_id, created_at);
