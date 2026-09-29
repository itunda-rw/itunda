-- Real 당근알바 job-post wishlist (rw.itunda.jobs.JobPostFavoriteService, 2026-07-22) --
-- closes a docs/DESIGN_REFERENCES.md-named Hood gap: Marketplace listings already got a
-- real wishlist (2026-07-21, see V56__listing_favorites.sql) but Jobs never did. Mirrors
-- listing_favorites' exact shape, just for a job post instead of a listing.

CREATE TABLE job_post_favorites (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64) NOT NULL,
    job_post_id    VARCHAR(64) NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    CONSTRAINT uq_job_post_favorites_user_post UNIQUE (user_id, job_post_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_job_post_favorites_user_id ON job_post_favorites (user_id);
