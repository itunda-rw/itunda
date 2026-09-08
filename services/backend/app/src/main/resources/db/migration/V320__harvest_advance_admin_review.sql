-- Bank product-completeness pass, cycle 2 (2026-09-08): a real overdue-detection +
-- admin default-review queue for Harvest Advances, mirroring V318's own VUP loan
-- review columns exactly. HarvestAdvance already had a real repayment_due_date
-- column with nothing ever checking it -- HarvestAdvanceStatus.OVERDUE existed and
-- was defensively checked in repayAdvance's own guard, but nothing in the codebase
-- ever set it. All three columns default to NULL so every advance already in the
-- table keeps behaving exactly as before -- only an advance an admin actually
-- reviews gets these populated.
ALTER TABLE harvest_advances ADD COLUMN reviewed_by VARCHAR(64) NULL;
ALTER TABLE harvest_advances ADD COLUMN review_note VARCHAR(255) NULL;
ALTER TABLE harvest_advances ADD COLUMN reviewed_at DATETIME(6) NULL;
