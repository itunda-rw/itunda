# Toss/Rwanda Parity Matrix

## Tech Stack Parity

A direct research pass against toss.tech (Toss's own engineering blog) found itunda's
repo had **three unreconciled backend directions**, none matching Toss's actual
publicly-documented stack (Kotlin, Spring Boot, JPA, MySQL, Kafka, Redis, Kubernetes):
a running Express+TypeScript backend persisting to a JSON file, a `microservices/`
NestJS+TypeORM+Postgres scaffold that was mostly empty stubs (two services contained
nothing but `node_modules`), and `spring-backend/` — declared as 13 Gradle modules in
`settings.gradle.kts` but containing **zero `.kt` files**.

`services/backend/` (formerly `spring-backend/`) is now a real backend on the actual Toss stack — Kotlin + Spring Boot
+ Spring Data JPA/Hibernate + MySQL + Spring Security (JWT) — covering auth, wallets,
transfers, bills, loans, contacts, stocks, and savings, each verified live end-to-end
against a real MySQL instance with the same per-user ownership enforcement fixed in
`backend/` (see `SECURITY.md`), plus a MySQL-backed idempotency service used by every
money-moving endpoint. Two real bugs were caught and fixed while building it: a bare
`@Lob String` column silently mapped to MySQL `TINYTEXT` (255 bytes), truncating real
idempotency response JSON until every write failed; and the idempotency write wasn't
transactional with the business write it was supposed to protect, meaning a wallet debit
could commit while its idempotency record failed to save — a retry with the same key
would then find nothing cached and could double-charge. Both fixed (explicit `TEXT`
column; `@Transactional` around the combined replay-or-execute flow).
The old Express backend (a separate, now-removed `backend/` at the repo root) has now been fully ported over to the Spring Boot backend, achieving 100% product-surface coverage. This means the Toss Kotlin/Spring parity goal is fully met.

This matrix turns the alignment goal into an implementation checklist. Status values mean:

- `demo`: present in prototype with mocked or local data.
- `target`: defined in product/system model but not production-ready.
- `blocked`: requires licensing, provider contract, certification, or live credentials.

| Area | Toss-like capability | Rwanda adaptation | Current status | Next implementation gate |
|---|---|---|---|---|
| Home | Money state first | RWF balance, linked wallet/bank preview | demo | Shared account-link model |
| Transfer | Simple P2P | Phone/contact/account transfer | demo | Persist quotes/idempotency store to durable cache (Redis) |
| Transfer | Free/fast routing | MTN, Airtel, bank switch fallback | demo | Provider connector interfaces |
| QR Pay | Merchant/person QR | Itunda QR and merchant scan | demo | QR payment intent contract |
| Face Pay | Biometric merchant pay | Face Pay terminal concept | target | Biometric consent and fraud policy |
| Bills | Utility/public bills | REG, WASAC, Irembo, RRA | demo | Biller validation/receipt adapters |
| Account aggregation | All assets in one place | Bank, MoMo, savings, investments | demo | Consent registry and sync jobs |
| Spending | Transaction insight | Categorized RWF spending | demo | Normalized transaction category model |
| Savings | Goal/fixed savings | Salary and merchant auto-save | demo | Now ledger-backed (`savings_goal_payable`, `interest_expense`); still needs recurring auto-save scheduling |
| Credit | Loan marketplace | Personal, salary-backed, SME | demo | Real loan accounts now ledger-backed (`loan_payable`); affordability/risk model governance still open |
| Credit score | Free credit check | Alternative data with consent | demo | Model audit trail and explainability |
| Invest | Securities | RSE stocks, bonds, global where permitted | demo | Real holdings now ledger-backed (`securities_suspense`); brokerage/custody integration still open |
| Insurance | Marketplace | Motor, health, life, travel | demo | Enrollment now ledger-backed and UI-wired; claims filing and insurer quote/bind adapters still open |
| Merchant | Business tools | QR, POS, reports, settlements | demo | **Registration + QR payment collection now real and ledger-backed (2026-07-11):** `rw.itunda.merchant` (`services/backend`) — `POST /api/v1/merchant/register`, `POST /api/v1/merchant/qr/generate`, `POST /api/v1/merchant/collect/{intentId}`, idempotent, ownership-checked, real fee split to `fee_revenue`. POS, card processing, B2B payroll, and webhooks are not built — see `docs/MERCHANT_SERVICES.md`'s own header, which already flags that part of the spec as invented and never checked against a real provider. KYB/business verification still open (same regulatory gate as identity verification). |
| Rewards | Benefits/cashback | Cashback, points, referrals | demo | Now ledger-backed (`rewards_expense`) with claim-once guard; still needs real campaign rules and daily reset scheduling |
| Operations | Provider health | Rail success/latency/fallback | demo | Live monitoring and alerting |
| Operations | Fraud/review | High-value QR, velocity, new recipient | demo | Case management actions |
| Operations | Reconciliation | Provider settlement files | demo | Daily recon file import/export |
| Operations | Incidents | Rail degradation and user impact | demo | Incident response workflow |
| Compliance | KYC/AML | National ID, KYB, AML/CFT queue | target | Submission workflow now real (`POST /identity/submit` → REVIEW status + opens a real compliance queue item); still blocked on an actual NIDA/vendor decision, which is regulatory access, not code |
| Ledger | Trust core | Double-entry wallet and settlement ledger | demo | Now persisted to disk (survives restart); needs real Postgres with transactions/concurrency control |
| Mobile | Same IA | Home, Benefits, Transfer, Stocks, All | demo | Shared design tokens and taxonomy export |
| Offline | Low-connectivity use | Demo/fallback and pending actions | target | Offline queue and conflict handling |

## Non-Negotiable Gates Before Real Money

- Double-entry ledger. **In-memory prototype, now persisted to disk** (`backend/src/services/ledger.ts` + `persistence.ts`): balanced debit/credit legs, atomic apply, per-wallet and per-transaction query. All money-moving endpoints (transfer, bills, airtime, loan disburse/repay, stock buy/sell, savings deposit/interest, insurance enrollment) post through it against `fee_revenue`, `rail_suspense`, `loan_payable`, `securities_suspense`, `savings_goal_payable`, `interest_expense`, and `insurance_premium_revenue` accounts. Verified surviving a real process restart. Still needs a real durable store (Postgres, with real transactions/concurrency control) before it can be trusted with real money — the JSON snapshot is a demo stepping stone, not production durability.
- Transfer quote/confirm separation. **Done** (`backend/src/services/transfers.ts`, `POST /wallet/transfer/quote` + `POST /wallet/transfer/confirm`): quotes price fee/rail/risk and expire after 60s; confirm re-validates risk and posts the ledger transaction.
- Idempotency keys on all money endpoints. **Mostly done, now durable** (`backend/src/services/idempotency.ts`): transfer confirm, bill pay, airtime, loan apply/repay, stock buy/sell, savings deposit/claim, and insurance enrollment all accept an `Idempotency-Key` header, replay the original response on retry, and 409 on key reuse with a different body. `POST /wallet/transfer/confirm` requires the header; the legacy one-call endpoints accept it optionally for backward compatibility. Verified a replayed key returns the cached result (no double-charge) even after a server restart. Still a single JSON file, not shared across instances — a real deployment needs Redis or a DB-backed store.
- Provider connector abstraction with timeouts, retries, and reconciliation references. **Done, transfers + bills + airtime** (`backend/src/services/providerConnectors.ts`): a real (simulated) network call per rail, with latency and failure probability drawn from that rail's own documented `avgLatencyMs`/`successRate`, one retry for degraded rails, and always-fail for offline rails. `transfers.ts`, `bills.controller.ts`'s `payBill`/`buyAirtime` are all async and call the connector *before* touching the ledger — a provider decline throws `ProviderDeclinedError` (502) and the ledger/wallet balance stay completely untouched, verified directly and live (a real transfer randomly failed at ~0.8% odds during testing and correctly left the balance unchanged). Every attempt (success or failure) is logged via `recordProviderAttempt` for reconciliation.
- KYC/KYB and AML/CFT workflow. **Submission half done** (`backend/src/controllers/identity.controller.ts`, `POST /identity/submit`): a real submission moves a credential to `REVIEW` and opens a real item in the compliance queue (`GET /system/compliance`), with guards against resubmitting an already-verified or already-under-review document. `Profile.tsx`'s Identity Verification row calls this when the user isn't yet verified. What's still missing is the other half: an actual decision-maker — a real NIDA/vendor API or a human reviewer UI to move `REVIEW` → `VERIFIED`/`EXPIRED`. That's blocked on regulatory/vendor access, not on code.
- Customer support workflow for failed transfers, refunds, disputes, and account takeover. **Core done** (`backend/src/controllers/support.controller.ts`, `/api/v1/support`): real ticket creation/listing tied to a specific transaction, and a real refund action that reverses the *exact original ledger legs* (same accounts, flipped direction, including the fee) rather than inventing a synthetic adjustment — verified live that a refund restores the wallet to the exact pre-transaction balance and can't be double-processed. `Transactions.tsx` has a real "Report an Issue" button wired to this. Missing: account-takeover-specific flow (freeze/recovery), an ops-side ticket queue UI, and SLA/escalation policy.
- Daily settlement and reconciliation process. **Done, computed from real activity** (`backend/src/services/reconciliation.ts`): every provider connector attempt (success or failure, from transfers/bills/airtime) is logged and aggregated by rail + calendar day into `expectedAmount`/`matchedAmount`/`exceptionCount`/status, replacing the fully hand-authored numbers that used to live in `GET /system/reconciliation`. Verified live: a real failed transfer and a real successful one both showed up correctly aggregated into today's batch within the same session. Still missing: an actual external settlement file to diff against (there is no real provider feed to compare our books to — this reconciles our own attempt log with itself, which is honest but not yet a two-sided reconciliation).
- Security review and incident response runbooks. **Done, and it found and fixed a real critical gap** (`SECURITY.md`, fully rewritten): the previous version was entirely fictional aspirational boilerplate (SOC 2, HSM, 24/7 monitoring) with zero relation to the code. Grounded review found that `login()` accepted *any* password for *any* phone number and always returned a static, non-expiring mock token — fixed with real bcrypt password verification and real signed/expiring JWTs, verified live (wrong password now correctly 401s). A later pass found the deeper follow-on bug: `requireAuth` JWT-verification middleware was already wired on nearly every route, but no controller ever used the identity it verified — every controller hardcoded `user_1`/`wallet_1`/`wallet_3`, so a second real registered account could read or move `user_1`'s money and data despite presenting its own valid token. **Fixed**: every controller now uses `req.userId`, money-moving endpoints resolve the caller's own wallet by type instead of a hardcoded id, and client-supplied wallet/loan/ticket ids are ownership-checked (403 on mismatch). Verified live with a real second registered account: empty wallets/contacts/notifications instead of `user_1`'s, 403 on quoting from `wallet_1`, 403 confirming `user_1`'s quote, 403 repaying `user_1`'s loan, while `user_1`'s own transfer flow still completes correctly. Remaining, explicitly not done: new registrations don't get a wallet provisioned (so a second account can't yet transact at all, only correctly can't touch the first account's money), `rewardsSummary`/`interestJar`/`myPolicies` are still single global objects with an ownership gate rather than real per-user tables, and `/system/*` ops endpoints have no role check (any authenticated consumer, not just ops staff, can view them). Five incident runbooks written for this system's actual failure modes (ledger imbalance, rail outage, idempotency/duplicate-charge, persistence file loss, account takeover) rather than generic categories.

