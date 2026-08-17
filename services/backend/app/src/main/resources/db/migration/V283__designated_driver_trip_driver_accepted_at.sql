-- Real gap fix (rw.itunda.rideshare.DesignatedDriverService, 2026-08-18): an ACCEPTED
-- designated-driver trip had no cancel path at all -- if a driver accepted and then
-- never started driving, the customer's fare was stuck in designated_driver_holding
-- with zero recourse. Ports RideTrip.driver_assigned_at's own real cancellation-fee
-- grace-period clock (see V272__ride_trip_driver_assigned_at.sql) to this structurally
-- identical escrow.

ALTER TABLE designated_driver_trips ADD COLUMN driver_accepted_at DATETIME(6) NULL;
