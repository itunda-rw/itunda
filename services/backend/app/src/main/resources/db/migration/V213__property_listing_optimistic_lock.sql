-- Real bug found live (2026-08-02): PropertyListingService's mark-taken/remove/
-- boost-equivalent flows read-then-mutate a PropertyListing's status with no @Version
-- guard, the same real check-then-act race Listing itself just got fixed for.
ALTER TABLE property_listings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
