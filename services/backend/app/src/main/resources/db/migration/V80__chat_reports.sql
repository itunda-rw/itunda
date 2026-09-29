CREATE TABLE chat_reports (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    reporter_user_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    reason VARCHAR(180) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reviewed_by VARCHAR(64) NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_chat_reports_open UNIQUE (reporter_user_id, message_id, status),
    INDEX idx_chat_reports_queue (status, created_at)
);
