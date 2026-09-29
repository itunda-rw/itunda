// Real gap found live (uncalled-endpoint sweep, 2026-08-29): this business news/
// updates feed (itunda Maps redesign, 2026-08-28, Naver Map 소식-tab reference) has
// been fully built on the backend since it shipped, already displayed live to
// customers on Android/iOS's real place-detail News tab, but no merchant client
// anywhere had a way to actually post one -- a live customer-facing surface with no
// way to populate it. periodStart/periodEnd deliberately omitted from this first pass
// (both genuinely optional/independent per MerchantUpdate.kt's own doc comment;
// most real updates -- a plain notice -- don't have a date range at all).
//
// Split out of lib/merchant.ts (not added there) since that file was already at its
// file-size-lint baseline -- same "extract instead of growing a baselined file"
// discipline bank-mfe's lib/eats.ts already established for lib/eatsRider.ts/
// lib/eatsGroupOrders.ts.
import { apiFetch } from './api';

export type MerchantUpdateLabel = 'NOTICE' | 'EVENT' | 'PROMO';

export interface MerchantUpdate {
  id: string;
  merchantId: string;
  label: MerchantUpdateLabel;
  title: string;
  body: string;
  periodStart: string | null;
  periodEnd: string | null;
  likeCount: number;
  createdAt: string;
}

export const fetchMerchantUpdates = (merchantId: string) =>
  apiFetch<{ success: boolean; updates: MerchantUpdate[] }>(`/api/v1/merchant/${merchantId}/updates`).then((r) => r.updates);

export const postMerchantUpdate = (label: MerchantUpdateLabel, title: string, body: string) =>
  apiFetch<{ success: boolean; update: MerchantUpdate }>('/api/v1/merchant/updates', {
    method: 'POST',
    body: JSON.stringify({ label, title, body }),
  }).then((r) => r.update);
