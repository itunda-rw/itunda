# Itunda Bank — durable external-transfer recovery design

Status: implementation specification; **not implemented and not release approval**.

This document is the required design gate after durable quote persistence. It addresses the failure boundary between an external provider and Itunda's MySQL ledger. A SQL transaction cannot roll back an external payment.

## Current confirmed risk

`AccountService.confirmTransfer` currently claims a quote, invokes `ProviderConnector.attempt`, posts ledger entries, and marks the quote confirmed in one Spring transaction. If the provider has accepted a real transfer and a later ledger write fails, MySQL can roll back the quote claim and ledger changes while the external effect remains. A retry could then duplicate the transfer. The current connector is simulated, not a live MTN/Airtel/bank integration, so this is a source-level risk analysis rather than evidence of a live incident.

The existing `provider_attempt_log` records simulated rail attempts for aggregate operational reporting. It does **not** identify a transfer attempt, persist a provider idempotency key/reference, or recover an ambiguous transfer outcome.

## Required durable records

### Transfer attempt

Persist one record per submission attempt with at least:

- `id`: immutable internal transfer/attempt identifier;
- `quote_id`: reference to the immutable, owner-bound transfer quote;
- `attempt_number`: monotonically increasing per quote;
- `idempotency_key`: unique, stable key sent to the provider when its contract supports it;
- `provider_reference`: nullable until the provider returns one;
- `state`: `PREPARED`, `SUBMITTED`, `PROVIDER_ACCEPTED`, `SETTLED`, `FAILED_FINAL`, or `RECONCILIATION_REQUIRED`;
- `request_hash`: binds retries to the same amount, currency, recipient and quote;
- `last_error_code`, `created_at`, `updated_at`, `submitted_at`, `settled_at`, `next_reconcile_at`;
- unique constraints for `(quote_id, attempt_number)` and the provider idempotency key within its provider scope.

Do not store full secrets, PINs, credentials, or unnecessary payment data in the record or logs.

### State transition rules

- `PREPARED → SUBMITTED`: commit the durable intent before calling the provider.
- `SUBMITTED → PROVIDER_ACCEPTED`: record a definitive acceptance and provider reference.
- `PROVIDER_ACCEPTED → SETTLED`: post balanced ledger entries exactly once and atomically persist the ledger result plus settled state where they share the same database.
- `SUBMITTED → RECONCILIATION_REQUIRED`: timeout, process crash, or any outcome that does not prove acceptance or rejection.
- `PROVIDER_ACCEPTED → RECONCILIATION_REQUIRED`: ledger persistence fails after provider acceptance; never release the quote for a fresh external submission.
- `SUBMITTED → FAILED_FINAL`: only a definitive provider rejection, never a timeout or connection reset.
- `RECONCILIATION_REQUIRED → SETTLED/FAILED_FINAL`: only after provider status lookup or an auditable operator resolution establishes the outcome.

Transitions must use conditional updates and verify affected-row counts. No blind retry is permitted for an unknown outcome.

## Transaction and service boundaries

1. Validate the authenticated owner and immutable quote.
2. In a short, separately committed database transaction, create/claim a durable transfer attempt and stable idempotency key.
3. Submit to the provider only after the intent is durable. Use provider idempotency if documented and supported.
4. Persist definitive provider outcome in an independent transaction. If the process dies before recording it, the durable `SUBMITTED` state causes reconciliation—not a new payment.
5. On confirmed acceptance, post ledger legs and settle the attempt with exactly-once database constraints/locking. The ledger transaction ID must be unique to the transfer attempt.
6. On ambiguous outcome, keep the quote/attempt non-retryable and surface a pending/reconciling state to the customer. Do not show success until authoritative state is known.
7. Run a scheduled reconciliation worker with bounded retries, backoff, alerting for aged unresolved attempts, and an auditable manual-resolution path.

Do not try to achieve this by adding `REQUIRES_NEW` calls casually inside the existing `@Transactional confirmTransfer`: proxy boundaries, rollback semantics, and lock lifetime must be tested. Prefer an explicit orchestration service whose committed phases are visible in code.

## Required tests before enabling any real rail

- Two independent connections concurrently confirm the same quote: one provider submission maximum.
- Crash after intent commit but before provider call: reconciliation can safely determine whether submission occurred.
- Provider accepts, response is lost: no second transfer; status lookup/idempotency recovers the original result.
- Provider accepts, ledger commit fails: attempt remains recoverable and quote cannot be resubmitted.
- Provider definitively declines: no debit; retry policy is explicit and bounded.
- Same idempotency key and same payload returns the same logical result; same key with a different payload is rejected.
- Duplicate provider callbacks/status responses do not duplicate ledger entries.
- Concurrent transfers against one balance cannot overdraw the account.
- Migration from a clean database and from a database with the existing `transfer_quotes` table both succeeds.
- Failure-injection integration tests use MySQL/InnoDB, not only mocked repositories or an in-memory database.

## Release gates

- [ ] Confirm actual provider contract: idempotency scope, status lookup, acceptance/settlement semantics, timeout behavior, reversals and callback authenticity.
- [ ] Implement attempt table/entity/repository and conditional state transitions.
- [ ] Implement separate transaction boundaries and exactly-once ledger linkage.
- [ ] Implement scheduled reconciliation, metrics, alerts and operator audit trail.
- [ ] Pass CI, MySQL concurrency tests and failure-injection tests.
- [ ] Verify customer UI correctly distinguishes pending, completed and failed outcomes.
- [ ] Keep real external money movement disabled until every gate is evidenced.

## Honest scope boundary

Toss Bank is a reference for clarity, transparent status, simple flows and strong recovery. It is not evidence that Itunda has equivalent banking licenses, provider contracts, products, pricing or settlement infrastructure. Current Itunda provider behavior is simulated. This specification does not claim implementation, provider integration, successful tests, or production readiness.
