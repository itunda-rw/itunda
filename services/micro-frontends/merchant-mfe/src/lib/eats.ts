import { apiFetch } from './api';

// Real Coupang Eats/Baemin-style restaurant order queue (item 208) -- see
// services/backend's EatsOrderService.kt doc comment for the full account. Until now
// the only client for a restaurant owner's real incoming Eats orders was bank-mfe's
// RestaurantOrdersView; merchant-mfe (the actual merchant-facing app) had none at all,
// so a restaurant owner using this app specifically had no way to see, accept, or
// complete their real orders except by raw API calls.

export type EatsOrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'READY_FOR_PICKUP' | 'RIDER_ASSIGNED' | 'PICKED_UP' | 'DELIVERED' | 'CANCELLED';

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
  status: EatsOrderStatus;
  createdAt: string;
  updatedAt: string;
  deliveryNotes: string | null;
  // Real Baemin-style 포장주문 (Pickup) order type -- a PICKUP order has riderId: null
  // for its whole lifecycle and reaches DELIVERED via completePickupOrder below, not a
  // rider hand-off.
  fulfillmentType?: 'DELIVERY' | 'PICKUP';
}

export const fetchRestaurantOrders = () =>
  apiFetch<{ success: boolean; orders: EatsOrder[] }>('/api/v1/eats/orders/restaurant-orders').then((r) => r.orders);

export const advanceRestaurantOrder = (orderId: string, status: EatsOrderStatus) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

// Real 배민오더-style table/QR in-store ordering, restaurant side -- found via a fresh
// "defined but uncalled" endpoint sweep: real, working since 2026-07-25 on Android/iOS
// MerchantApp, zero client on this web dashboard. A customer scans a printed per-table
// QR straight into the dine-in ordering screen for that exact restaurant + table --
// same real, restaurant-driven-only status chain (PLACED -> ACCEPTED -> PREPARING ->
// SERVED) as the Eats queue above, minus the rider-handoff step this order type never
// has.
export type DineInOrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'SERVED' | 'CANCELLED';

export interface DineInOrder {
  id: string;
  buyerId: string;
  restaurantId: string;
  tableNumber: string;
  totalAmount: number;
  status: DineInOrderStatus;
  notes: string | null;
  createdAt: string;
}

export const fetchDineInOrders = () =>
  apiFetch<{ success: boolean; orders: DineInOrder[] }>('/api/v1/eats/dine-in/orders/restaurant-orders').then((r) => r.orders);

export const advanceDineInOrder = (orderId: string, status: DineInOrderStatus) =>
  apiFetch<{ success: boolean; order: DineInOrder }>(`/api/v1/eats/dine-in/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

// Same encoding convention as lib/merchant.ts's own paymentIntentQrPayload/staticQrPayload.
export const dineInTableQrPayload = (restaurantId: string, tableNumber: string) =>
  `itunda://eats/dine-in?restaurantId=${restaurantId}&table=${encodeURIComponent(tableNumber)}`;

export const completePickupOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/complete-pickup`, {
    method: 'POST',
  }).then((r) => r.order);
