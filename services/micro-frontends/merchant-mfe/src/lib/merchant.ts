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
  // Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
  // see the backend's Merchant.kt doc comment. Null means the standard platform fee
  // rate applies unchanged; 0 means a real active full waiver.
  feeRateOverride: number | null;
  // Real restaurant-card photo (2026-07-21) -- see MerchantService.setPhotoUrl's own
  // doc comment. Found 2026-08-01 with zero client anywhere despite being real since
  // ship day -- see setPhotoUrl/setMinOrderAmount/setCashbackRate/
  // setAcceptsScheduledOrders below, all discovered the same way (dead-field sweep).
  photoUrl: string | null;
  // Real merchant-set minimum order amount (2026-07-21) -- null means no minimum.
  minOrderAmount: number | null;
  // Real Naver Pay-style boosted cashback opt-in (2026-07-26) -- a fraction 0-0.05
  // (0-5%), see ShoppingCashbackService.MAX_CASHBACK_RATE on the backend. Null means
  // the merchant hasn't opted into a boosted rate.
  cashbackRate: number | null;
  // Real 배달의민족 예약주문 (scheduled ordering) opt-in (2026-07-26) -- a restaurant
  // explicitly opts into accepting buyer-scheduled future delivery/pickup times.
  acceptsScheduledOrders: boolean;
  // Real Baemin CEO app 영업일시중지 (temporarily pause business) (2026-08-16) -- see
  // Merchant.isAcceptingOrders's own doc comment. Defaults true.
  isAcceptingOrders: boolean;
  // Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule)
  // (2026-08-16) -- see backend Merchant.closedWeekdays's own doc comment.
  // Comma-separated 1=Monday..7=Sunday; null means no recurring closed days.
  closedWeekdays: string | null;
  // Real phone number + opening hours (2026-08-09) -- see Merchant.kt's own doc
  // comment on the backend. Both plain merchant-set free text; null means unset.
  phoneNumber: string | null;
  openingHours: string | null;
  // Real per-merchant kitchen-prep time (2026-08-16) -- see Merchant.kt's own doc
  // comment on the backend (Baemin's real "가게배달 배달시간 AI 예측"). Null means the
  // real customer-facing delivery estimate falls back to itunda's own flat default.
  avgPrepTimeMinutes: number | null;
  // Real Baemin 포장할인 (pickup discount) -- see MerchantService.setPickupDiscount's
  // own doc comment on the backend. Found via scripts/uncalled-endpoint-sweep.py:
  // EatsOrderService already applies this real discount when a customer orders for
  // pickup, but no merchant-facing client ever let a merchant actually set the rate.
  // Null means no pickup discount.
  pickupDiscountPercent: number | null;
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
  isSurplusDeal?: boolean;
  surplusExpiresAt?: string | null;
  // Real Baemin CEO app/DoorDash-style "86" (temporarily sold out) toggle (2026-08-16)
  // -- see backend MerchantProduct.soldOut's own doc comment. Distinct from `active`
  // (that's this backend's own soft-delete).
  soldOut: boolean;
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

export interface TopSellingProduct {
  productId: string;
  productName: string;
  unitsSold: number;
  revenue: number;
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

// Real API key + webhook delivery log/replay -- found via a fresh "defined but
// uncalled" endpoint sweep: real, working since ship day, zero client anywhere. The
// natural companion to the webhook-URL setting above: a merchant integrating
// programmatically needs a key to authenticate with, and a way to see whether their
// endpoint is actually receiving delivery attempts. See WebhookDeliveryService.kt's
// own doc comment for the real Toss Payments-sourced 7-attempt/4096-minute retry
// schedule this delivery log reflects.
export const generateApiKey = () =>
  apiFetch<{ success: boolean; apiKey: string }>('/api/v1/merchant/api-key/generate', { method: 'POST' }).then((r) => r.apiKey);

export type WebhookDeliveryStatus = 'PENDING' | 'DELIVERED' | 'EXHAUSTED';

export interface WebhookDelivery {
  id: string;
  eventType: string;
  status: WebhookDeliveryStatus;
  attemptCount: number;
  createdAt: string;
  nextAttemptAt: string;
  deliveredAt: string | null;
  lastError: string | null;
}

export const getWebhookDeliveries = () =>
  apiFetch<{ success: boolean; deliveries: WebhookDelivery[] }>('/api/v1/merchant/webhook-deliveries').then((r) => r.deliveries);

export const replayWebhookDelivery = (deliveryId: string) =>
  apiFetch<{ success: boolean; delivery: { id: string; status: string; replayOf: string } }>(
    `/api/v1/merchant/webhook-deliveries/${deliveryId}/replay`,
    { method: 'POST' },
  );

// Real Naver Pay 영세 가맹점 수수료 지원 (small-merchant fee-waiver support program) --
// see the backend's MerchantFeeWaiverService doc comment.
export const applyForFeeWaiver = () =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/fee-waiver/apply', {
    method: 'POST',
  }).then((r) => r.merchant);

