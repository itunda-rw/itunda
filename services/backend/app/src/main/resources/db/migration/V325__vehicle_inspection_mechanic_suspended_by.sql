-- Real admin-accountability gap (Bank/Merchant product-completeness pass, cycle 2,
-- 2026-09-09): VehicleInspectionMechanicModerationAdminController.suspend/reactivate
-- had zero record of which admin acted, same class of gap as merchants' own
-- status_changed_by (V323).
ALTER TABLE vehicle_inspection_mechanics
    ADD COLUMN suspended_by VARCHAR(64) NULL,
    ADD COLUMN suspended_at DATETIME(6) NULL;
