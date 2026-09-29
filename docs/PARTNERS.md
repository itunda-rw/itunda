# itunda Partners

> **Written 2026-09-04.** No prior version of this document existed — verified directly
> against `services/backend/partners` (`PartnerController.kt`, `PartnerIdentityController.kt`,
> `PartnerService.kt`, `IdentityVerificationService.kt`), not ported from an earlier draft.
> This is itunda's own equivalent of `docs/PAYMENTS.md`, one level up: `docs/PAYMENTS.md`
> documents the Pay-specific API under `/api/v1/pay/*` and `/api/v1/merchant/*`; this
> document covers the shared partner-account layer under `/api/v1/partners/*` that Pay,
> the identity API, and itunda's mini-app platform all sit on top of. Keep this in sync the
> same way — if the controllers/service change, update this document first, then
> `services/developer-docs/src/partnerIdentityReference.ts` (the public port of the
> identity half of this doc).

## What a "partner" is

A partner is an external company integrating with itunda server-to-server — it authenticates
with its own API key (`X-Api-Key` header), never an itunda user JWT. `permitAll` at the Spring
Security layer for every route under `/api/v1/partners/**`; the real authentication happens
inside `PartnerService.resolvePartner`/`authenticate` on every call except `/register` itself
(see `PartnerController.kt`'s own doc comment).

## Registration

```
POST /api/v1/partners/register
{ "companyName": "...", "contactEmail": "..." }
```

Returns `{ success, partner: { id, companyName, contactEmail }, apiKey }` (201). The raw
`apiKey` is returned exactly once — only its SHA-256 hash (`apiKeyHash`) is ever stored, never
serialized back (`@JsonIgnore`, fixed live during this feature's own build after an early
draft leaked the hash by returning the `Partner` entity directly). One partner account per
`contactEmail` (`PARTNER_EMAIL_ALREADY_REGISTERED`, 409). Rate-limited
(`partner:register:$contactEmail`, 3 per 10 minutes) specifically because the
already-registered check doubles as an email-enumeration oracle — the same reasoning
`AuthService.register` already applies to itunda's own user signup.

A partner's `status` (`PartnerStatus`) can be suspended by an operator, at which point every
other endpoint throws `PartnerSuspendedException` (403, `PARTNER_SUSPENDED`).

## Mini-app platform (`/api/v1/partners/mini-apps`)

A partner can submit a mini-app for itunda's own in-app mini-app surface (Saronite):

```
POST /api/v1/partners/mini-apps
X-Api-Key: sk_test_...
{ "name": "...", "description": "...", "iconUrl": "...", "bundleUrl": "...", "permissions": ["account:read"] }
```

`permissions` must be a subset of `PartnerMiniAppPermissions.ALLOWED` — currently
`account:read`, `transactions:read`, `profile:read` (`INVALID_PERMISSION_SCOPE`, 400, if not).
Field limits mirror the shared DB column bounds fixed across every other user-submitted-text
service the same day: `name` ≤ 255 chars, `description`/`iconUrl`/`bundleUrl` ≤ 500
(`INVALID_MINI_APP_SUBMISSION`, 400). Every submission starts `PENDING` review
(`PartnerMiniAppStatus`) — `GET /api/v1/partners/mini-apps` returns the calling partner's own
apps at any status; there is a separate, operator-only review queue
(`PartnerService.getQueue`) not exposed under this partner-facing controller.

`GET /api/v1/partners/permissions` (no auth) returns the current `ALLOWED` set so a partner's
own UI can validate scope choices before submitting.

## Identity verification (`/api/v1/partners/identity`)

"Verify/sign in with itunda" — closes the real 토스인증 (Toss Cert identity-verification) gap,
distinct from the existing `Certificate` e-signature feature (a user's own document-signing
key; this is itunda vouching for a user's identity TO a third party). Real sourced shape
(toss.im/tosscert/docs/guides/integration/user): a partner creates a request server-to-server,
the user reviews and approves it inside the itunda app via a real consent screen naming the
partner and exactly what will be shared, and only on explicit approval does
`IdentityVerificationService` ever build/sign/store the disclosed payload — there is no silent
or default-approve path anywhere in the service.

1. **Create**: `POST /api/v1/partners/identity/requests` (`X-Api-Key`, no body) → `{ success,
   requestId, verifyUrl: "itunda://verify/{requestId}", expiresAt }`. `verifyUrl` reuses the
   same `itunda://` deep-link scheme `AndroidManifest.xml`'s `itunda://maps` intent-filter
   already establishes. `requestTtl` is 5 minutes (`IdentityVerificationService`, hardcoded).
   Rate-limited per partner (`identity:create:$partnerId`, 30/minute).
2. **User responds** inside the itunda app — `IdentityVerificationService.getForUser` (the
   app's own consent-screen read) / `.approve`/`.decline` (the app's own write, not exposed to
   partners at all). `approve` builds a frozen-at-approval-time JSON snapshot
   (`IdentityDisclosure`: `firstName`, `lastName`, `phoneNumber`, `kycVerified`, `birthDate?`)
   from the live `User` row, signs it with `IdentitySigningKeyProvider` (Ed25519), and stores
   both — never re-read live from `User` on a later poll, so a partner's later poll always sees
   exactly what the user actually approved, even if the user's profile changes afterward.
3. **Partner polls**: `GET /api/v1/partners/identity/requests/{requestId}` (`X-Api-Key`) →
   `{ success, status }`, plus `identity`/`signature` only when `status == APPROVED`.
   `IdentityVerificationStatus` is `PENDING | APPROVED | DECLINED | EXPIRED`. Real lazy-expiry
   read path (`withEffectiveStatus`, same convention `P2pPaymentRequest`/`P2pService.payRequest`
   already established): a `PENDING` row past its own `expiresAt` is persisted as `EXPIRED` the
   moment anything next reads it, no separate scheduled sweep needed. Ownership is enforced by
   the repository query itself (`findByIdAndPartnerId(requestId, partner.id)`), not a
   separate post-fetch check — a request belonging to a different partner produces the exact
   same `VERIFICATION_REQUEST_NOT_FOUND` (404) as a genuinely nonexistent id, never a
   distinguishable response (see `project_itunda_idor_audit` memory's own standing discipline
   on this exact bug class).
4. **Verify independently**: `GET /api/v1/partners/identity/public-key` (no auth) → `{ success,
   algorithm: "Ed25519", publicKey }`. `signature` is computed over the exact canonical JSON
   bytes of the `identity` object as originally serialized at approval time (field order:
   `firstName`, `lastName`, `phoneNumber`, `kycVerified`, `birthDate`) — a partner must
   reconstruct those same bytes before verifying, not re-serialize with its own JSON library's
   own field order.

## Errors

| Code | Status | Meaning |
| --- | --- | --- |
| `API_KEY_REQUIRED` | 401 | Missing `X-Api-Key` header |
| `INVALID_API_KEY` | 401 | Key doesn't match any partner |
| `PARTNER_SUSPENDED` | 403 | Partner account suspended |
| `PARTNER_EMAIL_ALREADY_REGISTERED` | 409 | `/register` called again for an existing email |
| `INVALID_PERMISSION_SCOPE` | 400 | A requested mini-app permission isn't in `ALLOWED` |
| `INVALID_MINI_APP_SUBMISSION` | 400 | Missing/over-length mini-app field |
| `VERIFICATION_REQUEST_NOT_FOUND` | 404 | Unknown `requestId`, or belongs to a different partner |
| `RATE_LIMITED` | 429 | Too many requests |

## What's NOT built here

No production/live key tier (every key is a sandbox key, matching Pay's own honesty
convention). No webhook delivery for identity-verification status changes — a partner must
poll; see `docs/PAYMENTS.md`'s webhook section for the pattern this could reuse if built.
`PartnerMiniApp` does carry `reviewedBy`/`reviewedAt`/`decisionReason` (set by
`PartnerService.decide`, the operator-only review action behind `PartnerAdminController`),
and `GET /api/v1/partners/mini-apps` returns the full entity — so a partner CAN already see
why their submission was rejected once reviewed, just with no separate summary/notification
beyond polling this same list endpoint.
