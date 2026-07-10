-- Merchant registration + QR-style payment collection (rw.itunda.merchant module,
-- 2026-07-11). See core/.../domain/Merchant.kt and PaymentIntent.kt for the
-- entities this transcribes column-for-column, same discipline as V1__init_schema.sql.

CREATE TABLE merchants (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    owner_user_id  VARCHAR(64)  NOT NULL,
    wallet_id      VARCHAR(64)  NOT NULL,
    business_name  VARCHAR(255) NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uq_merchants_owner_user_id UNIQUE (owner_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payment_intents (
    id                       VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id              VARCHAR(64)    NOT NULL,
    amount                   DECIMAL(18, 2) NOT NULL,
    description              VARCHAR(255)   NOT NULL,
    status                   VARCHAR(16)    NOT NULL,
    expires_at               DATETIME(6)    NOT NULL,
    completed_transaction_id VARCHAR(64)    NULL,
    paid_by_user_id          VARCHAR(64)    NULL,
    created_at               DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_payment_intents_merchant_id_created_at ON payment_intents (merchant_id, created_at);
