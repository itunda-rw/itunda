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
}

export interface PropertyType {
  id: string;
  label: string;
}

export const fetchPropertyTypes = () =>
  apiFetch<{ success: boolean; propertyTypes: PropertyType[] }>('/api/v1/realestate/property-types').then((r) => r.propertyTypes);

export const fetchPropertyListings = (listingType?: PropertyListingType, propertyType?: string) => {
  const params = new URLSearchParams();
  if (listingType) params.set('listingType', listingType);
  if (propertyType) params.set('propertyType', propertyType);
  const qs = params.toString();
  return apiFetch<{ success: boolean; listings: PropertyListing[] }>(`/api/v1/realestate/listings${qs ? `?${qs}` : ''}`).then(
    (r) => r.listings,
  );
};

export const fetchMyPropertyListings = () =>
  apiFetch<{ success: boolean; listings: PropertyListing[] }>('/api/v1/realestate/my-listings').then((r) => r.listings);

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment. Throws ApiError with code NEIGHBORHOOD_NOT_SET (real 400) if the caller
// hasn't set one yet. Deliberately not combined with listingType/propertyType filters --
// PropertyListingRepository's own doc comment names this as an honest v1 scoping choice.
export const fetchPropertyListingsMyNeighborhood = () =>
  apiFetch<{ success: boolean; listings: PropertyListing[] }>('/api/v1/realestate/listings/my-neighborhood').then(
    (r) => r.listings,
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

export const markPropertyListingTaken = (propertyListingId: string) =>
  apiFetch<{ success: boolean; listing: PropertyListing }>(`/api/v1/realestate/listings/${propertyListingId}/mark-taken`, {
    method: 'POST',
  }).then((r) => r.listing);

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
