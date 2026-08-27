import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { ShoppingMerchant } from './shopping';

// Real Coupang Eats-style food delivery (rw.itunda.eats, 2026-07-18) -- restaurant
// browsing/menus deliberately reuse the existing shopping endpoints below (a restaurant
// IS a Merchant, a menu item IS a MerchantProduct -- see EatsOrderService.kt's own doc
// comment), only order placement/tracking and the rider workflow are genuinely new.

// Real category/search filter (2026-07-19) -- both optional and combinable. See
// ShoppingController.getEligibleMerchants's own doc comment on the backend.
//
// Real browse-card enrichment (2026-07-21) -- optional buyerLat/buyerLng backs a real
// distanceKm + deliveryTimeMinutes estimate per restaurant; see ShoppingMerchant's own
// doc comment in lib/shopping.ts for the full field account.
// Real fix (2026-08-13, direct user report against a live screenshot): this list
// showed Electronics/Fashion merchants alongside real restaurants, since it shares
// the exact same unfiltered Merchant directory Shop's own browse uses -- see
// ShoppingController.getEligibleMerchants's own doc comment. businessType=RESTAURANT
// scopes this to real food merchants only, the same fix applied server-side and on
// Android.
// Real Baemin/Coupang Eats-style "fastest delivery" sort tab (2026-08-16) -- only
// takes effect when buyerLat/buyerLng are also supplied (see backend
// ShoppingController.getEligibleMerchants's own doc comment for why).
// 'favorites' added 2026-08-16 -- real Baemin 찜순 sort, needs no buyer location.
// 'rating'/'distance' added 2026-08-19 -- real Coupang Eats-style 별점순/거리순 sort;
// both were already computed per-row on the backend for display, just never sortable.
// 'distance', like 'delivery_time', only takes effect with a real buyerLat/buyerLng.
// 'discount'/'min_order' added 2026-08-28 (itunda Eats redesign, adapting the real
// Coupang Eats quick-filter chip row -- 최대할인/최소주문낮은매장 -- onto itunda's own
// real per-merchant signals rather than a fabricated curated chip) -- see
// ShoppingMerchantBrowseService.browse's own doc comment on both sort modes.
export type RestaurantSortMode = 'delivery_time' | 'favorites' | 'rating' | 'distance' | 'discount' | 'min_order';
export const fetchRestaurants = (category?: string, q?: string, buyerLat?: number, buyerLng?: number, sortBy?: RestaurantSortMode) => {
  const params = new URLSearchParams();
  params.set('businessType', 'RESTAURANT');
  if (category) params.set('category', category);
  if (q) params.set('q', q);
  if (buyerLat != null && buyerLng != null) {
    params.set('buyerLat', String(buyerLat));
    params.set('buyerLng', String(buyerLng));
  }
  if (sortBy) params.set('sortBy', sortBy);
  // Real bug found live (2026-07-20): this call never set a page size, so it silently
  // took the backend's own default of 20 -- fine while the catalog was small, but a
  // real restaurant past the 20th spot would then be missing from this list entirely,
  // breaking any UI that cross-references an order's restaurant by id (the delivery
  // route button, and the new live rider-tracking map). A real "browse everything"
  // list wants every real active restaurant, not a silently-truncated page of them.
  params.set('size', '100');
  const qs = params.toString();
  return apiFetch<{ success: boolean; merchants: ShoppingMerchant[] }>(`/api/v1/shopping/merchants${qs ? `?${qs}` : ''}`).then(
    (r) => r.merchants,
  );
};

// Real distinct category list, derived from real merchant data -- see
// MerchantRepository.findDistinctCategories's own doc comment.
export const fetchRestaurantCategories = () =>
  apiFetch<{ success: boolean; categories: string[] }>('/api/v1/shopping/merchants/categories?businessType=RESTAURANT').then(
    (r) => r.categories,
  );

// Real Coupang Eats-style dish grid (itunda Eats redesign, 2026-08-28) -- see backend
// EatsController.getDishes' own doc comment. Fully built since 2026-08-03, zero web/
// Android/iOS client anywhere until now (found via a real grep sweep before building
// this). `recommended` is real, not fabricated -- true only when the buyer has
// actually ordered from that dish's own restaurant before (a real personal-history
// signal, distinct from the neighborhood-popularity rail below).
export interface RecommendedDish {
  id: string;
  merchantId: string;
  merchantName: string;
  name: string;
  price: number;
  imageUrl: string | null;
  recommended: boolean;
}

