import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { Account } from './account';

// Real KakaoBank 카카오뱅크 mini-style capped starter account (rw.itunda.account.
// MiniAccountService, 2026-07-28) -- first client UI for this backend feature anywhere
// (found with zero client UI on any platform, item 99 of this session's parity-gap
// sweep). See MiniAccountService's own doc comment for the full sourced account: real
// balance/daily/monthly caps plus a real age-eligibility gate (7-18 inclusive).

export const openMiniAccount = () =>
  apiFetch<{ success: boolean; account: Account }>('/api/v1/account/mini/open', { method: 'POST' }).then((r) => r.account);

// Real bug found live 2026-08-05 via a repo-wide idempotency-coverage audit: this
// endpoint posted a real account-to-account ledger transaction on every call with no
// Idempotency-Key requirement -- a network-timeout retry of the exact same deposit
// would move the same money twice. Fixed on the backend (MiniAccountController.kt) and
// here, and on Android/iOS the same day.
export const depositToMiniAccount = (amount: number) =>
  apiFetch<{ success: boolean; id: string; amount: number; completedAt: string }>('/api/v1/account/mini/deposit', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });
