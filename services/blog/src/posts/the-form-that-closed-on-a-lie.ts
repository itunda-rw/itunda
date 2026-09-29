export const theFormThatClosedOnALie = {
  slug: 'the-form-that-closed-on-a-lie',
  title: "A conference-session title was enough to send us checking our own account-linking flow — and it lied to users the whole time",
  date: '2026-08-08',
  author: 'Backend Team',
  tags: ['backend', 'web', 'android', 'ios', 'ux', 'incident-writeup'],
  excerpt:
    "We still couldn't read the actual talk — it's video-gated behind a login we don't have. But the session title alone (\"신은 디테일에 있다\": a bank-linking flow redesigned to eliminate friction) was specific enough to send us walking our own equivalent feature end to end. We found a real bug: a declined verification and a successful one produced the exact same screen.",
  content: `
Fourth pass into the same conference archive this project keeps drawing lessons from. The first three found real, fixable things — a self-built accessibility scanner, a chatbot missing screen-reader announcements, a design-system fix that had landed on one platform and never made it to three others. This time the source material itself stayed locked. We recovered a session title — "신은 디테일에 있다," God is in the details, a real Toss designer's talk about redesigning the bank-account-linking flow to eliminate friction from the connection process — and nothing past it. No recap, no transcript, no third-party summary. Just a title and a name.

That was still enough to act on. We have our own account-linking feature. The obvious next move wasn't to keep hunting for the talk — it was to go walk our own version of the same flow and see what we'd find.

## What we found wasn't friction. It was a lie.

Linking an external bank or mobile-money account in itunda is one form: pick a provider, type an account number, submit. Behind that submit, the backend runs a real simulated verification through the same provider-connector mechanism transfers and bill payments already use — sometimes it succeeds, sometimes the simulated provider declines it, exactly like a real bank consent flow would. When it's declined, the account still gets saved, deliberately, with a \`VERIFICATION_FAILED\` status and a reason — so a failed attempt shows up in the user's history instead of vanishing. That part was correct and intentional.

What wasn't intentional: the API response for a declined attempt looked byte-for-byte like the response for a successful one. \`success: true\`, every time, regardless of what actually happened. And every client we have — the web dashboard, the Android app, the iOS app — took that response, cleared the form, and closed it. Three independent implementations, three independent teams, one shared blind spot: none of them ever looked past "the request didn't throw" to ask "did the thing I asked for actually happen?"

\`\`\`kotlin
val account = linkedAccountService.link(currentUser.userId, request.provider, request.externalAccountNumber)
return ResponseEntity.ok(mapOf("success" to true, "linkedAccount" to account))
\`\`\`

A user who typed the wrong account number, or whose provider simulation happened to decline, would watch the form close cleanly, see nothing that looked like an error, and walk away assuming their account was linked. The only evidence otherwise was a status word sitting in a list further down the same screen, easy to miss on a screen with a working "Link account" button they'd just tapped.

## Why three platforms all missed the same thing

This wasn't three separate bugs. It was one bug in one response shape, faithfully re-implemented three times because the underlying data was already there and nobody used it. Every one of our three clients already decodes a \`status\` field and a \`failureReason\` field off the linked-account object the backend returns — they were reading it correctly to render the status label in the account list below the form. The exact same object, already in hand, in the exact same function, just wasn't being checked before the code decided to declare success.

## The fix

The backend now reports what actually happened, not just that a request was accepted:

\`\`\`kotlin
val verified = account.status == LinkedAccountStatus.LINKED
return ResponseEntity.ok(mapOf("success" to verified, "linkedAccount" to account))
\`\`\`

And all three clients now check the account they already get back before treating a link attempt as done:

\`\`\`tsx
const linked = await linkAccount(provider, accountNumber);
setProvider(''); setAccountNumber(''); setShowLinkForm(false);
refresh();
if (linked.status === 'VERIFICATION_FAILED') {
  setError(linked.failureReason ?? \`Could not verify that \${provider} account. It wasn't linked.\`);
}
\`\`\`

Same shape on Android and iOS, each reading the same \`status\`/\`failureReason\` fields their own DTOs already had. The HTTP status stays 200 — the request itself did succeed, a row really was written, and that's honest — but the payload no longer pretends a declined verification and a real link are indistinguishable.

## What we didn't chase this time

While walking the flow we noticed two more real, smaller friction points: the form doesn't pre-fill the phone number the app already has on file when a user picks their own mobile-money provider, and there's no review step before the real submit. Both are genuine UX gaps in the spirit of the session title we couldn't read. Neither is a bug. We wrote them down and left them alone rather than turning one focused correctness fix into a scope-creeping redesign.

## The part worth keeping

We never got past this session's paywall. We still don't know what Toss's designer actually said about eliminating friction from a bank-linking flow. What we know is that the title alone was specific enough to be worth an hour of walking our own equivalent feature by hand instead of trusting that a flow which had shipped and never thrown an exception was therefore a flow that worked. A feature with zero error reports isn't the same thing as a feature with zero errors — sometimes it just means the errors are shaped exactly like success.
`,
};
