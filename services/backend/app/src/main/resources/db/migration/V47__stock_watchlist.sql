-- Real stock watchlist (rw.itunda.stocks.StocksService, 2026-07-19) -- follow a stock
-- without holding it, closing part of "Toss Securities as its own distinct surface".
-- See StockWatchlist.kt's own doc comment.

CREATE TABLE stock_watchlist (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    stock_id    VARCHAR(64) NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    CONSTRAINT uq_stock_watchlist_user_stock UNIQUE (user_id, stock_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_stock_watchlist_user_id ON stock_watchlist (user_id);
