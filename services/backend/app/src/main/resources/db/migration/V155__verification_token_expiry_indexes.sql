-- The cleanup job deletes expired verification challenges by expiry time.  Keep that
-- retention work index-backed as the authentication tables grow.
CREATE INDEX idx_email_verification_tokens_expires_at ON email_verification_tokens (expires_at);
CREATE INDEX idx_phone_verification_tokens_expires_at ON phone_verification_tokens (expires_at);
