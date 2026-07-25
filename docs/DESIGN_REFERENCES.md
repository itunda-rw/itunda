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

This document contains **zero color recommendations**. It is entirely about information
architecture, list/card layouts, bottom sheets, search/filter UX, navigation idioms, empty
states, chat UX, and map overlay patterns — sourced from named real products, official
design-system docs, and real engineering/product blogs. Where a claim could not be sourced to
something specific and verifiable, it is labeled `inferred` and should be treated as a hypothesis,
not a confirmed pattern.

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

1. **[sourced]** Replace the static floating `Card` with a real draggable peek/half/full bottom
   sheet. `MapScreen.kt` lines 581–689 is a plain `Column`/`Card` pinned to `BottomCenter` with no
   drag gesture — content just appears/disappears at whatever height its content dictates. Both
   Apple's own `UISheetPresentationController` and Google Maps' documented need for a custom
   `BottomSheetBehavior` extension confirm a true 3-state (peek/anchor/full) sheet is real,
   nontrivial engineering — Compose's stock `BottomSheetScaffold` only gives 2 states out of the
   box, so this needs a custom `AnchoredDraggable`/`SwipeableState`, same gap Android's stock API
   has.
   *Target: `android/app/src/main/java/rw/itunda/app/ui/MapScreen.kt:581-689`*

2. **[sourced]** Give the sheet a default "around me" state instead of only rendering when a place
   is selected or bookmarks exist. Naver's Smart Around keeps a non-modal sheet permanently docked
   with curated nearby sections even before any search, contracting to a peek rather than
   vanishing.
   *Target: `MapScreen.kt` — new default state when `selectedPlace == null && activeCategory == null`, around line 658*

3. **[sourced]** Elevate the place-detail card's action row and image. itunda's selected-place
   Card (lines 583–656) shows only a name, a star toggle, and a single "Directions" button.
   Kakao Map's real redesign deliberately surfaces a full action-button row (reserve/delivery/
   bookmark/call/directions) plus a hero image up front so actions don't require a sub-page.
   *Target: `MapScreen.kt:583-656`*

4. **[sourced]** Group bookmarks into named, colored lists instead of one flat list. Both Kakao
   Map (그룹 + per-group color, shareable) and Naver Map (named list + color + public/private,
   shareable URL) let the list color become the marker pin color on the map — letting a user
   visually distinguish saved-place categories on the map itself, not just in text.
   *Target: `MapScreen.kt:658-688` plus backend `MapBookmarkDto`/`AddMapBookmarkRequest`, which would need a list/group + color field*

5. **[partially-sourced]** Give turn-by-turn its own presentation instead of an inline
   expand/collapse text block (`showSteps` toggle, lines 605–629). Kakao treats routing as its own
   dedicated presentation with per-route summaries (duration/fare/transfers for transit;
   distance/duration/calories for walking). Note: itunda's OSRM-backed route already draws a real
   road-following line, so this gap is purely presentational, not a routing-data quality issue.
   *Target: `MapScreen.kt:605-629`*

6. **[inferred]** Move search from submit-then-list toward autocomplete with a recent-searches
   zero state. itunda's search is tap-"Search"-then-flat-list with no live suggestions and no
   recent-searches state. This is general autocomplete UX practice, **not** independently
   confirmed as Naver/Kakao Map's specific implementation — flagged as the weakest-sourced item
   in this section.
   *Target: `MapScreen.kt:476-509` (search field) and `550-573` (results list)*

7. **[partially-sourced]** Category chips (lines 513–537) already structurally match Kakao's
   pattern reasonably well — this is a genuine partial match, not an urgent gap. The one
   documented difference: Kakao's chips are multi-select toggles paired with a separate detail
   popup; `searchNearbyCategory()` (line 290) is strictly single-select today.

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

1. **[sourced]** The restaurant card is missing the data model to show delivery metadata at all.
   `ShoppingMerchantDto` (`ApiService.kt:488-492`) has only `merchantId, businessName, category,
   cashbackRate` plus optional lat/lng — no `photoUrl`, `rating`, `reviewCount`, `deliveryFee`,
   `deliveryTimeMinutes`, `minOrderAmount`, `distanceKm`. Both Baemin and Coupang Eats put all of
   these directly on the list card so restaurants are comparable before opening any of them. This
   needs a backend model change (table + DTO + endpoint), not just a UI tweak.
   *Target: `ApiService.kt:488` + `/api/v1/shopping/merchants` + `OrderFoodContent`'s browse `LazyColumn`*

2. **[sourced]** Rating exists but sits behind an extra tap. `RestaurantRatingBadge` is only
   called inside `RestaurantMenuView` (`SuperAppTabs.kt:4102`) — after a restaurant is already
   open. Real apps put rating on the browse-list card. Fix: fold rating/reviewCount into the
   existing list payload (a join, not a new round trip).
   *Target: `SuperAppTabs.kt:4102`, browse list*

