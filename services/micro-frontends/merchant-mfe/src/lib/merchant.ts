import { apiFetch, ApiError } from './api';
import { randomUUID } from './uuid';

// Field shapes match services/backend's real domain entities exactly (Merchant,
// PaymentIntent) -- see MerchantController.kt, the source of truth this talks to.

export interface Merchant {
  id: string;
  ownerUserId: string;
  businessName: string;
  webhookUrl: string | null;
  kybVerified: boolean;
  createdAt: string;
  category: string | null;
  // Real Baemin Club-style participating-restaurant opt-in (2026-07-26) -- see
  // EatsMembership.kt's own doc comment. Free delivery for Eats Club members only
  // applies at a restaurant that has itself opted in here.
  participatesInEatsMembership: boolean;
  // Real business location (POST /api/v1/merchant/location) -- backend has had this
  // since 2026-07-18 for Eats/delivery matching, but merchant-mfe never surfaced it
  // until MerchantAdService's own radius-targeted ads required it (item 147).
  latitude: number | null;
  longitude: number | null;
}

export const setMerchantLocation = (latitude: number, longitude: number) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/location', {
    method: 'POST',
    body: JSON.stringify({ latitude, longitude }),
  }).then((r) => r.merchant);

// Real demo KYB structural pre-check (see DemoKybVerificationService.kt's own doc
// comment) -- reuses the same /api/v1/identity/submit + human-review pipeline personal
// KYC already uses, with documentType = "BUSINESS_TIN" instead of "NATIONAL_ID".
export interface IdentitySubmission {
  id: string;
  userId: string;
  documentType: string;
  documentNumber: string;
  documentReference: string;
  status: 'PENDING' | 'VERIFIED' | 'REJECTED';
  submittedAt: string;
  autoVerificationStatus: 'MATCHED' | 'NOT_FOUND' | 'INVALID_FORMAT' | null;
  autoVerificationDetail: string | null;
}

// Real merchant product catalog -- the register-software half of "Toss Place" (see
// MerchantProductService.kt's own doc comment).
export interface MerchantProduct {
  id: string;
  merchantId: string;
  name: string;
  price: number;
  active: boolean;
  createdAt: string;
  imageUrl: string | null;
  originalPrice: number | null;
  discountPercent: number | null;
  description: string | null;
  durationMinutes: number | null;
  requiresPrepay: boolean;
  stockQuantity: number | null;
}

export interface PaymentIntent {
  id: string;
  merchantId: string;
  amount: number;
  description: string;
  status: 'PENDING' | 'COMPLETED' | 'EXPIRED';
  expiresAt: string;
  createdAt: string;
}

export interface ReportDay {
  date: string;
  collectionCount: number;
  grossAmount: number;
  fees: number;
  netAmount: number;
  byChannel: Record<string, number>;
}

export interface CardChargeResult {
  transactionId: string;
  merchantName: string;
  amount: number;
  fee: number;
  status: string;
  channel: string;
  cardLast4: string;
  completedAt: string;
}

// Real B2B payroll -- see PayrollService.kt's own doc comment for why this is real
// wallet-to-wallet money movement, not a demo/simulation (unlike card processing).
export interface PayrollEmployee {
  id: string;
  merchantId: string;
  employeeUserId: string;
  employeeName: string;
  salaryAmount: number;
  active: boolean;
  createdAt: string;
}

export interface PayrollRun {
  id: string;
  merchantId: string;
  ledgerTransactionId: string;
  totalAmount: number;
  employeeCount: number;
  createdAt: string;
}

export interface Payslip {
  id: string;
  payrollRunId: string;
  employeeUserId: string;
  employeeName: string;
  amount: number;
  transactionId: string;
  createdAt: string;
}

export interface PayrollRunResult {
  payrollRunId: string;
  totalAmount: number;
  employeeCount: number;
  completedAt: string;
  payslips: { employeeName: string; amount: number; transactionId: string }[];
}

// GET /api/v1/merchant/me real-404s (MERCHANT_NOT_FOUND) when the caller hasn't
// registered yet -- that's an expected, common state here (any itunda user can open
// this app before ever becoming a merchant), not an error condition, so it's translated
// to a real `null` rather than propagating the ApiError to every caller.
export const getMyMerchant = async (): Promise<Merchant | null> => {
  try {
    const r = await apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/me');
    return r.merchant;
  } catch (err) {
    if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') return null;
    throw err;
  }
};

export const registerMerchant = (businessName: string) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/register', {
    method: 'POST',
    body: JSON.stringify({ businessName }),
  }).then((r) => r.merchant);

