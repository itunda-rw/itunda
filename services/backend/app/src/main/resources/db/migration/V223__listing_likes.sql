-- Real Karrot-style listing like count (2026-08-03) -- closes a real UI-fidelity gap
-- found comparing itunda's Marketplace list against real 당근마켓 screenshots: every
-- real row shows a heart count, separate from itunda's own pre-existing
-- listing_favorites (a personal save-for-later wishlist, not a public engagement
-- count). Mirrors community_likes (V42__community.sql) column-for-column.

ALTER TABLE listings ADD COLUMN like_count BIGINT NOT NULL DEFAULT 0;

CREATE TABLE listing_likes (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    listing_id  VARCHAR(64) NOT NULL,
    user_id     VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_listing_likes_listing_user ON listing_likes (listing_id, user_id);
