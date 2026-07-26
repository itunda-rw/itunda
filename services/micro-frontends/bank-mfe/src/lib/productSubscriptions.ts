import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery -- see
// backend ProductSubscriptionService's own doc comment for the full sourced account
// and honest scope boundary (5% single-item discount only, the real 10% 3+-bundle
// discount not implemented).

export type ProductSubscriptionStatus = 'ACTIVE' | 'PAUSED' | 'CANCELLED';

export interface ProductSubscription {
  id: string;
  merchantId: string;
  productId: string;
  quantity: number;
  intervalDays: number;
  deliveryAddress: string;
  status: ProductSubscriptionStatus;
  nextDeliveryAt: string;
  createdAt: string;
  lastDeliveredAt: string | null;
  deliveryCount: number;
  lastFailureReason: string | null;
  cancelledAt: string | null;
}

export const fetchMyProductSubscriptions = () =>
  apiFetch<{ success: boolean; subscriptions: ProductSubscription[] }>('/api/v1/product-subscriptions').then((r) => r.subscriptions);

export const subscribeToProduct = (merchantId: string, productId: string, quantity: number, intervalDays: number, deliveryAddress: string) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>('/api/v1/product-subscriptions', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ merchantId, productId, quantity, intervalDays, deliveryAddress }),
  }).then((r) => r.subscription);

export const pauseProductSubscription = (id: string) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>(`/api/v1/product-subscriptions/${id}/pause`, { method: 'POST' }).then((r) => r.subscription);

export const resumeProductSubscription = (id: string) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>(`/api/v1/product-subscriptions/${id}/resume`, { method: 'POST' }).then((r) => r.subscription);

export const cancelProductSubscription = (id: string) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>(`/api/v1/product-subscriptions/${id}/cancel`, { method: 'POST' }).then((r) => r.subscription);
