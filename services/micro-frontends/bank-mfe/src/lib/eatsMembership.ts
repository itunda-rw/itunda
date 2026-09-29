import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Eats membership products, moved out of lib/eats.ts (itunda Eats redesign,
// 2026-08-28, real file-size-lint threshold crossed for the first time) --
// genuinely distinct from everything else in that file, same "own real lifecycle"
// precedent lib/eatsRider.ts/lib/eatsGroupOrders.ts already established. TWO real,
// both-real, both-stackable products (see PlatformMembership.kt's own doc comment):
// EatsMembership is Baemin Club (배민클럽)-style, restaurant-opt-in-only free
// delivery; PlatformMembership is Coupang 와우(WOW)-style, unconditional at every
// restaurant, no merchant opt-in required.

// Real Baemin Club (배민클럽)-style free-delivery membership (rw.itunda.eats.
// EatsMembershipService, 2026-07-26). Free delivery only applies at a restaurant
// that has itself opted in (see MerchantController.setParticipatesInEatsMembership)
// -- never a blanket waiver, mirroring Baemin's own real "참여 가게" scoping.
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
