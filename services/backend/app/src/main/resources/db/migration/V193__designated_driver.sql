-- Real Kakao T 대리운전 (designated driver, rw.itunda.rideshare.DesignatedDriverService)
-- -- see DesignatedDriver.kt / DesignatedDriverTrip.kt's own doc comments.

CREATE TABLE designated_drivers (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id          VARCHAR(64)  NOT NULL,
    wallet_id        VARCHAR(64)  NOT NULL,
    license_number   VARCHAR(100) NOT NULL,
    available        TINYINT(1)   NOT NULL DEFAULT 0,
    current_latitude DOUBLE       NULL,
    current_longitude DOUBLE      NULL,
    created_at       DATETIME(6)  NOT NULL,
    CONSTRAINT uq_designated_drivers_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE designated_driver_trips (
    id                        VARCHAR(64)   NOT NULL PRIMARY KEY,
    customer_id               VARCHAR(64)   NOT NULL,
    driver_id                 VARCHAR(64)   NULL,
    pickup_address             VARCHAR(500)  NOT NULL,
    pickup_latitude            DOUBLE        NOT NULL,
    pickup_longitude           DOUBLE        NOT NULL,
    dropoff_address            VARCHAR(500)  NOT NULL,
    dropoff_latitude           DOUBLE        NOT NULL,
    dropoff_longitude          DOUBLE        NOT NULL,
    vehicle_make               VARCHAR(100)  NOT NULL,
    vehicle_model               VARCHAR(100)  NOT NULL,
    vehicle_plate               VARCHAR(20)   NOT NULL,
    distance_km                 DECIMAL(10,3) NOT NULL,
    fare                        DECIMAL(18,2) NOT NULL,
    platform_fee                DECIMAL(18,2) NOT NULL,
    hold_transaction_id         VARCHAR(64)   NOT NULL,
    status                      VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    payout_transaction_id       VARCHAR(64)   NULL,
    refund_transaction_id       VARCHAR(64)   NULL,
    created_at                  DATETIME(6)   NOT NULL,
    updated_at                  DATETIME(6)   NOT NULL,
    version                     BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_designated_driver_trips_customer_id ON designated_driver_trips (customer_id);
CREATE INDEX idx_designated_driver_trips_driver_id ON designated_driver_trips (driver_id);
