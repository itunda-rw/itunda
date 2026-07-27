-- Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
-- OverdraftAccount.kt's own doc comment for the full sourced account.

CREATE TABLE overdraft_accounts (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    wallet_id VARCHAR(64) NOT NULL,
    credit_limit DECIMAL(18,2) NOT NULL,
    drawn_balance DECIMAL(18,2) NOT NULL DEFAULT 0,
    interest_rate DOUBLE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    last_accrual_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL
);
