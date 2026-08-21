// Real Savings goals + Interest Jar (itunda's Kakao Bank SafeBox/세이프박스 equivalent).
// The backend has been fully real since 2026-07-13 (ledger-backed goals, real recurring
// auto-save, real anti-spam rate limiting) and the InterestJar accrual gap was closed
// 2026-07-20 -- but until now neither had ANY UI touchpoint anywhere in bank-mfe.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface SavingsGoal {
  id: string;
  userId: string;
  accountId: string;
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
  accountId: string;
  balance: number;
  rate: number;
  earnedThisMonth: number;
  earnedTotal: number;
  lastPaidAt: string;
  nextPayoutAt: string;
}

export const fetchGoals = () =>
  apiFetch<{ success: boolean; goals: SavingsGoal[] }>('/api/v1/savings/goals').then((r) => r.goals);

// Real bug found live (2026-08-19, direct click-through of the newly-rebuilt
// CreateGoalForm): the backend real-400s "Idempotency-Key header is required" on this
// endpoint (same requirement depositToGoal/claimInterest below already honor) -- this
// call had simply never sent one. Pre-existing gap, not introduced by this pass; never
// surfaced before because bank-mfe's own goal-creation form had never been exercised
// end to end against the real deployed backend until this live-verification.
export const createGoal = (name: string, targetAmount: number, monthlyContribution?: number, targetDate?: string, category?: string) =>
  apiFetch<{ success: boolean; goal: SavingsGoal }>('/api/v1/savings/goals', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ name, targetAmount, monthlyContribution, targetDate, category }),
  }).then((r) => r.goal);

export const depositToGoal = (goalId: string, amount: number, fromAccountId?: string) =>
  apiFetch<{ success: boolean; message: string; goal: SavingsGoal }>('/api/v1/savings/deposit', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ goalId, amount, fromAccountId }),
  });

export const fetchInterestJar = () =>
  apiFetch<{ success: boolean; jar: InterestJar }>('/api/v1/savings/interest-jar').then((r) => r.jar);

export const claimInterest = () =>
  apiFetch<{ success: boolean; message: string; claimed: number; newBalance: number }>('/api/v1/savings/interest-jar/claim', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

// Real itunda Deposit Protection Fund (2026-08-11) -- see backend's
// DepositProtectionFund.kt doc comment for the full honesty framing: real, ledger-
// backed mechanics, disclosed everywhere as itunda's own internal reserve, not a real
// BNR-backed deposit insurance scheme. coverageCapPerUser/contributionRateBps are
// itunda's own chosen policy figures.
export interface DepositProtectionStatus {
  fundReserveBalance: number;
  coverageCapPerUser: number;
  contributionRateBps: number;
  lastContributionAt: string | null;
  yourTotalDeposits: number;
  yourCoveredBalance: number;
}

export const fetchDepositProtectionStatus = () =>
  apiFetch<{ success: boolean; status: DepositProtectionStatus }>('/api/v1/savings/deposit-protection').then((r) => r.status);

// Real Kakao Pay 머니굴리기 ("rolling money") round-up auto-saving (rw.itunda.savings.
// RoundUpService, real since well before this session) -- first client UI for this
// feature anywhere (item 112, found via a content-grep sweep: Android has a real
// client, bank-mfe and iOS never did). Honest v1 scope, matching Android's own current
// client exactly: only the goal destination (targetGoalId), not the newer
// (2026-07-27) stock-destination option -- a real, separate, not-yet-started gap on
// every client including Android's.
export const ROUND_UP_INCREMENTS = [100, 500, 1000] as const;

export interface RoundUpSettings {
  id: string;
  userId: string;
  enabled: boolean;
  roundToNearest: number;
  targetGoalId: string | null;
}

export const fetchRoundUpSettings = () =>
  apiFetch<{ success: boolean; settings: RoundUpSettings | null }>('/api/v1/savings/round-up').then((r) => r.settings);

export const setRoundUpSettings = (enabled: boolean, roundToNearest: number, targetGoalId: string | null) =>
  apiFetch<{ success: boolean; settings: RoundUpSettings }>('/api/v1/savings/round-up', {
    method: 'POST',
    body: JSON.stringify({ enabled, roundToNearest, targetGoalId }),
  }).then((r) => r.settings);
