-- Real funding-source selection for customer-presented payment codes -- see
-- CustomerPaymentCode.kt's own doc comment. NULL means "fall back to WalletType.MAIN",
-- the real historical behavior before this column existed, so no existing row changes
-- meaning.
ALTER TABLE customer_payment_codes ADD COLUMN wallet_id VARCHAR(64) NULL AFTER expires_at;
