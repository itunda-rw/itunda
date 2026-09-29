-- Scheduled billing and customer cancellation must not race into an unwanted charge.
ALTER TABLE merchant_billing_subscriptions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