3. **[sourced]** No menu options/customization model — the single biggest structural gap. Coupang
   Eats' own seller guide makes required-option groups a first-class, **enforced** concept
   (explicitly requiring a +0원 choice so list and cart price never diverge); Baemin's item detail
   is built the same way. itunda's `MerchantProductDto` (`ApiService.kt:531`) is flat
   `id, merchantId, name, price, active, createdAt` — no way to represent spice level, size, or
   add-ons. Without this, itunda cannot represent most real restaurant menus.
   *Target: `ApiService.kt:531`, item row in `RestaurantMenuView:4109-4125`*

4. **[sourced]** No live rider-location map — status is a text label swap
   (`EATS_STATUS_LABELS`/`RIDER_STATUS_CHAIN`, `SuperAppTabs.kt:3519-3527`). itunda already has a
   self-hosted OSRM/Nominatim stack and a `RouteMiniMap` component used elsewhere
   (`~1674`) — the pieces exist, just not wired into Eats tracking the way Baemin/Coupang Eats do
   (live rider dot + phone + vehicle type during delivery).
   *Target: `SuperAppTabs.kt:3519-3527`, `4330` (`EatsOrderRow`), pair with `RouteMiniMap`*

5. **[sourced]** Review model has no photo field and no owner reply. `SubmitEatsReviewRequest`
   (`~4063`) is numeric stars + optional text only. Baemin's 2022 push ranks photo-bearing reviews
   first via 추천순 정렬, and 사장님 댓글 (owner replies) is a named, sourced feature. Food-delivery
   trust leans disproportionately on photos of the actual plated food, making this higher-leverage
   here than on other surfaces.
   *Target: `SubmitEatsReviewRequest`/`ReviewOrderCard`, `SuperAppTabs.kt:3997-4063`*

6. **[inferred]** Cart cannot hold two configurations of the same item, and has no per-line notes.
   `cart = remember { mutableStateMapOf<String, Int>() }` (`3580`) is keyed by raw product id only.
   This follows structurally from the missing options model above (item 3) rather than being an
   independently sourced claim, and should be fixed alongside it.
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

### Unresolved / worth a follow-up

- Baemin's/Coupang Eats' own official design-system docs (story.baemin.com, bcut.baemin.com)
  returned only search snippets, not full content — a direct fetch would sharpen visual/spacing
  specifics.
- No pixel-level card layout verified against a live, dated screenshot.
- Baemin's B마트 and either app's algorithmic "today's picks" ranking weren't researched — itunda
  has no comparable surface yet.
- Coupang Eats' 좋아요/싫어요 per-item rating and pickup/delivery cart toggle came from secondary
  writeups, not Coupang's own product pages.

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

2. **[implemented]** Direct messages now show a Kakao-style pending-read `1` and message timestamps across all clients. Group receipts now have a true per-member model too (2026-07-26): a live per-message unread countdown built on the existing per-member `lastReadAt` cursor — backend-only so far, no client UI touchpoint yet.

3. **[sourced]** Add a long-press message menu: reply/thread, forward, pin, delete. itunda's only
   per-message interaction today is tap-to-toggle a reaction (`MessageReactionsRow`). Kakao's
   confirmed 2025 toolkit is Copy/Reply (→ thread)/Forward (≤10 destinations)/Pin/Delete/@mention
   — none of this exists in `ChatThreadView`/`GroupThreadView`.

4. **[partially implemented]** Talk has private, durable quiet-room controls and suppresses notifications for the participant who enables one. Recoverable archive/list placement and conversation-list swipe actions remain.

5. **[sourced]** Add a per-thread shared-media gallery (Chat Room Drawer). itunda has zero
   aggregation of media/files/links shared in a conversation. Directly relevant to itunda's
   Hood-to-Talk handoff flows (listing photos, offer/gift bubbles).

6. **[sourced]** Add a real attach ("+") menu to the composer. Both thread composers are just a
   text field + send button (plus a gift icon on `ChatThreadView`, `~1016-1027`) — no photo, file,
   or sticker send at all. Kakao's emoticon picker alone is core, monetized product surface (~1/3
   of 2020 revenue), not a nice-to-have.

7. **[implemented, backend]** @mention support in group chats is real (`GroupMessagingService
   .parseMentions`, 2026-07-25): `@FirstName` tokens resolve against real group members and
   trigger a distinctly-titled `GROUP_MENTION` notification. Group-chat pin is also now real
   (2026-07-26), matching 1:1's own pin. UI affordance to actually type/select a mention or
   trigger pin from a long-press menu is still missing on every client — see item 3.
   *Target: `GroupThreadView` header/composer, `GroupMessageBubble` (~500-676)*

8. **[implemented]** Per-message timestamps now appear in direct and group threads across all clients.

