-- Real Toss 외환 (foreign exchange) 환율 알림 (exchange rate alert) (2026-08-17) -- set
-- a target rate on a real currency pair and get notified once ForeignCurrencyRateClient's
-- real live rate (open.er-api.com, ECB-sourced) crosses it. Same real target-price-alert
-- shape stock_watchlist's target_price/target_direction/alert_triggered_at columns
-- already establish (V260__stock_price_alert.sql), just for FX instead of equities.

CREATE TABLE exchange_rate_alerts (
    id                  VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64) NOT NULL,
    from_currency       VARCHAR(8)  NOT NULL,
    to_currency         VARCHAR(8)  NOT NULL,
    target_rate         DOUBLE      NOT NULL,
    direction           VARCHAR(8)  NOT NULL,
    alert_triggered_at  DATETIME(6) NULL,
    created_at          DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE UNIQUE INDEX uq_exchange_rate_alerts_user_pair ON exchange_rate_alerts (user_id, from_currency, to_currency);
