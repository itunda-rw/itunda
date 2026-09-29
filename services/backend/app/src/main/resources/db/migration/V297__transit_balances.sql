-- Real Kigali public-transit stored-value balance (2026-08-27, direct user follow-up
-- after the card-design-picker feature: "after this we will build transit features").
-- See TransitBalance.kt's own doc comment for the full sourced account of Kigali's real
-- Tap&Go fare system (AC Group Ltd, Kigali Bus Services, Royal Express) and the honest
-- boundary this simulates. Column shape mirrors debit_cards/debit_card_transactions
-- (V184__debit_cards.sql) -- one balance row per user, one trip row per real fare tap.

CREATE TABLE transit_balances (
    id         VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id    VARCHAR(64)    NOT NULL,
    balance    DECIMAL(18, 2) NOT NULL DEFAULT 0,
    created_at DATETIME(6)    NOT NULL,
    version    BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_transit_balances_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transit_trips (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id               VARCHAR(64)    NOT NULL,
    operator              VARCHAR(64)    NOT NULL,
    fare                  DECIMAL(18, 2) NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    created_at            DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_transit_trips_user_created ON transit_trips (user_id, created_at);
