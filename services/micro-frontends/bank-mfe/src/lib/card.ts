// Real Toss Bank 체크카드 (check/debit card) client -- see the backend's DebitCard.kt
// doc comment for the full sourced account. itunda has no real card-network
// partnership or physical card fulfilment, so "paying with your card" here is itunda's
// own real, ledger-backed simulation of a card-present purchase (real money moves,
// real limits are enforced, real history is kept) rather than a real Visa/Mastercard
// rail transaction.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface Card {
  id: string;
  last4: string;
  dailyLimit: number;
  monthlyLimit: number;
  frozen: boolean;
  issuedAt: string;
  spentToday: number;
  spentThisMonth: number;
  remainingToday: number;
  remainingThisMonth: number;
}

export interface CardTransaction {
  id: string;
  cardId: string;
  amount: number;
  merchantName: string;
  createdAt: string;
}

export const issueCard = () =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/issue', { method: 'POST' }).then((r) => r.card);

export const fetchMyCard = () =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/my-card').then((r) => r.card);

export const fetchCardTransactions = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; transactions: CardTransaction[]; totalElements: number; totalPages: number }>(
    `/api/v1/card/transactions?page=${page}&size=${size}`,
  );

export const setCardLimits = (dailyLimit: number, monthlyLimit: number) =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/limits', {
    method: 'PUT',
    body: JSON.stringify({ dailyLimit, monthlyLimit }),
  }).then((r) => r.card);

export const freezeCard = () =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/freeze', { method: 'POST' }).then((r) => r.card);

export const unfreezeCard = () =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/unfreeze', { method: 'POST' }).then((r) => r.card);

export const chargeCard = (amount: number, merchantName: string) =>
  apiFetch<{ success: boolean; transaction: CardTransaction; card: Card }>('/api/v1/card/charge', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount, merchantName }),
  });
