-- Real Toss Payments-style API key self-service reissue with a grace period -- see
-- MerchantService.generateApiKey's own doc comment. NULL means no key was ever rotated
-- (or the previous key's grace period already elapsed).

ALTER TABLE merchants ADD COLUMN previous_api_key_hash VARCHAR(64) NULL;
ALTER TABLE merchants ADD COLUMN previous_api_key_expires_at DATETIME(6) NULL;
