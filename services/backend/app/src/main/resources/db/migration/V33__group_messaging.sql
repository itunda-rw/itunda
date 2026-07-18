-- Real group chat (rw.itunda.messaging.GroupMessagingService, 2026-07-18) -- the single
-- most defining KakaoTalk capability the original 1:1-only conversations/messages pair
-- didn't cover. Brand-new, additive tables -- conversations/messages (1:1) are
-- completely untouched. See GroupConversation.kt's own doc comment.

CREATE TABLE group_conversations (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    name              VARCHAR(100) NOT NULL,
    created_by        VARCHAR(64)  NOT NULL,
    last_message_at   DATETIME(6)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE group_conversation_members (
    id                       VARCHAR(64) NOT NULL PRIMARY KEY,
    group_conversation_id    VARCHAR(64) NOT NULL,
    user_id                  VARCHAR(64) NOT NULL,
    joined_at                DATETIME(6) NOT NULL,
    last_read_at             DATETIME(6) NULL,
    CONSTRAINT uq_group_members_group_user UNIQUE (group_conversation_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_members_user_id ON group_conversation_members (user_id);

CREATE TABLE group_messages (
    id                       VARCHAR(64)   NOT NULL PRIMARY KEY,
    group_conversation_id    VARCHAR(64)   NOT NULL,
    sender_id                VARCHAR(64)   NOT NULL,
    body                     VARCHAR(2000) NOT NULL,
    sent_at                  DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_messages_group_id ON group_messages (group_conversation_id);
