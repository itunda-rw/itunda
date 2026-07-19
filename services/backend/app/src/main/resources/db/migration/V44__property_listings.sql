-- Real 당근부동산 (Danggeun/Karrot "Real Estate")-style property listing
-- (rw.itunda.realestate.PropertyListingService), 2026-07-19 -- the third and last of
-- the three explicitly-named 당근-style neighborhood-services products (alongside the
-- already-real 당근마켓/Marketplace, 당근생활/Community, 당근알바/Jobs). See
-- PropertyListing.kt's own doc comment for why this is its own entity, not a widened
-- Listing.

CREATE TABLE property_listings (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    lister_id      VARCHAR(64) NOT NULL,
    listing_type   VARCHAR(16) NOT NULL,
    property_type  VARCHAR(32) NOT NULL,
    title          VARCHAR(200) NOT NULL,
    description    VARCHAR(2000) NOT NULL,
    price          DECIMAL(18, 2) NOT NULL,
    bedrooms       INT NULL,
    size_sqm       DOUBLE NULL,
    status         VARCHAR(16) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    latitude       DOUBLE NULL,
    longitude      DOUBLE NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_property_listings_status_created_at ON property_listings (status, created_at);
CREATE INDEX idx_property_listings_status_type_created_at ON property_listings (status, listing_type, property_type, created_at);
CREATE INDEX idx_property_listings_lister_id ON property_listings (lister_id);
