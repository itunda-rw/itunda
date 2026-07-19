-- Real automatic dispatch (rw.itunda.eats.EatsOrderService, 2026-07-20) -- exclusive
-- right-of-refusal for one specific rider at a time, closing the "full automatic
-- assignment" gap on top of the existing open-browse/first-claim-wins model. See
-- EatsOrder.kt's own doc comment.

ALTER TABLE eats_orders
    ADD COLUMN offered_rider_id VARCHAR(64) NULL,
    ADD COLUMN offer_expires_at DATETIME(6) NULL,
    ADD COLUMN excluded_rider_user_ids VARCHAR(500) NULL;
