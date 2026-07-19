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

// Real P2P wallet-to-wallet transfer (2026-07-20) -- closes a real gap found live: this
// app's own home-screen "Transfer" button had zero onClick handler despite
// WalletController's quote/confirm transfer already being fully real and already used by
// Android/iOS (MainViewModel.sendTransfer). Same quote-then-confirm shape those clients
// already use: a quote is short-lived (60s TTL, see TransferQuote.kt) and re-checked at
// confirm time against the authenticated caller, so it can't be hijacked by id alone.

export interface TransferQuote {
  id: string;
  fromWalletId: string;
  recipient: string;
  amount: number;
  fee: number;
  totalDebit: number;
  currency: string;
  status: string;
  expiresAt: string;
}

export const quoteTransfer = (recipient: string, amount: number) =>
  apiFetch<{ success: boolean; quote: TransferQuote }>('/api/v1/wallet/transfer/quote', {
    method: 'POST',
    body: JSON.stringify({ recipient, amount }),
  }).then((r) => r.quote);

export const confirmTransfer = (quoteId: string) =>
  apiFetch<{ success: boolean; message: string; transaction: Transaction; newBalance: number }>('/api/v1/wallet/transfer/confirm', {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({ quoteId }),
  });
