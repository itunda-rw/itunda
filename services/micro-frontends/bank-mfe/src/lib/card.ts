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
  design: string;
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

// Real Toss Bank precedent (namu.wiki: 5 named colorways, e.g. 레몬 블루/오렌지 밀크/나이트
// 핑크), direct user instruction 2026-08-27: "update itunda bank with all those cards
// designs allowing users to choose from those designs... that's how toss does it too".
// itunda's own 5, each a real front/back color pair validated in the card-lineup design
// pass -- this array is the single source of truth CardExplainer's picker and CardView's
// issued-card rendering both read from, and its `id` values are exactly what the
// backend's DebitCardDesign whitelist (DebitCard.kt) accepts.
export interface CardDesign {
  id: string;
  name: string;
  front: string;
  back: string;
  frontLight?: boolean;
}

export const CARD_DESIGNS: CardDesign[] = [
  { id: 'onyx_indigo', name: 'Onyx Indigo', front: '#191f28', back: '#7472f4' },
  { id: 'indigo_onyx', name: 'Indigo Onyx', front: '#7472f4', back: '#191f28' },
  { id: 'rose_forest', name: 'Rose Forest', front: '#df466c', back: '#05804a' },
  { id: 'frost_onyx', name: 'Frost Onyx', front: '#f4f4f2', back: '#191f28', frontLight: true },
  { id: 'forest_rose', name: 'Forest Rose', front: '#05804a', back: '#df466c' },
];

export const DEFAULT_CARD_DESIGN = CARD_DESIGNS[0].id;

export const cardDesign = (id: string): CardDesign => CARD_DESIGNS.find((d) => d.id === id) ?? CARD_DESIGNS[0];

export const issueCard = (design: string = DEFAULT_CARD_DESIGN) =>
  apiFetch<{ success: boolean; card: Card }>('/api/v1/card/issue', {
    method: 'POST',
    body: JSON.stringify({ design }),
  }).then((r) => r.card);

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
