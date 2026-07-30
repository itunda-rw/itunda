-- `record_key` combines a server-owned route scope with a caller-provided idempotency
-- key. Keep room for the documented 300-character caller key plus that safe scope.
ALTER TABLE idempotency_records MODIFY COLUMN record_key VARCHAR(512) NOT NULL;
