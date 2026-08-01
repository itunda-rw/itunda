-- Real Kakao T 시외버스 (intercity bus booking, rw.itunda.rideshare.BusService) --
-- see BusTrip.kt / BusBooking.kt's own doc comments.

CREATE TABLE bus_trips (
    id                VARCHAR(64)   NOT NULL PRIMARY KEY,
    operator_user_id  VARCHAR(64)   NOT NULL,
    wallet_id         VARCHAR(64)   NOT NULL,
    origin            VARCHAR(200)  NOT NULL,
    destination       VARCHAR(200)  NOT NULL,
    departure_time    DATETIME(6)   NOT NULL,
    total_seats       INT           NOT NULL,
    available_seats   INT           NOT NULL,
    fare_per_seat     DECIMAL(18,2) NOT NULL,
    created_at        DATETIME(6)   NOT NULL,
    version           BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_bus_trips_operator_user_id ON bus_trips (operator_user_id);
CREATE INDEX idx_bus_trips_departure_time ON bus_trips (departure_time);

CREATE TABLE bus_bookings (
    id                     VARCHAR(64)   NOT NULL PRIMARY KEY,
    trip_id                VARCHAR(64)   NOT NULL,
    rider_user_id          VARCHAR(64)   NOT NULL,
    seat_count             INT           NOT NULL,
    total_fare             DECIMAL(18,2) NOT NULL,
    platform_fee           DECIMAL(18,2) NOT NULL,
    payment_transaction_id VARCHAR(64)   NOT NULL,
    status                 VARCHAR(16)   NOT NULL DEFAULT 'BOOKED',
    refund_transaction_id  VARCHAR(64)   NULL,
    created_at             DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_bus_bookings_trip_id ON bus_bookings (trip_id);
CREATE INDEX idx_bus_bookings_rider_user_id ON bus_bookings (rider_user_id);
