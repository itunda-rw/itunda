-- Real bug found live (2026-08-02): BikeRentalService.startRental /
-- ParkingService.startSession only ever checked for the ABSENCE of an active
-- rental/session row before inserting a new one -- a check-then-act race with no
-- shared, lockable row to catch two concurrent riders/renters claiming the same
-- bike/spot. See Bike.kt / ParkingSpot.kt's own doc comments for the full account.
-- Adds optimistic-locking `version` columns, matching RideTrip/BusTrip/
-- DesignatedDriverTrip's own already-proven-safe pattern.

ALTER TABLE bikes ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE parking_spots ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
