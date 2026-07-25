-- Real composer photo send (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 3
-- recommendation #6 ("+" attach menu). Reuses the real UploadController pipeline
-- (2026-07-24) -- upload the file first via POST /api/v1/uploads, then send the
-- returned URL as imageUrl here. body stays NOT NULL (unchanged, no risky column-
-- nullability migration on a live table) -- an image-only send defaults body to a real
-- placeholder caption server-side, same "message body is always meaningful text, media
-- is an enrichment" convention every other special message type here already uses.
ALTER TABLE messages ADD COLUMN image_url VARCHAR(255) NULL;
ALTER TABLE group_messages ADD COLUMN image_url VARCHAR(255) NULL;
