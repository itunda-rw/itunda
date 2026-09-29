-- Real Coupang-style pre-purchase product Q&A ("상품문의")
-- (rw.itunda.commerce.ProductInquiryService, 2026-07-26). See ProductInquiry.kt's own
-- doc comment for the full account, including why this needs no real order/purchase at
-- all, unlike product_reviews.

CREATE TABLE product_inquiries (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    product_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    buyer_id VARCHAR(64) NOT NULL,
    question VARCHAR(500) NOT NULL,
    answer VARCHAR(1000) NULL,
    answered_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_product_inquiries_product (product_id),
    INDEX idx_product_inquiries_buyer (buyer_id)
);