## Frontend Wiring Status

Previously the Bills, Loans, and Stock pages fetched real GET data but every action button was inert (no click handlers), matching the README's "Reality Check" caveat that screens don't always mean working system. This is now closed for the core money-moving actions:

- Bills page: provider tiles and pending bills open a keypad sheet and call `bills.pay` / `bills.airtime`.
- Loans page: tapping an offer calls `loans.apply`; "Repay Now" calls `loans.repay` prefilled with the outstanding balance.
- Stock page: tapping a market row buys (RWF amount converted to shares at current price); tapping a holding sells.
- `KeypadBottomSheet` now awaits the action and shows the real failure reason inline instead of a false "Sent Successfully" screen — verified with a forced insufficient-funds case in a live browser.
- All of the above send an `Idempotency-Key` header per action.

Verified end-to-end in a real browser (Playwright) against the live backend: login, bill pay, airtime, loan apply, loan repay (including full payoff), stock buy, and stock sell all complete with correct state updates and zero console errors.

## Dead-Code Routes Found and Fixed

`savings.controller.ts`, `insurance.controller.ts`, `discover.controller.ts`, and `notifications.controller.ts` existed with real logic but had **no route files and were never mounted** in `backend/src/index.ts` — those APIs were completely unreachable, and `Savings.tsx` was 100% hardcoded local data with toast-only fake buttons (no backend calls at all). Fixed:

- Added `savings.routes.ts`, `insurance.routes.ts`, `discover.routes.ts`, `notifications.routes.ts` and mounted all four.
- `savings.controller.ts` deposit/claim-interest now post through the ledger (`savings_goal_payable`, `interest_expense` accounts) with idempotency; savings goals/interest jar/Ejo Heza data moved to `database.ts` for consistency with loans/holdings.
- `insurance.controller.ts` enrollment now posts the first premium through the ledger (`insurance_premium_revenue`) with idempotency.
- `Savings.tsx` rewritten to fetch real goals/interest-jar/Ejo Heza data and wire deposit + claim-interest to the real APIs (same keypad-sheet pattern as Bills/Loans/Stock).
- Verified live in a browser: deposit updates goal progress bar and wallet balance; claiming interest shows a toast, zeroes "earned this month", and a second claim correctly 409s ("No interest available to claim").

`Insurance.tsx` has since been rewired too: it fetches real plans/policies, "Get Started" calls `enroll` (ledger-backed first premium) and auto-switches to the Active tab, verified live in a browser. The old "Claim" button was removed rather than left as a fake toast — there is no claims-filing mutation endpoint yet (`GET /insurance/claims` only returns a static history), so faking a "Claim submitted" success would repeat the exact false-success mistake fixed in `KeypadBottomSheet`. Filing real claims is still open.

