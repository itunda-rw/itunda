-- Real Kakao T 주차 (Kakao T Parking, rw.itunda.rideshare.ParkingService) -- see
-- ParkingSpot.kt / ParkingSession.kt's own doc comments.

CREATE TABLE parking_spots (
    id            VARCHAR(64)   NOT NULL PRIMARY KEY,
    owner_user_id VARCHAR(64)   NOT NULL,
    wallet_id     VARCHAR(64)   NOT NULL,
    address       VARCHAR(500)  NOT NULL,
    latitude      DOUBLE        NOT NULL,
    longitude     DOUBLE        NOT NULL,
    hourly_rate   DECIMAL(18,2) NOT NULL,
    available     TINYINT(1)    NOT NULL DEFAULT 1,
    created_at    DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_parking_spots_owner_user_id ON parking_spots (owner_user_id);

CREATE TABLE parking_sessions (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    spot_id               VARCHAR(64)   NOT NULL,
    renter_user_id        VARCHAR(64)   NOT NULL,
    started_at            DATETIME(6)   NOT NULL,
    ended_at              DATETIME(6)   NULL,
    duration_minutes      INT           NULL,
    total_fare            DECIMAL(18,2) NULL,
    platform_fee          DECIMAL(18,2) NULL,
    payout_transaction_id VARCHAR(64)   NULL,
    status                VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    version               BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_parking_sessions_spot_id ON parking_sessions (spot_id);
CREATE INDEX idx_parking_sessions_renter_user_id ON parking_sessions (renter_user_id);
