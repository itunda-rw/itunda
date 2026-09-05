import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { EatsOrder, EatsOrderStatus } from './eats';

// Extracted from lib/eats.ts (2026-08-20, real file-size-lint threshold crossed) --
// the rider-role half of Coupang Eats-style delivery, genuinely distinct from the
// customer/restaurant-facing exports that remain in eats.ts: any itunda user can
// opt in as a rider, browse+claim available deliveries, and advance their own
// delivery's status, none of which a buyer or restaurant owner ever calls.

// Real rider role -- any itunda user can opt in.
export interface Rider {
  id: string;
  userId: string;
  accountId: string;
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
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/claim`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.order);

export const advanceRiderOrder = (orderId: string, status: EatsOrderStatus) =>
  apiFetch<{ success: boolean; order: EatsOrder }>(`/api/v1/eats/orders/${orderId}/rider-status`, {
    method: 'POST',
    body: JSON.stringify({ status }),
  }).then((r) => r.order);
