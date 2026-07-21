CREATE TABLE hood_reports (
    id VARCHAR(64) PRIMARY KEY,
    reporter_user_id VARCHAR(64) NOT NULL,
    target_type VARCHAR(32) NOT NULL,
    target_id VARCHAR(64) NOT NULL,
    reason VARCHAR(180) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    reviewed_by VARCHAR(64) NULL,
    reviewed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_hood_reports_queue (status, created_at),
    INDEX idx_hood_reports_target (target_type, target_id)
);
