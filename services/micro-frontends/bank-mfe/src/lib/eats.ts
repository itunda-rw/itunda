import { apiFetch } from './api';
import type { ShoppingMerchant } from './shopping';

// Real Coupang Eats-style food delivery (rw.itunda.eats, 2026-07-18) -- restaurant
// browsing/menus deliberately reuse the existing shopping endpoints below (a restaurant
// IS a Merchant, a menu item IS a MerchantProduct -- see EatsOrderService.kt's own doc
// comment), only order placement/tracking and the rider workflow are genuinely new.

// Real category/search filter (2026-07-19) -- both optional and combinable. See
// ShoppingController.getEligibleMerchants's own doc comment on the backend.
export const fetchRestaurants = (category?: string, q?: string) => {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  if (q) params.set('q', q);
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
  apiFetch<{ success: boolean; categories: string[] }>('/api/v1/shopping/merchants/categories').then((r) => r.categories);

export interface MenuItem {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
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
}

export const placeEatsOrder = (
  restaurantId: string,
  items: { menuItemId: string; quantity: number }[],
  deliveryAddress: string,
  deliveryLatitude?: number,
  deliveryLongitude?: number,
  deliveryNotes?: string,
) =>
  apiFetch<{ success: boolean; order: EatsOrder; items: EatsOrderItem[] }>('/api/v1/eats/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ restaurantId, items, deliveryAddress, deliveryLatitude, deliveryLongitude, deliveryNotes }),
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
  riderId: string;
  restaurantRating: number;
  restaurantComment: string | null;
  riderRating: number;
  riderComment: string | null;
  createdAt: string;
}

export interface RatingSummary {
  average: number | null;
  count: number;
}

export const submitEatsReview = (
  orderId: string,
  restaurantRating: number,
  restaurantComment: string,
  riderRating: number,
  riderComment: string,
) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify({
      restaurantRating,
      restaurantComment: restaurantComment.trim() || null,
      riderRating,
      riderComment: riderComment.trim() || null,
    }),
  }).then((r) => r.review);

export const fetchRestaurantRating = (restaurantId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/eats/restaurants/${restaurantId}/rating`).then(
    (r) => ({ average: r.average, count: r.count }) as RatingSummary,
  );

export const fetchRestaurantReviews = (restaurantId: string) =>
  apiFetch<{ success: boolean; reviews: EatsReview[] }>(`/api/v1/eats/restaurants/${restaurantId}/reviews`).then((r) => r.reviews);

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
