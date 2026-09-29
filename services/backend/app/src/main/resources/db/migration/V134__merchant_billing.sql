-- Real Kakao Pay 정기결제/Toss Payments billing-key-style merchant recurring billing
-- (rw.itunda.merchant.MerchantBillingService, 2026-07-26). See MerchantBillingPlan.kt/
-- MerchantBillingSubscription.kt's own doc comments for the full account.

CREATE TABLE merchant_billing_plans (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description VARCHAR(500) NULL,
    amount DECIMAL(18, 2) NOT NULL,
    interval_days INT NOT NULL,
    active TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_merchant_billing_plans_merchant (merchant_id)
);

CREATE TABLE merchant_billing_subscriptions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    plan_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    customer_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    next_charge_at DATETIME(6) NOT NULL,
    last_charged_at DATETIME(6) NULL,
    charge_count INT NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(255) NULL,
    created_at DATETIME(6) NOT NULL,
    cancelled_at DATETIME(6) NULL,
    INDEX idx_merchant_billing_subs_customer (customer_id),
    INDEX idx_merchant_billing_subs_status_next (status, next_charge_at)
);
