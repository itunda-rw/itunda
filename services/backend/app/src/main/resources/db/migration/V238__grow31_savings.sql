-- Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent
-- (rw.itunda.savings.Grow31SavingsService, 2026-08-12) -- see Grow31SavingsPlan.kt's own
-- doc comment. Distinct from weekly_savings_plans: a real once-per-calendar-day
-- USER-triggered deposit (not an auto-debit) over a short fixed 31-day term, with a
-- streak-tiered bonus rate keyed on the longest unbroken daily-deposit streak reached.

CREATE TABLE grow31_savings_plans (
    id                    VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id               VARCHAR(64) NOT NULL,
    wallet_id             VARCHAR(64) NOT NULL,
    name                  VARCHAR(255) NOT NULL,
    daily_amount          DECIMAL(18,2) NOT NULL,
    start_date            DATE NOT NULL,
    days_elapsed          INT NOT NULL DEFAULT 0,
    current_streak        INT NOT NULL DEFAULT 0,
    longest_streak        INT NOT NULL DEFAULT 0,
    last_deposit_date     DATE NULL,
    total_saved           DECIMAL(18,2) NOT NULL DEFAULT 0,
    base_rate             DOUBLE NOT NULL,
    status                VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at            DATETIME(6) NOT NULL,
    matured_at            DATETIME(6) NULL,
    cancelled_at          DATETIME(6) NULL,
    withdrawn_at          DATETIME(6) NULL,
    total_interest_paid   DECIMAL(18,2) NULL,
    version               BIGINT NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_grow31_savings_plans_user_id ON grow31_savings_plans (user_id);
CREATE INDEX idx_grow31_savings_plans_status_start_date ON grow31_savings_plans (status, start_date);

CREATE TABLE grow31_savings_deposits (
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_id             VARCHAR(64) NOT NULL,
    day_number          INT NOT NULL,
    deposit_date        DATE NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    streak_at_deposit   INT NOT NULL,
    deposited_at        DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_grow31_savings_deposits_plan_id ON grow31_savings_deposits (plan_id);
