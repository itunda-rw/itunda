export const jwtRevocation = {
  slug: 'why-your-jwt-needs-a-logout-button',
  title: "Why a stateless JWT needs a very stateful logout button",
  date: '2026-06-20',
  author: 'Ledger Platform Team',
  tags: ['security', 'redis', 'backend'],
  excerpt:
    "A JWT is stateless by design, which is exactly why logging out of one is harder than it sounds. Here's the denylist-with-TTL pattern we ended up with, and why plain deletion isn't an option.",
  content: `
Rotating to signed JWTs from a hardcoded mock token was the easy part. The question that came right after was: given a signed, self-contained token that's valid until it expires, what does "log out" actually mean?

With a server-side session, logout is a delete. With a stateless JWT, there is nothing on the server to delete — the token is its own proof of validity until its \`exp\` claim passes, and the server was never storing it in the first place. "Stateless" is the entire design point of a JWT; it's also exactly why revoking one early requires *adding* state back in, deliberately, for this one case.

## The pattern: a denylist, not an allowlist

We didn't want to track every valid token (that's just reinventing server-side sessions with extra steps). Instead: every issued token carries a unique \`jti\` claim. On logout, we write that \`jti\` to Redis with a TTL equal to the token's remaining lifetime. \`JwtAuthenticationFilter\` checks the denylist on every request, in addition to signature and expiry — reject if the \`jti\` is present.

The TTL is what makes this cheap. A denylist that never forgets anything grows forever as a pure function of login volume. Setting the Redis key's TTL to match the token's own remaining lifetime means an entry only has to exist for exactly as long as the token it's blocking would otherwise still be valid — once the token would have expired anyway, the denylist entry expires with it, with no batch cleanup job required.

## Refresh token rotation

The same mechanism does double duty for refresh tokens. \`POST /auth/refresh\` issues a new access+refresh pair and immediately revokes the *old* refresh token. That means a stolen-but-already-rotated refresh token isn't a silent, indefinitely-reusable credential — it's a detectable replay, rejected the moment someone tries to use it after the legitimate client already rotated past it.

We verified this end to end against a real Redis container: logged in, used the access token successfully, called logout, and confirmed the *same* access token now correctly 401s. Separately: refreshed with the original refresh token (got a new working access token), then tried to refresh again with that same original refresh token and confirmed it's rejected as already used, rather than quietly working a second time.

## What this closed

Before this, there was no logout endpoint at all — an issued token was valid for its full lifetime with no way to shorten that, which meant a compromised token had no remediation path short of waiting it out. This is a small amount of Redis usage for a real answer to "how do I revoke a JWT," and it's the first genuinely load-bearing use of Redis anywhere in this stack — added because a stateless JWT concretely needed it, not because it looked good on an architecture diagram.
`,
};
