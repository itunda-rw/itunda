-- Real free-text delivery instructions (rw.itunda.eats.EatsOrderService.placeOrder,
-- 2026-07-19) -- e.g. "Leave at the gate", "Call on arrival". See EatsOrder.kt's own
-- doc comment.

ALTER TABLE eats_orders ADD COLUMN delivery_notes VARCHAR(500) NULL;
