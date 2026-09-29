-- Real KakaoBank 모임통장 automated dues collection (rw.itunda.savings.GroupAccountService,
-- 2026-07-26) -- see docs/DESIGN_REFERENCES.md's Group Account row ("automated dues collection
-- with per-member payment-day rules, one-tap 'request all unpaid', playful reminder cards").

ALTER TABLE group_accounts
    ADD COLUMN monthly_dues_amount DECIMAL(18, 2) NULL AFTER wallet_id;

CREATE TABLE group_account_contributions (
    id               VARCHAR(64) NOT NULL PRIMARY KEY,
    group_account_id VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64) NOT NULL,
    cycle_month      VARCHAR(7)  NOT NULL,
    amount           DECIMAL(18, 2) NOT NULL,
    created_at       DATETIME(6) NOT NULL,
    KEY idx_group_account_contributions_cycle (group_account_id, cycle_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE group_account_dues_reminders (
    id               VARCHAR(64) NOT NULL PRIMARY KEY,
    group_account_id VARCHAR(64) NOT NULL,
    user_id          VARCHAR(64) NOT NULL,
    cycle_month      VARCHAR(7)  NOT NULL,
    sent_at          DATETIME(6) NOT NULL,
    CONSTRAINT uq_group_account_dues_reminders UNIQUE (group_account_id, user_id, cycle_month)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
