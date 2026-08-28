import { apiFetch } from './api';

// Real 비즈프로필 (Karrot Business Profile) visitor-count trend (itunda Hood redesign,
// 2026-08-28) -- see backend MerchantProfileView.kt's own doc comment. Kept as its own
// file rather than growing the already-at-baseline lib/merchant.ts.
export interface MerchantProfileView {
  id: string;
  merchantId: string;
  viewDate: string;
  viewCount: number;
}

export const fetchProfileViewTrend = (days = 7) =>
  apiFetch<{ success: boolean; trend: MerchantProfileView[] }>(`/api/v1/merchant/profile-views/trend?days=${days}`).then((r) => r.trend);
