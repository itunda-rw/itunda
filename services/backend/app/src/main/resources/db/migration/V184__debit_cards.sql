CREATE TABLE debit_cards (
    id             VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)    NOT NULL,
    last_4         VARCHAR(4)     NOT NULL,
    daily_limit    DECIMAL(18, 2) NOT NULL,
    monthly_limit  DECIMAL(18, 2) NOT NULL,
    frozen         BOOLEAN        NOT NULL DEFAULT FALSE,
    issued_at      DATETIME(6)    NOT NULL,
    version        BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT uq_debit_cards_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE debit_card_transactions (
    id                   VARCHAR(64)    NOT NULL PRIMARY KEY,
    card_id              VARCHAR(64)    NOT NULL,
    user_id              VARCHAR(64)    NOT NULL,
    amount               DECIMAL(18, 2) NOT NULL,
    merchant_name        VARCHAR(200)   NOT NULL,
    ledger_transaction_id VARCHAR(64)   NOT NULL,
    created_at           DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_debit_card_transactions_card_created ON debit_card_transactions (card_id, created_at);
