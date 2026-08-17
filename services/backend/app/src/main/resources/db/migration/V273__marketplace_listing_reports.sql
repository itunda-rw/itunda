CREATE TABLE marketplace_listing_reports (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    listing_id VARCHAR(64) NOT NULL,
    reporter_id VARCHAR(64) NOT NULL,
    reason VARCHAR(24) NOT NULL,
    details VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uk_marketplace_listing_reports_listing_reporter (listing_id, reporter_id),
    KEY idx_marketplace_listing_reports_listing (listing_id)
);
