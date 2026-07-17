-- Real 당근마켓 (Danggeun/Karrot Market)-style secondhand listings
-- (rw.itunda.marketplace.MarketplaceService, 2026-07-18) -- the second of the three new
-- "super app" phases named in the goal expansion. See core/.../domain/Listing.kt for
-- the entity this transcribes column-for-column.

CREATE TABLE listings (
    id           VARCHAR(64)    NOT NULL PRIMARY KEY,
    seller_id    VARCHAR(64)    NOT NULL,
    title        VARCHAR(255)   NOT NULL,
    description  VARCHAR(2000)  NOT NULL,
    price        DECIMAL(18, 2) NOT NULL,
    category     VARCHAR(64)    NOT NULL,
    status       VARCHAR(16)    NOT NULL DEFAULT 'ACTIVE',
    created_at   DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_listings_status_created_at ON listings (status, created_at);
CREATE INDEX idx_listings_status_category_created_at ON listings (status, category, created_at);
CREATE INDEX idx_listings_seller_id ON listings (seller_id);
