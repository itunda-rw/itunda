-- Real 배달의민족 함께주문 (Baemin "Together Order") shared-cart group ordering
-- (2026-08-15) -- see GroupEatsOrder.kt's own doc comment for the full account. A
-- pre-checkout staging area only: finalizing resolves into one real, unchanged
-- eats_orders row via the existing EatsOrderService.placeOrder.

CREATE TABLE group_eats_orders (
    id                  VARCHAR(64)    NOT NULL PRIMARY KEY,
    host_user_id        VARCHAR(64)    NOT NULL,
    restaurant_id       VARCHAR(64)    NOT NULL,
    join_code           VARCHAR(12)    NOT NULL,
    delivery_address    VARCHAR(500)   NOT NULL,
    delivery_latitude   DOUBLE,
    delivery_longitude  DOUBLE,
    fulfillment_type    VARCHAR(16)    NOT NULL DEFAULT 'DELIVERY',
    status              VARCHAR(16)    NOT NULL DEFAULT 'OPEN',
    resulting_order_id  VARCHAR(64),
    created_at          DATETIME(6)    NOT NULL,
    finalized_at        DATETIME(6),
    CONSTRAINT uk_group_eats_orders_join_code UNIQUE (join_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_eats_orders_host_user_id ON group_eats_orders (host_user_id);

CREATE TABLE group_eats_order_participants (
    id              VARCHAR(64) NOT NULL PRIMARY KEY,
    group_order_id  VARCHAR(64) NOT NULL,
    user_id         VARCHAR(64) NOT NULL,
    joined_at       DATETIME(6) NOT NULL,
    CONSTRAINT uk_group_eats_order_participants_order_user UNIQUE (group_order_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_eats_order_participants_group_order_id ON group_eats_order_participants (group_order_id);

CREATE TABLE group_eats_order_items (
    id                   VARCHAR(64)    NOT NULL PRIMARY KEY,
    group_order_id       VARCHAR(64)    NOT NULL,
    user_id              VARCHAR(64)    NOT NULL,
    product_id           VARCHAR(64)    NOT NULL,
    quantity             INT            NOT NULL,
    selected_choice_ids  VARCHAR(500),
    unit_price_snapshot  DECIMAL(18, 2) NOT NULL,
    created_at           DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_group_eats_order_items_group_order_id ON group_eats_order_items (group_order_id);
CREATE INDEX idx_group_eats_order_items_group_order_user ON group_eats_order_items (group_order_id, user_id);
