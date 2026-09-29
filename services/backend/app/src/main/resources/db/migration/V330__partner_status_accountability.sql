-- Real admin-accountability gap (2026-09-12): PartnerAdminController's
-- suspend/reactivate had zero record of which admin acted, matching the same real
-- gap already closed for Merchant (V323) and Vehicle Inspection (V325) but missed
-- for Partners.
ALTER TABLE partners
    ADD COLUMN status_changed_by VARCHAR(64) NULL,
    ADD COLUMN status_changed_at DATETIME(6) NULL;
