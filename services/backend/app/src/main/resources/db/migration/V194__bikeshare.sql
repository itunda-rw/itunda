-- Real Kakao T 바이크 (Kakao T Bike, rw.itunda.rideshare.BikeRentalService) -- see
-- Bike.kt / BikeRentalSession.kt's own doc comments.

CREATE TABLE bikes (
    id                VARCHAR(64)  NOT NULL PRIMARY KEY,
    owner_user_id     VARCHAR(64)  NOT NULL,
    wallet_id         VARCHAR(64)  NOT NULL,
    type              VARCHAR(16)  NOT NULL,
    current_latitude  DOUBLE       NOT NULL,
    current_longitude DOUBLE       NOT NULL,
    available         TINYINT(1)   NOT NULL DEFAULT 1,
    created_at        DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_bikes_owner_user_id ON bikes (owner_user_id);

CREATE TABLE bike_rental_sessions (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    bike_id               VARCHAR(64)   NOT NULL,
    rider_user_id         VARCHAR(64)   NOT NULL,
    started_at            DATETIME(6)   NOT NULL,
    ended_at              DATETIME(6)   NULL,
    start_latitude        DOUBLE        NOT NULL,
    start_longitude       DOUBLE        NOT NULL,
    end_latitude          DOUBLE        NULL,
    end_longitude         DOUBLE        NULL,
    duration_minutes      INT           NULL,
    total_fare            DECIMAL(18,2) NULL,
    platform_fee          DECIMAL(18,2) NULL,
    payout_transaction_id VARCHAR(64)   NULL,
    status                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version               BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_bike_rental_sessions_bike_id ON bike_rental_sessions (bike_id);
CREATE INDEX idx_bike_rental_sessions_rider_user_id ON bike_rental_sessions (rider_user_id);
