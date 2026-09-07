-- Real admin moderation lever for vehicle-inspection mechanics (Vehicle
-- product-completeness pass, 2026-09-07) -- see
-- VehicleInspectionMechanic.kt's own doc comment: mechanics are real
-- money-receiving business actors (identical shape to Merchant) but had
-- no admin suspend/reactivate surface at all before this, unlike
-- MerchantStatus.SUSPENDED. Deliberately a separate column from the
-- existing `available` (a self-toggled "am I taking new bookings right
-- now" flag) rather than reusing it -- an admin-suspended mechanic must
-- not be able to un-suspend themselves by flipping their own
-- availability.

ALTER TABLE vehicle_inspection_mechanics
    ADD COLUMN suspended TINYINT(1) NOT NULL DEFAULT 0;
