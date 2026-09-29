-- A paid table order may be updated by the restaurant while its buyer cancels it.
-- Make the lifecycle compare-and-swap safe.
ALTER TABLE dine_in_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
