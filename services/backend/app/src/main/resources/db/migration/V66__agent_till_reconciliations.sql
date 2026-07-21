CREATE TABLE agent_till_reconciliations (
    id                   VARCHAR(64)    NOT NULL PRIMARY KEY,
    agent_id             VARCHAR(64)    NOT NULL,
    business_date        DATE           NOT NULL,
    expected_cash        DECIMAL(18, 2) NOT NULL,
    counted_cash         DECIMAL(18, 2) NOT NULL,
    variance             DECIMAL(18, 2) NOT NULL,
    submitted_by_user_id VARCHAR(64)    NOT NULL,
    status               VARCHAR(20)    NOT NULL,
    reviewed_by_user_id  VARCHAR(64)    NULL,
    review_note          VARCHAR(255)   NULL,
    reviewed_at          DATETIME(6)    NULL,
    created_at           DATETIME(6)    NOT NULL,
    CONSTRAINT uq_agent_till_reconciliations_day UNIQUE (agent_id, business_date)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_agent_till_reconciliations_status_created_at ON agent_till_reconciliations (status, created_at);
