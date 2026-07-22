ALTER TABLE messages ADD COLUMN reply_to_message_id VARCHAR(64) NULL;

CREATE INDEX idx_messages_reply_to ON messages(reply_to_message_id);
