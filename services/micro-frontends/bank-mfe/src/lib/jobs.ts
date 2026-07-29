import { apiFetch } from './api';
import type { HoodReview } from './marketplace';

// Real 당근알바 (Danggeun/Karrot "Alba"/part-time-job board)-style local job posting
// (rw.itunda.jobs, 2026-07-19) -- see JobPostService.kt's own doc comment for the full
// backend account, including why "message poster" IS the real apply mechanism.

export type JobPayType = 'HOURLY' | 'FIXED';

export interface JobPost {
  id: string;
  posterId: string;
  category: string;
  title: string;
  description: string;
  payType: JobPayType;
  payAmount: number;
  status: 'OPEN' | 'FILLED' | 'REMOVED';
  createdAt: string;
  latitude?: number | null;
  longitude?: number | null;
  // Real hyperlocal neighborhood (2026-07-20), cached at creation time -- see
  // lib/neighborhood.ts's own doc comment for the full account.
  neighborhood?: string | null;
  // workerId added 2026-07-24 -- real optional worker identification captured at
  // mark-filled time, see backend JobPost.kt's own doc comment. Only set once a
  // real review becomes possible for this transaction.
  workerId?: string | null;
}

export interface JobCategory {
  id: string;
  label: string;
}

export const fetchJobCategories = () =>
  apiFetch<{ success: boolean; categories: JobCategory[] }>('/api/v1/jobs/categories').then((r) => r.categories);

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-24) -- see backend
// TrustScoreService's own doc comment for the full account. A posterId -> cached
// User.trustScore map, resolved server-side in one batch call alongside the post list
// itself (see JobPostController's own doc comment) -- was already spread in every one
// of these responses since 2026-07-21, but silently discarded here until now. Reuses
// lib/marketplace.ts's own TrustScores type (same Record<string, number> shape) rather
// than exporting a duplicate.
import type { TrustScores } from './marketplace';

export const fetchJobPosts = (category?: string) =>
  apiFetch<{ success: boolean; posts: JobPost[]; trustScores: TrustScores }>(
    `/api/v1/jobs/posts${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ posts: r.posts, trustScores: r.trustScores }));

export const fetchMyJobPosts = () =>
  apiFetch<{ success: boolean; posts: JobPost[]; trustScores: TrustScores }>('/api/v1/jobs/my-posts')
    .then((r) => ({ posts: r.posts, trustScores: r.trustScores }));

// Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
// recommendation #6. See backend JobPostRepository's own doc comment.
export const fetchMyWorkedJobPosts = () =>
  apiFetch<{ success: boolean; posts: JobPost[]; trustScores: TrustScores }>('/api/v1/jobs/my-worked-posts')
    .then((r) => ({ posts: r.posts, trustScores: r.trustScores }));

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment. Throws ApiError with code NEIGHBORHOOD_NOT_SET (real 400) if the caller
// hasn't set one yet.
export const fetchJobPostsMyNeighborhood = (category?: string) =>
  apiFetch<{ success: boolean; posts: JobPost[]; trustScores: TrustScores }>(
    `/api/v1/jobs/posts/my-neighborhood${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ posts: r.posts, trustScores: r.trustScores }));

export const createJobPost = (
  category: string,
  title: string,
  description: string,
  payType: JobPayType,
  payAmount: number,
  latitude?: number,
  longitude?: number,
) =>
  apiFetch<{ success: boolean; post: JobPost }>('/api/v1/jobs/posts', {
    method: 'POST',
    body: JSON.stringify({ category, title, description, payType, payAmount, latitude, longitude }),
  }).then((r) => r.post);

export const markJobPostFilled = (jobPostId: string, workerPhoneNumber?: string) =>
  apiFetch<{ success: boolean; post: JobPost }>(`/api/v1/jobs/posts/${jobPostId}/mark-filled`, {
    method: 'POST',
    body: JSON.stringify({ workerPhoneNumber }),
  }).then((r) => r.post);

// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodReviewService's own doc comment.
export const submitJobPostReview = (jobPostId: string, goodPoints: string[], uncomfortablePoints: string[]) =>
  apiFetch<{ success: boolean; review: HoodReview }>(`/api/v1/jobs/posts/${jobPostId}/review`, {
    method: 'POST',
    body: JSON.stringify({ goodPoints, uncomfortablePoints }),
  }).then((r) => r.review);

// Real read-back for the review submitted above (item 192) -- see lib/marketplace.ts's
// fetchListingReviews for the full account; identical shape.
export const fetchJobPostReviews = (jobPostId: string) =>
  apiFetch<{ success: boolean; reviews: HoodReview[] }>(`/api/v1/jobs/posts/${jobPostId}/review`).then((r) => r.reviews);

export const removeJobPost = (jobPostId: string) =>
  apiFetch<{ success: boolean; post: JobPost }>(`/api/v1/jobs/posts/${jobPostId}`, {
    method: 'DELETE',
  }).then((r) => r.post);

// Real "message poster" -- reuses the exact same messaging system (see lib/messaging.ts)
// under the hood, same as lib/marketplace.ts's contactSeller; the returned conversation
// id is a genuine messaging conversation id, openable straight into the Messages tab.
export const contactPoster = (jobPostId: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>(`/api/v1/jobs/posts/${jobPostId}/contact-poster`, {
    method: 'POST',
  }).then((r) => r.conversation);

// Real 당근알바 job-post wishlist (2026-07-22) -- closes a gap docs/DESIGN_REFERENCES.md
// named directly: Marketplace listings already got a real wishlist (2026-07-21,
// lib/marketplace.ts) but Jobs never did. Mirrors FavoriteListing's exact shape; see
// JobPostFavoriteService.kt's own doc comment on the backend.
export interface FavoriteJobPost {
  jobPostId: string;
  title: string;
  payAmount: number;
  category: string;
  favoritedAt: string;
}

export const addJobPostFavorite = (jobPostId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/jobs/posts/${jobPostId}/favorite`, { method: 'POST' });

export const removeJobPostFavorite = (jobPostId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/jobs/posts/${jobPostId}/favorite`, { method: 'DELETE' });

export const fetchMyFavoriteJobPosts = () =>
  apiFetch<{ success: boolean; favorites: FavoriteJobPost[] }>('/api/v1/jobs/posts/favorites').then((r) => r.favorites);
