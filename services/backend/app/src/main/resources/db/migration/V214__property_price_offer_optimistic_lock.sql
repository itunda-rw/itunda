-- Real bug found live (2026-08-02): mirrors price_offers' own optimistic-lock fix --
-- PropertyPriceOfferService's respond flow reads-then-mutates status with no @Version
-- guard.
ALTER TABLE property_price_offers
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
