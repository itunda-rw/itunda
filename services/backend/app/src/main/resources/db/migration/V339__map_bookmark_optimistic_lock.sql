-- Map bookmarks gained optimistic locking in MapBookmark.kt.
-- Keep the production schema aligned with Hibernate's @Version field.
ALTER TABLE map_bookmarks
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
