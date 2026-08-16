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
export const fetchRestaurants = (category?: string, q?: string, buyerLat?: number, buyerLng?: number, sortBy?: 'delivery_time' | 'favorites') => {
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
}

export interface MenuItem {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
  optionGroups?: MenuOptionGroup[];
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

// Real rider role -- any itunda user can opt in.
export interface Rider {
  id: string;
  userId: string;
  walletId: string;
  status: 'ACTIVE' | 'SUSPENDED';
  available: boolean;
  createdAt: string;
}

export const registerRider = () =>
  apiFetch<{ success: boolean; rider: Rider }>('/api/v1/eats/riders/register', { method: 'POST' }).then((r) => r.rider);

export const fetchMyRiderProfile = () =>
  apiFetch<{ success: boolean; rider: Rider }>('/api/v1/eats/riders/me').then((r) => r.rider);

export const setRiderAvailability = (available: boolean) =>
  apiFetch<{ success: boolean; rider: Rider }>('/api/v1/eats/riders/availability', {
    method: 'POST',
    body: JSON.stringify({ available }),
  }).then((r) => r.rider);

export const fetchAvailableDeliveries = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/available').then((r) => r.orders);

export const fetchRiderDeliveries = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/rider-deliveries').then((r) => r.orders);

export const claimDelivery = (orderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/claim`, { method: 'POST' }).then((r) => r.order);

export const advanceRiderOrder = (orderId: string, status: EatsOrderStatus) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/rider-status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

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
}

export interface RatingSummary {
  average: number | null;
  count: number;
}

// riderRating/riderComment are optional (2026-07-26) -- a real Baemin-style PICKUP
// order review has no rider to rate; see EatsReview.kt's own doc comment for the full
// account of the real bug this fixes (every PICKUP order was previously unreviewable).
export const submitEatsReview = (
  orderId: string,
  restaurantRating: number,
  restaurantComment: string,
  riderRating: number | null,
  riderComment: string,
) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify({
      restaurantRating,
      restaurantComment: restaurantComment.trim() || null,
      riderRating,
      riderComment: riderRating == null ? null : riderComment.trim() || null,
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

// Real Baemin Club (배민클럽)-style free-delivery membership (rw.itunda.eats.
// EatsMembershipService, 2026-07-26) -- backend-only until now (item 102), first client
// UI for this feature. Free delivery only applies at a restaurant that has itself
// opted in (see MerchantController.setParticipatesInEatsMembership) -- never a blanket
// waiver, mirroring Baemin's own real "참여 가게" scoping.
export interface EatsMembership {
  id: string;
  userId: string;
  activeUntil: string;
  createdAt: string;
  updatedAt: string;
}

export const EATS_MEMBERSHIP_TIERS: { days: number; priceRwf: number }[] = [
  { days: 30, priceRwf: 1500 },
  { days: 90, priceRwf: 4000 },
];

export const fetchMyMembership = () =>
  apiFetch<{ success: boolean; membership: EatsMembership | null }>('/api/v1/eats/membership/me').then((r) => r.membership);

export const subscribeMembership = (days: number) =>
  apiFetch<{ success: boolean; membership: EatsMembership }>('/api/v1/eats/membership/subscribe', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ days }),
  }).then((r) => r.membership);

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211,
// rw.itunda.eats.PlatformMembershipService, 2026-07-31) -- see PlatformMembership.kt's
// own doc comment. Deliberately distinct from Eats Club above: this waives the fee at
// every restaurant, no merchant opt-in required, the same real broader guarantee
// Coupang Wow has over a participating-seller-only free-delivery program.
export type PlatformMembership = EatsMembership;

export const PLATFORM_MEMBERSHIP_TIERS: { days: number; priceRwf: number }[] = [
  { days: 30, priceRwf: 2500 },
  { days: 90, priceRwf: 6500 },
];

export const fetchMyPlatformMembership = () =>
  apiFetch<{ success: boolean; membership: PlatformMembership | null }>('/api/v1/eats/platform-membership/me').then((r) => r.membership);

export const subscribePlatformMembership = (days: number) =>
  apiFetch<{ success: boolean; membership: PlatformMembership }>('/api/v1/eats/platform-membership/subscribe', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ days }),
  }).then((r) => r.membership);

// Real 배달의민족 함께주문 (Baemin "Together Order") shared-cart group ordering
// (2026-08-15) -- see GroupEatsOrderService.kt's own doc comment on the backend for the
// full account, including a fresh 2026-06 sourced account of Baemin's own real "host
// pays first, Dutch pay requested after via existing split-bill" mechanism, which this
// reuses unchanged rather than inventing a new multi-payer checkout model.
export type GroupEatsOrderStatus = 'OPEN' | 'FINALIZED' | 'CANCELLED';

export interface GroupEatsOrder {
  id: string;
  hostUserId: string;
  restaurantId: string;
  joinCode: string;
  deliveryAddress: string;
  deliveryLatitude: number | null;
  deliveryLongitude: number | null;
  fulfillmentType: 'DELIVERY' | 'PICKUP';
  status: GroupEatsOrderStatus;
  resultingOrderId: string | null;
  createdAt: string;
  finalizedAt: string | null;
}

export interface GroupEatsOrderItemView {
  productId: string;
  productName: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

export interface GroupEatsOrderParticipantView {
  userId: string;
  joinedAt: string;
  subtotal: number;
  items: GroupEatsOrderItemView[];
}

export interface GroupEatsOrderDetail {
  groupOrder: GroupEatsOrder;
  grandTotal: number;
  participants: GroupEatsOrderParticipantView[];
}

export const createGroupEatsOrder = (
  restaurantId: string,
  deliveryAddress: string,
  deliveryLatitude?: number,
  deliveryLongitude?: number,
  fulfillmentType: 'DELIVERY' | 'PICKUP' = 'DELIVERY',
) =>
  apiFetch<{ success: boolean; groupOrder: GroupEatsOrder }>('/api/v1/eats/group-orders', {
    method: 'POST',
    body: JSON.stringify({ restaurantId, deliveryAddress, deliveryLatitude, deliveryLongitude, fulfillmentType }),
  }).then((r) => r.groupOrder);

export const joinGroupEatsOrder = (joinCode: string) =>
  apiFetch<{ success: boolean; groupOrder: GroupEatsOrder }>('/api/v1/eats/group-orders/join', {
    method: 'POST',
    body: JSON.stringify({ joinCode }),
  }).then((r) => r.groupOrder);

export const fetchGroupEatsOrder = (groupOrderId: string) =>
  apiFetch<{ success: boolean } & GroupEatsOrderDetail>(`/api/v1/eats/group-orders/${groupOrderId}`);

export const setMyGroupEatsOrderItems = (
  groupOrderId: string,
  items: { menuItemId: string; quantity: number; selectedChoiceIds?: string[] }[],
) =>
  apiFetch<{ success: boolean } & GroupEatsOrderDetail>(`/api/v1/eats/group-orders/${groupOrderId}/items`, {
    method: 'POST',
    body: JSON.stringify({ items }),
  });

export const finalizeGroupEatsOrder = (groupOrderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder; items: EatsOrderItem[] }>(`/api/v1/eats/group-orders/${groupOrderId}/finalize`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

export const cancelGroupEatsOrder = (groupOrderId: string) =>
  apiFetch<{ success: boolean; groupOrder: GroupEatsOrder }>(`/api/v1/eats/group-orders/${groupOrderId}/cancel`, {
    method: 'POST',
  }).then((r) => r.groupOrder);
