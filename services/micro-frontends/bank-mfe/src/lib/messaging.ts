import { apiFetch, getToken, BASE_URL } from './api';

// Real 1:1 messaging (rw.itunda.messaging, 2026-07-18) -- the Kakao-style chat primitive
// named in the "super app" goal expansion, picked as the first of the three new phases
// since both Coupang-style support chat and 당근마켓-style buyer/seller negotiation would
// eventually need this same primitive. First real UI touchpoint for this endpoint --
// see MessagingService.kt's own doc comment for the full backend account, including the
// honest "poll-based delivery, no live transport yet" scope.

export interface ConversationSummary {
  conversationId: string;
  otherUserId: string;
  otherUserName: string;
  lastMessageAt: string;
  lastMessagePreview: string | null;
  unreadCount: number;
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  body: string;
  sentAt: string;
  readAt: string | null;
}

export const fetchConversations = () =>
  apiFetch<{ success: boolean; conversations: ConversationSummary[] }>('/api/v1/messages/conversations').then(
    (r) => r.conversations,
  );

// Real phone-number-based start -- the human-friendly entry point
// MessagingService.startOrGetConversationByPhoneNumber added specifically for this UI
// (a user only ever knows someone else's phone number, never their internal user id).
export const startConversation = (phoneNumber: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>('/api/v1/messages/conversations', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  }).then((r) => r.conversation);

// Newest-first from the real API (matches MessagingService.getMessages -- also what
// marks the other participant's messages as read on this same call); reversed for
// display so the chat thread renders oldest-to-newest top-to-bottom, the normal reading
// order every real chat UI uses.
export const fetchMessages = (conversationId: string) =>
  apiFetch<{ success: boolean; messages: Message[] }>(`/api/v1/messages/conversations/${conversationId}/messages`).then(
    (r) => [...r.messages].reverse(),
  );

export const sendMessage = (conversationId: string, body: string) =>
  apiFetch<{ success: boolean; message: Message }>(`/api/v1/messages/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body }),
  }).then((r) => r.message);

interface MessagePushPayload {
  type: 'message';
  conversationId: string;
  message: Message;
}

// Real WebSocket live-transport (2026-07-18) -- see
// rw.itunda.core.realtime.RealtimeMessagePublisher's own doc comment for the full
// backend account. The token travels as a query param (a native WebSocket can't set a
// custom Authorization header on its handshake request) -- same real JWT the REST API
// already trusts, verified server-side before the upgrade completes.
//
// Deliberately best-effort: if there's no token, or the socket errors/closes, this
// silently does nothing further -- callers are expected to keep their existing 4s poll
// running regardless, so a live push is a real latency improvement layered on top of an
// always-correct fallback, never a single point of failure for message delivery.
export function connectMessagingSocket(onMessage: (payload: MessagePushPayload) => void): () => void {
  const token = getToken();
  if (!token) return () => {};

  const wsUrl = `${BASE_URL.replace(/^http/, 'ws')}/ws/messaging?token=${encodeURIComponent(token)}`;
  const socket = new WebSocket(wsUrl);

  socket.addEventListener('message', (event) => {
    try {
      const payload = JSON.parse(event.data) as MessagePushPayload;
      if (payload.type === 'message') onMessage(payload);
    } catch {
      // Malformed/unexpected frame -- ignore, the poll fallback still covers delivery.
    }
  });

  return () => socket.close();
}
