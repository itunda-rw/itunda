-- Immutable release history for staged deployment and rollback.
-- PartnerMiniApp remains the catalog manifest; each approved artifact gets its own
-- durable release row so activating a new release never destroys rollback evidence.
CREATE TABLE partner_mini_app_releases (
    release_id VARCHAR(64) NOT NULL,
    mini_app_id VARCHAR(64) NOT NULL,
    bundle_url VARCHAR(500) NOT NULL,
    manifest_sha256 VARCHAR(64) NOT NULL,
    bundle_sha256 VARCHAR(64) NOT NULL,
    bundle_size_bytes BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    activated_at TIMESTAMP(6) NULL,
    rolled_back_at TIMESTAMP(6) NULL,
    rollback_reason VARCHAR(255) NULL,
    PRIMARY KEY (release_id),
    CONSTRAINT fk_partner_mini_app_release_app
        FOREIGN KEY (mini_app_id) REFERENCES partner_mini_apps(id)
);

CREATE INDEX idx_partner_mini_app_releases_app_created
    ON partner_mini_app_releases (mini_app_id, created_at DESC);

CREATE INDEX idx_partner_mini_app_releases_app_status
    ON partner_mini_app_releases (mini_app_id, status);
