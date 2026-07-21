ALTER TABLE merchants ADD COLUMN api_key_hash VARCHAR(64) NULL AFTER kyb_verified;
ALTER TABLE payment_intents ADD COLUMN order_id VARCHAR(200) NULL AFTER description;
ALTER TABLE payment_intents ADD COLUMN success_url VARCHAR(500) NULL AFTER order_id;
ALTER TABLE payment_intents ADD COLUMN fail_url VARCHAR(500) NULL AFTER success_url;
