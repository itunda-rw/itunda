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


## Implementation sequence (do not skip phases)

### Phase A — durable quote and single-winner claim

Add a Flyway migration under `services/backend/app/src/main/resources/db/migration/` (never edit an applied migration) and a JPA entity/repository discoverable by the canonical `:app` application. The persisted quote must include:

- opaque quote ID (primary key), authenticated owner ID, source account ID;
- recipient, amount, fee, total debit, currency;
- status, creation time, expiry time, and timestamps for claim/confirmation;
- an index for expiry cleanup and a checkable owner/status/expiry predicate.

Use the database clock or a single explicitly injected clock policy consistently. Claim with one conditional database update, not a read-then-write:

```sql
UPDATE transfer_quotes
SET status = 'CLAIMED', claimed_at = CURRENT_TIMESTAMP(6)
WHERE id = :id
  AND user_id = :authenticated_user_id
  AND status = 'PENDING'
  AND expires_at > CURRENT_TIMESTAMP(6);
```

Only an affected-row count of exactly one grants permission to proceed. Zero rows means missing, wrong-owner, expired, or already claimed; map responses without leaking whether another user's quote exists. The primary guarantee is MySQL/InnoDB state shared across Cloud Run replicas, not JVM locks. Do not introduce Redis as the correctness lock.

### Phase B — provider/ledger recovery (required before any real rail)

A durable quote alone does not close the provider/ledger gap. Introduce a durable transfer-attempt record with a unique internal transfer ID, unique idempotency key at the provider boundary where supported, provider reference, state, retry metadata, and reconciliation timestamps. Persist the transition to `SUBMITTED` before making the provider call. On provider timeout/unknown outcome, mark `RECONCILIATION_REQUIRED`; do not release the quote to `PENDING` and blindly resubmit. On confirmed provider acceptance, post ledger entries exactly once under a database transaction and persist the final state atomically with the ledger result where the schema permits. If provider and ledger cannot share a transaction, use an explicit saga/reconciliation worker; never imply a normal SQL rollback undoes a provider operation.

The current `SimulatedProviderConnector` is not a live bank/mobile-money integration. A production connector needs documented provider idempotency, status-query, timeout, settlement, and reversal semantics before this phase can be considered complete.

### Phase C — verification gates

1. Unit: quote owner, expiry, immutable amount/fee/currency, valid state transitions.
2. MySQL integration: concurrent conditional claims from independent transactions/connections; exactly one row update wins.
3. Multi-instance: quote created on instance A and confirmed on instance B; restart between quote and confirm.
4. Money safety: same quote with different idempotency keys still posts at most one ledger transaction; repeated same key replays the original response.
5. Failure injection: provider decline, timeout before acceptance, timeout after acceptance, ledger commit failure, process crash after provider acceptance, and reconciliation retry.
6. Operational: dashboards/alerts for stuck submitted transfers, age of reconciliation queue, provider-vs-ledger mismatch, and an auditable operator workflow.

Do not ship Phase A alone as proof that external transfers are production-safe. It fixes quote durability and distributed claiming, but Phase B remains a separate P0 release gate.
