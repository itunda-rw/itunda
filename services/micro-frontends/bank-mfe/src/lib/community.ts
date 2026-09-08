import { apiFetch } from './api';
import { randomUUID } from './uuid';

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
  // Real 동네생활 topic chip (2026-08-28) -- a lifestyle axis independent of the
  // functional category above, see backend CommunityPost.topic's own doc comment.
  topic?: string | null;
  // Real AI-generated 모임 summary (2026-08-28) -- see backend HoodAiSummaryService's
  // own doc comment. Null means either not a meetup post or the honesty gate declined
  // (thin body) -- never fabricate a summary client-side when this is null.
  aiSummary?: string | null;
  aiSummaryGeneratedAt?: string | null;
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

// Real 동네생활 topic-chip filter row (2026-08-28) -- see backend
// CommunityService.TOPICS' own doc comment. Same {id,label} shape as categories above.
export const fetchCommunityTopics = () =>
  apiFetch<{ success: boolean; topics: CommunityCategory[] }>('/api/v1/community/topics').then((r) => r.topics);

// Real 같이해요 (join-together) group join counts (2026-07-24) -- postId -> real
// member count of that meetup's group chat, closing docs/DESIGN_REFERENCES.md
// Section 4 recommendation #4.
export type JoinedCounts = Record<string, number>;

// Real pagination-discard fix (named as the systemic sibling of the Knowledge
// gap fixed in 134758cf/30741887/a2dc88dc -- CommunityController's real
// Pageable/pageMeta endpoints were always there; page/size just weren't sent,
// silently capping every Hood feed at its first 20 posts).
export const fetchCommunityPosts = (category?: string, topic?: string, page = 0, size = 20) => {
  const params = new URLSearchParams();
  if (category) params.set('category', category);
  if (topic) params.set('topic', topic);
  params.set('page', String(page));
  params.set('size', String(size));
  return apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts; page: number; totalPages: number }>(
    `/api/v1/community/posts?${params.toString()}`,
  );
};

// Real 당근모임-style "upcoming meetups" browse (backend 2026-07-25,
// CommunityController.upcomingMeetups) -- unlike fetchCommunityPosts(category:
// 'meetup'), this excludes meetups whose eventDate has already passed and orders
// by soonest eventDate first (backend query: `eventDate > now ORDER BY eventDate
// ASC`), not by post creation time. Found unused by every client (Android had the
// same dead API binding) while auditing the "🎉 Meetups" dedicated-slot feature --
// that slot was built by client-side-filtering the general feed, which can surface
// an already-happened meetup if it was posted recently, and can miss a genuinely
// upcoming one if it's fallen off the loaded page of the general feed.
export const fetchUpcomingMeetups = () =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts }>(
    '/api/v1/community/meetups/upcoming',
  ).then((r) => ({ posts: r.posts, joinedCounts: r.joinedCounts }));

export const fetchMyCommunityPosts = (page = 0, size = 20) =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts; page: number; totalPages: number }>(
    `/api/v1/community/my-posts?page=${page}&size=${size}`,
  );

// Real hyperlocal "my neighborhood" browse (2026-07-20) -- see lib/neighborhood.ts's own
// doc comment. Throws ApiError with code NEIGHBORHOOD_NOT_SET (real 400) if the caller
// hasn't set one yet.
export const fetchCommunityPostsMyNeighborhood = (category?: string, page = 0, size = 20) =>
  apiFetch<{ success: boolean; posts: CommunityPost[]; joinedCounts: JoinedCounts; page: number; totalPages: number }>(
    `/api/v1/community/posts/my-neighborhood?page=${page}&size=${size}${category ? `&category=${encodeURIComponent(category)}` : ''}`,
  );

// Real bug fix, found live while wiring 같이사요 (2026-07-31): this function never
// accepted/sent eventDate/capacity at all, so a real 'meetup' post created from this
// app could never actually succeed (the backend real-400s InvalidMeetupException
// without a real date) -- the only way a meetup post here was ever created was by
// direct API call, never through this real UI path. Fixed the same day it was found,
// same "found live, fixed same day" discipline this codebase already documents
// everywhere else.
export const createCommunityPost = (
  category: string,
  title: string,
  body: string,
  latitude?: number,
  longitude?: number,
  eventDate?: string,
  capacity?: number,
  topic?: string,
) =>
  apiFetch<{ success: boolean; post: CommunityPost }>('/api/v1/community/posts', {
    method: 'POST',
    body: JSON.stringify({ category, title, body, latitude, longitude, eventDate, capacity, topic }),
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

// Real "who attended" view (Hood product-completeness pass, 2026-09-08) -- see backend
// CommunityService.getSessionAttendance's own doc comment: the endpoint returned raw
// MeetupAttendance (userId only, no display name) with zero UI caller anywhere. Mirrors
// the backend's own real MeetupAttendanceWithName response shape.
export interface SessionAttendee {
  attendance: MeetupAttendance;
  userName: string;
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
  apiFetch<{ success: boolean; attendance: SessionAttendee[] }>(`/api/v1/community/sessions/${sessionId}/attendance`).then((r) => r.attendance);

// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see the backend's
// CommunityService.finalizeGroupBuy doc comment. Reuses the already-real SplitBill
// mechanic wholesale for the actual cost-splitting once the organizer fronts the total.
export const finalizeGroupBuy = (postId: string, totalAmount: number, description: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/community/posts/${postId}/finalize-group-buy`, {
    method: 'POST',
    headers: { 'Idempotency-Key': randomUUID() },
    body: JSON.stringify({ totalAmount, description }),
  });

// Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- see
// backend CommunityController's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: real, fully-built account-wide toggle with zero
// client anywhere.
export const fetchCommentNotificationsEnabled = () =>
  apiFetch<{ success: boolean; commentNotificationsEnabled: boolean }>('/api/v1/community/notification-preference')
    .then((r) => r.commentNotificationsEnabled);

export const setCommentNotificationsEnabled = (enabled: boolean) =>
  apiFetch<{ success: boolean; commentNotificationsEnabled: boolean }>('/api/v1/community/notification-preference', {
    method: 'POST',
    body: JSON.stringify({ enabled }),
  }).then((r) => r.commentNotificationsEnabled);

// Real 살아본 후기 (Karrot "lived here" neighborhood reviews), itunda Hood redesign
// 2026-08-28 -- see backend NeighborhoodReview.kt's own doc comment. Distinct from
// HoodReview (a buyer/seller transaction review) -- this is a public review of an
// area, shown on every property listing in that neighborhood.
export interface NeighborhoodReview {
  id: string;
  userId: string;
  neighborhood: string;
  residencyYears?: number | null;
  body: string;
  createdAt: string;
}

export const fetchNeighborhoodReviews = (neighborhood: string) =>
  apiFetch<{ success: boolean; reviews: NeighborhoodReview[] }>(
    `/api/v1/community/neighborhoods/${encodeURIComponent(neighborhood)}/reviews`,
  ).then((r) => r.reviews);

export const submitNeighborhoodReview = (neighborhood: string, body: string, residencyYears?: number) =>
  apiFetch<{ success: boolean; review: NeighborhoodReview }>(
    `/api/v1/community/neighborhoods/${encodeURIComponent(neighborhood)}/reviews`,
    { method: 'POST', body: JSON.stringify({ body, residencyYears }) },
  ).then((r) => r.review);
