export const theAuditWeSaidWeFinished = {
  slug: 'the-audit-we-said-we-finished',
  title: "Our accessibility audit had a scope note we never re-read: 'Android and iOS only'",
  date: '2026-08-07',
  author: 'Design Systems Team',
  tags: ['accessibility', 'design', 'web', 'android', 'ios', 'incident-writeup'],
  excerpt:
    "We had a real, dated accessibility audit on file. It was real when it was written. What it didn't say out loud — because the doc's own scope note said it, quietly, in the header — is that it never once looked at the web apps. Three platforms, and one of them had never been checked at all.",
  content: `
We keep a real accessibility document — contrast ratios computed with the actual WCAG formula against our actual design tokens, not estimated; icon-only buttons found by grepping every \`contentDescription = null\` in the codebase and reading each one in context; touch targets measured against the real platform minimums. It's a good document. It's also, we found out, an incomplete one, in a way that was never actively hidden — the header says plainly it was built from "every icon usage in \`ItundaAppScreen.kt\`" and "every \`Image(systemName:)\` usage under \`ios/\`." Android and iOS. The header never claimed web. We just stopped re-reading the header.

## What "never checked" actually looked like

Once we went and did the same sweep on the web micro-frontends that we'd already done twice on mobile, it took about an hour to find real, live bugs:

- A form field where the \`<label>\` was a sibling of its \`<input>\`, not wrapping it — every other form in the same codebase gets this right, wraps the input as the label's own child, which is what makes clicking the label focus the field and what gives a screen reader the field's name for free. This one didn't, on a component used five times across real ride and rental screens.
- A "Ladder game" toggle for splitting a bill that was a bare \`<label onClick={...}>\` with no associated control — not in the tab order, not reachable by keyboard at all, because a plain \`<label>\` isn't focusable and a click handler on one doesn't make it so.
- Ten icon-only buttons — a sticker picker, two Send buttons, cancel-reply ×'s, modal-close ×'s — with no \`aria-label\` anywhere. A screen reader hitting any of these announces "Button." Not "Send message." Not "Close." Just: Button.
- A KYC identity-document submission form — the one place in the whole app tied directly to identity verification — where both fields relied entirely on placeholder text for their name. Placeholder isn't a label. It disappears the moment you start typing, for every user, not just screen reader users.

Then we checked touch target sizes, properly this time, across all 707 buttons in the web codebase rather than just the ones we'd already found for other reasons. Sixteen more violations turned up, and eleven of them were the exact same copy-pasted style object — a "Back" button with zero padding, reused across a dozen different screens, meaning one bad value had been silently multiplied by however many times someone had copied that screen as a starting point for the next one.

## The part that's actually the point

None of this is a story about how careless anyone was. It's a story about what a scope note in a document header actually protects you from, and what it doesn't. "Android and iOS only" was true and honestly stated. It didn't stop the next person — us, a few weeks later — from reading "accessibility audit: done" as a whole-app claim instead of a two-thirds claim, because nobody re-opened the header to check.

We closed it properly this time: form labels, icon names, touch targets, and keyboard reachability, swept across every web micro-frontend the same way it had already been done twice on mobile. Thirty real bugs, fixed and verified building clean on every platform they touched. And this time the document says, in the same header that scoped it to mobile before, exactly what's still open on web — color contrast (inherited automatically from the same design tokens mobile uses, but never independently re-verified on web), Dynamic Type's web equivalent, and true focus *order* as opposed to focus *reachability*, which are two different claims we were careful not to conflate.

## The copy-voice pass had the identical shape

We'd also, separately, done real work establishing how itunda's empty states should read — not "No listings yet," which answers a question nobody asked, but "No listings yet — be the first to list something," which says what's missing and what fixes it. Four rounds of that work, real before-and-afters, a written rule.

Same blind spot, different document. Those four rounds all lived in one file — the consumer app's own screens, on all three platforms. The native MerchantApp, RiderApp, and AgentApp are three *separate* apps, not screens inside the one we'd already covered, and nobody had ever pointed the same rule at them. Neither had two more rounds' worth of screens inside the app we thought we'd finished — Marketplace, Community, Jobs, and Property still said things like "No jobs posted yet." with nothing after the period, because the four rounds that came before had covered other screens and the assumption quietly became "we did the empty states" instead of "we did the empty states we looked at."

Five more rounds closed it — the four Hood features, the three satellite apps, and a couple of straggler screens a final repo-wide grep turned up that even round five hadn't reached. Fifty-one real strings, rewritten the same way, on every platform. We also caught ourselves mid-fix: a comment placed inside a ternary expression instead of inside real JSX children is invalid syntax, and it shipped to \`vite build\` before it shipped anywhere real — \`tsc\` alone hadn't caught it. Both checks matter, not just the one that runs first.

## What we're actually taking from this

A document's own scope note is honest exactly as long as someone keeps reading it as a scope note and not as a finish line. "We audited X" is a true, useful, narrow claim. It becomes a false, dangerous, broad one the moment it gets remembered as "we audited it" with the object silently dropped. The fix isn't a better document. It's re-reading the ones we have before trusting what we remember they said.
`,
};
