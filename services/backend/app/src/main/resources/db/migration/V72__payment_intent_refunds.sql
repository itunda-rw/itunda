ALTER TABLE payment_intents ADD COLUMN refunded_amount DECIMAL(18,2) NOT NULL DEFAULT 0 AFTER fail_url;
