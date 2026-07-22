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
