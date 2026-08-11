-- Real customer-presented payment code (rw.itunda.merchant.MerchantService.
-- generateCustomerPaymentCode/chargeByCustomerCode) -- see CustomerPaymentCode.kt's
-- own doc comment for the real KakaoPay/Toss Pay flow this closes: the customer
-- opens Pay, a scannable code is already on screen (no typing), a merchant scans it
-- and enters the amount. Same real single-use/expiring-token shape as
-- phone_verification_tokens/email_verification_tokens, not a new pattern.

CREATE TABLE customer_payment_codes (
    id          VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64)  NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    used_at     DATETIME(6)  NULL,
    created_at  DATETIME(6)  NOT NULL,
    UNIQUE KEY uk_customer_payment_codes_code (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_customer_payment_codes_user_id ON customer_payment_codes (user_id);
