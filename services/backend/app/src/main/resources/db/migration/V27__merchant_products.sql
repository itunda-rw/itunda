-- Real merchant product catalog (rw.itunda.merchant.MerchantProductService, 2026-07-17),
-- closing the "Toss Place" register-software gap. See core/.../domain/MerchantProduct.kt
-- for the entity this transcribes column-for-column.

CREATE TABLE merchant_products (
    id           VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id  VARCHAR(64)    NOT NULL,
    name         VARCHAR(255)   NOT NULL,
    price        DECIMAL(18, 2) NOT NULL,
    active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at   DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_merchant_products_merchant_id ON merchant_products (merchant_id);
