-- Prevent concurrent delivery/cancellation transitions from producing conflicting
-- fulfillment state or duplicate refund processing.
ALTER TABLE orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
