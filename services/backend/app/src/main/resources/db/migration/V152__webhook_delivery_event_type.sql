-- Keep the immutable event contract queryable for merchant support and replay operations.
-- Nullable preserves delivery history created before event types were persisted separately.
ALTER TABLE webhook_deliveries
    ADD COLUMN event_type VARCHAR(64) NULL AFTER merchant_id;

CREATE INDEX idx_webhook_delivery_merchant_event_created
    ON webhook_deliveries (merchant_id, event_type, created_at);
