export const theLinterThatLiedToItselfFirst = {
  slug: 'the-linter-that-lied-to-itself-first',
  title: "Building an accessibility linter, and catching it lying to us before we trusted a single result",
  date: '2026-08-07',
  author: 'Design Systems Team',
  tags: ['accessibility', 'design', 'android', 'ios', 'web', 'tooling', 'incident-writeup'],
  excerpt:
    "Toss built a real tool — Ally — that scans their own app for accessibility gaps automatically, replacing manual expert audits. We built our own version. First real run: 88 violations. Second run, after we caught the linter itself producing false positives: 69. The gap between those two numbers is the actual story.",
  content: `
We'd already done three rounds of manual accessibility fixes this project — a touch-target sweep, a dark-mode diagnosis, a dead-tap bug found by hand in a component nobody had clicked through carefully. Each one found something real. Each one was also a one-time pass: find it, fix it, move on, and hope the next screen someone writes doesn't reintroduce the same mistake. Toss's own engineering blog names this exact problem and its own real fix: they built "Ally," a self-hosted scanner that checks app code for missing alt-text and labels in one click, and their own stated payoff was concrete — developers now catch and fix roughly 100 accessibility errors an hour themselves, replacing a process that used to require a manual expert consultant audit. Not a research paper, a tool. So we built ours.

## What it checks, and why we kept it narrow

The scope is small on purpose: an Android \`IconButton\` whose only content is an icon with \`contentDescription = null\`, an iOS \`Button\` whose only content is an \`Image(systemName:)\` with no \`.accessibilityLabel\` anywhere on it, and a web \`<img>\` with no \`alt\` attribute at all. We looked at a fourth check — flagging any \`.clickable()\`/\`onTapGesture\`/\`onClick\` on a plain container instead of a real button — and deliberately left it out. Too many of our own legitimate custom row and card components use exactly that pattern correctly, and a linter that's wrong half the time teaches people to ignore it, which is worse than not having one at all. Every check we shipped is chosen because a hit is very likely a real bug, not a style opinion.

## The first run, and why we didn't trust it

We ran it. 88 violations. Before fixing a single one, we looked closely at a handful, because a linter's first real output is exactly the moment you find out whether your own logic is right — and one of the first ones we checked was wrong.

\`DeviceStepUpView.swift\` — a file we'd personally added a password show/hide toggle to earlier this same session — got flagged for having an icon-only button with no accessible label. It wasn't true. The button had a real label:

\`\`\`swift
Button(action: { passwordVisible.toggle() }) {
    Image(systemName: passwordVisible ? "eye.slash" : "eye")
        .frame(width: 24, height: 24)
}
.accessibilityLabel(passwordVisible ? "Hide password" : "Show password")
\`\`\`

The label is real. It's just not *inside* the button's closure — it's chained on as a modifier after the closing brace, which is the normal, idiomatic way to write SwiftUI. Our first version of the check only looked inside the \`{ }\` block for \`.accessibilityLabel\`, because that's where the icon lives, and we hadn't separately accounted for the fact that the label usually doesn't live in the same place. A tool that flags its own correct, already-shipped code as broken doesn't get to claim any of its other 87 results are trustworthy either — you fix the tool first, or you're just guessing.

We fixed the scan to also look at the modifier chain immediately following the closing brace. Rerun: 69 violations. Eighteen and a half percent of the first run's output was the linter arguing with itself.

## The second bug was quieter, and would have shipped actual garbage

While hand-fixing the real 69, we hit a case in \`BenefitsShopAllScreens.swift\` that already had a correct, deliberately-commented accessibility label — with a two-line comment explaining a *previous* real bug fix sitting between the closing brace and the label:

\`\`\`swift
Button(action: onOpenSettings) {
    Image(systemName: "gearshape")
    ...
}
// Found live via FocusOrderTests (2026-07-12): this button's action and
// icon were changed from direct-logout to opening the real Settings
// screen, but the accessibility label was never updated to match...
.accessibilityLabel("Settings")
\`\`\`

Our chain-detection regex expected whitespace, then a dot. A comment in between isn't whitespace, so it broke the match — the linter re-flagged an already-fixed button as broken, and our first-pass automated fixer, trusting the flag, inserted a second, redundant \`.accessibilityLabel("Settings")\` right after the closing brace. Harmless at runtime (the later modifier in a SwiftUI chain wins), but it's exactly the kind of silent duplication that looks fine in a diff and just sits there as debt. We caught it by reading the diff before committing, not by the tool telling us — which is itself the lesson: an autofix script is not exempt from the same "read what you actually changed" discipline as a human edit. Fixed by stripping \`//\` comments from the post-brace window before matching the chain, in both the iOS and the web checks (the web \`<img>\` check had the same class of bug, from a different cause — see below).

## The bug that would have flagged a comment, not code

The web check does one thing: find \`<img>\` tags with no \`alt\`. Simple regex, should be safe. It flagged a line in \`BankDashboard.tsx\` that had no \`<img>\` tag on it at all — it had a code *comment* that happened to mention \`<img>\` while documenting a nearby real one:

\`\`\`tsx
// plain <img> with onError falling back to the same placeholder icon shown for a
// product that simply has no image set at all -- both are real, valid states.
\`\`\`

A regex matching \`<img\\b[^>]*>\` doesn't know the difference between a real JSX tag and four characters that happen to look like one inside a sentence. We fixed this the same way as the SwiftUI comment bug: blank out \`//\` and \`/* */\` comment regions (preserving line numbers, so violation reports still point at the right place) before scanning for real tags.

## What was actually real, once the tool could be trusted

69 real violations, all iOS. 43 were the exact same shape: a back-chevron button with no label, which we verified was really wired to \`onBack\` at every single call site before batch-fixing it as "Back" — not just pattern-matching the icon name and hoping. The rest needed real per-context labels, not a generic mapping: a heart-shaped favorite toggle needed a *dynamic* label ("Add to favorites" / "Remove from favorites" depending on current state, not one static string), a 1-5 star rating control needed "Rate 3 stars" per button, and — the one place our first attempt at an automatic icon-to-label mapping was flatly wrong — a map's zoom controls got auto-labeled "Add" and "Remove" from a generic \`"plus": "Add"\` lookup table, when the actual correct label is "Zoom in" / "Zoom out." Same icon, different screen, different meaning. That one we caught by reading the diff, not by any check catching it automatically — a reminder that even a working linter's autofix still needs a human to confirm the *words* are right, not just that a label now exists.

## Why the false positives were the actual point

A linter that returns 88 uncritically-accepted results and a linter that returns 69 verified ones look identical in a commit message unless someone checks. The entire value of building this tool ourselves, instead of trusting the first number it printed, was catching that it was wrong about roughly a fifth of its own output — on the very first real run, before any of it shipped. That's not a one-time cost. It's now sitting in CI, checking every future PR the same way, and the version of it we shipped is the one that already survived being wrong about its own creator's code.
`,
};
