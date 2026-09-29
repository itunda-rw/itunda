-- KYC decisions control platform capabilities and require one definitive outcome.
ALTER TABLE kyc_submissions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
