-- Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
-- can make pay for tax and moto as well"). See MotoFareTrip.kt's own doc comment for
-- the full sourced account of Kigali's real smart-metered moto-taxi fares and the
-- honest boundary this simulates. Unlike transit_trips (one user_id, itunda absorbs
-- the fare as its own simulated expense against an external bus operator), this has
-- both a real rider and a real driver -- the driver is an itunda user tapping/scanning
-- the rider's own code, so the fare is a real, direct WALLET-to-WALLET payment, no
-- itunda-owned clearing account involved.

CREATE TABLE moto_fare_trips (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    rider_user_id         VARCHAR(64)    NOT NULL,
    driver_user_id        VARCHAR(64)    NOT NULL,
    fare                  DECIMAL(18, 2) NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_moto_fare_trips_rider_created ON moto_fare_trips (rider_user_id, created_at);
CREATE INDEX idx_moto_fare_trips_driver_created ON moto_fare_trips (driver_user_id, created_at);
