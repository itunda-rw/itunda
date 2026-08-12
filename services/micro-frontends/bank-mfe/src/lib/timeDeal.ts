import { apiFetch } from './api';

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment for the full sourced account. A time-boxed, quantity-capped discount OVERLAY
// on an existing product -- distinct from the always-on "🔥 Deals" rail
// (fetchShopDeals in lib/shopping.ts), which surfaces a permanent discountPercent/
// originalPrice, not a scheduled event. This file is the first client for an
// already-real backend that previously had zero UI anywhere.

export interface TimeDeal {
  id: string;
  merchantId: string;
  productId: string;
  dealPrice: number;
  originalPrice: number;
  totalQuantity: number;
  remainingQuantity: number;
  startsAt: string;
  endsAt: string;
  createdAt: string;
}

export interface TimeDealView {
  deal: TimeDeal;
  productName: string;
  productImageUrl?: string | null;
  businessName: string;
}

export const fetchActiveTimeDeals = () =>
  apiFetch<{ success: boolean; deals: TimeDealView[] }>('/api/v1/time-deals?size=20').then((r) => r.deals);

// Real Toss Shopping banner carousel (2026-08-12, direct user screenshot) -- see
// backend TimeDealService.getBanners's own doc comment: derives every banner from a
// real, currently-live, merchant-priced Time Deal, never invented promo content. Same
// endpoint Android's ShopScreen.kt already ported (docs/DESIGN_REFERENCES.md Section 53).
export const fetchShopBanners = () =>
  apiFetch<{ success: boolean; banners: TimeDealView[] }>('/api/v1/time-deals/banners').then((r) => r.banners);

export const fetchTimeDeal = (dealId: string) =>
  apiFetch<{ success: boolean; deal: TimeDealView }>(`/api/v1/time-deals/${dealId}`).then((r) => r.deal);
