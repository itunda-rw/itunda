ALTER TABLE messages ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE messages ADD COLUMN deleted_by_user_id VARCHAR(64) NULL;
CREATE INDEX idx_messages_deleted_at ON messages(deleted_at);
