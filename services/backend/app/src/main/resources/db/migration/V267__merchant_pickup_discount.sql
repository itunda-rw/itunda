-- Real Baemin-style 포장할인 (pickup discount) -- see Merchant.pickupDiscountPercent's own
-- doc comment.
ALTER TABLE merchants ADD COLUMN pickup_discount_percent INT NULL;
ALTER TABLE eats_orders ADD COLUMN pickup_discount DECIMAL(18,2) NOT NULL DEFAULT 0;
