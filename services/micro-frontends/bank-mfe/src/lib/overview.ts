import { apiFetch } from './api';
import { randomUUID } from './uuid';

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

// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots). See OverviewService.kt's own doc comments for why
// each of these 4 is shaped the way it is (Car is raw count+total, not a
// duplicated valuation; Tax is always real, even at zero payments).
export interface CardsSummary {
  hasCard: boolean;
  last4: string | null;
  design: string | null;
  frozen: boolean | null;
}

export interface VehicleSummary {
  vehicleCount: number;
  totalPurchasePrice: number;
}

export interface TaxSummary {
  totalPaid: number;
  paymentCount: number;
}

export interface PointsSummary {
  rewardsTotal: number;
  payMoneyBalance: number;
}

export interface Overview {
  netWorth: number;
  accounts: AccountSummary[];
  savings: SavingsSummary;
  loans: LoansSummary;
  investments: InvestmentsSummary;
  insurance: InsuranceSummary;
  linkedAccounts: LinkedAccountSummary[];
  cards: CardsSummary;
  vehicles: VehicleSummary;
  tax: TaxSummary;
  points: PointsSummary;
}

export const fetchOverview = () => apiFetch<{ success: boolean } & Overview>('/api/v1/overview').then((r) => r);

// Real Toss "자산 변화" (asset change over time) reference (2026-09-12, direct
// user-supplied total-assets screenshots) -- see the backend's NetWorthSnapshot doc
// comment for why `liquidTotal` is deliberately narrower than `Overview.netWorth`
// (account balances + reward points only, matching Toss's own real disclosure of
// excluding anything that swings for reasons unrelated to genuine saving/spending).
// `month` is a raw "yyyy-MM" string -- format it for display, don't assume English.
export interface NetWorthHistoryPoint {
  month: string;
  liquidTotal: number;
}

export const fetchNetWorthHistory = () =>
  apiFetch<{ success: boolean; history: NetWorthHistoryPoint[] }>('/api/v1/overview/net-worth-history').then((r) => r.history);

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

// Idempotency-Key added 2026-09-07 (Overview product-completeness pass) -- a lost
// response after a real successful link previously created a genuine duplicate
// LinkedAccount row and burned a second real simulated ProviderConnector call.
export const linkAccount = (provider: string, externalAccountNumber: string) =>
  apiFetch<{ success: boolean; linkedAccount: LinkedAccount }>('/api/v1/accounts/link', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ provider, externalAccountNumber }),
  }).then((r) => r.linkedAccount);

export const unlinkAccount = (accountId: string) =>
  apiFetch<{ success: boolean; linkedAccount: LinkedAccount }>(`/api/v1/accounts/link/${accountId}/unlink`, {
    method: 'POST',
  }).then((r) => r.linkedAccount);
