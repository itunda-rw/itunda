import { apiFetch } from './api';

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
}

export interface JobCategory {
  id: string;
  label: string;
}

export const fetchJobCategories = () =>
  apiFetch<{ success: boolean; categories: JobCategory[] }>('/api/v1/jobs/categories').then((r) => r.categories);

export const fetchJobPosts = (category?: string) =>
  apiFetch<{ success: boolean; posts: JobPost[] }>(
    `/api/v1/jobs/posts${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => r.posts);

export const fetchMyJobPosts = () =>
  apiFetch<{ success: boolean; posts: JobPost[] }>('/api/v1/jobs/my-posts').then((r) => r.posts);

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

export const markJobPostFilled = (jobPostId: string) =>
  apiFetch<{ success: boolean; post: JobPost }>(`/api/v1/jobs/posts/${jobPostId}/mark-filled`, {
    method: 'POST',
  }).then((r) => r.post);

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
