-- Dashboard volume/count are based on completion time, not initiation time.
CREATE INDEX idx_transactions_status_completed_at ON transactions (status, completed_at);
