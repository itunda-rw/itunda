ALTER TABLE stock_watchlist ADD COLUMN target_price DECIMAL(18,2) NULL;
ALTER TABLE stock_watchlist ADD COLUMN target_direction VARCHAR(8) NULL;
ALTER TABLE stock_watchlist ADD COLUMN alert_triggered_at TIMESTAMP NULL;
