-- Real versioned migration replacing Hibernate's `ddl-auto: update` (2026-07-11 fix --
-- see docs/ARCHITECTURE.md and TOSS_PARITY_MATRIX.md's "Non-Negotiable Gates Before
-- Real Money"). Every table/column here is transcribed directly from the @Entity
-- classes in core/src/main/kotlin/rw/itunda/core/domain/ and core/.../idempotency/ --
-- one table per @Entity, one column per @Column, nullability taken literally from
-- each @Column's `nullable` attribute (defaulting to NULL per the JPA spec where an
-- attribute isn't set explicitly, not inferred from the Kotlin type). Indexes added
-- for every column a Spring Data `findByX`/`findByXAndY` repository method queries on.
--
-- Not runtime-verified against a live MySQL instance (no Docker daemon in this
-- environment) -- `ddl-auto` is set to `validate` alongside this migration
-- specifically so any mismatch surfaces loudly at startup instead of silently.

CREATE TABLE users (
    id            VARCHAR(64)  NOT NULL PRIMARY KEY,
    phone_number  VARCHAR(32)  NOT NULL,
    email         VARCHAR(255) NULL,
    first_name    VARCHAR(255) NOT NULL,
    last_name     VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    kyc_verified  BOOLEAN      NOT NULL,
    credit_score  INT          NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    CONSTRAINT uq_users_phone_number UNIQUE (phone_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE wallets (
    id                 VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id            VARCHAR(64)    NOT NULL,
    account_number     VARCHAR(32)    NOT NULL,
    account_name       VARCHAR(255)   NOT NULL,
    type               VARCHAR(16)    NOT NULL,
    balance            DECIMAL(18, 2) NOT NULL,
    available_balance  DECIMAL(18, 2) NOT NULL,
    currency           VARCHAR(8)     NOT NULL,
    is_active          BOOLEAN        NOT NULL,
    created_at         DATETIME(6)    NOT NULL,
    CONSTRAINT uq_wallets_account_number UNIQUE (account_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_wallets_user_id ON wallets (user_id);

CREATE TABLE ledger_accounts (
    id      VARCHAR(64)    NOT NULL PRIMARY KEY,
    name    VARCHAR(255)   NOT NULL,
    balance DECIMAL(18, 2) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ledger_entries (
    id             VARCHAR(64)    NOT NULL PRIMARY KEY,
    transaction_id VARCHAR(64)    NOT NULL,
    account_id     VARCHAR(64)    NOT NULL,
    account_type   VARCHAR(32)    NOT NULL,
    direction      VARCHAR(8)     NOT NULL,
    amount         DECIMAL(18, 2) NOT NULL,
    currency       VARCHAR(8)     NOT NULL,
    balance_after  DECIMAL(18, 2) NOT NULL,
    memo           VARCHAR(255)   NOT NULL,
    created_at     DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX idx_ledger_entries_account_id_created_at ON ledger_entries (account_id, created_at);

CREATE TABLE contacts (
    id           VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64)  NOT NULL,
    name         VARCHAR(255) NOT NULL,
    bank         VARCHAR(255) NOT NULL,
    acc          VARCHAR(255) NOT NULL,
    phone_number VARCHAR(32)  NOT NULL,
    color        VARCHAR(16)  NOT NULL,
    letter       VARCHAR(4)   NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_contacts_user_id ON contacts (user_id);

CREATE TABLE loan_accounts (
    id            VARCHAR(64)      NOT NULL PRIMARY KEY,
    user_id       VARCHAR(64)      NOT NULL,
    wallet_id     VARCHAR(64)      NOT NULL,
    offer_id      VARCHAR(40)      NOT NULL,
    principal     DECIMAL(18, 2)   NOT NULL,
    outstanding   DECIMAL(18, 2)   NOT NULL,
    interest_rate DOUBLE PRECISION NOT NULL,
    status        VARCHAR(16)      NOT NULL,
    disbursed_at  DATETIME(6)      NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_loan_accounts_user_id ON loan_accounts (user_id);

CREATE TABLE transactions (
    id                 VARCHAR(64)    NOT NULL PRIMARY KEY,
    reference_number   VARCHAR(64)    NOT NULL,
    sender_id          VARCHAR(64)    NOT NULL,
    recipient_id       VARCHAR(64)    NOT NULL,
    from_wallet_id     VARCHAR(64)    NULL,
    to_wallet_id       VARCHAR(64)    NULL,
    amount             DECIMAL(18, 2) NOT NULL,
    fee                DECIMAL(18, 2) NOT NULL,
    currency           VARCHAR(8)     NOT NULL,
    type               VARCHAR(16)    NOT NULL,
    status             VARCHAR(16)    NOT NULL,
    description        VARCHAR(255)   NOT NULL,
    channel            VARCHAR(64)    NULL,
    provider_reference VARCHAR(80)    NULL,
    completed_at       DATETIME(6)    NULL,
    created_at         DATETIME(6)    NOT NULL,
    CONSTRAINT uq_transactions_reference_number UNIQUE (reference_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_transactions_sender_id ON transactions (sender_id);
CREATE INDEX idx_transactions_recipient_id ON transactions (recipient_id);

CREATE TABLE savings_goals (
    id                    VARCHAR(64)      NOT NULL PRIMARY KEY,
    user_id               VARCHAR(64)      NOT NULL,
    wallet_id             VARCHAR(64)      NOT NULL,
    name                  VARCHAR(255)     NOT NULL,
    target_amount         DECIMAL(18, 2)   NOT NULL,
    current_amount        DECIMAL(18, 2)   NOT NULL,
    monthly_contribution  DECIMAL(18, 2)   NOT NULL,
    interest_rate         DOUBLE PRECISION NOT NULL,
    target_date           VARCHAR(32)      NULL,
    category              VARCHAR(32)      NULL,
    status                VARCHAR(16)      NOT NULL,
    color                 VARCHAR(16)      NULL,
    created_at            DATETIME(6)      NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_savings_goals_user_id ON savings_goals (user_id);

CREATE TABLE interest_jars (
    user_id           VARCHAR(64)    NOT NULL PRIMARY KEY,
    wallet_id         VARCHAR(64)    NOT NULL,
    balance           DECIMAL(18, 2) NOT NULL,
    rate              DOUBLE PRECISION NOT NULL,
    earned_this_month DECIMAL(18, 2) NOT NULL,
    earned_total      DECIMAL(18, 2) NOT NULL,
    last_paid_at      DATETIME(6)    NOT NULL,
    next_payout_at    DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE insurance_policies (
    id                 VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id            VARCHAR(64)    NOT NULL,
    plan_id            VARCHAR(64)    NOT NULL,
    plan_name          VARCHAR(255)   NOT NULL,
    category           VARCHAR(255)   NOT NULL,
    status             VARCHAR(32)    NOT NULL,
    start_date         DATE           NOT NULL,
    end_date           DATE           NOT NULL,
    monthly_premium    DECIMAL(18, 2) NOT NULL,
    next_payment_date  DATE           NOT NULL,
    policy_number      VARCHAR(64)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_insurance_policies_user_id ON insurance_policies (user_id);

CREATE TABLE notifications (
    id         VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    type       VARCHAR(64)  NOT NULL,
    title      VARCHAR(255) NOT NULL,
    body       VARCHAR(255) NOT NULL,
    is_read    BOOLEAN      NOT NULL,
    created_at DATETIME(6)  NOT NULL,
    data_json  TEXT         NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_notifications_user_id_created_at ON notifications (user_id, created_at);

CREATE TABLE holdings (
    id        VARCHAR(64)    NOT NULL PRIMARY KEY,
    user_id   VARCHAR(64)    NOT NULL,
    wallet_id VARCHAR(64)    NOT NULL,
    stock_id  VARCHAR(16)    NOT NULL,
    shares    DECIMAL(18, 4) NOT NULL,
    avg_price DECIMAL(18, 4) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_holdings_user_id ON holdings (user_id);
CREATE INDEX idx_holdings_user_id_stock_id ON holdings (user_id, stock_id);

CREATE TABLE idempotency_records (
    record_key    VARCHAR(300) NOT NULL PRIMARY KEY,
    body_json     TEXT         NOT NULL,
    status_code   INT          NOT NULL,
    response_json TEXT         NOT NULL,
    created_at    DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- Seed data (the 8 canonical ledger clearing accounts, the demo user, wallets, etc.)
-- deliberately stays out of this migration -- app/.../SeedDataRunner.kt already seeds
-- all of it idempotently (existsById/findByUserId guards) on every boot, immediately
-- after this migration runs. Duplicating it here would just be two sources of truth
-- for the same rows.
