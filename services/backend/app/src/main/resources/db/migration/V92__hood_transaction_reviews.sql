ALTER TABLE listings ADD COLUMN buyer_id VARCHAR(64) NULL;
ALTER TABLE job_posts ADD COLUMN worker_id VARCHAR(64) NULL;
ALTER TABLE property_listings ADD COLUMN counterparty_id VARCHAR(64) NULL;

CREATE TABLE hood_transaction_reviews (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    transaction_type VARCHAR(32) NOT NULL,
    transaction_id VARCHAR(64) NOT NULL,
    reviewer_id VARCHAR(64) NOT NULL,
    reviewee_id VARCHAR(64) NOT NULL,
    good_points VARCHAR(500) NOT NULL,
    uncomfortable_points VARCHAR(500) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uk_hood_review_transaction_reviewer (transaction_type, transaction_id, reviewer_id),
    INDEX idx_hood_review_transaction (transaction_type, transaction_id),
    INDEX idx_hood_review_reviewee (reviewee_id)
);
