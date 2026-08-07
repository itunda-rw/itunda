export const theFixWeAlreadyHad = {
  slug: 'the-fix-we-already-had',
  title: "We researched a design-system problem and found we'd already fixed it — on one platform, three weeks ago, and never checked the other three",
  date: '2026-08-08',
  author: 'Design Systems Team',
  tags: ['design', 'web', 'android', 'design-system', 'incident-writeup'],
  excerpt:
    "A fresh research pass into Toss's design conference turned up the real fix behind a session called 'nobody uses our design system.' We went to check whether itunda had the same problem — and found a doc comment already citing the exact same source, already applied, on Android, from three weeks earlier. Nobody had asked whether web got the same fix.",
  content: `
We keep coming back to Toss's own conference archives this project is modeled on, and a recent instruction was blunt: "simplicity." Keep digging into their design conference. So we did — past the sessions we'd already covered, into the ones that had only ever shown up as a title with no recoverable content.

One of those was a Simplicity25 session called "아무도 쓰지 않는 디자인 시스템" — nobody uses the design system. We'd found the problem statement once before, in an earlier pass, and moved on without the actual fix. This time we found it: a real toss.tech article, "디자인 시스템 다시 생각해보기" (Rethinking Design System). Toss's own diagnosis was that a design system built as one rigid shape stops getting adopted — not because teams are careless, but because the real shape of a real problem doesn't fit the one shape the system offers, so people detach the Figma component and fork the code instead of filing feedback. Their fix: a hybrid API, a simple flat version for the common case and a composable version for the edge case, both built on one shared internal primitive. Their own framing stuck with us: "the role of a design system is to help teams solve product problems, not to police them."

We went to go check whether itunda had this problem. That's when it got interesting.

## The fix was already sitting in the codebase

\`android/core/designsystem/.../IdsButton.kt\` has its own doc comment. It cites \`toss.tech/article/rethinking-design-system\` by name. It's dated three weeks before this research pass even started. The story it tells is almost identical to what we'd just (re-)discovered: \`ItundaAppScreen.kt\` used to have three separate local button composables that never touched the shared design-system file at all, and one of them had hardcoded a raw color literal that happened to match a real design token at the moment someone wrote it, with nothing keeping the two in sync going forward. Someone on this project had already read this exact Toss article, diagnosed this exact failure mode in itunda's own Android code, and fixed it with the exact API shape Toss describes — a flat, props-based \`variant\`/\`size\` API, not a rigid one-shape component.

Android had the lesson. Nobody had asked the obvious next question: does web have it too?

## It didn't, and the number was bigger than we expected

\`find services/micro-frontends -iname "*Button*.tsx"\` returned nothing. Not "a rigid one that people escape" — nothing. No shared button component existed on any of the four web apps at all. Every button anywhere in bank-mfe, merchant-mfe, ops-mfe, or kyc-mfe was a raw \`<button className="toss-btn toss-btn-primary">\`, styled by a CSS class string, hand-typed at every call site. We counted: **415 occurrences in a single file**, \`BankDashboard.tsx\`, alone.

This is actually a step earlier in the same failure Toss described. Toss had a real shared component that people escaped because it was too rigid. Web here never built the shared component in the first place — every screen just re-implemented "a Toss-styled button" independently, which means every screen also independently decided (or forgot to decide) things like whether the button has a real disabled state, whether it's a real \`<button type="button">\` or something that could accidentally submit a form, whether it's keyboard-accessible. Some call sites get all of that right. Some don't. There was no single place enforcing any of it.

## The color bug that snuck through its own fix

While we were in the CSS to check the actual button styling, we found something else: the \`.toss-btn-danger\` class's background color was still a hardcoded \`#E53935\`, in four separate files across bank-mfe, ops-mfe, merchant-mfe, and a fourth web app (kyc-mfe) we hadn't even been tracking in this specific hunt. This is the exact same wrong-red-instead-of-the-real-token bug this project already found and fixed once this session — 271 occurrences, all in \`.tsx\` files. That earlier sweep never looked inside \`.css\` files, so these four sat there the whole time, technically outside the search that was supposed to have caught every instance. "We already fixed this" turned out to mean "we already fixed every instance we thought to search for."

## What we actually shipped, and what we didn't

We built \`IdsButton.tsx\` for web, porting Android's real API shape exactly rather than inventing a new one — same \`filled\`/\`tinted\` variants, same three sizes, same disabled-state color handling. Then we migrated exactly two call sites: the real transfer-confirm button ("Send X RWF") and the real merchant payment-confirm button ("Pay") — the two highest-stakes, actual-money-movement buttons in the app, as proof the component works end-to-end in the flow that matters most if something's wrong with it.

We did not migrate the other 413 call sites in that one file, or the equivalent hundreds across the other three apps. That's a real, large, mechanical piece of follow-up work, and doing it in one sweep right now would have meant either rushing it or not shipping anything real today. We wrote down what's done and what's still open, the same way we've tried to be honest about every partial fix this session — the CodePush gap, the SAGA pattern, the parts of Toss's own conference archive we still can't get past a video wall for. A design-system migration that gets marked "done" after fixing two buttons out of 415 would be a worse outcome than one honestly marked "started."

## The part worth remembering

The interesting failure here wasn't technical. It was organizational, and it was small: one platform read a source, learned a lesson, and fixed a real bug. Nobody ever circled back and asked "did the other three platforms need the same fix?" — not because anyone decided they didn't, but because nobody asked the question at all. The lesson from the Toss article was real and it worked. The lesson from watching it sit un-asked-about on three-quarters of our own platforms for three weeks is arguably more useful: a fix that lands on one surface is a fact about that surface, not a fact about the problem being solved everywhere it exists.
`,
};
