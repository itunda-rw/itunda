// Extracted from BankDashboard.tsx (2026-08-30, project_itunda_architecture_vs_toss.md
// rec 4). One external call site (GroupsList). Deeply stateful (~25 useState hooks
// for one cohesive chat screen) with no safe sub-split available -- moved verbatim.

import { useEffect, useRef, useState } from 'react';
import { IconBack, IconSend } from './icons/ItundaIcons';
import { CameraGlyph, PinGlyph } from './icons/ItundaFaceMisc';
import { SmileySlight } from './icons/ItundaFaceSmileys';
import { EmojiPicker, renderTextWithEmoji } from './icons/ItundaFaceEmoji';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import { uploadFile } from './lib/upload';
import { chatMessageTime, shouldShowChatTimestamp, MessageReactions, EmoticonBubble } from './TalkBubbles';
import { EmoticonPickerPanel, EmoticonStoreModal } from './TalkEmoticonStore';
import { MediaGalleryModal, ForwardPickerModal, ThreadModal } from './TalkModals';
import { applyMention, MentionSuggestions } from './TalkMentions';
import { TalkLinksModal } from './TalkLinksTab';
import { TalkGroupAnnouncementPoll } from './TalkGroupAnnouncementPoll';
import { TalkGroupToolbar } from './TalkGroupToolbar';
import { extractLinks } from './lib/talk';
import { GroupSplitBillsView } from './GroupSplitBillsView';
import { GroupManageMembersView } from './GroupManageMembersView';
import { fetchEmoticonImageMap, sendGroupEmoticon } from './lib/emoticons';
import { connectMessagingSocket, type MessagingSocketHandle } from './lib/messaging';
import {
  deleteGroupMessage, fetchGroupMembers, fetchGroupMessages, fetchGroupThread,
  fetchPinnedGroupMessage, forwardGroupMessage, pinGroupMessage, sendGroupMessage, toggleGroupReaction, unpinGroupMessage,
  type GroupMember, type GroupMessage, type GroupSummary,
} from './lib/groupMessaging';
import { useDeferredLoading } from './useDeferredLoading';