export const generateQr = (amount: number, description: string) =>
  apiFetch<{ success: boolean; paymentIntent: PaymentIntent }>('/api/v1/merchant/qr/generate', {
    method: 'POST',
    body: JSON.stringify({ amount, description }),
  }).then((r) => r.paymentIntent);

export const setWebhookUrl = (webhookUrl: string) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/webhook-url', {
    method: 'POST',
    body: JSON.stringify({ webhookUrl }),
  }).then((r) => r.merchant);

// Real category/cuisine (2026-07-19) -- powers restaurant categories + search/filter on
// the buyer side (bank-mfe's Eats tab). See MerchantController.setCategory's own doc
// comment on the backend.
export const setCategory = (category: string) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/category', {
    method: 'POST',
    body: JSON.stringify({ category }),
  }).then((r) => r.merchant);

// Real Baemin Club-style participating-restaurant opt-in (2026-07-26) -- first client
// UI for this endpoint (item 103). See MerchantController.setParticipatesInEatsMembership's
// own doc comment on the backend.
export const setParticipatesInEatsMembership = (participates: boolean) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/eats-membership-participation', {
    method: 'POST',
    body: JSON.stringify({ participates }),
  }).then((r) => r.merchant);

// Real Naver Smart Store-style "관심고객" (interested-customer) follower count +
// broadcast-to-followers -- see MerchantFollowService.kt's own doc comment. Distinct
// from the customer-facing follow/unfollow already ported to bank-mfe/Android/iOS
// (item 117): this is the merchant-owner-facing half of the same feature, first client
// anywhere for it (item 118).
export const fetchFollowerCount = () =>
  apiFetch<{ success: boolean; count: number }>('/api/v1/merchant/followers/count').then((r) => r.count);

export const broadcastToFollowers = (title: string, body: string) =>
  apiFetch<{ success: boolean; recipientCount: number }>('/api/v1/merchant/followers/broadcast', {
    method: 'POST',
    body: JSON.stringify({ title, body }),
  }).then((r) => r.recipientCount);

// Real demo card-processing flow -- see MerchantService.chargeCard's own doc comment
// on the backend for why this is a real Luhn-validated + simulated authorization, not
// a real PSP integration. Idempotency-Key required, same convention as every other
// money-moving endpoint this app calls.
export const chargeCard = (
  amount: number, description: string, cardNumber: string, expiryMonth: number, expiryYear: number, cvc: string,
) =>
  apiFetch<{ success: boolean } & CardChargeResult>('/api/v1/merchant/card/charge', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount, description, cardNumber, expiryMonth, expiryYear, cvc }),
  });

export const addProduct = (
  name: string, price: number, imageUrl?: string, originalPrice?: number, description?: string, stockQuantity?: number,
) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>('/api/v1/merchant/products', {
    method: 'POST',
    body: JSON.stringify({ name, price, imageUrl, originalPrice, description, stockQuantity }),
  }).then((r) => r.product);

export const getProductCatalog = () =>
  apiFetch<{ success: boolean; products: MerchantProduct[] }>('/api/v1/merchant/products').then((r) => r.products);

export const updateProduct = (productId: string, name: string, price: number) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}`, {
    method: 'PUT',
    body: JSON.stringify({ name, price }),
  }).then((r) => r.product);

export const updateProductStock = (productId: string, stockQuantity: number | null) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}/stock`, {
    method: 'PATCH',
    body: JSON.stringify({ stockQuantity }),
  }).then((r) => r.product);

export const removeProduct = (productId: string) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}`, {
    method: 'DELETE',
  }).then((r) => r.product);

// Real menu-item option groups (2026-07-21, v1: required single-select only) -- the
// merchant-facing half of a gap the buyer side (bank-mfe's "Choose options" panel) has
// had since the same day: this backend endpoint (MerchantProductController.kt's own
// /{productId}/option-groups) previously had no UI anywhere, so a merchant could only
// create/view/delete an option group via a direct API call. See MenuOptionGroup.kt's
// own doc comment on the backend for the full account.
export interface MenuOptionChoice {
  id: string;
  name: string;
  priceDelta: number;
}
export interface MenuOptionGroup {
  id: string;
  name: string;
  choices: MenuOptionChoice[];
}

export const getOptionGroups = (productId: string) =>
  apiFetch<{ success: boolean; optionGroups: MenuOptionGroup[] }>(`/api/v1/merchant/products/${productId}/option-groups`).then(
    (r) => r.optionGroups,
  );

export const addOptionGroup = (productId: string, name: string, choices: { name: string; priceDelta: number }[]) =>
  apiFetch<{ success: boolean; optionGroup: MenuOptionGroup }>(`/api/v1/merchant/products/${productId}/option-groups`, {
    method: 'POST',
    body: JSON.stringify({ name, choices }),
  }).then((r) => r.optionGroup);

export const removeOptionGroup = (productId: string, groupId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/merchant/products/${productId}/option-groups/${groupId}`, {
    method: 'DELETE',
  });

