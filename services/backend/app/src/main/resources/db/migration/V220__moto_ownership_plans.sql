-- Real Rwanda moto-taxi ownership savings-to-loan plan -- see MotoOwnershipPlan.kt's
-- own doc comment for the full sourced account. A real ~600,000 RWF entry-level
-- moto-taxi bike price (Anadolu Agency, 14 May 2021) and Ampersand's real rent-to-own
-- mechanic in this exact sector (Frontier Tech Hub, WeeTracker/WEF) motivate a
-- digital savings-then-loan path to bike ownership, filling the real gap left by
-- Rwanda's dissolved taxi-moto cooperatives (Africa-Press, 2026).
--
-- Honest v1 limitation: this is an UNSECURED facility once the loan phase starts --
-- itunda has no path to a real chattel lien or RURA vehicle-registry hold, so it
-- cannot repossess the bike or verify it was actually purchased.

CREATE TABLE moto_ownership_plans (
    id                              VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id                         VARCHAR(64)   NOT NULL,
    bike_price                      DECIMAL(18,2) NOT NULL,
    down_payment_target             DECIMAL(18,2) NOT NULL,
    saved_amount                    DECIMAL(18,2) NOT NULL,
    daily_contribution              DECIMAL(18,2) NOT NULL,
    loan_outstanding                DECIMAL(18,2) NOT NULL,
    status                          VARCHAR(16)   NOT NULL DEFAULT 'SAVING',
    last_auto_contribution_at       DATETIME(6)   NULL,
    created_at                      DATETIME(6)   NOT NULL,
    version                         BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_moto_ownership_plans_user_id ON moto_ownership_plans (user_id);
CREATE INDEX idx_moto_ownership_plans_user_status ON moto_ownership_plans (user_id, status);
CREATE INDEX idx_moto_ownership_plans_status_last_auto ON moto_ownership_plans (status, last_auto_contribution_at);