export function GroupThread({ group, onBack }: { group: GroupSummary; onBack: () => void }) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<GroupMessage[] | null>(null);
  // Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
  // see TalkGroupToolbar.tsx's own doc comment.
  const [searchResults, setSearchResults] = useState<GroupMessage[] | null>(null);
  const showSkeleton = useDeferredLoading(messages === null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<GroupMessage | null>(null);
  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const [forwardingMessage, setForwardingMessage] = useState<GroupMessage | null>(null);
  // Real Thread support (2026-08-05) -- see ConversationThread's own identical state.
  const [threadRootMessage, setThreadRootMessage] = useState<GroupMessage | null>(null);
  // Real group-chat pin (2026-07-26) -- see GroupMessagingService.setPinnedMessage's
  // own doc comment; mirrors ConversationThread's own identical 1:1 state.
  const [pinnedMessage, setPinnedMessage] = useState<GroupMessage | null>(null);
  const [updatingPin, setUpdatingPin] = useState(false);
  const [sending, setSending] = useState(false);
  const [typingUserIds, setTypingUserIds] = useState<Record<string, boolean>>({});
  // Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14), same as
  // ConversationThread's own identical addition above.
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  // Real split-bill/manage-members (found 2026-07-22 fully built on the backend with
  // zero UI anywhere) -- toggles a sibling view over this same thread.
  const [showSplitBills, setShowSplitBills] = useState(false);
  const [showManageMembers, setShowManageMembers] = useState(false);
  // Real group 공지/투표 (announcement/poll) (itunda Talk redesign, 2026-08-28) --
  // toggles a sibling view over this same thread, same pattern as split-bill above.
  const [showAnnouncementPoll, setShowAnnouncementPoll] = useState(false);
  // Real attach ("+") menu + photo send/gallery -- ports Android TalkScreen.kt's own
  // identical addition (2026-08-04) to bank-mfe. Reuses lib/upload.ts's own uploadFile,
  // already real since 2026-08-01; this is just the Talk-composer wiring.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
  // Real Links tab (itunda Talk redesign, 2026-08-28) -- see TalkLinksTab.tsx's own
  // doc comment, mirrors showMediaGallery's exact shape.
  const [showLinks, setShowLinks] = useState(false);
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const lastTypingSentAt = useRef(0);

  const handleSendPhoto = async (file: File | undefined) => {
    if (!file) return;
    setShowAttachMenu(false);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      const sent = await sendGroupMessage(group.groupId, '', replyingTo?.id, url);
      setMessages((prev) => [...(prev ?? []), sent]);
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingPhoto(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
    }
  };

  const load = () =>
    fetchGroupMessages(group.groupId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  const loadPin = () => fetchPinnedGroupMessage(group.groupId).then(setPinnedMessage).catch(() => {});

  useEffect(() => {
    load();
    loadPin();
    // Real 4s poll as an always-correct fallback, same reasoning as ConversationThread's
    // own identical poll -- kept even with the live socket below.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

  useEffect(() => {
    // Real member list with real resolved display names (2026-07-18), fetched once per
    // thread open -- closes the honest, named limitation this UI carried since group
    // chat first shipped (a truncated sender id instead of a real name).
    fetchGroupMembers(group.groupId).then(setMembers).catch(() => {
      // Real, non-critical -- a failed member-list fetch shouldn't block the thread;
      // bubbles just fall back to a truncated sender id below.
    });
  }, [group.groupId]);

  const nameForSender = (senderId: string) => members.find((m) => m.userId === senderId)?.name ?? senderId.slice(0, 12);

  useEffect(() => {
    // Real WebSocket live delivery for group chat (2026-07-18) -- same real push
    // GroupMessagingService.sendMessage fans out to every other real member.
    const socket = connectMessagingSocket((payload) => {
      if (payload.type === 'typing') {
        if (payload.groupConversationId !== group.groupId) return;
        const userId = payload.userId;
        setTypingUserIds((prev) => ({ ...prev, [userId]: true }));
        if (typingClearTimers.current[userId]) clearTimeout(typingClearTimers.current[userId]);
        typingClearTimers.current[userId] = setTimeout(() => {
          setTypingUserIds((prev) => {
            const next = { ...prev };
            delete next[userId];
            return next;
          });
        }, 3000);
        return;
      }
      if (payload.type === 'reaction') {
        if (payload.groupConversationId !== group.groupId) return;
        setMessages((prev) => prev?.map((m) => (m.id === payload.messageId ? { ...m, reactions: payload.reactions } : m)) ?? prev);
        return;
      }
      if (payload.type === 'group_read_receipt') {
        // Real live read-receipt countdown (2026-07-26) -- see
        // RealtimeMessagePublisher.publishGroupReadReceiptChange's own doc comment. The
        // server is the source of truth for each message's exact remaining unreadCount;
        // simplest correct client response is a real refetch, not a local guess.
        if (payload.groupConversationId !== group.groupId) return;
        load();
        return;
      }
      if (payload.type !== 'group_message' || payload.groupConversationId !== group.groupId) return;
      setTypingUserIds((prev) => {
        if (!(payload.message.senderId in prev)) return prev;
        const next = { ...prev };
        delete next[payload.message.senderId];
        return next;
      });
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
      if (payload.message.senderId !== currentUser?.id) {
        setLiveAnnouncement(`New message from ${nameForSender(payload.message.senderId)}: ${payload.message.body || 'sent an attachment'}`);
      }
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
      Object.values(typingClearTimers.current).forEach(clearTimeout);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    setError(null);
    try {
      const sent = await sendGroupMessage(group.groupId, body, replyingTo?.id);
      setMessages((prev) => [...(prev ?? []), sent]);
      setDraft('');
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSending(false);
    }
  };

  const handleToggleReaction = async (groupMessageId: string, emoji: string) => {
    try {
      const reactions = await toggleGroupReaction(groupMessageId, emoji);
      setMessages((prev) => prev?.map((m) => (m.id === groupMessageId ? { ...m, reactions } : m)) ?? prev);
    } catch {
      // Best-effort -- a failed reaction toggle just leaves the badge as it was, never
      // blocks the thread.
    }
  };

  const handlePin = async (message: GroupMessage) => {
    setUpdatingPin(true);
    try {
      await pinGroupMessage(group.groupId, message.id);
      setPinnedMessage(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinGroupMessage(group.groupId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try { await deleteGroupMessage(group.groupId, messageId); load(); }
    catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardGroupMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleCopy = (body: string) => {
    navigator.clipboard?.writeText(body).catch(() => {});
  };

  // Real KakaoTalk Emoticon Store, group-send side (item 133) -- see
  // lib/emoticons.ts's sendGroupEmoticon doc comment. 1:1 chat has had this since the
  // Emoticon Store shipped; group chat never got a client for the identical, already-
  // real backend endpoint. Found 2026-07-29 via the defined-but-uncalled-method sweep.
  const [emoticonPickerOpen, setEmoticonPickerOpen] = useState(false);
  const [emoticonStoreOpen, setEmoticonStoreOpen] = useState(false);
  const [emoticonImageById, setEmoticonImageById] = useState<Record<string, string>>({});
  // Real itundaface emoji picker -- see the 1:1-thread composer's identical
  // addition and icons/ItundaFaceEmoji.tsx's own doc comment.
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);

  useEffect(() => {
    fetchEmoticonImageMap().then(setEmoticonImageById).catch(() => {});
  }, []);

  const handleSendGroupEmoticon = async (emoticonId: string) => {
    setError(null);
    try {
      await sendGroupEmoticon(group.groupId, emoticonId);
      setEmoticonPickerOpen(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  if (showSplitBills) {
    return (
      <GroupSplitBillsView
        groupConversationId={group.groupId}
        members={members}
        currentUserId={currentUser?.id ?? null}
        onBack={() => setShowSplitBills(false)}
      />
    );
  }
  if (showManageMembers) {
    return (
      <GroupManageMembersView
        group={group}
        members={members}
        currentUserId={currentUser?.id ?? null}
        onMembersChanged={() => fetchGroupMembers(group.groupId).then(setMembers).catch(() => {})}
        onLeft={() => { setShowManageMembers(false); onBack(); }}
        onBack={() => setShowManageMembers(false)}
      />
    );
  }
  if (showAnnouncementPoll) {
    return <TalkGroupAnnouncementPoll groupId={group.groupId} onBack={() => setShowAnnouncementPoll(false)} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', marginBottom: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversations">
            <IconBack size={20} />
          </button>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{group.name}</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{group.memberCount} members</p>
          </div>
        </div>
        <TalkGroupToolbar
          groupId={group.groupId}
          onShowMediaGallery={() => setShowMediaGallery(true)}
          onShowLinks={() => setShowLinks(true)}
          onShowManageMembers={() => setShowManageMembers(true)}
          onShowAnnouncementPoll={() => setShowAnnouncementPoll(true)}
          onShowSplitBills={() => setShowSplitBills(true)}
          onSearchResultsChange={setSearchResults}
        />
      </div>

      {showMediaGallery && (
        <MediaGalleryModal
          imageUrls={(messages ?? []).map((m) => m.imageUrl).filter((u): u is string => !!u).reverse()}
          onClose={() => setShowMediaGallery(false)}
        />
      )}
      {showLinks && (
        <TalkLinksModal links={extractLinks((messages ?? []).map((m) => m.body))} onClose={() => setShowLinks(false)} />
      )}

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--itunda-grey-100)', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          <span aria-hidden="true" style={{ display: 'inline-flex' }}><PinGlyph size={14} /></span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-600)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px' }}>
        {messages === null && showSkeleton && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {(searchResults ?? messages)?.map((m, index, list) => {
          const isMine = m.senderId === currentUser?.id;
          // Never collapsed when showing search hits -- same reasoning as
          // ConversationThread's own identical 1:1 search.
          const showTimestamp = searchResults != null || shouldShowChatTimestamp(list, index);
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {!isMine && (
                <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '2px', marginLeft: '4px' }}>
                  {nameForSender(m.senderId)}
                </span>
              )}
              {/* Real message forwarding (2026-07-25) -- see lib/messaging.ts's own
                  doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
              )}
              {m.emoticonId ? (
                <EmoticonBubble imageUrl={emoticonImageById[m.emoticonId]} />
              ) : m.imageUrl ? (
                // Real photo message -- ports Android TalkScreen.kt's own identical
                // addition (2026-08-04) to bank-mfe.
                <img src={m.imageUrl} alt="Shared photo" style={{ maxWidth: '220px', borderRadius: '16px' }} />
              ) : (
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '10px 14px',
                    borderRadius: '16px',
                    fontSize: 'var(--itunda-type-scale-14-size)',
                    backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                    color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                  }}
                >
                  {renderTextWithEmoji(m.body)}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Reply</button>
              {!m.imageUrl && <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Copy</button>}
              {!(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Forward</button>}
              {isMine && !(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              {(showTimestamp || (isMine && m.unreadCount > 0)) && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                  {/* Real Kakao-style read-receipt countdown -- see
                      GroupMessagingService.getUnreadCounts's own doc comment. Only shown
                      on my own messages, same convention 1:1's own "1" indicator uses;
                      disappears at 0, exactly matching real KakaoTalk. */}
                  {[isMine && m.unreadCount > 0 ? `${m.unreadCount}` : null, showTimestamp ? chatMessageTime(m.sentAt) : null].filter(Boolean).join(' · ')}
                </span>
              )}
              {/* Real Thread support (2026-08-05) -- see ConversationThread's own
                  identical affordance. */}
              {!!m.replyCount && (
                <button
                  type="button"
                  onClick={() => setThreadRootMessage(m)}
                  style={{ border: 'none', background: 'none', color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, padding: '4px 0' }}
                >
                  {m.replyCount} {m.replyCount === 1 ? 'reply' : 'replies'} →
                </button>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      {forwardingMessage && <ForwardPickerModal onForward={handleForward} onClose={() => setForwardingMessage(null)} />}
      {threadRootMessage && (
        <ThreadModal
          rootMessage={threadRootMessage}
          currentUserId={currentUser?.id}
          fetchThreadMessages={() => fetchGroupThread(group.groupId, threadRootMessage.id)}
          onSend={(body) => sendGroupMessage(group.groupId, body, threadRootMessage.id)}
          onClose={() => { setThreadRootMessage(null); load(); }}
        />
      )}

      {/* Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14) -- same
          new-message live region as ConversationThread's own identical addition. */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">{liveAnnouncement}</div>
      {Object.keys(typingUserIds).length > 0 && (
        <p aria-live="polite" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {Object.keys(typingUserIds).map(nameForSender).join(', ')} {Object.keys(typingUserIds).length === 1 ? 'is' : 'are'} typing…
        </p>
      )}

      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      {emoticonPickerOpen && (
        <EmoticonPickerPanel onSend={handleSendGroupEmoticon} onOpenStore={() => setEmoticonStoreOpen(true)} />
      )}
      {emoticonStoreOpen && <EmoticonStoreModal onClose={() => setEmoticonStoreOpen(false)} />}

      {replyingTo && <div style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', padding: '8px', borderLeft: '3px solid var(--itunda-indigo)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" aria-label="Cancel reply" onClick={() => setReplyingTo(null)}>×</button></div>}
      <MentionSuggestions draft={draft} members={members} currentUserId={currentUser?.id} onPick={(name) => setDraft((d) => applyMention(d, name))} />
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {emojiPickerOpen && <EmojiPicker onPick={(emoji) => setDraft((d) => d + emoji)} />}
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            Kakao's own real "+"-opens-a-menu pattern (References table: "'+' opens a
            multi-function attach menu"). */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--itunda-white)', border: '1px solid var(--itunda-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}><CameraGlyph size={16} /> Photo</span>
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmojiPickerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <SmileySlight size={16} /> Emoji
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              😊 Emoticon
            </button>
          </div>
        )}
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            const now = Date.now();
            if (now - lastTypingSentAt.current > 2000) {
              lastTypingSentAt.current = now;
              socketRef.current?.sendTyping({ groupConversationId: group.groupId });
            }
          }}
          placeholder="Message"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" aria-label="Send message" className="itunda-btn itunda-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <IconSend size={16} />
        </button>
      </form>
    </div>
  );
}
