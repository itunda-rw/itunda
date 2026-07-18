-- Real emoji reactions (rw.itunda.messaging.MessagingService.toggleReaction /
-- rw.itunda.messaging.GroupMessagingService.toggleGroupReaction, 2026-07-19) -- closes
-- the "message reactions" item on the Talk polish roadmap. See MessageReaction.kt's own
-- doc comment.

CREATE TABLE message_reactions (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    message_id  VARCHAR(64) NOT NULL,
    user_id     VARCHAR(64) NOT NULL,
    emoji       VARCHAR(16) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_message_reactions_message_user_emoji UNIQUE (message_id, user_id, emoji)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_message_reactions_message_id ON message_reactions (message_id);

CREATE TABLE group_message_reactions (
    id                VARCHAR(64) NOT NULL PRIMARY KEY,
    group_message_id  VARCHAR(64) NOT NULL,
    user_id           VARCHAR(64) NOT NULL,
    emoji             VARCHAR(16) NOT NULL,
    created_at        DATETIME(6) NOT NULL,
    CONSTRAINT uq_group_message_reactions_message_user_emoji UNIQUE (group_message_id, user_id, emoji)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_message_reactions_message_id ON group_message_reactions (group_message_id);
