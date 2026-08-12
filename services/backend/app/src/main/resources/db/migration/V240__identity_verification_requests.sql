-- Real "verify/sign in with itunda" partner requests -- see
-- IdentityVerificationRequest.kt's own doc comment for the full sourced account.

CREATE TABLE identity_verification_requests (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    partner_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64),
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_at DATETIME(6) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    responded_at DATETIME(6),
    disclosed_payload_json VARCHAR(2000),
    signature VARCHAR(512),
    version BIGINT NOT NULL DEFAULT 0,
    INDEX idx_identity_verification_requests_partner (partner_id)
);
