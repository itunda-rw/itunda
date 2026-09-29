-- Real Baemin-style self-pickup order type (rw.itunda.eats.EatsOrderService,
-- 2026-07-26) -- a buyer can choose to collect their own order instead of using a
-- rider, per Baemin's own real "포장주문" (Pickup) feature: zero delivery fee, zero
-- rider dispatch. Defaults every existing row to DELIVERY, its exact current
-- unchanged behavior.

ALTER TABLE eats_orders
    ADD COLUMN fulfillment_type VARCHAR(16) NOT NULL DEFAULT 'DELIVERY';
