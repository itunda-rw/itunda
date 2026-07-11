-- Real transactional outbox table (2026-07-11). See core/.../events/OutboxEvent.kt
-- and EventPublisher.kt for the full reasoning -- this replaces the earlier
-- "publish directly to Kafka after commit, log and drop on failure" pattern with a
-- real at-least-once one: EventPublisher writes rows here as part of the caller's
-- own transaction, OutboxRelay polls and publishes them to Kafka.

CREATE TABLE outbox_events (
    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    topic        VARCHAR(100) NOT NULL,
    message_key  VARCHAR(100) NOT NULL,
    payload      TEXT         NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    processed_at DATETIME(6)  NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_outbox_events_processed_at_created_at ON outbox_events (processed_at, created_at);
