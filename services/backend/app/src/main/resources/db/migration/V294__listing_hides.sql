-- Real Karrot "숨기기" (hide this post) -- see rw.itunda.marketplace.ListingHideService's
-- own doc comment for the full sourced account. Mirrors listing_favorites' exact shape
-- (see V56__listing_favorites.sql), just a suppression list instead of a wishlist.

CREATE TABLE listing_hides (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64) NOT NULL,
    listing_id     VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_listing_hides_user_listing UNIQUE (user_id, listing_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_listing_hides_user_id ON listing_hides (user_id);
