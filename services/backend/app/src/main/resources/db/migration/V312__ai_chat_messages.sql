CREATE TABLE ai_chat_messages (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    role VARCHAR(16) NOT NULL,
    content VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_ai_chat_messages_user_id_created_at ON ai_chat_messages (user_id, created_at);
