-- Real Naver Smart Store-style "알림받기" (follow a store for promotional notices)
-- (rw.itunda.merchant.MerchantFollowService, 2026-07-26). See MerchantFollow.kt's own
-- doc comment for the full account.

CREATE TABLE merchant_follows (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY uq_merchant_follows_user_merchant (user_id, merchant_id),
    INDEX idx_merchant_follows_merchant (merchant_id)
);
