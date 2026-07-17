-- Real digital identity/signing certificate (rw.itunda.certificate, 2026-07-17),
-- closing the "Toss 인증서" gap. See core/.../domain/Certificate.kt for the entity
-- this transcribes column-for-column, same discipline as V2__merchant.sql. No private-
-- key column exists anywhere here -- see Certificate.kt's own doc comment for why.

CREATE TABLE certificates (
    id             VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id        VARCHAR(64)  NOT NULL,
    serial_number  VARCHAR(64)  NOT NULL,
    public_key     VARCHAR(100) NOT NULL,
    algorithm      VARCHAR(16)  NOT NULL,
    status         VARCHAR(16)  NOT NULL,
    issued_at      DATETIME(6)  NOT NULL,
    expires_at     DATETIME(6)  NOT NULL,
    revoked_at     DATETIME(6)  NULL,
    CONSTRAINT uq_certificates_serial_number UNIQUE (serial_number)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
CREATE INDEX idx_certificates_user_id_status ON certificates (user_id, status);
