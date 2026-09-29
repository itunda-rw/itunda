-- Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line,
-- rw.itunda.loans.PostpaidCreditService) -- see PostpaidCreditLine.kt's own doc comment.

CREATE TABLE postpaid_credit_lines (
    id                  VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64)   NOT NULL,
    wallet_id           VARCHAR(64)   NOT NULL,
    credit_limit        DECIMAL(18,2) NOT NULL,
    current_balance     DECIMAL(18,2) NOT NULL DEFAULT 0,
    status              VARCHAR(16)   NOT NULL DEFAULT 'ACTIVE',
    cycle_due_at            DATETIME(6) NULL,
    last_late_fee_accrual_at DATETIME(6) NULL,
    created_at          DATETIME(6)   NOT NULL,
    updated_at          DATETIME(6)   NOT NULL,
    CONSTRAINT uq_postpaid_credit_lines_user_id UNIQUE (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_postpaid_credit_lines_status ON postpaid_credit_lines (status);
