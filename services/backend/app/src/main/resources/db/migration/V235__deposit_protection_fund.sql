-- Real itunda Deposit Protection Fund (rw.itunda.savings.DepositProtectionService) --
-- see DepositProtectionFund.kt's own doc comment for the full honesty framing: real,
-- working ledger-backed mechanics (this codebase already builds real internal systems
-- for institutions it has no genuine external connection to -- VUP, RSE, SACCO/Ikimina
-- -- rather than refusing to), but disclosed everywhere it's shown as itunda's OWN
-- internal reserve, not a real BNR-backed deposit insurance scheme.
--
-- Singleton row (id = 'system'): one pooled reserve covering every depositor
-- collectively, the same real shape a government deposit insurance fund has -- not a
-- per-user balance like interest_jars or insurance_premium_funds.

CREATE TABLE deposit_protection_fund (
    id                     VARCHAR(16)   NOT NULL PRIMARY KEY,
    reserve_balance        DECIMAL(18,2) NOT NULL DEFAULT 0,
    coverage_cap_per_user  DECIMAL(18,2) NOT NULL DEFAULT 500000,
    contribution_rate_bps  INT           NOT NULL DEFAULT 50,
    last_contribution_at   DATETIME(6)   NULL,
    created_at             DATETIME(6)   NOT NULL,
    version                BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
