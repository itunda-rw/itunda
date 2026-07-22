import { apiFetch } from './api';

// Real "alternative data" credit score (rw.itunda.creditscore / :core's
// CreditScoreService, already load-bearing on LoansService's real risk gate) -- found
// 2026-07-22 fully built on the backend with zero client UI anywhere. Not a real
// bureau score (no regulatory credit-bureau access exists) -- computed live from a
// user's own real transaction/loan/savings/KYC history.

export interface CreditScoreFactor {
  name: string;
  points: number;
  description: string;
}

export interface CreditScoreResult {
  score: number;
  factors: CreditScoreFactor[];
  computedAt: string;
}

export const fetchCreditScore = () =>
  apiFetch<{ success: boolean } & CreditScoreResult>('/api/v1/credit-score').then((r) => r);
