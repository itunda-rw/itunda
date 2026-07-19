-- Real 당근-style price-offer negotiation on a real PropertyListing
-- (rw.itunda.realestate.PropertyPriceOfferService), 2026-07-19 -- mirrors price_offers
-- (V40) exactly, see PropertyPriceOffer.kt's own doc comment for why real estate,
-- unlike Jobs, genuinely warrants this.

CREATE TABLE property_price_offers (
    id                     VARCHAR(64) NOT NULL PRIMARY KEY,
    property_listing_id    VARCHAR(64) NOT NULL,
    message_id             VARCHAR(64) NOT NULL,
    conversation_id        VARCHAR(64) NOT NULL,
    inquirer_id            VARCHAR(64) NOT NULL,
    lister_id              VARCHAR(64) NOT NULL,
    proposed_by_user_id    VARCHAR(64) NOT NULL,
    amount                 DECIMAL(18, 2) NOT NULL,
    status                 VARCHAR(16) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    responded_at           DATETIME(6) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_property_price_offers_listing_id ON property_price_offers (property_listing_id);
CREATE UNIQUE INDEX uq_property_price_offers_message_id ON property_price_offers (message_id);
