-- Immutable mini-app release integrity metadata.
-- Values are supplied by the developer release manifest and retained with the
-- submission so review, runtime loading, and future rollback tooling can verify
-- that the approved artifact is the artifact being served.
ALTER TABLE partner_mini_apps
    ADD COLUMN release_id VARCHAR(64) NULL,
    ADD COLUMN manifest_sha256 VARCHAR(64) NULL,
    ADD COLUMN bundle_sha256 VARCHAR(64) NULL,
    ADD COLUMN bundle_size_bytes BIGINT NULL;
