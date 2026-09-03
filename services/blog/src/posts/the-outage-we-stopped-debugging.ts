export const theOutageWeStoppedDebugging = {
  slug: 'the-outage-we-stopped-debugging',
  title: "The outage we stopped debugging because we assumed it would always be there",
  date: '2026-09-03',
  author: 'Platform Team',
  tags: ['ci', 'incident-writeup', 'infrastructure', 'process'],
  excerpt:
    "GitHub Actions failed instantly on every run starting 2026-08-15, annotated with a real billing error. Someone diagnosed it correctly, flagged it, and moved on. Weeks later it turned out the billing issue had been resolved — and nobody had re-checked, so every real CI failure since had been silently absorbed into 'it's billing, not fixable from code.'",
  content: `
On 2026-08-15, every CI run on this repo's long-lived working branch started failing in 2-6 seconds. \`gh run view\` on the failing runs showed the same annotation on every job: "The job was not started because recent account payments have failed or your spending limit needs to be increased." That's not a flaky test or a bad config — that's GitHub refusing to schedule the job at all. Someone diagnosed it correctly the same day, wrote it down, flagged it to the account owner, and moved on to other work. Correct diagnosis, correct triage, nothing left to do from inside the repo.

The note they left behind was reasonable: *"don't claim something is CI-verified until this is confirmed fixed. Keep using local verification as the substitute bar in the meantime."* That's a good rule for the day it was written.

## The rule outlived the problem

At some point between then and now, the billing issue got resolved. We don't know exactly when, because nobody was watching for it — there was no reason to watch for it, since the working assumption was settled: CI is blocked, it's not a code problem, wait for the account owner. Every session since treated that as background truth. "CI-wired" in a commit message meant "the workflow file has the right steps in it and passes when I run the equivalent commands by hand" — never "a real GitHub Actions run actually went green."

That's not a small gap. It's weeks of real commits landing on a real branch, with a real, growing CI configuration, where the one thing that would have caught an environment-specific bug — an environment nobody's laptop reproduces — was switched off by assumption, not by fact.

## What was actually happening this whole time

We went back to check. Not because anything prompted it specifically — it came up while working through an unrelated backlog of PR maintenance, and checking CI status was step one of that, the same as it would be for any PR. \`gh run view --log\` on the most recent failing run didn't show a 2-6 second billing rejection. It showed real, multi-minute builds, with real Gradle and Yarn output, failing on real errors partway through. The billing block was gone. Nobody had looked long enough to notice.

Once we started reading the actual logs instead of trusting the old diagnosis, five separate, unrelated, completely mundane bugs came out, one job at a time:

**Corepack.** \`package.json\` pins \`"packageManager": "yarn@4.17.0"\`, which is Corepack-managed — but the runner's global Yarn was the old classic 1.22.22, and nothing in the workflow ever ran \`corepack enable\`. Every \`yarn install --immutable\` failed before installing a single package.

**A JVM target mismatch.** The backend's own \`build.gradle.kts\` pins Kotlin's \`jvmTarget\` to 17. The workflow's \`backend-build\` job set up JDK 21. Nothing pins \`compileJava\`'s target the same way, so it silently followed whatever JDK was active — 21 in CI, 17 on every developer's own machine, which is why nobody had seen this locally. Gradle's own error name for this is refreshingly blunt: "Inconsistent JVM-target compatibility."

**A missing install.** Android's \`settings.gradle.kts\` applies a Gradle plugin that lives inside a separate npm workspace's \`node_modules\`. The iOS job already installed that workspace's dependencies first; the Android job never did. It failed at settings-evaluation time, before compiling a single Kotlin file.

**A gitignored, regenerated directory nobody regenerated.** One mobile bridge module's iOS integration depends on a folder that's deliberately not checked into git — it's generated per mini-app spec, and the project's own internal docs already say how to regenerate it. CI never ran that command. A fresh checkout just never had the folder.

**Memory pressure that looked like a hang.** Once the first four fixes cleared the way, Android's \`assembleDebug\` step — the first time it had ever actually run in CI at all — sat at "in progress" for over an hour, then the whole job died with no error and no captured log. The project's own \`gradle.properties\` requests a 6GB JVM heap, tuned for a developer's own machine. On the shared runner, that heap request plus a second, independently-sized Kotlin compiler daemon left no real headroom, and the build never got to finish before something killed it. A conservative CI-only heap override, plus an explicit job timeout so a future stall fails cleanly instead of vanishing, fixed it.

Every one of these is a normal, forgettable CI bug. None of them is interesting on its own. What's interesting is that all five sat there for weeks, actively failing, on a branch under active, daily development — completely invisible, because "it's billing, not fixable from code" doesn't invite a second look.

## The part that bit us again, an hour later

With all 13 jobs green for the first time, we did one more obvious thing: none of the jobs cached anything. Every run downloaded every dependency from scratch. We added the standard built-in caching each setup action already supports — and broke \`lint-and-typecheck\` immediately. The Node setup action's own \`cache: yarn\` option shells out to \`yarn cache dir\` as part of *its own* execution, before the separate "enable Corepack" step later in the same job ever runs. It hit the exact same Corepack error we'd just spent an hour fixing, one step earlier in the chain. We reverted just that piece rather than chase a manual cache key for a package manager whose real cache location in this repo isn't even confirmed (there's no \`.yarnrc.yml\`, no \`.yarn/cache\` checked in) — this was a nice-to-have, not a requirement, and there's no reason to guess at something nobody asked for. The npm and Gradle caching from the same change were unaffected and stayed in.

## What actually caught a real bug

The newly-working \`backend-build\` job immediately found something no local run ever had: a merchant test asserting a payment intent expires in *exactly* 900 seconds, intermittently failing. The code computed the expiry and the creation timestamp from two separate \`Instant.now()\` calls — fine on a fast, quiet laptop, where those two calls land close enough together that the difference is always exactly 900 to the second. On a shared, contended CI runner, a scheduling gap between them could push the difference off by a second in either direction. The fix was small — capture one timestamp, derive both fields from it — but it's a real correctness bug that had been sitting in the code the whole time CI was assumed broken, waiting for an environment slow enough to expose it. A pipeline nobody trusted had, within an hour of actually running again, done exactly the job a CI pipeline is for.

## The part worth remembering

Every individual fix here is unremarkable. The mistake that let five of them accumulate wasn't. "This is blocked by something outside the repo" is a fact about a moment, not a fact about forever, and it has a much shorter shelf life than it's comfortable to assume. The cost of re-checking it — one \`gh run view --log\` — is measured in seconds. The cost of not re-checking it, this time, was every real commit on this branch for weeks running with its actual safety net quietly switched off, and nobody finding out until someone happened to look for an unrelated reason. If a diagnosis says "not fixable from here, it's external" — write down when you checked, and treat that note as something with an expiration date, not a fact you get to stop verifying.
`,
};
