-- Restaurant-specific Eats stamps are order-scoped and separate from merchant payment loyalty points.
CREATE TABLE eats_stamp_campaigns (
    id VARCHAR(64) PRIMARY KEY,
    restaurant_id VARCHAR(64) NOT NULL,
    name VARCHAR(100) NOT NULL,
    stamps_required INTEGER NOT NULL CHECK (stamps_required BETWEEN 2 AND 50),
    reward_description VARCHAR(240) NOT NULL,
    reward_discount_amount NUMERIC(18, 2),
    starts_at TIMESTAMP WITH TIME ZONE NOT NULL,
    ends_at TIMESTAMP WITH TIME ZONE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_eats_stamp_campaign_restaurant
        FOREIGN KEY (restaurant_id) REFERENCES merchants(id),
    CONSTRAINT chk_eats_stamp_campaign_dates
        CHECK (ends_at IS NULL OR ends_at > starts_at),
    CONSTRAINT chk_eats_stamp_campaign_discount
        CHECK (reward_discount_amount IS NULL OR reward_discount_amount > 0)
);

CREATE INDEX idx_eats_stamp_campaign_restaurant_active
    ON eats_stamp_campaigns (restaurant_id, active, starts_at, ends_at);

CREATE TABLE eats_stamp_earnings (
    id VARCHAR(64) PRIMARY KEY,
    campaign_id VARCHAR(64) NOT NULL REFERENCES eats_stamp_campaigns(id),
    restaurant_id VARCHAR(64) NOT NULL REFERENCES merchants(id),
    buyer_id VARCHAR(64) NOT NULL,
    order_id VARCHAR(64) NOT NULL REFERENCES eats_orders(id),
    earning_day DATE NOT NULL,
    earned_at TIMESTAMP WITH TIME ZONE NOT NULL,
    reversed_at TIMESTAMP WITH TIME ZONE,
    reversal_reason VARCHAR(240),
    CONSTRAINT uq_eats_stamp_campaign_order UNIQUE (campaign_id, order_id),
    CONSTRAINT uq_eats_stamp_restaurant_buyer_day UNIQUE (restaurant_id, buyer_id, earning_day)
);

CREATE INDEX idx_eats_stamp_earnings_progress
    ON eats_stamp_earnings (campaign_id, buyer_id, reversed_at);

CREATE INDEX idx_eats_stamp_earnings_order
    ON eats_stamp_earnings (order_id, reversed_at);

CREATE TABLE eats_stamp_rewards (
    id VARCHAR(64) PRIMARY KEY,
    campaign_id VARCHAR(64) NOT NULL REFERENCES eats_stamp_campaigns(id),
    restaurant_id VARCHAR(64) NOT NULL REFERENCES merchants(id),
    buyer_id VARCHAR(64) NOT NULL,
    stamp_threshold INTEGER NOT NULL CHECK (stamp_threshold > 0),
    reward_description VARCHAR(240) NOT NULL,
    discount_amount NUMERIC(18, 2),
    status VARCHAR(16) NOT NULL DEFAULT 'AVAILABLE'
        CHECK (status IN ('AVAILABLE', 'REDEEMED', 'EXPIRED', 'REVOKED')),
    issued_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE,
    redeemed_at TIMESTAMP WITH TIME ZONE,
    redeemed_order_id VARCHAR(64) REFERENCES eats_orders(id),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uq_eats_stamp_reward_milestone UNIQUE (campaign_id, buyer_id, stamp_threshold),
    CONSTRAINT chk_eats_stamp_reward_discount
        CHECK (discount_amount IS NULL OR discount_amount > 0),
    CONSTRAINT chk_eats_stamp_reward_redemption
        CHECK ((status <> 'REDEEMED') OR (redeemed_at IS NOT NULL AND redeemed_order_id IS NOT NULL))
);

CREATE INDEX idx_eats_stamp_rewards_buyer_status
    ON eats_stamp_rewards (buyer_id, status, issued_at DESC);