export const fetchRecommendedDishes = (sortBy?: 'popular') =>
  apiFetch<{ success: boolean; dishes: RecommendedDish[] }>(`/api/v1/eats/dishes?size=20${sortBy ? `&sortBy=${sortBy}` : ''}`).then((r) => r.dishes);

// Real "frequently ordered together" cross-sell (itunda Eats redesign, 2026-08-28) --
// see backend OrderItemRepository.getFrequentlyOrderedWith's own doc comment. Real
// co-purchase data only; an empty real list means genuinely no real pair has cleared
// the minimum co-occurrence threshold yet, never padded with unrelated products.
export interface FrequentlyOrderedWithItem {
  id: string;
  merchantId: string;
  merchantName: string;
  name: string;
  price: number;
  imageUrl: string | null;
  originalPrice: number | null;
  discountPercent: number | null;
  stockQuantity: number | null;
}

export const fetchFrequentlyOrderedWith = (productId: string) =>
  apiFetch<{ success: boolean; products: FrequentlyOrderedWithItem[] }>(`/api/v1/shopping/products/${productId}/frequently-ordered-with`).then(
    (r) => r.products,
  );

// Real menu-item option groups (2026-07-21, v1: required single-select only) -- closes
// docs/DESIGN_REFERENCES.md's Eats recommendation #3, the single biggest structural
// gap: itunda previously had no way to represent size/spice-level/add-on choices at
// all. See MenuOptionGroup.kt's own doc comment on the backend for the full,
// honestly-scoped account.
export interface MenuOptionChoice {
  id: string;
  name: string;
  priceDelta: number;
}
export interface MenuOptionGroup {
  id: string;
  name: string;
  choices: MenuOptionChoice[];
  // Real optional/multi-select support (itunda Eats redesign, 2026-08-28) -- the
  // shared ShoppingController.getMerchantProducts response has always returned
  // these two real fields (see backend MenuOptionGroup.kt's own doc comment on the
  // 4 real group combinations), this client type just never declared them, so
  // every group rendered as a required-single-select radio regardless of its real
  // shape.
  required: boolean;
  multiSelect: boolean;
}

export interface MenuItem {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
  optionGroups?: MenuOptionGroup[];
  // Real Baemin CEO app/DoorDash-style "86" (temporarily sold out) flag (2026-08-16) --
  // see backend MerchantProduct.soldOut's own doc comment. Shown, not filtered out
  // (unlike `active`), so a buyer sees WHY the item can't be added right now.
  soldOut?: boolean;
  // Real per-dish discount + "Best seller" badge (itunda Eats redesign, 2026-08-28) --
  // the shared ShoppingController.getMerchantProducts response has always returned
  // these real fields (see lib/shopping.ts's MerchantProductDto-equivalent), this
  // client type just never declared them, so Eats' own menu never rendered a discount
  // badge despite Shop's identical catalog already having one. Real merchant-set
  // originalPrice/server-computed discountPercent, real order-count-derived
  // isBestSeller -- see ShoppingController.bestSellerProductIds' own doc comment.
  originalPrice?: number | null;
  discountPercent?: number | null;
  isBestSeller?: boolean;
}

export const fetchMenu = (restaurantId: string) =>
  apiFetch<{ success: boolean; merchant: { id: string; businessName: string }; products: MenuItem[] }>(
    `/api/v1/shopping/merchants/${restaurantId}/products`,
  );

export type EatsOrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP' | 'RIDER_ASSIGNED' | 'PICKED_UP' | 'DELIVERED' | 'CANCELLED';

export interface EatsOrderItem {
  id: string;
  orderId: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  // Real menu-options receipt breakdown (2026-07-21) -- see EatsOrderItem.kt's own doc
  // comment. unitPrice above already includes every selected choice's priceDelta; this
  // is purely a human-readable summary, never a second pricing source.
  selectedOptionsJson?: string | null;
}

