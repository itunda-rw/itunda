-- Real 배달의민족/Naver Smart Place-style restaurant-owner reply to a review
-- (rw.itunda.eats.EatsReviewService.replyToRestaurantReview, 2026-07-26). See
-- EatsReview.kt's own doc comment for the full account of the gap this closes.

ALTER TABLE eats_reviews ADD COLUMN owner_reply VARCHAR(1000) NULL;
ALTER TABLE eats_reviews ADD COLUMN owner_replied_at DATETIME(6) NULL;
