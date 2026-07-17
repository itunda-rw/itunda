import { apiFetch, ApiError } from './api';

// Field shapes match services/backend's real domain entities exactly (Merchant,
// PaymentIntent) -- see MerchantController.kt, the source of truth this talks to.

export interface Merchant {
  id: string;
  ownerUserId: string;
  businessName: string;
  webhookUrl: string | null;
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

export const getReport = (from?: string, to?: string) => {
  const params = new URLSearchParams();
  if (from) params.set('from', from);
  if (to) params.set('to', to);
  const qs = params.toString();
  return apiFetch<{ success: boolean; from: string; to: string; days: ReportDay[] }>(
    `/api/v1/merchant/reports${qs ? `?${qs}` : ''}`,
  );
};

// The backend deliberately returns a reference/code, not a QR image (see
// PaymentIntent.kt's own doc comment) -- this is the client-side encoding convention:
// itunda's real custom URL scheme (itunda://saronite/... is already used for mini-apps,
// see docs/ARCHITECTURE.md's mini-app host row), extended with a pay path a customer's
// app would resolve into a POST /api/v1/merchant/collect/{intentId} call.
export const paymentIntentQrPayload = (intentId: string) => `itunda://pay?intentId=${intentId}`;
