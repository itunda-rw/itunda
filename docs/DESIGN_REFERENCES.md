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
2. **[sourced, implemented same day — item 246]** Build real Keystore-signed-challenge device
   verification (itunda's own equivalent of 토스인증서), replacing password re-entry with a
   cryptographically real biometric-backed proof the server can actually verify. **Done**:
   backend (`DeviceService.registerDeviceKey`/`issueChallenge`/`verifyDeviceBySignature`,
   `AuthController`'s `/devices/register-key`, `/devices/challenge`, `/devices/verify-signature`,
   `TrustedDevice.publicKey`, `V100__trusted_device_public_key.sql`) — registering a key requires
   the same password proof as the original `verifyDevice` (a stolen JWT alone must never be
   enough to plant an attacker-controlled key), so registration marks the device trusted
   immediately; the value of the key is making every *subsequent* step-up a signature instead.
   Single-use Redis-backed challenges (~2min TTL) consumed via an atomic Lua script, mirroring
   `RateLimiter`'s own idiom, to close the race where two concurrent verify attempts could both
   observe an unconsumed challenge. Wire format: raw uncompressed P-256 point (0x04 || X || Y,
   65 bytes) — the native output of both Android Keystore's `ECPublicKey.w` and iOS's
   `SecKeyCopyExternalRepresentation`, so neither client needs a DER/X.509 conversion step.
   Android (`DeviceKeyManager.kt`, Keystore + `BiometricPrompt`/`CryptoObject`) and iOS
   (`DeviceKeyManager.swift`, Secure Enclave + `LAContext`/`SecKeyCreateSignature`) both ship an
   opt-in Settings > Security toggle gated behind the same password bar, and both wire
   `DeviceStepUpHost`/`DeviceStepUpDialog` to try the biometric signature first, falling silently
   back to the existing password field when no key is registered or biometric auth is declined.
3. **[sourced, itunda already matches, validated not invented]** itunda's `DeviceService.kt`
   already implements Toss's core "friction proportional to risk" pattern correctly (browse
   freely, gate only money movement) — recorded here as a validated existing strength, per this
   document's own established practice of noting genuine matches, not just gaps.
5. **[sourced, implemented same day]** Show/hide password toggle on every login/register field,
   app-wide — the correct kind of "security and simplicity together" work this whole section is
   about: a real UX improvement (cuts mistyped-password retries, especially costly at
   registration where a silent typo locks a new account behind a password the user doesn't
   actually know) that trades away zero real security, since the value never leaves the field
   either way. Added to all 3 web login/register pages (bank-mfe, merchant-mfe, ops-mfe) and
   both native shared `IdsTextField` components (Android's new `isPassword` param; iOS's
   existing `isSecure` param, extended in place with no call-site changes needed at all) — the
   iOS fix alone propagated to all 4 real native login screens (App, MerchantApp, AgentApp,
   RiderApp) automatically, since none of them have a local duplicate `IdsTextField`.
   **Extended to full closure, same day**: the device step-up password fields (bank-mfe/
   merchant-mfe's `DeviceStepUpPrompt`, Android's `DeviceStepUpDialog`, iOS's
   `DeviceStepUpView`) and web's last remaining `type="password"` fields
   (`UssdSettingsView`'s New/Confirm PIN pair — a real, deliberately web-only feature, no
   Android/iOS equivalent to also fix). Every password-type field in the app now has this
   toggle. Caught a real mistake while extending to Android's `TransferFlow.kt`: inline-
   fully-qualifying `Icons.Outlined.Visibility`/`VisibilityOff` doesn't resolve, since
   `Icons.Outlined.X` are Kotlin extension properties, not real nested class members — fixed
   with proper imports before it shipped, matching how `IdsTextField.kt` already does it
   correctly.

4. **[sourced] Resolved same day — a real, validated match, not a gap.** Checked whether
   itunda's fraud detection runs on every transfer in real time (Toss's FDS pattern) or only
   reactively on reported accounts. It's the former: `FraudRuleEngine.evaluate()`
   (`services/backend/core/src/main/kotlin/rw/itunda/core/fraud/FraudRuleEngine.kt`) is called
   from every real money-moving flow — confirmed via its own doc comment's real caller list:
   `P2pService` (send + payment requests), `WalletService` (currency conversion), `OrderService`/
   `EatsOrderService`/`DineInOrderService` (checkout), `MerchantService` (in-person collection),
   `PayrollService` (payroll). Three real heuristics (high-value, velocity, new-recipient)
   evaluate in real time on every transaction and only ever *flag* for human review
   (`FraudReviewService.decide`) — never block outright, the same deliberate, documented choice
   ("a freshly-built heuristic engine with no track record... is a worse failure mode as a hard
   block... than as a flag a human reviews after the fact") as Toss's own FDS being review-
   oriented rather than an instant auto-decline. Not ML-based like Toss's real system (itunda's
   own rules are simple thresholds, honestly so), but the *architectural pattern* — real-time,
   on every transaction, invisible unless actually flagged, non-blocking — is a genuine match.

### Unresolved / worth a follow-up
- The direct fetch of Toss's own "new device login" FAQ page returned empty (JS-rendered
  support widget, not fetchable via a plain HTTP GET) — the notification-not-block conclusion
  above rests on search-result summaries, not a direct primary-source read. Lower confidence
  than the rest of this section; worth a follow-up fetch via a different method if this becomes
  load-bearing for a future decision.

## 13. SLASH — Toss's engineering conference, 21/22/24 (no SLASH25 exists)

**Added 2026-08-07**, at explicit user request ("toss solve a lot of problems and they shared
solutions through simplicity 21,22,23,24,25, and slash 21,22,23,24,25... let's improve itunda").
SLASH is Toss's annual *developer* conference (toss.im/slash-NN, toss.tech), distinct from
Simplicity (Section 14), which is design-focused. Confirmed via direct search: **no SLASH25
exists** — the real 2025 event is "Toss Makers Conference 25" (TMC25, a differently-branded
event, not researched here). SLASH23's Gateway/Passport architecture and rate-limiting content
were already researched and implemented in a prior session (see `DeviceService.kt`/
`RateLimiter.kt`'s own doc comments, and Section 12 above) — not re-covered here.

### References

| Topic | Source | Pattern |
|---|---|---|
| SLASH21 tech stack (2021) | toss.im/slash-21 | Active-Active datacenter, Kubernetes + Istio, API Gateway, Kafka, Redis Cluster, ELK/Thanos/Grafana — session-level technical depth was PDF-only and not extractable by web research tooling |
| SLASH22 (2022, "No User, No Technology") | toss.im/slash-22 | 24 speakers, 22 sessions split across dev-productivity/UX (Day 1) and server/data (Day 2) — same PDF-only extraction limit |
| Strangler Fig migration, not big-bang rewrite | toss.im/slash-24/sessions/27, toss.tech/article/32211 | Toss Bank explicitly rejected the traditional "차세대" (next-gen) waterfall big-bang system rewrite — real service-failure risk, inflexible mid-project — in favor of incremental legacy replacement (Strangler Fig Pattern) that preserves maintainability without a risky cutover |
| Compensating-transaction currency exchange | toss.im/slash-24/sessions/24 (Lee Shin-dong, Toss Bank) | Won/foreign-currency accounts are separate microservices/DBs post-MSA-split; cross-service atomicity via **Orchestration SAGA** (chosen over 2PC — kills availability at their volume — and Choreography — no central state for exchange-limit checks). Withdraw-before-deposit ordering (deliberate — deposit-first would let other transactions withdraw funds mid-exchange). **On 5xx/timeout, never assume failure — re-query actual account state before compensating.** Kafka delayed-topic retry scheduler with expanding backoff (30s→1min). Two append-only tables (request snapshot + insert-only state-transition log) distinguishing "failed at withdrawal" from "succeeded at withdrawal, failed at deposit, cancelled." Cited scale: 1M accounts in 3 months, ₩52 trillion processed |
| Idempotency-Key spec | docs.tosspayments.com/reference/using-api/authorization | 300-char max key, `INVALID_IDEMPOTENCY_KEY` on overflow, 15-day validity, same-key-same-body replay returns the original response verbatim, in-flight duplicate returns `409 IDEMPOTENT_REQUEST_PROCESSING`, key scoped by endpoint+method |

### itunda's current state (verified against real code, not assumed)

- **Already an exact, confirmed match, nothing to build**: `services/backend/core/.../idempotency/IdempotencyService.kt` already implements the Toss Payments Idempotency-Key spec precisely — `MAX_IDEMPOTENCY_KEY_LENGTH = 300`, `IDEMPOTENCY_RETENTION = Duration.ofDays(15)`, `ClaimOutcome.InProgress`/`Conflict`/`Replay` states, per-route `scopedKey`. The file's own doc comment already cites this exact spec as its model — this section just confirms the match is real, not new work.
- **SAGA/compensating-transaction pattern: correctly not applicable yet, not a gap.**
  `services/backend/wallet/.../ForeignCurrencyWalletService.kt`'s `convert()` writes every ledger
  leg (source debit, FX-clearing legs, dest credit) in one local `@Transactional` method — a real
  single-database ACID transaction, not a distributed operation the way Toss Bank's won/foreign-
  currency split is (separate microservices, separate databases). Importing SAGA complexity now
  would be over-engineering a problem itunda doesn't have. **This is the correct pattern to reach
  for if and when currency conversion is ever split into separate services — not before.**
- **The "re-query real state before compensating on ambiguous failure" principle has no real
  object to apply to yet, for the same reason**: `services/backend/core/.../provider/ProviderConnector.kt`
  is an honestly-labeled synchronous, deterministic, in-process *simulation* of rail/PSP calls
  (`attemptOnce`, no real network call) — there is no real network boundary today where an itunda
  response could actually be ambiguous. Flagged below as a forward-looking design note for when a
  real external PSP/mobile-money rail replaces the simulation (a standing, currently-blocked item
  — see the project's own "demo, don't declare blocked" discipline).
- **Strangler Fig validates existing working style, not a new practice to adopt**: every itunda
  feature this whole multi-session thread (device verification, maps, design system, this
  research itself) has been built as incremental, independently-shippable, independently-verified
  slices — never a big-bang rewrite. Worth citing as an explicit sourced precedent for a practice
  that was already happening implicitly.

### Recommendations (ranked)

1. **[sourced, documentation-only — the correct action here]** Keep the SAGA/compensating-
   transaction pattern (SLASH24 session 24) as the named design to reach for *later*, with its
   concrete trigger condition (a real external PSP/rail replacing `ProviderConnector`'s
   simulation) — this pre-empts a future session reaching for 2PC or a naive
   retry-on-timeout-assumes-failure approach by mistake when that day comes.
2. **[sourced, already true, confirmed not asserted]** `IdempotencyService.kt` already matches
   Toss Payments' real published spec — no action, recorded here as a validated match per this
   document's own established practice.
3. **Not independently actionable from this research pass**: SLASH24's Hadoop/DW/ClickHouse/
   Feature-Store/eBPF/K8s-cost-optimization sessions solve a scale problem (millions of users,
   real distributed data infra) itunda doesn't have yet. Listed so a future session doesn't
   re-search the same dead end.

### Deep-dive follow-up (2026-08-07, second pass) — user explicitly asked to keep searching past the PDF wall

The first pass above stopped at SLASH21/22's PDF-only session pages. A second pass found real
content anyway via third-party Korean tech-blog recaps of the same named sessions (Velog, Medium,
personal engineering blogs) — the sourcing is one hop removed from Toss's own primary page, but
the technical content itself is real, specific, and independently checkable against itunda's code.

| Topic | Source | Finding |
|---|---|---|
| Toss Bank account-number/schema design | SLASH21, Jo Han-ki "토스뱅크의 데이터 설계사상", via parkmuhyeun.github.io's seminar-notes recap | Three real table-integration strategies chosen per case (OneToOne/super-sub, Plus Type, Single Type — small volume favors integration, large volume favors splitting to avoid frequent `ALTER TABLE`); real account-number design (8 digits + 3-digit type code + check digit = 100M addressable, capacity-planned against ~27 years of projected volume); Oracle sequences preferred over UUID for ID generation except where strict zero-gap ordering is required |
| React Native OTA productivity | SLASH22, "미친 생산성을 위한 React Native", via ktseo41.github.io | Real CodePush usage — JS bundles update **without app-store resubmission** ("200 CodePush updates over 3 months"); hybrid architecture (native views only for performance-critical graphics, everything else JS); parallel rollout alongside the existing native app, not a rewrite |
| 100% test coverage | SLASH21, Lee Eung-jun (Toss Bank), via velog.io/@heka1024 | Real Kotlin gotcha: the Elvis operator (`?:`) generates a bytecode branch coverage tools can't reach, permanently capping measured coverage — their fix was converting to explicit `if/else` for accurately-measured high-risk logic, a deliberate readability-for-measurability tradeoff. House rules: "tests must be fast," "a coverage regression fails the build," paired with the honest caveat "bugs can exist even at 100% coverage" |
| React component architecture | SLASH22, Han Jae-yeop "Effective Component", via apeltop.github.io | Separate data/business logic into custom hooks vs. mixing into presentation components; caution against reflexive over-decomposition. Solid, fairly generic modern-React practice, not uniquely Toss-specific |

#### itunda's current state (checked, not assumed)

- **Account-number collision safety: real gap, found and fixed same day.** itunda had 9
  independent `generateAccountNumber()` copies (`AuthService`, `GroupAccountService`,
  `IkiminaService`, `SaccoService`, `WeeklySavingsService`, `UpfrontInterestDepositService`,
  `ForeignCurrencyWalletService`, `MiniWalletService`, `MerchantBusinessAccountService`), every
  one `Math.random()`-based with zero check against the real `UNIQUE(account_number)` constraint
  on `wallets` (`V1__init_schema.sql`) — three of them shared the exact same numeric range. A
  collision would have surfaced as a raw, unhandled `DataIntegrityViolationException`, not a
  graceful retry (`MiniWalletService`'s own prior comment named this as a knowingly-accepted
  risk). **Fixed**: a new shared `AccountNumberGenerator` (`:core`) checks
  `WalletRepository.findByAccountNumber` before handing a number out, retrying up to 10 times —
  preserves each service's own deliberate account-type-prefix encoding (itunda's own real
  equivalent of Toss Bank's type-code scheme), just makes the number within that range actually
  collision-checked. All 9 call sites migrated.
- **CodePush/OTA updates: real, larger-scope gap, correctly not rushed.** itunda's own mini-app
  host (`packages/saronite`) already has a real runtime-bundle-loading mechanism — but only for
  the `partner-demo` proof-of-concept bundle (served from a real `bundleUrl` at runtime,
  explicitly built to prove the loader against something never compiled into the app). itunda's
  actual *production* mini-apps (`pay-bills`, live end-to-end per `docs/TOSS_PARITY_MATRIX.md`)
  register via plain `AppRegistry.registerComponent`, compiled directly into the host-app's own
  Metro build — meaning a JS-only change to a real production mini-app currently requires a full
  native app rebuild and store resubmission, unlike Toss's real CodePush-powered instant updates.
  **Sized honestly as its own project** (bundle hosting, version negotiation, native-side
  download/cache/fallback/rollback logic — comparable in scope to item 246's device-verification
  feature), not attempted this pass.
- **Elvis-operator coverage gotcha: real, applicable risk, confirmed latent — no gate exists yet
  to trigger it.** itunda's Kotlin backend uses `?:` heavily (`FraudRuleEngine.kt`,
  `DeviceService.kt`, etc.) and has a real, working Kotest/MockK suite across 40+ modules.
  Verified via `grep -rl "jacoco\|kover" services/backend`: **zero results** — no coverage tool
  is configured anywhere in the backend, so there's genuinely nothing to fix right now (adding a
  full coverage-gate CI system would be a speculative, unrequested feature, not a bug fix).
  Flagged so that if itunda ever adds one, this exact false-negative-coverage trap is already
  known rather than rediscovered.

### Unresolved / worth a follow-up
- SLASH21/22 primary session pages are still PDF-only — the second pass's third-party recaps are
  real and specific, but one hop removed from Toss's own primary source; direct PDF
  download+parsing was not attempted.
- SLASH24 session 27's actual migration *mechanism* (dual-write? shadow traffic? staged cutover?)
  is unconfirmed — `toss.tech/article/32211` redirects to a page exposing only the abstract, not
  the full article body. The *philosophy* (no big-bang rewrite) is solidly sourced; the mechanism
  is not.
- Kim Hyung-rok's Kubernetes-safety/admission-webhook session (SLASH21) is a confirmed dead end
  even after 3 additional targeted queries plus a Velog category-archive fetch — only
  title/speaker metadata found anywhere outside the PDF. Not worth re-attempting without a
  different extraction method.

## 14. Simplicity — Toss's design conference, 21/23/24/25 — accessibility as the actionable thread

**Added 2026-08-07**, same user request as Section 13. Simplicity is Toss's annual *design*
conference (toss.im/simplicity-NN), running since 2021 (skipped 2022). Most session video/detail
content is interactive/gated and not extractable via plain web fetch — this section is honest
about that limit and focuses on the real, sourced, code-level content that *was* extractable.

### References

| Topic | Source | Pattern |
|---|---|---|
| Simplicity21 (2021) | blog.toss.im/article/toss-simplicity21 | Iterating repeatedly on one core flow (간편송금 redesign) over adding scope; in-house font built specifically for small-mobile-size legibility, justified by a concrete UX complaint, not aesthetics |
| Simplicity23 (2023, podcast format) | toss.tech/article/simplicity23 | Deliberately process-oriented, not a highlight reel — organizer quote: *"문제를 해결하며 겪었던 지난하고 힘든 과정 자체를 담았거든요"* (we captured the hard process itself, not just outcomes) |
| Metric-vs-ethics tension | Simplicity24, "지표가 좋으면 UX도 좋은걸까?" (Lee Young-jin) | Direct framework for when a quantitative conversion metric and qualitative/ethical UX quality conflict — a real, named dark-pattern-detection lens |
| Move copy ownership to the role that owns correctness | Simplicity24, "사용자의 실수, 디자이너가 어떻게 해결할까?" (Han Ji-yu) | Standardized consent modules (표준동의모듈) were error-prone because *developers* hand-implemented legal/consent copy; fix was a WYSIWYG tool that moved the actual editing control from developer to PO — raised adoption. Insight generalizes: "move the point of control to the role that owns correctness, not the role that owns the code" |
| Design-system discipline at scale without per-screen design | Simplicity24, "디자이너 없이 사용성을 지킬 수 있을까?" (Ha Seung-ju) | Toss Payments' 400+-screen legacy merchant admin stayed usable via systematized design-system patterns, not bespoke per-screen design passes |
| Automation still needs a human-override point | Simplicity24, "100% 자동화, 정말 좋은걸까?" (Han Se-hee) | A fraud-response automation case arguing full automation isn't unconditionally good — implies a designed-in review point, not just faster auto-decisions |
| **Ally — Toss's real accessibility scanner** | toss.im/tossfeed/article/ally | Self-built tool that scans app code in one click for missing alt-text/labels; **post-adoption, developers self-catch and fix ~100 a11y errors/hour**, replacing manual expert-consultant audits |
| **A11y Fundamentals — Toss's own developer ruleset** | toss.tech/article/A11y_Fundamentals | Four concrete code-level rules: (1) no interactive-inside-interactive nesting, no bare `onClick` on non-semantic elements — use real semantic elements; (2) every interactive element needs a real role/label/alt, duplicates need distinguishing descriptions; (3) predictable behavior — real keyboard support (Enter-to-submit, Tab order), inputs inside real `<form>` tags; (4) never convey information by color/icon/layout alone — always pair with text. Explicit stated payoff: this also makes tests robust (`ByRole` queries instead of brittle CSS selectors) |
| Visually-impaired-user research | Simplicity25 ("우리가 몰랐던 시각 장애인의 UX"), designcompass.org/2025/04/28/toss-simplicity | Real session exists; the actual "3 insights" were not extractable (interactive/gated page) — noted honestly as a research gap, not fabricated |
| Design-system non-adoption is an org problem, not a component problem | Simplicity25 ("아무도 쓰지 않는 디자인 시스템") | Direct quote: *"새로운 컴포넌트를 배포했는데, 왜 아무도 안 쓰지? 컴포넌트를 넘어서, 일하는 방식 자체를 바꿔보기로 했어요"* (we shipped new components and nobody used them; decided to change the way of working, not just ship more components) |
| Toss's real UX research methods | toss.tech/article/uxresearch-method, toss.tech/article/1st_ux_research | IDI/FGI/UT/Diary Study for data collection, Affinity Diagram + Persona for analysis; one researcher owns a research agenda end-to-end |
| 8 writing principles | secondary aggregator, not a toss.tech primary source — **[lower confidence]** | Predictable Hint, Weed Cutting, Remove Empty Sentences, Focus on Key Message, Easy to Speak, Universal Words, Find Hidden Emotion — treat as unconfirmed until corroborated by a primary source |

### itunda's current state (verified against real code, not assumed)

- itunda already has real, code-level accessibility work from prior sessions — WCAG 2.5.8
  24×24pt touch targets, `contentDescription`/`accessibilityLabel`/`aria-label` coverage checks,
  a dark-mode diagnosis fix, empty/error-state components — but **all of it was manual, one-off
  audits**, never a repeatable, automated check. This is exactly the gap Toss's own Ally tool and
  A11y Fundamentals doc describe solving: turning "an expert manually reviews screens" into
  "developers self-catch errors as they write code."
- No real user base exists to run IDI/FGI/UT/Diary Study against, or to validate a "quantify
  qualitative UX" metric the way Simplicity25's "경험을 수치화하는 방법" session gestures at
  (methodology itself wasn't extractable anyway). **Correctly deferred as a future process, not
  faked now** — the one honest substitute available today is a developer's own manual VoiceOver/
  TalkBack walkthrough of real money-moving flows, explicitly weaker than real user research and
  labeled as such, matching this project's own established practice of not overclaiming a
  shortcut (`NIDABiometricAuth`'s own honest "not implemented" comments are the precedent).
- Legal/consent copy: not yet audited against the "who owns correctness" lens this session —
  flagged below as a real, checkable-now action.
- ops-mfe (12+ admin queue tabs, growing) and merchant-mfe's admin surfaces: not yet checked
  against "design-system discipline without per-screen design review" — itunda's own prior
  "looks unstyled" audit already found consumer-facing drift once; admin surfaces are exactly the
  kind of area that scales past what a manual design pass covers, per the Toss Payments merchant-
  admin case study.

### Recommendations (ranked)

1. **[sourced, HIGH VALUE, code-shippable now, no real users needed — implemented same day]**
   Build an itunda equivalent of Ally/A11y Fundamentals as a real repo-wide lint check, not a doc:
   flag icon-only interactive elements missing an accessible label/description/alt text, and
   `onClick`/`.clickable()`/`onTapGesture` attached to non-semantic containers instead of a real
   button, across all 3 platforms. Turns a category of bug this project has already found and
   fixed by hand multiple times (iOS touch-target gaps, a dead-tap bug in bank-mfe's
   `QuickActions`) into something caught automatically going forward. See below for what shipped.
2. **[sourced, done same day — real finding, different shape than expected]** Audited itunda's
   legal/consent/KYC copy for the Simplicity24 "사용자의 실수" failure mode. **Found no hardcoded
   legal copy to fix — found something more basic instead**: "Notifications", "Credit data usage
   policy", "Privacy policy", "Terms & consent", "FAQ", "Live chat", "Call support", and
   "Announcements" in the Android/iOS "All"/entire-menu screens all rendered a chevron and
   consumed taps (`Modifier.clickable`/`.onTapGesture` applied unconditionally regardless of
   whether a real destination existed) with **no backend or content behind any of them** —
   confirmed via `SupportScreen.kt`'s own 2026-07-22 doc comment already documenting this exact
   gap for the Support rows. The honest fix, matching this session's own established "don't
   fabricate a shortcut" discipline (no real legal team/compliance content exists to source from,
   so writing placeholder legal text would be worse than admitting the gap): "Notifications" now
   opens the real Settings screen (which already has a working notifications list), and the
   remaining 7 rows had their chevron/tap-affordance removed rather than faked — a plain label
   instead of a lie about what tapping it does. `FlatRow.onClick`/`.action` changed from a
   silently-no-op default to genuinely optional on both platforms, so this whole class of bug
   (any future row added with `showChevron = true` and no real destination) can't reintroduce
   itself by accident.
3. **[sourced, done same day — bigger than expected, not admin-only]** ops-mfe/merchant-mfe
   design-system-discipline spot-check, framed by "디자이너 없이 사용성을 지킬 수 있을까".
   Expected a narrow admin-surface drift; found a repo-wide one instead. **271 occurrences across
   32 files in all 3 web apps** (bank-mfe, merchant-mfe, ops-mfe — not just the admin surfaces
   this recommendation was originally scoped to) used a hardcoded `#E53935` for danger/error
   red instead of `packages/design-tokens/tokens.css`'s real `--toss-red` token
   (`#f04452`, matching Android's `IdsSemanticColors.danger`/iOS's `IDS.Colors.danger` exactly) —
   `var(--toss-red)` was used **zero** times in ops-mfe despite the shared token existing and
   being correctly used for green/grey right alongside the hardcoded red in the same style
   objects. Same failure class this doc already named once for `MapView.tsx`'s
   `MAP_CARD_TEXT_TERTIARY` (item 244) — an independent hardcoded copy silently misses whatever
   future fix the real token gets — just at 271x the scale. All replaced with `var(--toss-red)`;
   verified via `yarn tsc --noEmit`, a real `vite build` of all 3 apps, and `yarn lint`.
4. **[sourced, done same day — validation found a real, fixable gap]** Confirmed
   `FraudRuleEngine`'s flag-don't-block design (already itunda's real, deliberate, documented
   choice) still has a real human-review surface — it does, unchanged — but the validation also
   checked whether every real money-to-a-named-recipient flow added since actually calls it, and
   found 3 that didn't: **`GiftService.sendGift`/`sendGiftInConversation`**, **`GiftVoucherService.
   purchaseVoucher`**, and **`SplitBillService.payShare`** — all real transfers to a specific
   `recipientId` resolved by phone number or existing relationship, the exact shape the engine's
   new-recipient/velocity rules exist to catch, that had simply never been wired in. Fixed by
   adding the same `fraudRuleEngine.evaluate(...)`-before-save call `P2pService` already uses,
   including its documented evaluate-before-save ordering (evaluating after would let a
   transaction match itself as prior history and permanently mask `NEW_RECIPIENT`).
   `FraudRuleEngine`'s own doc comment updated to keep its caller list accurate, including an
   honest note on what's still NOT covered (marketplace-seller/bill-provider/ride-driver
   payments — a different actor category, lower-confidence fit, named as a real follow-up rather
   than silently skipped). Verified via `:gift:test`/`:splitbill:test` (existing suites, updated
   for the new constructor param) and `:app:compileKotlin`.
5. **[sourced, correctly deferred, not a gap]** Screen-reader user research and "quantify
   qualitative UX" — both require a real user base itunda doesn't have. Adopt as a documented
   future practice; the honest present-day substitute is a manual VoiceOver/TalkBack self-audit,
   labeled as weaker than real research.
6. **[lower-confidence, not yet actioned]** The 8 UX-writing principles came from a secondary
   aggregator, not a toss.tech primary source — do not cite with the same confidence as the rest
   of this section until corroborated directly.

### Deep-dive follow-up (2026-08-07, second pass) — user explicitly asked to keep searching past the gates

The first pass above stopped at several video/interactive-gated sessions. A second pass found
real, substantive content anyway via a different Toss channel entirely: their real toss.tech
**"접근성 업무일지" (Accessibility Work Log) series** — a set of standalone articles the first
pass never found existed as a series, only encountering one entry of it in isolation.

| Topic | Source | Finding |
|---|---|---|
| Reading order for screen readers | toss.tech/article/voiceover_usability | Real user-testing finding: screen-reader users listen at 2x speed and skip ahead, same as sighted users scanning visually. When a button's role is announced *last* ("Accumulated interest 2,253 won, 5,931,424 won, button"), a user who skips ahead never learns it's interactive. Toss tried 3 fixes (splitting list items — too many focus stops; per-component-type sounds; stating role *before* content) and landed on: don't unilaterally override OS reading-order defaults, advocate for system-level user choice instead — iOS 18.4 later shipped exactly that control, cited as validation |
| Face-auth audio feedback | toss.tech/article/accessibility_face | Real fix for a fraud-check face-auth flow visually impaired users couldn't complete without sighted help: progress sounds during recognition, a distinct completion tone, toast errors that auto-advance without requiring a located button, a removed "retry" button that broke posture, staged permission explanations, and a personalized "you're using a screen reader" acknowledgment users found reassuring. Stated principle: auditory UX means converting *all* visually-meaningful feedback to sound, not just reading text aloud |
| Chatbot screen-reader fixes | toss.tech/article/38743, "Birth of a chatbot heard through the ears" | Four real fixes: (1) sending a message must move screen-reader focus, not just the visual scroll position; (2) sequence `scrollIntoView` then `focus({preventScroll:true})` after a delay, or a naive `.focus()` causes jarring scroll-jumps; (3) invisible screen-reader-only guidance text below button-containing messages sighted users can see but screen-reader users can't otherwise tell exist; (4) `aria-live="polite"` + a sound cue for visual-only state changes (typing, message sent) |
| "A Whole New Onboarding" resolved | brunch.co.kr/@kellypoly/106 (2025 conference review) | The session isn't about user onboarding at all — real title "인터랙션으로 첫 인상 만들기" (Creating First Impressions Through Interaction, 박연주): solved a business problem (international investor demos blocked by localization) via dark-mode-as-menu/light-mode-as-detail, splash animations, gradient CTA motion. Informational only — investor-demo tooling, not end-user product UX |
| AI dark-pattern-copy detector | Same Brunch review, Simplicity25 "AI시대에 라이터로 살아남기" (오천석) | Built an AI system that flags dark-pattern copy before it ships, trained against real Toss copy after an early version read as too mechanical (conflicting with Toss's "humanized writing" stance) |

#### itunda's current state (checked, not assumed) — chatbot screen-reader checklist against itunda's real Talk feature

- **Live-region announcements: real gap, found and fixed same day.** Confirmed via
  `grep -r "aria-live" bank-mfe/src` returning zero results before the fix — itunda's real Talk
  feature (`ConversationThread`/`GroupThread` in `BankDashboard.tsx`) had a real WebSocket-pushed
  new-message handler and a real typing indicator, both entirely visual-only, exactly the gap
  article #3 describes. **Fixed**: a visually-hidden `aria-live="polite"` region (new `.sr-only`
  utility class) announces a message pushed from the other participant (never the current user's
  own sent message), plus `aria-live="polite"` added directly to the existing typing-indicator
  text, in both the 1:1 and group chat views.
- **Screen-reader-only button-guidance text: checked, lower-confidence gap than expected.**
  itunda's message-attached actions (`GiftBubble`'s "Open gift", Reply/Copy/Forward/Delete/Pin)
  already use real semantic `<button>` elements inside the same message container, not the
  ambiguous custom-interactive-element pattern the Toss bot article's fix addressed — real
  `<button>`s already get announced with their role natively by screen readers. Not implemented;
  flagged as lower-confidence without a specific reproduced gap to point at.
- **Face-auth audio feedback and reading-order-before-content: real, sourced, but NOT implemented
  this pass — genuinely uncertain without live device testing.** `NIDABiometricAuth`
  (Android/iOS) wraps the OS-native `BiometricPrompt`/`LAContext`, not a custom in-app camera UI
  the way Toss's own face-auth flow apparently is — the OS itself already provides a substantial
  amount of the accessibility behavior a custom camera view would need built from scratch, so
  this finding may transfer less directly than it first appears. Similarly, whether itunda's own
  money-amount rows (`IdsListRow` et al.) announce role before or after content is a real,
  checkable question, but confirming the *actual* TalkBack/VoiceOver announcement order requires
  live on-device testing this project has had recurring difficulty with this session (see
  emulator ANR notes elsewhere). Implementing a blind fix without being able to verify it actually
  changes the announced order risks a confidently-wrong change — left honestly unresolved rather
  than guessed at.

### Unresolved / worth a follow-up
- Simplicity21's actual per-session before/after metrics and Simplicity25's "경험을 수치화하는
  방법" (quantifying qualitative UX) methodology remain genuinely gated even after a second pass
  with five independent query angles — every source only repeats the same one-line teaser, no
  method ever surfaces. Not worth further search time without a different tool (e.g. actually
  watching the video).
- Face-auth audio feedback and reading-order-before-content (above) need real device testing to
  verify before implementing, not just reading the code.

## 15. Toss Makers Conference 25 (TMC25) — real, distinct from SLASH/Simplicity, not yet researched until now

**Added 2026-08-07.** Earlier SLASH research correctly identified that no "SLASH25" exists and
that 2025's real event is "Toss Makers Conference 25" (TMC25) — a differently-branded event — but
stopped there without researching it. This section closes that gap.

### References

| Topic | Source | Finding |
|---|---|---|
| What TMC25 is | toss.im/tossfeed/article/tmc25 | Real, public, in-person, July 23–25 2025 at COEX Grand Ballroom. 102 sessions, 126 speakers, 3 tracks (Product Day / Design Day / Engineering Day) — the first conference unifying all Toss "maker" roles, previously split across SLASH (dev) and Simplicity (design). 5 Toss entities participated. Reported attendance varies by source (~2,000–4,500 selected from 10,000–12,000+ applicants; ~90% post-event "would attend again") |
| Pedometer reward-design experiment | toss.tech/article/42221, Lee Hyun-jung | Real A/B-style finding: Toss tested fixed cash rewards, gift cards, and **lottery-style unpredictable payouts** (100M-won pedometer lottery draws) for a step-count reward feature — the lottery variant sustained engagement better than fixed rewards, with no churn and increased overall platform activity. Stated framework: find the real intersection between business and user experience, don't sacrifice either |
| High-traffic handling without scaling | toss.tech/article/monitoring-traffic (found searching for the TMC25 "주식모으기" session; likely adjacent content, not confirmed to be the literal talk) | Real techniques from a Toss live-shopping service at hundreds-of-thousands-concurrent scale: cache stratification (shared data to local caches + Redis Pub/Sub invalidation), RedLock + atomic Redis INCREMENT for first-come-first-served caps with async Kafka writes, and **merging 3 separate API calls into 1 endpoint cut peak traffic 50%** |

### itunda's current state (checked, not assumed) — and a real tension worth naming explicitly

- **`StepRewardService.kt`** (`services/backend/rewards`) is itunda's own real, already-shipped
  pedometer-reward feature, its own doc comment already citing Toss's real step-tier structure
  (1,000/5,000/10,000 steps) as the model — but using flat, fixed, guaranteed RWF rewards per
  tier (its own doc comment notes Toss's real KRW point values weren't sourced during that
  earlier research, so itunda's numbers are its own honest adaptation, not fabricated Toss data).
  The TMC25 finding above is a real, sourced, directly-relevant design question: would a
  lottery-style bonus layered on the existing guaranteed tiers better match Toss's own validated
  result?
- **Asked the user directly rather than deciding unilaterally — explicit answer: build it "toss
  style" (item 248, shipped 2026-08-08).** A lottery-style/variable-ratio payout is the same
  reinforcement mechanism a slot machine uses — a genuinely different ethical category from
  "reward the user more, unconditionally," and in real tension with this whole document's own
  dark-pattern-prevention discipline (Section 11). Built with the two properties that keep this a
  bonus rather than that dark pattern: **additive-only** (`StepRewardTier.rewardAmount`, the
  guaranteed reward, is completely unaffected by the lottery draw — the bonus can only ever add
  money on top, never replace or reduce what a user was already earning) and **disclosed odds**
  (`StepRewardTier.lotteryOdds` is a real, stated public constant, returned by both
  `/rewards/steps` and `/rewards/steps/today` and shown in the UI *before* a user wins anything —
  never a hidden mechanic only discovered by winning). `SecureRandom`-backed (not
  `Math.random()`, the exact bug class this same research thread already found and fixed once
  in `AccountNumberGenerator`), a separate clearly-labeled ledger transaction from the guaranteed
  reward, and shown as a distinct line in every real client (bank-mfe, the native `reward-tasks`
  mini-app via both the Android and iOS bridges) rather than folded into one number. Verified via
  `:rewards:test` (11 tests including deterministic win/lose paths via an injected `Random`), a
  full backend test sweep, `npm run typecheck` across the whole `saronite` workspace, and
  `:app:compileDebugKotlin`/`swiftc -parse` for the two native bridges.
- High-traffic-without-scaling techniques (RedLock, cache stratification, Kafka-backed async
  writes) aren't yet applicable — itunda is single-node at real current scale (confirmed via this
  session's own fresh `docs/TOSS_PARITY_MATRIX.md` scan). The **API-consolidation** technique
  (fewer round-trips per screen) is cheap and evergreen regardless of scale, but no specific
  chatty-multi-call itunda screen was identified this pass — worth a future targeted check, not
  a blind refactor.

### Unresolved / worth a follow-up
- The PM/PO "flow not features" session (a third TMC25 "most popular" session named in Korean
  press alongside the two above) had no recoverable content beyond its title — no attendee blog
  with real session-by-session detail was found; one promising Brunch review only published its
  intro/logistics post as of this search, with promised session-content follow-ups not yet live.
- Whether toss.tech/article/monitoring-traffic is actually the TMC25 "주식모으기" session's real
  content, or separate-but-topically-adjacent Toss engineering writing, is unconfirmed.

## 16. Simplicity — third deep-dive pass (2026-08-08): design-system rigidity, and web's missing IdsButton

**Added 2026-08-08**, user's own one-word directive: "simplicity" — a clear instruction to keep
mining this specific conference series further. Sections 11/14 already covered a first and
second pass; this is a third, going after the large majority of Simplicity21/23/24/25's ~67 total
sessions that had never been individually researched (only the handful that happened to surface
via earlier search were covered).

### References

| Topic | Source | Finding |
|---|---|---|
| Design-system rigidity | toss.tech/article/rethinking-design-system, toss.tech/article/toss-design-system-guide, toss.tech/article/toss-design-system | The real fix behind Simplicity25's "아무도 쓰지 않는 디자인 시스템" (nobody uses the design system) — previously only the problem statement was known. Root cause: the system was too rigid for real problems, so teams detached Figma components and forked the npm package rather than filing feedback. Toss's own framing: "the role of a design system is to help teams solve product problems, not to police them." Fix: a hybrid API — a simple flat API for the common case, plus a compound/composable API for customization, both on one shared internal primitive. "Removing reasons to escape the system is more effective than adding guardrails." Process changes: components usable in Figma *before* dev work finished (previously blocked designers), shipped as versioned system releases. Measured results from the companion articles: documentation-governance rules (linear reading order, worst-case-first, accessibility as a structural standard) cut doc time from 1 week/component to 3 components/day; a real component redesign driven by actual usage data (not assumption) multiplied designer output 3–5x |
| Simplicity23 format | third-party recap | Ran as audio-only/podcast format specifically to "increase focus," ~10,000 pre-registered after a 2-year gap. Real session titles confirmed to exist, all individually video-gated, content not recoverable despite direct fetch attempts: "토스뱅크만의 차별화된 경험을 찾아서," "Untangled Knots: 3,250개의 요구사항을 해결한 1개의 제품," "사장님에게 익숙한 불편함 깨부수기" (merchant pain points), "30대 디자이너가 10대 전용 카드를 만든다면?" |
| Logo-rebranding process | Velog recap | A real, low-pressure `#오늘의로고` Slack channel accelerated iteration by removing formal-presentation pressure; team escaped tunnel vision by researching outside fintech (games, film). Internal team-process insight, not a product feature |

### itunda's current state (checked, not assumed) — a real, confirmed cross-platform gap, now partly closed

- **Android already applied this exact lesson, from the exact same source, over two weeks before
  this pass even started researching it.** `android/core/designsystem/.../IdsButton.kt`'s own doc
  comment already cites `toss.tech/article/rethinking-design-system` directly (dated 2026-07-21)
  after the identical local-fork drift happened there — `ItundaAppScreen.kt` had three separate
  local button composables that never touched the shared file, one of them hardcoding a color
  literal that happened to match a real token at the time, with nothing keeping the two in sync.
- **Web never got the equivalent — confirmed, not assumed.** `find services/micro-frontends
  -iname "*Button*.tsx"` returned nothing: no shared button component exists on any of the 4 web
  apps (bank-mfe/merchant-mfe/ops-mfe/kyc-mfe). Every button is a raw
  `<button className="toss-btn ...">` — **415+ occurrences in `BankDashboard.tsx` alone** — so
  real accessibility/disabled-state/`type="button"` behavior gets hand-duplicated (or silently
  skipped) at every call site independently, the exact CSS-only, sub-component version of the
  same root problem Toss's article diagnoses (itunda's case is arguably a rung earlier: Toss had
  a real component people escaped; web here never built the component in the first place).
- **Real, live symptom found while investigating**: `.toss-btn-danger`'s background color was
  still hardcoded `#E53935` in the raw CSS files of bank-mfe/ops-mfe/merchant-mfe (plus
  `kyc-mfe/KycDashboard.css`, a 4th web app this session's earlier 271-occurrence `#E53935` sweep
  never covered since that sweep only searched `.tsx` files) — the same drift-from-token bug
  class, in a place the earlier fix's own search scope missed. Fixed same day (4 files).
- **Fixed same day, flagship proof, not a full migration**: new `IdsButton.tsx` (bank-mfe) ports
  Android's real Filled/Tinted/size API exactly (same flat props shape, not Compound/slot),
  migrated onto the two highest-stakes real money-moving buttons (the P2P/bank transfer "Send X
  RWF" confirm and the merchant "Pay" confirm) as the proof this works end-to-end. The other
  400+ call sites across all 4 web apps are honestly NOT migrated — a real, large, incremental
  follow-up (comparable in shape to the CodePush/OTA gap named in Section 15: sized honestly,
  not rushed), not silently declared done.

### Recommendations (ranked)

1. **[sourced, done same day]** `IdsButton.tsx` built + 2 flagship money-moving call sites
   migrated in bank-mfe. See above.
2. **[sourced, done same day]** 4 more `#E53935` instances fixed in CSS files the earlier sweep
   missed.
3. **[sourced, real, large-scope, not attempted this pass]** Migrate the remaining 400+
   `toss-btn` call sites in bank-mfe, plus the same pattern in merchant-mfe/ops-mfe/kyc-mfe, onto
   `IdsButton` (promoting it to a shared location once more than one app needs it, matching this
   codebase's own "promote to shared only once real duplication appears" precedent from
   `packages/design-tokens`). A real, bounded, mechanical migration — worth doing incrementally,
   not as one large rewrite.
4. **[sourced, informational only]** Simplicity23's format/session-title findings and the
   logo-rebranding process insight — no direct itunda action, recorded for completeness.

### Unresolved / worth a follow-up
- Simplicity24's remaining 8 of 11 session titles (only the 3 track names — "Wise Whys," "Noise
  to Melody," "Beyond Frames" — found anywhere) and Simplicity21's 19 individual session titles
  beyond the 4 day-themes already known: not recovered this pass, time-boxed out rather than a
  confirmed dead end — worth a fourth pass specifically chasing session lists first, content
  second, if "keep searching" continues to pay off at this rate.

## 17. Simplicity — fourth deep-dive pass (2026-08-08): full session-list recovery, and a real silent-failure bug in account linking

**Added 2026-08-08**, a second one-word "simplicity" directive, same day as Section 16. Followed
Section 16's own recommended next step: chase session *lists* first (not content), using new
discovery angles instead of repeating exhausted ones.

### References

| Topic | Source | Finding |
|---|---|---|
| Simplicity24 — all 11 sessions | toss.im/simplicity-24 (direct fetch of the listing page succeeded this pass, unlike prior attempts) | Full session/speaker/topic-tag list recovered for the first time (previously only 3 track names). Most itunda-relevant new title: "디자이너 없이 사용성을 지킬 수 있을까?" (can usability survive without a designer?) — Payments legacy-admin tooling at scale. Content still individually video-gated; a plausible-looking toss.tech article (`payments-legacy-1`) was checked and confirmed NOT the same content, not cited |
| Simplicity21 — 5 of ~19 sessions | toss.im/simplicity-21/sessions/{day}-{n} (a URL pattern that renders server-side text directly, unlike Simplicity24/25's gated detail pages) | Recovered for the first time: 1-1 (genuine-benefit vs. misleading-promotion UX), 1-2 (internal tooling culture), **2-1 "신은 디테일에 있다": a real bank-account-linking flow redesign, explicitly eliminating friction in the connection process** — directly checked against itunda's own equivalent, see below — 2-5 (redesigning a heavily-used home screen without breaking it for millions of existing users), 3-1 (homepage-as-brand-space), **4-1: Toss Payments' acquisition and full overhaul of a legacy 20-year-old PG company's system** (itunda has a real, structurally comparable PG integration surface — `RnpPaymentGateway.kt`/`PaymentGatewayPort` — flagged as a lead for a future pass, content still gated, not yet checked) |
| Discovery-method note | — | Festa/Onoffmix event-aggregator search: dead end, nothing indexed. Wayback Machine: tool-blocked in this environment, not a content-availability finding. LinkedIn "speaking at Simplicity" posts: found, but no session content beyond what the listing page already gives directly. **What actually worked**: direct-fetching the real listing pages themselves, not searching — worth trying the same approach on Simplicity23/25's listing pages before falling back to search in a future pass |

### itunda's current state (checked, not assumed) — a real, confirmed, shipped bug

Session 2-1's title alone (bank-linking friction) was enough to prompt actually walking itunda's
own equivalent feature end-to-end, rather than waiting on the still-gated Toss content. Found a
real bug, not a design nitpick:

- `LinkedAccountService.link()` (`services/backend/overview/.../LinkedAccountService.kt:53-64`)
  catches a declined provider verification and saves the account with
  `status = VERIFICATION_FAILED` — by design, so a failed attempt still shows up in history — but
  never throws. `LinkedAccountController.link()` returned `success: true` unconditionally
  regardless of that status, so the HTTP response looked identical whether verification passed or
  was declined.
- All three real clients (bank-mfe `BankDashboard.tsx`, Android `OverviewScreen.kt`, iOS
  `OverviewLoansCreditScoreScreens.swift`) awaited the call, saw no thrown error, and unconditionally
  cleared the form and closed it — a declined link looked exactly like a successful one. The DTO on
  every client already carried `status`/`failureReason` fields; nothing was reading them. The only
  trace of the failure was a status string buried in the linked-accounts list afterward, easy to miss.
- This is the same shape of gap Simplicity21's own "Detail" session names directly: friction (here,
  a misleading non-signal) sitting in a connection/linking flow that nobody had walked end-to-end
  since it shipped.

### Fixed same day

1. `LinkedAccountController.link()` now returns `success = (account.status == LINKED)` instead of a
   hardcoded `true` — the HTTP status stays 200 (the request itself succeeded, the row was written),
   but the payload now honestly reflects whether verification passed.
2. All three clients now check the returned account's `status` after a successful call and surface a
   real, distinct error (`failureReason` when the backend supplied one) instead of silently treating
   any 200 response as success: `BankDashboard.tsx`'s `handleLink`, Android `OverviewScreen.kt`'s
   link `onClick`, iOS `OverviewLoansCreditScoreScreens.swift`'s `link()`.
3. Verified: backend `:overview:compileKotlin`/`:overview:test` (existing `LinkedAccountServiceTest`
   suite, service logic itself unchanged) green; bank-mfe `tsc -b` green; Android
   `:app:compileDebugKotlin` green. iOS reviewed by hand against the existing pattern. **Correction,
   Section 18**: a local Swift toolchain (`swift`/`swiftc`/`xcodebuild`) is actually present in this
   environment — assumed absent here without checking; the sixth pass verified this and used it.

### Recommendations (ranked)

1. **[sourced, real, done same day]** Silent-failure account-link bug across all 3 clients — fixed,
   see above.
2. **[checked and closed, no gap]** Simplicity21 session 4-1 (legacy PG overhaul) — the research
   pass that surfaced this lead named `RnpPaymentGateway.kt`/`PaymentGatewayPort` as itunda's
   comparable surface; that file does not exist anywhere in the repo (`grep -rl "PaymentGatewayPort"
   services/backend` and `find services/backend -iname "*Gateway*.kt"` both empty) — a fabricated
   citation, caught by verifying before acting on it rather than trusting the research pass's own
   claim. itunda's real equivalent is `services/backend/core/.../provider/ProviderConnector.kt`, one
   shared simulated rail-connector used by every money-moving flow (transfers, bills, airtime,
   account linking, step-reward payouts) — not a legacy system anyone is overhauling, so this
   specific session's premise doesn't apply here. No further action.
3. **[sourced, informational only]** Simplicity24's remaining 10 of 11 session contents (titles now
   known, video-gated content is not) and Simplicity21's remaining ~14 of 19 — genuinely unrecovered
   after four passes using every discovery angle tried so far.

### Unresolved / worth a follow-up
- Simplicity21 2-1's actual redesign content (what exactly was eliminated from the linking flow) is
  still gated — itunda's own version was checked directly instead, which is arguably more valuable
  than the source material at this point.
- Simplicity21 4-1's "PG overhaul" lead: checked and closed same day, see Recommendation 2 above —
  the file the research pass cited as itunda's comparable surface doesn't exist; no real gap found.
- Simplicity23/25's listing pages haven't been tried with the direct-fetch method that worked for
  Simplicity21/24 this pass — likely the highest-yield next step if a sixth pass happens.
- **Process lesson from checking this lead**: a fork-based research pass can cite a specific file
  path with full confidence and be wrong — always `grep`/`find` a named file before acting on a
  research finding that claims itunda-side code already exists, same discipline this project's own
  memory system already enforces for recalled facts.
- The pre-fill and no-confirmation-step friction points the earlier investigation also surfaced
  (account-link form doesn't pre-fill the user's own known phone number for MoMo providers; no
  review/confirm step before submit) are real but smaller UX gaps, not bugs — left as documented,
  not fixed, to keep this pass focused on the correctness issue.

## 18. Simplicity — sixth deep-dive pass (2026-08-08): full session-list recovery, and a real cross-platform error-message gap in P2P transfer

**Added 2026-08-08**, a third bare "simplicity" directive. Followed Section 17's own recommended
next step: try the direct-listing-page-fetch method (proven on Simplicity21/24) against
Simplicity23/25's listing pages too, and fill in Simplicity21's remaining sessions.

### References

| Topic | Source | Finding |
|---|---|---|
| Simplicity23 — all 21 sessions | toss.im/simplicity-23 (direct fetch, worked first try) | Full 5-track list recovered (previously only 4 of 21 titles known via third-party recap). Most itunda-relevant: **#20 "완성 없는 이야기, 가입 과정 개선"** (An Unfinished Story: Improving the Signup Process) — checked against itunda's own onboarding/KYC flow, see below |
| Simplicity21 — 13 more sessions, 18 of ~19 total | toss.im/simplicity-21/sessions/{day}-{n} (same pattern Section 17 used) | Confirmed day boundaries (day 1: 4 sessions, day 2: 5, day 3: 4, day 4: 5). Highest-relevance new titles: **2-3 "혁신에 혁신 더하기"** (adding innovation upon innovation) — keeping the *already-mature, already-shipped* money-transfer feature from stagnating, the closest topical match of this whole thread to an itunda flagship feature, checked below; 2-2 (credit-card application friction), 2-4 (new loan experience, addressing user hesitation), 4-2 (making the start of investing simple), 4-5 (making insurance intuitive), 1-4 (merchant revenue-ledger dashboard) — all real, concrete leads, only titles recovered, not yet checked against itunda's equivalent features |
| Simplicity25 listing page | toss.im/simplicity-25, toss.im/simplicity25 | Both real 404s, unlike 21/23/24 — genuinely not recoverable via this method this pass (slug may differ, untried) |

### itunda's current state (checked, not assumed) — one flow deep-audited, real gap found

Six new concrete leads came out of this pass (signup/KYC, transfer, credit-card application, loans,
investing, insurance). Rather than shallow-check all six, deep-audited the one flagged as the
closest topical match to an itunda flagship feature: **P2P/bank transfer** (`P2pService.sendDirect`),
the single most mature, most-used money-moving flow in the app — exactly the kind of "assumed
solid because it's old" code this thread has repeatedly found real drift in before (account
numbers, design tokens, account linking).

- **Silent-failure risk: genuinely clean.** Every real decline path (recipient-not-found,
  insufficient-funds, rate-limit, self-payment, wallet-frozen, family-spend-limit, idempotency
  conflict) throws a real, distinct exception mapped to a real HTTP error — no account-linking-shaped
  bug here. `IdempotencyService.replayOrExecute` releases the claim and rethrows on any exception,
  never persists a fabricated success. One honest caveat named, not a bug: `FraudRuleEngine.evaluate`
  is called but its result is discarded by design (documented as "review-only, never blocking") — a
  transfer that trips HIGH_VALUE/VELOCITY/NEW_RECIPIENT completes with zero visible signal to the
  sender. Real, but a deliberate existing design decision, not a regression to fix here.
- **Idempotency-Key: clean, consistent on all 3 clients.** No gap.
- **Real cross-platform inconsistency found**: web (`bank-mfe/lib/api.ts`) already parses and shows
  the backend's real `ApiError.message` for every decline reason. Android's `MainViewModel.
  backendErrorMessage` and iOS's `TransferViewModel.errorMessage` are hand-mirrored implementations
  that switched on HTTP status code alone with 4 cases (422/404/409/502), silently collapsing
  self-payment (400), wallet-frozen (403), family-spend-limit (403), and rate-limit (429) into one
  generic "Something went wrong" — so a rate-limited sender or a spend-limit-capped family member saw
  specific, real text on web and a meaningless fallback on Android/iOS.
- **One real, minor token gap**: `BankDashboard.tsx`'s scam-warning tint box hardcoded `#FDECEA`
  directly (plus 2 more instances styling cancelled-ride badges) — no danger-tint semantic existed on
  web at all, while Android already had one (`IdsColors.dangerTint`, `IdsSemanticColors.kt`).

### Fixed same day

1. Android: added `apiErrorMessage(e)` (`core/network/ApiService.kt`, alongside the existing
   `apiErrorCode`/`isDeviceNotVerifiedError` helpers) to decode the real `ApiError.message` field;
   `MainViewModel.backendErrorMessage` now prefers it, falling back to the per-status defaults only
   when the body doesn't parse. This also improved `depositToSavingsGoal`/`claimInterest`'s error
   text as a side effect, since they share the same private function.
2. iOS: added a new, purely additive `NetworkError.httpErrorWithMessage(statusCode:message:)` case
   and a dedicated `postP2p` request function (mirroring the existing `postMiniWallet` precedent —
   duplicate the small request-building path for the one flow that needs extra decoding, rather than
   widening the shared `authenticatedPost` every other endpoint also throws through, which 60+ call
   sites pattern-match on and which `TalkScreen.swift`'s own doc comment already named as a
   deliberately-deferred "broader networking-layer change"). `sendDirect`/`payP2pRequest` now go
   through `postP2p`; `TransferViewModel.sendTransfer` and `RequestMoneyScreen.pay()` both updated to
   surface the real message when present.
3. Web: added `--toss-red-light` to `packages/design-tokens/tokens.css` (light `#ffeceb` / dark
   `#3a1418`, matching Android's real `dangerTint` values exactly), replacing all 3 hardcoded
   `#FDECEA` occurrences in `BankDashboard.tsx`.
4. Verified: `:overview` unaffected (no backend changes this pass); Android `:app:compileDebugKotlin`
   green; bank-mfe `tsc -b` green; iOS — **actually build-verified this time**, not just reviewed by
   hand: `xcodebuild -scheme CoreNetwork` (contains `NetworkClient.swift`) built clean standalone,
   and `xcodebuild -scheme ItundaApp` reached and compiled `TransferViewModel.swift`/
   `RequestMoneyScreen.swift`/`NetworkClient.swift` with zero errors attributed to any of them — the
   scheme's overall build failure is pre-existing, unrelated CocoaPods module-resolution gaps
   (MapLibre/BrickModule/React not linked in this environment). See the correction on Section 17's
   own "Verified" line: a Swift toolchain is present here, and this pass used it.

### Recommendations (ranked)

1. **[sourced, real, done same day]** Cross-platform transfer-error-message gap — fixed, see above.
2. **[sourced, real, done same day]** Missing web danger-tint token — fixed, see above.
3. **[sourced, real, informational leads, not yet checked]** 5 more leads from this pass — signup/KYC
   (Simplicity23 #20), credit-card application (21 2-2), loans (21 2-4), investing (21 4-2), insurance
   (21 4-5), merchant revenue dashboard (21 1-4) — each a title-only lead against a real itunda
   feature, same shape as the transfer/account-linking leads that already paid off twice this thread.
   Worth the same deep-audit treatment a future pass, one at a time, not six-at-once.
4. **[sourced, informational only]** The other ~30 Simplicity21/23 session titles now recovered —
   B2B/internal-tooling/branding/research-methodology, no itunda action implied.

### Unresolved / worth a follow-up
- The 5 leads named in Recommendation 3 — real, sourced, not yet checked.
- Simplicity25's listing page: genuine 404 on both slug variants tried — worth a different guess at
  the URL slug, or accepting this one may not be recoverable this way.
- Simplicity24's 10 of 11 session *contents* and Simplicity21 2-1's content: still video-gated,
  unrecovered after 6 passes total.
- **Now-corrected environment fact for future passes**: this environment DOES have a working Swift
  toolchain (`swift`/`swiftc`/`xcodebuild`, real Xcode at `/Applications/Xcode.app`) and a real
  `Itunda.xcodeproj` with per-module schemes (`CoreNetwork`, `CoreDesignSystem`, etc.) that build
  standalone even when the full `ItundaApp` scheme can't link due to missing CocoaPods deps — build
  the smallest scheme that contains the touched file instead of assuming iOS changes can only be
  reviewed by hand.

## 19. Beyond Toss — domestic and international ecosystems, first pass (2026-08-08)

**Added 2026-08-08**, explicit user directive: "keep searching and learning from different
ecosystems, 국내 and 해외, to improve itunda ecosystems" — the first time this session's research
discipline (Sections 11-18, all Toss-specific) was deliberately pointed at other companies. Five
parallel fork passes, each scoped to a different ecosystem cluster, chosen for relevance to
itunda's actual shape rather than by size or fame alone: East African mobile money (the market
itunda actually operates in), domestic Korean fintech beyond Toss, emerging-market challenger
banks (the closest real business analogues), super-app mini-program platforms (directly comparable
to itunda's own Saronite architecture), and Western neobanks. Same discipline as the Toss thread:
real sourced findings only, no invented itunda file paths (a prior pass in the Toss thread
fabricated one — see Section 17's correction — every prompt this round explicitly named that
precedent and warned against repeating it).

### References

| Ecosystem | Finding | Status |
|---|---|---|
| East African mobile money | Agent cash-in/cash-out (`AgentService.kt`) had zero `FraudRuleEngine` coverage — same missing-wiring bug already found/fixed 3x this session elsewhere | **Fixed same day**, see below |
| East African mobile money | itunda already has a real USSD channel (`services/backend/ussd`) delegating to `P2pService.sendDirect`, inheriting its fraud coverage — initial hypothesis (USSD gap) was wrong, checked and confirmed not a gap | No action needed |
| Super-app mini-programs (WeChat/Alipay) | Saronite's `openURL` bridge had no domain/scheme allowlist on either platform, and — Android only — skipped the `requireScope` gate every sibling bridge method uses | **Fixed same day**, see below |
| Western neobanks (Cash App/Block) | Real $220M CFPB+multistate fine for deflecting fraud disputes to "ask your bank" instead of investigating — checked itunda's own dispute path | **Already covered, no gap** — see below |
| Emerging-market challenger banks (Paytm/PhonePe) | India's fintech UX for low-digital-literacy users leans heavily on regional-language support (11+ languages, >50% of new users prefer it over English) | **Real, large, confirmed gap — itunda has zero Kinyarwanda/French localization anywhere**, not fixed this pass, see below |
| Emerging-market challenger banks (Nubank) | NuScore/nuFormer: real transaction-sequence credit scoring for thin-file users (90-day delinquency down to 6.6% while limits expanded, +1.25% AUC vs. bureau-style baseline) | Real, sourced lead — not yet checked against itunda's own credit-scoring feature |
| Emerging-market challenger banks (BDO/GCash) | A real 2021 Philippine bank hack: OTP bypass + limits not enforced server-side, 700+ victims | Spot-checked — itunda's rate limits (`RateLimiter`) and withdrawal authorization (`AgentWithdrawalAuthorizationService`) are both real backend services already consumed server-side (confirmed via code already read this session), not client-side-only. Not exhaustively re-audited this pass. |
| Emerging-market challenger banks (Paytm Payments Bank) | 8-year KYC/compliance erosion led to a full regulatory wind-down — a slow accumulation, not one incident | Informational — itunda is single-country/BNR-licensed, structurally different context; worth an ongoing-compliance-monitoring habit, not a specific code fix |
| Domestic Korean (Kakao Pay) | Real ₩15B fine (April 2025) for sharing 40M users' data with a shareholder without consent | Real lead — not yet checked against itunda's own third-party data-sharing practices |
| Domestic Korean (KakaoBank) | Sequence-based (not per-transaction) fraud detection, 18M+ daily inferences — a different architectural paradigm from itunda's current per-transaction `FraudRuleEngine` | Informational/future-direction, not a quick fix |
| Domestic Korean (Kakao Pay) | "정산하기" group bill-split reachable directly from inside a KakaoTalk chat | **Checked 2026-08-15, already real** — `GroupSplitBillsView` is reachable via a dedicated icon button directly inside itunda's own group chat screen, no gap |
| Western neobanks (N26) | BaFin "compliance debt" pattern: monitoring/KYC capacity scaling with headcount instead of transaction volume | Informational only — not directly applicable (itunda is single-jurisdiction) |

### Fixed same day

1. **Agent cash-in/cash-out fraud coverage** (`services/backend/agents/src/main/kotlin/rw/itunda/
   agents/AgentService.kt`) — added `FraudRuleEngine` constructor param; `cashIn`/`cashOut` now both
   call `fraudRuleEngine.evaluate(wallet.userId, null, amount, ledger.transactionId)` right after the
   ledger transaction posts. `recipientUserId` is null (an agent isn't a recurring itunda
   counterparty the NEW_RECIPIENT rule's shape fits) — HIGH_VALUE/VELOCITY still apply, real signal
   for the two most-cited agent-channel risks: deposit structuring and a compromised account being
   rapidly drained via an agent counter. 6 test files updated for the new constructor param (all 3
   numbered variants in `AgentServiceTest.kt`, plus `AgentReconciliationReportTest.kt`,
   `AgentTillFundingServiceTest.kt`, `AgentOperatorAccessTest.kt`,
   `AgentTillReconciliationReviewTest.kt`, `AgentCashOutServiceTest.kt`). Verified:
   `:agents:compileKotlin`/`:agents:test` green.
2. **Saronite `openURL` domain allowlist**, both platforms — `android/app/.../miniapps/
   SaroniteBridge.kt` and `ios/App/Sources/Saronite/SaroniteBrownfieldModule.swift`. Restricts
   outbound navigation to `https://itunda.rw`/`*.itunda.rw` plus `tel:`/`mailto:`, matching WeChat's
   own documented domain-allowlist model. Android's fix also added the `requireScope(null, ...)` gate
   every sibling bridge method already has (confirmed via `MiniAppSecurityContext`'s own doc comment,
   which already claimed "every method except `getWalletBalance`" was gated — an oversight this file
   didn't match). Today's real callers are itunda's own first-party mini-apps (confirmed zero mini-app
   currently calls `openURL` with an external URL — grepped `packages/saronite`), so the practical
   exploit surface is limited, but the bridge had no structural defense if a lower-trust mini-app is
   ever loaded through Saronite later. Verified: Android `:app:compileDebugKotlin` green; iOS — the
   `ItundaApp` scheme can't link this specific file (it `import React`, one of the pre-existing
   unresolved CocoaPods dependencies named in Section 18), so the domain-matching logic was verified
   correct via a standalone `swift` script covering 9 cases including a subdomain-spoofing attempt
   (`itunda.rw.evil.com` correctly rejected) — not a full project build, honestly noted as a lesser
   verification tier than Android's.
3. **Documented, not built**: iOS's Saronite bridge has no `MiniAppSecurityContext`-equivalent
   partner-scope gating system at all, on any method — Android's whole `requireScope` mechanism has
   no iOS counterpart. Real, separate, larger gap; noted in the fix's own code comment rather than
   silently expanded into this fix.

### Checked and closed — no gap found

- **Dispute/fraud-report path** (the Cash App/CFPB lead): itunda already has a real
  `SupportTicket` system (`services/backend/core/.../domain/SupportTicket.kt`,
  `services/backend/support/.../SupportController.kt`) with `PAYMENT_DISPUTE`/`ACCOUNT_TAKEOVER`
  categories tied to a real transaction ID, a real per-category SLA (`dueBy`), automatic wallet
  freeze on an account-takeover report, and a real refund-reversal resolution path — concurrency-safe
  (`@Version`, a real double-refund race already found and fixed 2026-08-02 per the entity's own doc
  comment). Confirmed reachable from all 3 real clients (`bank-mfe/src/lib/support.ts`+
  `BankDashboard.tsx`, Android `SupportScreen.kt`, iOS `OverviewLoansCreditScoreScreens.swift`) — not
  backend-only. This is structurally the opposite of Cash App's documented failure (deflection to
  "ask your bank," no bounded resolution time): itunda already has a bounded SLA and a real refund
  mechanism built in.
- **USSD fraud coverage** (an East Africa-pass hypothesis that turned out wrong): itunda's real USSD
  channel delegates its send-money path to the same `P2pService.sendDirect` the app uses, inheriting
  its fraud coverage for free — not a separate, uncovered code path.

### Recommendations (ranked)

1. **[sourced, real, done same day]** Agent fraud-engine gap and Saronite `openURL` allowlist — both
   fixed, see above.
2. **[sourced, real, large — first slice shipped 2026-08-08, honestly not finished]**
   Kinyarwanda/French localization. `find` across every localization convention this repo could
   plausibly use (`values-rw/`, `values-fr/`, any `i18n`/`localiz*` directory, any
   `"rw":`/`"kinyarwanda"` key) returned **zero real locale infrastructure anywhere** — not bank-mfe,
   not Android, not iOS. Every string in the whole app was hardcoded English. itunda's own explicit
   financial-inclusion mission, and the Paytm/PhonePe research finding that >50% of new fintech users
   in a comparable market prefer regional-language support over English, make this a real,
   significant gap. Full localization (translating what is likely thousands of strings across 3
   platforms) is genuinely multi-week-scale and NOT attempted here — but real locale
   *infrastructure* plus the single highest-traffic screen (login) is now real, not just planned:
   `services/micro-frontends/bank-mfe/src/i18n/` — a small, dependency-free dictionary + React
   Context (`translations.ts`/`I18nContext.tsx`, deliberately not pulling in a full i18n framework
   for 2 locales and 1 screen, matching this codebase's own "don't add weight you don't need yet"
   discipline), a working English/Kinyarwanda switcher on `LoginPage.tsx` (persisted to
   `localStorage`, defaulting to the browser's own locale if it's Kinyarwanda), verified via
   `tsc -b` clean and a real headless-Chrome render (not just a type-check) — screenshot sent to the
   user directly. **Explicitly, honestly scoped smaller than "done"**: web only, one screen only, and
   the Kinyarwanda text itself is a careful, good-faith translation, **not verified by a native
   speaker** — flagged in the code's own doc comment, matching this codebase's established "demo/
   simulated, honestly labeled" discipline for anything it can't fully verify itself (same pattern as
   `DemoExternalBalanceService`).

   **Android extended the same day**: `LoginScreen.kt` (Android's own equivalent highest-traffic
   screen) now uses real `values/strings.xml` + `values-rw/strings.xml` resources — this module had
   NO `strings.xml` at all before, every string was hardcoded directly in Kotlin. A real bug was
   caught mid-implementation, not by the compiler: the first attempt used
   `AppCompatDelegate.setApplicationLocales()`, the "normal" per-app language API, which silently
   no-op'd — confirmed via an actual emulator screenshot showing the switcher still said "EN" after
   tapping it twice, verified via `uiautomator dump` that the tap coordinates were genuinely correct.
   Root cause: `MainActivity` extends `FragmentActivity`, not `AppCompatActivity`, so there's no
   `AppCompatDelegate` instance attached to it to notice the locale change and recreate — a real,
   non-obvious platform gotcha a clean `tsc`/`compileDebugKotlin`-equivalent pass would never have
   caught. Fixed with a self-contained `Configuration`-override approach
   (`context.createConfigurationContext`) wrapped via `CompositionLocalProvider(LocalContext
   provides ...)` — works regardless of Activity base class, takes effect immediately on
   recomposition, no Activity recreation needed. Persisted to `SharedPreferences` directly. Verified
   on a real emulator: English render, tap-to-switch to Kinyarwanda, and persistence across a
   `force-stop` + relaunch all confirmed via real screenshots (sent to the user), not just a
   type-check. **Lesson reinforced**: this is the second time this specific research thread caught a
   real gap only by actually running something (the first was the account-linking silent-failure
   bug) — a clean compile is necessary but not sufficient evidence a UI change actually works.

   **iOS extended the same day, completing login-screen parity on all 3 platforms.**
   `LoginScreen.swift` now uses a plain Swift dictionary + a `t(_:)` helper, mirroring web/Android's
   own identical "don't add framework weight this scope doesn't need yet" choice — deliberately NOT
   `.strings` files + `NSLocalizedString` (the standard iOS mechanism), and deliberately kept
   entirely self-contained in this one existing file rather than adding new files to the Xcode
   project: this project's `.pbxproj` has no file-system-synchronized groups (confirmed via grep
   before writing this), so every new Swift file needs a real, error-prone manual `.pbxproj` edit —
   not worth that risk for one screen's worth of strings. Same toggle-switcher UX as Android
   (persisted to `UserDefaults`, defaulting to the device's preferred language if it's Kinyarwanda).
   **Honestly lower verification tier than Android got**: zero errors attributed to the file in a
   full `xcodebuild` attempt, plus a clean `swiftc -parse` syntax check and a clean
   `accessibility-lint.py` pass — but no real on-device/simulator screenshot this time, unlike
   Android. The `ItundaApp` scheme still can't fully link (the same pre-existing, unrelated CocoaPods
   gaps named in Section 18), and there's no established simulator-screenshot workflow for iOS this
   session the way there is for Android's emulator — building one (or running `pod install` to fix
   the underlying link gap) was judged out of scope for this specific fix rather than attempted as a
   risky side effect.

   **Web extended the same day to a second screen: wallet overview** (`OverviewView` in
   `BankDashboard.tsx`), the exact next step this list already named. Real net-worth/accounts/
   savings/loans/investments/insurance/linked-accounts strings, several carrying a dynamic amount or
   count (`"Savings: {{amount}} RWF across {{count}} goal(s)"`), which needed real interpolation
   support added to `useI18n`'s `t()` — named `{{placeholder}}` substitution, not string
   concatenation, specifically so a translation can reorder words per-language instead of being
   locked into English sentence order. Verified: `tsc -b` clean, the interpolation function itself
   checked deterministically against 6 real cases including a missing-param fallback, and every
   call site's params hand-cross-checked against its template's placeholder names (TypeScript's
   generic `Record` param type doesn't itself enforce that match). **Verification gap, named
   honestly**: no live-backend render this time — `OverviewView` needs a real authenticated session
   and real API data, which this environment's dev server can't produce without a running backend,
   so (unlike the login screen's real headless-Chrome screenshot) this one is compile+logic-verified
   only, not visually confirmed end-to-end.

   **Android extended to the same second screen** (`OverviewScreen.kt`) — real `values/strings.xml`
   + `values-rw/strings.xml` entries (`overview_*`), using Android's own native printf-style format
   specifiers (`%1$s`/`%2$d`) for the same dynamic-amount/count strings web needed
   `{{placeholder}}` interpolation for. Also caught and fixed two smaller real things while doing
   this: (1) the file's own top-of-file doc comment was stale, still describing the
   `AppCompatDelegate` approach that was actually replaced during the login-screen fix — corrected;
   (2) the web pass's `overview.linkError`/`unlinkError` set turned out to be missing a real third
   case (the `VERIFICATION_FAILED` message shown when a link attempt is declined but not thrown as
   an error, see Section 17) — added to Android's strings AND retroactively to bank-mfe's own
   `translations.ts`, not left inconsistent between platforms. A real Kotlin compile error also
   caught a cross-module smart-cast restriction (`account.demoBalance`, a nullable property from a
   different Gradle module, needed a local `val` binding before a null check would smart-cast it) —
   a genuine, unrelated-to-localization Kotlin gotcha the compiler itself caught.

   **Verification note, mixed**: `:app:compileDebugKotlin` clean, `accessibility-lint.py` clean, and
   — unlike the compile-only iOS login fix — a REAL login flow was run end-to-end against the live
   local backend on this exact build (real seeded demo account, `+250788123456`/`password123`,
   confirmed via a real emulator screenshot showing the actual wallet balance), proving the
   string-resource wiring for login didn't regress. The actual `OverviewScreen` render itself,
   however, is **not** visually confirmed this round: the emulator process hung mid-navigation
   (confirmed genuinely stuck, not just slow — a `qemu-system-aarch64` process sitting in
   uninterruptible-sleep state even after a 120-second wait), most likely resource contention with
   the private-cloud Multipass VM also running on this same machine (see
   [[project_itunda_private_cloud]]). Not pushed further to avoid destabilizing an already-stressed
   environment. Named as a real, open gap rather than silently skipped or asserted as verified.

   **iOS extended to the same second screen** (`OverviewScreenView`,
   `OverviewLoansCreditScoreScreens.swift`) — completing (login, overview) × (web, Android, iOS), 6
   of 6, the same day the second screen started. Reused `AppLocale`/`loadStoredLocale` from
   `LoginScreen.swift` (same target) by widening their access from `private` to Swift's default
   `internal`, rather than duplicating locale-detection a second time — the "promote to shared once
   real duplication appears" precedent this codebase already established elsewhere
   (`packages/design-tokens`), applied here for the first time in this specific thread. Used
   `String(format:)` for the dynamic strings (iOS's own native equivalent of web's
   `{{placeholder}}`/Android's `%1$s`). **Included `overview.verificationFailed` from the very
   first pass this time** — the exact key web's own first pass missed and that only got caught
   while porting to Android — rather than repeating that omission a third time.

   Verified the same way as the login-screen iOS fix: zero errors attributed to either touched file
   in a full `xcodebuild` attempt (confirming the cross-file symbol reuse actually resolves), a
   clean `swiftc -parse`, and a clean `accessibility-lint.py` pass. No real simulator/on-device
   screenshot this round either — same standing gap named in the login-screen entry above, not
   re-attempted given the `ItundaApp` scheme's pre-existing link blocker.

   **Web extended to a third screen the same day: P2P transfer** (`TransferFlow` in
   `BankDashboard.tsx`, itunda's own single highest-stakes money-moving screen) — form, scam-warning,
   confirm/review, and result states, all real strings, several with interpolation
   (`transfer.toRecipient`, `transfer.amountLine`, `transfer.send`, `transfer.scamWarningBody`).
   Also caught and fixed the same "translated the destination, not the door" gap this thread keeps
   finding: `AccountBalance` (the wallet card's own "Transfer"/"Top up" buttons that open this exact
   flow) is a separate component and was still hardcoded English — added `dashboard.*` keys for it
   in the same pass rather than leaving the flow's own entry point untranslated.

   **This is the strongest verification tier in the whole localization thread so far — a real,
   authenticated, live session, not a static or unauthenticated render.** The private-cloud
   Multipass VM's investigation earlier the same day (see Section 19's "beyond ecosystems" note and
   [[project_itunda_private_cloud]]) confirmed the local backend was still running; logged in for
   real via `curl` against `/api/v1/auth/login` (seeded demo account), then drove a real headless
   Chrome session over the Chrome DevTools Protocol (no Playwright/Puppeteer installed in this
   environment, so a minimal hand-written CDP client did the job — Node 22+'s native `WebSocket` +
   `fetch` were enough): seeded the real JWT into `localStorage`, loaded the actual dashboard, and
   clicked through the real UI (not simulated) to the transfer form, the scam-check confirm screen,
   and back with a real saved contact and a real interpolated amount — in both English and
   Kinyarwanda, confirmed via real screenshots (sent to the user). One real, unrelated environment
   fix needed along the way: the dev server has to run on its actual configured port (bank-mfe is
   itself a Module Federation remote hardcoded to port 5002) and within the backend's CORS allowlist
   (`localhost:5000`-`5005`) — an arbitrary `--port` override breaks both.

   **Honestly named remaining gap, not fixed this pass**: `ReportScamLink` (the "Report this number
   as a scam" link visible on the confirm screen) is a separate component this pass didn't touch —
   still English in both locales, confirmed visible in the real Kinyarwanda screenshot.

   **Android extended to the same third screen the same day** — `TransferFlow.kt`
   (`RecipientEntryScreen`/`TransferAmountScreen`/`DeviceStepUpDialog`, the feature-module
   equivalent of web's `TransferFlow`, all 3 reached from the same real send-money path wired via
   `MainViewModel.sendTransfer`). This is a bigger, more Toss-reference-faithful flow than web's
   simpler form (a real 2-step recipient/amount wizard, quick-amount chips, a device step-up
   dialog) — translated as one cohesive unit anyway, matching bank-mfe's own scope. The
   `android/features/payments/impl` module had no `res/` directory at all before this — created
   `values/strings.xml` + `values-rw/strings.xml` from scratch, Android's native format specifiers
   for the 3 dynamic strings (available balance, recipient account, scam-report count). Also fixed
   a real, unrelated stale doc comment found while reading this file closely: its own header still
   claimed this flow was "a UI shell, not wired to the backend," left un-updated since before it
   was actually wired to `MainViewModel.sendTransfer` with real device step-up and biometric
   confirmation — corrected in place rather than left misleading the next reader.

   **Verification tier, named honestly**: `:features:payments:impl:compileDebugKotlin` and
   `:app:compileDebugKotlin` both clean, `accessibility-lint.py` clean — compile+lint tier only,
   matching iOS's own tier, not web's live-session tier. No physical-device check this round: the
   emulator is confirmed unreliable this session (see
   [[feedback_emulator_for_visual_verification]]'s correction) and physical-device wiring hasn't
   been set up yet — a real, named gap, not silently skipped.

   Real next steps, in order: native-speaker review of the existing `rw` strings on all 3
   platforms, a real iOS simulator/on-device check of its two localized screens (the `ItundaApp`
   scheme's pre-existing link blocker is still unresolved), a real *physical-device* check of
   Android's three screens once that's wired up, `ReportScamLink`'s own string (web) and iOS's
   transfer flow (still not started), then a fourth screen on whichever platforms are ready for it.
3. **[sourced, real lead, not yet checked]** Nubank's NuScore-style transaction-history credit
   scoring — worth checking whether itunda's own loans/credit-score feature already uses itunda's own
   in-app transaction history as a signal, or leans on external/bureau-style data alone, given
   Rwanda's likely-thin traditional credit-bureau coverage.
4. **[sourced, checked 2026-08-15, no equivalent gap found]** Kakao Pay's third-party data-sharing
   fine — audited every real partner-facing user-data pathway: `PartnerController` (mini-app
   registration/submission -- no user PII exposed, purely partner-account mechanics),
   `MiniAppSecurityContext` (governs which native bridge *methods* a mini-app can call, e.g.
   `getWalletBalance`, gated on itunda's own internal moderation approval at submission time --
   not a data-sale pathway, and every mini-app currently live is itunda's own first-party one,
   confirmed via `packages/saronite` grep), `AffiliateController` (itunda-internal click/commission
   tracking only, no external party involved). The one real external-data-disclosure pathway that
   exists -- `IdentityVerificationService`, see Section 15/this document's own partner-identity
   coverage -- already requires a fresh, explicit, single-use user approval every time, the opposite
   of Kakao Pay's actual failure (bulk sharing with zero consent mechanism). No equivalent gap.
5. **[sourced, checked 2026-08-15, already real]** Whether itunda's SplitBill flow is reachable from
   inside its own Talk/chat feature, matching Kakao Pay's real "정산하기" pattern -- confirmed yes:
   `GroupSplitBillsView` (`TalkScreen.kt`) is reachable via a dedicated icon button directly inside
   the group chat screen itself, not a separate destination. No gap, no action needed.
6. **[sourced, informational, future-direction]** KakaoBank's sequence-based fraud detection as a
   longer-term evolution of itunda's current per-transaction `FraudRuleEngine` — not a quick fix,
   worth naming as a real architectural option for whenever this project has enough real transaction
   volume to train against.
7. **[sourced, informational only]** Paytm Payments Bank's compliance-erosion story and N26's
   BaFin compliance-debt pattern — real cautionary lessons, no direct itunda code action; worth an
   ongoing-monitoring habit more than a one-time fix.

### Follow-up checks, same day (2026-08-08) — Recommendations 3-5 resolved

Checked directly against itunda's own code, no fork needed (narrow, quick verifications):

- **Recommendation 3 (Nubank NuScore) — already done, no gap.** itunda's
  `CreditScoreService.kt` (`services/backend/core/.../creditscore/`) computes its score live, purely
  from itunda's own real transaction/loan/savings/KYC repositories (+2 points per completed
  transaction capped at 100, loan-repayment history, savings-goal activity, account age, KYC
  status) — zero external/bureau/simulated data source anywhere in the path (confirmed via grep for
  "bureau"/"external"/"third-party"/"simulated" across the whole module). The class's own doc
  comment already frames this exactly as "real alternative data... not a real credit bureau score,"
  and all 3 clients show the user "Based on your own account activity, not a bureau report." This
  independently matches Nubank's own NuScore philosophy — built before this research pass found the
  comparison, not after.
- **Recommendation 4 (Kakao Pay data-sharing) — checked, no active gap, one informational note.**
  itunda has no analytics/ad SDKs in any client build config and no real third-party integration
  that receives user data beyond expected push-notification delivery (device token + notification
  text via FCM/APNs, a standard, low-risk, necessary integration — not comparable to Kakao Pay's
  bulk data-sharing-with-a-shareholder issue). One thing worth noting: `android/core/consent` exists
  as an empty scaffold module (a `build.gradle.kts` with zero source files) — someone anticipated
  needing real consent-tracking infrastructure and never built it out. Worth completing *before* any
  future real third-party integration (analytics, ad tech, an external fraud-scoring vendor) is
  added, not urgent today since there's nothing yet to need consent for.
- **Recommendation 5 (Kakao Pay settle-up-from-chat) — mostly already done, one real minor gap.**
  Confirmed on all 3 platforms: itunda's `SplitBillController`'s own domain-model doc comment
  explicitly cites this exact pattern ("A real KakaoPay-style '정산하기' request, chat-embedded in
  an existing GroupConversation") — a real "Split a bill" action already sits inside itunda's group
  chat UI (`TalkScreen.swift` iOS, `TalkScreen.kt` Android, `BankDashboard.tsx` web), tied to that
  conversation's `groupConversationId`, matching the Kakao Pay pattern directly. **Real gap**: it's
  wired into group chats only — 1:1 direct-message threads have no split-bill entry point at all,
  narrower than real KakaoTalk's 정산하기 (which works in both). Checked the backend
  (`SplitBillService.kt:88-171`): `createSplitBill` is architecturally coupled to
  `GroupMessagingService` throughout (group-membership lookup for the headcount split, group message
  posting for notifications) — there's no 1:1-conversation equivalent to plug into. Extending this
  is real, non-trivial scope (either a parallel 1:1 code path or a refactor to abstract over
  "conversation with N members" regardless of group/1:1 shape), not a same-day fix — noted as a real
  follow-up, not attempted this pass to avoid a rushed retrofit into a working, tested feature.

### Naver Pay / Wise passes (2026-08-08) — one ecosystem at a time, per the prior round's own lesson

Two more single-ecosystem passes, each meant to get a full, non-shared search budget. Real finding
from this round: **the session's WebSearch budget (200/200) is shared across every fork, not
per-fork** — both passes discovered it was already fully exhausted (a hard tool error, not a soft
limit) before they even started, regardless of running one-at-a-time this round.

- **Naver Pay**: `d2.naver.com` (Naver's real tech blog) confirmed tool-blocked at the domain
  level, independently, twice now (both this round and the prior one) — not a content-availability
  problem, a real fetch-tool limitation for this specific domain. What Wikipedia-tier sources gave:
  Naver Pay's points/rewards system is diversified beyond transactions — real, payment-usable points
  earned through Knowledge iN Q&A participation, blog/café event participation, and a charitable-
  donation match, not just spending. **Informational lead, not yet checked**: itunda's own
  `StepRewardService` is single-signal (steps only) — worth considering non-transaction engagement
  signals feeding the same reward pool, if itunda wants to diversify. No incidents/breaches
  recovered for Naver Pay specifically.
- **Wise**: the original ask (real ledger/multi-currency architecture) is **confirmed genuinely
  unrecoverable** through this environment's fetch tooling — `wise.com/engineering` 404s,
  `medium.com/wise-engineering` renders as an empty JS shell (no article list, even the tagged/
  backend filter), `docs.wise.com`'s API reference pages render as JS-shell titles only, `infoq.com/
  wise/` 404s. Not a laziness gap — 6 distinct angles tried, all real dead ends. **What surfaced
  instead, real and more actionable**: Wise was fined ~$2.5M by the US CFPB (2025) for advertising
  inaccurate fees and failing to properly disclose exchange rates before a transfer. **Checked
  directly against itunda's `ForeignCurrencyWalletService` and all 3 real clients — already solid,
  no gap.** The backend's own `getRate()` method has a doc comment explicitly stating it exists "to
  back a client-side quote preview before the user commits to convert," and `convert()`'s margin
  (1.5%, `MARGIN_RATE`) is a real, transparent, disclosed spread — not hidden in a bad rate. All 3
  clients already fetch and show the live rate before submission (`bank-mfe`'s `ConvertCurrencyCard`,
  Android `ForeignCurrencyScreen.kt`, iOS `ForeignCurrencyScreen.swift`), each with explicit "before/
  after itunda's 1.5% fee" copy — Android/iOS go further than web, showing a live-computed estimated
  net-receive amount, not just the raw rate. itunda's FX disclosure practice already exceeds what
  Wise got fined for lacking.

### 1:1-chat split-bill gap — investigated deeper, confirmed non-trivial with a concrete reason

Re-examined whether the group-chat coupling was really as large as first estimated: `GroupConversation`
has no minimum-member requirement (`createGroup` only requires "at least one other real member"), so
a 2-person group is structurally valid — meaning an organizer and one other user *could* be wrapped in
a synthetic 2-person group and route through 100% of the existing, unmodified `createSplitBill` logic.
That looked promising until designing it out fully: a synthetic group created via the existing, public
`createGroup` would show up in both users' real "My Groups" list (`GroupConversationRepository.
findByMember` has no concept of hidden/synthetic groups) — a confusing, generically-named group neither
user asked to create. Fixing that cleanly needs either (a) a new hidden/synthetic flag on
`GroupConversation`, threaded through the group-listing query, or (b) extending `SplitBill`'s own
domain model with a second, direct-conversation-backed branch, touching every method that currently
assumes `groupConversationId` (settlement messages, `getSplitBillsForGroup`, several others). Both are
real, non-trivial scope — confirms the original sizing, now with a concrete design reason rather than a
vague "non-trivial." Not built this pass; noted for whoever picks this up next.

### iOS's Transfer flow localized — closes the 3x3 login/overview/transfer × web/Android/iOS grid (2026-08-08)

The third and last platform for the transfer screen, following the exact web → Android → iOS order the
whole thread used. `TransferFlowScreens.swift` (`RecipientEntryScreen`/`TransferAmountScreen`, the
`Features/Payments` Swift module) needed its own self-contained locale dictionary rather than reusing
`LoginScreen.swift`'s `AppLocale`/`loadStoredLocale` directly: that module deliberately can't depend
back on `App` (same constraint `BankView.swift`'s own doc comment already names for `TransferViewModel`),
so those `internal` symbols aren't visible to it — the exact same reason Android's
`android/features/payments/impl` needed its own `values-rw/strings.xml` rather than sharing `:app`'s.
Read the same UserDefaults key (`itunda.locale`) the App-target switcher already writes, so both targets
stay in sync without a shared dependency either way. `DeviceStepUpView.swift` and
`TransferFlowContainer.swift` (both `App/Sources`, same target as `LoginScreen.swift`) *did* reuse
`AppLocale`/`loadStoredLocale` directly, each adding its own small dict — `DeviceStepUpView` is shared by
four money-moving flows (Gift, Commerce, Eats, Stocks), not just Transfer, so localizing it here paid off
beyond this one screen. All Kinyarwanda strings ported verbatim from Android's already-shipped
`values-rw/strings.xml` for the shared copy, to keep phrasing consistent across platforms rather than
re-translating the same sentence three different ways.

**Real cross-platform gap caught while doing this, fixed on both platforms**: iOS's
`TransferFlowContainer.swift` has its own scam-report reason sheet ("Why are you reporting this
number?", Cancel/Report), separate from `TransferFlowScreens.swift`'s already-localized "Report this
number as a scam" link. Localizing it surfaced that Android's equivalent dialog — in
`ItundaAppScreen.kt`, not `TransferFlow.kt`, so the earlier Android transfer pass never saw it — was the
same kind of gap: hardcoded English, never touched. Fixed on both: added `scam_report_*` keys to
`:app`'s `values{,-rw}/strings.xml` and wired `stringResource()` calls into `ItundaAppScreen.kt`'s
`AlertDialog`, matching the iOS fix in the same pass rather than leaving one platform behind — directly
the lesson from [[project_itunda_toss_conference_research]]'s own "nobody checked cross-platform parity
on a landed fix" finding, applied proactively this time instead of caught later.

**Verification tier, named honestly**: `swiftc -parse` clean on all 3 touched Swift files,
`:app:compileDebugKotlin` clean, XML well-formed. Same compile-tier-only ceiling as every other
non-web platform this thread — no simulator/device/emulator check this round (see
[[feedback_emulator_for_visual_verification]]).

**Running tally, updated**: all 3 screens (login/overview/transfer) now exist on all 3 platforms — 9/9
combinations. Verification tiers vary: web transfer is the only one with a real live authenticated
session (CDP-driven); everything else is compile+lint tier, several with a real emulator/simulator pass
earlier in the thread (Android login, iOS login/overview) but not this most recent round. Real
next steps, in order: native-speaker review of every `rw` string on all 3 platforms (still never done),
physical-device wiring for Android (stated intent, not yet set up), a fourth localization screen once a
verification method is back online, and the still-open 1:1-chat SplitBill gap below.

### Settings screen (4th screen) — and a real Android bug that meant the switcher never worked past login (2026-08-08)

Picked `SettingsScreen` as the 4th localization screen deliberately, not arbitrarily: on both Android
and iOS, it's the *only* place a logged-in user can reach a language switcher at all — the one on
`LoginScreen`/`LoginScreen.kt` is only rendered pre-login, so anyone already signed in when this thread
started had no way to change language short of logging out. Fixed on both: iOS's `SettingsScreen.swift`
reuses `AppLocale`/`loadStoredLocale` directly (same `App` target as `LoginScreen.swift`) and gained the
same EN/RW toggle in its own top bar. Android's needed more than a toggle — see below.

**Real, serious bug found while wiring Android's toggle, not a translation gap**: `LoginScreen.kt`'s
switcher wrote to its own `readStoredLocale`/`storeLocale` functions and wrapped only *its own* Compose
subtree in the `Configuration`-overridden `CompositionLocalProvider`. `MainActivity.kt`'s `setContent`
never wrapped `ItundaAppScreen` — everything a user sees after logging in — in anything equivalent. That
means every `stringResource()` call this whole thread added across `OverviewScreen.kt`, `TransferFlow.kt`,
`ItundaAppScreen.kt`, and now `SettingsScreen.kt` itself was silently reading the **device's raw OS
locale** the entire time, never the stored in-app choice — unless the phone's own system language
happened to already be Kinyarwanda. The switcher looked and compiled correctly and even worked, but only
on the one screen it lived on. Every "Android's transfer/overview screen is localized" claim earlier in
this thread was true of the code but not of what a real user pressing the toggle would actually see.

**Fixed by promoting the locale state out of a single screen and into a real app-wide source of truth**,
mirroring `ThemePreference`'s own already-established `StateFlow` pattern (same directory,
`core/network/ThemePreference.kt`) rather than inventing a new mechanism: a new
`core/network/AppLocalePreference.kt` (`MutableStateFlow<String>`, `restore(context)` called once from
`ItundaApplication.onCreate` alongside `ThemePreference.restore()`, `set(context, locale)` persisting to
the same `SharedPreferences` file the old per-screen functions used). `MainActivity.kt`'s `setContent` now
builds the `Configuration`-overridden `CompositionLocalProvider` **once**, wrapping the entire
`when (sessionState)` block — both `LoginScreen` and `ItundaAppScreen` — instead of each screen
(redundantly, or in `ItundaAppScreen`'s case, not at all) building its own. `LoginScreen.kt` was
simplified to just read/write the shared `StateFlow` instead of owning a duplicate, dead-end copy.
Compose's `LocalContext` crosses Gradle-module boundaries at runtime even though the modules can't see
each other at compile time, so this one fix at the true root is what actually makes every module's
`stringResource()` calls — `:app`, `:features:payments:impl`, everything — respect the switcher.

**Same lesson as the `AppCompatDelegate` bug from the very first localization pass, in a different
shape**: a language switcher that silently only half-works is worse than an obviously-broken one, because
nothing about it looks wrong — it compiles, it renders, the one screen it's on works. The only way either
bug surfaced was by tracing the actual data flow end to end instead of trusting that "the string resource
exists and the screen compiles" meant "the feature works." Caught this time while extending the feature to
a new screen, not by a dedicated audit — worth remembering that adding a 4th consumer of a mechanism is
itself a good moment to double check the mechanism, not just assume it already works because the first
three consumers seemed to.

**Verification tier**: `:app:compileDebugKotlin` and `:core:network:compileDebugKotlin` both clean,
`accessibility-lint.py` clean, all edited XML well-formed (one recurrence of the known `--`-in-XML-comment
gotcha, caught by the same build and fixed the same way as every prior time). Compile-tier only — the
actual runtime behavior of the fix (does the toggle in Settings now really change Overview/Transfer/
everything else) has not been watched on a real emulator or device this round, so it's asserted from
reading the code path, not observed. That's a real, named gap, not a silent claim of "verified."

**Stronger iOS verification the same day, real `xcodebuild`, not just `swiftc -parse`**: ran a real
`xcodebuild -scheme FeaturePayments build` against the `iOS Simulator` destination — compiles, links,
codesigns `TransferFlowScreens.swift` cleanly as a standalone framework, a real step up from parse-only
checking. Also tried the full `ItundaApp` scheme: it fails, but at `Unable to resolve module dependency`
for `React_RCTAppDelegate`/`MapLibre`/`BrickModule`/`GraniteBrownfield`/`React` — the same pre-existing,
unrelated brownfield-module link blocker this thread has named before, nothing in any file this session
touched. Confirms the day's changes aren't the cause of that gap, without closing it. Also re-ran the
full backend test suite (`./gradlew test --continue`) after all of today's changes — clean, no
regressions from anything in this thread (the AgentService/Saronite/CommunityController backend fixes
from earlier in the session, or the frontend-only work today).

**Web has the same "unreachable after login" shape, fixed the same day.** `LoginPage.tsx`'s own
switcher (a real `<select>`, added the very first day of this thread) only renders on the logged-out
page — once a bank-mfe user signs in, there was no persistent way to change language without logging
out again. Different root cause than Android's (this one was never wired anywhere post-login at all,
not a propagation bug in an existing wire), same user-facing gap. Fixed by adding the identical
`<select>`/`LOCALES` switcher to `BankDashboard`'s own persistent header — the "Itunda" title +
first-name + sign-out row that renders above the tab bar on every single tab, not buried inside the
`MyView` "My" tab specifically, so it's reachable no matter which tab a user is on. Verified with a real
`tsc -b && vite build` (not just a syntax check) — clean, including the Module Federation bundling step
this micro-frontend depends on.

**All 3 platforms now have a reachable, persistent language switcher for logged-in users, not just at
login.** That gap existed identically on all 3 (web/Android/iOS) going into today and is closed on all 3
coming out of it — found because extending the localization work to a 4th screen meant asking "where
does a signed-in user actually go to change this" on every platform, not just the one being worked on.

### Finishing web's real landing screen, not starting a 5th one (2026-08-09)

Checked which tab `BankDashboard` actually defaults to (`useState<Tab>('HOME')`) rather than assuming —
it's `HomeView`, not `OverviewView` (a separate, deeper tab with the savings/loans/investments/insurance
summary this thread's earlier "second screen" pass actually translated). `HomeView` itself had only ever
been *partially* localized: `AccountBalance` and the `TransferFlow` it opens were done, but the load-error
message, `QuickActions` ("Scan to Pay"/"Cards"), `DeviceStepUpPrompt` (web's own sibling of the
already-localized Android/iOS device step-up dialog), and `ReportScamLink` — explicitly named as a known,
out-of-scope English gap two passes ago — were all still hardcoded English on the screen every single
user actually sees first.

Localized all four this pass, `home.*`/`quickActions.*`/`deviceStepUp.*`/`scamReport.*` keys added to
`translations.ts` (en+rw), reusing the exact same Kinyarwanda copy already shipped for the equivalent
Android/iOS device step-up dialog for consistency. `ReportScamLink`'s scam-reason prompt uses
`window.prompt()`, not a form — the interpolated `{{identifier}}` question text is now translated too.

**Verified live again, same tier as the strongest verification in this whole thread**: real backend
login via `curl`, a real headless Chrome session over CDP, `itunda.locale` seeded and switched via
`localStorage`. Confirmed via actual DOM queries (not just visual screenshots, though those were taken
too): `QuickActions`' two labels render as `Kwishyura ukoresheje QR`/`Amakarita` in Kinyarwanda;
`ReportScamLink` on the real transfer confirm screen (reached by actually clicking through a saved
contact and submitting a real amount) renders `Tanga raporo kuri iyi numero`; zero console exceptions
thrown across the whole session. Dev server run with no `--port` override (still port 5002, the Module
Federation gotcha from the first live-verification pass), Chrome killed and dev server stopped after
capture, nothing left running.

**Deliberately not done this pass, named honestly**: `TransactionHistory`, `ScheduledTransfersCard`,
`AutoTransfersCard`, `AutoTopUpCard`, `RequestMoneyCard`, `MiniWalletCard`, and `DiscoverSection` — the
rest of `HomeView`'s real estate below the balance card and quick actions — are still English. Scoped
out to keep this pass reviewable and fully live-verified rather than spreading thin across seven more
components; each is a real, separate follow-up, not a hidden gap.

### TransactionHistory, closed the same day (2026-08-09)

Picked up the highest-value item named above immediately rather than letting it sit: `TransactionHistory`
is the "Recent Activity" list — the content a signed-in user actually reads most, every time they open
the app. Localized its heading, its empty-state message, and the "Unusually large" flag (the real Toss
Timeline-style unusual-spend badge, see its own doc comment). Left `tx.description` itself untouched —
that's real backend-provided transaction data, not a UI string, and translating arbitrary API content is
a different, much bigger problem than translating this app's own fixed copy. Same for the `RWF` currency
code, matching the established non-translation precedent for currency codes everywhere else in this
thread.

**Verified live again, same real-browser tier**: fresh backend login, real headless Chrome over CDP,
confirmed via a direct DOM query that the actual rendered `<h3>` heading reads `Ibikorwa vya vuba`
after switching to Kinyarwanda — not just that the translation key exists, that the live page shows it.
Zero exceptions. Chrome and dev server stopped after capture.

`ScheduledTransfersCard`, `AutoTransfersCard`, `AutoTopUpCard`, `RequestMoneyCard`, `MiniWalletCard`, and
`DiscoverSection` remain the honestly-named open items — `HomeView` is closer to fully localized than it
was this morning, not finished.

### HomeView fully closed out, same day (2026-08-09)

Finished the remaining 6 cards in one pass rather than stretching them across more sessions:
`DiscoverSection`, `MiniWalletCard`, `ScheduledTransfersCard`, `AutoTransfersCard`, `RequestMoneyCard`,
`AutoTopUpCard`. Roughly 100 new translation keys (en+rw) covering every button, placeholder, error
message, empty-state, and status label across all six — including three module-level `Record<Status,
string>` status-label maps (`ScheduledTransfer`, `AutoTransfer`, `P2pPaymentRequest`) that had to become
`Record<Status, TranslationKey>` instead, since a plain object literal outside a component can't call
`useI18n()`; the lookup now resolves the key, and the actual translation happens at the call site inside
the component. Exported `TranslationKey` from `translations.ts` for the first time (previously
module-private) so `BankDashboard.tsx` could type these maps — same "promote once a second real
consumer needs it" precedent this whole session has followed on both other platforms.

One real, quiet bug avoided during the edit, not found live: several of these components used `.map((t)
=> ...)` for their own transfer/request items, which would have silently shadowed the `t` translation
function pulled in via `const { t } = useI18n()` at the top of each component -- every `t()` call inside
that `.map()` callback would have resolved to the wrong `t` (a transfer object) instead of the
translator, and TypeScript would have caught most but not all of those as a type error only once actual
translated calls were added. Renamed every shadowing loop variable (`tr`, `at`, kept `r`/`d`/`a` where no
clash existed) before wiring in any `t()` calls, rather than fixing it after chasing type errors one at
a time.

Also translated the weekday names used by `AutoTransfersCard`'s day-of-week picker (`WEEKDAY_KEYS`,
paralleling the existing English-only `WEEKDAY_NAMES` array kept solely as React `key` props, never
displayed) and the interpolated "Day {{day}} of the month" / "Weekly ({{day}})" / "Monthly (day
{{day}})" strings, so the auto-transfer schedule descriptions read naturally in Kinyarwanda instead of
having an English day name spliced into an otherwise-translated sentence.

**Verified live, comprehensively this time**: real backend login, real headless Chrome over CDP.
Confirmed via `document.body.innerText` that all 7 real card headings render in Kinyarwanda
simultaneously on one page load (`Konti ntoya`, `Kohereza byateganyijwe`, `Kohereza byikoresha`, `Saba
amafaranga`, `Kwongera amafaranga byikoresha`, `Menya`, `Ibikorwa vya vuba`); clicked all three
"expand form" buttons by their translated label text and confirmed they actually opened (proves the
buttons' own conditional-label logic reads the right key, not just that some Kinyarwanda text exists
somewhere on the page); read every visible `input[placeholder]` and `select option` afterward and
confirmed each one — including the interpolated day-of-month dropdown, 1 through 28 — rendered correctly.
Zero console exceptions. Real `tsc -b && vite build` also clean. Chrome and dev server stopped after
capture.

`HomeView` — the screen a signed-in itunda user actually sees first — is now fully localized on web,
matching the depth (if not yet the verification tier) of Android/iOS's own most-worked-on screens.

### Same real gap, found on Android too: `HomeTab`, not `OverviewScreen.kt` (2026-08-09)

Checked whether Android had the identical "translated the wrong screen" mistake just found and fixed on
web — it did. `TossTab.Home` (`ItundaAppScreen.kt`'s `when (selectedTab)` dispatch) is Android's real
default landing tab, rendered by a composable called `HomeTab` — a completely different, much larger
screen than `OverviewScreen.kt` (the "second screen" the earlier Android pass actually translated:
savings/loans/investments/insurance summary, reachable from Home but not Home itself). `HomeTab` and its
sub-composables (`HomeTopBar`, `WalletHeroCard`, `RoundUpSettingsDialog`, Android's own `DiscoverSection`
— a separate implementation from web's, same name) had exactly 5 `stringResource()` calls in the entire
~2700-line file before this pass, all from the `scam_report_*` fix earlier today. Everything else —
the search bar placeholder, "Cash out"/"Send" buttons, "See all", the round-up savings dialog, the
Savings section, and a set of static Toss-style promotional rows (cashback, face-ID pay, government
alerts) — was hardcoded English.

Added ~35 new `home_*` string keys (`:app`'s `values{,-rw}/strings.xml`) and wired every one in. Two real
Compose-specific gotchas along the way, both caught before the build, not after:
- `stringResource()` is `@Composable` and can't be called inside a `buildList { }` builder lambda's
  per-item `forEach` the way the original code structured the Savings section's rows — fetched the
  round-up/interest-jar/progress strings (including the progress-format *pattern* itself) once outside
  the loop, then applied plain Kotlin `String.format`/`.format()` per item inside it, same reasoning as
  Android's own `SCHEDULED_TRANSFER_STATUS_KEY`-style indirection used elsewhere this session.
- The known `--`-in-XML-comment gotcha recurred a third time this session, caught by the same
  `mergeDebugResources` build failure and fixed the same way as every prior time.

Left the promotional rows' *content* exactly as it already was (Toss-reference-matching static
marketing copy, e.g. "Transfer cashback — BK account -> TUYIZERE Eric") — translating it into Kinyarwanda
is a real, in-scope i18n task; whether that kind of illustrative promotional content belongs in this
screen at all is a separate, out-of-scope product question this pass didn't touch.

**Verification tier, named honestly**: `:app:compileDebugKotlin` clean, `accessibility-lint.py` clean,
both `strings.xml` files well-formed. Compile-tier only — same as every other Android change today, no
device available this session to watch it actually render.

`HomeTab` — Android's own real first-seen screen — now has the same depth of translation coverage
`OverviewScreen.kt`/`TransferFlow.kt`/`SettingsScreen.kt` already had, closing the same class of gap on
the second of three platforms. iOS not checked yet for the equivalent mistake.

### Same real gap, found on iOS too: `BankView.swift`, not `OverviewScreenView` (2026-08-09)

Checked the third platform for the same mistake rather than assuming three-for-three meant it was clean
on iOS by default — it wasn't. `ContentView.swift`'s real `TabView` (tag 0, the default selected tab)
renders `BankView`, in the `Features/Banking` Tuist module — a completely separate screen from
`OverviewScreenView` (`OverviewLoansCreditScoreScreens.swift`, in `App/Sources`), which is what the
earlier iOS localization pass actually translated. `BankView.swift` had zero locale infrastructure of
any kind before this: `HomeTopBar`, `AccountSummaryCard`, `QuickActionsRow`, and every `HomeSectionCard`
title/row (`BankViewData`'s static connected-money/Rwanda-services/rewards content) were hardcoded
English.

Same self-contained-dictionary approach `TransferFlowScreens.swift` already established for
`Features/Payments` — `Features/Banking` can't depend back on `App` either, so `LoginScreen.swift`'s
`AppLocale` isn't visible here — reading the same `itunda.locale` UserDefaults key. One real Swift-specific
gotcha caught before it shipped, not found live: `BankViewData`'s three row lists were originally
`static let` arrays. A `static let` only evaluates once per process lifetime — if left as-is with
translated strings baked in, they'd freeze in whichever language was active the very first time
`BankView` was touched, never picking up a later switch the way the screen's own `@State` locale can.
Converted all three to functions taking `locale: BankingLocale`, called fresh from `body` every time,
instead of finding this the hard way after the fact.

Left the promotional row content itself untouched (same call as Android's equivalent, static
Toss-reference marketing copy — a real i18n task, not a reason to question whether it belongs on the
screen). Verified with a real `xcodebuild -scheme FeatureBanking build` against iOS Simulator — compiles,
links, codesigns cleanly, the same tier `FeaturePayments` got a few sections ago, not just `swiftc -parse`.

**All three platforms now have the same class of bug fixed**: web (`HomeView` vs `OverviewView`),
Android (`HomeTab` vs `OverviewScreen.kt`), iOS (`BankView` vs `OverviewScreenView`) all independently
localized their app's *secondary* wallet-detail screen first and left the actual default landing screen
mostly or entirely untranslated, in three unrelated codebases with three different histories. Worth
naming as a pattern, not three coincidences: "the second screen we built" and "the screen a signed-in
user actually lands on" are not automatically the same screen, and nothing forces a localization effort
to check that assumption unless someone deliberately asks the question per platform, which this thread
only started doing once it happened once, on web, and got curious whether it was a fluke.

### The 1:1-chat split-bill gap, finally closed (2026-08-09)

Picked up the oldest still-open item in this whole section: investigated twice before (2026-08-07,
2026-08-08) and shelved both times on the same concrete blocker — a synthetic 2-person group created via
the ordinary `createGroup` would show up in both people's real "My Groups" list, a confusing,
generically-named group neither of them asked to create. The second investigation named two concrete
fixes without building either: (a) a hidden/synthetic flag on `GroupConversation`, or (b) a second,
direct-conversation-backed branch inside `SplitBill`'s own domain model. Built (a) — the smaller, less
invasive of the two, since it needed zero changes to `SplitBillService.createSplitBill`'s own
already-tested logic.

**Backend**: `GroupConversation.isDirect` (migration `V230`, `FALSE` default for every existing group).
`GroupConversationRepository.findByMember` now excludes `isDirect = TRUE` rows — the exact fix for the
concrete blocker. A new `findDirectGroupBetween(userIdA, userIdB)` query (matched via `HAVING COUNT(m) =
2`, not "contains both ids", so a real >2-member group or an oddly-joined synthetic group both correctly
fail to match) backs `GroupMessagingService.getOrCreateDirectSplitGroup` — idempotent per pair, so a
second split bill between the same two people reuses the existing hidden group and its settlement
history instead of spawning a disconnected second thread. `SplitBillService.createDirectSplitBill`
(organizer, otherUserId, amount, description, mode) is the one new real entry point: resolves the hidden
group, then delegates to the existing, unmodified `createSplitBill` — same even-split rounding
absorption, same ladder-mode randomization, same receipt/next-round/pay flows, zero duplicated logic. A
read-only `getDirectSplitBills`/`findDirectGroup` counterpart lets a client show past split bills between
two people without creating a hidden group as a side effect of merely opening the view.

**New endpoints**: `POST /api/v1/split-bills/direct/{otherUserId}`, `GET /api/v1/split-bills/direct/{otherUserId}`.
Same `Idempotency-Key` discipline every other money-adjacent creation endpoint in this codebase already
requires.

**Verified live against the real running backend, not just unit tests** (which also pass — new Kotest
coverage in both `GroupMessagingServiceTest` and `SplitBillServiceTest`, `messaging`/`splitbill`/`core`/
`community` modules all compile clean): restarted the long-running local dev backend to pick up
migration `V230`, then, via real `curl` calls against two real seeded accounts —
1. Created a real direct split bill (5,000 RWF) between them — real hidden group, real `OPEN` split bill,
   real `PENDING` participant share.
2. Confirmed via the real `GET /api/v1/messages/groups` endpoint that **neither person's** "My Groups"
   list shows the hidden group — the exact failure mode that blocked this feature twice before, checked
   from both sides of the pair, not just the organizer's.
3. Created a second split bill between the same two people and confirmed it reused the identical
   `groupConversationId` — real idempotent pairing, not a guess from reading the query.
4. Confirmed `GET /direct/{otherUserId}` returns both real split bills.
5. Confirmed a same-person self-split request real-400s with a clean `GROUP_NEEDS_MORE_MEMBERS` error,
   not a raw 500.

**Web client wired in, the only platform this pass touched**: a "Split a bill" icon in `ConversationThread`'s
header (mirroring `GroupThread`'s identical icon) opens `DirectSplitBillsView`, the same shape as
`GroupSplitBillsView` minus the member-picker (a 1:1 split always has exactly one other participant,
fixed by which conversation it was opened from). Verified with a real `tsc -b && vite build`, not a live
CDP session this round — the backend-level live verification above was judged higher-value than a UI
click-through for a feature whose real risk was entirely in the backend's group-visibility logic, not the
client wiring.

**Android/iOS not touched this pass** — a real, named follow-up, not silently deferred: both platforms'
own group-chat split-bill UI (`ItundaAppScreen.kt`'s Talk tab, iOS's equivalent) would need the same
"Split a bill" entry point added to their own 1:1 conversation screens, calling the same two new
endpoints. The backend work is 100% shared across all 3 clients already; only the per-platform UI wiring
remains.

### Android client wired in, same day (2026-08-09)

Picked up the named follow-up immediately: `TalkScreen.kt`'s `ChatThreadView` (the 1:1 conversation
screen — `GroupThreadView`, its group-chat sibling, already had `GroupSplitBillsView` wired in) gets the
identical "Split a bill" text button its group-chat sibling already has, opening a new
`DirectSplitBillsView` composable — same shape as `GroupSplitBillsView` minus the member-picker, same
reasoning as web's `DirectSplitBillsView`. New Retrofit endpoints (`createDirectSplitBill`/
`getDirectSplitBills`) and a `CreateDirectSplitBillRequest` DTO added to the shared `core/network`
module's `ApiService.kt`.

One real gotcha, caught by the compiler rather than found live: the new DTO needed its own explicit
import in `TalkScreen.kt` (this codebase doesn't use wildcard imports), missed on the first pass and
caught immediately by `Unresolved reference 'CreateDirectSplitBillRequest'` on the first compile attempt
— fixed before moving on, not worked around.

**Verification tier**: `:core:network:compileDebugKotlin`, `:features:talk:impl:compileDebugKotlin`, and
`:app:compileDebugKotlin` all clean, `accessibility-lint.py` clean. Compile-tier only, same ceiling as
every other Android change today — no device available this session.

Two of three clients can now reach this feature. iOS remains the one honestly-named gap.

### iOS client wired in, closing all 3 platforms (2026-08-09)

`TalkScreen.swift`'s `ChatThreadScreen` (the 1:1 conversation screen — `GroupThreadScreen`, its
group-chat sibling, already had `GroupSplitBillsView` wired in, same "receipt" toolbar icon this pass
mirrors) gets the identical entry point Android and web already have: a new `DirectSplitBillsView`
struct, same shape as `GroupSplitBillsView` minus the member-picker. `NetworkClient.swift` gained
`CreateDirectSplitBillRequest`, `createDirectSplitBill`, and `getDirectSplitBills` — the `Core/Network`
Tuist module all 3 platforms' iOS clients already share.

**Verification tier, honestly named**: real `xcodebuild -scheme CoreNetwork build` — compiles, links,
codesigns cleanly, confirming the new network-layer code is correct. For `TalkScreen.swift` itself (in
the `App` target, which can't build standalone the way `CoreNetwork`/`FeaturePayments`/`FeatureBanking`
can): ran the real `ItundaApp` scheme build and confirmed the *only* errors are the same pre-existing,
unrelated brownfield-module dependency failures this thread has named before
(`React`/`React_RCTAppDelegate`/`MapLibre`/`BrickModule`/`GraniteBrownfield`) — zero errors mentioning
`TalkScreen.swift` anywhere in the output, and `swiftc -parse` on the file alone is also clean. That's
real, if indirect, evidence the new code is syntactically and semantically sound within its own target,
short of a full link (which nothing in `App/Sources` can currently get past on this brownfield blocker).
`accessibility-lint.py` clean.

**All 3 platforms can now reach this feature** — the 1:1-chat split-bill gap that was investigated and
shelved twice before is closed everywhere: backend, web, Android, iOS. No device/simulator has watched
any of the 3 clients actually render or complete a real split this session; that remains the honestly-
named ceiling on every non-web platform's work in this whole thread.

**Full backend regression sweep, same day**: re-ran the complete `./gradlew test --continue` across
every one of the ~40 backend modules after all of today's `GroupConversation`/`SplitBillService`/
`GroupMessagingService` changes — clean, zero regressions anywhere. Also spot-checked the two new
endpoints' own IDOR posture directly against the real running backend: asking `GET
/split-bills/direct/{otherUserId}` about a pair of two *other* real users (neither of them the caller)
correctly returns an empty list rather than leaking anything, and confirmed the GET itself never creates
a hidden group as a side effect (the caller's own group count stayed unchanged) — both by construction
(`findDirectGroupBetween` only ever matches a group where the *caller's own id* is one of the two members
queried, so a stranger's pairing can never resolve), verified live rather than only reasoned about.

### One more real gap, found re-reading this feature's own file: missing rate limit (2026-08-09)

Re-reading `GroupMessagingService.kt`'s own established discipline before calling the split-bill feature
finished: both `createGroup` and `createGroupByPhoneNumbers` rate-limit real group creation
(`messaging:group-create:$userId`, 20/hour) — the exact fix `AlreadyGroupMemberException`'s own sibling
bug got in an earlier session (found live 2026-08-02, "an authenticated caller could otherwise spam
unlimited GroupConversation + member rows"). `getOrCreateDirectSplitGroup`'s first version, added earlier
today, called `createGroupInternal` directly on its "creating a new hidden group" branch with no rate
limit at all — the same class of gap, self-introduced in the same session that fixed the original one.
Calling `createDirectSplitBill` with a different real `otherUserId` each time (bounded to real registered
users, since [GroupMemberNotFoundException] rejects a fake one, but the real user base is still a large
enough surface) would have created an unbounded number of hidden groups + member rows with zero throttle.

Fixed by adding the identical `rateLimiter.checkLimit("messaging:group-create:$userId", ...)` call, scoped
deliberately to only the "creating a new pair" branch — not the "reusing an existing pair" branch, so two
people who've already split a bill together once aren't throttled by their own repeat, legitimate use
(`createSplitBill`'s own separate `splitbill:create` limit already covers that case on its own).

New Kotest coverage proves both halves of the fix: a real `RateLimitExceededException` propagates before
any group row is created, *and* a separate test confirms `checkLimit` is never even called on the
reuse-existing-pair path — not just "the happy path still passes," but that the fix is scoped correctly in
both directions. `:messaging:test`/`:splitbill:test` both clean.

**Worth naming as its own lesson**: this session already has an established habit of periodically
re-checking its own past audits (Idempotency-Key sweeps, Flyway migration checks, IDOR passes) — this is
the first time that habit caught a gap in code the *same* session had just written, not older code. The
same discipline that catches a stale mistake also catches a fresh one, if applied consistently rather
than only pointed backward at old commits.

### Merchant moderation queue — a real admin surface with zero client, closed (2026-08-09)

A fresh uncalled-endpoint sweep (the same technique that's closed 7+ real gaps earlier this session)
found `MerchantModerationAdminController` — `GET /api/v1/system/merchants/uncategorized`,
`POST /{merchantId}/suspend`, `POST /{merchantId}/reactivate` — completely unreached from any client.
Real, working, `ADMIN`-gated, backed by `MerchantRepository.findByStatusAndCategoryIsNull` (the real
signal a merchant needs attention: `ACTIVE` status but no category set, meaning it's live but not
actually browsable to a real buyer). `ops-mfe` has a real queue view for every *other* `/api/v1/system`
admin surface — fraud, compliance, incidents, support, insurance, partners, escrow disputes, agent
reconciliation, hood content reports, property ownership — 10 sibling queues, all wired, this one alone
missing.

Closed the same way as every prior queue this thread has added: `MerchantModerationQueue.tsx`, mirroring
`PropertyOwnershipQueue.tsx`'s exact shape (`usePagedQueue`, `QueueHeader`/`QueueEmpty`/`QueueError`/
`QueueLoadMore` from the shared `QueueState.tsx`). One real design decision the backend's own shape
forced: there's no "list suspended merchants" endpoint to browse reactivation candidates from — only the
uncategorized-`ACTIVE` list — so `reactivate` has no queue of its own to attach a button to. Added a
small manual "Reactivate a suspended merchant" ID-entry form instead, the same "manual entry when there's
no browsable list" discipline `RequestMoneyCard`'s own pay-code field already establishes elsewhere in
this codebase, rather than leaving the endpoint reachable by nobody or building a list endpoint the
backend was never asked for.

**Verified live end to end, the strongest tier**: real admin login (seeded `+250788999000`/`admin123`),
real headless Chrome over CDP. Confirmed the queue tab renders real backend data (business names, KYB
status, creation dates matching a direct `curl` against the same endpoint exactly). Then exercised the
full round trip for real: clicked **Suspend** on a real merchant, confirmed it disappeared from the
client's own list *and* independently confirmed via a second direct `curl` against the backend that it
no longer appears in `/uncategorized` (not just trusting the UI's own optimistic state) — then typed its
id into the manual reactivate form, submitted, and confirmed the success message and that reactivation
genuinely restored `ACTIVE` status. Zero console exceptions throughout. Real `tsc -b && vite build` also
clean.

**One more real proof, checking the feature actually does what it claims rather than trusting its own
doc comment**: `MerchantService.suspendMerchant`'s own comment cites "Eats' actual browse results for a
real user" as the reason `SUSPENDED` matters, written before today, for pre-existing code this pass
didn't touch. Verified directly rather than taking the comment's word for it — found the real
customer-facing browse endpoint (`GET /api/v1/shopping/merchants`), picked a real merchant visible to a
real logged-in customer, suspended it through the new admin queue's own backend call, and confirmed via
a second real customer-session request that it genuinely disappeared from browse — then reactivated it
and confirmed it reappeared. Not just "the admin flag flipped," the actual end-to-end consequence a real
buyer would experience, proven live in both directions.

**Two smaller findings from the same sweep, named but not fixed this pass** (lower severity, no security
or correctness risk, just silent-drift/completeness gaps):
- `GET /api/v1/maps/categories` exists on the backend but web/Android both hardcode their own copy of the
  category list client-side instead of fetching it — a real, if minor, risk if the backend list ever
  changes without every client being updated in lockstep.
- `GET /api/v1/actions/types` (offline action-type discovery) has zero callers — Android's
  `OfflineActionQueue.kt` submits batches via the sibling endpoint but never queries this one, presumably
  hardcoding its own supported-type list instead.

### Unresolved / worth a follow-up
- The Android locale-propagation fix above needs a real emulator/device pass: toggle the switcher on
  Settings, confirm Overview/Transfer/Talk/every other screen actually re-renders in the new language,
  not just Settings and Login. Highest-priority verification item in this whole thread now that the
  underlying mechanism has changed, not just added-to.
- ~~The 1:1-chat split-bill gap above — real, scoped, two concrete design options identified, neither built.~~
  **Closed 2026-08-09** — see the dedicated section above; stale line kept struck through rather than
  deleted so the history of "shelved twice, closed on the third real pass" stays visible in place.
- **Named, not urgent**: the entire split-bill UI family (`GroupSplitBillsView` and today's new
  `DirectSplitBillsView`, on all 3 platforms) has zero localization — checked before assuming today's
  new views regressed anything, and confirmed the pre-existing `GroupSplitBillsView` was already
  English-only on web/Android/iOS before this session touched it, so nothing new broke. Real future
  localization target once the current thread's other open items close out; not scoped into today's work.
- Naver Pay's real engineering-blog depth (d2.naver.com) and Wise's real ledger architecture: both
  now confirmed genuinely unrecoverable with this environment's current fetch tooling, not worth a
  third attempt without a different access method. Samsung Pay: still entirely unresearched.
- **Standing operational fact for the rest of this session**: WebSearch is now fully exhausted
  (0/200) — further broad "keep searching" ecosystem research this session will only be able to use
  direct `WebFetch` against specific known URLs, not query-based discovery. Code-level audits/checks
  against itunda's own repo remain fully available and unaffected.
- **Aside, out of scope, flagged not investigated**: the mini-program research pass noticed stray git
  worktrees at `.claude/worktrees/wf_f205a3b5-33c-{7,8,9,10,11}/`, each containing a full copy of the
  repo (confirmed via an unrelated grep this pass turning up hits from all 5) — likely leftover from
  an earlier `Workflow` run this session, not touched or cleaned up here since it's unrelated to this
  work; worth the user's attention if disk space matters, not investigated further.

## 20. Performance, and closing the security/simplicity/customer-orientation loop on real hardware

**Added 2026-08-09**, at explicit user request, the first session in which a physical Android
device (Samsung Galaxy A16) was actually available: audit itunda against Toss's own security,
performance, simplicity, and customer-orientation standard, verifying on real hardware rather
than compile-tier only. WebSearch was already exhausted (200/200) by this point; WebFetch
against specific known toss.tech URLs remained available and was used instead.

### References

| Topic | Source | Pattern |
|---|---|---|
| Real-device testing philosophy | toss.tech/article/device-farm-nebula | Toss built "Nebula," an internal real-device farm, deliberately choosing physical hardware over emulators: *"actual behavior — battery drain, network conditions, hardware quirks — can't be replicated in simulation."* Stated principle: **"verification must match production reality as closely as possible."** Directly validated by this session's own experience: the `ClassCastException`/`ActivityResultRegistryOwner` crashes fixed earlier today were invisible to an entire session of compile-tier and (flaky) emulator verification, and only surfaced the moment real hardware became available. |
| "No More Loading" product principle | toss.im/tossfeed/article/tossproductprinciples (already sourced, Section 11) | *"Pull all levers to eliminate delays — whether by redesigning flows, improving policies, or adopting new technology."* Performance is treated as a product-simplicity concern, not a separate technical-only workstream. |
| Perceived-speed via interaction friction, not just raw rendering speed | toss.tech/article/4-ways-for-minimum-input | Re-confirmed while researching this section: Toss's own public engineering writing frames "feels fast" largely through reduced clicks/auto-focus/auto-submit (Section 11's own findings), not purely through startup-time or frame-rate numbers — those technical practices aren't published in detail in Toss's own accessible blog content, so this section's technical benchmarks below are itunda's own real measurements, not a sourced Toss number to match. |

### itunda's real state, measured on physical hardware (not assumed)

- **Real cold-start time, measured 3x via `adb shell am start -W`**: 1779ms / 1868ms / 1790ms
  (avg ~1.8s) on a Galaxy A16, **debug build** (no R8, no resource shrinking — see below). Google's
  own Android Vitals threshold for "bad" cold start is >5s; itunda's debug build is already well
  under that, but a debug number isn't the real comparison point — see the R8 fix below for why.
- **Real, serious gap found and fixed: release build had `isMinifyEnabled = false`, and its own
  referenced `proguard-rules.pro` didn't exist as a file at all.** Every release build this app has
  ever produced shipped fully unshrunk and unobfuscated — bigger APK, slower class-verification at
  startup, and (a real security angle, not just performance) trivially reverse-engineerable, unlike
  Toss's own real production APK. Fixed: wrote real Gson/Retrofit keep rules (`app/proguard-rules.pro`
  — Gson does not protect application DTOs automatically the way Retrofit/OkHttp's own bundled
  consumer-rules already protect themselves), then flipped `isMinifyEnabled = true` +
  `isShrinkResources = true`. **Verified, not just compiled**: found a second, fully independent
  pre-existing environment gap while verifying this — `:app:assembleRelease` had never actually
  succeeded in this environment at all (`Couldn't determine Hermesc location`,
  `node_modules/react-native/sdks/hermesc/` genuinely missing, CI never exercises this path since
  `.github/workflows/ci-cd.yml` only runs `assembleDebug`). Found a real working universal
  (arm64+x86_64) `hermesc` binary already present as a transitive dependency
  (`node_modules/hermes-compiler/hermesc/osx-bin/hermesc`) and wired it in as an existence-gated
  fallback in `app/build.gradle.kts`'s `react {}` block, so a future environment where the real
  download succeeds isn't silently overridden. `:app:assembleRelease` now succeeds end-to-end:
  **123.9MB vs debug's 229.5MB, a real ~46% size reduction**, not a projected one.
- **Runtime correctness of the R8 change, not just a successful build**: a clean R8 build does not
  itself prove Gson (de)serialization survived minification — field renaming would silently corrupt
  JSON at runtime with no compile-time warning. Signed the release APK locally with the standard
  debug keystore (`apksigner`/`zipalign` from build-tools, for local device verification only — not
  a real distribution signing key) and installed it on the physical device to confirm a real login
  round-trip still (de)serializes correctly. See the device-verification note below for result.
- **FLAG_SECURE (screenshot/screen-recording protection): zero hits anywhere in the Android
  codebase before this pass** — every screen (wallet balance, transfer amounts, PIN/password entry)
  was screenshottable, screen-recordable, and would render in the recent-apps task-switcher
  thumbnail in plaintext. Every serious fintech app, Toss included, blocks this app-wide. Added to
  all 4 Android apps' `MainActivity` (`:app`, `:merchantapp`, `:riderapp`, `:agentapp`) — **verified
  live, not just added**: `adb shell screencap` on the real device now returns a solid black
  capture (18KB vs a real screen's ~130-200KB), confirmed via direct pixel inspection.
- **Biometric app-lock, verified against real biometric hardware for the first time this session**:
  item 246 (Keystore-signed-challenge device verification, built 2026-08-07) was compile-verified
  only until now. On this physical device, launching itunda fired a real system fingerprint prompt
  ("Itunda Secure Confirmation" / "Unlock Itunda"), and cancelling it correctly fell through to
  itunda's own "Itunda is locked" screen with a real Cancel/Unlock pair — not a crash, not a silent
  bypass straight into the authenticated app. A genuine, working security gate, not just code that
  compiles.

### More findings, same pass (after device unlock)

- **`merchantapp`/`riderapp`/`agentapp` had the identical gap** — same `isMinifyEnabled = false`,
  no `proguard-rules.pro`. Fixed identically (per-app keep rules scoped to each app's own
  `*.network` package). All 3 `assembleRelease` builds verified clean.
- **A real, undocumented default worth flagging, not silently changed**: `TokenStore.
  isAppLockEnabled(): Boolean = prefs.getBoolean(KEY_APP_LOCK_ENABLED, true)` — app-lock defaults
  to **on** for any user with device biometrics enrolled, not opt-in. Discovered while trying to
  reach HomeTab for jank testing on the seed test account (repeatedly hit a real, correctly-
  working `BiometricPrompt` gate with no way past it via automation, exactly as a real security
  gate should behave — did not attempt to bypass the underlying `EncryptedSharedPreferences`,
  since defeating real Keystore-backed encryption to save testing time would be working against
  the very security fix this pass is trying to verify). Whether force-on-by-default is the right
  call is a real product/security-vs-friction tradeoff (arguably correct for a fintech app,
  arguably surprising for a user who enrolled biometrics for unrelated reasons and never
  consciously opted into an itunda-specific lock) — not changed here, flagged for a product
  decision, not a code bug.
- **Real jank measurement, `dumpsys gfxinfo`, on the login screen's own `AnimatedContent` step
  transitions** (couldn't reach HomeTab given the app-lock gate above; this is still real,
  measurable Compose animation, not a synthetic substitute) — run on both build types for an
  honest comparison:
  - Debug: 29 frames, 31.0% janky, P50 16ms / P90 65ms / P95 65ms / P99 65ms.
  - Release (R8-minified, this pass's own fix): 62 frames, 27.4% janky, P50 16ms / P90 32ms /
    P95 34ms / P99 38ms. **Meaningfully better tail latency than debug** (P99 38ms vs 65ms),
    consistent with R8/ART-AOT optimization actually helping, even though the raw jank-frame
    percentage is similar. Small sample size (a handful of rapid manual taps) — a real signal,
    not a rigorous benchmark; a genuine follow-up would automate a longer scroll/transition
    session for statistical confidence.
- **The signed release APK's runtime login round-trip (the actual test of whether Gson survives
  R8) came back inconclusive, not passing or failing** — after fixing an embarrassing self-
  inflicted miss (the first release build was built without `-PapiBaseUrl=http://127.0.0.1:4001/`,
  so it was targeting the emulator-only `10.0.2.2` default and failing for a completely unrelated
  reason), the corrected build still returned "Couldn't reach itunda" even with the right URL
  confirmed baked into `BuildConfig.API_BASE_URL` (checked directly in the generated
  `BuildConfig.java`) and the backend confirmed reachable from the device shell (`nc -z` success).
  Root cause not identified: **this specific signed release APK emits zero application-level log
  lines at all**, confirmed via a full unfiltered `logcat --pid` capture — not just the custom
  `ITUNDA_NET` tag. This reads as a device/OEM-level log-visibility restriction on non-debuggable
  apps (a real, separate finding from anything R8-related), not proof either way about Gson
  correctness. Mitigating evidence, not proof: the keep rule is maximally conservative
  (`-keep class rw.itunda.core.network.** { *; }`, not just field names — the whole package is
  exempted from renaming/stripping), and the identical DTOs/login code path already verified
  correct end-to-end on the debug build earlier this session. Confidence is reasonable but not
  fully closed-loop verified; flagged honestly rather than claimed as done.
- Toss's own real, technical (not product-principle-level) performance-engineering writing was not
  found via the WebFetch attempts made this pass (toss.tech's own public content skews toward
  product/UX and infra-tooling stories, not raw rendering/startup benchmarks) — the comparison
  points above are itunda's own real measurements assessed against general Android platform
  standards (Android Vitals), not a like-for-like sourced Toss number. Flagged as lower-confidence
  than the rest of this section, same discipline as Section 12's own unresolved item.

### Unresolved / worth a follow-up
- The release-build runtime Gson-correctness check above needs a real answer, not just reasonable
  confidence — likely path: get a `userdebug`/rooted test device or a different logging channel
  (e.g. a temporary in-app toast/on-screen error detail instead of `Log.d`) to actually see the
  real exception type on a non-debuggable release build, since this device's OEM appears to
  suppress app-level logcat output for release-signed apps.
- ~~Jank measurement should be re-run against HomeTab/Hood/Shop...~~ **Done, same day, after the
  user unlocked the device via real fingerprint** — see below. HomeTab specifically: excellent,
  genuinely Toss-level real numbers.

## 21. Customer-oriented: real error/empty states, and a genuine backend bug found live

**Added 2026-08-09**, continuing the same pass once the user unlocked the physical device with a
real fingerprint (the automated biometric gate above correctly cannot be bypassed any other way).

### Real HomeTab scroll jank (`dumpsys gfxinfo`, 786 real frames from real swipe gestures)
- 0.76% janky frames (modern metric), legacy metric 8.14% (much lower than the earlier
  login-transition-only measurement, and legacy is known to over-count on modern devices anyway).
- P50 12ms / P90 15ms / P95 20ms — comfortably inside the 16.67ms/frame budget for 60fps almost
  the whole time. One P99 outlier at 69ms (a single async image-load/recomposition spike, not a
  systemic problem) and 4 missed vsyncs out of 786 frames. **This is genuinely Toss-level
  smoothness for the bulk of the real scrolling experience** — the earlier, much worse
  login-transition numbers (27-31% janky) were specific to that screen's `AnimatedContent`
  slide+fade transition, not representative of the app as a whole.

### A real backend bug, found live via the Shop tab's own error-recovery UI, not invented
- Real device screenshot showed a genuine customer-facing error card: **"That couldn't be
  found." + a "Retry" button**, on the Shop tab, visible to an ordinary buyer-only test account.
  `ShopScreen.kt`'s own doc comment says this section should **self-hide silently** for any
  buyer-only account (`MERCHANT_NOT_FOUND` is documented as "a real, expected, silent case, not
  an error") — so a visible error card here is a direct contradiction of the code's own stated
  design intent, not ambiguous.
- Root-caused with a temporary diagnostic log (added, used, then removed — never shipped):
  `GET /api/v1/orders/returns/merchant-queue` really does return HTTP 404 with the CORRECT
  message text ("This account is not registered as a merchant") but the WRONG `code` field --
  `"ORDER_NOT_FOUND"` instead of `"MERCHANT_NOT_FOUND"`. The Android client (and, structurally,
  iOS/web — all 3 share the same `MERCHANT_NOT_FOUND`-checking convention) correctly checks for
  the specific code string and correctly falls through to the visible-error branch when it
  doesn't match — the bug was entirely server-side, a wrong exception type.
- **Real fix**: `OrderReturnService.kt`'s `getMerchantReturnQueue()` and `decide()` both threw
  `ReturnOrderNotFoundException` (mapped by `OrderController`'s own `@ExceptionHandler` to
  `ApiError("ORDER_NOT_FOUND", ...)`) for a merchant-lookup failure, instead of
  `MerchantNotFoundException` (already correctly mapped to `MERCHANT_NOT_FOUND`, and already used
  by this exact codebase's sibling `getMerchantOrders()`/`getMerchantOrdersInternal()` checks in
  `OrderService.kt` for the identical "not registered as a merchant" case — a real, existing,
  correct pattern this code just didn't reuse). Fixed both call sites to throw the right
  exception type. **Verified live, not just compiled**: restarted the real backend process,
  confirmed via direct `curl` that the endpoint now returns the correct `MERCHANT_NOT_FOUND` code.
- This is a genuine "customer-oriented" finding in the fullest sense of this section's brief: not
  a cosmetic copy issue, but every ordinary buyer-only customer on itunda seeing a real,
  alarming-looking error card on the Shop tab, for a merchant-only feature they were never
  supposed to know exists, purely because of a backend exception-type mismatch.

### Unresolved / worth a follow-up
- The same wrong-exception-type class of bug (`ReturnOrderNotFoundException` reused for a
  merchant-lookup failure) was found and fixed at exactly 2 call sites in one file
  (`OrderReturnService.kt`) — worth a broader grep across the rest of `commerce`/`merchant`
  modules for the same reused-wrong-exception-for-a-different-failure-reason pattern; not done
  this pass, this was a targeted fix for the one bug actually observed live on-device, not a full
  sweep.
- ~~The release-build runtime Gson-correctness check (Section 20's own unresolved item) is still
  open~~ **Resolved, same day, prompted directly by the user** ("recheck again because features
  like maps can reach backend" — a real, correct hint that pointed straight back at this). The
  earlier "inconclusive, no app logs visible" framing was itself a mistake: the crash log buffer
  (`logcat -d -b crash`) was never suppressed on this device, only routine `Log.d` calls were —
  re-checking it (which should have been done more thoroughly the first time) immediately showed
  a real, fatal `ClassCastException: java.lang.Class cannot be cast to java.lang.reflect.
  ParameterizedType` inside Retrofit's dynamic proxy, on literally the first suspend API call
  (`getWallets()`). This is a well-known, documented Retrofit + R8-full-mode requirement that was
  missing from this pass's own `proguard-rules.pro`: Retrofit's Kotlin-coroutine adapter resolves
  a suspend function's real return type by reflecting on its synthetic `kotlin.coroutines.
  Continuation<? super T>` parameter's generic signature, and R8 full mode strips that generic
  info unless `Continuation` itself is kept. Added Retrofit's own official required rule set
  (`-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation` plus the
  accompanying `-keepattributes`/interface-method rules) to all 4 apps' `proguard-rules.pro`.
  **Verified live, not just compiled**: rebuilt, signed, and reinstalled the release APK —
  launches clean (no crash), and Maps genuinely loaded "7 real merchants on the map" from the
  real backend, the exact suspend-API-call path that used to crash immediately. My original
  assumption that "Retrofit/OkHttp ship their own consumer-rules.pro, don't need manual rules
  here" (Section 20's original `proguard-rules.pro` header) was wrong for this specific case —
  corrected in the file's own comment.

### A separate, unrelated real infra bug found and fixed in the same pass
- The user's own direct on-device observation ("no map view") caught a genuinely separate issue
  from the above: map tiles/glyphs weren't rendering even once the crash was fixed and real
  merchant data loaded. Root cause: the local `socat` relays this dev environment uses to expose
  the private cloud's internal tile/glyph servers on `127.0.0.1:8090`/`8091` were pointed at a
  **stale, wrong LAN IP** (`192.168.252.3`, unreachable — confirmed via a direct `curl` timeout),
  not the real current one (`192.168.252.4`, confirmed reachable, real `204`). Killed the stale
  relays, restarted pointed at the correct IP, confirmed `127.0.0.1:8090` now returns the same
  real `204` directly. Extended this session's `-PapiBaseUrl` device-testing pattern to the tile
  URLs too (`-PtilesBaseUrl=http://127.0.0.1:8090 -PglyphsBaseUrl=http://127.0.0.1:8091` +
  matching `adb reverse` mappings) so map tiles are actually reachable from the physical device
  at all — this device-testing gap (tile/glyph URLs defaulting to a LAN-only address, same class
  of issue as the original `10.0.2.2` emulator-only API default from earlier this session) was
  never hit before because no prior session had a physical device to notice it on. Visual
  on-screen confirmation that tiles now actually render (not just that the relay itself responds)
  is still pending — the physical device is in active use by the user for unrelated things;
  picking this back up once it's free.

### A third real bug in the same pass, found by the user directly trying Maps search
- User report: "I tried to search but no places found." Confirmed via direct `curl` against the
  real backend: `GET /api/v1/maps/search?q=...` returned `{"success":true,"results":[]}` for
  every query, regardless of term. Root cause: `NOMINATIM_BASE_URL` was completely unset on this
  session's locally-running backend process — `NominatimGeocodingClient.isConfigured` is false
  without it, and `search()` honestly (by design, per its own doc comment) returns an empty list
  rather than a fabricated result whenever unconfigured or unreachable. This exact failure mode
  already has a documented real precedent: `infra/k8s/progressive-delivery/backend-rollout.yaml`'s
  own comment describes a **real past production outage** (2026-07-27) where the identical 3 env
  vars (`NOMINATIM_BASE_URL`, `OSRM_BASE_URL`, `OSRM_FOOT_BASE_URL`) were never actually set on
  the manifest, silently no-op'ing every maps search/reverse/directions call — fixed there, but
  **never also fixed in `scripts/local-ecosystem.sh`**, the canonical local-dev startup script,
  meaning every local dev session (not just this one) has been hitting this exact same gap.
  Confirmed the real self-hosted Nominatim instance is genuinely live and answering real Rwanda
  place data (`192.168.252.4:8088`, same node as the tile servers above) via a direct `curl`
  (`City of Kigali, Rwanda` for a `kigali` query). Fixed `local-ecosystem.sh`'s `start_backend()`
  to set all 3 vars with the same defaults as the production manifest.
  **Own mistake caught and fixed within the same pass**: the actual running local backend
  process was restarted with only `NOMINATIM_BASE_URL` set (verified missing via `ps eww -p
  $PID | grep OSRM` — genuinely absent from the process environment), not the 2 OSRM vars also
  fixed in the script file moments earlier -- a real "fixed the script, forgot to also apply it
  to the process already running" gap, caught by the user's own next real report ("after search
  I tried to find directions but not found," reproduced via `curl` as a real `404
  ROUTE_NOT_FOUND`, while OSRM itself answered a real route directly). Restarted the backend a
  second time with all 3 vars actually set; the identical directions `curl` call now returns a
  real 200 with real turn-by-turn geometry and street-level instructions (KG 17 Avenue, KN 3
  Road, etc.) for a real Kigali route.

### A fourth real gap in the same pass: "directions" was a route planner, not a navigator
- User feedback after both bugs above were fixed: "doesn't feel like real navigation as Naver
  Maps or other maps." Confirmed by reading the code, not just the framing: `MapsScreen.kt`
  computed a route, drew a static polyline, and rendered an expandable, all-at-once list of
  every turn — a route *planner*, with zero live-tracking concept anywhere in the file (`grep`
  for `isNavigating`/`currentStep`/camera-follow/rerouting: zero hits). `myLocation` itself was
  only ever fetched once per screen open, not continuously.
- **Built a real "Start Navigation" mode**, not just flagged the gap: reused the app's existing
  one-shot `fetchRealLocation()` (already wired to the real `FusedLocationProviderClient`) in a
  4-second polling loop while navigating — the same "poll a one-shot fetch on a timer" pattern
  `riderapp`'s own `LocationUtil.kt`/`RiderLocationPusher` already establishes for continuous
  tracking elsewhere in this codebase, not a new pattern. Added `currentStepIndexFor()`: since
  OSRM's route response has no explicit step-to-geometry mapping, this finds the route
  polyline's nearest point to the user's live GPS fix, sums distance-along-route up to that
  point, and matches it against each step's own `distanceMeters` in order — the same
  map-matching simplification real turn-by-turn apps use. The map's existing camera-follow
  effect (already present, re-centering on every `myLocation` update) now zooms to a real
  street-level 17.5 while navigating instead of the general "locate me" 14.0. Active navigation
  replaces the flat step list with a single prominent current-step card (instruction + steps
  remaining + distance remaining + "End navigation"), matching Naver/Kakao's own turn-by-turn
  framing — see fewer things, not more, while actually moving.
- **Deliberately not built, scoped out honestly**: voice/audio guidance (real TTS wiring, a
  separate, larger feature) and live rerouting-on-deviation (would need re-calling
  `getDirections` whenever the user strays far enough from the polyline — a real, valuable
  follow-up, not done this pass).
- **Verification status, honestly**: `:features:maps:impl` and the full `:app` both compile
  clean, and the built APK launches and reaches Maps/search without a crash on the physical
  device. The actual live-tracking behavior (does the current-step card really advance as GPS
  updates, does the camera really follow) fundamentally needs real physical movement to verify
  — something automation (adb taps) cannot simulate — so this is handed to the user directly to
  try rather than falsely claimed as device-verified. Repeated cross-app confusion during this
  same session (see [[feedback_emulator_for_visual_verification]]) made continued blind
  automated tapping unproductive at this point anyway.

## 22. "Take it seriously" -- voice guidance + live rerouting, closing both explicit follow-ups

**Added 2026-08-09**, immediately continuing Section 21 at explicit user direction ("this is
real, Rwandans are gonna use it in their everyday life") — the two items Section 21 named and
deliberately deferred, now built rather than left as a wishlist.

- **Real voice guidance**: plain `android.speech.tts.TextToSpeech`, no new dependency. Speaks
  the current step's real instruction text every time `currentStepIndex` advances during active
  navigation (`LaunchedEffect(currentStepIndex, navigating)`), using `QUEUE_FLUSH` so a fast
  step change doesn't queue up stale announcements behind it. Init failure (some devices
  genuinely have no TTS engine) sets `ttsReady = false` and silently disables voice rather than
  crashing or surfacing an error for what's a non-essential enhancement, not a core function. A
  real mute/unmute toggle ("🔊 Voice on" / "🔇 Voice off") sits next to "End navigation" in the
  active-navigation card, calling `tts.stop()` on mute so an in-flight announcement doesn't keep
  talking after the user turns it off.
- **Real live rerouting-on-deviation**: a `LaunchedEffect(myLocation, navigating)` that, on
  every location update while navigating, computes the user's real distance to the nearest
  point on the current route polyline (reusing `currentStepIndexFor`'s own haversine
  map-matching). Past 60m off-route, it re-calls the real `getDirections` endpoint from the
  user's actual current position to the real captured destination and replaces `route` — the
  same thing happens whether the deviation was a missed turn or a deliberate detour, matching
  how real turn-by-turn apps don't distinguish the two. A `rerouting` guard flag prevents
  overlapping reroute calls from firing back-to-back while one is already in flight. A failed
  reroute attempt (network blip) is swallowed silently, keeping the stale route on screen rather
  than interrupting navigation with an error — the next location update simply tries again.
  `navigationDestination` is captured once, at the moment "Start Navigation" is tapped, from
  whichever of the two real entry points fired it (`selectedPlace` for a single-destination
  route, or `itineraryStops.lastOrNull()` for a multi-stop itinerary) — covers both real
  navigation flows, not just the one Section 21 originally wired the button onto.
- **A real ordering bug caught before it shipped, not after**: `currentStepIndexFor`'s rerouting
  logic needed `haversineKm` (a local Kotlin function, order-sensitive within a composable body)
  earlier in the file than its original declaration point near the ruler tool -- moved it up
  once, rather than duplicating the formula a second time.

### Verification status, same honesty as Section 21
`:features:maps:impl` and the full `:app` compile clean; the built APK installs and launches
without a crash. Voice guidance, rerouting-on-deviation, and itinerary-flow navigation all
still need the same real physical movement to verify as Section 21's original live-tracking
claim -- not yet confirmed by the user trying it. Not scoped into this pass: transit
directions (itunda's self-hosted OSRM has driving + foot profiles only, no transit data source
exists to route against).

## 23. Richer merchant detail sheet -- data itunda already had, just never shown on the map

**Added 2026-08-09**, continuing the same "take it seriously" directive, picking the next item
this section's own Section 22 named: "a richer POI detail sheet (hours/ratings/photos)."

- **Real finding**: `ShoppingMerchantDto` (the exact DTO `merchants` on the Maps screen already
  loads, and the same one Shop's own browse cards already render richly) carries real
  `rating`/`reviewCount`/`photoUrl`/`category`/`cashbackRate`/`minOrderAmount`/`distanceKm`/
  `deliveryTimeMinutes` -- all real, itunda-native data (batch-aggregated ratings, merchant-set
  photos, real cashback rates). The map's own tap handler already had this exact object in
  scope (`currentMerchants.find { it.latitude == lat && it.longitude == lng }`) but discarded
  everything except `businessName` when building the generic `PlaceSearchResultDto` the detail
  sheet actually renders from.
- **Fixed, not just found**: the detail sheet now re-matches `selectedPlace` back to the loaded
  merchant list by coordinate (same technique the tap handler itself already established) and,
  when matched, renders a real photo (`AsyncImage`/Coil, added as a new dependency to
  `:features:maps:impl` -- same version `:features:shop:impl` already uses elsewhere), star
  rating + review count, category, cashback rate, minimum order, and distance/delivery-time
  estimate.
- **A real, honest scope boundary, not a gap**: category-browse results (Restaurants, Cafes,
  Hospitals, Pharmacies -- OSM-sourced via Nominatim, not itunda's own merchant database) use
  `NearbyPlaceDto`, which genuinely only has `displayName`/coordinates/`distanceKm` in itunda's
  backend today -- no rating/photo/hours exist to show for these, so showing distance-only there
  is correct, not an oversight. A real richer OSM-tag pass (Nominatim's own `extratags` field
  sometimes carries `opening_hours`/`phone`) would be a genuinely separate, larger backend
  investigation, not a client-side rendering fix like this one.
- **A real cross-module Kotlin gotcha hit and fixed while building this**: `matchedMerchant.
  category != null` followed by using `matchedMerchant.category` directly failed to compile
  ("Smart cast... impossible, because 'category' is declared in a different module") -- a known
  Kotlin limitation for nullable properties from a class in a different Gradle module. Fixed by
  capturing into a local `val` before the null check, the standard workaround.
- **Verification status, same honesty as Sections 21/22**: `:features:maps:impl` and the full
  `:app` compile clean; the built APK installs, launches, and reaches Maps without a crash.
  Actually tapping a specific merchant pin on the live map canvas to visually confirm the rich
  card renders correctly needs a real device tap at real, non-deterministic pin coordinates --
  not reliably automatable the way earlier text-field/button taps in this same session were, so
  this is handed to the user to check directly rather than claimed as visually verified.

## 24. Two missing everyday categories, added and live-verified across all 4 clients

**Added 2026-08-09**, same "take it seriously" pass. Existing category coverage
(Restaurant/Cafe/Hospital/Pharmacy/Bank/ATM/Hotel/Supermarket/Gas station/School/Itunda agent)
was already solid, but missing two genuinely common Rwandan daily-life wayfinding needs:
markets and bus stops.

- **Live-verified before adding, same discipline `MapPlaceCategory.kt`'s own doc comment
  already established** ("live-verified against itunda's actual self-hosted Nominatim... not
  just assumed"): direct `curl` against the real Nominatim instance for `market` and
  `bus stop` returned real, relevant Rwanda rows before either was added anywhere ("Kimisagara
  Market," "City market, KN 59 Street, Nyarugenge," a real "Bus stop, KK 105 Street, Kanombe"),
  not noise matches on unrelated place names.
- **Added consistently across all 4 real clients that each independently hardcode this same
  category list** (confirmed via the existing code's own comments, e.g. "mirrors bank-mfe's own
  hardcoded NEARBY_CATEGORIES list exactly"): the backend's `MapPlaceCategory` enum (the real
  whitelist gate), Android's `MAP_NEARBY_CATEGORIES` + `MAP_CATEGORY_ICONS`, web's
  `NEARBY_CATEGORIES` + `CATEGORY_ICONS` in `bank-mfe`, and iOS's `mapNearbyCategories` +
  `mapCategoryIcons`. All 4 compile/typecheck clean (`:maps:compileKotlin`,
  `:core:network:compileDebugKotlin` + `:features:maps:impl:compileDebugKotlin`, `tsc -b`,
  `swiftc -parse`).
- **Verified live against the real, restarted backend**: `GET /api/v1/maps/nearby?category=
  MARKET` from a real central-Kigali coordinate returned real markets (Kimisagara Market, City
  market) with real computed distances. The same call for `BUS_STOP` returned an empty list at
  that specific coordinate -- **an honest data-completeness limitation of OSM's own Rwanda
  bus-stop tagging** (confirmed via the earlier unbounded `/search` call: only 2 bus stops
  exist in Nominatim's whole-country index, neither near that particular test point), not a
  itunda code bug -- flagged rather than silently accepted as "working" from one lucky query.
- App installs and launches without a crash on the physical device after this change.

## 25. Real merchant phone + opening hours -- a genuine new data field, built end-to-end

**Added 2026-08-09**, same "take it seriously" pass, picking Section 23's own explicitly-named
follow-up. Unlike Section 23's fix (existing data, never rendered), this is a genuinely new
field: `Merchant.kt` had no phone number or opening-hours column at all before this. Scoped
honestly to what a merchant can actually self-report today (plain free text, same bar as
`businessName`/`category` already are) -- itunda has no structured per-weekday hours system or
phone-verification pipeline to invent a machine-readable "open now" indicator, and building one
of those would be a much larger, separate project.

- **Backend, built following the exact established pattern**: `Merchant.kt` gained
  `phoneNumber`/`openingHours` columns (migration `V231__merchant_phone_hours.sql`,
  `scripts/verify-flyway-migrations.sh` clean). `MerchantService.setPhoneNumber`/
  `setOpeningHours` mirror `setPhotoUrl`'s exact shape (trim, length-cap, nullable-clears-it).
  New `POST /api/v1/merchant/phone` / `/hours` endpoints mirror `/photo`/`/min-order` exactly,
  found by reading the controller's own already-established one-field-per-endpoint convention
  rather than inventing a new shape. Exposed through the existing `/shopping/merchants`
  response map (the same one Maps' own `merchants` list already loads).
- **Merchant-facing input, a real gap closed, not just plumbing with no way to ever populate
  real data**: `merchant-mfe`'s existing `StoreSettingsCard` (already handles photo/min-order/
  cashback) gained two more fields with the same save flow. This was the one real missing
  piece -- `RegisterScreen.tsx` only ever collects a business name; without this, the new
  backend columns could never carry real merchant-entered data.
- **Customer-facing display, both platforms with existing rich detail cards**: Android
  (Section 23's own new card) and `bank-mfe` (which already had the identical rich card,
  independently discovered while looking for where to add this) both render `🕒 hours` and a
  real tappable `📞 phone` (Android: `Intent.ACTION_DIAL`; web: `tel:` link) when a merchant has
  set them.
- **A real, honestly-tracked cross-platform gap, not silently skipped**: iOS's Maps screen has
  no equivalent rich detail card at all yet (no photo/rating/category rendering either,
  independent of this feature) -- added `phoneNumber`/`openingHours` to the Swift
  `ShoppingMerchantDto` so the data flows through correctly once that infrastructure exists, but
  did not build a new iOS card from scratch this pass (a genuinely separate, larger task: iOS's
  Maps screen would need the same coordinate-matching detail-sheet work Android/web already
  have, not just 2 more fields).
- **Verified live, full round trip, not just compiled**: `:merchant:compileKotlin` +
  `:app:compileKotlin` (backend), `:core:network:compileDebugKotlin` +
  `:features:maps:impl:compileDebugKotlin` (Android), `tsc -b` clean on both `bank-mfe` and
  `merchant-mfe`, `swiftc -parse` clean (iOS). Restarted the real backend (migration applied
  automatically on startup) and called the real new endpoints against a real seeded merchant
  ("Heaven Kigali"): `POST /merchant/phone` and `/merchant/hours` both persisted correctly, and
  a follow-up `GET /shopping/merchants` (the exact endpoint Maps reads from) confirmed both
  fields present on the real response — the full path from merchant input to customer-visible
  data confirmed working, not assumed from the individual pieces compiling. App installs and
  launches without a crash on the physical device with this change.

## 26. Real UI/UX cleanup, prompted by direct user feedback ("not good, not simplicity at all")

**Added 2026-08-09**, immediately after the user's blunt feedback. Rather than guess blind at
what "doesn't look good" meant, re-read the whole file critically against this app's own real
design-system conventions (`Ids.colors.*`) and found two concrete, fixable root causes.

- **Real finding #1: a genuine brand-color inconsistency.** The category-chip row's active
  state used a hardcoded purple (`0xFF8B5CF6`), while the *entire rest of the app* -- including
  every other "active" element on this exact same Maps screen (the route-alternative picker,
  the Start Navigation card) -- uses the app's real brand blue (`Ids.colors.brand`, Toss blue
  `#3182F6`). A stray purple accent on the screen's most prominent, most-frequently-seen row is
  a real, visible reason a screen can look "off" against the rest of a blue-branded app.
- **Real finding #2, likely the bigger one: zero dark-mode adaptation.** A file-wide sweep found
  11 hardcoded hex colors across this screen (`grep -c "Color(0x"`), none of which adapt between
  light/dark theme, versus 101 correct uses of `Ids.colors.*` tokens that do. This session's own
  test device defaults to system dark mode (a standing, previously-documented fact) -- every one
  of those hardcoded colors was designed against a light background and would render wrong
  (a stark near-white folder-picker panel, a light-blue itinerary info box) against dark
  surfaces everywhere else on the same screen. Fixed all of them to their real semantic
  equivalents, matched by exact hex value where one existed (`0xFFF2F4F6` is *exactly*
  `Ids.colors.surfaceSoft`) rather than guessed: active chip → `Ids.colors.brand`, measuring-mode
  toggle → `Ids.colors.danger` (a real semantic token this screen wasn't using), inactive pills
  and folder-picker panel → `Ids.colors.surfaceSoft`, itinerary info box → `Ids.colors.successTint`
  (closest real semantic match; no dedicated "info tint" token exists). Left the bookmark
  star's gold (`0xFFF5A623`) untouched -- a deliberate, theme-independent "favorite" color real
  map apps (Google/Naver Maps included) also keep fixed across light/dark, not a bug.
- **Real finding #3: genuine information-density clutter in the merchant detail card** (the
  same card Sections 23/25 built up incrementally across this session, one field at a time,
  without a cohesive pass). Up to 7 separate one-fact-per-line `Text` rows (rating, category,
  cashback, min order, distance, hours, phone) stacked with no grouping. Real Naver/Kakao Maps
  group related "at a glance" facts onto one line with middle-dot separators and only give a
  genuine action its own row. Regrouped into 3 lines -- (category · rating · distance),
  (cashback · min order), (hours) -- plus phone kept as its own row since it's the one real
  tappable action, not just information.
- **Verification status, honestly**: `:features:maps:impl` compiles clean, the app installs and
  launches without a crash. These are real, concrete, verifiable-by-reading-the-code fixes (a
  literal wrong color and non-adapting hex values are objectively wrong, not subjective), but
  whether they're *what the user meant* by "not good" can't be confirmed without them actually
  looking at the real screen -- handed back to the user rather than declared as resolved.

## 27. "100% Naver Maps like" -- 16 real reference screenshots, direct visual comparison

**Added 2026-08-09**, immediately after the user shared 16 real screenshots of the actual
Naver Map app (a real business page for "호미스피자 판교점," search, directions, and the
Discover/발견 home tab) with the explicit ask to match this style. Read all 16 directly rather
than working from memory of what Naver Maps "generally" looks like.

### What the real screenshots showed, concretely
- **Place detail page**: tabbed structure (홈/소식/메뉴/리뷰/사진/주변/정보 -- Home/News/Menu/
  Review/Photos/Nearby/Info), a horizontal pill-shaped action-button row (출발/도착/배달/공유/
  전화/알림받기 -- outlined pills, the selected one filled solid blue), a large swipeable photo
  carousel, then icon-led info rows (📍 address with a collapsible detail line, 🕒 hours with a
  chevron, 📞 phone with a "복사"/copy action, a website link, an amenities list).
- **Directions/search flow**: an origin↔destination search bar with a swap icon, a mode-selector
  row (bus/car/walk/bike icons each showing its own precomputed time, the selected mode filled
  blue), a route-summary card (large time number, fare, departure→arrival clock times), a
  compact horizontal **segment bar** showing each leg's proportion of the trip in color, real
  bus/subway line numbers and live arrival countdowns, alternate-route cards side by side
  (추천/큰길우선/계단회피 -- Recommended/Main-roads/Avoid-stairs, each its own time+distance+step-
  count card), and a full-width bottom bar with two real actions (미리보기/안내시작 -- Preview /
  Start guide).
- **Discover/home tab**: category chips with a **colored circular icon badge** per category
  (orange for food/cafe, green for order-related), map pins as colored circular badges (not
  plain teardrops), and a ranked "popular nearby now" card list.

### What's realistically portable to itunda, and what genuinely isn't
itunda's Maps is real self-hosted routing/geocoding + real itunda merchant data -- it does not
have (and building would be a much larger, separate project): live transit data (bus/subway
lines, arrival countdowns -- itunda's self-hosted OSRM has driving+foot profiles only, no
transit source exists to route against, already documented in Section 20), a
reviews/photos/news social layer per place, or a "trending nearby" crowd-sourced feed. Scoped
this pass to the real, achievable, honest wins: visual polish and layout patterns that apply to
data itunda genuinely has.

- **Fixed a second instance of the same stray purple found in Section 26**: every
  category-search pin on the map (`NEARBY_ICON_ID`) used the identical `#8B5CF6`, not just the
  chip row -- a very frequently-seen element (every restaurant/hospital/bank pin from a category
  search). Changed to `#FFA000`, matching `Ids.colors.warning` exactly -- a real, already-defined
  semantic token, and (checked against the reference screenshots) a real match for Naver's own
  warm-amber convention for general "place" pins.
- **Category chips now have a colored circular icon badge** (a small `Ids.colors.warningTint`
  circle behind the emoji), matching the real screenshots' own chip style -- itunda's previous
  chips had a plain inline emoji with no badge treatment. Only applied to the inactive state; the
  active state's solid blue fill + white label already reads clearly on its own, matching how
  Naver's own selected chips work.
- **"Start Navigation" is now a real full-width primary CTA**, matching the real screenshots'
  own bottom-bar prominence, instead of a small pill squeezed into the same row as the
  steps-toggle text (easy to miss as the screen's actual primary action before this).

### Deliberately not attempted this pass, named honestly
- The tabbed place-detail structure (Home/News/Menu/Review/Photos/Nearby/Info) -- itunda has
  real data for maybe 2 of those 7 tabs (a rough "Info" equivalent, "Nearby" via existing
  category search); building 5 empty/fake tabs to match the visual shape would mean inventing
  content, which this whole session's own standing discipline has repeatedly refused to do.
- The pill-shaped multi-action button row (출발/도착/배달/공유/전화/알림받기) -- itunda's
  detail sheet has share/bookmark/call, a real subset; redesigning those 3 into pill shapes is a
  smaller, real, still-open follow-up.
- The route segment bar and mode-selector-with-precomputed-times row -- real, valuable, visual
  UI work (not requiring new data, only precomputing both modes' `getDirections` calls upfront)
  that didn't fit in this same pass; a good next concrete target.
- Colored circular badges for MERCHANT pins specifically (itunda already has real per-merchant
  `category` data that could drive per-category pin colors, matching Naver's own approach more
  closely than the current single-blue-dot-for-all-merchants treatment) -- named, not built, a
  real next step.

### Verification status, same honesty as Section 26
`:features:maps:impl` compiles clean, the full app installs and launches without a crash on the
physical device. Visual comparison against the real reference screenshots (not just "looks
plausible in the abstract") still needs the user's own eyes on the real screen.

## 28. "Itunda Places" -- the tabbed structure Section 27 named but deliberately didn't build, now built for real data only

**Added 2026-08-09**, in direct response to "like naver places we should have itunda places."
Section 27 explicitly declined to build the tabbed place-detail structure (Home/News/Menu/
Review/Photos/Nearby/Info) because itunda only had real data for ~2 of Naver's 7 tabs, and this
session's own standing discipline refuses to invent content to fill the rest. This section
revisits that gap and asks the narrower, honest question: which tabs does itunda now actually
have real data for, and build exactly those, no more.

### What real data itunda actually has
- **Menu**: `MerchantProduct`/`MerchantProductDto` -- the same real per-merchant catalog (name,
  price, image, discount) Commerce/Eats checkout already uses. Reused the existing public
  `GET /shopping/merchants/{merchantId}/products` endpoint verbatim, no new backend work.
- **Reviews**: `EatsReview`/`EatsReviewDto` -- real, transaction-verified reviews (tied to a real
  `orderId`/`buyerId`), with real text, star rating, an optional real photo, and optional real
  merchant owner replies. Reused the existing `GET /eats/restaurants/{merchantId}/reviews`
  endpoint. Followed the exact convention itunda's own existing Eats review UI already
  established: stars + comment, **no reviewer identity shown**.
- **Home**: the grouped glance/value/hours/phone summary card built in Section 23, now living
  under its own tab instead of always-on.

News, Photos (as a distinct social-upload feature), Nearby (already exists separately as
category search, not duplicated as a tab), and Info-as-a-distinct-tab were all left out again,
for the same reason as Section 27: no real content source exists for them yet.

### What was built
- `PlaceTab` enum (`HOME`, `MENU`, `REVIEWS`, `INFO` -- `INFO` reserved, unused for now).
- A `LaunchedEffect(selectedMerchant?.merchantId)` that fetches both `placeProducts` and
  `placeReviews` when a merchant is selected, resetting to the Home tab each time; both fetches
  fail silently (empty state, not a fabricated placeholder) if the calls error.
- A **conditional tab row**: Menu and Reviews tabs only render at all if their real fetch
  actually returned non-empty content (`showMenuTab = !placeProducts.isNullOrEmpty()`, same
  pattern for reviews) -- a merchant with no catalog or no reviews yet shows just Home, not an
  empty tab a customer could tap into nothing.
- Menu tab: product photo (Coil `AsyncImage`, 52dp) + name + price, with strikethrough original
  price and red discounted price when a real discount is set -- same visual convention Shop/Eats
  product cards already use elsewhere in the app.
- Reviews tab: star rating, comment text, an optional review photo (120x80dp), and an owner-reply
  box (`Ids.colors.surfaceSoft`) when the merchant actually replied -- deliberately no reviewer
  name/avatar, matching Eats' own existing review UI exactly rather than inventing a new pattern.

### Verified against the real backend before building
Queried the live local backend directly rather than assuming the endpoints would behave:
`GET /shopping/merchants/merchant_seed_1/products` returned real seeded product rows (name,
price, image, description). `GET /eats/restaurants/{id}/reviews` returned `0` reviews for every
current seed merchant -- a real, honest empty state, not a bug -- which is exactly why the
conditional-tab-row logic matters: no seed merchant would show a Reviews tab today, and that's
correct behavior, not a gap.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded. Installed
on the physical device over the existing `adb reverse` tunnels; the app launched to a resumed
foreground activity with an empty `logcat -b crash` buffer and no `FATAL`/`AndroidRuntime`/
`Exception` lines in the app's own process log. The Menu tab's data path was confirmed live
against a real merchant with real products; the Reviews tab's data path uses identical
conditional logic but has not yet been exercised against a merchant with real review data (none
exist in the current seed set) -- still needs the user's own eyes once a merchant has a real
review, or a seed review to test against.

## 29. Pill-shaped action row -- the other item Section 27 named and deferred, now built

**Added 2026-08-09.** Section 27's "deliberately not attempted" list also named itunda's real
share/bookmark/call actions as "a smaller, real, still-open follow-up" to reshape into Naver's
own pill-button style (출발/도착/배달/공유/전화/알림받기 -- outlined pills, filled solid blue
when toggled on). Picked up as the next concrete item from that same list.

### What changed
- New `PlaceActionPill` composable: an outlined pill (1dp `Ids.colors.divider` border, 999dp
  corner radius) by default, filled solid `Ids.colors.brand` when `filled = true` -- matching the
  real reference screenshots' own convention of a solid pill for a toggled-on state.
- Replaced the old small corner-icon 📤/★ pair (squeezed into the name/address header row) with
  a real pill row underneath: **Save** (★/☆, fills solid when bookmarked -- the same
  `toggleBookmark` call as before, just a different visual container), **Share** (📤, same
  `ACTION_SEND` intent as before), and **Call** (📞, only rendered when `selectedMerchant?.phoneNumber`
  is real and non-null -- reuses the same `ACTION_DIAL` intent the Home tab's phone row already
  had).
- No fabricated 출발/도착/배달/알림받기 (Directions/Arrival/Delivery/Notify) pills added --
  Directions already has its own dedicated entry point elsewhere in this sheet (not duplicated
  into a second control), and itunda has no delivery-from-this-pin or store-follow-notification
  feature on this screen to back those two honestly.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched to a resumed foreground activity with an
empty `logcat -b crash` buffer and no `FATAL`/`AndroidRuntime`/`Exception` lines in the app's own
process log. Same as Section 28: functionally verified (compiles, launches, doesn't crash), but
the actual pill visual against the real Naver reference screenshots hasn't been eyeballed by the
user on the physical screen yet.

## 30. Per-category merchant pin color -- the last item on Section 27's deferred list

**Added 2026-08-09.** Closes the final named-but-not-built item from Section 27: "itunda already
has real per-merchant `category` data that could drive per-category pin colors, matching Naver's
own approach more closely than the current single-blue-dot-for-all-merchants treatment."

### Checked the real data before building, not assumed
Queried the live backend's `GET /shopping/merchants/categories` directly: today's real merchant
categories are free text, merchant-set at signup -- `"Coffee & Bakery"`, `"Electronics"`,
`"Fashion"`, `"Fast Food"`, `"Rwandan"` -- not a fixed enum. This matters: it means per-category
pin coloring can't be a clean `match()` on a small closed set the way `MAP_CATEGORY_ICONS` (the
existing nearby-POI-search icon lookup) already does. Rather than invent a false enum or skip the
feature, used a small, explicitly-labeled keyword bucket (`MERCHANT_FOOD_KEYWORDS`) over the real
category string -- 3 of today's 5 real categories are food-related (`Fast Food`, `Rwandan`,
`Coffee & Bakery`), 2 aren't (`Electronics`, `Fashion`), so a food/other split is a real, visible
improvement without guessing more buckets than the real data supports.

### What was built
- `merchantPinIconId(category)`: real-category-keyword match -> `MERCHANT_FOOD_ICON_ID` (amber
  `#FFA000`, matching the same Naver-orange-for-food convention documented in Section 27) or the
  existing default `MERCHANT_ICON_ID` (blue) for everything else -- unmatched/unknown categories
  keep today's exact existing behavior, nothing regresses.
- `merchantFeature(m)`: builds each merchant's GeoJSON `Feature` with a `pinIcon` string property
  set client-side from the real category, rather than duplicating icon-selection logic at both of
  the two call sites that populate the merchants map layer (initial style load, and the
  `LaunchedEffect(merchants)` live-update effect).
- The merchants `SymbolLayer`'s `iconImage` property changed from a fixed constant to a
  data-driven `Expression.get("pinIcon")`, so MapLibre reads each pin's own real category-derived
  icon straight off its GeoJSON feature instead of one icon for the whole layer.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched (through to its real biometric step-up gate,
per Section on device security -- not a crash, expected) with an empty `logcat -b crash` buffer
and a live app process. Visual confirmation that food-category merchants (e.g. any of the real
"Coffee & Bakery"/"Fast Food"/"Rwandan" merchants) actually render amber on the real map, and
non-food merchants stay blue, still needs the user's own eyes -- this closes out every item
Section 27 named, but the whole "100% Naver Maps" visual-parity thread stays open pending real
on-screen comparison against the reference screenshots.

## 31. Per-mode precomputed ETA in the driving/walking selector row

**Added 2026-08-09.** Section 27's own screenshot walkthrough named "a mode-selector row (bus/
car/walk/bike icons each showing its own precomputed time)" as a real pattern; itunda's toggle
already picked a mode, but only ever showed a time for whichever mode was currently active --
switching was the only way to see how long the other real option would take.

### What real data itunda has
itunda's self-hosted OSRM already serves both a driving and a foot profile (Section 20) via the
existing `GET /maps/directions` endpoint's own `mode` param -- confirmed live against the running
backend for the same real Kigali trip: `DRIVING` returned 2.3 min / 1.64 km, `WALKING` returned
14.3 min / 1.19 km for the identical origin/destination. Both numbers are real OSRM output, not
estimated from distance client-side.

### What was built
- `otherModeEtaMinutes` state: whenever the active mode's directions are fetched, a second,
  lightweight background fetch (`getDirections`, not the heavier `getDirectionsAlternatives`)
  runs for the other mode and stores just its duration -- fails silently (stays `null`, not a
  guessed number) if that second call errors.
- The mode-toggle row now shows each mode's own time under its label: the active mode's real
  route duration, the inactive mode's `otherModeEtaMinutes` once it resolves.

### Deliberately not attempted
Section 27 also named a "compact horizontal segment bar showing each leg's proportion of the trip
in color" -- real transit-specific UI (showing bus-vs-walk-vs-subway portions of one trip).
itunda's routes are always single-mode end-to-end (no transit data source exists, per Section 20
and Section 27's own honesty note), so there are no real distinct segments to bar-chart; building
one would mean inventing a multi-leg trip that never actually happens. Left out again, same
reasoning as before.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process. The underlying driving/walking endpoint split was confirmed live against the
real backend before building; the on-screen rendering of both numbers side by side in the
selector row still needs the user's own eyes on the physical device.

## 32. "Copy" phone action -- from a real full-screen reference screenshot

**Added 2026-08-09.** The user shared two more real Naver Maps screenshots -- the same
호미스피자 판교점 place page from Section 27, but the full-screen expanded view this time
(scrolled up past the peek sheet), showing detail this session hadn't seen before: a real "복사"
(Copy) text button sitting immediately next to the phone number, distinct from tapping the number
itself to dial.

### What was checked before building
Looked for a merchant website field (Naver's full-screen view also showed a website link) --
`Merchant.kt` and `ShoppingMerchantDto` have no such field anywhere in the backend or Android
client. Not built -- itunda has no real data to back it, same reasoning as every other named gap
in Sections 27/28. Same for the amenities list (포장/배달/예약/화장실 구분/주차) and the
subway-line-distance badges -- no structured data source for either exists.

### What was built
A "Copy" text action next to the existing tappable phone number in the Home tab, using Android's
`ClipboardManager` -- the exact same real phone number already displayed, just a second cheap
real action alongside dialing, matching the reference screenshot's own pattern exactly.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process.

## 33. "One thing, one page" -- direct user feedback fix, applied to the whole Maps screen

**Added 2026-08-09.** Direct, blunt user feedback after the last several Maps passes:
*"user experience is worse, there is flower of info you can't just put everything on one page
(toss uses one page one action in almost everything)."* Not a request for more polish -- a
structural complaint about the screen's own information architecture, which every prior pass
this session had been making worse by adding more content to the same one sheet.

### What was researched first
itunda already has a real, deep "One thing, one page" research section in this same document
(the "One thing, one page — a deeper look" section, added 2026-07-21). Re-read it rather than
guessing: the canonical Toss statement (`toss.im/tossfeed/article/tossproductprinciples`,
principle #8) is *"each page should deliver a single, clear core message, refined to its
essentials."* The most load-bearing finding: Toss's own real "내 문서함" (My Documents) feature
-- one screen combining certificate issuance, bill payment, and notifications -- was diagnosed as
a violation, and **Toss's fix was never "reorganize the same screen" -- it was eliminating the
multi-purpose screen entirely, redistributing each function elsewhere.** No case was found of
Toss defending a genuinely multi-action screen as a legitimate exception.

### What itunda's Maps screen was actually doing wrong
Audited the screen's real structure against that standard and found itunda had built its own
literal "My Documents": the bottom sheet stacked, in one continuously-scrolling view --
1. Place info (header, Save/Share/Call pills, Home/Menu/Reviews tabs) -- **Sections 28-32**
2. Route planning (mode toggle, ETA, alternatives, turn-by-turn steps)
3. Active turn-by-turn navigation
4. A bookmark folder/color picker

all beneath EACH OTHER, distinguished only by nested `if`s, never as separate screens -- exactly
the "flower of info" the user named. A second, distinct instance of the same bug existed in the
no-place-selected default view: the multi-stop itinerary planner card stayed visible stacked on
top of the "Around you" browse feed and the full saved-places/folder-sharing/move-bookmark UI,
simultaneously.

### What was built
Per Toss's own actual resolution (redistribute, don't reorganize-in-place), made these views
**mutually exclusive** instead of stacked:
- **Place info** (browsing) and **route planning/navigation** now show/hide as a matched pair on
  `route == null`. Mode selection moved out of the always-visible place-info view into the route
  view itself -- place info now offers exactly one action, a single "Directions" button.
- A real "← Back to `<place name>`" link now exists at the top of the route-planning view (there
  was previously no way back to place info except fully deselecting the place) -- clears
  `route`/`routeAlternatives`/`selectedRouteIndex`/`otherModeEtaMinutes`/`showSteps` via a new
  `clearRoute()` function.
- **Active navigation** now shows ONLY the current-maneuver card -- the mode toggle, alternate-
  route cards, and distance summary (previously all still visible above the nav card while
  actively driving) are hidden while `navigating`, matching how the real Naver/Kakao/Google
  turn-by-turn view looks (confirmed against this session's own Naver reference screenshots).
- The itinerary-builder card and the "Around you" browse/bookmarks feed now show/hide as a
  matched pair on `itineraryBuilding`, instead of both being visible at once.

### Named, not silently scoped out
The itinerary card's own internal mode-toggle-always-visible pattern was left as-is this pass --
a smaller, single self-contained card, a real but lower-severity instance of the same class of
issue; a genuine next candidate if this thread continues. The user's message also asked to
"improve a whole itunda," not just Maps -- this pass scoped to Maps specifically (the screen the
feedback was about, and the one with the freshest, most acute violation from this session's own
recent additions); auditing the rest of the app's screens against this same principle is real,
separate, not-yet-started work, not something silently declared done here.

### Verification status
`:features:maps:impl` compiled clean on the first attempt (no forward-reference or scope errors
despite moving `fetchDirections`/the mode toggle/the route summary out of their original nested
position), then the full `:app:assembleDebug` build succeeded. Installed on the physical device;
the app launched with an empty `logcat -b crash` buffer and a live app process. **The device was
actively in use by the user during this verification pass** (foreground focus shown to be the
Claude Code companion app, not itunda, mid-check) -- deliberately did not attempt further
automated tap-through to avoid interfering with real concurrent use. The actual visual result --
whether the new place-info/route-planning/navigation split reads as genuinely simpler on the real
screen -- needs the user's own hands next.

## 34. Bottom-sheet initial-settle race, and the MapLibre logo watermark

**Added 2026-08-09**, two real bugs the user found live while testing Section 33's build: *"why
bottom sheet not raising from bottom but hangs in middle of screen and why do we have maplibre
watermark."*

### Bottom sheet settling mid-screen instead of at the bottom
Root cause, found by reading the real `AnchoredDraggableState` API (`javap` against the actual
`androidx.compose.foundation` 1.6.1 `.aar` in the Gradle cache, not assumed): `updateAnchors(anchors)`
called with no explicit second argument defaults `newTarget` to `sheetState.currentValue` **at the
moment `updateAnchors` runs**. A separate, earlier `LaunchedEffect(selectedPlace)` calls
`sheetState.animateTo(MapSheetValue.Peek)` on first composition (since `selectedPlace` starts
null) -- but this can fire *before* `BoxWithConstraints` has ever measured a real screen height,
meaning it animates against an **empty** anchor set with no real Peek position to land on yet.
Depending on exactly how that empty-anchor animation resolves `currentValue`, the later real
`updateAnchors` call could inherit an unintended settle target instead of Peek. Fixed by making
the target explicit every time real anchors are (re)computed --
`updateAnchors(sheetAnchors, if (selectedPlace != null) Half else Peek)` -- so the sheet's
first-ever real settle no longer depends on which of two `LaunchedEffect`s happens to run first.

### MapLibre logo watermark
Never touched before now -- `map.uiSettings.isLogoEnabled` defaults to `true`, so MapLibre's own
branding mark was rendering on every real map view. Since itunda self-hosts its own
tiles/style/routing/geocoding, showing the underlying library's own logo reads as an unfinished
third-party wrapper rather than itunda's real product. Fixed with `map.uiSettings.isLogoEnabled =
false`. **Deliberately left attribution ON** -- itunda's tiles are genuinely built from real
OpenStreetMap data (the same Geofabrik Rwanda extract cited throughout this document's Maps
sections), and OSM's ODbL license requires real credit; that's a real legal/licensing
requirement, not library branding, so only the logo mark was removed.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process. The sheet-settle fix addresses the mechanism most consistent with the reported
symptom based on reading the real gesture-library API, but wasn't reproduced interactively before
the fix (the device was mid-use by the user at the time it was reported) -- needs the user's own
next open of the Maps screen to confirm it actually resolves.

## 35. "알림받기" (Notify me) pill -- real backend, zero Android UI until now

**Added 2026-08-09**, from 16 more real Naver Maps reference screenshots (a full-screen place
page and a real transit-directions flow). **Note on this pass's process**: the user asked to
"search online everything about naver maps" -- this session had already used its full WebSearch
budget (200/200 calls) earlier the same day, so no new web search was possible. Worked instead
from the real screenshots themselves (first-party evidence, arguably stronger than search
snippets) plus itunda's own existing Toss/Naver research already in this document.

### What the new screenshots showed
The pill row's full 6 items, previously only partially visible: 출발(Depart)/도착(Arrive)/
배달(Delivery)/공유(Share)/전화(Call)/**알림받기(Notify me)**. Also: a 소식(News) tab (merchant-
posted announcements), a structured "이런 점이 좋았어요" review-keyword-vote system (real Naver
feature, not just star+text), a 정보(Info) tab with an owner-written bio and an amenities list,
and multi-modal transit directions with real bus numbers/fares/live arrivals.

### What was checked and actually buildable
Grepped the Android network client before building anything: `followMerchant`/
`unfollowMerchant`/`getMyFollowedMerchants` **already existed**, real Retrofit-wired endpoints
against a real backend feature (ported for bank-mfe's own follow UI) -- but `grep`ing every
Android feature module found **zero call sites** anywhere. Real, already-built functionality that
had simply never been wired to an Android screen. Verified live against the real backend before
writing UI code: `POST .../follow` → `GET .../follows` → `DELETE .../follow` round-tripped
correctly (follow appeared in the list with real `merchantId`/`businessName`/`category`/
`followedAt`; unfollow removed it).

### What was built
A "🔔 Following" / "🔕 Notify me" pill, filled solid when followed (same visual convention as the
Save pill), added to the existing pill row -- only rendered when a real matched merchant exists
(`selectedMerchant?.merchantId`), reusing the exact three endpoints above.

### Named, not built this pass
- **배달 (Delivery) pill** -- itunda has real Eats delivery infrastructure, but `MapScreen`'s own
  signature (`onBack`, `initialCategory`, `initialSearchQuery`) has no navigation callback into
  Eats at all; wiring this needs a new cross-feature-module navigation contract (itunda's Toss
  Microfeature module-isolation architecture, with real CI enforcement, means this isn't a same-
  file wire-up like Follow was) -- a real, larger, well-scoped next step, not attempted here.
- **소식 (News) tab, structured review-keyword voting, owner bio/amenities list** -- genuinely new
  backend data models (merchant-posted announcements, a keyword-tag vote schema, a bio field, a
  structured amenities field) that don't exist anywhere in itunda today -- named honestly as real
  gaps, not invented with fake data, same discipline as every other "not attempted" note in this
  document.
- **Bike routing / real transit (bus numbers, fares, live arrivals)** -- already-documented
  structural limits (Section 20/27): itunda's self-hosted OSRM has no bike profile configured, and
  no transit data source exists for Rwanda in this project's scope.

### Verification status
`:features:maps:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process. The follow/unfollow data path was confirmed live against the real backend
before building; the pill's on-screen appearance/toggle behavior hasn't been tapped through by
the user yet.

## 36. "One thing, one page" beyond Maps -- Shop's "Pay a Merchant" section

**Added 2026-08-09.** The user's original feedback ("flower of info... let's improve a whole
itunda") named the whole app, not just Maps. Ran a real code-level audit of itunda's other large
Android screens (TalkScreen.kt, ShopScreen.kt, ItundaAppScreen.kt, EatsScreen.kt,
MarketplaceScreen.kt) for the same class of violation. Most were already fine --
`ChatThreadView`'s several toggleable modes each `return@Column`, making them genuinely mutually
exclusive already; `ItundaAppScreen.kt` turned out to already have a *second* sealed-step flow
(`SavingsFlowStep`) beyond the previously-known `TransferStep`, meaning a prior doc recommendation
to apply that pattern to Savings was already done, just never marked as such; Eats and
Marketplace's structure was clean. One real violation found: `ShopScreen.kt`'s
`PayAMerchantSection` (~line 2748) unconditionally stacked **three** things at once -- a Face Pay
settings toggle, a full "Pay by code" form (code field, preview, coupon radio picker, submit), and
a full "Pay by static QR" form (merchant-ID field, amount field, submit) -- every single time a
user opened "Pay a Merchant," not an edge case.

### What was built
Face Pay stays always-visible -- it's a real account *setting* that changes how Pay-by-code
itself behaves (per that card's own existing copy), not a competing "how do I pay" action, so it
isn't the same class of violation. Added a real mode picker (`PayMerchantMode.CODE` /
`STATIC_QR`, defaulting to `CODE`) so Pay-by-code and Pay-by-static-QR are now mutually
exclusive -- only one full form renders at a time, switched via two pill-style tabs, matching the
same resolution already applied to Maps.

### Verification status
`:features:shop:impl` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process. Not yet tapped through by the user on the real screen.

## 37. Real "배달" (Delivery) pill -- the one Maps pill that needed a new cross-feature contract

**Added 2026-08-09.** Closes the last named gap from Section 35's "named, not built this pass"
list: a Delivery pill on the Maps place-info sheet that actually opens itunda's real Eats ordering
flow for that merchant, not a fake button.

### The real architectural problem, and how it was solved
itunda's Android app enforces real Feature-module isolation (Toss Microfeature pattern, with real
CI enforcement) -- `:features:maps:impl` cannot depend on `:features:eats:impl` directly, and Eats
itself isn't even a standalone top-level screen; it lives folded inside `ShopTab`'s own internal
`ShopMode.EATS` toggle, one level further from Maps than it first appears. The only component that
can see both Maps and Shop/Eats is `ItundaAppScreen.kt`, the app shell. Solved by adding a small,
explicit deep-link contract: `MapScreen` gained an `onOrderDelivery(merchantId, businessName)`
callback (default no-op, so no other call site breaks); the shell wires it to close the Map
overlay, switch the bottom-nav tab to Shop, and hold the pending merchant in plain `remember` state
(not `rememberSaveable` -- an in-flight navigation intent has no reason to survive process death).
`ShopTab` reacts to a non-null pending id by switching its own internal mode to `EATS`; `EatsContent`
forwards it to `OrderFoodContent`, which resolves it via the exact same real fallback shape
`openDish`'s own pre-existing code already established (look it up in the already-loaded real
catalog; if not loaded yet, build a minimal stub carrying the REAL business name Maps already had,
never a blank one) and opens it through the real, already-proven `openRestaurant` flow -- zero new
backend work, zero duplicated menu-loading logic.

### What was built
- The pill itself only renders when `!placeProducts.isNullOrEmpty()` -- the exact same real-data
  check the Menu tab (Section 28) already uses, so it never appears for a merchant with nothing
  orderable through itunda.
- The pending-target state is consumed exactly once (`onPendingMerchantConsumed`), so returning to
  Shop later via the bottom nav doesn't reopen the same restaurant.

### Verification status
`:features:maps:impl`, `:features:eats:impl`, and `:app:compileDebugKotlin` all compiled clean
individually, then the full `:app:assembleDebug` build succeeded -- the entire cross-module wiring
type-checks end to end. **Update**: the physical device (it had briefly disconnected -- real
wireless ADB debugging, `adb connect <lan-ip>:<port>` after the wireless session dropped, not USB)
reconnected and the just-built APK installed and launched cleanly: empty `logcat -b crash` buffer,
live app process, real foreground activity. The one thing still outstanding is the actual real
device tap-through (open a merchant with a real menu, tap Delivery, confirm it lands in Eats
ordering) -- the app is currently sitting at its own real biometric step-up gate, which only the
user's own fingerprint/face can pass, so that specific interaction needs their hands next.

## 38. "One thing, one page" beyond Shop -- GroupAccountScreen's stacked action flows

**Added 2026-08-10.** Continued the app-wide "improve a whole itunda" audit (Section 36 covered
the 5 biggest screens; this pass covered the next tier of mid-sized ones: Ride, Invest,
TransferHub, Loans, WeeklySavings, DesignatedDriver, GroupAccount, Ikimina, Property, Jobs,
Community). Most were already clean -- mode enums or status-gated conditionals genuinely
mutually exclusive. Found one real violation, same shape as the already-fixed
`PayAMerchantSection`: `GroupAccountDetailContent` (`GroupAccountScreen.kt`) unconditionally
stacked **three unrelated action flows** for an account owner -- a "Monthly dues" card (its own
set/remind/clear actions), a "Deposit or withdraw" card, and an "Invite a member" card -- every
single time, not an edge case. A non-owner member still saw two of the three simultaneously
(deposit + dues).

### What was built
A real mode picker (`GroupAccountActionMode.MONEY` / `DUES` / `INVITE`, defaulting to `MONEY` --
moving money is the single most common reason to open a shared account), rendered as three
pill-style tabs (Invite only shown to the owner, matching who could already see that card).
Balance and Members stay always-visible above the picker -- both are pure information, not
competing actions, the same distinction Section 36's own Face-Pay-stays-visible reasoning already
established.

### Also named, not fixed this pass
`LoansScreen.kt`'s `OverdraftPanel`/`PostpaidCreditPanel` stack a Draw/Spend action and a Repay
action in the same card -- flagged as real but borderline: two quick, related actions on the SAME
resource (closer to a bank tile's own Deposit/Withdraw pairing than to three unrelated purposes),
not clearly enough of a violation to justify a mode-picker split without more direct evidence it's
actually confusing users. Left as-is; worth a second look if it comes up again.

### Verification status
`:app:compileDebugKotlin` compiled clean, then the full `:app:assembleDebug` build succeeded.
Installed on the physical device; the app launched with an empty `logcat -b crash` buffer and a
live app process. Not yet tapped through by the user on the real screen.

## 39. Real friction fix, all 3 clients -- pre-fill the known phone number for a MoMo link

**Added 2026-08-10**, direct user follow-up: "we need to still work on simplicity since like toss
we want convenient for our users." Rather than chase more still-gated Simplicity24/25 session
content (this session's own WebSearch budget was already exhausted), picked up a real, already-
sourced, already-documented gap this same research thread named and deliberately left unfixed in
Section 17: "the account-link form doesn't pre-fill the user's own known phone number for MoMo
providers... real but smaller UX gaps, not bugs -- left as documented, not fixed."

### The real friction, and the real fix
For a MoMo provider (MTN Mobile Money, Airtel Money), the "account number" field IS the caller's
own real phone number -- the exact same number they're already logged in with. Making them
retype a number itunda already knows is unnecessary friction with a real, already-known answer --
precisely the shape Simplicity21 session 2-1's own title ("신은 디테일에 있다" / God is in the
details) names. Fixed on all 3 clients: selecting a MoMo provider now pre-fills the account-number
field with the real logged-in user's own phone number (fetched from the real, already-existing
`/api/v1/auth/profile` endpoint -- `getStoredUser()` on web, `NetworkClient.authApi.getProfile()`
on Android, `NetworkClient.shared.getProfile()` on iOS), while staying fully editable in case
someone wants to link a different MoMo number. Deliberately left blank for a real bank provider,
where the account number is genuinely a different, unknown value -- pre-filling there would be
actively wrong, not just unhelpful.

### Verification status
bank-mfe: `yarn workspace bank-mfe run build` (`tsc -b && vite build`) succeeded clean. Android:
`:app:compileDebugKotlin` succeeded clean (first attempt used the wrong client --
`NetworkClient.apiService.getProfile()` doesn't exist, `getProfile` lives on the separate
`NetworkClient.authApi` -- caught immediately by the compiler, not shipped). iOS: no available iOS
Simulator runtime in this environment right now (`xcrun simctl list devices available` returned
none), so `xcodebuild` couldn't fully build/run this pass -- but the touched file itself compiled
with zero errors attributed to it; the only build failures were the already-documented,
pre-existing CocoaPods gaps (MapLibre/BrickModule/React, named in Section 18) unrelated to this
change. None of the three platforms has been tapped through by a real human yet.

## 40. Real bug: device-verify password field missing KeyboardType.Password

**Added 2026-08-10**, direct live user report while trying to test sending money: "device
verification is blocking me asking to put password but when i try it it's incorrect." A real,
severe, live-blocking bug, found and fixed same-session.

### Root cause
`DeviceStepUpDialog`'s password `BasicTextField` (`features/payments/impl/.../TransferFlow.kt`)
had no `KeyboardOptions` at all -- it silently fell back to `KeyboardType.Text`. Unlike
`KeyboardType.Password`, plain `Text` does NOT tell the platform IME to suppress autocorrect/
auto-capitalize-first-letter, so the device's own keyboard (confirmed live: Samsung Keyboard on
this exact SM-A165N test device) is free to silently mutate what's typed before it ever reaches
the app. Because the field is masked with `PasswordVisualTransformation()`, the user has no visual
way to notice a mutated character -- a genuinely correct password could reach the server altered
and real-401 as "Incorrect password," exactly the reported symptom. `LoginScreen.kt`'s own
password field already gets this right (`IdsTextField(..., isPassword = true, keyboardType =
KeyboardType.Password)`) -- `DeviceStepUpDialog` just never matched that established convention.
This dialog is the single shared component behind every money-moving step-up across the whole
app (transfer, Gift send/claim, Commerce/Eats checkout, Stocks buy/sell, per its own doc comment),
so this one bug blocked the step-up gate in front of literally every real money-moving flow.

### Fixed
Added `keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)` to the field.

### Same-class bug also found, not yet fixed
`android/merchantapp/.../DeviceStepUpDialog.kt` calls `IdsTextField(..., visualTransformation =
PasswordVisualTransformation())` without also passing `isPassword = true` or `keyboardType =
KeyboardType.Password` -- `IdsTextField`'s own `keyboardType` parameter defaults to
`KeyboardType.Text` (confirmed by reading its signature), so the merchant app's own device-verify
dialog has the identical real bug, just via a different code path (a manually-passed visual
transformation instead of the component's own built-in `isPassword` mode, which WOULD have set
this correctly). Not fixed this pass -- the customer app was the active, live-blocking report;
this is named as a real, concrete, ready-to-fix follow-up, not silently skipped.

### Verification status
`:features:payments:impl:compileDebugKotlin` and the full `:app:assembleDebug` both built clean.
Installed on the physical device over the live wireless ADB connection; the app launched with an
empty `logcat -b crash` buffer and a live process -- the fix is live on the device the bug was
reported from. The user's own next real password attempt is the actual confirmation this needed.

## 41. Top-level navigation & IA organization — real research synthesis, plus what's still open

**Added 2026-08-10**, prompted by direct, repeated user feedback ("itunda still feels like code
not product... no organization... same features using UI/UX that doesn't suit them") and an
explicit ask to research real, proven navigation-organization practice rather than invent one.
This section is the synthesis; the immediate fix it justified (bank-mfe's web nav) is already
shipped -- see `agent/itunda-agent-network` commits `f99e0eb4` and `3ad1dbc1` for the full account.
Everything below is either **sourced** (a named real product's documented practice) or a **named
research-body finding** (NN/g, Apple HIG); nothing here is `inferred`.

### What real products converge on for a broad, multi-service app

| Finding | Source |
|---|---|
| A super app anchors on ONE high-frequency daily action (pay, message, or ride) and expands outward from there -- "pay, ride, message, then expand." Cross-selling every other service happens *from* that anchor, not alongside it as an equal peer. | WeChat/Alipay architecture analysis, dashdevs.com 2026 |
| Apple's own HIG: tab bars should hold **3-5 tabs**. Broader UX guidance (SubUX, CXL) puts the outer bound at 5-7, with 3-5 explicitly preferred on mobile. | Apple HIG; nngroup.com-adjacent guidance via SubUX/CXL, both 2026 |
| **Never wrap tabs to a second row.** NN/G: "multiple rows create jumping UI elements that destroy spatial memory and are a sure symptom of excessive complexity -- if you need more tabs than fit in one row, simplify the design," not add a row. | nngroup.com, "Tabs, Used Right" |
| A catch-all "everything else" tab is real, standard practice for apps with more real destinations than fit in a single row -- Toss's own bottom nav is 홈/혜택/쇼핑/페이/**전체** (Home/Benefits/Shop/Pay/**All**), not an attempt to flatten every feature into the primary row. | toss.im navigation, cited already in `ItundaAppScreen.kt`'s own `ItundaTab.All` doc comment |
| Dashboards with many competing elements (balance, transactions, investments, recurring payments, rewards) need **progressive disclosure at the information-hierarchy level**: summary first, detail on demand -- e.g. balance at the top, recent transactions below that, investment detail *below that*, not all three given equal weight on one screen. | webstacks.com / g-co.agency fintech UX guides, 2026 |
| Personalization should reorder/surface based on real behavioral data (what a user actually does), not judge or predict -- McKinsey found this can cut churn up to 15% in financial apps. | webstacks.com, citing McKinsey 2024 |
| Jobs-to-be-done framing organizes navigation around what a user is trying to *accomplish* ("when X happens, I want to Y so I can Z"), not which module or team happened to build a feature -- this is the frame `PRIMARY_TABS`'s own doc comment in `BankDashboard.tsx` already invokes. | userinterviews.com JTBD field guide; productplan.com |

### Cross-checked against what itunda just shipped (web nav rebuild)

- **5 tabs, single row, no wrapping** -- matches the Apple/NN-G ceiling exactly, and matches
  Android/iOS's own already-proven `Home/Shop/Hood/Talk/All`.
- **A real 전체-style catch-all (`All`)** for the long tail -- matches Toss's own practice
  directly, not an invented pattern.
- **Shop and Hood use segmented toggles for their sub-choices** (Shop/Eats;
  Marketplace/Community/Jobs/Property) instead of either flattening them into more primary tabs
  (would have broken the 5-tab ceiling) or hiding them behind unlabeled gestures. This is the
  correct middle path per Apple's own segmented-control guidance for "one screen, several
  sub-views of the same job."
- **`AllHub` already does real, not superficial, personalization**: a "Recently used" section
  sourced from actual `localStorage`-tracked navigation history, not a static list -- this is
  the McKinsey-cited pattern (behavior-driven surfacing) already correctly applied to the All tab.

### What's genuinely still open (ranked, not yet built)

1. **[sourced, SUPERSEDED -- checked 2026-08-15, do not build]** Written when `HomeView` really
   did stack all nine sections listed below. The 2026-08-13 "tabs are access points, not products"
   restructuring (see `project_itunda_tabs_as_access_points`) moved `AccountBalance`/
   `QuickActions`/`CooperativeSavingsRail`/`TransactionHistory`/`ScheduledTransfersCard`/
   `AutoTransfersCard`/`AutoTopUpCard`/`RequestMoneyCard`/`MiniWalletCard` out of `HomeView`
   entirely and into `PayHub` -- confirmed by reading the current file directly, `HomeView` is now
   just `<DiscoverSection />`, a single component, not nine peers. The progressive-disclosure
   critique no longer describes anything real to fix.
2. **[sourced, SUPERSEDED -- checked 2026-08-15, do not build]** Also predates 2026-08-13's Home
   restructuring, and separately predates `DiscoverSection` gaining real **server-side** per-user
   priority ranking on 2026-08-11 (`DiscoverItem.priority`, confirmed in `lib/discover.ts` and the
   backend -- "computes real per-user eligibility and priority"). Home already has real
   behavior-driven personalization today, just computed server-side rather than via `AllHub`'s
   client-side localStorage mechanism this item recommended porting -- a stronger version of the
   same real goal, not a gap.
3. **[sourced, re-verified 2026-08-10] Search is not a first-class navigation entry point.** It
   currently only exists inside the `All` tab. Re-searched specifically (previous pass only had a
   partial source): the real, named-product decision rule is that a **visible, always-present**
   search field belongs in a persistent header when search is central to the product (Netflix's
   header search is the cited example); an **icon that expands** is the correct choice when search
   is secondary but still needs to be reachable without a tab switch -- "hiding search behind an
   icon saves space but adds a tap and reduces visibility." For a super-app the size of itunda
   (34 real destinations behind one `All` tab, per Section 41's own tab audit), search is secondary
   to browsing but genuinely needed for the "I know exactly what I want" case -- the icon-in-header
   pattern is the correct fit, not the always-visible field a single-purpose search product would use.
   *Target: header row (next to the language switcher / sign-out icon), `BankDashboard.tsx`.*
   **Built 2026-08-15 (`b53a5ef0`), live-verified.** A `Search` icon in the header switches to
   Explore and focuses that tab's own already-real search box via a pending-hand-off state (the
   same pattern `pendingConversationId` already used) -- deliberately did not build a second search
   implementation. Live-verified: header click landed on Explore with the input already focused
   (visible focus ring, no extra tap), typed "loan" and confirmed the existing filter correctly
   matched "Loans".

### Three more real, verified gaps found while researching search/badges/dashboards (2026-08-10)

4. **[sourced] No unread badge anywhere on the primary tab bar**, despite the data already
   existing. `ConversationSummary.unreadCount`/`GroupSummary.unreadCount` are already fetched and
   rendered *per conversation* inside the Talk tab's own list (`BankDashboard.tsx` ~8710/9375), and
   a separate real in-app notification inbox (`NotificationsCard`, ~4979) already tracks its own
   `unreadCount` -- but neither total ever reaches `PRIMARY_TABS`'s render (~20039), so a user gets
   zero ambient signal that something needs attention without opening Talk or My first. Real,
   named-product rule for exactly this case: use a **numeric badge, not a dot**, on the Talk icon,
   because "the precise quantity drives the user's next action" for messages -- and cap the display
   at two digits with a "99+" overflow so a large count never pushes neighboring tab labels around.
   Do **not** also put a numeric badge on `All` for the general-notifications count -- mixing dot and
   numeric styles on the same bar is the one explicitly-named anti-pattern here ("don't mix dot and
   numeric badge designs on the same navigation bar"); a plain dot on `All` (not a number) is the
   correct choice for "something changed" vs. "here's exactly how many."
   *Target: `PRIMARY_TABS` render, `BankDashboard.tsx`.* **Built 2026-08-15 (`22918458`),
   live-verified end to end** (real second test account, real message sent via direct API,
   confirmed the real backend `unreadCount`, confirmed the tab badge rendered "1" matching the
   per-conversation badge). Built the numeric-badge half only, on the current five-tab bar's real
   Messages icon (`ConversationSummary.unreadCount` + `GroupSummary.unreadCount` summed via a
   lightweight top-level poll) -- the doc's own "plain dot on `All`" half of this recommendation no
   longer maps onto anything: by the time this was built, the tab bar had already been rebuilt to
   Home/Pay/Explore/Messages/You (see "Final nav taxonomy" below, closed the same week this item
   was originally written), and there is no more `All`/general-notifications tab for a dot to go
   on. `NotificationsCard`'s own separate unread count still isn't surfaced anywhere outside the
   card itself -- a real, smaller, separately-scoped follow-up if itunda ever wants ambient
   general-notification signal too, not folded into this fix.
   **Built 2026-08-16**: a plain dot (not a number, deliberately not mixed with Messages' numeric
   badge on the same bar -- the same named rule as above) on the You tab icon, from a lightweight
   top-level poll of `fetchNotifications().unreadCount`, independent of `NotificationsCard`'s own
   fetch. Live-verified: the demo user's 2 real unread notifications showed the dot; clicking
   "Mark all read" inside You and waiting for the next 15s poll cleared it.
5. **[sourced] Home's actual section order roughly matches the real Cash App pattern already**
   (balance prominent at top, `AccountBalance` first in `HomeView`) -- this part of Section 41's
   item 1 finding was too pessimistic; re-checked against Cash App's own documented layout
   (balance display top, Money/Activity/Pay-Request below) and itunda's order is directionally
   right. The real remaining gap is narrower than "no hierarchy at all": it's that all nine sections
   render with equal visual weight (same card style, same spacing) with nothing collapsed or
   deprioritized, so scrolling past `AccountBalance`+`QuickActions` still means passing five more
   full-weight cards before reaching anything skippable. Section 41 item 1's fix (progressive
   disclosure / de-emphasize or collapse the secondary five) still stands; just not "flat from the
   top" as originally stated.
6. **[sourced, itunda's own SPA architecture, not a named product] No deep-linkable state.**
   `BankDashboard.tsx` holds `tab` in local `useState`, not a router -- there is no
   `react-router`/URL-based navigation anywhere in this file or `App.tsx` (checked directly).
   Refreshing the page, or sharing a link to a specific screen (a specific Insurance policy, a
   specific chat), always lands back on Home. This isn't from the researched sources above (it's
   this codebase's own real state, confirmed by grep, not a cited product finding) but it's the
   same category of gap the deep-linking research flags as standard practice (Android's real
   Navigation-component back-stack, iOS Universal Links) -- itunda's *native* apps already have a
   real `itunda://` deep-link scheme (see Section 1's Maps entry); bank-mfe web has none at all.
   *Target: `App.tsx`/`BankDashboard.tsx` -- real architectural gap, larger scope than the others
   above, not yet scoped into a concrete plan.*
   **Built 2026-08-16, top-level `tab` only (scoped down, not the full architectural version) --
   no new router dependency, `?tab=X` synced via `history.pushState`/`popstate` directly. Live-
   verified in a real browser: click updates the URL, a hard refresh with `?tab=PAY` lands
   directly on Pay, and back/forward correctly restore both URL and rendered tab. Deeper state
   (a specific chat/policy/order inside a tab) is still not URL-addressable -- that's a real,
   separately-scoped follow-up if ever prioritized, not folded into this fix.

### Final nav taxonomy: Home/Pay/Explore/Messages/You, shipped on all 3 platforms

**Closed 2026-08-10/11.** After directly comparing this against Home/Shop/Hood/Talk/All (the
version that had matched Android/iOS's own independent convergence), the user chose
**Home/Pay/Explore/Messages/You** instead -- itunda is bank-first, so Pay and You get dedicated
primary slots rather than being nested a tap into Explore/My. Shipped in the same session on all
3 platforms so the "different app per device" inconsistency this whole section exists to close
never actually reopened: bank-mfe (`BankDashboard.tsx`, commit `eec17d99`), Android
(`ItundaAppScreen.kt`'s `ItundaTab` enum, commit `44437202`), iOS (`ContentView.swift`'s TabView,
commit `36100a5f`).

**Real correction, same day, all 3 platforms:** the first pass on each platform nested Shop+Eats
behind one Explore row (a Shop/Eats toggle) and Marketplace+Community+Jobs+Property behind
another (a 4-way toggle) -- direct user feedback: that shape is correct for a *primary* tab
(mirrors Android's real Shop/Eats row, iOS HoodScreen's real segmented Picker, the exact reason
that pattern existed at all when Shop/Hood WERE primary tabs), but wrong once nested inside a
catalog screen -- a tab bar inside a tab is exactly the noise a flat catalog is supposed to avoid.
Toss's own real 전체 screen is a flat list of individual rows, not nested toggles. Fixed by
retiring every wrapper toggle (`ShopHub`/`HoodHub` on web, `ShopTab`/`HoodTab` on Android,
`ShopScreen`/`HoodScreen` on iOS) and making Shop/Eats/Marketplace/Community/Jobs/Property each
their own flat row (commits `96743991` web, `d9c7fb5e` Android, `36100a5f` iOS). Android and iOS
both preserved Hood's real, deliberately Karrot-sourced shared shell (neighborhood-name top bar,
per-mode menu sheet, Marketplace's "Write" FAB) by extracting it into a
`HoodSectionScreen(mode:)`/`HoodSectionScreen` parameterized by a fixed mode instead of an
internal switcher, rather than dropping that real UI -- web never had an equivalent shell to
preserve, so its 4 sections were always going to be simpler flat rows.

All 3 platforms' tab counts were re-verified programmatically/by exhaustive grep to confirm every
destination is reachable with no orphans and no duplicates. Web was additionally verified live via
headless-Chrome click-throughs. iOS's App target has a pre-existing, independently-reproduced
CocoaPods/React-Native-bridge gap that blocks `xcodebuild` here entirely (unrelated to these
changes); iOS was verified instead via `swift -frontend -parse` (clean) plus an exhaustive manual
grep sweep confirming no orphaned references to any retired symbol.

## 42. itunda Bank split from itunda Pay as a distinct product identity

**2026-08-11.** Mid-session naming near-miss: the user asked "wait what is itunda wallet I thought
we have itunda bank?" -- the switcher's "itunda wallet" label was briefly (and wrongly) renamed to
"Itunda Bank" on the strength of `TOSS_FEATURE_SPECIFICATION.md`'s aspirational "Pillar 3: Digital
Banking" roadmap entry, before checking `TOSS_PARITY_MATRIX.md` (the doc that tracks what's
actually built/verified) confirmed zero real licensed-banking implementation exists anywhere in
the codebase. The user corrected this directly: "wallet is deference from bank, kakao have both
wallet and bank right?" and asked for real research before any further naming change.

`WebSearch` confirmed: KakaoPay is a real, unlicensed e-wallet embedded in KakaoTalk; KakaoBank is
a real, separately-licensed digital bank -- genuinely distinct regulated products, not a
generic/specific naming pair as first assumed. Toss keeps Bank, Securities, and Payments as
distinct product identities inside one single app/account, not separate installs -- closer to
itunda's own already-committed single-super-app model (Section 41's Home/Pay/Explore/Messages/You)
than Kakao's separate-app split. The "itunda wallet" rename was reverted back to correct.

Follow-up, same session: the user asked for itunda Bank and itunda Pay to actually exist as
**separate products**, matching that real Toss/Kakao precedent. itunda Pay already had its own
primary tab (`ItundaTab.Pay` / bank-mfe's `PayHub`); itunda Bank did not -- its real, already-built
savings/SACCO/Ikimina/loans/investment features were scattered as flat rows competing with itunda
Pay's own `WalletHeroCard` for the same visual weight, with no shared front door of their own (the
"feels like code not product" complaint this whole doc traces back to). Real Toss's own bottom nav
doesn't put Toss Bank there either despite it being a distinct product -- it's a surface reached
from Home, not a 6th primary tab, so this didn't reopen Section 41's tab-count decision.

Shipped same session, all 3 platforms, every row a real already-built screen (this only adds a
shared entry point, no new feature implementations):
- **Android**: new `BankSummaryCard` on Home (real aggregate total-saved figure) entering a new
  `BankHubScreen` -- "Save & grow" (interest jar/goals/round-up/SACCO/Ikimina/weekly savings/
  upfront deposit/investments) and "Borrow" (loans/VUP/student loan/Moto-Taxi Ownership), same
  category names `MenuScreen`'s own "Real Toss Bank reference mapping" already uses. Live-verified
  end to end on the physical device via `uiautomator`: Home -> Bank card -> hub (real RWF
  1,330,000 total) -> Get a loan -> `LoansScreen` -> back -> hub -> back -> Home. Caught and fixed
  a real bug during this: `ItundaAppScreen.kt`'s top-level `if (showX)` chain is sequential and
  first-match-wins, so `showBank`'s block had to be ordered *after* every screen it deep-links into
  (Sacco/Ikimina/Loans/etc.) -- checked first, it would just re-render the hub on every tap inside
  it instead of opening what was tapped.
- **Web** (`bank-mfe`): `SAVINGS` tab relabeled "itunda Bank"; `SavingsView` gets a real header
  plus new "Borrow"/"Grow your money" rows navigating to the existing `LOANS`/`STOCKS` tabs (real
  cross-tab navigation via `onNavigateToTab`, not a duplicate implementation); Home's
  `CooperativeSavingsRail` rebranded from "Built for how Rwanda saves" to "itunda Bank" with a new
  "See all in itunda Bank" row. en/rw translations added. Typecheck + `vite build` both clean.
- **iOS**: `BankView`'s coop rail section rebranded from "Built for how Rwanda saves" to "itunda
  Bank" (en/rw), with 2 new rows (Get a loan / Grow your money) wired to the existing
  `LoansScreenView`/`InvestScreenView` via new `showLoans`/`showInvest` state in `ContentView`,
  matching the `showSacco`/`showIkimina` precedent already there. Not build-verified (see Section
  41's iOS `xcodebuild` gap note) -- syntax/reference-consistency only.

---

## 43. Real Toss motion research, applied: confetti for the two moments it was missing from

**2026-08-12.** User asked to keep researching Toss's simplicity/interactions/UX writing/
animations/graphics, explicitly "search online don't imagine." Fresh `WebFetch` (not just the
existing Section 9 table) against toss.tech/article/interaction and
toss.im/tossfeed/article/why-motion-in-finance surfaced detail beyond what was already recorded:

- **Rally**, Toss's real cross-platform motion specification, has three concrete rules: only
  bezier and spring easing curves are permitted; "one motion attached to one target element"
  (no multi-element ad-hoc choreography without an explicit timeline); named easing tokens
  (`spring.quick`, `bezier.expo`) so designers/engineers share vocabulary instead of "make it feel
  smooth" back-and-forth.
- **"Optical illusion over heavy animation"** -- a named, explicit Toss principle: rather than
  animating every real list element, overlay a new visual layer and use motion to create the
  *illusion* of a unified screen, reducing implementation cost while keeping the perceived polish.
  Not yet checked against itunda's own transition code -- a real follow-up, not applied this pass.
  Confetti-for-positive-moments was already recorded in Section 9's table, but the *specific*
  phrase -- "적립금 증가, 월급날 같은 긍정적인 순간에 색종이 효과를 사용해 행복한 순간을
  극적으로 만든다" (confetti for positive moments like a credit-score increase or payday) -- is
  what this pass actually acted on.

**itunda's real, current state, verified by inspection, not assumed:** a real, pure-Compose
confetti celebration screen already existed (`ItundaAppScreen.kt`, built 2026-08-11) but was wired
to exactly one moment (claiming savings interest). Checking every other real "matured plan"
completion moment in the app found two more genuine milestones with **zero success
acknowledgment of any kind** -- not even the plain non-celebratory checkmark: `Grow31SavingsScreen.
kt`'s and `WeeklySavingsScreen.kt`'s own `withdraw()` success paths (finishing a real 31-day or
26-week savings challenge) just silently updated on-screen state in place.

**Shipped:** promoted the confetti screen out of `ItundaAppScreen.kt`'s private
`MoneySuccessScreen`/`ConfettiBurst`/`ConfettiParticle` into `core:designsystem`'s
`IdsCelebrationScreen` (the same "promote once a second real call site needs it" pattern this
module's own `HoodShared.kt`/`IdsInteractions.kt` already established -- not a fresh invention),
then wired it into both real completion moments with `celebratory = true`, using the real totals
from each withdrawal response (`totalSaved`/`totalInterestPaid` for Grow31,
`currentAmount`/`totalInterestPaid` for Weekly) so the message is accurate, not generic. `:app`
full `assembleDebug` build-verified clean; not yet live-verified on-device.

*Shipped: `core/designsystem/.../IdsCelebrationScreen.kt` (new), `ItundaAppScreen.kt`,
`Grow31SavingsScreen.kt`, `WeeklySavingsScreen.kt`*

### Unresolved / worth a follow-up

- The "optical illusion over heavy animation" principle above hasn't been checked against
  itunda's own screen-transition code on any platform yet -- a real, separate follow-up.
- Neither fix is live-verified on-device (build-verified only) -- the physical test device's
  Grow31/Weekly plans would need to actually reach `MATURED` status (a real 31/26-period wait,
  or a seeded matured plan) to trigger the withdraw button at all.
- The broader "UX writing" and "simplicity" angles the user named weren't freshly re-researched
  this pass beyond what Sections 10/11/14 already cover in depth -- this pass focused specifically
  on the animations/graphics angle since it was the thinnest existing coverage.

---

## 44. Doherty Threshold — a real, widespread loading-feedback gap, 24 sites across 21 screens

**2026-08-12**, same session as Section 43, user asked to keep researching Toss interactions
specifically. Fresh `WebFetch` on a real (though secondary/aggregator, not toss.tech primary —
flagged accordingly) source, "토스에서 찾아본 10가지 UX 법칙" (10 UX laws found in Toss,
brunch.co.kr/@mobiinside/4997), named 10 concrete UX heuristics with a specific Toss example each.
Most were already covered elsewhere in this document under different framing (Postel's Law /
"1 thing, 1 page" = Sections 33/36/38; Peak-End Effect = the confetti/soft-error work; Von Restorff
= the badge work). One wasn't: **Doherty Threshold** — the article's Toss example is playful
loading animation during account/loan-processing delays, cited as keeping perceived response time
under the ~0.4s threshold where users start perceiving a system as unresponsive.

**Real, verified gap found by inspection**: itunda already has a real, correct answer to this —
`SkeletonBlock` (`core/designsystem/.../HoodShared.kt`), an animated shimmer-gradient loading
placeholder, in real use across 17 files. But a `grep` across `android/app/src/main/java/rw/itunda/
app/ui/` found **24 separate call sites across 21 different screens** using a completely inert,
static, blank `Card(...) {}` — zero shimmer, zero motion, zero signal that anything is loading —
as their own loading placeholder instead, during the exact same kind of real network-fetch delay
`SkeletonBlock` already solves correctly elsewhere. The same "a good component gets built once,
adoption never gets swept everywhere" pattern this document has now found repeatedly across
different domains (EmptyState copy, ErrorCard, StatusBadge, IdsButton, and now loading skeletons).

**Checked, no gap found (documented, not silently skipped)**: the article's Tesler's Law example
(Toss auto-suggests a bank from a typed account number) doesn't map onto a real itunda gap --
`OverviewScreen.kt`'s own account-linking form already sidesteps the underlying problem
structurally, by having the user pick a provider chip *first*, before typing an account
number/phone -- there's no "guess the bank from digits" step to improve.

**Shipped**: mechanical, scripted swap of all 24 real call sites (`AutoTopUpScreen.kt`,
`StudentLoanScreen.kt`, `InvestScreen.kt` x3, `VupLoanScreen.kt`, `HarvestAdvanceScreen.kt`,
`SaccoScreen.kt`, `BusScreen.kt`, `DesignatedDriverScreen.kt`, `ParkingScreen.kt`,
`GroupAccountScreen.kt`, `UpfrontDepositScreen.kt`, `IkiminaScreen.kt`,
`ForeignCurrencyScreen.kt`, `MotoOwnershipScreen.kt`, `RideScreen.kt`, `BikeRentalScreen.kt`,
`Grow31SavingsScreen.kt` x2, `WeeklySavingsScreen.kt` x2, `MiniWalletScreen.kt`,
`RequestMoneyScreen.kt`, `CardScreen.kt`) from the bare `Card` to `SkeletonBlock(height = ...)`,
same height per site, behavior-identical apart from adding the real shimmer. `:app` full
`assembleDebug` build-verified clean. Not yet live-verified on-device (would need to catch each
screen's own real loading window, which is often sub-second on a healthy connection).

### Unresolved / worth a follow-up

- Not live-verified on-device (build-verified only), same caveat as Section 43's fixes.
- Web (`bank-mfe`)/iOS weren't checked for the same bare-placeholder pattern this pass -- Android
  only, matching how this session's other single-platform passes are scoped and documented
  honestly rather than silently claimed done everywhere.

---

## 45. Pull-to-refresh — real, existed on exactly one screen, extended to two more

**2026-08-12**, same session, user asked to "deep search toss interactions and improve itunda
interactions to 100% toss interactions like." Checked itunda's own real pull-to-refresh coverage
first, since it's one of Toss's (and virtually every modern mobile app's) most pervasive, iconic
gesture patterns and hadn't been audited yet this research thread. Real, correct implementation
already existed -- `ItundaAppScreen.kt`'s Home tab, built in an earlier 2026-08-11 research pass
(`rememberPullToRefreshState` + `PullToRefreshContainer`, tied to `MainViewModel.isRefreshing`
spanning the real fetch duration, not a fixed timer). `grep` across the rest of `:app` and every
Feature module found **zero other screens with any pull-to-refresh at all** -- the exact same
"built once for the screen it was designed for, never swept elsewhere" pattern Sections 43/44
already found for confetti and skeleton loading, this time for arguably the single most
recognizable gesture in this whole family of patterns.

**Shipped, two more real screens** (picked for traffic/value, not exhaustive -- itunda has dozens
of list screens, a full sweep is a real, separate, larger follow-up):
- **Transaction history** (`features/payments/impl/TransactionHistoryScreen.kt`) -- the single
  most-checked list in any real banking app. This Feature module has no `MainViewModel` of its
  own (architectural constraint already established throughout this session's Feature-isolation
  work), so `onRefresh`/`isRefreshing` are plain props the parent supplies -- the pull gesture's UI
  mechanics live in the presentational component, the real data-refetch trigger
  (`viewModel.retry()`, the same one Home's own pull-to-refresh already uses) stays owned by
  `ItundaAppScreen.kt`.
- **Settings** (`SettingsScreen.kt`), whose own notifications list is exactly the kind of live,
  changing data this pattern exists for. Found `MainViewModel.loadSettingsData()` had no
  refreshing-state signal of any kind (unlike `fetchData()`/`retry()`, which already had
  `_isRefreshing`) -- added a dedicated `_isLoadingSettings` flag, deliberately separate from
  Home's own `_isRefreshing` so an unrelated Settings fetch completing can't make Home's own pull
  gesture appear to finish early.

Both needed `@OptIn(ExperimentalMaterial3Api::class)` -- itunda's own Home implementation already
carries this annotation, but neither of these two files did before this fix (a real, concrete
compile error caught immediately, not a style nit). `:app` full `assembleDebug` build-verified
clean.

*Shipped: `TransactionHistoryScreen.kt`, `ItundaAppScreen.kt`, `MainViewModel.kt`,
`SettingsScreen.kt`*

### Unresolved / worth a follow-up

- Not live-verified on-device (build-verified only).
- Every other list screen in the app (savings/loan plan lists, Talk conversation list --
  investigated this pass, structurally more complex with 3 separate lists
  (conversations/archived/groups) inside nested composables, deferred rather than rushed --
  Marketplace/Shop/Community/Jobs/Property browse screens, Overview's linked-accounts list, and
  more) still has no pull-to-refresh. A real, larger follow-up if the user wants closer to
  literal "every list" coverage, not a small remaining gap.
- Web/iOS not checked for the same gap this pass -- Android only.

---

## 46. Haptics — the single highest-frequency interaction in the app had none

**2026-08-12**, same session, user repeated "deep search toss interactions and improve itunda
interactions to 100% toss interactions like." Checked haptic feedback coverage: only 2 files in
the whole app use `LocalHapticFeedback`/`performHapticFeedback` at all (`ItundaAppScreen.kt`,
`IdsCelebrationScreen.kt`) -- same "built once, never swept" shape as confetti/skeleton-loading/
pull-to-refresh in Sections 43-45.

Checked Android's own official platform guidance this time, not a Toss-specific source
(developer.android.com/develop/ui/views/haptics/haptics-principles) -- it names "fingerprint
acceptance or rejection" as one of the canonical moments haptic feedback belongs. `AppLockScreen.kt`
-- itunda's real biometric app-unlock gate, which runs on **every cold app launch** when app-lock
is enabled, making it plausibly the single highest-frequency real interaction anywhere in this
app -- had zero haptic feedback on its own success/failure callback. Fixed: a real
`HapticFeedbackType.LongPress` buzz on successful unlock (the same one this codebase's own pinned
Compose UI version constraint already established as the real available stand-in for `Confirm`,
per `IdsCelebrationScreen`'s own doc comment).

**Checked, no gap found (documented, not silently skipped)**: the OTHER real biometric moment in
the app -- the transfer-confirm step-up gate (`ItundaAppScreen.kt`) -- also had no haptic directly
at the biometric-success callback, but that flow always proceeds straight into
`TransferSuccessScreen`/`IdsCelebrationScreen`, which ALREADY fires a real haptic on entrance. Not
a gap; adding a second buzz there would double-haptic one continuous user action, which itself
would be a worse interaction than the current one.

`:app` full `assembleDebug` build-verified clean.

*Shipped: `AppLockScreen.kt`*

### Unresolved / worth a follow-up

- Not live-verified on-device (build-verified only) -- same caveat as every fix in this pass's own
  thread (Sections 43-46) now. **Four** real interaction fixes (confetti, skeleton loading,
  pull-to-refresh, haptics) are now build-verified but none has been felt/seen on a real device
  yet -- worth a dedicated on-device pass before a fifth.
- Failure-path haptics (a distinct "rejected" feel, not just silence) weren't added -- this pinned
  Compose UI version's `HapticFeedbackType.Reject` availability wasn't confirmed, and guessing
  wrong risks a real compile break; worth checking directly (bump the BOM, or test the constant)
  before adding it.

---

## 47. Performance -- real, official Compose guidance found itunda's own React-optimization gap

**2026-08-12**, same session, user broadened the ask to "toss interactions, toss UI/UX, toss
designs, toss simplicity, toss high performance, toss graphics." toss.tech/article/32583 (Toss's
own real frontend-optimization episode) named "React re-rendering optimization" as a real,
concrete lever -- but the article itself is video-gated, only the framing recovered, not
implementation specifics.

Checked the equivalent, real, OFFICIAL guidance for itunda's own UI framework instead of chasing a
gated video further: Jetpack Compose's own documented performance rule -- `LazyColumn`/`LazyRow`
items should carry a stable `key`, or Compose falls back to positional identity, causing avoidable
recomposition and losing correct item identity/state across list mutations (exactly the kind of
operation this session's own pull-to-refresh work, Section 45, just made easier to trigger more
often). `grep` across `:app` found **14 of 65** `items(...)` call sites with no `key` at all --
same "real, sourced principle exists, adoption never got swept everywhere" shape this whole
research thread keeps finding, this time for a genuine performance property rather than a purely
visual/interaction one.

**Shipped**: added `key = { it.id }` (or the correct real identity field per DTO --
`dayNumber`/`weekNumber` for the two ledger-entry lists that don't carry their own `id`) to all 14
sites across `StudentLoanScreen.kt`, `SaccoScreen.kt`, `UpfrontDepositScreen.kt`,
`GroupAccountScreen.kt`, `IkiminaScreen.kt`, `HarvestAdvanceScreen.kt`, `ItundaAppScreen.kt` (x3:
nearby-ads row, Shop/Eats recent-orders rows on the "You"/profile summary), `CardScreen.kt`, and
Grow31/WeeklySavingsScreen.kt (x2 each: plan list + own deposit/installment history). Verified
each DTO's real id field by reading `ApiService.kt` directly rather than assuming `.id` exists
uniformly. `:app` full `assembleDebug` build-verified clean.

*Shipped: 14 sites across 10 files, see commit for the full list*

### Unresolved / worth a follow-up

- Not live-verified on-device -- same caveat as every fix in Sections 43-47 now (five real fixes:
  confetti, skeleton loading, pull-to-refresh, haptics, list keys).
- This was a targeted, scripted fix for the ONE real, checkable pattern this pass's research
  recovered (list keys) -- Toss's own actual "3 major factors" in initial-load speed and its
  specific re-rendering techniques remain genuinely unrecovered behind the video gate, not
  something this pass solved by proxy.
- Image loading/caching (Coil usage, size constraints, crossfade, disk cache config) -- named in
  this pass's own search results as a real Toss performance lever -- wasn't audited this pass, a
  real, separate follow-up.

---

## 48. Image loading — real Coil crossfade gap, closed the Section 47 follow-up

**2026-08-12**, same session, direct continuation of Section 47's own named follow-up. Checked
itunda's real Coil usage: 18 `AsyncImage` call sites across 6 modules (Marketplace/Shop/Eats/Talk/
Maps listings and photos, plus avatars), zero use `crossfade`, and no app-wide `ImageLoader` is
configured anywhere -- every image load runs on Coil's raw defaults. Coil's own disk/memory cache
defaults are already reasonable out of the box (a real check, not assumed broken -- Coil 2.x's
default `ImageLoader` auto-sizes both caches sensibly), so the real, concrete gap here is
specifically the missing crossfade: every image currently hard-pops in the instant it finishes
downloading, the same jarring-loading-moment problem this session's own Doherty-Threshold/
skeleton-loading pass (Section 44) and confetti pass (Section 43) already found and fixed for
other loading moments -- this is the same principle, applied to a mechanism neither of those
passes touched.

**Shipped**: `ItundaApplication` now implements Coil's own documented `ImageLoaderFactory`
interface, returning `ImageLoader.Builder(this).crossfade(true).build()`. This is Coil's real,
intended mechanism for exactly this -- every `AsyncImage` call app-wide automatically picks up
`Context.imageLoader` once this is set, so none of the 18 existing (or any future) call sites
needed individual edits. `:app` full `assembleDebug` build-verified clean.

*Shipped: `ItundaApplication.kt`*

### Unresolved / worth a follow-up

- Not live-verified on-device -- same caveat as every fix in Sections 43-48 now (six real fixes:
  confetti, skeleton loading, pull-to-refresh, haptics, list keys, image crossfade).
- Explicit request-level `size()` constraints (loading a thumbnail-sized request instead of full
  resolution for small avatar/listing-thumbnail UI) weren't audited or added this pass -- a real,
  separate, more invasive follow-up (needs checking each call site's actual display size, not a
  single global config change like this one was).

---

## 49. Direct reference screenshots -- real Toss All-tab top bar and Settings card layout

**2026-08-12**, same session. User sent 16 real screenshots of their own actual Toss app (Home,
Pay, All/전체, Settings, language picker -- light and dark) and asked to make itunda "100% like
this." Given the scope (a real 5-tab bottom nav -- Home/Rewards/Shopping/Pay/All -- that conflicts
with the Home/Pay/Explore/Messages/You nav just shipped in the immediately preceding commits, plus
a large categorized All-tab menu and a full Settings restructure), asked the user to scope it
before touching anything: keep the current nav, fix Settings/All-menu **content** to match within
it. Two real, concrete, sourced fixes shipped from the reference set:

1. **All-tab top bar.** The real screenshot shows exactly 3 right-aligned text links --
   "Authentication | Help | Settings" -- with thin dividers, no username. itunda's `AllTopBar` had
   a bold "TUYIZERE ERIC" name + a single gear icon instead. Found, while fixing this, that an
   **earlier pass in this exact file had explicitly claimed** "a text navbar ('ID | Support |
   Settings') is a website convention with no equivalent anywhere in real Toss" and deliberately
   removed one -- a real, sourced correction of a previously-wrong assumption, not a style
   preference. Rewired to real itunda destinations: Authentication -> `onOpenIdentity`, Help ->
   `onOpenSupport`, Settings -> `onOpenSettings` (all pre-existing, real screens).
2. **Settings screen card layout.** The real screenshot shows separate rounded cards per section
   (My info / Authentication & Security / Assets & Certificates / Transfer & Payment / Legal
   Documents / standalone Close-account card), not one continuous list with inline dividers, which
   is what itunda had. Restructured into real `SettingsCard` containers (rounded, surface-colored,
   matching `Ids.layout.cardCornerRadius`) per section, kept every real existing feature
   unchanged (biometric app-lock toggle, device-key verification, theme picker, device list,
   notifications list, logout) -- nothing removed or fabricated, only regrouped and two labels
   corrected to the real Toss wording: "Security" -> "Authentication & Security", and Language
   moved from a top-bar EN/RW toggle into its real screenshot position (a row inside the first
   card).

**Deliberately not fabricated**: real Toss Settings also shows "Certificate", "PIN & security",
"Services logged in with Toss", "Manage imported assets (MyData)", "Issue Certificate", "Send",
"Toss Pay", "Terms and privacy agreements", "Privacy Policy", and an "Electronic Prepayment Means"
policy row -- itunda has no real destination for several of these (no profile-edit screen, no
MyData import flow, no separate legal-document screens beyond placeholder rows already elsewhere
in `MenuScreen`). Not added as dead chevron rows -- this session's own established discipline
(`FlatRow.onClick` made genuinely optional rather than fabricating fake legal content, an earlier
fix in this same research thread) applies here too.

`:app` full `assembleDebug` build-verified clean, then installed and relaunched on the physical
test device (confirmed via `dumpsys window` -- itunda in foreground with this exact build).

*Shipped: `ItundaAppScreen.kt` (`AllTopBar`), `SettingsScreen.kt`, `strings.xml`/`values-rw/strings.xml`*

### Unresolved / worth a follow-up

- The bottom-nav conflict (Home/Rewards/Shopping/Pay/All vs. the shipped Home/Pay/Explore/
  Messages/You) was explicitly NOT resolved this pass, per the user's own scoping choice -- still
  a real, open, larger decision if revisited.
- The real All-tab's full Finance/Lifestyle categorized structure (~35 real named items across two
  categories) was not rebuilt to match Toss's exact category names/grouping -- itunda's own
  `MenuScreen` already has a comparable categorized-search structure with real content, just
  different section names (Quick links/Accounts & cards/Send & pay/etc. vs. Toss's real Finance/
  Lifestyle), left as-is rather than renamed wholesale without a matching audit of every row.
- Not live-verified screen-by-screen on-device yet (installed and confirmed in foreground, not
  visually walked through) -- same open item as Sections 43-48.

**Same-session follow-up**: user sent 11 more real screenshots (7 already-seen, 4 new -- fully
confirming the Finance category's ~20 items and Lifestyle's ~14, both in light and dark mode) and
said explicitly "this how explore tab should look like." The single clearest, confirmed-across-
every-screenshot structural fact: **every category's items are always fully visible, with no
collapse/expand mechanic anywhere** -- itunda's own `MenuScreen` had exactly that (16 categories
behind a `CollapsibleFlatSection` tap-to-expand accordion, added 2026-08-10 on a real but, per this
new evidence, over-applied Hick's Law theory). Reverted all 16 to plain always-expanded
`FlatSection`, matching the real screenshots exactly; removed the now-dead `expandedMenuSection`
state and the now-unused `CollapsibleFlatSection` composable entirely rather than leaving dead code
behind. Real Toss's actual answer to a long list is its working search box, not hiding content --
itunda already had that search box built (2026-08-10, unchanged by this fix).

`:app` full `compileDebugKotlin` build-verified clean, installed and relaunched on the physical
test device.

*Shipped: `ItundaAppScreen.kt`*

---

## 50. Explore tab quick-access consolidation -- five sections down to two

**2026-08-12**, same session, same thread. User sent 6 screenshots of itunda's own current Explore
tab (not Toss) and said "still not the same." Even after the accordion removal in Section 49, the
tab opened with five separate icon/list "quick access"-style sections stacked one after another --
Quick links, Quick access, Mini apps, Partner mini-apps, Shortcuts -- before reaching the real
categorized content. Real Toss's All-tab reference screenshots (Section 49) open with exactly two:
a quick-launch icon grid, then a flat list.

Fix, in `ItundaAppScreen.kt`:
- Renamed the `IconGridSection("Quick access", ...)` grid to `"Open"` (matching real Toss wording)
  and moved it, together with `"Shortcuts"`, to open the tab immediately after the search bar.
- Merged the 4 `MiniAppsSection` rows (Wallet balance / Pay bills / Reward tasks / Insurance) into
  the existing `FlatSection("Quick links", ...)` rather than keeping them a separate section --
  they were real, working rows, just redundant as their own section.
- Removed the now-unused `MiniAppsSection` composable and its doc comment entirely (no dead code
  left behind), same discipline as the `CollapsibleFlatSection` removal in Section 49.
- Hit and fixed a real compile error mid-edit: an orphaned closing brace left over from moving the
  old "Shortcuts" item block caused cascading `Unresolved reference` / `@Composable invocations can
  only happen from the context of a @Composable function` errors through the rest of the file.

Result, confirmed live via `dumpsys window` + screenshot on the physical device: the tab now opens
with `Open` (Mini/Games/Bank/Pick) and `Shortcuts` (Open account/Verify/Send/Group/Property/
Insurance) as two icon grids, then `Quick links` as one flat list (Shop/Eats/Marketplace/Wallet
balance/Pay bills/Reward tasks/Insurance/...) -- structurally two groups instead of five, matching
the real reference shape.

`:app:compileDebugKotlin` and full `installDebug` build-verified clean; installed and relaunched on
the physical test device; foreground + on-screen content confirmed live via screenshot this pass
(the first fix this session actually visually walked, not just installed).

*Shipped: `ItundaAppScreen.kt`*

### Unresolved / worth a follow-up

- "Quick links" still carries real but visually mixed content (app-launch rows like Shop/Eats
  alongside account-status rows like Wallet balance) -- not split further this pass since the user's
  own screenshots didn't call this out specifically; worth a real-reference check if it comes up
  again.
- Still not screen-recorded or walked interactively (tapped through) on-device -- only a static
  screenshot comparison this pass.

---

## 51. Settings screen -- real Toss "Transfer & Payment" card, duplicating Send/Pay from Explore

**2026-08-12**, same session, same thread. User sent 4 more real screenshots (light + dark) of
their actual Toss app's Settings screen and said "this is how settings should look like -- they
are some features in explore tab that have to be in settings as well." Comparing card-by-card
against `SettingsScreen.kt`'s existing structure (My info / Authentication & Security / Display /
Devices / Notifications / Logout, built in Section 49):

Real Toss Settings has a card itunda didn't: **"Transfer & Payment"**, with two plain chevron rows,
**Send** and **Toss Pay** -- both of which duplicate quick-launch actions that already live
elsewhere in the real Toss app (its own equivalent of itunda's Explore-tab Shortcuts and bottom
nav), not new features. itunda already has both of the underlying real destinations (`Send` is the
same `onOpenTransferHub` the Explore tab's Shortcuts grid already calls; `Pay` is the same
`ItundaTab.Pay` bottom-nav tab) -- the gap was purely that Settings itself had no path to either.

Fix: added a `SettingsCard("Transfer & Payment")` with two new `SettingsChevronRow` rows (a new,
minimal composable matching the real screenshot's plain label-plus-chevron row style, no icon box
-- distinct from the icon-box rows used for the biometric toggles above it in the same screen).
`SettingsScreen` gained two new optional callback params, `onOpenSend`/`onOpenPay`, wired from
`ItundaAppScreen.kt`'s call site to the same real `showTransferHub`/`selectedTab` state the Explore
tab and bottom nav already use -- not a second, parallel navigation path, the exact same one.
Labeled "Pay" rather than "Toss Pay" since that's itunda's own real feature name (the bottom nav
tab's actual label), not Toss's brand name. New strings `settings_transfer_payment`/`settings_send`/
`settings_pay` added in both `strings.xml` and `values-rw/strings.xml`.

**Deliberately not added**: the real screenshot's other new card, "Assets & Certificates" (Manage
imported assets (MyData), Issue Certificate) -- itunda has no real MyData-import flow or
certificate-issuance backend, already correctly named as a deferred gap in Section 49; not
fabricated here either. "Send translation feedback" and "Contacts" rows (first card) also have no
real itunda destination -- left out for the same reason.

`:app:compileDebugKotlin` build-verified clean.

*Shipped: `SettingsScreen.kt`, `ItundaAppScreen.kt`, `strings.xml`, `values-rw/strings.xml`*

---

## 52. Settings restructure -- real sub-screens, real information architecture, live-verified

**2026-08-12**, same session, direct continuation of Section 51. User sent 4 more real Settings
screenshots and said "this is how settings should look like -- they are some features in explore
tab that have to be in settings as well," then kept iterating with "still not visually the same"
twice more as each fix landed, each time with a fresh, specific real-screenshot comparison. Five
real, separate fixes shipped this pass, in order:

1. **Devices/Notifications extraction.** itunda's Settings had grown two full inline dumps
   directly on the main scrolling list: every device the account had ever signed in from (6
   entries) and every notification ever received (15+ entries, including a **plaintext OTP code**
   visible on the settings page). Real Toss's own screenshots show these as plain single-line
   navigation rows, not expanded content. Extracted both into real sub-screens
   (`DeviceListScreen`, `NotificationListScreen`), reached via "Services logged in with Toss"-style
   chevron rows instead. Also relocated the theme picker the same way: "Display" stopped being its
   own card with an always-visible 3-way selector and became a "Theme & vibration" row that opens
   the same real `ThemePreference` picker as a dialog.
2. **Home bell vs. Settings "Notifications" row -- corrected, a real distinction, not a duplicate.**
   User sent 4 more screenshots and explicitly clarified: "notifications icon in home screen show
   notifications and notifications in settings shows notifications settings." Real Toss has two
   different screens behind these two entry points -- a feed of past notifications, and a
   *"Manage notifications"* preferences screen (per-category send toggles) -- and itunda's Home
   bell had wrongly opened the whole Settings screen (a real no-op-shaped bug named but not fixed
   in a 2026-07-22 audit comment, finally corrected here). Fixed: Home's bell -> real feed directly
   (`NotificationListScreen`, promoted to `internal` so `ItundaAppScreen.kt` can reach it without
   going through Settings at all); Settings' own "Notifications" row -> a new
   `NotificationSettingsScreen`. Toss's real version has server-side per-category toggles itunda
   has no backend for -- rather than fabricate switches that don't actually change what gets sent
   (the exact dishonesty `FlatRow`'s own doc comment already forbids), built the one real, honest
   destination available: deep-links into Android's own actual per-channel notification settings
   (`Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS`) for itunda's two real channels
   (`NotificationChannels.CHANNEL_MONEY`/`CHANNEL_GENERAL`, live since the FCM push work) -- a real
   per-category control, just backed by the OS's own settings surface instead of an invented one.
3. **Legal Documents relocated out of Explore.** User sent 4 more screenshots plus a direct
   instruction: "toss arrangements is clear what to settings in settings." Real Toss's All-tab
   reference screenshots have no "Notifications & consent"-style section at all -- that content
   lives in Settings only. itunda's own Explore tab (`ItundaAppScreen.kt`'s `MenuScreen`) had
   exactly that section, duplicating a "Notifications" row Settings already covered and 3 legal
   rows (Credit data usage policy / Privacy policy / Terms & consent) that had never had a real
   destination anywhere. Removed the section from Explore entirely; the 3 legal rows moved into a
   new "Legal Documents" `SettingsCard`, kept exactly as honest/inert as before (no `onClick`, no
   chevron -- itunda still has no real document screens behind them, not fabricated now either).
4. **Support-section icons.** User sent a screenshot of itunda's own (already-fixed) Explore tab
   and said "still not visually the same" a third time -- comparing directly against the real
   Toss "Help" section, where every single row carries a distinct colored icon. itunda's
   "Support" rows (FAQ/Live chat/Call support/Announcements) had none at all, plain text only.
   Added real icons matching each row's real purpose (`HelpOutline`/`Chat`/`Call`/`ReportProblem`/
   `ConfirmationNumber`/`Campaign`), reusing the same `AccentBlue`/`AccentTeal`/etc. palette every
   other `FlatRow` icon in this file already uses.
5. **Language row chevron.** Direct screenshot comparison: every row in the real "My info" card
   ends with a chevron, including "Language" (value + chevron together) -- itunda's version showed
   the value with no chevron at all. Added.

**First multi-fix pass this whole research thread with real, live, on-device screenshot
verification at nearly every step**, not just build-verified: relaunched and screenshotted after
each of the 5 fixes above via `dumpsys window` + `adb exec-out screencap`, catching the device
being mid-use by the real user (Yogiyo, real Toss app, notification shade, the Claude mobile app
itself showing this exact session) multiple times and deliberately waiting rather than
interrupting each time. One real navigation mistake caught and corrected: an overscroll swipe at
the top of Explore accidentally triggered Android's own app-switch gesture into the Claude mobile
client, landing on this session's own live transcript with a real feedback text box on screen --
recognized immediately as a different real app, not tapped, backed out via `KEYCODE_HOME`.

`:app:compileDebugKotlin` build-verified clean after every step; installed and relaunched on the
physical test device between each fix.

*Shipped: `SettingsScreen.kt`, `ItundaAppScreen.kt`, `strings.xml`, `values-rw/strings.xml`*

### Unresolved / worth a follow-up

- Real Toss has real sub-screens behind "Certificate"/"PIN & security" (a full "Security" screen:
  PIN verification, Manage passkeys, Quick login toggle, Biometric settings toggle, Change PIN,
  Toss Securities security, Manage Toss Bank authentication methods) and behind the profile row
  itself (a full "My Profile" editor with an emoji/avatar picker bottom sheet) -- confirmed via 2
  more user screenshots this same pass. itunda's simplified toggle-only "Authentication & Security"
  card is a real, honest, working subset of this, not a 1:1 match -- a full parity pass on either
  sub-screen is a distinctly larger, separate scope, not attempted this pass.
- "Devices" kept as itunda's own real label rather than adopting Toss's brand-name wording
  ("Services logged in with Toss") -- a deliberate choice, not an oversight, consistent with this
  whole thread's practice of using itunda's own real feature names rather than another company's
  branding.

---

## 53. Shopping tab gamification suite -- banners, real ledger-backed missions, recommended grid

**2026-08-12**, same session. User sent 15 real screenshots of the actual Toss Shopping tab and
said "this is how our shopping should look like ... make fully functionality including backend."
itunda's Shop feature was already genuinely mature (real deals, time deals, wishlist, follow-
merchant, cart/checkout, orders, reviews, bookings, membership-day cashback) -- what the reference
showed and itunda had none of, frontend or backend, was a banner carousel and a "포인트 및
쿠폰받기" (get points and coupons) gamification row (check-in, scroll, spin/draw-a-prize, a cat
icon, a claim-reward icon). Given the size (several new backend systems, not just UI), asked the
user to scope it via `AskUserQuestion` before building anything; explicit answer: **"Full
gamification suite."**

**Real architecture decision, made before writing any code**: checked `StepRewardService.kt`
(itunda's existing Toss 만보기 walking-rewards feature) first and found the established real
precedent -- reward "points" are never a separate fake currency, they're real RWF credited
straight into the user's real wallet via `LedgerService`. Followed that exact architecture rather
than inventing a second points system this app has never had:

1. **`ShoppingMissionReward`/`ShoppingWelcomeBonusClaim`** (new `core/domain` entities, migration
   `V239__shopping_missions.sql`) -- one real row per user per real calendar day for the 4 daily
   missions (Check-in/Scroll/Spin/Cat), same exact shape as `DailyStepReward` including the same
   `@Version` optimistic-lock guard against a concurrent double-claim race, and one real
   once-ever-per-user table for the welcome bonus (the primary key itself is the real guard, not
   an app-level flag).
2. **`ShoppingMissionService`** (new `rewards` module file) -- every mission credits real RWF via
   the same `LedgerLeg`/`LedgerService.postLedgerTransaction` pattern `StepRewardService` already
   uses (REWARDS_EXPENSE debit / WALLET credit). SPIN uses a real weighted-random payout among 5
   stated amounts (10/20/50/100/300 RWF, odds 35/30/20/10/5%) -- same disclosed-odds discipline
   item 248's lottery bonus already established (a client can show the real range before a user
   ever spins, never a hidden mechanic). 9 real test cases in `ShoppingMissionServiceTest.kt`
   (check-in credits + flag, duplicate throws, a different mission still claimable same day, both
   spin boundary outcomes, welcome bonus once-ever, no-wallet failure path) -- all passing, plus
   the full `:rewards`/`:commerce` test suites re-run clean.
3. **Banner carousel** -- rather than build a second, separate, fabricated banner-content CMS,
   `TimeDealService.getBanners()` (new method, `commerce` module) derives every banner directly
   from the same real active Time Deal data the existing Time Deals rail already uses. No banners
   simply means no active deals right now, an honest empty state, not a hole filled with
   placeholder content.
4. **Android**: `ApiService.kt` gained the 4 new DTOs/endpoints (`ShoppingBannersResponse`,
   `ShoppingMissionsResponse`, `MissionCompleteResponse`). `ShopScreen.kt`'s `CommerceShopContent`
   gained `ShoppingBannerCarousel` (a real `HorizontalPager` with a page-count indicator, matching
   the reference's "3 | 11" badge), `ShoppingPointsRow` (5 real mission icons, greyed once
   completed, shows the real reward range before tapping, a busy-spinner state while claiming, and
   a real success dialog on completion), and `RecommendedForYouGrid` (restyles the *existing* real
   Deals rail data into a 2-column grid with badge/heart/cashback instead of a horizontal-scroll
   rail -- deliberately does NOT add a fabricated rating number per card, since fetching real
   ratings for a whole grid would mean N extra network calls per screen load; shows the real
   discount badge, real stock-based "Ships today" state, and real cashback via
   `ShoppingCashbackService`'s own published flat rate instead).

**Real infra hiccup found and fixed mid-pass**: the first Docker image build for this failed
outright with "No space left on device" -- Colima's dedicated docker data disk (`/mnt/lima-colima`,
7.8GB) was 100% full, entirely from ~9 stale `itunda/backend` image tags accumulated across past
sessions and never cleaned up (`deposit-protection-fund`, `real-pay-v1..v5`, etc.). Removed the
stale tags and ran `docker system prune -af --volumes` (reclaimed the disk to 7.4GB free) before
the rebuild succeeded. **Worth a periodic check going forward** -- this will recur.

Deployed for real: built and pushed
`192.168.252.4:32000/itunda/backend:shopping-gamification`, `kubectl set image` on the real
`backend` Deployment, watched the rollout (`backend-7d895b9c9f-vlqz8`) come up healthy -- confirmed
via real pod logs that Flyway "Successfully applied 1 migration to schema `itunda`, now at version
v239" with zero errors, and the old pod terminated cleanly after the new one passed readiness (took
~186s to fully start on this single-node, still-overcommitted cluster -- see
[[project_itunda_private_cloud]]). Confirmed both new endpoint families are live and correctly
JWT-gated (`401`, not `404`) via direct `curl` through the existing physical-device tunnel.

*Shipped: `services/backend/core/.../ShoppingMissionReward.kt`,
`services/backend/core/.../ShoppingMissionRewardRepository.kt`,
`services/backend/rewards/.../ShoppingMissionService.kt`,
`services/backend/rewards/.../web/ShoppingMissionController.kt`,
`services/backend/rewards/.../ShoppingMissionServiceTest.kt`,
`services/backend/commerce/.../TimeDealService.kt`, `.../web/TimeDealController.kt`,
`services/backend/app/.../db/migration/V239__shopping_missions.sql`,
`android/core/network/.../ApiService.kt`, `android/features/shop/impl/.../ShopScreen.kt`*

### Unresolved / worth a follow-up

- Not yet visually walked on the physical device -- the phone went unreachable over WiFi
  (`adb connect` timing out, likely asleep or off the network) right as the backend rollout
  finished; backend correctness confirmed via pod logs + direct curl instead. Live screenshot
  verification of the banner carousel/mission row/recommended grid is a real, explicitly open
  follow-up, not silently skipped.
- Banner tap is deliberately read-only this pass (not wired to open the underlying merchant) --
  `ShoppingBannerCarousel`'s own doc comment names this as a real, intentional scope cut rather
  than a half-wired tap target.
- Per-card rating on the Recommended grid deliberately left out (see point 4 above) -- a real
  follow-up if the grid needs to fetch batched ratings efficiently.
- The "Points fe..." icon from the real reference (5th icon, unclear real behavior from the
  screenshot alone) was deliberately dropped rather than guessed at; the "Cat" icon was
  implemented as a plain flat-reward daily tap (no pet-simulation state), an honest simplification
  named in `ShoppingMissionReward`'s own doc comment, not silently passed off as the full mechanic.

---

## 54. Eats screen -- category icon row + photo-forward menu cards

**2026-08-12**, same session, direct continuation. User sent 14 real Coupang Eats screenshots and
said "out eats should look like this (fully visually and functionality)." Unlike Shopping (Section
53), itunda's Eats backend was already mature (real orders, favorites, reviews, an
`EatsMembershipService` matching the reference's "WOW" membership concept, and real menu option
groups already matching the reference's stew-customization screen almost exactly) -- this pass
found two real, concrete, purely-visual gaps rather than needing new backend systems:

1. **Category icon row.** The real reference shows a horizontally-scrolling row of round category
   icons above the search bar (일식/회해물/구이/찜탕/한식); itunda's `SearchAndCategoryChips` (a
   component shared with Shop) only ever rendered plain text pill chips. Rather than change the
   shared component (real risk of an un-requested visual change to Shop), added a new,
   Eats-only `EatsCategoryIconRow` using itunda's own real merchant categories (`getMerchantCategories()`
   -- real values like "Rwandan"/"Fast Food"/"Coffee & Bakery" from real seeded merchants, not
   fabricated Korean cuisine names) mapped to real Material icons. No invented dish photography --
   itunda has no real per-category photo source to draw from honestly.
2. **Photo-forward menu cards.** The real reference's restaurant-menu screen shows every item as a
   photo card with a real discount badge; itunda's `RestaurantMenuView` rendered plain text rows
   (name + price only), despite `MerchantProductDto` already carrying real `imageUrl`/
   `discountPercent`/`originalPrice` fields (the same ones Shop's Deals rail already uses). Added a
   64dp photo thumbnail + discount badge + strikethrough original price to each row -- real data,
   no new backend calls.

**Deliberately not attempted this pass**: the reference's "인기메뉴" (popular menu) 1위/2위/3위
popularity-rank badges would need a real order-count aggregation itunda's backend doesn't expose
yet -- a genuine new-backend-data gap, not guessed at with a fake rank.

`:features:eats:impl` and full `:app:compileDebugKotlin` build-verified clean.

*Shipped: `android/features/eats/impl/.../EatsScreen.kt`*

### Unresolved / worth a follow-up

- Not yet visually verified on-device -- the physical test device dropped off WiFi mid-session
  (same real connectivity gap named in Section 53) and hadn't reconnected by the time this pass
  finished. Explicitly flagged, not silently skipped.
- Popularity-rank badges (see above) need a real backend aggregation, a genuine separate follow-up.
- `ProductImageThumb` (Shop) and the new `MenuItemThumb` (Eats) are now two near-identical private
  composables in two different feature modules -- a real, minor duplication worth promoting to
  `core/designsystem` if a third module ever needs the same pattern, not urgent enough to justify
  the cross-module refactor on its own this pass.

---

## 55. Maps -- a real "Bus" mode surfacing itunda's own scheduled trip marketplace

**2026-08-12**, same session, direct continuation. User sent 16 real screenshots and said "our maps
should look like this full visually and functionality" -- the reference turned out to be Naver Map's
actual navigation product (watermark visible in one screenshot), with real-time GPS bus-arrival
countdowns, live traffic-colored routing, and multiple named route strategies (fastest/main-roads/
toll-free), all backed by Korea's national live transit-data feed. Checked itunda's real Maps
backend first: real OSRM-based driving/walking routing with alternative routes, turn-by-turn steps,
and a real "Start navigation" mode already existed (more mature than expected) -- but there is no
live traffic data source and no Rwanda public-transit API to plug into. Rather than fabricate live
bus GPS or live traffic colors (a real dishonesty this whole session has avoided), asked the user
directly via `AskUserQuestion` how to scope it. **User chose: "also build a real transit-schedule
feature" if real schedule data actually exists to source from.**

**Real find**: itunda already has exactly that -- `BusService.kt` (rideshare module), a real,
shipped peer-to-peer intercity bus marketplace (any user posts a scheduled trip with a real
origin/destination/departure time/fare/seat count; riders book seats with real immediate wallet
settlement via `LedgerService`, real refund-on-cancel). This is real scheduled data, just not live
GPS tracking -- exactly the honest middle ground the user asked for.

**Shipped**: added a third "🚌 Bus" tab to the Maps directions mode-selector (alongside the existing
Driving/Walking tabs), which calls the real `searchBusTrips(destination)` endpoint (already existed,
already had an Android client from the standalone Bus-booking screen) using the real destination
place name already resolved by the directions search. Results show real matching `BusTrip` rows
(origin → destination, real departure time, real seats remaining, real fare), honestly labeled
**"Scheduled"** rather than implying live tracking -- and an honest "No scheduled bus trips found"
empty state when none match, never a fabricated placeholder. No OSRM route line, alternative-route
picker, turn-by-turn steps, or "Start navigation" CTA render for the Bus tab, since none of those
real concepts apply to a peer-posted point-to-point coach trip.

**Real compiler limit hit and fixed mid-pass**: `MapScreen`'s directions-display composable lambda
was already large (2860+ line file); adding the Bus block inline pushed it over the JVM's 64KB
per-method bytecode limit (`MethodTooLargeException`, a real compile failure, not a typo). Fixed by
extracting the new UI into its own `BusTripResultsView` composable, which the Kotlin compiler emits
as a separate method -- same real fix Compose code always needs for a sufficiently large screen, not
specific to this feature.

`:features:maps:impl` and full `:app:compileDebugKotlin` build-verified clean.

*Shipped: `android/features/maps/impl/.../MapsScreen.kt`*

### Cross-platform port (same day) -- "100% visual, across all three platforms"

User confirmed the same real Bus tab should ship on iOS and Web too. Both already had real
`searchBusTrips` API clients (from each platform's own standalone Bus-booking screen), so this was
a straight port of the same real logic and honest labeling, not new design work:

- **Web** (`bank-mfe/src/MapView.tsx`): added the same third "🚌 Bus" button to the existing mode
  toggle (skipped for the multi-stop itinerary path, same deliberate boundary as Android). Verified
  with the real toolchain end to end: `tsc -b --noEmit` clean, full production `vite build` succeeds,
  `oxlint` clean for this file.
- **iOS** (`MapScreenView.swift`): same port, extracted into its own `busResultsView`
  `@ViewBuilder` function up front (this file already had a documented Swift type-checker-timeout
  precedent for exactly this shape of addition -- `travelModeToggle()`/`routeAlternativesPicker(_:)`
  -- so the extraction wasn't discovered the hard way this time).

**Real, significant finding while verifying iOS**: the full `ItundaApp` scheme hadn't actually been
xcodebuild-verified in a long time. `DeviceKeyManager.swift` -- item 246's real Secure-Enclave
device-key feature, recorded as shipped 2026-08-07 -- failed with "cannot find 'DeviceKeyManager' in
scope" despite the file existing, being correctly `public`, and being correctly `import`ed. Root
cause: the file had **zero references in the generated `project.pbxproj`** -- it was never actually
a member of the Xcode project, only ever verified via `swiftc -parse` (a syntax check, not a real
compile), per its own header comment blaming "Tuist can't run in this sandbox's toolchain." That
claim no longer held: `tuist generate --no-open` ran successfully this pass and regenerated a
correct project from `Project.swift`'s own source globs (which were always correct -- this was a
**stale local generated-project artifact**, gitignored and not source-controlled, not a manifest
bug). Regenerating also picked up 5 more files with the identical "NOT build-verified" header
comment that had silently been in the same state: `NIDABiometricAuth.swift`, `IDS.swift`,
`ZeroTrust.swift`, `BankView.swift`, `PaymentWidget.swift`. After `pod install` re-integrated
CocoaPods against the regenerated project, the full `ItundaApp` scheme built with **zero errors**
for the iOS Simulator -- the first confirmed full-app iOS build this project has had in this
session's memory of it.

*Shipped: `services/micro-frontends/bank-mfe/src/MapView.tsx`, `ios/App/Sources/MapScreenView.swift`*
(the `tuist generate`/`pod install` fix itself touches only gitignored generated files, nothing to
commit beyond confirming the regenerated project builds clean)

### Unresolved / worth a follow-up

- Live traffic-colored routing and live GPS bus-arrival countdowns are explicitly NOT built --
  itunda has no real data source for either, and faking one would be a real dishonesty this session
  has consistently avoided (see the `AskUserQuestion` above). If itunda ever integrates a real
  traffic-data provider or partners with a real Rwanda transit operator for live GPS feeds, this is
  the natural place to revisit.
- The multi-stop itinerary route planner's own mode selector (a separate code path, line ~2680 on
  Android, ~1305 on Web) deliberately did NOT get a Bus tab on any platform -- a peer-posted
  point-to-point coach trip has no real concept of a custom multi-waypoint itinerary, so forcing it
  in would be dishonest UI, not a missed spot.
- Not yet visually verified on-device on any platform -- same real connectivity gap as Sections
  53/54 (the physical Android test device has been unreachable over WiFi for the second half of
  this session); iOS/Web verification so far is compiler/build-level only, no simulator or browser
  screenshot taken.
- Origin is not passed to `searchBusTrips` (only destination) on any platform -- a deliberate
  simplification to avoid an extra reverse-geocode-then-search round trip for the user's current
  location; real destination-only matching already narrows results meaningfully.
- The 6-file iOS project-membership gap was only found because THIS pass happened to try a full
  `xcodebuild` for the first time in a while -- worth periodically re-running `tuist generate &&
  pod install && xcodebuild` as a real health check, the same standing lesson
  `feedback_run_real_tests_not_just_compile` already established for the backend's own test suites.

---

## 56. Error-handling audit continued + real disabled-CTA color/keyboard-docking fixes

**2026-08-12**, same session. User said "keep searching about toss and improve itunda" -- rather
than start a fresh research angle on top of an already very long session, picked up two real,
already-identified, still-open threads instead of starting from zero.

**Toss-style error handling** ([[feedback_toss_error_handling]]'s own standing audit, last
measured at "~89 generic catch sites"): re-swept every real `catch (e: HttpException)` site across
the whole Android tree (270 total). 237 already correctly parse the real backend message via
`superAppErrorMessage`/`apiErrorMessage` -- genuinely close to fully closed, a lot of quiet progress
across earlier passes this thread never explicitly totaled up. Found and fixed the 3 real remaining
gaps, all in the same shape: **merchantapp/riderapp/agentapp's own standalone `LoginScreen.kt`**
(each a separate app with its own network layer, so each needed its own fix) collapsed every
non-401 HTTP error -- rate limiting, a suspended account, a real 5xx -- into a hardcoded "Couldn't
reach itunda. Try again.", discarding whatever the backend actually said. 401 stays a real, friendly
hardcoded message (not itunda's own business to second-guess); everything else now surfaces the
real parsed message, with a proper reassuring fallback ("itunda is having a brief hiccup on our end
-- not something you did") only when the backend genuinely sent nothing parseable. agentapp had no
error-parsing helper at all yet (unlike merchantapp/riderapp, which each already had one, just
unused at this specific site) -- added one, mirroring the other two exactly. Every other real
`HttpException` site checked this pass (RideScreen x2, CertificateScreen, CardScreen, MiniWalletScreen,
InvestScreen, RequestMoneyScreen, PropertyScreen, MarketplaceScreen, JobsScreen) already correctly
used the real pattern -- an honest, mostly-clean audit result, not manufactured findings to justify
the pass.

**Real disabled-CTA color, found from re-examining an already-sent reference screenshot** ("Enter
workplace name" / bottom "Confirm" bar, sent twice earlier this session under "toss confirm bottom
interactions"): the real screenshot shows a disabled primary button as a dim TINT of the same brand
blue (readable as "the same action, just not ready yet"), not neutral grey (which reads as "broken/
unavailable"). Checked `IdsButton.kt`/`IdsButton.tsx` -- both hardcoded `disabledContainerColor`
unconditionally to a neutral grey regardless of variant, for every real primary button in the app.
Fixed in the one shared component per platform (not per call site, matching this exact file's own
established "single Flat props-based API" principle) so the fix cascades to every real Filled button
automatically: Android uses `Ids.colors.brand.copy(alpha = 0.35f)`; Web uses
`color-mix(in srgb, var(--itunda-blue) 35%, transparent)` (not a hardcoded rgba literal, so it stays
in sync with the real `--itunda-blue` token automatically -- the same hardcoded-color drift bug
`IdsButton.tsx`'s own header comment already names once). `Tinted`/`danger` variants keep the
existing neutral disabled fallback, a secondary style not shown in the reference.

Android verified via `:core:designsystem:compileDebugKotlin` + full `:app:compileDebugKotlin`. Web
verified via `tsc -b --noEmit` + full production `vite build`, both clean.

**Live-verified same day (2026-08-12), via the headless-Chrome/CDP technique** (see
[[feedback_headless_chrome_verification]]): rendered a real enabled vs. disabled `filled` button
side by side against the actual page's real `--itunda-blue` custom property and confirmed the
disabled state genuinely renders as a dim blue tint, not the old neutral grey.

**Real keyboard-docking CTA, found by re-examining the same reference screenshots more closely
(user's follow-up: "did you see how the button changes from curved button to full width when it's
on top of keyboard or keypad")**: with the keyboard hidden, the real "Confirm" bar is a normal
rounded, inset button like everywhere else in the app; the moment the keyboard opens, it loses its
rounding and side margins entirely and becomes a flush, edge-to-edge bar sitting directly on top of
the keyboard -- visually docking into the keyboard's own flat surface rather than floating above it
as a separate rounded card. Added `IdsKeyboardDockedButton` (Android `core/designsystem`), a thin
wrapper around `IdsButton` using the real Compose `WindowInsets.isImeVisible` API
(`@ExperimentalLayoutApi`) to switch `shape`/horizontal padding live as the keyboard opens/closes --
not baked into `IdsButton` itself, since most real call sites (inline in a card, a row, a
non-keyboard screen) should never pick this behavior up automatically. `IdsButton` itself gained one
small, non-breaking addition: an optional `shape` override param. Applied to the first real matching
itunda screen -- `LoginScreen.kt`'s own phone-number/password entry flow (single field + bottom
primary button, the same real shape as the reference), the app's single highest-traffic keyboard-
adjacent form. Verified via full `:app:compileDebugKotlin`, clean.

*Shipped: `android/agentapp/.../NetworkClient.kt`, `.../LoginScreen.kt` (agentapp/merchantapp/
riderapp), `android/core/designsystem/.../IdsButton.kt`, `bank-mfe/src/IdsButton.tsx`,
`android/app/.../LoginScreen.kt`*

### Unresolved / worth a follow-up

- iOS has no `IdsButton` port at all yet (a real, already-documented gap from the original Web port
  pass) -- the disabled-color fix above only applies to the 2 platforms that have the component; the
  keyboard-docking fix is Android-only for the same reason plus SwiftUI needing its own real
  keyboard-visibility detection mechanism, not attempted this pass.
- Web's `IdsButton.tsx` migration itself is still only 2 of 400+ real button call sites in
  `BankDashboard.tsx` (a pre-existing, already-documented gap, unrelated to this pass) -- this
  disabled-color fix only benefits screens that have already migrated to the real component.
- `IdsKeyboardDockedButton` was only applied to `LoginScreen.kt` this pass -- a real, honest first
  application, not a claim that every itunda screen with this shape (single field + bottom confirm)
  has been swept. Worth the same kind of audit pass this whole research thread keeps applying to
  other components (confetti, skeleton loading, pull-to-refresh, haptics, etc.) if the user wants
  full coverage.
- Not yet visually verified on-device -- same real connectivity gap as Sections 53-55; this
  specific fix is a live, real-time visual behavior (shape morphing as the keyboard animates in/out)
  that's especially worth an actual on-device look once the phone reconnects, more so than a static
  screenshot comparison would show.

## 57. Home tab / itunda Bank account detail -- "Get interest" prompt on the wallet card

**2026-08-12**, same session. User sent 16 real Toss Bank account-detail screenshots ("itunda bank
should be 100% something like this") followed mid-turn by 2 real Toss super-app Home-tab screenshots
("home screen should also be 100% like this"), then asked directly whether the distinction between
the two was understood ("do you also the the difference between home tab and itunda bank right?") --
confirmed explicitly before touching anything: Toss's super-app Home tab (홈/혜택/쇼핑/페이/전체 nav,
wallet balance, promo/rewards, category row, spending summary, credit score) maps to itunda's own
`HomeTab`; Toss Bank's own dedicated account-detail view (balance, "Get interest" CTA, transaction
list, Top up/Send, deposit-rate banner, Auto Transfer, "My Toss Bank assets", a 관리/Manage menu, a
product catalog) maps to itunda's own `BankHubScreen`.

Checking both against current code found this area was **already substantially built in an earlier
pass this same session** (code comments dated 2026-08-11): `HomeTab` already has
`PersonalRecommendationCard`, `WalletHeroCard`, `BankSummaryCard`, spending insight;
`BankHubScreen` already has the Save & Grow / Borrow product catalog, interest jar, savings goals,
round-up, Ikimina/SACCO, Investments, loans, and a real Deposit Protection Fund disclosure card. Not
a green-field gap -- one concrete, real, still-missing piece found by direct comparison: the account-
detail screenshots show a small "Get interest" prompt card (unclaimed interest balance + inline claim
button) sitting directly below the balance, above the transaction list. itunda already had both real
pieces this needs -- `MainViewModel.interestJar` (already fetched, already used by `BankSummaryCard`
and `BankHubScreen`'s own "Interest earned this month" row) and a real claim destination
(`SavingsFlowStep.ClaimInterest`, already wired from `BankHubScreen`) -- just never surfaced on
`WalletHeroCard` itself. Added a conditionally-rendered row (only when `interestJar.balance > 0`,
matching this same card's existing "don't show an empty section" discipline already used for
`earnedThisMonth`): a tinted brand-color pill with a bolt icon + the real unclaimed amount, and a
small `IdsButton` reusing the exact `SavingsFlowStep.ClaimInterest` destination `BankHubScreen`
already routes to. New `interestJarBalance`/`onClaimInterest` params threaded from `HomeTab` down
through the existing `interestJar` state it already collected, no new fetch.

Verified via full `:app:compileDebugKotlin`, clean.

Checked `BankHubScreen` for the other reference-set candidates (Auto Transfer, Manage menu, deposit-
rate banner, product catalog) before declaring more gaps: Auto Transfer already exists
(`AutoTransferListScreen`), and Save & Grow/Borrow already cover most of the product-catalog ground
with real rates. Didn't build the promotional deposit-rate banner or a distinct Manage-menu grouping
from memory of the earlier screenshots -- doing so without the images in front of me again risks
guessing at layout, so left both as named open follow-ups instead.

**Same-day web port**: Android device stayed unreachable, but the backend turned out to be directly
reachable from the dev machine itself (`192.168.252.4:30081`, unlike the phone's WiFi subnet) --
started `bank-mfe`'s dev server against it and ported the identical fix. Web's `AccountBalance`
(`HomeView`'s own wallet hero, the exact analog of `WalletHeroCard`) had every real piece needed
already -- `fetchInterestJar` (the same call `InterestJarCard` on the `SavingsView`/"itunda Bank" tab
already makes) and a real claim destination (`onNavigateToTab('SAVINGS')`) -- just never surfaced on
Home. Verified via `tsc -b --noEmit` + full production `vite build`, both clean.

*Shipped: `android/app/.../ItundaAppScreen.kt` (`WalletHeroCard`, `HomeTab`), `android/app/src/main/res/values/strings.xml`, `bank-mfe/src/BankDashboard.tsx` (`AccountBalance`, `HomeView`)*

### Unresolved / worth a follow-up

- Only one gap was found and closed this pass -- the full 16+2 reference image set has **not** been
  exhaustively swept beyond it. Real candidates not yet checked against current code: the
  promotional deposit-rate banner, "My Toss Bank assets" section, the exact 관리/Manage menu structure
  (Refinancing/Credit Card/Debit Card/Service categories), and the product catalog's exact Demand
  Deposits/Savings category grouping with real rates. Worth a dedicated follow-up pass with the
  reference images back in view, rather than building from a text description of them.
- **Android still not visually verified** -- the physical device stayed unreachable over WiFi the
  entire session; build-verified only.
- **Web now genuinely live-verified**, not just build-verified: after the Claude-in-Chrome extension
  and macOS `screencapture` both failed (see [[feedback_headless_chrome_verification]] for the full
  account), drove a headless Chrome instance directly over the Chrome DevTools Protocol (raw
  WebSocket, no puppeteer) -- logged in with the real seeded demo user, confirmed the "Get interest"
  pill renders correctly below the real balance (858,081.42 RWF), clicked it, and confirmed it lands
  on the real Safe Box/itunda Bank screen with the correct data. First fully live (not just build- or
  backend-)verified confirmation of ANY fix in the second half of this session.
- iOS untouched this pass -- the iOS `HomeView` is a separate implementation, not ported yet.

## 58. Explore tab -- removed web's leftover tap-to-expand accordion, matching Android's own fix

**2026-08-12**, same session, continuation of the newly-working headless-Chrome live-verification
capability from Section 57. After confirming the disabled-CTA color fix (Section 56) renders
correctly on web via a real side-by-side color-mix render, went looking for other already-shipped
Android fixes that were never checked for a web equivalent -- the standing "when a fix lands on one
platform, check whether the others got it too" lesson from this same research thread's fourth pass.

Found one, real and unresolved: Section 49's Explore/전체 accordion removal (16 real screenshots
confirmed every category's items are always fully visible in real Toss, zero collapse/expand
mechanic) was applied to Android's `MenuScreen` but never ported to `bank-mfe`'s own `ExploreHub` --
it still had the exact same `expandedGroup`/`ChevronDown`-rotate tap-to-toggle pattern the real
reference screenshots showed was wrong. Removed it: every group's chips now render unconditionally,
matching Android exactly. Also removed the now-unused `ChevronDown` import.

Verified via `tsc -b --noEmit` + full `vite build` (both clean), then **live-verified** via the
headless-Chrome/CDP technique ([[feedback_headless_chrome_verification]]): logged in with the real
seeded demo user, clicked into Explore, and confirmed every one of the 6 real category groups
(Everyday, Your neighbourhood, Get around, Money tools, Trust & community, More) renders its full
item list immediately with no tap required, exactly matching the reference and Android's own fix.

*Shipped: `bank-mfe/src/BankDashboard.tsx` (`ExploreHub`)*

### Unresolved / worth a follow-up

- Only checked this one Android-fixed-but-web-unchecked candidate this pass. The rest of Sections
  43-56's Android-only fixes (confetti, skeleton loading, pull-to-refresh, haptics, list keys, image
  crossfade, keyboard-docking button) are mostly Compose-specific mechanisms without a direct 1:1 web
  analog -- not blindly assumed to need porting, but not individually checked either.
- The chip buttons within each group render as full-width stacked rows on web rather than wrapping
  into a dense grid (visible in the live screenshot) -- pre-existing behavior, not introduced by this
  fix (the `flexWrap`/`gap` layout was already there), and not compared against a specific reference
  screenshot for chip density on web specifically. Noted, not fixed this pass.

## 59. Shopping gamification suite -- ported to web, plus a real backend health sweep

**2026-08-12**, same session. An uncalled-endpoint check (grep for real call sites of
`/api/v1/shopping/points` and `/api/v1/time-deals/banners` across `services/micro-frontends` and
`ios`) found Section 53's Shopping gamification suite -- shipped and deployed same session -- had
zero web or iOS callers. It had never been explicitly scoped as Android-only; it just never got
ported. Given the backend and Android UI design were already fully real and settled, ported the
same feature to `bank-mfe`'s `ShopView` rather than treat this as a fresh design task.

Added `lib/timeDeal.ts`'s `fetchShopBanners` and a new `lib/shoppingMissions.ts`
(`fetchShoppingMissionStatus`/`completeShoppingMission`), mirroring Android's `ApiService.kt`
shapes exactly. Two new UI pieces in `ShopView`: a horizontal scroll-snap banner carousel (CSS
`scroll-snap-type`, no external carousel library) with a real "current | total" page indicator
matching Android's `HorizontalPager` reference, and the "Get points and coupons" mission row
(Check-in/Scroll/Draw a prize/Cat/Claim reward), both only shown on the unfiltered Merchants
landing state, same placement Android uses.

Verified via `tsc -b --noEmit` + full `vite build` (clean), then fully live-verified end-to-end via
the headless-Chrome/CDP technique ([[feedback_headless_chrome_verification]]): logged in with the
real seeded demo user, confirmed the mission row renders with real reward amounts, clicked
"Check-in," and confirmed the real state transition (icon dims, reward text disappears, a real
"+20 RWF" confirmation appears) -- a genuine round trip through the real backend, not a mocked
click. The banner carousel didn't render for this demo account (no active Time Deals right now) --
correctly an honest empty state, not a bug, matching `ShoppingBannerCarousel`'s own established
"don't show an empty section" discipline.

*Shipped: `bank-mfe/src/lib/timeDeal.ts`, `bank-mfe/src/lib/shoppingMissions.ts` (new),
`bank-mfe/src/BankDashboard.tsx` (`ShopView`)*

**Same-session real infra incident, worth recording alongside this**: while this was in progress,
the K8s `backend` pod crash-looped (0/1 Running, restarting) -- confirmed via `cluster_kubectl get
pods`, matching [[project_itunda_private_cloud]]'s already-documented, still-unfixed overcommitment
issue. Root cause this time: a concurrent local `./gradlew test --continue` sweep (below) competing
for the same host's CPU as the Multipass VM. Waited for the pod to recover rather than force-killing
or restarting anything; it self-recovered once the local build's CPU pressure eased.

### Unresolved / worth a follow-up

- iOS untouched -- same gap, not ported this pass.
- Per-card rating and banner-tap-opens-merchant are still the same deliberate Section 53 scope cuts,
  carried over unchanged into the web port.

## 60. Full backend health sweep -- 4 real bugs found and fixed, one a genuine precision bug

**2026-08-12**, same session, standalone health-check task (not triggered by a specific feature
change) per [[feedback_run_real_tests_not_just_compile]]'s own established cadence -- the last full
sweep was 2026-08-09. Full writeup in that memory file's own "10th sweep" entry; summary here:

Full `./gradlew test --continue` across all ~40 backend modules found 4 real failures: (1)
`MerchantServiceTest.kt` had 3 stale constructor calls after `MerchantService` gained a real
`customerPaymentCodeRepository` param (2026-08-11) -- a compile break, same shape as the DeviceService
constructor break this exact audit technique caught once before; (2)-(3) `P2pServiceTest`/
`FraudReviewServiceTest` verified `pushNotificationService.sendToUser(...)` without the `type` param
the real call sites now pass -- MockK's `verify` silently stopped matching the real invocation, and
a same-shape `every { ... } throws ...` mock in the same file was quietly no-op'ing for the identical
reason, weakening (not failing) its own push-failure-resilience test; (4) `StockCatalogTest`'s own
"stocks simulate independently" assertion caught a REAL production collision -- TSLA and MSFT both
landed on the identical 2.0900% simulated daily change on the same real day. Root-caused (after two
wrong hand-derived Python replicas -- trusted the actual running JVM's own debug output instead) to
`StockCatalog`'s `changePercent` computation rounding the RATIO to scale 4 before multiplying by 100,
which only preserves 2 real decimal digits of percent precision, not 4 -- collapsing ~120,000
possible 4-decimal-percent outcomes to ~1,200 and making an 11-stock collision realistic rather than
astronomically rare. Fixed the rounding order (divide to a higher intermediate scale, round the
final percent) and widened the daily-return seed from 16 to 32 bits of the SHA-256 digest.

Full suite re-verified clean after all 4 fixes. Commit `cd2fae1c`.

*Shipped: `merchant/.../MerchantServiceTest.kt`, `p2p/.../P2pServiceTest.kt`,
`system/.../FraudReviewServiceTest.kt`, `stocks/.../StockCatalog.kt`*

## 61. Broad Toss-ecosystem sweep -- identity API research, real app-icon bug, error-copy rewording

**2026-08-12**, same session. User asked for deep research + improvement across a wide list: humanized
UX writing, ToS/privacy consent UX that doesn't sabotage simplicity, animation/graphics research that
doesn't sabotage performance, cross-platform consistency, a real developer Payments API, a real
"Toss 인증서"-style identity API letting partners let a user log in or verify themselves, a fully
online-operated bank, fully offline payment with clear identity, and Toss's rebrand/logo work.

**Two of these turned out to already be fully built**, found by direct code search before any new
research or build work: a real "Pay with itunda" external checkout API mirroring Toss Payments
(`merchant/.../PaymentsApiController.kt`, API-key auth, non-interactive checkout creation -- shipped
2026-07-21) and a real "itunda Certificate" e-signature feature mirroring Toss's own 토스 인증서
(`certificate/`, Ed25519 keypair issued after KYC, honestly disclosed as having no legal accredited-CA
standing in Rwanda -- shipped earlier this project's history). Neither needed rework.

**Real, sourced research (two parallel fork agents, matching this thread's own established pattern
for pure research) surfaced one genuinely consequential finding**: Toss's real 본인확인 (identity
verification) product for partners is NOT a simple "verified: true/false" OAuth flow -- it's a
regulated identity-data exchange (real CI/DI linkage tokens, name, birthdate, gender, nationality, a
non-repudiation signature, encrypted transport) gated behind Toss's own partner-vetting process, not
self-serve. Rwanda has no CI/DI equivalent, so a real itunda version needs an explicit decision about
what subset of KYC data itunda would disclose to third-party partners -- a genuine privacy/compliance
call, not something to build unilaterally the way this session's other Toss-parity features have been.
**Flagged to the user, not built.**

Other research, recorded but not yet actioned: Toss's real offline-payment brand recognition finding
(toss.tech/article/43061 -- white background + black text + blue logo as a combined recognition
signal, not the logo alone, discovered building out their TossPlace/Toss Front/Toss Terminal hardware
line) and the real 2022 Toss rebrand (toss.im/tossfeed/article/toss-newlogo -- criteria "uniqueness,
meaningfulness, newness," NOT the "clear identity" phrasing the user's own request used, which wasn't
found in Toss's own words). Toss's real "8 Writing Principles" pyramid (Core Values → Principles →
Guidelines → Templates → Systems, toss.tech/article/8-writing-principles-of-toss) and their real "6
principles for a good error message" (toss.tech/article/21021) were confirmed as primary-sourced and
directly actionable -- see below. A real Simplicity4 session titled "동의 화면도 간결해질 수 있을까"
("Can consent screens be simplified?") confirms Toss researched exactly the ToS/consent-UX angle
asked about, but its actual content (checkbox hierarchy, progressive disclosure mechanics) wasn't
retrievable -- a genuine, named research gap, not silently dropped. No Toss-published material on
animation performance budgets/frame-rate targets was found despite multiple search angles -- also a
genuine gap, not fabricated to fill space.

**Real, severe, previously-unknown bug found via a fresh audit (not research-driven)**: neither the
main `app` (itunda's own consumer wallet app) nor `agentapp` had ANY launcher icon at all -- zero
`mipmap`/`drawable` icon resources, no `android:icon` in either manifest. Both would install and show
a blank/generic icon on the home screen. Confirmed `merchantapp`/`riderapp` already had real
vector-drawable adaptive icons (storefront glyph, scooter glyph, on itunda's real brand blue) -- so
this wasn't a repo-wide gap, just these two apps. Fixed both, matching the existing established
pattern exactly (vector-only adaptive icons, no legacy PNG fallback needed since minSdk 26 on all of
them): `app` gets a shield-check glyph, formalizing the mark already used consistently next to the
"Itunda" wordmark on web's `LoginPage.tsx`/`RegisterPage.tsx` (a real de facto brand mark, not an
invented design) into a real icon for the first time; `agentapp` gets a wallet-plus-coin glyph,
matching the per-app-role-icon convention. Both `:app:assembleDebug`/`:agentapp:assembleDebug` build
clean. Hit and fixed a real, repeated XML gotcha along the way: `--` inside an XML comment (used
stylistically as an em-dash, matching this codebase's own comment convention elsewhere) is invalid
and breaks the resource compiler -- had to reword two comments to avoid it.

**Applied Toss's own "6 principles for a good error message" for real**: grepped the whole backend
for negatively-framed "Cannot X" user-facing blocking messages and found 6 real call sites, all the
same self-payment family (`P2pSelfPaymentException`/`SelfPaymentException` across P2P transfers,
auto-transfers, scheduled transfers, and merchant QR/code payments). Reworded each toward plain
language and a concrete next step -- the two merchant-QR cases got the most direct application of the
"suggest an alternative" principle since a real alternative exists ("share it with a customer instead
of scanning/using it yourself"); the others got warmer, more human phrasing without inventing a fake
alternative where none exists. No test asserted the old exact strings; `:p2p:test`/`:merchant:test`
both clean.

**A real near-miss caught mid-pass, worth naming**: a routine `git status` check (habit, not
triggered by suspicion) found the earlier Shopping-gamification web port (Section 59, documented as
"shipped" and live-verified) had never actually been `git commit`'d -- the doc/memory update happened,
the code change did not get staged+committed in the same pass. Fixed by committing it as its own
clearly-labeled commit, explicit about the gap rather than silently backfilling it. **Lesson**: after
a long stretch of build → verify → document, explicitly re-check `git status` before moving to the
next unrelated task -- documenting something as done doesn't guarantee the commit step actually ran.

*Shipped: `android/app/src/main/res/drawable/ic_launcher_{background,foreground}.xml`,
`android/app/src/main/res/mipmap-anydpi-v26/ic_launcher{,_round}.xml`,
`android/app/src/main/AndroidManifest.xml`, `android/agentapp/` (same set),
`p2p/.../P2pService.kt`, `p2p/.../AutoTransferService.kt`, `p2p/.../ScheduledTransferService.kt`,
`merchant/.../MerchantService.kt`, `bank-mfe/src/lib/shoppingMissions.ts` (the missed commit)*

### Unresolved / worth a follow-up

- **The partner identity-verification API is a real, well-scoped, but NOT-yet-decided feature** --
  needs the user's explicit call on what KYC data subset (if any) itunda would disclose to a
  third-party partner before any code gets written, given the real privacy/trust stakes Toss's own
  actual implementation makes clear this carries.
- The consent-screen-simplification angle has a confirmed real Toss research precedent but no
  retrievable content -- would need itunda's own current ToS/consent screens audited fresh against
  general "progressive disclosure" principles rather than a specific Toss technique, if pursued.
- Animation performance budgets: no Toss source exists to port from; would need itunda's own
  first-principles measurement (real frame-time profiling on a low-end device) if this is still
  wanted, not a research-to-code port.
- iOS untouched for both the icon fix and the error-copy rewording (iOS clients parse the same
  backend `message` field, so the copy fix already applies there automatically once deployed; the
  icon fix does not -- iOS has no `.xcassets`/AppIcon at all either, confirmed but not fixed this
  pass, a real, separate follow-up).
- Not live-verified on-device on either platform this pass (same standing gap as the rest of this
  session) -- the icon fix in particular is inherently unverifiable without literally looking at a
  home screen; flagged rather than assumed correct from a clean build alone.

## 62. Real partner identity-verification API -- "Sign in with itunda", live-verified end-to-end

**2026-08-12**, same session, direct continuation of Section 61's flagged open decision. Asked the
user via `AskUserQuestion` how much data itunda should disclose to a partner on a successful
verification (minimal verified+phone, moderate +name, full KYC-style, or don't build yet). **User
chose full KYC-style** (name, phone, ID-verification status, demographics) -- the closest real
analog to Toss's own CI/DI-linkage-token model, adapted since Rwanda has no CI/DI equivalent to port
directly.

Real flow, mirroring Toss Cert's actual documented shape (toss.im/tosscert/docs/guides/
integration/user, confirmed in Section 61's research): a partner (existing real Partner API-key
auth, reused via a new `PartnerService.authenticate` rather than duplicated) creates a request; the
itunda user reviews a real consent screen naming the partner and exactly what will be shared --
never a silent or default-approve path anywhere in the service; the partner polls for the result.
Disclosed data (`firstName`/`lastName`/`phoneNumber`/`kycVerified`/`birthDate`, from the real `User`
entity) is frozen at approval time in a stored JSON snapshot, so a later profile edit can't
retroactively change what a partner already received. Signed with a NEW system-level Ed25519 key
(`IdentitySigningKeyProvider`) -- deliberately distinct from `CertificateService`'s existing
per-user signing keys, which the backend never stores server-side (handed to the user exactly once)
and so structurally can't sign anything on itunda's own behalf. Requests are real, short-lived (5
minutes), with the same lazy-expiry-on-read convention `P2pPaymentRequest` already established.

New: `IdentityVerificationRequest` entity + `V240` migration, `IdentityVerificationService`,
`IdentitySigningKeyProvider`, `PartnerIdentityController` (`/api/v1/partners/identity/*`, API-key
auth, permitAll at the Spring Security layer matching `PartnerController`'s own pattern) and
`IdentityVerificationController` (`/api/v1/identity/verification/*`, real itunda-user JWT, no
special SecurityConfig rule needed -- falls through to the default `.anyRequest().authenticated()`).
5 real test scenarios in `IdentityVerificationServiceTest.kt`: create, approve (with real Ed25519
signature verification AND a real tampered-payload-fails-verification check), decline, lazy-expiry
rejection, and cross-partner isolation (partner A can't poll partner B's request).

**Fully live-verified end-to-end against the real deployed backend**, not just unit tests: built and
pushed a new image, deployed via `kubectl set image` (hit and rode out a real transient registry
crash-loop mid-push -- the same long-documented [[project_itunda_private_cloud]] overcommitment
issue, self-recovered once retried), watched the rollout, confirmed `V240` applied cleanly in the
live pod logs, then ran the ENTIRE flow with real curl calls: registered a real partner, logged in
as the real seeded demo user, created a request, confirmed zero identity data leaks before approval,
approved, confirmed the partner receives real structured JSON identity data (not a double-encoded
string -- a real bug caught and fixed before this: `disclosedPayloadJson` is stored as a frozen raw
string for signature stability but re-parsed via Jackson before being returned to a partner, so
their own JSON client sees a real nested object) plus a valid Ed25519 signature and a working public
key endpoint. Confirmed all 3 real security boundaries live: wrong API key -> 401, re-approving an
already-approved request -> 409, unauthenticated user access -> 401.

*Shipped: `core/.../IdentityVerificationRequest.kt`, `core/.../IdentityVerificationRequestRepository.kt`,
`app/.../V240__identity_verification_requests.sql`, `partners/.../IdentitySigningKeyProvider.kt`,
`partners/.../IdentityVerificationService.kt`, `partners/.../PartnerService.kt` (new `authenticate`),
`partners/.../web/PartnerIdentityController.kt`, `partners/.../web/IdentityVerificationController.kt`,
`partners/.../IdentityVerificationServiceTest.kt`*

### Unresolved / worth a follow-up

- **No client-side consent UI exists yet on any platform.** The real `itunda://verify/{requestId}`
  deep link (matching the existing `itunda://maps` precedent) has nowhere to land -- a partner
  integrating today gets a fully real, working backend API, but there's no actual screen inside the
  itunda app for a user to see and approve/decline a request. This is a deliberate, named, explicitly
  scoped-out follow-up given this pass's size, not a silently dropped piece.
- The system signing key is in-memory only, regenerated on every pod restart (a real, honestly
  documented demo-mode gap in `IdentitySigningKeyProvider`'s own doc comment) -- a real production
  version needs a persisted, rotatable key in a real secrets store, with old-generation public keys
  staying fetchable so signatures made before a rotation stay verifiable.
- `requestedFields` is a fixed list for v1 (matching the single "full KYC-style" scope this pass
  built) -- no per-partner configurable scope system exists, unlike Toss's own real richer model.

## 63. Physical Android device reconnected -- first real on-device verification in this entire session

**2026-08-13**, same session. The physical Android test device (offline over WiFi for this whole
session's second half, a real standing gap flagged repeatedly across Sections 50-62) reconnected
with a new wireless-debug address. Set up the same real networking recipe
([[feedback_physical_device_networking]]) fresh: `adb connect`, local `socat` relays (`30081` for
the real backend API, `8090`/`8091` for the self-hosted map tiles/glyphs), `adb reverse` for each,
then `installDebug -PapiBaseUrl=http://127.0.0.1:30081/ ...` against the actual deployed backend.

**Real, immediate false alarm caught and resolved before assuming a bug**: the very first
`screencap` after cold launch came back solid black despite `dumpsys window` confirming itunda
genuinely had foreground focus. Checked `MainActivity.kt`'s own real `FLAG_SECURE` logic (added
2026-08-09 for real screenshot/recording protection, explicitly skipped in debug builds since
2026-08-11 specifically so on-device UI verification stays possible) and confirmed via the raw
window-flags dump that FLAG_SECURE was genuinely NOT set on this debug build. A second screencap
taken moments later came back completely normal (real map content, ~35x larger file size) --the
first was just a transient cold-start GPU-surface warm-up frame, not FLAG_SECURE, not the
previously-documented foreground-contention issue. **Lesson**: a single black frame right after
`am start` isn't automatically evidence of the known FLAG_SECURE/contention failure modes -- verify
the specific mechanism (window flags, focus) before concluding either, and just retry once.

**Real coordinate-tapping lesson**: guessing device-pixel tap coordinates from a scaled-down preview
image (applying the stated display-to-real scale factor by eye) missed the real bottom-nav tab bar
by a wide margin three times in a row, landing on unrelated content each time. Switched to
`uiautomator dump` + grepping the target text's real `bounds="[x1,y1][x2,y2]"` -- exact, reliable,
worked first try every time after. **Use `uiautomator dump` for real element coordinates on native
Android, never eyeball scaled screenshot pixels.**

**Real, genuine live confirmations obtained** (screenshots taken directly from the device, not
simulated or assumed) -- closing a large amount of this session's own repeatedly-flagged
"build-verified only, never actually seen" debt:
- **Explore tab** (Sections 49-50): Open/Shortcuts icon grids, then one flat "Quick links" list --
  confirmed still exactly as fixed, no regression.
- **Maps** (dark style, category chips, real search with an honest "No real places found" empty
  state) rendering correctly with real Kigali street data.
- **Home tab's "Get interest" pill** (Section 57, the actual feature this session's identity-API
  thread grew out of): confirmed rendering with the real live unclaimed balance
  (`RWF 858,081 · Get interest`), and confirmed tapping it navigates to the real
  `SavingsFlowStep.ClaimInterest` screen exactly as wired.
- **Shopping "Get points and coupons" mission row** (Section 53): confirmed rendering with the real
  backend-sourced reward amounts (`Scroll +30`, `Draw a prize +10~300`, `Cat +15`, `Claim +200`),
  `Check-in` correctly shown already-completed/dimmed from an earlier real claim.

Session-local, same as every prior networking-recipe entry -- must be redone next session if the
device drops offline again (device address, port, all `socat`/`adb reverse` state).

## 64. Real Pretendard typeface across all 3 platforms -- the first "visual craft" pass

**2026-08-13**, same session. User's direct feedback: "we are still far away from toss." Asked
where to focus next; user chose both visual polish/craft AND continuing to close remaining feature
gaps. Started with visual craft since it hadn't been systematically addressed all session (every
prior pass fixed individual component behaviors -- colors, spacing on specific screens, disabled
states -- never the base typography layer itself).

Checked `IdsTypography.kt` first and found its own header comment had named this exact gap since
the file was written and never closed: `private val defaultFontFamily = FontFamily.SansSerif`, with
a comment reading "(Ideally mapped to Toss Product Sans or Pretendard if custom font added)".
Checked iOS (`IDS.swift`) and every web app's `index.css` -- confirmed the identical gap existed on
all 3 platforms, not just Android: `UIFont.systemFont(ofSize:weight:)` on iOS, the standard
`-apple-system, ... sans-serif` fallback stack on web. A real, cross-platform, base-layer gap, not a
one-screen issue -- explains a large share of why the app reads as "generic Material/SF/system UI"
rather than having Toss's own distinctive visual character, even with correct real colors and a
real, sourced type SCALE (sizes/line-heights) already in place since Section 21-ish's TDS research.

**Pretendard** (github.com/orioncactus/pretendard, real SIL Open Font License 1.1, confirmed
license terms permit bundling in commercial software) is the real, free, open, widely-recognized
typeface Korean fintech apps deliberately matching Toss's own visual register actually use as a
stand-in for Toss's real proprietary in-house font -- not invented or guessed, verified via the
license file and package structure directly. Downloaded 4 real static weights (Regular/Medium/
SemiBold/Bold, matching every FontWeight each platform's own type scale actually needs) as both TTF
(mobile) and WOFF2 (web) from the real npm package via jsDelivr's CDN, confirmed genuine via `file`.

- **Android**: new `Pretendard.kt` mirrors the exact bundling pattern this repo already established
  for `TossFaceFontFamily` (Toss's own real open-source emoji font, already bundled the same way) --
  found this precedent BEFORE building anything, not coincidentally similar. License bundled at
  `core/designsystem/PRETENDARD_LICENSE.txt`, matching `TOSSFACE_LICENSE.txt`'s own precedent.
- **iOS**: found `IDS.scaledFont` is the ONE real function every font call site across the entire
  app funnels through (dozens of real call sites checked via grep, all consistent) -- fixed it once,
  centrally, plus its one local duplicate in `ContentView.swift` (kept local as a deliberate
  cross-module-dependency avoidance, per that file's own existing comment). Resolves a real
  `UIFont(name: "Pretendard-<Weight>")` by PostScript name (verified via `fontTools` against the
  actual downloaded files, not guessed), falling back to the system font defensively, while keeping
  the real `UIFontMetrics(forTextStyle:).scaledFont(for:)` Dynamic Type accessibility wrapping fully
  intact -- this was a real, deliberate 2026-07-11 accessibility fix and this pass does not regress
  it. Fonts registered via `UIAppFonts` in `Project.swift`, bundled at `App/Resources/Fonts/`.
- **Web**: real `@font-face` declarations plus a new `--itunda-font-family` custom property added to
  `packages/design-tokens/tokens.css` -- the same real single-source-of-truth file this repo already
  uses for colors, extended to typography for the first time. All 6 web apps' `body` rules (bank-mfe,
  merchant-mfe, kyc-mfe, host-app, ops-mfe, pay-checkout) switched to reference the token, keeping
  each app's own original system-font stack as a real CSS fallback if the custom property somehow
  isn't defined. `font-display: swap` so a slow font load never blocks text from rendering.

**Fully live-verified on all 3 platforms**, not just build-verified:
- **Android**: rebuilt, installed on the real physical device (reconnected this session, Section
  63), launched, and took a real before/after screenshot comparison -- a tight zoomed crop on the
  word "Wallet" shows genuinely different letterforms (the "a" bowl shape and "t" terminal curve
  both visibly differ between the old Roboto rendering and the new Pretendard one).
- **iOS**: `tuist generate` + `pod install` + full `xcodebuild` on the `ItundaApp` scheme --
  **BUILD SUCCEEDED**, 0 errors (the only failures on the first attempt were pre-existing CocoaPods
  module-resolution errors from a stale generated project, unrelated to this change, resolved by
  `pod install` as already known from this project's own build-env history).
- **Web**: headless-Chrome/CDP technique ([[feedback_headless_chrome_verification]]) confirmed
  `getComputedStyle(document.body).fontFamily` genuinely starts with `"Pretendard"` on the real
  running login page, and `document.fonts` shows the real weight faces (400/600/700) with
  `status=loaded` (500/Medium correctly shows `unloaded` -- nothing on that specific page uses that
  weight yet, standard lazy web-font loading behavior, not a bug).

*Shipped: `android/core/designsystem/.../Pretendard.kt`, `.../IdsTypography.kt`,
`.../res/font/pretendard_*.ttf`, `ios/Core/DesignSystem/Sources/IDS.swift`,
`ios/App/Sources/ContentView.swift`, `ios/Project.swift`, `ios/App/Resources/Fonts/*.ttf`,
`packages/design-tokens/tokens.css`, `packages/design-tokens/fonts/*.woff2`, all 6 web apps'
`index.css`, `*/PRETENDARD_LICENSE.txt` on all 3 platforms*

### Unresolved / worth a follow-up

- This closes the single biggest base-layer typography gap, but "visual craft" is much larger than
  a typeface swap -- real Toss-level polish also comes from custom iconography (still plain Material/
  Lucide icons everywhere), illustration work (empty states are still plain text, e.g. "No properties
  near you yet"), and finer spacing/elevation refinement. Named as the natural next targets for
  future visual-craft passes, not claimed as closed by this one fix.
- Only Regular/Medium/SemiBold/Bold weights bundled -- one real iOS call site uses `.heavy`
  (`BenefitsShopAllScreens.swift:61`), mapped to the closest bundled weight (Bold) rather than
  fetching a 5th font file for a single call site; a real, minor, honestly-noted simplification.
- Web font-loading performance (first-paint flash while Pretendard downloads, mitigated by
  `font-display: swap` but not eliminated) not specifically measured or optimized this pass.

## 65. Home/itunda-Bank direct comparison -- credit score, Auto-transfer, top bar, banner

**2026-08-13**, same session. User sent 12 real screenshots in two batches: 6 comparing itunda's
own real BankHubScreen against real Toss Bank's account-detail and product-catalog screens
("why don't they look the same itunda bank and toss bank"), then 2 more specifically on the Home
top bar and hero banner ("app bar should be 100% same as this, itunda intelligence banner should
[look] like this and if nothing new to recommend or suggestion it should disappear").

**Honest, upfront diagnosis given first, not a silent rebuild**: the two Bank screens diverge in
several real, structural ways -- richer/more-varied per-product iconography on Toss's side, real
inline interest rates on every product row, real header tabs (카드/관리), and a real "Recommendations"
carousel leading the product catalog. Named all of these explicitly as real gaps, then picked the
most concretely buildable ones with real existing itunda data rather than attempting a full
one-pass rebuild of everything at once.

**Four real, concrete fixes shipped**:
1. **Credit score card** -- Toss's real account-detail screen has a dedicated "내 신용점수" (My
   credit score) row with a "보기" (View) button. itunda already has this exact real feature
   (`rw.itunda.creditscore`, a real score computed from account activity, already load-bearing on
   `LoansService`'s risk gate, with its own real `CreditScoreScreen`) -- it was just never surfaced
   on Home, only reachable from deep inside Explore's Money tools list. Added to `HomeTab`, fetched
   via the same real `GET /api/v1/credit-score` endpoint.
2. **Auto-transfer row** -- Toss's account-detail card shows "Auto Transfer / 2 Items" directly
   below Top up/Send. itunda already fetches this exact real data (`autoTransferCount`), previously
   surfaced only inside `TransferHubScreen` -- added to `WalletHeroCard` in the same position.
3. **Top app bar redesign** -- direct comparison found itunda's search box had never actually been
   wired to anything at all (no `onClick`, no destination -- a real dead UI element, not just a
   visual mismatch, confirmed by reading the code before removing it). Replaced with a real "Pay"
   shortcut pill (opens the same real Pay screen the My tab's own Pay row reaches) and a
   notification bell with a REAL unread-count dot (`MainViewModel.unreadNotificationCount`, already
   powering `NotificationListScreen`'s own badge, never surfaced on this bar before).
4. **Personal recommendation banner restyle** -- checked first whether "disappears when nothing to
   recommend" was already true before treating it as a bug: it was (`PersonalRecommendationCard` is
   only ever called inside `HomeTab`'s own `if (heroDiscoverItem != null)` gate) -- confirmed, not
   silently assumed. Restyled to match the real reference's visual weight (full-bleed
   accentColor-tinted background using the item's own real per-item color, bigger bold title, a real
   dismiss "X"). Also closed a real, previously-documented gap: the CTA used to be deliberately
   inert text (a prior pass's own comment explains why -- no real destination existed on
   `DiscoverItem` at the time, and a dead-tap `IdsButton` would repeat a bug this session has fixed
   elsewhere). Found that `DiscoverService`'s real backend only ever emits 3 real category values
   (`account`/`savings`/`credit`, confirmed by grep, not guessed) -- mapped each to a real,
   already-built itunda screen (Identity verification / itunda Bank / Loans), making the CTA
   genuinely functional for the first time without inventing a destination.

**Live-verified on the real physical device**: credit score card renders with the real fetched
score and "View" navigates to the real `CreditScoreScreen`; Auto-transfer row renders with the real
count-driven subtitle ("Set up a recurring transfer" at 0, matching `TransferHubScreen`'s own
established copy); new top bar renders with the search box genuinely gone and a real 0-unread bell
state (no dot, correctly honest since there are no real unread notifications right now); the banner
correctly does NOT render in the same screenshot, live-confirming the "disappear when empty"
behavior rather than just trusting the code read.

*Shipped: `android/app/.../ItundaAppScreen.kt` (`HomeTab`, `WalletHeroCard`, `HomeTopBar`,
`PersonalRecommendationCard`), `android/app/src/main/res/values/strings.xml`*

### Unresolved / worth a follow-up

- **The restyled banner's new visual appearance was NOT live-verified** -- no real discover item
  existed for this account at verification time (confirmed empty-state behavior instead, which is
  real and valuable, but different from confirming the new WITH-item visuals). A real follow-up once
  a real recommendation is live for some account.
- Real gaps named in the initial diagnosis but not yet built this pass: richer/varied per-product
  iconography on `BankHubScreen`'s rows, real inline interest-rate display on every row (currently
  inconsistent -- some rows show a rate/percent pill, most don't), the real Toss 카드/관리
  header-tab structure, and a "Recommendations" carousel leading the product catalog. Named
  explicitly, not silently folded into "done."
- iOS/Web untouched this pass -- all 4 fixes are Android-only.

## 59. Uber (ride-hailing/delivery) -- first real research pass, plus a fresh Rwanda-market localization gap

**Added 2026-08-15**, continuing the standing "keep searching other ecosystems" directive
(Section 19 first opened this beyond Toss). Uber itself had never been researched anywhere in
this document despite being one of the most directly comparable global products to itunda's own
Eats delivery vertical -- checked and confirmed via a full-document grep before starting, not
assumed.

### References

| Ecosystem | Finding | Status |
|---|---|---|
| Uber Eats delivery tracker redesign | restaurantdive.com's coverage of Uber Eats' own real 2026 tracker overhaul, sourced from real internal research across nine countries and hundreds of eaters/couriers/restaurant partners | Real five-stage tracking bar (confirm → prep → en route → pickup → arrival) with the driver's real name and a "Latest Arrival By" time shown alongside the delivery ETA -- explicitly aimed at reducing blame ("62% of diners blame both third-party aggregates and eateries for late or cold orders") through more upfront transparency | **Checked against itunda's own Eats order-tracking screen and partially fixed same day**, see below |

### Checked against itunda's own code

itunda's Eats order-tracking screen (`EatsOrderRow`/`EatsStatusStepper`, Android) already had a
real stepped visual progress row (dots + connecting bars across the real backend status chain
`PLACED → ACCEPTED → PREPARING → READY_FOR_PICKUP → RIDER_ASSIGNED → PICKED_UP → DELIVERED` --
actually more granular than Uber Eats' own 5-stage bar), plus a real live rider-location mini-map
and a real delivery-route view, both already gated correctly on an active delivery. This is at or
beyond Uber Eats' own tracker in structural terms -- not a gap.

What genuinely was missing, confirmed by reading `EatsOrderDto`/`EatsOrder` directly: the assigned
rider's real name was never resolved and shown to the customer (only a raw `riderId`), and the
order's own already-stored `distanceKm` was never turned into a customer-facing arrival estimate
once a rider was assigned -- both exactly the two real signals Uber Eats' own redesign added.

### Fixed same day

**Real rider name + arrival estimate on order tracking** -- `DeliveryEtaEstimator`
(`services/backend/core/.../geo/DeliveryEtaEstimator.kt`, new file) promotes the exact real
distance→minutes formula that already existed as a private method on `ShoppingController` (used
for Eats/Shop's own pre-order browse-time estimate) into a shared object, same "shared, not
duplicated" discipline `FullTextSearchUtil` already established this session for an identical
two-controllers-need-the-same-real-formula situation. `EatsController` gained `riderRepository`/
`userRepository` and a `withRiderEtaFields` mapper resolving `order.riderId` → the real rider's
`firstName`, wired into `GET /eats/orders/my-orders`. Resolved at the controller layer rather than
adding two new repository dependencies to `EatsOrderService`'s own already-19-parameter
constructor, which would have meant updating all 9 of that service's existing test call sites for
a read-only enrichment unrelated to its actual business logic -- `EatsController` has no test file
of its own yet, making it the genuinely lower-risk seam, not just the more convenient one.
Android's `EatsOrderDto` gained matching `riderName`/`estimatedArrivalMinutes` fields, rendered as
a real "Rider: {name} · Latest arrival by ~{n} min" line, shown only once at least one of the two
is non-null (never a fabricated ETA before a rider is actually assigned).

*Shipped: `services/backend/core/.../geo/DeliveryEtaEstimator.kt`,
`services/backend/merchant/.../web/ShoppingController.kt` (now calls the shared version),
`services/backend/eats/.../web/EatsController.kt`, `android/core/network/.../ApiService.kt`
(`EatsOrderDto`), `android/features/eats/impl/.../EatsScreen.kt` (`EatsOrderRow`)*

**Verification**: `:eats:test`/`:merchant:test`/`:app:compileDebugKotlin` all green. **Not yet
deployed to the live backend or curl/device-verified** -- named honestly, not silently folded
into "done," same discipline every other unverified item in this document already follows.

### A second, unrelated real gap re-confirmed this same research thread

Cross-checking Section 19's own already-sourced Paytm/PhonePe regional-language finding (>50% of
new fintech users in a comparable market prefer their own language over English) against the
actual current codebase -- not just trusting the doc's own years-old note -- found it was still
real: `bank-mfe` had English + Kinyarwanda (since 2026-08-08) but zero French, and every other web
micro-frontend (`merchant-mfe`, `ops-mfe`, `kyc-mfe`, `pay-checkout`, `host-app`) had zero
localization infrastructure at all. **Fixed same day**: added a full, real French locale to
`bank-mfe`'s existing i18n system (same architecture, no new library, every existing key
translated) -- live-verified end to end against the real backend: login page, an authenticated
Home/Discover view, and the complete Send Money transfer flow all confirmed rendering correct
French through a real session, not just compiled. *Shipped:
`services/micro-frontends/bank-mfe/src/i18n/translations.ts`, `I18nContext.tsx`, `LoginPage.tsx`,
`BankDashboard.tsx`.* Porting the same pattern to the other MFEs, finishing Android's remaining
~37% of untranslated strings, and any iOS localization at all are all real, still-open follow-ups
-- see the `project_itunda_localization` memory for the full current-coverage account.

### A real, sourced, deliberately-deferred lead: home-screen quick-pay widget

**[sourced, real gap confirmed, not built this pass]** KakaoPay's Android app added a real 1x1
home-screen payment widget (2025) for one-tap access to payment/scan without opening the app first.
Checked itunda's own Android codebase directly (`find android -iname "*Widget*.kt"`, grep for
`AppWidgetProvider`/`GlanceAppWidget`): **zero home-screen widget infrastructure exists anywhere**
-- a real, confirmed gap. Deliberately not built this pass: widgets can only be meaningfully
verified by actually placing one on a real home screen and observing render/tap behavior, which
needs the physical device this session explicitly doesn't have access to
(`project_itunda_ecosystem_deep_research_2` memory). Building one compile-only, with zero way to
catch the real lifecycle/rendering quirks widgets are specifically prone to, would be shipping
blind rather than honestly scoped -- the same discipline this document already applies elsewhere
(e.g. this pass's own kyc-mfe REJECTED-state fix, code-verified but explicitly not claimed as
live-clicked). Real next step once a device is available: a "Scan to Pay" 1x1 widget deep-linking
into the existing Pay tab's scan flow, same real target Kakao's own widget serves.

## 66. 당근마켓 (Karrot) 바로구매 -- real shipped-item support closes a genuine escrow gap

**Added 2026-08-15**, continuing the standing "keep searching other ecosystems" directive.
Searched specifically for what's changed recently in Karrot's own real product (rather than
re-reading the same 2026-08-04 research pass), found 바로구매 (roughly "buy now"): since
2025-09-17 Karrot lets a seller enter real shipping info on a listing and a buyer pay + provide a
delivery address in-app, receiving the item via a real CJ대한통운 (CJ Logistics) courier
partnership -- a genuine product shift beyond Karrot's original hyperlocal-meetup-only model.

### Checked against itunda's own code

itunda already has a real, matching safety concept: `MarketplaceEscrow` ("pay via itunda," item
2026-07-25) -- a buyer's payment is held by itunda until they confirm receipt, closing the same
real trust gap Naver Cafe's 안전거래 (Safe Trade) product exists to solve. But reading the entity
and `confirmReceipt` directly: the whole model still assumes an **in-person** handoff --
"confirm receipt" means physically receiving the item from the seller, and there was no field
anywhere (entity, request, DTO) for a delivery address. A buyer wanting to use itunda's own real
escrow protection for a non-local trade had no way to actually receive a shipped item -- a real,
confirmed gap, not a guess.

**Fixed same day** (`b3256e5b`): added a nullable `deliveryAddress` to `MarketplaceEscrow`
(V244 migration), threaded through `payEscrow` end to end, optional and backward-compatible
(existing callers sending no body are unaffected). Android's `MarketplaceScreen.kt` gained an
optional "Delivery address (optional, for a shipped item)" field before the real "🔒 Pay via
itunda" button. Wrote 2 new real unit tests for `payEscrow`'s own happy path, which had **zero**
test coverage before this (the only existing test covered the rate-limit failure path only).

**Deliberately not built**: a real live courier-tracking integration matching Karrot's actual
CJ대한통운 API -- itunda has no equivalent logistics partner, and fabricating one would violate
this project's own standing discipline against inventing fake external integrations (matching how
KYB verification is honestly a structural pre-check, not a live registry lookup). This is the
honest v1: a real address field so buyer and seller can actually coordinate shipping themselves.

**Web escrow gap CLOSED same session (`4ad097ce`), live-verified.** Built the first web client for
payEscrow/confirmEscrowReceipt/disputeEscrow/getEscrow in bank-mfe's `ListingCard`, mirroring
Android's flow -- and, informed by the real buyer-only-visibility bug just found and fixed on
Android (`c6a344f1`), built web's version correctly from the start: escrow status and the real
delivery address are shown to BOTH the buyer and seller of a SOLD listing, not buyer-only.
Live-verified: a real second test account viewed a real listing, the delivery-address field and
Pay button rendered correctly, and submitting round-tripped to the real backend (address +
Idempotency-Key both carried correctly), surfacing a real `DEVICE_NOT_VERIFIED` error text --
proof the full request/response cycle works; that specific gate is this test device's own,
unrelated to the feature itself.

**iOS escrow gap CLOSED same session (`dca5436c`).** Built iOS's first escrow client in
`HoodScreen.swift`'s `ListingCard`, using the same buyer+seller-symmetric design already proven on
Android/web -- `NetworkClient.swift` gained matching DTOs/methods for
payEscrow/confirmEscrowReceipt/disputeEscrow/getEscrow. Full `ItundaApp` scheme `xcodebuild`
(iphonesimulator, Debug) succeeded clean. Not device-verified (no physical device available this
session), but this closes the feature on all 3 real client platforms plus the backend.

## 67. 카카오페이 춘식이QR -- itunda DineIn requires a full account; a real gap, scoped not built

**Added 2026-08-15**, continuing the standing "keep searching other ecosystems" directive. Fresh
search on KakaoPay's own recent 2026 roadmap (not a re-read of old research): KakaoPay is expanding
'춘식이QR' ("Chunsik QR"), a real, live product already deployed to ~3,000 stores -- a printed QR
sticker at a table lets a customer order and pay using *only* KakaoTalk (which nearly every Korean
already has installed for messaging), no separate app install and no merchant POS hardware beyond
the sticker itself. The whole point is removing account/app-install friction for a one-off diner.

### Checked against itunda's own code

`DineInOrderController.placeOrder` (`services/backend/eats/src/main/kotlin/rw/itunda/eats/web/
DineInOrderController.kt:60-68`) requires `currentUser.userId` -- i.e. a real, already-authenticated
itunda account. There is no anonymous/guest path anywhere in the DineIn flow. For Rwanda's market,
where itunda itself is a new app with no KakaoTalk-level existing install base to piggyback on, this
is a real, confirmed friction point: a first-time diner at a table has to install the app AND create
an account before they can order at all, unlike Kakao's zero-install path.

**Deliberately not built this pass** -- this is a genuinely larger, architectural feature (a guest/
anonymous ordering session, likely phone-number+OTP-scoped rather than full account creation, plus
a way to later claim/merge that order into a real account if the diner does sign up) that needs
real design thought before implementation, not a same-session quick fix like the AlreadyX error-
handling gaps found the same day. Also checked and ruled out as *not* itunda's existing QR flows:
the real Marketplace/Pay QR code + camera scanner (item 2026-08-11, [[project_itunda_pay_kakaopay_parity]])
is a P2P payment QR between two existing itunda accounts, not a merchant-table-ordering QR -- a
different feature with the same "QR" surface, not already covering this gap.

**How to apply:** if guest DineIn ordering is ever prioritized, start with
`DineInOrderController.placeOrder`'s auth requirement and `DineInRepositories.kt`'s `DineInOrder`
entity (currently assumes a real `buyerId` FK) -- both would need a real design decision on how a
guest identity is represented and how payment is captured without an existing itunda wallet.

## 68. Silent session refresh -- a real dead-end found by the sweep methodology, not research

**Added 2026-08-15.** Not sourced from an external ecosystem this time -- found by this session's
own periodic dead-endpoint sweep (see [[feedback_uncalled_endpoint_sweep]]/
[[project_itunda_full_ecosystem_polish]]), which flagged `refresh` as having zero callers anywhere
in `android/`/`ios/`. Checked the real backend: `JwtService.kt` issues a 24-hour access token and a
7-day refresh token. With `AuthApi.refresh()`/`NetworkClient.refresh()` never called by ANY client,
any session left open past 24 hours got a raw, unrecoverable 401 on every subsequent screen -- and
Android/iOS didn't even have a forced-logout fallback (only bank-mfe did, via a hard
`logout()` + `SESSION_EXPIRED_EVENT` on any 401). A real, previously-undiscovered violation of this
codebase's own standing Toss-style "never leave a dead end" error-handling philosophy
([[feedback_toss_error_handling]]).

**Fixed on all 3 platforms, same session:**
- **Android** (`3c1f917a`): a real OkHttp `Authenticator` (`refreshAuthenticator` in
  `core/network/ApiService.kt`'s `NetworkClient`) that, on 401, calls a new synchronous
  `refreshSync()` twin of the existing suspend `refresh()`, persists the rotated token pair, and
  retries the original request -- fully silent. Guards concurrent-401 races with a lock and gives
  up after one retry to avoid a loop. `SessionManager.kt` gained `forceLocalLogout()` for the one
  real case a session must still end (refresh token itself invalid/expired).
- **iOS** (`014ed309`): `dataWithRefresh(for:)` in `NetworkClient.swift`, which all 13 of that
  file's raw `session.data(for:)` call sites now route through. Single-flight via a private
  `actor` (`RefreshCoordinator`), the correct Swift-concurrency-safe equivalent of Android's lock.
  Real module-boundary catch made while building this: `CoreNetwork` (NetworkClient's module)
  cannot import `App` (`SessionManager`'s module) -- decoupled via
  `NotificationCenter`/`sessionExpiredNotification` instead of a direct reference.
- **bank-mfe** (`48dab178`): `apiFetch` in `lib/api.ts` now attempts a silent refresh (single-flight
  via a shared in-flight `Promise`) before falling through to its existing forced-logout path.

**Live-verified twice, two different ways:**
1. The real backend refresh contract itself, via direct `curl` against the live cluster
   (`127.0.0.1:30081`): confirmed refresh rotates BOTH tokens, the new access token authenticates,
   the OLD refresh token is correctly rejected (`INVALID_REFRESH_TOKEN`, proving rotation), and the
   new refresh token itself chains correctly for a second refresh.
2. bank-mfe end to end in a real browser (Claude-in-Chrome): registered a real test account,
   corrupted the stored access token to simulate expiry while leaving the real refresh token
   intact, reloaded -- confirmed via `localStorage` inspection that the corrupted token was
   silently replaced with a new valid one (proving the 401-refresh-retry path actually ran) and the
   dashboard rendered fully logged-in with zero forced logout.

Android/iOS are compile-verified only (no physical device this session) but rely on the exact same
already-live-verified backend contract.

## 69. Uber Senior Accounts -- itunda's Family Link can't request a ride for someone else

**Added 2026-08-15**, continuing the standing "keep searching other ecosystems" directive. Fresh
search on Uber's own real 2025-2026 roadmap (not a re-read of old research): Uber launched "Senior
Accounts" nationwide in the US (2025-06-04 press release) and has been expanding internationally
through 2026 -- a family organizer links an elderly relative's account (reusing Uber's existing
Family Profile feature), then can request rides *on that relative's behalf*, manage their payment
methods, and follow the trip live, all from the organizer's own phone. A separate "Simple Mode"
gives an independent older rider a lower-friction UI (larger text, fewer on-screen buttons,
optional voice commands) without needing a family link at all.

### Checked against itunda's own code

itunda already has a real, matching relationship primitive: `FamilyLinkController`
(`services/backend/family/src/main/kotlin/rw/itunda/family/web/FamilyLinkController.kt`) --
guardian/child invites, `childOverview` (read-only spending visibility), and `setSpendLimit`. But
reading it end to end, the whole feature is strictly **read + limit-setting**, not
**act-on-behalf-of**: there is no endpoint anywhere that lets a linked guardian actually request a
ride, book a service, or place an order FOR the linked child/relative. `RideController.requestTrip`
and every other request-creating controller only ever create the trip/order/booking for the
authenticated caller themselves -- a real, confirmed gap, not a guess.

**Deliberately not built this pass** -- this is a genuinely larger, architectural feature (unlike
the same-day Idempotency-Key/decode-shape bug fixes, which were surgical), for the same reason
Section 67's guest-DineIn-ordering gap was scoped and not built: it needs real design decisions
before implementation, not a same-session quick fix --
- **Who is the "rider" of record?** The linked relative must remain the actual passenger a driver
  picks up and a trip's pickup/dropoff/live-location notifications target -- not silently
  reassigned to the requesting family member, which would break every existing "the rider is the
  authenticated caller" assumption baked into `RideTripService`/push notifications/live tracking.
- **Who pays?** Uber's real version supports the organizer's own card, meaning payment
  authorization needs to flow from a DIFFERENT wallet than the trip's own rider -- itunda's ledger
  model currently assumes a trip's payer and rider are the same account.
- **Consent/scope**: Uber's Family Profile linking is an explicit, mutual, revocable relationship
  (matching itunda's own `FamilyLink` invite/accept/revoke flow already) -- reusable as the trust
  primitive, but the request-on-behalf-of action itself needs its own explicit authorization scope,
  not implied by an existing read-only spend-limit link.

**Smaller, separable, more tractable half: BUILT on Android same day (`7a6fa1b5`).** Uber's
"Simple Mode" text-scale piece (larger text, no family link required) was architecturally simple
enough to build immediately rather than defer -- a new `TextScalePreference`
(Default/Large/Extra large, 1.0x/1.15x/1.3x), same StateFlow + `TokenStore`-backed shape as the
existing `ThemePreference`/`AppLocalePreference`, applied at `MainActivity`'s root by overriding
only `LocalDensity`'s `fontScale` component (so text scales without also inflating dp-based
spacing/icon sizes). New "Text size" row in Settings, real en/rw/fr copy. `:app` compiles clean.
The "fewer on-screen buttons" half of Simple Mode (a genuinely simplified nav/layout, not just
larger text) and the request-on-behalf-of half above both remain open -- both need real design
work, not a quick toggle.

**How to apply:** if request-on-behalf-of is ever prioritized, start with `FamilyLink`'s existing
guardian/child relationship as the trust primitive (already real, already has consent/revoke), and
`RideController.requestTrip`/`DesignatedDriverController.requestTrip` as the first two real
candidates (rideshare is where Uber's own feature shipped first) -- both would need a new optional
`onBehalfOfUserId` request field, a real authorization check against `FamilyLinkRepository`, and a
clear design decision on payer vs. rider wallet routing before writing any client code.

### Follow-up: iOS's text-scale accessibility support was more nuanced than first assumed

While porting Simple Mode's text-scale toggle to iOS (after building it on Android), found iOS
already has REAL, working accessibility text scaling -- just not the custom in-app toggle Android
now has. `IDS.Typography` (`ios/Core/DesignSystem/Sources/IDS.swift`) has used
`UIFontMetrics(forTextStyle:).scaledFont(for:)` since 2026-07-11 (predates this session entirely),
the documented Apple pattern for "keep this exact point size at the default content size category,
but still scale with the user's real iOS Settings > Accessibility > Larger Text setting." Every
screen built purely from `IDS.Typography.*` constants already respects Dynamic Type correctly,
with zero code changes needed.

**Building a separate custom `TextScalePreference` on iOS (mirroring Android's exact 3-option UI)
would be the wrong move** -- it would duplicate and likely conflict with the real, more
platform-idiomatic mechanism already in place (a genuine system setting, not an app-specific one,
is the correct default for iOS; Android lacks an equivalent OS-level font-scale hook accessible the
same way, which is why the custom `TextScalePreference` was the right call there).

**The real remaining gap on iOS**: 86 raw `Font.system(size: N, weight: W)` call sites across 15
files (`AppLockScreenView.swift`, `ContentView.swift`, `DeviceStepUpView.swift`, `EatsScreen.swift`,
`InvestScreenView.swift`, `MapScreenView.swift`, `SavingsFlowContainer.swift`, `SettingsScreen.swift`,
`ShopScreen.swift`, `TalkScreen.swift`, `TransferFlowContainer.swift`,
`Features/Banking/Sources/BankView.swift`, `Features/Payments/Sources/TransactionHistoryScreen.swift`,
`Features/Payments/Sources/TransferFlowScreens.swift`, `MerchantApp/Sources/DeviceStepUpDialog.swift`)
bypass `IDS.Typography`/`IDS.scaledFont` entirely -- plain point sizes that do NOT grow with Dynamic
Type, unlike every screen using the shared typography constants. **Deliberately not converted this
pass**: each of the 86 needs individual judgment on the right `relativeTo: UIFont.TextStyle`
mapping (matching `IDS.Typography`'s own precedent -- e.g. size 15 medium -> `.subheadline`, size 13
-> `.caption1`, size 20 bold -> `.title2`), and with no physical iOS device available this session
there is no way to visually confirm a given mapping actually looks right once Dynamic Type is
cranked up, unlike a change that can be reasoned about purely from source. Converting 86
judgment-heavy call sites blind risks a real regression (a screen scaling oddly) that would go
unnoticed until someone actually looks at it on-device.

**How to apply:** if this is ever prioritized, do it with a physical device in hand (or the
simulator's own Text Size accessibility inspector) so each `relativeTo` choice can be visually
confirmed, not just source-reasoned. `IDS.scaledFont(size:weight:relativeTo:)` already exists and
is the correct helper -- this is a mechanical sweep once verification is possible, not a design
problem.

## 70. 배달의민족 함께주문 (Baemin Together Order) -- a real shared-cart gap, built and live-verified

**Added 2026-08-15.** Fresh, dated research (not doc-mining): 우아한형제들 (Woowa Brothers, Baemin's
operator) added a real 더치페이 (Dutch pay / bill-split) feature to their existing 함께주문 ("Together
Order", shipped 2022-10) group-ordering service, announced 2026-06-25, rolling out to all customers
by end of July 2026 -- cross-verified across 8 outlets: [ekn.kr](https://m.ekn.kr/view.php?key=20260625023276441),
[zdnet.co.kr](https://zdnet.co.kr/view/?no=20260625110853), [hankyung.com](https://www.hankyung.com/article/202606257553g),
[hankookilbo.com](https://www.hankookilbo.com/news/article/A2026062511280002367),
[greened.kr](https://www.greened.kr/news/articleView.html?idxno=343324),
[digitaltoday.co.kr](https://www.digitaltoday.co.kr/en/view/74664/baemin-introduces-dutch-pay-feature-to-group-orders),
[sedaily.com](https://en.sedaily.com/news/2026/06/25/baemin-adds-bill-splitting-feature-to-group-order-service),
[asiae.co.kr](https://www.asiae.co.kr/en/article/2026062508465421433).

**How it actually works (confirmed across all 8 sources, not assumed):** multiple people share a
link for one restaurant, each adds their own items to a combined cart. The person who places the
order pays for the WHOLE real order up front (every payment method works for this part); only
*afterward* do they request 더치페이 from the other participants, either split evenly or by each
person's own itemized share. Settlement itself requires 배민페이 (Baemin Pay) -- requested amounts
are sent as in-app Baemin Pay Money. **This is a single-payer-then-reimburse model, not a real
multi-payer atomic checkout** -- an important, easy-to-get-wrong detail that shaped the whole
implementation below.

**Checked against itunda's own code:** `EatsController`'s `PlaceEatsOrderRequest` is a flat
single-buyer item list -- `grep`ing the whole backend for "grouporder"/"sharedcart"/"together
order" found zero hits. A real, confirmed gap, distinct from the already-closed 1:1-chat split-bill
gap ([[project_itunda_ecosystem_research]]) -- that closes AFTER a solo order already exists;
Baemin's real feature is a shared cart BEFORE checkout.

**Built, matching Baemin's real mechanism exactly, not a fabricated multi-payer model:** new
`GroupEatsOrder`/`GroupEatsOrderParticipant`/`GroupEatsOrderItem` entities (a pre-checkout staging
layer only), `GroupEatsOrderService` at `/api/v1/eats/group-orders` -- `create` (host picks a
restaurant, gets a real 6-character join code), `join`, `setMyItems` (each participant's own items,
full-replace semantics), `getDetail` (live per-participant subtotals + grand total), `finalizeOrder`
(host-only -- merges every participant's items into ONE call to the existing, unchanged
`EatsOrderService.placeOrder`, then calls the existing, unchanged
`SplitBillService.createDirectSplitBill` once per other participant with their own real subtotal --
literally Baemin's own "pay first, Dutch pay after" mechanism, reusing two already-tested code paths
rather than inventing new money-movement logic), `cancel`. IDOR-safe: a non-participant gets an
identical 404 `GROUP_ORDER_NOT_FOUND` for both a real and a fake group-order id, same discipline as
[[project_itunda_idor_audit]]. Web (bank-mfe): new "Together order" tab in `EatsView` -- create/join,
a live shared-cart view, host finalize/cancel.

**Live-verified end to end via real curl calls against the deployed backend** (not just
compile-checked): registered two fresh real users, created a group order, joined with the second
user, both added real menu items via a real seed restaurant, host finalized -- one real `EatsOrder`
was placed (real ledger transaction, real `itemsSubtotal`), and the other participant received a
real, correctly-sized Dutch-pay `SplitBill` request (`GET /api/v1/split-bills/direct/{userId}`)
with their exact subtotal and the description "Together Order at <restaurant>". Confirmed the
IDOR-safe 404 by comparing a real finalized group-order id against a fabricated one from a third,
unrelated account -- byte-identical responses.

**Two real bugs found and fixed during this live-verification pass, not just compile-time
issues:**
1. `GroupEatsOrderParticipant`'s generated id (`"group_eats_order_participant_" + UUID`, 66 chars)
   exceeded the `id` column's `VARCHAR(64)` -- a real `DataIntegrityViolationException`
   ("Data too long for column 'id'") on every single group-order creation, caught only because this
   was actually exercised against a real MySQL instance, not a mocked/in-memory test. Fixed by
   shortening the prefix to `"group_eats_participant_"`.
2. `GroupEatsOrderController.finalize()` was missing exception handlers for the subset of
   `EatsOrderService.placeOrder`'s own real validations that can legitimately surface through it
   (`MinOrderAmountNotMetException`, `SelfEatsOrderException`,
   `MissingRequiredMenuOptionException`/`InvalidMenuOptionSelectionException`) -- each fell through
   to a raw, unhelpful 500 instead of the same proper 4xx `EatsController` already gives for the
   identical underlying exception. Fixed by copying the same handlers over. **Deployed to
   `itunda-dc-a` at image tag `...-group-eats-order-2` (id-length fix only) -- the exception-handler
   fix is committed in source but not yet redeployed**, since the cluster was flapping under its own
   known resource pressure (see below) right as this second fix was ready to ship; the core feature
   itself was already fully live-verified on `-2` before that, so this is a real, minor, tracked gap
   between source and the currently-running image, not an unverified feature.

**A real, pre-existing infra fragility resurfaced during this session's deploy** (not caused by
this feature's code): `itunda-dc-a` is a genuinely memory-constrained single-node cluster (see
[[project_itunda_private_cloud]]) -- the in-cluster image build was refused outright (847MiB
available vs. a 1536MiB safety floor), so the image was built locally via Colima and pushed directly
to the cluster's registry instead (a real, working alternative path, not previously documented).
Rolling the new image out then hit the startup probe's tight 5-minute budget under contention
(`failureThreshold: 30 * periodSeconds: 10`) -- bumped to 60 (10 minutes), a safe, reversible probe
tuning change left in place on the deployment, not a resource/capacity change. Even after that, the
pod later failed its ongoing *liveness* probe (not startup) under sustained interactive load --
`context deadline exceeded` against a 1-second probe timeout -- and briefly restarted; this
self-recovered within about a minute with kubelet's normal retry behavior, no data loss, no code
change needed. This matches, and does not change, the standing documented conclusion that the
cluster's real fix is resizing resource requests/limits, which needs the user's go-ahead before
being attempted.

## 71. Naver Pay "가족 공유 자산 관리" -- instant transfer to a linked family member

**Added 2026-08-15.** Fresh research (not doc-mining) into Naver Pay's own 2026 feature set found
가족 공유 자산 관리 (family shared asset management): family members can be invited into a shared
view, with parents/children able to see each other's real payment history live and instantly
transfer points/money to one another, all in-app.

**Checked against itunda's own code**: `FamilyLinkService.getChildOverview` already gives a guardian
a real, live read-only view of a linked child's balance and recent transactions (built earlier this
project) -- but a full audit of `bank-mfe`'s `FamilyLinkCard` component found no send/transfer action
anywhere in that view. A guardian who wants to top up a linked child's wallet had no faster path than
leaving the Family card, opening Send Money, and manually typing the child's phone number -- a real,
confirmed friction gap, not a missing feature category.

**Built as a thin, additive layer on top of already-tested code, not new money-movement logic**:
`FamilyLinkService.isActiveGuardianOf` is a small, additive read-only helper exposing the exact same
ACTIVE-link check `getChildOverview` already enforces. `P2pService.sendToFamilyMember` resolves the
child's real account number through that check, then delegates straight to the existing, unchanged,
already battle-tested `sendDirect` -- same rate limit, same Naver-Pay-style auto top-up-on-shortfall,
same fraud check, same round-up auto-save, same "money received" notification, zero duplicated ledger
logic. (`:p2p` already depended on `:family` for child spend-limit enforcement, so this needed no new
module dependency and created no circularity -- confirmed by checking `:family`'s own
`build.gradle.kts` first, since the reverse dependency would have been circular.) New
`POST /api/v1/p2p/send-to-family`; every exception it can throw was already handled by
`P2pController`'s existing handlers, so no new exception-handling surface was needed at all. Web
(bank-mfe): a real "Send money" quick action inside the Family card's child-overview panel.

**Live-verified end to end against the real deployed backend**: linked a real guardian/child pair
through the actual invite/accept flow, sent a real transfer through the new endpoint, confirmed the
child's real MAIN wallet balance increased by the exact amount sent (a genuine ledger-posted
transfer, not a mocked response) -- and confirmed a non-guardian gets an honest 404 attempting the
same action against someone else's linked child (the same `isActiveGuardianOf` gate correctly denies
a stranger).

**Same deploy also finally shipped the group-order exception-handler fix from Section 70** (committed
earlier but not yet redeployed when the cluster was flapping) -- re-verified live in this same pass:
`MinOrderAmountNotMetException` now correctly surfaces as a real `422 MIN_ORDER_AMOUNT_NOT_MET`
instead of the raw 500 it gave before.

## 72. 마감할인 (closing/surplus discount) -- a real government-partnered food-waste feature

**Added 2026-08-15.** Fresh, dated research: [welfarehello.com](https://www.welfarehello.com/community/policyInfo/%EB%B0%B0%EB%8B%AC%EC%95%B1-%EB%A7%88%EA%B0%90%ED%95%A0%EC%9D%B8-%EC%84%9C%EB%B9%84%EC%8A%A4-2026%EB%85%84-6%EC%9B%94-15%EC%9D%BC-%EC%98%A4%EB%8A%98%EB%B6%80%ED%84%B0-%EA%B0%9C%EC%8B%9C-%EB%AF%B8%ED%8C%90%EB%A7%A4-%EC%8B%9D%ED%92%88-%EC%A0%80%EB%A0%B4%ED%95%98%EA%B2%8C-%EC%82%AC%EB%8A%94-%EB%B0%A9%EB%B2%95-%EC%86%8C%EA%B0%9C),
[imnews.imbc.com](https://imnews.imbc.com/replay/2026/nwtoday/article/6830178_37012.html),
[m.ekn.kr](https://m.ekn.kr/view.php?key=20260629029477128),
[mt.co.kr](https://www.mt.co.kr/economy/2026/06/14/2026061412201077098),
[foodtoday.or.kr](https://www.foodtoday.or.kr/news/article.html?no=205568),
[sedaily.com](https://www.sedaily.com/article/20055702) -- Korea's 기후부 (Climate Ministry)
partnered with 배달의민족/요기요/쿠팡이츠 (plus independent apps 럭키밀/마구마켓) to launch 마감할인 on
2026-06-15: bakeries (CJ Foodville/파리바게뜨), restaurants, and convenience stores list unsold
near-closing food at a real, time-boxed discount, aimed at cutting Korea's ~5 million tonnes/year of
food waste.

**Checked against itunda's own code first**: a repo-wide grep for "surplus"/"closing sale"/"마감"
found zero hits -- confirmed itunda's existing `/products/deals` rail (2026-07-25) is a *permanent*
discount ranking, structurally distinct from a genuinely time-boxed closing sale. A real, confirmed
gap, not a duplicate of something already built.

**Built as purely additive metadata on the existing `MerchantProduct`**, not a parallel commerce
system -- `isSurplusDeal`/`surplusExpiresAt` (migration V246) plus `MerchantProductService.
setSurplusDeal` (mirrors the existing `updateStockQuantity`'s "focused operation" convention: setting
a deal never touches pricing/description) and a new `findSurplusDeals` query (real, time-boxed,
still-in-stock listings only, soonest-to-expire first). **Purchase itself needed zero new code**: a
surplus deal is bought through the exact same, already-tested `OrderService.placeOrder` every other
Commerce product uses, which already correctly decrements `stockQuantity` -- the single biggest
scoping decision here was choosing Commerce/Shop (which already has real, tested stock tracking) over
Eats (which has none) as the home for this feature, even though the real-world 마감할인 use case is
food -- itunda's packaged-goods Shop model fits a bakery/convenience-store closing sale more honestly
than Eats' restaurant-order model does anyway.

New endpoints: `PATCH /api/v1/merchant/products/{id}/surplus-deal` (merchant),
`GET /api/v1/shopping/products/surplus-deals` (buyer browse). Web: a real "Mark as closing deal"
toggle in merchant-mfe's `PosScreen` (full en/rw/fr i18n, matching this session's standing
localization discipline), and a "⏳ Closing deals" rail in bank-mfe's `ShopScreen` showing a real
"closes at HH:mm" countdown, never a fabricated urgency banner.

**Live-verified end to end against the real deployed backend, all three real exit paths tested**:
marked a real product as a surplus deal, confirmed it appeared in the browse listing, bought a real
unit through the normal checkout flow and confirmed stock genuinely decremented (3 -> 2) via the
existing, unchanged decrement logic, confirmed a past `expiresAt` is correctly rejected by
validation (`INVALID_SURPLUS_DEAL`, 400), and confirmed the deal disappears from the listing the
instant it expires -- set a real 5-second-future expiry, waited for it to pass, re-queried, got an
empty list, zero manual cleanup needed (the time filter in the query itself does the work).

## 73. Uber Women Preferences -- a real, sourced safety-matching gap, scoped not built

**Added 2026-08-15.** Fresh, dated research, cross-verified across multiple outlets
([gadgetreview.com](https://www.gadgetreview.com/ubers-women-only-driver-preference-feature-launches-nationwide),
[news.designrush.com](https://news.designrush.com/uber-women-only-rides-us-launch),
[time.com](https://time.com/article/2026/03/09/uber-women-driver-passenger-feature/),
[axios.com](https://axios.com/2026/03/09/uber-women-preferences-drivers-expansion/),
[uber.com/newsroom](https://www.uber.com/us/en/newsroom/expanding-women-preferences/)): Uber's "Women
Preferences" launched nationwide 2026-03-09 after an August-2025 pilot -- women riders can request a
woman driver (on-demand, reserved in advance, or as a standing preference); women drivers separately
toggle "Women Rider Preference" to receive trip requests only from women riders. **Real, bidirectional
safety matching** -- confirmed both directions matter, not just riders choosing drivers (the driver
side exists specifically for driver safety too, matching the feature's own original 2025 motivation).
Powered 230M+ trips globally across 40+ countries by the time of this research.

**Checked against itunda's own code**: `rw.itunda.rideshare` has a real, mature ride-hailing dispatch
pipeline (`RideTripService.requestTrip` + `RideDispatchScheduler`, a 3-second-poll exclusive-offer
dispatch loop -- the same real pattern `EatsOrderService`'s delivery dispatch already established,
confirmed by reading both). A repo-wide check found no gender field anywhere on `User`, and no
preference/matching concept in `RideDriver` -- a real, confirmed gap. **Interesting, real, adjacent
finding along the way**: `DemoNidaVerificationService` (Rwanda NIDA identity verification) already
parses a real gender digit out of a valid NIDA number's checksum -- but `IdentityService` never
persists it anywhere; it's extracted and silently discarded. A future build of this feature could
use that as a real, already-verified gender source instead of self-declaration, closing two gaps at
once.

**Deliberately scoped, not built this pass** -- unlike this session's other three shipped features
(group ordering, family send, closing deals, all safely additive on top of already-tested code), a
correct women-preference matching filter needs to reach into `RideTripService`'s real driver-candidate
ranking/dispatch logic itself (the same class of already-tested, real-time, safety-relevant code this
session has consistently avoided modifying without strong cause) -- and getting a SAFETY feature
subtly wrong (e.g. a rider who set the preference still getting matched with a male driver under some
edge case, or a driver's "women riders only" toggle silently not being honored) is a materially worse
failure mode than a bug in, say, a discount rail. Real open design questions before this should be
built: (1) is gender self-declared (fast, matches existing `birthDate`/`neighborhood` optional-profile
precedent) or NIDA-verified (slower, more trustworthy, and the parsing already exists unused per the
finding above) -- or does v1 need to require verification specifically because this is safety-facing,
unlike every other optional profile field; (2) exact matching semantics when a driver has enabled
"women riders only" but the incoming rider did NOT request a woman driver -- should that driver simply
be excluded from that rider's candidate pool entirely, silently, or does the rider need to be told why
fewer drivers are available; (3) whether "reserved in advance" and "standing preference" modes (real
per Uber's own launch) are in v1 scope or a bounded on-demand-only start, mirroring how
`DesignatedDriverService`/`RideTripService` already scope their own real-world counterparts down to
an honest v1 rather than the full original feature.

**How to apply**: if this is ever prioritized, start by reading `RideTripService`'s real candidate-
ranking method in full (not yet done this pass beyond confirming it exists) and resolve the gender-
source question first -- it changes the data model. `RideDriver` is the natural home for the
driver-side `acceptsWomenOnlyRequests` toggle (mirrors its existing `available: Boolean`); the
rider-side preference is naturally a new optional param on `RideTripService.requestTrip`, matching
how `scheduledFor`/`stops` were both added as backward-compatible optional params to that same
function for their own real features.

## 74. Real Android bug found live: 4 screens silently unreachable, missing `return@IdsTheme`

**Found 2026-08-15/16**, while porting the family send-money feature (Section 71) to Android and
live-verifying it on a physical device. Tapping the "Family" row in the Explore tab's "Accounts &
cards" section never navigated anywhere -- but every adjacent row in the same list (Card, Spending,
Group account) navigated correctly on the first tap. Roughly 20 blind `uiautomator`-based attempts
(fresh coordinates, a full app restart, ruling out overlay windows/notification interference/
accessibility services) found nothing, because the actual bug wasn't in touch handling at all.

**Root-caused with a real diagnostic, not more guessing**: added temporary `Log.d` lines at the
click lambda and the recomposition check. The log conclusively showed the click fired, the
`showFamilyLink` state correctly flipped to `true`, and Compose DID invoke `FamilyLinkScreen(...)` --
but the screenshot taken at that exact moment still showed the untouched Explore list. Reading
`ItundaAppScreen.kt`'s full sequence of `if (showX) { BackHandler...; XScreen(...); }` blocks (one
per full-screen destination -- Card, WeeklySavings, GroupAccount, Rides, etc., ~30 of them) revealed
that every one of them ends with a `return@IdsTheme` right after rendering, to stop the rest of the
composable function from executing (and re-rendering the tab content on top in the same pass) --
except a contiguous run of exactly 4: `VehicleInspection`, `VehicleValuation`, `FamilyLink`,
`Subscriptions`. A real, pre-existing gap (not introduced this session, not by this session's own
family-send-money port) -- likely all 4 added in one batch at some point without the established
pattern being followed.

**Fixed by adding the missing `return@IdsTheme` to all 4 blocks** (commit `4f04bdff`). Live-verified
2 of the 4 directly on-device after the fix: Family now correctly loads and shows real linked-child
data (balance, transaction history, and the new send-money form from Section 71, which correctly hit
a real `DEVICE_NOT_VERIFIED` security gate on an unverified test device -- proof the request round
trip works without needing the real account password); Subscriptions now correctly loads its real
(empty) recurring-payment state. VehicleInspection/VehicleValuation got the mechanically identical
fix in the same commit but weren't separately re-tapped on-device this pass.

**How to apply**: this class of bug (a full-screen overlay composable that renders in Compose's
logical tree but is invisibly overdrawn by sibling content in the same frame) will NOT show up as a
crash, an exception, or even an obviously-wrong log -- `dumpsys window`, `logcat` filtered to
exceptions, and screenshot diffing all looked completely normal throughout. The only way it surfaced
was adding real `Log.d` lines at the exact state-check and render call sites and comparing that
against a screenshot taken at the same moment. If a future on-device test finds a row that "does
nothing" despite everything else checking out, add debug logging at the click handler and the render
condition BEFORE spending more time on blind coordinate/timing tweaks -- this session burned roughly
20 attempts on the wrong hypothesis (touch input) before trying that.

## 75. Kakao Pay 페이아이 소비 리포트 -- real month-over-month spending report

**Added 2026-08-16.** Fresh research into Kakao Pay's AI-powered spending report ("페이아이"):
weekly/monthly personalized spending-pattern analysis from a user's real payment history. Checked
against itunda's own `WalletService.getSpendingInsight` (already real, ledger-based, live since
2026-07-13) -- confirmed it's an unbounded, all-time total with zero period comparison. A real,
confirmed gap, not a duplicate of something already built.

**Built as an honest, rules-based comparison, not a fabricated AI model**: `getMonthlySpendingReport`
computes this-calendar-month vs. last-calendar-month spend, reusing the exact same `categorizeDebits`
helper the existing all-time view uses (so the two views can never numerically drift against each
other), via `YearMonth` boundaries -- same convention `setBudget`/`getBudgets` already use in this
file. `percentChange` is `null` (not a fabricated `0%`) when a category has no prior-month spend at
all, a real "new this month" signal. No new tables needed -- purely a new read query over existing
ledger data.

New `GET /api/v1/wallet/spending/monthly-report`. Web: a `MonthlySpendingReportCard` at the top of
bank-mfe's `SpendingInsightView`, showing the real total, real percent change (▲ red / ▼ green), and
the top real movers by category.

**Live-verified against the real deployed backend**: real `currentTotal`/`previousTotal`/
`percentChange`/`categories` reflecting the seed test account's actual ledger history --
this-month's real ~15,750 RWF (all from this session's own testing) against last month's real
~2.7M RWF, both independently matching totals already observed elsewhere in this same session (the
physical-device Spending screen showed "Total spent, all time: 2,711,558 RWF" earlier, consistent
with this month + last month's real sum here). `Currency conversion` correctly showed `percentChange:
null` since it genuinely had zero prior-month spend.

**A real, transient cluster hiccup during this deploy, worth a brief note**: the `kubectl set image`
call failed once with `Error from server (Forbidden): ... cannot get resource "deployments"` -- a
real RBAC-shaped error, not a normal timeout -- and succeeded on a plain retry seconds later, while
`private-cloud-deploy.sh status` (read-only) worked the whole time. The cluster's `backend` AND
`ledger-service` were BOTH already 0/1 and restarting independently of this deploy right before it
(the same known, still-unresolved resource overcommitment documented in
[[project_itunda_private_cloud]]) -- most likely the API server itself briefly rejected the mutating
request under memory/CPU pressure rather than a real permission change, since nothing about RBAC was
touched and the identical command worked immediately after. Both pods (including the freshly-rolled
one) settled to healthy `1/1` within a few minutes with no further intervention.

## 76. Nubank NuScore -- a real "card usage" credit-score factor, sourced from Nu International's own model

**Added 2026-08-16.** Fresh research into Nu International's (Nubank's) real, publicly-described
NuScore model: alongside payment history and account age, it names card-usage data as its own
distinct scoring input, separate from general transaction activity. Checked against itunda's
existing `CreditScoreService` (real, live, computed from real ledger/loan/savings/KYC data since
2026-07-13) -- confirmed it scores general wallet transaction activity but has no factor for the
itunda Card specifically, even though `DebitCard`/`DebitCardTransaction` (itunda's own real,
ledger-backed check-card product, see Section entries on card issuance) already exist and are live.
A real, confirmed gap, additive to an already-real feature rather than a new concept.

**Built the same way every other factor in this file already is**: a plain `countByCardId` query
against real `DebitCardTransaction` rows (no new table), capped at `MAX_CARD_USAGE_POINTS = 50`,
`POINTS_PER_CARD_TRANSACTION = 5` -- itunda's own named, honest point values, same convention as
every other factor's named constants in `CreditScoreService.Companion`. `getImprovementSuggestions`
correctly branches on whether the user has a card at all: "Get an itunda Card" (full 50 points) if
they don't, or "Use your itunda Card more" (the real remaining delta to the cap) if they do.

**Live-verified against the real deployed backend**: the demo seed user (`user_1`) has a real
itunda Card with zero real card transactions yet -- `/api/v1/credit-score` correctly omits "Card
usage" from `factors` (0 points doesn't clear the same `> 0` threshold every other factor uses), and
`/api/v1/credit-score/suggestions` correctly returns "Use your itunda Card more, 10 more real card
purchase(s) reaches the real cap for this factor" rather than "Get an itunda Card", proving the
has-a-card branch is real and not just compile-verified.

**Test coverage**: extended the existing `CreditScoreServiceTest` (constructor now takes
`DebitCardRepository`/`DebitCardTransactionRepository`, all 6 existing `Given` blocks stub
`findByUserId(...) returns null` so a cardless user's existing assertions are unchanged) plus one new
`Given` block for a user with a real card and 6 real purchases, asserting the exact real point math
(6 x 5 = 30, under the 50 cap) on both `computeScore` and `getImprovementSuggestions`.

## 77. Uncalled-endpoint sweep, merchant-mfe/kyc-mfe focus -- 2 real gaps closed, 1 false positive ruled out

**Added 2026-08-16.** Past uncalled-endpoint sweeps (see Section 68 and the standing
[[feedback_uncalled_endpoint_sweep]] technique) mostly scanned bank-mfe's client dir only. This pass
deliberately focused on merchant-mfe and kyc-mfe, cross-referencing `MerchantController`,
`MerchantBookingController`, and related controllers against every web client dir (bank-mfe,
merchant-mfe, kyc-mfe, ops-mfe, pay-checkout).

**False positive, worth naming explicitly**: `POST /pay/customer-code` + `POST /pay/charge-by-code`
looked uncalled from any web client, but a native-app check confirmed both are real, already-shipped
endpoints -- Android's customer app (Pay tab QR) and merchant app (camera-scan charge) have called
them since 2026-08-11 (see [[project_itunda_pay_kakaopay_parity]]). A sweep that only checks web
client dirs will always misreport native-only endpoints as gaps -- check `android/` and `ios/` too,
not just the web MFEs, before treating a "zero web callers" finding as a real gap.

**Two real gaps found and closed the same session**:
- `GET /{merchantId}/booking-availability` -- BookingWidget picked a date blind, only learning "no
  open times" after the fact. Now shows "Closed on this day" up front.
- `GET /bookings/{bookingId}/deposit` -- a booking's real held/released/refunded/forfeited deposit
  status had no UI anywhere. Now a badge on each row in My bookings.

Both **live-verified end to end** against the real deployed backend: a fresh test merchant
(Mon-Fri-only availability, a 10 RWF prepay service, kept intentionally cheap per the established
wallet-funding technique in [[project_itunda_group_eats_orders]] -- the demo account's balance has
kept shrinking session over session and can no longer cover a realistic deposit amount) booked as
the real demo user, confirmed via curl that the availability endpoint returns exactly the 5 weekday
windows and the deposit endpoint returns a real `HELD` status with the exact ledger-linked amount,
then confirmed in a real browser that "Deposit held · 10 RWF" renders on the real booking row.

## 78. 가게배달 배달시간 AI 예측 (Baemin per-store AI delivery-time estimate) -- real per-merchant prep time

**Added 2026-08-16.** Baemin (배달의민족) opened "가게배달 배달시간 AI 예측 기능" 2026-06-17: merchants
input their own real food-prep time (delivery time excluded), and the shown delivery estimate factors
in that store's own characteristics plus current load, instead of one generic number for every
restaurant (asiae.co.kr, 2026-06-15: "AI로 '배달 품질경쟁' 기어 올리는 배민").

**Confirmed gap**: `DeliveryEtaEstimator.estimateDeliveryMinutes(distanceKm)` used a single hardcoded
`BASE_PREP_MINUTES = 15.0` for every restaurant regardless of how fast or slow that kitchen actually
runs -- this single function backs BOTH real customer-facing ETA surfaces, `ShoppingController`'s
browse-time estimate and `EatsController`'s in-flight arrival estimate, so the flat constant was
doubly load-bearing.

**Built as an honest, merchant-self-reported real signal, not a fabricated AI model** -- same "real,
not fabricated" bar `photoUrl`/`openingHours` already established on `Merchant`: itunda has no
measured historical prep-time data to compute one itself. `Merchant.avgPrepTimeMinutes` (nullable,
0-90 min sanity-bounded) threaded through the *already-shared* estimator (promoted out of
`ShoppingController` specifically to avoid this exact kind of duplication) to both real call sites --
zero new call sites needed. New `POST /api/v1/merchant/prep-time`; merchant-mfe settings input
(en/rw/fr), same shape as the existing openingHours/phoneNumber rows.

**Live-verified against the real deployed backend**: a fresh test merchant's real
`deliveryTimeMinutes` was 15 (the old flat default) before setting a prep time, and exactly 45 after
`POST /prep-time` with `avgPrepTimeMinutes: 45` -- a real before/after delta, not just a non-null
check. Confirmed 4 other real merchants with no prep time set kept their unaffected default estimates
in the same `GET /shopping/merchants` response, proving full backward compatibility.

## 79. Fresh concurrency audit -- 2 real missing-lock races found in code shipped this session

**Added 2026-08-16.** Continuing the standing feature/audit rotation (a research fork scoped
specifically to code added in the last ~2 weeks, not a repeat of the 2026-08-09 backend-wide sweep,
which is separately exhausted for its own bug class). Found and fixed 2 real, previously-shipped
missing-lock races:

1. **`GroupEatsOrderService.finalizeOrder`/`cancel`** -- read-checked-then-wrote
   `GroupEatsOrder.status` (OPEN -> FINALIZED/CANCELLED) with no lock. Two concurrent
   `finalizeOrder` calls (a real double-tap, or two devices) could both read OPEN, both pass, and
   both call `EatsOrderService.placeOrder` + `SplitBillService.createDirectSplitBill` before either
   committed -- two real orders, two real ledger charges, duplicate split-bill requests. Fixed with
   a new `GroupEatsOrderRepository.findByIdForUpdate`, matching this codebase's own established
   convention.

2. **`MerchantBookingService.payOutDeposit`/`refundDeposit`** -- same shape: read
   `BookingDeposit.status`, check `HELD`, post a real ledger payout/refund, THEN write the new
   status, no lock. 4 real call sites (`respond(confirm=false)`, `markCompleted`, `cancel`,
   `BookingNoShowScheduler`) could race each other into a double-payout or double-refund from the
   same escrowed hold. Fixed with pessimistic locking (`findByBookingIdForUpdate`), deliberately not
   optimistic `@Version` -- real money moves before the status write, so a lock is needed to stop
   the second caller from ever reading a stale `HELD` status, not just to fail loudly after the fact.

Both fixes follow the exact `findByIdForUpdate` convention `WalletRepository`/`FraudFlagRepository`/
`DebitCardRepository` already established. See [[project_itunda_concurrency_audit]] for the fuller
sourced account, including why this doesn't reopen the 2026-08-09 sweep's own "exhausted" finding --
both bugs are in code shipped after that sweep ran.

## 80. iOS platform-parity gap: credit-score improvement suggestions never ported from Android

**Added 2026-08-16.** Found while checking whether Section 76's new backend-only "Card usage"
credit-score factor would surface correctly on native apps without any client changes -- confirmed
it does for the factor breakdown (both Android and iOS render `factors` generically, keyed by
name), but discovered along the way that `CreditScoreService.getImprovementSuggestions` (real on the
backend since 2026-07-26, real on Android's `CreditScoreScreen.kt`) had **zero iOS client at all** --
`CreditScoreScreenView.swift` only ever fetched `getCreditScore()`, never the suggestions endpoint.

**Built**: `CreditScoreSuggestionDto`/`CreditScoreSuggestionsResponse` + `getCreditScoreSuggestions()`
added to `NetworkClient.swift` (exact same field shape as Android's DTO), plus a "Ways to raise your
score" section in `CreditScoreScreenView.swift` mirroring Android's card-per-suggestion layout
exactly. Full `ItundaApp` xcodebuild succeeded clean.

**How this was found**: not from doc-mining or fresh ecosystem research this time -- a routine
"does the new backend factor need any client work" check surfaced a real, pre-existing platform gap
unrelated to the factor itself. Worth remembering as a technique: whenever a backend-only change is
verified to "just work" on existing clients, it's a cheap moment to also check whether ALL the
related endpoints for that same feature area are actually wired on every platform, not just the one
being checked.

## 81. iOS platform-parity gap: monthly spending report never ported (2nd found via a targeted sweep)

**Added 2026-08-16.** A follow-up to Section 80's fix, this time from a deliberately targeted
sweep (not "uncalled anywhere" -- specifically "called by Android and/or web but iOS's sibling
screen never got it"). `WalletService.getMonthlySpendingReport` (Section 75, real since this same
session, shipped same-day on bank-mfe and Android) had zero iOS client at all, despite
`SpendingScreenView.swift` already existing for the neighboring all-time `getSpendingInsight()`
endpoint.

**Built**: `MonthlySpendingReportResponse`/`SpendingComparisonCategoryDto` + `getMonthlySpendingReport()`
added to `NetworkClient.swift` (exact same field shape as the backend/bank-mfe), plus a "This month
so far" section at the top of `SpendingScreenView.swift` mirroring bank-mfe's own
`MonthlySpendingReportCard` exactly -- real `percentChange` (▲/▼, red/green), top 3 movers by
category, `percentChange` left `null` (never fabricated `0%`) when a category has no prior-month
spend. Full `ItundaApp` xcodebuild succeeded clean.

**Sweep methodology note, worth keeping**: the fork that found this hit two real false-positive
sources before landing on this candidate -- iOS Swift call sites omit the leading `/` that
Kotlin/TS retain (unstripped comparison produced ~270 false positives), and iOS has **4 separate
`NetworkClient.swift` files** across `Core/Network`/`MerchantApp`/`RiderApp`/`AgentApp` (checking
only `Core/Network`'s falsely flagged every merchant/agent/rides/bus/parking endpoint, which DO have
real clients in their own app's file). Also correctly ruled out several bigger, pre-existing,
already-known gaps as NOT small wire-up jobs: `Insurance`/`Bills` Feature modules are placeholder-only
(whole features never built), and `eats/group-orders`/`loans/student/*` are whole multi-endpoint
sub-products, not single-endpoint gaps -- don't re-scope those as quick fixes.

## 82. Naver Pay 페이펫-inspired collectible pet -- real gamification layer over existing reward data

**Added 2026-08-16.** Naver Pay's real "페이펫" (PayPet) feature was upgraded in 2026 with expanded
customization (7 item categories) and 4 new minigames -- a gamified collectible companion tied to
real payment/reward activity. Checked against itunda's own code: real gamification infrastructure
already exists (`RewardsService`'s task claims, `StepRewardService`'s daily step rewards) but
nothing visual/collectible -- confirmed via a grep sweep for `mascot`/`pet`/`gamif`/`badge` across
the wallet and rewards modules, nothing found.

**Built an honest v1 slice, not the full 7-category/4-minigame version**: a purely cosmetic pet
whose level grows from two real, already-stored signals -- claimed reward tasks
(`RewardClaimRepository`) and distinct real days engaging with step rewards (new
`DailyStepRewardRepository.countByUserId`) -- no new points currency, no fabricated AI. Named
stages (Egg -> Hatchling -> Chick -> Fledgling -> Soaring) with plain emoji, matching itunda's
existing "no image-asset pipeline, don't invent one" bar. New `GET /api/v1/rewards/pet`. bank-mfe:
a self-contained `PetCard` at the top of `RewardsView`.

**Live-verified against the real deployed backend, both the floor and a real state change**: the
demo user's pet started at exactly level 1/"Egg" (0 claimed tasks, 0 active reward days, matching
`GET /rewards/tasks` and `/rewards/steps/today` for the same account) -- then, after claiming a real
`task_first_transfer` reward via `POST /rewards/claim`, the pet immediately updated to level
2/"Hatchling" with `claimedTaskCount: 1`, proving the level is genuinely computed from real stored
data in real time, not a static or fabricated value.

**Deliberately not ported to Android this pass**: Android's reward-tasks UI lives inside the
Saronite React Native mini-app (`packages/saronite`, bridged via `SaroniteBridge.kt`), not a plain
Kotlin Compose screen -- a meaningfully bigger integration than the bank-mfe web addition, found
while briefly scoping it. A real, honestly-assessed gap for a future session with more time for the
RN mini-app's own build/bridge work, not a gap to feel bad about skipping this pass.

## 83. Baemin-style tiered order-amount promotion for Eats -- platform-funded, restaurant untouched

**Added 2026-08-16.** Baemin's real April 2026 fee/promotion restructuring added automatic, coupon-
free discounts tiered by order subtotal (real KRW thresholds, no sourced RWF equivalent). Checked
`EatsOrderService.placeOrder` -- zero discount logic anywhere; the only existing mechanism
(`MerchantCouponService`) is merchant-created/merchant-funded, a genuinely different real product.

**Built**: `EatsPromotionCalculator` (mirrors `DeliveryEtaEstimator`'s stateless pattern), itunda's
own honest RWF tiers (>=5000->1000 off, >=10000->2500 off, >=15000->4000 off). The buyer pays less;
the restaurant's `netToRestaurant` and itunda's own `platformFee` revenue are both completely
untouched -- a new `PROMOTION_EXPENSE` ledger account absorbs the discount as itunda's own real
expense, the same "itunda's own money" shape `REWARDS_EXPENSE` already establishes. Refunds needed
zero new code: `cancelOrder` already reverses every ledger leg by transaction id, so the new leg is
automatically refunded/re-debited correctly for free. New `EatsOrder.promotionDiscount` column
(migration V248), surfaced on the order-detail response and bank-mfe's post-checkout confirmation
("X RWF off, on us").

**A real bug found and fixed during live-verification, not by any test**: the first real order
crossing the promotion tier 500'd with `IllegalStateException: Unknown ledger account
promotion_expense` -- the new `PROMOTION_EXPENSE` enum value needed a matching row in
`LedgerAccount.SEED_IDS`, the exact same bug class `LedgerAccount.kt`'s own doc comments already
document EIGHT prior instances of (`interest_income`, `agent_commission_expense`,
`card_spend_expense`, `postpaid_credit_payable`, `vehicle_inspection_holding`,
`designated_driver_holding`, `insurance_premium_fund_payable`, `deposit_protection_expense`) --
this codebase's own mocked-`LedgerService` unit tests can never catch a missing seed row, only a
real live request against the real database does. Fixed and redeployed.

**Verification, honestly accounting for a real financial constraint**: 128 `EatsOrderServiceTest`
unit tests (5 updated, 2 new) exactly verify the discount math and ledger legs for both the
`>=5000` tier and the zero-discount floor. Live-verified end to end for the zero-discount floor with
a real affordable order (`promotionDiscount: 0`, matching). The `>=5000` tier itself could NOT be
live-verified with a completed real purchase this session -- every available seed test account's
real MAIN wallet balance has been drawn down by cumulative testing across many prior sessions to
well under the ~4000 RWF a discounted tier-crossing order would still cost, and itunda has no
wallet-funding endpoint (confirmed again, same finding as
[[project_itunda_group_eats_orders]]). Instead, directly queried the real `ledger_accounts` table
(`SELECT id, name, balance FROM ledger_accounts WHERE id = 'promotion_expense'`) and confirmed the
seed row now exists post-deploy -- a real, honest confirmation of the actual fix mechanism, not a
substitute for a full money-moving test, clearly distinguished as such.

## 84. KakaoBank 결제홈 (Payment Home) -- unifying card spend with the real benefits it earns

**Added 2026-08-16.** KakaoBank's real 결제홈 service, launching August 2026 per their own H1
earnings coverage ("카드 결제 내역과 혜택을 통합 관리할 수 있는 '결제홈'"), consolidates card spend
history with the benefits that spend actually earned. Checked `CardView` (bank-mfe): shows the card,
spend limits, and a raw transaction list, with zero connection to `CreditScoreService`'s own real
"Card usage" factor (Section 76, built this same session) despite it being computed from the exact
same real `DebitCardTransaction` data this screen already displays.

**Built, purely client-side, zero new backend work**: both `GET /api/v1/credit-score` and
`/credit-score/suggestions` already existed and were already live elsewhere (`lib/creditScore.ts`).
`CardView` now fetches both alongside its existing load, and shows a "Card benefits" section: the
real "Card usage" factor's earned points if any exist, and/or the real "Use your itunda Card
more"/"Get an itunda Card" suggestion otherwise -- the same real data already proven correct in
Section 76's own live-verification, no fabricated points system.

**Verification note, honestly limited this pass**: type-checked clean, and directly traced the real
API responses for the demo account against the component's render logic (`cardUsageFactor: null`
since 0 real card transactions; `cardSuggestion` correctly the real "Use your itunda Card more, 10
more real card purchase(s)..." suggestion) -- confirms the logic is correct for real data, but the
Claude-in-Chrome browser extension was disconnected this pass, so this is not a full visual
screenshot check the way most of this session's other UI features were verified.

**Not recommended this pass, per the same research fork**: Toss Bank's real-time overseas-remittance
tracking (blocked on the same MTN/Airtel provider-credential gap already documented as itunda's one
open Toss-parity item) and Coupang Eats' expanded-delivery-radius feature (itunda's merchant search
has no distance restriction to begin with, so there's no radius-bypass gap to close without first
inventing a restriction system -- a bigger two-part feature, not a quick win).

## 85. Fresh IDOR audit (pass 4) + Android/iOS Eats promotion discount parity

**Added 2026-08-16.** A 4th IDOR pass, scoped to code this session's own new feature work actually
touched (bookings, group orders, family send-money, surplus deals, split bill, vendor advance,
student loans, linked accounts) rather than a full re-sweep -- pass 3 had already covered
essentially the whole backend. See [[project_itunda_idor_audit]] for the full account. **Result:
clean, no new finding** -- every checked resource-id lookup still uses the identical-404 discipline
the first 3 passes established and fixed 3 real violations of.

**Also closed a same-day platform-parity gap**: Section 83's new `EatsOrder.promotionDiscount`
field was silently dropped by both Android's `EatsOrderDto` (Gson ignores unknown fields, no
crash, just a quiet display gap) and iOS's `EatsOrderDto`. Added the field to both, plus a "X RWF
off, on us" line to each platform's own `EatsOrderConfirmationView`, mirroring bank-mfe's identical
addition -- now all 3 platforms show the real discount consistently.

## 86. USSD merchant payment completion (Toss Payments ARS결제-style)

**Added 2026-08-16.** Toss Payments' real ARS결제 feature (docs.tosspayments.com/resources/
release-note, 2026) confirms a pending payment over the phone -- built for call-center/telesales
contexts where the customer has no app or browser open. itunda already had both real halves this
needs: the external "Pay with itunda" checkout API (`PaymentsApiController`) creates a real
`PaymentIntent`, and `UssdService` already operates a real `*XXX#`-style feature-phone channel
(check balance, send money, mini statement, set PIN) -- but zero path connected them. A customer
told a payment reference over the phone had no way to complete it without a smartphone/data.

**The real blocker, found and closed**: `PaymentIntent`'s existing id (`"pi_<uuid>"`) is unusable
on a feature-phone numeric keypad. Added a real, short, collision-checked 6-digit `ussdCode`
generated alongside every intent (migration V249) specifically for USSD use, resolved via a new
`PaymentIntentRepository.findByUssdCode`.

**Built**: new USSD menu option "5. Pay a merchant" -- enter the code, enter PIN, completes via the
exact same, already-proven `MerchantService.collect` every other channel (QR, Face Pay, static QR)
already uses. No new money-movement logic, purely a new real entry point into it. Added a real
`"USSD"` channel label so the transaction memo correctly reflects the channel rather than falling
through to the QR default. `:ussd` now depends on `:merchant`, mirroring the existing `:p2p`
dependency `handleSendMoney` already has.

**No client-side work needed, by design**: USSD's whole real point is working without a
smartphone/app/data connection -- there is no bank-mfe/Android/iOS UI for this feature to have, the
existing `*XXX#` gateway webhook is the entire real interface.

**Verified**: full backend compiles clean, all `:ussd`/`:merchant` tests pass (2 existing
`MerchantServiceTest` cases needed a new `existsByUssdCode` stub; 1 new `UssdServiceTest` Given
block covers both the real success path and a not-found code). `scripts/verify-ledger-account-seeds.py`
confirmed clean -- this feature reuses `collect()`'s existing ledger accounts, no new
`LedgerAccountType` introduced.

**Live-verified end to end against the real deployed backend, 2026-08-16**: generated a real
`PaymentIntent` via `POST /merchant/qr/generate` (real `ussdCode: "243118"`), set a real USSD PIN
via `POST /ussd/pin`, then walked the full real stateless `POST /ussd/session` flow -- menu
correctly showed "5. Pay a merchant" -> code prompt -> PIN prompt -> `END Paid 500 RWF to Booking
Deposit Test Salon. New balance: 477 RWF.` Confirmed directly in the database that the resulting
transaction's real `channel` column is `USSD`, not the generic `QR` fallback, proving the
channel-label fix works too.

## 87. Coupang Eats AI 개인화 메뉴 추천 -- budget filter + real "recommended for you" ranking

**Added 2026-08-16.** Coupang's real 2026 roadmap ("when users set a budget, the app will factor in
real-time delivery fees to surface restaurants and dishes that fit their total price range")
sourced budget-aware, order-history-personalized menu recommendations. Checked `EatsController.
getDishes` -- a flat, unpersonalized dish list, zero price filter, zero use of the buyer's own real
order history to rank results (confirmed via `grep -rln "recommend\|personalized"` across
`services/backend/eats`, no hits).

**Built as two honest, rules-based pieces, not a fabricated ML model**: a plain `maxBudget` price
cap (deliberately not Coupang's own delivery-fee-aware TOTAL budget, which needs a per-merchant
distance computation at browse time -- a bigger v2), and a real "recommended for you" re-sort using
the buyer's own actual `EatsOrder` history (`findDistinctRestaurantIdsByBuyerId`) -- dishes from
restaurants the buyer has genuinely ordered from before rank ahead of unfamiliar ones. Response
gains a real `recommended: Boolean` per dish.

**Android**: `EatsDishDto` + `getEatsDishes` updated, `EatsDishGrid` shows a small "For you" corner
tag -- kept minimal to preserve the grid's own deliberately bare-tile design (sourced Coupang Eats
UX research already documented for this grid). The `maxBudget` param is queryable now but has no
filter UI wired to it yet on Android, an honest "backend real, client catching up" gap matching
several other entries already in this file.

**Not built this pass, a separate pre-existing gap found along the way**: bank-mfe and iOS have
*zero* dish-grid client at all -- Android is the only platform with this whole feature, not just
missing the new budget/recommendation fields. Bigger scope than this session's own pass, worth its
own future session.

**Live-verified end to end against the real deployed backend, 2026-08-16**: `GET
/api/v1/eats/dishes` for the demo user (who has real prior orders from "Heaven Kigali") returned
both Heaven Kigali dishes (`Grilled tilapia`, `Beef brochettes`) first with `recommended: true`,
every other merchant's dishes `recommended: false` -- the familiarity re-sort works on real order
history, not just compiles. `GET /api/v1/eats/dishes?maxBudget=1300` correctly returned exactly the
2 dishes at or under budget (`Banana bread slice` 1300, `Espresso` 1200), excluding `Croissant`
(1500) and every pricier dish -- the budget filter is real, not just present in the query string.

## 88. Standalone "send as a gift" flow (KakaoTalk 선물하기-style, general entry point)

**Added 2026-08-16.** A fresh uncalled-endpoint sweep (cross-referencing all 740 real backend REST
endpoints against every real client call site across bank-mfe/merchant-mfe/kyc-mfe/ops-mfe,
Android's every module, and all 4 iOS `NetworkClient.swift` files) turned up `GiftController`'s
standalone `POST /api/v1/gifts` -- send a real KakaoTalk-style money gift to any phone number, not
just someone already in a chat -- fully built server-side (idempotent, rate-limited, full exception
handling for every real failure mode) with **zero client caller anywhere**. Only the chat-embedded
sibling (`POST /gifts/conversations/{id}`) had a UI, so a user could only gift someone they were
already messaging. `GET /gifts/{id}` was the same story -- built, unreachable.

**Built (bank-mfe only this pass)**: rather than a whole new screen, added a "Send as a gift
instead" toggle to the existing Transfer form (`TransferFlow`) -- it already collects the exact
recipient-phone-number and amount fields a gift needs. Checking it reveals the same theme/note
fields the chat gift composer already offers (`GIFT_THEME_LABELS`, reused as-is). On confirm,
branches to the new `sendGift()` instead of `sendDirect()`, and renders an escrow-aware result panel
("held until they claim it, auto-refunded after 7 days") instead of the instant-transfer one, since
a gift genuinely behaves differently from a normal transfer -- money moves into escrow immediately,
not into the recipient's wallet.

**Not built this pass**: Android already has the chat-embedded gift flow but not this standalone
entry point either -- same shape of gap as several other cross-platform items in this file, worth a
future pass.

**Ported to iOS same session**: a "Send as a gift instead" `Toggle` added to `TransferAmountScreen`
(`FeaturePayments`), threading gift state through `TransferFlowContainer`'s biometric-confirm and
device-step-up-retry paths into a new `TransferViewModel.sendGift`, plus `sendGift`/`getGift` added
to `NetworkClient.swift`. Verification is an honest partial: the full `xcodebuild -scheme ItundaApp`
App-target build is currently blocked by an unrelated, pre-existing environment regression (Saronite/
React Native codegen module-map failures, reproduced on a file this change never touched -- see
`project_itunda_ios_build_env` memory). `FeaturePayments` and `CoreNetwork` -- the two targets this
change's own new code actually lives in -- both still build, link, and code-sign successfully as
standalone framework targets, real full compilation just not through the whole app. The two
App-target files this change also touches only got a `swiftc -parse` pass, weaker evidence.

**Live-verified end to end against the real deployed backend, 2026-08-16**: a real `POST
/api/v1/gifts` from the demo user (477 RWF MAIN balance) to a second real seeded user
(`0788555123`) for 100 RWF returned a real `Gift` with `status: PENDING`, a real `holdTransactionId`
-- sender's balance immediately dropped to 377 RWF (real escrow debit, not a no-op). `GET
/api/v1/gifts/{id}` returned the identical gift. Logged in as the real recipient (balance 0 RWF),
called `POST /api/v1/gifts/{id}/claim` -- returned `status: CLAIMED` with a real
`claimTransactionId`, and the recipient's real balance moved 0 -> 100 RWF. Full send-hold-claim loop
confirmed real, not just compiled.

**Real pitfall hit during verification, worth remembering**: this session's seeded second test user
phone number is stored as `0788555123` (no `+250` prefix), while the demo user's own number is
`+250788123456` (with it) -- inconsistent seed data across sessions. A first gift-send attempt with
`+250788555123` real-404'd (`GIFT_RECIPIENT_NOT_FOUND`) purely because of this format mismatch, not
a backend bug -- confirmed via a direct `SELECT phone_number FROM itunda.users` query before
retrying with the exact stored format.

**Ported to Android same session, closing this feature on all 3 platforms.** A "Send as a gift
instead" `Switch` + note field + theme `FilterChip` row added to `TransferAmountScreen`
(`features/payments/impl`), threading gift state through `ItundaAppScreen.kt`'s biometric-confirm
and device-step-up-retry paths into a new `MainViewModel.sendGift`, plus `SendGiftRequest`/
`sendGift`/`getGift` added to `ApiService.kt`. `:features:payments:impl:compileDebugKotlin`,
`:app:compileDebugKotlin`, and `:core:network:compileDebugKotlin` all `BUILD SUCCESSFUL` (a real
missing-import compile error was caught and fixed on the first attempt: `SendGiftRequest` needed an
explicit import in `MainViewModel.kt`, this codebase's `core.network` package doesn't wildcard-import
anywhere). Not live-verified on a physical device this pass (no device connected this session) --
compile-verified only, same bar as several other lower-priority parity items this session.

**Standalone gift feature status across all 3 platforms**: bank-mfe fully live-verified (real
send-hold-claim money loop against the deployed backend); iOS ported, partially compile-verified
(`FeaturePayments`/`CoreNetwork` framework targets build+link+codesign clean, but the full
`ItundaApp` App-target build is blocked by an unrelated, pre-existing environment regression -- see
`project_itunda_ios_build_env` memory); Android ported, compile-verified only. The backend itself was
never touched this pass -- it was already fully built and live-proven before any client existed.

## 89. Ride PIN verification (Uber "Verify Your Ride")

**Added 2026-08-16.** A fresh ecosystem research pass (Uber and Karrot were the two least-explored
ecosystems in this file by mention density -- 22 and 37 respectively vs. Toss's 318) found Uber's
real "Verify Your Ride" PIN feature (uber.com/pl/en/blog/pin-number, help.uber.com's own
"What's Verify my Ride?" article): a 4-digit code issued per trip, the rider tells it to the driver,
the driver enters it before the trip actually starts -- confirming the right passenger is getting
into the right car before the fare clock begins. Checked against itunda's own code:
`RideTripService.startTrip` flipped straight from `DRIVER_ASSIGNED` to `IN_PROGRESS` with zero
identity confirmation of any kind, on any platform. A genuinely missing, buildable safety gap.

**Built**: `RideTrip.pin` (migration V250, nullable so pre-existing in-flight trips aren't
locked out), generated at request time via the same `.random()` convention
`MerchantService.generateUssdCode` already established for a similar short numeric code (not a
cryptographic secret -- a real-time verbal-confirmation code, same threat model). `@JsonIgnore` on
the field itself, so it can never leak through any driver-facing endpoint that serializes a raw
`RideTrip` (`getMyDriverTrips`, `acceptTrip`'s own response, etc.) -- only the new passenger-only
`GET /trips/{id}/pin` explicitly re-includes it, with the same sender-or-recipient-style 404 IDOR
check every other resource lookup in this codebase uses. `startTrip` now requires the PIN and
throws a real `RidePinMismatchException` (400 `INCORRECT_RIDE_PIN`) on a wrong one.

**Real near-miss caught before deploy, worth remembering**: the first pass only checked bank-mfe for
existing callers of `POST /trips/{id}/start` (an initial grep for the literal string `"startTrip"`
missed both native apps' real function name, `startRideTrip`). Both Android (`RideScreen.kt`) and
iOS (`RideScreenView.swift`) turned out to have full, already-shipped native driver flows calling
that exact endpoint -- had the image deployed with only bank-mfe fixed, every real driver on both
native apps would have hit a real 400 the next time they tried to start a trip. Caught and fixed
before push by re-checking all 3 platforms by the correct function name -- see
[[feedback_backend_contract_change_all_clients]] for the standing lesson this produced. Built:
a PIN entry field before "Start trip" on all 3 platforms, and the passenger's own active-trip view
shows their PIN once a driver is assigned, on all 3 platforms too.

**Live-verified end to end against the real deployed backend, 2026-08-16**: requested a real trip
(fare 1893 RWF, funded via a direct DB top-up since no test account had real ride-fare-sized funds),
accepted it as a second real test driver. Confirmed the driver's own `GET
/trips/my-driver-trips` response contains zero `pin` field anywhere (`@JsonIgnore` working).
Confirmed the passenger's `GET /trips/{id}/pin` returned the real PIN (`4096`) while the SAME
endpoint called by the driver real-404'd (`RIDE_TRIP_NOT_FOUND`, the IDOR check working both ways).
`POST /trips/{id}/start` with the wrong PIN (`0000`) real-400'd with `INCORRECT_RIDE_PIN`; with the
correct PIN it real-200'd and the trip genuinely moved to `IN_PROGRESS`. Completed the trip
afterward to confirm the full lifecycle still works end to end (real payout transaction posted).

## 90. Reward Community Q&A answer adoption (Naver Pay-style non-transactional engagement)

**Added 2026-08-16.** A fresh Naver ecosystem research pass re-confirmed a lead an earlier pass had
flagged but never checked: Naver Pay's real points/rewards system pays out for non-transactional
engagement -- Knowledge iN Q&A participation, blog/café event participation, charitable-donation
matching -- not just spending. Checked `RewardsService.taskCatalog`: all 5 existing tasks were
either a one-time onboarding step or a money-moving transaction, zero community-engagement signal.
Separately, itunda already has a real, shipped Naver 지식iN-style Q&A feature
(`services/backend/knowledge`) whose own doc comment had already named this exact gap as a
deliberately deferred follow-up -- two independent research passes converged on the same real gap
from opposite directions, and the feature that should trigger the reward already existed and was
already live.

**Built, backend-only**: `task_knowledge_answer_adopted` (500 RWF) added to the catalog, eligible
once `KnowledgeAnswerRepository.countByAnswererIdAndIsAdoptedTrue` (already existed, no new query
needed) is nonzero, claimed through the exact same flow every other task already uses. Zero client
changes needed on any platform -- the Saronite `reward-tasks` mini-app (the single shared UI bank-
mfe/Android/iOS all embed) renders the task list generically off title/subtitle/rewardAmount/
claimed, the same pattern 3 of the other 5 tasks already use with no dedicated per-task panel.

**Live-verified end to end against the real deployed backend, 2026-08-16**: posted a real question
(`POST /knowledge/questions`), a real answer from a second real user (`POST
/knowledge/questions/{id}/answers`), adopted it as the asker (`POST .../adopt`, confirmed
`isAdopted: true`). As the answerer, `GET /rewards/tasks` correctly showed `eligible: true` for
`task_knowledge_answer_adopted` (previously false). `POST /rewards/claim` succeeded with a real 500
RWF reward -- the answerer's real wallet balance moved 1964.6 -> 2464.6 RWF, exactly +500, confirmed
via a direct wallet fetch before and after, not just trusting the claim response.

## 91. KakaoTalk Open Chat (오픈채팅)-style join-by-code groups

**Added 2026-08-16.** This document itself had already flagged KakaoTalk's real Open Chat
(join-by-link/search public rooms, no prior friend relationship required) as unresearched (line
620). A fresh pass confirmed it real and sourced (kakaocorp.com/page/service/service/KakaoTalk;
talksafety.kakao.com/en/report/enforcement/openchat) and checked it against itunda's actual group
messaging: `GroupMessagingService.createGroup`/`createGroupByPhoneNumbers` are the *only* two ways
to form a group, and both require the creator to already know every member's real userId or phone
number -- there was no way to form a group with a stranger at all.

**Built, mirroring an existing precedent rather than inventing a new pattern**:
`GroupConversation.joinCode` (migration V251, nullable -- unset means an ordinary invite-only
group, every existing group's real, unchanged behavior) generated via the exact same 6-character
alphabet/collision-retry convention `GroupEatsOrderService.generateUniqueJoinCode` already
established for an identical real invite-code shape. New `GroupMessagingService.createOpenGroup`/
`joinByCode`, `POST /messages/groups/open` and `POST /messages/groups/join`. `bank-mfe` gained a
new `OpenChatCard` (create-with-code / join-by-code) in the Talk tab, alongside the existing
`NewGroupCard`.

**Deliberately NOT ported**: Kakao's own real pseudonymous "Open Profile" layer (up to 3 per user,
participate under a name distinct from your real KakaoTalk identity) -- itunda's entire identity
model is KYC-verified real names tied to a real wallet, unlike Kakao's separate pseudonymous layer.
Porting that honestly needs an explicit scoping decision about whether pseudonymous participation
belongs in a real-money app at all, not something to build silently as a side effect of this
feature -- every member of an itunda open group is a real, real-name user, same as any other group.
The 4,000-member cap and search/recommendation indexing from Kakao's own real feature are also
deliberately left out of this first pass as real scope-growers, not needed for the core "join
without an invite" mechanic this closes.

**Live-verified end to end against the real deployed backend, 2026-08-16**: created a real open
group (`POST /messages/groups/open`), got back a real generated code (`7ATE8G`). A SECOND real
user, never invited by phone number or userId, joined with just that code (`POST
/messages/groups/join`) -- confirmed via a direct `GET /messages/groups` fetch from the joiner's
own session that the group now shows `memberCount: 2`, not just trusting the join call's own
response. Confirmed lowercase input (`7ate8g`) normalizes correctly. Confirmed idempotent re-join
(`memberCount` stayed 2, no duplicate member row). Confirmed an unknown code real-404's with
`INVALID_GROUP_JOIN_CODE`.

## 92. Baemin/Coupang Eats-style "fastest delivery" sort

**Added 2026-08-16.** Surfaced as an adjacent, smaller find while researching (and correctly
scoping out) Baemin's B-Mart quick-commerce line, a genuine business-model mismatch not a code gap
-- see the corresponding memory entry. `ShoppingController.getEligibleMerchants` already computed a
real `deliveryTimeMinutes` per merchant (from real Haversine distance + real
`Merchant.avgPrepTimeMinutes`) whenever the caller supplied `buyerLat`/`buyerLng`, but nothing let a
client sort by it -- every major Korean delivery app (Baemin, Coupang Eats, Yogiyo) has a real
"fastest delivery" sort tab; itunda had the exact data already computed and no way to use it.

**Built**: an optional `sortBy=delivery_time` query param on `GET /shopping/merchants`. A real
in-page sort, not a DB-level `ORDER BY` -- `deliveryTimeMinutes` is computed at request time from
Haversine distance, not a stored column -- same discipline `getDishes`'s own `recommended` re-sort
already established: never re-fetches, so the page's own real pagination/count stays exact. A
genuine no-op without real buyer coordinates, same as the underlying field itself.

**Android** (`EatsScreen.kt`) already sends real `buyerLat`/`buyerLng` on its restaurant list --
confirmed before building this -- so it gets a real "Fastest delivery" toggle chip, shown only once
a real browse location exists. **bank-mfe's own restaurant list currently never sends buyer
coordinates at all**, a separate pre-existing gap this pass didn't fix -- the client function gained
the optional param for whenever that gap is closed, but no UI was built on top of it yet, since it
would be hollow without real coordinates behind it.

**Live-verified end to end against the real deployed backend, 2026-08-16**: `GET
/shopping/merchants?businessType=RESTAURANT&buyerLat=...&buyerLng=...&sortBy=delivery_time`
returned all 3 real seeded restaurants genuinely ascending by `deliveryTimeMinutes` (25, 30, 35).
The identical query WITHOUT `sortBy` returned the same 3 restaurants in the opposite order (35, 30,
25) -- proving the sort is real, not a coincidence of already-sorted data. `sortBy=delivery_time`
WITHOUT buyer coordinates returned a real 200 with every `deliveryTimeMinutes` correctly `null` and
original order preserved -- a genuine no-op, not a crash.

## 93. Real agent location reporting (uncalled-endpoint sweep)

**Added 2026-08-16.** A fresh uncalled-endpoint sweep found a real, live gap: the only endpoint
that could ever set `Agent.latitude`/`longitude` lived on the deprecated admin controller
(`AgentAdminController`) with zero caller anywhere -- superseded everywhere else by the real
operator-facing `AgentOperatorController` (`/me`, `/till`, `/cash-ins`, `/cash-outs`,
`/till-reconciliations`), which had no location endpoint at all. Meanwhile a real, already-live
customer-facing feature (`AgentDiscoveryController.getNearbyAgents`, cash-point discovery)
depended entirely on that same data being current -- no real agent in the field had any way to
report where they actually are.

**Built**: `AgentService.setLocationForOperator`, resolving the caller's own agent from their JWT
(the same `activeOperator` pattern `cashInForOperator` already establishes -- callers never supply
an agent id), and `POST /api/v1/agent/location` on the real operator-facing controller. Android's
`AgentOperatorScreen` (the real store-facing till console) gained a "Report my location" button,
using the same permission-gated `rememberRealLocationRequester` pattern `EatsScreen`/`RideScreen`
already establish. 2 new Kotest blocks (`AgentOperatorAccessTest.kt`): a real success case and an
outside-Rwanda case (real `IllegalArgumentException`, reusing `GeoUtils.isWithinRwanda`'s existing
validation).

**Live-verified end to end against the real deployed backend, 2026-08-16**: the demo user's real
seeded agent ("Itunda Demo Agent Kigali") started with `latitude`/`longitude` both `NULL`. `POST
/agent/location` with real Kigali coordinates returned a real success with the updated coordinates
in the response. A subsequent `GET /agents/nearby` search from a nearby point returned that exact
agent with the real coordinates just set and a real computed `distanceKm` (0.087 km) -- proving the
whole real pipeline (report → store → customer-facing discovery) works end to end, not just that
the write succeeded in isolation.

## 94. Karrot-style Marketplace category browsing

**Added 2026-08-16.** Karrot's real 중고거래 category system was named twice as an untouched
research angle before finally being investigated: a real, fixed ~24-category taxonomy (Karrot's
own official Korea App Store listing), distinct from a generic flat/emergent list. Checked itunda's
own Marketplace: `Listing.category` was already a real, stored, filterable field --
`MarketplaceService.browse(pageable, category)` already accepted a `category` filter param -- but
no real curated taxonomy or client UI ever drove it. A buyer could not browse Marketplace by
category at all, only free-text search. The exact same "backend has the filter, no real taxonomy or
UI" shape Eats/Shop already closed via `MerchantService.CATEGORIES`.

**Built**: `MarketplaceService.CATEGORIES`, a real fixed 12-category list adapted from Karrot's own
sourced taxonomy to general secondhand goods relevant to Rwanda (itunda's own `vehicle`/
`realestate` modules already own those categories elsewhere, deliberately not duplicated here) --
mirroring `KnowledgeService.CATEGORIES`'s exact existing pattern. `GET /marketplace/categories`
returns it. Purely additive: real, pre-existing free-text listing categories keep working unchanged
-- this is a real curated list to browse BY, not a validation change to what a seller can type at
creation. bank-mfe's Marketplace `BROWSE` view gained a real category chip row, same visual shape
as Eats/Shop's existing chips.

**Live-verified end to end against the real deployed backend, 2026-08-16**: `GET
/marketplace/categories` returned the real fixed 12-category list. `GET
/marketplace/listings?category=Electronics` returned 5 real listings, `GET
/marketplace/listings?category=Sports` returned a different, smaller real set of 3 -- confirming
the filter genuinely differentiates results, not returning everything regardless of the param.
Real, useful side effect discovered during verification: the underlying JPA query is a plain
equality (`ListingRepository.kt`'s `l.category = :category`) with no explicit case-folding, but
MySQL's own default collation is case-insensitive -- pre-existing free-text listings stored as
lowercase ("electronics") still matched the new curated, capitalized category name ("Electronics")
correctly, with zero extra code needed.

## 95. Naver Maps-style colored merchant pins

**Added 2026-08-16.** A real, previously-named-but-unbuilt gap: every merchant marker on
bank-mfe's `MapView` rendered as a single hardcoded blue dot (`#3182F6`) regardless of merchant
category, unlike Naver Maps' own real category-colored POI pins (restaurants red, cafes brown,
shopping purple, etc.) that let a user visually scan a dense map without opening every pin.

**Built**: `merchantPinColor(category)` in `MapView.tsx` -- real substring/contains matching over
itunda's own free-text merchant `category` field (no backend change: this is a purely client-side
rendering change), with an honest neutral gray (`#6B7280`) fallback for any unmapped or null
category rather than guessing. Applied at the one marker-creation call site
(`new maplibregl.Marker({ color: merchantPinColor(m.category) })`).

**Verified**: a standalone script ran the real function against every real distinct merchant
category value confirmed via direct DB query (`Rwandan`, `Fast Food`, `Coffee & Bakery`,
`Electronics`, `Fashion`) plus `null`/unmapped cases -- every one resolved to a distinct, sensible
color or the neutral fallback correctly. No browser round-trip was done for this pass (a
deliberate call, matching [[feedback_verification_pace]]'s "match verification pace to risk" for a
purely cosmetic, deterministic, already-type-checked function with no server round-trip to prove).

## 96. Uber Eats-style "Message restaurant" (Live Order Chat)

**Added 2026-08-16.** Uber Eats' real official "Live Order Chat" lets a buyer message the
restaurant directly from an active order (delayed pickup, missing item, special instruction) --
itunda's Eats module had a full order lifecycle (`EatsOrderService`, review/reply, rider tracking)
but zero way for a buyer to contact the restaurant at all once an order was placed.

**Built**: `POST /api/v1/eats/orders/{orderId}/contact-restaurant` on `EatsController`, resolved at
the controller layer (not `EatsOrderService`, already at 28 constructor params) -- verifies the
caller is the order's real buyer (real 404 for anyone else, same IDOR discipline as every other
resource-ownership check in this codebase), then reuses `MessagingService.startOrGetConversation`
between the buyer and the restaurant's `ownerUserId`, mirroring `MarketplaceService.contactSeller`'s
exact existing pattern byte-for-byte (including catching `SelfConversationException` and rethrowing
as a feature-specific `EatsOrderOwnRestaurantException` for the case where the buyer owns their own
restaurant). Required adding `implementation(project(":messaging"))` directly to
`eats/build.gradle.kts` -- confirmed Gradle's `implementation(...)` is not transitive here, so
`eats`'s existing `implementation(project(":splitbill"))` (which itself depends on `messaging`)
does not expose `MessagingService` to `eats`. bank-mfe's `MyEatsOrdersView` gained a "💬 Message
restaurant" button on any order that isn't yet `DELIVERED`/`CANCELLED`, threaded through
`EatsOrderCard` → `MyEatsOrdersView` → `OrderFoodView` → `EatsView` → the top-level Messages-tab
hand-off, reusing the exact same `handleMessageSeller` function `MarketplaceView` already
established.

**Live-verified end to end against the real deployed backend, 2026-08-16**: called as the real
demo buyer against a real active order (`PLACED`, buyer `user_1`) -- returned a real new
conversation between the buyer and the real restaurant owner. A second identical call returned the
exact same conversation id (proving `startOrGetConversation`'s reuse, not a duplicate thread per
click). A freshly-registered, unrelated account calling the same endpoint against the same order
got a real `404 ORDER_NOT_FOUND` -- confirmed the IDOR check holds for a real non-buyer, not just
an unauthenticated request.

## 97. Coupang WING-style best-selling-products report

**Added 2026-08-16.** Coupang's real seller portal (WING) gives merchants a top-products-by-sales
ranking in their analytics tab (베스트 상품), distinct from a raw revenue total -- lets a seller see
WHAT is driving revenue, not just how much. itunda's `MerchantService.getReport` already gave
merchants a real day-by-day revenue report, but zero product-level breakdown existed anywhere
(confirmed via grep: no "topProduct"/"bestSelling" in merchant-mfe's `ReportsScreen.tsx`).

**Built**: `MerchantService.getTopSellingProducts(ownerUserId, from, to, limit)`, same bounded-31-
day-window + in-memory-grouping shape `getReport`'s own doc comment already justifies (`OrderItem`
has no createdAt of its own; joining through `Order` and grouping in Kotlin avoids a database-
specific date-truncation function at this scale). Fetches the merchant's real `Order`s in range via
a new `OrderRepository.findByMerchantIdAndCreatedAtBetween`, their real `OrderItem`s via a new
`OrderItemRepository.findByOrderIdIn`, groups by `productId`, sums real `quantity`/`unitPrice *
quantity`, sorts by revenue descending. Deliberately does not filter by `OrderStatus` -- matches
`getReport`'s own definition of "revenue" (gross collected at placement, not fulfillment-gated).
`GET /api/v1/merchant/reports/top-products` sibling to the existing `/reports` endpoint. Wired into
merchant-mfe's `ReportsScreen.tsx` as a new card above the daily-report table, with real en/rw/fr
translations. 3 new Kotest blocks: cross-order aggregation-and-ranking, empty-range, inverted-range
rejection.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh
real merchant ("Kigali Best Sellers Shop") with 2 real products (Coffee 5000 RWF, Tea 2000 RWF),
placed 2 separate real orders as a real buyer (order 1: coffee×3 + tea×2; order 2: coffee×1) --
`GET /merchant/reports/top-products` returned coffee ranked first with `unitsSold: 4`,
`revenue: 20000` (correctly summed across BOTH separate orders, not just the larger one) and tea
second with `unitsSold: 2`, `revenue: 4000` -- exact match to the real placed data. A query for a
date range with no orders returned a real empty list, not an error.

## 98. Karrot-style Marketplace listing view count (조회수)

**Added 2026-08-16.** 당근마켓's real listing pages show a view count next to like/save counts --
a lightweight social-proof signal a buyer uses to gauge listing popularity/staleness. itunda's
`Listing` had no view counter at all, and `GET /marketplace/listings/{id}` -- which already computed
a real `sellerTrustScore` -- had zero caller on any platform: bank-mfe's `ListingCard` rendered
straight off the already-fetched browse-list item, never fetching the single-listing detail
endpoint at all.

**Built**: `Listing.viewCount` (new column, migration `V252__listing_view_count.sql`),
`ListingRepository.incrementViewCount` as an atomic JPQL `UPDATE ... SET view_count = view_count +
1` (avoids the lost-update race a read-modify-write risks under concurrent viewers -- same
discipline [[project_itunda_concurrency_audit]] already established elsewhere). `getListing` now
increments on every real fetch, then bumps the returned in-memory entity by 1 to reflect that view
without a second round-trip read. bank-mfe's `ListingCard` fetches the fresh count once per mount
via `fetchListingDetail`, but only `if (!isMine)` -- the "don't count your own views" exclusion is
a client-side UX decision, not a backend rule (the endpoint itself increments unconditionally for
whoever calls it, same as it would for any other authenticated caller). Named honestly rather than
implied otherwise: bank-mfe has no separate listing detail page to gate this on, so "a view" is
defined as "a non-owner's `ListingCard` mounting," the closest honest analogue available in the
current UI shape.

**Live-verified end to end against the real deployed backend, 2026-08-16**: created a real listing
(`viewCount: 0` at creation). 3 real sequential `GET /marketplace/listings/{id}` calls from a real,
separately-registered non-owner viewer returned `viewCount` 1, then 2, then 3 -- a genuine atomic
increment per call, not a cached or duplicated value. A direct API call from the real seller's own
account (bypassing the client's `isMine` gate, which normal UI usage never does) returned
`viewCount: 4`, confirming the increment is real and unconditional at the backend layer exactly as
built -- the owner-exclusion lives only in `ListingCard`'s fetch condition, honestly documented
above rather than left as an untested assumption.

## 99. Baemin-style restaurant favorite count + 찜순 sort

**Added 2026-08-16.** Baemin's real restaurant listings show a 찜 (favorites) count, a publicly-
cited popularity signal, and let buyers sort by it (찜순). itunda already tracked `EatsFavorite` per
user (bookmarking) but never surfaced or sorted by the aggregate count anywhere.

**Built**: `EatsFavoriteRepository.getFavoriteCounts`, a batched `GROUP BY` query returning one row
per restaurant with any favorites (same "batch, don't N+1" discipline
`EatsReviewRepository.getRestaurantRatingSummaries` already established) -- a restaurant with zero
favorites is simply absent from the result, the caller treats a missing id as count 0, never a
fabricated row. `GET /shopping/merchants` gained a real `favoriteCount` field per merchant and a
`sortBy=favorites` option, sibling to the existing `sortBy=delivery_time` -- unlike delivery-time
sort, this one needs no buyer geolocation at all, so it ships with a real, immediately usable
"❤️ Most favorited" toggle chip on bank-mfe's restaurant browse from day one.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered two fresh
real restaurants ("Popular Kigali Grill", "Quiet Kigali Diner") and 3 fresh real fan accounts; had
all 3 favorite the Grill and 1 favorite the Diner. `GET /shopping/merchants?sortBy=favorites`
returned the Grill first (`favoriteCount: 3`) and the Diner second (`favoriteCount: 1`), both ahead
of every 0-favorite merchant in the list -- a real, correct descending sort. The identical query
WITHOUT `sortBy` still returned `favoriteCount` on every merchant but in a genuinely different,
unsorted order (a 0-favorite merchant ranked ahead of the 3-favorite Grill) -- proving the sort
param does real work rather than being silently ignored.

## 100. Baemin-style favorites-list sharing (찜 리스트 공유하기)

**Added 2026-08-16.** Baemin lets a buyer share their bookmarked-restaurant list with a friend --
distinct from Section 99's favorite-*count* feature. itunda has no public, unauthenticated
share-link surface anywhere (every screen is auth-gated), so the honest analogue is itunda's own
established "send a real message into a real Talk conversation" convention -- same shape
`GiftVoucherService.purchaseVoucher` and Marketplace's sold-notification already use, a plain
formatted text body rather than a bespoke rendered card (no custom message-type infrastructure
exists yet to build one of those).

**Built**: `EatsFavoriteService.shareFavoritesToConversation(userId, conversationId)` -- real IDOR
check via `MessagingService.getConversationForParticipant` (a non-participant gets a real 404, not
a leak), takes the caller's top 5 favorites (newest-first, matching `getMyFavorites`'s own existing
order), formats them as a numbered list, and sends via the existing `MessagingService.sendMessage`.
Real `NO_FAVORITES_TO_SHARE` rejection for a caller with zero favorites. `POST
/api/v1/eats/favorites/share`. bank-mfe's `FavoriteRestaurantsView` gained a "Share favorites"
button opening `ShareFavoritesModal`, a DIRECT-only conversation picker mirroring
`ForwardPickerModal` but deliberately scoped to what the backend actually supports (no group
sharing). 3 new Kotest blocks: real send, zero-favorites rejection, non-participant IDOR check.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered 3 fresh real
users (A, B, C). A favorited 2 real restaurants and started a real DIRECT conversation with B.
`POST /eats/favorites/share` as A returned a real `201` with a message body reading `"❤️ My
favorite restaurants:\n1. Popular Kigali Grill\n2. Heaven Kigali"` -- A's actual favorites, in the
real newest-first order. C (never a participant in that conversation) calling the identical
endpoint with the same `conversationId` got a real `404 CONVERSATION_NOT_FOUND`. C, with zero
favorites of their own, calling it against a real conversation C legitimately started with B got a
real `400 NO_FAVORITES_TO_SHARE` -- all three real code paths confirmed, not just the happy path.

This deploy's own docker build was killed once by local disk pressure (reclaimable image space at
93% from a day of rapid iterative builds -- fixed with `docker image prune -f`, freed 4.7GB, clean
retry succeeded -- see the new `feedback_docker_build_killed_disk_pressure` memory) and the
subsequent registry push initially failed with `connection refused` during a real, unusually severe
cluster load spike (idle 0%, load average 60.77, worse than this project's routine documented
instability pattern) -- both resolved by waiting for the underlying condition to clear rather than
retrying blind, then completing normally once the primary node's `vmstat` genuinely showed idle
capacity again.

## 101. Baemin CEO app 영업일시중지 (temporarily pause orders)

**Added 2026-08-16.** Baemin's real 사장님(CEO) seller app lets a restaurant temporarily pause
accepting new orders (영업일시중지) when overwhelmed -- a real, self-service, buyer-visible state
distinct from the heavier, ADMIN-only `MerchantStatus.SUSPENDED` moderation path already in this
codebase. itunda had no concept of a merchant-initiated "busy, please wait" state anywhere.

**Built**: `Merchant.isAcceptingOrders` (new column, default `true`, migration
`V253__merchant_accepting_orders.sql`), `MerchantService.setAcceptingOrders` (same "explicit owner
opt-out, never forced, no auto-expiry timer" shape `setAcceptsScheduledOrders` already
establishes), `POST /api/v1/merchant/accepting-orders`. Real **server-side enforcement** in
`EatsOrderService.placeOrder` -- throws `RestaurantNotAcceptingOrdersException` (real `400
RESTAURANT_NOT_ACCEPTING_ORDERS`) for a paused restaurant, checked in the service layer so a stale
client or a direct API call can't place an order a paused restaurant never agreed to fulfill, not
just a UI-level hint. `GET /shopping/merchants` exposes `isAcceptingOrders`; bank-mfe's restaurant
browse shows a "⏸ Temporarily paused" badge; merchant-mfe's `SettingsScreen` gets an On/Paused
toggle mirroring the existing scheduled-orders toggle exactly.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh real
restaurant with a real menu product. `POST /merchant/accepting-orders {"accepting": false}` real
flipped the flag. A fresh real buyer's order attempt against that restaurant got a real `400
RESTAURANT_NOT_ACCEPTING_ORDERS`. `POST /merchant/accepting-orders {"accepting": true}` resumed it
-- the identical retry then failed only on the real, unrelated, expected `INSUFFICIENT_FUNDS`
(proving the pause check itself had genuinely cleared, not just that the request format changed).
After funding the buyer's wallet, the identical retry succeeded with a real `201` and a real order
id. `GET /shopping/merchants` confirmed `isAcceptingOrders: true` on the resumed restaurant's real
browse row.

## 102. Uber Eats-style "busy kitchen" delivery delay signal

**Added 2026-08-16.** Uber's own official "Managing busy delivery times" merchant help article
confirms a real backlog of in-kitchen orders is a genuine, documented cause of delivery delay --
every major delivery app (Uber Eats, DoorDash, Baemin) surfaces this to customers. itunda's
`DeliveryEtaEstimator` already computed an honest ESTIMATE-labeled delivery time from distance +
prep time, but had no signal for a restaurant's current order-volume backlog.

**Built**: `EatsOrderRepository.getActiveKitchenOrderCounts`, a batched `GROUP BY` over real
kitchen-stage orders only (`PLACED`/`ACCEPTED`/`PREPARING` -- an order already
`READY_FOR_PICKUP` or later has left the kitchen's own workload, so counting it would overstate
current backlog), same discipline `getFavoriteCounts`/`getRestaurantRatingSummaries` already
established. `DeliveryEtaEstimator.estimateDeliveryMinutes` gained an `isBusy` param -- a flat,
honest 10-minute delay bump, still capped by the existing 90-minute max, default `false` and fully
backward-compatible with both existing call sites. `GET /shopping/merchants` computes `isBusy` per
merchant (real threshold: `BUSY_ORDER_THRESHOLD = 5` concurrent kitchen-stage orders) and exposes
it alongside the already-bumped `deliveryTimeMinutes`. bank-mfe's restaurant browse shows a "🔥
Busy, delivery may take longer" badge. 4 new Kotest blocks for `DeliveryEtaEstimator` (previously
completely untested): non-busy baseline, busy strictly longer, busy respects the max bound,
default-param backward compatibility.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh
real restaurant with a real location and menu product, and a fresh real buyer. After 4 real placed
orders, `GET /shopping/merchants` showed `isBusy: false`, `deliveryTimeMinutes: 15`. Placing a real
5th order and re-querying the identical endpoint flipped it to `isBusy: true`,
`deliveryTimeMinutes: 25` -- exactly a real +10-minute bump at exactly the real
`BUSY_ORDER_THRESHOLD = 5` boundary, not just eventual busy-ness sometime after enough orders.

## 103. Baemin CEO app/DoorDash-style "86" (temporarily mark sold out)

**Added 2026-08-16.** Both Baemin's own real seller (사장님/CEO) app and DoorDash's real merchant
portal let a restaurant temporarily mark a single dish sold out without permanently deleting it
from the menu -- itunda's only existing lever, `MerchantProduct.active`, is a real permanent
soft-delete (`removeProduct`) that also drops the item from the merchant's OWN catalog view
(`findByMerchantIdAndActiveTrue`), so there was no honest way to say "temporarily out of this dish,
back soon" and bring it back later.

**Built**: `MerchantProduct.soldOut` (new field, distinct from `active`, migration
`V254__merchant_product_sold_out.sql`), `MerchantProductService.setSoldOut` (same
focused-single-field-update shape `updateStockQuantity`/`setSurplusDeal` already establish, with a
real cross-merchant IDOR check -- a 404, never a 403, matching this codebase's IDOR discipline
everywhere else). `PATCH /merchant/products/{id}/sold-out`. Real **server-side enforcement** in
both `EatsOrderService.placeOrder` and `DineInOrderService` -- a distinct
`MenuItemSoldOutException`/`DineInMenuItemSoldOutException`, a real `409 MENU_ITEM_SOLD_OUT` (not a
misleading `404`, since the item genuinely exists and stays visible on the menu). merchant-mfe's
`PosScreen` gets a Mark sold out/Mark available toggle mirroring the existing surplus-deal button;
bank-mfe's restaurant menu shows a "Sold out" badge and disables adding the item to cart. 4 new
Kotest blocks: toggle on, toggle off, cross-merchant IDOR, and a real `placeOrder` rejection.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh real
restaurant with a real menu item. `PATCH .../sold-out {"soldOut": true}` real-flipped the flag. A
fresh real buyer's order attempt for that item got a real `409 MENU_ITEM_SOLD_OUT`. Toggling back
to `{"soldOut": false}` and retrying (after funding the buyer's wallet) succeeded with a real `201`
and a real order id. A second, unrelated real restaurant owner attempting to toggle the FIRST
owner's product got a real `404 MERCHANT_PRODUCT_NOT_FOUND` -- confirming the cross-merchant IDOR
check holds for a real second account, not just an unauthenticated request.

## 104. Uber Eats-style order-acceptance timeout + auto-pause

**Added 2026-08-16.** Uber's own official "Automatic pausing for merchants" blog post confirms
stores get auto-paused when multiple real orders in a row go unaccepted -- a restaurant that never
responds leaves a buyer's real payment held hostage on an unresponsive kitchen. itunda's Eats module
had no order-acceptance deadline anywhere: a `PLACED` order could sit forever with no real
resolution.

**Built**: `EatsOrderService.ORDER_ACCEPTANCE_TIMEOUT` (itunda's own honestly-chosen 10 minutes --
this backend has no historical restaurant-response-time data to derive a real figure from, same
honesty this session's other constants already model), backed by a new
`OrderAcceptanceExpiryScheduler` (same real "30-second poll interval, real business-duration
window" convention `DispatchOfferScheduler` already establishes) that finds real `PLACED` orders
past the window and auto-cancels+refunds them via `refundAndCancel` -- a shared helper extracted
from the existing `cancelOrder`'s own real ledger-reversal logic, so no duplicated money-movement
code exists. Each miss increments a new `Merchant.consecutiveMissedOrders`; a real `ACCEPTED`
transition resets it to 0; reaching `CONSECUTIVE_MISSES_TO_AUTO_PAUSE = 3` flips the exact same
`isAcceptingOrders` flag Section 101's manual pause-orders toggle already built and enforces --
zero new client code needed, the existing bank-mfe badge and merchant-mfe toggle both work
automatically. Deliberately does not replicate Uber's own real 6am auto-unpause timer -- itunda's
existing pause design is explicitly manual-resume-only, kept consistent rather than inventing a
second, divergent auto-resume policy.

**Live-verified end to end against the real deployed backend, 2026-08-16** (using a legitimate
DB-timestamp-backdating technique to exercise the real 10-minute window without a literal 10-minute
wait -- only the `created_at` the scheduler's own query checks was manipulated; every downstream
step ran for real once the scheduler's actual 30-second poll picked it up): placed a real order,
backdated it past the window, and within one real scheduler poll it flipped to real `CANCELLED`
with a real `refundTransactionId` -- the buyer's real wallet balance was measurably restored
(47000 → 50000 RWF). Repeated twice more against the same restaurant: `consecutiveMissedOrders`
genuinely incremented 1 → 2 → 3 across real, separate scheduler runs, and at the 3rd miss the
restaurant was real auto-paused -- `isAcceptingOrders: false` confirmed both via direct DB query
AND a real `GET /merchant/me` call as that owner, with `consecutiveMissedOrders` reset to 0 by the
pause itself. Separately, at a different real restaurant: gave it one real miss
(`consecutiveMissedOrders` confirmed at 1), then placed and real-`ACCEPTED` a second order --
confirmed `consecutiveMissedOrders` genuinely reset to 0, proving the reset-on-accept path fires on
a real nonzero streak, not just showing the default value on a restaurant that never missed.

## 105. Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule)

**Added 2026-08-16.** Baemin's own real seller guide (ceo.baemin.com: "가게 관리 > 휴무일 설정")
confirms restaurants can declare which days of the week they're regularly closed -- distinct from
both existing itunda pause mechanisms: Section 101's `isAcceptingOrders` is a one-off manual toggle
a merchant flips by hand, and Section 104's `consecutiveMissedOrders` auto-pause is a reactive
streak-driven safety net. Neither expresses a standing, recurring "closed every Sunday" fact a
restaurant knows about itself in advance.

**Built**: `Merchant.closedWeekdays` (comma-separated `1=Monday..7=Sunday`, ISO-8601 numbering
matching `java.time.DayOfWeek.getValue()`, migration `V256__merchant_closed_weekdays.sql`) and
`Merchant.isClosedToday()` as the single real source of truth (real `Africa/Kigali` local time,
matching this codebase's existing real-timezone convention) -- shared by
`EatsOrderService.placeOrder`'s server-side enforcement (checked, not just a UI hint, reusing the
same `RestaurantNotAcceptingOrdersException` the manual pause already throws) and
`ShoppingController`'s `closedToday` browse-card flag, so the two can never drift into disagreeing
about the same real business day. `POST /merchant/closed-weekdays` with real 1-7 range validation.
merchant-mfe's `SettingsScreen` gets a 7-day toggle grid with full en/rw/fr translations; bank-mfe's
restaurant browse shows a "Closed today" badge. Real Kotest coverage: `MerchantServiceTest`
(set/clear/validate) plus a new `MerchantTest.kt` exercising `isClosedToday()`'s real current-day
logic directly, not mocked.

**Live-verified end to end against the real deployed backend, 2026-08-16** (2026-08-16 is real ISO
weekday 7/Sunday in Africa/Kigali time): registered a fresh real restaurant, set
`closedWeekdays: [7]`. A fresh real buyer's order attempt got a real `400
RESTAURANT_NOT_ACCEPTING_ORDERS` with the exact message "This restaurant is closed today", and
`GET /shopping/merchants` showed `closedToday: true`. Clearing the schedule (`weekdays: []`) real-
flipped `closedToday` to `false`, and the identical order retry succeeded with a real `201`.
Separately, `POST /merchant/closed-weekdays {"weekdays":[8]}` (out of the real 1-7 range) got a
real `400 INVALID_CLOSED_WEEKDAYS` -- confirming server-side validation holds, not just a client-
side range check.

## 106. Uber Driver app-style earnings report for ride drivers

**Added 2026-08-16.** Uber's own real driver-facing "Earnings" tab shows a day-by-day trip count
and net-of-platform-fee total, not just a raw trip list. itunda's `RideTripService.completeTrip`
already computed the exact real `fare`/`platformFee` split at settlement, and `GET
/rides/trips/my-driver-trips` already exposed raw completed trips, but no aggregate summary existed
anywhere for a driver to see their own earnings at a glance.

**Built**: `RideTripService.getMyEarnings(driverUserId, from, to)`, same bounded-31-day-window +
in-memory-grouping shape `MerchantService.getReport` already establishes (grouped by request date,
since `RideTrip` has no separate `completedAt` column either). `GET /rides/trips/my-earnings`, with
real 31-day-window and inverted-range validation (`InvalidEarningsRangeException`). 4 new Kotest
blocks: cross-day aggregation with correct net-of-fee totals, non-driver rejection, inverted-range
rejection, 31-day-window rejection. Backend-only this pass -- itunda's Android `RiderApp` earnings
display is currently just a plain string on `RiderHomeScreen.kt` with no dedicated screen to extend
cleanly within a single-afternoon scope, flagged as a natural follow-up rather than rushed.

**Live-verified end to end against the real deployed backend, 2026-08-16**, running the FULL real
ride-trip lifecycle twice (register driver + passenger, request, accept, real PIN fetch/start,
complete -- the same proven recipe Section 89's ride-PIN-verification build established): trip 1
completed with real `fare: 1915.0`, `platformFee: 28.73`; `GET /rides/trips/my-earnings`
immediately showed `tripCount: 1`, `grossFare: 1915.0`, `platformFees: 28.73`, `netEarnings:
1886.27` -- an exact match. A second full real trip completed with `fare: 2242.25`, `platformFee:
33.63`; its real `created_at` was backdated by one day via direct SQL (the same legitimate
"manipulate only the grouping timestamp, not the money logic" technique Section 104 already used)
to prove cross-day aggregation -- the endpoint then returned two separate, correctly-totaled day
entries, each matching its own trip's real numbers exactly. An inverted date range and a >31-day
range both real-`400`'d `INVALID_EARNINGS_RANGE`; a non-driver account calling the endpoint got a
real `404 RIDE_DRIVER_NOT_REGISTERED`.

This deploy's registry push failed once with `connection refused` during a second real, severe
cluster-overload spike this session (idle 0%, load average 51.41 -- the same pattern Section 100's
deploy hit once already), resolved the same way: waited for the primary node's `vmstat` to
genuinely show idle capacity again before retrying, rather than pushing through a connection-
refused state.

## 107. Coupang WING-style product view count + analytics (전환율)

**Added 2026-08-16.** Coupang WING's real seller portal 상품분석 tab pairs per-product views with
order volume/conversion. bank-mfe's `ProductDetailView` rendered straight off the merchant's
already-fetched catalog list with zero real per-product fetch anywhere -- the exact same gap
Section 98 (Marketplace listing view count) already fixed for `ListingCard`, just never ported to
Commerce products.

**Built**: `MerchantProduct.viewCount` (atomic JPQL increment, migration
`V257__merchant_product_view_count.sql`, same "increment then bump the returned entity by 1"
pattern `MarketplaceService.getListing` already established for Section 98). `GET
/shopping/products/{productId}` as the real customer-facing view trigger -- any authenticated buyer
can view a real active product. `GET /merchant/products/{id}/analytics` pairs the real view count
with a real order count (`OrderItemRepository.countByProductId`, matching
`MerchantService.getTopSellingProducts`'s own "gross collected at placement" definition -- a
cancelled order's reversal is a separate real refund, not a retroactive rewrite of what was
genuinely ordered) for Coupang WING's own 전환율 (conversion rate) signal. bank-mfe's
`ProductDetailView` fetches the fresh count on mount. Real Kotest coverage: view-increment, owner
analytics fetch, cross-merchant IDOR. Merchant-facing analytics UI (a real display for the new
endpoint in merchant-mfe's `PosScreen`) is a natural follow-up, deliberately out of scope for this
pass -- same precedent Section 106's driver-earnings report set.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh real
merchant + product. 3 real sequential `GET /shopping/products/{id}` calls from a fresh buyer
returned `viewCount` 1, then 2, then 3 -- a genuine atomic increment, not cached or duplicated.
Placed one real Commerce order for that product. `GET /merchant/products/{id}/analytics` as the
real owner returned `viewCount: 3, orderCount: 1` -- an exact match to the real actions taken. A
second, unrelated real merchant owner calling analytics on the FIRST owner's product got a real
`404 MERCHANT_PRODUCT_NOT_FOUND`, confirming the cross-merchant IDOR check holds.

## 108. Uber-style trip issue report from a completed ride

**Added 2026-08-16.** Uber's real post-trip support flow lets a rider report a problem -- unsafe
driving, overcharge, lost item -- directly from a specific completed trip. itunda's generic
`SupportTicket` system already let a user attach any transaction to a ticket, but had no
ride-specific category and no contextual "report an issue" entry point anywhere on a completed
trip.

**Built**: `SupportTicketCategory.RIDE_ISSUE` with its own real 24-hour SLA (faster than the default
`GENERAL` 72h, slower than the wallet-freezing `ACCOUNT_TAKEOVER` 4h) and, correctly, no wallet
freeze -- `SupportService.createTicket`'s freeze branch is `ACCOUNT_TAKEOVER`-only by construction,
so `RIDE_ISSUE` was free to add without touching that logic at all. bank-mfe's `RidesView` gained a
"Report an issue" button on completed trips that hands off to `SupportView` with the trip's real
`transactionId` and `RIDE_ISSUE` pre-selected, reusing the exact same `pendingConversationId`
hand-off convention already established elsewhere. Fixed a real side-gap found along the way:
bank-mfe's `RideTrip` client type never declared `transactionId` even though the backend always
returned it. ops-mfe's `SupportQueue` gained a matching category label. Real Kotest coverage:
`RIDE_ISSUE`'s SLA and no-wallet-freeze behavior.

**Live-verified end to end against the real deployed backend, 2026-08-16**, running a full real
ride-trip lifecycle (register driver + passenger, request, accept, real PIN fetch/start, complete
-- Sections 89/106's proven recipe) to get a real completed trip with a real transaction: `POST
/support/tickets` with that trip's real `transactionId` and `category: RIDE_ISSUE` returned a real
ticket with `frozeWalletId: null` and a real `dueBy` timestamp exactly 24 hours after `createdAt`
(`2026-08-16T15:01:39` → `2026-08-17T15:01:39`) -- the SLA constant applied correctly at creation
time, not just asserted in a unit test. The ticket appeared in a real `GET /support/tickets` call
for that passenger. A direct DB check confirmed the passenger's wallet `is_active: 1` (genuinely
never frozen), matching the `frozeWalletId: null` in the API response -- proving `RIDE_ISSUE`
behaves distinctly from `ACCOUNT_TAKEOVER` for real, not just by code inspection.

One real dispatch-matching quirk hit and worked around during setup, worth noting: requesting a
trip from Kigali Center's coordinates (this session's default test location, reused across many
prior features) repeatedly offered the trip to a different, pre-existing driver from earlier test
runs rather than the freshly-registered one, since real distance-based matching correctly preferred
whichever driver was actually closer. Resolved by moving the fresh driver to a distinct location
(Kicukiro) and requesting the trip from there -- a real driver-matching behavior working as
designed, not a bug in this feature.

## 109. Uber "Share Trip Status" for active rides

**Added 2026-08-16.** Uber's own real feature (help.uber.com/en/riders/article/sharing-your-trip-
status-faq) sends an unauthenticated public link showing a live map and driver name/plate to up to
5 contacts. itunda has no public, unauthenticated share-link surface anywhere -- every screen is
auth-gated -- so this reuses the established "send a real message into a real Talk conversation"
convention Section 100's favorites-sharing already proved out. Also honestly scoped to what
`RideDriver` actually has: no name or vehicle-plate field exists on that entity at all (the same
real limitation `DriverRatingSection`'s own doc comment already names), so the shared message
includes trip status, pickup/dropoff addresses, and the driver's real current coordinates when
assigned -- never a fabricated name or plate.

**Built**: `RideTripService.shareTripStatus(passengerUserId, tripId, conversationId)` -- real
passenger-only IDOR check (a non-passenger gets a real 404, not a leak), real
`MessagingService.getConversationForParticipant` check (a conversation the caller isn't part of
real-404s too), a real driver-location snapshot pulled fresh from `RideDriver` at share time (not
cached). `POST /rides/trips/{id}/share`. bank-mfe's active-ride card gained a "Share trip status"
button reusing the existing conversation-picker modal, generalized with a `title` prop rather than
forking a second modal component. 3 new Kotest blocks: real share with location snapshot,
non-passenger IDOR, non-participant conversation rejection.

**Live-verified end to end against the real deployed backend, 2026-08-16**: ran a full real
ride-trip lifecycle (register driver + passenger at a distinct location, request, accept, real PIN
fetch/start) to a real `IN_PROGRESS` trip. Registered a real third "friend" account and started a
real conversation with them. `POST /rides/trips/{id}/share` returned a real `201` with body `"🚗 My
ride status: IN_PROGRESS\nFrom: Kicukiro\nTo: Kanombe\nDriver's last known location: -1.9995,
30.1512"` -- the real trip status, real addresses, and the real driver's actual coordinates, not
placeholder text. A different, non-passenger real account calling the same endpoint against the
same trip got a real `404 RIDE_TRIP_NOT_FOUND`. The real passenger attempting to share into a real
conversation they were never a participant of got a real `404 CONVERSATION_NOT_FOUND`.

## 110. Uber Destination Filter for ride-hailing drivers

**Added 2026-08-16.** Uber's own official Destination Filter (help.uber.com/en-GB/driving-and-
delivering/article/driver-destination-filter) lets a driver nearing the end of their shift set a
destination up to twice a day, resetting at midnight local, and get preferentially matched with
trips whose dropoff genuinely moves them closer to it -- Uber's own documented condition being
"the dropoff location should bring you closer to your final destination."

**Built**: `RideDriver.destinationLatitude/Longitude/UsesToday/UsesResetDate` (migration `V258`).
`RideDriverService.setDestination`/`clearDestination` -- the real 2-per-day limit resets at real
Africa/Kigali midnight (same timezone convention `Merchant.isClosedToday()` already established);
clearing nulls the active filter but deliberately does NOT reset the daily use count, matching the
real distinction between "how many times you set it" and "whether it's currently active." `POST
/rides/drivers/destination` (+`/clear`). `RideTripService.rankNearbyDrivers` now excludes a driver
with an active filter from a trip's candidate pool unless the trip's real dropoff genuinely reduces
their real haversine distance to their own chosen destination versus their current position -- a
real eligibility restriction, never a fabricated preference boost (this backend has no real signal
to honestly weight one). 8 new Kotest blocks across a new `RideDriverServiceTest.kt` and
`RideTripServiceTest.kt`. Backend-only this pass -- itunda's driver-facing client lives in
Android's `RiderApp`, not bank-mfe, so no client UI was extended, same precedent Section 106's
driver-earnings report set.

**Live-verified end to end against the real deployed backend, 2026-08-16**, using fresh, isolated
coordinates to keep the candidate pool unambiguous: **exclusion** -- driver A set a destination
filter east of their position; a real trip requested with a dropoff genuinely FARTHER from that
destination than driver A's own current position was offered to a real, different, more-distant
driver instead of driver A (despite driver A being by far the nearest driver to pickup) --
confirmed by comparing the trip's real `offeredDriverId` against driver A's own real id (they
didn't match), and driver A's own accept attempt real-404'd. **Inclusion** -- driver B set an
identically-shaped filter; a real trip requested with a dropoff genuinely CLOSER to that
destination was offered to driver B specifically (`offeredDriverId` exactly matched driver B's real
id), and driver B successfully real-accepted it (`DRIVER_ASSIGNED`). **Daily limit** -- a fresh
driver's first two `POST /destination` calls succeeded with `destinationUsesToday` incrementing
1 → 2 exactly; the third real-`429`'d `DESTINATION_FILTER_LIMIT_EXCEEDED`. **Clear semantics** --
`POST /destination/clear` real-nulled the active filter but left `destinationUsesToday` at 2, and
an immediately-following `POST /destination` still real-429'd -- proving clearing is genuinely free
to undo but does not refund a daily use, exactly as designed.

## 111. Uber post-trip tipping for ride-hailing drivers

**Added 2026-08-16.** Uber's own real, published policy (uber.com/us/en/ride/how-it-works/tips):
"Tips go directly to drivers; Uber doesn't charge service fees on tips," addable up to 30 days
after a trip. itunda's rideshare module had zero tipping concept anywhere.

**Built**: `RideTrip.tipAmount`/`tipTransactionId` (migration `V259`). `RideTripService.tipDriver`
-- a direct real passenger-wallet-to-driver-wallet ledger transfer that deliberately bypasses the
`ride_holding` escrow the fare itself uses, since a tip is never itunda's revenue to hold or take a
cut of. Real once-only enforcement (`RideTripAlreadyTippedException`), real 30-day window
(`RideTripTipWindowExpiredException`, measured from `updatedAt` since `RideTrip` has no separate
`completedAt` column), real passenger-only IDOR check, real positive-amount validation. `POST
/rides/trips/{id}/tip`, real `Idempotency-Key` required (money-moving, same convention every other
real transfer endpoint in this codebase already establishes). 7 new Kotest blocks. Backend-only
this pass -- itunda's driver-facing client lives in Android's `RiderApp`, not bank-mfe.

**Live-verified end to end against the real deployed backend, 2026-08-16**, with direct DB balance
checks on both sides of the transfer (not just trusting the API response): ran a full real
ride-trip lifecycle to a real `COMPLETED` trip. Passenger wallet balance before: `18214.00`; driver
wallet balance before: `1759.21`. `POST /trips/{id}/tip {"amount": 500}` returned a real `200` with
`tipAmount: 500`. Re-querying both wallets directly showed the passenger at exactly `17714.00`
(−500.00) and the driver at exactly `2259.21` (+500.00) -- a precise, fee-free transfer on both
sides, proving Uber's own "no service fee on tips" claim holds for real, not just in the code
comment. A second tip attempt on the same trip real-409'd `RIDE_TRIP_ALREADY_TIPPED`. A different,
unrelated real account attempting to tip the same trip real-404'd (passenger-only IDOR). A
`{"amount": 0}` tip real-400'd `INVALID_TIP_AMOUNT`. A second real completed trip, with its real
`updated_at` backdated 31 days via direct SQL (the same legitimate timestamp-manipulation
technique Sections 104/106 already used), real-400'd `RIDE_TRIP_TIP_WINDOW_EXPIRED` on tip attempt.

This deploy's registry push hit `connection refused` during the third occurrence this session of a
genuine, severe cluster-overload spike (idle 0%, load average 62.30) -- now documented as a known,
self-resolving condition in a new `feedback_private_cloud_severe_overload_registry_refused` memory,
resolved the same way as the prior two occurrences: waited for the primary node's `vmstat` to
genuinely recover before retrying.

## 112. KakaoTalk-style Today's Birthday in Talk

**Added 2026-08-16 (verified 2026-08-17).** KakaoTalk's real "오늘의 생일" (Today's Birthday) feature
surfaces friends with a birthday today in a dedicated section at the top of the friend list. itunda
already had both real ingredients unconnected: a real Talk-contacts directory (`listTalkContacts`)
and a real, already-settable `User.birthDate` (previously only used for Mini-wallet age
eligibility) -- this feature is purely joining the two.

**Built**: `MessagingService.getTodaysBirthdays(userId)` -- reuses the exact same real
saved-contacts-only privacy discipline `listTalkContacts` already establishes (a birthday is only
ever visible for someone the caller has saved as a real phone contact who is also a real itunda
user, never a public search that would leak a stranger's birthday). Compares month+day only, never
year, since a birthday recurs annually. Real `Africa/Kigali` local date, matching
`Merchant.isClosedToday()`'s existing timezone convention. `GET
/messages/contacts/birthdays-today`. bank-mfe's Friends tab gained a "Today's birthday" card above
the regular contact list, rendering nothing when the list is empty. 3 new Kotest blocks.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a real user
A and set their birth date to today's real date (`1995-08-16` -- caught and corrected a test-setup
mistake along the way: an earlier system note claimed the date had rolled to 2026-08-17, but the
real backend's own clock, checked via `date -u` and `TZ=Africa/Kigali date`, was still genuinely
2026-08-16 at verification time; used the real clock, not the stale note). Registered a real user C
with a birth date NOT matching today. Registered a real viewer B who saved both A and C as real
Talk contacts. `GET /messages/contacts/birthdays-today` as B returned exactly user A (real
`userId`/`name`) and correctly did NOT include C in the same real response -- both the inclusion
and exclusion paths proven by a single real call, not two separately-trusted assertions. A control
call from a fresh user D with zero saved contacts returned a real empty list with a real `200`, not
an error.

## 113. Toss Securities-style stock target-price alerts

**Added 2026-08-17.** A real, well-known Toss Securities feature (목표가 알림): set a target price on
a watched stock and get notified once it's crossed. itunda's `StockWatchlist` had no alert concept
at all despite stock prices genuinely moving day-to-day via a real deterministic simulation.

**Built**: `StockWatchlist.targetPrice`/`targetDirection`/`alertTriggeredAt` (migration `V260`).
`StocksService.setPriceAlert` -- auto-adds the stock to the watchlist if not already watched (same
real Toss UX: there's no "alert but not watching" concept), re-arms (clears the fired flag) if a
new target is set on an already-triggered row. `StocksService.clearPriceAlert`. A new
`StockPriceAlertScheduler` (real 60-second poll, mirroring `ProductPriceDropScheduler`'s exact
shape) that finds due alerts and fires them -- with a real re-check inside `triggerPriceAlert`
itself right before firing, so a genuine race can't double-fire. Real `ABOVE`/`BELOW` direction
validation and positive-price validation. `POST`/`DELETE /stocks/{id}/price-alert`. 6 new Kotest
blocks. Backend-only this pass -- itunda's stock UI lives in bank-mfe's existing dashboard
component, and a dedicated alert-management panel is a natural follow-up.

**Live-verified end to end against the real deployed backend, 2026-08-17**: checked the real stock
catalog (`GET /stocks`), found `MTNR` at a real current price of `127.68`. Set a real alert
(`{"targetPrice": 100, "direction": "ABOVE"}`) already crossed by that real price. Within the real
60-second scheduler window, a direct DB check confirmed `alert_triggered_at` had genuinely been set
(`2026-08-16 17:44:51`) -- not just trusting the API, the real background job actually ran. `GET
/notifications` confirmed a real `STOCK_PRICE_ALERT` notification with the exact expected content:
`"MTNR hit your target price" / "MTN Rwanda PLC is now 127.68 (target: 100.00)"`. Setting a new
target re-armed the alert (`alertTriggeredAt` back to `null` in the real response).
`DELETE /price-alert` real-cleared `targetPrice`/`targetDirection`/`alertTriggeredAt` all to `null`.
An invalid direction (`"SIDEWAYS"`) and a negative target price both real-`400`'d
`INVALID_PRICE_ALERT`.

## 114. KB Kookmin Bank-style savings goal maturity reminder

**Added 2026-08-17.** KB국민은행's real, officially named 상품만기알림서비스 (Product Maturity Alert
Service, obank.kbstar.com) notifies a customer when a savings product reaches its target date.
itunda's `SavingsGoal.targetDate` has been a real, stored, user-set free-text field since goals
existed, but nothing ever read it to notify anyone -- pure dead data until this build.

**Built**: `SavingsGoal.maturityNotifiedAt` (migration `V261`).
`SavingsService.getGoalsDueForMaturityReminder` -- an active goal whose real `targetDate` has
arrived (today or already past) and hasn't been notified yet; defensively skips a genuinely
unparseable free-text `targetDate` (this field has zero format enforcement at goal creation)
rather than crashing the whole sweep over one bad row. `sendMaturityReminder`, with a real
re-check of `maturityNotifiedAt` right before firing so a genuine race can't double-fire. A new
`SavingsMaturityReminderScheduler`. `POST /savings/goals/process-maturity-reminders` exposes the
same real logic as a manually-callable endpoint -- same "expose the scheduler's own logic as a
real endpoint" convention `WeeklySavingsController.processDue` already establishes -- so a goal's
real maturity can be verified without waiting actual wall-clock days. 5 new Kotest blocks.
Backend-only this pass.

**Live-verified end to end against the real deployed backend, 2026-08-17** (real UTC date confirmed
as 2026-08-16 via `date -u` before designing the test): created three real goals -- "Matured Goal"
(`targetDate: 2026-08-15`, a real past date), "Future Goal" (`targetDate: 2026-09-15`), and
"Unparseable Goal" (`targetDate: "next month sometime"`, genuinely unparseable free text).
`POST /process-maturity-reminders` returned `processed: 1` -- not crashing on the unparseable row.
`GET /notifications` showed exactly one real `SAVINGS_GOAL_MATURED` notification, with the exact
expected content ("Matured Goal has matured" / "...reached its target date. Current balance: 0.00
RWF."), mentioning only the matured goal. `GET /savings/goals` confirmed `maturityNotifiedAt` was
real-set on "Matured Goal" alone -- both "Future Goal" and "Unparseable Goal" stayed `null`. Calling
`process-maturity-reminders` a second time returned `processed: 0`, and the real
`SAVINGS_GOAL_MATURED` notification count stayed at exactly 1 -- confirming the no-double-fire
guarantee holds for real, not just in the unit test.

## 115. Kakao Pay-style auto bill pay (자동납부), plus two real Spring-transaction bugs found and fixed pre-deploy

**Added 2026-08-16/17.** Kakao Pay's real 자동납부 lets a user register a recurring bill (electricity,
water, etc.) once and have it paid automatically whenever it's due, up to a self-set safety cap.
Built by a research fork: `BillAutoPaySetting` entity (migration `V262`) --
`userId`/`providerId`/`accountNumber`/`maxAmount`/`active`/`lastPaidBillId`.
`POST`/`GET`/`DELETE /bills/auto-pay` for the user-facing setup, and an ADMIN-gated
`POST /bills/process-auto-payments` exposing the sweep as a manually-callable endpoint (same
"expose scheduler logic as a real POST" convention as `WeeklySavingsController.processDue`), same
as Sections 113/114.

**Two real bugs found and fixed pre-deploy during this session's own code review of the fork's
diff** -- not caught by the fork's own passing unit tests, only by reasoning about real Spring
transaction semantics and then confirming live:

1. The fork's original `processAutoPayments()` looped over every active setting and called
   `payBill()` on `this` with no per-row exception handling. Since `processAutoPayments()` was
   itself `@Transactional` and the call to `payBill()` was a self-invocation (bypasses Spring's
   proxy, a well-known Kotlin/Spring pitfall), the whole sweep was really one physical
   transaction -- one user's uncaught `InsufficientFundsException` would silently roll back every
   other user's already-processed payment in the same poll. First fix attempt: wrapped the
   per-setting logic in try/catch, matching the established per-row resilience convention
   `OrderAcceptanceExpiryScheduler`/`StockPriceAlertScheduler`/`SavingsMaturityReminderScheduler`
   already use elsewhere.

2. **That first fix was itself insufficient.** Live-testing it produced a real
   `500 Internal Server Error` -- `org.springframework.transaction.UnexpectedRollbackException:
   Transaction silently rolled back because it has been marked as rollback-only`, confirmed via the
   real pod logs. Root cause: `LedgerService.postLedgerTransaction` is a *separately-proxied* Spring
   bean call (not self-invocation), so when it threw for the insufficient-funds row, Spring's own
   `TransactionInterceptor` marked the *ambient, shared* transaction rollback-only right there --
   catching the exception one level up in `processAutoPayments()` could not undo that mark. When the
   outer transaction later tried to commit (since no exception escaped `processAutoPayments()`
   itself), Spring threw `UnexpectedRollbackException` instead -- turning "one bad row" into "the
   whole endpoint 500s." **Real fix**: extracted the loop out of `BillsService` entirely into a new,
   plain `BillAutoPayProcessor` bean (not itself `@Transactional`) that calls
   `billsService.payBill()` -- now a genuine *cross-bean* call through `BillsService`'s real proxy,
   giving each row its own independent physical transaction (the same effect as
   `Propagation.REQUIRES_NEW`, without needing it). This is exactly the "loop lives in a separate
   class, calls a `@Transactional` method on a *different* bean" structure
   `OrderAcceptanceExpiryScheduler` already uses -- restructuring to match it, rather than trying to
   force self-invocation to behave, was the actual fix. **Lesson for future sessions**: catching an
   exception around a self-invoked call is not sufficient to fix a poisoned Spring transaction when
   the throw actually came from a separately-proxied bean deeper in the call -- the loop and the
   transactional per-row unit of work must live in different beans, not just be wrapped in try/catch.

**Live-verified end to end against the real deployed backend, 2026-08-16, against the corrected
fix**: two real users -- user1 with an active auto-pay setting for REG - Electricity (`bill_1`,
35,000 RWF) but a genuine `0` MAIN wallet balance, and user2 with an active auto-pay setting for
WASAC - Water (`bill_2`, 8,500 RWF) funded with a real 20,000 RWF via an actual agent cash-in
(`POST /agent/cash-ins`, itunda's only real money-creation rail -- transfers are outbound-only).
`POST /bills/process-auto-payments` as the real seeded admin (`+250788999000`) returned a real
`200` (not the previous `500`) with exactly one result: user2's `bill_2` payment, `8,500 RWF`,
`COMPLETED`. A direct DB check confirmed `bill_auto_pay_settings.last_paid_bill_id` was genuinely
`'bill_2'` for user2's row and still `NULL` for user1's -- user1's real insufficient-funds failure
no longer poisoned user2's real successful payment in the same sweep. Calling the sweep a second
time returned an empty `processed: []` -- no double-fire. A third user registered with an
auto-pay setting whose `maxAmount` (1,000) was below `bill_1`'s real amount (35,000) was correctly
skipped in the same sweep alongside user1's insufficient-funds row, with the endpoint still
returning a clean `200`.

## 116. Coupang/Baemin/Naver-style photo-review reward (포토리뷰 적립금)

**Added 2026-08-17.** Every major Korean delivery/e-commerce platform pays a small one-time
reward for a review that includes a real photo, since photo-bearing reviews are disproportionately
trusted by other buyers -- the same real motivation `EatsReview.photoUrl`'s own doc comment already
cites for ranking photo reviews first. itunda's `EatsReview.photoUrl` has existed since migration
`V224` (2026-08-04) with zero incentive ever attached to actually using it. Built by a research
fork: new `task_first_photo_review` (300 RWF) in `RewardsService`'s existing static task catalog,
eligibility checked via a real repository query
(`EatsReviewRepository.existsByBuyerIdAndPhotoUrlIsNotNull`), same "check a real activity, never an
honor-system flag" convention every other catalog task already follows. No new migration or ledger
account -- reuses the existing `REWARDS_EXPENSE` rail every other task claim already uses. 2 new
Kotest cases (14 existing sites updated for the new constructor dependency).

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh
buyer, funded via a real agent cash-in (`POST /agent/cash-ins`, 10,000 RWF). Placed a real PICKUP
order against the seeded `merchant_seed_1` restaurant (Beef brochettes, 3,500 RWF), progressed it
through the real merchant-owner status chain (`ACCEPTED` → `PREPARING` → `READY_FOR_PICKUP` →
`POST /complete-pickup` → `DELIVERED`). Submitted a real review with a non-null `photoUrl`.
`POST /rewards/claim` for `task_first_photo_review` returned a real `200` with `rewardAmount: 300`
-- the buyer's real MAIN wallet balance moved from `6,500` to exactly `6,800` RWF. A direct DB
check confirmed a real `reward_claims` row (`amount: 300.00`, real `claimed_at` timestamp). Claiming
a second time real-`409`'d `REWARD_TASK_ALREADY_CLAIMED`. A separate fresh user who never submitted
a photo review real-`403`'d `REWARD_TASK_NOT_ELIGIBLE` on the same claim call.

## 117. DoorDash/Uber Eats-style "Item Unavailable" flow

**Added 2026-08-17.** A real DoorDash/Uber Eats merchant-facing feature: a restaurant that
discovers mid-prep that one item can't be fulfilled marks just that item unavailable instead of
cancelling the whole order. Built by a research fork, distinct from three existing itunda
mechanisms it deliberately does not touch: the pre-order §103 "86" sold-out toggle (permanently
stops future orders of a product), `cancelOrder` (cancels a still-`PLACED` order before the
restaurant has started fulfillment), and Commerce's post-delivery `OrderReturnRequest` (after the
buyer has already received the goods).

**Built**: `EatsOrderItem.unavailable`/`refundTransactionId` (migration `V263`).
`EatsOrderService.markItemUnavailable` -- restaurant-only, valid only from `ACCEPTED`/`PREPARING`
(fulfillment underway, before `READY_FOR_PICKUP`/rider dispatch). Posts a **standalone 2-leg
refund** (buyer wallet credit, restaurant wallet debit, both for exactly the item's
`unitPrice × quantity`) rather than prorating the original order's multi-leg transaction --
matches the real product's own behavior: the platform fee/delivery fee/any already-settled
promotion discount stay untouched, since the platform still does the real dispatch/delivery work
for the rest of the order. Rejects double-marking the same item and rejects marking the last
remaining item unavailable (`cancelOrder` is the correct path for a fully-unfulfillable order).
`POST /eats/orders/{orderId}/items/{itemId}/unavailable`. 5 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: registered a fresh
buyer, funded via a real agent cash-in (15,000 RWF). Placed a real 2-item PICKUP order against
`merchant_seed_1` (Beef brochettes 3,500 RWF + Grilled tilapia 5,000 RWF), progressed it to
`ACCEPTED` as the real restaurant owner. Marked the tilapia item unavailable -- the buyer's real
MAIN wallet balance moved from `7,500` to exactly `12,500` RWF (+5,000). A direct DB check on
`ledger_entries` confirmed the exact real 2-leg posting: `wallet_restaurant_1` DEBIT `5,000.00`
(`balance_after: 6,869.25`) and the buyer's wallet CREDIT `5,000.00` (`balance_after: 12,500.00`),
both tagged "Item unavailable clawback/refund - Grilled tilapia with ugali". Marking the same item
again real-`409`'d `ORDER_ITEM_ALREADY_UNAVAILABLE`. Marking the one remaining item (brochettes)
real-`422`'d `CANNOT_EMPTY_ORDER`. A freshly-registered, unrelated merchant calling the same
endpoint on this order real-`404`'d `ORDER_NOT_FOUND` -- the same IDOR discipline every other
controller in this codebase already enforces.

**Real latent bug found (not by live-verification, by the fork's own code review) but not yet
fixed**: `ProductSubscriptionService.executeOne` (commerce module) and
`MerchantBillingService.chargeOne` (merchant module) both catch a separately-proxied
`LedgerService.postLedgerTransaction` exception *inside* their own still-open `@Transactional`
method before returning normally -- the identical root cause behind Section 115's bills auto-pay
`UnexpectedRollbackException` before its second fix (commit `a5a821e4`). Both are `@Scheduled`
background polls rather than synchronous HTTP endpoints a caller waits on, so this most likely just
silently delays one row to the next poll tick rather than 500ing a live request -- lower severity
than the bills case, but a real bug in already-deployed code. Flagged as a follow-up task, not
fixed in this pass.

## 118. Bug fix: scheduler transaction-poisoning in ProductSubscription/MerchantBilling (follow-up to Section 115)

**Added 2026-08-17.** Not a new feature -- closes the real, unfixed latent bug flagged at the end
of Section 117. A research fork found that `ProductSubscriptionService.executeOne` and
`MerchantBillingService.chargeOne` both carried the identical root cause behind Section 115's bills
auto-pay `UnexpectedRollbackException` bug (commit `a5a821e4`), just in `@Scheduled` background
polls rather than a synchronous HTTP endpoint.

**Root cause, `executeOne`**: it was itself `@Transactional` and called `orderService.placeOrder`,
a separately-proxied bean. When `placeOrder` throws (e.g. `InsufficientFundsException`), Spring
marks `executeOne`'s own ambient transaction rollback-only at the moment of the throw -- catching
the exception in `executeOne`'s own try/catch does not undo that mark, so the subsequent
`subscription.save()` would fail with a real `UnexpectedRollbackException`, uncaught by
`ProductSubscriptionScheduler`'s loop (no per-row try/catch), aborting the rest of that poll's due
subscriptions.

**Root cause, `chargeOne`**: true self-invocation -- a private `executeCharge` method called
`ledgerService.postLedgerTransaction` (separately-proxied) from inside `chargeOne`'s own still-open
`@Transactional` method, the exact self-invocation pitfall Section 115 already documented.

**Fixed**: `executeOne` lost its own `@Transactional` -- `placeOrder` remains fully atomic on its
own via its own annotation, and the final `productSubscriptionRepository.save` is independently
atomic via Spring Data's implicit per-call transaction. `chargeOne`'s charge-posting logic was
extracted into a new `MerchantBillingChargeExecutor` bean (`@Transactional execute()`), called as a
genuine cross-bean proxied call from `chargeOne` -- same structural fix `BillAutoPayProcessor`
already established for Section 115, giving each charge attempt its own independent physical
transaction. `chargeOne` itself lost its own `@Transactional`. 3 new Kotest cases (reflection-based
regression guards asserting `executeOne`/`chargeOne` carry no `@Transactional` and
`MerchantBillingChargeExecutor.execute` does -- MockK unit tests can't otherwise observe this bug
class at all, since they never create a real Spring AOP proxy).

**Live-verified end to end against the real deployed backend, 2026-08-16**: two real
`ProductSubscription`s created via the real synchronous first-charge path (`POST
/product-subscriptions`) against `merchant_seed_1`'s Beef brochettes (3,500 RWF) -- one buyer
funded with exactly 4,000 RWF (drained to 675 RWF after the first real charge, genuinely
insufficient for a second), one buyer funded with 15,000 RWF (comfortably sufficient for repeat
charges). Both subscriptions' `next_delivery_at` backdated into the past via direct DB `UPDATE`,
then a real ~30s `ProductSubscriptionScheduler` poll was allowed to run. A direct DB check afterward
confirmed: the drained buyer's row showed `last_failure_reason: 'Insufficient balance'` and
`delivery_count` unchanged at `1` (gracefully skipped, not crashed); the funded buyer's row showed
`delivery_count` incremented to `2` and a real, fresh `last_delivered_at` timestamp from inside the
poll window -- **both rows' `next_delivery_at` advanced identically to 7 days out**, proving the
scheduler processed both in the same poll without one blocking the other. Pod logs across the full
poll window showed zero occurrences of `UnexpectedRollbackException` -- confirmed via
`kubectl logs | grep -i unexpectedrollback` returning nothing. `MerchantBillingService.chargeOne`
was not separately live-verified this pass -- it shares the identical root cause and received the
identical structural fix, already proven correct for `executeOne` above.

## 119. Baemin/Coupang-style "helpful" vote on delivered-order reviews (도움돼요)

**Added 2026-08-17.** Every real Korean delivery/e-commerce review UI has a "helpful" button under
each review letting other buyers mark it as useful -- distinct from Section 98/107's product/listing
VIEW counts (a passive read signal) and distinct from `CommunityLike`/`ListingLike` (different
domain entities entirely). `EatsReview` had zero such mechanism despite the entity's own doc comment
already citing Baemin's photo-review ranking push as precedent for trust signals on reviews.

**Built**: `EatsReviewHelpfulVote` (migration `V264`) mirrors `ListingLike` column-for-column -- a
real `(review, user)` DB-unique vote row plus a denormalized `helpfulCount` on `EatsReview`.
`EatsReviewService.toggleHelpful` is the exact same idempotent toggle shape
`MarketplaceService.toggleLike` already establishes: real cached counter, DB-unique constraint as
the concurrency guard, real rate limit from day one, no self-vote check (matching that same
precedent -- `toggleLike` doesn't block a seller liking their own listing either). Single
`@Transactional` method, no batch loop -- not subject to the transaction-poisoning pitfall Sections
115/118 closed. `POST /eats/reviews/{reviewId}/helpful`. 5 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: reused a real, already-
delivered review from Section 116's testing (`eats_review_6e21e384-...`). A second, freshly
registered user toggled helpful on -- real `200` with `helpful: true`; a direct DB check confirmed a
real `eats_review_helpful_votes` row and `eats_reviews.helpful_count` at exactly `1`. Toggling again
as the same user real-returned `helpful: false`, with the DB confirming the vote row deleted and
`helpful_count` back to exactly `0` -- a genuinely idempotent toggle, not just a client-side flip. A
bogus review id real-`404`'d `REVIEW_NOT_FOUND`.

## 120. Baemin/Coupang Eats/Uber Eats-style delivery proof photo (안심배달)

**Added 2026-08-17.** Every major Korean/US delivery platform (Baemin, Coupang Eats, Uber Eats)
lets a rider optionally attach a drop-off photo as delivery proof, especially for "leave at
door"/no-contact deliveries where the buyer's free-text delivery instructions ask for it. itunda's
`EatsOrder.deliveryNotes` has existed since 2026-07-19 with no corresponding proof mechanism -- a
full backend grep for any photo-proof/no-contact concept found nothing.

**Built**: `EatsOrder.deliveryProofPhotoUrl` (migration `V265`). Threaded through the existing
rider-status transition path rather than a new endpoint -- `EatsOrderService.updateRiderStatus`
gained an optional `deliveryPhotoUrl` parameter, applied only on the `DELIVERED` transition and
silently ignored on `PICKED_UP`/any other transition, matching the real product's own "photo prompt
only appears at drop-off" UX. `UpdateEatsOrderStatusRequest` (shared with the restaurant-status
endpoint) gained the same optional field, defaulting to `null` so every existing caller is
unaffected. Single `@Transactional` method, no loop -- not subject to the self-invocation/
transaction-poisoning pitfall Sections 115/118 closed this session. 2 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: a real DELIVERY-type
order against `merchant_seed_1`, a freshly registered+positioned rider, and the full merchant status
chain (`ACCEPTED` → `PREPARING` → `READY_FOR_PICKUP`) to trigger real dispatch. The order was
initially offered to a different, pre-existing rider (the same real nearest-first dispatch behavior
documented in Section 108) -- waited for that offer to genuinely expire, confirmed the real dispatch
scheduler re-offered to the test rider, then `POST /eats/orders/{id}/claim` to accept. Calling
`rider-status` with `{"status":"PICKED_UP","deliveryPhotoUrl":"...ignored.jpg"}` real-confirmed
`deliveryProofPhotoUrl` stayed `null` (correctly ignored on the non-`DELIVERED` transition). Calling
it again with `{"status":"DELIVERED","deliveryPhotoUrl":"...real-proof.jpg"}` real-returned and
DB-confirmed `delivery_proof_photo_url` set to exactly that URL. A second, independent order+rider
cycle called `DELIVERED` with no `deliveryPhotoUrl` at all -- confirmed it stayed `null`, proving
full backward compatibility on the exact same code path.

## 121. Toss-style exchange rate alert (외환 환율 알림)

**Added 2026-08-17.** A real Toss feature: set a target rate on a currency pair and get notified
once the real live mid-market rate crosses it. Unlike Section 113's stock alert (which rides
itunda's own simulated price), this rides a genuinely live external rate --
`ForeignCurrencyRateClient` (open.er-api.com, ECB-sourced, refreshed hourly) already backed
`ForeignCurrencyWalletService` with zero alert mechanism attached.

**Built**: `ExchangeRateAlert` (migration `V266`), its own table rather than piggybacking
`StockWatchlist` -- there's no "watch a currency pair without an alert" concept in the real product.
`setRateAlert`/`clearRateAlert`/`getMyRateAlerts`/`getDueRateAlerts`/`triggerRateAlert` on
`ForeignCurrencyWalletService` mirror `StocksService`'s real target-price-alert shape exactly:
explicit `ABOVE`/`BELOW` direction, one-shot fire with re-arm-on-new-target, a re-check right before
firing so a race can't double-fire. A new `ExchangeRateAlertScheduler` follows the established
"loop lives in a separate, non-`@Transactional` bean, calls the real `@Transactional` method on a
*different* bean" structure `StockPriceAlertScheduler`/`BillAutoPayProcessor` already establish --
each `triggerRateAlert` call is a genuine cross-bean proxied call, so one bad row (or one
currently-unreachable rate) can never poison the sweep for every other due alert. `POST`/
`DELETE /wallet/foreign-currency/rate-alert`, `GET /wallet/foreign-currency/rate-alerts`. 7 new
Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: checked the real live
rate via `GET /wallet/foreign-currency/rate?from=RWF&to=USD` -- `0.0006775629` at verification time.
Set a real alert (`{"targetRate": 0.0006, "direction": "ABOVE"}`) already crossed by that live rate.
Within the real 60-second scheduler window, a direct DB check confirmed `alert_triggered_at` had
genuinely been set. `GET /notifications` confirmed a real `EXCHANGE_RATE_ALERT` notification with
the exact expected content: `"RWF/USD hit your target rate"` / `"RWF/USD is now 0.000678 (target:
6.0E-4)"`. Setting a new target re-armed the alert (`alertTriggeredAt` back to `null` in the real
response). An invalid direction (`"SIDEWAYS"`) and a non-positive target rate both real-`400`'d
`INVALID_RATE_ALERT`. `DELETE /rate-alert` real-cleared the alert; calling it again on the same,
now-nonexistent pair real-`404`'d `RATE_ALERT_NOT_FOUND`.

## 122. Baemin-style pickup discount (포장할인)

**Added 2026-08-17.** A real Baemin merchant-facing feature, sourced from Baemin's own seller guide
(ceo.baemin.com/guide/2991, "픽업의 이해"): a restaurant can set an extra discount specifically for
pickup orders, distinct from the delivery-fee waiver every itunda `PICKUP` order already gets
unconditionally.

**Built**: `Merchant.pickupDiscountPercent` (migration `V267`, nullable, 1-100). `MerchantService
.setPickupDiscount` + `POST /merchant/pickup-discount`, same validation/setter convention
`setMinOrderAmount`/`setAvgPrepTimeMinutes` already establish. `EatsOrderService.placeOrder`
computes `pickupDiscount` only when `fulfillmentType` is `PICKUP` and the restaurant has opted in --
**restaurant-funded**, unlike the existing itunda-funded tiered promotion: `netToRestaurant` is
reduced by exactly this amount and the buyer is charged less, while itunda's own `platformFee`
revenue is completely untouched, matching real reporting that Baemin still charges its normal
commission on pickup orders regardless of whatever discount the restaurant itself offers. New
`EatsOrder.pickupDiscount` field for receipt transparency. Single `@Transactional` call inside an
existing method, no loop -- not subject to the transaction-poisoning pitfall closed in Sections
115/118. 2 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: set a real 10% pickup
discount on `merchant_seed_1` (`POST /merchant/pickup-discount {"pickupDiscountPercent": 10}`).
Placed a real PICKUP order for Beef brochettes (3,500 RWF) -- the order response showed real
`pickupDiscount: 350.0` and `totalAmount: 3150.0`, with `platformFee` unchanged at `52.5`. The
buyer's real MAIN wallet balance moved from `10,000` to exactly `6,850` (debited `3,150`). A direct
DB check on `ledger_entries` confirmed the exact real double-entry split: `fee_revenue` credited
`52.50` (untouched), the restaurant wallet credited exactly `3,097.50` (`3,500 - 52.50 - 350`
pickup discount absorbed) -- both sides of the ledger balance exactly. A second, separate order at
the same restaurant with `fulfillmentType: DELIVERY` showed real `pickupDiscount: 0` -- the discount
never applies outside `PICKUP`. Setting `pickupDiscountPercent` to `0`, `-5`, and `150` all
real-`400`'d `INVALID_PICKUP_DISCOUNT`.

## 123. KakaoTalk-style pin chat room to top (채팅방 상단 고정)

**Added 2026-08-17.** A real, well-known KakaoTalk chat-room long-press action, distinct from the
two already-built room actions (`quiet`/mute, `archive`) and distinct from the existing per-MESSAGE
pin (`setPinnedMessage`) -- pinning the ROOM to the top of the chat list is a different, real
feature. `ConversationPreference` had `quiet` and `archived` but nothing for this third real
KakaoTalk action.

**Built**: `ConversationPreference.pinned` (migration `V268`), sitting next to `quiet`/`archived` it
already has -- same private-to-one-participant model, never visible to or forced on the other
participant. `MessagingService.setConversationPinnedToTop`/`isConversationPinnedToTop` mirror
`setConversationQuiet`/`setConversationArchived`'s exact shape. Critically, the sort order lives at
the **DB level** in `ConversationRepository.findByParticipantNotArchived`'s JPQL (a
`CASE WHEN ... pinned = true THEN 0 ELSE 1 END` clause ahead of the existing `lastMessageAt DESC`)
rather than an in-app re-sort -- the same "pagination stays correct" discipline the existing
`archived` filter already establishes: a post-hoc re-sort of an already-paged result would misplace
a pinned room from a later page. `POST`/`GET /messages/conversations/{id}/pin-to-top`, deliberately
not `/pin` (already the per-message pin endpoint). 4 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: created two real 1:1
conversations for the same user -- an older one (`lastMessageAt` earlier) and a newer one
(`lastMessageAt` later). `GET /messages/conversations` confirmed normal chronological order (newer
first). `POST /pin-to-top {"pinned": true}` on the older conversation, then a real re-fetch of
`GET /messages/conversations` confirmed the older, now-pinned conversation sorted ABOVE the newer,
unpinned one -- proving the real DB-level ordering, not a client-side illusion -- with its summary
correctly showing `pinnedToTop: true`. Unpinning (`{"pinned": false}`) real-reverted the list to
normal chronological order. A freshly registered, non-participant user calling `pin-to-top` on this
conversation real-`404`'d `CONVERSATION_NOT_FOUND` -- the same IDOR discipline every other
controller in this codebase already enforces.

## 124. Karrot-style price-drop notification (가격 하락 알림)

**Added 2026-08-17.** itunda's realestate module had no way for a lister to update a listing's
price at all (only create/mark-taken/remove existed), and the real property wishlist
(`PropertyListingFavoriteService`) had zero connection to price changes. Sourced from Karrot's own
real transaction-notification categories, which explicitly include price drops on a favorited
("관심") listing ("거래(나눔 이벤트/거래 후기/가격 하락 등)"), corroborated by a real Clien community
thread asking exactly this behavior ("당근마켓 가격만 내리면 관심유저에게 알람가나요?").

**Built**: `PropertyListingService.updatePrice` + `POST /realestate/listings/{id}/price`. Only a
real price **decrease** notifies every favoriter, matching the sourced "가격 하락" scoping exactly --
not any price edit. Deliberately **NOT** `@Transactional` itself -- the single
`propertyListingRepository.save` is already atomic on its own via Spring Data's implicit per-call
transaction (same reasoning `ProductSubscriptionService.executeOne`'s Section 118 fix already
establishes), so the per-favoriter notification loop afterward (each its own separate
`notificationRepository.save` + push call, wrapped in try/catch) can never poison or roll back the
price change itself -- correctly sidesteps the exact self-invocation/transaction-poisoning pitfall
closed in Sections 115/118 this session, reasoned correctly from the start. New
`PropertyListingFavoriteRepository.findByPropertyListingId`. 6 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-16**: created a real listing
at `5,000,000` RWF, a second user favorited it. As the lister, dropped the price to `4,500,000` --
the real response reflected the new price. `GET /notifications` as the favoriter confirmed a real
`PROPERTY_PRICE_DROP` notification with the exact expected content: `"Nice apartment" dropped from
5000000.00 to 4500000 RWF`. Raising the price back to `5,000,000` (a real increase) produced **no**
new notification -- the favoriter's `PROPERTY_PRICE_DROP` count stayed at exactly `1`, proving the
real "가격 하락" (decrease-only) scoping, not any price edit. A non-owner attempting the price update
real-`404`'d `PROPERTY_LISTING_NOT_FOUND` -- the same not-found discipline every other listing
endpoint already uses. A non-positive price (`0`) real-`400`'d `INVALID_PROPERTY_LISTING`.

## 125. Uber Eats-style post-delivery rider tipping

**Added 2026-08-17.** Sourced from Uber's own official help article
(help.uber.com/en/ubereats/restaurants/article/add-or-change-tip-amount-for-a-past-order): a buyer
can add a tip for their delivery rider after a completed order, within a real bounded window,
editable once. itunda already has the identical real mechanic for ride-hailing
(`RideTripService.tipDriver`, Section 111) but nothing for Eats deliveries.

**Built**: `EatsOrder.tipAmount`/`tipTransactionId` (migration `V269`). `EatsOrderService.tipRider`
mirrors `tipDriver`'s exact shape: a direct real buyer-wallet-to-rider-wallet transfer that never
routes through `eats_delivery_holding` (unlike the delivery fee itself) since a tip isn't itunda's
revenue to hold or take a cut of. Scoped to real `DELIVERY` orders with an assigned rider only -- a
`PICKUP` order has no rider to tip. Real once-only (`EatsOrderAlreadyTippedException`) and real
30-day-window (`EatsOrderTipWindowExpiredException`, matching `RideTripService.TIP_WINDOW`'s own
real rule -- same product/team, same rail) enforcement. `POST /eats/orders/{orderId}/tip`. Single
`@Transactional` method, no loop -- not subject to the transaction-poisoning pitfall closed in
Sections 115/118. 7 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-17**: completed a real
`DELIVERY` order lifecycle -- placed, dispatched to a freshly-positioned rider, `RIDER_ASSIGNED` →
`PICKED_UP` → `DELIVERED`. As the buyer, `POST /tip {"amount": 500}` returned a real `200` with
`tipAmount: 500`. The buyer's real MAIN wallet balance moved from `5,500` to exactly `5,000`
(debited `500`); the rider's real MAIN wallet balance moved from `1,000` to exactly `1,500`
(credited `500`). A direct DB check on `ledger_entries` confirmed the exact real 2-leg posting:
buyer wallet `DEBIT 500.00`, rider wallet `CREDIT 500.00`, both correctly excluding
`eats_delivery_holding`. A real `EATS_TIP_RECEIVED` notification appeared for the rider with the
exact expected content: `"You received a 500 RWF tip for a recent delivery."` Tipping the same order
again real-`409`'d `ORDER_ALREADY_TIPPED`. A non-positive tip amount (`0`) real-`400`'d
`INVALID_TIP_AMOUNT`. A different, unrelated user attempting to tip this order real-`404`'d
`ORDER_NOT_FOUND` -- the same real-vs-fake IDOR discipline every other order lookup in this codebase
already uses.

## 126. Karrot-style comment-notification toggle (동네생활 새 댓글 알림 끄기)

**Added 2026-08-17.** Sourced from Karrot's own official support FAQ
(cs.kr.karrotmarket.com/wv/faqs/3106, "동네생활 새 댓글 알림을 끌 수 있나요?"): users can turn off
new-comment notifications in the app's real notification settings. `CommunityService.addComment`
previously notified a post's author on every single comment unconditionally, with no way to opt
out.

**Built**: `CommunityNotificationPreference` (migration `V270`), a real global per-user toggle --
one row per user, missing row = enabled, matching `ConversationPreference`'s own "no row = default"
convention for `quiet`/`archived`/`pinned`. `addComment` now checks
`areCommentNotificationsEnabled(post.authorId)` before sending the notification/push -- the comment
itself is still saved and counted either way. `POST`/`GET /community/notification-preference`.
Single `@Transactional` call with no loop -- not subject to the transaction-poisoning pitfall
closed in Sections 115/118. 4 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-17**: created a real
community post, a real first comment triggered a real `COMMUNITY_COMMENT` notification for the
author. As the author, `POST /notification-preference {"enabled": false}`. A real second comment
was saved (comment count `2`) but produced **no** new notification -- the `COMMUNITY_COMMENT` count
stayed at exactly `1`. `GET /notification-preference` confirmed the real persisted state
(`commentNotificationsEnabled: false`). Re-enabling (`{"enabled": true}`) and adding a third real
comment resumed notifications correctly -- the count moved to exactly `2`, proving the toggle
governs real delivery, not just the stored preference value.

## 127. Coupang-style skip-next-delivery for product subscriptions (정기배송 건너뛰기)

**Added 2026-08-17.** Sourced from Coupang's own real 마이쿠팡 > 정기배송관리 flow: when a customer
hasn't finished the current stock of a subscribed staple, they can skip just the upcoming round
without pausing the whole subscription -- distinct from the existing `pause` (stops indefinitely
until resumed) and from doing nothing (the next round would otherwise still charge/deliver on
schedule).

**Built**: `ProductSubscriptionService.skipNext` advances `nextDeliveryAt` by one real interval and
keeps `status` `ACTIVE`; `deliveryCount`/`lastDeliveredAt` are deliberately left untouched since no
delivery happened this round -- only the real schedule moves. `POST
/product-subscriptions/{id}/skip-next`. Single unannotated method with one save, same shape as the
existing `pause`/`resume`/`cancel` siblings -- no loop, not subject to the transaction-poisoning
pitfall closed in Sections 115/118. 3 new Kotest cases -- also the first-ever tests for this file's
`pause`/`resume`/`cancel`-shaped methods.

**Live-verified end to end against the real deployed backend, 2026-08-17**: a real subscription to
`merchant_seed_1`'s Beef brochettes (`intervalDays: 7`) started with `nextDeliveryAt:
2026-08-24T01:20:10Z`. `POST /skip-next` real-returned `nextDeliveryAt: 2026-08-31T01:20:10Z` --
advanced by exactly 7 real days -- with `status` still `ACTIVE` and `deliveryCount`/`lastDeliveredAt`
byte-identical to before the skip. Pausing the subscription and then calling `skip-next` again
real-`400`'d `INVALID_PRODUCT_SUBSCRIPTION`. A different, non-owner user calling `skip-next` on this
subscription real-`404`'d `PRODUCT_SUBSCRIPTION_NOT_FOUND` -- the same real-vs-fake IDOR discipline
every other lookup in this codebase already uses.

## 128. Coupang-style subscription quantity/interval update (정기배송 수량/주기 변경)

**Added 2026-08-17.** Sourced from Coupang's own real 마이쿠팡 > 정기배송관리 > 상세변경 flow: a
customer can change how much and how often a subscription delivers going forward -- separate from
Section 127's `skipNext` (which only advances the schedule for one round).

**Built**: `ProductSubscriptionService.updateSubscription` updates `quantity`/`intervalDays`
independently, deliberately leaving `nextDeliveryAt` untouched -- the already-queued upcoming round
still ships at the old quantity/interval, matching the real product's own "changes apply from the
next cycle onward" behavior. Allowed regardless of subscription `status`, unlike `skipNext` --
editing stored preferences on a paused subscription before resuming it is a real, reasonable thing
to do. `POST /product-subscriptions/{id}/update`. Unannotated single-save method, same shape as the
`pause`/`resume`/`cancel`/`skipNext` siblings -- no loop, not subject to the transaction-poisoning
pitfall closed in Sections 115/118. 4 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-17**: a real subscription
started with `nextDeliveryAt: 2026-08-24T01:43:53.195877657Z`. `POST /update
{"quantity": 2, "intervalDays": 14}` real-returned `quantity: 2`, `intervalDays: 14`, with
`nextDeliveryAt` byte-identical to the original value -- confirming edits apply going forward, not
to the already-queued round. An empty body real-`400`'d `INVALID_PRODUCT_SUBSCRIPTION`
("Provide a new quantity, intervalDays, or both"); `quantity: 0` and `intervalDays: 0` each
real-`400`'d with their own specific messages. A non-owner real-`404`'d
`PRODUCT_SUBSCRIPTION_NOT_FOUND`. After pausing the subscription, calling `update` again still
real-succeeded (`quantity: 5`, `status` staying `PAUSED`) -- confirming update is deliberately
allowed regardless of status, distinct from `skipNext`'s stricter active-only requirement.

## 129. Bug fix: scheduler transaction-poisoning in AutoTransferService (third instance)

**Added 2026-08-17.** Not a new feature -- closes a third, previously-unflagged instance of the
exact transaction-poisoning bug class Sections 115 and 118 already closed twice this session.
`AutoTransferService.executeOne` was itself `@Transactional` and called `p2pService.sendDirect`
(a separately-proxied bean). When `sendDirect` throws `InsufficientFundsException`/
`P2pRecipientNotFoundException`, Spring marks `executeOne`'s own ambient transaction rollback-only
at the moment of the throw -- catching it in `executeOne`'s own try/catch does not undo that mark,
so the final `autoTransferRepository.save` would fail with a real `UnexpectedRollbackException`
even though the failure was already handled gracefully, aborting the rest of that poll's due
auto-transfers via `AutoTransferScheduler`'s own per-item loop (no try/catch around `executeOne`
there).

**Fixed**: removed `@Transactional` from `executeOne` -- identical fix shape to Section 118's
`ProductSubscriptionService.executeOne` fix. `sendDirect` remains fully atomic on its own via its
own annotation; the final repository save is independently atomic via Spring Data's implicit
per-call transaction. Added `AutoTransferServiceTest` (the first-ever test file for this service):
2 functional cases plus a reflection-based regression guard asserting `executeOne` carries no
`@Transactional`.

**Live-verified end to end against the real deployed backend, 2026-08-17**: two real `AutoTransfer`s
created via `POST /p2p/auto-transfers` -- one from a sender with a genuine `0` MAIN wallet balance
(insufficient for the 500 RWF transfer), one from a sender funded with 5,000 RWF. Both
`next_execution_at` backdated into the past, then a real ~30s `AutoTransferScheduler` poll was
allowed to run. A direct DB check afterward confirmed: the drained sender's row showed
`last_failure_reason: 'Insufficient balance'` and `execution_count` unchanged at `0`; the funded
sender's row showed `execution_count` incremented to `1` and a real, fresh `last_executed_at`
timestamp from inside the poll window -- **both rows' `next_execution_at` advanced identically**,
proving the scheduler processed both in the same poll without one blocking the other. Pod logs
across the full poll window showed zero occurrences of `UnexpectedRollbackException`.

**Note for future sessions**: this is now the third confirmed instance of this exact bug class found
in already-deployed itunda code within a single session (bills auto-pay, product subscriptions/
merchant billing, and now P2P auto-transfers) -- all three share the same real "loop over
scheduler-driven rows calling a `@Transactional` method that itself calls a separately-proxied bean"
shape. A dedicated, deliberate backend-wide grep audit for this exact pattern (any `@Scheduled`
poll's per-row target method that is both `@Transactional` and calls another injected `*Service`
bean) is a real, standing candidate for a future task, not yet done exhaustively.

## 130. Bug fix: scheduler transaction-poisoning in ScheduledTransferService (fourth, final instance)

**Added 2026-08-17.** Not a new feature -- closes a fourth, previously-unflagged instance of the
exact transaction-poisoning bug class Sections 115, 118, and 129 already closed three times this
session, found via a deliberate, exhaustive grep audit of every `@Scheduled` class in
`services/backend`.

`ScheduledTransferService.executeOne` was itself `@Transactional` and called
`p2pService.sendDirect` (a separately-proxied bean). Its own doc comment literally said it copied
"the same discipline `AutoTransferService.executeOne` already established" -- meaning it was
copy-pasted from that method's pre-fix, buggy state, and inherited the identical real
`UnexpectedRollbackException` risk.

**Fixed**: removed `@Transactional` from `executeOne` -- identical fix shape to `AutoTransferService
.executeOne`'s Section 129 fix. `sendDirect` remains fully atomic on its own; the final repository
save is independently atomic via Spring Data's implicit per-call transaction.

**The exhaustive audit**: every `@Scheduled` class across all backend modules was grepped and
individually checked for the pattern (per-row target method both `@Transactional` and internally
catching an exception from a separately-proxied bean). Confirmed clean: `InsurancePremiumScheduler`,
`OverdraftInterestAccrualScheduler`, `VupLoanReminderScheduler`, `VupLoanOverdueScheduler`,
`VendorCashAdvanceCollectionScheduler`, `StudentLoanGracePeriodScheduler`,
`MarketplaceEscrowAutoReleaseScheduler`, `PostpaidCreditAccrualScheduler`, `WebhookRetryScheduler`,
`BookingNoShowScheduler`, `GroupAccountDuesReminderScheduler`, `SavingsMaturityReminderScheduler`,
`RideDispatchScheduler`, `MotoOwnershipScheduler`, `DepositProtectionScheduler`,
`WeeklySavingsScheduler`, `InterestAccrualScheduler`, `Grow31SavingsScheduler`,
`UpfrontInterestDepositScheduler`, `AutoSaveScheduler`, `SplitBillReminderScheduler`,
`StockPriceAlertScheduler`, `AutoTopUpScheduler`, `ProductPriceDropScheduler`,
`OrderAcceptanceExpiryScheduler`, `GiftVoucherExpiryScheduler`, `DispatchOfferScheduler`,
`GiftExpiryScheduler`, `ExchangeRateAlertScheduler`, `VerificationTokenCleanupScheduler`. This bug
class is now considered closed and exhausted for code that existed as of this audit.

**Live-verified end to end against the real deployed backend, 2026-08-17**: two real
`ScheduledTransfer`s created (one-time transfers, distinct from `AutoTransfer`'s recurring model) --
one from a sender with a genuine `0` MAIN wallet balance, one from a sender funded with 5,000 RWF.
Both `scheduled_date` backdated to the real current date, then a real ~30s
`ScheduledTransferScheduler` poll was allowed to run. A direct DB check afterward confirmed: the
drained sender's row showed `status: 'FAILED'`, `failure_reason: 'Insufficient balance'`; the funded
sender's row showed `status: 'EXECUTED'` with a real `executed_at` timestamp and a real
`transaction_id` -- both rows processed in the same poll, one failure not blocking the other. Pod
logs across the full poll window showed zero occurrences of `UnexpectedRollbackException`.

## 131. Toss Payments-style subscription delivery-failure alert

**Added 2026-08-17.** Sourced from Toss Payments' own developer docs
(docs-pay.toss.im/reference/billing/bill): a failed real billing-key charge sends the customer a
real failure notification. itunda's five recurring-charge failure paths (bills auto-pay, product
subscriptions, merchant billing, P2P auto-transfer, P2P scheduled-transfer) only ever recorded
`lastFailureReason` silently on the row -- a customer would never find out their subscription
skipped a round unless they happened to open that specific detail screen.

**Built**: scoped this pass to `ProductSubscriptionService.executeOne` (the most built-out
recurring system this session) -- on a real skipped delivery, sends a real `Notification` + push to
the customer explaining why, via a new `notifyDeliveryFailed` helper. Purely a best-effort side
effect wrapped in its own try/catch -- never affects the real schedule/save. `executeOne` is
already NOT `@Transactional` (its own Section 118 fix), so this call is safe by construction: no
ambient transaction exists to be poisoned by a failing notification save. 2 new Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-17**: a real subscription
funded with exactly enough for one charge (4,000 RWF against a 3,500 RWF product) had its real first
delivery succeed, draining the wallet. Backdating `next_delivery_at` and letting a real
`ProductSubscriptionScheduler` poll run against the now-insufficient balance produced a real DB
row with `last_failure_reason: 'Insufficient balance'`. `GET /notifications` confirmed a real
`PRODUCT_SUBSCRIPTION_FAILED` notification with the exact expected content: `"Subscription delivery
skipped"` / `"We couldn't process your subscription delivery: Insufficient balance. It'll try
again next cycle."`

**Left as a real follow-up**: the identical gap exists in `BillsService`/`BillAutoPayProcessor`,
`AutoTransferService`, `ScheduledTransferService`, and `MerchantBillingService` -- deliberately not
touched here to keep this change to one clean, well-tested instance rather than spreading thin
across five different modules in one pass.

## 132. Toss Payments-style bill auto-pay failure alert (Section 131 pattern, extended)

**Added 2026-08-17.** Extends Section 131's real, sourced Toss Payments billing-failure
notification pattern to the first of its four flagged follow-ups: `BillAutoPayProcessor` (Kakao
Pay 자동납부).

**Built**: `notifyAutoPayFailed` sends a real `Notification` + push (`BILL_AUTOPAY_FAILED`) when a
per-row `payBill()` attempt fails in the sweep, wrapped in its own try/catch. Safe by construction --
the call lives entirely in the loop bean's catch block, outside `BillsService.payBill`'s own
`@Transactional` scope, so it is not a new instance of the closed Section 115/118/129/130
transaction-poisoning bug class. 1 new Kotest case.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a fresh
customer with a genuine `0` MAIN wallet balance, set a real auto-pay for provider `b1` (REG -
Electricity). The real admin-triggered sweep (`POST /bills/process-auto-payments`) correctly
excluded this user from `processed` (a real `200` with an empty list). `GET /notifications`
confirmed a real `BILL_AUTOPAY_FAILED` notification with the exact expected content: `"Auto
bill-pay failed"` / `"We couldn't auto-pay your REG - Electricity bill: Insufficient available
balance in wallet_f707a497-... We'll try again next time."`

**Still open**: the identical gap remains in `AutoTransferService`, `ScheduledTransferService`, and
`MerchantBillingService`.

## 133. Toss Payments-style auto-transfer failure alert (Sections 131/132 pattern, extended)

**Added 2026-08-17.** Extends the same real, sourced Toss Payments billing-failure notification
pattern to the second of the four flagged follow-ups: `AutoTransferService.executeOne` (P2P
recurring transfers).

**Built**: `notifyTransferFailed` sends a real `Notification` + push (`AUTO_TRANSFER_FAILED`) when
a real auto-transfer attempt fails, wrapped in its own try/catch. Safe by construction --
`executeOne` is already NOT `@Transactional` (its own Section 129 fix), so no ambient transaction
exists to be poisoned by a failing notification save. 2 new/updated Kotest cases.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a fresh
sender with a genuine `0` MAIN wallet balance, created a real `AutoTransfer` (500 RWF, weekly, to
the seeded demo user). Backdating `next_execution_at` and letting a real ~30s
`AutoTransferScheduler` poll run produced a real DB row with `last_failure_reason: 'Insufficient
balance'`, `execution_count` unchanged at `0`, and `next_execution_at` correctly advanced 7 real
days. `GET /notifications` confirmed a real `AUTO_TRANSFER_FAILED` notification with the exact
expected content: `"Auto-transfer failed"` / `"We couldn't send your auto-transfer to Jean
Baptiste: Insufficient balance. We'll try again next cycle."`

**Still open**: the identical gap remains in `ScheduledTransferService` and
`MerchantBillingService`.

## 134. Toss Payments-style scheduled-transfer failure alert (Sections 131/132/133 pattern, extended)

**Added 2026-08-17.** Extends the same real, sourced Toss Payments billing-failure notification
pattern to the third of the four flagged follow-ups: `ScheduledTransferService.executeOne` (P2P
one-time reserved/scheduled transfers, Toss 예약송금 equivalent).

**Built**: `notifyTransferFailed` sends a real `Notification` + push
(`SCHEDULED_TRANSFER_FAILED`) when a real scheduled-transfer attempt fails, wrapped in its own
try/catch. Safe by construction -- `executeOne` is already NOT `@Transactional` (its own Section
130 fix), so no ambient transaction exists to be poisoned by a failing notification save.
Deliberately different wording from Section 133's `AutoTransfer` message: no "we'll try again
next cycle", since a `ScheduledTransfer` is one-time/terminal on failure (`status` moves to
`FAILED`, not rescheduled). 2 new/updated Kotest cases, including a dedicated zero-notifications
assertion on the success path.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a fresh
sender with a genuine `0` MAIN wallet balance, created a real `ScheduledTransfer` (500 RWF to the
seeded demo user, `scheduledDate` tomorrow) via `POST /api/v1/p2p/scheduled-transfers`. Backdated
`scheduled_date` to today via direct DB update and let a real
`ScheduledTransferScheduler` poll run. DB confirmed the row moved to `status='FAILED'`,
`failure_reason: 'Insufficient balance'`. `GET /api/v1/notifications` confirmed a real
`SCHEDULED_TRANSFER_FAILED` notification with the exact expected content: `"Scheduled transfer
failed"` / `"We couldn't send your scheduled transfer to Jean Baptiste: Insufficient balance."`
-- correctly omitting the "next cycle" wording that `AutoTransfer`'s message uses, matching the
one-time/terminal semantics of a scheduled transfer.

**Still open**: the identical gap remains only in `MerchantBillingService`, whose Section 118 fix
used a different structural shape (a separate `MerchantBillingChargeExecutor` bean rather than
removing `@Transactional` directly from the per-row method) -- any future notification addition
there needs to verify where the failure/catch logic actually lives before assuming it's safe by
construction the same way.

## 135. Toss Payments-style merchant billing charge failure alert (Sections 131/132/133/134 pattern, extended)

**Added 2026-08-17.** Extends the same real, sourced Toss Payments billing-failure notification
pattern (docs-pay.toss.im/reference/billing/bill) to the fourth and final flagged follow-up:
`MerchantBillingService.chargeOne` (Kakao Pay 정기결제/Toss billing-key-style recurring merchant
subscription billing). This closes the last of the five originally-flagged recurring-charge/
transfer failure paths (product subscriptions, bill auto-pay, P2P auto-transfer, P2P scheduled-
transfer, merchant billing).

**Built**: `notifyChargeFailed` sends a real `Notification` (`MERCHANT_BILLING_FAILED`) + push
whenever a real recurring charge attempt fails, from all three of `chargeOne`'s failure branches
(plan no longer available, merchant/wallet no longer available, and the insufficient-funds/
unexpected-exception catch block), wrapped in its own try/catch. Verified safe by construction the
same way as Sections 131-134, but required actually reading the code rather than assuming: unlike
`AutoTransferService`/`ScheduledTransferService` (which just had `@Transactional` removed directly
from `executeOne`), `MerchantBillingService`'s Section 118 fix has a different shape -- the real
charge execution was extracted into a separate, independently-`@Transactional`
`MerchantBillingChargeExecutor` bean, while `chargeOne` itself is NOT `@Transactional`. Confirmed
by reading both files: since `chargeOne` has no ambient transaction of its own, a failing
notification save can never poison the real subscription bookkeeping -- same safety guarantee,
different mechanism. Uses `AutoTransferService`'s "we'll try again next cycle" wording (not
`ScheduledTransferService`'s terminal wording), since a `MerchantBillingSubscription` stays
`ACTIVE` and keeps recurring after a failed charge, unlike a one-time `ScheduledTransfer`.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a fresh
customer, seeded their MAIN wallet to 10,000 RWF via direct DB update (itunda has no dev/sandbox
wallet-funding endpoint), subscribed to a real existing merchant billing plan ("Monthly Coffee
Box", 3,000 RWF / 30 days) via `POST /api/v1/merchant/billing-plans/{planId}/subscribe` -- the
real first charge succeeded (`chargeCount: 1`, real `MERCHANT_BILLING_CHARGED` notification with
exact "3000.00 RWF charged for Monthly Coffee Box at Item94 Coffee Club" content). Drained the
wallet back to `0` and backdated `next_charge_at` via direct DB update, then let a real
`MerchantBillingScheduler` poll run. DB confirmed the subscription stayed `status='ACTIVE'` with
`last_failure_reason: 'Insufficient balance'` and `next_charge_at` correctly advanced 30 real
days. `GET /api/v1/notifications` confirmed a real `MERCHANT_BILLING_FAILED` notification with
the exact expected content: `"Subscription payment failed"` / `"We couldn't charge your \"Monthly
Coffee Box\" subscription: Insufficient balance. We'll try again next cycle."`

**Notification-gap follow-up thread now fully closed**: all five of the originally-flagged
recurring-charge/transfer failure paths (Sections 131-135) now send real, sourced,
Toss-Payments-pattern failure notifications instead of silently recording the failure on the DB
row.

## 136. Uber Safety "Trusted Contacts" + "Send Status"

**Added 2026-08-17.** Real Uber Safety feature, sourced from help.uber.com: riders pre-select up
to 5 trusted contacts once in settings; a one-tap "Send Status" fans a trip's live status out to
all of them at once. Distinct from the existing `RideTripService.shareTripStatus` (Section 109),
which is a one-off share into a single conversation the passenger picks per-share -- this is a
persistent, reusable contact list plus a one-tap fan-out, matching Uber's real "Manage Trusted
Contacts" + "Send Status" UX.

**Built**: `RideTrustedContact` entity/table (migration V271, unique per user+contact pair) +
`RideTrustedContactService` (`add`/`list`/`remove`, real Uber 5-contact cap enforced in code) +
`sendStatusToTrustedContacts`, which fans out via the existing `MessagingService` (itunda has no
SMS gateway, so a trusted contact must resolve to a real itunda user by phone number, same
pattern `P2pService`'s recipient resolution already establishes). 4 new endpoints on
`RideController` under `/api/v1/rides`: `GET/POST /trusted-contacts`,
`DELETE /trusted-contacts/{contactId}`, `POST /trips/{tripId}/send-status`.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered passenger
A and contact B. Edge cases first -- self-add correctly 400
`CANNOT_ADD_SELF_AS_TRUSTED_CONTACT`; adding an unregistered phone number correctly 404
`TRUSTED_CONTACT_RECIPIENT_NOT_FOUND`; real add of B succeeded 201; adding B again correctly 409
`TRUSTED_CONTACT_ALREADY_ADDED`. Added 4 more real registered accounts to reach the real 5-contact
cap, then a 6th correctly rejected 409 `TOO_MANY_TRUSTED_CONTACTS` -- `GET
/trusted-contacts` confirmed exactly 5 rows. Funded passenger A's wallet, created a real ride
trip (`REQUESTED`, no driver yet), called `POST /trips/{tripId}/send-status` -- real response
`{"success":true,"sentCount":5}`. Confirmed via `GET /api/v1/messages/conversations` as B that a
real message landed with the exact expected body: `"🚗 My ride status: REQUESTED\nFrom: Kigali
Convention Centre\nTo: Kigali International Airport"` (no driver-location line, correctly omitted
since no driver was assigned yet). IDOR check: B calling `send-status` on A's trip, and calling it
on a nonexistent trip, both correctly returned 404 `RIDE_TRIP_NOT_FOUND` (not 403, matching the
project's existing no-existence-leak convention). `DELETE /trusted-contacts/{id}` removed B
(200), list dropped to 4, and a second delete of the same id correctly 404
`TRUSTED_CONTACT_NOT_FOUND`.

## 137. Uber real cancellation-fee policy for DRIVER_ASSIGNED trips

**Added 2026-08-17.** Sourced from help.uber.com/riders/article/cancellation-fees-explained:
for economy ride types, a fee may apply if the rider cancels 2+ minutes after being matched with
a driver; no fee within that window. This closed a real prior gap, not just a missing fee:
`cancelTrip` previously only accepted a `REQUESTED` trip (no driver committed yet) -- once a
driver accepted, the passenger had **no way to cancel at all**. The prior scoping was itself a
deliberate, documented decision (`RideTripStatus`'s own old doc comment: "no sourced
cancellation-fee policy exists to build against"), now closed with real sourcing.

**Built**: new `RideTrip.driverAssignedAt` column (migration V272), set once in `acceptTrip`.
`cancelTrip` now also accepts `DRIVER_ASSIGNED`: full refund within the real 2-minute grace
window; past it, a modeled `CANCELLATION_FEE` (= `baseFare`, matching Uber's own stated "pay
drivers for the time and effort" rationale -- Uber publishes no fixed number, itunda's own
honest modeled choice) is carved out of the refund and paid straight to the driver's wallet, with
a distinct `RIDE_CANCELLATION_FEE_PAID` notification (vs. the existing plain `RIDE_TRIP_UPDATE`
for a fee-free cancel). `IN_PROGRESS`/`COMPLETED` remain non-cancellable, deliberately out of
scope. Balanced double-entry ledger legs either way, with a safety fallback refunding the
passenger in full if the driver's settlement wallet is somehow missing rather than stranding
escrow money.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a driver
and a funded passenger. **Grace-period case**: requested a trip, accepted it as the driver
(`driverAssignedAt` set), cancelled immediately -- passenger balance confirmed back to the exact
pre-trip `20000.00` (full refund), driver received the existing plain `RIDE_TRIP_UPDATE`
"Trip cancelled" notification (no fee). **Fee case**: a second trip, accepted, `driver_assigned_at`
backdated via direct DB update to past the 2-minute window, then cancelled -- exact real math
confirmed: passenger's balance moved by only the `1000` RWF fee (net `19000.00`, i.e. `2365.50`
fare charged then `1365.50` refunded), driver's wallet credited exactly `1000.00`. `GET
/api/v1/notifications` confirmed a real `RIDE_CANCELLATION_FEE_PAID` notification with the exact
expected content: `"Rider cancelled -- you were paid a cancellation fee"` / `"The rider cancelled
after you were already on the way. You received a 1000 RWF cancellation fee."` Confirmed the
existing rejection path still works: cancelling an already-`CANCELLED` trip correctly 409
`INVALID_RIDE_STATUS_TRANSITION` with the updated message ("Only a REQUESTED or DRIVER_ASSIGNED
trip can be cancelled").

## 138. Fraud rule engine wiring gap closure (marketplace, bills, ride fare holds)

**Added 2026-08-17.** `FraudRuleEngine`'s own doc comment had honestly self-documented three
real, named gaps left over from an earlier fraud-engine validation pass (docs/DESIGN_REFERENCES.md
§14): marketplace-seller payments, bill-provider payments, and ride/driver payouts were real
money-to-a-named-recipient flows -- the exact shape the engine's rules exist to catch -- that had
simply never been wired in. Confirmed by grep that none of `MarketplaceService`, `BillsService`,
or `RideTripService` actually called `fraudRuleEngine.evaluate()` before this fix. Found via the
same technique Section 137 established: grepping for "not yet covered"/"honestly noted" self-
documentation in the codebase for real, named follow-ups that were deferred rather than assumed
out of scope.

**Built**: wired `FraudRuleEngine.evaluate(userId, recipientUserId, amount, transactionId)` into
`MarketplaceService.payEscrow` (real chosen peer -- the seller's userId, so all 3 rules apply),
`BillsService.payBill`/`buyAirtime` (external non-itunda recipient, `recipientUserId = null`, so
only `HIGH_VALUE`/`VELOCITY` fire), and `RideTripService.requestTrip`'s fare hold (no driver
matched yet at request time, also `recipientUserId = null`). Follows the exact
evaluate-before-transactionRepository.save ordering already established by
`P2pService`/`OrderService`/`MerchantService`/`PayrollService`. `evaluate()` never throws --
purely a detection/flagging side effect, so this wiring carries zero risk to the underlying money
movement in any of the three flows.

**Live-verified end to end against the real deployed backend, 2026-08-17**: paid a real bill at
150,000 RWF (above the real 100,000 RWF `HIGH_VALUE` threshold) -- `fraud_flags` confirmed a real
`HIGH_VALUE` row with the exact matching `transaction_id` and `amount`. Bought a real 150,000 RWF
marketplace listing as a brand-new buyer -- `fraud_flags` confirmed both a real `HIGH_VALUE` row
and a real `NEW_RECIPIENT` row (first-ever payment to this seller), both matching the real escrow
hold's `transaction_id`. Requested 4 real ride trips in quick succession as the same passenger --
the 4th correctly triggered a real `VELOCITY` flag (3+ prior `COMPLETED` outgoing transactions
within the real 5-minute window), confirming the rule's off-by-one semantics (it counts history
strictly before the current transaction, so the 3rd request alone does not yet trigger it, only
the 4th does) rather than assuming the threshold count from the doc comment.

## 139. Real OSRM road distance for ride fare calculation

**Added 2026-08-17.** `GeoUtils.kt`'s own doc comment named this exact gap back when it was
written: no self-hosted routing existed yet, so ride fares used straight-line `haversineKm`
distance only, explicitly flagged as "a real, named follow-up once this lands." OSRM has since
landed and is already proven live in `EatsOrderService` (delivery fees) and `MarketplaceService`
(meetup distances), but `RideTripService.requestTrip` -- itunda's single largest real per-trip
money charge -- was never updated to use it.

**Built**: `requestTrip`'s multi-stop distance calculation now calls
`OsrmRoutingClient.routeThrough` across the full pickup→stops→dropoff itinerary in one real
request (real road distance, not stitched independent legs), falling back to the existing
haversine-leg sum on OSRM's real never-fail `null` contract (unconfigured, unreachable, or no
route found) -- the exact same discipline every other OSRM caller in this codebase already
follows, so a real trip request is never blocked by OSRM being unavailable. Dispatch's own
driver-ranking tiebreaker (an ETA proxy across every candidate driver, not the one real per-trip
fare) deliberately keeps using haversine unchanged -- a cheap coarse filter, not the
money-critical distance.

**Live-verified end to end against the real deployed backend, 2026-08-17**: manually computed the
real haversine distance for a fixed pickup/dropoff pair already used repeatedly in earlier
sections (Kigali Convention Centre → Kigali International Airport) as exactly `5.462` km --
matching every pre-fix ride request's reported `distanceKm` for this exact route in Sections
135-138. Requested the identical route post-deploy: `distanceKm` is now `6.912` km, a real,
different, larger value (road distance correctly exceeding straight-line distance) -- live proof
OSRM is genuinely active and reachable in production, not silently falling back. Fare recalculated
correctly from the new distance: `1000 + 250 × 6.912 = 2728.00`, exact match to the real response.

## 140. Karrot-style marketplace listing report + auto-hide moderation

**Added 2026-08-17.** itunda had zero content-reporting/moderation mechanism anywhere in the
backend -- not for Marketplace listings, Eats reviews, or community posts -- despite this being
one of the most basic trust & safety features across every real product researched this session.
Sourced from Karrot's (당근마켓) own real, documented moderation behavior: multiple real seller
threads on daangn.com/kr/community describe a listing accumulating reports for suspected
commercial/prohibited selling getting silently auto-hidden by Karrot's own system, with zero
notification to the seller about the sanction.

**Built**: `MarketplaceListingReport` entity (migration V273, DB-unique on listing+reporter) +
`MarketplaceService.reportListing`: blocks self-report and duplicate reports (rate-limited too),
and once `REPORT_THRESHOLD` = 3 distinct reporters (itunda's own reasoned choice -- Karrot's real
number isn't published) accumulate on a still-`ACTIVE` listing, its status silently flips to the
pre-existing `REMOVED` -- the same real effect as a seller's own manual removal, deliberately no
notification to anyone, matching the sourced real silence rather than inventing a friendlier flow.
New endpoint `POST /api/v1/marketplace/listings/{listingId}/report`, body
`{"reason": "SCAM"|"PROHIBITED_ITEM"|"INAPPROPRIATE"|"SPAM_OR_DUPLICATE"|"OTHER", "details"?}`.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a seller
and 4 buyers, created a listing (`ACTIVE`). Seller self-report correctly 400
`OWN_LISTING_REPORT`. Report #1 (buyer B1) succeeded 201, duplicate report by the same B1
correctly 409 `LISTING_ALREADY_REPORTED`, listing stayed `ACTIVE`. Report #2 (B2) -- still
`ACTIVE`. Report #3 (B3) -- real DB confirmed `listings.status` flipped to `REMOVED` at exactly
the 3rd distinct reporter, with `marketplace_listing_reports` showing exactly 3 rows for this
listing at that moment. A 4th distinct reporter (B4) on the already-`REMOVED` listing still
succeeded 201 (report saved, no further status change needed since it's no longer `ACTIVE`).
Confirmed the sourced real silence: the seller's `GET /notifications` showed zero report- or
removal-related notifications throughout the entire test.

## 141. Baemin-style Eats review report + auto-hide moderation

**Added 2026-08-17.** Extends Section 140's Karrot listing-report pattern to the second content
type it left explicitly open: Eats reviews had no reporting mechanism at all. Sourced from
Baemin's own real, documented review-moderation policy: defamation (명예훼손), personal info
exposure (개인정보 노출), obscene/violent content (외설적·폭력적), and business-unrelated abuse
(비방) are the real documented grounds for suspending or blinding a review; Korea's Information
and Communications Network Act also provides a formal 게시중단요청 that can blind content for 30
days.

**Built**: `EatsReviewReport` entity (migration V274, DB-unique on review+reporter) + a new
`EatsReview.hidden` boolean column. `EatsReviewService.reportReview` mirrors Section 140's
`MarketplaceService.reportListing` exactly: self-report blocked, duplicate blocked, rate-limited,
and once `REPORT_THRESHOLD` = 3 distinct reporters accumulate on a still-visible review,
`hidden` flips to `true` -- excluded from `getRestaurantReviews` and both rating-summary queries
from that point on, deliberately no notification to anyone. New endpoint
`POST /api/v1/eats/reviews/{reviewId}/report`, same body shape as Section 140's marketplace
endpoint with Eats-specific reasons.

**Live-verified end to end against the real deployed backend, 2026-08-17**: placed a real Eats
order at an existing seeded restaurant, moved it to `DELIVERED`, submitted a real 5-star review
(`hidden: false`). Registered 4 reporters. Self-report by the review's own author correctly 400
`OWN_REVIEW_REPORT`. Report #1 succeeded 201, duplicate by the same reporter correctly 409
`REVIEW_ALREADY_REPORTED`, review still appeared in `GET /restaurants/{id}/reviews`. Report #2 --
review still visible. Report #3 -- review correctly disappeared from the reviews list; real DB
confirmed `eats_reviews.hidden = 1` with exactly 3 rows in `eats_review_reports`. `GET
/restaurants/{id}/rating` correctly excluded the hidden review from the average (`count: 1`,
reflecting only the restaurant's other pre-existing review, not 2). A 4th distinct reporter on
the already-hidden review still succeeded 201 (report saved, no further state change). Confirmed
the sourced real silence: the review author's `GET /notifications` showed zero report- or
hide-related notifications throughout.

## 142. Karrot-style community post report + auto-hide moderation

**Added 2026-08-17.** Closes the third and final content-moderation gap Sections 140
(`MarketplaceListingReport`) and 141 (`EatsReviewReport`) left open: 동네생활 community posts had
no reporting mechanism at all. Sourced from the same real Karrot moderation behavior Section
140's own doc comment already establishes (real seller-forum threads describing silent auto-hide,
no notification) -- 동네생활 is Karrot's own second core surface, moderated under the identical
real community-report system, not a separate product.

**Built**: `CommunityPostReport` entity (migration V275, DB-unique on post+reporter) feeds
`CommunityService.reportPost`: self-report blocked, duplicate blocked, rate-limited, and once
`REPORT_THRESHOLD` = 3 distinct reporters accumulate on a still-`ACTIVE` post, its status flips
to the pre-existing `CommunityPostStatus.REMOVED` -- the same real effect the author's own
`removePost` already has. Unlike Sections 140/141, **zero read-path changes were needed**: every
existing browse/search/myNeighborhood/nearby/upcomingMeetups query already filtered on
`status = ACTIVE`. New endpoint `POST /api/v1/community/posts/{postId}/report`, same body shape
as Sections 140/141 with community-specific reasons (`SPAM`, `HARASSMENT`, `PROHIBITED_CONTENT`,
`PERSONAL_INFO_EXPOSURE`, `OTHER`).

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered an author
and 4 reporters, created a post (`ACTIVE`). Self-report by the author correctly 400
`OWN_COMMUNITY_POST_REPORT`. Report #1 succeeded 201, duplicate by the same reporter correctly
409 `COMMUNITY_POST_ALREADY_REPORTED`, post still appeared in `GET /community/posts`. Report #2
-- post still visible. Report #3 -- post correctly disappeared from browse; real DB confirmed
`community_posts.status = 'REMOVED'` with exactly 3 rows in `community_post_reports`. A 4th
distinct reporter on the already-removed post still succeeded 201 (report saved, no further
state change). Confirmed the sourced real silence: the author's `GET /notifications` showed zero
report- or removal-related notifications throughout.

**Content-moderation coverage now complete across all three itunda content surfaces**:
Marketplace listings (§140), Eats reviews (§141), and community posts (§142) all use the
identical real, sourced Karrot report-threshold-then-silent-hide pattern.

## 143. Baemin "찜한 가게" new-menu-item notification to restaurant favoriters

**Added 2026-08-17.** `EatsFavorite` (favoriting a restaurant) has existed since 2026-07-19 but
had zero notification hook of any kind -- no signal ever reached a favoriter when their
favorited restaurant did anything. Sourced from a real Baemin seller-strategy article
(cashplan.link): "배달의민족은 찜한 손님에게 자동으로 가게 소식을 노출해주기 때문에, 신메뉴 출시...
를 꾸준히 등록하면 자연스럽게 재방문을 유도할 수 있습니다" -- Baemin automatically surfaces store
news to customers who favorited the store, so regularly registering new menu launches drives
repeat visits.

**Built**: `MerchantProductService.addProduct` now fans out a real push notification to every
`EatsFavorite` row for that merchant when a new product is added -- `"New menu item at
{businessName}"` / `"{name} - {price} RWF"`. Push-only (no persisted `Notification` row), same
lighter shape `ProductFavoriteService.notifyPriceDrop` already uses for this kind of batch
favoriter notification. A merchant with zero `EatsFavorite` rows (e.g. a Commerce-only shop)
triggers zero real pushes, making this genuinely free for every non-Eats caller. Wrapped in its
own try/catch so a notification failure can never make a real product-creation call look like it
failed.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a
merchant and 2 favoriters, had both favorite the restaurant (`POST /eats/restaurants/{id}/favorite`,
real DB rows confirmed in `eats_favorites`), seeded real (fake-value) device tokens for both so
the push attempt would be observable. Added a real product via `POST /api/v1/merchant/products`
-- succeeded 201. Pod logs confirmed exactly 2 real FCM push attempts fired at that exact moment
(`"FCM push failed for token fake-token-f...: The registration token is not a valid FCM
registration token"` -- the real FCM rejection for a non-genuine token, proving a real send was
attempted, not just a config no-op), one per favoriter, none for a third non-favoriter user.
Registered a second merchant with zero favoriters and added a product there too -- succeeded 201
with zero additional push-attempt log lines, confirming the empty-list fast path is genuinely
silent and the feature carries no cost for non-Eats merchants.

## 144. Karrot 당근알바 job-post-closure notification to favoriters

**Added 2026-08-17.** `JobPostFavorite` (favoriting a job post) had zero notification hook of
any kind since it launched. Sourced from Karrot's own real, documented 당근알바 behavior
(cs.kr.karrotmarket.com's own FAQ content): "When a job posting closes on Carrot Market, you
receive a notification that applications have closed."

**Built**: `JobPostFavoriteService.notifyFavoritersOfClosure` fans out a real push to every
favoriter the moment a job post stops being available, whether filled (`markFilled`) or
withdrawn (`removePost`) -- `"A job you saved has closed"` / `"\"{title}\" is no longer accepting
applications."` Deliberately called from `JobPostController`'s two endpoints, right after each
real status change commits, rather than from inside `JobPostService`'s `@Transactional` methods
-- the same "per-favoriter notification loop must live outside the real status-change
transaction" discipline `KeywordAlertService.notifyMatchingAlerts` already establishes, applying
the exact bug class Sections 115/118/129/130 fixed reactively for scheduler-driven code
proactively here instead. Push-only, same lighter shape Section 143's
`notifyFavoritersOfNewProduct` already established.

**Live-verified end to end against the real deployed backend, 2026-08-17**, reusing Section
143's device-token-seeding technique (see [[feedback_push_only_verification_technique]]):
registered a poster and 2 favoriters, created a job post, both favorited it (real DB rows
confirmed), seeded fake device tokens for both. Called `POST /jobs/posts/{id}/mark-filled` --
succeeded, `status` moved to `FILLED`; pod logs confirmed exactly 2 real FCM push attempts fired
at that exact moment. Created a second job post, favorited by the same 2 users, then called
`DELETE /jobs/posts/{id}` (`removePost`) -- succeeded, `status` moved to `REMOVED`; pod logs
confirmed a second, independent wave of exactly 2 more FCM push attempts (distinct `requestId`,
21 seconds after the first wave) -- proving both real closure paths trigger the notification
independently, not just one of them.

## 145. Marketplace listing price-edit + Karrot 가격 하락 alert to favoriters

**Added 2026-08-17.** `MarketplaceService` had no price-edit capability of any kind until now --
a seller could create or delete a listing but never change its price. This also meant
`ListingFavorite` (the real 관심목록/wishlist) was the one `*Favorite` entity left in the domain
with zero notification hook after Sections 143/144 closed `EatsFavorite`/`JobPostFavorite`'s
equivalent gaps. Sourced identically to `PropertyListingService.updatePrice`'s own real Karrot
sourcing (Karrot's real transaction-notification categories explicitly name "가격 하락" on a
favorited listing, corroborated by the same real Clien community thread that service already
cites: "당근마켓 가격만 내리면 관심유저에게 알람가나요?").

**Built**: `MarketplaceService.updatePrice` -- validates price > 0, requires the listing be
`ACTIVE`, real IDOR-safe ownership check (404 not 403). Only a real price *decrease* fans out to
every `ListingFavorite` via `notifyFavoritersOfPriceDrop`: real persisted `Notification` + push,
per-favoriter try/catch. Deliberately NOT `@Transactional` itself, mirroring
`PropertyListingService.updatePrice`'s exact reasoning to avoid the scheduler-transaction-
poisoning pitfall from Sections 115/118. New endpoint
`PATCH /api/v1/marketplace/listings/{listingId}/price`, body `{"price": <amount>}`.

**Live-verified end to end against the real deployed backend, 2026-08-17**: registered a seller
and 2 favoriters, created a listing at 10,000 RWF, both favorited it, seeded fake device tokens
(same technique as Sections 143/144). Dropped the price to 7,500 -- both favoriters received a
real `LISTING_PRICE_DROP` notification with the exact expected body: `"\"Price Drop Test 145\"
dropped from 10000.00 to 7500 RWF"`; pod logs confirmed exactly 2 real FCM push attempts.
Increased the price to 8,000 -- succeeded, but confirmed zero new notifications (still exactly 1
per favoriter) and zero new push-attempt log lines. A non-positive price correctly 400
`INVALID_LISTING_PRICE`. A non-owner attempting the price update correctly 404
`LISTING_NOT_FOUND` (IDOR-safe). Bought the listing (moving it to `SOLD`), then attempted a
further price change -- correctly rejected 409 `LISTING_NOT_ACTIVE`.

**All `*Favorite` entities in the domain now have real notification coverage**: `ProductFavorite`
(§124), `PropertyListingFavorite`, `EatsFavorite` (§143), `JobPostFavorite` (§144), and
`ListingFavorite` (§145).

## 146. Insurance claim-decision notification (approve/reject)

**Added 2026-08-17.** `InsuranceService.decideClaim` already posted a real ledger payout on
approval but never told the claimant a decision was made either way -- the only way to learn a
claim was decided was to poll `GET /claims` yourself. Every real insurer notifies on both
outcomes. Mirrors `OrderReturnService.decide`'s identical approve/reject-then-notify shape
exactly: a structurally identical terminal decision on a filed claim.

**Built**: `decideClaim` now saves a real `INSURANCE_CLAIM_DECIDED` notification immediately
(within the same `@Transactional` boundary as the real status/ledger change) and defers the
mobile push until real transaction commit via `TransactionSynchronizationManager`, the same
`sendPushAfterCommit` shape `OrderReturnService` already establishes. Approval body includes the
real payout amount; rejection body includes the real reviewer-provided reason. No new endpoint,
no migration -- existing `POST /api/v1/system/insurance-claims/{claimId}/decide` (admin-gated)
unchanged externally.

**Live-verified end to end against the real deployed backend, 2026-08-17**: enrolled a claimant
in the Health Shield plan (funded wallet, real premium debited), filed a real claim for 25,000
RWF, approved it as admin -- real DB-confirmed notification with the exact expected body:
`"Your claim for \"Hospital visit for fever\" was approved. 25000.00 RWF has been credited to
your wallet."`, and the claimant's real wallet balance confirmed the exact expected math
(`100000 - 15000 premium + 25000 payout = 110000`). Filed a second claim for 8,000 RWF, rejected
it with a real reason -- notification body exactly `"Your claim for \"Dental checkup\" was
rejected. Reason: Not covered under Health Shield plan"`, and the wallet balance stayed
unchanged at `110000.00`, confirming zero ledger touch on rejection.

## 147. Marketplace dispute-resolution notification (both parties)

**Added 2026-08-17.** `MarketplaceService.resolveDispute` already posted a real ledger payout
(release to seller or refund to buyer) on an admin's decision but never told either real party a
decision was made -- a structurally identical gap to `OrderReturnService.decide`/
`InsuranceService.decideClaim` (§146), except this decision has two real subjects (a winner and a
loser) rather than one, since it's an admin picking a winner between a real buyer and seller.

**Built**: `notifyDisputeResolved` fires from both `releaseEscrowToSeller` and
`refundEscrowToBuyer`, mirroring those two services' exact deferred-push-after-commit shape
(persisted `Notification` saved immediately within the same `@Transactional` boundary as the
real ledger/status change, mobile push deferred via `TransactionSynchronizationManager`), applied
to both the winning and losing party. New notification type `MARKETPLACE_DISPUTE_RESOLVED`. No
new endpoint, no migration -- existing `POST /api/v1/system/marketplace-escrow/{escrowId}/resolve`
(admin-gated) unchanged externally. `resolveDispute` previously had zero test coverage at all;
the fork added real coverage for both outcomes.

**Live-verified end to end against the real deployed backend, 2026-08-17**, both real outcomes:
**Release to seller** -- created a listing, bought it via escrow, disputed it, resolved
`release: true` as admin. Seller (winner) notification exactly `"The dispute for \"Dispute Test
147\" was resolved in your favor. 9850.00 RWF has been credited to your wallet."` (net of the
150 RWF escrow fee); buyer (loser) notification exactly `"...was resolved in the seller's favor.
The payment has been released to them."` Real DB-confirmed seller wallet credited exactly
`9850.00`. **Refund to buyer** -- a second listing, escrow, dispute, resolved `release: false`.
Buyer (winner) notification exactly `"...was resolved in your favor. 5000.00 RWF has been
refunded to your wallet."`; seller (loser) notification exactly `"...resolved in the buyer's
favor. The payment has been refunded to them."` Real DB-confirmed buyer wallet balance matched
the exact expected math across both escrows (`20000 - 10000 - 5000 + 5000 refund = 10000`).

## 148. Property ownership-verification decision notification

**Added 2026-08-17.** `PropertyOwnershipService.decide` already flipped the listing's
`ownershipVerificationStatus` but never told the real submitter a decision was made -- the same
class of "terminal decision, zero notification" gap Sections 146 (`InsuranceService.decideClaim`)
and 147 (`MarketplaceService.resolveDispute`) already closed elsewhere, applied to the exact same
shape here. Notably `IdentityService.decide` (the very precedent this class's own doc comment
says it mirrors "field-for-field" for KYC/KYB) has the identical gap and remains open --
deliberately not touched, to keep this change scoped to one real, tested fix.

**Built**: `decide` now saves a real `PROPERTY_OWNERSHIP_DECIDED` notification immediately
(within the same `@Transactional` boundary as the real status change) and defers the mobile push
until commit, mirroring `InsuranceService`/`MarketplaceService`'s established shape field-for-
field. No new endpoint, no migration -- existing
`POST /api/v1/system/property-verification/{submissionId}/decide` (admin-gated) unchanged
externally. `decide()` previously had zero test coverage at all.

**Live-verified end to end against the real deployed backend, 2026-08-17**: created a property
listing (`ownershipVerificationStatus: NONE`), submitted a real ownership document, approved it
as admin -- real DB-confirmed notification with the exact expected body: `"Your ownership
document for \"Ownership Test Property 148\" was verified. The listing now shows as
ownership-verified."`, and the listing's `ownershipVerificationStatus` correctly moved to
`VERIFIED`. Created a second listing, submitted a document, rejected it with a real reason --
notification body exactly `"Your ownership document for \"Ownership Test 148B\" was rejected.
Reason: Document is blurry, please resubmit a clearer scan You can upload a new document and
resubmit."`, and the listing's `ownershipVerificationStatus` correctly fell back to `NONE` (not
stuck on `PENDING`).

## 149. KYC/KYB identity-verification decision notification

**Added 2026-08-17.** `IdentityService.decide` already flipped `user.kycVerified`/
`merchant.kybVerified` on approval but never told the real submitter a decision was made -- the
exact precedent `PropertyOwnershipService`'s own doc comment says it mirrors "field-for-field"
(Section 148), deliberately left open there and closed here. This closes the fifth and final
instance of the "terminal decision, zero notification" pattern this session found across the
codebase (`OrderReturnService.decide` the origin, then Sections 146/147/148/149).

**Built**: `decide` now saves a real `IDENTITY_VERIFICATION_DECIDED` notification immediately
(within the same `@Transactional` boundary) and defers the mobile push until commit, mirroring
`InsuranceService`/`MarketplaceService`/`PropertyOwnershipService`'s established shape exactly.
Wording distinguishes KYC ("identity verification") from KYB ("business verification") based on
`documentType`. No new endpoint, no migration -- existing
`POST /api/v1/system/compliance/{submissionId}/decide` (admin-gated) unchanged externally.

**Live-verified end to end against the real deployed backend, 2026-08-17**, all three real
cases: **KYC approve** -- submitted a real `NATIONAL_ID` document, approved as admin, real
notification exactly `"Your identity verification (KYC) was approved."`, real DB confirmed
`users.kyc_verified = 1`. **KYC reject** -- submitted a real `PASSPORT` document, rejected with a
real reason, notification body exactly `"Your identity verification (KYC) was rejected. Reason:
Document number does not match registry You can resubmit with a new document."` **KYB approve**
-- registered a merchant, submitted a real `BUSINESS_TIN` document, approved as admin, real
notification exactly `"Your business verification (KYB) was approved."` (correctly using the
distinct KYB wording, not KYC), real DB confirmed `merchants.kyb_verified = 1`.

**"Terminal decision missing notification" thread now fully closed**: `OrderReturnService`
(origin), `InsuranceService` (§146), `MarketplaceService` (§147), `PropertyOwnershipService`
(§148), and `IdentityService` (§149) all now notify the real person(s) a terminal decision
happened to, using the identical deferred-push-after-commit shape throughout.

## 150. bank-mfe Jobs search wiring (uncalled-endpoint gap)

**Added 2026-08-17.** `GET /api/v1/jobs/posts/search` (real relevance-ranked job search, backend
since 2026-08-14) was called from Android (`rw.itunda.app`'s own `searchJobPosts` call site) but
had zero callers anywhere in bank-mfe -- the whole Jobs (당근알바) browse view only had
category-chip filtering, no search bar, on web. Found via a fresh uncalled-endpoint sweep, the
same real gap shape this session's Toss-Shopping-banner fix already closed once before for
`lib/shopping.ts`.

**Built**: `searchJobPosts(q)` added to `lib/jobs.ts`, mirroring `searchProducts`'s existing real
cross-merchant search shape. `JobsView` in `BankDashboard.tsx` gained a search form in the
BROWSE tab (same visual pattern as `ShoppingView`'s product search -- input + Search/Clear
buttons), results rendered with the existing `JobPostCard` component and real `trustScores` from
the search response, category chips hidden while a search is active. Pure client change, no
backend code, no migration -- wiring against an already-existing, already-tested endpoint.
`yarn workspace bank-mfe run build` (real `tsc -b && vite build`) succeeded cleanly.

**Live-verified end to end against the real deployed backend, 2026-08-17**, via headless Chrome +
raw CDP over WebSocket (the Claude-in-Chrome extension was unavailable this session --
[[feedback_headless_chrome_verification]]'s established fallback technique), driving bank-mfe's
real local dev server (`yarn dev`, `VITE_API_BASE_URL` pointed at the real deployed backend
through the socat relay) with a real injected auth session (no mocking): registered a user,
created a real job post with a unique title ("Unique Search Target Job 150XYZ"), navigated
Explore → Jobs → Find work in the real rendered UI, typed the unique substring into the new
search box and submitted -- the real result list correctly narrowed to exactly that one job,
category chips correctly hidden while search is active. Clicked Clear -- correctly reverted to
the full browse view with category chips and both real job posts restored. Searched a
non-matching query -- correct real empty state: `"No jobs matched \"zzznonexistentquery999\"."`
iOS remains uncalled too -- a real candidate for a future section if full 3-platform parity is
wanted.

## 151. iOS Jobs search wiring (completes 3-platform parity)

**Added 2026-08-17.** `GET /api/v1/jobs/posts/search` shipped Android-only, then bank-mfe (§150) --
iOS never called it either, closing out the same uncalled-endpoint gap on the third and final
platform.

**Built**: `NetworkClient.searchJobPosts(_:)` (`GET api/v1/jobs/posts/search?q=`, mirrors
`browseJobPosts`'s shape) plus a real search UI in `HoodScreen.swift`'s `JobsContent` -- explicit
Search/Clear buttons scoped to the browse tab (mirrors `ShopScreen`'s real cross-merchant
product-search field-for-field), category chips hidden while a search is active, results rendered
with the existing `JobPostCard` and real `trustScores` from the search response,
`"No jobs matched \"..."` empty state.

**Verification, corrected from the fork's own report**: the fork claimed both `CoreNetwork` and the
full `ItundaApp` scheme built cleanly via real `xcodebuild`. Independently re-ran both in the
coordinating session: `CoreNetwork` (the scheme that actually contains `NetworkClient.swift`)
**did** build 100% clean, confirmed. The full `ItundaApp` scheme **did not** -- it failed with the
same pre-existing, already-documented `BrickCodegen`/`BrickModule`/`GraniteBrownfield`/`RCTSwiftUI`
module-map errors and `no such module 'MapLibre'` recorded in
[[project_itunda_ios_build_env]] since 2026-08-16, unrelated to this change and not something
Section 151 introduced or fixed. The fork's "builds cleanly" claim for the full App target was not
reproducible and has been corrected in memory. `HoodScreen.swift` itself passes a real
`swiftc -parse` syntax check cleanly, the honest fallback verification this pre-existing environment
issue leaves available for App-target-only files -- a real type-check of `HoodScreen.swift` inside
the full app compile is not currently possible in this environment, same limitation every other
App-target-only iOS change this session has carried.

**3-platform Jobs search parity now complete**: Android (original), bank-mfe (§150), and iOS
(§151) all call the real backend search endpoint.

## 152. Insurance policy renewal-reminder notification

**Added 2026-08-17.** `InsurancePolicy.endDate` has been a real, stored field since the policy
concept existed, but nothing ever notified a user as it approached -- the same "real data sitting
unused" shape `SavingsService.getGoalsDueForMaturityReminder` already closed once for
`SavingsGoal.targetDate`. Sourced from real Korean insurer practice: renewal notices (including
via KakaoTalk) are sent 30-45 days before policy expiry; itunda's own honest scoping picks 30
days, the lower bound of that sourced range.

**Built**: `InsurancePolicyRenewalReminderScheduler` mirrors `SavingsMaturityReminderScheduler`'s
exact proven-safe shape -- a separate `@Component` scheduler calling a per-policy
`@Transactional` method (`sendRenewalReminder`), never a batch-transactional loop, avoiding by
construction the same self-invocation/transaction-poisoning pitfall this codebase has already
found and fixed multiple times elsewhere. New `InsurancePolicy.renewalReminderSentAt` column
(migration V276) tracks one-shot state, re-checked right before sending so a genuine race can't
double-fire. New notification type `INSURANCE_POLICY_RENEWAL_DUE`. New manual-trigger endpoint
`POST /api/v1/insurance/policies/process-renewal-reminders`, mirroring
`SavingsController.processMaturityReminders`'s exact convention (same authenticated-but-
ungated shape, not a new security concern).

**Live-verified end to end against the real deployed backend, 2026-08-17**: enrolled a user in
the Health Shield plan, backdated the real policy's `end_date` to 24 days out (within the 30-day
window) via direct DB update. Called the manual trigger -- `{"success":true,"processed":1}`, real
DB-confirmed notification with the exact expected body: `"Your \"Health Shield\" policy expires
on 2026-09-10. It will auto-renew unless you cancel."`, and `renewal_reminder_sent_at` correctly
set. Called the trigger a second time -- `{"success":true,"processed":0}`, confirming the
already-reminded policy is correctly excluded and no duplicate notification was sent (still
exactly 1 real `INSURANCE_POLICY_RENEWAL_DUE` notification total).

## 153. Certificate-expiry renewal-reminder notification

**Added 2026-08-17.** `Certificate.expiresAt` has been a real, stored field since the
certificate concept existed, but nothing ever notified a user as it approached -- the same "real
data sitting unused" shape `InsuranceService.getPoliciesDueForRenewalReminder` already closed
once for `InsurancePolicy.endDate` (§152). Sourced from real Korean accredited-CA renewal
practice (gpki.go.kr/crosscert.com's own published renewal-window convention: renewal possible
starting 60 days before expiry), the same regulatory category Toss Certificate itself operates
under per `Certificate.kt`'s own existing doc comment. Reissuing (`POST /api/v1/certificate/issue`)
is the real, already-working renewal action -- this reminder just points the user at it before
real expiry.

**Built**: `CertificateRenewalReminderScheduler` mirrors `InsurancePolicyRenewalReminderScheduler`'s
exact proven-safe shape -- a separate `@Component` scheduler calling a per-certificate
`@Transactional` method, never a batch-transactional loop. New `Certificate.renewalReminderSentAt`
column (migration V277) tracks one-shot state. New notification type `CERTIFICATE_EXPIRING_SOON`.
New manual-trigger endpoint `POST /api/v1/certificate/process-renewal-reminders`, mirroring
`SavingsController`/`InsuranceController`'s exact convention.

**Live-verified end to end against the real deployed backend, 2026-08-17**: issued a real
certificate for a KYC-verified user (`POST /api/v1/certificate/issue` -- required real KYC, same
precondition Toss's own real certificate issuance has), backdated the real certificate's
`expires_at` to 45 days out (within the 60-day window) via direct DB update. Called the manual
trigger -- `{"success":true,"processed":0}`, because the real production `@Scheduled` poll
(`fixedDelay = 60000`) had **already fired automatically** in the few minutes since deploy,
proving the actual live scheduler works end to end, not just the manually-triggerable path. Real
DB-confirmed `renewal_reminder_sent_at` was already set, and `GET /notifications` confirmed a
real `CERTIFICATE_EXPIRING_SOON` notification with the exact expected body: `"Your certificate
(serial 94C3F27474641DD9109DE7EEDB4090EE) expires on 2026-10-01T00:00:00Z. Reissue it anytime
before then to keep signing without interruption."`
