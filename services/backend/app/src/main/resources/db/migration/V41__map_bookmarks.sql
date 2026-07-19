-- Real bookmarked/favorite places on the map (rw.itunda.maps.MapBookmarkService,
-- 2026-07-19) -- closes item 7 on the Maps "100%" roadmap. A place here isn't a foreign
-- key into any existing table (Nominatim results aren't itunda-owned records) so the
-- real display name + coordinate are stored directly, same shape a real client already
-- gets back from GET /api/v1/maps/search or /nearby.

CREATE TABLE map_bookmarks (
    id            VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id       VARCHAR(64) NOT NULL,
    display_name  VARCHAR(512) NOT NULL,
    latitude      DOUBLE NOT NULL,
    longitude     DOUBLE NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    CONSTRAINT uq_map_bookmarks_user_place UNIQUE (user_id, latitude, longitude)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_map_bookmarks_user_id ON map_bookmarks (user_id);
