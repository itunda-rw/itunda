-- Real 당근마켓-style price-offer negotiation (rw.itunda.marketplace.PriceOfferService,
-- 2026-07-19) -- closes the last item on the Talk polish roadmap ("당근-style
-- 'message seller with a price offer' negotiation"). See PriceOffer.kt's own doc
-- comment. Each row is one real proposed amount tied to one real chat message; a
-- counter-offer is a brand-new row (proposed_by_user_id flips to the counterer), not an
-- edit of the original, so the full negotiation history survives.

CREATE TABLE price_offers (
    id                     VARCHAR(64) NOT NULL PRIMARY KEY,
    listing_id             VARCHAR(64) NOT NULL,
    message_id             VARCHAR(64) NOT NULL,
    conversation_id        VARCHAR(64) NOT NULL,
    buyer_id               VARCHAR(64) NOT NULL,
    seller_id              VARCHAR(64) NOT NULL,
    proposed_by_user_id    VARCHAR(64) NOT NULL,
    amount                 DECIMAL(18, 2) NOT NULL,
    status                 VARCHAR(16) NOT NULL,
    created_at             DATETIME(6) NOT NULL,
    responded_at           DATETIME(6) NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_price_offers_listing_id ON price_offers (listing_id);
CREATE UNIQUE INDEX uq_price_offers_message_id ON price_offers (message_id);
