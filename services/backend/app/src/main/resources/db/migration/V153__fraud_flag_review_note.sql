ALTER TABLE fraud_flags
    ADD COLUMN review_note VARCHAR(2000) NULL AFTER reviewed_at;
