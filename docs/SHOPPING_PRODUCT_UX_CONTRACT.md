# Itunda Shopping — Discovery & Checkout Quality Contract

**Status:** implementation plan grounded in the current Shop web surface and public Toss Shopping product descriptions.  
**Product rule:** learn from the interaction principles; keep Itunda's own identity, assets, colors, copy, and Rwanda-first commerce model. Do not copy Toss branding or proprietary implementation.

## What exists today

The current Shop surface already has cross-merchant product search, merchant/category filters, deals rails, recently viewed items, a wishlist, orders/reorder, seller contact, multi-merchant cart, and checkout results. These are real foundations, not reasons to rebuild the shopping vertical from scratch.

## Product principles

1. **Show useful products before asking for work.** The first screen should explain what is available and expose relevant products/deals without requiring a search first.
2. **One primary action per state.** Search, product selection, add-to-cart, and checkout should each have an obvious next step; secondary tools must not compete with buying.
3. **Trust every number.** Price, original price, discount, stock, rating/review count, delivery estimate, seller status, and reward/cashback must be backed by live data. Hide unknown values rather than inventing them.
4. **Make total cost clear early.** Before payment, show item subtotal, delivery/fees, discounts/rewards, final total, delivery destination, and seller split. Do not imply one shipment when a multi-merchant cart creates separate orders.
5. **Recover without losing work.** Preserve cart lines for failed seller checkouts; explain which seller failed and how to fix it. Revalidate price, stock, and seller eligibility on the server at checkout.
6. **Reduce search effort.** Product search should have loading, empty, error, retry, and clear states; preserve the query when the user returns from a product.
7. **Keep the experience calm.** Use concise labels, generous spacing, predictable navigation, accessible touch targets, keyboard support, reduced-motion support, and Itunda's canonical IDS tokens.
8. **Personalization must be explainable.** Use observed behavior and explicit preferences only where supported. Provide a useful non-personalized fallback; do not imply that a recommendation is personalized if it is just a generic rail.
9. **Localize for Rwanda.** Kinyarwanda-first copy, clear RWF prices, local delivery/address conventions, and realistic seller coverage. Do not surface Korean-only shopping assumptions.
10. **Protect the purchase.** Prevent duplicate submits, use idempotency keys for order creation, disable the submit control while in flight, and show a durable order result. A UI swipe gesture is only an interaction affordance, never proof that payment succeeded.

## Required states and acceptance criteria

### Browse / home
- First-time users see real, active SHOP products and meaningful categories or a clear no-sellers-yet state.
- Merchants/restaurants are isolated by business type; Shop must not show Eats-only merchants.
- Each rail has a clear purpose and does not repeat the same products excessively.
- Product cards show image fallback, name, current price, and only verified discount/rating/review/stock signals.
- Loading and retry states are accessible and do not flash misleading empty states.

### Search
- Search spans active SHOP products across merchants and is debounced where appropriate.
- Submit empty/whitespace-only queries is blocked.
- Loading, API failure, zero matches, and successful results have distinct states.
- Opening a result leads to the matching merchant and product, not merely the merchant catalog.
- Clearing search restores the previous browse context without unexpectedly clearing the cart.

### Product detail
- Show price, availability, seller, relevant options (when supported), delivery/return information, and verified reviews.
- Quantity changes are bounded by stock when stock is known.
- Add-to-cart feedback is immediate and accessible; the cart count/summary stays consistent.
- Seller claims, discounts, and delivery promises must come from trusted server data.

### Cart / checkout
- Group lines by merchant and explain that fulfillment/order results can differ per seller.
- Refresh or validate stock and price server-side at checkout; return line-level change information.
- Display full cost breakdown and delivery destination before the final confirmation.
- Block double submission and make order creation idempotent.
- Partial multi-seller success clears only successful groups; failed groups remain editable and retryable.
- A failed payment/order must never be presented as successful based only on a client animation.

### Orders / trust
- Order status, tracking, cancellation, returns, refunds, and seller contact reflect backend capabilities and current server state.
- Reorder revalidates active products and prices, merges intentionally, and always opens a reviewable cart.
- Wishlist and cart survive navigation; persistence across sessions must be explicitly implemented and tested before being promised.

### Quality gates
- Unit tests for search state transitions, quantity bounds, cart totals, partial checkout success, duplicate submit protection, and reorder sanitization.
- Integration tests for product search -> correct product detail -> cart -> checkout -> order result.
- Accessibility checks for keyboard navigation, screen-reader labels, focus restoration, contrast, and reduced motion.
- Mobile responsive verification on narrow Android-sized viewports.
- Backend tests for stock/price revalidation, idempotency, authorization, and multi-merchant isolation.
- Verify CI and production smoke tests before claiming release readiness.

## Implementation sequence

1. **P0 — Fix the highest-friction browse/search defects:** distinguish product search states, ensure a search result opens the exact product, add clear/retry behavior, and retain the query/cart.
2. **P0 — Checkout safety:** server-side price/stock revalidation, idempotency, duplicate-submit protection, and partial-failure recovery.
3. **P1 — Discovery quality:** active product/category feed, remove repetitive rails, improve relevance from real signals, and track click-through/add-to-cart/conversion with privacy-respecting analytics.
4. **P1 — Trust content:** seller policies, delivery estimates, return/refund status, and complete price breakdown only where real backend data exists.
5. **P2 — Personalization and seller tools:** explicit preference controls, explainable recommendations, and seller analytics based on actual events.

## Public reference

Toss Shopping's public product description emphasizes reducing the work of finding products, helping people discover relevant items from the first screen, reducing price-comparison effort, and simplifying checkout. This document translates those high-level outcomes into Itunda-specific, testable criteria; it does not claim access to Toss's private source code.

- https://10904.toss.support/service/shopping
- https://toss.im/business/shopping
