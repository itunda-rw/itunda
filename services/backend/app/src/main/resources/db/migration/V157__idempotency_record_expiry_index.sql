-- The scheduled retention sweep deletes by created_at; keep that operational work
-- index-backed instead of scanning the full replay history.
CREATE INDEX idx_idempotency_records_created_at ON idempotency_records (created_at);
