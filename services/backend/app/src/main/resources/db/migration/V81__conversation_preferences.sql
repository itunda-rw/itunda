CREATE TABLE conversation_preferences (
    id VARCHAR(64) PRIMARY KEY,
    conversation_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    quiet BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_conversation_preference_user_room UNIQUE (conversation_id, user_id),
    CONSTRAINT fk_conversation_preference_conversation FOREIGN KEY (conversation_id) REFERENCES conversations(id)
);

CREATE INDEX idx_conversation_preferences_user ON conversation_preferences(user_id, quiet);
