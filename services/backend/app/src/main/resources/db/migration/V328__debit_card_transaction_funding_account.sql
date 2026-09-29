ALTER TABLE debit_card_transactions ADD COLUMN funding_account_type VARCHAR(32) NOT NULL DEFAULT 'MAIN';
