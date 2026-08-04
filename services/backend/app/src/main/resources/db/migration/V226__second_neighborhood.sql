-- Real dual-neighborhood support (2026-08-04) -- see User.kt's own doc comment.

ALTER TABLE users
    ADD COLUMN second_neighborhood VARCHAR(120) NULL;
