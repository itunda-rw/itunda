-- Real demo KYB pre-check (rw.itunda.identity.DemoKybVerificationService, 2026-07-17).
-- See core/.../domain/Merchant.kt's own doc comment for why this reuses the existing
-- KYC submission/review pipeline rather than a new one.

ALTER TABLE merchants ADD COLUMN kyb_verified BOOLEAN NOT NULL DEFAULT FALSE;
