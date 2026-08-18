-- Real Toss Place-style 자동 적립 (automatic per-merchant point accrual)
-- (rw.itunda.core.domain.MerchantLoyaltyAccount, 2026-08-18) -- one real balance per
-- (merchant, customer) pair, earned on real completed payments at that merchant,
-- redeemable only there. Purely additive, no ledger account involved -- see the
-- domain class's own doc comment for why this is deliberately data-only.

CREATE TABLE merchant_loyalty_accounts (
    id             VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id    VARCHAR(64)    NOT NULL,
    customer_id    VARCHAR(64)    NOT NULL,
    point_balance  DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    UNIQUE KEY uq_merchant_loyalty_accounts_merchant_customer (merchant_id, customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
