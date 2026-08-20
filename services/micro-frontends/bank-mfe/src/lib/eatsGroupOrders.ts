import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { EatsOrder, EatsOrderItem } from './eats';

// Extracted from lib/eats.ts (2026-08-20, real file-size-lint threshold crossed a
// second time) -- real 배달의민족 함께주문 (Baemin "Together Order") shared-cart group
// ordering, see backend GroupEatsOrderService.kt's own doc comment for the full
// account, including a fresh 2026-06 sourced account of Baemin's own real "host pays
// first, Dutch pay requested after via existing split-bill" mechanism, which this
// reuses unchanged rather than inventing a new multi-payer checkout model. Genuinely
// distinct from everything else in eats.ts -- its own real join-code-based join flow,
// its own finalize/cancel lifecycle.

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
