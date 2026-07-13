-- Real Face Pay enrollment -- closes docs/TOSS_PARITY_MATRIX.md's Face Pay row.
-- Deliberately stores no biometric data -- see FacePayEnrollment.kt's own doc comment
-- for why. This table only ever records that Face Pay is turned on for an account.
CREATE TABLE facepay_enrollments (
    id          VARCHAR(64) NOT NULL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL UNIQUE,
    active      BOOLEAN     NOT NULL DEFAULT TRUE,
    enrolled_at DATETIME(6) NOT NULL,
    revoked_at  DATETIME(6)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
