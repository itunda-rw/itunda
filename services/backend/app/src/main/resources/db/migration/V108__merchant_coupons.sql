-- Real merchant coupons + 단골 (regular customer) loyalty gating (rw.itunda.merchant.
-- MerchantCouponService, 2026-07-25) -- closes the "coupons/loyalty on top" half of the
-- Naver Smart Place/Kakao Hair Shop/Karrot Business Profile convergent research
-- (docs/DESIGN_REFERENCES.md); the booking half already shipped as merchant_bookings.

CREATE TABLE merchant_coupons (
    id              VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id     VARCHAR(64)    NOT NULL,
    title           VARCHAR(100)   NOT NULL,
    description     VARCHAR(500),
    discount_type   VARCHAR(16)    NOT NULL,
    discount_value  DECIMAL(19, 2) NOT NULL,
    regulars_only   BOOLEAN        NOT NULL DEFAULT FALSE,
    active          BOOLEAN        NOT NULL DEFAULT TRUE,
    expires_at      DATETIME(6),
    created_at      DATETIME(6)    NOT NULL,
    INDEX idx_merchant_coupons_merchant_id (merchant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE merchant_coupon_redemptions (
    id               VARCHAR(64)    NOT NULL PRIMARY KEY,
    coupon_id        VARCHAR(64)    NOT NULL,
    merchant_id      VARCHAR(64)    NOT NULL,
    customer_id      VARCHAR(64)    NOT NULL,
    transaction_id   VARCHAR(64)    NOT NULL,
    discount_amount  DECIMAL(19, 2) NOT NULL,
    redeemed_at      DATETIME(6)    NOT NULL,
    CONSTRAINT uq_merchant_coupon_redemptions_coupon_customer UNIQUE (coupon_id, customer_id),
    INDEX idx_merchant_coupon_redemptions_customer_id (customer_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
