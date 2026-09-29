import type { Post } from './index';

export const aRealDebitCardWithNoCardNetwork: Post = {
  slug: 'a-real-debit-card-with-no-card-network',
  title: "A real debit card, with no card network behind it",
  date: '2026-07-31',
  author: 'Ledger Platform Team',
  tags: ['ledger', 'product', 'fintech'],
  excerpt:
    'itunda shipped a check-card-style debit card this week: real spend limits, real freeze, real money movement. We do not have a Visa or Mastercard partnership. Here is how those two facts coexist honestly.',
  content: `
Toss Bank's real check card does something worth copying: the card itself is almost incidental. What actually matters is that the app controls it completely — a daily spend limit you can raise or lower from your phone, a one-tap freeze that blocks every purchase before you've even found your wallet, an instant notification the moment it's used. None of that requires a bank branch. All of it requires that the app is the real source of truth, not the plastic.

We wanted that for itunda. We do not have a Visa or Mastercard partnership, and we are not going to fake one. So the question going in was: how much of "a real debit card" can you build honestly when the one piece you're missing is the actual card network?

## Separate the network from the product

A check card is really two things bolted together: a payment rail (the part that needs Visa/Mastercard/a real PAN routing to a real acquirer) and a spend-control layer (limits, freeze, notifications, a statement). The rail is the part we can't build. The control layer is a real product on its own — arguably the more interesting half, since it's the part a user actually interacts with day to day.

So that's what we built. \`DebitCard\` gets a real \`last4\` (stored, SecureRandom-generated) so it looks and feels like a card, but it deliberately isn't a routable PAN — there's no network to route it over, and pretending otherwise would be the kind of fabricated realism we try hard to avoid. What is real: the daily and monthly limits, the freeze toggle, and — the part that took the most care — what happens when you actually "pay" with it.

## Paying with a card you can't swipe

Without a POS terminal, "paying with your card" has no natural trigger. We could have stubbed it — a fake success screen, no real effect. We didn't, because the interesting engineering problem was making the *rest* of the feature real even without one, and a stub would have made everything downstream fake too: the limits would never actually get exercised, the transaction history would be empty, the freeze would have nothing to block.

Instead, \`chargeWithCard\` is a real, honestly-labeled simulation of a card-present purchase. The UI says exactly what it is: itunda has no card-network partnership yet, this simulates a purchase. But underneath, it moves real money through the same ledger every other product in this codebase uses — a real \`WALLET\` debit and a real credit to a new \`CARD_SPEND_EXPENSE\` clearing account, posted through \`LedgerService.postLedgerTransaction\`, the one balanced double-entry choke point everything from a P2P transfer to a loan disbursement already goes through. If the card is frozen, or the purchase would blow past a limit, the ledger is never touched — same enforcement order a real payment processor would use, decline before debit.

## Limits are a query, not a counter

The tempting way to enforce a daily limit is a running counter: increment on each purchase, reset at midnight. We didn't do that, because a counter that resets needs a scheduler, and a scheduler that fails silently (a missed cron tick, a clock skew, a redeploy at the wrong moment) leaves you with a counter that's just wrong, with nothing to check it against.

Instead, every card purchase writes a real row, and the daily/monthly spend is a real \`SUM(amount) WHERE card_id = ? AND created_at >= ?\` query against those rows. There's nothing to reset and nothing that can drift out of sync with the real transaction history, because the transaction history *is* the source of truth — the query result and the real audit trail can never disagree, by construction. It costs a query instead of an increment; for the volume a debit card actually sees, that's the right trade.

## What "real" means when a network doesn't exist

The honest version of this feature isn't the one that pretends hardest to be a bank. It's the one that's precise about which half is real: the limits are real, the freeze is real, the money movement is real and ledger-backed, the notification is real. The card network is the one piece that isn't, and we say so, in the code's own comments and in the UI a user actually sees. That's the same discipline this whole project tries to hold itself to — build the part that can be real, all the way down to the ledger, and name the boundary explicitly rather than blur it.
`,
};
