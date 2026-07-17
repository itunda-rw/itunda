import { apiFetch } from './api';

// Real "browse partner merchants, earn cashback" catalog (Toss Shopping parity) -- see
// ShoppingCashbackService.kt's own doc comment. First real UI touchpoint for this
// endpoint, added 2026-07-17 alongside making this app real.

export interface ShoppingMerchant {
  merchantId: string;
  businessName: string;
  cashbackRate: string;
}

export const fetchShoppingCatalog = () =>
  apiFetch<{ success: boolean; merchants: ShoppingMerchant[] }>('/api/v1/shopping/merchants').then((r) => r.merchants);
