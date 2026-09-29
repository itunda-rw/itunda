ALTER TABLE eats_order_items
    ADD COLUMN unavailable BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN refund_transaction_id VARCHAR(64) NULL;
