-- Real gap found live (2026-08-31, market-readiness audit): RideDriverService.register
-- had zero identity/license info at all, unlike the smaller, adjacent
-- DesignatedDriverService.register (rw.itunda.rideshare), which already requires a real
-- licenseNumber. See RideDriver.kt's own doc comment for the full account -- an honest,
-- self-declared informational text field, not a real license-verification gate this
-- backend has no path to check, same discipline designated_drivers.license_number
-- already established. Default '' backfills any existing row (this app has real
-- already-registered drivers with no license on file); the real registration form now
-- requires a non-blank value going forward.
ALTER TABLE ride_drivers ADD COLUMN license_number VARCHAR(100) NOT NULL DEFAULT '';
