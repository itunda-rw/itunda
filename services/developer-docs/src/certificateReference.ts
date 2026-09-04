// Source of truth: services/backend/core/src/main/kotlin/rw/itunda/core/domain/
// Certificate.kt's own doc comment (the authoritative statement of this feature's real,
// honest legal scope) plus CertificateController.kt/CertificateService.kt for the
// request/response shapes. No internal docs/CERTIFICATE.md exists yet -- everything below
// was read directly from that code. Keep in sync the same way: if those files change,
// especially Certificate.kt's own scope disclosure, update this content first.
export const certificateReferenceMarkdown = `
## Overview

Any third party who receives a document or message signed with an itunda user's real
Ed25519 certificate can verify that signature here — no account, no API key, no
registration. This is the ONLY itunda partner-facing API that needs no authentication at
all, because that's the whole point of public-key verification: it never requires a
secret credential.

**Real, honest legal scope — read this before integrating**: itunda's certificate is a
genuine Ed25519 keypair issued only after real phone + ID verification (itunda's own
equivalent of Toss's real 인증서 product), and signature verification against it is real
cryptographic math, not simulated. But **itunda holds no accredited
certification-authority status with the Rwandan government** — a signature made with this
certificate has no legal standing outside itunda's own systems. If your use case depends on
a legally-binding electronic signature, this API confirms "itunda's own records say this
user signed this," not "this is a legally recognized signature" in any jurisdiction.

## Verify a signature

\`\`\`
POST /api/v1/certificate/verify
Content-Type: application/json

{ "serialNumber": "...", "payload": "the exact original text that was signed", "signature": "<base64 Ed25519 signature>" }
\`\`\`

\`\`\`
{ "success": true, "signatureValid": true, "certificateStatus": "ACTIVE", "userId": "...", "serialNumber": "..." }
\`\`\`

\`payload\` must be byte-identical to what the signer actually signed (this backend hashes
the exact UTF-8 bytes you send, not a re-serialized or re-encoded version of them).
\`certificateStatus\` is one of \`ACTIVE\`, \`REVOKED\`, \`EXPIRED\` — checked as a fact
independent of \`signatureValid\`, matching how real PKI verification checks the math and
the certificate's standing separately rather than collapsing them into one boolean. A
mathematically valid signature from a REVOKED or EXPIRED certificate still reports
\`signatureValid: true\` — decide for yourself whether an old/revoked signature is
acceptable for your use case; this API reports both facts and doesn't make that judgment
call for you.

## Check a certificate's status directly

\`\`\`
GET /api/v1/certificate/status/{serialNumber}
\`\`\`

Returns the certificate's current \`status\`/\`expiresAt\`/\`issuedAt\`/\`publicKeyBase64\`
without needing a signature to check against — useful for looking up a certificate before
you have a signed payload in hand yet.

## Errors

| Code | Status | Meaning |
| --- | --- | --- |
| \`CERTIFICATE_NOT_FOUND\` | 404 | Unknown \`serialNumber\` |

## Questions

This is an early, actively-developed API, written directly from itunda's own backend
source (\`Certificate.kt\`'s own doc comment is the authoritative statement of this
feature's real scope) rather than a separate internal spec.
`;
