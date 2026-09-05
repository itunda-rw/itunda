// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan client
// -- see the backend's StudentLoanService.kt doc comment for the full sourced
// account. Rwanda has run a national student-loan-and-bursary scheme since Law No.
// 44/2015, administered by BRD since an October 2016 MINEDUC agreement. Real scale:
// Rwf 221.85 billion disbursed to 139,925 students (through mid-2023), fixed
// interest rates of 11% undergraduate / 12% postgraduate, repayment terms of 2-10
// years (brd.rw). Honest v1 limitation: `declaredAnnualHouseholdIncome` is
// self-declared, not verified against BRD's real Financial Means Testing (FMT)
// process, and the real 8%-of-income payroll deduction is only ever a SUGGESTED
// amount here -- itunda has no payroll/RRA-integration path to enforce it.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export type StudentLoanLevel = 'UNDERGRADUATE' | 'POSTGRADUATE';
export type StudentLoanStatus = 'REQUESTED' | 'DISBURSED' | 'IN_GRACE_PERIOD' | 'REPAYING' | 'REPAID' | 'OVERDUE';

export interface StudentLoan {
  id: string;
  userId: string;
  level: StudentLoanLevel;
  declaredAnnualHouseholdIncome: number;
  principalAmount: number;
  outstandingBalance: number;
  interestRate: number;
  status: StudentLoanStatus;
  appliedAt: string;
  disbursedAt: string | null;
  expectedGraduationDate: string;
  graceEndsAt: string | null;
}

export interface StudentLoanSuggestedPayment {
  loanId: string;
  outstandingBalance: number;
  suggestedMonthlyPayment: number;
  note: string;
}

// Idempotency-Key added 2026-09-05 -- matching disburseStudentLoan/repayStudentLoan
// below, a lost response after a successful apply would previously resubmit here
// and hit the backend's own StudentLoanAlreadyActiveException guard on retry (see
// StudentLoanController.apply's own doc comment for the full real gap).
export const applyForStudentLoan = (
  level: StudentLoanLevel,
  declaredAnnualHouseholdIncome: number,
  amount: number,
  expectedGraduationDate: string,
) =>
  apiFetch<{ success: boolean; loan: StudentLoan }>('/api/v1/loans/student/apply', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ level, declaredAnnualHouseholdIncome, amount, expectedGraduationDate }),
  }).then((r) => r.loan);

export const disburseStudentLoan = (loanId: string) =>
  apiFetch<{ success: boolean; loan: StudentLoan }>(`/api/v1/loans/student/${loanId}/disburse`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.loan);

export const declareGraduated = (loanId: string) =>
  apiFetch<{ success: boolean; loan: StudentLoan }>(`/api/v1/loans/student/${loanId}/declare-graduated`, {
    method: 'POST',
  }).then((r) => r.loan);

export const repayStudentLoan = (loanId: string, amount: number) =>
  apiFetch<{ success: boolean; loan: StudentLoan }>(`/api/v1/loans/student/${loanId}/repay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.loan);

export const fetchMyStudentLoans = () =>
  apiFetch<{ success: boolean; loans: StudentLoan[] }>('/api/v1/loans/student/my').then((r) => r.loans);

export const fetchSuggestedPayment = (loanId: string) =>
  apiFetch<{ success: boolean } & StudentLoanSuggestedPayment>(`/api/v1/loans/student/${loanId}/suggested-payment`).then((r) => r);
