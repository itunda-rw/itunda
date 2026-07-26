-- Real Toss 내 차 시세 (my car's market value)-style registered vehicle
-- (rw.itunda.vehicle.VehicleValuationService, 2026-07-27). See Vehicle.kt's own doc
-- comment for the full sourced account.

CREATE TABLE vehicles (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    make VARCHAR(100) NOT NULL,
    model VARCHAR(100) NOT NULL,
    model_year INT NOT NULL,
    purchase_price DECIMAL(18, 2) NOT NULL,
    purchase_date DATE NOT NULL,
    mileage_km INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_vehicles_user (user_id)
);
