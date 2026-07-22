CREATE TABLE user_blocks (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    blocker_user_id VARCHAR(64) NOT NULL,
    blocked_user_id VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_user_blocks_pair UNIQUE (blocker_user_id, blocked_user_id),
    INDEX idx_user_blocks_blocker (blocker_user_id),
    INDEX idx_user_blocks_blocked (blocked_user_id)
);
