ALTER TABLE group_messages ADD COLUMN reply_to_message_id VARCHAR(64) NULL;
CREATE INDEX idx_group_messages_reply_to ON group_messages(reply_to_message_id);
