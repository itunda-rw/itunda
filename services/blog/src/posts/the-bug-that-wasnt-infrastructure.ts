export const theBugThatWasntInfrastructure = {
  slug: 'the-bug-that-wasnt-infrastructure',
  title: "We fixed four real infrastructure problems chasing a bug that wasn't infrastructure",
  date: '2026-07-28',
  author: 'Backend Platform Team',
  tags: ['backend', 'kubernetes', 'jvm', 'incident'],
  excerpt:
    "A one-line push-notification feature turned into a multi-hour investigation across CPU throttling, probe timeouts, resource limits, and JVM heap sizing — all real, all worth fixing, and all completely beside the point. The actual bug was one line of date arithmetic.",
  content: `
The feature itself was small: when a merchant replies to a booking review, push the customer a notification. We'd already wired this exact pattern twice before — a new booking request, a booking-review reply — so this was supposed to be the third, most routine repetition of something already proven. Live-verification against the deployed cluster is where it stopped being routine.

## The symptom

One endpoint — fetching a merchant's available booking slots for a given day — started hanging. Not erroring, not timing out cleanly: just sitting there for anywhere from a few seconds to ninety seconds, sometimes succeeding, sometimes taking the whole pod down with it. Every other endpoint on the same service worked fine. Login was instant. Creating a booking was instant. This one call, intermittently, wasn't.

The obvious hypothesis for "hangs intermittently, sometimes crashes the pod" is infrastructure, not application code. So we started there.

## Four real problems, in order

**CPU throttling.** \`cat /sys/fs/cgroup/cpu.stat\` inside the container showed \`nr_throttled\` at 49% of scheduling periods — the backend had a 1-core CPU limit and was being kernel-throttled roughly half the time it tried to run. This is invisible to a JVM thread dump: a throttled thread still reports \`RUNNABLE\`, it's just not being scheduled. We removed the hard limit and raised the namespace's default CPU allocation. Real fix, worth keeping regardless of what happened next.

**Probe timeouts.** Kubernetes' liveness and readiness probes default to a 1-second timeout if you don't set one explicitly. We hadn't set one, on any of our four services. A JVM under any real GC pressure can trivially miss a 1-second HTTP round trip without being unhealthy — and two of our other services already had 60+ restarts each from exactly this, restarts we'd been quietly attributing to "normal churn." We raised the timeout to 5 seconds everywhere. Also a real fix, also worth keeping.

**Resource-limit propagation.** Fixing the CPU limit meant patching a namespace-level \`LimitRange\` that supplies defaults when a container doesn't set its own. We patched it in two steps — remove the pod's explicit limit, then raise the namespace default — and a pod got scheduled in the exact gap between those two patches, permanently stamped with the *old*, smaller default. Kubernetes doesn't retroactively re-apply a \`LimitRange\` to an already-running pod. We caught it by checking the live pod's actual resource spec instead of trusting what we'd just applied, and forced a fresh one.

**JVM heap ergonomics.** Even after all of the above, one number still looked wrong: \`MaxHeapSize\` was 512MB, on a container we'd just given a 2GB memory allowance. The JVM's default is to use 25% of the container's memory limit for heap unless you tell it otherwise — a generous container limit does not imply a generous heap. We set \`-XX:MaxRAMPercentage=70\` explicitly. Heap jumped to roughly 1.4GB. Also correct, also kept.

Four real, independently-justified infrastructure fixes. And the endpoint still hung.

## The actual bug

At that point the working theory ran out of runway, and it was worth asking a more basic question: was this ever an infrastructure problem at all, or had four real-but-unrelated issues been masking one application bug the whole time?

The slot-generation code built a merchant's bookable time slots like this:

\`\`\`kotlin
generateSequence(window.startTime) { it.plusMinutes(durationMinutes.toLong()) }
    .takeWhile { !it.plusMinutes(durationMinutes.toLong()).isAfter(window.endTime) }
\`\`\`

\`LocalTime.plusMinutes\` doesn't carry a date — it wraps at midnight. \`23:30 + 30 minutes\` isn't "the next day at 00:00," because \`LocalTime\` has no concept of "the next day." It's just \`00:00\`. And \`00:00\` is never "after" \`23:30\` — it's *earlier* in the day, by definition. So once a merchant's availability window closed within one slot-duration of midnight, the \`takeWhile\` predicate stopped being able to become false. The sequence looped forever, generating an endless stream of wrapped-around time slots, pinning a CPU core on every request that hit it.

The test data we'd used to verify the *original* feature happened to include a 00:00–23:30 availability window — the exact pathological case. Every prior shipped feature touching this same booking system had used ordinary business hours, which never gets close enough to midnight to wrap. Sixty-six previous features had exercised this code path without ever triggering the bug.

The fix replaced the wrapping-comparison loop with a bounded count, computed once:

\`\`\`kotlin
val slotCount = Duration.between(window.startTime, window.endTime).toMinutes() / durationMinutes
(0 until slotCount).map { i -> window.startTime.plusMinutes(i * durationMinutes.toLong()) }
\`\`\`

No wraparound possible, because there's no comparison against a value that can wrap — just a fixed number of iterations, computed from real elapsed minutes.

## What we verified

Booking-slots for the same 00:00–23:30 window that used to hang instead returned in 0.275 seconds, with all 47 real half-hour slots, correctly. We wrote a regression test that runs the fix on a background thread with a hard 5-second timeout, specifically so a future regression of this exact bug fails a test instead of hanging the whole suite.

## The actual lesson

None of the four infrastructure fixes were wasted effort — CPU throttling, probe timeouts, and heap sizing are all real capacity problems this cluster genuinely had, worth finding regardless. But they were also, in this specific case, an extremely convincing distraction. The honest lesson isn't "check infrastructure first" or "check code first" — it's that when a hang survives several rounds of infrastructure fixes that each individually make sense and each individually check out clean, that persistence is itself informative. It's evidence pointing away from infrastructure, not a sign to keep looking harder in the same place. Especially for anything touching date or time arithmetic that can wrap: a loop bounded by comparison against a value that cycles is a real, specific, checkable red flag, worth looking for directly rather than discovering it after four rounds of unrelated fixes.
`,
};
