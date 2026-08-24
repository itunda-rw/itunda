import { apiFetch } from './api';
import { randomUUID } from './uuid';

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
  // Real hyperlocal neighborhood (2026-07-20), cached at creation time -- see
  // lib/neighborhood.ts's own doc comment for the full account.
  neighborhood?: string | null;
  // Seller-provided public landmark only. It is not verified and is never an
  // address or a safety guarantee.
  meetingPlace?: string | null;
  // buyerId added 2026-07-24 -- real optional buyer identification captured at
  // mark-sold time, see backend Listing.kt's own doc comment. Only set once a real
  // review becomes possible for this transaction.
  buyerId?: string | null;
  // Real seller-uploaded photo (rw.itunda.marketplace.web.UploadController), real on
  // backend + Android since 2026-07-24/25 -- bank-mfe never had this field at all
  // despite createListing already accepting it server-side. Set once at creation time
  // only (no separate edit-photo endpoint).
  photoUrl?: string | null;
  // Real 당근마켓 조회수 (view count), 2026-08-16 -- see backend Listing.viewCount's own
  // doc comment. Real-incremented server-side on every real GET of this listing's
  // detail page; optional since older cached listing objects (e.g. a browse-list item
  // fetched before this field existed) may not carry it.
  viewCount?: number;
}

// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodTransactionReview.kt's own doc comment. goodPoints/
// uncomfortablePoints are preset tag ids (never free text), matching Karrot's own real
// review UX.
export interface HoodReview {
  id: string;
  transactionType: string;
  transactionId: string;
  reviewerId: string;
  revieweeId: string;
  goodPoints: string[];
  uncomfortablePoints: string[];
  createdAt: string;
}

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-21) -- see backend
// TrustScoreService's own doc comment for the full account. A sellerId -> cached
// User.trustScore map, resolved server-side in one batch call alongside the listing
// page itself (see MarketplaceController's own doc comment) -- never fetched per-card.
export type TrustScores = Record<string, number>;

export const fetchListings = (category?: string) =>
  apiFetch<{ success: boolean; listings: Listing[]; trustScores: TrustScores }>(
    `/api/v1/marketplace/listings${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

// Real per-listing detail fetch (2026-08-16) -- GET /marketplace/listings/{id} existed
// on the backend already (sellerTrustScore comes from here) but had zero real caller
// on any platform; bank-mfe's own ListingCard renders straight off the already-fetched
// browse-list item, never a fresh per-listing round trip. Wired in now specifically to
// give the real backend 조회수 (view count) increment (Listing.viewCount) a genuine
// trigger -- see ListingCard's own doc comment for where this is called.
export const fetchListingDetail = (listingId: string) =>
  apiFetch<{ success: boolean; listing: Listing; sellerTrustScore: number | null }>(
    `/api/v1/marketplace/listings/${listingId}`,
  );

// Real Karrot 중고거래 category taxonomy -- see backend MarketplaceService
// .CATEGORIES's own doc comment.
export const fetchMarketplaceCategories = () =>
  apiFetch<{ success: boolean; categories: string[] }>('/api/v1/marketplace/categories').then((r) => r.categories);

export const fetchMyListings = () =>
  apiFetch<{ success: boolean; listings: Listing[]; trustScores: TrustScores }>('/api/v1/marketplace/my-listings')
    .then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

// Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
// recommendation #6. See backend ListingRepository's own doc comment.
export const fetchMyPurchases = () =>
  apiFetch<{ success: boolean; listings: Listing[]; trustScores: TrustScores }>('/api/v1/marketplace/my-purchases')
    .then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment for the full account. Throws ApiError with code NEIGHBORHOOD_NOT_SET
// (real 400) if the caller hasn't set one yet -- callers should catch that specific
// code and prompt for setup, not treat it as a generic load failure.
export const fetchListingsMyNeighborhood = (category?: string) =>
  apiFetch<{ success: boolean; listings: Listing[]; trustScores: TrustScores }>(
    `/api/v1/marketplace/listings/my-neighborhood${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ listings: r.listings, trustScores: r.trustScores }));

