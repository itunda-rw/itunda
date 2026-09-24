export const idempotencyKeys = {
  slug: 'idempotency-keys-what-we-got-wrong',
  title: 'Idempotency keys: what we got wrong the first time',
  date: '2026-06-18',
  author: 'Ledger Platform Team',
  tags: ['ledger', 'kotlin', 'reliability'],
  image: '/images/posts/idempotency-keys-what-we-got-wrong/hero.webp',
  imageAlt: 'Idempotency and duplicate payment request flow',
  excerpt:
    "We scoped idempotency keys globally instead of per-route, and our first fix for concurrent duplicate requests introduced a second bug that turned a 409 into a bare 401. Here's how we found both, in that order.",
  content: `
Every money-moving endpoint in itunda accepts an \`Idempotency-Key\` header, the same convention [Toss Payments documents publicly](https://docs.tosspayments.com/reference/error-codes) for their own API. A client retries a request with the same key after a timeout; the server either replays the original result or returns \`409 IDEMPOTENT_REQUEST_PROCESSING\` if the first attempt hasn't finished yet. That part of the design was right from the start. Two things underneath it weren't.

## Bug 1: keys were global, not scoped per route

Our first implementation kept one idempotency table, keyed only by the header value the client sent. That's fine until two different endpoints get called with the same key — which happens more often than you'd hope, especially from clients that generate keys from a simple counter or timestamp rather than a UUID.

We found this by deliberately replaying the same key across two unrelated endpoints — \`bills/pay\` and \`loans/apply\` — and watching the second one come back with the first one's cached bill-payment response. Nothing about that is idempotent; it's just a collision.

The fix was to make the record's real primary key \`"$method $path::$key"\` instead of bare \`$key\`. Obvious in hindsight. We verified it by reusing the same key value across two different endpoints and confirming both ran as independent requests instead of one shadowing the other.

## Bug 2: the race-condition fix broke the status code

The actual hard part was two truly concurrent requests sharing the same key before the first one finishes. Our first attempt: try to \`INSERT\`, catch the unique-constraint violation if someone beat us to it, treat that as "someone else is already processing this" and return 409.

Sounds reasonable. It broke in a specific way: the *losing* request's failed insert happened inside a \`@Transactional(propagation = REQUIRES_NEW)\` block. Catching the \`DataIntegrityViolationException\` didn't clear Spring's internal rollback-only flag on that inner transaction — so when Spring went to commit it, it threw \`UnexpectedRollbackException\` instead. Nothing in our stack caught *that*, so it fell through as an uncaught exception, which our global error handler forwarded to \`/error\` — a path that, as it turns out, wasn't on our security filter's public allowlist. The forward hit auth middleware, got rejected, and the client that should have seen \`409\` instead got a bare \`401\` with no useful body at all.

That's the same failure shape we'd already hit once before with a Jackson deserialization bug: uncaught exception → internal forward to \`/error\` → blocked by the security filter → misleading status code. Once we recognized the pattern, the fix was straightforward: stop relying on catching a constraint violation inside a transaction at all. \`INSERT IGNORE\` (via a native query) reports a lost race as a \`0\`-row return value — a normal, successful statement — instead of throwing anything. No exception, no rollback-only flag, no surprise 401.

We verified this by firing two genuinely concurrent identical requests at the same endpoint and confirming we get exactly one \`200\` and one \`409\` — never two \`200\`s, and never another mystery \`401\`.

## The lesson that generalized

The actual takeaway wasn't about idempotency specifically — it was that *any* uncaught exception in this stack silently becomes a \`401\` via the \`/error\` forward, which makes debugging genuinely confusing unless you know to look for it. We now check for this specific failure shape first whenever a client reports "I expected an error code and got 401 instead."
`,
};
