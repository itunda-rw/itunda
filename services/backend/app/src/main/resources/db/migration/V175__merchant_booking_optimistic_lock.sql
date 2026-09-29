-- Booking state and its held deposit are advanced by independent customer, merchant,
-- and scheduler paths; protect both from conflicting terminal transitions.
ALTER TABLE merchant_bookings ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE merchant_booking_deposits ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
