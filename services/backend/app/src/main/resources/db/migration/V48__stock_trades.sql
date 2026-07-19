-- Real immutable trade log (rw.itunda.stocks.StocksService, 2026-07-20) -- purely
-- additive alongside the existing holdings table, backing real historical portfolio
-- value reconstruction. See StockTrade.kt's own doc comment.

CREATE TABLE stock_trades (
    id           VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64) NOT NULL,
    stock_id     VARCHAR(16) NOT NULL,
    type         VARCHAR(8) NOT NULL,
    shares       DECIMAL(18, 4) NOT NULL,
    price        DECIMAL(18, 2) NOT NULL,
    executed_at  DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_stock_trades_user_id ON stock_trades (user_id);
CREATE INDEX idx_stock_trades_user_id_stock_id ON stock_trades (user_id, stock_id);
