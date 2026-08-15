import { apiFetch } from './api';

// Field shapes match services/backend's real domain entities exactly (Wallet,
// Transaction) -- see WalletController.kt, the source of truth this talks to.

export interface Wallet {
  id: string;
  userId: string;
  accountNumber: string;
  accountName: string;
  type: 'MAIN' | 'SAVINGS' | 'INVESTMENT' | 'LOAN' | 'MINI';
  balance: number;
  availableBalance: number;
  currency: string;
  isActive: boolean;
  createdAt: string;
}

export interface Transaction {
  id: string;
  referenceNumber: string;
  senderId: string;
  recipientId: string;
  fromWalletId: string | null;
  toWalletId: string | null;
  amount: number;
  fee: number;
  currency: string;
  type: string;
  status: string;
  description: string;
  channel: string | null;
  completedAt: string;
  createdAt: string;
}

export const fetchWallets = () =>
  apiFetch<{ success: boolean; wallets: Wallet[] }>('/api/v1/wallet').then((r) => r.wallets);

export const fetchTransactions = () =>
  apiFetch<{ success: boolean; transactions: Transaction[] }>('/api/v1/wallet/transactions').then((r) => r.transactions);

// Real Toss Timeline-style unusual-spend flag -- see WalletService.getTransactionTimeline's
// own doc comment for the full sourced account.
export interface TransactionTimelineEntry {
  transaction: Transaction;
  unusuallyLarge: boolean;
}

export const fetchTransactionTimeline = () =>
  apiFetch<{ success: boolean; timeline: TransactionTimelineEntry[] }>('/api/v1/wallet/transactions/timeline').then((r) => r.timeline);

// Real recurring-payment ("subscription") detection over a user's own real
// transaction history -- see SubscriptionDetectionService's own doc comment for the
// real Toss "구독 관리" capability this closes, including the 2026-07-26 price-change
// alert. Found with zero client UI anywhere.
export interface DetectedSubscription {
  displayName: string;
  amount: number;
  cadence: 'WEEKLY' | 'MONTHLY';
  occurrenceCount: number;
  lastPaidAt: string;
  nextExpectedAt: string;
  monthlyEquivalent: number;
  priceIncreased: boolean;
  previousAmount: number | null;
}

export const fetchSubscriptions = () =>
  apiFetch<{ success: boolean; subscriptions: DetectedSubscription[]; estimatedMonthlyTotal: number }>('/api/v1/wallet/subscriptions');

// Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
// WalletService.getSpendingInsight, 2026-07-13) -- first client UI for this backend
// feature (item 106, found backend-only via a fresh matrix scan). Built over the real
// ledger (every WALLET debit's sibling leg reveals what it actually paid for), not the
// transactions table -- see the backend's own doc comment for the full account.
export interface SpendingCategory {
  name: string;
  amount: number;
}

export const fetchSpendingInsight = () =>
  apiFetch<{ success: boolean; categories: SpendingCategory[]; totalSpent: number }>('/api/v1/wallet/spending');

// Real Kakao Pay 페이아이 소비 리포트 (AI spending report, sourced 2026-08) -- a real
// month-over-month comparison the all-time fetchSpendingInsight above never had. See
// WalletService.getMonthlySpendingReport's own doc comment on the backend -- an honest
// rules-based comparison against the user's own real ledger history, not a fabricated
// AI model. percentChange is null (not 0%), a real "nothing to compare against yet"
// signal, when a category has no prior-month spend at all.
export interface SpendingComparisonCategory {
  name: string;
  currentAmount: number;
  previousAmount: number;
  percentChange: number | null;
}

export const fetchMonthlySpendingReport = () =>
  apiFetch<{ success: boolean; currentTotal: number; previousTotal: number; percentChange: number | null; categories: SpendingComparisonCategory[] }>(
    '/api/v1/wallet/spending/monthly-report',
  );

// Real Toss-style monthly budgets/limits (item 165, found via a fresh discovery pass:
// WalletService.setBudget/getBudgets and the POST/GET /api/v1/wallet/budgets endpoints
// were already real -- including real 80%/100%-threshold in-app + push notifications,
// wired since 2026-07-28 -- but had zero client anywhere on any platform). `category`
// null means an overall (all-spending) budget; otherwise it must match one of
// fetchSpendingInsight's own real category names, so a budget's "spent" figure is
// grounded in the exact same categorization, not a separate parallel one.
export type BudgetStatus = 'UNDER' | 'NEAR' | 'OVER';

export interface BudgetView {
  category: string | null;
  monthlyLimit: number;
  spent: number;
  remaining: number;
  percentUsed: number;
  status: BudgetStatus;
}

export const fetchBudgets = () =>
  apiFetch<{ success: boolean; budgets: BudgetView[] }>('/api/v1/wallet/budgets').then((r) => r.budgets);

export const setBudget = (category: string | undefined, monthlyLimit: number) =>
  apiFetch<{ success: boolean; budget: { category: string | null; monthlyLimit: number } }>('/api/v1/wallet/budgets', {
    method: 'POST',
    body: JSON.stringify({ category, monthlyLimit }),
  }).then((r) => r.budget);

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168, found via the
// entity-cross-reference discovery sweep -- AutoTopUpController's own
// /api/v1/wallet/{walletId}/auto-topup base path has a path variable in the middle,
// which broke this session's earlier @RequestMapping-prefix sweep's string matching).
// See AutoTopUpService.kt's own doc comment: once this wallet's real balance falls
// below a self-set threshold, a configured amount auto-pulls from a pre-linked
// external account (reusing fetchLinkedAccounts/lib/overview.ts's own real linking),
// capped at a real daily trigger count. Fully real (real background scheduler since
// 2026-07-27, real provider-decline handling) but had zero client anywhere.
export interface AutoTopUpSetting {
  id: string;
  userId: string;
  walletId: string;
  linkedAccountId: string;
  enabled: boolean;
  thresholdAmount: number;
  topUpAmount: number;
  dailyTriggerCap: number;
  triggersToday: number;
  lastTriggerDate: string | null;
  lastTriggeredAt: string | null;
}

export const fetchAutoTopUpSetting = (walletId: string) =>
  apiFetch<{ success: boolean; setting: AutoTopUpSetting }>(`/api/v1/wallet/${walletId}/auto-topup`).then((r) => r.setting);

export const configureAutoTopUp = (
  walletId: string, linkedAccountId: string, thresholdAmount: number, topUpAmount: number, dailyTriggerCap: number, enabled: boolean,
) =>
  apiFetch<{ success: boolean; setting: AutoTopUpSetting }>(`/api/v1/wallet/${walletId}/auto-topup`, {
    method: 'PUT',
    body: JSON.stringify({ linkedAccountId, thresholdAmount, topUpAmount, dailyTriggerCap, enabled }),
  }).then((r) => r.setting);

export const triggerAutoTopUp = (walletId: string) =>
  apiFetch<{ success: boolean; triggered: boolean; reason: string }>(`/api/v1/wallet/${walletId}/auto-topup/trigger`, { method: 'POST' });
