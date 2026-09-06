import { apiFetch } from './api';
import type { ReactionGroup } from './messaging';

// Real group chat (2026-07-18) -- the single most defining KakaoTalk capability the
// original 1:1-only pair in messaging.ts didn't cover, added at the user's direct
// request ("Talk should be 100% like KakaoTalk + 당근 채팅 for Rwanda"). See
// GroupMessagingService.kt's own doc comment for the full backend account. Extracted
// out of messaging.ts (2026-09-06, Talk product-completeness pass) once that file
// crossed the 500-line guideline -- the exact 1:1-vs-group boundary this codebase
// already used to split its own backend/UI/native-client files for this same feature.

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

// Real Thread support (2026-08-05) -- see messaging.ts's fetchThread's own doc comment.
export const fetchGroupThread = (groupId: string, messageId: string) =>
  apiFetch<{ success: boolean; messages: GroupMessage[] }>(`/api/v1/messages/groups/${groupId}/messages/${messageId}/thread`)
    .then((r) => r.messages);

// Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
// GroupMessagingController.searchMessages was added the same pass this client was;
// see MessagingController.searchMessages's own doc comment for the 1:1 equivalent.
export const searchGroupMessages = (groupId: string, query: string) =>
  apiFetch<{ success: boolean; messages: GroupMessage[] }>(`/api/v1/messages/groups/${groupId}/messages/search?query=${encodeURIComponent(query)}`)
    .then((r) => r.messages);

export const sendGroupMessage = (groupId: string, body: string, replyToMessageId?: string, imageUrl?: string) =>
  apiFetch<{ success: boolean; message: GroupMessage }>(`/api/v1/messages/groups/${groupId}/messages`, {
    method: 'POST',
    body: JSON.stringify({ body, replyToMessageId, imageUrl }),
  }).then((r) => r.message);

// Real message forwarding (2026-07-25) -- see messaging.ts's forwardMessage's own doc
// comment above; identical shape, this message is always the real GROUP source instead.
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
// doc comment; mirrors messaging.ts's pinConversationMessage/unpinConversationMessage/
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
