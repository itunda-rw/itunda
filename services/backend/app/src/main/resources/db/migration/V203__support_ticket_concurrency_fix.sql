-- Real bug found live (2026-08-02): SupportService.resolve already read/checked/wrote
-- SupportTicket in one transaction (the correct shape), but with no @Version, two
-- reviewers concurrently resolving the same ticket as REFUNDED could both pass the
-- status check before either committed and both post a real refund -- a real double
-- refund, not just a data-integrity issue. See SupportTicket.kt's own doc comment.

ALTER TABLE support_tickets ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
