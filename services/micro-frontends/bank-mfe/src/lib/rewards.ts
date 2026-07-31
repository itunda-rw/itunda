import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Toss-style rewards/mission-task system (rw.itunda.rewards) -- see
// RewardsController's own doc comment: the Saronite reward-tasks mini-app's native
// bridge (android/.../SaroniteBridge.kt, ios/.../SaroniteBrownfieldModule.swift) has
// called these routes since it was built, but bank-mfe -- the actual banking app --
// had zero client for any of it. Found 2026-08-01 via a full-backend-endpoint sweep.

export interface RewardTask {
  id: string;
  title: string;
  subtitle: string;
  rewardAmount: number;
  claimed: boolean;
  claimedAt: string | null;
  eligible: boolean;
}

export interface RewardTasksResult {
  tasks: RewardTask[];
  rewardsTotal: number;
}

export interface ReferralInfo {
  referralCode: string;
  referredCount: number;
  completedReferralCount: number;
}

export interface ClaimRewardResult {
  message: string;
  rewardAmount: number;
  newBalance: number;
}

export interface StepReportResult {
  steps: number;
  newlyEarnedTiers: number[];
  newlyEarnedAmount: number;
  totalEarnedToday: number;
}

export const fetchRewardTasks = () =>
  apiFetch<{ success: boolean } & RewardTasksResult>('/api/v1/rewards/tasks').then((r) => r);

export const fetchReferralInfo = () =>
  apiFetch<{ success: boolean } & ReferralInfo>('/api/v1/rewards/referral').then((r) => r);

export const claimRewardTask = (taskId: string) =>
  apiFetch<{ success: boolean } & ClaimRewardResult>('/api/v1/rewards/claim', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ taskId }),
  }).then((r) => r);

// Honest v1 scope-down: bank-mfe has no real step-counter sensor access (this is a
// browser, not a phone with a pedometer) -- the Saronite bridge's own doc comment
// already names `steps` as "the mini-app's own manually-entered count, not a real
// device pedometer reading" even on native, so a manual-entry field here is no less
// honest than the native clients' own.
export const reportSteps = (steps: number) =>
  apiFetch<{ success: boolean } & StepReportResult>('/api/v1/rewards/steps', {
    method: 'POST',
    body: JSON.stringify({ steps }),
  }).then((r) => r);

export const fetchTodaySteps = () =>
  apiFetch<{ success: boolean; steps: number }>('/api/v1/rewards/steps/today').then((r) => r.steps);
