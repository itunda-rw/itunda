// Real Savings goals + Interest Jar (itunda's Kakao Bank SafeBox/세이프박스 equivalent).
// The backend has been fully real since 2026-07-13 (ledger-backed goals, real recurring
// auto-save, real anti-spam rate limiting) and the InterestJar accrual gap was closed
// 2026-07-20 -- but until now neither had ANY UI touchpoint anywhere in bank-mfe.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface SavingsGoal {
  id: string;
  userId: string;
  walletId: string;
  name: string;
  targetAmount: number;
  currentAmount: number;
  monthlyContribution: number;
  interestRate: number;
  targetDate: string | null;
  category: string;
  status: 'active' | 'completed';
  color: string;
  createdAt: string;
  lastAutoContributionAt: string | null;
}

export interface InterestJar {
  userId: string;
  walletId: string;
  balance: number;
  rate: number;
  earnedThisMonth: number;
  earnedTotal: number;
  lastPaidAt: string;
  nextPayoutAt: string;
}

export const fetchGoals = () =>
  apiFetch<{ success: boolean; goals: SavingsGoal[] }>('/api/v1/savings/goals').then((r) => r.goals);

export const createGoal = (name: string, targetAmount: number, monthlyContribution?: number, targetDate?: string, category?: string) =>
  apiFetch<{ success: boolean; goal: SavingsGoal }>('/api/v1/savings/goals', {
    method: 'POST',
    body: JSON.stringify({ name, targetAmount, monthlyContribution, targetDate, category }),
  }).then((r) => r.goal);

export const depositToGoal = (goalId: string, amount: number, fromWalletId?: string) =>
  apiFetch<{ success: boolean; message: string; goal: SavingsGoal }>('/api/v1/savings/deposit', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ goalId, amount, fromWalletId }),
  });

export const fetchInterestJar = () =>
  apiFetch<{ success: boolean; jar: InterestJar }>('/api/v1/savings/interest-jar').then((r) => r.jar);

export const claimInterest = () =>
  apiFetch<{ success: boolean; message: string; claimed: number; newBalance: number }>('/api/v1/savings/interest-jar/claim', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });
