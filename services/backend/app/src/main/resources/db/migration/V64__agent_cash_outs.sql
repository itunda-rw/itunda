ALTER TABLE agents ADD COLUMN daily_cash_out_limit DECIMAL(18, 2) NOT NULL DEFAULT 0 AFTER daily_cash_in_limit;
UPDATE agents SET daily_cash_out_limit = daily_cash_in_limit WHERE daily_cash_out_limit = 0;

CREATE TABLE agent_cash_outs (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    agent_id              VARCHAR(64)    NOT NULL,
    wallet_id             VARCHAR(64)    NOT NULL,
    receipt_number        VARCHAR(80)    NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    amount                DECIMAL(18, 2) NOT NULL,
    paid_by_user_id       VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agent_cash_outs_receipt UNIQUE (receipt_number),
    CONSTRAINT uq_agent_cash_outs_ledger_transaction UNIQUE (ledger_transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_cash_outs_agent_created_at ON agent_cash_outs (agent_id, created_at);
