ALTER TABLE group_messages ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE group_messages ADD COLUMN deleted_by_user_id VARCHAR(64) NULL;
CREATE INDEX idx_group_messages_deleted_at ON group_messages(deleted_at);
