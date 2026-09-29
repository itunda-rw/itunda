-- Real Umurenge SACCO-style shares & dividends, rw.itunda.savings.SaccoService. See
-- SaccoShareholding.kt's own doc comment for the full sourced account (Rwanda's real
-- 416-sector government-backed cooperative savings model). Genuinely distinct from
-- every Toss/Kakao/Naver/Coupang-sourced feature in this backend and from Ikimina
-- (rotating-pot ROSCA) -- a second Rwanda-specific financial primitive.

CREATE TABLE sacco_shareholdings (
    id                  VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64)   NOT NULL,
    wallet_id           VARCHAR(64)   NOT NULL,
    shares_held         DECIMAL(18,2) NOT NULL DEFAULT 0,
    total_contributed   DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at          DATETIME(6)   NOT NULL,
    version             BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_sacco_shareholdings_user UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sacco_dividend_distributions (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    distribution_date     DATETIME(6)   NOT NULL,
    total_pool_value      DECIMAL(18,2) NOT NULL,
    dividend_rate         DECIMAL(10,6) NOT NULL,
    total_dividend_paid   DECIMAL(18,2) NOT NULL DEFAULT 0,
    created_at            DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE sacco_dividend_payouts (
    id                      VARCHAR(64)   NOT NULL PRIMARY KEY,
    distribution_id         VARCHAR(64)   NOT NULL,
    shareholding_id         VARCHAR(64)   NOT NULL,
    amount                  DECIMAL(18,2) NOT NULL,
    payout_transaction_id   VARCHAR(64)   NOT NULL,
    created_at              DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_sacco_dividend_payouts_shareholding_id ON sacco_dividend_payouts (shareholding_id);
