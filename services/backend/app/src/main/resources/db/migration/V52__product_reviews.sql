-- Real post-delivery product reviews (rw.itunda.commerce.ProductReviewService, 2026-07-20) --
-- the Commerce row's one remaining real, buildable gap versus Coupang/Naver Shopping's
-- product review culture (Eats already got this treatment on 2026-07-18 -- see
-- V34__eats_reviews.sql -- this mirrors it for Commerce products). One real review per
-- real order_items row, so a buyer reviews each distinct purchase of a product once.

CREATE TABLE product_reviews (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    order_item_id         VARCHAR(64)   NOT NULL,
    order_id              VARCHAR(64)   NOT NULL,
    buyer_id              VARCHAR(64)   NOT NULL,
    product_id            VARCHAR(64)   NOT NULL,
    merchant_id           VARCHAR(64)   NOT NULL,
    rating                INT           NOT NULL,
    comment               VARCHAR(1000) NULL,
    created_at            DATETIME(6)   NOT NULL,
    CONSTRAINT uq_product_reviews_order_item_id UNIQUE (order_item_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_product_reviews_product_id ON product_reviews (product_id);
CREATE INDEX idx_product_reviews_merchant_id ON product_reviews (merchant_id);
