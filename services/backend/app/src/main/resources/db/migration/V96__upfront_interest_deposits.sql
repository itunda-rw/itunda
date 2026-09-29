-- Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
-- equivalent (2026-07-25) -- see docs/DESIGN_REFERENCES.md Section 4/6 research and
-- UpfrontInterestDeposit's own doc comment for the sourced mechanics and the
-- deliberate no-early-withdrawal scoping choice that keeps this un-exploitable.
CREATE TABLE upfront_interest_deposits (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id           VARCHAR(64)  NOT NULL,
    wallet_id         VARCHAR(64)  NOT NULL,
    principal         DECIMAL(18,2) NOT NULL,
    interest_rate     DOUBLE       NOT NULL,
    interest_paid     DECIMAL(18,2) NOT NULL,
    status            VARCHAR(16)  NOT NULL,
    opened_at         TIMESTAMP    NOT NULL,
    matures_at        TIMESTAMP    NOT NULL,
    matured_at        TIMESTAMP    NULL,
    withdrawn_at      TIMESTAMP    NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_upfront_interest_deposits_user_id ON upfront_interest_deposits (user_id);
CREATE INDEX idx_upfront_interest_deposits_status_matures_at ON upfront_interest_deposits (status, matures_at);
