// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model. See the backend's SaccoShareholding.kt doc comment for
// the full sourced account (416 real government-backed cooperatives, one per
// administrative sector, established 2008/2009 -- 4M+ members, RWF 200B+ deposits as
// of 2024). Distinct from lib/ikimina.ts (informal rotating-pot ROSCA, no shares/
// dividends): a SACCO member buys real shares and receives periodic real dividend
// distributions tied to the pool's real performance. The second feature in this
// codebase not sourced from Toss/Kakao/Naver/Coupang.

import { apiFetch } from './api';
import { randomUUID } from './uuid';

export interface SaccoShareholding {
  id: string;
  userId: string;
  accountId: string;
  sharesHeld: number;
  totalContributed: number;
  createdAt: string;
}

export interface SaccoDividendPayout {
  id: string;
  distributionId: string;
  shareholdingId: string;
  amount: number;
  payoutTransactionId: string;
  createdAt: string;
}

export const fetchMySaccoShareholding = () =>
  apiFetch<{ success: boolean; shareholding: SaccoShareholding | null; currentValue: number | null }>('/api/v1/sacco/shares/me');

export const fetchMySaccoDividendHistory = () =>
  apiFetch<{ success: boolean; payouts: SaccoDividendPayout[] }>('/api/v1/sacco/dividends/me').then((r) => r.payouts);

export const buySaccoShares = (amount: number) =>
  apiFetch<{ success: boolean; shareholding: SaccoShareholding; currentValue: number }>('/api/v1/sacco/shares/buy', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });

export const redeemSaccoShares = (amount: number) =>
  apiFetch<{ success: boolean; shareholding: SaccoShareholding; currentValue: number }>('/api/v1/sacco/shares/redeem', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  });
