ALTER TABLE agents ADD COLUMN latitude DOUBLE NULL AFTER daily_cash_out_limit;
ALTER TABLE agents ADD COLUMN longitude DOUBLE NULL AFTER latitude;
