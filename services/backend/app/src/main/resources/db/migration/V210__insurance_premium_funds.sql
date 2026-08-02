-- Real Ejo Heza ya Moto-style insurance premium savings fund
-- (rw.itunda.insurance.InsuranceService) -- see InsurancePremiumFund.kt's own doc
-- comment. Sourced from Africa-Press (2026) reporting Rwanda's ~46,000 registered
-- taxi-moto riders facing insurance premiums up to RWF 250,000/year for older bikes,
-- worsened since the taxi-moto cooperatives that used to pool this cost were
-- dissolved. Lets any user save toward a specific policy's premium ahead of time so
-- the recurring premium-collection scheduler can draw on it instead of lapsing the
-- policy when the wallet alone is short.

CREATE TABLE insurance_premium_funds (
    id                       VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id                  VARCHAR(64)   NOT NULL,
    policy_id                VARCHAR(64)   NOT NULL,
    target_amount            DECIMAL(18,2) NOT NULL,
    current_amount           DECIMAL(18,2) NOT NULL DEFAULT 0,
    daily_contribution       DECIMAL(18,2) NOT NULL DEFAULT 0,
    status                   VARCHAR(16)   NOT NULL DEFAULT 'active',
    last_auto_contribution_at DATETIME(6)  NULL,
    created_at               DATETIME(6)   NOT NULL,
    version                  BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_insurance_premium_funds_user_id ON insurance_premium_funds (user_id);
CREATE INDEX idx_insurance_premium_funds_policy_status ON insurance_premium_funds (policy_id, status);
