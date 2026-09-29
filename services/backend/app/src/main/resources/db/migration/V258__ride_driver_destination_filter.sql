ALTER TABLE ride_drivers ADD COLUMN destination_latitude DOUBLE NULL;
ALTER TABLE ride_drivers ADD COLUMN destination_longitude DOUBLE NULL;
ALTER TABLE ride_drivers ADD COLUMN destination_uses_today INT NOT NULL DEFAULT 0;
ALTER TABLE ride_drivers ADD COLUMN destination_uses_reset_date DATE NULL;
