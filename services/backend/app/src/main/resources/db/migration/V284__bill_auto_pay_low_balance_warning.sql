-- Real Kakao Bank card-billing-amount-notification-style proactive low-balance warning
-- (rw.itunda.bills.BillAutoPayProcessor, Section 178, 2026-08-18): BillAutoPayProcessor
-- previously only ever told a user AFTER an auto-pay attempt had already failed. This
-- column is the once-per-bill dedup guard (same reasoning as last_paid_bill_id) for the
-- new proactive warning sent before a doomed attempt is even made.

ALTER TABLE bill_auto_pay_settings ADD COLUMN last_low_balance_warned_bill_id VARCHAR(64) NULL;