export const getReport = (from?: string, to?: string) => {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const qs = params.toString();
  return apiFetch<{ success: boolean; from: string; to: string; days: ReportDay[] }>(
    `/api/v1/merchant/reports${qs ? `?${qs}` : ''}`,
  );
};

export const submitKyb = (tin: string) =>
  apiFetch<{ success: boolean; submission: IdentitySubmission }>('/api/v1/identity/submit', {
    method: 'POST',
    body: JSON.stringify({ documentType: 'BUSINESS_TIN', documentNumber: tin, documentReference: `TIN ${tin}` }),
  }).then((r) => r.submission);

export const getMyIdentitySubmissions = () =>
  apiFetch<{ success: boolean; submissions: IdentitySubmission[] }>('/api/v1/identity/status').then((r) => r.submissions);

export const addPayrollEmployee = (phoneNumber: string, salaryAmount: number) =>
  apiFetch<{ success: boolean; employee: PayrollEmployee }>('/api/v1/merchant/payroll/employees', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber, salaryAmount }),
  }).then((r) => r.employee);

export const getPayrollRoster = () =>
  apiFetch<{ success: boolean; employees: PayrollEmployee[] }>('/api/v1/merchant/payroll/employees').then((r) => r.employees);

export const removePayrollEmployee = (employeeId: string) =>
  apiFetch<{ success: boolean; employee: PayrollEmployee }>(`/api/v1/merchant/payroll/employees/${employeeId}`, {
    method: 'DELETE',
  }).then((r) => r.employee);

// Money-moving -- real Idempotency-Key convention, same as chargeCard/collect above.
export const runPayroll = () =>
  apiFetch<{ success: boolean } & PayrollRunResult>('/api/v1/merchant/payroll/run', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  });

export const getPayrollHistory = () =>
  apiFetch<{ success: boolean; runs: PayrollRun[] }>('/api/v1/merchant/payroll/runs').then((r) => r.runs);

export const getPayslips = (payrollRunId: string) =>
  apiFetch<{ success: boolean; payslips: Payslip[] }>(`/api/v1/merchant/payroll/runs/${payrollRunId}/payslips`).then((r) => r.payslips);

// The backend deliberately returns a reference/code, not a QR image (see
// PaymentIntent.kt's own doc comment) -- this is the client-side encoding convention:
// itunda's real custom URL scheme (itunda://saronite/... is already used for mini-apps,
// see docs/ARCHITECTURE.md's mini-app host row), extended with a pay path a customer's
// app would resolve into a POST /api/v1/merchant/collect/{intentId} call.
export const paymentIntentQrPayload = (intentId: string) => `itunda://pay?intentId=${intentId}`;

// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see the backend's
// MerchantStaticQrService doc comment. Same client-side encoding convention as
// paymentIntentQrPayload above, keyed on the merchant's own permanent id instead of a
// fresh per-sale intentId -- this one QR never needs regenerating.
export const staticQrPayload = (merchantId: string) => `itunda://pay-static?merchantId=${merchantId}`;

// Real post-appointment booking reviews + owner-side reply (item 143) -- see backend
// MerchantBookingReview.kt's own doc comment: closes the "owner-side review replies"
// half of Naver Smart Place's own real, sourced feature. Found via a fresh discovery
// sweep checking native RiderApp/MerchantApp's OWN endpoint coverage -- turned out the
// ENTIRE review lifecycle (submit/list/reply) had zero client on any platform,
// including this one. Scoped to the merchant-owner-facing list+reply half only; the
// customer-facing submit-a-review half needs a real booking flow to hang off of,
// which only Android currently has (a real, separate, larger follow-up).
export interface MerchantBookingReview {
  id: string;
  bookingId: string;
  merchantId: string;
  customerId: string;
  serviceName: string;
  rating: number;
  comment: string | null;
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
}
export interface MerchantReviewRating { average: number | null; count: number }

export const fetchMerchantReviews = (merchantId: string) =>
  apiFetch<{ success: boolean; reviews: MerchantBookingReview[]; rating: MerchantReviewRating }>(
    `/api/v1/merchant/${merchantId}/reviews?size=50`,
  );

