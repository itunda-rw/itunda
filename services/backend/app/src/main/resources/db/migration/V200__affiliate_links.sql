-- Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program
-- (rw.itunda.commerce.AffiliateService) -- see AffiliateLink.kt/AffiliateCommission.kt's
-- own doc comments.

CREATE TABLE affiliate_links (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    product_id   VARCHAR(64) NOT NULL,
    code         VARCHAR(16) NOT NULL,
    click_count  BIGINT      NOT NULL DEFAULT 0,
    created_at   DATETIME(6) NOT NULL,
    CONSTRAINT uq_affiliate_links_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_affiliate_links_user_id ON affiliate_links (user_id);

CREATE TABLE affiliate_commissions (
    id                    VARCHAR(64)   NOT NULL PRIMARY KEY,
    link_id               VARCHAR(64)   NOT NULL,
    referrer_id           VARCHAR(64)   NOT NULL,
    order_id              VARCHAR(64)   NOT NULL,
    buyer_id              VARCHAR(64)   NOT NULL,
    commission_amount     DECIMAL(18,2) NOT NULL,
    payout_transaction_id VARCHAR(64)   NOT NULL,
    created_at            DATETIME(6)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_affiliate_commissions_referrer_id ON affiliate_commissions (referrer_id);
