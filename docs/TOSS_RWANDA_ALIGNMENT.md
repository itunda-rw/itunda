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
- `transfer.confirmed` — **real (2026-07-11):** `WalletService.confirmTransfer` publishes
  this to Kafka after the transaction row is saved, same after-commit pattern as
  `ledger.posted`.
- `payment.provider_succeeded` — **real (2026-07-11):** `BillsService.payBill`/
  `buyAirtime` publish this after `ProviderConnector.attempt` and the ledger post both
  succeed. Merchant QR collection does not publish it — it's pure wallet-to-wallet
  with no external rail call, so there's no provider to have succeeded against.
- `payment.provider_failed` — **real (2026-07-11):** `BillsService`'s new
  `attemptOrPublishFailure` wraps `ProviderConnector.attempt`, publishing this event
  before rethrowing `ProviderDeclinedException` (still caught as an HTTP 502 by
  `BillsController`, unchanged). Published via `EventPublisher.publishImmediately`
  (new method), not `publishAfterCommit` — the enclosing `@Transactional` method
  rolls back right after this fires, so an afterCommit hook would never run for it.
- `ledger.posted` — **real (2026-07-11):** `services/backend`'s `LedgerService` publishes
  this to Kafka after every successful ledger post (see `docs/ARCHITECTURE.md` §1).
- The rest of this list (`settlement.batch_created` onward) is still purely a target,
  not emitted anywhere — no real settlement/fraud/reconciliation/reward code exists yet
  to publish from.
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
  touches a wallet balance. **Extended to transfers (2026-07-11):** every P2P transfer
  previously always succeeded with no rail simulation at all — `WalletService.confirmTransfer`
  now calls `providerConnector.attempt` before ledger posting too, same
  decline-before-ledger-touch discipline, publishing `payment.provider_failed` on decline
  (`WalletController` got the same `ProviderDeclinedException` → 502 handler as
  `BillsController`). `RailCatalog.resolve` almost always falls through to `generic` for a
  transfer's free-text `recipient` (a phone number in practice) rather than matching a named
  rail — an honest reflection of not having real per-rail routing for P2P yet, not a bug.
  New decline-path test in `WalletServiceTest.kt` (now 8 tests). **Not extended to merchant
  collection** — deliberately: `MerchantService.collect` is pure wallet-to-wallet with no
  external rail call at all, so there's no provider to simulate a decline against.
- ~~Add a real double-entry ledger module before expanding money movement.~~ **Done** —
  `rw.itunda.core.ledger.LedgerService`, row-locked, tested (`LedgerServiceTest.kt`).
- ~~Add transfer quote/confirm separation.~~ **Done** — `WalletController`'s quote/confirm split.
- ~~Add idempotency keys to transfer and payment APIs.~~ **Done** — `IdempotencyService`,
  used across wallet/bills/loans/savings/insurance/merchant.
- ~~Create merchant domain routes instead of only documentation.~~ **Done (2026-07-11)** —
  `rw.itunda.merchant`: registration + QR payment collection, see
  `docs/TOSS_PARITY_MATRIX.md`'s Merchant row for the real/not-real split.
- **Correction (2026-07-11):** every "not build-verified"/"no Xcode in this
  environment" note below (and throughout `docs/ARCHITECTURE.md`'s §3) was true when
  written but not re-checked hard enough — `docs/ARCHITECTURE.md` §3's own
  "MAJOR CORRECTION" note has the full account: a real Xcode 14.3.1 install was
  present the whole time, just not the active `xcode-select` target, reachable via a
  scoped `DEVELOPER_DIR` env var with no system changes; a compatible Tuist version
  (3.42.3, via `mise`, no sudo) generates the real project; a real
  `xcodebuild ... BUILD SUCCEEDED` now exists, installed/launched/screenshotted on a
  real simulator, with Dynamic Type scaling confirmed live (before/after screenshots
  at a larger accessibility text size). Treat every "not build-verified" iOS note
  below as historical, superseded by that one.
