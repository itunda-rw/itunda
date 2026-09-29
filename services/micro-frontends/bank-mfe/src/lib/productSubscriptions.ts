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

// Real Coupang 정기배송 "건너뛰기" (skip next delivery) -- see backend
// ProductSubscriptionService.skipNext's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built with zero client anywhere despite
// pause/resume/cancel already being real bank-mfe features.
export const skipNextProductSubscriptionDelivery = (id: string) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>(`/api/v1/product-subscriptions/${id}/skip-next`, { method: 'POST' }).then((r) => r.subscription);

// Real Coupang 정기배송 수량/주기 변경 (change quantity/interval) -- see backend
// ProductSubscriptionService.updateSubscription's own doc comment. Deliberately does
// NOT touch the already-scheduled next delivery -- matches the real product.
export const updateProductSubscription = (id: string, quantity: number | null, intervalDays: number | null) =>
  apiFetch<{ success: boolean; subscription: ProductSubscription }>(`/api/v1/product-subscriptions/${id}/update`, {
    method: 'POST',
    body: JSON.stringify({ quantity, intervalDays }),
  }).then((r) => r.subscription);
