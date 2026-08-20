import { apiFetch } from './api';

// Real 당근부동산 (Danggeun/Karrot "Real Estate")-style property listing
// (rw.itunda.realestate, 2026-07-19) -- see PropertyListingService.kt's own doc comment
// for the full backend account.

export type PropertyListingType = 'SALE' | 'RENT';

export interface PropertyListing {
  id: string;
  listerId: string;
  listingType: PropertyListingType;
  propertyType: string;
  title: string;
  description: string;
  price: number;
  bedrooms?: number | null;
  sizeSqm?: number | null;
  status: 'AVAILABLE' | 'TAKEN' | 'REMOVED';
  createdAt: string;
  latitude?: number | null;
  longitude?: number | null;
  // Real hyperlocal neighborhood (2026-07-20), cached at creation time -- see
  // lib/neighborhood.ts's own doc comment for the full account.
  neighborhood?: string | null;
  // counterpartyId added 2026-07-24 -- real optional buyer/tenant identification
  // captured at mark-taken time, see backend PropertyListing.kt's own doc comment.
  // Only set once a real review becomes possible for this transaction.
  counterpartyId?: string | null;
  // Real ownership verification (2026-07-25) -- NONE/PENDING/VERIFIED, see backend
  // PropertyOwnershipService's own doc comment. Real client added to Android the same
  // day (a document-picker + submit flow); bank-mfe had zero client for it despite
  // that -- found via a fresh backend-module sweep. Same "URL, not a binary upload"
  // honest scope-down lib/neighborhood.ts's updateProfilePhoto already established for
  // this web client, since bank-mfe has no real photo/document upload pipeline at all.
  ownershipVerificationStatus?: 'NONE' | 'PENDING' | 'VERIFIED';
}

export interface PropertyType {
  id: string;
  label: string;
}

export const fetchPropertyTypes = () =>
  apiFetch<{ success: boolean; propertyTypes: PropertyType[] }>('/api/v1/realestate/property-types').then((r) => r.propertyTypes);

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-24) -- see backend
// TrustScoreService's own doc comment for the full account. A listerId -> cached
// User.trustScore map, resolved server-side alongside the listing list itself (see
// PropertyListingController's own doc comment) -- was already spread in every one of
// these responses since 2026-07-21, but silently discarded here until now. Reuses
// lib/marketplace.ts's own TrustScores type (same Record<string, number> shape).
import type { HoodReview, TrustScores } from './marketplace';

export const fetchPropertyListings = (listingType?: PropertyListingType, propertyType?: string) => {
  const params = new URLSearchParams();
  if (listingType) params.set('listingType', listingType);
  if (propertyType) params.set('propertyType', propertyType);
  const qs = params.toString();
  return apiFetch<{ success: boolean; listings: PropertyListing[]; trustScores: TrustScores }>(`/api/v1/realestate/listings${qs ? `?${qs}` : ''}`).then(
    (r) => ({ listings: r.listings, trustScores: r.trustScores }),
  );
};

export const fetchMyPropertyListings = () =>
  apiFetch<{ success: boolean; listings: PropertyListing[]; trustScores: TrustScores }>('/api/v1/realestate/my-listings')
    .then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

// Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
// recommendation #6. See backend PropertyListingRepository's own doc comment.
export const fetchMyAcquiredPropertyListings = () =>
  apiFetch<{ success: boolean; listings: PropertyListing[]; trustScores: TrustScores }>('/api/v1/realestate/my-acquired-listings')
    .then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment. Throws ApiError with code NEIGHBORHOOD_NOT_SET (real 400) if the caller
// hasn't set one yet. Deliberately not combined with listingType/propertyType filters --
// PropertyListingRepository's own doc comment names this as an honest v1 scoping choice.
export const fetchPropertyListingsMyNeighborhood = () =>
  apiFetch<{ success: boolean; listings: PropertyListing[]; trustScores: TrustScores }>('/api/v1/realestate/listings/my-neighborhood').then(
    (r) => ({ listings: r.listings, trustScores: r.trustScores }),
  );

export const createPropertyListing = (
  listingType: PropertyListingType,
  propertyType: string,
  title: string,
  description: string,
  price: number,
  bedrooms?: number,
  sizeSqm?: number,
  latitude?: number,
  longitude?: number,
) =>
  apiFetch<{ success: boolean; listing: PropertyListing }>('/api/v1/realestate/listings', {
    method: 'POST',
    body: JSON.stringify({ listingType, propertyType, title, description, price, bedrooms, sizeSqm, latitude, longitude }),
  }).then((r) => r.listing);

export const markPropertyListingTaken = (propertyListingId: string, counterpartyPhoneNumber?: string) =>
  apiFetch<{ success: boolean; listing: PropertyListing }>(`/api/v1/realestate/listings/${propertyListingId}/mark-taken`, {
    method: 'POST',
    body: JSON.stringify({ counterpartyPhoneNumber }),
  }).then((r) => r.listing);

// Real Karrot(당근마켓)-style price-drop notification -- see backend
// PropertyListingService.updatePrice's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: the same real gap shape as
// MarketplaceService.updatePrice (§227, already closed this session) -- fully built
// with zero client anywhere.
export const updatePropertyListingPrice = (propertyListingId: string, price: number) =>
  apiFetch<{ success: boolean; listing: PropertyListing }>(`/api/v1/realestate/listings/${propertyListingId}/price`, {
    method: 'POST',
    body: JSON.stringify({ price }),
  }).then((r) => r.listing);

// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodReviewService's own doc comment.
export const submitPropertyListingReview = (propertyListingId: string, goodPoints: string[], uncomfortablePoints: string[]) =>
  apiFetch<{ success: boolean; review: HoodReview }>(`/api/v1/realestate/listings/${propertyListingId}/review`, {
    method: 'POST',
    body: JSON.stringify({ goodPoints, uncomfortablePoints }),
  }).then((r) => r.review);

// Real read-back for the review submitted above (item 192) -- see lib/marketplace.ts's
// fetchListingReviews for the full account; identical shape.
export const fetchPropertyListingReviews = (propertyListingId: string) =>
  apiFetch<{ success: boolean; reviews: HoodReview[] }>(`/api/v1/realestate/listings/${propertyListingId}/review`).then((r) => r.reviews);

export const removePropertyListing = (propertyListingId: string) =>
  apiFetch<{ success: boolean; listing: PropertyListing }>(`/api/v1/realestate/listings/${propertyListingId}`, {
    method: 'DELETE',
  }).then((r) => r.listing);

// Real "message lister" -- reuses the exact same messaging system (see lib/messaging.ts)
// under the hood, same as lib/marketplace.ts's contactSeller / lib/jobs.ts's contactPoster.
export const contactLister = (propertyListingId: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>(`/api/v1/realestate/listings/${propertyListingId}/contact-lister`, {
    method: 'POST',
  }).then((r) => r.conversation);

// Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService's
// own doc comment. Mirrors lib/marketplace.ts's PriceOffer/makeOffer/respondToOffer
// exactly; each offer/counter/accept/reject is a real message posted in the same real
// conversation contactLister establishes, rendered inline as an offer bubble (the same
// OfferBubble component Marketplace uses -- see its narrowed prop type in
// BankDashboard.tsx for why this reuse is type-safe despite the different field names).
export type PropertyOfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED';

export interface PropertyPriceOffer {
  id: string;
  propertyListingId: string;
  messageId: string;
  conversationId: string;
  inquirerId: string;
  listerId: string;
  proposedByUserId: string;
  amount: number;
  status: PropertyOfferStatus;
  createdAt: string;
  respondedAt: string | null;
}

export const makePropertyOffer = (propertyListingId: string, amount: number) =>
  apiFetch<{ success: boolean; offer: PropertyPriceOffer }>(`/api/v1/realestate/listings/${propertyListingId}/offers`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  }).then((r) => r.offer);

export const respondToPropertyOffer = (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) =>
  apiFetch<{ success: boolean; offer: PropertyPriceOffer }>(`/api/v1/realestate/offers/${offerId}/respond`, {
    method: 'POST',
    body: JSON.stringify({ action, counterAmount }),
  }).then((r) => r.offer);

export const fetchPropertyOffersForConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; offers: PropertyPriceOffer[] }>(`/api/v1/realestate/conversations/${conversationId}/offers`).then(
    (r) => r.offers,
  );

// Real 당근부동산 property-listing wishlist (2026-07-22) -- closes the same
// docs/DESIGN_REFERENCES.md-named gap lib/jobs.ts's FavoriteJobPost closes: Marketplace
// listings already got a real wishlist (2026-07-21, lib/marketplace.ts) but Property
// never did. Mirrors FavoriteListing's exact shape; see PropertyListingFavoriteService.kt's
// own doc comment on the backend.
export interface FavoritePropertyListing {
  propertyListingId: string;
  title: string;
  price: number;
  propertyType: string;
  favoritedAt: string;
}

export const addPropertyListingFavorite = (propertyListingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/realestate/listings/${propertyListingId}/favorite`, { method: 'POST' });

export const removePropertyListingFavorite = (propertyListingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/realestate/listings/${propertyListingId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoritePropertyListings = () =>
  apiFetch<{ success: boolean; favorites: FavoritePropertyListing[] }>('/api/v1/realestate/listings/favorites').then(
    (r) => r.favorites,
  );

export const submitPropertyOwnershipVerification = (propertyListingId: string, documentUrl: string) =>
  apiFetch<{ success: boolean; submission: { status: string } }>(`/api/v1/realestate/listings/${propertyListingId}/verify-ownership`, {
    method: 'POST',
    body: JSON.stringify({ documentUrl }),
  });

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see
// PropertyListingService.estimateValue's own doc comment on the backend. A real
// comparable-listings-based estimate, computed fresh on every call, not persisted.
export interface PropertyValuationEstimate {
  estimatedValue: number;
  comparableCount: number;
  averagePricePerSqm: number;
  radiusKm: number;
}

export const fetchPropertyValuation = (
  latitude: number, longitude: number, propertyType: string, listingType: PropertyListingType, sizeSqm: number, radiusKm = 5.0,
) => {
  const params = new URLSearchParams({
    latitude: String(latitude), longitude: String(longitude), propertyType, listingType,
    sizeSqm: String(sizeSqm), radiusKm: String(radiusKm),
  });
  return apiFetch<{ success: boolean; estimate: PropertyValuationEstimate }>(`/api/v1/realestate/valuation?${params}`).then((r) => r.estimate);
};
