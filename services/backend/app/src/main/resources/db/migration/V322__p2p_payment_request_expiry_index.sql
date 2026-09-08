-- Real performance gap found alongside this same cycle's P2P proactive-expiry fix
-- (Bank product-completeness pass, cycle 2, 2026-09-09): the new
-- P2pPaymentRequestExpiryScheduler polls p2p_payment_requests by status+expiresAt every
-- 60s (P2pService.getRequestsDueForExpiryCheck -> findByStatusAndExpiresAtBefore), but
-- the table only ever had an index on (requester_user_id, created_at) -- same class of
-- gap as harvest_advances' own missing (status, repayment_due_date) index fixed in
-- V321, for the identical "new scheduler polls a column combination the table was never
-- indexed for" reason.
CREATE INDEX idx_p2p_payment_requests_status_expires_at ON p2p_payment_requests (status, expires_at);
