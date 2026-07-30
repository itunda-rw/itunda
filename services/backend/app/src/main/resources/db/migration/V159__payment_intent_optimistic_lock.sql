-- Collection, cancellation, and checkout-expiry polling can all transition the same
-- intent. Hibernate uses this version for compare-and-swap updates.
ALTER TABLE payment_intents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
