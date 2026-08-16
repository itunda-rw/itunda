ALTER TABLE eats_orders
    ADD COLUMN tip_amount DECIMAL(18, 2) NULL,
    ADD COLUMN tip_transaction_id VARCHAR(64) NULL;
