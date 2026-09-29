-- Real 마감할인 (closing/surplus discount) support (2026-08-15) -- see
-- MerchantProduct.kt's own doc comment. A real, government-partnered
-- (기후부/환경부 + major delivery apps) food-waste-reduction feature that launched
-- 2026-06-15: unsold near-closing food listed at a time-boxed discount.

ALTER TABLE merchant_products ADD COLUMN is_surplus_deal   BOOLEAN     NOT NULL DEFAULT FALSE;
ALTER TABLE merchant_products ADD COLUMN surplus_expires_at DATETIME(6) NULL;
CREATE INDEX idx_merchant_products_surplus_deal ON merchant_products (is_surplus_deal, surplus_expires_at);
