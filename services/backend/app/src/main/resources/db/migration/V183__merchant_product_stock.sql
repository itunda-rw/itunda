ALTER TABLE merchant_products
    ADD COLUMN stock_quantity INT NULL,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
