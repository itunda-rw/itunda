import { apiFetch } from './api';
import type { ShoppingMerchant } from './shopping';

// Real Coupang Eats-style food delivery (rw.itunda.eats, 2026-07-18) -- restaurant
// browsing/menus deliberately reuse the existing shopping endpoints below (a restaurant
// IS a Merchant, a menu item IS a MerchantProduct -- see EatsOrderService.kt's own doc
// comment), only order placement/tracking and the rider workflow are genuinely new.

export const fetchRestaurants = () =>
  apiFetch<{ success: boolean; merchants: ShoppingMerchant[] }>('/api/v1/shopping/merchants').then((r) => r.merchants);

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
}

export const placeEatsOrder = (restaurantId: string, items: { menuItemId: string; quantity: number }[], deliveryAddress: string) =>
  apiFetch<{ success: boolean; order: EatsOrder; items: EatsOrderItem[] }>('/api/v1/eats/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ restaurantId, items, deliveryAddress }),
  });

export const fetchMyEatsOrders = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/my-orders').then((r) => r.orders);

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
