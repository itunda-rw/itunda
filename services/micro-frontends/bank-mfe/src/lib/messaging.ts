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
  quiet: boolean;
  pinnedMessageId: string | null;
  // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
  // .archived's own doc comment. Same private-to-me model as quiet.
  archived: boolean;
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

// Real group chat (2026-07-18) -- the single most defining KakaoTalk capability the
// original 1:1-only pair above didn't cover, added at the user's direct request
// ("Talk should be 100% like KakaoTalk + 당근 채팅 for Rwanda"). See
// GroupMessagingService.kt's own doc comment for the full backend account.

export interface GroupSummary {
  groupId: string;
  name: string;
  memberCount: number;
  lastMessageAt: string;
  lastMessagePreview: string | null;
  unreadCount: number;
  // Real group photo/description (2026-07-28) -- see GroupMessagingService
  // .setGroupPhotoUrl/setGroupDescription's own doc comments. Found 2026-08-01 via a
  // defined-but-uncalled-endpoint sweep: real on backend since it shipped, zero client
  // anywhere on any of the 3 platforms until now.
  photoUrl?: string | null;
  description?: string | null;
}

export interface GroupMessage {
  id: string;
  groupConversationId: string;
  senderId: string;
  body: string;
  sentAt: string;
  replyToMessageId?: string | null;
  reactions: ReactionGroup[];
  // Real message forwarding (2026-07-25) -- see backend GroupMessage's own doc
  // comment; identical shape to Message.forwardedFromMessageId/forwardedFromType.
  forwardedFromMessageId?: string | null;
  forwardedFromType?: 'DIRECT' | 'GROUP' | null;
  // Real Kakao-style per-message read-receipt countdown (2026-07-26) -- see
  // GroupMessagingService.getUnreadCounts's own doc comment. How many OTHER real
  // members haven't read up through this message yet.
  unreadCount: number;
  // Real KakaoTalk Emoticon Store, group-send side (item 133) -- see
  // lib/emoticons.ts's sendGroupEmoticon doc comment. Set only on a message actually
  // sent via EmoticonController's /groups/{id}/send endpoint. Found 2026-07-29 via the
  // defined-but-uncalled-method sweep: the backend/DTO field existed, but this type
  // never carried it and no group-chat client ever sent one.
  emoticonId?: string | null;
  // Real photo message -- see Message.imageUrl's own doc comment.
  imageUrl?: string | null;
  // Real Thread support (2026-08-05) -- see Message.replyCount's own doc comment.
  replyCount?: number;
}

// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// startConversation's phoneNumber) -- a real UI user only ever knows someone else's
// phone number, never their internal id.
export const createGroup = (name: string, memberPhoneNumbers: string[]) =>
  apiFetch<{ success: boolean; group: { id: string; name: string } }>('/api/v1/messages/groups', {
    method: 'POST',
    body: JSON.stringify({ name, memberPhoneNumbers }),
  }).then((r) => r.group);

// Real KakaoTalk 오픈채팅-style open group -- see backend GroupMessagingService
// .createOpenGroup's own doc comment. Anyone with the returned joinCode can join
// without being invited by phone number first.
export const createOpenGroup = (name: string) =>
  apiFetch<{ success: boolean; group: { id: string; name: string; joinCode: string } }>('/api/v1/messages/groups/open', {
    method: 'POST',
    body: JSON.stringify({ name }),
  }).then((r) => r.group);

export const joinGroupByCode = (joinCode: string) =>
  apiFetch<{ success: boolean; group: { id: string; name: string } }>('/api/v1/messages/groups/join', {
    method: 'POST',
    body: JSON.stringify({ joinCode }),
  }).then((r) => r.group);

export const fetchGroups = () =>
  apiFetch<{ success: boolean; groups: GroupSummary[] }>('/api/v1/messages/groups').then((r) => r.groups);

export const fetchGroupMessages = (groupId: string) =>
  apiFetch<{ success: boolean; messages: GroupMessage[] }>(`/api/v1/messages/groups/${groupId}/messages`).then(
    (r) => [...r.messages].reverse(),
  );

