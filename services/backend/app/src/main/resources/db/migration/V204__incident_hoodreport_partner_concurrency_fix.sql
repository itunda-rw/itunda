-- Real bug found live (2026-08-02): Incident/HoodReport/PartnerMiniApp all already
-- read/check/write their own entity in one transaction (the correct shape), but with
-- no @Version, two concurrent reviewers could both pass the status check before either
-- committed and silently overwrite each other's decision -- the same audit-trail-
-- corruption class SupportTicket's own V203 fix addressed, found sweeping this codebase
-- for the identical "single claimable resource, check-then-act" pattern. See
-- Incident.kt / HoodReport.kt / Partner.kt (PartnerMiniApp)'s own doc comments.

ALTER TABLE incidents ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE hood_reports ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE partner_mini_apps ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
