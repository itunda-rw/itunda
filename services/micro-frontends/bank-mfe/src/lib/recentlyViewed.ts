// Real "recently viewed" convenience store (2026-08-23) -- ported from Android's
// RecentlyViewedProductsStore.kt (2026-08-10, itself sourced from real Coupang/Naver/
// Toss/Kakao Shopping) and RecentlyViewedRestaurantsStore.kt (sourced from real
// Baemin/Coupang Eats), neither of which bank-mfe ever got -- a real, confirmed gap:
// this feature existed only on Android before now, not web, not iOS. Purely
// client-side, per-device localStorage -- no account-wide sync, no backend needed,
// same real scope the Android stores already established. A tiny generic factory
// rather than two near-identical copies, since the add/get/cap logic is genuinely
// identical between products and restaurants -- only the item shape and storage key
// differ.
function createRecentlyViewedStore<T extends { id: string }>(storageKey: string, cap: number) {
  const getAll = (): T[] => {
    try {
      const raw = localStorage.getItem(storageKey);
      return raw ? (JSON.parse(raw) as T[]) : [];
    } catch {
      return [];
    }
  };
  const add = (item: T): T[] => {
    const next = [item, ...getAll().filter((i) => i.id !== item.id)].slice(0, cap);
    try {
      localStorage.setItem(storageKey, JSON.stringify(next));
    } catch {
      // Real, non-critical (e.g. private-browsing storage quota) -- a pure
      // convenience feature, never worth failing the whole screen over.
    }
    return next;
  };
  return { getAll, add };
}

export interface RecentlyViewedProduct {
  id: string;
  merchantId: string;
  businessName: string;
  name: string;
  price: number;
  imageUrl?: string | null;
  discountPercent?: number | null;
}
export const recentlyViewedProductsStore = createRecentlyViewedStore<RecentlyViewedProduct>('itunda_shop_recently_viewed', 12);

export interface RecentlyViewedRestaurant {
  id: string; // merchantId
  businessName: string;
  category?: string | null;
  photoUrl?: string | null;
}
export const recentlyViewedRestaurantsStore = createRecentlyViewedStore<RecentlyViewedRestaurant>('itunda_eats_recently_viewed', 12);
