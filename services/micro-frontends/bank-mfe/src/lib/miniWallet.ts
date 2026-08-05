import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { Wallet } from './wallet';

// Real KakaoBank 카카오뱅크 mini-style capped starter wallet (rw.itunda.wallet.
// MiniWalletService, 2026-07-28) -- first client UI for this backend feature anywhere
// (found with zero client UI on any platform, item 99 of this session's parity-gap
// sweep). See MiniWalletService's own doc comment for the full sourced account: real
// balance/daily/monthly caps plus a real age-eligibility gate (7-18 inclusive).

export const openMiniWallet = () =>
  apiFetch<{ success: boolean; wallet: Wallet }>('/api/v1/wallet/mini/open', { method: 'POST' }).then((r) => r.wallet);

// Real bug found live 2026-08-05 via a repo-wide idempotency-coverage audit: this
// endpoint posted a real wallet-to-wallet ledger transaction on every call with no
// Idempotency-Key requirement -- a network-timeout retry of the exact same deposit
// would move the same money twice. Fixed on the backend (MiniWalletController.kt) and
// here, and on Android/iOS the same day.
export const depositToMiniWallet = (amount: number) =>
  apiFetch<{ success: boolean; id: string; amount: number; completedAt: string }>('/api/v1/wallet/mini/deposit', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });
