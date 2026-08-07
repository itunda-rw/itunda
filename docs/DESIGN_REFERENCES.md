# itunda Design References: Structural & Interaction Patterns from Real Products

**Status:** Research synthesis, 2026-07-20/21. Six parallel deep-dives, one per itunda product
surface, into real reference ecosystems (Naver, Kakao, Baemin/Woowa Brothers, Karrot/당근마켓,
Coupang, Toss).

## 0. What this document is not

itunda's design system (IDS) uses **one consistent blue brand color across the entire app**, and
that is not changing. A prior attempt to give Hood its own accent color (mirroring Karrot's real
orange) was deliberately reverted: a real multi-product ecosystem like Kakao keeps one main color
across its whole family for a consistent feel, and itunda is a single app, not a family of
separate apps — per-tab color fragmentation would be the wrong lesson to take from that ecosystem.

**Re-verified, 2026-08-04:** this Kakao claim was checked directly against KakaoPay's own official
brand page (kakaopay.com/brand) rather than assumed — KakaoPay's stated brand color is `#FFEB00`,
the same yellow family as KakaoTalk (`#FEE500`)/KakaoBank, not a distinct mint or blue as might be
guessed. The single-color-across-the-family discipline holds up under direct verification, so this
document is **not** recommending itunda abandon its one-blue rule. One real, sourced nuance worth
the design team's own explicit decision (not applied here without asking): Naver's own practice is
softer than Kakao's — Naver Maps' color system is described as "based on the existing green and
blue of NAVER Maps, a naturally extending spectrum," i.e. tints/shades *within* one hue family
that vary by service, rather than either strict single-color-everywhere or fully separate
per-service colors. Whether itunda's modules could similarly use tonal *variations* of the same
blue (not new hues) is a real open question this document surfaces but does not resolve.

This document contains **zero color recommendations**. It is entirely about information
architecture, list/card layouts, bottom sheets, search/filter UX, navigation idioms, empty
states, chat UX, and map overlay patterns — sourced from named real products, official
design-system docs, and real engineering/product blogs. Where a claim could not be sourced to
something specific and verifiable, it is labeled `inferred` and should be treated as a hypothesis,
not a confirmed pattern.

**Correction, 2026-08-04:** an audit of this document's own citations found that despite the title
above claiming "six parallel deep-dives," the actual sourcing is heavily lopsided — roughly 33
citations trace to Toss (toss.im/toss.tech/developers-apps-in-toss) versus single digits for
Naver, Kakao, Coupang, Baemin, and Karrot combined, and Section 9 (Graphics) below was **100%
Toss-sourced** with zero citations to any other ecosystem. This is the real, verified root of a
real complaint: itunda's design language reads as "Toss, uniformly" rather than genuinely
synthesized from all six reference ecosystems this document claims to draw from. Section 9 has
been rewritten below with real research into the other five; re-confirmed Kakao's real
single-brand-color discipline (see the new note under Section 0) rather than silently reversing
it, since that decision was made deliberately and explicitly and is not this document's call to
overturn unilaterally.

Every recommendation below carries a confidence tag:

- **sourced** — backed by an official doc, a named product's documented behavior, or a
  cross-verified press/blog account.
- **partially-sourced** — the underlying pattern is real, but at least one detail (exact figures,
  full article content, single-source claim) couldn't be independently corroborated.
- **inferred** — general UX reasoning, not confirmed against the named product. Flagged explicitly
  wherever it appears; treat as lowest priority and validate before building.

Within each surface, recommendations are ranked by confidence first, then by how large a real gap
they close in itunda's current implementation.

---

## 1. Maps

### References

| Product | Source | Pattern |
|---|---|---|
| Naver Map — Smart Around (스마트어라운드) | brunch.co.kr/@bydot/4 (UX teardown) | Persistent, non-modal bottom sheet docked over an interactive map; peeks rather than disappears; scrolling expands past filters into curated horizontal sections (Today's Pick / Nearby / Worth visiting this week / Frequently saved / New openings); place-detail sheet shows hours/rating/swipeable review photos inline |
| NAVER Maps official app / Cloud Maps docs | map.naver.com; guide.ncloud-docs.com/docs/en/application-maps-overview; guide.ncloud-docs.com/docs/en/maps-url-scheme | Official app promotes themed saved-place lists, sharing, Home/Work quick directions, panorama, and real-time transit. Its documented app handoff covers opening a map, search, a coordinate marker, driving/walking/bicycle/transit routes, navigation, and five intermediate waypoints. Itunda now supports its own authenticated `itunda://maps` entry, search, coordinate reverse geocoding, real driving/walking directions, and five intermediate waypoints; adapt only capabilities supported by Itunda's own Rwanda data and self-hosted routing. |
| Kakao Map — place-detail redesign (v5.18.0+) | ditoday.com | Always-visible action-button row (reserve/delivery/bookmark/call/directions) up front; larger hero images; improved transit-info visibility for transport POIs |
| Kakao Map — 즐겨찾기 (bookmarks) | kakaocorp.io, cs.kakao.com | Bookmarks grouped into named, colored folders; group color becomes marker pin color; bulk recolor per group; shareable via KakaoTalk/link; syncs to map.kakao.com |
| Naver Map — saved places | antennagom.com, echeveau.net | Save flow triggered from the info sheet's "저장" button; choose/create a list; each list has name + color (becomes marker color) + public/private; public lists get a shareable URL; documented caps (2,000/list default, 1,000/custom list, 400 lists, 5,000 total) |
| Apple Maps / iOS `UISheetPresentationController` | developer.apple.com | Official multi-detent sheet API (`.medium()`/`.large()`), grabber handle, `largestUndimmedDetentIdentifier` keeps the map interactive under the sheet up to the medium detent |
| Google Maps (Android) bottom-sheet state model | developer.android.com `BottomSheetBehavior` docs + `reline/Google-Maps-BottomSheet`, `miguelhincapie/CustomBottomSheetBehavior` (GitHub) | Real place-detail needs 3 states (peek/anchor/full); stock `BottomSheetBehavior` only gives 2 cleanly — teams had to build custom extensions to get Google Maps' real middle "anchor" state, confirming this is a genuine, nontrivial engineering gap |
| Kakao Map — filter chips | search-snippet of icunow.co.kr (article itself unreachable, TLS cert mismatch) | Chip row combines a contextual "search this area" chip with multi-select toggle chips; deeper filters (parking, pet-friendly, wheelchair, 24hr) live in a separate popup |
| Kakao Map — 길찾기 (directions) | consumer blogs sisimoms.com, wishwrite.com (not official) | Transit options combine bus+subway, color-coded to real line colors, each summarized with duration/fare/transfers; walking directions show distance/duration/calories with crosswalk-aware routing |

### Recommendations (ranked)

1. **[sourced] Implemented.** Replace the static floating `Card` with a real draggable peek/half/full
   bottom sheet. Was a plain `Column`/`Card` pinned to `BottomCenter` with no drag gesture — content
   just appeared/disappeared at whatever height its content dictated. Both Apple's own
   `UISheetPresentationController` and Google Maps' documented need for a custom
   `BottomSheetBehavior` extension confirm a true 3-state (peek/anchor/full) sheet is real,
   nontrivial engineering — Compose's stock `BottomSheetScaffold` only gives 2 states out of the
   box. **Verified 2026-08-04 (re-audit while sourcing the Eats dish grid): this file moved to
   `features/maps/impl/.../MapsScreen.kt` during the later Feature-module extraction (stale path in
   this doc), and a real `AnchoredDraggableState`-based 3-state sheet exists there.**
   *Target: `android/features/maps/impl/src/main/java/rw/itunda/feature/maps/impl/MapsScreen.kt`*

2. **[sourced] Implemented 2026-08-04 (partial, honest scope).** Give the sheet a default "around
   me" state instead of only rendering when a place is selected or bookmarks exist. Naver's Smart
   Around keeps a non-modal sheet permanently docked with 5 curated sections (오늘의 PICK/주변/이번
   주에 가볼 만한/이번 주에 많이 저장한/새로 오픈한) even before any search, contracting to a peek
   rather than vanishing -- confirmed by directly re-fetching the primary teardown
   (brunch.co.kr/@bydot/4), not just a search snippet. itunda has real, non-fabricated data for
   exactly 2 of the 5: 주변 (`MapsService.getAroundMe`, merging a real Nominatim call per real
   `MapPlaceCategory` since the pre-existing `getNearbyPlaces` required a category to already be
   picked) and 이번 주에 많이 저장한 (`getTrendingSavedPlaces`, a real cross-user aggregate over
   `MapBookmark.createdAt` -- how many distinct real users saved a place in the real trailing
   window). The other 3 imply editorial curation or a "date opened" signal Nominatim/OSM carries
   neither of -- deliberately not built as fabricated lists, documented in the code itself. Rendered
   in `MapsScreen.kt`'s sheet as two real horizontal-scroll sections in the true empty state.
   *Target: `MapsScreen.kt` — default state when `selectedPlace == null && activeCategory == null`*

3. **[sourced, Kakao Map -- flagged 2026-08-04; address part implemented same day]** Elevate the
   place-detail card's action row and image. itunda's selected-place card still shows only a name, a
   star toggle, and a single "Directions" button. **This recommendation's own source is Kakao Map's
   redesign, not Naver's** -- during a Naver-specific pass, a fresh, more targeted search confirmed
   real Naver Map place-detail sheets do show richer info (주소/연락처/영업시간/메뉴판/사진/주차 --
   address/contact/hours/menu/photos/parking), but no primary Naver source with precise
   action-row/hero-image layout specifics was found (only a general feature-list description). The
   action-row/hero-image part is real but still open (needs primary Naver sourcing before building
   Kakao's specific layout as if it were Naver's). **Re-attempted 2026-08-05**: three more targeted
   web searches (Korean-language, aimed at UI/UX breakdown blogs and app-review sites) still found
   no primary source with precise action-row-ordering or hero-image-placement specifics -- one
   design-analysis blog post (brunch.co.kr/@bydot/4) covers the place-detail screen's scrollable
   content sections (기본 정보/메뉴/방문자 사진/리뷰) but not the header/action-row layout. Still
   genuinely unsourced, not just unsearched -- don't re-attempt without a materially different
   search angle (e.g. an actual screenshot walkthrough, not a feature-list article). The address
   part shipped 2026-08-04:
   **correcting an earlier same-day misdiagnosis** -- this session's own backend restarts had been
   omitting `NOMINATIM_BASE_URL`, so itunda's self-hosted Nominatim (which genuinely IS configured
   and populated with real Rwanda OSM data, confirmed once restarted correctly -- e.g. "Miracle
   Pharmacy, KN 81 Street, Nyarugenge...") was silently falling back to its documented "unconfigured
   → empty, never fabricated" behavior the whole time, which had been read as a real data-coverage
   gap. Once corrected, confirmed `displayName` already carries the real full address, just as one
   run-on string. `splitPlaceName` (MapsScreen.kt) splits it on Nominatim's own convention (segment
   0 = specific place name, the rest = real address hierarchy) into a bold name + muted address
   line, matching real Naver card layout -- no new backend field, pure presentation of data already
   present.
   *Target: `MapsScreen.kt` selected-place card*

4. **[sourced] Implemented.** Group bookmarks into named, colored lists instead of one flat list.
   Both Kakao Map (그룹 + per-group color, shareable) and Naver Map (named list + color +
   public/private, shareable URL) let the list color become the marker pin color on the map —
   letting a user visually distinguish saved-place categories on the map itself, not just in text.
   **Verified 2026-08-04: `BOOKMARK_COLOR_PALETTE`, per-folder `color`, and a real move-between-
   folders flow all exist in `MapsScreen.kt`.** The public/private + shareable-URL half was
   genuinely still missing at that point (no `isPublic` concept anywhere) -- **closed the same day**:
   migration V225 adds `MapBookmark.isPublic`; `MapsService.setFolderPublic`/`getPublicFolder`
   bulk-toggle and read a whole named folder. itunda has no public web frontend for Maps (only the
   existing app-only `itunda://maps` deep link), so sharing reuses that real mechanism instead of a
   fabricated web URL -- a real, deliberately unauthenticated `GET /api/v1/maps/shared/{userId}/
   {folderName}` backs a folder-level Share toggle in `MapsScreen.kt`'s bookmarks list. Live-verified
   with a genuinely unauthenticated curl call (no token at all).
   *Target: `MapsScreen.kt` plus backend `MapBookmarkDto`/`AddMapBookmarkRequest`, which needed a list/group + color field*

5. **[partially-sourced, Kakao Map -- corrected 2026-08-04]** Give turn-by-turn its own
   presentation instead of an inline expand/collapse text block (`showSteps` toggle). Kakao treats
   routing as its own dedicated presentation with per-route summaries. **A fresh, Naver-specific
   search found this does NOT hold for Naver**: real 네이버 지도 directions explicitly keep the map
   route and the detailed text-based turn-by-turn visible together "on one screen at once"
   (이용가이드/사용법 guide sites), closer to itunda's existing inline-toggle approach than to
   Kakao's separate-presentation pattern. Building a Kakao-style separate screen here would now
   actively contradict real Naver behavior -- not implemented, and shouldn't be without a stronger,
   Naver-specific primary source.
   *Target: `MapsScreen.kt` (`showSteps` toggle) -- not currently recommended for a Naver-parity pass*

6. **[inferred] Implemented.** Move search from submit-then-list toward autocomplete with a
   recent-searches zero state. itunda's search was tap-"Search"-then-flat-list with no live
   suggestions and no recent-searches state. This is general autocomplete UX practice, **not**
   independently confirmed as Naver/Kakao Map's specific implementation — flagged as the
   weakest-sourced item in this section. **Verified 2026-08-04: real search-as-you-type
   autocomplete (shipped 2026-07-22, ported from bank-mfe) plus `RecentMapSearchesStore` backing a
   real recent-searches zero state.**
   *Target: `MapsScreen.kt` search field and results list*

7. **[partially-sourced, Kakao Map -- flagged 2026-08-04]** Category chips already structurally
   match Kakao's pattern reasonably well — this is a genuine partial match, not an urgent gap. The
   one documented difference: Kakao's chips are multi-select toggles paired with a separate detail
   popup; `searchNearbyCategory()` is strictly single-select today. This item is Kakao-sourced, not
   Naver -- lowest priority of the three Kakao-attributed items in this section (3/5/7) given it was
   already described as a non-urgent partial match even before that correction.

### Unresolved / worth a follow-up

- **Rwanda data boundary (verified 2026-07-22):** Itunda has no configured transit/GTFS feed,
  traffic probe/feed, CCTV feed, or street-level/panorama image provider. Consequently, Maps
  deliberately does not present arrival predictions, congestion claims, crowd estimates, or
  panorama controls as if they were real. These need a first-party data collection programme or a
  licensed Rwanda data source before they become product work; routing, saved places, nearby
  discovery, and up-to-seven-stop itineraries remain fully self-hosted today.
- Naver's autocomplete/recent-search behavior wasn't independently verified — only generic
  search-UX practice surfaced.
- icunow.co.kr (Kakao filter/sort breakdown) was unreachable; only a search-snippet was usable.
- No official Kakao Map design-system doc exists publicly — everything here is third-party
  teardown, which is normal for this product but should be corroborated with a second source
  before treating fine details (e.g. exact section names) as authoritative.
- No public spec for exact snap-point heights/animation timing from Naver/Kakao — any itunda
  implementation will need to choose its own values.

---

## 2. Eats (food delivery)

### References

| Product | Source | Pattern |
|---|---|---|
| 배달의민족 (Baemin) | brunch.co.kr/@plusx/69, techblog.woowahan.com, news.nate.com (추천 리뷰), story.baemin.com, mt.co.kr (2024 overhaul) | List card shows delivery time/fee/min order/rating together; 8-way swipeable filter chips; "추천순" review ranking (weights photo + length + recency); review-stats aggregate block; 2024 overhaul split home into per-service tabs; owner replies to reviews (사장님 댓글) |
| 쿠팡이츠 (Coupang Eats) | yozm.wishket.com, brunch.co.kr/@uibowl/356, partners.coupangeats.com (official seller guide), namu.wiki | Proactive address-confirmation bubble on open; "주문많음"/좋아요 tags on menu items; **required-option system where at least one +0원 choice is mandatory** so list price never mismatches checkout price (explicit seller-guide constraint); 치타배달/단건배달 (one rider, one order) speed badge, pioneered 2019, since industry-adopted; in-cart delivery↔pickup toggle; one-tap order with no confirmation screen; bifurcated post-delivery rating (food vs. delivery, binary good/bad with reason tags on "bad"); live rider map + phone + vehicle type with 4-stage status chain |
| 배민오더 (Baemin Order) — official product page | baemin-order.com, story.baemin.com | Table/QR in-store ordering: each physical table gets its own printed QR; scanning opens that restaurant's real menu pre-scoped to that table, no delivery address/rider step at all; order status is restaurant-driven only (accept -> preparing -> served); payment settles to the restaurant immediately rather than sitting in a delivery-holding account |
| 배민상회 (Baemin Sanghoe) — B2B restaurant-supplies marketplace | hankyung.com (40조원 ingredient-distribution market entry), mart.baemin.com, cidermics.com | Online wholesale mall where restaurant owners buy ingredients/packaging/tableware at real bulk-discount pricing; targets small owners/first-time founders with explicit "save money on sourcing" positioning; entered a ~40조원 market by piggybacking on Baemin's own existing restaurant-owner relationship, not a separate acquisition funnel |

### Recommendations (ranked)

1. **[sourced] Implemented.** The restaurant card is missing the data model to show delivery
   metadata at all. `ShoppingMerchantDto` (`ApiService.kt:488-492`) has only `merchantId,
   businessName, category, cashbackRate` plus optional lat/lng — no `photoUrl`, `rating`,
   `reviewCount`, `deliveryFee`, `deliveryTimeMinutes`, `minOrderAmount`, `distanceKm`. Both Baemin
   and Coupang Eats put all of these directly on the list card so restaurants are comparable before
   opening any of them. This needs a backend model change (table + DTO + endpoint), not just a UI
   tweak. **Verified 2026-08-04 (re-audit while sourcing the dish grid below): `ShoppingMerchantDto`
   now carries every one of these fields and `RestaurantCard` renders them inline — this doc entry
   was simply stale, not an open gap.**
   *Target: `ApiService.kt:488` + `/api/v1/shopping/merchants` + `OrderFoodContent`'s browse `LazyColumn`*

2. **[sourced] Implemented.** Rating exists but sits behind an extra tap. `RestaurantRatingBadge` is
   only called inside `RestaurantMenuView` (`SuperAppTabs.kt:4102`) — after a restaurant is already
   open. Real apps put rating on the browse-list card. Fix: fold rating/reviewCount into the
   existing list payload (a join, not a new round trip). **Verified 2026-08-04: `RestaurantCard`'s
   own secondary line already shows rating/reviewCount/distance/ETA/minOrder together — closed
   alongside item 1, same stale-doc-entry finding.**
   *Target: `SuperAppTabs.kt:4102`, browse list*

