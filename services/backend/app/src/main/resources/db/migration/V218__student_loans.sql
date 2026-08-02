-- Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
-- StudentLoan.kt's own doc comment for the full sourced account. Rwanda has run a
-- national student-loan-and-bursary scheme since Law No. 44/2015, administered by
-- BRD since an October 2016 MINEDUC agreement. Real scale: Rwf 221.85 billion
-- disbursed to 139,925 students (through mid-2023), fixed interest rates of 11%
-- undergraduate / 12% postgraduate, repayment terms of 2-10 years (brd.rw).
-- Eligibility runs through Financial Means Testing (FMT), distinct from Ubudehe.
-- BRD has publicly acknowledged real collection difficulty: only 13.3% repayment
-- compliance by mid-2023 (IGIHE), Rwf 7.2bn recovered in 2024, Rwf 34.7bn cumulative
-- since 2016 (KT Press, May 2025). Sources: brd.rw, IGIHE, KT Press.
--
-- Honest v1 limitation: declared_annual_household_income is self-declared by the
-- user, not verified against BRD's real Financial Means Testing (FMT) process, which
-- this backend has no access to.

CREATE TABLE student_loans (
    id                                  VARCHAR(64)   NOT NULL PRIMARY KEY,
    user_id                             VARCHAR(64)   NOT NULL,
    level                               VARCHAR(16)   NOT NULL,
    declared_annual_household_income    DECIMAL(18,2) NOT NULL,
    principal_amount                    DECIMAL(18,2) NOT NULL,
    outstanding_balance                 DECIMAL(18,2) NOT NULL,
    interest_rate                       DOUBLE        NOT NULL,
    status                              VARCHAR(16)   NOT NULL DEFAULT 'REQUESTED',
    applied_at                          DATETIME(6)   NOT NULL,
    disbursed_at                        DATETIME(6)   NULL,
    expected_graduation_date            DATE          NOT NULL,
    grace_ends_at                       DATE          NULL,
    version                             BIGINT        NOT NULL DEFAULT 0
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_student_loans_user_id ON student_loans (user_id);
CREATE INDEX idx_student_loans_user_status ON student_loans (user_id, status);
CREATE INDEX idx_student_loans_status_grace_ends_at ON student_loans (status, grace_ends_at);
