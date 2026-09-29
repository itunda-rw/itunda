-- Cached engagement counters and lazy meetup-group creation share the post row.
ALTER TABLE community_posts
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