export interface EatsOrder {
  id: string;
  buyerId: string;
  restaurantId: string;
  riderId: string | null;
  deliveryAddress: string;
  itemsSubtotal: number;
  deliveryFee: number;
  platformFee: number;
  totalAmount: number;
  // Real Baemin-style tiered order-amount promotion (2026-08-16) -- itunda-funded, not
  // restaurant-funded. See EatsPromotionCalculator's own doc comment on the backend.
  // Zero for every order below the lowest real tier.
  promotionDiscount: number;
  transactionId: string;
  deliveryPayoutTransactionId: string | null;
  status: EatsOrderStatus;
  createdAt: string;
  updatedAt: string;
  deliveryNotes: string | null;
  // Real optional delivery coordinates + OSRM road distance (2026-07-18) -- backs the
  // real distance-based delivery fee. See EatsOrderService's own doc comment.
  deliveryLatitude?: number | null;
  deliveryLongitude?: number | null;
  distanceKm?: number | null;
  // Real Baemin-style 포장주문 (Pickup) order type (2026-07-26) -- see
  // EatsOrderService.placeOrder's own doc comment. A PICKUP order has riderId: null
  // for its whole lifecycle, so a review of one has no rider to rate -- see
  // ReviewOrderCard's own use of this field.
  fulfillmentType?: 'DELIVERY' | 'PICKUP';
  // Same real gap this session already found on RideTrip.tipAmount -- the backend has
  // always returned this (EatsOrder.tipAmount), but this client type never declared
  // it. See EatsOrderService.tipRider's own doc comment (real Uber Eats post-delivery
  // tip).
  tipAmount?: number | null;
}

export const placeEatsOrder = (
  restaurantId: string,
  items: { menuItemId: string; quantity: number; selectedChoiceIds?: string[] }[],
  deliveryAddress: string,
  deliveryLatitude?: number,
  deliveryLongitude?: number,
  deliveryNotes?: string,
  // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- the backend has
  // supported this since 2026-07-26 (EatsOrderService.placeOrder defaults to
  // DELIVERY), but no client anywhere ever passed anything else, so a buyer could
  // never actually choose it. Defaults to DELIVERY, preserving every existing call
  // site's exact current behavior unchanged.
  fulfillmentType: 'DELIVERY' | 'PICKUP' = 'DELIVERY',
) =>
  apiFetch<{ success: boolean; order: EatsOrder; items: EatsOrderItem[] }>('/api/v1/eats/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ restaurantId, items, deliveryAddress, deliveryLatitude, deliveryLongitude, deliveryNotes, fulfillmentType }),
  });

export const fetchMyEatsOrders = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/my-orders').then((r) => r.orders);

// Real order detail, including items -- backs the real "Reorder" button (2026-07-19):
// a buyer can re-populate a cart from a past order's real items rather than retyping
// their whole order from scratch.
export const fetchEatsOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder; items: EatsOrderItem[] }>(`/api/v1/eats/orders/${orderId}`);

export const fetchRestaurantOrders = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/restaurant-orders').then((r) => r.orders);

export const advanceRestaurantOrder = (orderId: string, status: EatsOrderStatus) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

// Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- a PICKUP order has no
// rider, so RESTAURANT_STATUS_CHAIN's own READY_FOR_PICKUP -> (rider takes over) path
// never applies; the restaurant confirms the buyer actually collected it instead. See
// EatsOrderService.completePickup's own doc comment.
export const completePickupOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/complete-pickup`, {
    method: 'POST',
  }).then((r) => r.order);

// Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders only,
// safely before any rider is ever involved. See EatsOrderService.cancelOrder's own doc
// comment for the full backend account.
export const cancelEatsOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/cancel`, {
    method: 'POST',
  }).then((r) => r.order);

// Real live rider-location tracking (2026-07-19 backend, 2026-07-20 first UI) -- "the
// defining 'watch your order arrive' moment every real Coupang Eats/Uber Eats-style app
// has," per EatsOrderService.getRiderLocation's own doc comment. `available: false` (not
// an error) is the real, honest response whenever there's genuinely nothing to show yet
// (no rider assigned, order already delivered/cancelled, or the assigned rider hasn't
// pushed a location yet) -- never a fabricated position.
export interface RiderLocation {
  latitude: number;
  longitude: number;
  updatedAt: string;
}

export const fetchRiderLocation = (orderId: string) =>
  apiFetch<{ success: boolean; available: boolean; location: RiderLocation | null }>(`/api/v1/eats/orders/${orderId}/rider-location`);

