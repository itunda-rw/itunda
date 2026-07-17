-- Real B2B payroll (rw.itunda.merchant.PayrollService, 2026-07-17). See
-- core/.../domain/Payroll.kt for the entities this transcribes column-for-column,
-- same discipline as V2__merchant.sql.

CREATE TABLE payroll_employees (
    id                VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id       VARCHAR(64)    NOT NULL,
    employee_user_id  VARCHAR(64)    NOT NULL,
    employee_name     VARCHAR(255)   NOT NULL,
    salary_amount     DECIMAL(18, 2) NOT NULL,
    active            BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at        DATETIME(6)    NOT NULL,
    CONSTRAINT uq_payroll_employees_merchant_employee UNIQUE (merchant_id, employee_user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE payroll_runs (
    id                    VARCHAR(64)    NOT NULL PRIMARY KEY,
    merchant_id           VARCHAR(64)    NOT NULL,
    ledger_transaction_id VARCHAR(64)    NOT NULL,
    total_amount          DECIMAL(18, 2) NOT NULL,
    employee_count        INT            NOT NULL,
    created_at            DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_payroll_runs_merchant_id_created_at ON payroll_runs (merchant_id, created_at);

CREATE TABLE payslips (
    id               VARCHAR(64)    NOT NULL PRIMARY KEY,
    payroll_run_id   VARCHAR(64)    NOT NULL,
    employee_user_id VARCHAR(64)    NOT NULL,
    employee_name    VARCHAR(255)   NOT NULL,
    amount           DECIMAL(18, 2) NOT NULL,
    transaction_id   VARCHAR(64)    NOT NULL,
    created_at       DATETIME(6)    NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_payslips_payroll_run_id ON payslips (payroll_run_id);
