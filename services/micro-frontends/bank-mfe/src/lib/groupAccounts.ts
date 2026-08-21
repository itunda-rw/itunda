// Real Kakao Bank 모임통장 (group/shared account) equivalent -- see the backend's
// GroupAccount.kt doc comment for the full account (real, sourced mechanics: the
// creator holds withdrawal authority, invited members can view/deposit only).

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface GroupAccount {
  id: string;
  name: string;
  ownerId: string;
  accountId: string;
  monthlyDuesAmount: number | null;
  createdAt: string;
}

export interface GroupAccountMember {
  userId: string;
  firstName: string;
  lastName: string;
  isOwner: boolean;
  joinedAt: string;
}

export interface GroupAccountDetail {
  groupAccount: GroupAccount;
  balance: number;
  members: GroupAccountMember[];
}

export const fetchMyGroupAccounts = () =>
  apiFetch<{ success: boolean; groupAccounts: GroupAccount[] }>('/api/v1/group-accounts').then((r) => r.groupAccounts);

export const createGroupAccount = (name: string) =>
  apiFetch<{ success: boolean; groupAccount: GroupAccount }>('/api/v1/group-accounts', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }).then((r) => r.groupAccount);

export const fetchGroupAccount = (id: string) =>
  apiFetch<{ success: boolean } & GroupAccountDetail>(`/api/v1/group-accounts/${id}`);

export const inviteGroupAccountMember = (id: string, phoneNumber: string) =>
  apiFetch<{ success: boolean; member: GroupAccountMember }>(`/api/v1/group-accounts/${id}/members`, {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  }).then((r) => r.member);

export const depositToGroupAccount = (id: string, amount: number) =>
  apiFetch<{ success: boolean; message: string } & GroupAccountDetail>(`/api/v1/group-accounts/${id}/deposit`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });

export const withdrawFromGroupAccount = (id: string, amount: number) =>
  apiFetch<{ success: boolean; message: string } & GroupAccountDetail>(`/api/v1/group-accounts/${id}/withdraw`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });

// Real KakaoBank 회비 (dues) management (2026-07-26) -- see
// GroupAccountService.setDuesAmount's own doc comment.
export interface GroupAccountDuesMember {
  userId: string;
  firstName: string;
  lastName: string;
  contributedAmount: number;
  paid: boolean;
}

export interface GroupAccountDuesStatus {
  duesAmount: number | null;
  cycleMonth: string;
  members: GroupAccountDuesMember[];
}

export const setGroupAccountDuesAmount = (id: string, amount: number | null) =>
  apiFetch<{ success: boolean; groupAccount: GroupAccount }>(`/api/v1/group-accounts/${id}/dues`, {
    method: 'PUT',
    body: JSON.stringify({ amount }),
  }).then((r) => r.groupAccount);

export const fetchGroupAccountDues = (id: string) =>
  apiFetch<{ success: boolean; dues: GroupAccountDuesStatus }>(`/api/v1/group-accounts/${id}/dues`).then((r) => r.dues);

export const requestUnpaidGroupAccountDues = (id: string) =>
  apiFetch<{ success: boolean; remindedCount: number }>(`/api/v1/group-accounts/${id}/dues/remind`, { method: 'POST' }).then(
    (r) => r.remindedCount,
  );