// Real "message restaurant" (2026-08-16, Uber Eats-sourced Live Order Chat) -- reuses
// the exact same messaging system (see lib/messaging.ts) under the hood, same shape as
// lib/marketplace.ts's own contactSeller; the returned conversation id is a genuine
// messaging conversation id, openable straight into the Messages tab's real chat thread.
export const contactRestaurant = (orderId: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>(`/api/v1/eats/orders/${orderId}/contact-restaurant`, {
    method: 'POST',
  }).then((r) => r.conversation);

// The rider-role half of Eats (Rider type, registerRider/fetchMyRiderProfile/
// setRiderAvailability/fetchAvailableDeliveries/fetchRiderDeliveries/claimDelivery/
// advanceRiderOrder) moved to lib/eatsRider.ts (2026-08-20, real file-size-lint
// threshold crossed) -- genuinely distinct from everything else in this file, none
// of it ever called by a buyer or restaurant owner.

// Real post-delivery ratings & reviews (2026-07-18) -- the single biggest remaining
// Coupang Eats-defining gap, added at the user's direct request. See
// EatsReviewService.kt's own doc comment for the full backend account.

export interface EatsReview {
  id: string;
  orderId: string;
  buyerId: string;
  restaurantId: string;
  riderId: string | null;
  restaurantRating: number;
  restaurantComment: string | null;
  riderRating: number | null;
  riderComment: string | null;
  // Real owner-side reply (2026-07-26) -- see EatsReviewService.replyToRestaurantReview's
  // own doc comment.
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
  // Real Baemin/Coupang-style "도움돼요" (helpful) count -- see backend
  // EatsReviewService.toggleHelpful's own doc comment. Same silent-discard shape as
  // EatsOrder.tipAmount above: the backend has always returned this, this client
  // type never declared it.
  helpfulCount?: number;
  // Real review photo (itunda Eats redesign, 2026-08-28) -- see backend
  // EatsReview.photoUrl's own doc comment (migration V224). Same silent-discard
  // shape as helpfulCount above: real end-to-end on the backend (submit + read),
  // this client type just never declared it, so a real submitted photo was never
  // rendered anywhere. Null/undefined means the reviewer genuinely didn't attach one.
  photoUrl?: string | null;
}

export interface RatingSummary {
  average: number | null;
  count: number;
}

// riderRating/riderComment are optional (2026-07-26) -- a real Baemin-style PICKUP
// order review has no rider to rate; see EatsReview.kt's own doc comment for the full
// account of the real bug this fixes (every PICKUP order was previously unreviewable).
// Real Uber Eats post-delivery tip -- see backend EatsOrderService.tipRider's own doc
// comment. Found via scripts/uncalled-endpoint-sweep.py: fully built (real
// already-tipped guard, real TIP_WINDOW, real account-to-account ledger legs) with zero
// client anywhere, mirroring the real gap this session already closed for
// RideTripService.tipDriver. Real backend shape: no Idempotency-Key required here
// (unlike the ride tip), matching this exact endpoint's own real signature.
export const tipEatsOrderRider = (orderId: string, amount: number) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/tip`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  }).then((r) => r.order);

// Real Baemin/Coupang-style "도움돼요" (helpful) idempotent toggle -- see backend
// EatsReviewService.toggleHelpful's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built with zero client anywhere.
// Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
// EatsReviewService.reportReview's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built (own-review guard,
// already-reported guard, real reporter-count auto-hide threshold) with zero client
// anywhere. Genuinely NOT covered by the existing generic HoodReportButton
// (lib/hoodReport.ts) -- that mechanism's 4 real targets are
// MARKETPLACE_LISTING/COMMUNITY_POST/JOB_POST/PROPERTY_LISTING, no REVIEW target
// exists there at all, so this needs its own real client, not a route-through.
export type EatsReviewReportReason = 'DEFAMATION' | 'PERSONAL_INFO_EXPOSURE' | 'OBSCENE_OR_VIOLENT' | 'UNRELATED_ABUSE' | 'OTHER';

export const reportEatsReview = (reviewId: string, reason: EatsReviewReportReason, details?: string) =>
  apiFetch<{ success: boolean; report: { id: string } }>(`/api/v1/eats/reviews/${reviewId}/report`, {
    method: 'POST',
    body: JSON.stringify({ reason, details: details?.trim() || null }),
  });

export const toggleReviewHelpful = (reviewId: string) =>
  apiFetch<{ success: boolean; helpful: boolean }>(`/api/v1/eats/reviews/${reviewId}/helpful`, { method: 'POST' }).then((r) => r.helpful);

export const submitEatsReview = (
  orderId: string,
  restaurantRating: number,
  restaurantComment: string,
  riderRating: number | null,
  riderComment: string,
  // Real optional review photo (itunda Eats redesign, 2026-08-28) -- see
  // EatsReview.photoUrl's own doc comment; the backend has taken this since
  // 2026-08-04, no web client ever sent it. Same "bring your own already-hosted
  // URL" bar as Merchant.photoUrl elsewhere in this codebase -- itunda has no
  // image-upload/storage pipeline to invent one.
  photoUrl?: string,
) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify({
      restaurantRating,
      restaurantComment: restaurantComment.trim() || null,
      riderRating,
      riderComment: riderRating == null ? null : riderComment.trim() || null,
      photoUrl: photoUrl?.trim() || null,
    }),
  }).then((r) => r.review);

export const fetchRestaurantRating = (restaurantId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/eats/restaurants/${restaurantId}/rating`).then(
    (r) => ({ average: r.average, count: r.count }) as RatingSummary,
  );

