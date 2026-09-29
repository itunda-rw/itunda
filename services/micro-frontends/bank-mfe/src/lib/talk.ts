import { apiFetch, ApiError } from './api';
import type { ConversationSummary } from './messaging';

// Real itunda Talk (KakaoTalk-parity) redesign client surface (2026-08-28) -- a new
// sibling file, not an addition to lib/messaging.ts: that file sits at exactly 500
// lines (this codebase's own hard new-file cap), so every genuinely new endpoint this
// redesign adds lives here instead. See docs/DESIGN_REFERENCES.md Talk section for
// the full backend account (favorite toggle, service channel, AI chatbot, group
// announcement/poll, real 1:1 voice/video calling -- calling itself gets its own
// sibling lib/calling.ts, since its WebSocket signaling shape is a distinct concern
// from these plain REST calls).

// ConversationSummary already carries a real `favorite` field from the backend (see
// MessagingService.setConversationFavorite/isConversationFavorite's own doc
// comment) -- messaging.ts's own interface just has no headroom left to declare it.
export type ConversationSummaryWithFavorite = ConversationSummary & { favorite: boolean };

export const setConversationFavorite = (conversationId: string, favorite: boolean) =>
  apiFetch<{ success: boolean; favorite: boolean }>(`/api/v1/messages/conversations/${conversationId}/favorite`, {
    method: 'POST', body: JSON.stringify({ favorite }),
  }).then((r) => r.favorite);

export interface ServiceChannelBubble {
  id: string;
  title: string;
  body: string;
  type: string;
  createdAt: string;
  isRead: boolean;
  ctaRoute: string | null;
}

// Real pagination-discard fix (2026-09-11, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- this real Pageable endpoint's page just wasn't ever sent,
// silently capping the itunda service-channel thread at the most recent
// 20 updates.
export const fetchServiceChannel = (page = 0) =>
  apiFetch<{ success: boolean; bubbles: ServiceChannelBubble[]; page: number; totalPages: number }>(
    `/api/v1/talk/service-channel?page=${page}&size=20`,
  );

export interface AiChatMessage {
  id: string;
  userId: string;
  role: 'user' | 'assistant';
  content: string;
  createdAt: string;
}

// Real pagination-discard fix (2026-09-11, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- this real Pageable endpoint's page just wasn't ever sent,
// silently capping the AI chat thread's scrollback at the most recent 30
// messages with no way to load anything older.
export const fetchAiChatHistory = (page = 0) =>
  apiFetch<{ success: boolean; messages: AiChatMessage[]; page: number; totalPages: number }>(
    `/api/v1/talk/ai-chat/messages?page=${page}&size=30`,
  );

// Real single-flight, honestly-busy AI chatbot (see backend AiChatService's own doc
// comment) -- a 429 here means the shared self-hosted model is busy with someone
// else's request right now, not an error to swallow silently.
export class AiChatBusyError extends Error {}

export const sendAiChatMessage = (text: string) =>
  apiFetch<{ success: boolean; message: AiChatMessage; reply: AiChatMessage }>('/api/v1/talk/ai-chat/messages', {
    method: 'POST', body: JSON.stringify({ text }),
  }).catch((err: unknown) => {
    if (err instanceof ApiError && err.status === 429) throw new AiChatBusyError('itunda AI is busy right now, try again shortly');
    throw err;
  });

export interface GroupAnnouncement { id: string; groupConversationId: string; createdBy: string; body: string; createdAt: string }
export interface GroupPollOption { id: string; pollId: string; text: string }
export interface GroupPoll { id: string; groupConversationId: string; createdBy: string; question: string; allowMultiple: boolean; closesAt: string | null; createdAt: string }
export interface GroupPollWithVotes { poll: GroupPoll; options: GroupPollOption[]; voteCountByOptionId: Record<string, number>; myVoteOptionIds: string[] }

export const fetchGroupAnnouncement = (groupId: string) =>
  apiFetch<{ success: boolean; announcement: GroupAnnouncement | null }>(`/api/v1/messages/groups/${groupId}/announcement`).then((r) => r.announcement);

export const postGroupAnnouncement = (groupId: string, body: string) =>
  apiFetch<{ success: boolean; announcement: GroupAnnouncement }>(`/api/v1/messages/groups/${groupId}/announcement`, {
    method: 'POST', body: JSON.stringify({ body }),
  }).then((r) => r.announcement);

export const fetchGroupPolls = (groupId: string) =>
  apiFetch<{ success: boolean; polls: GroupPollWithVotes[] }>(`/api/v1/messages/groups/${groupId}/polls`).then((r) => r.polls);

export const createGroupPoll = (groupId: string, question: string, options: string[], allowMultiple = false, closesAt?: string) =>
  apiFetch<{ success: boolean; poll: GroupPollWithVotes }>(`/api/v1/messages/groups/${groupId}/polls`, {
    method: 'POST', body: JSON.stringify({ question, options, allowMultiple, closesAt: closesAt ?? null }),
  }).then((r) => r.poll);

export const voteGroupPoll = (groupId: string, pollId: string, optionId: string) =>
  apiFetch<{ success: boolean; poll: GroupPollWithVotes }>(`/api/v1/messages/groups/${groupId}/polls/${pollId}/vote`, {
    method: 'POST', body: JSON.stringify({ optionId }),
  }).then((r) => r.poll);

// Real 1:1 voice/video calling (2026-08-28) -- this is just the read-only call-log
// list (the 통화 chat-list filter tab); actually placing/answering a call over
// WebRTC is a separate, later piece and lives in its own lib/calling.ts once built.
export type CallType = 'VOICE' | 'VIDEO';
export type CallEndReason = 'MISSED' | 'DECLINED' | 'COMPLETED' | 'CANCELLED' | 'FAILED';
export interface CallSession {
  id: string;
  conversationId: string;
  callerId: string;
  calleeId: string;
  callType: CallType;
  startedAt: string;
  answeredAt: string | null;
  endedAt: string | null;
  endReason: CallEndReason | null;
}

// Real pagination-discard fix (2026-09-11, same systemic gap fixed
// throughout the sweep -- see project_itunda_pagination_discard_sweep
// memory) -- CallController's /calls/history was always real
// Pageable/pageMeta-backed; page just wasn't ever sent, silently capping
// the call-log tab at the most recent 30 calls.
export const fetchCallHistory = (page = 0) =>
  apiFetch<{ success: boolean; calls: CallSession[]; page: number; totalPages: number }>(
    `/api/v1/calls/history?page=${page}&size=30`,
  );

// Real Links tab (see TalkLinksTab.tsx's own doc comment) -- pure client-side
// extraction over already-fetched message bodies, no new backend endpoint.
const URL_REGEX = /https?:\/\/[^\s]+/g;

export function extractLinks(bodies: (string | null | undefined)[]): string[] {
  const links: string[] = [];
  for (const body of bodies) {
    if (!body) continue;
    const matches = body.match(URL_REGEX);
    if (matches) links.push(...matches);
  }
  return links.reverse();
}
