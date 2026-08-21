import { apiFetch } from './api';

// Real Toss-style unified account overview (rw.itunda.overview.OverviewService) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere (Android,
// iOS, or bank-mfe) until the Android port the same day. Aggregates accounts, savings,
// loans, investments, insurance, and linked external bank/MoMo accounts in one call.
// See OverviewService.kt's own doc comment for why insurance is excluded from net
// worth (a sunk expense, not an asset).

export interface AccountSummary {
  id: string;
  type: string;
  name: string;
  balance: number;
  currency: string;
}

export interface SavingsSummary {
  totalSaved: number;
  goalCount: number;
}

export interface LoansSummary {
  totalOutstanding: number;
  activeCount: number;
}

export interface InvestmentsSummary {
  totalCostBasis: number;
  holdingCount: number;
}

export interface InsuranceSummary {
  activePolicyCount: number;
  totalMonthlyPremium: number;
}

export interface LinkedAccountSummary {
  id: string;
  provider: string;
  maskedAccountNumber: string;
  status: string;
  demoBalance: number | null;
  demoBalanceCurrency: string | null;
  isDemoBalance: boolean;
}

export interface Overview {
  netWorth: number;
  accounts: AccountSummary[];
  savings: SavingsSummary;
  loans: LoansSummary;
  investments: InvestmentsSummary;
  insurance: InsuranceSummary;
  linkedAccounts: LinkedAccountSummary[];
}

export const fetchOverview = () => apiFetch<{ success: boolean } & Overview>('/api/v1/overview').then((r) => r);

// Real external bank/MoMo account linking (rw.itunda.overview.LinkedAccountService) --
// a real simulated per-rail verification (same ProviderConnector/RailCatalog
// mechanism transfers/bills/airtime use), demo balance only generated when linking
// actually succeeds. This is the raw entity shape LinkedAccountController returns,
// distinct from the summary shape fetchOverview's linkedAccounts uses above.
export interface LinkedAccount {
  id: string;
  userId: string;
  provider: string;
  externalAccountNumberMasked: string;
  status: string;
  failureReason: string | null;
  linkedAt: string;
  unlinkedAt: string | null;
  demoBalance: number | null;
  demoBalanceCurrency: string | null;
}

export const fetchLinkedAccounts = () =>
  apiFetch<{ success: boolean; linkedAccounts: LinkedAccount[] }>('/api/v1/accounts/linked').then((r) => r.linkedAccounts);

export const linkAccount = (provider: string, externalAccountNumber: string) =>
  apiFetch<{ success: boolean; linkedAccount: LinkedAccount }>('/api/v1/accounts/link', {
    method: 'POST',
    body: JSON.stringify({ provider, externalAccountNumber }),
  }).then((r) => r.linkedAccount);

export const unlinkAccount = (accountId: string) =>
  apiFetch<{ success: boolean; linkedAccount: LinkedAccount }>(`/api/v1/accounts/link/${accountId}/unlink`, {
    method: 'POST',
  }).then((r) => r.linkedAccount);
