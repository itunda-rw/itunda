# Itunda Bank — source-audited transfer release gaps

Audit scope: current `main` source for `AccountService.kt`, `TransferQuote.kt`, `AccountController.kt`, and `IdempotencyService.kt`. This is a code inspection, not a live production test. Re-check behavior against the current commit before implementation.

## Confirmed from source

### P0 — Transfer quotes are process-local, not durable/distributed
`AccountService` constructs `private val quoteStore = QuoteStore()`; `QuoteStore` stores quotes in a `ConcurrentHashMap`. Therefore quote state is scoped to one application process. If quote creation and confirmation land on different Cloud Run instances, or an instance restarts between the two requests, confirmation can fail with “Transfer quote not found”. The atomic `computeIfPresent` claim only serializes callers that share that same in-memory map; it is not a cross-instance lock.

**Required fix:** persist quote state in the database with expiry, owner, and status, then use a conditional status transition such as `UPDATE ... WHERE id=? AND owner_id=? AND status='PENDING' AND expires_at > now`. Check affected-row count; only the winner may continue. Add multi-instance/restart and concurrent-confirm integration tests.

### P0 — External provider side effect and ledger write need a recoverable boundary
`confirmTransfer` calls `providerConnector.attempt(...)` before posting the ledger transaction. A database/ledger failure after a provider has accepted the operation can leave the external side effect and internal ledger out of sync; a normal database transaction cannot roll back an external provider action.

**Required fix:** define provider idempotency/reference semantics and a durable transfer state machine (e.g. `CREATED → SUBMITTED → SETTLED` or `FAILED/RECONCILIATION_REQUIRED`), persist provider reference, and implement retry/reconciliation. Do not show final success until authoritative business state is known. If the provider connector is currently a simulator, label it as such and do not represent it as a real bank rail.

### P1 — Quote fee/routing must be explicit before confirmation
The source currently computes a flat 1% fee and resolves the rail from a phone-number prefix. Code comments note that per-rail fee inference is not fully ported. Before live routing, return selected rail, fee, total debit, currency, quote expiry, and recipient confirmation data in the quote; bind confirmation to that immutable quote. Fees and supported rails must come from verified, configured product rules—not UI constants.

### P1 — Account balance and available balance semantics need tests
Quote creation checks `availableBalance`, while confirmation ultimately posts ledger legs. Add race tests proving concurrent transfers cannot overdraw the same account and that the balance returned to the client reflects committed ledger state. The authoritative ledger must enforce sufficient funds under the same database transaction/locking strategy as the debit.

## Required automated tests before release

- Two simultaneous confirms of one quote: exactly one debit/ledger transaction.
- Confirm quote on another application instance or after restart: durable quote remains usable once, never zero times or twice.
- Same idempotency key + same body: original result is replayed.
- Same key + different body: conflict; no side effect.
- Two different keys confirming the same quote: still at most one transfer.
- Provider accepted + ledger/database failure: durable reconciliation state and no false success.
- Provider timeout with unknown outcome: safe status lookup/retry, not a blind second transfer.
- Concurrent transfers whose combined amount exceeds available funds: at most the amount permitted by ledger rules is committed.
- Expired quote, wrong owner, provider decline, and insufficient funds: no debit and clear recoverable errors.

## Release gate

Do not call external money movement production-ready until persistent quote claiming, provider/ledger recovery, actual rail integration, operational reconciliation, monitoring, and end-to-end tests are verified. A passing build or in-memory concurrency test alone is insufficient.
