# Itunda Product & Platform Completeness Audit

> Created 2026-10-11. This is the execution register for the request to bring every Itunda product and platform surface to Toss-level product/engineering quality while retaining Itunda's own identity and Rwanda-first priorities. It is an audit plan, not a declaration that every feature is complete.

## Audit rule: implementation is not completeness

The current [Toss/Rwanda parity matrix](TOSS_PARITY_MATRIX.md) contains a broad set of product areas and implementation notes. Its status labels describe implementation evidence at the time recorded; they do **not** automatically certify end-to-end product readiness. A row marked `real` may still have a demo provider, missing legal permission, incomplete native UI, untested failure states, or no operational runbook. Preserve those distinctions.

A product is only marked **complete** when the relevant evidence is linked and verified across all applicable layers:

1. **User journey:** entry point, eligibility, onboarding/consent, primary success path, history/status, cancellation/reversal/refund, support and recovery.
2. **Backend/domain:** ownership and tenant isolation, validation, state machine, transaction boundaries, idempotency, concurrency, audit trail, migrations and data retention.
3. **Provider/market readiness:** real integration versus simulator clearly labeled; credentials/certification, settlement and reconciliation, local legal/compliance prerequisites explicitly tracked.
4. **Client parity:** consumer web, Android, iOS, business and mini-app surfaces where relevant; loading/empty/error/offline/permission-denied states; responsive and accessible UI.
5. **API/SDK:** versioned contract, typed errors, auth/scopes, pagination, idempotency, examples, compatibility policy, generated docs and working tests.
6. **Operations:** dashboards, alerting, trace/request IDs, support tooling, admin RBAC, immutable audit events, incident and rollback runbooks.
7. **Evidence:** tests and CI job links, live smoke evidence when safe, clear list of mocked/skipped checks, owner and next action.

Use these states for the final audit:
- **Verified complete** — all applicable gates above have evidence and no known blocker.
- **Implemented, incomplete** — meaningful code exists but one or more lifecycle, client, operational or test gates remain.
- **Demo/simulated** — behavior is intentionally simulated or uses local/test data.
- **Blocked externally** — requires licensed partners, provider credentials, certification, regulator approval or third-party access.
- **Not implemented** — product target only.
- **Needs verification** — repository descriptions claim capability but the current audit has not re-run its proof.

Never promote a demo rail to live, a product screen to a complete journey, or a green build to proof of a production deployment.

## Full product inventory

This register enumerates every row currently present in the source parity matrix. The detailed implementation history and per-row next gates remain in that matrix; the new audit standard above must be applied to each row, not inferred from the old status column.

| Product area | Capability being assessed | Existing matrix claim (not a completion certificate) | Rwanda adaptation |
|---|---|---|---|


## Platform surfaces that must be audited alongside every product

| Surface | Completion checks |
|---|---|
| Consumer super-app (web) | Home hierarchy; navigation; authentication/session recovery; profile/security; each product entry point; deep links; loading/empty/error/offline states; mobile and desktop behavior. |
| Android app | Clean reproducible build; device login and network configuration; canonical brand assets; every product route reachable; native back/lifecycle behavior; permissions; accessibility; unit/instrumentation tests. |
| iOS app | Clean Tuist/codegen/pods build; every product route reachable; native lifecycle and permissions; accessibility; unit/UI tests; canonical brand assets. |
| Business / merchant app | Business onboarding and KYB; staff/role permissions; QR/checkout; catalog/POS; settlement, refunds, disputes and reports; operator-visible failure states. |
| Developer portal | First successful local sample; versioned SDK install; API reference generated from canonical contracts; guides, changelog, migration docs, support and release status. |
| SDK / mini-app platform | One canonical manifest; capability permissions; deterministic browser mock; native bridge parity; shell/runtime compatibility; lifecycle/cancellation/offline handling; signed immutable artifacts; revocation and rollback. |
| API / gateway | OpenAPI or equivalent contract; typed clients; normalized errors; authZ and tenant ownership; idempotency; bounded timeouts; safe retries; rate limits; trace propagation; redaction; compatibility CI. |
| Sandbox | Explicit mock vs isolated backend sandbox; resettable seeded identities/balances; simulated labels; no production secrets; rate limits and audit events; API examples executable. |
| Internal operations console | RBAC; searchable audit trail; provider/rail health; incidents; fraud/compliance review; reconciliation exceptions; deployment/feature flags; SDK/artifact state; destructive-action confirmation. |
| Consumer, business, developer, tech sites | Host-aware routing verified on deployed hosts; correct distinct content; shared IDS tokens and light/dark themes; SEO/accessibility; production smoke tests and rollback. |
| Design system (IDS) | One canonical token source; generated web/Android/iOS outputs; component and product-flow patterns; accessibility and visual regression; no duplicate hex values or legacy logo copies. |
| Infrastructure / delivery | Environment separation; secrets management; migration safety; dependency/SBOM/license checks; artifact provenance; staged rollout; SLOs, alerts, backup/restore and tested rollback. |

## Cross-product reliability requirements

- **Money state:** quote → confirm → pending/processing → settled/failed/unknown → reconcile; timeout does not imply failure; duplicate retries cannot duplicate ledger postings.
- **Provider isolation:** mock, sandbox and live adapters share a contract but have unmistakable mode labels and separate credentials.
- **Identity and permissions:** phone/session/device controls, consent, account ownership, least privilege, step-up authentication for sensitive actions, privacy-safe logs.
- **Localization:** Kinyarwanda-first MVP copy, correct RWF amounts and formatting, local institution/rail names, low-bandwidth behavior and meaningful timestamps/time zones.
- **Design:** Itunda Indigo `#7472F4`, canonical logo assets only, light/dark mode, quiet whitespace, accessible contrast/focus, reduced motion and no Toss branding.
- **Observability:** a request can be followed across client, gateway, backend, provider adapter, ledger and event relay with one trace/request correlation.
- **Recovery:** each critical flow explains what happened, what is still pending, whether retry is safe, and how to get help.

## First implementation wave

1. **Resolve contradictory architecture documentation.** The root README currently describes Saronite as a hand-rolled approximation that is not built on Granite, while the newer ADR and architecture record say the Granite runtime is already integrated. The canonical truth must be checked against actual dependency declarations, native host wiring and CI, then stated consistently everywhere.
2. **Run the current PR #38 gates to completion.** Manifest contract and Dependency Review have passed; web boundary, general CI, Android and iOS must be checked on the latest commit before declaring the changes verified.
3. **Build a reproducible evidence index.** For each product row, record backend route/service, web/native route, tests, provider mode, operational owner, and missing gate. Use code search and CI output, not the prose claim alone.
4. **Audit real-money boundaries before expanding features.** Verify ledger invariants, idempotency, provider timeout/reconciliation, refund/reversal paths, admin authorization, seed-data isolation and secret handling.
5. **Finish developer experience and sandbox.** Make every implemented Saronite SDK API discoverable and runnable in a browser mock, then prove native/mock contract parity.
6. **Audit each public site by hostname.** A successful source build is not enough; request each production host and verify it serves the intended product and canonical brand assets.
7. **Close per-product gaps in priority order.** Start with trust-critical flows (auth, wallet, transfers, bills, merchant checkout, refunds, identity, support), then engagement and Rwanda-specific services. Do not label all products complete while external partner/regulatory gates remain blocked.

## External references reviewed

- [Toss public GitHub organization](https://github.com/toss) — public repositories only; not evidence of private internal implementation.
- [Toss Granite](https://github.com/toss/granite) — public React Native micro-app framework and brownfield host integration.
- [Apps in Toss community SDK example](https://github.com/apps-in-toss-community/sdk-example) — executable SDK reference and browser mock approach.
- [Apps in Toss Harness](https://github.com/toss/apps-in-toss-harness) — public developer workflow/scaffolding reference.
- [Toss es-toolkit](https://github.com/toss/es-toolkit) — reusable typed utility package and testing reference.
- [Toss UI repository](https://github.com/toss/toss-ui) — public UI package organization reference.
- [Apps in Toss developer documentation](https://developers-apps-in-toss.toss.im/) — public developer onboarding and runtime guidance.

These references support engineering patterns such as executable examples, a mock-first sandbox, consistent scaffolding, typed reusable packages and an independently governed mini-app runtime. They do not authorize copying Toss trademarks, assets, private APIs, or unobserved internal systems.
