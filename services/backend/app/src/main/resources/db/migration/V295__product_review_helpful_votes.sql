-- Real Coupang/Naver Shopping-style "helpful" vote on a product review (2026-08-25) --
-- mirrors eats_review_helpful_votes (V264__eats_review_helpful_votes.sql) column-for-
-- column, same real (row, user) unique-vote shape, same denormalized counter-on-parent
-- convention. Closes the direct Toss Shopping reference screenshot gap: "OO명에게 도움
-- 됐어요" + thumbs-up under each product review.

ALTER TABLE product_reviews ADD COLUMN helpful_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE product_review_helpful_votes (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    review_id  VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_product_review_helpful_votes_review_user ON product_review_helpful_votes (review_id, user_id);
