export const threeFlowsTheFraudEngineNeverSaw = {
  slug: 'three-flows-the-fraud-engine-never-saw',
  title: "We already knew our fraud engine covered every money-moving flow. It didn't.",
  date: '2026-08-07',
  author: 'Security Team',
  tags: ['security', 'backend', 'fraud', 'incident-writeup'],
  excerpt:
    "Our fraud engine's own doc comment names its callers explicitly — five services, kept up to date, cited elsewhere as a validated match to Toss's own real-time detection pattern. Then we went to double-check that list instead of trusting it, and found three real money-to-a-named-recipient flows that had simply never been wired in.",
  content: `
A few weeks ago we researched how Toss keeps security and simplicity from trading off against each other, and one of the things we checked was whether our own fraud detection actually ran on every real transaction the way Toss's does. It did — \`FraudRuleEngine.evaluate()\` runs on every P2P send, every wallet currency conversion, every commerce/food/dine-in checkout, every merchant in-person collection, every payroll disbursement. We wrote that down as a validated match, not a gap, and moved on.

That was true. It also wasn't the whole list, and we only found out because we went back to check it again instead of trusting our own prior conclusion.

## Checking a "done" list instead of re-reading it

The engine's own doc comment names its callers explicitly — a real, maintained list, not a guess: "P2pService (send + payment requests), WalletService (currency conversion), OrderService/EatsOrderService/DineInOrderService (checkout), MerchantService (in-person collection), PayrollService (salary disbursement)." That comment has already been corrected once before, per its own text — an earlier version said merchant collection and wallet transfer were "not yet wired," and whoever wrote that never went back to update it after they actually got wired the same session. The comment fixed itself acknowledging its own staleness.

That history was the reason we didn't just re-read the comment and call it done. We wrote a small grep instead: every backend file that constructs a real \`Transaction\` domain object, cross-referenced against which of those files actually call \`fraudRuleEngine\`. Not because we suspected a specific gap — because a list that's already been caught going stale once is a list worth re-deriving from the actual code, not from its own summary of itself.

The grep returned 48 files. Most of them were obviously fine to skip — internal ledger bookkeeping, rewards being earned rather than spent, a sticker-pack purchase too trivial to matter. But three weren't:

\`\`\`kotlin
// GiftService.kt
fun sendGift(senderUserId: String, recipientPhoneNumber: String, amount: BigDecimal, ...): Gift

// GiftVoucherService.kt
fun purchaseVoucher(purchaserUserId: String, recipientPhoneNumber: String, ...): GiftVoucher

// SplitBillService.kt
fun payShare(payerUserId: String, splitBillId: String): SplitBillParticipant
\`\`\`

All three do the exact same thing P2P transfer does — move real money out of one wallet, into an escrow or directly into another named person's account, with a real \`recipientId\` on the resulting transaction. A gift is sent to a recipient resolved by phone number, precisely like a P2P send. A gift voucher is purchased for a recipient resolved by phone number, same shape again. A split-bill payment goes to the bill's organizer, a real, specific account. Every one of these is exactly the pattern our new-recipient and velocity rules exist to catch — an account that's been compromised moving money to a destination the real owner never authorized — and none of the three had ever called the fraud engine at all.

## Why this didn't get caught the normal way

It's not that anyone made a mistake writing these features. Gift, gift vouchers, and split-bill were each built as their own real, working, tested product surface, each correctly wired to the ledger, each correctly checked for balance and rate limits. Fraud evaluation is a separate, cross-cutting concern that lives in a different module for exactly this reason — so any money-moving service can call into it without owning fraud logic itself. But "can call into it" only becomes "does call into it" if someone remembers to add the line, and nothing in the type system or the test suite forces that. A missing security check compiles fine. It passes every test that doesn't specifically test for it. The gap doesn't announce itself; you have to go looking, the same way we finally did.

## The fix, and the one detail that had to be copied exactly

The fix itself is small — one call, added to each of the three services:

\`\`\`kotlin
fraudRuleEngine.evaluate(senderUserId, recipientUserId, amount, holdTransaction.id)
transactionRepository.save(holdTransaction)
\`\`\`

The ordering matters more than it looks like it should. \`P2pService\`'s own code has a comment explaining a real bug that already happened there once: if you save the transaction *before* evaluating fraud rules, the evaluation's own recipient-history lookup finds the transaction that was just saved and matches against itself — permanently masking the NEW_RECIPIENT flag no matter how new the recipient actually is, because as far as the query is concerned, there's already history with them. Evaluate first, save second. We copied that ordering into all three fixes deliberately, not by habit — it's exactly the kind of detail that looks like a stylistic choice until you understand why it's there, and getting it backwards would have shipped three new flows with fraud detection that silently never fires on the one signal it exists to catch.

## What actually generalizes here

The lesson isn't "gift sending was insecure." It's that a validated, documented, correctly-described security boundary can still have real holes next to it, because the boundary's own accuracy depends on someone re-deriving it from the code periodically, not trusting that the last person who wrote it down got every caller. Our own doc comment already told us this exact class of drift had happened once before. We read that as history. It was also a standing instruction to go check again.
`,
};
