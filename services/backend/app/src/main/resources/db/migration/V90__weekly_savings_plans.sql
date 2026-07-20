-- Real KakaoBank 26주적금 (26-week savings) equivalent
-- (rw.itunda.savings.WeeklySavingsService, 2026-07-21) -- see WeeklySavingsPlan.kt's own
-- doc comment. Distinct from savings_goals/interest_jars/group_accounts: an
-- auto-escalating weekly auto-debit, a hard weekday lock, per-installment interest, and
-- a streak-gated preferential rate.

CREATE TABLE weekly_savings_plans (
    id                        VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id                   VARCHAR(64) NOT NULL,
    wallet_id                 VARCHAR(64) NOT NULL,
    name                      VARCHAR(255) NOT NULL,
    base_weekly_amount        DECIMAL(18,2) NOT NULL,
    escalation_rate           DECIMAL(6,4) NOT NULL,
    opening_weekday           INT NOT NULL,
    base_rate                 DOUBLE NOT NULL,
    bonus_rate                DOUBLE NOT NULL,
    installments_collected    INT NOT NULL DEFAULT 0,
    weeks_elapsed             INT NOT NULL DEFAULT 0,
    current_amount            DECIMAL(18,2) NOT NULL DEFAULT 0,
    streak_broken             TINYINT(1) NOT NULL DEFAULT 0,
    status                    VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    next_installment_due_at   DATETIME(6) NOT NULL,
    created_at                DATETIME(6) NOT NULL,
    matured_at                DATETIME(6) NULL,
    cancelled_at              DATETIME(6) NULL,
    withdrawn_at              DATETIME(6) NULL,
    total_interest_paid       DECIMAL(18,2) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_weekly_savings_plans_user_id ON weekly_savings_plans (user_id);
CREATE INDEX idx_weekly_savings_plans_status_due ON weekly_savings_plans (status, next_installment_due_at);

CREATE TABLE weekly_savings_installments (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_id        VARCHAR(64) NOT NULL,
    week_number    INT NOT NULL,
    amount         DECIMAL(18,2) NOT NULL,
    deposited_at   DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_weekly_savings_installments_plan_id ON weekly_savings_installments (plan_id);
