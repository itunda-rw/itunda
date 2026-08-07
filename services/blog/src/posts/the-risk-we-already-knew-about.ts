export const theRiskWeAlreadyKnewAbout = {
  slug: 'the-risk-we-already-knew-about',
  title: "Nine copies of the same known risk, and a Toss Bank talk on account-number design that made us finally fix it",
  date: '2026-08-07',
  author: 'Backend Team',
  tags: ['backend', 'reliability', 'research', 'incident-writeup'],
  excerpt:
    "We didn't discover this bug by tripping over it in production. We found it by reading a conference talk about how Toss Bank designs account numbers, then going and checking whether our own account-number generator held up against the same standard. It didn't — and one of our own code comments had already said so, out loud, and shipped anyway.",
  content: `
A few weeks back we researched Toss's SLASH developer conference and found real, load-bearing engineering patterns already validated against our own codebase — a compensating-transaction design we correctly judged not applicable yet, an idempotency spec we already matched exactly. We treated that as the end of the SLASH thread. It wasn't. When we went back and pushed past the parts of SLASH21 and SLASH22 we'd written off as PDF-locked and unreachable, one specific talk — Toss Bank's own real account-number design philosophy — turned into the most concrete bug this whole research effort has found.

## What the talk actually said

Toss Bank's real schema-design talk from SLASH21 covers a genuinely mundane-sounding topic: how do you pick an account number. Their answer wasn't "pick something unique enough" — it was a deliberate, sized decision. Eight digits, a three-digit account-type code, one check digit, for a total addressable space of 100 million accounts, explicitly capacity-planned against roughly 27 years of projected volume. ID generation preferred database sequences over random values specifically because sequences don't collide and don't contend for locks under concurrent writes. This is the kind of detail that's easy to skim past in a conference recap — "yeah, account numbers, sure" — until you go check whether your own system actually does anything like it.

## What we actually had

We went and checked. Nine separate backend services — account registration, group accounts, ikimina rotating savings, SACCO shares, 26-week savings plans, upfront-interest deposits, foreign-currency wallets, the youth Mini wallet, merchant business accounts — each carried its own private \`generateAccountNumber()\` function. Every single one looked like this:

\`\`\`kotlin
private fun generateAccountNumber(): String =
    (2024100000L + (Math.random() * 900000).toLong()).toString()
\`\`\`

A prefix identifying the account type, plus a random offset into a 900,000-number range. No sequence. No check against what already exists. Just \`Math.random()\` and hope. Three of the nine — new-user registration, group accounts, and the Mini wallet — didn't even have their own separate range; they all drew from the exact same 2024100000–2024999999 pool, simultaneously, from three completely different code paths that had no idea the other two existed.

## The part that actually stung

We have a real, enforced \`UNIQUE(account_number)\` constraint on the wallets table. So a collision was never going to corrupt data — the database would catch it. What it wouldn't do is catch it gracefully. A collision would surface as a raw \`DataIntegrityViolationException\`, unhandled, on whichever request happened to lose the race. Not a retry. Not a clear error. Just a 500 for some unlucky user opening a savings account at the wrong microsecond.

And here's the detail that made this different from an oversight: one of the nine copies had a comment sitting directly above it.

\`\`\`kotlin
// No collision-avoidance loop, same accepted-risk precedent
// AuthService.generateAccountNumber already establishes for this codebase.
private fun generateAccountNumber(): String = (2024100000L + (Math.random() * 900000).toLong()).toString()
\`\`\`

This wasn't a bug nobody noticed. It was a risk somebody noticed, decided was probably fine, wrote down as a deliberate precedent, and then let three more services quietly inherit without anyone re-checking whether "probably fine" was still true at nine copies deep and three of them sharing a number pool. Every individual decision was locally reasonable — a 900,000-number range does sound big, and the odds of any single collision on any single day are genuinely low. What nobody did was add up nine copies of that same locally-reasonable bet and ask what the actual combined odds looked like, or notice that reusing a number range wasn't the same as reasoning about a fresh range each time.

## The fix, and why it's one thing instead of nine

\`\`\`kotlin
@Component
class AccountNumberGenerator(private val walletRepository: WalletRepository) {
    fun generate(prefix: Long, rangeSize: Long = 900_000L): String {
        repeat(10) {
            val candidate = (prefix + Random.nextLong(rangeSize)).toString()
            if (walletRepository.findByAccountNumber(candidate) == null) return candidate
        }
        throw IllegalStateException("Could not generate a unique account number in the range starting at $prefix after 10 attempts")
    }
}
\`\`\`

Ten lines, one shared component, a real existence check against the same table every one of the nine callers already writes into. Each service keeps its own account-type prefix — that part of the original design was already correct, it's itunda's own real equivalent of Toss's type-code scheme — but the number *within* that range is no longer a guess. Nine call sites now go through one path instead of nine independent ones, which matters for a second reason beyond fixing today's bug: the next person who needs a tenth account type doesn't get to accidentally reintroduce this, because there's no longer a "just copy the private function from the file next to yours" temptation sitting there.

## Why this is the useful kind of research finding

We didn't find this by load-testing account creation or by a user reporting a weird error. We found it by reading how a real bank thinks about a problem we thought we'd already solved adequately, then going and checking our own reasoning against theirs instead of assuming a topic we'd shipped code for was a topic we'd actually finished thinking about. The account number itself was never the interesting part of either team's product. Whether nine copies of the same convenient shortcut can survive contact with real concurrent users — that's the part worth reading someone else's real answer to before deciding your own was good enough.
`,
};
