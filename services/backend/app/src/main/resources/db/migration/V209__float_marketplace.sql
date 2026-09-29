-- Real Rwanda-native peer-to-peer agent float rebalancing marketplace
-- (rw.itunda.agents.FloatMarketplaceService) -- see FloatListing.kt's own doc comment.

CREATE TABLE float_listings (
    id              VARCHAR(64)   NOT NULL PRIMARY KEY,
    agent_id        VARCHAR(64)   NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    claimed_amount  DECIMAL(18,2) NOT NULL DEFAULT 0,
    status          VARCHAR(16)   NOT NULL DEFAULT 'OPEN',
    created_at      DATETIME(6)   NOT NULL,
    version         BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_float_listings_agent_id ON float_listings (agent_id);
CREATE INDEX idx_float_listings_status ON float_listings (status);

CREATE TABLE float_transfer_requests (
    id                  VARCHAR(64)   NOT NULL PRIMARY KEY,
    listing_id          VARCHAR(64)   NOT NULL,
    requesting_agent_id VARCHAR(64)   NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    status              VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    transaction_id      VARCHAR(64)   NULL,
    created_at          DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_float_transfer_requests_listing_id ON float_transfer_requests (listing_id);
CREATE INDEX idx_float_transfer_requests_requesting_agent_id ON float_transfer_requests (requesting_agent_id);
