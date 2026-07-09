export const rateLimiting = {
  slug: 'rate-limit-the-phone-number-not-the-ip',
  title: "Rate limit the phone number, not the IP",
  date: '2026-06-21',
  author: 'Platform Security Team',
  tags: ['security', 'redis', 'backend'],
  excerpt:
    "IP-based rate limiting protects against the wrong attacker model for credential stuffing. The thing actually being brute-forced is one account's password — so that's what we key the limiter on.",
  content: `
Toss's own public write-up on their gateway architecture lists rate limiting as one of the gateway's core jobs, alongside auth and circuit breaking. We didn't have a separate gateway service — standing one up purely to rate-limit two endpoints would be solving a problem this app doesn't have yet at itunda's current scale — so the question was where this belongs instead, and what key to rate-limit on.

## Why IP is the wrong key

The default instinct is to rate-limit by source IP. For a real credential-stuffing attacker, that's close to useless: the asset actually being attacked is one specific phone number's password, and an attacker rotates source IPs — via proxies, botnets, mobile carrier NAT — long before they'd rotate their list of target accounts. IP-based limiting mostly inconveniences people on shared or carrier-grade NAT IPs (coincidentally the exact IP situation a lot of mobile users in Rwanda are actually behind) while doing very little to slow down the attack it's nominally defending against.

We rate-limit \`POST /auth/login\` and \`POST /auth/register\` keyed on phone number instead: 5 attempts per minute for login, 3 per 10 minutes for registration. This directly targets the thing being brute-forced, and it means a real attacker methodically working through a list of phone numbers gets slowed down per-number, which is the actual attack we're defending against — not incidentally penalizing everyone on the same IP as one attacker.

## Making the counter itself correct under concurrency

The naive version of a fixed-window counter — \`GET\` the current count, check it, \`INCR\`, set a TTL if this is the first request in the window — has a race: two concurrent requests can both read count \`0\`, both decide they're under the limit, and both proceed, with only one of them correctly setting the expiry.

We implemented this as a single atomic Redis Lua script instead: \`INCR\` and the conditional \`EXPIRE\`-if-this-is-the-first-hit both happen inside one script execution, so there's no window where two concurrent requests can both observe the pre-increment state. Redis executes Lua scripts atomically with respect to other commands, which is what actually closes the race — doing the same two operations as separate round-trips, even back to back, doesn't.

## What we verified

Five wrong-password login attempts for one phone number all correctly \`401\` (wrong credentials, but under the limit). A sixth correctly \`429\`s with \`RATE_LIMITED\`. A *different*, never-attempted phone number tried immediately after is unaffected — it gets a normal \`401\` for being unknown, not a \`429\` — confirming the limiter is scoped per-key and one account being hammered doesn't lock out unrelated accounts.

This closed a gap that had been sitting in our own security review for a while: there was no brute-force protection on login at all before this, on an authentication endpoint that — unlike most of this API — deliberately has to be reachable without a token.
`,
};
