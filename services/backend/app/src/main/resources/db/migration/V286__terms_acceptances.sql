-- Real Toss/Korean-fintech-style 약관 동의 (terms consent) audit trail
-- (rw.itunda.core.domain.TermsAcceptance, 2026-08-18) -- itunda had zero terms-consent
-- tracking anywhere before this; AuthService.register created a real account and a
-- real wallet with no record the user agreed to anything. One immutable row per
-- accepted term per user, written once at registration.

CREATE TABLE terms_acceptances (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    terms_id       VARCHAR(64)  NOT NULL,
    terms_version  VARCHAR(32)  NOT NULL,
    accepted_at    DATETIME(6)  NOT NULL
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_terms_acceptances_user_id ON terms_acceptances (user_id);
