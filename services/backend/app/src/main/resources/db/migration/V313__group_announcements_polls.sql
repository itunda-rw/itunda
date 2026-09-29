CREATE TABLE group_announcements (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    group_conversation_id VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_group_announcements_group_id_created_at ON group_announcements (group_conversation_id, created_at);

CREATE TABLE group_polls (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    group_conversation_id VARCHAR(64) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    question VARCHAR(500) NOT NULL,
    allow_multiple BOOLEAN NOT NULL DEFAULT FALSE,
    closes_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_group_polls_group_id_created_at ON group_polls (group_conversation_id, created_at);

CREATE TABLE group_poll_options (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    poll_id VARCHAR(64) NOT NULL,
    text VARCHAR(200) NOT NULL
);

CREATE INDEX idx_group_poll_options_poll_id ON group_poll_options (poll_id);

CREATE TABLE group_poll_votes (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    poll_id VARCHAR(64) NOT NULL,
    option_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    voted_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_group_poll_vote_poll_option_user (poll_id, option_id, user_id)
);

CREATE INDEX idx_group_poll_votes_poll_id ON group_poll_votes (poll_id);