// Real category/cuisine (2026-07-19) -- powers restaurant categories + search/filter on
// the buyer side (bank-mfe's Eats tab). See MerchantController.setCategory's own doc
// comment on the backend.
export const setCategory = (category: string) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/category', {
    method: 'POST',
    body: JSON.stringify({ category }),
  }).then((r) => r.merchant);

// Real restaurant-card photo -- see MerchantService.setPhotoUrl's own doc comment.
export const setMerchantPhotoUrl = (photoUrl: string) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/photo', {
    method: 'POST',
    body: JSON.stringify({ photoUrl }),
  }).then((r) => r.merchant);

// Real merchant-set minimum order amount -- null clears it (no minimum).
export const setMinOrderAmount = (minOrderAmount: number | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/min-order', {
    method: 'POST',
    body: JSON.stringify({ minOrderAmount }),
  }).then((r) => r.merchant);

// Real merchant-set phone number + opening hours (2026-08-09) -- see
// MerchantService.setPhoneNumber/setOpeningHours's own doc comments on the backend.
export const setMerchantPhoneNumber = (phoneNumber: string | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/phone', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  }).then((r) => r.merchant);

export const setMerchantOpeningHours = (openingHours: string | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/hours', {
    method: 'POST',
    body: JSON.stringify({ openingHours }),
  }).then((r) => r.merchant);

// Real per-merchant kitchen-prep time (2026-08-16) -- see
// MerchantService.setAvgPrepTimeMinutes's own doc comment on the backend.
export const setMerchantAvgPrepTimeMinutes = (avgPrepTimeMinutes: number | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/prep-time', {
    method: 'POST',
    body: JSON.stringify({ avgPrepTimeMinutes }),
  }).then((r) => r.merchant);

// Real Baemin 포장할인 (pickup discount) -- see Merchant.pickupDiscountPercent's own
// doc comment above.
export const setMerchantPickupDiscount = (pickupDiscountPercent: number | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/pickup-discount', {
    method: 'POST',
    body: JSON.stringify({ pickupDiscountPercent }),
  }).then((r) => r.merchant);

// Real Naver Pay-style boosted cashback opt-in -- rate is a fraction 0-0.05 (0-5%);
// null clears it back to the standard (unboosted) rate.
export const setCashbackRate = (rate: number | null) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/cashback-rate', {
    method: 'POST',
    body: JSON.stringify({ rate }),
  }).then((r) => r.merchant);

// Real 배달의민족 예약주문 (scheduled ordering) opt-in.
export const setAcceptsScheduledOrders = (accepts: boolean) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/scheduled-orders-participation', {
    method: 'POST',
    body: JSON.stringify({ accepts }),
  }).then((r) => r.merchant);

// Real Baemin CEO app 영업일시중지 (temporarily pause business) (2026-08-16) -- see
// MerchantService.setAcceptingOrders's own doc comment on the backend.
export const setAcceptingOrders = (accepting: boolean) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/accepting-orders', {
    method: 'POST',
    body: JSON.stringify({ accepting }),
  }).then((r) => r.merchant);

// Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day schedule) (2026-08-16) --
// see backend Merchant.closedWeekdays's own doc comment. weekdays: 1=Monday..7=Sunday
// (java.time.DayOfWeek's own real ISO-8601 numbering), empty array clears the schedule.
export const setClosedWeekdays = (weekdays: number[]) =>
  apiFetch<{ success: boolean; merchant: Merchant }>('/api/v1/merchant/closed-weekdays', {
    method: 'POST',
    body: JSON.stringify({ weekdays }),
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

// Real 마감할인 (closing/surplus discount) toggle (2026-08-15) -- see
// MerchantProductService.setSurplusDeal's own doc comment on the backend. Pass
// expiresAt = null to clear an existing deal.
export const setSurplusDeal = (productId: string, expiresAt: string | null, stockQuantity: number | null) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}/surplus-deal`, {
    method: 'PATCH',
    body: JSON.stringify({ expiresAt, stockQuantity }),
  }).then((r) => r.product);

// Real Baemin CEO app/DoorDash-style "86" (temporarily sold out) toggle (2026-08-16) --
// see backend MerchantProductService.setSoldOut's own doc comment.
export const setSoldOut = (productId: string, soldOut: boolean) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}/sold-out`, {
    method: 'PATCH',
    body: JSON.stringify({ soldOut }),
  }).then((r) => r.product);

// Real Coupang WING 상품분석 (product analytics) -- see backend
// MerchantProductService.getProductAnalytics's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built with zero client anywhere.
export interface ProductAnalytics {
  viewCount: number;
  orderCount: number;
}

export const getProductAnalytics = (productId: string) =>
  apiFetch<{ success: boolean } & ProductAnalytics>(`/api/v1/merchant/products/${productId}/analytics`);

export const removeProduct = (productId: string) =>
  apiFetch<{ success: boolean; product: MerchantProduct }>(`/api/v1/merchant/products/${productId}`, {
    method: 'DELETE',
  }).then((r) => r.product);

// Real bulk/wholesale pricing -- see the backend's ProductPriceTier doc comment.
// Real checkout money impact: OrderService applies the highest-qualifying tier's
// unitPrice automatically once a buyer's order quantity meets minQuantity -- this
// isn't informational-only pricing. Replace-all, same convention
// MerchantBookingService.setAvailability already established.
export interface PriceTier {
  minQuantity: number;
  unitPrice: number;
}

export const fetchPriceTiers = (productId: string) =>
  apiFetch<{ success: boolean; tiers: PriceTier[] }>(`/api/v1/merchant/products/${productId}/price-tiers`).then((r) => r.tiers);

export const setPriceTiers = (productId: string, tiers: PriceTier[]) =>
  apiFetch<{ success: boolean; tiers: PriceTier[] }>(`/api/v1/merchant/products/${productId}/price-tiers`, {
    method: 'POST',
    body: JSON.stringify({ tiers }),
  }).then((r) => r.tiers);

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount OVERLAY on an existing product,
// distinct from PriceTier above (a permanent bulk-quantity discount, not a scheduled
// event). Consumer browse already real on bank-mfe/Android/iOS; this is the
// merchant-facing creation/management half.
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

export const createTimeDeal = (productId: string, dealPrice: number, totalQuantity: number, startsAt: string, endsAt: string) =>
  apiFetch<{ success: boolean; deal: TimeDeal }>('/api/v1/time-deals', {
    method: 'POST',
    body: JSON.stringify({ productId, dealPrice, totalQuantity, startsAt, endsAt }),
  }).then((r) => r.deal);

export const fetchMyTimeDeals = () =>
  apiFetch<{ success: boolean; deals: TimeDeal[] }>('/api/v1/time-deals/mine?size=50').then((r) => r.deals);

export const endTimeDeal = (dealId: string) =>
  apiFetch<{ success: boolean; deal: TimeDeal }>(`/api/v1/time-deals/${dealId}/end`, { method: 'POST' }).then((r) => r.deal);

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

// Real Coupang WING-style 베스트 상품 (best-selling products) report (2026-08-16) --
// see backend MerchantService.getTopSellingProducts's own doc comment.
export const getTopSellingProducts = (from?: string, to?: string, limit?: number) => {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  if (limit) params.set('limit', String(limit));
  const qs = params.toString();
  return apiFetch<{ success: boolean; from: string; to: string; products: TopSellingProduct[] }>(
    `/api/v1/merchant/reports/top-products${qs ? `?${qs}` : ''}`,
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
