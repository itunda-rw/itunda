// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 153) -- see the backend's UpfrontInterestDeposit.kt / UpfrontInterestDepositService.kt
// doc comments for the full sourced mechanics and the no-early-withdrawal design choice
// this depends on: the full year's 2.80% interest is paid the moment the deposit opens
// (straight to the main account, immediately spendable), not at maturity like every other
// savings product (WeeklySavingsPlan/InterestJar/SavingsGoal) -- the principal itself
// stays locked in its own dedicated account for the full 12-month term with no early exit,
// the one real design choice this product depends on to not be a money-printing exploit.
// Backend has been real (ledger-backed) but had zero client anywhere until now.

import { apiFetch } from './api';

export type UpfrontDepositStatus = 'ACTIVE' | 'MATURED';

export interface UpfrontInterestDeposit {
  id: string;
  userId: string;
  accountId: string;
  principal: number;
  interestRate: number;
  interestPaid: number;
  status: UpfrontDepositStatus;
  openedAt: string;
  maturesAt: string;
  maturedAt: string | null;
  withdrawnAt: string | null;
}

// Real fixed constants, purely for display (min/max input hints) -- mirrors
// UpfrontInterestDepositService's own MIN_PRINCIPAL/MAX_PRINCIPAL/ANNUAL_RATE; the
// backend remains the actual source of truth and authoritative validator.
export const UPFRONT_DEPOSIT_MIN_PRINCIPAL = 10000;
export const UPFRONT_DEPOSIT_MAX_PRINCIPAL = 50000000;
export const UPFRONT_DEPOSIT_ANNUAL_RATE = 2.80;

export const fetchMyUpfrontDeposits = () =>
  apiFetch<{ success: boolean; deposits: UpfrontInterestDeposit[] }>('/api/v1/upfront-deposits').then((r) => r.deposits);

export const openUpfrontDeposit = (principal: number) =>
  apiFetch<{ success: boolean; deposit: UpfrontInterestDeposit; message: string }>('/api/v1/upfront-deposits', {
    method: 'POST',
    body: JSON.stringify({ principal }),
  });

// Only succeeds once the deposit's real 12-month term has matured -- see the backend's
// UPFRONT_DEPOSIT_NOT_MATURED error, surfaced as-is rather than guessed at client-side.
export const withdrawUpfrontDeposit = (id: string) =>
  apiFetch<{ success: boolean; deposit: UpfrontInterestDeposit; message: string }>(`/api/v1/upfront-deposits/${id}/withdraw`, {
    method: 'POST',
  });
