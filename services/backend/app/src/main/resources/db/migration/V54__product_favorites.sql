-- Real product wishlist (rw.itunda.commerce.ProductFavoriteService, 2026-07-20) -- the
-- real "찜하기"/wishlist every real Coupang/Naver/Kakao/Toss Shopping-style app has,
-- entirely missing from Commerce until now. Mirrors eats_favorites' exact shape (see
-- V-whatever migration for that table), just for a product instead of a restaurant.

CREATE TABLE product_favorites (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64) NOT NULL,
    product_id     VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_product_favorites_user_product UNIQUE (user_id, product_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_product_favorites_user_id ON product_favorites (user_id);
