# Itunda Toss/Rwanda Alignment

This is the canonical alignment target for making Itunda feel and operate like Toss while staying Rwanda-native. It separates the target system from the current demo implementation so product, engineering, design, compliance, and operations can build against one map.

For the implementation checklist, see [TOSS_PARITY_MATRIX.md](TOSS_PARITY_MATRIX.md). For source-backed product facts, see [FACT_CHECKED_TOSS_RWANDA_MAP.md](FACT_CHECKED_TOSS_RWANDA_MAP.md).

## North Star

Itunda should be the financial home screen for Rwanda: open the app, understand your money, move money safely, discover the right financial product, and trust that the platform can route, settle, reconcile, and support every transaction.

The Toss reference is the operating model:

- One consumer app with simple financial jobs.
- Many regulated products behind a calm UI.
- A strong operating layer hidden from users but visible to internal teams.
- Trust through speed, clarity, safety, and recoverability.

The Rwanda adaptation is the market model:

- RWF-first balances and pricing.
- Phone-number identity and National ID KYC.
- MTN MoMo and Airtel Money as primary daily rails.
- Bank/wallet interoperability as the money-movement backbone.
- Irembo, RRA, REG, WASAC, airtime, school, transport, and merchant payments as local utility anchors.
- SME and merchant tools as a first-class growth engine.
- Low-bandwidth and fallback-ready UX for rural and intermittent-connectivity usage.

## Product System

### 1. Money

User jobs:

- Send money by phone number, contact, account, or QR.
- Request money.
- Pay merchants with QR, payment link, or Face Pay terminal concept.
- Top up airtime and data.
- Pay REG, WASAC, Irembo, RRA, school, transport, insurance, and subscription bills.
- Schedule rent, savings, supplier payouts, and recurring family support.

System capabilities:

- Rail selection across mobile money, bank switch, government biller, utility, merchant acquiring, and card/token rails.
- Idempotent transfer creation.
- Quote before confirmation: amount, fee, rail, ETA, fallback, limits, and fraud state.
- Reversal/refund workflow.
- Provider health, retry, timeout, and fallback logic.

### 2. Assets

User jobs:

- See all money in one place: Itunda wallet, MTN MoMo, Airtel Money, bank accounts, savings, investments, loans, and insurance.
- Hide sensitive values.
- Search transactions.
- Categorize spending.
- Understand upcoming obligations.

System capabilities:

- Account-link consent registry.
- Balance and transaction sync.
- Normalized transaction schema.
- Data freshness indicators.
- Consent expiry and revocation.

### 3. Banking and Savings

User jobs:

- Hold RWF in a trusted wallet.
- Create savings goals.
- Lock fixed deposits.
- Auto-save from salary or merchant revenue.
- Manage virtual/physical card concepts where licensed.

System capabilities:

- Double-entry ledger.
- Available, pending, held, and settled balances.
- Product ledger accounts.
- Interest accrual and disclosure.
- Safeguarding and reconciliation reporting.

### 4. Credit

User jobs:

- Check credit score.
- Compare personal, salary-backed, and SME loan offers.
- See clear repayment cost.
- Apply in minutes.
- Repay manually or automatically.

System capabilities:

- Affordability and risk scoring.
- Alternative data consent.
- Lender marketplace routing.
- Underwriting audit trail.
- Collections, restructuring, and delinquency workflows.

### 5. Invest

User jobs:

- Buy RSE stocks and bonds.
- Track portfolio value.
- Set recurring investment plans.
- Discover funds/global stocks where permitted.

System capabilities:

- Suitability checks.
- Market data.
- Order status.
- Custody sync.
- Tax and statement exports.

### 6. Protect

User jobs:

- Compare motor, health, life, travel, home, and business insurance.
- Buy policies.
- Store policy documents.
- Submit and track claims.

System capabilities:

- Insurer catalog.
- Quote orchestration.
- Policy issuance.
- Claims workflow.
- Renewal reminders.

### 7. Merchant

User jobs:

- Accept QR, mobile money, payment links, and terminal payments.
- View sales today.
- Settle to bank or mobile money.
- Refund customers.
- Export tax reports.
- Access working capital.

System capabilities:

- Merchant onboarding and KYB.
- Store, terminal, and cashier management.
- Settlement batches.
- Fee calculation.
- Disputes, refunds, receipts, and reconciliation.
- Risk holds and release workflow.

### 8. Benefits

User jobs:

- Earn cashback, points, merchant offers, referrals, and activity rewards.
- Understand why an offer is relevant.
- Redeem instantly.

System capabilities:

- Campaign engine.
- Eligibility rules.
- Reward ledger.
- Fraud controls.
- Partner settlement.

## Architecture System

### Bounded Contexts

- Identity: auth, biometrics, National ID KYC, user profile, device trust.
- Consent: account linking, data scopes, expiry, revocation, audit.
- Ledger: accounts, postings, holds, reversals, balances, statements.
- Payments: transfer intent, routing, provider connectors, fees, confirmations.
- Bills: biller catalog, account validation, presentment, receipt.
- Merchant: KYB, QR, terminal, payment links, settlements, disputes.
- Credit: offers, applications, underwriting, repayments, collections.
- Wealth: market data, orders, portfolio, custody sync.
- Insurance: quote, bind, policy, claims, renewal.
- Engagement: rewards, recommendations, notifications.
- Risk: fraud scoring, limits, AML queue, sanctions hooks.
- Operations: reconciliation, incidents, support, observability, admin.

### Core Data Contracts

Every money movement should have:

- `transfer_intent_id`
- `quote_id`
- `idempotency_key`
- `customer_id`
- `source_instrument_id`
- `destination_instrument_id`
- `rail_id`
- `amount`
- `fee`
- `currency`
- `risk_decision`
- `ledger_transaction_id`
- `provider_reference`
- `status`
- `created_at`
- `confirmed_at`
- `settled_at`

