-- Real "pay via itunda" Marketplace escrow (rw.itunda.marketplace.MarketplaceService,
-- 2026-07-25) -- closes a real trust gap Naver Cafe's own real "안전거래" (Safe Trade)
-- product exists specifically to solve: itunda's Marketplace has always settled
-- buyer/seller in person, off-platform, with zero protection. Purely opt-in, alongside
-- the existing in-person cash handoff, never replacing it.

CREATE TABLE marketplace_escrows (
    id                        VARCHAR(64)    NOT NULL PRIMARY KEY,
    listing_id                VARCHAR(64)    NOT NULL,
    buyer_id                  VARCHAR(64)    NOT NULL,
    seller_id                 VARCHAR(64)    NOT NULL,
    amount                    DECIMAL(18, 2) NOT NULL,
    fee                       DECIMAL(18, 2) NOT NULL,
    status                    VARCHAR(16)    NOT NULL DEFAULT 'HELD',
    hold_transaction_id       VARCHAR(64)    NOT NULL,
    resolution_transaction_id VARCHAR(64),
    dispute_reason            VARCHAR(500),
    created_at                DATETIME(6)    NOT NULL,
    updated_at                DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_marketplace_escrows_listing_id ON marketplace_escrows (listing_id);
CREATE INDEX idx_marketplace_escrows_buyer_id ON marketplace_escrows (buyer_id);
CREATE INDEX idx_marketplace_escrows_status ON marketplace_escrows (status);
