-- Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program,
-- rw.itunda.merchant.MerchantFeeWaiverService) -- see Merchant.kt's own doc comment.

ALTER TABLE merchants
    ADD COLUMN fee_rate_override DECIMAL(6,4) NULL;
