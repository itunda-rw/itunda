ALTER TABLE partner_mini_apps
    ADD COLUMN manifest_url VARCHAR(500) NULL;

UPDATE partner_mini_apps
SET manifest_url = NULL
WHERE manifest_url = '';
