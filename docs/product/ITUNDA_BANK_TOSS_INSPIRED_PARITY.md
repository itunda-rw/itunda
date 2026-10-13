# Itunda Bank — Toss Bank–inspired parity plan

> Goal: make Itunda Bank feel as clear, fast, safe, and dependable as the best Korean mobile-banking experiences, while remaining unmistakably Itunda. This is a parity plan, not a claim that Itunda is a licensed bank or already has Toss Bank's products, rails, rates, or regulatory permissions.

## Non-negotiable product rules

- Use Toss Bank as a source of UX principles and public product research only. Do not copy Toss branding, logos, proprietary screens, text, or assets.
- All customer-facing UI must use the Itunda Design System (IDS), canonical Itunda assets, and current Itunda brand tokens (including the current indigo `#8F89FF`).
- Prefer one clear task per screen, plain-language labels, visible fees/limits, short flows, meaningful loading states, and recoverable errors.
- Rwanda-first: Kinyarwanda-first copy, realistic low-bandwidth/mobile-device performance, local payment and identity constraints, and accessible feature-phone pathways where supported.
- Never fabricate account balances, interest, transaction history, exchange rates, card benefits, credit eligibility, or success states. Clearly label simulations and unavailable integrations.
- Money-moving operations must be server-authoritative, authenticated, idempotent, auditable, and protected against concurrent requests and replay.

## Reference research

- Toss Bank's official product catalogue covers transaction accounts, savings/deposits, loans, foreign currency, cards, customer support, and business banking: https://tossbank.com/
- Its account product page highlights flexible deposits/withdrawals, interest information, transfer/certificate fee clarity, and ATM fee information: https://tossbank.com/product-service/savings/account
- Toss Bank's account-opening explainer emphasizes mobile onboarding, customer choice around interest, transfers, foreign exchange, and product discovery: https://5088.toss.family/service/create-an-account
- Toss's published frontend UX session describes iterative UX improvement and sustaining quality: https://toss.im/tmc-25/sessions/engineering/frontend-17

These are references for user problems and design principles, not assumptions that the same Korean products or pricing can be offered in Rwanda.

## Workstreams and acceptance criteria

### P0 — Trust, account correctness, and release safety
- Audit account list/detail, balance, transaction feed, transfer, PIN, session expiry, and device-step-up flows across web, Android, and iOS.
- Use a single authoritative ledger/balance source; enforce database transactions and concurrency-safe idempotency for transfers and other money-moving commands.
- Reject duplicate/replayed requests safely; define pending/succeeded/failed states and reconciliation for ambiguous timeouts.
- Show the server-confirmed receipt only after authoritative success. Never optimistically claim money moved.
- Add tests for simultaneous transfers, insufficient funds under race conditions, duplicate idempotency keys, timeouts/retries, reversals, and ledger invariants.
- Gate release on authenticated production-like smoke tests, migrations, observability, and no unresolved critical security findings.

### P1 — Make everyday banking effortless
- Bank home prioritizes available balance, recent activity, and the user's next useful action; hide secondary features behind clear progressive disclosure.
- Transfer flow: choose recipient → enter amount → review recipient, amount, fees, and delivery timing → authenticate → show a shareable receipt.
- Make recipient lookup, saved recipients, recent recipients, transaction search/filter, and transaction detail consistent and resilient.
- Explain every failure in human language with a safe next action; preserve entered values when retrying where safe.
- Replace immediate skeleton flashes with delayed skeletons only for genuinely slow requests; use stable layouts and accessible reduced-motion behavior.
- Ensure empty, offline, stale-data, loading, permission-denied, and retry states are designed—not accidental.

### P2 — Product breadth, only when operationally real
- Account types and savings goals; interest accrual/crediting only when backed by a verified product and ledger rules.
- Cards: issue/freeze/unfreeze, spending controls, PIN management, lost-card reporting, and clear transaction status where issuer integrations actually support them.
- Foreign currency and remittance: display a timestamped quote, total fees, final received amount, and quote expiry before confirmation.
- Loans: eligibility, terms, total repayment, fees, due dates, and hardship/support pathways before application.
- Statements and certificates with verifiable generation, privacy-safe downloads, and audit logs.
- Shared/group money features with clear ownership, permissions, consent, and dispute handling.
- Each feature stays visibly unavailable until its provider, legal basis, operations, and end-to-end tests are verified.

### P3 — Developer and operations quality
- Stable versioned API contracts, generated/validated SDK types, documented error codes, request IDs, idempotency rules, and pagination.
- A sandbox with deterministic test users and clearly marked simulated funds; no accidental production side effects.
- Feature flags, staged rollout, dashboards for transfer failure/latency, alerts, audit trails, and tested rollback.
- Contract tests across web/Android/iOS and backend; accessibility, localization, performance, and security checks in CI.
- Keep domain boundaries explicit; do not introduce Redis or other architectural dependencies contrary to the approved Itunda architecture without evidence and approval.

## Immediate implementation sequence

1. Inspect the existing bank-mfe account, transfer, card, PIN, and API clients; map each UI action to the actual backend endpoint and ledger behavior.
2. Fix correctness/security and write regression tests before changing high-risk transfer behavior.
3. Audit and migrate remaining customer-facing legacy controls to IDS; verify light/dark mode, keyboard/screen-reader support, reduced motion, and the current brand token.
4. Simplify the highest-frequency account and transfer journeys; add instrumented funnel/error events without logging sensitive financial data.
5. Run the full relevant CI matrix, authenticated smoke tests, and Android/iOS/web verification before calling any work release-ready.

## Definition of done

A workstream is complete only when the implementation is committed, tests pass, the relevant app/platform is verified, production configuration and observability are checked, and evidence is linked in the PR. A plan, mockup, passing typecheck, or successful build alone is not proof that live banking works.
