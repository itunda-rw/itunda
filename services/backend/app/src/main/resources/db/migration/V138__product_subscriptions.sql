-- Real Coupang 정기배송 (subscribe & save)-style recurring product delivery
-- (rw.itunda.commerce.ProductSubscriptionService, 2026-07-27). See
-- ProductSubscription.kt's own doc comment for the full sourced account.

CREATE TABLE product_subscriptions (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    customer_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    product_id VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    interval_days INT NOT NULL,
    delivery_address VARCHAR(500) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    next_delivery_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    last_delivered_at DATETIME(6) NULL,
    delivery_count INT NOT NULL DEFAULT 0,
    last_failure_reason VARCHAR(255) NULL,
    cancelled_at DATETIME(6) NULL,
    INDEX idx_product_subscriptions_customer (customer_id),
    INDEX idx_product_subscriptions_status_next (status, next_delivery_at)
);
