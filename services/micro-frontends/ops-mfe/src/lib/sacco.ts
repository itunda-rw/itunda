import { apiFetch } from './api';

// Real Umurenge SACCO-style shares & dividends ops surface (2026-09-07, Savings/
// Account product-completeness pass) -- a real, own dedicated file rather than
// growing queues.ts, matching lib/system.ts's own established extraction
// precedent (that file's own doc comment). See the backend's SaccoService.kt
// own doc comment for declareDividend's disclosed half-built-feature gap this
// closes.

export interface SaccoPoolStatus {
  poolAccountBalance: number;
  totalSharesOutstanding: number;
  solvent: boolean;
}

export const fetchSaccoPoolStatus = () =>
  apiFetch<{ success: boolean } & SaccoPoolStatus>('/api/v1/sacco/pool-status');

export const declareSaccoDividend = () =>
  apiFetch<{ success: boolean; distribution: { totalDividendPaid: number; dividendRate: number } }>('/api/v1/sacco/declare-dividend', {
    method: 'POST',
  });
