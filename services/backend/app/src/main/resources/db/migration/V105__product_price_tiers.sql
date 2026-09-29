-- Real bulk/wholesale pricing (rw.itunda.merchant.MerchantProductService.setPriceTiers,
-- 2026-07-25) -- closes the gap named in Baemin's own real 배민상회 B2B supplies
-- marketplace research: the real differentiator between a B2B wholesale listing and a
-- normal retail one is that price genuinely depends on quantity, not a separate catalog.

CREATE TABLE product_price_tiers (
    id           VARCHAR(64)    NOT NULL PRIMARY KEY,
    product_id   VARCHAR(64)    NOT NULL,
    min_quantity INT            NOT NULL,
    unit_price   DECIMAL(18, 2) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_product_price_tiers_product_id ON product_price_tiers (product_id);
