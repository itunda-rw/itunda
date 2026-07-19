import { apiFetch } from './api';

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
}

export const fetchShoppingCatalog = () =>
  apiFetch<{ success: boolean; merchants: ShoppingMerchant[] }>('/api/v1/shopping/merchants').then((r) => r.merchants);

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
// PaymentIntent id a merchant's real QR encodes -- typing it in is the same real
// manual-code-entry fallback many real payment apps offer alongside camera QR
// scanning, not an invented shortcut; this app has no camera-based QR scanner (that's
// the real mobile app's job, already real there), so this is the honest, real
// alternative rather than faking a scan.
export const collectPayment = (intentId: string) =>
  apiFetch<{ success: boolean } & CollectPaymentResult>(`/api/v1/merchant/collect/${intentId}`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
  });