// Real Thread support (2026-08-05) -- see fetchThread's own doc comment.
export const fetchGroupThread = (groupId: string, messageId: string) =>
  apiFetch<{ success: boolean; messages: GroupMessage[] }>(`/api/v1/messages/groups/${groupId}/messages/${messageId}/thread`)
    .then((r) => r.messages);

export const sendGroupMessage = (groupId: string, body: string, replyToMessageId?: string, imageUrl?: string) =>
  apiFetch<{ success: boolean; message: GroupMessage }>(`/api/v1/messages/groups/${groupId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body, replyToMessageId, imageUrl }),
  }).then((r) => r.message);

// Real message forwarding (2026-07-25) -- see forwardMessage's own doc comment above;
// identical shape, this message is always the real GROUP source instead.
export const forwardGroupMessage = (messageId: string, destinationType: 'DIRECT' | 'GROUP', destinationId: string) =>
  apiFetch<{ success: boolean; destinationType: 'DIRECT' | 'GROUP' }>(`/api/v1/messages/groups/messages/${messageId}/forward`, {
    method: 'POST',
    body: JSON.stringify({ destinationType, destinationId }),
  });

export const deleteGroupMessage = (groupId: string, messageId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/groups/${groupId}/messages/${messageId}`, { method: 'DELETE' });

export const toggleGroupReaction = (groupMessageId: string, emoji: string) =>
  apiFetch<{ success: boolean; reactions: ReactionGroup[] }>(`/api/v1/messages/groups/messages/${groupMessageId}/reactions`, {
    method: 'POST',
    body: JSON.stringify({ emoji }),
  }).then((r) => r.reactions);

// Real group-chat pin (2026-07-26) -- see GroupMessagingService.setPinnedMessage's own
// doc comment; mirrors pinConversationMessage/unpinConversationMessage/
// fetchPinnedConversationMessage's exact 1:1 shape.
export const pinGroupMessage = (groupId: string, messageId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/groups/${groupId}/pin/${messageId}`, { method: 'POST' });

export const unpinGroupMessage = (groupId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/groups/${groupId}/pin`, { method: 'DELETE' });

export const fetchPinnedGroupMessage = (groupId: string) =>
  apiFetch<{ success: boolean; message: GroupMessage | null }>(`/api/v1/messages/groups/${groupId}/pin`).then((r) => r.message);

export interface GroupMember {
  userId: string;
  name: string;
}

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
export const fetchGroupMembers = (groupId: string) =>
  apiFetch<{ success: boolean; members: GroupMember[] }>(`/api/v1/messages/groups/${groupId}/members`).then(
    (r) => r.members,
  );

// Real leave-group/add-member (found 2026-07-22 fully built on the backend with zero
// client UI anywhere, despite group chat itself being fully wired).
export const addGroupMember = (groupId: string, userId: string) =>
  apiFetch<{ success: boolean; group: GroupSummary }>(`/api/v1/messages/groups/${groupId}/members`, {
    method: 'POST',
    body: JSON.stringify({ userId }),
  }).then((r) => r.group);

export const leaveGroup = (groupId: string) =>
  apiFetch<{ success: boolean }>(`/api/v1/messages/groups/${groupId}/members/me`, { method: 'DELETE' });

// Real group photo/description (2026-07-28), open to any real member -- same flat-
// membership discipline the pin/leave/add-member actions above already use. A URL, not
// a binary upload (this backend has no file-storage layer); blank clears it.
export const setGroupPhotoUrl = (groupId: string, photoUrl: string) =>
  apiFetch<{ success: boolean; group: GroupSummary }>(`/api/v1/messages/groups/${groupId}/photo`, {
    method: 'POST',
    body: JSON.stringify({ photoUrl }),
  }).then((r) => r.group);

export const setGroupDescription = (groupId: string, description: string) =>
  apiFetch<{ success: boolean; group: GroupSummary }>(`/api/v1/messages/groups/${groupId}/description`, {
    method: 'POST',
    body: JSON.stringify({ description }),
  }).then((r) => r.group);

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
