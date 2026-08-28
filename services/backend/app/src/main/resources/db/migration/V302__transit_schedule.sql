-- Real Kigali GTFS-backed transit schedule (itunda Maps redesign, 2026-08-28, direct
-- Naver Map reference: the directions panel's Transit mode). Ships empty -- seeded via
-- a real, re-runnable admin-triggered import of the real Kigali GTFS feed
-- (TransitGtfsImportService), never hand-entered sample data.

CREATE TABLE transit_stops (
    id        VARCHAR(64) NOT NULL PRIMARY KEY,
    name      VARCHAR(200) NOT NULL,
    latitude  DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transit_routes (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    short_name VARCHAR(32) NULL,
    long_name  VARCHAR(200) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transit_scheduled_trips (
    id         VARCHAR(64) NOT NULL PRIMARY KEY,
    route_id   VARCHAR(64) NOT NULL,
    service_id VARCHAR(64) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_transit_scheduled_trips_route_id ON transit_scheduled_trips (route_id);

CREATE TABLE transit_stop_times (
    id                               VARCHAR(64) NOT NULL PRIMARY KEY,
    trip_id                          VARCHAR(64) NOT NULL,
    stop_id                          VARCHAR(64) NOT NULL,
    arrival_seconds_after_midnight   INT NOT NULL,
    departure_seconds_after_midnight INT NOT NULL,
    stop_sequence                    INT NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_transit_stop_times_trip_id ON transit_stop_times (trip_id);
CREATE INDEX idx_transit_stop_times_stop_id ON transit_stop_times (stop_id);
