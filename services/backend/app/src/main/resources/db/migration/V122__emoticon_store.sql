-- Real KakaoTalk Emoticon Store (rw.itunda.messaging.EmoticonService, 2026-07-26) --
-- purchasable artist sticker packs sent as real chat message content, distinct from
-- the existing ephemeral emoji-reaction toggle (MessageReaction/GroupMessageReaction).

CREATE TABLE emoticon_packs (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    title          VARCHAR(120) NOT NULL,
    artist_name    VARCHAR(120) NOT NULL,
    thumbnail_url  VARCHAR(255) NOT NULL,
    price          DECIMAL(18, 2) NOT NULL,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE emoticons (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    pack_id     VARCHAR(64)  NOT NULL,
    image_url   VARCHAR(255) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_emoticons_pack_id ON emoticons (pack_id);

CREATE TABLE user_emoticon_packs (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    pack_id      VARCHAR(64) NOT NULL,
    source       VARCHAR(16) NOT NULL,
    acquired_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_user_emoticon_packs_user_pack UNIQUE (user_id, pack_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_user_emoticon_packs_user_id ON user_emoticon_packs (user_id);

ALTER TABLE messages ADD COLUMN emoticon_id VARCHAR(64) NULL;
ALTER TABLE group_messages ADD COLUMN emoticon_id VARCHAR(64) NULL;
