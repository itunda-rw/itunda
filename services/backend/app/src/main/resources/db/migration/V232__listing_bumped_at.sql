-- Real 당근마켓-style "끌어올리기" (bump to top of feed), 2026-08-10 -- a genuinely
-- missing core Hood retention mechanic (manner-temperature/trust score and price
-- offers already existed; this one didn't). Nullable, separate from created_at (which
-- stays the real, immutable creation time) -- feed queries order by
-- COALESCE(bumped_at, created_at) DESC, so a listing with no bump ever still sorts
-- exactly as it always has.
ALTER TABLE listings
    ADD COLUMN bumped_at DATETIME(6) NULL;
