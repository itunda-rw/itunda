import { apiFetch } from './api';
import { randomUUID } from './uuid';
import type { Account } from './account';

// Real KakaoBank 카카오뱅크 mini-style capped starter account (rw.itunda.account.
// YouthAccountService, 2026-07-28) -- first client UI for this backend feature anywhere
// (found with zero client UI on any platform, item 99 of this session's parity-gap
// sweep). See YouthAccountService's own doc comment for the full sourced account: real
// balance/daily/monthly caps plus a real age-eligibility gate (7-18 inclusive).
//
// Renamed from "Mini account" to "Youth account" (2026-08-23, direct user correction) --
// see docs/UI_UX_GUIDELINES.md §12: the old bare "Mini" name collided with Saronite's own
// real "mini-app" framework naming in the same Explore/All-tab area, and needed a
// subtitle to explain itself where "Youth account" doesn't. The real backend account
// TYPE is still `AccountType.MINI` (a live DB-persisted value, deliberately unchanged);
// only the URL path, request/response shapes, and display copy renamed.

export const openYouthAccount = () =>
  apiFetch<{ success: boolean; account: Account }>('/api/v1/account/youth/open', { method: 'POST' }).then((r) => r.account);

// Real bug found live 2026-08-05 via a repo-wide idempotency-coverage audit: this
// endpoint posted a real account-to-account ledger transaction on every call with no
// Idempotency-Key requirement -- a network-timeout retry of the exact same deposit
// would move the same money twice. Fixed on the backend (YouthAccountController.kt) and
// here, and on Android/iOS the same day.
export const depositToYouthAccount = (amount: number) =>
  apiFetch<{ success: boolean; id: string; amount: number; completedAt: string }>('/api/v1/account/youth/deposit', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });
