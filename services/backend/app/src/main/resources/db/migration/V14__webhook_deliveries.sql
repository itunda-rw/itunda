-- Real persistent webhook retry queue -- previously docs/PAYMENTS.md named single-attempt
-- delivery as the honest gap versus Toss Payments' real documented scheme (retries a
-- failing endpoint up to 7 times over exponentially increasing intervals, 1 to 4096
-- minutes). A persisted row, not an in-memory retry loop, for the same reason
-- outbox_events exists: a multi-hour (up to ~2.8-day) retry window can't survive a
-- process restart otherwise.
CREATE TABLE webhook_deliveries (
    id               VARCHAR(64)  NOT NULL PRIMARY KEY,
    webhook_url      VARCHAR(2048) NOT NULL,
    payload          TEXT         NOT NULL,
    attempt_count    INT          NOT NULL DEFAULT 0,
    status           VARCHAR(16)  NOT NULL,
    next_attempt_at  DATETIME(6)  NOT NULL,
    last_error       VARCHAR(500),
    created_at       DATETIME(6)  NOT NULL,
    delivered_at     DATETIME(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_webhook_deliveries_status_next_attempt ON webhook_deliveries (status, next_attempt_at);
