-- Real backend for reconciliation -- previously docs/TOSS_PARITY_MATRIX.md's
-- Operations/Reconciliation row falsely claimed "real (one-sided)" logic aggregating
-- provider-attempt logs by rail/day; no such persisted log existed anywhere in the repo
-- (corrected 2026-07-13). This is that real log: every attempt SimulatedProviderConnector
-- makes, persisted (unlike ProviderHealthTracker's in-memory live-health view, which
-- resets on restart and can't be grouped by day), so a reconciliation report can aggregate
-- by rail and by day after the fact.
CREATE TABLE provider_attempt_log (
    id                 VARCHAR(64)  NOT NULL PRIMARY KEY,
    rail_id            VARCHAR(64)  NOT NULL,
    rail_display_name  VARCHAR(255) NOT NULL,
    success            BOOLEAN      NOT NULL,
    latency_ms         BIGINT       NOT NULL,
    occurred_at        DATETIME(6)  NOT NULL,
    occurred_date      DATE         NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_provider_attempt_log_rail_date ON provider_attempt_log (rail_id, occurred_date);
CREATE INDEX idx_provider_attempt_log_date ON provider_attempt_log (occurred_date);
