-- Real phone verification at registration (rw.itunda.auth.AuthService, 2026-07-26) --
-- closes docs/DESIGN_REFERENCES.md's own recommendation ("Add a real, honest
-- phone-verification step at registration ... mirror requestEmailVerification/
-- confirmEmailVerification field-for-field"). Same real single-use expiring token
-- shape as email_verification_tokens (V17-era), delivered via itunda's own real in-app
-- Notification system since there is no real SMS relay in this backend -- the code
-- itself is real (a random 6-digit OTP, not echoed back in any API response), same
-- "reuse a real existing mechanism, don't fabricate a fake one" convention email
-- verification already established.

ALTER TABLE users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE phone_verification_tokens (
    id VARCHAR(64) PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    token VARCHAR(64) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    UNIQUE KEY idx_phone_verification_tokens_token (token),
    KEY idx_phone_verification_tokens_user (user_id)
);
