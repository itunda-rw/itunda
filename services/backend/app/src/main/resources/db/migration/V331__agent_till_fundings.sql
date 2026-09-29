-- Real admin-accountability gap (2026-09-12): AgentAdminController.fundTill creates
-- real spendable balance in an agent's till but previously left zero record of which
-- admin acted -- unlike cashIn/cashOut (agent_cash_ins/agent_cash_outs) and till
-- reconciliation (agent_till_reconciliations.reviewed_by_user_id).
CREATE TABLE agent_till_fundings (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    agent_id              VARCHAR(64)    NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    amount                DECIMAL(18, 2) NOT NULL,
    reference             VARCHAR(80)    NOT NULL,
    funded_by_user_id     VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agent_till_fundings_ledger_transaction UNIQUE (ledger_transaction_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_till_fundings_agent_created_at ON agent_till_fundings (agent_id, created_at);
