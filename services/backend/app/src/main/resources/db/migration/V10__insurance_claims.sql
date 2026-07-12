-- Real insurance claims filing -- previously enrollment was real and ledger-backed but
-- there was no way to ever file a claim (see docs/TOSS_PARITY_MATRIX.md's Insurance row).
CREATE TABLE insurance_claims (
    id               VARCHAR(64)    NOT NULL PRIMARY KEY,
    policy_id        VARCHAR(64)    NOT NULL,
    user_id          VARCHAR(64)    NOT NULL,
    description      VARCHAR(255)   NOT NULL,
    amount           DECIMAL(18, 2) NOT NULL,
    status           VARCHAR(16)    NOT NULL,
    submitted_at     DATETIME(6)    NOT NULL,
    reviewed_by      VARCHAR(64)    NULL,
    reviewed_at      DATETIME(6)    NULL,
    decision_reason  VARCHAR(255)   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_insurance_claims_user_id ON insurance_claims (user_id);
CREATE INDEX idx_insurance_claims_status ON insurance_claims (status);
