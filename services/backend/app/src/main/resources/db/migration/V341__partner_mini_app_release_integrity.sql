ALTER TABLE partner_mini_apps
    ADD COLUMN release_id VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN manifest_sha256 VARCHAR(64) NOT NULL DEFAULT '',
    ADD COLUMN bundle_sha256 VARCHAR(64) NULL,
    ADD COLUMN bundle_size_bytes BIGINT NULL,
    ADD COLUMN published_at TIMESTAMP NULL,
    ADD COLUMN revision BIGINT NOT NULL DEFAULT 0;

UPDATE partner_mini_apps
SET release_id = CONCAT('legacy_', id)
WHERE release_id = '';

UPDATE partner_mini_apps
SET manifest_sha256 = SHA2(CONCAT(app_id, ':', version, ':', manifest_version, ':', name, ':', description, ':', bundle_url, ':', permissions), 256)
WHERE manifest_sha256 = '';

ALTER TABLE partner_mini_apps
    MODIFY COLUMN release_id VARCHAR(64) NOT NULL,
    MODIFY COLUMN manifest_sha256 VARCHAR(64) NOT NULL;

CREATE UNIQUE INDEX uq_partner_mini_apps_release_id
    ON partner_mini_apps (release_id);