export const replyToBookingReview = (reviewId: string, reply: string) =>
  apiFetch<{ success: boolean; review: MerchantBookingReview }>(`/api/v1/merchant/reviews/${reviewId}/reply`, {
    method: 'POST',
    body: JSON.stringify({ reply }),
  }).then((r) => r.review);

// Real Commerce product reviews + owner-side reply (item 187) -- ProductReviewService.
// replyToProductReview has been real since 2026-07-26 (the exact same pattern this
// file's own MerchantBookingReview/EatsReview siblings already establish), but had
// zero client anywhere: no backend endpoint aggregates "all reviews across my own
// products" in one call, so this fetches the merchant's own catalog (getProductCatalog,
// already real) and fans out one real GET /products/{id}/reviews per product --
// honest for this app's real catalog scale, matching Naver Smart Store's own per-item
// review management rather than inventing a new aggregate endpoint for this pass.
export interface ProductReview {
  id: string;
  orderItemId: string;
  orderId: string;
  buyerId: string;
  productId: string;
  merchantId: string;
  rating: number;
  comment: string | null;
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
}

export const fetchProductReviews = (productId: string) =>
  apiFetch<{ success: boolean; reviews: ProductReview[] }>(`/api/v1/orders/products/${productId}/reviews?size=50`).then((r) => r.reviews);

export const replyToProductReview = (reviewId: string, reply: string) =>
  apiFetch<{ success: boolean; review: ProductReview }>(`/api/v1/orders/reviews/${reviewId}/reply`, {
    method: 'POST',
    body: JSON.stringify({ reply }),
  }).then((r) => r.review);

// Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing
// (item 144) -- see backend MerchantBillingPlan.kt's own doc comment. A merchant
// defines a real recurring charge once; a customer authorizes it once and itunda
// charges their wallet automatically every intervalDays. Found via the same fresh
// discovery sweep as item 143 -- the backend is real, tested (11/11
// MerchantBillingServiceTest, push-wired), but had zero client anywhere. Scoped to
// the merchant-owner-facing plan-management half here; browsing/subscribing as a
// customer belongs on bank-mfe, a real, separate follow-up.
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

export const createBillingPlan = (name: string, description: string | undefined, amount: number, intervalDays: number) =>
  apiFetch<{ success: boolean; plan: MerchantBillingPlan }>('/api/v1/merchant/billing-plans', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ name, description, amount, intervalDays }),
  }).then((r) => r.plan);

export const fetchMyBillingPlans = () =>
  apiFetch<{ success: boolean; plans: MerchantBillingPlan[] }>('/api/v1/merchant/billing-plans').then((r) => r.plans);

export const deactivateBillingPlan = (planId: string) =>
  apiFetch<{ success: boolean; plan: MerchantBillingPlan }>(`/api/v1/merchant/billing-plans/${planId}/deactivate`, {
    method: 'POST',
  }).then((r) => r.plan);

// Real KakaoTalk-style 기프티콘 (mobile gift voucher) merchant-side redemption
// (item 139) -- see backend GiftVoucherService.redeemVoucher's own doc comment: the
// customer presents the voucher in person, the merchant's own authenticated account
// is what actually redeems it, never a self-serve redeem the customer could fake.
// Real Idempotency-Key convention, same as chargeCard/collect above.
export interface RedeemedGiftVoucher {
  id: string;
  productNameSnapshot: string | null;
  amount: number;
  status: 'ACTIVE' | 'REDEEMED' | 'EXPIRED';
  redeemedAt: string | null;
}

export const redeemGiftVoucher = (voucherId: string) =>
  apiFetch<{ success: boolean; voucher: RedeemedGiftVoucher }>(`/api/v1/gift-vouchers/${voucherId}/redeem`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.voucher);

// Real merchant coupons + 단골 (regular customer) loyalty gating (item 146) -- see
// MerchantCoupon.kt's own doc comment: a coupon applies to a real QR/Pay-by-code
// payment (MerchantService.collect), not the cart-based commerce checkout, so it isn't
// money-moving itself here (no Idempotency-Key, same as MerchantBookingController's own
// non-money-moving writes) -- actual redemption happens inside collect, which already
// requires one. Merchant-owner-facing create/list/deactivate only; the customer-facing
// browse+redeem-at-payment half is a real, separate follow-up.
export type CouponDiscountType = 'PERCENT' | 'FIXED_AMOUNT';

