ALTER TABLE partner_mini_apps
    ADD COLUMN app_id VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN version VARCHAR(64) NOT NULL DEFAULT '0.0.0',
    ADD COLUMN manifest_version INT NOT NULL DEFAULT 1;

CREATE INDEX idx_partner_mini_apps_app_version
    ON partner_mini_apps (app_id, version);
