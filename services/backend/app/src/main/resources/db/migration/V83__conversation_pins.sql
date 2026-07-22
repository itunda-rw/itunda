ALTER TABLE conversations ADD COLUMN pinned_message_id VARCHAR(64) NULL;

CREATE INDEX idx_conversations_pinned_message ON conversations(pinned_message_id);
