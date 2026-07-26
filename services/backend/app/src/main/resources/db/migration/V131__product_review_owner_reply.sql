-- Real Coupang/Naver Smart Store-style merchant reply to a product review
-- (rw.itunda.commerce.ProductReviewService.replyToProductReview, 2026-07-26). See
-- ProductReview.kt's own doc comment for the full account.

ALTER TABLE product_reviews ADD COLUMN owner_reply VARCHAR(1000) NULL;
ALTER TABLE product_reviews ADD COLUMN owner_replied_at DATETIME(6) NULL;
