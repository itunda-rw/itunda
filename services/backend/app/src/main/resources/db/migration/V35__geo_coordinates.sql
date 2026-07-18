ALTER TABLE merchants ADD COLUMN latitude DOUBLE NULL;
ALTER TABLE merchants ADD COLUMN longitude DOUBLE NULL;

ALTER TABLE listings ADD COLUMN latitude DOUBLE NULL;
ALTER TABLE listings ADD COLUMN longitude DOUBLE NULL;

ALTER TABLE eats_orders ADD COLUMN delivery_latitude DOUBLE NULL;
ALTER TABLE eats_orders ADD COLUMN delivery_longitude DOUBLE NULL;
ALTER TABLE eats_orders ADD COLUMN distance_km DECIMAL(8,3) NULL;
