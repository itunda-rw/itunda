-- Real Coupang Eats-style food delivery (rw.itunda.eats, 2026-07-18) -- reuses the
-- existing merchants/merchant_products tables as restaurants/menu items (see
-- EatsOrder.kt's own doc comment), adds real riders and a real delivery order lifecycle.

CREATE TABLE riders (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    wallet_id   VARCHAR(64) NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    available   BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uk_riders_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE eats_orders (
    id                            VARCHAR(64)    NOT NULL PRIMARY KEY,
    buyer_id                      VARCHAR(64)    NOT NULL,
    restaurant_id                 VARCHAR(64)    NOT NULL,
    rider_id                      VARCHAR(64),
    delivery_address              VARCHAR(500)   NOT NULL,
    items_subtotal                DECIMAL(18, 2) NOT NULL,
    delivery_fee                  DECIMAL(18, 2) NOT NULL,
    platform_fee                  DECIMAL(18, 2) NOT NULL,
    total_amount                  DECIMAL(18, 2) NOT NULL,
    transaction_id                VARCHAR(64)    NOT NULL,
    delivery_payout_transaction_id VARCHAR(64),
    status                        VARCHAR(16)    NOT NULL DEFAULT 'PLACED',
    created_at                    DATETIME(6)    NOT NULL,
    updated_at                    DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_eats_orders_buyer_id ON eats_orders (buyer_id);
CREATE INDEX idx_eats_orders_restaurant_id ON eats_orders (restaurant_id);
CREATE INDEX idx_eats_orders_rider_id ON eats_orders (rider_id);
CREATE INDEX idx_eats_orders_status_rider ON eats_orders (status, rider_id);

CREATE TABLE eats_order_items (
    id            VARCHAR(64)    NOT NULL PRIMARY KEY,
    order_id      VARCHAR(64)    NOT NULL,
    product_id    VARCHAR(64)    NOT NULL,
    product_name  VARCHAR(255)   NOT NULL,
    unit_price    DECIMAL(18, 2) NOT NULL,
    quantity      INT            NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_eats_order_items_order_id ON eats_order_items (order_id);