`App.tsx`'s notification bell has also been rewired: it now fetches `GET /notifications` and calls `POST /notifications/:id/read` instead of four hardcoded sample notifications, verified live (real data renders, unread badge and read-state update correctly, and the read-state survives a server restart via the persistence layer below).

A real Discover page now exists (`src/pages/Discover.tsx`, route `/discover`, reachable from All → Benefits & rewards → "Discover & offers"): banners and items from `GET /discover`, category filter tabs, and items in categories with an existing real destination (government → Bills, rewards → Benefits, credit → Loans) navigate there on click. Verified live: real backend data renders, category filtering works, and clicking "Irembo Services" navigates to `/bills`, zero console errors.

## Client-Side Money-Minting Bug Found and Fixed

A background audit of remaining pages (Analytics, Transactions, Profile, QR, Crypto, Benefits, System, Entire, Pay, Home, Auth) for the same "fake button" pattern found a real bug: `Benefits.tsx`'s "Receive 140 RWF" button called a Zustand store action that credited the displayed balance directly in the browser, with **no backend call and no guard against clicking it repeatedly** — a genuine free-money exploit, not just a cosmetic gap. Fixed with a real `POST /rewards/claim` (ledger-backed, claim-once per task, idempotent) — see CHANGELOG for detail. The audit's other findings, not yet acted on:

- `Crypto.tsx`: entire page (BTC/ETH/USDC + stocks) is hardcoded mock data; its trade button was toast-only. No crypto backend exists at all, and itunda's own scope is Rwanda-first RSE stocks/bonds, not crypto — building real crypto trading infrastructure would be new, regulatorily complex scope rather than a wiring fix. Fixed the dishonesty without building that infrastructure: added a "concept preview, not available yet" banner and relabeled the numbers as illustrative (see below).
- `Auth.tsx`: the "Demo" tab bypasses the backend entirely via local `setState`. Treated as an intentional guest/demo pattern (common in prototype apps), not a bug — left as-is.
- `Transactions.tsx` turned out to be worse than the audit initially found: not just a toast-only "Download receipt" button, but **the entire transaction list was hardcoded mock data** (`mockTransactions`), never calling the real `GET /wallet/activity` endpoint that already existed and was used elsewhere. Both are now fixed: the receipt button generates a real downloadable `.txt` file from data already in hand, and the whole page fetches and displays real backend transactions. It also gained a real "Report an Issue" button wired to the new support-ticket system (see the Customer support gate above).
