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
}

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
    headers: { 'Idempotency-Key': randomUUID() },
  });

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
