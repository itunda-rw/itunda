import { apiFetch, getToken, BASE_URL } from './api';
import type { GroupMessage } from './groupMessaging';

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
  quiet: boolean;
  pinnedMessageId: string | null;
  // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
  // .archived's own doc comment. Same private-to-me model as quiet.
  archived: boolean;
  // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) -- see backend
  // MessagingController's own doc comment distinguishing this from the existing
  // per-message pin (pinnedMessageId above). Was already returned by
  // GET /conversations for every summary; this was the missing client-side field.
  pinnedToTop: boolean;
}
export interface TalkContact { userId: string; name: string }

// Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own doc
// comment for the real toggle semantics (tapping an active reaction removes it).
export interface ReactionGroup {
  emoji: string;
  userIds: string[];
}

export interface Message {
  id: string;
  conversationId: string;
  senderId: string;
  body: string;
  sentAt: string;
  readAt: string | null;
  deletedAt?: string | null;
  replyToMessageId: string | null;
  reactions: ReactionGroup[];
  // Real message forwarding (2026-07-25) -- see backend Message.kt's own doc comment.
  // Only ever set on a message that was actually created via the forward endpoint,
  // never a client-asserted label.
  forwardedFromMessageId?: string | null;
  forwardedFromType?: 'DIRECT' | 'GROUP' | null;
  // Real KakaoTalk Emoticon Store (item 133) -- set only on a message actually sent
  // via EmoticonController's /send endpoints, never a client-asserted label. See
  // lib/emoticons.ts's own doc comment.
  emoticonId?: string | null;
  // Real photo message -- ports Android TalkScreen.kt's own identical addition
  // (2026-08-04) to bank-mfe. Set only on a message sent with a real uploaded photo
  // (POST /api/v1/uploads -> imageUrl passed to sendMessage), never client-asserted.
  imageUrl?: string | null;
  // Real Thread support (2026-08-05) -- closes docs/DESIGN_REFERENCES.md Talk section
  // recommendation #3's remaining gap: Kakao's confirmed 2025 toolkit includes a real
  // reply-expands-into-its-own-sub-conversation view. A real, read-time-computed count
  // of direct replies to this message (0 for a message no one has replied to).
  replyCount?: number;
}

export const fetchConversations = (archived = false) =>
  apiFetch<{ success: boolean; conversations: ConversationSummary[] }>(
    `/api/v1/messages/conversations?archived=${archived}`,
  ).then((r) => r.conversations);

export const fetchTalkContacts = () =>
  apiFetch<{ success: boolean; contacts: TalkContact[] }>('/api/v1/messages/contacts').then((r) => r.contacts);

// Real KakaoTalk-style "오늘의 생일" (Today's Birthday) (2026-08-17) -- see backend
// MessagingService.getTodaysBirthdays's own doc comment.
export const fetchTodaysBirthdays = () =>
  apiFetch<{ success: boolean; contacts: TalkContact[] }>('/api/v1/messages/contacts/birthdays-today').then((r) => r.contacts);

