-- Real named/colored bookmark folders (2026-07-22) -- closes item 4 from the Maps
-- design-doc sweep, Naver/Kakao Maps' own real "My Places" folder grouping (e.g.
-- "Favorites"/"Home"/"Cafes to try", each with its own pin color) rather than every
-- saved place living in one flat, uncategorized list. Existing bookmarks default into a
-- single real folder ("Saved places") in the same star-yellow color the star icon
-- already used before this migration (#F5A623, see MapView.tsx's isBookmarked color) --
-- a real, honest default, not a placeholder that silently reclassifies old data.

ALTER TABLE map_bookmarks
    ADD COLUMN folder_name VARCHAR(120) NOT NULL DEFAULT 'Saved places',
    ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#F5A623';

CREATE INDEX idx_map_bookmarks_user_folder ON map_bookmarks (user_id, folder_name);
