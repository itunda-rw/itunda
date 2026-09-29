-- Real gap found live (2026-08-10) -- see VupLoan.kt's own doc comment: itunda had no
-- pre-due repayment reminder anywhere, and its overdue check never notified the user
-- (server log only). MicroSave/Access to Finance Rwanda's own 2026 research: "only 14%
-- of borrowers repay loans digitally" in Rwanda -- an app that never reminds you
-- explains part of that.
ALTER TABLE vup_loans
    ADD COLUMN reminder_sent_at DATETIME(6) NULL;
