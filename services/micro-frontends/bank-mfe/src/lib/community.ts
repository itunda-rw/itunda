import { apiFetch } from './api';

// Real 동네생활 (Danggeun/Karrot "Neighborhood Life")-style community board
// (rw.itunda.community, 2026-07-19) -- Karrot's own second core surface alongside its
// already-real marketplace (see lib/marketplace.ts), explicitly named by the user
// alongside 당근알바/당근부동산 as a distinct neighborhood-services product. See
// CommunityService.kt's own doc comment for the full backend account.

export interface CommunityPost {
  id: string;
  authorId: string;
  category: string;
  title: string;
  body: string;
  status: 'ACTIVE' | 'REMOVED';
  likeCount: number;
  commentCount: number;
  createdAt: string;
  latitude?: number | null;
  longitude?: number | null;
}

export interface CommunityComment {
  id: string;
  postId: string;
  authorId: string;
  body: string;
  createdAt: string;
}

export interface CommunityCategory {
  id: string;
  label: string;
}

export const fetchCommunityCategories = () =>
  apiFetch<{ success: boolean; categories: CommunityCategory[] }>('/api/v1/community/categories').then((r) => r.categories);

export const fetchCommunityPosts = (category?: string) =>
  apiFetch<{ success: boolean; posts: CommunityPost[] }>(
    `/api/v1/community/posts${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => r.posts);

export const fetchMyCommunityPosts = () =>
  apiFetch<{ success: boolean; posts: CommunityPost[] }>('/api/v1/community/my-posts').then((r) => r.posts);

export const createCommunityPost = (
  category: string,
  title: string,
  body: string,
  latitude?: number,
  longitude?: number,
) =>
  apiFetch<{ success: boolean; post: CommunityPost }>('/api/v1/community/posts', {
    method: 'POST',
    body: JSON.stringify({ category, title, body, latitude, longitude }),
  }).then((r) => r.post);

export const fetchCommunityPost = (postId: string) =>
  apiFetch<{ success: boolean; post: CommunityPost; authorName: string; likedByMe: boolean }>(
    `/api/v1/community/posts/${postId}`,
  );

export const removeCommunityPost = (postId: string) =>
  apiFetch<{ success: boolean; post: CommunityPost }>(`/api/v1/community/posts/${postId}`, {
    method: 'DELETE',
  }).then((r) => r.post);

export const fetchCommunityComments = (postId: string) =>
  apiFetch<{ success: boolean; comments: { comment: CommunityComment; authorName: string }[] }>(
    `/api/v1/community/posts/${postId}/comments`,
  ).then((r) => r.comments);

export const addCommunityComment = (postId: string, body: string) =>
  apiFetch<{ success: boolean; comment: CommunityComment }>(`/api/v1/community/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  }).then((r) => r.comment);

// Real idempotent like/unlike toggle -- same shape lib/eats.ts's own favorite toggle
// already established.
export const toggleCommunityLike = (postId: string) =>
  apiFetch<{ success: boolean; liked: boolean }>(`/api/v1/community/posts/${postId}/like`, {
    method: 'POST',
  }).then((r) => r.liked);
