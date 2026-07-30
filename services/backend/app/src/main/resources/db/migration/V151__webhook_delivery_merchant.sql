ALTER TABLE webhook_deliveries ADD COLUMN merchant_id VARCHAR(64) NULL AFTER id;
CREATE INDEX idx_webhook_deliveries_merchant_created ON webhook_deliveries (merchant_id, created_at);
