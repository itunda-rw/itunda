-- Real 동네생활 (Danggeun/Karrot "Neighborhood Life")-style community board
-- (rw.itunda.community.CommunityService), 2026-07-19 -- Karrot's own second core
-- surface alongside its already-real marketplace, explicitly named by the user
-- alongside 당근알바/당근부동산 as a distinct neighborhood-services product. See
-- CommunityPost.kt's own doc comment for why like_count/comment_count are real cached
-- counters rather than computed at read time.

CREATE TABLE community_posts (
    id             VARCHAR(64) NOT NULL PRIMARY KEY,
    author_id      VARCHAR(64) NOT NULL,
    category       VARCHAR(32) NOT NULL,
    title          VARCHAR(200) NOT NULL,
    body           VARCHAR(4000) NOT NULL,
    status         VARCHAR(16) NOT NULL,
    like_count     BIGINT NOT NULL DEFAULT 0,
    comment_count  BIGINT NOT NULL DEFAULT 0,
    created_at     DATETIME(6) NOT NULL,
    latitude       DOUBLE NULL,
    longitude      DOUBLE NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_community_posts_status_created_at ON community_posts (status, created_at);
CREATE INDEX idx_community_posts_status_category_created_at ON community_posts (status, category, created_at);
CREATE INDEX idx_community_posts_author_id ON community_posts (author_id);

CREATE TABLE community_comments (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    post_id     VARCHAR(64) NOT NULL,
    author_id   VARCHAR(64) NOT NULL,
    body        VARCHAR(1000) NOT NULL,
    created_at  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_community_comments_post_id ON community_comments (post_id, created_at);

CREATE TABLE community_likes (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    post_id     VARCHAR(64) NOT NULL,
    user_id     VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_community_likes_post_user ON community_likes (post_id, user_id);
