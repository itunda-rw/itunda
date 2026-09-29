-- Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment,
-- rw.itunda.marketplace.VehicleInspectionService) -- see VehicleInspectionMechanic.kt /
-- VehicleInspectionBooking.kt's own doc comments.

CREATE TABLE vehicle_inspection_mechanics (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id       VARCHAR(64)  NOT NULL,
    wallet_id     VARCHAR(64)  NOT NULL,
    business_name VARCHAR(200) NOT NULL,
    available     TINYINT(1)   NOT NULL DEFAULT 1,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_vehicle_inspection_mechanics_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE vehicle_inspection_bookings (
    id                        VARCHAR(64)   NOT NULL PRIMARY KEY,
    listing_id                VARCHAR(64)   NOT NULL,
    buyer_id                  VARCHAR(64)   NOT NULL,
    mechanic_id               VARCHAR(64)   NOT NULL,
    fee                       DECIMAL(18,2) NOT NULL,
    platform_fee              DECIMAL(18,2) NOT NULL,
    scheduled_for             DATETIME(6)   NOT NULL,
    hold_transaction_id       VARCHAR(64)   NOT NULL,
    status                    VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    resolution_transaction_id VARCHAR(64)   NULL,
    findings                  VARCHAR(2000) NULL,
    created_at                DATETIME(6)   NOT NULL,
    updated_at                DATETIME(6)   NOT NULL,
    version                   BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_vehicle_inspection_bookings_buyer_id ON vehicle_inspection_bookings (buyer_id);
CREATE INDEX idx_vehicle_inspection_bookings_mechanic_id ON vehicle_inspection_bookings (mechanic_id);
