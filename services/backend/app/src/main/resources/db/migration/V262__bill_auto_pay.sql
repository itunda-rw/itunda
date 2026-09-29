CREATE TABLE bill_auto_pay_settings (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    provider_id VARCHAR(64) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    max_amount DECIMAL(18,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_paid_bill_id VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE KEY uq_bill_auto_pay_user_provider (user_id, provider_id)
);
