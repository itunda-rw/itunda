-- Real 배달의민족 예약주문 (scheduled ordering), per-restaurant opt-in
-- (rw.itunda.eats.EatsOrderService.placeOrder, 2026-07-26). See Merchant.kt's own doc
-- comment for the full account.

ALTER TABLE merchants ADD COLUMN accepts_scheduled_orders TINYINT(1) NOT NULL DEFAULT 0;
ALTER TABLE eats_orders ADD COLUMN scheduled_for DATETIME(6) NULL;
