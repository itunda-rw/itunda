-- Real hyperlocal neighborhood auto-filtering (2026-07-20) -- closes the "User has no
-- address/district field" gap Marketplace/Community/Jobs/RealEstate's own doc comments
-- all named. See User.kt/Listing.kt/CommunityPost.kt/JobPost.kt/PropertyListing.kt's own
-- doc comments.

ALTER TABLE users
    ADD COLUMN neighborhood VARCHAR(120) NULL;

ALTER TABLE listings
    ADD COLUMN neighborhood VARCHAR(120) NULL;

ALTER TABLE community_posts
    ADD COLUMN neighborhood VARCHAR(120) NULL;

ALTER TABLE job_posts
    ADD COLUMN neighborhood VARCHAR(120) NULL;

ALTER TABLE property_listings
    ADD COLUMN neighborhood VARCHAR(120) NULL;
