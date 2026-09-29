-- Real Baemin/Coupang-style "helpful" vote on a delivered-order review (2026-08-17) --
-- every real Korean delivery/e-commerce review UI has a "도움돼요"/"도움이 됐어요"
-- button under each review letting OTHER buyers mark it as helpful, distinct from
-- Section 98/107's product/listing VIEW counts (a passive read signal) and distinct
-- from CommunityLike/ListingLike (different domain entities). Mirrors listing_likes
-- (V223__listing_likes.sql) column-for-column -- same real (row, user) unique-vote
-- shape, same denormalized counter-on-parent convention.

ALTER TABLE eats_reviews ADD COLUMN helpful_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE eats_review_helpful_votes (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    review_id  VARCHAR(64) NOT NULL,
    user_id    VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_eats_review_helpful_votes_review_user ON eats_review_helpful_votes (review_id, user_id);
