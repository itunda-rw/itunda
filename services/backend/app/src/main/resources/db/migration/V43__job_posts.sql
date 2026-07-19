-- Real 당근알바 (Danggeun/Karrot "Alba"/part-time-job board)-style local job posting
-- (rw.itunda.jobs.JobPostService), 2026-07-19 -- the second of the three explicitly-
-- named 당근-style neighborhood-services products (alongside the already-real
-- 당근마켓/Marketplace and 당근생활/Community). See JobPost.kt's own doc comment for
-- why this is its own entity, not a widened Listing.

CREATE TABLE job_posts (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    poster_id    VARCHAR(64) NOT NULL,
    category     VARCHAR(32) NOT NULL,
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(2000) NOT NULL,
    pay_type     VARCHAR(16) NOT NULL,
    pay_amount   DECIMAL(18, 2) NOT NULL,
    status       VARCHAR(16) NOT NULL,
    created_at   DATETIME(6) NOT NULL,
    latitude     DOUBLE NULL,
    longitude    DOUBLE NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_job_posts_status_created_at ON job_posts (status, created_at);
CREATE INDEX idx_job_posts_status_category_created_at ON job_posts (status, category, created_at);
CREATE INDEX idx_job_posts_poster_id ON job_posts (poster_id);
