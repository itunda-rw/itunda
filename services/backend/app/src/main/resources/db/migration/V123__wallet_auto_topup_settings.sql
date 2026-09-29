-- Real Naver Pay Money 자동충전 (auto-charge) equivalent (rw.itunda.wallet.AutoTopUpService,
-- 2026-07-26) -- a standing per-wallet rule that auto-pulls a configured top-up amount
-- from a user's already-linked external account once their MAIN wallet balance drops
-- below a user-set threshold.

CREATE TABLE wallet_auto_topup_settings (
    id                 VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id            VARCHAR(64) NOT NULL,
    wallet_id          VARCHAR(64) NOT NULL,
    linked_account_id  VARCHAR(64) NOT NULL,
    enabled            BOOLEAN     NOT NULL DEFAULT TRUE,
    threshold_amount   DECIMAL(18, 2) NOT NULL,
    top_up_amount      DECIMAL(18, 2) NOT NULL,
    daily_trigger_cap  INT         NOT NULL DEFAULT 3,
    triggers_today     INT         NOT NULL DEFAULT 0,
    last_trigger_date  DATE        NULL,
    last_triggered_at  DATETIME(6) NULL,
    created_at         DATETIME(6) NOT NULL,
    updated_at         DATETIME(6) NOT NULL,
    CONSTRAINT uq_wallet_auto_topup_settings_wallet UNIQUE (wallet_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_wallet_auto_topup_settings_user_id ON wallet_auto_topup_settings (user_id);
