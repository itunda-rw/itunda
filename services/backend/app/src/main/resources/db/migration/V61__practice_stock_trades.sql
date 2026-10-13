-- Isolated paper-trading history; never shares the real-money stock_trades table.
CREATE TABLE practice_stock_trades (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    stock_id    VARCHAR(16) NOT NULL,
    type        VARCHAR(8) NOT NULL,
    shares      DECIMAL(18, 4) NOT NULL,
    price       DECIMAL(18, 2) NOT NULL,
    executed_at DATETIME(6) NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_practice_stock_trades_user_time ON practice_stock_trades (user_id, executed_at);
CREATE INDEX idx_practice_stock_trades_user_stock ON practice_stock_trades (user_id, stock_id);
