-- Real KakaoTalk-style 선물하기 기프티콘 (mobile gift voucher), redeemable at one
-- specific real itunda merchant (rw.itunda.gift.GiftVoucherService, 2026-07-26). See
-- GiftVoucher.kt's own doc comment for why this is distinct from the existing
-- money-gift `gifts` table.

CREATE TABLE gift_vouchers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    purchaser_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    merchant_id VARCHAR(64) NOT NULL,
    merchant_product_id VARCHAR(64) NULL,
    product_name_snapshot VARCHAR(255) NULL,
    amount DECIMAL(18, 2) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    hold_transaction_id VARCHAR(64) NOT NULL,
    redeem_transaction_id VARCHAR(64) NULL,
    refund_transaction_id VARCHAR(64) NULL,
    expires_at DATETIME(6) NOT NULL,
    redeemed_at DATETIME(6) NULL,
    extended TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_gift_vouchers_purchaser (purchaser_id),
    INDEX idx_gift_vouchers_recipient (recipient_id),
    INDEX idx_gift_vouchers_merchant (merchant_id),
    INDEX idx_gift_vouchers_status_expires (status, expires_at)
);
