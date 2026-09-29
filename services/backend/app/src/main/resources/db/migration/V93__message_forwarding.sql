ALTER TABLE messages ADD COLUMN forwarded_from_message_id VARCHAR(64) NULL;
ALTER TABLE messages ADD COLUMN forwarded_from_type VARCHAR(16) NULL;
ALTER TABLE group_messages ADD COLUMN forwarded_from_message_id VARCHAR(64) NULL;
ALTER TABLE group_messages ADD COLUMN forwarded_from_type VARCHAR(16) NULL;
