ALTER TABLE riders
    ADD COLUMN current_latitude DOUBLE NULL,
    ADD COLUMN current_longitude DOUBLE NULL,
    ADD COLUMN location_updated_at TIMESTAMP NULL;
