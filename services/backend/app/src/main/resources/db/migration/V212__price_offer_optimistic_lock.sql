-- Real bug found live (2026-08-02): PriceOfferService.respondToOffer reads a PENDING
-- PriceOffer, then mutates its status (ACCEPTED/REJECTED/COUNTERED) with no @Version
-- guard -- two concurrent responses to the same offer (e.g. accept + counter racing)
-- can both read PENDING and both win, leaving an inconsistent negotiation trail.
ALTER TABLE price_offers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
