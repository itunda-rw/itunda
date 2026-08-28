CREATE TABLE merchant_profile_views (
    id VARCHAR(96) NOT NULL PRIMARY KEY,
    merchant_id VARCHAR(64) NOT NULL,
    view_date DATE NOT NULL,
    view_count BIGINT NOT NULL DEFAULT 1,
    UNIQUE KEY uk_merchant_profile_views_merchant_date (merchant_id, view_date)
);

CREATE INDEX idx_merchant_profile_views_merchant_id ON merchant_profile_views (merchant_id);
