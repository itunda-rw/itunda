-- Real KakaoTalk-style "선물하기" (gift money) sent within an existing 1:1 chat
-- conversation (rw.itunda.gift.GiftService, 2026-07-20). See Gift.kt's own doc
-- comment for why this is a real escrow-then-claim flow, not an instant transfer.

CREATE TABLE gifts (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    sender_id VARCHAR(64) NOT NULL,
    recipient_id VARCHAR(64) NOT NULL,
    conversation_id VARCHAR(64) NOT NULL,
    message_id VARCHAR(64) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    note VARCHAR(500) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    hold_transaction_id VARCHAR(64) NOT NULL,
    claim_transaction_id VARCHAR(64) NULL,
    expires_at DATETIME(6) NOT NULL,
    claimed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_gifts_recipient (recipient_id),
    INDEX idx_gifts_sender (sender_id),
    INDEX idx_gifts_status_expires (status, expires_at)
);