<!-- Historical pre-implementation audit retained below for source provenance.
1. **[sourced]** Give Talk a real Friends/contacts directory, not just a Direct/Groups
   chat-*history* toggle. `TalkTab` (`SuperAppTabs.kt:~200-312`) only switches between two
   chat-history lists — no way to browse contacts you haven't messaged, no favorites, no
   online-status directory. KakaoTalk's Friends tab is so structurally central that Kakao's own
   Sept 2025 attempt to bury it caused a rating collapse and was reverted within 3 months. itunda's
   "New chat" only accepts a hand-typed phone number today.
   *Target: `SuperAppTabs.kt` `TalkTab`/`TalkView`/`DirectMessagesList` (~200-389); mirror in `ios/App/Sources/TalkScreen.swift` and bank-mfe*

2. **[sourced]** Add a per-message read-receipt countdown — Kakao's most iconic feature. itunda
   only tracks `unreadCount` at the conversation-list level; inside an open thread there's no
   per-message read signal at all. Needs backend support (per-recipient message-read tracking, not
   just conversation-level `unreadCount`) plus the UI badge.
   *Target: `MessageBubble`/`GroupMessageBubble` in `SuperAppTabs.kt` for UI; backend messaging service for the read-state model*

3. **[sourced]** Add a long-press message menu: reply/thread, forward, pin, delete. itunda's only
   per-message interaction today is tap-to-toggle a reaction (`MessageReactionsRow`). Kakao's
   confirmed 2025 toolkit is Copy/Reply (→ thread)/Forward (≤10 destinations)/Pin/Delete/@mention
   — none of this exists in `ChatThreadView`/`GroupThreadView`.

4. **[sourced]** Add swipe actions and per-chat mute/archive ("quiet chat room") to the
   conversation list. `ConversationRow`/`GroupRow` (`~478-498`, `~712-749`) support only tap-to-open.
   Real KakaoTalk supports right-swipe (favorite/notify/pin) and left-swipe (read/leave), plus an
   official archive-without-leaving feature.

5. **[sourced]** Add a per-thread shared-media gallery (Chat Room Drawer). itunda has zero
   aggregation of media/files/links shared in a conversation. Directly relevant to itunda's
   Hood-to-Talk handoff flows (listing photos, offer/gift bubbles).
   *Target: new surface off `ChatThreadView`/`GroupThreadView`*

6. **[sourced]** Add a real attach ("+") menu to the composer. Both thread composers are just a
   text field + send button (plus a gift icon on `ChatThreadView`, `~1016-1027`) — no photo, file,
   or sticker send at all. Kakao's emoticon picker alone is core, monetized product surface (~1/3
   of 2020 revenue), not a nice-to-have.

7. **[sourced]** Add @mention support in group chats. No mention parsing exists anywhere; Kakao's
   own message-toolkit coverage lists mention alongside pin/forward/delete as shipped.
   *Target: `GroupThreadView` header/composer, `GroupMessageBubble` (~500-676)*

8. **[partially-sourced]** Add per-message timestamps — currently entirely absent from both bubble
   types (`MessageBubble ~1236`, `GroupMessageBubble ~679`). Kakao's bubble redesign coverage
   discusses time placement alongside read state, but the exact collapsed-per-run convention
   recommended here is general chat-UI practice, not confirmed pixel-for-pixel from a Kakao
   source.

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

1. **[sourced]** Replace itunda's missing trust signal with a Karrot-Score-style numeric badge —
   **not** a literal manner-temperature metaphor. itunda's Hood cards show no seller/poster
   reputation at all today. Seed Design's real "Manner Temp & Badge" component (10 levels, pill
   variant for cards) is the mechanic to borrow; Karrot's own localization research (dropped the
   temperature/Celsius framing for non-Korean users, replaced with a neutral 0–1000 score starting
   at 30) is the specific choice itunda should copy, since Rwanda is exactly this kind of
   non-Korean market.
   *Target: new `SellerTrustBadge` for `ListingCard`/`JobPostCard`/`PropertyListing` in `SuperAppTabs.kt` (~1685+), mirrored in `HoodScreen.swift`; needs a new trust-score field on `User`*

2. **[sourced]** Add a post-transaction review flow with asymmetric public/private visibility.
   itunda has star reviews for Shop/Eats but Marketplace/Jobs/Property have zero review step —
   "Mark sold" just flips status. Extend the existing `ProductReviewDto`/`StarRatingRow` pattern
   with Karrot's public-good/private-uncomfortable split rather than inventing free-text review.
   *Target: `markListingSold` flow and Job/Property equivalents; template at `~3355-3490`*

