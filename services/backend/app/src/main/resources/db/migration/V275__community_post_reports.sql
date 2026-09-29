CREATE TABLE community_post_reports (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    post_id VARCHAR(64) NOT NULL,
    reporter_id VARCHAR(64) NOT NULL,
    reason VARCHAR(24) NOT NULL,
    details VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_community_post_reports_post_reporter (post_id, reporter_id),
    KEY idx_community_post_reports_post (post_id)
);
