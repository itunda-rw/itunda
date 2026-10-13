# Itunda Investing — Toss Securities-Inspired Product Parity

**Purpose:** Build an investing experience with the clarity, discovery, feedback, and trust expected from Toss Securities, while retaining Itunda’s identity and Rwanda-first product choices. This is a capability benchmark, not a copy of Toss’s proprietary UI, code, branding, or assets.

## Source-backed baseline

- Toss Securities’ public product surface includes market indices, exchange and sector browsing, rankings/filters, market-moving news and schedules, price charts, investor activity, and AI summaries. Reference: https://www.tossinvest.com/
- Toss Securities’ published Open API describes six capability areas: OAuth authentication; market/stock/indicator/ranking/sector data; accounts/assets; order creation and lifecycle; conditional orders; and real-time trade/order-book/order-event WebSockets. Reference: https://developers.tossinvest.com/
- Itunda already has a wealth screen on web, Android, and iOS; an RSE catalog plus NASDAQ-labelled examples; market/portfolio/watchlist tabs; holdings and return calculations; stock history charts; buy/sell ledger flows; watchlists; and target-price alerts.
- Important current limitation: StockCatalog generates daily prices deterministically from symbol/date. These are **simulated prices**, not a live RSE or US exchange feed. Current buy/sell actions update Itunda’s internal ledger and do not route orders to an exchange or broker. The NASDAQ-labelled instruments are not a live-market integration.

## P0 — Trust and safe product boundaries

- [x] Show a clear, localized disclosure on investing and stock-detail screens: practice market, simulated prices, internal demo ledger only, no real exchange orders or securities ownership.
- [ ] Gate any production real-money stock purchase behind an approved brokerage/custody partner, real instrument identifiers, execution/settlement reconciliation, and explicit product/legal approval. Do not represent ledger-only trades as real brokerage.
- [ ] Add machine-readable market-data status (LIVE, DELAYED, SIMULATED, UNAVAILABLE), provider timestamp, exchange timezone, currency, and source to the API.
- [ ] Separate simulated balances/positions from any future real brokerage account and ledger. Never migrate demo positions into a live account as if they were securities.
- [ ] Add server-side kill switches for trading and market-data providers; fail closed if a live execution provider is unhealthy or instrument/price freshness checks fail.

## P1 — Toss-like core investing journey

- [ ] **Market home:** indexes, market status, search, popular/most-active/risers/fallers, sectors, and recently viewed instruments; empty and error states explain what is missing.
- [ ] **Instrument detail:** price and percentage move with period selector (1D/1W/1M/1Y/ALL), accessible chart, sourced open/high/low/volume, company profile, exchange, market status, and last-updated timestamp.
- [ ] **Order ticket:** buy/sell preview with estimated notional, available cash, sourced fees/taxes, quantity precision, order type, time-in-force, and final confirmation that summarizes exactly what will happen.
- [ ] **Order lifecycle:** submitted/accepted/partially-filled/filled/cancelled/rejected states, order history, trade confirmations, idempotency, and reconciliation.
- [ ] **Portfolio:** holdings, quantity, average cost, market value, realized/unrealized P&L, day change, allocation, cash, dividends, and explainable time-range performance.
- [ ] **Watchlist and alerts:** persist per user, edit/remove, deduplicate alert events, track delivery, and disclose whether alerts use simulated or stale prices.
- [ ] **Discovery:** search by symbol/company, popular themes/sectors, sourced corporate events/earnings calendar, explainable rankings, and no fabricated market headlines or AI summaries.

## P2 — Rwanda-first localization

- [ ] RWF-first displays for RSE assets; show source currency and FX rate/time for foreign assets.
- [ ] RSE instrument master from an authorized/reliable exchange or broker feed; verify ticker, tradability, lot size, hours, suspensions, corporate actions, and instrument identity before enabling orders.
- [ ] Kinyarwanda-first copy, then English and French; accessibility, large text, dark mode, reduced motion, low-bandwidth behavior, and offline/error states.
- [ ] Education for risk, diversification, fees, liquidity, volatility, and simulated versus executable prices.
- [ ] Suitability, identity, AML/KYC, retention, investor complaints, disclosures, and local securities-law review before a real-money launch.

## P3 — Platform and operations quality

- [ ] One versioned wealth API contract shared by web/Android/iOS; contract tests and backwards-compatible schema changes.
- [ ] Real-time quote/order events with reconnect/backoff, bounded subscriptions, stale-state indicators, and polling fallback where appropriate.
- [ ] Audit every order attempt/state transition and admin action; monitor quote freshness, order latency, rejection rate, provider uptime, reconciliation breaks, and alert delivery.
- [ ] Deterministic fixtures only in test/sandbox environments; never label fixtures or simulated returns as live market facts.
- [ ] CI gates: backend tests, web typecheck/build, Android wealth-module tests/build, iOS build, accessibility/localization checks, and provider-readiness checks before production deployment.

## Acceptance gates for Toss Securities-level quality

1. Users can tell whether data is live, delayed, simulated, or unavailable at every point.
2. Every quote has a source and timestamp; every order preview accurately describes its execution path.
3. Portfolio values reconcile to holdings and cash; every order is auditable and idempotent.
4. No simulated asset can be mistaken for a real security or silently carried into a production brokerage account.
5. Search, charts, portfolio, watchlist, alerts, order lifecycle, accessibility, localization, and error recovery work consistently on web, Android, and iOS.
6. Real-money trading is enabled only after brokerage/custody, market-data, settlement, and regulatory dependencies are verified. UI similarity alone does not satisfy parity.
