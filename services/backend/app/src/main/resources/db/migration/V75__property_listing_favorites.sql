-- Real 당근부동산 property-listing wishlist (rw.itunda.realestate.PropertyListingFavoriteService,
-- 2026-07-22) -- closes the same docs/DESIGN_REFERENCES.md-named Hood gap as
-- V74__job_post_favorites.sql: Marketplace listings already got a real wishlist
-- (2026-07-21, see V56__listing_favorites.sql) but Property never did. Mirrors
-- listing_favorites' exact shape, just for a property listing instead of a listing.

CREATE TABLE property_listing_favorites (
    id                     VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id                VARCHAR(64) NOT NULL,
    property_listing_id    VARCHAR(64) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    CONSTRAINT uq_property_listing_favorites_user_listing UNIQUE (user_id, property_listing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_property_listing_favorites_user_id ON property_listing_favorites (user_id);
