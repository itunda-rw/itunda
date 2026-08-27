import { apiFetch } from './api';
import type { MerchantCouponView } from './shopping';

// Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
// "Coupon box" + Membership screens). Both endpoints this file wraps had ZERO
// controller endpoint anywhere before this pass -- see MerchantCouponService.kt's
// own doc comments (browseCoupons/getMyRedemptions) and
// MerchantLoyaltyPointsService.kt's (getMyBalances) for the full sourced account.
//
// Deliberately no curated brand-campaign browsing (the real reference's own
// Online/Offline/특가 tabs) -- itunda has no such marketing partnerships. This is
// one flat, honest list of itunda's own real merchant coupons. Same honesty
// discipline for loyalty balances: real itunda per-merchant "Store points" only,
// never a fabricated third-party brand (Naver/Kakao/Coupang/etc. have no itunda
// equivalent).

export interface CouponBrowseView {
  coupon: MerchantCouponView['coupon'];
  merchantName: string;
  eligible: boolean;
  alreadyRedeemed: boolean;
}

export const browseCoupons = () =>
  apiFetch<{ success: boolean; coupons: CouponBrowseView[] }>('/api/v1/merchant/coupons/browse').then((r) => r.coupons);

export interface CouponRedemption {
  id: string;
  couponId: string;
  merchantId: string;
  transactionId: string;
  discountAmount: number;
  redeemedAt: string;
}

export const fetchMyCouponRedemptions = () =>
  apiFetch<{ success: boolean; redemptions: CouponRedemption[] }>('/api/v1/merchant/coupons/my-redemptions').then((r) => r.redemptions);

export interface LoyaltyBalance {
  merchantId: string;
  merchantName: string;
  pointBalance: number;
}

export const fetchMyLoyaltyBalances = () =>
  apiFetch<{ success: boolean; balances: LoyaltyBalance[]; total: number }>('/api/v1/merchant/loyalty/my-balances');