export const createListing = (
  title: string,
  description: string,
  price: number,
  category: string,
  latitude?: number,
  longitude?: number,
  meetingPlace?: string,
  photoUrl?: string,
) =>
  apiFetch<{ success: boolean; listing: Listing }>('/api/v1/marketplace/listings', {
    method: 'POST',
    body: JSON.stringify({ title, description, price, category, latitude, longitude, meetingPlace, photoUrl }),
  }).then((r) => r.listing);

export const markListingSold = (listingId: string, buyerPhoneNumber?: string) =>
  apiFetch<{ success: boolean; listing: Listing }>(`/api/v1/marketplace/listings/${listingId}/mark-sold`, {
    method: 'POST',
    body: JSON.stringify({ buyerPhoneNumber }),
  }).then((r) => r.listing);

// Real 가격 수정 (price edit) + Karrot 가격 하락 알림 (price-drop notification) -- see
// backend MarketplaceService.updatePrice's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built (real favoriters-notified-on-drop
// side effect) with zero client anywhere.
export const updateListingPrice = (listingId: string, price: number) =>
  apiFetch<{ success: boolean; listing: Listing }>(`/api/v1/marketplace/listings/${listingId}/price`, {
    method: 'PATCH',
    body: JSON.stringify({ price }),
  }).then((r) => r.listing);

// Real "pay via itunda" Marketplace escrow (backend since 2026-07-25) -- an opt-in
// safer alternative to the existing in-person cash handoff, never replacing it. Real
// gap found 2026-08-15: this had existed on the backend and Android for weeks with
// ZERO client on web (confirmed by grep -- no caller anywhere in this MFE). First web
// client for these endpoints, mirroring Android's own MarketplaceScreen.kt flow.
// deliveryAddress is real, optional (당근마켓 바로구매-style shipped-item support,
// see backend MarketplaceEscrow.deliveryAddress's own doc comment) -- leaving it
// blank keeps the original in-person handoff this feature has always assumed.
export interface MarketplaceEscrow {
  id: string;
  listingId: string;
  buyerId: string;
  sellerId: string;
  amount: number;
  fee: number;
  status: 'HELD' | 'RELEASED' | 'REFUNDED' | 'DISPUTED';
  holdTransactionId: string;
  resolutionTransactionId: string | null;
  disputeReason: string | null;
  deliveryAddress: string | null;
  createdAt: string;
  updatedAt: string;
}

export const payEscrow = (listingId: string, deliveryAddress?: string) =>
  apiFetch<{ success: boolean; escrow: MarketplaceEscrow }>(`/api/v1/marketplace/listings/${listingId}/pay-escrow`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ deliveryAddress: deliveryAddress?.trim() || undefined }),
  }).then((r) => r.escrow);

export const confirmEscrowReceipt = (listingId: string) =>
  apiFetch<{ success: boolean; escrow: MarketplaceEscrow }>(`/api/v1/marketplace/listings/${listingId}/confirm-receipt`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
  }).then((r) => r.escrow);

export const disputeEscrow = (listingId: string, reason: string) =>
  apiFetch<{ success: boolean; escrow: MarketplaceEscrow }>(`/api/v1/marketplace/listings/${listingId}/dispute-escrow`, {
    method: 'POST',
    body: JSON.stringify({ reason }),
  }).then((r) => r.escrow);

export const getEscrow = (listingId: string) =>
  apiFetch<{ success: boolean; escrow: MarketplaceEscrow }>(`/api/v1/marketplace/listings/${listingId}/escrow`).then((r) => r.escrow);

// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodReviewService's own doc comment.
export const submitListingReview = (listingId: string, goodPoints: string[], uncomfortablePoints: string[]) =>
  apiFetch<{ success: boolean; review: HoodReview }>(`/api/v1/marketplace/listings/${listingId}/review`, {
    method: 'POST',
    body: JSON.stringify({ goodPoints, uncomfortablePoints }),
  }).then((r) => r.review);

