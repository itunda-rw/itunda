// Source of truth: docs/PARTNERS.md's "Identity verification" section (added in the same
// pass as this file, once none existed yet), same "internal doc backs the public port"
// relationship apiReference.ts already has with docs/PAYMENTS.md. Keep these two in sync;
// if PartnerIdentityController.kt/IdentityVerificationService.kt change, update
// docs/PARTNERS.md first, then this file.
export const partnerIdentityReferenceMarkdown = `
## Overview

"Verify with itunda" lets your own backend confirm a real user's identity — name, phone
number, itunda KYC status — without you ever building or storing your own identity
documents. The user reviews and approves the request inside their own itunda app; you get
back a signed, structured disclosure your server can verify independently. This is a
separate product from "Pay with itunda": no money moves here at all.

Every key issued today is a sandbox key — itunda has no production/live tier yet.

## Authentication

Every request is authenticated with a secret API key in the \`X-Api-Key\` header, shared
across all of itunda's partner APIs (Pay, this one, and itunda's mini-app platform). If you
don't have one yet:

\`\`\`
POST /api/v1/partners/register
Content-Type: application/json

{ "companyName": "Your Company Ltd", "contactEmail": "dev@yourcompany.example" }
\`\`\`

\`\`\`
{ "success": true, "partner": { "id": "...", "companyName": "...", "contactEmail": "..." }, "apiKey": "..." }
\`\`\`

The raw key is returned exactly once — only its hash is ever stored, so save it immediately.

## The real flow

1. **Create a request.** Your backend calls \`POST /api/v1/partners/identity/requests\` with
   your API key — no body needed:

   \`\`\`
   POST /api/v1/partners/identity/requests
   X-Api-Key: sk_test_...
   \`\`\`

   \`\`\`
   { "success": true, "requestId": "idverify_...", "verifyUrl": "itunda://verify/idverify_...", "expiresAt": "..." }
   \`\`\`

   The request expires in 5 minutes if the user never responds.

2. **Hand off to the itunda app.** Open \`verifyUrl\` (itunda's own \`itunda://\` deep-link
   scheme) from your own app or site. This takes the user directly to a real consent screen
   inside their itunda app naming your company and exactly what will be shared — never a
   silent or default-approve path.

3. **Poll for the result** from your own server:

   \`\`\`
   GET /api/v1/partners/identity/requests/{requestId}
   X-Api-Key: sk_test_...
   \`\`\`

   \`\`\`
   { "success": true, "status": "PENDING" }
   \`\`\`

   \`status\` is one of \`PENDING\`, \`APPROVED\`, \`DECLINED\`, \`EXPIRED\`. The \`identity\` and
   \`signature\` fields only ever appear once \`status\` is \`APPROVED\` — never returned for
   any other status, even briefly:

   \`\`\`
   {
     "success": true,
     "status": "APPROVED",
     "identity": { "firstName": "...", "lastName": "...", "phoneNumber": "...", "kycVerified": true, "birthDate": "1998-04-12" },
     "signature": "..."
   }
   \`\`\`

   \`birthDate\` is \`null\` if the user hasn't completed itunda's own KYC flow.

4. **Verify the signature independently before trusting the result.** Fetch itunda's public
   key once (this doesn't change per-request, safe to cache):

   \`\`\`
   GET /api/v1/partners/identity/public-key
   \`\`\`

   \`\`\`
   { "success": true, "algorithm": "Ed25519", "publicKey": "..." }
   \`\`\`

   \`signature\` is an Ed25519 signature over the exact canonical JSON bytes of the
   \`identity\` object as itunda originally serialized it at approval time (field order:
   \`firstName\`, \`lastName\`, \`phoneNumber\`, \`kycVerified\`, \`birthDate\`) — not whatever
   byte order your own JSON library re-serializes it in. Reconstruct those exact bytes
   before verifying, the same discipline as verifying any detached signature.

## Endpoints

| Method | Path | Auth | Purpose |
| --- | --- | --- | --- |
| POST | \`/api/v1/partners/identity/requests\` | \`X-Api-Key\` | Create a request, get a \`verifyUrl\` |
| GET | \`/api/v1/partners/identity/requests/{requestId}\` | \`X-Api-Key\` | Poll for the result |
| GET | \`/api/v1/partners/identity/public-key\` | none (public) | Fetch itunda's Ed25519 public key |

## Errors

Every error response is \`{ "success": false, "code": "...", "message": "..." }\` with a
matching HTTP status:

| Code | Status | Meaning |
| --- | --- | --- |
| \`API_KEY_REQUIRED\` | 401 | Missing \`X-Api-Key\` header |
| \`INVALID_API_KEY\` | 401 | Key doesn't match any partner |
| \`PARTNER_SUSPENDED\` | 403 | Your partner account has been suspended |
| \`VERIFICATION_REQUEST_NOT_FOUND\` | 404 | Unknown \`requestId\`, or it belongs to a different partner |
| \`RATE_LIMITED\` | 429 | Too many requests — back off and retry |

## Questions

This is an early, actively-developed API, written directly from itunda's own backend
source rather than a separate internal spec — if something here doesn't match what you
see in practice, the actual controller (\`PartnerIdentityController.kt\` in the itunda
repository) is the canonical source.
`;
