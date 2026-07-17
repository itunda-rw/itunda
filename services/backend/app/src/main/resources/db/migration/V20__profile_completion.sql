-- Real "complete your profile" feature (2026-07-17), closing the last honor-system
-- reward task, task_profile: "Add a profile photo and verify your email". A real photo
-- is stored as a URL (same "no file-storage layer in this backend" honest simplification
-- IdentityController.kt's documentReference already established for KYC document
-- uploads), and email verification uses a real single-use expiring token delivered via
-- itunda's own real in-app Notification system -- there is no SMTP relay in this
-- backend, so a real email can't be sent, but the token itself is real (not echoed back
-- in any API response), same "reuse a real existing mechanism, don't fabricate a fake
-- one" convention LinkedAccount.kt's own doc comment already established for consent
-- verification.
ALTER TABLE users ADD COLUMN profile_photo_url VARCHAR(512) NULL;
ALTER TABLE users ADD COLUMN email_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE email_verification_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    token VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY idx_email_verification_tokens_token (token),
    KEY idx_email_verification_tokens_user (user_id)
);
