// Real KakaoBank 26주적금 (26-week savings) equivalent -- see the backend's
// WeeklySavingsPlan.kt / WeeklySavingsService.kt doc comments for the full sourced
// mechanics. Distinct from both the generic SavingsGoal/InterestJar products (lib/
// savings.ts) and the already-shipped Group Account (lib/groupAccounts.ts): the weekly
// auto-debit amount escalates on a real schedule, a hard weekday lock is captured at
// creation, interest accrues per-installment rather than one flat rate, and a streak-
// gated bonus rate rewards an unbroken run to real 26-week maturity. Backend has been
// real (ledger-backed, scheduler-driven) since 2026-07-21 but had zero UI touchpoint
// anywhere until now.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type WeeklySavingsPlanStatus = 'ACTIVE' | 'MATURED' | 'CANCELLED';

export interface WeeklySavingsPlan {
  id: string;
  userId: string;
  accountId: string;
  name: string;
  baseWeeklyAmount: number;
  escalationRate: number;
  openingWeekday: number;
  baseRate: number;
  bonusRate: number;
  installmentsCollected: number;
  weeksElapsed: number;
  currentAmount: number;
  streakBroken: boolean;
  status: WeeklySavingsPlanStatus;
  nextInstallmentDueAt: string;
  createdAt: string;
  maturedAt: string | null;
  cancelledAt: string | null;
  withdrawnAt: string | null;
  totalInterestPaid: number | null;
}

export interface WeeklySavingsInstallment {
  id: string;
  planId: string;
  weekNumber: number;
  amount: number;
  depositedAt: string;
}

export interface WeeklySavingsPlanDetail {
  plan: WeeklySavingsPlan;
  accountBalance: number;
  installments: WeeklySavingsInstallment[];
}

// Real KakaoBank step-up presets -- must match WeeklySavingsService.allowedEscalationRates
// exactly, or plan creation real-400s with INVALID_ESCALATION_RATE.
export const WEEKLY_SAVINGS_ESCALATION_RATES = [0, 0.10, 0.20, 0.30, 0.50, 1.00];

// Real fixed schedule constants (mirrors WeeklySavingsService's own TERM_WEEKS/
// ESCALATION_STEP_WEEKS) -- purely for display (progress %, "steps up every N weeks"
// copy); the backend is the actual source of truth for every real number.
export const WEEKLY_SAVINGS_TERM_WEEKS = 26;
export const WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS = 4;

export const fetchWeeklySavingsPlans = () =>
  apiFetch<{ success: boolean; plans: WeeklySavingsPlan[] }>('/api/v1/weekly-savings/plans').then((r) => r.plans);

export const createWeeklySavingsPlan = (name: string, baseWeeklyAmount: number, escalationRate: number) =>
  apiFetch<{ success: boolean; plan: WeeklySavingsPlan }>('/api/v1/weekly-savings/plans', {
    method: 'POST',
    body: JSON.stringify({ name, baseWeeklyAmount, escalationRate }),
  }).then((r) => r.plan);

export const fetchWeeklySavingsPlan = (id: string) =>
  apiFetch<{ success: boolean } & WeeklySavingsPlanDetail>(`/api/v1/weekly-savings/plans/${id}`);

// Real early withdrawal -- always forfeits the streak bonus (see backend
// cancelPlan's own doc comment); pays out principal + base-rate-only interest
// immediately, unlike withdraw() below which requires a separately-reached maturity.
export const cancelWeeklySavingsPlan = (id: string) =>
  apiFetch<{ success: boolean; message: string } & WeeklySavingsPlanDetail>(`/api/v1/weekly-savings/plans/${id}/cancel`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

// Withdraws an already-MATURED plan's full balance to MAIN. Separate from cancel()
// on purpose -- maturity is a real scheduled/system event, this is the real explicit
// user action that follows it.
export const withdrawWeeklySavingsPlan = (id: string) =>
  apiFetch<{ success: boolean; message: string } & WeeklySavingsPlanDetail>(`/api/v1/weekly-savings/plans/${id}/withdraw`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });
