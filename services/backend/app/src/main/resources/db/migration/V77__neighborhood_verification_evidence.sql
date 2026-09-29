-- Privacy-preserving neighborhood-verification evidence.  The app keeps the resolved
-- neighborhood and confirmation metadata, never a history of precise user coordinates.
ALTER TABLE users
    ADD COLUMN neighborhood_verified_at TIMESTAMP NULL,
    ADD COLUMN neighborhood_verification_count INT NOT NULL DEFAULT 0;