export const fetchRestaurantReviews = (restaurantId: string) =>
  apiFetch<{ success: boolean; reviews: EatsReview[] }>(`/api/v1/eats/restaurants/${restaurantId}/reviews`).then((r) => r.reviews);

// Real owner-side reply (2026-07-26 backend, first client 2026-07-29, item 184) -- see
// EatsReviewService.replyToRestaurantReview's own doc comment. Restaurant-owner only,
// enforced server-side; one editable reply per review.
export const replyToRestaurantReview = (reviewId: string, reply: string) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/reviews/${reviewId}/reply`, {
    method: 'POST',
    body: JSON.stringify({ reply }),
  }).then((r) => r.review);

// Real address-search autocomplete (2026-07-18) -- itunda's own self-hosted Nominatim
// geocoder, not a third-party Maps API. See EatsController.searchDeliveryAddress's own
// doc comment.
export interface AddressSuggestion {
  displayName: string;
  latitude: number;
  longitude: number;
}

export const searchDeliveryAddress = (query: string) =>
  apiFetch<{ success: boolean; suggestions: AddressSuggestion[] }>(
    `/api/v1/eats/geocode/search?q=${encodeURIComponent(query)}`,
  ).then((r) => r.suggestions);

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
export interface FavoriteRestaurant {
  restaurantId: string;
  businessName: string;
  category: string | null;
  favoritedAt: string;
}

export const addFavoriteRestaurant = (restaurantId: string) =>
  apiFetch<{ success: boolean; favorite: unknown }>(`/api/v1/eats/restaurants/${restaurantId}/favorite`, { method: 'POST' });

export const removeFavoriteRestaurant = (restaurantId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/eats/restaurants/${restaurantId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoriteRestaurants = () =>
  apiFetch<{ success: boolean; favorites: FavoriteRestaurant[] }>('/api/v1/eats/favorites').then((r) => r.favorites);

// Real Baemin-style 찜 리스트 공유하기 (share your favorites list, 2026-08-16) -- see
// backend EatsFavoriteService.shareFavoritesToConversation's own doc comment.
export const shareFavoritesToConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; message: unknown }>('/api/v1/eats/favorites/share', {
    method: 'POST',
    body: JSON.stringify({ conversationId }),
  });

// Real Eats membership products (EatsMembership/PlatformMembership + their real
// tiers/fetch/subscribe functions) moved to lib/eatsMembership.ts (2026-08-28, real
// file-size-lint threshold crossed for the first time) -- genuinely distinct from
// everything else in this file, same "own real lifecycle" precedent
// lib/eatsRider.ts/lib/eatsGroupOrders.ts already established.

// Real 배달의민족 함께주문 (Baemin "Together Order") shared-cart group ordering moved to
// lib/eatsGroupOrders.ts (2026-08-20, real file-size-lint threshold crossed a second
// time) -- genuinely distinct from everything else in this file: its own real
// join-code-based join flow, its own finalize/cancel lifecycle.
