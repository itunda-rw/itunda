# itunda copy voice

Closes `docs/DESIGN_REFERENCES.md` Section 9 recommendation #3: a short, real
guideline built from actual shipped copy (item 238's three-round empty-state
pass), not an abstract tone document written in a vacuum.

## The position

Baemin's own real, named voice (배민다움) is the strongest single proof point
in the ecosystem research behind this doc that copy register is a legitimate,
separate design lever from iconography — not a nice-to-have layered on top of
a finished UI. itunda didn't have a stated position on this before item 238;
every string was functionally correct but personality-neutral ("No orders
yet.", "No notifications").

itunda's own position, closer to Toss's softened, specific register than
Baemin's more playful one (itunda has no in-house illustrator or brand voice
team to sustain a stronger personality, and this is a financial product where
overly cute copy can undercut trust):

**Every zero-content or failure moment tells the reader two things: what's
missing, and what makes it appear.** Never just the first half.

## Three rules, each with a real before/after already shipped

### 1. Say what's missing *and* what fixes it — don't just report the absence

A bare fact ("no X yet") answers a question the reader didn't ask ("is
there X?" — obviously not, that's why they're looking at an empty screen).
What they actually need is *what do I do about it*.

- Before: `"No transactions yet."`
- After: `"No transactions yet — once you send, receive, or spend, it'll all show up here."`
  (`WalletTransactionsView`, all 3 platforms)

- Before: `"No auto-transfers set up yet."`
- After: `"No auto-transfers set up yet — set one up to send money on a schedule automatically."`
  (bank-mfe `AutoTransfersView`)

### 2. Be specific to the real surface, not a generic template

A generic "no items" template reused everywhere reads as though no one
actually looked at what's supposed to be there. The fix names the real
object and the real action, every time — which also means never copy-paste
the same string across two functionally different screens without checking
what each one actually shows (see the real mistake below).

- Before: `"No orders yet."` (used identically for both Eats and Shop)
- After, Eats: `"No orders yet — order from a nearby restaurant and it'll show up here."`
- After, Shop: `"No orders yet — browse a merchant's shop and your first order will show up here."`
  (`MyEatsOrdersView` vs `MyCommerceOrdersView`, all 3 platforms)

- Before: `"No vehicles added yet."`
- After: `"No vehicles added yet — add one to track its value and get real offers."`
  (`VehicleValuationView`, all 3 platforms)

**Real mistake, corrected the same day**: bank-mfe's "No requests received/
sent yet" and "No listings posted yet" were first rewritten as if they were
P2P payment-request copy ("when someone asks you to pay them…"). They
actually live inside `FloatMarketplaceSection` — agent cash-float trading —
a completely different feature that happens to share generic-sounding
function/variable names. The real P2P payment-request screen
(`RequestMoneyCard`) doesn't even show an empty state; it self-hides when
there's nothing to show. Caught while porting to Android, where the
surrounding code made the real domain obvious, and fixed on all 3 platforms
before it shipped further. **Always confirm the surrounding function/screen
name before trusting what a string appears to mean** — a flat "no X yet"
string carries no context of its own.

### 3. When the cause is someone else's, say so honestly — don't imply the reader broke something

Some empty states aren't "you haven't done anything yet" — they're "the
other party hasn't set something up yet." Conflating the two makes the
reader think *they're* missing a step when they aren't.

- Before: `"No menu items yet."` (shown to a buyer browsing a specific restaurant)
- After: `"This restaurant hasn't added menu items yet — check back soon."`
  (`RestaurantMenuView`/`DineInMenuView`, all 3 platforms)

- Before: `"No products yet."` (shown to a buyer browsing a specific store)
- After: `"This store hasn't added products yet — check back soon."`
  (`ProductCatalogView`, all 3 platforms)

## What this doesn't mean

- No invented mascot or character — itunda has no in-house illustrator to
  draw one, and a placeholder mascot with no real visual identity behind it
  would read as more fake than no mascot at all.
- No forced whimsy on a financial product. "Active now" and "you're all
  caught up" are warm; a joke about an empty wallet is not the register this
  product wants.
- Generic *failure* copy (`"Couldn't reach itunda. Check your connection and
  try again."`) stays generic on purpose — a real network failure has one
  real cause and one real remedy regardless of which screen it happened on,
  so specificity there would be manufactured, not genuine.

## Where this has shipped so far

Four rounds, ~40 call sites across bank-mfe/Android/iOS — see
`docs/DESIGN_REFERENCES.md` Section 9 recommendation #1 for the full,
per-round accounting.

**Round 5 (item 244, 2026-08-07, ~19 call sites × 3 platforms ≈ 57 strings):**
found while auditing web accessibility and discovering the same bare strings
existed on Android/iOS too, not just web — Marketplace/Community/Jobs/
Property (`MarketplaceScreen.kt`/`CommunityScreen.kt`/`JobsScreen.kt`/
`PropertyScreen.kt` on Android, `HoodScreen.swift` on iOS, `BankDashboard.tsx`
on web) had never gotten the rounds-1-4 treatment at all — every BROWSE/
NEARBY/NEIGHBORHOOD/MINE/PURCHASES/WORKED/ACQUIRED state was still a bare
"No X yet." Rewrote each to reference its own screen's real, visible "+ ..."
add button by its exact text (rule 1), and fixed Eats/Shop's "No
restaurants/stores/merchants registered yet." to honestly attribute the gap
to no merchant having joined (rule 3), matching the earlier `RestaurantMenuView`/
`ProductCatalogView` precedent exactly. Also caught merchant-mfe's
`ReviewsScreen.tsx` "No reviews yet." in the same honest-attribution pattern.

**Round 6 (item 244, 2026-08-07, 11 call sites, web-only):** independently
verified round 5's own sweep for completeness rather than assuming it was
exhaustive — found 11 more real, live bare strings round 5 didn't reach
(scoped to Hood features + Eats/Shop): bank-mfe's Float-marketplace "nearby
listings," recurring-payment detection, and Knowledge Q&A screens; ops-mfe's
agent registration list; merchant-mfe's booking/product reviews, POS
product catalog, billing plans, coupons, business transactions, webhook
deliveries, and devices. Each points back to a real, visible affordance
where one exists, or honestly attributes system/other-party-driven state
where none does. Caught a real bug while doing this: several edits placed a
`{/* JSX comment */}` directly inside a ternary's parenthesized branch — a
real parse error `vite build` caught (`tsc -b` alone didn't), fixed by
converting to `//` line comments before shipping.

~140 lower-traffic `EmptyState` call sites (mostly admin/niche-feature
screens) still remain a deliberately deferred follow-up, not a silent gap —
apply these same three rules to them as they're picked up.
