-- Real Kakao Hair Shop-style 100%-prepay-to-book (rw.itunda.merchant.
-- MerchantBookingService, 2026-07-25) -- see BookingDeposit.kt's own doc comment. Source:
-- docs/DESIGN_REFERENCES.md's Naver Smart Place/Kakao Hair Shop/Karrot Business Profile
-- research row ("a 100%-prepay-to-book mechanic that cut no-shows from ~20% to under 0.5%").

ALTER TABLE merchant_products
    ADD COLUMN requires_prepay BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE merchant_booking_deposits (
    id                       VARCHAR(64)    NOT NULL PRIMARY KEY,
    booking_id               VARCHAR(64)    NOT NULL,
    merchant_id              VARCHAR(64)    NOT NULL,
    customer_id              VARCHAR(64)    NOT NULL,
    amount                   DECIMAL(18, 2) NOT NULL,
    fee                      DECIMAL(18, 2) NOT NULL,
    hold_transaction_id      VARCHAR(64)    NOT NULL,
    status                   VARCHAR(16)    NOT NULL DEFAULT 'HELD',
    resolution_transaction_id VARCHAR(64),
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    CONSTRAINT uq_merchant_booking_deposits_booking_id UNIQUE (booking_id),
    INDEX idx_merchant_booking_deposits_merchant_id (merchant_id),
    INDEX idx_merchant_booking_deposits_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
