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
