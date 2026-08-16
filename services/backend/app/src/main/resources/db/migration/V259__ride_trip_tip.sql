ALTER TABLE ride_trips ADD COLUMN tip_amount DECIMAL(18,2) NULL;
ALTER TABLE ride_trips ADD COLUMN tip_transaction_id VARCHAR(64) NULL;
