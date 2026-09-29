-- Real Coupang 타임특가 (Time Deal, rw.itunda.commerce.TimeDealService) -- see
-- TimeDeal.kt's own doc comment.

CREATE TABLE time_deals (
    id                 VARCHAR(64)   NOT NULL PRIMARY KEY,
    merchant_id        VARCHAR(64)   NOT NULL,
    product_id         VARCHAR(64)   NOT NULL,
    deal_price         DECIMAL(18,2) NOT NULL,
    original_price     DECIMAL(18,2) NOT NULL,
    total_quantity     INT           NOT NULL,
    remaining_quantity INT           NOT NULL,
    starts_at          DATETIME(6)   NOT NULL,
    ends_at            DATETIME(6)   NOT NULL,
    version            BIGINT        NOT NULL DEFAULT 0,
    created_at         DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_time_deals_merchant_id ON time_deals (merchant_id);
CREATE INDEX idx_time_deals_product_id ON time_deals (product_id);
CREATE INDEX idx_time_deals_active_window ON time_deals (starts_at, ends_at);
