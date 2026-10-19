# Itunda Eats — Coupang Eats Parity Audit

**Status:** source-backed audit and implementation plan; not a claim of feature completeness.  
**Baseline:** main at 347c8d2afbfe0ce062c681598f133e1d1a03733f (2026-10-13).  
**Product rule:** learn from Coupang Eats' customer experience, but keep Itunda's brand and design system. Adapt pricing, payments, delivery operations, and consumer protection to Rwanda; do not copy Korean-only membership or subsidy assumptions.

## Existing capabilities confirmed in source

| Area | Evidence in repository | Audit result |
|---|---|---|
| Restaurant discovery | services/micro-frontends/bank-mfe/src/lib/eats.ts | Real restaurant-only catalog query; category/search filters; delivery-time, favorites, rating, distance, discount, and minimum-order sorts. Location-aware sorts require buyer coordinates. |
| Menu customization | Same buyer API file; services/backend/eats/src/main/kotlin/rw/itunda/eats/EatsOrderService.kt | Required/multi-select option groups and per-choice price deltas are represented. |
| Availability and offers | Same buyer API file and shared merchant-product response | Sold-out state, original price, discount, and best-seller signals are represented. |
| Fulfillment | services/backend/eats/src/main/kotlin/rw/itunda/eats/web/EatsController.kt | Delivery and pickup; scheduled orders are represented in the request contract. |
| Delivery tracking | android/features/eats/impl/src/main/java/rw/itunda/feature/eats/impl/EatsOrders.kt; iOS Eats order screens | Live rider-location UI paths exist; production freshness and end-to-end runtime behavior still require device verification. |
| Restaurant operations | services/micro-frontends/merchant-mfe/src/lib/eats.ts | Restaurant order queue, status changes, pickup completion, order detail, and unavailable-item flow are exposed to the merchant web app. |
| Loyalty-like value | services/backend/merchant/src/main/kotlin/rw/itunda/merchant/MerchantLoyaltyPointsService.kt; services/backend/core/src/main/kotlin/rw/itunda/core/geo/EatsPromotionCalculator.kt | Generic per-merchant loyalty points already exist, but accrual is currently called from MerchantService.collect (merchant payment collection), not from completed Eats orders. Eats promotions/memberships are separate. Reuse merchant identity and customer-facing balance patterns, but do not treat these points as Eats stamps or award them from the wrong payment flow. |
| Trust | services/backend/eats/src/main/kotlin/rw/itunda/eats/EatsReviewService.kt and associated UI | Post-order reviews exist; reporting/reply and rider rating flows are part of the broader Eats implementation. |

## Highest-priority parity gap: restaurant stamp rewards

Coupang announced restaurant stamp events on 2026-09-03: customers earn stamps on completed delivery orders and receive a restaurant-defined coupon or menu reward after five stamps. This is a useful retention pattern, not a requirement to copy the exact Korean threshold or reward.

Official evidence: https://news.coupang.com/archives/66229/

A repository-tree audit of the baseline found no dedicated Eats stamp-event domain/service/migration or stamp client module. It also found that generic MerchantLoyaltyAccount is a mutable points balance with inactivity expiry, not an immutable per-order stamp ledger. Treat the Eats stamp capability as **not found in the audited source**, not proof that no prototype exists elsewhere. The new feature should be order-scoped and auditable rather than bolting stamp counts onto that balance.

### Implementation contract

1. **Restaurant-managed campaign** — owner can create, pause, and end a campaign; define eligible order conditions, stamp threshold, reward type, start/end time, and per-customer limits. Validate owner permissions server-side. Keep this campaign config separate from generic merchant payment points.
2. **Earn only on qualifying completed orders** — award after delivery/pickup completion and confirmed settlement, never on order placement. Exclude cancelled, refunded, self-orders, and ineligible items according to explicit campaign rules.
3. **Exactly-once accounting** — database uniqueness for (campaign_id, order_id) and for reward redemption identifiers. Repeated completion events must not double-award stamps.
4. **Immutable event ledger** — record earned/reversed/redeemed events with timestamps and reason. Refund or chargeback handling must reverse eligibility idempotently; never silently rewrite history.
5. **Atomic reward redemption** — verify campaign active status, user stamp balance, reward availability, expiration, and order eligibility in one transaction. A concurrent double tap must not redeem twice.
6. **Customer UX** — show progress and reward terms on the restaurant page, item/menu flow, order receipt, and a Rewards area; make expiry and exclusions clear. Do not show a fake progress count if the API is unavailable.
7. **Merchant UX** — expose campaign setup and earned/redeemed summaries in merchant-mfe; enforce a server-side cap and audit changes.
8. **Notifications** — send stamp-earned and reward-unlocked notifications after commit, with deduplication keys. Notification delivery must not decide whether the reward was earned.
9. **Rwanda adaptation** — allow RWF-denominated discounts or menu-item rewards, with restaurant-funded/platform-funded responsibility explicit. Do not advertise free delivery or a membership benefit unless the order's actual eligibility and funding are verified.

## Remaining verification gates before calling Eats “Coupang Eats-level”

- Verify the deployed Android, iOS, buyer web, merchant web, and rider apps against the same order-state contract.
- Verify live tracking uses fresh rider coordinates and clearly degrades when location permission/network is unavailable; do not show a moving marker from stale data.
- Verify checkout displays the complete amount breakdown (items, options, discounts, delivery, platform fee, tax if applicable, and final total) before confirmation.
- Verify coupon stacking rules and eligibility on the server; client-displayed discounts must match the charged ledger amount.
- Verify pickup, scheduled orders, restaurant-paused state, sold-out items, partial item unavailability/refunds, cancellation, and delivery failure paths end-to-end.
- Add database-backed integration tests for duplicate completion, concurrent reward redemption, refund reversal, and campaign boundary conditions.
- Validate real Rwanda restaurant coverage, delivery zones, hours, distance/time estimates, and rider availability from production data; never pad results with placeholders.
- Keep Itunda's canonical logo, Brand v3 color tokens, and IDS components. Use behavioral inspiration, not Coupang Eats' copied visual assets or branding.

## Suggested delivery order

1. Add stamp campaign and immutable stamp-event schema with database uniqueness constraints.
2. Implement service-level earn/reversal/redemption rules and database-backed concurrency tests.
3. Add buyer progress/reward UI and merchant campaign controls.
4. Add after-commit notifications and observability for duplicate/rejected events.
5. Run backend and web tests, Android/iOS builds, then production smoke tests before release claims.

**Release policy:** this audit document is not an implementation, CI pass, deployment, or verified APK. Keep the feature behind a release gate until its backend invariants and all client paths are tested.
