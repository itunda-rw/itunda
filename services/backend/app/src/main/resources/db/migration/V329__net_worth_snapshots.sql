CREATE TABLE net_worth_snapshots (
    id           VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id      VARCHAR(64)   NOT NULL,
    liquid_total DECIMAL(18,2) NOT NULL,
    captured_at  TIMESTAMP     NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_net_worth_snapshots_user_id_captured_at ON net_worth_snapshots (user_id, captured_at);
