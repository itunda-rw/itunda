import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real multi-lender loan marketplace (rw.itunda.loans) -- BNR-licensed partner banks
// (Bank of Kigali, Equity Bank Rwanda, Urwego Bank) alongside itunda's own book; only
// itunda has a real underwriting/disbursement path, see LoanOffer.kt's own doc
// comment. Found 2026-07-22 fully built on the backend with zero client UI anywhere.

export interface LoanOffer {
  id: string;
  lenderId: string;
  lenderName: string;
  name: string;
  maxAmount: number;
  interestRate: number;
  term: string;
  requirements: string;
}

export interface Lender {
  id: string;
  name: string;
  kind: string;
}

export interface LoanAccount {
  id: string;
  userId: string;
  walletId: string;
  offerId: string;
  principal: number;
  outstanding: number;
  interestRate: number;
  status: string;
  disbursedAt: string;
}

export const fetchLoanOffers = (lenderId?: string) =>
  apiFetch<{ success: boolean; offers: LoanOffer[] }>(`/api/v1/loans/offers${lenderId ? `?lenderId=${lenderId}` : ''}`).then(
    (r) => r.offers,
  );

export const fetchLenders = () => apiFetch<{ success: boolean; lenders: Lender[] }>('/api/v1/loans/lenders').then((r) => r.lenders);

export const fetchMyLoans = () => apiFetch<{ success: boolean; loans: LoanAccount[] }>('/api/v1/loans/my-loans').then((r) => r.loans);

export const applyForLoan = (loanId: string, amount: number) =>
  apiFetch<{ success: boolean; message: string; loan: LoanAccount }>('/api/v1/loans/apply', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ loanId, amount }),
  });

export const repayLoan = (loanId: string, amount: number) =>
  apiFetch<{ success: boolean; message: string; remaining: number; newBalance: number }>('/api/v1/loans/repay', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ loanId, amount }),
  });

// Real 대환대출 (loan refinancing, 2026-07-26) -- see LoansService.refinanceLoan's own
// doc comment. Itunda's own book only, not a real cross-institution comparison.
export interface RefinanceResult {
  success: boolean;
  message: string;
  oldLoanId: string;
  oldInterestRate: number;
  newLoanId: string;
  newInterestRate: number;
  newLoanName: string;
  amount: number;
  creditScore: number;
}

export const refinanceLoan = (loanId: string) =>
  apiFetch<RefinanceResult>('/api/v1/loans/refinance', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ loanId }),
  });

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit, 2026-07-27)
// -- a real pre-approved credit LIMIT, not a lump-sum disbursement: draw any amount up
// to the limit whenever needed, real interest accrues only on the actual drawn
// balance. Genuinely, structurally distinct from the term loans above -- a repayment
// here never closes the account, it just frees up available credit to draw again.
// Found 2026-07-29 via the full-backend-endpoint sweep: the entire feature (open/get/
// draw/repay, interest accrual, security-alert push) was real and fully built with
// zero client anywhere on any of the 3 platforms.
export interface OverdraftAccount {
  id: string;
  userId: string;
  walletId: string;
  creditLimit: number;
  drawnBalance: number;
  interestRate: number;
  status: 'ACTIVE' | 'CLOSED';
  createdAt: string;
  updatedAt: string;
}

export const fetchMyOverdraft = () =>
  apiFetch<{ success: boolean; account: OverdraftAccount | null }>('/api/v1/loans/overdraft').then((r) => r.account);

export const openOverdraft = (requestedLimit: number) =>
  apiFetch<{ success: boolean; account: OverdraftAccount }>('/api/v1/loans/overdraft/open', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ requestedLimit }),
  }).then((r) => r.account);

export interface OverdraftDrawResult {
  success: boolean;
  transactionId: string;
  amount: number;
  drawnBalance: number;
  availableCredit: number;
}

export const drawOverdraft = (amount: number) =>
  apiFetch<OverdraftDrawResult>('/api/v1/loans/overdraft/draw', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });

export interface OverdraftRepayResult extends OverdraftDrawResult {
  newBalance: number;
}

export const repayOverdraft = (amount: number) =>
  apiFetch<OverdraftRepayResult>('/api/v1/loans/overdraft/repay', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });
