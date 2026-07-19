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
