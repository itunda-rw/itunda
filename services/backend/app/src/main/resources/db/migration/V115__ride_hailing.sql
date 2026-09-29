-- Real Kakao T-style ride-hailing (rw.itunda.rideshare, 2026-07-26) -- see
-- RideTripService's own doc comment (kakaomobility.com/contents/taxi-dispatch).

CREATE TABLE ride_drivers (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64)  NOT NULL,
    wallet_id           VARCHAR(64)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    available           BOOLEAN      NOT NULL DEFAULT FALSE,
    current_latitude    DOUBLE,
    current_longitude   DOUBLE,
    location_updated_at DATETIME(6),
    total_offers        INT          NOT NULL DEFAULT 0,
    total_accepted      INT          NOT NULL DEFAULT 0,
    created_at          DATETIME(6)  NOT NULL,
    CONSTRAINT uq_ride_drivers_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ride_trips (
    id                       VARCHAR(64)    NOT NULL PRIMARY KEY,
    passenger_id             VARCHAR(64)    NOT NULL,
    driver_id                VARCHAR(64),
    pickup_address           VARCHAR(500)   NOT NULL,
    pickup_latitude          DOUBLE         NOT NULL,
    pickup_longitude         DOUBLE         NOT NULL,
    dropoff_address          VARCHAR(500)   NOT NULL,
    dropoff_latitude         DOUBLE         NOT NULL,
    dropoff_longitude        DOUBLE         NOT NULL,
    distance_km              DECIMAL(10, 3) NOT NULL,
    fare                     DECIMAL(18, 2) NOT NULL,
    platform_fee             DECIMAL(18, 2) NOT NULL,
    transaction_id           VARCHAR(64)    NOT NULL,
    payout_transaction_id    VARCHAR(64),
    refund_transaction_id    VARCHAR(64),
    status                   VARCHAR(16)    NOT NULL DEFAULT 'REQUESTED',
    offered_driver_id        VARCHAR(64),
    offer_expires_at         DATETIME(6),
    excluded_driver_user_ids VARCHAR(2000),
    created_at               DATETIME(6)    NOT NULL,
    updated_at               DATETIME(6)    NOT NULL,
    INDEX idx_ride_trips_passenger_id (passenger_id),
    INDEX idx_ride_trips_driver_id (driver_id),
    INDEX idx_ride_trips_offer_expires_at (offer_expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
