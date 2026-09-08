-- Real admin-accountability gap (Bank/Merchant product-completeness pass, cycle 2,
-- 2026-09-09): MarketplaceEscrowAdminController.resolve had zero record of which admin
-- decided a real money release-vs-refund dispute. The highest-stakes of the 3 gaps
-- found this pass -- resolveDispute moves real money either to the seller or back to
-- the buyer.
ALTER TABLE marketplace_escrows
    ADD COLUMN resolved_by VARCHAR(64) NULL;
