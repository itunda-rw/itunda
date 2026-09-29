-- Real KakaoPay-style 정산 reminder nudges -- see SplitBillReminderScheduler's own doc
-- comment. NULL means never reminded yet.

ALTER TABLE split_bill_participants ADD COLUMN last_reminder_sent_at DATETIME(6) NULL;
