import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real KakaoPay-style "정산하기" (chat-embedded split-bill), rw.itunda.splitbill --
// found 2026-07-22 fully built on the backend, tied to an existing real group
// conversation, with zero client UI anywhere despite group chat itself being fully
// wired. See SplitBill.kt's own doc comment: a flat, even split with the rounding
// remainder silently absorbed into one participant's share so shares always sum
// exactly to totalAmount; each participant pays their own share directly to the
// organizer via a real wallet-to-wallet push, no escrow.

export interface SplitBill {
  id: string;
  organizerId: string;
  groupConversationId: string;
  messageId: string;
  totalAmount: number;
  description: string;
  status: string;
  settledAt: string | null;
  createdAt: string;
  // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see backend
  // SplitBillService.ladderSplit's own doc comment. 'EVEN' is the unchanged v1 default.
  mode: 'EVEN' | 'LADDER';
  ladderVarianceLevel: number | null;
  // Real photo receipt attach (2026-07-28) -- see SplitBillService.attachReceipt's own
  // doc comment. null means no receipt attached yet.
  receiptImageUrl: string | null;
  // Real up-to-5 sequential settlement round counter (2026-07-28) -- see
  // SplitBillService.requestNextRound's own doc comment. Starts at 1.
  currentRound: number;
}

export interface SplitBillParticipant {
  id: string;
  splitBillId: string;
  userId: string;
  shareAmount: number;
  status: string;
  paidTransactionId: string | null;
  paidAt: string | null;
  createdAt: string;
}

export interface SplitBillWithParticipants {
  splitBill: SplitBill;
  participants: SplitBillParticipant[];
}

export const createSplitBill = (
  groupConversationId: string,
  totalAmount: number,
  description: string,
  participantUserIds: string[],
  mode: 'EVEN' | 'LADDER' = 'EVEN',
  ladderVarianceLevel?: number,
) =>
  apiFetch<{ success: boolean } & SplitBillWithParticipants>(`/api/v1/split-bills/conversations/${groupConversationId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ totalAmount, description, participantUserIds, mode, ladderVarianceLevel }),
  });

// Real 1:1-chat split-bill entry point (2026-08-09) -- see backend
// SplitBillService.createDirectSplitBill's own doc comment: resolves a hidden 2-person
// group between the caller and `otherUserId` first, so this never needs an existing
// named group the way createSplitBill above does. Closes docs/DESIGN_REFERENCES.md
// Section 19's "is SplitBill reachable from 1:1 Talk" gap, investigated and shelved
// twice before.
export const createDirectSplitBill = (
  otherUserId: string,
  totalAmount: number,
  description: string,
  mode: 'EVEN' | 'LADDER' = 'EVEN',
  ladderVarianceLevel?: number,
) =>
  apiFetch<{ success: boolean } & SplitBillWithParticipants>(`/api/v1/split-bills/direct/${otherUserId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ totalAmount, description, mode, ladderVarianceLevel }),
  });

export const attachSplitBillReceipt = (splitBillId: string, imageUrl: string) =>
  apiFetch<{ success: boolean; splitBill: SplitBill }>(`/api/v1/split-bills/${splitBillId}/receipt`, {
    method: 'POST',
    body: JSON.stringify({ imageUrl }),
  }).then((r) => r.splitBill);

export const requestSplitBillNextRound = (splitBillId: string) =>
  apiFetch<{ success: boolean; splitBill: SplitBill }>(`/api/v1/split-bills/${splitBillId}/next-round`, {
    method: 'POST',
  }).then((r) => r.splitBill);

// Real read-only counterpart to createDirectSplitBill above -- see backend
// SplitBillService.getDirectSplitBills's own doc comment. Never creates a hidden group
// as a side effect of viewing this tab; an empty list when the two people have never
// split a bill before.
export const fetchDirectSplitBills = (otherUserId: string) =>
  apiFetch<{ success: boolean; splitBills: SplitBillWithParticipants[] }>(`/api/v1/split-bills/direct/${otherUserId}`).then(
    (r) => r.splitBills,
  );

export const fetchSplitBillsForGroup = (groupConversationId: string) =>
  apiFetch<{ success: boolean; splitBills: SplitBillWithParticipants[] }>(
    `/api/v1/split-bills/conversations/${groupConversationId}`,
  ).then((r) => r.splitBills);

export const paySplitBillShare = (splitBillId: string) =>
  apiFetch<{ success: boolean; participant: SplitBillParticipant }>(`/api/v1/split-bills/${splitBillId}/pay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.participant);
