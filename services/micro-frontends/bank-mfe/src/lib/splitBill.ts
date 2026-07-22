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

export const createSplitBill = (groupConversationId: string, totalAmount: number, description: string, participantUserIds: string[]) =>
  apiFetch<{ success: boolean } & SplitBillWithParticipants>(`/api/v1/split-bills/conversations/${groupConversationId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ totalAmount, description, participantUserIds }),
  });

export const fetchSplitBillsForGroup = (groupConversationId: string) =>
  apiFetch<{ success: boolean; splitBills: SplitBillWithParticipants[] }>(
    `/api/v1/split-bills/conversations/${groupConversationId}`,
  ).then((r) => r.splitBills);

export const paySplitBillShare = (splitBillId: string) =>
  apiFetch<{ success: boolean; participant: SplitBillParticipant }>(`/api/v1/split-bills/${splitBillId}/pay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.participant);
