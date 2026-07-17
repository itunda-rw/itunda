-- Real Coupang-style multi-item orders (rw.itunda.commerce.OrderService, 2026-07-18) --
-- the third and last of the three new "super app" phases named in the goal expansion.
-- See core/.../domain/Order.kt and OrderItem.kt for the entities this transcribes
-- column-for-column.

CREATE TABLE orders (
    id                VARCHAR(64)    NOT NULL PRIMARY KEY,
    buyer_id          VARCHAR(64)    NOT NULL,
    merchant_id       VARCHAR(64)    NOT NULL,
    delivery_address  VARCHAR(500)   NOT NULL,
    total_amount      DECIMAL(18, 2) NOT NULL,
    fee               DECIMAL(18, 2) NOT NULL,
    transaction_id    VARCHAR(64)    NOT NULL,
    status            VARCHAR(16)    NOT NULL DEFAULT 'PLACED',
    created_at        DATETIME(6)    NOT NULL,
    updated_at        DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_orders_buyer_id ON orders (buyer_id);
CREATE INDEX idx_orders_merchant_id ON orders (merchant_id);

CREATE TABLE order_items (
    id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    order_id      VARCHAR(64)    NOT NULL,
    product_id    VARCHAR(64)    NOT NULL,
    product_name  VARCHAR(255)   NOT NULL,
    unit_price    DECIMAL(18, 2) NOT NULL,
    quantity      INT            NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_order_items_order_id ON order_items (order_id);
