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
}

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

export const addProduct = (name: string, price: number) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>('/api/v1/merchant/products', {
    method: 'POST',
    body: JSON.stringify({ name, price }),
  }).then((r) => r.product);

export const getProductCatalog = () =>
  apiFetch<{ success: boolean; products: MerchantProduct[] }>('/api/v1/merchant/products').then((r) => r.products);

export const updateProduct = (productId: string, name: string, price: number) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}`, {
    method: 'PUT',
    body: JSON.stringify({ name, price }),
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
