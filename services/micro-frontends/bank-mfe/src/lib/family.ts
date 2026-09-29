import { apiFetch } from './api';
import type { Transaction } from './account';

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- see backend
// FamilyLinkService's own doc comment for the full sourced account and honest scope
// boundary: no real minor-specific account type or physical card, real read-only
// spending oversight only. Allowance itself has no new mechanism here -- point an
// existing AutoTransfer/ScheduledTransfer at the child's phone number instead.

export type FamilyLinkStatus = 'PENDING' | 'ACTIVE' | 'DECLINED' | 'REVOKED';

export interface FamilyLink {
  id: string;
  guardianUserId: string;
  childUserId: string;
  status: FamilyLinkStatus;
  createdAt: string;
  respondedAt: string | null;
  // Real spend-limit enforcement (Family product-completeness pass, 2026-09-08) --
  // already enforced server-side and already surfaced on Android; web was missing
  // this field entirely.
  dailySpendLimit: number | null;
}

export interface FamilyLinkView {
  link: FamilyLink;
  guardianName: string;
  childName: string;
}

export interface ChildOverview {
  childUserId: string;
  childName: string;
  accountBalance: number;
  recentTransactions: Transaction[];
}

export const inviteChild = (childPhoneNumber: string) =>
  apiFetch<{ success: boolean; link: FamilyLink }>('/api/v1/family/invite', {
    method: 'POST',
    body: JSON.stringify({ childPhoneNumber }),
  }).then((r) => r.link);

export const fetchMyInvites = () =>
  apiFetch<{ success: boolean; invites: FamilyLink[] }>('/api/v1/family/invites').then((r) => r.invites);

export const respondToInvite = (id: string, accept: boolean) =>
  apiFetch<{ success: boolean; link: FamilyLink }>(`/api/v1/family/invites/${id}/respond`, {
    method: 'POST',
    body: JSON.stringify({ accept }),
  }).then((r) => r.link);

export const fetchMyChildren = () =>
  apiFetch<{ success: boolean; children: FamilyLinkView[] }>('/api/v1/family/children').then((r) => r.children);

export const fetchMyGuardians = () =>
  apiFetch<{ success: boolean; guardians: FamilyLinkView[] }>('/api/v1/family/guardians').then((r) => r.guardians);

export const fetchChildOverview = (childUserId: string) =>
  apiFetch<{ success: boolean; overview: ChildOverview }>(`/api/v1/family/children/${childUserId}/overview`).then((r) => r.overview);

export const revokeFamilyLink = (id: string) =>
  apiFetch<{ success: boolean; link: FamilyLink }>(`/api/v1/family/links/${id}/revoke`, { method: 'POST' }).then((r) => r.link);

// Real spend-limit enforcement (Family product-completeness pass, 2026-09-08) -- see
// FamilyLink.dailySpendLimit's own doc comment. Not money-moving itself, no
// Idempotency-Key requirement, same convention as every other settings-style endpoint.
export const setSpendLimit = (childUserId: string, dailySpendLimit: number | null) =>
  apiFetch<{ success: boolean; link: FamilyLink }>(`/api/v1/family/children/${childUserId}/spend-limit`, {
    method: 'POST',
    body: JSON.stringify({ dailySpendLimit }),
  }).then((r) => r.link);
