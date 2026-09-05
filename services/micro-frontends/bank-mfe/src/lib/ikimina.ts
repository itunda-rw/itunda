// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
// backend's Ikimina.kt doc comment for the full sourced account (real ROSCA
// literature, and a real existing Rwandan startup, smartikimina.rw, already
// digitizing this exact mechanic via mobile money). Distinct from
// lib/groupAccounts.ts (Kakao Bank 모임통장): that feature has one permanent owner with
// sole withdrawal authority; an ikimina rotates the full pot to a different member
// each real round, until everyone has been paid exactly once. Genuinely the first
// feature in this codebase not sourced from Toss/Kakao/Naver/Coupang.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type IkiminaStatus = 'FORMING' | 'ACTIVE' | 'COMPLETED';

export interface Ikimina {
  id: string;
  name: string;
  organizerId: string;
  accountId: string;
  contributionAmount: number;
  cycleFrequencyDays: number;
  memberCap: number;
  currentRound: number;
  status: IkiminaStatus;
  createdAt: string;
}

export interface IkiminaMember {
  userId: string;
  firstName: string;
  lastName: string;
  payoutOrder: number;
  hasReceivedPayout: boolean;
  isOrganizer: boolean;
}

export interface IkiminaContributionStatus {
  userId: string;
  contributed: boolean;
}

export interface IkiminaPayoutResult {
  ikimina: Ikimina;
  recipientUserId: string;
  amount: number;
}

export interface IkiminaDetail {
  ikimina: Ikimina;
  balance: number;
  members: IkiminaMember[];
  currentRoundContributions: IkiminaContributionStatus[];
}

export const fetchMyIkiminas = () =>
  apiFetch<{ success: boolean; ikiminas: Ikimina[] }>('/api/v1/ikiminas').then((r) => r.ikiminas);

export const createIkimina = (name: string, contributionAmount: number, cycleFrequencyDays: number, memberCap: number) =>
  apiFetch<{ success: boolean; ikimina: Ikimina }>('/api/v1/ikiminas', {
    method: 'POST',
    body: JSON.stringify({ name, contributionAmount, cycleFrequencyDays, memberCap }),
  }).then((r) => r.ikimina);

export const fetchIkimina = (id: string) =>
  apiFetch<{ success: boolean } & IkiminaDetail>(`/api/v1/ikiminas/${id}`);

export const inviteIkiminaMember = (id: string, phoneNumber: string) =>
  apiFetch<{ success: boolean; member: IkiminaMember }>(`/api/v1/ikiminas/${id}/members`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ phoneNumber }),
  }).then((r) => r.member);

export const startIkiminaCycle = (id: string) =>
  apiFetch<{ success: boolean; ikimina: Ikimina }>(`/api/v1/ikiminas/${id}/start`, { method: 'POST' }).then((r) => r.ikimina);

// Real bug fix: a contribution that completes the round now auto-triggers the payout
// in the same call (see the backend's IkiminaService.contributeThisRound doc comment
// for the full account of why -- nothing used to call the separate payout endpoint
// automatically, so a real round could sit completed-but-unpaid indefinitely).
// `payout` is null on every contribution except the one that completes a round.
export const contributeToIkimina = (id: string) =>
  apiFetch<{ success: boolean; ikimina: Ikimina; payout: IkiminaPayoutResult | null }>(`/api/v1/ikiminas/${id}/contribute`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => ({ ikimina: r.ikimina, payout: r.payout }));

export const triggerIkiminaPayout = (id: string) =>
  apiFetch<{ success: boolean; ikimina: Ikimina; recipientUserId: string; amount: number }>(`/api/v1/ikiminas/${id}/payout`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });
