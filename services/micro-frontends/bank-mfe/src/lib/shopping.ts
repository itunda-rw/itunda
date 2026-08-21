import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real "browse partner merchants, earn cashback" catalog (Toss Shopping parity) -- see
// ShoppingCashbackService.kt's own doc comment. First real UI touchpoint for this
// endpoint, added 2026-07-17 alongside making this app real.

export interface ShoppingMerchant {
  merchantId: string;
  businessName: string;
  category: string | null;
  cashbackRate: string;
  latitude?: number | null;
  longitude?: number | null;
  // Real browse-card enrichment (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's Eats
  // recommendations #1/#2. photoUrl/minOrderAmount are real, merchant-set (null when
  // unset); rating/reviewCount are real, batch-aggregated from EatsReview. distanceKm/
  // deliveryTimeMinutes are only present when the caller supplied its own real
  // buyerLat/buyerLng -- deliveryTimeMinutes is a real, clearly-an-ESTIMATE derived from
  // that distance (see ShoppingController.estimateDeliveryMinutes's own doc comment on
  // the backend), never a fabricated/measured number.
  photoUrl?: string | null;
  minOrderAmount?: number | null;
  rating?: number | null;
  reviewCount?: number;
  distanceKm?: number | null;
  deliveryTimeMinutes?: number | null;
  // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own doc comment on
  // the backend. Null/undefined unless the merchant has actually set one.
  phoneNumber?: string | null;
  openingHours?: string | null;
  // Real Baemin 찜 (favorites) count (2026-08-16) -- see
  // ShoppingController.getEligibleMerchants's own doc comment. The real backend
  // response always includes it (0 for an unfavorited merchant); optional here only
  // because a few call sites construct a partial synthetic ShoppingMerchant from a
  // narrower source object (an ad/deal/favorite row) that never carried this field.
  favoriteCount?: number;
  // Real Baemin CEO app 영업일시중지 (temporarily pause business) (2026-08-16) -- see
  // Merchant.isAcceptingOrders's own doc comment. Optional for the same partial-object
  // reason favoriteCount is; treat a missing value as accepting (true), matching the
  // real backend column's own default.
  isAcceptingOrders?: boolean;
  // Real Uber Eats-style "busy kitchen" signal (2026-08-16) -- see
  // ShoppingController.getEligibleMerchants's own doc comment. deliveryTimeMinutes
  // above already includes the real delay bump when this is true.
  isBusy?: boolean;
  // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule)
  // (2026-08-16) -- see Merchant.isClosedToday's own doc comment. Computed
  // server-side from the real restaurant's own closed-weekday schedule against
  // real current Rwanda local time, so this can never drift from what
  // EatsOrderService.placeOrder's own enforcement actually checks.
  closedToday?: boolean;
}

// Real Naver Pay 멤버십 데이 (Membership Day) boost -- see the backend's
// ShoppingCashbackService doc comment. Single source of truth for "is today boosted",
// so this banner never drifts from what the server actually applies.
export const fetchMembershipDayStatus = () =>
  apiFetch<{ success: boolean; isMembershipDay: boolean; multiplier: number }>('/api/v1/shopping/membership-day');

// Real category/search filter (2026-07-21) -- both optional and combinable, mirroring
// Eats' fetchRestaurants (lib/eats.ts) exactly, since both hit the same
// ShoppingController.getEligibleMerchants endpoint on the backend.
export const fetchShoppingCatalog = (category?: string, q?: string, buyerLat?: number, buyerLng?: number) => {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  if (q) params.set('q', q);
  if (buyerLat != null && buyerLng != null) {
    params.set('buyerLat', String(buyerLat));
    params.set('buyerLng', String(buyerLng));
  }
  params.set('size', '100');
  const qs = params.toString();
  return apiFetch<{ success: boolean; merchants: ShoppingMerchant[] }>(`/api/v1/shopping/merchants${qs ? `?${qs}` : ''}`).then(
    (r) => r.merchants,
  );
};

// Real distinct category list, derived from real merchant data -- see
// MerchantRepository.findDistinctCategories's own doc comment. Same endpoint Eats'
// fetchRestaurantCategories (lib/eats.ts) already uses.
export const fetchMerchantCategories = () =>
  apiFetch<{ success: boolean; categories: string[] }>('/api/v1/shopping/merchants/categories').then((r) => r.categories);

// imageUrl/originalPrice/discountPercent added 2026-07-21 -- see commerce.ts's
// CommerceProduct comment for the full account; search results carry the same real
// product-card fields the per-merchant catalog already exposes.
export interface ProductSearchResult {
  id: string;
  merchantId: string;
  merchantName: string;
  name: string;
  price: number;
  imageUrl?: string | null;
  originalPrice?: number | null;
  discountPercent?: number | null;
  description?: string | null;
  stockQuantity?: number | null;
}

// Real cross-merchant product search (2026-07-20) -- until now a shopper could only
// search MERCHANT names then browse one seller's catalog at a time; there was no way
// to search for a product across every real seller at once, the most basic real
// feature Coupang/Naver/Toss Shopping all have. See MerchantProductRepository.search's
// own doc comment on the backend for the real query this hits.
export const searchProducts = (q: string) =>
  apiFetch<{ success: boolean; products: ProductSearchResult[] }>(`/api/v1/shopping/products/search?q=${encodeURIComponent(q)}`).then((r) => r.products);

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment. Reuses ProductSearchResult's
// exact shape (same fields, same source table) rather than a duplicate type.
export const fetchShopDeals = () =>
  apiFetch<{ success: boolean; products: ProductSearchResult[] }>('/api/v1/shopping/products/deals').then((r) => r.products);

// Real 마감할인 (closing/surplus discount) rail (2026-08-15) -- a real,
// government-partnered (기후부/환경부 + Baemin/Yogiyo/Coupang Eats) food-waste-reduction
// feature that launched 2026-06-15, sourced fresh, not doc-mined. Distinct from
// fetchShopDeals above: only genuinely time-boxed, still-in-stock closing sales,
// soonest-to-expire first -- see MerchantProductRepository.findSurplusDeals' own doc
// comment on the backend.
export interface SurplusDealResult extends ProductSearchResult {
  surplusExpiresAt: string;
}

export const fetchSurplusDeals = () =>
  apiFetch<{ success: boolean; products: SurplusDealResult[] }>('/api/v1/shopping/products/surplus-deals').then((r) => r.products);

export interface CollectPaymentResult {
  transactionId: string;
  merchantName: string;
  amount: number;
  fee: number;
  status: string;
  channel: string;
  completedAt: string;
  cashbackEarned: number;
}

// Real checkout, closing the "browse-only" gap this tab previously had -- calls the
// exact same real POST /api/v1/merchant/collect/{intentId} MerchantService.collect
// already proved out for QR Pay (see that method's own doc comment), which is also
// where real Toss Shopping cashback gets awarded. A payment code here is the same real
// PaymentIntent id a merchant's real QR encodes. Real camera QR scanning shipped into
// `PayByCodeCard` 2026-08-19 (this comment used to say the opposite -- "this app has
// no camera-based QR scanner" -- gone stale the moment that landed); manual entry is
// now the honest fallback alongside it, not the only path.
export const collectPayment = (intentId: string, couponId?: string, pointsToRedeem?: number) =>
  apiFetch<{ success: boolean } & CollectPaymentResult>(`/api/v1/merchant/collect/${intentId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: couponId || pointsToRedeem ? JSON.stringify({ couponId, pointsToRedeem }) : undefined,
  });

// Real Toss Place-style 자동 적립 balance check -- see backend
// MerchantController.getLoyaltyBalance's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: real automatic point accrual already happens on
// every collectPayment call (MerchantLoyaltyPointsService.getBalance is called from
// MerchantService.collect), but there was no way for a customer to ever SEE their real
// balance, let alone redeem it, before this.
export const fetchLoyaltyBalance = (merchantId: string) =>
  apiFetch<{ success: boolean; pointBalance: number }>(`/api/v1/merchant/${merchantId}/loyalty-balance`).then((r) => r.pointBalance);

// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see the backend's
// MerchantStaticQrService doc comment. Genuinely distinct from collectPayment above:
// that pays a merchant-preset amount (a fresh PaymentIntent code per sale); this pays a
// merchant's own permanent merchantId, with the CUSTOMER choosing the amount -- the same
// honest manual-entry alternative to camera scanning this file's own collectPayment
// doc comment already establishes, just for the merchant's static code instead of a
// per-sale one.
export const payByStaticQr = (merchantId: string, amount: number, description?: string) =>
  apiFetch<{ success: boolean } & CollectPaymentResult>(`/api/v1/merchant/${merchantId}/static-qr/pay`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount, description }),
  });

// Real customer-presented payment code (Pay-parity port, §238) -- see backend
// MerchantService.generateCustomerPaymentCode's own doc comment. Android shipped
// this real KakaoPay/Toss Pay "My code" reveal-QR flow 2026-08-11; bank-mfe never
// received it (confirmed via a real grep sweep before starting -- zero
// generateCustomerPaymentCode references anywhere in this workspace). Same real
// short-lived (2-min), single-use, opaque code a merchant scans and charges via
// chargeByCustomerCode -- the QR must encode the RAW `code` value, no
// `itunda://...` URL wrapping, matching exactly what Android's real merchant-side
// scanner (`CameraQrScanner`'s `onScanned` callback) passes straight through as
// the code with no param extraction.
export interface CustomerPaymentCode {
  code: string;
  expiresAt: string;
  accountId: string | null;
}

export const generateCustomerPaymentCode = (accountId?: string) =>
  apiFetch<{ success: boolean } & CustomerPaymentCode>('/api/v1/merchant/pay/customer-code', {
    method: 'POST',
    body: JSON.stringify({ accountId }),
  });

// Real read-only preview (item 149) -- see backend MerchantService.previewIntent's own
// doc comment. Lets a payer see which merchant/amount a code resolves to, and their own
// real coupon eligibility, before committing to collectPayment -- the actual blocker
// that made the coupon-apply half of MerchantCouponController (item 146) unbuildable
// until this existed, since Pay-by-code previously had zero merchant context pre-charge.
export interface MerchantCouponView {
  coupon: {
    id: string;
    merchantId: string;
    title: string;
    description: string | null;
    discountType: 'PERCENT' | 'FIXED_AMOUNT';
    discountValue: number;
    regularsOnly: boolean;
    active: boolean;
    expiresAt: string | null;
    createdAt: string;
  };
  eligible: boolean;
  alreadyRedeemed: boolean;
}

export interface PaymentIntentPreview {
  merchantId: string;
  businessName: string;
  amount: number;
  description: string;
  coupons: MerchantCouponView[];
}

export const previewPaymentIntent = (intentId: string) =>
  apiFetch<{ success: boolean } & PaymentIntentPreview>(`/api/v1/merchant/intent/${intentId}`);

// Real Naver Smart Store-style "알림받기" (follow a store for its own broadcast
// notices) -- see MerchantFollowService.kt's own doc comment. Distinct from the
// wishlist heart above: following opts a customer into the merchant's own promotional
// messages, not just a private bookmark.
export interface FollowedMerchant {
  merchantId: string;
  businessName: string;
  category: string | null;
  followedAt: string;
}

export const fetchMyFollowedMerchants = () =>
  apiFetch<{ success: boolean; follows: FollowedMerchant[] }>('/api/v1/merchant/follows?size=200').then((r) => r.follows);

export const followMerchant = (merchantId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/merchant/${merchantId}/follow`, { method: 'POST' });

export const unfollowMerchant = (merchantId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/merchant/${merchantId}/follow`, { method: 'DELETE' });

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing (item 144
// built the merchant-owner plan-management side on merchant-mfe) -- this is the
// customer-facing half: browse a merchant's own active plans and subscribe. Subscribing
// charges the first cycle immediately (real "인증 + 첫결제"), then itunda charges the
// same account automatically every intervalDays with zero further approval, distinct
// from SubscriptionsView's own "detected from payment history" read-only insight above.
export interface MerchantBillingPlan {
  id: string;
  merchantId: string;
  name: string;
  description: string | null;
  amount: number;
  intervalDays: number;
  active: boolean;
  createdAt: string;
}

export type MerchantBillingSubscriptionStatus = 'ACTIVE' | 'CANCELLED';

export interface MerchantBillingSubscription {
  id: string;
  planId: string;
  merchantId: string;
  customerId: string;
  status: MerchantBillingSubscriptionStatus;
  nextChargeAt: string;
  lastChargedAt: string | null;
  chargeCount: number;
  lastFailureReason: string | null;
  createdAt: string;
  cancelledAt: string | null;
}

export const fetchMerchantBillingPlans = (merchantId: string) =>
  apiFetch<{ success: boolean; plans: MerchantBillingPlan[] }>(`/api/v1/merchant/${merchantId}/billing-plans`).then((r) => r.plans);

export const subscribeToBillingPlan = (planId: string) =>
  apiFetch<{ success: boolean; subscription: MerchantBillingSubscription }>(`/api/v1/merchant/billing-plans/${planId}/subscribe`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.subscription);

export const fetchMyBillingSubscriptions = () =>
  apiFetch<{ success: boolean; subscriptions: MerchantBillingSubscription[] }>('/api/v1/merchant/billing-subscriptions/my').then((r) => r.subscriptions);

export const cancelBillingSubscription = (subscriptionId: string) =>
  apiFetch<{ success: boolean; subscription: MerchantBillingSubscription }>(`/api/v1/merchant/billing-subscriptions/${subscriptionId}/cancel`, {
    method: 'POST',
  }).then((r) => r.subscription);

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 148 -- the
// customer-facing browse half; merchant-mfe's item 147 built the paid create/extend
// side). "Pull" discovery, same as every other nearby() in this codebase: the caller's
// live coordinate is a request param, not a stored location itunda doesn't keep.
export interface NearbyMerchantAd {
  ad: {
    id: string;
    merchantId: string;
    title: string;
    description: string | null;
    radiusMeters: number;
    activeUntil: string;
  };
  businessName: string;
  distanceKm: number;
}

export const fetchNearbyAds = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; ads: NearbyMerchantAd[] }>(`/api/v1/merchant/ads/nearby?latitude=${latitude}&longitude=${longitude}`).then((r) => r.ads);

// Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up: "it
// should look 100% like toss pay UI/UX features everything") -- the reference's own
// "345 stores nearby where you can earn rewards" banner. Distinct from fetchNearbyAds
// above -- that's paid ad placements (a subset), this is every real ACTIVE merchant
// nearby (MerchantDiscoveryService.kt), mirrors Android's identical getNearbyMerchants
// exactly.
export interface NearbyMerchant {
  id: string;
  businessName: string;
  category: string | null;
  cashbackRate: number;
  latitude: number;
  longitude: number;
  distanceKm: number;
}

export const fetchNearbyMerchants = (latitude: number, longitude: number, radiusKm = 5) =>
  apiFetch<{ success: boolean; merchants: NearbyMerchant[] }>(`/api/v1/merchant/nearby?latitude=${latitude}&longitude=${longitude}&radiusKm=${radiusKm}`).then((r) => r.merchants);
