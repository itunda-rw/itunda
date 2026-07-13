-- Real monthly spending budgets/limits -- closes docs/TOSS_PARITY_MATRIX.md's Spending
-- row's own named gap ("Budgeting/limits still target").
CREATE TABLE spending_budgets (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    category       VARCHAR(32),
    monthly_limit  DECIMAL(18,2) NOT NULL,
    month          VARCHAR(7)   NOT NULL,
    notified_near  BOOLEAN      NOT NULL DEFAULT FALSE,
    notified_over  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     DATETIME(6)  NOT NULL,
    updated_at     DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_spending_budgets_user_month ON spending_budgets (user_id, month);
