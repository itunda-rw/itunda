import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real 배민오더-style table/QR in-store ordering (item 155) -- see the backend's
// DineInOrder.kt / DineInOrderService.kt doc comments for the full account. Deliberately
// reuses the exact same real GET /api/v1/shopping/merchants and
// GET /api/v1/shopping/merchants/{id}/products catalog endpoints lib/eats.ts already
// uses (a restaurant IS a Merchant, a menu item IS a MerchantProduct) -- only order
// placement/tracking is genuinely new here. No delivery address, no rider: payment
// settles straight into the restaurant's wallet at placement, and a table number
// (free text off a physical table tag/QR code) replaces the delivery address entirely.
export type DineInOrderStatus = 'PLACED' | 'ACCEPTED' | 'PREPARING' | 'SERVED' | 'CANCELLED';

export interface DineInOrderItem {
  id: string;
  orderId: string;
  productId: string;
  productName: string;
  unitPrice: number;
  quantity: number;
  selectedOptionsJson: string | null;
}

export interface DineInOrder {
  id: string;
  buyerId: string;
  restaurantId: string;
  tableNumber: string;
  itemsSubtotal: number;
  platformFee: number;
  totalAmount: number;
  transactionId: string;
  status: DineInOrderStatus;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
  refundTransactionId: string | null;
}

export interface DineInOrderItemRequest {
  menuItemId: string;
  quantity: number;
  selectedChoiceIds?: string[];
}

export const placeDineInOrder = (restaurantId: string, tableNumber: string, items: DineInOrderItemRequest[], notes?: string) =>
  apiFetch<{ success: boolean; order: DineInOrder; items: DineInOrderItem[] }>('/api/v1/eats/dine-in/orders', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ restaurantId, tableNumber, items, notes }),
  });

export const fetchMyDineInOrders = () =>
  apiFetch<{ success: boolean; orders: DineInOrder[] }>('/api/v1/eats/dine-in/orders/my-orders?size=50').then((r) => r.orders);

export const fetchRestaurantDineInOrders = () =>
  apiFetch<{ success: boolean; orders: DineInOrder[] }>('/api/v1/eats/dine-in/orders/restaurant-orders?size=50').then((r) => r.orders);

export const advanceDineInOrderStatus = (orderId: string, status: DineInOrderStatus) =>
  apiFetch<{ success: boolean; order: DineInOrder }>(`/api/v1/eats/dine-in/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

export const cancelDineInOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: DineInOrder }>(`/api/v1/eats/dine-in/orders/${orderId}/cancel`, { method: 'POST' }).then((r) => r.order);
