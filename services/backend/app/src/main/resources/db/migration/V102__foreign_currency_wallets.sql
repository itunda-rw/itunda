-- Real 토스뱅크 외화통장 (foreign-currency account) equivalent
-- (rw.itunda.wallet.ForeignCurrencyWalletService, 2026-07-25) -- reuses the existing
-- `wallets` table (WalletType.FOREIGN_CURRENCY, Wallet.currency = USD/EUR/GBP) with no
-- schema change there; this migration only adds the real conversion-history table.
-- The 4 new fx_clearing_* ledger_accounts rows are seeded idempotently by
-- SeedDataRunner/LedgerAccount.SEED_IDS on app startup, same as every other clearing
-- account, not by this migration.

CREATE TABLE currency_conversions (
    id             VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)    NOT NULL,
    from_currency  VARCHAR(8)     NOT NULL,
    to_currency    VARCHAR(8)     NOT NULL,
    from_amount    DECIMAL(18, 2) NOT NULL,
    to_amount      DECIMAL(18, 2) NOT NULL,
    rate           DECIMAL(18, 6) NOT NULL,
    margin_amount  DECIMAL(18, 2) NOT NULL,
    transaction_id VARCHAR(64)    NOT NULL,
    created_at     DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_currency_conversions_user_id ON currency_conversions (user_id);
