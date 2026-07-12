-- Real fraud/velocity rule engine -- previously nothing existed here at all (see
-- docs/TOSS_PARITY_MATRIX.md's Operations/Fraud row: "No fraud-rule engine exists yet").
-- Review-only by design -- never blocks a transaction, see FraudRuleEngine.kt's own comment.
CREATE TABLE fraud_flags (
    id              VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id         VARCHAR(64)    NOT NULL,
    transaction_id  VARCHAR(64)    NOT NULL,
    rule            VARCHAR(32)    NOT NULL,
    description     VARCHAR(255)   NOT NULL,
    amount          DECIMAL(18, 2) NOT NULL,
    reviewed        BOOLEAN        NOT NULL DEFAULT FALSE,
    decision        VARCHAR(16)    NULL,
    reviewed_by     VARCHAR(64)    NULL,
    reviewed_at     DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_fraud_flags_reviewed_created_at ON fraud_flags (reviewed, created_at);
CREATE INDEX idx_fraud_flags_user_id ON fraud_flags (user_id);
