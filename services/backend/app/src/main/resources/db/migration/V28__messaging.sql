-- Real 1:1 messaging (rw.itunda.messaging.MessagingService, 2026-07-17/18) -- the
-- foundational Kakao-style chat primitive named in the "super app" goal expansion. See
-- core/.../domain/Conversation.kt and Message.kt for the entities this transcribes
-- column-for-column.

CREATE TABLE conversations (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    participant_a_id   VARCHAR(64)  NOT NULL,
    participant_b_id   VARCHAR(64)  NOT NULL,
    last_message_at    DATETIME(6)  NOT NULL,
    created_at         DATETIME(6)  NOT NULL,
    -- Enforces "at most one conversation between any two users" at the DB level --
    -- participant ids are always stored canonically sorted (see
    -- MessagingService.canonicalPair), so this single unique pair covers both
    -- orderings without needing a second reversed-pair constraint.
    CONSTRAINT uq_conversations_participants UNIQUE (participant_a_id, participant_b_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_conversations_participant_a ON conversations (participant_a_id);
CREATE INDEX idx_conversations_participant_b ON conversations (participant_b_id);

CREATE TABLE messages (
    id               VARCHAR(64)   NOT NULL PRIMARY KEY,
    conversation_id  VARCHAR(64)   NOT NULL,
    sender_id        VARCHAR(64)   NOT NULL,
    body             VARCHAR(2000) NOT NULL,
    sent_at          DATETIME(6)   NOT NULL,
    read_at          DATETIME(6)   NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_messages_conversation_id ON messages (conversation_id);
