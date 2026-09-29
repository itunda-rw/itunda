-- Real bug found live (2026-08-02): payEscrow/markSold/boostListing/markTaken all
-- read-then-mutate a Listing's status with no @Version guard -- two concurrent
-- payEscrow calls on the same ACTIVE listing can both read ACTIVE, both debit a
-- buyer's wallet, and both create a MarketplaceEscrow row before either commits.
ALTER TABLE listings
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
