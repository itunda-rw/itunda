-- Real KYC submission/review workflow -- previously did not exist at all (see
-- docs/TOSS_PARITY_MATRIX.md's Compliance row, corrected 2026-07-13 after a repo-wide grep
-- found no /identity/* route anywhere). document_reference is a demo-mode stand-in for an
-- uploaded ID scan; this backend has no file-storage layer.
CREATE TABLE kyc_submissions (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    user_id             VARCHAR(64)  NOT NULL,
    document_type       VARCHAR(32)  NOT NULL,
    document_number     VARCHAR(32)  NOT NULL,
    document_reference  VARCHAR(255) NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    submitted_at        TIMESTAMP    NOT NULL,
    reviewed_by         VARCHAR(64),
    reviewed_at         TIMESTAMP    NULL,
    decision_reason     VARCHAR(255)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_kyc_submissions_user_id ON kyc_submissions (user_id);
CREATE INDEX idx_kyc_submissions_status ON kyc_submissions (status);
