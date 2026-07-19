import { apiFetch } from './api';

// Field shapes match services/backend's real domain entities exactly (Wallet,
// Transaction) -- see WalletController.kt, the source of truth this talks to.

export interface Wallet {
  id: string;
  userId: string;
  accountNumber: string;
  accountName: string;
  type: 'MAIN' | 'SAVINGS' | 'INVESTMENT' | 'LOAN';
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
