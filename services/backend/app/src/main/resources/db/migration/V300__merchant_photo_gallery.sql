-- Real photo gallery for merchants (itunda Maps redesign, 2026-08-28, direct Naver Map
-- reference) -- comma-separated real merchant-supplied URLs, same delimited-string
-- convention as merchants.closed_weekdays. photo_url (the existing single cover photo)
-- is untouched.

ALTER TABLE merchants ADD COLUMN photo_urls VARCHAR(2000) NULL;
