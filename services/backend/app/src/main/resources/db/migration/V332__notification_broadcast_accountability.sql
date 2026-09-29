-- Real admin-accountability gap (2026-09-13): NotificationAdminController's
-- broadcast (the single highest-blast-radius admin action -- fans out to every
-- real user's inbox in one call) had zero record of which admin sent it, not
-- even captured at the controller layer. Same real gap already closed for
-- Partner (V330), Merchant (V323), and Vehicle Inspection (V325).
ALTER TABLE notifications
    ADD COLUMN sent_by_user_id VARCHAR(64) NULL;
