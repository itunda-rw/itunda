-- The order status controls rider payout and buyer refunds.  Use optimistic locking to
-- make concurrent lifecycle transitions mutually exclusive.
ALTER TABLE eats_orders
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
