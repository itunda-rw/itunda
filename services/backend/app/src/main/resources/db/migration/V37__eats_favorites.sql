-- Real bookmarked/favorited restaurants (rw.itunda.eats.EatsFavoriteService, 2026-07-19)
-- -- closes the "favorites" item on the Eats polish roadmap. See EatsFavorite.kt's own
-- doc comment.

CREATE TABLE eats_favorites (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64) NOT NULL,
    restaurant_id  VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_eats_favorites_user_restaurant UNIQUE (user_id, restaurant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_eats_favorites_user_id ON eats_favorites (user_id);
