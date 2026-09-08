-- Real admin-accountability gap (Bank/Merchant product-completeness pass, cycle 2,
-- 2026-09-09): MerchantModerationAdminController.suspend/reactivate/revoke-fee-waiver
-- had zero record of which admin acted, unlike the identical-shape decide()-style
-- admin actions elsewhere (VupLoanService.decide etc.) that already persist
-- reviewedBy/reviewedAt.
ALTER TABLE merchants
    ADD COLUMN status_changed_by      VARCHAR(64) NULL,
    ADD COLUMN status_changed_at      DATETIME(6) NULL,
    ADD COLUMN fee_waiver_revoked_by  VARCHAR(64) NULL,
    ADD COLUMN fee_waiver_revoked_at  DATETIME(6) NULL;
