-- Real 배민오더-style table/QR in-store ordering (rw.itunda.eats.DineInOrderService,
-- 2026-07-25) -- reuses the existing merchants/merchant_products tables as
-- restaurants/menu items exactly like eats_orders already does (see DineInOrder.kt's own
-- doc comment), but no delivery/rider columns at all -- payment settles straight to the
-- restaurant's own wallet at placement.

CREATE TABLE dine_in_orders (
    id                  VARCHAR(64)    NOT NULL PRIMARY KEY,
    buyer_id            VARCHAR(64)    NOT NULL,
    restaurant_id       VARCHAR(64)    NOT NULL,
    table_number        VARCHAR(50)    NOT NULL,
    items_subtotal      DECIMAL(18, 2) NOT NULL,
    platform_fee        DECIMAL(18, 2) NOT NULL,
    total_amount        DECIMAL(18, 2) NOT NULL,
    transaction_id      VARCHAR(64)    NOT NULL,
    status              VARCHAR(16)    NOT NULL DEFAULT 'PLACED',
    notes               VARCHAR(500),
    created_at          DATETIME(6)    NOT NULL,
    updated_at          DATETIME(6)    NOT NULL,
    refund_transaction_id VARCHAR(64)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_dine_in_orders_buyer_id ON dine_in_orders (buyer_id);
CREATE INDEX idx_dine_in_orders_restaurant_id ON dine_in_orders (restaurant_id);

CREATE TABLE dine_in_order_items (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    order_id              VARCHAR(64)    NOT NULL,
    product_id            VARCHAR(64)    NOT NULL,
    product_name          VARCHAR(255)   NOT NULL,
    unit_price            DECIMAL(18, 2) NOT NULL,
    quantity              INT            NOT NULL,
    selected_options_json VARCHAR(4000)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_dine_in_order_items_order_id ON dine_in_order_items (order_id);
