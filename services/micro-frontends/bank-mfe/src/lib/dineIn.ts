import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real 배민오더-style table/QR in-store ordering (item 155) -- see the backend's
// DineInOrder.kt / DineInOrderService.kt doc comments for the full account. Deliberately
// reuses the exact same real GET /api/v1/shopping/merchants and
// GET /api/v1/shopping/merchants/{id}/products catalog endpoints lib/eats.ts already
// uses (a restaurant IS a Merchant, a menu item IS a MerchantProduct) -- only order
// placement/tracking is genuinely new here. No delivery address, no rider: payment
// settles straight into the restaurant's account at placement, and a table number
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

// Real pagination-discard fix (2026-09-11, same systemic gap fixed for
// Commerce/Eats own order-history endpoints -- see
// project_itunda_pagination_discard_sweep memory) -- these real Pageable
// endpoints were bumped to size=50 at some point but page was still never
// sent, silently capping table-order history at the 50 most recent rows.
export const fetchMyDineInOrders = (page = 0) =>
  apiFetch<{ success: boolean; orders: DineInOrder[]; page: number; totalPages: number }>(
    `/api/v1/eats/dine-in/orders/my-orders?page=${page}&size=50`,
  );

export const fetchRestaurantDineInOrders = (page = 0) =>
  apiFetch<{ success: boolean; orders: DineInOrder[]; page: number; totalPages: number }>(
    `/api/v1/eats/dine-in/orders/restaurant-orders?page=${page}&size=50`,
  );

export const advanceDineInOrderStatus = (orderId: string, status: DineInOrderStatus) =>
  apiFetch<{ success: boolean; order: DineInOrder }>(`/api/v1/eats/dine-in/orders/${orderId}/status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);

export const cancelDineInOrder = (orderId: string) =>
  apiFetch<{ success: boolean; order: DineInOrder }>(`/api/v1/eats/dine-in/orders/${orderId}/cancel`, { method: 'POST' }).then((r) => r.order);
