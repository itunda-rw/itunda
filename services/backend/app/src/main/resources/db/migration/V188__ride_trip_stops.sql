-- Real Kakao T-style multi-stop rides (rw.itunda.rideshare, item 214) -- up to 3 real
-- extra waypoints between pickup and dropoff, visited in order. See RideTripStop.kt's
-- own doc comment.

CREATE TABLE ride_trip_stops (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    trip_id     VARCHAR(64)  NOT NULL,
    sequence    INT          NOT NULL,
    address     VARCHAR(500) NOT NULL,
    latitude    DOUBLE       NOT NULL,
    longitude   DOUBLE       NOT NULL,
    arrived_at  DATETIME(6)  NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ride_trip_stops_trip_id ON ride_trip_stops (trip_id);
