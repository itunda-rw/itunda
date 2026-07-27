-- Real Kakao Pay Securities "동전 모으기" (coin collection) round-up-to-invest:
-- spare change under a chosen increment auto-invests into a chosen stock, instead of
-- (or alongside the existing choice of) a savings goal. See RoundUpSettings.kt's own
-- doc comment for the full sourced account.

ALTER TABLE round_up_settings ADD COLUMN target_stock_id VARCHAR(64) NULL;
