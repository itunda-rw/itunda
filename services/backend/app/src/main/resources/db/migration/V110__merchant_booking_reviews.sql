-- Real post-appointment reviews + owner-side reply (rw.itunda.merchant.
-- MerchantBookingReviewService, 2026-07-25) -- closes the "owner-side review replies"
-- half of Naver Smart Place's real, sourced feature set (docs/DESIGN_REFERENCES.md).

CREATE TABLE merchant_booking_reviews (
    id               VARCHAR(64)    NOT NULL PRIMARY KEY,
    booking_id       VARCHAR(64)    NOT NULL,
    merchant_id      VARCHAR(64)    NOT NULL,
    customer_id      VARCHAR(64)    NOT NULL,
    service_name     VARCHAR(255)   NOT NULL,
    rating           INT            NOT NULL,
    comment          VARCHAR(1000),
    owner_reply      VARCHAR(1000),
    owner_replied_at DATETIME(6),
    created_at       DATETIME(6)    NOT NULL,
    CONSTRAINT uq_merchant_booking_reviews_booking_id UNIQUE (booking_id),
    INDEX idx_merchant_booking_reviews_merchant_id (merchant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
