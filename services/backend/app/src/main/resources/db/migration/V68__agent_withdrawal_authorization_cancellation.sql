ALTER TABLE agent_withdrawal_authorizations ADD COLUMN cancelled_at DATETIME(6) NULL AFTER consumed_at;