3. **[sourced] Implemented.** No menu options/customization model — the single biggest structural
   gap. Coupang Eats' own seller guide makes required-option groups a first-class, **enforced**
   concept (explicitly requiring a +0원 choice so list and cart price never diverge); Baemin's item
   detail is built the same way. itunda's `MerchantProductDto` (`ApiService.kt:531`) is flat
   `id, merchantId, name, price, active, createdAt` — no way to represent spice level, size, or
   add-ons. Without this, itunda cannot represent most real restaurant menus. **Verified 2026-08-04:
   `MerchantProductDto.optionGroups`/`EatsMenuOptionGroupDto`/`EatsMenuOptionChoiceDto` exist,
   `MenuOptionService` enforces the real "a required group needs at least one real choice"
   constraint from Coupang's own seller guide, and per-configuration cart lines (item 6 below) are
   built on top of it.**
   *Target: `ApiService.kt:531`, item row in `RestaurantMenuView:4109-4125`*

4. **[sourced] Implemented.** No live rider-location map — status is a text label swap
   (`EATS_STATUS_LABELS`/`RIDER_STATUS_CHAIN`, `SuperAppTabs.kt:3519-3527`). itunda already has a
   self-hosted OSRM/Nominatim stack and a `RouteMiniMap` component used elsewhere
   (`~1674`) — the pieces exist, just not wired into Eats tracking the way Baemin/Coupang Eats do
   (live rider dot + phone + vehicle type during delivery). **Verified 2026-08-04: real live
   rider-location tracking (item 182) wires `RouteMiniMap` into the delivery-in-progress order view
   with the real restaurant/delivery coordinates.**
   *Target: `SuperAppTabs.kt:3519-3527`, `4330` (`EatsOrderRow`), pair with `RouteMiniMap`*
   **Commerce (Shop) sibling closed 2026-08-05 (item 230):** found via a
   defined-but-uncalled-endpoint sweep — `OrderController.getRiderLocation` (Commerce backend, real
   since 2026-07-26) mirrors `EatsOrderService.getRiderLocation` exactly (same `RiderLocationDto`
   shape, same buyer/seller/rider authorization) but had zero client callers on any platform. Shipped
   as `SimpleLiveRiderMap`/`SimpleLiveRiderMiniMap` on bank-mfe, Android, and iOS — an honest v1
   scope-down from the Eats version above: Commerce's `Order`/`OrderDto` carries no delivery-coordinate
   fields (only free-text `deliveryAddress`), so there's no "to" endpoint to draw a route toward. This
   shows the rider's own live position only (single marker, no route line, no from/to pins), a
   "Track your rider live" toggle shown once an order is `SHIPPED`.
   *Targets: `lib/commerce.ts`/`SimpleLiveRiderMap.tsx` (bank-mfe); `ApiService.kt:getOrderRiderLocation`,
   `LiveRiderMiniMap.kt:SimpleLiveRiderMiniMap` (Android); `NetworkClient.swift:getOrderRiderLocation`,
   `SimpleLiveRiderMiniMap.swift` (iOS) — all wired into their respective Commerce order-list views.*

5. **[sourced] Owner reply implemented; photo implemented 2026-08-04.** Review model has no photo
   field and no owner reply. `SubmitEatsReviewRequest` (`~4063`) is numeric stars + optional text
   only. Baemin's 2022 push ranks photo-bearing reviews first via 추천순 정렬, and 사장님 댓글 (owner
   replies) is a named, sourced feature. Food-delivery trust leans disproportionately on photos of
   the actual plated food, making this higher-leverage here than on other surfaces. Owner reply
   (`EatsReview.ownerReply`) shipped 2026-07-26. Photo (`EatsReview.photoUrl`, migration V224)
   shipped 2026-08-04, closing this item fully — same real-external-URL-only convention as
   `Merchant.photoUrl`, verified via 3 new `EatsReviewServiceTest` cases (trim, 500-char truncation,
   null-passthrough).
   *Target: `SubmitEatsReviewRequest`/`ReviewOrderCard`, `SuperAppTabs.kt:3997-4063`*

6. **[inferred] Implemented.** Cart cannot hold two configurations of the same item, and has no
   per-line notes. `cart = remember { mutableStateMapOf<String, Int>() }` (`3580`) is keyed by raw
   product id only. This follows structurally from the missing options model above (item 3) rather
   than being an independently sourced claim, and should be fixed alongside it. **Verified
   2026-08-04: `EatsCartLine`/`eatsCartKey` (keyed by `productId` + sorted `choiceIds`) closes this
   — two configurations of the same item are genuinely distinct cart lines.**
   *Target: `SuperAppTabs.kt:3580`, consumers in `EatsCheckoutView:4214+`*

7. **[sourced] Implemented 2026-07-26.** No delivery-speed badge / no single-order vs. batched-order distinction. Coupang
   Eats pioneered 단건배달/치타배달 (2019); Baemin now has 배민1. Lower priority than the above —
   itunda has no equivalent anywhere, but this is a purchase-decision nicety, not a functional gap.
   *Target: browse list, `~3746+`; no current equivalent* -- **and a real, live bug found while
   closing it**: nothing in `EatsOrderService.claimDelivery` ever stopped a rider from claiming a
   second delivery while still carrying an unfinished one (no batching logic exists anywhere to
   justify it either). Fixed: a rider can now only claim one active delivery at a time, real-409
   (`RIDER_ALREADY_ON_DELIVERY`) otherwise, and the dispatch/notify candidate pool now excludes
   busy riders too (skipping straight to the next free candidate instead of wasting an offer on
   someone who can't accept it). `singleOrderDelivery: true` surfaced on
   `GET /api/v1/shopping/merchants`, honestly true because it's now enforced, not decorative copy.
   See `docs/TOSS_PARITY_MATRIX.md`'s Eats row for the full live-verified account.

8. **[sourced] Implemented 2026-07-25.** Add table/QR in-store ordering, matching 배민오더's real
   product exactly: `EatsOrder` was hardcoded delivery-only (`deliveryAddress` `nullable = false`,
   plus rider/delivery-fee-holding fields with no way to skip them). Rather than widen that
   already-tested entity, this ships as a purely additive `DineInOrder` sibling with no
   delivery/rider fields at all -- payment settles straight to the restaurant's wallet at
   placement instead of sitting in `eats_delivery_holding`. Ships with real per-table QR
   generation on the merchant side (reusing the existing ZXing `generateQrBitmap` util from
   the register/POS flow) and a Delivery/dine-in toggle at consumer checkout.
   *Shipped: `DineInOrder`, `DineInOrderService`, `POST /api/v1/eats/dine-in/orders`,
   merchant app's Dine-in tab (table QR + order queue), `EatsCheckoutView`'s mode toggle*

9. **[sourced] Implemented 2026-07-25.** Add real bulk/wholesale pricing, closing the gap named
   in Baemin's own real 배민상회 B2B supplies marketplace research -- rather than build a second,
   B2B-only catalog system (배민상회 is itself just Baemin's existing merchant relationship reused
   for a different buyer intent), this reuses itunda's existing Shopping/`MerchantProduct` catalog
   completely: the real differentiator is that price genuinely depends on quantity, not a separate
   marketplace. A product with no real tiers behaves exactly as before -- purely additive. Tiers
   are validated as a real, honest bulk-discount schedule (every tier, including a lone first one,
   must cost strictly less per unit than the product's own flat retail price -- a real gap in an
   early version of this validation was caught live via curl testing before shipping: a lone tier
   priced ABOVE retail slipped through because the check only compared tiers to each other, never
   to the base price). `OrderService.placeOrder` resolves the real effective unit price from the
   real ordered quantity server-side, same "price is never trusted from the client" discipline
   this class's own doc comment already establishes.
   *Shipped: `ProductPriceTier`, `MerchantProductService.setPriceTiers`,
   `POST /api/v1/merchant/products/{id}/price-tiers`, merchant app's "Bulk pricing" editor, Shop's
   "Buy N+ for X each" hint*

10. **[sourced] Implemented 2026-08-04.** Home browse wasn't a photo-forward dish grid at all — a
    real Coupang Eats-specific UX teardown (brunch.co.kr/@e6b24f6f7c6949f/20, re-fetched directly
    and re-confirmed, not just search-snippet-summarized) states the real home surface is a
    3-column grid of individual food photos, with rating/delivery-time/fee deliberately deferred to
    the restaurant page. `GET /api/v1/eats/dishes` + `EatsDishGrid` ships this as an *additive*
    rail above the existing restaurant list (which stays — its real search-by-name has no
    equivalent in the dishes endpoint). **A real, live bug was found while verifying this against
    real seeded data**: the shared Shop/Eats merchant directory (`getShoppingMerchants`) was 90%
    (45/50) leftover QA test-fixture merchants with no real category set, polluting both Shop's and
    Eats' actual browse results for a real user (a hair salon, a clothing shop, and a fake
    streaming service showing up in what should be a restaurant list). There was previously no way,
    anywhere in the app, to take a merchant out of public browse once created — fixed with a real
    ADMIN-gated moderation endpoint (`GET/POST /api/v1/system/merchants/**`), not a one-off DB
    cleanup, since the same class of pollution can recur from future test runs.
    *Shipped: `MerchantProductRepository.findDishes`, `EatsController.getDishes`, `EatsDishGrid`,
    `MerchantRepository.findByStatusAndCategoryIsNull`, `MerchantService.suspendMerchant`/
    `reactivateMerchant`, `MerchantModerationAdminController`*

### Unresolved / worth a follow-up

- Baemin's/Coupang Eats' own official design-system docs (story.baemin.com, bcut.baemin.com)
  returned only search snippets, not full content — a direct fetch would sharpen visual/spacing
  specifics.
- No pixel-level card layout verified against a live, dated screenshot.
- Baemin's B마트 and either app's algorithmic "today's picks" ranking weren't researched — itunda
  has no comparable surface yet.
- **Re-checked 2026-08-04**: the delivery/pickup cart toggle this bullet used to also flag as
  unverified is real and already shipped (`EatsCheckoutMode.DELIVERY/PICKUP/DINE_IN`, checkout
  toggle). Coupang Eats' 좋아요/싫어요 **per-menu-item** rating (distinct from the already-real
  restaurant/rider review system) is not confirmed real — a fresh, more targeted search plus a
  direct namu.wiki fetch found no mention of a per-item like/dislike feature at all; the earlier
  search snippet backing this claim more likely described rider ratings, which itunda already has.
  Not built. Don't re-add this to the implemented list without a primary source (an actual screen
  showing per-dish 좋아요/싫어요 buttons), not another secondary summary.

---

## 3. Talk (real-time chat)

### References

| Product | Source | Pattern |
|---|---|---|
| KakaoTalk — per-message read receipt | waegukin.com, anotherlittleworldinmymind.blogspot.com, cross-checked vs. namu.wiki | Countdown number next to each sent bubble = recipients who haven't read it yet, disappears at 0; "1" in a 1:1 chat |
| KakaoTalk — bubble/read-state critique | brunch.co.kr/@ultra0034/32 | Read state via disappearing number, not a checkmark; article separately proposes (unshipped) fade-on-read |
| KakaoTalk — 2025 reply/thread redesign | facebook.com/hipinkorea, eyesmag.com, inven.co.kr, v.daum.net | Long-press → Copy/Select-copy/Reply, expanding into "start thread"/"thread reply"; forward to up to 10 destinations (individual or new group); pin/delete/vote/@mention listed as the message toolkit |
| KakaoTalk — Friends tab restoration (Dec 2025, v25.11.0) | designcompass.org, koreapost.com, en.sedaily.com | Friends tab is structurally separate from Chats and is the app's default screen; a Sept 2025 feed-first replacement caused a rating collapse and was reverted within 3 months; restored version adds a Friends/Updates toggle, chat folders, unread-conversations summary |
| KakaoTalk — Chat Room Drawer | kakaocorp.com/page/detail/8643, cs.kakao.com | Per-thread aggregated gallery of every photo/video/file/link in that one room, distinct from the account-wide Talk Cloud backup |
| KakaoTalk vs LINE UI/UX comparison | ditoday.com | Swipe right = favorite/notify/pin, swipe left = read/leave; "+" opens a multi-function attach menu (Album flow called out as friction) |
| KakaoTalk — 조용한 채팅방 (Quiet chat room) | kakaocorp.com/page/detail/10583 | Archive + auto-mute without leaving, distinct from muting notifications only or leaving; also a "leave silently" feature |
| KakaoTalk — typing indicator (v25.4.0, May 2025) | designcompass.org | Animated yellow dots inline, opt-in via Lab, requires both participants to enable |
| Kakao emoticon store | inquivix.com, bowloftech.substack.com | Emoticon Studio lets creators publish/sell stickers into the attach menu, 50:50 split; emoticons were ~1/3 of Kakao's 2020 revenue (~$340M) |

### Recommendations (ranked)

1. **[implemented]** Talk now has a privacy-preserving saved-contacts directory across web, Android, and iOS. It resolves only the caller's own saved contacts, never a public user search.

