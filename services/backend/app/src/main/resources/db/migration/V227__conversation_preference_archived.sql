ALTER TABLE conversation_preferences ADD COLUMN archived BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_conversation_preferences_user_archived ON conversation_preferences(user_id, archived);