// Real read-back for the review submitted above (item 192) -- HoodReviewService's own
// "asymmetric public/private visibility" is party-only even for reading: this real-403s
// (HOOD_REVIEW_NOT_PARTY) for anyone who wasn't a real party to the transaction, so it's
// safe to call for any transaction this account can see at all. Existed as a real,
// callable endpoint since 2026-07-24 with zero client anywhere -- submitting a review
// never showed it back, and a page refresh reset the local reviewSubmitted flag,
// silently re-offering the form (the second POST attempt just surfaced the backend's
// own REVIEW_ALREADY_SUBMITTED error rather than the actual submitted content).
export const fetchListingReviews = (listingId: string) =>
  apiFetch<{ success: boolean; reviews: HoodReview[] }>(`/api/v1/marketplace/listings/${listingId}/review`).then((r) => r.reviews);

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

// Real Marketplace listing wishlist (2026-07-21) -- closes a gap docs/DESIGN_REFERENCES.md
// named directly: itunda already had this pattern for Shop products (lib/shopping.ts)
// and Eats restaurants but not Hood listings. Mirrors ProductFavorite's exact shape;
// see ListingFavoriteService.kt's own doc comment on the backend.
export interface FavoriteListing {
  listingId: string;
  title: string;
  price: number;
  category: string;
  favoritedAt: string;
}

export const addListingFavorite = (listingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/marketplace/listings/${listingId}/favorite`, { method: 'POST' });

export const removeListingFavorite = (listingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/marketplace/listings/${listingId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoriteListings = () =>
  apiFetch<{ success: boolean; favorites: FavoriteListing[] }>('/api/v1/marketplace/listings/favorites').then((r) => r.favorites);

// Real Karrot "이 글 숨기기" (hide this post) -- see backend ListingHideService's own
// doc comment for the full sourced account.
export const hideListing = (listingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/marketplace/listings/${listingId}/hide`, { method: 'POST' });

export const unhideListing = (listingId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/marketplace/listings/${listingId}/hide`, { method: 'DELETE' });

// Real 당근마켓-style Keyword Alert (rw.itunda.marketplace.KeywordAlertService, real
// since before this session) -- first client UI for this feature on any platform
// (item 114, found via a content-grep sweep confirming zero client anywhere).
// Karrot's own real, published 30-keyword-per-user cap.
export interface KeywordAlert {
  id: string;
  userId: string;
  keyword: string;
  createdAt: string;
}

export interface KeywordAlertQuietHours {
  id: string;
  userId: string;
  startTime: string;
  endTime: string;
  enabled: boolean;
}

export const fetchKeywordAlerts = () =>
  apiFetch<{ success: boolean; alerts: KeywordAlert[] }>('/api/v1/marketplace/keyword-alerts').then((r) => r.alerts);

export const addKeywordAlert = (keyword: string) =>
  apiFetch<{ success: boolean; alert: KeywordAlert }>('/api/v1/marketplace/keyword-alerts', {
    method: 'POST',
    body: JSON.stringify({ keyword }),
  }).then((r) => r.alert);

export const removeKeywordAlert = (alertId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/marketplace/keyword-alerts/${alertId}`, { method: 'DELETE' });

export const fetchKeywordAlertQuietHours = () =>
  apiFetch<{ success: boolean; quietHours: KeywordAlertQuietHours | null }>('/api/v1/marketplace/keyword-alerts/quiet-hours').then(
    (r) => r.quietHours,
  );

export const setKeywordAlertQuietHours = (startTime: string, endTime: string, enabled: boolean) =>
  apiFetch<{ success: boolean; quietHours: KeywordAlertQuietHours }>('/api/v1/marketplace/keyword-alerts/quiet-hours', {
    method: 'POST',
    body: JSON.stringify({ startTime, endTime, enabled }),
  }).then((r) => r.quietHours);
