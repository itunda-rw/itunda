ALTER TABLE listings
    ADD COLUMN vehicle_mileage_km INT NULL,
    ADD COLUMN vehicle_insurance_claim_count INT NULL,
    ADD COLUMN vehicle_is_lease_takeover BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN lease_total_acquisition_cost DECIMAL(18,2) NULL,
    ADD COLUMN lease_remaining_months INT NULL,
    ADD COLUMN lease_total_months INT NULL,
    ADD COLUMN lease_monthly_payment DECIMAL(18,2) NULL,
    ADD COLUMN lease_subsidy_amount DECIMAL(18,2) NULL,
    ADD COLUMN lease_return_fee DECIMAL(18,2) NULL;
