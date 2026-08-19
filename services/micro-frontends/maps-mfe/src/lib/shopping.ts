import { apiFetch } from './api';

// Narrow slice of bank-mfe/src/lib/shopping.ts (2026-08-19, the maps-mfe split) --
// only what MapView's nearby-merchant browse needs, same "each MFE re-implements its
// own slice" convention as lib/api.ts/lib/maps.ts's own doc comments in this package.

export interface ShoppingMerchant {
  merchantId: string;
  businessName: string;
  category: string | null;
  cashbackRate: string;
  latitude?: number | null;
  longitude?: number | null;
  photoUrl?: string | null;
  minOrderAmount?: number | null;
  rating?: number | null;
  reviewCount?: number;
  distanceKm?: number | null;
  deliveryTimeMinutes?: number | null;
  phoneNumber?: string | null;
  openingHours?: string | null;
  favoriteCount?: number;
  isAcceptingOrders?: boolean;
  isBusy?: boolean;
  closedToday?: boolean;
}

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
