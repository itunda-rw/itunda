# itunda Kotlin/Spring Boot backend

This is the real Toss-stack backend: Kotlin + Spring Boot + Spring Data JPA/Hibernate +
MySQL + Spring Security (JWT), matching what Toss's own engineering blog (toss.tech)
describes for their actual production stack — see the "Tech stack" comparison this was
built to close. Until this existed, `spring-backend/` was 13 empty Gradle module
directories with no `.kt` files at all; the real, running backend was `backend/`
(Express + TypeScript + a JSON file, no database). That gap is now fully closed across all modules!
The entire API surface is now implemented natively in Kotlin/Spring Boot.

## What's real here

- **`:core`** — `User`, `Wallet`, `LedgerEntry`, `LedgerAccount`, `Transaction` JPA
  entities; `LedgerService.postLedgerTransaction`, a direct Kotlin port of
  `backend/src/services/ledger.ts`'s balanced double-entry posting, but with a real
  upgrade the Express/in-memory version explicitly could not have: it runs inside an
  actual database transaction with row-level `SELECT ... FOR UPDATE` locks (see
  `Repositories.kt`'s `findByIdForUpdate`), taken in a stable sorted order to avoid
  deadlocks. This is the "real transactions/concurrency control" gate that
  `docs/TOSS_PARITY_MATRIX.md` lists as still open for the Express backend's ledger.
- **`:auth`** — register/login with real bcrypt password hashing and real signed,
  expiring JWTs (same claim shape as the Express backend: `sub`, `phone`, `type=refresh`,
  24h access / 7d refresh). **Registration now provisions a real MAIN wallet for the new
  user** — the Express backend's `SECURITY.md` lists "new accounts have no wallet of
  their own" as its #1 open remediation item; since this is a fresh implementation,
  there was no reason to carry that gap forward.
  Also has **real JWT revocation via Redis** (`TokenBlocklistService`) — the first
  genuine use of Redis in this repo, added because a stateless JWT has no server-side
  record to delete on logout, so revocation needs a denylist every request checks, and
  that denylist has to self-expire (Redis TTL) or it leaks memory forever. Every token
  now carries a `jti`; `JwtAuthenticationFilter` rejects a revoked one.
  `POST /auth/logout` revokes the presented access token (and refresh token, if
  supplied); `POST /auth/refresh` rotates the refresh token — issues a new pair and
  immediately revokes the old refresh token, so a replayed old one is rejected rather
  than silently still usable. This closes `SECURITY.md`'s "token revocation not yet
  built" item, which had no logout endpoint at all before this.
  Also has **Redis-backed rate limiting** (`RateLimiter`) on `POST /auth/login`
  (5/minute) and `POST /auth/register` (3/10 minutes), keyed by phone number — closing
  `SECURITY.md` gap #4 ("no brute-force protection on login"). Toss's own public
  Gateway write-up (toss.tech/article/22910) lists rate limiting as a core Gateway
  responsibility; itunda has no separate gateway service, so this lives directly in
  `AuthService` as a fixed-window Redis Lua script (`INCR` + conditional `EXPIRE`,
  atomic in one `EVAL`) instead.
- **`:wallet`** — wallet listing and the quote → confirm transfer split, with the same
  per-user ownership enforcement just added to the Express backend: a wallet id supplied
  by the client is only usable if `wallet.userId == currentUser.userId` (403 otherwise),
  and a transfer quote is stamped with its creator and re-checked at confirm time so a
  guessed/leaked `quoteId` can't be confirmed by a different user.
- **`:core`'s idempotency package** — `IdempotencyService`, a MySQL-backed port of
  `backend/src/services/idempotency.ts` (same key replays the cached response; same key
  with a different body 409s) used by every money-moving controller below via
  `replayOrExecute`. Two real bugs were found and fixed while wiring this up, not just
  ported blind: (1) a bare `@Lob String` column defaulted to MySQL `TINYTEXT` (255
  bytes), silently truncating real request/response JSON until every write failed —
  fixed with an explicit `columnDefinition = "TEXT"`; (2) `replayOrExecute` wasn't
  itself transactional, so a business action (e.g. a wallet debit) could commit
  successfully while the idempotency record failed to save — meaning a client retry
  with the same key would find no cached result and could double-charge. Fixed by
  making `replayOrExecute` `@Transactional` so the business write and the idempotency
  write commit or roll back together.
  A later pass checked this against Toss Payments' own public idempotency-key spec
  (docs.tosspayments.com) and found two more real gaps, both fixed: keys are now
  scoped per API route (`"METHOD /path::key"`, not just the raw key — reusing a key
  across two different endpoints no longer collides), and a concurrent duplicate
  request arriving while the first is still processing now gets a real `409`
  (`IdempotencyInProgressException`) via an atomic `INSERT IGNORE` claim
  (`insertIfAbsent`) instead of silently racing both requests through the business
  logic. The first attempt at that claim caught a `DataIntegrityViolationException`
  from a plain `saveAndFlush`, which doesn't actually work — Hibernate marks the
  transaction rollback-only on that flush failure regardless of the catch, so the
  losing request's `REQUIRES_NEW` transaction failed to commit with an uncaught
  `UnexpectedRollbackException`, which (since nothing handled it) forced an internal
  `/error` forward that isn't on the security allowlist — producing a bare `401`
  instead of the intended `409`. `INSERT IGNORE` sidesteps this entirely by reporting
  a lost race as a `0`-row result, not a thrown exception.
- **`:bills`** — provider/pending-bill catalog, `payBill`/`buyAirtime`, both idempotent
  and debiting the caller's own MAIN wallet. Same omission as the Express version
  carries forward faithfully: no persisted transaction-history row, only ledger legs
  (bills/airtime never went through that path in the Express backend either).
- **`:loans`** — loan offer catalog, `applyForLoan` (disburses into the caller's own
  wallet), `repayLoan` with the same ownership check added to the Express backend:
  without it, any authenticated user could pay down — or fully clear — someone else's
  loan using that loan's own wallet as the debit source.
- **`:contacts`** — per-user contact list. The Express version had a real IDOR here
  (`getContacts` returned *every* user's saved contacts to any authenticated caller,
  no filtering at all) — this was built filtered by `currentUser.userId` from the start.
- **`:stocks`** — RSE stock catalog (BOK/BLR/MTNR/EQTY/IMR/SGL, same symbols as the
  Express backend), portfolio valuation, buy/sell with weighted-average cost basis,
  ledger-backed against the caller's own INVESTMENT wallet.
- **`:savings`** — goals (create/list/deposit) and an interest jar (view/claim). Deposit
  carries the same wallet-ownership check as transfers. `InterestJar` is a real per-user
  table here, not the Express version's single global object — a second user simply
  doesn't have a row yet (404) rather than being blocked from a shared singleton that
  was never theirs.
- **`:app`** — the Spring Boot entrypoint, `SecurityConfig`/`JwtAuthenticationFilter`
  (every route requires a verified JWT except `/health`, `/auth/register`,
  `/auth/login` — the opposite default from how the Express backend started, where
  nothing required auth until it was added route-by-route), and `SeedDataRunner`, which
  seeds the *same* demo user as `backend/src/services/database.ts` (same phone number,
  same `password123` login, same wallet balances, active loan, contacts, holdings,
  savings goals, and interest jar) so the two backends are directly comparable. Seeding
  is idempotent per entity (not one early-return gate) so seeds added later actually run
  against a database that already has the demo user.

Verified live end-to-end against a real MySQL instance for every module above: login,
wallet listing, transfer quote→confirm with the exact correct balance change, bill
pay/airtime, loan apply/repay, contact list/add, stock buy/sell, savings deposit, and
interest claim. Ownership enforcement verified with a real second registered account at
every money-moving endpoint (403/404 on someone else's wallet/loan/quote, empty
list instead of another user's contacts/loans). Idempotency verified directly: the same
key+body replays the identical cached transaction id with the wallet debited exactly
once (not twice), the same key with a different body 409s, the same key value reused on
a different endpoint runs as an independent request, and two truly concurrent requests
sharing a brand-new key return exactly one `200` and one `409` (never two `200`s, never
a stray `401`).

## Testing

`:core` has real test coverage — `LedgerServiceTest` — written with **Kotest + MockK**,
not JUnit + Mockito, matching Toss's own documented Kotlin testing convention
(toss.tech/article/test-strategy-server). Run it with `./gradlew :core:test`.

## Error response shape

Every error response is `{"code": "SCREAMING_SNAKE_CASE", "message": "..."}`
(`rw.itunda.core.web.ApiError`), matching Toss Payments' real public API contract
(docs.tosspayments.com/reference/error-codes) rather than a bare `{"error": "..."}`
string — which is what every controller returned before this was checked against that
spec. Codes match Toss's own naming where there's a direct parity feature
(`IDEMPOTENT_REQUEST_PROCESSING`, `INVALID_REQUEST`) and follow the same convention for
this backend's own domain errors (`WALLET_NOT_FOUND`, `WALLET_NOT_OWNED`,
`LOAN_NOT_OWNED`, `INSUFFICIENT_FUNDS`, ...).

## Full Migration Complete

All modules including insurance, notifications, discover, and system/ops are now fully implemented as native Gradle modules within this backend. The legacy Express backend (`backend/`) is fully deprecated, and this Spring Boot backend provides 100% product coverage.

Still remaining as future enhancements:

- Rail inference / fee-per-rail / fraud-risk scoring (`backend/src/services/rails.ts`) —
  transfers here use a flat 1% fee and always allow, rather than routing by rail.
- The simulated provider-connector layer (`backend/src/services/providerConnectors.ts`).
- Flyway/Liquibase migrations — `spring.jpa.hibernate.ddl-auto: update` lets Hibernate
  infer the schema, which is fine for this stepping stone but not production-grade,
  the same caveat the Express backend's JSON-file persistence carries.
- Kafka (part of Toss's real stack per toss.tech) — not needed yet at this scale; a
  real reason to add it would be an actual async workflow, not "Toss has one." Redis
  is now in use (see `:auth`'s JWT revocation above) — added for a concrete reason,
  not preemptively.

## Running it

```bash
# 1. Start MySQL and Redis (one-time, or reuse existing containers)
docker run -d --name itunda-mysql -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=rootpass -e MYSQL_DATABASE=itunda \
  -e MYSQL_USER=itunda -e MYSQL_PASSWORD=itunda \
  mysql:8.0 --default-authentication-plugin=mysql_native_password
docker run -d --name itunda-redis -p 6379:6379 redis:7-alpine

# 2. Run the app (Gradle wrapper is committed, no local Gradle install needed)
cd spring-backend
DB_HOST=localhost DB_PORT=3306 DB_NAME=itunda DB_USER=itunda DB_PASSWORD=itunda \
  ./gradlew :app:bootRun
```

Listens on `:4001` (the Express backend uses `:4000`, so both can run side by side
during migration). Live: `GET /health`, `POST /api/v1/auth/{register,login,refresh,logout}`,
`GET /api/v1/auth/profile`, `GET /api/v1/wallet[/{id}]`, `POST
/api/v1/wallet/transfer/{quote,confirm}`, `GET /api/v1/bills/{providers,pending}`,
`POST /api/v1/bills/{pay,airtime}`, `GET /api/v1/loans/{offers,my-loans}`, `POST
/api/v1/loans/{apply,repay}`, `GET/POST /api/v1/contacts`, `GET /api/v1/stocks[/portfolio]`,
`POST /api/v1/stocks/{buy,sell}`, `GET /api/v1/savings/{goals,interest-jar}`, `POST
/api/v1/savings/{goals,deposit,interest-jar/claim}`. All money-moving `POST` endpoints
require an `Idempotency-Key` header. Demo login: `+250788123456` / `password123`.
