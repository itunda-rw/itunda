import { apiFetch } from './api';
import { randomUUID } from './uuid';

// Real Isoko ("market" in Kinyarwanda) Vendor Cash Advance -- see the backend's
// VendorCashAdvanceService.kt own doc comment for the full sourced account. Closes
// the "사장님 대출 (Boss Loans) as a business-specific lending product" follow-up gap
// this file's own getBusinessAccount/openBusinessAccount section names as unshipped.
//
// Genuinely distinct from every other lending product in this codebase: repayment is
// auto-collected as a variable % of this merchant's own REAL itunda-routed daily
// settlement inflow (QR/card collections), never a fixed installment the merchant
// initiates. Honest v1 limitation surfaced directly in this UI's own copy below: a
// vendor's off-platform cash sales are invisible to both underwriting and collection.

export type VendorCashAdvanceStatus = 'REQUESTED' | 'DISBURSED' | 'REPAID';

export interface VendorCashAdvance {
  id: string;
  merchantId: string;
  principalAmount: number;
  feeAmount: number;
  totalOwed: number;
  remainingOwed: number;
  collectionRatePercent: number;
  status: VendorCashAdvanceStatus;
  requestedAt: string;
  disbursedAt: string | null;
  repaidAt: string | null;
  lastCollectionAt: string | null;
}

export interface VendorCashAdvanceOffer {
  eligible: boolean;
  reason?: string;
  offerAmount?: number;
  feeAmount?: number;
  collectionRatePercent?: number;
  averageDailySettlement?: number;
  tradingDays?: number;
}

export const getVendorCashAdvanceOffer = (merchantId: string) =>
  apiFetch<{ success: boolean } & VendorCashAdvanceOffer>(`/api/v1/vendor-advance/offer?merchantId=${encodeURIComponent(merchantId)}`);

// Correction, 2026-09-05: the "row creation only, money only moves at disburse"
// reasoning above missed the actual risk -- VendorCashAdvanceService.applyForAdvance
// has a VendorCashAdvanceAlreadyActiveException guard, so a lost response after a
// successful apply would resubmit here and hit that guard on retry, showing the
// merchant a confusing "already active" error for an application that actually just
// succeeded. The cited VupLoanService.applyForLoan precedent had the identical gap
// and has now been fixed the same way (see VendorCashAdvanceController.apply's own
// doc comment) -- added Idempotency-Key here to match.
export const applyForVendorCashAdvance = (merchantId: string) =>
  apiFetch<{ success: boolean; advance: VendorCashAdvance }>('/api/v1/vendor-advance/apply', {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ merchantId }),
  }).then((r) => r.advance);

// Money-moving -- real Idempotency-Key convention, same as chargeCard/moveToBusinessAccount above.
export const disburseVendorCashAdvance = (advanceId: string) =>
  apiFetch<{ success: boolean; advance: VendorCashAdvance }>(`/api/v1/vendor-advance/${advanceId}/disburse`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.advance);

export const getMyVendorCashAdvance = (merchantId: string) =>
  apiFetch<{ success: boolean; advance: VendorCashAdvance | null }>(`/api/v1/vendor-advance/me?merchantId=${encodeURIComponent(merchantId)}`).then(
    (r) => r.advance,
  );

export const repayVendorCashAdvanceEarly = (advanceId: string, amount: number) =>
  apiFetch<{ success: boolean; advance: VendorCashAdvance }>(`/api/v1/vendor-advance/${advanceId}/repay-early`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.advance);
