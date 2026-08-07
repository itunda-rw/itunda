-- Real Keystore/Secure-Enclave-signed-challenge device verification (item 246) --
-- closes docs/DESIGN_REFERENCES.md Section 12 recommendation #2: replaces password
-- re-entry for device step-up with a real cryptographic proof (ECDSA P-256), matching
-- Toss's own real 토스인증서 architecture instead of trusting a local-only biometric
-- success (see rw.itunda.core.identity.NIDABiometricAuth's own honest doc comment on
-- why that alone was never sufficient).
--
-- public_key stores the raw uncompressed P-256 point (0x04 || X || Y, 65 bytes),
-- base64-encoded -- the same wire format both Android Keystore's ECPublicKey.w and
-- iOS's SecKeyCopyExternalRepresentation produce for an EC key, so no per-platform
-- conversion is needed on either client.

ALTER TABLE trusted_devices
    ADD COLUMN public_key VARCHAR(200) NULL;
