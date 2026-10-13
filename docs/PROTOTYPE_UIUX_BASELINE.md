# Clickable Prototype UI/UX Baseline

**Source:** user-provided `itunda, clickable prototype (1).html` (reviewed 2026-10-14).  
**Purpose:** preserve the prototype's intended product experience and turn it into a testable implementation checklist. This document does not certify production behavior.

## Product direction to preserve

- Itunda remains its own Rwanda-first brand; the prototype uses the current requested accent `#8F89FF`, the Itunda mark, rounded compact controls, quiet surfaces, and light/dark mode.
- Primary navigation: Home, Benefits, Shop, Pay, All.
- Rwanda-first use cases: MTN MoMo, Airtel Money, Irembo, RRA, REG, WASAC, school fees, local merchants, and RWF formatting.
- Core money journey: recipient → amount → confirmation → result.
- Shop journey: merchant → item quantities → checkout → explicit confirmation → order result.
- Language choices: English, Kinyarwanda, French; reduced-motion styling is included.
- The prototype itself explicitly discloses that bank, savings, protection, rates, balances, and merchant details are samples and that Itunda is not a licensed bank. Keep those disclosures clear and contextual.

## Prototype screen and flow inventory

| Surface | Intended experience | Evidence/limitation in the prototype |
|---|---|---|
| Home | Balance-first overview, quick actions, Rwanda services, spending and recommendations | Sample balances, spending, bill status, and provider-health message |
| Benefits | Rewards, referrals, cashback and offers | Sample reward total and sample offers |
| Shop | Search, local food and deals | Search field and “See all” are visual only; catalog, price, stock and delivery are sample data |
| Pay | Payment-code concept, itunda Pay balance, money actions, card entry point | QR is illustrative; add money explains that another linked account is not available |
| All | Money, saving/borrowing/investing and other product entry points | Many rows route to “Not built in this prototype” |
| Bank | Savings, Ikimina/SACCO, loans, investment and insights | Rates, returns, credit/protection numbers are sample/simulation content |
| 31-day savings | Streak and bonus-rate concept | “Start saving” is not implemented; displayed streak/rate are examples |
| Send | Recipient/contact selection, amount keypad, warning, confirmation and result | Entirely local in-memory simulation; recipient warning is hardcoded to a sample contact; fee/reference are fixed |
| Shop checkout | Quantity changes, total, payment confirmation and order result | Confirmation deducts the in-memory sample Pay balance; no provider or order API is called |
| Card | Card issuance concept, freeze and online-payment toggles | “Get a card” creates a local sample card; toggles only change local UI state |

## Findings to address

### P0 — do not represent a prototype action as a real financial operation

1. The transfer flow changes `S.bank` locally and then renders “Sent”; its reference and zero fee are hardcoded. Replace this with the real authenticated transfer flow, a server-authoritative status, persisted transaction reference, fees and error/unknown-state handling. Never show success unless the backend confirms it.
2. Shop confirmation subtracts the total from local `S.pay` and renders “Order placed”. Integrate a real order/payment state machine; handle insufficient funds, duplicate taps, timeouts, cancellation, refunds and uncertain provider responses.
3. Card issuance, freeze and online-payment settings only mutate client-side state. Wire them to authenticated card APIs and reflect the server result; clearly label unavailable card issuing as a concept until the issuer/processor and authorization path exist.
4. Sample savings rates, returns, lending offers, credit scores and the “Deposit Protection Fund (simulation)” must remain clearly labeled. Do not imply licensed-bank status, guaranteed yields, or real government deposit insurance.

### P1 — complete the primary interactions

1. Make Shop search, filters/categories, “See all”, product details, availability, address/delivery selection and order tracking functional.
2. Make Pay’s QR action scan or generate a server-verifiable payment payload; a decorative QR image must never be treated as a payment credential.
3. Implement add-money, bills, airtime, request-money, contacts and profile/notification actions, or render them as explicitly unavailable rather than dead-looking controls.
4. Replace fixed contact numbers and hardcoded scam counts with recipient resolution and risk information from trusted server responses. Provide a safe review/continue path and a way to report a suspicious recipient.
5. Ensure back navigation, cancel, retry and repeated taps are safe across every flow.

### P1 — accessibility, localization and UI consistency

1. Use semantic buttons/links for interactive rows; provide keyboard focus, visible focus states, screen-reader names, and switch semantics with keyboard support.
2. Keep reduced-motion support and verify contrast in both themes, including the accent color used for button text and focus indicators.
3. Translate every visible string in every screen. The prototype only localizes selected transfer and Pay strings; several tab labels, headings and other surfaces remain English. Have native Kinyarwanda and French reviewers validate financial terminology.
4. Remove the prototype's hardcoded legacy purple values from illustrative SVG artwork when migrating UI components; consume the shared Itunda design tokens and approved canonical brand assets instead. Do not redraw or recolor canonical logo files.
5. Replace fixed 390×844 assumptions with responsive layouts, safe-area handling and small-screen/large-text checks.

## Repository integration points checked

- `services/micro-frontends/bank-mfe/src/BankDashboard.tsx` is the main web banking surface and imports existing real API clients/views for accounts, cards, P2P, bills, shop/eats, rewards, messages, notifications, identity and related features. Implement against these existing domain surfaces where applicable instead of building a second disconnected demo.
- `packages/design-tokens/tokens.css` is the shared web design-token source and currently defines `--itunda-indigo: #7472F4` and `--itunda-logo-color: #7472F4`. The supplied prototype uses `#8F89FF` for its primary accent. Reconcile this difference with the canonical brand v3.1 review before changing shared tokens globally.
- `docs/TOSS_PARITY_MATRIX.md` is the existing status taxonomy and should remain the source for real/demo/target/blocked capability claims.

## Acceptance criteria for the next implementation pass

- Each primary action has a real API-backed success, explicit failure, loading state, retry behavior and an idempotency strategy where money/order mutation is involved.
- The UI only shows balances, fees, recipient details, order status and references returned by the server.
- No prototype/sample label disappears until the corresponding data and operation are verified end-to-end.
- All screens work in light/dark mode, keyboard/screen reader navigation, reduced-motion mode, and English/Kinyarwanda/French.
- Automated tests cover successful flow, insufficient balance, API timeout/unknown outcome, duplicate submission, cancellation and stale data.
- The canonical logo archive and approved brand assets remain untouched unless the brand review explicitly authorizes a verified migration.

## Status

This is an implementation baseline derived from the supplied HTML and a source-level check of the repository entry points. It is not a completed implementation, live-provider certification, or production validation.
