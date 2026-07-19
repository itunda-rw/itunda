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
  // Real optional seller-set location (2026-07-18) -- backs real proximity search
  // (MarketplaceService.nearby) and, 2026-07-19, "Get directions to this seller".
  latitude?: number | null;
  longitude?: number | null;
}

export const fetchListings = (category?: string) =>
  apiFetch<{ success: boolean; listings: Listing[] }>(
    `/api/v1/marketplace/listings${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => r.listings);

export const fetchMyListings = () =>
  apiFetch<{ success: boolean; listings: Listing[] }>('/api/v1/marketplace/my-listings').then((r) => r.listings);

export const createListing = (
  title: string,
  description: string,
  price: number,
  category: string,
  latitude?: number,
  longitude?: number,
) =>
  apiFetch<{ success: boolean; listing: Listing }>('/api/v1/marketplace/listings', {
    method: 'POST',
    body: JSON.stringify({ title, description, price, category, latitude, longitude }),
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

// Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's own
// doc comment. Each offer/counter/accept/reject is a real message posted in the same
// real conversation contactSeller establishes, rendered inline as an offer bubble
// rather than a separate surface.
export type PriceOfferStatus = 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED';

export interface PriceOffer {
  id: string;
  listingId: string;
  messageId: string;
  conversationId: string;
  buyerId: string;
  sellerId: string;
  proposedByUserId: string;
  amount: number;
  status: PriceOfferStatus;
  createdAt: string;
  respondedAt: string | null;
}

export const makeOffer = (listingId: string, amount: number) =>
  apiFetch<{ success: boolean; offer: PriceOffer }>(`/api/v1/marketplace/listings/${listingId}/offers`, {
    method: 'POST',
    body: JSON.stringify({ amount }),
  }).then((r) => r.offer);

export const respondToOffer = (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) =>
  apiFetch<{ success: boolean; offer: PriceOffer }>(`/api/v1/marketplace/offers/${offerId}/respond`, {
    method: 'POST',
    body: JSON.stringify({ action, counterAmount }),
  }).then((r) => r.offer);

// Real per-thread negotiation history -- fetched alongside a conversation's messages so
// the Talk thread can render offer bubbles for whichever messages carry one.
export const fetchOffersForConversation = (conversationId: string) =>
  apiFetch<{ success: boolean; offers: PriceOffer[] }>(`/api/v1/marketplace/conversations/${conversationId}/offers`).then(
    (r) => r.offers,
  );
