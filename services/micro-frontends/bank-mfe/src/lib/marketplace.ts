import { apiFetch } from './api';

// Real 당근마켓-style marketplace (rw.itunda.marketplace, 2026-07-18) -- the second
// "super app" phase, built right after messaging so "message seller" could reuse it.
// First real UI touchpoint for these endpoints -- see MarketplaceService.kt's own doc
// comment for the full backend account, including the honest "no real location data"
// scope.

export interface Listing {
  id: string;
  sellerId: string;
  title: string;
  description: string;
  price: number;
  category: string;
  status: 'ACTIVE' | 'SOLD' | 'REMOVED';
  createdAt: string;
}

export const fetchListings = (category?: string) =>
  apiFetch<{ success: boolean; listings: Listing[] }>(
    `/api/v1/marketplace/listings${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => r.listings);

export const fetchMyListings = () =>
  apiFetch<{ success: boolean; listings: Listing[] }>('/api/v1/marketplace/my-listings').then((r) => r.listings);

export const createListing = (title: string, description: string, price: number, category: string) =>
  apiFetch<{ success: boolean; listing: Listing }>('/api/v1/marketplace/listings', {
    method: 'POST',
    body: JSON.stringify({ title, description, price, category }),
  }).then((r) => r.listing);

export const markListingSold = (listingId: string) =>
  apiFetch<{ success: boolean; listing: Listing }>(`/api/v1/marketplace/listings/${listingId}/mark-sold`, {
    method: 'POST',
  }).then((r) => r.listing);

export const removeListing = (listingId: string) =>
  apiFetch<{ success: boolean; listing: Listing }>(`/api/v1/marketplace/listings/${listingId}`, {
    method: 'DELETE',
  }).then((r) => r.listing);

// Real "message seller" -- reuses the exact same messaging system (see lib/messaging.ts)
// under the hood; the returned conversation id is a genuine messaging conversation id,
// openable straight into the Messages tab's real chat thread.
export const contactSeller = (listingId: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>(`/api/v1/marketplace/listings/${listingId}/contact-seller`, {
    method: 'POST',
  }).then((r) => r.conversation);
