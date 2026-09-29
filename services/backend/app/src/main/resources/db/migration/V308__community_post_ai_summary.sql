ALTER TABLE community_posts
    ADD COLUMN ai_summary VARCHAR(500) NULL,
    ADD COLUMN ai_summary_generated_at DATETIME(6) NULL;
