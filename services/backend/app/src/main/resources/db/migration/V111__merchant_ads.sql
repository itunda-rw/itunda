-- Real radius-targeted local business ads (rw.itunda.merchant.MerchantAdService,
-- 2026-07-26) -- closes Karrot's own real, sourced "반경 타기팅" (radius targeting)
-- feature. See MerchantAd.kt's own doc comment.

CREATE TABLE merchant_ads (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    merchant_id    VARCHAR(64)  NOT NULL,
    title          VARCHAR(100) NOT NULL,
    description    VARCHAR(300),
    radius_meters  INT          NOT NULL,
    active_until   DATETIME(6)  NOT NULL,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL,
    CONSTRAINT uq_merchant_ads_merchant_id UNIQUE (merchant_id),
    INDEX idx_merchant_ads_active_until (active_until)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