2. **[implemented]** Direct messages now show a Kakao-style pending-read `1` and message timestamps across all clients. Group receipts now have a true per-member model too (2026-07-26): a live per-message unread countdown built on the existing per-member `lastReadAt` cursor. **UI closed on all 3 platforms 2026-08-05** (bank-mfe already had it; found via direct code read that Android/iOS's `GroupMessageDto` never modeled the field despite the real backend always returning it). Added `unreadCount` to both DTOs and a `"N · "` badge next to own group messages, matching the existing 1:1 `"1 · "` pattern exactly. Live-verified against the real dev backend with two real seeded accounts: countdown showed `1` before the second member opened the thread, `0` immediately after (opening a group thread implicitly marks it read server-side, same as `getMessages` already did for 1:1). **Real fix found while wiring this in on iOS**: `TalkScreen.swift`'s reaction-toggle and real-time reaction-push handlers rebuilt `MessageDto`/`GroupMessageDto` via the memberwise initializer, which always hardcodes `imageUrl`/`forwardedFromMessageId`/`forwardedFromType` (and would have hardcoded the new `unreadCount`) back to nil/0 — a photo or forward label would flicker away for a few seconds after any reaction change, until the next 4s poll refresh. Added a `withReactions(_:)` method to both structs that preserves every field, fixed all 4 call sites.

3. **[sourced] Forward+Pin closed on Android 2026-08-04, on iOS 2026-08-05; Thread remains open.**
   Add a long-press message menu: reply/thread, forward, pin, delete. Kakao's confirmed 2025
   toolkit is Copy/Reply (→ thread)/Forward (≤10 destinations)/Pin/Delete/@mention. Real finding on
   inspection: itunda's Reply/Pin/Delete/Report already existed (backend-complete) but as
   permanently-visible `TextButton`s under every bubble, not a long-press menu -- a real
   UX-pattern mismatch, not a missing-capability one. Both `MessageBubble`/`GroupMessageBubble` now
   use `combinedClickable(onLongClick)` + a real `DropdownMenu`. Copy is new (real
   `LocalClipboardManager`). **Correction, same day**: this entry previously claimed Forward
   "needs new backend concepts itunda doesn't have" -- that was an unverified assumption, not a
   checked fact. Direct inspection found a complete, real `MessageForwardService` (2026-07-25)
   already supporting forwarding any 1:1/group message to any 1:1/group destination with real
   read-authorization and genuine server-stamped provenance -- zero Retrofit method or UI anywhere
   on Android at the time. Closed: `ForwardDestinationDialog` (multi-select, capped at 10 real
   destinations), a real "↪ Forwarded" label. Live-verified end-to-end in both directions against
   the real dev backend. **iOS closed 2026-08-05**: `NetworkClient.swift` had zero group-pin
   functions and zero forward functions of any kind despite both backend contracts already being
   real. Added `getPinnedGroupMessage`/`pinGroupMessage`/`unpinGroupMessage` (exact mirror of the
   already-real 1:1 pin trio) plus `forwardDirectMessage`/`forwardGroupMessage` and a
   `ForwardPickerView` sheet (single-destination picker over real conversations+groups --
   deliberately scoped narrower than Android's up-to-10 multi-select, matching bank-mfe's own
   simpler single-destination convention). `MessageBubble`/`GroupMessageBubble` gained
   Forward buttons and a "↪ Forwarded" label from the now-modeled `forwardedFromMessageId`/
   `forwardedFromType` fields; `GroupThreadScreen` gained the same pinned-message banner
   `ChatThreadScreen` already had. Live-verified against the real dev backend: pinned a real group
   message (`GET` reflected it), unpinned (`GET` returned null), forwarded a group message into a
   1:1 conversation and a 1:1 message into a group, both directions returning a real
   `forwardedFromMessageId`/`forwardedFromType` on the created message. **Thread closed on backend +
   bank-mfe 2026-08-05**, real correction to the note below: `replyToMessageId` already existed on
   both `Message`/`GroupMessage` (real Reply already worked), so this needed no new migration at
   all -- only a real way to VIEW replies as a sub-conversation rather than an inline "replying to"
   tag. Added `MessageRepository`/`GroupMessageRepository.findByReplyToMessageIdAndDeletedAtIsNullOrderBySentAtAsc`
   plus a batch `countRepliesByMessageIds` projection (same discipline `getReactionSummaries` already
   established: one query per page, not one COUNT per message), a real
   `GET .../messages/{id}/thread` endpoint on both `MessagingController`/`GroupMessagingController`
   returning the root message + every direct reply oldest-first, and a `replyCount` field on every
   message in the main timeline. `:messaging:test` and the full `./gradlew test` sweep across all
   40+ backend modules both green. **bank-mfe UI wired the same day**: a message with
   `replyCount > 0` gets a real "N replies →" affordance opening a new `ThreadModal` (generic over
   `Message`/`GroupMessage`, reused for both 1:1 and group) -- a real sub-conversation view with its
   own composer that replies straight into the thread via the same `replyToMessageId` mechanism.
   Live-verified end-to-end against the real dev backend with two real seeded accounts: a root
   message correctly showed `replyCount: 2` after two real replies, the thread endpoint returned
   root+both replies in the correct oldest-first order, and a real non-participant's thread fetch
   correctly 404'd (not revealing the conversation exists) rather than leaking anything. Test data
   cleaned up after. **Android client added the same day**: `ApiService.kt` gained `replyCount` on
   both `MessageDto`/`GroupMessageDto` and `getThread`/`getGroupThread`; `TalkScreen.kt` gained a
   real "N replies →" affordance on `MessageBubble`/`GroupMessageBubble` (same visual convention
   bank-mfe established) opening a new `RepliesThreadView`/`GroupRepliesThreadView` -- named
   "Replies" rather than reusing "Thread" specifically to avoid colliding with this file's own
   pre-existing `ChatThreadView`/`GroupThreadView` naming (the whole conversation screen, a
   different real concept). A full clean `:app:compileDebugKotlin` (434/434 tasks re-executed)
   compiled zero-error. **Verification caveat, disclosed honestly**: on-device visual confirmation
   on the review emulator was attempted but blocked by a genuine emulator System UI hang (a
   resource-exhaustion issue after several hours of continuous uptime in the same session, not
   caused by this change -- the underlying app screen rendered correctly with real data behind the
   stuck system dialog) -- this pass relied on the full clean compile plus the already
   curl-verified backend contract, not a live screenshot, unlike this session's usual bar for
   Android UI changes. **iOS client added the same day, closing this recommendation's client-UI
   thread on all 3 platforms + backend.** `NetworkClient.swift` gained `replyCount` on both
   `MessageDto`/`GroupMessageDto` and `getThread`/`getGroupThread`. **Real bug found live while
   wiring this in, fixed the same day**: `MessageDto.replyToMessageId` was declared as a stored
   property but never actually decoded -- the custom `init(from:)`/`CodingKeys` never mentioned it,
   so it silently read back `nil` on every real 1:1 message regardless of what the backend sent
   (unlike Android's own `MessageDto`, which decoded it correctly, and unlike iOS's own
   `GroupMessageDto`, which also decoded it correctly -- a real, narrow 1:1-only regression, not a
   platform-wide one). `TalkScreen.swift` gained a real "N replies →" affordance on
   `MessageBubble`/`GroupMessageBubble` opening a new `RepliesThreadView`/`GroupRepliesThreadView`
   `.sheet(item:)`, matching the naming discipline the Android port already established (avoiding
   this file's own pre-existing `ChatThreadScreen`/`GroupThreadScreen` naming). Verified via
   `swiftc -parse` across both changed files (syntax-only, no type-checking) -- a full
   `xcodebuild` was attempted but blocked by a pre-existing, unrelated environment issue (missing
   CocoaPods for the React Native brownfield modules, present before this change and not something
   this pass caused or attempted to fix), so this iOS change carries the same lower-confidence,
   syntax-verified-only caveat this session's other iOS work already discloses.

4. **[implemented 2026-08-05]** Talk has private, durable quiet-room controls and
   suppresses notifications for the participant who enables one. Recoverable
   archive/list placement and conversation-list actions are now real too: backend
   `ConversationPreference.archived` (same private-to-one-participant model as
   `quiet`), `GET /conversations?archived=true|false` filtered at the DB level (not
   post-hoc, so pagination stays correct), `POST/GET .../conversations/{id}/archive`.
   **Real bug found and fixed on all 3 platforms while wiring this in**: each
   client's "Archived (N)" toggle was actually filtering on `quiet` (mute) and
   mislabeling the result -- muting had been repurposed to hide a conversation from
   the list since no real archive concept existed yet. Muted conversations now stay
   visible in the main list on all 3 platforms (matching real KakaoTalk: muting only
   silences notifications, never hides a room); the toggle now shows the real
   archived list. Per-row action scoped per-platform idiom rather than forcing
   identical UI: Android uses a real `SwipeToDismissBox` swipe gesture (live-verified
   against the real dev backend via curl; the swipe *gesture* itself could not be
   triggered through this environment's adb automation, only the underlying API
   contract), iOS uses `.swipeActions` inside a real `List` (a hard SwiftUI
   constraint -- `.swipeActions` silently no-ops outside a `List`, so
   `DirectMessagesList` moved off ScrollView+VStack for this), and bank-mfe uses an
   always-visible icon button matching its own established Pin/Delete/Forward
   convention (no real touch-swipe convention on desktop web to match against). iOS
   and bank-mfe changes are syntax/build-verified only (no simulator or headless
   browser in this environment) -- not screenshot-verified like Android's.

5. **[sourced] Implemented 2026-08-04 (honest partial scope).** Add a per-thread shared-media
   gallery (Chat Room Drawer). itunda had zero aggregation of media shared in a conversation.
   Scoped to photos only -- no file-attachment type or link-preview system exists to aggregate, so
   a "files/links" tab wasn't built as a fabricated empty one. `MediaGalleryView` filters each
   thread's already-loaded message list to real `imageUrl` entries client-side, no new backend
   endpoint needed.

6. **[sourced] Implemented 2026-08-04.** Add a real attach ("+") menu to the composer. Both thread
   composers were just a text field + send button (plus separate always-visible gift/emoticon/
   voucher icons on `ChatThreadView`) — no photo, file, or sticker send at all. Kakao's emoticon
   picker alone is core, monetized product surface (~1/3 of 2020 revenue), not a nice-to-have.
   **Real finding on inspection**: both backend services already validated/persisted a real
   `imageUrl` (enforced to be a genuine `/api/v1/uploads/` file) -- neither Android request DTO
   carried the field, neither response DTO read it back, no UI could pick or send one. Reuses the
   exact real upload flow `MarketplaceScreen`/`PropertyScreen` already established. Consolidates
   `ChatThreadView`'s 3 separate always-visible icons (🎁/😊/🎟️) plus the new 📷 into one real
   Kakao-style "+" menu; `GroupThreadView`'s single 😊 button gets the same treatment. Both bubbles
   now render `imageUrl` via `AsyncImage`. Live-verified end-to-end for both 1:1 and group threads
   against the real dev backend (upload -> send -> real GET reflects it -> real static fetch
   returns 200), test messages cleaned up afterward.

7. **[implemented]** @mention support in group chats is real end-to-end, both backend and UI.
   `GroupMessagingService.parseMentions` (2026-07-25): `@FirstName` tokens resolve against real
   group members and trigger a distinctly-titled `GROUP_MENTION` notification. Group-chat pin is
   also real (2026-07-26), matching 1:1's own pin -- UI closed 2026-08-04 alongside item 3's
   long-press menu (`getPinnedGroupMessage`/`pinGroupMessage`/`unpinGroupMessage` + a pinned-message
   banner in `GroupThreadView`, live-verified pin -> GET reflects it -> unpin -> GET returns null).
   **@mention's own composer UI closed 2026-08-04**: typing "@" with no following space now shows a
   real inline suggestion row of matching group members (client-side, from the already-fetched
   member list -- no new endpoint, since `parseMentions` only ever reads the message body text);
   tapping one inserts `@FirstName` matching the backend's own first-token resolution exactly.
   Live-verified against the real dev backend: a real 3-member group, a real message
   ("Hey @Ladder are you around?"), the response's `mentionedUserIds` resolving to exactly the
   right member's real user id.

8. **[implemented]** Per-message timestamps now appear in direct and group threads across all clients.

9. **[sourced] Implemented 2026-08-06.** Give Talk a real Friends directory, not just a Direct/Groups
   chat-*history* toggle -- KakaoTalk's Friends tab is so structurally central that Kakao's own Sept
   2025 attempt to bury it caused a rating collapse, reverted within 3 months. Found on a research
   pass mining this doc's own remaining open items: the backend infra (`GET /messages/contacts`
   cross-references saved contacts against real registered itunda users by phone number, `GET
   /messages/presence` for real online status) was already fully real and already used inline in
   the New-chat/add-group-member composers on all 3 platforms -- a client-only addition, no new
   endpoint. New `FriendsList`/`FriendsView` component: a real browsable contact list with live
   "Active now" presence dots, tapping a friend starts/opens their real 1:1 conversation via the
   same `startConversationWithUser` the New-chat composer already used. Wired in as a third
   Direct/Groups/Friends option on all 3 platforms.
   *Shipped: `MessagesView`'s `FriendsList` (bank-mfe), `TalkTab`'s `FriendsView` (Android),
   `TalkScreen`'s `FriendsList` (iOS)*

10. **[sourced] Already implemented -- doc was stale.** The old recommendation here asked for "a
    per-message read-receipt countdown," reasoning that itunda only tracked `unreadCount` at the
    conversation-list level with no per-message read signal inside an open thread. Re-checked
    2026-08-06 while researching Talk's remaining gaps: `Message.readAt` is a real per-message
    field on the backend, and all 3 clients already show Kakao's own "1" indicator next to a sent
    message (`isMine && message.readAt == null`), removed the instant the recipient reads it --
    this is exactly the real per-message read-receipt the old entry asked for. No action needed;
    this entry just hadn't been updated when the feature shipped.

<!-- Historical pre-implementation audit retained below for source provenance.
3. **[sourced] Implemented on Android, 2026-08-04; iOS capabilities closed 2026-08-05; bank-mfe
   partial.** Long-press message menu: reply/forward/pin/delete, plus Copy for plain-text messages
   (`GroupMessageBubble`/`MessageBubble` in `TalkScreen.kt`, `combinedClickable(onLongClick = ...)`
   opening a real `DropdownMenu`) -- closes Kakao's confirmed Copy/Reply/Forward/Pin/Delete toolkit
   (mention closed separately, #7 below) on Android. **bank-mfe already has the full real
   Reply/Copy/Forward/Delete/Pin set on group threads (always-visible buttons, confirmed by direct
   code read 2026-08-05) -- only the long-press-menu *presentation* is the remaining gap there,
   not any missing capability.** **iOS's Forward (both thread types) and group Pin were genuinely
   missing capabilities as of 2026-08-05's morning read** (`forwardedFromMessageId` wasn't modeled
   in `TalkScreen.swift` at all) **-- closed same day**: real `forwardDirectMessage`/
   `forwardGroupMessage`/`getPinnedGroupMessage`/`pinGroupMessage`/`unpinGroupMessage` in
   `NetworkClient.swift`, a `ForwardPickerView` destination-picker sheet, Forward buttons + a
   "↪ Forwarded" label on both bubble types, and a pinned-message banner on `GroupThreadScreen`.
   Presentation (always-visible buttons vs. a long-press menu) is now iOS's only remaining gap
   here too, matching bank-mfe's own remaining gap -- not a capability gap on either platform.

4. **[sourced] Implemented on all 3 platforms, 2026-08-05.** Add swipe actions and
   per-chat mute/archive ("quiet chat room") to the conversation list.
   `ConversationRow`/`GroupRow` used to support only tap-to-open. Real KakaoTalk
   supports right-swipe (favorite/notify/pin) and left-swipe (read/leave), plus an
   official archive-without-leaving feature -- scoped honestly to the archive half
   (itunda's Talk has no per-conversation "favorite" concept to wire a second swipe
   direction to). See recommendation #4 above (live list) for the full account,
   including a real quiet/archived conflation bug found identically on all 3
   platforms and fixed the same way on each.

5. **[sourced] Implemented on all 3 platforms, 2026-08-04/05.** Per-thread shared-media gallery
   (Chat Room Drawer) -- `MediaGalleryView`/`MediaGalleryModal`, built entirely client-side from
   the conversation's own already-loaded messages (filtered to real `imageUrl != null` entries), no
   new backend endpoint. Scoped honestly to photos only (no file-attachment type or link-preview
   system exists to back a real "files/links" tab). Android shipped first; iOS
   (`TalkScreen.swift`) and bank-mfe (`BankDashboard.tsx`) ported the identical client-side-filter
   approach the next turn, reusing each platform's own pre-existing photo-upload primitive
   (iOS `uploadPhoto`/`ImagePickerView`, bank-mfe `uploadFile`, both real since 2026-08-01).

6. **[sourced] Implemented on all 3 platforms, 2026-08-04/05.** Real attach ("+") menu on the
   composer (📷 Photo / 😊 Emoticon, plus each platform's own pre-existing Gift/Gift-voucher
   options folded in on 1:1 threads), plus real photo-message send/display. Closes this row
   everywhere in the same pass as #5 above.

7. **[sourced] Implemented on all 3 platforms, 2026-08-04/05.** @mention support in group chats:
   typing `@` shows a row of real group members' first names as tappable chips, inserting
   `@FirstName ` on tap -- `GroupMessagingService.parseMentions` (backend) already resolved these
   tokens purely from message-body text since 2026-07-25, so every client needed was a composer
   addition, no new endpoint. Android shipped first (`activeMentionQuery`/`applyMention`/
   `MentionSuggestions` in `TalkScreen.kt`); iOS (`TalkScreen.swift`) and bank-mfe
   (`BankDashboard.tsx`) ported the identical logic the next turn, closing this row on every
   platform.

8. **[partially-sourced] Implemented on all 3 platforms, including the collapsed-per-run
   convention (item 243, 2026-08-07).** Per-message timestamps (`chatMessageTime`) exist in both
   bubble types on Android, iOS, and bank-mfe -- found already real while auditing this row
   2026-08-05; this entry had gone stale, not the code. The exact collapsed-per-run convention
   originally proposed here (each message shows its own timestamp, not grouped by
   consecutive-run) was the one real, honest, remaining polish gap left after that audit --
   closed same day as items 240-242 (this session's WCAG/a11y pass): a new
   `shouldShowChatTimestamp` helper on each platform (`HoodShared.kt`, `TalkScreen.swift`,
   `BankDashboard.tsx`) shows a message's timestamp only when it's the last in a consecutive run
   from the same sender within the same local minute (compared by full date+minute, not just
   clock face, to avoid false-collapsing same-clock-time-different-day messages). Deliberately
   excluded from collapsing in the search-results list on all 3 platforms, since adjacent search
   hits aren't temporally adjacent in the real conversation. Unread markers ("1 · " / unreadCount)
   render independently of the timestamp collapse, so they never disappear on a message that
   isn't its run's last. Verified: Android `:app:compileDebugKotlin` clean, iOS `swiftc -parse`
   clean (syntax-only, same caveat as this session's other iOS work), bank-mfe `tsc -b` and
   `vite build` both clean. No remaining gap in this row.

9. **[inferred, low priority]** Consider Kakao's newer inline-dots typing indicator. itunda's
   existing text-line typing indicator ("X is typing…", 3s auto-clear) already covers the core
   need — this is visual polish, not a missing capability. -->

### Unresolved / worth a follow-up

- namu.wiki returned HTTP 403 for this session; feature/redesign pages were only triangulated via
  search snippets and press coverage.
- No official public KakaoTalk *consumer* design-system site exists (Kakao's public design guide
  covers KakaoSync/business integrations, not chat UI) — all consumer-UI claims here are
  press/corporate-blog/independent-teardown sourced.
- iOS `TalkScreen.swift` and bank-mfe's `ConversationThread` were grep-confirmed to share the same
  `MessageBubble`/`unreadCount` shape as Android, but not fully read through — worth doing before
  implementation given this project's history of the three clients drifting.
- Exact date-divider visual spec (pill shape, format, sticky behavior) wasn't sourced to a
  specific citation — flagged as general convention, not Kakao-specific.
- KakaoTalk's Open Chat (오픈채팅) wasn't researched — may be more relevant to Hood's community
  features than Talk proper; worth a separate pass.

---

## 4. Hood (Market / Life / Jobs / Home)

### References

| Product | Source | Pattern |
|---|---|---|
| Karrot — Manner Temperature → Karrot Score localization | redbusbagman.com | Domestic: 매너온도, starts at 36.5°C, warmth metaphor. Karrot's own UK/Canada research found the temperature metaphor confusing and low scores read as insulting to non-Korean users; global replacement is a plain 0–1000 "Karrot Score" starting at 30, reframed around "trustworthy" language. Lesson: localizing a trust signal means redesigning the metric, not translating the label |
| Seed Design (Karrot's official open-source design system) | seed-design.io, github.com/daangn/seed-design | 44 official components incl. Manner Temp & Badge (10 discrete levels l1–l10, pill variant for cards), Bottom Sheet (documented anatomy, 90/50/10% snap points, mandatory drag handle, switch to full page past 90% height), Scroll Fog (persistent edge-fade, 15–20% depth, always rendered to avoid flicker), Content Placeholder/Skeleton/Identity Placeholder, Tag Group, Menu Sheet, Reaction Button |
| Karrot — 동네인증 (neighborhood verification) | cs.kr.karrotmarket.com (official FAQs 44, 1), corroborated by nuthang.com | GPS verification of up to 2 neighborhoods (home + work); user-adjustable radius (8–63 nearby areas per secondary source); verification frequency shown as a trust signal |
| Karrot — 동네생활 board structure | brunch.co.kr/@zezezeze/79 | Question posts get distinct 궁금해요/답변하기 CTA vs. ordinary 공감하기/댓글; feed is strictly chronological (no popularity ranking, to preserve voice diversity); 같이해요 (join-together) posts get a dedicated pinned mid-feed slot; joining their group chat requires an explicit 참여하기 tap |
| Naver Cafe — 안전거래 (Safe Trade) escrow | navercorp.com press release (Feb/Sept 2025) | Real escrow + Naver Certificate ID-verification + fraud-detection system bolted onto informal P2P trades happening inside community posts/comments -- trust infrastructure for commerce that was never a dedicated marketplace to begin with, built specifically to stop fraud in that informal setting |
| Karrot — review & wishlist UX | ditoday.com | Post-transaction review is a preset checklist, not free text; "good points" shown publicly, "uncomfortable points" kept private between the two parties (deliberate asymmetric visibility to avoid public-negative-review churn); un-hearting a wishlist item is toast-confirmed, not silent |
| Karrot — general IA | mobiinside.co.kr | Transaction history split into 판매내역/구매내역/관심목록 (sales/purchases/wishlist) tabs; search radius as a slider, not checkboxes |
| 당근알바 (Karrot Jobs) — official product page | daangn.com/kr/jobs/about | Three-step flow: 알바공고 작성하기 (post) → 지원자 확인하기 (review applicants) → 채팅으로 약속 잡기 (arrange via chat, only *after* reviewing applicants, not before); proximity-first framing ("걸어서 10분 거리" — within a 10-minute walk); phone number never exposed, all contact routes through in-app chat |
| 당근부동산 (Karrot Real Estate) — official product page + press coverage | realty.daangn.com/about, digitaltoday.co.kr, about.daangn.com press archive | **Mandatory** per-listing ownership verification (made mandatory specifically to stop fraud, per their own 2025 press release): owner-posted listings are cross-checked against 등기부등본 (title deed registry) and labeled "집주인 확인 매물" (owner-confirmed listing); non-owner posters (e.g. tenants) must submit a lease agreement or get the owner's confirmation instead; separate realtor/agent accounts can only post brokered listings, never mixed with owner-direct ones |
| 토스뱅크 (Toss Bank) — official product pages | tossbank.com/product-service/savings/{space-account, moim-account}, press coverage (alphabiz.co.kr, heraldcorp.com) | 나눠모으기 통장: multiple named, color-coded sub-purposes within one real account, daily compound interest, no manual "claim" step; 모임통장: consent-based co-ownership (any withdrawal/card-issuance right requires existing co-owners' approval), shared 모임카드; **먼저 이자받는 정기예금** (2025): a 12-month locked deposit that pays the *entire year's* interest immediately at signup (2.80% pre-tax) instead of at maturity — principal stays locked, tax is withheld at closing, not at the interest payout |
| 배달의민족 owner ad products (오픈리스트/울트라콜) + Coupang seller Ads | self.baemin.com, ads.coupang.com, aijeju.co.kr fee breakdown | Baemin runs 4 distinct real ad formats: 울트라콜 (flat monthly fee per real listing slot), 오픈리스트 (% of order value), 우리가게클릭 (CPC), 배민1 (delivery commission) — a real menu of self-serve formats, not one "boost" button; Coupang's own Ads platform is self-serve PPC with AI auto-bidding. Two unrelated companies independently monetize marketplace visibility as a real, direct seller-paid product |

### Recommendations (ranked)

1. **[sourced] Implemented — doc was stale, corrected 2026-08-04.** Karrot-Score-style numeric
   trust badge, plain `0–1000` number (not a Celsius/manner-temperature metaphor — see this doc's
   own reasoning for why that's specifically wrong for a non-Korean market). Shipped as
   `TrustBadge` in `core/designsystem/components/HoodShared.kt`, real-called from
   Marketplace/Jobs/Property listing cards (verified via a repo-wide call-site check, not just
   "the component exists").

2. **[sourced] Implemented — doc was stale, corrected 2026-08-04.** Post-transaction review flow
   with Karrot's real asymmetric public/private split. Shipped as `HoodReviewForm`/
   `HoodReviewResultView` in the same shared file — a preset good-points/uncomfortable-points
   checklist, good points public (feed the trust score above), uncomfortable points private
   between the two real parties. Also real-called from all three Hood verticals.

3. **[implemented, correction 2026-07-26]** This was miscategorized as still-open — checking the
   actual backend before starting a fresh build (this session's own discipline) found
   `ListingFavoriteService`/`JobPostFavoriteService`/`PropertyListingFavoriteService` already
   real and controller-wired for all three (migrations `V56`/`V74`/`V75`), each mirroring
   `ProductFavoriteService`'s exact add/remove/list shape. Left un-tagged here since this file
   wasn't updated when those shipped. Client UI (toast-confirmed add/remove, a Saved `HoodView`)
   still worth checking separately.

4. **[sourced] Implemented — doc was stale, corrected 2026-08-04.** 같이해요 (join-together) posts
   get a pinned mid-feed slot, sorted by real `eventDate`, with an explicit 참여하기 join tap and a
   real live member count off that meetup's own group chat (`CommunityScreen.kt`, 2026-07-24).

5. **[sourced] Implemented 2026-08-04.** Shared `ScrollFog` composable in
   `core/designsystem/components/HoodShared.kt` (a persistent bottom edge-fade gradient, matching
   Seed Design's documented always-rendered spec), wired into all four Hood modules' main browse
   feeds — Marketplace, Community, Jobs, and Property — each verified individually (exact
   brace-matched span located per file via script before wrapping, not eyeballed) and each
   module compiled clean on its own before the full-app build was re-verified.

6. **[sourced] Implemented — doc was stale, corrected 2026-08-04.** `HoodView` already splits a
   user's own activity into distinct `MINE`/`PURCHASES`/`WISHLIST` tabs (plus `ALERTS`), not one
   flat list (`MarketplaceScreen.kt`'s own `HoodView` enum).

7. **[partially-sourced] Closed -- doc was stale, corrected 2026-08-05.** The one remaining bare
   `Text("Loading…")` this entry named (`MarketplaceScreen.kt`'s boost-options dialog) is gone --
   a repo-wide grep for "Loading" (any casing/spacing) across `MarketplaceScreen.kt` found zero
   matches. Every loading state in the file now uses something else, matching the rest of the
   file's `SkeletonBlock` convention.

8. **[sourced] Dual-neighborhood implemented 2026-08-04; radius control deliberately not built.**
   `User.secondNeighborhood` (migration V226), `AuthService.setSecondNeighborhood`/
   `clearSecondNeighborhood`, `POST`/`DELETE /api/v1/auth/profile/second-neighborhood` -- same
   real reverse-geocode-only provenance as the primary neighborhood. All four Hood browse
   services (Marketplace/Community/Jobs/Property) now match either neighborhood via a real
   `findByStatusAndNeighborhoodIn...` query, not just the primary. Android UI: the neighborhood
   switcher shows both names (`primary · second`) and an Add/Change/Remove row for the second one,
   reusing `NeighborhoodSetupPrompt(isSecond = true)`. Live-verified end-to-end against the real
   dev backend (set primary -> set second via real coordinates -> profile reflects both -> clear
   -> profile reflects null again). **Radius control intentionally not built**: this
   recommendation's own specific figures (8-63-area radius scaling) were already flagged
   `partially-sourced`/secondary-only in the original entry -- building a fabricated radius tier
   system off an admittedly-illustrative source would be inventing a number, not implementing a
   researched one. **iOS and bank-mfe ported 2026-08-04**, closing this gap on all three
   platforms: iOS's `NeighborhoodSetupPrompt` gained the same `isSecond` parameter and copy,
   `HoodScreen`'s neighborhood line is now tappable into a `NeighborhoodSwitcherOverlay` mirroring
   Android's dialog; bank-mfe's `NeighborhoodSetupPrompt` gained the same `isSecond` prop and a new
   `NeighborhoodSwitcherRow` (Add/Change/Remove) wired into all four Hood-tab modules
   (Marketplace/Community/Jobs/Property), each of which manages its own local neighborhood state
   independently rather than through one shared header component (a bank-mfe/iOS-vs-Android
   architecture difference, not a scope gap).

9. **[sourced] Implemented 2026-07-25.** Add per-listing ownership verification to Property —
   before this, `PropertyListingService.createListing` had zero ownership proof of any kind,
   unlike 당근부동산's real product, which made registry-document verification *mandatory*
   specifically to stop listing fraud. Rwanda has no publicly documented land-registry number
   format to structurally pre-validate the way `DemoNidaVerificationService` does for National
   IDs, so this is honestly scoped as document-upload + human-review only, mirroring
   `KycSubmission`'s real submit/queue/decide shape — no fabricated auto-check against a
   registry this backend has no real access to.
   *Shipped: `PropertyOwnershipSubmission`, `PropertyOwnershipService`, `PropertyListing.ownershipVerificationStatus`, `POST /api/v1/realestate/listings/{id}/verify-ownership`, `/api/v1/system/property-verification/**` (admin queue+decide)*

10. **[sourced] Implemented — doc was stale, corrected 2026-08-04.** 당근알바's real
    post → review applicants → chat structure. `JobApplication` (résumé snapshot at submit time,
    matching Karrot's real mechanic), `JobApplicationService`, `GET /posts/{id}/applications`
    (poster reviews), `GET /my-applications`, `POST /applications/{id}/respond` all real and
    Android-wired (`MyJobApplicationsView` in `JobsScreen.kt`) — applying is additive alongside
    "Message poster", not a replacement.

11. **[sourced] Implemented 2026-07-25.** Add seller-paid sponsored placement to Marketplace --
    independently converged on by Coupang's real self-serve seller Ads product and Baemin's real
    오픈리스트/울트라콜 flat-fee listing slots, itunda's Marketplace had zero monetization of any
    kind. Chose Baemin's flat-fee-per-real-time-slot mechanic (울트라콜) over Coupang's per-click
    auction -- the simpler, more honestly-buildable of the two real sourced models at this
    marketplace's real scale. A real payment (seller wallet debited, 100% to itunda's
    `fee_revenue` -- a direct service purchase, not a marketplace transaction between two
    parties) extends `Listing.boostedUntil`; `browse()` ranks a currently-boosted listing first
    and the client shows a real "Sponsored" badge only when boostedUntil is genuinely still
    future, never fabricated on an unpaid listing.
    *Shipped: `Listing.boostedUntil`, `MarketplaceService.boostListing` (3/7/14-day real flat-fee
    tiers), `POST /api/v1/marketplace/listings/{id}/boost`, Marketplace's "Boost" action + Sponsored badge*

12. **[sourced] Implemented 2026-07-25.** Add structured date/capacity fields to Community's real
    같이해요 (join-together) meetup post type. Karrot's own real product spun 모임 out of the
    freeform 같이해요 post type specifically because it added mandatory date-setting and a real
    capacity cap on top -- itunda's existing `joinMeetup` was unlimited-join with no event date at
    all. `eventDate` is now required (and must be a real future instant) for the meetup category
    only; `capacity` is optional (null stays unlimited, the pre-existing behavior for every meetup
    created before this field existed) and is enforced at join time with a real, honest
    first-come-first-served cap -- no unsourced waitlist-with-promotion mechanic invented on top.
    *Shipped: `CommunityPost.eventDate`/`capacity`, `CommunityService.joinMeetup`'s real capacity
    check, `GET /api/v1/community/meetups/upcoming` (soonest-first), Android's date/time/capacity
    fields on meetup creation + soonest-first pinned meetup ordering*

13. **[sourced] Implemented 2026-07-25.** Add real "pay via itunda" Marketplace escrow, closing a
    real trust gap Naver Cafe's own "안전거래" (Safe Trade) product exists specifically to solve.
    itunda's Marketplace has always settled buyer/seller in person, off-platform (same real
    당근마켓 model), with zero protection against a no-show or a not-as-described item -- this ships
    as a purely opt-in alternative alongside that existing cash handoff, never replacing it. A
    buyer's payment sits in a real `marketplace_escrow_holding` clearing account (same shape
    `EATS_DELIVERY_HOLDING`/`GIFT_HOLDING` already establish) until they confirm receipt, at which
    point the seller is paid minus a real 1.5% escrow fee (matching `OrderService.feeRate`); a
    disputed trade is resolved by a real human admin (release or full refund, no fee on a refund,
    and the listing reopens for sale), the same real human-review-queue precedent
    `PropertyOwnershipService` already established -- deliberately not an automated resolution,
    since this backend has no real fraud-detection system to drive one safely.
    *Shipped: `MarketplaceEscrow`, `MarketplaceService.payEscrow`/`confirmReceipt`/`disputeEscrow`,
    `MarketplaceEscrowAdminController` (`/api/v1/system/marketplace-escrow`), Marketplace's "🔒 Pay
    via itunda" + Confirm receipt/Report a problem actions*

### Unresolved / worth a follow-up

- Could not confirm whether Karrot's live product actually gates category/filter selection behind
  a Seed Design Bottom Sheet vs. an inline chip row — deliberately left out of the sourced
  recommendations.
- Seed Design's Tag Group / Menu Sheet / Reaction Button / Result Section / Contextual Floating
  Button pages weren't fetched beyond their one-line index listing — worth a follow-up pull of
  full specs before implementing.
- 동네인증 radius-scaling numbers (8–63 areas) are secondary-sourced only; the mechanic itself
  (dual neighborhoods, adjustable radius, verification-count trust signal) is corroborated by
  Karrot's own CS FAQ.
- No sourced detail found on Karrot's masked-calling/"safe number" equivalent or exactly how
  price-offer chips render in-chat — no recommendation was made here beyond the 같이해요 join-gate.
- Everything in this section was sourced via text-based fetch/search, not visual inspection of
  live screenshots or Seed Design's Figma file — worth a follow-up visual pass.

---

## 5. Shopping (merchant e-commerce browse/catalog/cart)

### References

| Product | Source | Pattern |
|---|---|---|
| Coupang app redesign case study (Chloe Youn) | chloeyoun.squarespace.com/work/coupang-design-renewal | Cards support add-to-cart AND wishlist directly, no detail-page visit needed; detail page uses an inline scrollable variant+quantity selector; reviews restructured into scannable titled/keyword summaries; filters cut from 20+ to a small essential set plus a graphical price-range slider; notification/preference toggles surfaced on the home surface, grouped by type |
| Coupang Rocket Delivery badge | multiple independent secondary descriptions (Coupang Rocket Growth seller docs, aggregator docs) | Distinct blue rocket badge directly on the product card/thumbnail (not buried in the detail page); separate orange "Seller Rocket" badge for a different fulfillment tier |
| Coupang product data shape | Apify "Coupang Products Crawler" listing (third-party structured scrape) | Real Coupang list items carry 30+ fields incl. title/brand/current+original price/rating/review count/delivery-speed flag/ad flag — list cards are data-rich, not name+price |
| Naver 가격비교 (Shopping) price-comparison model | 메이크샵 고객센터 explainer | One product identity maps to multiple seller listings shown together, sortable by lowest price, with per-listing price-tracking — cross-seller comparison, not a single-seller catalog |
| Baymard Institute — discount/price-badge placement research | baymard.com/blog/product-page-price-discounts | Discount % must sit immediately next to the struck-through original price; badges must stay visually consistent across list card, search result, and detail page |
| itunda's own codebase (ground truth) | direct repo inspection | `MerchantProduct` has only id/merchantId/name/price/active/createdAt — no image, no discount price, no description, no stock; all three clients render single-column full-width list rows, never a grid; cross-merchant search endpoint exists and is wired into bank-mfe only; wishlist exists in bank-mfe only |
| Naver Smart Place / Kakao Hair Shop / Karrot Business Profile — convergent local-business research | smartplace.naver.com, koreaherald.com (Kakao Hair Shop), business.daangn.com | Three unrelated Korean companies independently ship the same real product: a persistent local-business profile page (hours, photos, description) with real appointment booking, distinct from a marketplace listing or a food-delivery restaurant catalog. Kakao Hair Shop's real, sourced innovation is a 100%-prepay-to-book mechanic that cut no-shows from ~20% to under 0.5%; Naver Smart Place adds owner-side review replies and push notifications on new bookings; Karrot's Business Profile adds coupons/loyalty on top |

### Recommendations (ranked)

1. **[sourced] Implemented.** Wire itunda's own existing cross-merchant product-search endpoint
   into Android and iOS. This is an itunda-internal inconsistency, not a gap against Coupang/Naver:
   `GET /api/v1/shopping/products/search` already exists on the backend, and bank-mfe already has a
   working "search across every merchant" bar wired to it (`BankDashboard.tsx:~5340`) — matching
   what every real marketplace treats as table stakes. Android's `ShopTab` and iOS `ShopScreen`
   never call it at all. **Verified 2026-08-04 (re-audit while sourcing the Eats dish grid):
   `ShopScreen.kt`'s `searchProducts()`/`NetworkClient.apiService.searchProducts(q)` and
   `ShopScreen.swift`'s equivalent `searchProducts()` both call it — this doc entry was stale, not
   an open gap.**
   *Target: `SuperAppTabs.kt` `CommerceShopContent`; `ios/App/Sources/ShopScreen.swift` `CommerceShopContent`*

2. **[sourced] Implemented.** Surface the already-shipped category/search filter params in Shop's
   own merchant browse UI. `GET /api/v1/shopping/merchants` already accepts `category`/`q`, and Eats
   already uses both in the *same file* (`~3620`) — Shop's own `CommerceShopContent` calls the
   endpoint with no params and shows no search box or chips at all. **Verified 2026-08-04: both
   clients call `getMerchantCategories()` and render real chips.**
   *Target: `SuperAppTabs.kt` `CommerceShopContent` (~2903-3048), mirror Eats' pattern a few hundred lines below; `ShopScreen.swift`*

3. **[sourced] Implemented.** Extend product wishlist ("찜") from bank-mfe-only to Android and iOS.
   bank-mfe already has `WishlistButton`/`WishlistView` backed by real endpoints; Android only has
   favorites for Eats restaurants, iOS Shop has none. **Verified 2026-08-04: `ProductWishlistView`
   exists in both `ShopScreen.kt` and `ShopScreen.swift`, wired to a real "♡ Wishlist" tab.**
   *Target: `CommerceShopContent`/`MerchantDetailView` — reuse the `FavoriteRestaurantDto` pattern; `ShopScreen.swift` `MerchantDetailView`*

4. **[partially-sourced] Implemented.** Give `MerchantProduct` real images and a discount-price
   pair. Coupang's documented data model and Baymard's research both confirm image +
   current/original price is the baseline for a real product card; itunda's `MerchantProduct`
   entity has neither — a schema-level blocker, not just a UI gap. **Verified 2026-08-04:
   `imageUrl`/`originalPrice`/`discountPercent` all exist on the real entity and DTOs, rendered via
   `ProductImageThumb` and a real struck-through-original-price badge (matching Baymard's specific
   placement research) in both clients.**
   *Target: `services/backend/core/.../MerchantProduct.kt` (add `imageUrl`, `originalPrice`/`discountPercent`, `description`); propagate through `MerchantProductController.kt`, `ShoppingController.kt`, and all three client DTOs*

5. **[partially-sourced] Implemented.** Switch merchant/product browse from single-column list to a
   2-column image-led grid. Chloe Youn's case study confirms Coupang's real cards carry quick
   add-to-cart and wishlist directly on a grid card; itunda's three clients render text-only
   full-width rows with a generic storefront icon. **Verified 2026-08-04:
   `LazyVerticalGrid(columns = GridCells.Fixed(2))` / SwiftUI's matching two-column `LazyVGrid` both
   real, plus a real 3-column variant elsewhere in the same files.**
   *Target: `CommerceShopContent` merchant list (~3016) and `MerchantDetailView` product list (~3078) in `SuperAppTabs.kt`; `browseBody`/`MerchantDetailView` in `ShopScreen.swift`; `ProductCatalogView` in `BankDashboard.tsx` (~4850)*

6. **[partially-sourced] Implemented.** Add a dedicated product detail screen with inline
   variant/qty selection. itunda has none anywhere — tapping a product only reveals an inline qty
   stepper in the flat catalog list. Coupang's redesign implies a real detail page exists to have
   replaced a multi-step flow with an inline selector on. Naver Smart Store's tab structure was
   sourced only from secondary description sites — weaker sourcing, flagged. **Verified 2026-08-04:
   `ProductDetailScreen`/`ProductDetailView` are real, dedicated screens in both clients.**
   *Target: new `ProductDetailView` alongside `MerchantDetailView` in all three clients*

7. **[partially-sourced] Implemented.** Move add-to-cart and wishlist onto the list/grid card
   itself (depends on the grid layout landing first). Chloe Youn names this as a specific,
   deliberate Coupang improvement. **Verified 2026-08-04: real, alongside item 5's grid.**

8. **[inferred] Implemented.** Add merchandising modules (banner/promo carousel, category
   shortcuts, curated deal rails) to the Shop landing surface, above the raw item list. This is
   general, widely-documented Coupang/Naver home-surface structure but was **not independently
   re-verified** this pass beyond general knowledge — flagged as the weakest-sourced recommendation
   in this section. **Verified 2026-08-04: `getShopDeals()`/a real deals rail exists in both
   clients (shipped 2026-07-25, `MerchantProductRepository.findDeals`) — this doc entry just wasn't
   updated when it shipped.**
   *Target: top of `browseBody` in `CommerceShopContent`/`ShopScreen.swift`; bank-mfe shopping tab header*

9. **[sourced] Implemented 2026-07-25.** Add real local-business appointment booking, the single
   gap Naver Smart Place, Kakao Hair Shop, and Karrot's Business Profile independently converged
   on -- itunda had a marketplace, a restaurant catalog (Eats), and a property board, but nothing
   for "any shop or service gets a page and takes bookings" (salons, tutors, repair services).
   Ships as a bookable-service flag on the existing `MerchantProduct` catalog (`durationMinutes`,
   same "a menu item IS a MerchantProduct" reuse discipline `EatsOrderService` already
   established) plus two new entities: a merchant's real weekly `MerchantAvailabilityWindow`s and
   the resulting `MerchantBooking` request/confirm/decline/cancel/complete flow. No payment or
   deposit at booking time -- no research source described one, and real local service businesses
   overwhelmingly settle in person.
   *Shipped: `MerchantProduct.durationMinutes`, `MerchantBookingService`, `POST /api/v1/merchant/bookings`,
   Shop's "Book" flow (date strip + real backend-computed slot grid), merchant app's Bookings tab
   (weekly availability editor + confirm/decline/complete queue)*
   **Reviews/coupons half closed 2026-08-05 (item 231):** found via a defined-but-uncalled-endpoint
   sweep -- `MerchantBookingReviewController.getMerchantReviews` and `MerchantCouponController.
   getCouponsForCustomer` were both real, fully-authorized backend endpoints (Karrot's own
   "coupons/loyalty on top" and Naver Smart Place's "owner-side review replies" halves,
   `regularsOnly` loyalty-gating already enforced server-side) but had zero client callers on any
   platform -- a buyer choosing whether to book with a merchant could never see that merchant's
   real review history/rating or their own real coupon eligibility before requesting a slot. Shipped
   as `MerchantBookingInfoSection` on bank-mfe, Android, and iOS, shown above the date picker in
   each platform's booking flow.

Cross-platform note: two candidates flagged by the same sweep were checked and confirmed *not*
real gaps, not built: `GET /{merchantId}/booking-availability` (raw weekly windows) is genuinely
superseded by `/{merchantId}/booking-slots` (real backend-computed concrete slots), which all 3
platforms already call; a plain `GET /gifts/{id}`/`GET /gift-vouchers/{id}` single-item detail
fetch has no natural entry point anywhere (no push notification or deep link resolves to a single
gift/voucher, and every real UI flow already works off the list/conversation endpoints) -- building
a detail screen with nothing that navigates into it would be speculative UI, not a real gap.

A third candidate from the same sweep, `DELETE /notifications/device-tokens/{token}`, closed as a
real bug fix (item 232) rather than a UI feature: `DeviceTokenController.unregister` existed (with
a real `@Transactional` fix already applied from its own original verification) but had zero
callers anywhere, so a device stayed push-registered forever after logout -- whoever logged into
the same browser/app next would keep receiving push meant for the previous account. Now called from
`logout()`/`SessionManager.logout()` on all 3 platforms, before the local token is cleared (the
access token has to be captured/ordered carefully on each platform -- see the commit for bank-mfe's
real fire-and-forget dynamic-import race and its fix). Live-verified end-to-end against the real
backend on bank-mfe: registered a token, unregistered it, confirmed the row was gone via direct SQL.

Two further candidates from the same original sweep were checked and are real backend endpoints
that don't actually return itemized data worth a client for: `VendorCashAdvanceController.
getCollectionHistory` returns only the same summary fields (`remainingOwed`, `lastCollectionAt`,
`totalOwed`, `status`) already present on the main advance object every client already fetches --
there's no separate per-collection ledger entity behind it, so a "history" screen would show
nothing new. `RideController.getDriverReviews` (a driver's written reviews, distinct from the
already-shown aggregate `rating`) closed 2026-08-05 (item 233) -- turned out smaller in scope than
first thought: `RideTrip.driverId` was already available client-side the whole time (used for
post-trip review submission), so no new driver-profile view was actually needed, just a
`DriverRatingSection` shown on the already-existing active-trip card once a driver is assigned.
Honest v1: `RideDriver` has no name/vehicle field on the backend at all, so this shows the driver's
real rating + written reviews only (expand-on-tap), never a fabricated name. Built on bank-mfe,
Android, and iOS.

While checking `getCollectionHistory`'s callers, found a genuine full platform-parity gap rather
than a sub-feature: iOS MerchantApp had **zero** client for the entire Isoko Vendor Cash Advance
feature (offer/apply/disburse/repay-early), unlike merchant-mfe and Android's native merchantapp,
which both already had it. Closed 2026-08-05 -- straight port of Android's own
`VendorCashAdvanceScreen.kt` as iOS's new `VendorCashAdvanceTab`, same real business logic and copy
verbatim, wired into `MerchantHomeScreen`'s tab picker as "Cash advance".

**Merchant-side Commerce order fulfillment queue closed 2026-08-05 (item 234)**: found via a
sibling-consistency audit -- bank-mfe's own `MerchantOrdersView`/`MerchantReturnQueueView` have
been real since before this session (a real itunda user who also runs a merchant storefront can
advance their store's Commerce orders through PLACED → PACKED → SHIPPED → DELIVERED and
approve/reject Return & Exchange requests), but had zero client anywhere on Android or iOS -- not
the main consumer apps, not either native merchant app. Straight ports on both, same
`MERCHANT_NOT_FOUND`/404-stays-silent discipline for buyer-only accounts as the source component.

### Unresolved / worth a follow-up

- Naver Smart Store's exact detail-page tab structure (상세정보/리뷰/Q&A/Story) wasn't confirmed
  against a primary Naver source — only secondary description sites.
- No official Coupang design-system page or engineering blog describing real card anatomy
  (image placement, badge stacking, aspect ratio) was reachable — Chloe Youn's case study is real
  and named but is a third-party redesign analysis, not Coupang's own documentation.
- Naver's real filter UX (facet sidebar, price-slider specifics, sort options beyond lowest-price)
  wasn't confirmed with a strong primary source.
- Moot as of 2026-08-04: items 1-8 above shipped independently, on Shop's own grid/wishlist/detail
  pattern, not by reusing Hood's Market surface (Market itself went card-less/flat-list per a later
  2026-08-03 fix — a grid wouldn't have been reusable regardless).
- Real Coupang/Naver checkout-flow specifics (address selection, payment picker, delivery-slot
  selection) weren't compared against itunda's existing `MultiCartView`.

---

## 6. Bank / Pay (Talk-embedded money, savings)

### References

| Product | Source | Pattern |
|---|---|---|
| Kakao Pay — chat money transfer | story.kakaopay.com, support.kakaopay.com | Entry point is the "+" icon inside a chat room, not a separate Pay tab; 1:1 sends directly, group requires tapping the specific member; recipient gets a notification with an explicit "receive" button (claim is a distinct act, not auto-credit); daily send/receive limits (10M/30M/2M KRW cited); sender can cancel before accept; claim required same-day; **송금봉투 (money envelopes)** — themed presets ([축하해요]/[내마음]/[행운만땅]/[정산해요]) carry occasion/message instead of free text |
| KakaoBank — Group Account (모임통장) | kakaobank.com/products/moim, eng.kakaobank.com | Creator/owner retains withdraw authority; invited members (even non-account friends) can view/deposit but not withdraw; capped at 100 members; fresh account number issued (no history leak); automated dues collection with per-member payment-day rules, one-tap "request all unpaid," playful reminder cards |
| KakaoBank — 26주적금 (26-week savings) | kakaobank.com/products/26weeks | Weekly auto-debit amount escalates automatically from the opening deposit; locked to the account's opening weekday, no mid-plan changes; interest computed per-weekly-installment on its own remaining term then summed; preferential rate requires an unbroken 26-week streak through maturity — a real "don't break the streak" gamification mechanic |
| KakaoPay 정산하기 (settlement/split-bill) 2020 redesign | seoulfn.com, hankyung.com, digitaltoday.co.kr, moneys.mt.co.kr, v.daum.net (cross-verified across 5 outlets) | "사다리타기" (ladder-game) mode randomizes the split by headcount instead of always even, 3 adjustable variance levels (top level assigns the whole amount to one "loser"); up to 5 sequential settlement "rounds" tracked per thread; manual per-person override with KakaoPay itself absorbing the rounding remainder; scheduled reminder-nudge bell for unpaid friends; up to 3 photos attached; KakaoBank runs its own, differently-branded, non-converged "1/N 빵나누기" feature — Kakao didn't even unify this within its own family |
| Toss — 더치페이 (split-the-bill) baseline for contrast | blog.toss.im/article/toss-split-the-bill | Opened by searching or tapping under a specific spending-history transaction — receipt/ledger-anchored, not chat-anchored; no randomization, no multi-round, no rounding absorption, no photo attach — deliberately minimal versus KakaoPay |
| 토스뱅크 외화통장 (Toss Bank foreign-currency account) | tossbank.com/articles/foreign-currency-exchange | Single account holds multiple real currencies (Toss's own product spans 17); 100% preferential FX rate messaging, scheduled auto-buy/accumulate, rate-triggered auto-exchange, cashback on overseas card spend using the held balance |
| 토스뱅크 개인사업자 (Toss Bank business banking) | tossbank.com/articles/selfemployed, asiae.co.kr (Boss Loan scale), businesspost.co.kr (guaranteed-loan rates) | Dedicated business account + purpose-based savings vaults + cashback business debit card, separate from consumer banking; account auto-categorizes income/expenses from transaction history for tax filing, card integrates with Homtax (Korea's e-tax system); unsecured "Boss Loans" (사장님 대출) reached ₩1.5T in a year with government-subsidized interest support |

### Recommendations (ranked)

1. **[sourced] Implemented -- doc was stale, corrected 2026-08-04.** Build a Talk-chat-embedded
   split-bill/settlement feature. KakaoPay's real 정산하기 is structurally richer than Toss's own
   더치페이: chat-embedded entry point, randomized ladder-game split with adjustable intensity, up
   to 5 tracked rounds, silent rounding-remainder absorption, scheduled reminder nudges, photo
   receipt attach. **Verified 2026-08-04 (re-audit while sourcing Talk's message-forwarding
   feature): every one of these mechanics is real and shipped** --
   `services/backend/splitbill/SplitBillService.kt`'s own doc comment lists all four real,
   dated follow-ups as closed: `ladderSplit` (사다리타기, 2026-07-25), `SplitBillReminderScheduler`
   (2026-07-27), `attachReceipt` (2026-07-28), `requestNextRound` (up to 5 rounds, 2026-07-28) --
   plus the base even-split with real rounding-remainder absorption. `GroupSplitBillsView` is wired
   into `GroupThreadView`'s own Talk thread (a real "+"-family chat action) in both Android and iOS.
   This recommendation was simply never marked closed when the feature shipped.
   *Shipped: `services/backend/splitbill` (SplitBillService/SplitBillController/
   SplitBillReminderScheduler), `GroupSplitBillsView` in `TalkScreen.kt`*

2. **[sourced] Implemented -- doc was stale, corrected 2026-08-04.** Add a Kakao-Bank-style
   auto-escalating, day-locked weekly savings product (26주적금 pattern). **Verified 2026-08-04**:
   `services/backend/savings/WeeklySavingsService.kt` + `WeeklySavingsScheduler.kt` +
   `WeeklySavingsController.kt` are real and shipped, sibling to `GroupAccountService.kt` in the same
   module, covered by their own `WeeklySavingsServiceTest.kt`.
   *Shipped: `services/backend/savings/WeeklySavingsService.kt`*

3. **[partially-sourced] Implemented -- doc was stale, corrected 2026-08-04.** Give itunda's Gift
   feature themed "envelope" presets instead of free-text notes. **Verified 2026-08-04**:
   `GiftService.kt` has a real `theme: GiftTheme?` parameter on `sendGift`/`sendGiftInConversation`,
   with a `themeLabel` mapping and its own doc comment confirming "exactly the 4 real, sourced
   presets; nothing invented."
   *Shipped: `GiftTheme`, `GiftService.kt`'s theme param/themeLabel*

4. **[sourced — validation, no action]** itunda's Gift claim/escrow flow and Group Account already
   match Kakao's real structural pattern point-for-point: Gift's escrow-then-explicit-claim
   mirrors KakaoPay's real notification-driven claim (not instant credit), and Group Account
   (`GroupAccountService.kt`, shipped 2026-07-20) already mirrors KakaoBank's 모임통장 mechanics
   (100-member cap, owner-only withdraw, fresh account number, transparency notifications) per the
   parity matrix's own citation. No new work follows from this — flagged so the design team knows
   these two are genuinely sourced-accurate already, not just superficially similar.

5. **[sourced] Implemented 2026-07-25.** Add a real 토스뱅크 외화통장 (foreign-currency account)
   equivalent, scoped to USD/EUR/GBP -- the currencies real Rwandan diaspora remittance corridors
   (US, Eurozone/Belgium, UK) actually run through, not Toss's real 17-currency breadth. Honestly
   scoped: this is real conversion between a user's OWN RWF and foreign-currency wallets at a real
   live rate (a free, keyless public FX feed -- Frankfurter's ECB-only feed doesn't carry RWF,
   confirmed live before picking a provider that does) plus a real itunda margin, not a
   cross-border receiving/SWIFT rail (itunda has no real correspondent-banking relationship to
   build one on, same genuinely-blocked-external-access category as NIDA/PSP elsewhere in this
   codebase). Each conversion is two separate, each-individually-balanced single-currency ledger
   transactions rather than one cross-currency one, since `LedgerService.postLedgerTransaction`
   enforces raw debits==credits per call with one shared currency label.
   *Shipped: `WalletType.FOREIGN_CURRENCY`, `ForeignCurrencyRateClient`, `ForeignCurrencyWalletService`,
   `POST /api/v1/wallet/foreign-currency/convert`, Menu's "Foreign currency" screen*

6. **[sourced] Implemented 2026-07-25.** Add a real 토스뱅크 개인사업자 (business banking for sole
   proprietors) equivalent, scoped to its real, buildable core -- `Merchant.kt`'s own doc comment
   already named the structural gap: every merchant's real card/QR collection has always settled
   straight into their PERSONAL main wallet ("reuses the owner's existing MAIN wallet... rather
   than introducing a new WalletType"), so business income and personal spending were genuinely
   inseparable. Ships a real dedicated `WalletType.BUSINESS` wallet a merchant can open, plus real
   fee-free wallet-to-wallet money movement into/out of it and its own real transaction history --
   deliberately does NOT touch `MerchantService.collect`/`chargeCard` at all (those keep settling
   to `Merchant.walletId` exactly as before), favoring an additive new wallet over modifying
   already-tested money-movement code. Deliberately not shipped, named follow-ups: a real business
   debit card (itunda has no physical card issuance anywhere to extend), expense
   auto-categorization for tax filing (`WalletService.getSpendingInsight`'s existing categorization
   logic is the real reusable foundation once there's a concrete tax-authority integration to
   feed), and 사장님 대출 (Boss Loans) as a business-specific lending product (itunda's existing
   generic Loan product is the real foundation, but business-specific underwriting is a genuinely
   separate build).
   *Shipped: `WalletType.BUSINESS`, `MerchantBusinessAccountService`,
   `POST /api/v1/merchant/business-account/{move-to-business,move-to-personal}`, merchant app's
   Business tab*

### Unresolved / worth a follow-up

- KakaoBank's exact onboarding/KYC screen sequence wasn't sourced beyond general "simple UI"
  commentary from personal blogs — would need an app teardown or screenshot walkthrough.
- A complete, verified catalog of 송금봉투 presets and their visual design wasn't found — the
  primary Kakao help page for this is now defunct/redirected.
- KakaoPay's same-day claim-expiry window and 10M/2M KRW limits came from a search-engine summary
  layered over story.kakaopay.com, not a directly re-verified current help page — re-confirm before
  treating exact figures as current.
- KakaoBank/KakaoPay's personalized product-recommendation logic has no official documentation or
  credible engineering-blog source — excluded from recommendations entirely as unsourced.

---

## 7. Cross-product consistency: what's shared vs. surface-specific

itunda's six surfaces pull from six different real ecosystems, which creates a real risk of the
app feeling like six different apps stitched together if every pattern is treated as
surface-local. The following separates what should become a **shared itunda component** (built
once, reused across tabs) from what is genuinely surface-specific and should **not** leak into
other tabs, even though the underlying idea (e.g. "bottom sheet") sounds generic.

### Should be shared, one component, multiple call sites

- **Bottom sheet primitive (peek/half/full, draggable, non-modal up to a threshold).** This exact
  need shows up independently in three research threads: Maps (place-detail + around-me),
  Eats/Shop (if a product-detail sheet pattern is adopted instead of a full page), and potentially
  Hood (Seed Design's own Bottom Sheet spec was flagged as fitting category/filter selection,
  though not confirmed as Karrot's actual usage). Building one `AnchoredDraggable`-based
  itunda-sheet component — matching what both Apple's `UISheetPresentationController` and Google
  Maps' custom-extension pattern converge on — and reusing it across Maps and any future
  Eats/Shop detail sheets avoids three independent, slightly-different sheet implementations.

- **Skeleton/placeholder loading state. Implemented 2026-08-06 (item 239).** The "Loading…"
  text-row problem was flagged specifically in Hood but is visibly the same anti-pattern anywhere
  itunda shows a bare loading string instead of a shaped placeholder. **Real finding on
  inspection**: bank-mfe (a real `.skeleton` CSS shimmer class) and Android (`SkeletonBlock`,
  `core/designsystem`'s `HoodShared.kt`) already had this real animated shaped placeholder --
  iOS was the actual gap, falling back to a plain `ProgressView()` spinner everywhere instead.
  New `SkeletonBlock` in `Core/DesignSystem/Components.swift`, same animated gradient-sweep shape
  as the other two platforms; swapped all 16 real `ProgressView().frame(maxWidth: .infinity,
  minHeight: N)` full-list-loading call sites across Talk/Eats/Shop/Hood/AutoTopUp (inline button
  spinners deliberately left as `ProgressView`, matching Android's own convention of reserving
  `SkeletonBlock` for list/section-level loading only).

- **Wishlist/heart affordance + toast-confirmed add/remove.** This pattern is independently
  recommended for Hood listings and Shopping products, and already exists for Community posts and
  Eats restaurant favorites. It should be one shared favorite/heart component with a shared toast
  copy convention ("saved"/"removed from interest list"), backed by one generalized
  favorite-entity backend shape, rather than four separate bespoke favorite implementations (which
  is close to what exists today — bank-mfe wishlist, Android Eats favorites, Community likes are
  all separate).

- **Chat-embedded financial action pattern ("+" menu → Gift / Split-bill / future transfer).
  Factual correction 2026-08-07: Split-bill already shipped, but not in this shape.** Gift is a
  real inline chat bubble with an explicit-claim step, exactly the pattern this bullet describes.
  Split-bill (`GroupSplitBillsView`) is also real and shipped, but as a separate full-screen
  takeover reached by leaving the chat thread (`showSplitBills` swaps the whole view, `onBack`
  returns to the thread) -- not an inline bubble in the message stream. A real, still-open
  architectural inconsistency, not a missing feature: converting Split-bill's already-shipped,
  working full-screen flow into inline chat bubbles would be a genuine redesign with real
  regression risk to a tested feature, not a quick fix -- left as a named, deliberately deferred
  recommendation rather than attempted this pass.

- **Search-with-filter-chips pattern. Implemented -- doc was stale, corrected 2026-08-07.**
  Re-checked while auditing this section: bank-mfe's own `SearchAndCategoryChips` is a real single
  shared component (search field + horizontal chip row) used by both `OrderFoodView` (Eats) and
  `ShopView` (Shop) -- not two independently-built implementations. This entry's own "Shop is
  missing it" claim was true when written but the gap had already been closed elsewhere in this
  doc's own history (Section 5's item 6, cited in this entry's own last sentence) without this
  cross-consistency note being updated to match.

- **Product/listing card "quick action without opening detail". Implemented -- doc was stale,
  corrected 2026-08-07.** Re-checked while auditing this section: Shop's product grid cards
  already have both a real inline quantity stepper and a real shared `WishlistButton` directly on
  the card (not buried behind a detail-page visit) -- the card's own code comment even cites this
  exact doc section by name. Hood listing cards independently reuse the identical `WishlistButton`
  component (see this same file's Marketplace/RealEstate/Jobs `ListingCard`s). One shared
  interaction contract across both surfaces, exactly as recommended; this note just hadn't been
  updated to match.

### Should stay surface-specific — do not generalize into a shared component

- **Trust/reputation badge semantics.** Hood's recommended Karrot-Score-style numeric trust badge
  is specifically about person-to-person marketplace trust between strangers transacting locally.
  This should **not** bleed into Talk (which is closer contacts, not stranger transactions) or
  Shop (where merchant trust is closer to a business rating, not a peer-to-peer score). Keep the
  Hood trust badge a Hood-only concept.

- **Map bottom-sheet content sections** (Naver's "Today's Pick"/"Nearby"/"New openings" curated
  rails). This curated-discovery content model is specific to Maps' place-discovery use case and
  should not be copied into Shop's merchandising modules (Section 5, item 8) even though both are
  "browse surface with a home rail" in the abstract — Maps' rails are place-recommendation-driven,
  Shop's would be promo/deal-driven; conflating them risks a generic, purposeless carousel in both
  places.

- **Karrot's asymmetric public/private review split** (good points public, uncomfortable points
  private) is a specific answer to secondhand peer-to-peer transaction dynamics (avoiding
  platform-wide seller reputation damage from one bad private trade). This is recommended for Hood
  transactions specifically (Section 4, item 2) and should not be generalized to Eats/Shop
  reviews, where the reviewed party is a business, not a peer, and public accountability serves a
  different purpose.

- **26-week savings' streak-gated interest mechanic.** This is a specific KakaoBank product
  mechanic (Section 6, item 2) tied to a savings product's maturity terms. It should not be
  reused as a generic "gamification" pattern elsewhere (e.g. do not apply a streak mechanic to
  Hood posting frequency or Talk usage) without its own independent sourcing — nothing in this
  research supports that extension.

- **Money-envelope theming (송금봉투)** is specific to person-to-person Gift/transfer occasions
  (congrats, good luck, settle-up) and shouldn't be generalized into, say, Hood listing templates
  or Shop gift-wrapping — it's a Talk/Pay-specific expressive layer, sourced only in that context.

### Open question for the design team

Several "shared" candidates above (the sheet primitive, the favorite/heart component, the
chat-embedded-money shell) don't exist as shared IDS components in itunda today — each surface
independently rebuilt a version of them (bank-mfe wishlist vs. Android Eats favorites; Gift's
existing chat-bubble shell vs. what a new Split-bill feature would naturally reinvent otherwise).
Before building any of the individual recommendations above, it is worth a short IDS design pass
to define these as first-class shared components — the same discipline that keeps itunda's brand
color unified should extend to these interaction primitives, so six product surfaces continue to
feel like one app rather than six.

---

## 8. Sign-up simplicity & user-segment design

**Added:** 2026-07-21. Three parallel research threads into Toss (Viva Republica): 회원가입
(sign-up) simplification specifically, general interaction-simplicity philosophy, and
segment-specific products for seniors/teens/foreign residents. Cross-referenced against a full
current-state inventory of itunda's own registration/login/accessibility code (all 3 clients +
backend) so every recommendation below cites a real file, not a hypothetical.

### References

| Topic | Source | Pattern |
|---|---|---|
| Sign-up field-entry redesign | toss.tech/article/toss-signup-process | Fields auto-append *below* the one just filled (reverse-stacked scroll) rather than splitting across pages; usability testing found users fixate on the active cursor field and don't notice the reversed order (the article likens this to the "invisible gorilla" selective-attention effect); a separate top-to-bottom flow exists for VoiceOver users specifically |
| Sign-up conversion experiments | toss.tech/article/signup | Four sequential, named A/B experiments as the user base skewed older: cutting permission prompts (negligible), removing the intro screen (negligible), optimizing for large system text + a mandatory confirm button (negligible), and — the one that actually worked — adding *contextual explanation of why a step is needed*, specifically on Android where iOS already had it and Android didn't |
| Login credential | toss.im/tossfeed/article/toss-overseas-identity-verification | Toss's real login credential is a 6-digit PIN or Face ID, not a conventional alphanumeric password; overseas/no-Korean-carrier users get a separate passport+NFC-based path instead of carrier-SMS real-name verification |
| Toss product principles (official) | toss.im/tossfeed/article/tossproductprinciples | Named principles: **Clear Action**, **One Thing** (one core message per screen), **Easy to Answer** (any on-screen question answerable within 3 seconds or the product recommends one), **No More Loading**, **Minimum Features**, **Value First** |
| Insurance-claim redesign case study | toss.tech/article/insurance-claim-process | Reframing an ambiguous open question ("get help, or do it yourself?") into a concrete yes/no question ("do you have the documents?") cut abandonment at that step by 60% and drop-off between screens by 50% — a direct, quantified application of "Easy to Answer" |
| UX research team & methodology | toss.im/tossfeed/article/user-research-team-interview, toss.tech/article/26109 | Dedicated User Research function (interviews/FGI/UT/diary studies/journey maps, standing UT room, same-day recruiting); documented rule from a real case study — "for daily-use services, wait at least a month before reading user opinion" — after a new feature's sentiment normalized post-launch |
| Push-notification de-targeting | toss.tech/article/data-analyst-ab-test | Real 2-month, 3-variant, 6%-of-users A/B test: auto-dropping a user from a notification category after N consecutive non-responses raised CTR ~4-4.5pp with no meaningful drop in app-opens/usage/revenue — fatigue traced to *irrelevant* volume, not frequency itself |
| Design system rethink | toss.tech/article/rethinking-design-system | Toss's own design system became too rigid, causing teams to fork components locally around it; fixed by offering both a **Flat** (simple) and **Compound** (composable) version of the same component — cited elsewhere in this doc (Section 7) as already influencing itunda's own `TdsButton` work |
| Youth/teen products | toss.im/teens/uss-card, toss.im/tossfeed/article/how-teens-get-toss, newsis.com 2025-08-06 | Three-tier real product line: **Youth Home** (7-13, deliberately feature-subtracted, not the adult app minus a lock), **USS/Youth Card** (7-16, prepaid-only, ₩500k balance/txn caps, blacklisted merchant categories, instant parent-freeze), and full self-service banking for 14+ using **passport-based identity verification instead of a resident-registration number** |
| Senior usability research | toss.tech/article/senior-usability-research | In-person testing with 50+ users found: non-button clickable elements (text/arrows) go unrecognized; labeled "example" placeholder images get mistaken for real personal data; users fixate on mimicking an animated intro character during face-liveness setup instead of following the actual instruction; question-phrased microcopy reads as ambiguous vs. imperative phrasing; no instinctive scrolling, missing off-screen content entirely |
| Accessibility tooling | toss.im/tossfeed/article/ally | In-house automated a11y linter ("Ally," ~100 errors/hour caught pre-launch) built directly from feedback gathered from blind users; font scaling inherits all native OS steps (9 iOS / 12 Android) rather than a typical 3-step in-app toggle |
| Foreign-resident onboarding | khan.co.kr 2022-05-02, korean-culture.org | First Korean internet-bank to offer non-face-to-face account opening for foreign residents via ARC/residency card cross-checked against government MyData; unsecured personal loans explicitly excluded for this segment; 10-language in-app support (full flow translation, not just labels) |

### itunda's current state (verified, not assumed)

- **Registration is already fairly minimal**: `RegisterRequest` (`services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt:5-20`) requires only `phoneNumber`/`firstName`/`lastName`/`password`; `email` and `referralCode` are optional. No KYC is required at registration — `User.kycVerified` starts `false` and identity verification is a fully separate, later, opt-in module (`IdentityController.kt`). This already matches Toss's own "defer identity verification, don't gate signup on it" pattern — a genuine existing strength, not a gap.
- **No phone verification exists at all** — any phone number is accepted with zero proof of possession. No SMS/OTP flow exists anywhere in the backend (confirmed by repo-wide grep).
- **Login is conventional phone+password only** (`AuthDtos.kt:22`, bcrypt-compared in `AuthService.login`). itunda already has a real biometric primitive (`NIDABiometricAuth.kt`/`.swift`) but it's used exclusively as a transaction step-up gate before confirming a transfer (`ItundaAppScreen.kt:215,252-254`) — never at login/app-launch. FacePay is similarly collection-only (merchant payment), never login.
- **No passport/alternative-ID path exists** — `IdentityService.submit` explicitly returns `UNSUPPORTED_DOCUMENT_TYPE` for anything but `NATIONAL_ID`/`BUSINESS_TIN` (`IdentityServiceTest.kt:82-83`), meaning a foreign resident of Rwanda (Congolese, Burundian, Ugandan, Kenyan, or expat) has no supported KYC path at all today.
- **No accessibility features exist anywhere** — no font-scaling override, no simplified/senior mode, no multi-language support in any of the 3 clients. `User` has no age/date-of-birth field, and there is no age-gated product anywhere (confirmed by repo-wide grep).
- **bank-mfe has no registration UI at all** — `LoginPage.tsx` is login-only; there's no sign-up page on that client, an itunda-internal gap unrelated to Toss.

### Recommendations (ranked)

1. **[implemented, backend]** Real phone verification now exists (2026-07-26), mirroring
   `requestEmailVerification`/`confirmEmailVerification` field-for-field exactly as this
   recommendation asked: a real single-use 6-digit OTP, delivered via in-app `Notification` (no
   SMTP/SMS relay exists), sent automatically at registration with a resend action for an
   expired/lost code. Honestly does not prove real SMS possession -- the user is already
   authenticated when they see the in-app notification, same limitation the recommendation itself
   anticipated. No client UI touchpoint yet on any of the 3 clients' registration screens.

2. **[sourced] Implemented -- doc was stale, corrected 2026-08-05.** Reuse itunda's own
   existing biometric primitive as a login/app-launch quick-unlock, not just a
   transaction step-up gate. This was actually built 2026-07-21, two weeks before this
   correction -- the recommendation just never got marked closed. Real, fully wired on
   both native apps: `AppLockScreen.kt`/`AppLockScreenView.swift` gate entry into the
   app once per process launch (checked in `MainActivity.kt`/`ItundaApp.swift`),
   backed by `TokenStore.isAppLockEnabled()`/`KeychainTokenStore.isAppLockEnabled()`
   with a real toggle in `SettingsScreen.kt`/`SettingsScreen.swift`, reusing
   `NIDABiometricAuth`'s already-real Keystore/BiometricPrompt plumbing -- no new
   crypto, no schema change, matching exactly what this recommendation asked for. Not
   applicable to bank-mfe (a browser tab has no native biometric-gate equivalent to
   this).

3. **[implemented, backend, correction 2026-07-26]** This description was already stale when
   written -- checking the code directly found `IdentityService` did NOT reject `PASSPORT`
   (it was already an accepted `documentType`, creating a real `PENDING` submission for human
   review); only the automated pre-check itself was a stub. `DemoNidaVerificationService` now
   real-validates passport numbers against ICAO Doc 9303's shared shell (6-9 alphanumeric
   characters) and returns a real deterministic MATCHED/NOT_FOUND/INVALID_FORMAT signal, same
   convention as National ID, honestly never fabricating fields a generic passport number can't
   convey. Still open: no consumer-facing KYC submission UI exists on any of the 3 clients at
   all (for either document type) -- that's the real remaining half of this gap.
   *Target: first consumer KYC UI on all 3 clients*

4. **[sourced]** Apply "Easy to Answer" (Toss's own principle, quantified 60%/50% drop-off
   reduction in a real case study) to itunda's own highest-friction moment found this pass: KYC
   submission has no consumer UI at all, so there's no current screen to audit — but the principle
   should shape whatever screen recommendation 3 above produces: prefer a concrete yes/no framing
   ("do you have your ID or passport with you?") over an open question, and test the imperative-
   vs-question microcopy distinction the senior-usability research also independently confirms.

5. **[sourced] Audited 2026-08-05 -- functionally already satisfied.** Inherit full native
   OS font-scaling rather than building (or not building) any in-app text-size toggle.
   Android's Compose `sp` unit is inherently OS-scalable by platform default; a
   spot-check across `app/ui/*.kt` found no `fontSize = X.dp` anti-pattern or
   `LocalDensity`/`fontScale` override disabling it. iOS's `IDS.scaledFont`/
   `IdsTypeScale` (`Core/DesignSystem/Sources/IDS.swift`) already wrap
   `UIFontMetrics.scaledFont(for:)`, Apple's own documented Dynamic-Type pattern, per
   that file's own doc comment. Not an exhaustive file-by-file audit (dozens of call
   sites use `.sp`/`.font()`), but the correct pattern was confirmed as the default,
   not an exception -- no accidental fixed-size text found in the sample checked.
   *Target: audit `sp`/fixed-size `Text()` calls across `SuperAppTabs.kt` and SwiftUI `.font()`
   modifiers for hardcoded sizes that don't respond to system text-size settings*

6. **[partially-sourced]** Consider a teen/youth product tier — the biggest lift of any
   recommendation here, requiring a new `User.dateOfBirth`/age field (doesn't exist today),
   parental-consent/linking design, and spend-cap enforcement, none of which itunda has any
   foundation for yet. Toss's own three-tier structure (Youth Home for 7-13, USS Card for 7-16,
   passport-based self-service for 14+) is real and sourced, but porting it is a multi-session
   effort, not a single closable gap — flagged as a real opportunity, not a next action.

7. **[inferred, itunda-internal] Implemented 2026-07-xx -- doc was stale.** Build a real
   registration UI for bank-mfe. Re-checked 2026-08-06 while researching this section's remaining
   items: `lib/api.ts`'s real `register()` function (phone/name/password, real device binding on
   the same request, same auto-trust reasoning `LoginScreen.kt`/`.swift` already use) has existed
   for a while -- this entry just hadn't been updated when it shipped.

### "One thing, one page" — a deeper look

**Added:** 2026-07-21, a follow-up pass specifically on this one principle (user asked to go
deeper than the one-line summary above).

- **[sourced]** The canonical statement (toss.im/tossfeed/article/tossproductprinciples, principle
  #8 of 10) is genuinely just one sentence — *"each page should deliver a single, clear core
  message, refined to its essentials"* — with no elaboration or named screens at that URL. The real
  depth lives in separate toss.tech engineering articles, not the principles page itself.
- **[sourced]** Toss's own real-name signup redesign (`toss.tech/article/toss-signup-process`,
  already cited above) treated this principle as a hard constraint, not a suggestion: the designer
  explicitly rejected splitting the form across multiple "next"-tap screens as "burdensome," and
  rejected cramming every field onto one static screen as a "One Thing" violation — landing instead
  on the reverse-stacking single-scroll pattern already described in Section 8's main findings.
- **[sourced]** Toss's own **"Flow" architecture** (`tosspayments.com/blog/articles/engineering-note-1`)
  enforces this at the *code* level, not just as a visual-design guideline: because every screen is
  scoped to one action, a real multi-step funnel (e.g., escrow → SMS verify → password → confirm)
  becomes a composed chain of small "Flows," each independently tracking how many of its own steps
  deep it is (`pageCount`) so a real back-press unwinds exactly that many screens — a single shared
  browser-history stack can't do this once screens are broken down this finely. Named cost: chaining
  flows causes a visible flash back to each sub-flow's entry point.
- **[sourced] The exception case, and how Toss actually resolved it**: Toss's own "내 문서함"
  (My Documents) feature — one screen combining certificate issuance, bill payment, and
  notifications — was diagnosed as a "One Thing" violation (`toss.tech/article/mydoc`,
  *"화면 내에서 우선순위 정리가 되지 않았던 것"* — priorities weren't organized within the screen).
  Toss's fix was **not** to carve out an exception for "complex" screens — it was to eliminate the
  multi-purpose screen entirely, redistributing each function to a contextually appropriate spot
  elsewhere in the app. No case was found anywhere of Toss defending a genuinely multi-action screen
  as a legitimate exception — a violation is treated as a bug to restructure, full stop.
- **[unconfirmed]** No TDS (Toss Design System) documentation was found that codifies this as a
  component-level rule (e.g., "max one `fill`-variant primary button per screen") — the discipline
  appears to live entirely in product principles + the Flow architecture, not an enforced API
  constraint.

8. **[sourced]** Adopt "one thing per page" as an explicit, written itunda IDS principle — it
   doesn't exist as a stated rule anywhere in this repo today, only as an emergent pattern in some
   screens. itunda already has one real, structural precedent to build the written rule around:
   `TransferStep` (`ItundaAppScreen.kt` — a sealed `Recipient`/`Amount` step model with its own
   `BackHandler` unwinding exactly one step at a time) is itunda's own miniature version of Toss's
   Flow architecture, already proven for Transfer. The actionable move is applying that same
   sealed-step-plus-explicit-back-handling shape consistently to itunda's *other* multi-step
   money/identity flows (Savings goal creation, Gift sending, and whatever KYC UI results from
   recommendation 3 above) rather than each screen inventing its own ad hoc step tracking — and,
   per the My Documents case above, treating any future screen that accretes two unrelated actions
   as a bug to decompose, not a tradeoff to accept.
   *Target: new short principle in itunda's own IDS documentation; `TransferStep` in
   `ItundaAppScreen.kt` as the pattern to replicate, not reinvent, elsewhere*

### Unresolved / worth a follow-up

- No sourced "sign-up in under X minutes" marketing claim was found for Toss anywhere, official or
  press — any such claim should be treated as unconfirmed rather than cited.
- Whether Toss's 2022-announced auto-detected 50+ senior mode still exists/matches its original
  description in 2026 was not confirmed either way by any later source.
- Toss's exact current (2026) sign-up screen-by-screen flow wasn't verified against a live, dated
  screenshot — the sourced articles describe design decisions and a flow skeleton, not exact
  current field/screen counts.
- A "skip for now" pattern deferring optional fields at Toss sign-up could not be confirmed either
  way; one low-quality source claimed referral codes can't be added post-signup (the opposite of
  deferral) but wasn't independently verified.
- No dedicated Toss UX-research case study for the foreign-resident segment was found (unlike
  seniors) — unclear whether Toss has run equivalent usability studies for that segment at all.
- Competitor senior-mode comparison (KB/Shinhan/Hana/Woori) rests on a single third-party teardown,
  not independently cross-verified bank-by-bank.
- No before/after drop-off metrics were found for the signup redesign specifically (unlike the
  insurance-claim case, which has real 60%/50% figures) — the reverse-stacking pattern is real and
  sourced, but its actual conversion impact isn't quantified anywhere found.
- No public TDS documentation confirms a component-level "one primary button per screen" rule —
  the one-thing-per-page discipline appears to live in product principles and the Flow architecture
  only, not an enforced design-system API constraint.
- One secondary source (disquiet.io) discussing Toss's one-page-one-thing philosophy returned an
  HTTP 403 on direct fetch and could only be seen via a search snippet — excluded from findings
  above as unverifiable; worth a retry via a different fetch method if more depth is needed later.

---

## 9. Graphics: illustration, iconography, color, and motion

**Added:** 2026-07-21 (Toss-only pass). **Rewritten 2026-08-04** after an audit found this section
was 100% Toss-sourced despite the document's own claim of six parallel ecosystem deep-dives — see
the correction note at the top of this document. The Toss material below is kept (it's real and
useful) but is now balanced with genuine research into Naver, Kakao, Coupang, Baemin, and Karrot's
own graphic/brand identity, not just their interaction patterns (which Sections 1-8 already cover
reasonably well).

### References — Toss (original pass, kept)

| Topic | Source | Pattern |
|---|---|---|
| Icon system | developers-apps-in-toss.toss.im/design/resources.html | Icons used at 24-40px; explicit rule to **never combine multiple icons side by side** ("use only one at a time"); 7,000+ icons/emoji available via AppBuilder/Figma; decorative icons marked non-readable for screen readers |
| Illustration style rules | developers-apps-in-toss.toss.im/design/resources.html | Explicit target aesthetic: "simple, clear, clean digital graphic style" — hand-drawn look, "lyrical/sentimental" painting styles, and cartoonish expression are all explicitly ruled out as feeling out of place; graphics must read correctly in both dark and light mode using mid-tone colors |
| Illustration's product role | toss.im/tossfeed/article/graphicdesign-team-interview | Graphics evolved from "seasoning" to a core problem-solving tool; a 3,600-emoji custom set ("Toss Graphic Universe") built around deliberately non-clichéd, original metaphors rather than stock financial iconography |
| Illustration at friction points | toss.im/tossfeed/article/why-motion-in-finance | Cute/friendly animation deliberately frames uncomfortable moments (network errors, terms-agreement screens) as "less daunting"; confetti-style animation celebrates positive moments |
| Brand color, re-examined | toss.tech/article/43061 | Toss's own internal research found users did **not** spontaneously recall the brand as "blue" alone; what people actually retained was white background + blue logo + square app-icon frame + bold black type together |
| Motion/interaction principles | toss.tech/article/interaction, toss.im/tossfeed/article/why-motion-in-finance | Express with one movement instead of many words; emotional softening; a shared cross-platform motion library ("Rally") standardizes easing tokens |
| Tossface (custom emoji font) | toss.im/tossface | Six explicit rules: simplest form for small-size legibility, uniform sizing across all 3,600 glyphs, one unified palette, all directional emoji face right, fixed 45° perspective angle |

### References — Naver, Kakao, Coupang, Baemin, Karrot (2026-08-04 pass)

| Topic | Source | Pattern |
|---|---|---|
| Naver brand color | **[sourced]** navercorp.com/en/company/brandGuide (official) | `#03C75A` ("NAVER Green"), stated to symbolize "trust and reliability... friendliness, and eco-friendliness"; logo may never be outlined, gradiented, or recolored |
| Naver per-service color extension | **[partially-sourced]** designcompass.org NAVER icon-renewal article; korea Times "green dot" article | 189 service icons renewed over 468 days; NAVER Maps' own color system is "based on the existing green and blue of NAVER Maps, a naturally extending spectrum" — i.e. tonal variation *within* the green/blue family per service, not a wholly separate hue per service. Mobile-app-wide identity now centers on an interactive "green dot" search button as the unifying mark, not just the wordmark |
| Kakao brand color, re-verified | **[sourced]** kakaocorp.com/kakao/introduce/ci (official CI page); kakaopay.com/brand (official) | KakaoTalk `#FEE500`, KakaoPay confirmed **`#FFEB00`** (same yellow family) directly from KakaoPay's own brand page — chosen specifically to differentiate from Naver (green), Daum (blue), Facebook (blue). Confirms, doesn't contradict, itunda's existing one-color decision (see Section 0 note above) |
| Kakao chat bubble language | **[partially-sourced]** oh-my-design.kr/design-systems/kakao (third-party reverse-engineered token aggregation, not official) | Warm yellow bubble for your own messages, clean white for others' — an instantly legible visual language relying on *one* color contrast, not a palette. 12px corner radius standard across buttons/cards/avatars; flat design, no shadow on bubbles, depth via background color only |
| Kakao emoticons as product, not decoration | **[sourced]** inquivix.com Kakao Emoticons article | Kakao Emoticons (Ryan, Apeach, Muzi, from the "Kakao Friends" character lineup) are a real ~$300M+ business line, not throwaway stickers — proof that in the Korean market, character-driven expressive graphics are a legitimate, monetizable product surface, not just a UX nicety |
| Coupang design system existence | **[sourced]** medium.com/coupang-engineering "Introducing Coupang's design system" (official eng blog) | Real internal "Rocket Design System" (RDS): Elements (color/type/icon) → Parts (buttons, chips, controls) → Units (composed, business-specific components); explicitly justified by A/B-tested business-metric impact, not aesthetics alone. **Not publicly published** — no public token values exist to cite, a real, stated limitation |
| Coupang badge system | **[partially-sourced]** windly.cc RocketGrowth badge guide | Two-tier delivery badge system: a base "Rocket Delivery" badge vs. a "Rocket WOW" badge for members-only extra benefits (dawn delivery, free shipping/returns) — badges are tied to a real, named membership/benefit tier, not decorative labels |
| Baemin brand philosophy | **[sourced]** multiple: Wikipedia (Baedal Minjok), vietcetera.com, Sandoll type-foundry story page | Explicit "playful warmth" identity built by founder Kim Bong-jin around typography as cultural heritage, not just a UI toolkit; signature mint-green plus legendary UX-writing wit ("배민다움") |
| Baemin custom typefaces | **[sourced]** en.sandoll.co.kr (official type foundry, Baemin's actual font partner); noonnu.cc font catalog | 12+ real, publicly-released custom fonts, each preserving a specific disappearing Korean signage tradition: Hanna (1960s-70s acrylic shop-sign lettering, named after the founder's daughter), Jua, Dohyeon, Euljiro (weathered district-lettering revival), Kkubulim. This is real cultural-preservation-as-brand-strategy, not generic "friendly rounded font" styling |
| Baemin illustration style | **[partially-sourced]** oh-my-design.kr/design-systems/baemin (third-party, current tokens dated to "Baemin 2.0," July 2025) | Illustration-based icon system leans sketch-like/appetizing rather than flat-geometric; brand palette centers `#0cefd3` mint on white/near-white surfaces with high-contrast dark text, not a saturated multi-color palette |
| Karrot rebrand & mascot | **[sourced]** about.daangn.com official PR archive (당근이/단추/앙리 character launches); noonnu.cc font post | Official mascot **당근이 ("Danggeuni")**, launched 2015 alongside the service name itself — a local neighborhood *dog* (explicitly not a rabbit, despite looking similar), positioned as "a guide for healthy neighborhood living." Karrot has since expanded its character roster (단추/Danchu, 앙리/Angri, 2024) into an actual character-licensing business, publishes 당근이 webtoons, and uses "Karrot Sans," a custom brand typeface, in its wordmark/symbol |
| Karrot design tokens | **[partially-sourced]** oh-my-design.kr/design-systems/karrot (third-party reverse-engineered aggregation, not official) | Primary `#ff6f0f`/marketing `#ff6600` orange, used deliberately sparingly ("orange is scarce, on purpose" — CTAs/active states only); system font stack, "content is the brand," no custom typeface loaded on the actual product surfaces (the Karrot Sans wordmark is brand/marketing use, not body UI type); explicit design principle "trust via calm, not badges — no padlock icons in main flow"; target 3-4 listings visible per mobile viewport (content-dense, chrome-light); spring animations explicitly forbidden, only linear/standard easing curves |

### itunda's current state (verified, not assumed)

- **Zero illustrations or characters exist anywhere in itunda.** Every empty state found across
  this session's own work (Marketplace, Shop, Eats, Talk, Community, Jobs, Property) is bare text
  — `Text("No listings yet.")`, `Text("No merchants registered yet.")`, and so on. This remains the
  single largest, currently-zero-effort gap in this section — true whether measured against Toss's
  emoji system, Kakao's character-driven emoticons, Karrot's own neighborhood-dog mascot, or
  Baemin's illustration-based icons. Every one of the five non-Toss ecosystems researched above
  independently arrives at the same conclusion Toss did: personality-bearing graphics at exactly
  these low-content moments (empty, error, onboarding) is not optional polish in this product
  category, it is a real, repeatedly-reinvented pattern.
- itunda's copy voice is currently neutral/functional everywhere (`"No listings yet."`,
  `"Couldn't reach itunda. Check your connection and try again."`). None of the five ecosystems
  above ship copy this flat at these moments — Baemin's "배민다움" conversational wit is the most
  extreme documented example, but even Toss's own softened error framing (Section 9's original
  Toss references) sets a bar itunda's current copy doesn't meet anywhere.
- Icon usage is otherwise reasonably disciplined already (Material/SF Symbols icons used singly per
  row/button, not stacked) — no confirmed violation of Toss's "one icon at a time" rule was found,
  though this wasn't exhaustively audited.
- itunda's single-blue-brand decision is, per the re-verification above, actually the *better*
  match to Kakao's real, confirmed practice than the alternative would be — this is a place itunda
  is already doing the right, sourced thing, not a gap.

### Recommendations (ranked)

1. **[sourced, cross-verified against 6/6 ecosystems] Structure implemented; copy partially closed
   2026-08-06 (item 238).** Build a real shared empty/error-state component: an icon-in-a-soft-circle
   + title + **warmer, more specific subtitle copy** (closer to Baemin's documented conversational
   register than itunda's original flat phrasing, without inventing a fictional mascot character
   itunda has no in-house illustrator to actually draw). The structural half (`EmptyState`
   composable/View/component, icon-in-circle layout) was already real on all 3 platforms. The copy
   half was still flat everywhere ("No orders yet.", "No notifications") until now -- rewrote ~48
   call sites across four rounds on all 3 platforms: round 1 covered the highest-traffic daily
   surfaces (wallet transactions, home overview, Talk conversations/groups, notifications, Eats/Shop
   order history, carpool bookings, Marketplace search, Shop's merchant directory, card purchases,
   trusted devices); round 2 covered auto-transfers, scheduled transfers, savings goals, spending
   breakdown, saved contacts, family members, vehicles, merchant subscriptions, and the Float
   marketplace (agent cash-float listings/requests); round 3 covered vehicle inspections (buyer +
   mechanic queue, distinct copy for each real-world role), Community sessions, split bills,
   Eats/dine-in table orders, restaurant/store menus and catalogs, product/restaurant written
   reviews, and the booking time-slot picker (also closed iOS's own two remaining plain-`Text`
   gaps for this same pair, found while porting round 3); round 4 covered Eats/Rideshare driver
   queues (deliveries/trip requests waiting), bike/parking nearby search, agent cash-till activity,
   and the Talk forward picker. Each rewrite says what's missing AND what will make it
   show up. Round 3 also surfaced two smaller, real, honest gaps left as-is rather than
   over-engineered: Commerce product reviews and the booking time-slot picker have no `EmptyState`
   call on iOS at all yet (a distinct, smaller platform-parity gap, not chased down this round).
   **Real bug caught while porting round 2**: bank-mfe's own "No requests received/
   sent yet"/"No listings posted yet" turned out to be inside `FloatMarketplaceSection` (agent
   cash-float trading), not the P2P payment-request feature the first draft assumed -- corrected
   before porting to Android/iOS, a reminder to verify the surrounding function/screen name before
   trusting a string's apparent meaning. **Honest partial scope**: ~165 total `EmptyState` call
   sites exist across bank-mfe alone -- the remaining ones (mostly lower-traffic admin/niche-feature
   screens) are a deliberately deferred follow-up, not a silent gap.
   *Shipped: `EmptyState`/`EmptyStateView` call-site copy in `BankDashboard.tsx`, `TalkScreen.kt`/
   `ShopScreen.kt`/`EatsScreen.kt`/`CardScreen.kt` (Android), `TalkScreen.swift`/`ShopScreen.swift`/
   `EatsScreen.swift`/`CardScreenView.swift` (iOS)*

2. **[sourced] Implemented on all 3 platforms, 2026-08-05.** Apply the same treatment to
   itunda's error states — Toss's documented friction-softening reasoning plus Baemin's
   real UX-writing register both point the same direction: itunda's `ErrorCard`/inline
   error rows were plain red text with a bare "Retry" link, unlike `EmptyState`'s own
   already-real icon-in-circle treatment right next to it in the same load-failure
   branches. Same shared icon-based visual language as item 1, not a second design
   language: `ErrorCard` (Android `core/designsystem`), `ErrorCardView` (iOS
   `Core/DesignSystem`), and bank-mfe's `ErrorCard` all now render a danger-tinted
   icon circle + centered message + a real button component, mirroring each
   platform's own `EmptyState` layout exactly. Merchantapp doesn't currently call
   `ErrorCard` anywhere, so there was no separate merchantapp-specific copy to fix.
   Live-verified on Android (a real network-error state triggered against an
   unreachable backend port on the review emulator); iOS/bank-mfe verified via
   swiftc -parse / `yarn build` only, not visually.

3. **[sourced] Implemented 2026-08-06.** Write itunda a short, real copy-voice guideline (5-10
   concrete before/after examples, not an abstract tone document) before or alongside item 1 --
   Baemin's own real evidence (a documented, named "배민다움" voice) is the strongest single proof
   point in this research that copy register is a legitimate, separate design lever from
   iconography. Written from item 1's own three shipped rounds rather than in a vacuum: three
   rules (say what's missing *and* what fixes it; be specific to the real surface, never a
   generic template; when the cause is someone else's, say so honestly), each demonstrated with a
   real before/after already shipped, plus the real Float-marketplace mixup from round 2 as a
   documented cautionary example.
   *Shipped: `docs/COPY_VOICE.md`*

4. **[sourced] Implemented 2026-08-05.** Per-surface badge/urgency-tag visual language, modeled
   on Coupang's real two-tier badge precedent (a badge tied to a *named, real* benefit tier, not a
   decorative label). **Real finding on inspection**: Android's Time Deal countdown/remaining-
   quantity already used a real shared `StatusBadge` component (`core/designsystem/components/
   HoodShared.kt`) -- a sibling-platform inconsistency, not a platform-wide gap. iOS and bank-mfe
   still rendered plain `Text`/`<p>`. Ported the same shape: `IdsBadge` (`Core/DesignSystem/
   Sources/Components/Components.swift`) for iOS, `Badge` (new `Badge.tsx`, independently in both
   bank-mfe and merchant-mfe per this repo's own per-micro-frontend component convention) for web.
   Applied to Time Deal on iOS `ShopScreen.swift` and bank-mfe. **`kybVerified` was a real,
   deeper gap**: never rendered anywhere in the Android or iOS merchant apps at all (not even as
   plain text) -- only merchant-mfe showed it, as plain colored text. Added a real "✓ Verified"
   badge next to the business name in `MerchantHomeScreen.kt`/`MerchantHomeScreen.swift`'s own
   header (Android/iOS) and replaced merchant-mfe's plain text with the new `Badge`. "Membership-
   day flags" turned out already correct on inspection -- bank-mfe's Membership Day banner is a
   real highlighted card (emoji + bold heading + tinted background/border), not plain text; no fix
   needed there. Verified: Android full clean recompile (`:app:compileDebugKotlin` +
   `:merchantapp:compileDebugKotlin`) zero-error; bank-mfe/merchant-mfe `tsc -b && vite build`
   both clean; iOS `swiftc -parse` clean (same syntax-only caveat as this session's other iOS
   work).

5. **[partially-sourced]** Consider itunda's own equivalent of a Tossface-style rule set for any
   future custom iconography/emoji work — a real, reusable checklist (uniform sizing, one palette,
   fixed viewing angles) if itunda ever commissions custom assets. Lower priority than items 1-4
   since it has no current call site.

### Unresolved / worth a follow-up

- Coupang's actual RDS token values (colors, type scale, spacing) are genuinely not public — the
  official engineering blog states this explicitly. Any future Coupang-informed visual work has to
  rely on direct app teardown/screenshot analysis, not documentation, and should be labeled
  `inferred` accordingly.
- The Karrot and Baemin and Kakao token tables above trace to oh-my-design.kr, a third-party site
  that states its own methodology as observing/reverse-engineering public product surfaces rather
  than publishing official specs — treat exact hex/spacing values from that source as
  `partially-sourced` (the *direction* is corroborated by official brand pages where checked, e.g.
  Kakao's yellow and Karrot's orange both independently confirm against official sources; the
  *precise* token values do not have an official citation).
- No official TDS icon naming convention or full size-grid specification was found beyond Toss's
  24-40px screen-use rule.
- Naver's app-wide UI beyond the green-dot search identity and the already-covered Maps section
  (Section 1) wasn't deep-dived this pass — a follow-up specifically into Naver Shopping/Naver Pay
  visual patterns would round this out, since Naver's e-commerce/payments surfaces are closer
  analogues to itunda's Shop/Bank surfaces than Naver Maps is.
- 당근이 (Danggeuni) and the Kakao Friends characters are real, IP-protected mascots — nothing in
  this document recommends itunda copy or resemble them; the actionable lesson is the *design
  principle* (personality-bearing graphics at low-content moments), not the specific characters.

---

## 10. Copy voice — a real position, not an abstract tone document

**Added 2026-08-04**, closing Section 9's own recommendation #3. Baemin's real, named "배민다움"
voice is the single strongest piece of evidence in this whole research pass that copy register is
a legitimate, separate design lever from iconography/color — a food-delivery app with genuinely
funny, warm push notifications and in-app copy, not a coincidence but a deliberate, staffed
discipline (Woowa Brothers is documented as having dedicated copywriters for exactly this). itunda
currently has **no stated position on voice at all**: every string audited this session was
functionally correct and personality-neutral (`"No listings yet."`, `"Couldn't reach itunda. Check
your connection and try again."`). This section is deliberately short and concrete — a real
before/after list, not an abstract tone essay nobody will apply.

### itunda's voice, stated plainly

Not Baemin's register (Baemin's specific jokes are a Korean food-delivery brand's own IP, not a
transferable "be funny" instruction, and itunda is a financial super-app, a genuinely different
trust context where a wrong joke lands badly). The actual, applicable lesson: **be specific and
warm instead of generic and flat.** Concretely:

1. **Name the next action, don't just report the absence.** "No X yet." describes a void; "No X
   yet — [do this] to get started" points somewhere. This is the single change applied throughout
   this session's own EmptyState conversion pass (Sections above) — e.g. `"No saved contacts yet."`
   → `"No saved contacts yet — add one above to send faster next time."`
2. **Use real second person, not passive description.** "Enter a name and a real price." reads as
   a rule the system is stating; "Enter a name and a price to add this product." reads as itunda
   talking to the one specific person about the one specific thing they're doing right now.
3. **Prefer a plain word to a formal one where both are equally clear.** "Insufficient funds" vs.
   "You don't have enough in your wallet for this" — itunda already does this well in several
   places (`"Couldn't reach itunda."` rather than "A network error has occurred"); the inconsistency
   is the actual problem, not the absence of a rule.

### Before / after (real strings, not invented examples)

| Before (found in the codebase) | After | Why |
|---|---|---|
| `"No requests yet."` | `"No requests yet — ask someone to pay you above."` | Names the next action |
| `"No coupons yet."` | `"No coupons yet — create one above to give repeat customers a reason to come back."` | Names the action AND the reason it matters to a merchant specifically |
| `"No employees on the roster yet."` | `"No employees on the roster yet — add one above to start running payroll."` | Connects the empty state to the feature it's blocking |
| `"Enter a name and a real price."` | *(kept as-is — already second person and specific; a genuine example of itunda's voice already working)* | Shows the bar other strings should meet, not just what to change |
| `"Couldn't reach itunda. Check your connection and try again."` | *(kept as-is)* | Already itunda's best error-copy example — plain words, real second person, no jargon |
| `"IDEMPOTENCY_KEY_REQUIRED"`-style raw error codes ever reaching a user-facing surface | Always resolve through `superAppErrorMessage()`/the platform-equivalent human-message mapper | Machine codes are never voice — an easy, mechanical rule, not a judgment call |

### Recommendations (ranked)

1. **[sourced principle, itunda-specific application]** Apply the three rules above during any
   future copy pass — this session's own EmptyState conversion (Sections 1-9 above) already is
   the first real application, not a hypothetical. No new component or backend change needed;
   this is a writing discipline, not an engineering task.
2. **[inferred]** If itunda ever staffs a dedicated copywriter role (Baemin's real precedent for
   why this pays off at scale), that's the point to build a real, versioned copy-voice reference
   doc with more than 6 examples — premature today given itunda has no one dedicated to this yet.

### Unresolved / worth a follow-up

- This section deliberately does NOT attempt to define a "brand personality" in the abstract
  (playful/serious/etc.) — Baemin's own real lesson is that voice work is concrete copy decisions,
  not a mood board, and an abstract personality statement with no examples is exactly the kind of
  artifact that gets written once and never applied. Any future expansion of this section should
  stay in the same before/after format.
- Push-notification copy (a real, separate surface with its own real precedent in Baemin's
  research) wasn't audited this pass — itunda's notification copy across Android/iOS wasn't
  reviewed for this same specific/generic distinction.

---

## 11. Simplicity & convenience — Toss's own product principles, applied app-wide

**Added 2026-08-07** (item 244/245), at explicit user request to do deep research on why Toss
is known for simplicity specifically, then apply it broadly across itunda rather than to one
feature. Section 8 already named Toss's 6 official product principles and deep-dived 2 of them
("One Thing", "Easy to Answer") — this section deep-dives the remaining 4 and adds a genuinely
new, highly concrete source (the Apps-in-Toss developer UX/dark-pattern guide) that wasn't found
in any earlier research pass.

### References

| Topic | Source | Pattern |
|---|---|---|
| Toss product principles (official, full text) | toss.im/tossfeed/article/tossproductprinciples | **Clear Action**: "Is the required action to achieve the desired result obvious at a glance? Without reading a word, users should know exactly what to do next." **No More Loading**: "Have we completely removed waiting for users? ... Pull all levers to eliminate delays -- whether by redesigning flows, improving policies, or adopting new technology." **Minimum Features**: "More features in a product mean more complexity, slower updates, and more bugs. Adding a new feature often brings more cost than value, and should be a last resort." **Value First**: "It's our job to show users that our product is worth their time and money. By clearly communicating its benefits before asking them to act, we guide users seamlessly to complete the task at hand." |
| Four ways to eliminate unnecessary clicks | toss.tech/article/4-ways-for-minimum-input | Four real, named, shipped techniques: (1) omit the CTA button entirely for single-selection input, (2) omit the CTA for fixed-digit-length fields (ID numbers, phone numbers, OTP codes) -- the field submits itself once full, (3) pre-select the correct keyboard type (numeric for verification codes) rather than defaulting to text, (4) auto-focus the input on page load so the keyboard appears without an extra tap |
| When to add an interaction/animation | toss.tech/article/interaction | Real decision framework, not aesthetic preference: an interaction only ships if it moves a *measurable* metric (conversion, drop-off, task completion) or gives clearer functional feedback about what's happening/available -- not because it "looks better." Named a real failure case: a full sidebar, ID-recognition screen, and card-issuance-flow animation set were all built and *rejected* despite positively-perceived UX, because none moved a metric. Named a real success case: a loan-approval loading screen showing the real product in real time raised engagement. |
| Apps-in-Toss dark-pattern-prevention policy | developers-apps-in-toss.toss.im/design/consumer-ux-guide | Real, enforced (not aspirational) submission-review rules: no bottom sheet/ad/notification-prompt blocking the very first screen a user sees; no bottom sheet blocking backward navigation; no UI structure where the only way forward is the partner's preferred CTA (an escape/other option must always exist); no full-screen ads appearing unexpectedly mid-task; CTA button labels must state the specific outcome, never repeat the screen's own heading or use a generic label |
| Apps-in-Toss UX writing rules | developers-apps-in-toss.toss.im/design/consumer-ux-guide | Positive framing preferred over negative ("이 혜택을 받을 수 있어요" [you can get this benefit] over "이 혜택이 없어요" [this benefit doesn't exist]); dialog dismiss buttons use "닫기" (Close) not "취소" (Cancel) -- the label names the actual resulting action, not a generic verb |

### itunda's current state (verified, not assumed)

Audited against the concretely-checkable rules above (the Korean-grammar-specific writing rules
don't translate to itunda's English copy, so scoped out; the universally-applicable ones did):

- **Fixed-length-field CTA elimination (rule #2 above): real, confirmed violation, now fixed.**
  `VerificationRow` (Android `ItundaAppScreen.kt`, iOS `BenefitsShopAllScreens.swift`, web
  `BankDashboard.tsx`) required typing a real, fixed 6-digit OTP (`AuthService.kt`'s own doc
  comment confirms the fixed length) *and* a separate manual "Confirm" tap -- exactly the
  anti-pattern this research names. See item 245 below.
- **Keyboard-type pre-selection (rule #3): same screen, same violation, now fixed.** The code
  field defaulted to a text keyboard on all 3 platforms instead of numeric.
- **Auto-focus (rule #4): fixed on web only.** Android's `IdsTextField` and no SwiftUI
  `@FocusState` wiring exists anywhere in this codebase for this field -- adding it means a
  shared-component API change (`IdsTextField` has no focus-requester parameter at all today),
  a real risk to every other call site if done without dedicated verification time. Flagged as
  a real, scoped-out opportunity below, not silently skipped.
- **Dark-pattern check (entry bottom sheets / exit-blocking popups / forced-only CTAs): spot-checked, not exhaustive.** No systematic sweep was run this pass (would require reading every modal/bottom-sheet mount condition across ~40+ call sites); a full sweep is a real, valuable follow-up, not yet done.
- **CTA label clarity (buttons must state outcome, not repeat the heading): not audited this pass.** A real candidate for the next simplicity-focused round -- cross-reference every generic "Confirm"/"Submit"/"OK" button label against what it actually does.

### Recommendations (ranked)

1. **[sourced, implemented same day, item 245]** Auto-confirm the verification-code field the
   instant a valid 6-digit value is entered, keeping the Confirm button as a manual fallback
   (not removing it outright, since this is a security-sensitive identity-verification step,
   not an ordinary form) -- exactly Toss's own researched, shipped pattern. Fixed on all 3
   platforms. Added numeric-keyboard hints alongside it (rule #3, same research).
   **Deliberately not extended further, checked and confirmed same day**: bank-mfe's other
   code-entry fields (P2P "Pay a request code", agent "Customer's withdrawal code", merchant
   "Payment code") all either have no confirmed fixed length (unlike the OTP's
   backend-guaranteed always-6-digits) or directly trigger real money movement with no
   separate preview/confirm step in the common case (`PayByCodeCard`'s own `handleSubmit`
   calls `payDirect()` immediately whenever no coupon applies) -- auto-submitting any of these
   on code-length-reached would risk firing a real payment before the user intends to. The
   explicit tap on these fields is a deliberate safety gate, not removable friction; applying
   rule #2 here would have been a misapplication of the research, not a genuine simplification.
2. **[sourced] Done same day.** Added real focus-management support to `IdsTextField` on both
   Android (`autoFocus: Boolean = false` param, `FocusRequester` + `LaunchedEffect(Unit) {
   requestFocus() }`, gated behind the new param so all ~35 existing call sites keep their
   current behavior unchanged -- verified via a clean compile of `:app`, `:core:designsystem`,
   `:merchantapp`, `:riderapp`, `:agentapp`) and iOS's shared `IdsTextField` component
   (`autoFocus: Bool = false`, `@FocusState` + `.onAppear`). Wired both to the verification-code
   field item 245 already fixed, matching web's autoFocus. iOS's `VerificationRow` (in
   `BenefitsShopAllScreens.swift`) uses a raw `TextField`, not the shared component, so it got
   its own local `@FocusState` + `.onAppear` instead of the new shared param.
   **Extended, same day**: auto-focus applied to the first field on itunda's actual
   highest-traffic screen -- login -- which had none anywhere on web (`bank-mfe`/
   `merchant-mfe`/`ops-mfe`'s `LoginPage.tsx`, plus `RegisterPage.tsx`/`RegisterScreen.tsx`)
   and on iOS (`LoginScreen.swift` had zero prior focus handling at all). Found and fixed a
   real latent bug in this recommendation's own Android addition while doing so: `LoginScreen.kt`
   already had a working `rememberAutoFocus` helper with a documented real fix ("requesting
   focus in the same frame a composable enters can silently no-op if the node hasn't attached
   yet" -- wait one frame first); the new `IdsTextField.autoFocus` used a bare
   `LaunchedEffect(Unit)` with no delay, reintroducing that exact bug. Fixed with the same
   `delay(80)` pattern before it shipped further.
3. **[sourced] Done same day -- clean result, not a skipped check.** Ran the systematic
   dark-pattern sweep against all 3 checkable Apps-in-Toss rules, across all 3 platforms:
   - **Auto-shown-on-entry** (a modal/sheet visible without a real user action triggering
     it): grepped every `useState(true)`/`mutableStateOf(true)`/`@State ... = true` and every
     mount-time effect (`useEffect(() => ..., [])`, `LaunchedEffect(Unit)`, `.onAppear`)
     setting a show/open/modal/sheet/dialog/prompt flag true, across web, Android, and iOS.
     Zero genuine hits. One real candidate found and ruled out on inspection:
     `ItundaAppScreen.kt`'s `showMap = true` inside `LaunchedEffect(openMapFromDeepLink)` is a
     real deep-link handoff (the user already took the triggering action elsewhere), not an
     unsolicited interstitial.
   - **Back-navigation-blocking**: grepped all 122 Android `BackHandler` call sites for any
     that shows a dialog instead of actually navigating back -- zero hits (the ones that exist
     unwind one step of a real multi-step Flow, matching Section 8's own documented
     `TransferStep` precedent, not a block). Checked web for `beforeunload`/history-blocking --
     zero hits. Checked iOS for `.interactiveDismissDisabled()` -- zero hits anywhere in the
     app, meaning every `.sheet()` (60 across `ios/App/Sources`) keeps its default
     swipe-to-dismiss escape route intact structurally, no need to check all 60 individually.
   - **Forced-CTA-only dialogs** (no real escape option): checked all 4 Android `AlertDialog`s
     individually -- all 4 have a real `dismissButton`/tap-outside `onDismissRequest`. Checked
     all 4 web full-screen modal backdrops in bank-mfe -- all 4 have `onClick={onClose}` on the
     backdrop itself (real click-outside-to-close).

   **Conclusion: itunda has no dark patterns in any of these 3 checkable categories today** --
   a genuinely verified negative result across every platform, not an unswept gap.
4. **[sourced] Done same day.** Ran the CTA-label-clarity pass: grepped every generic-verb
   button ("Confirm"/"Submit"/"Continue") across every web micro-frontend plus Android/iOS.
   Found and fixed 7 real instances: bank-mfe's transfer-confirm modal ("Confirm" →
   "Send {amount} RWF"), Mini Account eligibility form ("Continue" → "Check eligibility"),
   property ownership-doc upload ("Submit" → "Submit for review" — starts a real
   human-reviewer process, not an instant action); merchant-mfe's KYB TIN form ("Submit" →
   "Submit for verification"); ops-mfe's agent till-funding row ("Confirm" → "Fund till");
   Android/iOS's identical MiniWalletScreen "Continue" → "Check eligibility". Left alone:
   `VerificationRow`'s OTP-confirm "Confirm" (item 245's own auto-confirm widget) — its
   surrounding context (code input directly under "Email/Phone not verified") already makes
   the outcome obvious, matching this rule's own stated exception. `:app:compileDebugKotlin`
   clean, `swiftc -parse` clean, all 3 web packages `tsc -b` + `vite build` clean.
5. **[sourced, real opportunity, larger scope]** The interaction decision framework (measurable
   metric or clearer functional feedback, not aesthetic preference) is a real, adoptable review
   question for any future itunda animation/motion work — itunda has no equivalent stated
   principle today (Section 9's motion research covers *when* animation softens friction
   emotionally, a related but distinct question from *whether* a specific animation is worth
   building at all).

### Unresolved / worth a follow-up

- The Korean-specific UX-writing grammar rules (honorific-dropping, "됐어요" vs "되어요", etc.)
  don't transfer to itunda's English copy at all — noted for completeness, not applicability.
- "Minimum Policy" (a named sub-principle under Minimum Features, per secondary search results)
  and "Minimum Input" as officially-named categories weren't found at a single canonical source
  URL the way the 6 headline principles were — the specific article content was reconstructed
  from the "4 ways to eliminate clicks" piece and search-result summaries, not one definitive
  page. Flagged as lower-confidence than the rest of this section.

---

## 12. Security *and* simplicity together — how Toss does both, not one at the cost of the other

**Added 2026-08-07**, at explicit user request: research specifically how Toss achieves real
security without the friction-heavy tradeoffs common elsewhere (the user's own framing: "toss
archived security and simplicity at the same time unlike apple which focuses security and leave
out simplicity"), then cross-check itunda's own real security code against it.

### References

| Topic | Source | Pattern |
|---|---|---|
| Toss's own stated position | support.toss.im/security | Explicit, quotable: *"간편함과 안전은 더 이상 양립 불가능한 말이 아닙니다"* ("convenience and safety are no longer mutually exclusive words") — not an implicit design choice but a stated philosophy, credited to "전담 인력과 자체 기술" (dedicated personnel and proprietary technology, i.e. real investment, not a shortcut) |
| Real-time, invisible fraud detection (FDS) | support.toss.im/security; docs.tosspayments.com/resources/glossary/fds; multiple Korean fintech-press summaries | A proprietary, supervised-learning ML system performs real-time fraud-score inference on **every single transfer**, 24/7, with zero user-facing friction unless a transaction is actually flagged — blocked ~310,000 fraudulent transactions in 2022 alone (roughly one every 2 minutes). Security work happens entirely in the background; the user only ever sees it when it actually catches something |
| Pre-transaction recipient screening | support.toss.im/security | A real, police-partnered fraud-account lookup runs automatically before a transfer completes, "usable without additional setup" (자동, no separate opt-in step) — security as a default behavior, not an extra step the user must remember to take |
| New-device notification, not silent block | search-result summaries of support.toss.im FAQ (직접 fetch returned empty — JS-rendered page) | A new device can still log in and browse; Toss sends an immediate "logged in from a new device" alert rather than blocking access outright — friction is proportional to actual risk (browsing is low-risk, moving money is not), not applied uniformly |
| Passwordless-first credential (already documented, Section 8) | toss.im/tossfeed/article/toss-overseas-identity-verification | 6-digit PIN or Face ID as the real login credential, not a conventional alphanumeric password — security through a faster, harder-to-phish factor, not a longer one |

### itunda's current state (verified against real code, not assumed)

itunda already has a genuinely close match to this whole philosophy, not a gap needing invention:

- **`DeviceService.kt`** (`services/backend/auth/src/main/kotlin/rw/itunda/auth/`) already
  implements the exact same shape: `recordLoginDevice` runs on every login with zero user-facing
  friction, fires a real push notification the instant an unrecognized device signs in
  (`sendNewDevicePushAfterCommit`, same urgency `FraudReviewService`'s confirmed-fraud alert
  already uses), and — critically — **does not block the login itself**. The new device can sign
  in and look around immediately; only money-moving calls are gated behind `DeviceVerificationFilter`
  until the device proves itself. This is a real, working match to Toss's own "friction
  proportional to risk" pattern, not browsing-blocked-by-default.
- Self-service device management (`getMyDevices`/`revokeDevice`) already exists, matching Toss's
  own security-settings self-service pattern.
- **A real gap, correctly not shortcut-fixed**: device *verification* (`verifyDevice`) requires
  typing a password on all 3 platforms — no biometric alternative, even though itunda's own
  `NIDABiometricAuth` primitive already exists and is used for the *transaction*-confirm gate
  (a different, lower-stakes check: proving "it's still you pressing send" on an already-
  authenticated session, not proving device trust to the server). Checked whether to wire
  biometric into device verification too, and did not: `NIDABiometricAuth`'s own doc comment
  already honestly documents why not — it's a **local-only** biometric check ("Not implemented:
  binding this prompt to a CryptoObject and the server-side ... verification call ... Local
  biometric success only, honestly labeled above"). A local biometric success can't itself prove
  anything to the server; Toss's own real architecture for this (토스인증서, Toss Certificate)
  is a genuine public-key-cryptography system — a Keystore-bound private key that *signs* a
  server challenge, not just a local gate. Building the itunda equivalent safely means new
  server-side infrastructure (public-key registration + challenge-response verification), not a
  client-only UI change. Implementing a shortcut version (treating local biometric success alone
  as sufficient to mark a device server-trusted) would be a real security regression — exactly
  the "fake success" pattern `NIDABiometricAuth`'s own header comment already documents finding
  and fixing once in this exact file's history. **Correctly left undone, not silently skipped.**
- **A real, small, safe win, found and fixed same day**: the device step-up dialog's password
  field (the *sole* meaningful action on that entire screen) had no auto-focus anywhere —
  Android's `DeviceStepUpDialog`, iOS's `DeviceStepUpView`, and both web copies (bank-mfe,
  merchant-mfe). Fixed on all 4, matching Section 11's own already-established `delay(80)`
  timing-fix convention. This is the correct kind of security+simplicity work — a real UX
  improvement to an existing, sound security flow, not a shortcut around it.

### Recommendations (ranked)

1. **[sourced, implemented same day]** Auto-focus the device step-up password field — done, see
   above.
2. **[sourced, real opportunity, larger scope, not a quick fix]** Build real Keystore-signed-
   challenge device verification (itunda's own equivalent of 토스인증서), replacing password
   re-entry with a cryptographically real biometric-backed proof the server can actually verify.
   Requires: a server-side public-key registration endpoint, a challenge-issuance endpoint, and
   Android Keystore/iOS Secure Enclave key-generation-and-signing on the client — a genuine new
   security feature spanning backend + all 3 clients, not a UI change. Flagged as the single
   highest-value next step in this whole security-simplicity thread, sized honestly as multi-
   session work.
3. **[sourced, itunda already matches, validated not invented]** itunda's `DeviceService.kt`
   already implements Toss's core "friction proportional to risk" pattern correctly (browse
   freely, gate only money movement) — recorded here as a validated existing strength, per this
   document's own established practice of noting genuine matches, not just gaps.

### Unresolved / worth a follow-up

- Whether itunda's `FraudReviewService`/scam-detection work (`ScamReportService.kt`, cited
  elsewhere in this document) runs on every transfer in real time the way Toss's FDS does, or
  only on explicitly-reported accounts, wasn't verified this pass — a real, valuable next
  research question given how central real-time invisible monitoring is to Toss's own stated
  approach.
- The direct fetch of Toss's own "new device login" FAQ page returned empty (JS-rendered
  support widget, not fetchable via a plain HTTP GET) — the notification-not-block conclusion
  above rests on search-result summaries, not a direct primary-source read. Lower confidence
  than the rest of this section; worth a follow-up fetch via a different method if this becomes
  load-bearing for a future decision.
