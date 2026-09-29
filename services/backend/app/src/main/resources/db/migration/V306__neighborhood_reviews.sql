CREATE TABLE neighborhood_reviews (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    neighborhood VARCHAR(120) NOT NULL,
    residency_years INT NULL,
    body VARCHAR(1000) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_neighborhood_reviews_user_neighborhood UNIQUE (user_id, neighborhood),
    INDEX idx_neighborhood_reviews_neighborhood (neighborhood)
);
