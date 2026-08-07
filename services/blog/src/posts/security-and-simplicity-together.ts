export const securityAndSimplicityTogether = {
  slug: 'security-and-simplicity-together',
  title: "Convenience and safety aren't opposites — what we learned auditing our own security UX against Toss's",
  date: '2026-08-07',
  author: 'Design Systems Team',
  tags: ['design', 'security', 'ux-research', 'android', 'ios', 'web'],
  excerpt:
    "Toss says it outright: 간편함과 안전은 더 이상 양립 불가능한 말이 아닙니다 — convenience and safety are no longer mutually exclusive words. We took that as a real question to check ourselves against, not a slogan to admire. Some of our own security flows already matched it. One didn't, and we found out why before touching it.",
  content: `
We'd already spent a session going through Toss's six named product principles one at a time — Clear Action, One Thing, Easy to Answer, No More Loading, Minimum Features, Value First — and fixing what we found. That work was mostly about friction: auto-confirming a 6-digit code instead of making someone tap Confirm after typing it, auto-focusing the first field on a form instead of making someone tap it first. Real fixes, but none of them touched anything security-sensitive.

Then we asked a sharper question: Toss is a bank. Every one of those simplicity wins has to survive contact with real money-movement security, or it isn't a real principle, it's a lucky coincidence that only works on low-stakes screens. So we went looking specifically at how Toss keeps its security flows simple, and then checked our own against it — not by assumption, by reading our own code next to theirs.

## What Toss actually says about this

Their own security page states it plainly: *"간편함과 안전은 더 이상 양립 불가능한 말이 아닙니다"* — convenience and safety are no longer mutually exclusive words. They credit it to real investment ("전담 인력과 자체 기술" — dedicated personnel and proprietary technology), not a design trick. The concrete mechanism behind that claim is their fraud detection system: a real-time model scores every single transfer as it happens, invisibly, and only surfaces anything to the user when a transaction actually gets flagged. In 2022 it blocked roughly 310,000 fraudulent transactions — about one every two minutes — without adding a single tap to the other 99.9% of transfers that were fine. Security work happening entirely in the background, with friction appearing only when risk is actually detected, not by default on every action.

That's the load-bearing idea: friction proportional to risk, not friction applied uniformly because friction feels safe.

## Checking our own code against that, not assuming it

We have a real device-binding system — \`DeviceService.kt\` on the backend, a \`TrustedDevice\` table, the whole thing. Before writing anything about how it compares to Toss, we read what it actually does.

\`recordLoginDevice\` runs on every login. If the device is new, it doesn't block the login — the user can sign in and look around immediately. It records the device as untrusted, fires a real push notification ("A login from a new device was detected. It can't send money until verified."), and that's it. The gate only shows up later, if that untrusted device tries to move money — a separate filter catches the attempt, returns a real 403, and the client shows a step-up prompt. Browsing is free. Money movement is gated. That's not a coincidence; it's the same shape as Toss's own model, and it was already built before we went looking for it.

We checked the fraud side too. \`FraudRuleEngine.evaluate()\` — high-value, velocity, and new-recipient heuristics — is called from every real money-moving flow: P2P sends, wallet currency conversion, order checkout across three different order types, in-person merchant collection, payroll disbursement. It runs on every transaction, flags rather than blocks (a documented, deliberate choice: a fresh rule engine with no track record is a worse failure mode as a hard block than as something a human reviews after the fact), and stays invisible unless it actually catches something. Not machine-learned like Toss's real system — ours is honestly just three threshold rules — but the *shape* is the same one Toss's FDS uses: real-time, on every transaction, quiet by default.

Two systems already doing the right thing. That's worth writing down precisely because it's easy to only write about what's broken.

## The gap we found, and the one we didn't fix

Device *verification* — the step where an untrusted device proves itself before it's allowed to move money — asks for a password on all three clients. We have a real biometric primitive already, \`NIDABiometricAuth\`, already used elsewhere in the app to confirm a transfer. The obvious move looked like: let biometric satisfy device verification too, same as it already satisfies transfer confirmation.

We didn't do that, and the reason is in \`NIDABiometricAuth\`'s own header comment, which we should have read more carefully before getting excited: *"Not implemented: binding this prompt to a CryptoObject and the server-side ... verification call ... Local biometric success only, honestly labeled above."* That comment exists because this exact class of shortcut already happened once in this codebase's history and had to be found and fixed — a prompt that reported success without a real check behind it. A local fingerprint match proves something to the phone. It proves nothing to the server unless there's a real cryptographic signature behind it, the way Toss's actual 토스인증서 (Toss Certificate) system works — a private key in the device's secure hardware signs a challenge the server issued, and the server verifies the signature. That's a real feature: a public-key registration endpoint, a challenge-issuance endpoint, and platform key generation on both native clients. Wiring a local biometric success straight into "mark this device trusted" without any of that would have been faster to ship and strictly worse — it would let anyone who can unlock the phone at all mark a stolen device as trusted, no password required.

We wrote that down as the real next step, sized honestly as its own project, and left device verification asking for a password. What we did fix, on the same screen: the password field had no autofocus, on any of four separate copies of this exact dialog (Android, iOS, and two web components). It's the single field that matters on that whole screen, and nothing about focusing it automatically weakens what it's checking.

## The other thing we shipped, and why it's not a tradeoff

We also added show/hide toggles to every password field across login and registration, on every platform. That one's simpler to reason about than the biometric question: the value in that field never leaves the device differently depending on whether you can see it while typing. It's not a security control at all — it's a UI convenience sitting on top of a security control that doesn't change. The distinction mattered more than it sounds: it's the same question we asked about biometric device verification, just with an easy answer instead of a hard one. Does this convenience change what the server can verify? For the eye icon, no. For a fingerprint standing in for a password, yes — unless there's a real signature behind it.

## What actually generalizes

The lesson isn't "add biometric everywhere" or "remove every confirm button." It's the question underneath both: does this piece of friction correspond to a real risk, and does removing it change what anything downstream can actually verify? Where the answer was no — a code that's provably fixed-length, a field that's the only thing on the screen, a value that stays local either way — we removed the friction. Where the answer was yes — money that moves the instant a code is entered, a trust decision the server has to be able to check — we left it, and in one case, wrote down the real fix instead of a fast one.
`,
};
