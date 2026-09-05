import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Rwanda coffee-cooperative harvest-advance / input financing
// (rw.itunda.loans.CooperativeService) -- sourced beyond this session's usual
// Toss/Kakao/Naver/Coupang reference ecosystems, grounded in Rwanda's own real coffee
// sector (Rwanda Coffee Cooperatives Federation: 13 member cooperatives, ~19,000
// producer members). See CooperativeService's own backend doc comment for the full
// sourced account and the real financing gap this closes.

export interface Cooperative {
  id: string;
  name: string;
  cropType: string;
  registrationNumber: string | null;
  createdAt: string;
}

export interface CooperativeMembership {
  id: string;
  cooperativeId: string;
  userId: string;
  accountId: string;
  memberSince: string;
  active: boolean;
}

export type HarvestAdvanceStatus = 'REQUESTED' | 'DISBURSED' | 'REPAID' | 'OVERDUE';

export interface HarvestAdvance {
  id: string;
  membershipId: string;
  accountId: string;
  principalAmount: number;
  purpose: string;
  expectedHarvestDate: string;
  repaymentDueDate: string;
  status: HarvestAdvanceStatus;
  disbursedAt: string | null;
  repaidAt: string | null;
  createdAt: string;
}

export const registerCooperative = (name: string, cropType: string, registrationNumber?: string) =>
  apiFetch<{ success: boolean; cooperative: Cooperative }>('/api/v1/cooperatives', {
    method: 'POST',
    body: JSON.stringify({ name, cropType, registrationNumber: registrationNumber || null }),
  }).then((r) => r.cooperative);

export const joinCooperative = (cooperativeId: string) =>
  apiFetch<{ success: boolean; membership: CooperativeMembership }>(`/api/v1/cooperatives/${cooperativeId}/join`, {
    method: 'POST',
  }).then((r) => r.membership);

export const fetchMyCooperativeMemberships = () =>
  apiFetch<{ success: boolean; memberships: CooperativeMembership[] }>('/api/v1/cooperatives/my-memberships').then((r) => r.memberships);

// Idempotency-Key added 2026-09-05 -- matching disburseHarvestAdvance/
// repayHarvestAdvance below. Worse than a mere confusing-error risk without it:
// CooperativeService.requestAdvance has no "already pending" guard at all, so a
// lost response after a successful request would previously resubmit here and
// silently create a SECOND harvest advance (see CooperativeController.requestAdvance's
// own doc comment for the full real gap).
export const requestHarvestAdvance = (
  membershipId: string, principalAmount: number, purpose: string, expectedHarvestDate: string,
) =>
  apiFetch<{ success: boolean; advance: HarvestAdvance }>('/api/v1/cooperatives/advances', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ membershipId, principalAmount, purpose, expectedHarvestDate }),
  }).then((r) => r.advance);

export const disburseHarvestAdvance = (advanceId: string) =>
  apiFetch<{ success: boolean; advance: HarvestAdvance }>(`/api/v1/cooperatives/advances/${advanceId}/disburse`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.advance);

export const repayHarvestAdvance = (advanceId: string, amount: number) =>
  apiFetch<{ success: boolean; advance: HarvestAdvance }>(`/api/v1/cooperatives/advances/${advanceId}/repay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.advance);

export const fetchMyHarvestAdvances = () =>
  apiFetch<{ success: boolean; advances: HarvestAdvance[] }>('/api/v1/cooperatives/advances/my-advances').then((r) => r.advances);
