-- Real local-business "booking" (rw.itunda.merchant.MerchantBookingService, 2026-07-25) --
-- closes the "business profile + real appointment booking" gap independently converged
-- on by Naver Smart Place, Kakao Hair Shop, and Karrot's Business Profile research
-- (docs/DESIGN_REFERENCES.md). Reuses the existing merchant_products table as the real
-- service catalog (a bookable service IS a MerchantProduct with a duration_minutes set).

ALTER TABLE merchant_products ADD COLUMN duration_minutes INT NULL;

CREATE TABLE merchant_availability_windows (
    id            VARCHAR(64) NOT NULL PRIMARY KEY,
    merchant_id   VARCHAR(64) NOT NULL,
    day_of_week   VARCHAR(16) NOT NULL,
    start_time    TIME        NOT NULL,
    end_time      TIME        NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_merchant_availability_windows_merchant_id ON merchant_availability_windows (merchant_id);

CREATE TABLE merchant_bookings (
    id             VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id    VARCHAR(64)    NOT NULL,
    customer_id    VARCHAR(64)    NOT NULL,
    service_id     VARCHAR(64)    NOT NULL,
    service_name   VARCHAR(255)   NOT NULL,
    booking_date   DATE           NOT NULL,
    start_time     TIME           NOT NULL,
    end_time       TIME           NOT NULL,
    status         VARCHAR(16)    NOT NULL DEFAULT 'REQUESTED',
    notes          VARCHAR(500),
    created_at     DATETIME(6)    NOT NULL,
    updated_at     DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_merchant_bookings_customer_id ON merchant_bookings (customer_id);
CREATE INDEX idx_merchant_bookings_merchant_id_date ON merchant_bookings (merchant_id, booking_date);
