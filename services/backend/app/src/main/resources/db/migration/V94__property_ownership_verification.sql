-- Real property ownership verification (2026-07-25) -- closes a genuine, previously-
-- unaddressed fraud gap this session's Karrot Real Estate (당근부동산) research surfaced:
-- itunda's PropertyListing had zero ownership proof of any kind, unlike Karrot's real
-- product, which made per-district-registry-document verification mandatory precisely
-- because of listing fraud. Rwanda has no publicly documented land-registry number format
-- to structurally pre-validate the way DemoNidaVerificationService does for National IDs,
-- so this is deliberately document-upload + human-review only -- no fabricated auto-check
-- pretending to validate against a registry this backend has no real access to. Mirrors
-- kyc_submissions' real submit/queue/decide shape exactly, now that UploadController
-- (2026-07-24) gives this a real document to point at instead of a demo-mode reference
-- string.
ALTER TABLE property_listings ADD COLUMN ownership_verification_status VARCHAR(16) NOT NULL DEFAULT 'NONE';

CREATE TABLE property_ownership_submissions (
    id                  VARCHAR(64)  NOT NULL PRIMARY KEY,
    listing_id          VARCHAR(64)  NOT NULL,
    user_id             VARCHAR(64)  NOT NULL,
    document_url        VARCHAR(255) NOT NULL,
    status              VARCHAR(16)  NOT NULL,
    submitted_at        TIMESTAMP    NOT NULL,
    reviewed_by         VARCHAR(64),
    reviewed_at         TIMESTAMP    NULL,
    decision_reason     VARCHAR(255)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_property_ownership_submissions_listing_id ON property_ownership_submissions (listing_id);
CREATE INDEX idx_property_ownership_submissions_status ON property_ownership_submissions (status);