// Real phone-number-based start -- the human-friendly entry point
// MessagingService.startOrGetConversationByPhoneNumber added specifically for this UI
// (a user only ever knows someone else's phone number, never their internal user id).
export const startConversation = (phoneNumber: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>('/api/v1/messages/conversations', {
    method: 'POST',
    body: JSON.stringify({ phoneNumber }),
  }).then((r) => r.conversation);

export const startConversationWithUser = (otherUserId: string) =>
  apiFetch<{ success: boolean; conversation: { id: string } }>('/api/v1/messages/conversations', {
    method: 'POST', body: JSON.stringify({ otherUserId }),
  }).then((r) => r.conversation);

// Newest-first from the real API (matches MessagingService.getMessages -- also what
// marks the other participant's messages as read on this same call); reversed for
// display so the chat thread renders oldest-to-newest top-to-bottom, the normal reading
// order every real chat UI uses.
export const fetchMessages = (conversationId: string) =>
  apiFetch<{ success: boolean; messages: Message[] }>(`/api/v1/messages/conversations/${conversationId}/messages`).then(
    (r) => [...r.messages].reverse(),
  );

export const searchConversationMessages = (conversationId: string, query: string) =>
  apiFetch<{ success: boolean; messages: Message[] }>(`/api/v1/messages/conversations/${conversationId}/messages/search?query=${encodeURIComponent(query)}`)
    .then((r) => r.messages);

// Real Thread support (2026-08-05) -- root message first, then every direct reply
// oldest-first, matching a real thread screen's own natural render order.
export const fetchThread = (conversationId: string, messageId: string) =>
  apiFetch<{ success: boolean; messages: Message[] }>(`/api/v1/messages/conversations/${conversationId}/messages/${messageId}/thread`)
    .then((r) => r.messages);

export const sendMessage = (conversationId: string, body: string, replyToMessageId?: string, imageUrl?: string) =>
  apiFetch<{ success: boolean; message: Message }>(`/api/v1/messages/conversations/${conversationId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body, replyToMessageId, imageUrl }),
  }).then((r) => r.message);

export const deleteMessage = (conversationId: string, messageId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/conversations/${conversationId}/messages/${messageId}`, { method: 'DELETE' });

// Real message forwarding (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Talk
// section recommendation #3. See backend MessageForwardService's own doc comment for
// the full account -- the real source message is always resolved server-side, this
// call never sends a body, only where to forward it. destinationType: 'GROUP' targets
// a group by id instead of a 1:1 conversation.
export const forwardMessage = (messageId: string, destinationType: 'DIRECT' | 'GROUP', destinationId: string) =>
  apiFetch<{ success: boolean; destinationType: 'DIRECT' | 'GROUP' }>(`/api/v1/messages/messages/${messageId}/forward`, {
    method: 'POST',
    body: JSON.stringify({ destinationType, destinationId }),
  });

export const blockConversationParticipant = (conversationId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/conversations/${conversationId}/block`, { method: 'POST' });

export const unblockConversationParticipant = (conversationId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/conversations/${conversationId}/block`, { method: 'DELETE' });

export const pinConversationMessage = (conversationId: string, messageId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/conversations/${conversationId}/pin/${messageId}`, { method: 'POST' });

export const unpinConversationMessage = (conversationId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/conversations/${conversationId}/pin`, { method: 'DELETE' });

export const fetchPinnedConversationMessage = (conversationId: string) =>
  apiFetch<{ success: boolean; message: Message | null }>(`/api/v1/messages/conversations/${conversationId}/pin`).then((r) => r.message);

export const fetchConversationQuiet = (conversationId: string) =>
  apiFetch<{ success: boolean; quiet: boolean }>(`/api/v1/messages/conversations/${conversationId}/quiet`).then((r) => r.quiet);

export const setConversationQuiet = (conversationId: string, quiet: boolean) =>
  apiFetch<{ success: boolean; quiet: boolean }>(`/api/v1/messages/conversations/${conversationId}/quiet`, {
    method: 'POST', body: JSON.stringify({ quiet }),
  }).then((r) => r.quiet);

export const fetchConversationArchived = (conversationId: string) =>
  apiFetch<{ success: boolean; archived: boolean }>(`/api/v1/messages/conversations/${conversationId}/archive`).then((r) => r.archived);

export const setConversationArchived = (conversationId: string, archived: boolean) =>
  apiFetch<{ success: boolean; archived: boolean }>(`/api/v1/messages/conversations/${conversationId}/archive`, {
    method: 'POST', body: JSON.stringify({ archived }),
  }).then((r) => r.archived);

// Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) -- backend
// (MessagingController POST/GET .../pin-to-top, MessagingService.setConversationPinnedToTop)
// was fully built with zero client anywhere; found via a fresh uncalled-endpoint sweep.
// Named setConversationPinnedToTop (not setConversationPinned) to stay distinct from the
// existing per-message pinConversationMessage/unpinConversationMessage above.
export const fetchConversationPinnedToTop = (conversationId: string) =>
  apiFetch<{ success: boolean; pinned: boolean }>(`/api/v1/messages/conversations/${conversationId}/pin-to-top`).then((r) => r.pinned);

export const setConversationPinnedToTop = (conversationId: string, pinned: boolean) =>
  apiFetch<{ success: boolean; pinned: boolean }>(`/api/v1/messages/conversations/${conversationId}/pin-to-top`, {
    method: 'POST', body: JSON.stringify({ pinned }),
  }).then((r) => r.pinned);

export const reportChatMessage = (messageId: string, reason: string) =>
  apiFetch<{ success: boolean }>('/api/v1/chat/reports', {
    method: 'POST', body: JSON.stringify({ messageId, reason }),
  });

// Real toggle -- tapping an already-active reaction removes it (matches
// MessagingService.toggleReaction's own toggle-off semantics), not add/remove as two
// separate calls.
export const toggleReaction = (messageId: string, emoji: string) =>
  apiFetch<{ success: boolean; reactions: ReactionGroup[] }>(`/api/v1/messages/messages/${messageId}/reactions`, {
    method: 'POST',
    body: JSON.stringify({ emoji }),
  }).then((r) => r.reactions);

interface MessagePushPayload {
  type: 'message';
  conversationId: string;
  message: Message;
}

interface GroupMessagePushPayload {
  type: 'group_message';
  groupConversationId: string;
  message: GroupMessage;
}

// Real online/offline presence push (2026-07-19) -- see
// rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
// comment for the real transition-only/1:1-only scoping.
interface PresencePushPayload {
  type: 'presence';
  userId: string;
  online: boolean;
}

// Real typing indicator (2026-07-19) -- see MessagingWebSocketHandler.handleTextMessage's
// own doc comment on the backend. Ephemeral, never persisted; ratelimited server-side to
// one relay per (user, conversation) per 2s.
interface TypingPushPayload {
  type: 'typing';
  conversationId?: string;
  groupConversationId?: string;
  userId: string;
}

// Real live reaction push (2026-07-19) -- see
// MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
// doc comments. Exactly one of conversationId/groupConversationId is set, matching
// MessagePushPayload/GroupMessagePushPayload's own shape.
interface ReactionPushPayload {
  type: 'reaction';
  conversationId?: string;
  groupConversationId?: string;
  messageId: string;
  reactions: ReactionGroup[];
}

// Real group-chat read-receipt push (2026-07-26) -- see
// RealtimeMessagePublisher.publishGroupReadReceiptChange's own doc comment. Fired
// whenever any real member's read cursor advances; the client's own response is to
// just refetch the thread (the server is the source of truth for each message's real
// remaining unreadCount, not something worth recomputing client-side from this alone).
interface GroupReadReceiptPushPayload {
  type: 'group_read_receipt';
  groupConversationId: string;
  readByUserId: string;
  lastReadAt: string;
}

type SocketPushPayload =
  | MessagePushPayload
  | GroupMessagePushPayload
  | PresencePushPayload
  | TypingPushPayload
  | ReactionPushPayload
  | GroupReadReceiptPushPayload;

// Real on-demand presence check, for any set of user ids (a group thread's members,
// or a 1:1 partner not covered by the real-time push above).
export const fetchPresence = (userIds: string[]) => {
  if (userIds.length === 0) return Promise.resolve({} as Record<string, boolean>);
  const params = new URLSearchParams();
  userIds.forEach((id) => params.append('userIds', id));
  return apiFetch<{ success: boolean; presence: Record<string, boolean> }>(`/api/v1/messages/presence?${params.toString()}`).then(
    (r) => r.presence,
  );
};

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
export interface MessagingSocketHandle {
  close: () => void;
  // Real typing indicator send (2026-07-19) -- best-effort, silently a no-op if the
  // socket never connected or has since closed (the same "never a single point of
  // failure" discipline this whole live-transport layer already follows).
  sendTyping: (target: { conversationId?: string; groupConversationId?: string }) => void;
}

export function connectMessagingSocket(onMessage: (payload: SocketPushPayload) => void): MessagingSocketHandle {
  let socket: WebSocket | undefined;
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined;
  let closedByCaller = false;
  let reconnectAttempt = 0;

  const scheduleReconnect = () => {
    if (closedByCaller || reconnectTimer) return;
    // Exponential backoff with bounded jitter prevents every open chat screen from
    // reconnecting at once after a deploy, network switch, or upstream restart. REST
    // polling remains the correctness path while this best-effort channel recovers.
    const capMs = 30_000;
    const baseMs = Math.min(1_000 * 2 ** reconnectAttempt, capMs);
    const jitterMs = Math.floor(Math.random() * Math.max(1, Math.floor(baseMs * 0.25)));
    reconnectAttempt += 1;
    reconnectTimer = setTimeout(() => {
      reconnectTimer = undefined;
      connect();
    }, baseMs + jitterMs);
  };

  const connect = () => {
    const token = getToken();
    if (!token || closedByCaller) return;

    const wsUrl = `${BASE_URL.replace(/^http/, 'ws')}/ws/messaging?token=${encodeURIComponent(token)}`;
    const nextSocket = new WebSocket(wsUrl);
    socket = nextSocket;
    nextSocket.addEventListener('open', () => {
      reconnectAttempt = 0;
    });
    nextSocket.addEventListener('message', (event) => {
      try {
        const payload = JSON.parse(event.data) as SocketPushPayload;
        if (payload.type === 'message' || payload.type === 'group_message' || payload.type === 'presence' || payload.type === 'typing' || payload.type === 'reaction' || payload.type === 'group_read_receipt') {
          onMessage(payload);
        }
      } catch {
        // Malformed/unexpected frame -- ignore, the poll fallback still covers delivery.
      }
    });
    nextSocket.addEventListener('close', scheduleReconnect);
    nextSocket.addEventListener('error', () => nextSocket.close());
  };

  connect();

  return {
    close: () => {
      closedByCaller = true;
      if (reconnectTimer) clearTimeout(reconnectTimer);
      reconnectTimer = undefined;
      socket?.close();
    },
    sendTyping: (target) => {
      if (socket?.readyState === WebSocket.OPEN) socket.send(JSON.stringify({ type: 'typing', ...target }));
    },
  };
}
