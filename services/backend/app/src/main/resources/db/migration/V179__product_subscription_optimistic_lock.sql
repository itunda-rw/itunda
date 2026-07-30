-- Subscription cancellation and scheduled fulfillment must not race.
ALTER TABLE product_subscriptions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
