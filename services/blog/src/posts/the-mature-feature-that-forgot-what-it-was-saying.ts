export const theMatureFeatureThatForgotWhatItWasSaying = {
  slug: 'the-mature-feature-that-forgot-what-it-was-saying',
  title: "Our most mature feature, audited on a hunch from a conference title — and one real gap survived three separate teams checking it",
  date: '2026-08-08',
  author: 'Backend Team',
  tags: ['backend', 'web', 'android', 'ios', 'ux', 'incident-writeup'],
  excerpt:
    "A recovered session title — 'adding innovation upon innovation,' about keeping an already-shipped transfer feature from stagnating — was enough to send us auditing our own core P2P transfer flow from scratch. Most of it held up. One real gap didn't: two platforms had quietly agreed on a worse error message than the third, and nobody had ever compared them side by side.",
  content: `
Sixth time back into the same conference archive. This pass finally found the trick that had eluded five previous attempts: fetching a Simplicity conference's raw listing page directly, instead of searching for it or asking for individual session pages, renders the full session list as plain server-rendered text even when everything else on the site sits behind a login or a video wall. It worked immediately for Simplicity23 (21 session titles, up from 4) and filled in 13 more of Simplicity21's roster. One new title stood out from the rest: "혁신에 혁신 더하기" — adding innovation upon innovation — a talk about keeping Toss's own money-transfer feature from calcifying years after it shipped and stopped being anyone's active project.

We don't have the talk. We have the title. That was enough reason to go run the same audit on our own transfer flow, on the theory that a feature old enough to be "done" is exactly the kind of code this project keeps finding real drift in when someone finally looks again.

## What we expected to find, and mostly didn't

We went in skeptical on purpose — not hunting for a bug with a conclusion already picked out, actually checking. Four things, no assumptions:

Does a declined transfer ever get silently treated as a success, the way the account-linking bug from two passes ago did? No. Every real decline path — recipient not found, insufficient funds, rate limit, self-payment, a frozen wallet, a family spend limit, an idempotency conflict — throws a real, distinct exception that maps to a real HTTP error. The idempotency service releases its claim and rethrows on any failure inside the guarded action; it never writes a fabricated success record. This is clean.

Is the Idempotency-Key header present and consistent everywhere? Yes, on all three clients, generated fresh per attempt, exactly as required.

Any hardcoded values that should be shared constants? One, small: a scam-warning box on web hardcoded a raw hex color where Android already had a real design-system token for the same role.

Three checks, three clean or minor results. The fourth wasn't.

## The one that wasn't clean

The backend already sends specific, real text for every decline reason — "Spend limit exceeded," "Too many attempts, please try again later," the actual thing that happened. Web reads and shows that real text. It always has.

Android and iOS don't. Both platforms have their own private function that turns a failed transfer into a message for the user, and both of those functions are near-identical, independently hand-written switch statements over the HTTP status code alone — four cases, then a generic fallback for everything else. That fallback silently swallows a self-payment rejection, a frozen-wallet block, a family-spend-limit cap, and a rate limit. A parent whose child just hit their weekly allowance cap sees "Something went wrong. Please try again." on their phone. On the web dashboard, the same decline shows the real reason.

Nobody wrote this bug on purpose. Someone wrote a reasonable four-case switch statement once, on one platform, and someone else — working from the same spec, building the same feature on a different platform around the same time — wrote a nearly identical one independently, matching it case for case, fallback for fallback. Two platforms agreeing with each other made the gap between them and the third platform easy to miss, because two out of three felt like consensus. It wasn't; it was two people solving the same problem the same slightly-incomplete way, without either one checking what the third client already had working.

## What we shipped

Both native platforms now check for the backend's real message before falling back to their own generic status-code table, instead of skipping straight past it:

\`\`\`kotlin
private fun backendErrorMessage(e: retrofit2.HttpException): String =
    apiErrorMessage(e) ?: when (e.code()) {
        422 -> "Insufficient funds for this amount."
        404 -> "That account or goal couldn't be found."
        409 -> "This request is already being processed."
        502 -> "The payment provider declined this transaction."
        else -> "Something went wrong. Please try again."
    }
\`\`\`

iOS needed a different shape of fix. Its networking layer's error type carries only a status code, by design — widening it to also carry the real error text would mean touching every one of the sixty-plus places across the app that already pattern-match on that exact shape, a change a much older comment in the same file had already flagged and deliberately deferred as too broad for a single fix. So instead of touching the shared type, we gave the one flow that needed it its own small, dedicated request path — the same move this codebase had already made once before for a different endpoint that needed extra decoding. One flow gets the richer error. Sixty-plus others stay exactly as they were.

We also gave web's scam-warning box a real token instead of a hardcoded hex value, matching the danger-tint color Android's design system already had a name for.

## Actually checking the checking

The last two research passes both ended their "verified" section with some version of "iOS reviewed by hand, no local Swift toolchain in this environment." Nobody had actually tried. This time we did: \`swift\`, \`swiftc\`, and a real Xcode installation are all sitting right there, and the iOS project has a real scheme per module. The module containing the file we changed built clean, standalone, with zero errors. The full app doesn't currently link — for reasons that have nothing to do with this change, a handful of native dependencies were never pod-installed in this environment — but that's a separate, pre-existing gap, not a reason to skip verifying the part we could actually verify. We'd been quietly settling for less rigor on one platform than the other two for at least two passes, on an assumption nobody had checked.

## What's still open

Five more session titles came out of this pass with no content behind them yet — a credit-card application flow, a loan experience, an investing onboarding, an insurance flow, a merchant revenue dashboard — each a real itunda feature, each worth the same kind of walk-through that found this bug and the account-linking one before it. We checked one flow this time, thoroughly, instead of six flows shallowly. That felt like the more honest trade.
`,
};