Every ledger entry should be double-entry:

- Debit and credit postings must balance.
- Postings are append-only.
- Reversals create new entries.
- Available balance must derive from posted balance minus holds.
- External provider balances are reconciled, not trusted as the ledger of record.

### API Layers

- Consumer API: mobile/web user flows.
- Merchant API: QR, links, settlements, webhooks, reports.
- Partner API: lenders, insurers, billers, banks, mobile money providers.
- Operations API: compliance, fraud, reconciliation, incidents, support.

### Event Model

Use events for cross-context workflows:

- `identity.verified`
- `consent.created`
- `transfer.quoted`
- `transfer.confirmed`
- `payment.provider_succeeded`
- `payment.provider_failed`
- `ledger.posted`
- `settlement.batch_created`
- `settlement.paid`
- `fraud.case_opened`
- `reconciliation.exception_found`
- `reward.earned`

### Production Readiness Gates

Before real money:

- PSP/bank/mobile-money contracts and certification.
- Licensed custody/safeguarding model.
- AML/CFT process, sanctions screening, and suspicious-activity workflow.
- Data-protection impact assessment.
- Pen test and secure SDLC.
- Reconciliation files and settlement SLAs.
- Customer support playbooks for failed transfers, reversals, refunds, chargebacks, and account takeover.
- Incident response and regulator notification playbook.

## UI/UX System

### App Structure

Bottom tabs:

- Home: money state and next best actions.
- Benefits: rewards and campaigns.
- Transfer: send/request/scan.
- Invest: RSE/global investing entry.
- All: full service directory.

Primary home order:

1. Rail/status confidence.
2. Main balance and linked assets.
3. Transfer/QR/bills quick actions.
4. Rwanda rails and public services.
5. Spending and upcoming obligations.
6. Product recommendations.
7. Credit score and identity/KYC status.

### Interaction Principles

- One action per screen when money is at risk.
- Always show recipient, amount, fee, rail, ETA, and fallback before confirmation.
- Use phone contacts first, account numbers second.
- Put risk/review states in plain language.
- Make failure recoverable: retry, use fallback rail, contact support, or reverse.
- Support low-bandwidth states and demo fallback without scary error messages.

### Visual Principles

- Use quiet card surfaces, compact rows, clear hierarchy, and stable icon buttons.
- Avoid decorative-heavy fintech marketing screens inside the app.
- Use RWF formatting consistently.
- Prefer local product labels over generic abstractions.
- Keep internal operations screens dense and scannable.
- Keep cards to actual items, repeated modules, or tools.

### Required States

Every product flow must define:

- Empty state.
- Loading state.
- Linked/unlinked state.
- Pending/review state.
- Success state.
- Failure with retry/fallback.
- Offline or provider-unavailable state.
- Support/escalation path.

## Current Repository Gap List

High priority:

- ~~Replace mocked provider calls with provider connector interfaces and typed fake providers.~~
  **Done (2026-07-11)** for bills/airtime: `rw.itunda.core.provider.ProviderConnector`
  (`services/backend`), a real interface with a `SimulatedProviderConnector` implementation —
  per-rail latency/success-rate profiles, called before ledger posting so a decline never
  touches a wallet balance. Not yet extended to transfers or merchant collection.
- ~~Add a real double-entry ledger module before expanding money movement.~~ **Done** —
  `rw.itunda.core.ledger.LedgerService`, row-locked, tested (`LedgerServiceTest.kt`).
- ~~Add transfer quote/confirm separation.~~ **Done** — `WalletController`'s quote/confirm split.
- ~~Add idempotency keys to transfer and payment APIs.~~ **Done** — `IdempotencyService`,
  used across wallet/bills/loans/savings/insurance/merchant.
- ~~Create merchant domain routes instead of only documentation.~~ **Done (2026-07-11)** —
  `rw.itunda.merchant`: registration + QR payment collection, see
  `docs/TOSS_PARITY_MATRIX.md`'s Merchant row for the real/not-real split.
- Make Android and iOS share the same product taxonomy as web. **Partially done** —
  design tokens now match exactly across `android/core/designsystem` and
  `ios/Core/DesignSystem` (2026-07-11 reconciliation); screen/navigation taxonomy parity
  is still open.
- Convert architecture docs from aspirational service lists to implemented/target
  sections. **Done, ongoing practice** — see `docs/ARCHITECTURE.md`'s real/demo/stub
  table, kept current as of every fix this document's own dated notes describe.

Medium priority:

- Add design tokens for mobile and web parity. **Done for mobile** (Android/iOS
  reconciled 2026-07-11); web (`services/micro-frontends`) still uses its own separate
  `--toss-*` CSS custom properties, not derived from the same source as mobile's Kotlin/
  Swift token files.
- Add end-to-end demo scripts for send, QR, bill, merchant settlement, and fraud review.
- Add accessibility checks for touch targets, contrast, form labels, and focus.
- ~~Add test coverage around fallback demo behavior.~~ **Started (2026-07-11)** —
  `LedgerServiceTest.kt`, `MerchantServiceTest.kt`, `BillsServiceTest.kt` now exist
  (previously only the first one did). Most other services (auth, wallet, loans,
  savings, stocks, insurance, notifications, discover, contacts, system) still have
  zero test coverage.

Low priority:

- Add advanced personalization and AI recommendations only after the core ledger/payment contracts are stable.

## Alignment Rule

If a proposed feature does not improve one of these four outcomes, it should not be prioritized:

- Move money safely.
- Understand money clearly.
- Access the right financial product.
- Operate and reconcile the platform reliably.
