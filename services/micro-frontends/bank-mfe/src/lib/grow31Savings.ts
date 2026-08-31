// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- see the
// backend's Grow31SavingsPlan.kt / Grow31SavingsService.kt doc comments for the full
// sourced mechanics. Distinct from weeklySavings.ts's 26-week auto-debit product: this
// is an explicit daily user action ("Save today"), not a scheduler-driven installment,
// and the bonus rate is streak-gated day-by-day rather than week-by-week. Real gap
// found 2026-08-15: Android's Grow31SavingsScreen.kt has had this since 2026-08-12, but
// bank-mfe and iOS both had zero client for it.

import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { BucketTransaction } from './bucketTransaction';

export type Grow31SavingsPlanStatus = 'ACTIVE' | 'MATURED' | 'CANCELLED';

export interface Grow31SavingsPlan {
  id: string;
  userId: string;
  accountId: string;
  name: string;
  dailyAmount: number;
  startDate: string;
  daysElapsed: number;
  currentStreak: number;
  longestStreak: number;
  lastDepositDate: string | null;
  totalSaved: number;
  baseRate: number;
  status: Grow31SavingsPlanStatus;
  createdAt: string;
  maturedAt: string | null;
  cancelledAt: string | null;
  withdrawnAt: string | null;
  totalInterestPaid: number | null;
}

export interface Grow31SavingsDeposit {
  id: string;
  planId: string;
  dayNumber: number;
  depositDate: string;
  amount: number;
  streakAtDeposit: number;
  depositedAt: string;
}

export interface Grow31SavingsPlanDetail {
  plan: Grow31SavingsPlan;
  accountBalance: number;
  deposits: Grow31SavingsDeposit[];
}

// Real fixed-term constant (mirrors Grow31SavingsService's own TERM_DAYS) -- purely
// for display (progress %, "day N of 31" copy); the backend is the actual source of
// truth for every real number.
export const GROW31_TERM_DAYS = 31;

// Real, sourced tier table mirrored from Grow31SavingsService.bonusRateForStreak on
// the backend (display-only copy, matching Android's Grow31SavingsScreen.kt's own
// identical constant).
export const grow31BonusRateForStreak = (streak: number): number => {
  if (streak >= 31) return 10;
  if (streak >= 21) return 8;
  if (streak >= 14) return 6;
  if (streak >= 7) return 4;
  if (streak >= 3) return 3;
  return 0;
};

export const fetchGrow31SavingsPlans = () =>
  apiFetch<{ success: boolean; plans: Grow31SavingsPlan[] }>('/api/v1/grow31-savings/plans').then((r) => r.plans);

export const createGrow31SavingsPlan = (name: string, dailyAmount: number) =>
  apiFetch<{ success: boolean; plan: Grow31SavingsPlan }>('/api/v1/grow31-savings/plans', {
    method: 'POST',
    body: JSON.stringify({ name, dailyAmount }),
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.plan);

export const fetchGrow31SavingsPlan = (id: string) =>
  apiFetch<{ success: boolean } & Grow31SavingsPlanDetail>(`/api/v1/grow31-savings/plans/${id}`);

export const fetchGrow31SavingsPlanTransactions = (id: string) =>
  apiFetch<{ success: boolean; transactions: BucketTransaction[] }>(`/api/v1/grow31-savings/plans/${id}/transactions`).then((r) => r.transactions);

// Real explicit daily action -- unlike weeklySavings' scheduler-driven installments,
// a Grow31 deposit only happens when the user actually taps "Save today."
export const depositGrow31SavingsToday = (id: string) =>
  apiFetch<{ success: boolean } & Grow31SavingsPlanDetail>(`/api/v1/grow31-savings/plans/${id}/deposit-today`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

// Real early withdrawal -- always forfeits the streak bonus (see backend cancelPlan's
// own doc comment); pays out principal + base-rate-only interest immediately, unlike
// withdraw() below which requires a separately-reached maturity.
export const cancelGrow31SavingsPlan = (id: string) =>
  apiFetch<{ success: boolean; message: string } & Grow31SavingsPlanDetail>(`/api/v1/grow31-savings/plans/${id}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

// Withdraws an already-MATURED plan's full balance to the main account.
export const withdrawGrow31SavingsPlan = (id: string) =>
  apiFetch<{ success: boolean; message: string } & Grow31SavingsPlanDetail>(`/api/v1/grow31-savings/plans/${id}/withdraw`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });
