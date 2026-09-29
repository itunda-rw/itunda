ALTER TABLE eats_reviews ADD COLUMN hidden BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE eats_review_reports (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    review_id VARCHAR(64) NOT NULL,
    reporter_id VARCHAR(64) NOT NULL,
    reason VARCHAR(24) NOT NULL,
    details VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_eats_review_reports_review_reporter (review_id, reporter_id),
    KEY idx_eats_review_reports_review (review_id)
);