3. **[implemented, correction 2026-07-26]** This was miscategorized as still-open — checking the
   actual backend before starting a fresh build (this session's own discipline) found
   `ListingFavoriteService`/`JobPostFavoriteService`/`PropertyListingFavoriteService` already
   real and controller-wired for all three (migrations `V56`/`V74`/`V75`), each mirroring
   `ProductFavoriteService`'s exact add/remove/list shape. Left un-tagged here since this file
   wasn't updated when those shipped. Client UI (toast-confirmed add/remove, a Saved `HoodView`)
   still worth checking separately.

4. **[sourced]** Differentiate community-board post types by interaction verb; give "join
   together" posts a dedicated feed slot. `CommunityContent` (`~1795+`) treats every post
   identically today. Karrot's real board: distinct CTA pairing for questions vs. ordinary posts,
   strictly chronological feed (no popularity ranking, to preserve voice diversity), and a pinned
   mid-feed slot for 같이해요 posts whose group chat requires an explicit join tap.
   *Target: `CommunityContent`/`CommunityPostCard` (~1795-2160)*

5. **[sourced]** Add a persistent edge-fade ("scroll fog") to Hood's scrollable lists. Seed
   Design's Scroll Fog is a specifically documented, always-rendered gradient (15–20% depth, min
   20px) — itunda's feeds have no equivalent hint that content continues below the fold.
   *Target: shared `LazyColumn` styling across Hood's four modes*

