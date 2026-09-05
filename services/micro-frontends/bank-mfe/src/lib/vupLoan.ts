// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan
// client -- see the backend's VupLoanService.kt doc comment for the full sourced
// account. VUP, run by LODA since 2008, subsidizes microloans for income-generating
// activities (farming, livestock, small business) targeted at households in poorer
// Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
// 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
// rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
// limitation: `declaredUbudeheCategory` is self-declared by the user, not verified
// against Rwanda's real government Ubudehe household-classification registry.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type VupLoanPurpose = 'FARMING' | 'LIVESTOCK' | 'BUSINESS';
export type VupLoanStatus = 'REQUESTED' | 'DISBURSED' | 'REPAID' | 'OVERDUE';

export interface VupLoan {
  id: string;
  userId: string;
  declaredUbudeheCategory: number;
  purpose: VupLoanPurpose;
  principalAmount: number;
  outstandingPrincipal: number;
  interestRate: number;
  status: VupLoanStatus;
  appliedAt: string;
  disbursedAt: string | null;
  dueDate: string | null;
}

export interface VupLoanEligibility {
  hasActiveLoan: boolean;
  canApply: boolean;
  minUbudeheCategory: number;
  maxUbudeheCategory: number;
  interestRate: number;
  maxAmount: number;
}

// Idempotency-Key added 2026-09-05 -- matching disburseVupLoan/repayVupLoan below, a
// lost response after a successful apply would previously resubmit here and hit the
// backend's own VupLoanAlreadyActiveException guard on retry (see
// VupLoanController.apply's own doc comment for the full real gap).
export const applyForVupLoan = (declaredUbudeheCategory: number, purpose: VupLoanPurpose, amount: number) =>
  apiFetch<{ success: boolean; loan: VupLoan }>('/api/v1/loans/vup/apply', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ declaredUbudeheCategory, purpose, amount }),
  }).then((r) => r.loan);

export const disburseVupLoan = (loanId: string) =>
  apiFetch<{ success: boolean; loan: VupLoan }>(`/api/v1/loans/vup/${loanId}/disburse`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.loan);

export const repayVupLoan = (loanId: string, amount: number) =>
  apiFetch<{ success: boolean; loan: VupLoan }>(`/api/v1/loans/vup/${loanId}/repay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.loan);

export const fetchMyVupLoans = () =>
  apiFetch<{ success: boolean; loans: VupLoan[] }>('/api/v1/loans/vup/my').then((r) => r.loans);

export const fetchVupLoanEligibility = () =>
  apiFetch<{ success: boolean } & VupLoanEligibility>('/api/v1/loans/vup/eligibility').then((r) => r);
