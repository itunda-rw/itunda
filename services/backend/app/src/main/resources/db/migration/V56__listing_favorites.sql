-- Real Marketplace listing wishlist (rw.itunda.marketplace.ListingFavoriteService,
-- 2026-07-21) -- the real 관심목록/wishlist Karrot's own product gives secondhand
-- listings, entirely missing from Hood/Marketplace until now. Mirrors
-- product_favorites' exact shape (see V54__product_favorites.sql), just for a listing
-- instead of a product.

CREATE TABLE listing_favorites (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64) NOT NULL,
    listing_id     VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_listing_favorites_user_listing UNIQUE (user_id, listing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_listing_favorites_user_id ON listing_favorites (user_id);
