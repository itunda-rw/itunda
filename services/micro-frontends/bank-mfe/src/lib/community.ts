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
  // Real hyperlocal neighborhood (2026-07-20), cached at creation time -- see
  // lib/neighborhood.ts's own doc comment for the full account.
  neighborhood?: string | null;
  // Real 같이해요 (join-together) meetup group chat (2026-07-24) -- see backend
  // CommunityPost.kt's own doc comment. Only ever set for category === 'meetup' posts
  // that have had at least one real join.
  groupConversationId?: string | null;
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

// Real 같이해요 (join-together) group join counts (2026-07-24) -- postId -> real
// member count of that meetup's group chat, closing docs/DESIGN_REFERENCES.md
// Section 4 recommendation #4.
export type JoinedCounts = Record<string, number>;

export const fetchCommunityPosts = (category?: string) =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts }>(
    `/api/v1/community/posts${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ posts: r.posts, joinedCounts: r.joinedCounts }));

export const fetchMyCommunityPosts = () =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts }>('/api/v1/community/my-posts')
    .then((r) => ({ posts: r.posts, joinedCounts: r.joinedCounts }));

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment. Throws ApiError with code NEIGHBORHOOD_NOT_SET (real 400) if the caller
// hasn't set one yet.
export const fetchCommunityPostsMyNeighborhood = (category?: string) =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts }>(
    `/api/v1/community/posts/my-neighborhood${category ? `?category=${encodeURIComponent(category)}` : ''}`,
  ).then((r) => ({ posts: r.posts, joinedCounts: r.joinedCounts }));

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

// Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
// CommunityService.joinMeetup's own doc comment.
export const joinCommunityMeetup = (postId: string) =>
  apiFetch<{ success: boolean; groupId: string }>(`/api/v1/community/posts/${postId}/join`, {
    method: 'POST',
  }).then((r) => r.groupId);

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see the
// backend's MeetupSession.kt/MeetupAttendance.kt doc comments.
export interface MeetupSession {
  id: string;
  postId: string;
  sequence: number;
  scheduledFor: string;
  createdAt: string;
}

export interface MeetupAttendance {
  id: string;
  sessionId: string;
  userId: string;
  checkedInAt: string;
}

export const scheduleMeetupSessions = (postId: string, dates: string[]) =>
  apiFetch<{ success: boolean; sessions: MeetupSession[] }>(`/api/v1/community/posts/${postId}/sessions`, {
    method: 'POST',
    body: JSON.stringify({ dates }),
  }).then((r) => r.sessions);

export const fetchMeetupSessions = (postId: string) =>
  apiFetch<{ success: boolean; sessions: MeetupSession[] }>(`/api/v1/community/posts/${postId}/sessions`).then((r) => r.sessions);

export const checkIntoMeetupSession = (sessionId: string) =>
  apiFetch<{ success: boolean; attendance: MeetupAttendance }>(`/api/v1/community/sessions/${sessionId}/check-in`, {
    method: 'POST',
  }).then((r) => r.attendance);

export const fetchSessionAttendance = (sessionId: string) =>
  apiFetch<{ success: boolean; attendance: MeetupAttendance[] }>(`/api/v1/community/sessions/${sessionId}/attendance`).then((r) => r.attendance);
