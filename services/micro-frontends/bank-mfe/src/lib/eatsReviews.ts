import { apiFetch } from './api';

// Extracted from eats.ts (2026-09-12, file-size-lint -- that file crossed 500
// lines for the first time once fetchRestaurantReviews's own pagination fix
// landed) -- a fully self-contained restaurant-review sub-domain, no shared
// state with anything else in that file.
//
// Real post-delivery ratings & reviews (2026-07-18) -- the single biggest remaining
// Coupang Eats-defining gap, added at the user's direct request. See
// EatsReviewService.kt's own doc comment for the full backend account.

export interface EatsReview {
  id: string;
  orderId: string;
  buyerId: string;
  restaurantId: string;
  riderId: string | null;
  restaurantRating: number;
  restaurantComment: string | null;
  riderRating: number | null;
  riderComment: string | null;
  // Real owner-side reply (2026-07-26) -- see EatsReviewService.replyToRestaurantReview's
  // own doc comment.
  ownerReply: string | null;
  ownerRepliedAt: string | null;
  createdAt: string;
  // Real Baemin/Coupang-style "도움돼요" (helpful) count -- see backend
  // EatsReviewService.toggleHelpful's own doc comment. Same silent-discard shape as
  // EatsOrder.tipAmount above: the backend has always returned this, this client
  // type never declared it.
  helpfulCount?: number;
  // Real review photo (itunda Eats redesign, 2026-08-28) -- see backend
  // EatsReview.photoUrl's own doc comment (migration V224). Same silent-discard
  // shape as helpfulCount above: real end-to-end on the backend (submit + read),
  // this client type just never declared it, so a real submitted photo was never
  // rendered anywhere. Null/undefined means the reviewer genuinely didn't attach one.
  photoUrl?: string | null;
}

export interface RatingSummary {
  average: number | null;
  count: number;
}

// Real Baemin/Coupang-style "도움돼요" (helpful) idempotent toggle -- see backend
// EatsReviewService.toggleHelpful's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built with zero client anywhere.
// Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
// EatsReviewService.reportReview's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built (own-review guard,
// already-reported guard, real reporter-count auto-hide threshold) with zero client
// anywhere. Genuinely NOT covered by the existing generic HoodReportButton
// (lib/hoodReport.ts) -- that mechanism's 4 real targets are
// MARKETPLACE_LISTING/COMMUNITY_POST/JOB_POST/PROPERTY_LISTING, no REVIEW target
// exists there at all, so this needs its own real client, not a route-through.
export type EatsReviewReportReason = 'DEFAMATION' | 'PERSONAL_INFO_EXPOSURE' | 'OBSCENE_OR_VIOLENT' | 'UNRELATED_ABUSE' | 'OTHER';

export const reportEatsReview = (reviewId: string, reason: EatsReviewReportReason, details?: string) =>
  apiFetch<{ success: boolean; report: { id: string } }>(`/api/v1/eats/reviews/${reviewId}/report`, {
    method: 'POST',
    body: JSON.stringify({ reason, details: details?.trim() || null }),
  });

export const toggleReviewHelpful = (reviewId: string) =>
  apiFetch<{ success: boolean; helpful: boolean }>(`/api/v1/eats/reviews/${reviewId}/helpful`, { method: 'POST' }).then((r) => r.helpful);

export const submitEatsReview = (
  orderId: string,
  restaurantRating: number,
  restaurantComment: string,
  riderRating: number | null,
  riderComment: string,
  // Real optional review photo (itunda Eats redesign, 2026-08-28) -- see
  // EatsReview.photoUrl's own doc comment; the backend has taken this since
  // 2026-08-04, no web client ever sent it. Same "bring your own already-hosted
  // URL" bar as Merchant.photoUrl elsewhere in this codebase -- itunda has no
  // image-upload/storage pipeline to invent one.
  photoUrl?: string,
  // Real preset-tag checklist (itunda Maps redesign, 2026-08-28, direct Naver Map
  // reference: "이런 점이 좋았어요") -- see EatsReview.goodPoints' own doc comment on
  // the backend, ported from HoodTransactionReview's own exact preset-tag convention.
  goodPoints: string[] = [],
) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/orders/${orderId}/review`, {
    method: 'POST',
    body: JSON.stringify({
      restaurantRating,
      restaurantComment: restaurantComment.trim() || null,
      riderRating,
      riderComment: riderRating == null ? null : riderComment.trim() || null,
      photoUrl: photoUrl?.trim() || null,
      goodPoints,
    }),
  }).then((r) => r.review);

export const fetchRestaurantRating = (restaurantId: string) =>
  apiFetch<{ success: boolean; average: number | null; count: number }>(`/api/v1/eats/restaurants/${restaurantId}/rating`).then(
    (r) => ({ average: r.average, count: r.count }) as RatingSummary,
  );

// Real preset-tag aggregate (itunda Maps redesign, 2026-08-28) -- see
// EatsReviewService.restaurantGoodPointCounts' own doc comment on the backend.
export const EATS_GOOD_POINT_LABELS: [string, string][] = [
  ['GREAT_FOOD', '🍽️ Great food'], ['GREAT_DESSERT', '🍰 Great dessert'], ['NICE_INTERIOR', '🛋️ Nice interior'],
  ['GREAT_DRINKS', '🥤 Great drinks'], ['GOOD_FOR_CONVERSATION', '💬 Good for conversation'],
];

export const fetchRestaurantGoodPoints = (restaurantId: string) =>
  apiFetch<{ success: boolean; counts: Record<string, number>; goodPointOptions: string[] }>(
    `/api/v1/eats/restaurants/${restaurantId}/good-points`,
  );

// Real pagination-discard fix (2026-09-12, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- the real Pageable/pageMeta endpoint was always there; page
// just wasn't ever sent, silently capping a popular restaurant's reviews at
// the most recent 20.
export const fetchRestaurantReviews = (restaurantId: string, page = 0) =>
  apiFetch<{ success: boolean; reviews: EatsReview[]; page: number; totalPages: number }>(
    `/api/v1/eats/restaurants/${restaurantId}/reviews?page=${page}&size=20`,
  );

// Real owner-side reply (2026-07-26 backend, first client 2026-07-29, item 184) -- see
// EatsReviewService.replyToRestaurantReview's own doc comment. Restaurant-owner only,
// enforced server-side; one editable reply per review.
export const replyToRestaurantReview = (reviewId: string, reply: string) =>
  apiFetch<{ success: boolean; review: EatsReview }>(`/api/v1/eats/reviews/${reviewId}/reply`, {
    method: 'POST',
    body: JSON.stringify({ reply }),
  }).then((r) => r.review);