export interface MerchantCoupon {
  id: string;
  merchantId: string;
  title: string;
  description: string | null;
  discountType: CouponDiscountType;
  discountValue: number;
  regularsOnly: boolean;
  active: boolean;
  expiresAt: string | null;
  createdAt: string;
}

export const createCoupon = (
  title: string,
  description: string | undefined,
  discountType: CouponDiscountType,
  discountValue: number,
  regularsOnly: boolean,
  expiresAt: string | undefined,
) =>
  apiFetch<{ success: boolean; coupon: MerchantCoupon }>('/api/v1/merchant/coupons', {
    method: 'POST',
    body: JSON.stringify({ title, description, discountType, discountValue, regularsOnly, expiresAt }),
  }).then((r) => r.coupon);

export const fetchMyCoupons = () =>
  apiFetch<{ success: boolean; coupons: MerchantCoupon[] }>('/api/v1/merchant/coupons').then((r) => r.coupons);

export const deactivateCoupon = (couponId: string) =>
  apiFetch<{ success: boolean; coupon: MerchantCoupon }>(`/api/v1/merchant/coupons/${couponId}/deactivate`, {
    method: 'POST',
  }).then((r) => r.coupon);

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads (item 147) -- see
// MerchantAd.kt's own doc comment for the sourced radius range and flat-fee tiers.
// One real ad slot per merchant; paying again extends activeUntil rather than losing
// remaining paid time. IS a real payment (fee_revenue), so Idempotency-Key-gated.
export const AD_VALID_RADII_METERS = Array.from({ length: 13 }, (_, i) => 300 + i * 100); // 300..1500
export const AD_DURATION_TIERS: { days: number; price: number }[] = [
  { days: 3, price: 1500 },
  { days: 7, price: 3000 },
  { days: 14, price: 5500 },
];

export interface MerchantAd {
  id: string;
  merchantId: string;
  title: string;
  description: string | null;
  radiusMeters: number;
  activeUntil: string;
  createdAt: string;
  updatedAt: string;
}

export const createOrExtendAd = (title: string, description: string | undefined, radiusMeters: number, days: number) =>
  apiFetch<{ success: boolean; ad: MerchantAd }>('/api/v1/merchant/ads', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ title, description, radiusMeters, days }),
  }).then((r) => r.ad);

export const fetchMyAd = () =>
  apiFetch<{ success: boolean; ad: MerchantAd | null }>('/api/v1/merchant/ads/me').then((r) => r.ad);

// Real 토스뱅크 개인사업자 (business banking for sole proprietors) equivalent (item 150)
// -- see MerchantBusinessAccountService.kt's own doc comment: every real card/QR
// collection settles to the merchant's PERSONAL wallet, so this is a dedicated,
// additive `WalletType.BUSINESS` wallet a merchant deliberately sweeps money into/out
// of, never touching the already-tested collect()/chargeCard settlement path. Move
// actions are real money movement (same person, both sides), so Idempotency-Key-gated
// same as every other money-moving write. Android's native merchantapp already has
// this; this closes the gap on merchant-mfe (web) and iOS MerchantApp.
export interface BusinessWallet {
  id: string;
  userId: string;
  accountNumber: string;
  accountName: string;
  type: 'MAIN' | 'BUSINESS';
  balance: number;
  availableBalance: number;
  currency: string;
}

export interface BusinessLedgerEntry {
  id: string;
  transactionId: string;
  accountId: string;
  direction: 'DEBIT' | 'CREDIT';
  amount: number;
  currency: string;
  balanceAfter: number;
  memo: string;
  createdAt: string;
}

export const openBusinessAccount = () =>
  apiFetch<{ success: boolean; wallet: BusinessWallet }>('/api/v1/merchant/business-account', { method: 'POST' }).then((r) => r.wallet);

export const getBusinessAccount = () =>
  apiFetch<{ success: boolean; wallet: BusinessWallet }>('/api/v1/merchant/business-account').then((r) => r.wallet);

export const fetchBusinessTransactions = () =>
  apiFetch<{ success: boolean; transactions: BusinessLedgerEntry[] }>('/api/v1/merchant/business-account/transactions').then((r) => r.transactions);

export const moveToBusinessAccount = (amount: number) =>
  apiFetch<{ success: boolean; wallet: BusinessWallet }>('/api/v1/merchant/business-account/move-to-business', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.wallet);

export const moveToPersonalAccount = (amount: number) =>
  apiFetch<{ success: boolean; wallet: BusinessWallet }>('/api/v1/merchant/business-account/move-to-personal', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ amount }),
  }).then((r) => r.wallet);