- Make Android and iOS share the same product taxonomy as web. **Partially done** —
  design tokens now match exactly across `android/core/designsystem` and
  `ios/Core/DesignSystem` (2026-07-11 reconciliation). **Tab taxonomy fixed
  (2026-07-11):** discovered `ios/App/Sources/ContentView.swift` (the app's real
  `@main` entry point, confirmed via `ItundaApp.swift`) had never been reconciled
  with Android's established taxonomy at all — its tabs were labeled
  Home/Benefits/**Discover**/Pay/**Entire** against Android's
  Home/Benefits/**Shop**/Pay/**All**. Not a cosmetic mismatch: Android's `ShopTab`
  contains its own internal `"Discover"`-titled section fed by the same
  discover-items data `ContentView.swift`'s `DiscoverScreen` already resembles
  (flash deals, partner offers), confirming they're the same tab under two names;
  "All" is the deliberate translation of Toss's 전체 tab Android's own code comments
  already establish, "Entire" was just the wrong word. Renamed both labels to match.
  Also found and fixed along the way: `ContentView.swift`'s Home tab used its own
  crude, hardcoded-mock-data `BankScreen` struct instead of the real, IDS-token-driven
  `BankView` (`ios/Features/Banking/`) — `BankView` had **zero call sites anywhere**
  despite being real, ported code (the same "correct but unreachable" bug pattern
  `docs/ARCHITECTURE.md` §3 already documented for it). Deleted `BankScreen`, wired
  `BankView()` in directly (`Project.swift`'s `ItundaApp` target already depends on
  `FeatureBanking`, so no dependency-graph change needed). Verified via
  `swift -frontend -parse` (clean except a pre-existing, unrelated `#Preview` macro
  limitation in this toolchain, confirmed present in the file before this change
  too) — not build-verified for the usual no-Xcode-in-this-environment reason (§3),
  so the cross-module `import FeatureBanking` resolution itself is unverified.
  **`TransferQuoteScreen` wired too, same pass:** also found with zero call sites —
  its own internal biometric-confirm wiring (an earlier fix this session) was real
  but unreachable, since the screen containing it was never shown anywhere. `Pay`
  tab's two merchant rows now open it in a `.sheet` on tap. **Benefits/Shop/All tabs
  rebuilt (2026-07-11):** the remaining "crude inline screens" — new
  `App/Sources/BenefitsShopAllScreens.swift` replaces all three with content
  transcribed directly from `ItundaAppScreen.kt`'s real, Toss-screenshot-verified
  `BenefitsTab`/`ShopTab`/`AllTab` (applying an already-sourced design to iOS, not
  guessing at a new one), styled through `IDS`/`TdsColors` like `BankView`.
  `Project.swift`'s `ItundaApp` target now depends on `CoreDesignSystem` directly for
  this. Two token additions, both exact-value ports from Android's
  `TdsSemanticColors.kt`: `IDS.Colors.chipBackground` and `TdsColors.accent*` (the
  fixed product-icon colors). The All tab's mini-apps section is ported as inert list
  rows, not fake-wired — no mini-app runtime exists on iOS yet. All 5 tabs (Home,
  Benefits, Shop, Pay, All) now use real, taxonomy-matched, design-system-driven
  screens. Not build-verified, same no-Xcode caveat as the rest of `ios/`.
- Convert architecture docs from aspirational service lists to implemented/target
  sections. **Done, ongoing practice** — see `docs/ARCHITECTURE.md`'s real/demo/stub
  table, kept current as of every fix this document's own dated notes describe.

Medium priority:

- ~~Add design tokens for mobile and web parity.~~ **Done (2026-07-11)** for mobile
  (Android/iOS reconciled) and for web's CSS-custom-property consumers: new
  `packages/design-tokens` is the single source of truth `host-app` and `bank-mfe` now
  both `@import`, replacing two independently hand-copied `:root` blocks that had
  already drifted (`bank-mfe`'s `--toss-green` was Apple's iOS system green `#34c759`,
  not Toss's real `green500` `#04C065` that Android/iOS both use correctly) — verified
  the corrected value and a real new dark-mode block actually landed in both apps'
  compiled CSS output, not just the source. **`kyc-mfe` converted too (2026-07-11):**
  new `KycDashboard.css` replaces every inline hex literal with real
  `var(--toss-*)`-based classes (also gaining real dark-mode support for the first
  time); `index.css`/`App.css` turned out to still be the unmodified `create-vite`
  scaffold theme, never actually re-themed since the project was created — `index.css`
  now imports the shared tokens like the other two apps, `App.css` was dead code
  (never imported) and deleted. All three micro-frontends now share one real token
  source. Verified via the compiled CSS output and a clean `yarn build`/`yarn lint`.
- ~~Add end-to-end demo scripts for send, QR, bill, merchant settlement, and fraud
  review.~~ **Done for send/bill/QR (2026-07-11):** `scripts/demo-e2e.sh` — real curl
  calls against `services/backend`'s actual endpoints (login as the seeded demo user
  so there's a real balance to spend, pay a bill, quote+confirm a transfer, register a
  merchant, generate a QR code, pay it). Every path/body/field is transcribed directly
  from the real controllers, not guessed. Not runtime-executed in this environment — no
  Docker daemon to bring up MySQL/Redis/a live backend to run it against. Merchant
  settlement batching and fraud review have no real, callable endpoints yet to script
  against at all (see this doc's own Operations section and
  `docs/TOSS_PARITY_MATRIX.md`'s Operations rows) — left undone rather than faked.
- ~~Add accessibility checks for touch targets, contrast, form labels, and focus.~~
  **Contrast + content-description/label checks done, touch targets checked
  (2026-07-11):** real WCAG 2.1 contrast ratios computed against the live design
  tokens (2 genuine defects found and documented: `textTertiary` fails AA in both
  themes, light-mode `success` green fails contrast on white entirely); full
  content-description audit on Android (`ItundaAppScreen.kt`, 3 real icon-only-button
  bugs fixed, 7 icon usages) and accessibility-label audit on iOS
  (`BankView.swift`'s `TopBarActionButton`, 1 real bug fixed); touch target sizes
  checked against Android 48dp/iOS 44pt/WCAG 44px minimums (iOS passes exactly;
  Android's `TopIconButton` was 44dp, below Material's 48dp recommendation —
  **fixed same day:** bumped to 48dp after confirming all 3 call sites' layouts
  absorb the extra 4dp without overflow, `:app:assembleDebug` verified). Full
  findings in `docs/ACCESSIBILITY.md`. **Form labels audited and fixed (2026-07-11):**
  the only form field in the Android app (`RecipientEntryScreen`'s account-number
  `BasicTextField`) had no accessible label at all — fixed with a real
  `contentDescription`; the numeric keypad's `DEL` key (a bare `"⌫"` glyph) got one
  too. **Dynamic Type audited and fixed (2026-07-11):** Android was already correct
  (every `fontSize` uses the scalable `.sp` unit, no `.dp`, no `fontScale`
  override). iOS was genuinely broken — `IDS.Typography`/`TdsTypography` (the app's
  only typography tokens) and 5 more inline calls in `BankView.swift` all used
  fixed-size `Font.system(size:weight:)`, ignoring the user's iOS text-size
  accessibility setting entirely. 19 real instances found and fixed via a new,
  shared `IDS.scaledFont` helper wrapping `UIFontMetrics` (Apple's documented
  pattern for this exact case). **Focus order real-verified on both platforms
  (2026-07-11):** once the real Xcode/simulator toolchain was found
  (`ARCHITECTURE.md` §3), "no live TalkBack/VoiceOver access" stopped being true
  on either platform — XCUITest/`androidx.compose.ui.test` both read the same
  accessibility/semantics tree TalkBack and VoiceOver actually use. New
  `ItundaAppUITests` (iOS, `App/UITests/FocusOrderTests.swift`) and a new
  `androidTest` source set (Android's first — `FocusOrderTest.kt`) both assert
  the tab bar's real accessibility order matches the Home/Benefits/Shop/Pay/All
  taxonomy and left-to-right visual position, and that the Home tab's
  notification/profile icons are in correct visual order — **extended same day
  to all 5 tabs on both platforms** (each test taps the real tab bar first,
  the same path a TalkBack/VoiceOver user takes). iOS: `TEST SUCCEEDED`, 6/6.
  Android: `OK (6 tests)`, run via `adb shell am instrument` on a real "andros"
  AVD emulator — getting Android's running at all found and fixed two real
  bugs: `build.gradle.kts` had never set `testInstrumentationRunner`, silently
  defaulting to a JUnit3-only legacy runner that would have reported "No tests
  found" for every real `@Test` ever added; and `espresso-core:3.5.1`'s
  reflection-based input-injection init threw a live `NoSuchMethodException`
  against the emulator's API 36 platform, fixed by bumping to `3.7.0`. On iOS,
  extending coverage also found that `ShopTopBar`'s Profile/Cart and
  `TdsAllTopBar`'s Settings icons are bare `Image()`s with an
  `accessibilityLabel`, not `Button`s — confirmed by XCUITest itself (querying
  `app.buttons[...]` genuinely failed; `app.images[...]` passed), matching the
  already-documented finding that those specific icons aren't wired to real
  navigation yet. Full 5-tab focus-order coverage on both platforms now.
- **Recent-recipients row added to Android's transfer flow (2026-07-11):** found,
  while investigating this session's other work, that the user-provided real Toss
  reference screenshots (2026-07-10) are still present in this environment
  (`~/.claude/uploads/`) — including the exact recipient-entry screen ("어디로
  돈을 보낼까요?") `TransferFlow.kt`'s own header already cites as this flow's
  source. That screenshot shows a "최근 보낸 계좌" (recently sent accounts) list
  above manual account entry, which `RecipientEntryScreen` didn't have. Added
  `RecentRecipientRow` — purely additive, the existing real, tested, biometric-
  gated manual-entry flow is unchanged — using "TUYIZERE Eric"/BK, the same demo
  recipient identity already established in `ItundaAppScreen.kt`'s
  `CashbackChanceCard`, confirmed as the real reference identity by the
  screenshot's own visible "TUYIZERE E" row, not an arbitrary placeholder. No iOS
  equivalent screen exists to update (`PayScreen` goes straight to amount
  confirmation, no separate recipient-entry step). Verified:
  `:features:payments:impl:compileDebugKotlin` and `:app:assembleDebug` both
  `BUILD SUCCESSFUL`. Not visually verified live — the real "andros" emulator hit
  a genuine, persistent OS-level ANR ("Process system isn't responding," Android's
  `system_server`, not itunda's app) across a fresh boot, a wait, and a force-stop/
  relaunch, consistent with resource exhaustion after this session's sustained
  heavy emulator use (many installs, instrumented test runs). The same visible
  Home-tab UI (confirmed correctly rendering behind the ANR dialog in the
  screenshot taken) and the identical `Row`/`Box`+`CircleShape`+`Text` composable
  pattern already used and previously screenshot-verified elsewhere in this same
  file (`TransferPartyRow`) both support this rendering correctly, but that's
  inference from a proven pattern, not a fresh screenshot of this exact addition.
- ~~Add test coverage around fallback demo behavior.~~ **Done for every module that
  moves money or authenticates (2026-07-11):** `LedgerServiceTest.kt` (pre-existing) plus
  new `AuthServiceTest.kt`, `WalletServiceTest.kt`, `MerchantServiceTest.kt`,
  `BillsServiceTest.kt`, `LoansServiceTest.kt`, `SavingsServiceTest.kt`,
  `StocksServiceTest.kt`, `InsuranceServiceTest.kt` — 9 of 13 backend modules, covering
  every real ledger-touching flow plus login/registration/token rotation. Several tests
  are specifically regression guards for real bugs this session (and `SECURITY.md`
  before it) found and fixed, not just generic coverage. The remaining 4
  (notifications, discover, contacts, system) are small (41-70 LOC) hardcoded-data read
  endpoints with no ledger interaction at all — meaningfully lower-stakes, left
  uncovered as a deliberate choice, not an oversight. **Contacts + notifications added
  (2026-07-11):** on closer look these two aren't purely hardcoded-data reads — both
  have real per-user authorization logic (`ContactsController.getContacts`'s own doc
  comment names a real IDOR the Express version had; `NotificationController.markAsRead`
  has an ownership check before ever flipping `isRead`), worth a regression guard the
  same way the ownership checks in `LoansServiceTest.kt`/`SavingsServiceTest.kt` are.
  New `ContactsControllerTest.kt` (4 tests) and `NotificationControllerTest.kt` (4
  tests), same Kotest/MockK convention, calling the controllers directly (no Spring
  context needed — `@AuthenticationPrincipal`/`Authentication` are just plain method
  parameters at the JVM level). `discover`/`system` remain uncovered — genuinely just
  static catalog data with no branching logic to regress.

Low priority:

- Add advanced personalization and AI recommendations only after the core ledger/payment contracts are stable.

## Alignment Rule

If a proposed feature does not improve one of these four outcomes, it should not be prioritized:

- Move money safely.
- Understand money clearly.
- Access the right financial product.
- Operate and reconcile the platform reliably.
