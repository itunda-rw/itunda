-- Real Karrot-Score-style numeric trust/reputation badge (2026-07-21) -- closes the
-- "itunda's Hood cards show no seller/poster reputation at all today" gap
-- docs/DESIGN_REFERENCES.md's Hood research names directly. Deliberately a plain
-- 0-1000 numeric score starting at 30, NOT a literal manner-temperature/Celsius
-- metaphor: Karrot's own real UK/Canada localization research (cited in that doc)
-- found the temperature framing confusing and low scores insulting for non-Korean
-- users, and replaced it with exactly this neutral scale for global markets -- Rwanda
-- is the same kind of non-Korean market, so it gets the already-localized version
-- directly rather than the original domestic metaphor. See User.trustScore's own doc
-- comment for how the score moves.

ALTER TABLE users
    ADD COLUMN trust_score INT NOT NULL DEFAULT 30;