6. **[sourced]** Group a user's own activity into labeled sales/purchases/wishlist tabs instead of
   one flat "my listings" list. Karrot's real screen splits this three ways specifically to avoid
   one overloaded list mixing different user intents (cited as an application of Miller's Law).
   *Target: `HoodView` enum, `MINE` branch in `MarketplaceContent` (~1310-1480)*

7. **[partially-sourced]** Replace bare "Loading…" text (5+ instances across Hood's feeds) with
   shaped skeleton placeholders — Seed Design ships named Content Placeholder / Skeleton /
   Identity Placeholder components for exactly this, confirming Karrot's real product uses
   card-shaped placeholders rather than a spinner or text; the itunda-side call sites are enumerated
   but the *visual* skeleton spec wasn't independently pulled beyond the component index.
   *Target: `SuperAppTabs.kt` lines ~592, 900, 3083, 3393, 4105 and iOS equivalents*

8. **[partially-sourced]** Upgrade single-shot, single-neighborhood setup to dual-neighborhood +
   radius control + verification-frequency trust signal. `NeighborhoodSetupPrompt` (`~1324`) is a
   one-time single-GPS-capture with no radius control. Karrot's official CS docs confirm the
   dual-neighborhood and verification-count mechanics; the specific 8–63-area radius-scaling
   figures are secondary-sourced (nuthang.com) and should be treated as illustrative only.
   *Target: `NeighborhoodSetupPrompt`, `bank-mfe/src/lib/neighborhood.ts`, `V50__hyperlocal_neighborhood.sql` (currently one VARCHAR(120) column, no radius/verification fields)*

9. **[sourced] Implemented 2026-07-25.** Add per-listing ownership verification to Property —
   before this, `PropertyListingService.createListing` had zero ownership proof of any kind,
   unlike 당근부동산's real product, which made registry-document verification *mandatory*
   specifically to stop listing fraud. Rwanda has no publicly documented land-registry number
   format to structurally pre-validate the way `DemoNidaVerificationService` does for National
   IDs, so this is honestly scoped as document-upload + human-review only, mirroring
   `KycSubmission`'s real submit/queue/decide shape — no fabricated auto-check against a
   registry this backend has no real access to.
   *Shipped: `PropertyOwnershipSubmission`, `PropertyOwnershipService`, `PropertyListing.ownershipVerificationStatus`, `POST /api/v1/realestate/listings/{id}/verify-ownership`, `/api/v1/system/property-verification/**` (admin queue+decide)*

10. **[sourced]** Replace Jobs' "just message the poster" apply flow with 당근알바's real
    three-step structure: post → review applicants → *then* chat. itunda's `JobPostService`
    has no application/resume object at all today — `contactPoster()` is a bare DM, so a
    poster has no way to review candidates before opening a conversation with each one.
    *Target: new `JobApplication` entity (résumé snapshot per Karrot's real "snapshot at submit
    time" mechanic), `JobPostService.contactPoster` → gated behind an application review step*

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

1. **[sourced]** Wire itunda's own existing cross-merchant product-search endpoint into Android
   and iOS. This is an itunda-internal inconsistency, not a gap against Coupang/Naver:
   `GET /api/v1/shopping/products/search` already exists on the backend, and bank-mfe already has a
   working "search across every merchant" bar wired to it (`BankDashboard.tsx:~5340`) — matching
   what every real marketplace treats as table stakes. Android's `ShopTab` and iOS `ShopScreen`
   never call it at all.
   *Target: `SuperAppTabs.kt` `CommerceShopContent`; `ios/App/Sources/ShopScreen.swift` `CommerceShopContent`*

2. **[sourced]** Surface the already-shipped category/search filter params in Shop's own merchant
   browse UI. `GET /api/v1/shopping/merchants` already accepts `category`/`q`, and Eats already uses
   both in the *same file* (`~3620`) — Shop's own `CommerceShopContent` calls the endpoint with no
   params and shows no search box or chips at all. The backend capability and UI pattern both
   already exist in itunda's own codebase.
   *Target: `SuperAppTabs.kt` `CommerceShopContent` (~2903-3048), mirror Eats' pattern a few hundred lines below; `ShopScreen.swift`*

3. **[sourced]** Extend product wishlist ("찜") from bank-mfe-only to Android and iOS. bank-mfe
   already has `WishlistButton`/`WishlistView` backed by real endpoints; Android only has favorites
   for Eats restaurants, iOS Shop has none. This is extending itunda's own precedent, not adopting
   a new external pattern.
   *Target: `CommerceShopContent`/`MerchantDetailView` — reuse the `FavoriteRestaurantDto` pattern; `ShopScreen.swift` `MerchantDetailView`*

4. **[partially-sourced]** Give `MerchantProduct` real images and a discount-price pair. Coupang's
   documented data model and Baymard's research both confirm image + current/original price is the
   baseline for a real product card; itunda's `MerchantProduct` entity has neither — a schema-level
   blocker, not just a UI gap.
   *Target: `services/backend/core/.../MerchantProduct.kt` (add `imageUrl`, `originalPrice`/`discountPercent`, `description`); propagate through `MerchantProductController.kt`, `ShoppingController.kt`, and all three client DTOs*

5. **[partially-sourced]** Switch merchant/product browse from single-column list to a 2-column
   image-led grid. Chloe Youn's case study confirms Coupang's real cards carry quick add-to-cart
   and wishlist directly on a grid card; itunda's three clients render text-only full-width rows
   with a generic storefront icon.
   *Target: `CommerceShopContent` merchant list (~3016) and `MerchantDetailView` product list (~3078) in `SuperAppTabs.kt`; `browseBody`/`MerchantDetailView` in `ShopScreen.swift`; `ProductCatalogView` in `BankDashboard.tsx` (~4850)*

6. **[partially-sourced]** Add a dedicated product detail screen with inline variant/qty
   selection. itunda has none anywhere — tapping a product only reveals an inline qty stepper in
   the flat catalog list. Coupang's redesign implies a real detail page exists to have replaced a
   multi-step flow with an inline selector on. Naver Smart Store's tab structure was sourced only
   from secondary description sites — weaker sourcing, flagged.
   *Target: new `ProductDetailView` alongside `MerchantDetailView` in all three clients*

7. **[partially-sourced]** Move add-to-cart and wishlist onto the list/grid card itself (depends
   on the grid layout landing first). Chloe Youn names this as a specific, deliberate Coupang
   improvement.

8. **[inferred]** Add merchandising modules (banner/promo carousel, category shortcuts, curated
   deal rails) to the Shop landing surface, above the raw item list. This is general,
   widely-documented Coupang/Naver home-surface structure but was **not independently re-verified**
   this pass beyond general knowledge — flagged as the weakest-sourced recommendation in this
   section.
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

### Unresolved / worth a follow-up

- Naver Smart Store's exact detail-page tab structure (상세정보/리뷰/Q&A/Story) wasn't confirmed
  against a primary Naver source — only secondary description sites.
- No official Coupang design-system page or engineering blog describing real card anatomy
  (image placement, badge stacking, aspect ratio) was reachable — Chloe Youn's case study is real
  and named but is a third-party redesign analysis, not Coupang's own documentation.
- Naver's real filter UX (facet sidebar, price-slider specifics, sort options beyond lowest-price)
  wasn't confirmed with a strong primary source.
- Didn't check whether Hood's Market surface already has grid/image-card patterns reusable for
  Shop before building new ones — worth a quick in-repo check first.
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

1. **[sourced]** Build a Talk-chat-embedded split-bill/settlement feature — itunda has none today
   (confirmed via grep across `docs/TOSS_PARITY_MATRIX.md`: Group Account and Gift both exist and
   are marked "real," nothing named split/dutch/settlement is). KakaoPay's real 정산하기 is
   structurally richer than Toss's own 더치페이: chat-embedded entry point (same "+" family as
   transfer), randomized ladder-game split with adjustable intensity, up to 5 tracked rounds,
   silent rounding-remainder absorption, scheduled reminder nudges, 3-photo receipt attach. Since
   itunda's own Gift feature was deliberately built as "a real chat message rather than a separate
   notification surface" (per `GiftService.kt`'s own doc comment), the chat-embedded Kakao pattern
   — not Toss's spending-history-anchored one — is the natural fit for itunda's Talk-first
   architecture. The ladder-game/multi-round/reminder/rounding mechanics are the genuine
   differentiators worth porting; a plain even-split alone would just be re-doing Toss.
   *Target: new backend module analogous to `services/backend/gift` (reusing `GiftService`'s escrow-ledger conventions), surfaced as a chat action in bank-mfe's Talk thread (mirror `lib/gift.ts`), Android `SuperAppTabs.kt`/`ItundaAppScreen.kt`, iOS `TalkScreen.swift`*

2. **[sourced]** Add a Kakao-Bank-style auto-escalating, day-locked weekly savings product
   (26주적금 pattern) as a distinct gamified product type, sibling to the already-shipped Group
   Account. Structurally distinct from a generic recurring deposit: escalating auto-debit amount,
   hard-locked opening weekday, per-installment interest computation, and a streak-gated
   preferential rate. itunda has the Group Account mechanic already sourced and live but nothing
   for this second, differently-gamified KakaoBank product.
   *Target: `services/backend/savings` module, sibling to `GroupAccountService.kt`; surfaced wherever Group Account currently lists*

3. **[partially-sourced]** Give itunda's existing Gift feature themed "envelope" presets instead
   of (or alongside) free-text notes. itunda's Gift already matches KakaoPay's core structure well
   (escrow-then-explicit-claim, inline chat bubble, 7-day auto-expiry-refund). What's missing is
   송금봉투: the envelope itself is a themed preset that communicates occasion without typed text —
   the envelope *is* the message. The specific 4-name preset list came from a search-engine summary
   of Kakao's help content, not a re-verified primary catalog page, so treat the exact preset names
   as illustrative.
   *Target: `services/backend/gift/.../Gift.kt` (add an envelope-theme field), `bank-mfe/src/lib/gift.ts` + gift bubble rendering, Android gift-send sheet, iOS gift-send flow*

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

- **Skeleton/placeholder loading state.** The "Loading…" text-row problem was flagged specifically
  in Hood (5+ call sites) but is visibly the same anti-pattern anywhere itunda shows a bare loading
  string instead of a shaped placeholder. A single `ContentPlaceholder`/`Skeleton` composable
  (Seed Design's named pattern) belongs in itunda's shared IDS layer, not rebuilt per-tab.

- **Wishlist/heart affordance + toast-confirmed add/remove.** This pattern is independently
  recommended for Hood listings and Shopping products, and already exists for Community posts and
  Eats restaurant favorites. It should be one shared favorite/heart component with a shared toast
  copy convention ("saved"/"removed from interest list"), backed by one generalized
  favorite-entity backend shape, rather than four separate bespoke favorite implementations (which
  is close to what exists today — bank-mfe wishlist, Android Eats favorites, Community likes are
  all separate).

- **Chat-embedded financial action pattern ("+" menu → Gift / future Split-bill / future
  transfer).** Both Gift (shipped) and the recommended Split-bill feature (Section 6) share the
  same structural idea: a financial action initiated from inside a Talk thread, rendered as an
  inline chat bubble, with an explicit-claim (not auto-credit) step. These should share one
  "chat-embedded money action" shell — the composer's attach affordance, the escrow/claim bubble
  rendering, the expiry-refund plumbing — with Gift and Split-bill as two instances of it, not two
  independently-built features.

- **Search-with-filter-chips pattern.** Eats already has category chips + search input built;
  Shop's own browse UI is missing the identical pattern despite the backend already supporting it.
  This should be one shared "merchant/product browse header" component (search field + horizontal
  chip row) used by both Eats and Shop, not reimplemented per tab — the recommendation in Section 5
  is explicitly to reuse Eats' existing pattern rather than invent a new one for Shop.

- **Product/listing card "quick action without opening detail"** (add-to-cart + wishlist on Shop
  cards; save/heart on Hood listing cards). The underlying interaction — act on an item from the
  list without navigating away — is the same mechanic Coupang applies to Shop and Karrot applies
  to Hood listings independently. The visual card can and should look different per surface
  (grocery/product card vs. secondhand-listing card), but the *tap targets and feedback pattern*
  (inline quantity stepper / heart toggle with toast) should be one shared interaction contract.

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

1. **[sourced]** Add a real, honest phone-verification step at registration, delivered the same
   way itunda's existing email verification already is (in-app `Notification`, since no SMTP/SMS
   relay exists — see `AuthService.kt:240-244`'s own comment). This is the single most
   Toss-aligned, currently-real gap: Toss's whole real-name-verification model is built on proven
   phone possession; itunda currently accepts any unverified number. Mirror
   `requestEmailVerification`/`confirmEmailVerification` (`AuthService.kt:245-282`) field-for-field
   for phone, rather than inventing a new pattern.
   *Target: `AuthService.kt:245-282` (pattern to mirror), new `requestPhoneVerification`/
   `confirmPhoneVerification`, `AuthController.kt`, all 3 clients' registration screens*

2. **[sourced]** Reuse itunda's own existing biometric primitive as a login/app-launch quick-unlock,
   not just a transaction step-up gate. Toss's real login credential is a 6-digit PIN or Face ID,
   never a conventional password — itunda already built the exact crypto/Keystore plumbing this
   needs (`NIDABiometricAuth.kt`/`.swift`) and already uses it for transfers; extending it to gate
   app-launch/resume (on top of an already-issued session token, not a full re-login) is almost
   pure reuse, no new external dependency, no schema change.
   *Target: `NIDABiometricAuth.kt`/`.swift` (existing), new call site at app launch/foreground in
   `ItundaAppScreen.kt`/`ContentView.swift`, a settings toggle to enable/disable*

3. **[sourced]** Add a passport-based identity-verification path for foreign residents, mirroring
   Toss's identical pattern for both foreign residents and 14+ teens (one alternative-identity
   mechanism, reused for two segments, not two separate builds). itunda's `IdentityService`
   explicitly rejects `PASSPORT` today; Rwanda has significant cross-border residency (Congolese,
   Burundian, Ugandan, Kenyan) with no NIDA number to submit. This also unblocks the "no consumer
   KYC UI exists in the main app at all" gap found during this pass, since building the first real
   consumer-facing identity-verification screen would need to happen either way.
   *Target: `IdentityService.kt`, `DemoNidaVerificationService.kt` (new demo passport-verification
   path, same "structural validation + simulated match, honestly labeled" convention already used
   for National ID), first consumer KYC UI on all 3 clients*

4. **[sourced]** Apply "Easy to Answer" (Toss's own principle, quantified 60%/50% drop-off
   reduction in a real case study) to itunda's own highest-friction moment found this pass: KYC
   submission has no consumer UI at all, so there's no current screen to audit — but the principle
   should shape whatever screen recommendation 3 above produces: prefer a concrete yes/no framing
   ("do you have your ID or passport with you?") over an open question, and test the imperative-
   vs-question microcopy distinction the senior-usability research also independently confirms.

5. **[sourced]** Inherit full native OS font-scaling rather than building (or not building) any
   in-app text-size toggle. Currently zero font-scaling override exists in any client — meaning
   itunda's SwiftUI/Compose text should already inherit the OS setting by default unless something
   is explicitly overriding it; this recommendation is really "audit for accidental fixed-size
   text and fix it," not "build a new feature," matching Toss's own finding that full OS-native
   scaling beats a limited in-app toggle.
   *Target: audit `sp`/fixed-size `Text()` calls across `SuperAppTabs.kt` and SwiftUI `.font()`
   modifiers for hardcoded sizes that don't respond to system text-size settings*

6. **[partially-sourced]** Consider a teen/youth product tier — the biggest lift of any
   recommendation here, requiring a new `User.dateOfBirth`/age field (doesn't exist today),
   parental-consent/linking design, and spend-cap enforcement, none of which itunda has any
   foundation for yet. Toss's own three-tier structure (Youth Home for 7-13, USS Card for 7-16,
   passport-based self-service for 14+) is real and sourced, but porting it is a multi-session
   effort, not a single closable gap — flagged as a real opportunity, not a next action.

7. **[inferred, itunda-internal]** Build a real registration UI for bank-mfe. Unrelated to Toss
   research directly, but a glaring itunda-internal inconsistency found during this pass — the web
   client has no sign-up page at all, unlike Android/iOS.
   *Target: new `services/micro-frontends/bank-mfe/src/RegisterPage.tsx`, mirroring `LoginScreen.kt`/
   `.swift`'s existing field set*

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

**Added:** 2026-07-21, following the sign-up/simplicity research above. This area is genuinely
thinner in Toss's own public documentation than their UX-process writing — most claims below trace
to 2-3 Toss Feed/Toss Tech articles rather than a dense body of material, flagged honestly rather
than padded.

### References

| Topic | Source | Pattern |
|---|---|---|
| Icon system | developers-apps-in-toss.toss.im/design/resources.html | Icons used at 24-40px; explicit rule to **never combine multiple icons side by side** ("use only one at a time"); 7,000+ icons/emoji available via AppBuilder/Figma; decorative icons marked non-readable for screen readers |
| Illustration style rules | developers-apps-in-toss.toss.im/design/resources.html | Explicit target aesthetic: "simple, clear, clean digital graphic style" — hand-drawn look, "lyrical/sentimental" painting styles, and cartoonish expression are all explicitly ruled out as feeling out of place; graphics must read correctly in both dark and light mode using mid-tone colors |
| Illustration's product role | toss.im/tossfeed/article/graphicdesign-team-interview | Graphics evolved from "seasoning" to a core problem-solving tool; a 3,600-emoji custom set ("Toss Graphic Universe") built around deliberately non-clichéd, original metaphors rather than stock financial iconography; named designers for systemic coherence, content graphics, and 3D/hero visuals |
| Illustration at friction points | toss.im/tossfeed/article/why-motion-in-finance | Cute/friendly animation deliberately frames uncomfortable moments (network errors, terms-agreement screens) as "less daunting"; confetti-style animation celebrates positive moments (credit score up, payday) — a documented, named tie to a peak-end-style softening effect |
| Brand color, re-examined | toss.tech/article/43061 | Toss's own internal research found users did **not** spontaneously recall the brand as "blue" alone; what people actually retained was the combination of white background + blue logo + the square app-icon frame + bold black type — Toss reoriented brand strategy around that three-part signature, not the color alone |
| Motion/interaction principles | toss.tech/article/interaction, toss.im/tossfeed/article/why-motion-in-finance | Five stated design principles: express with one movement instead of many words; emotional softening; resource-efficient 3D-to-Lottie layering; richer 3D reserved for web/social; interactive/manipulable 3D objects. A shared cross-platform motion library ("Rally") standardizes easing tokens. Interactions are justified internally with metrics, not aesthetics — a redesigned loan-approval loading screen (previously static) measurably improved engagement once made interactive |
| Tossface (custom emoji font) | toss.im/tossface | Six explicit rules: simplest form for small-size legibility, uniform sizing across all 3,600 glyphs, one unified color palette, all directional emoji face right, all perspective objects use a fixed 45° angle, all 3D objects share one viewing height |
| First graphic designer's own account | toss.tech/article/1st-graphic-designer | Toss's first graphic designer describes reversing an early "remove all rounded corners" rule, prioritizing one polished, on-brand icon set over broad coverage rather than chasing completeness |

### itunda's current state (verified, not assumed)

- **Zero illustrations exist anywhere in itunda.** Every empty state found across this session's
  own work (Marketplace, Shop, Eats, Talk, Community, Jobs, Property) is bare text — `Text("No
  listings yet.")`, `Text("No merchants registered yet.")`, `Text("No saved listings yet -- tap ♡
  ...")`, and so on. This is the single largest, currently-zero-effort gap in this section: Toss
  uses custom illustration/emoji specifically at exactly these moments (empty states, errors,
  onboarding) to communicate faster than text and soften friction — itunda has never done this
  once, anywhere, on any client.
- Icon usage is otherwise reasonably disciplined already (Material/SF Symbols icons used singly per
  row/button across the codebase, not stacked) — no confirmed violation of the "one icon at a time"
  rule was found, though this wasn't exhaustively audited.
- itunda's own single-blue-brand decision (Section 0 of this document) already independently
  arrived at a "one consistent color across the whole app" position — Toss's own brand-research
  finding above (users recall the *signature*, not the color alone) is a real, sourced reason to
  extend that thinking to itunda's white-background + blue-accent + wordmark combination as the
  actual recognizable signature, not the blue alone.

### Recommendations (ranked)

1. **[sourced]** Build a real shared empty-state component and use it everywhere itunda currently
   shows bare text. This is the highest-leverage, lowest-risk recommendation in this section: no
   backend change, no new dependency, closes a gap that exists identically across every surface.
   Scoped honestly: this should be a simple icon-in-a-soft-circle + title + subtitle composition
   using itunda's own existing icon set and color tokens (`Ids.colors.chip`/`brand`), matching
   Toss's own documented style rule ("simple, clear, clean... not cartoonish, not hand-drawn") —
   **not** an attempt at Toss's full custom-character illustration work, which is genuine
   in-house-illustrator asset production this doesn't have the input to match honestly.
   *Target: one shared `EmptyState` composable/View/component per platform, replacing the bare
   `Text(...)` empty-state calls across `SuperAppTabs.kt`, `HoodScreen.swift`/equivalent iOS
   screens, and `BankDashboard.tsx`*

2. **[sourced]** Apply the same friction-softening idea to itunda's error states, not just empty
   states — Toss's own documented reasoning (cute framing makes network errors/terms screens "less
   daunting") applies directly to itunda's existing `ErrorCard`/inline error rows, which are
   currently plain red text with a "Retry" link. A shared visual treatment (same icon-based
   language as the empty-state component, not full custom illustration) would apply this principle
   without a second, disconnected design language.
   *Target: `ErrorCard` (`SuperAppTabs.kt`) and its iOS/bank-mfe equivalents*

3. **[partially-sourced]** Consider itunda's own equivalent of a Tossface-style rule set for any
   future custom iconography/emoji work — Toss's six explicit consistency rules (uniform sizing,
   one palette, fixed viewing angles) are a real, reusable checklist if itunda ever commissions
   custom icon/illustration assets, even though itunda has no such assets today. Lower priority
   than items 1-2 since it has no current call site — a checklist for *when* this work happens,
   not a thing to build now.

### Unresolved / worth a follow-up

- No official TDS icon naming convention or full size-grid specification was found beyond the
  24-40px screen-use rule.
- No official documented hex/token values for "Toss blue," or a stated single-brand-color rationale
  in Toss's own words — the "blue signals trust for a finance brand" reasoning found only in
  secondary marketing-blog commentary, not confirmed as Toss's own stated reasoning.
- This area's overall thinness is itself worth noting for any future research pass: illustration
  and motion design are documented far more sparsely in Toss's public engineering/product writing
  than their UX-research process is — a follow-up pass would likely need to rely more heavily on
  direct screenshot/app teardown analysis than on primary-source blog articles.
