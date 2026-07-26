-- Real Toss 예약송금 (scheduled/reserved one-time transfer) equivalent
-- (rw.itunda.p2p.ScheduledTransferService, 2026-07-27). See ScheduledTransfer.kt's own
-- doc comment for the full sourced account -- genuinely distinct from auto_transfers
-- (recurring): this is always exactly one execution on a single future date.

CREATE TABLE scheduled_transfers (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    wallet_id VARCHAR(64) NOT NULL,
    recipient_identifier VARCHAR(64) NOT NULL,
    recipient_name VARCHAR(255) NOT NULL,
    amount DECIMAL(18, 2) NOT NULL,
    description VARCHAR(255) NULL,
    scheduled_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    executed_at DATETIME(6) NULL,
    transaction_id VARCHAR(64) NULL,
    failure_reason VARCHAR(255) NULL,
    cancelled_at DATETIME(6) NULL,
    INDEX idx_scheduled_transfers_user (user_id),
    INDEX idx_scheduled_transfers_status_date (status, scheduled_date)
);
