// Real gap found live (uncalled-endpoint sweep, 2026-08-29): Merchant.photoUrls (a
// real, real gallery of up to 20 photos, distinct from the older single cover photo
// every merchant client already edits via setMerchantPhotoUrl/POST /photo) is already
// displayed live to customers via the composed GET /maps/places/{merchantId} ->
// PlacePhotosTab on Android/iOS -- but no merchant client anywhere had a way to
// populate more than that one cover photo.
//
// Types are intentionally local, not added to lib/merchant.ts's shared `Merchant`
// interface -- that file was already at its file-size-lint baseline, and this new
// field only needs to be known here, not by every other screen that already imports
// `Merchant` for its existing 30+ fields.
import { apiFetch } from './api';
import type { Merchant } from './merchant';

// Real wire shape, confirmed live against the backend, NOT a JSON array: `Merchant.
// photoUrls` (services/backend/core/.../domain/Merchant.kt) is a single comma-joined
// column, re-serialized as-is on GET /merchant/me and this endpoint's own response --
// only the composed GET /maps/places/{merchantId} splits it server-side into a real
// array for MapPlaceDetailDto. The POST *request* body below is a real array; only
// the response field is this comma-joined string.
export type MerchantWithPhotos = Merchant & { photoUrls: string | null };

export const photoUrlList = (photoUrls: string | null): string[] =>
  (photoUrls ?? '').split(',').map((u) => u.trim()).filter(Boolean);

const MAX_GALLERY_PHOTOS = 20;

export const setMerchantPhotoUrls = (photoUrls: string[]) => {
  if (photoUrls.length > MAX_GALLERY_PHOTOS) {
    throw new Error(`A gallery can have at most ${MAX_GALLERY_PHOTOS} photos`);
  }
  return apiFetch<{ success: boolean; merchant: MerchantWithPhotos }>('/api/v1/merchant/photos', {
    method: 'POST',
    body: JSON.stringify({ photoUrls }),
  }).then((r) => r.merchant);
};
