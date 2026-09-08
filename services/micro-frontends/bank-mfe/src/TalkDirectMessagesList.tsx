// Extracted from BankDashboard.tsx (2026-09-03, itunda-vs-Toss architecture comparison
// thread's own open recommendation 4). The 1:1 direct-message list, its own shared row
// renderer/empty-state copy, and the read-only call log its own 통화 filter tab shows --
// verified via real usage grep before bundling (TalkVirtualRow/emptyConversationsMessage/
// TalkCallLog are each used only by DirectMessagesList).
import { useEffect, useState, type ReactElement } from 'react';
import { motion } from 'framer-motion';
import { Archive, ArchiveRestore, Bot, MessageCircle, Phone, Pin, PinOff, Star } from 'lucide-react';
import { itundaSpring } from './lib/motion';
import { useI18n } from './i18n/I18nContext';
import { ApiError, getStoredUser } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { ConversationThread } from './ConversationThread';
import { TalkAiChatThread, TalkServiceChannelThread } from './TalkThreads';
import { isRoomLocked } from './lib/roomSettings';
import {
  fetchConversations, fetchPresence, setConversationArchived, setConversationPinnedToTop,
  type ConversationSummary,
} from './lib/messaging';
import { fetchCallHistory, setConversationFavorite, type CallSession, type ConversationSummaryWithFavorite } from './lib/talk';
import { NewChatCard } from './TalkNewChatCards';

function TalkVirtualRow({ icon, name, preview, onClick }: { icon: ReactElement; name: string; preview: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', background: 'none', border: 'none', cursor: 'pointer' }}
    >
      <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{preview}</p>
      </div>
    </button>
  );
}

function emptyConversationsMessage(showArchived: boolean, filterTab: 'all' | 'unread' | 'calls'): string {
  if (showArchived) return "You haven't archived any chats.";
  if (filterTab === 'unread') return 'No unread chats.';
  return 'No conversations yet — start one from Friends, or say hi to someone you already know.';
}

// Real 1:1 voice/video calling log (itunda Talk redesign, 2026-08-28) -- the 통화
// filter tab's content. Read-only: shows real call history from CallService's own
// persisted CallSession rows. No "place a call" affordance yet -- that's a separate,
// later piece once the calling UI itself (WebRTC/dial screen) is built.
function TalkCallLog({ calls, currentUserId }: { calls: CallSession[] | null; currentUserId: string | null }) {
  const showSkeleton = useDeferredLoading(calls === null);
  if (calls === null) {
    return showSkeleton ? <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }
  if (calls.length === 0) {
    return <EmptyState message="No calls yet." />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {calls.map((call) => {
        const outgoing = call.callerId === currentUserId;
        const missed = call.endReason === 'MISSED' || call.endReason === 'DECLINED';
        let direction = 'Incoming';
        if (outgoing) direction = 'Outgoing';
        else if (missed) direction = 'Missed call';
        return (
          <div key={call.id} style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '14px 0' }}>
            <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Phone size={20} color="var(--itunda-indigo)" />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: missed ? 'var(--itunda-red)' : 'var(--itunda-grey-900)' }}>
                {direction} {call.callType === 'VIDEO' ? 'video' : 'voice'} call
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {new Date(call.startedAt).toLocaleString()}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

export function DirectMessagesList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  const { t } = useI18n();
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const showSkeleton = useDeferredLoading(conversations === null);
  // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
  // .archived's own doc comment. Loaded alongside the active list so the
  // "Archived (N)" toggle has a real count without an extra round-trip.
  const [archivedConversations, setArchivedConversations] = useState<ConversationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real pagination-discard fix (2026-09-09) -- separate page/hasMore state
  // per list (active vs archived), since a user can load more of one while
  // the other stays at its first page.
  const [activePage, setActivePage] = useState(0);
  const [activeHasMore, setActiveHasMore] = useState(false);
  const [archivedPage, setArchivedPage] = useState(0);
  const [archivedHasMore, setArchivedHasMore] = useState(false);
  const [loadingMoreConversations, setLoadingMoreConversations] = useState(false);
  const [openConversationId, setOpenConversationId] = useState<string | null>(null);
  const [presence, setPresence] = useState<Record<string, boolean>>({});
  // Real fix, found live 2026-08-05 (same audit that found the identical bug on
  // Android/iOS): this used to filter on `quiet` (mute) and mislabel the result
  // "Archived" -- there was no real archive concept on the backend yet, so muting
  // had been repurposed to also hide a conversation from the list. Muted
  // conversations now stay visible in the main list (matching real KakaoTalk: muting
  // only silences notifications, it never hides a room); this toggle now shows the
  // real archived list.
  const [showArchived, setShowArchived] = useState(false);
  // Real KakaoTalk chat-list filter tabs (전체/안읽음/통화) (itunda Talk redesign,
  // 2026-08-28) -- 전체/안읽음 are pure client-side filters over the already-fetched
  // list (no backend change needed); 통화 shows the real call log instead of the
  // conversation list, fetched only when that tab is actually selected.
  const [filterTab, setFilterTab] = useState<'all' | 'unread' | 'calls'>('all');
  const [callHistory, setCallHistory] = useState<CallSession[] | null>(null);
  // Real room-lock gate (itunda Talk redesign, 2026-08-28) -- see
  // TalkRoomSettings.tsx's own doc comment. Session-local: unlocking a room once
  // keeps it open for the rest of this tab session, matching real KakaoTalk's own
  // per-app-open (not per-message) lock behavior.
  const [unlockedRoomIds, setUnlockedRoomIds] = useState<Set<string>>(new Set());

  const load = () => {
    setError(null);
    setActivePage(0);
    setArchivedPage(0);
    fetchConversations(false, 0)
      .then((r) => { setConversations(r.conversations); setActiveHasMore(r.page + 1 < r.totalPages); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchConversations(true, 0)
      .then((r) => { setArchivedConversations(r.conversations); setArchivedHasMore(r.page + 1 < r.totalPages); })
      .catch(() => {});
  };

  const loadMoreConversations = () => {
    const nextPage = (showArchived ? archivedPage : activePage) + 1;
    setLoadingMoreConversations(true);
    fetchConversations(showArchived, nextPage)
      .then((r) => {
        if (showArchived) {
          setArchivedConversations((prev) => [...(prev ?? []), ...r.conversations]);
          setArchivedPage(nextPage);
          setArchivedHasMore(r.page + 1 < r.totalPages);
        } else {
          setConversations((prev) => [...(prev ?? []), ...r.conversations]);
          setActivePage(nextPage);
          setActiveHasMore(r.page + 1 < r.totalPages);
        }
      })
      .catch(() => {})
      .finally(() => setLoadingMoreConversations(false));
  };

  const toggleArchived = (conversationId: string, archived: boolean) => {
    setConversationArchived(conversationId, archived).then(load).catch(() => {});
  };

  // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) -- found on a fresh
  // uncalled-endpoint sweep: MessagingController's pin-to-top endpoints and
  // ConversationSummary.pinnedToTop were already fully built on the backend with zero
  // client anywhere. Same always-visible-icon-button convention as the Archive action
  // right next to it.
  const togglePinnedToTop = (conversationId: string, pinned: boolean) => {
    setConversationPinnedToTop(conversationId, pinned).then(load).catch(() => {});
  };

  // Real KakaoTalk 즐겨찾기 (favorite) toggle -- see backend MessagingService
  // .setConversationFavorite's own doc comment, mirrors togglePinnedToTop exactly.
  const toggleFavorite = (conversationId: string, favorite: boolean) => {
    setConversationFavorite(conversationId, favorite).then(load).catch(() => {});
  };

  useEffect(load, []);

  useEffect(() => {
    if (filterTab === 'calls' && callHistory === null) {
      fetchCallHistory().then(setCallHistory).catch(() => setCallHistory([]));
    }
  }, [filterTab, callHistory]);

  // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
  // check for every listed contact, refreshed on a 10s cadence (a real, coarser-grained
  // signal than the 4s message poll -- presence doesn't need to be as fresh as message
  // delivery). No live WebSocket connection is opened just for this list view; the
  // per-thread real-time push happens in ConversationThread once a thread is open.
  useEffect(() => {
    if (!conversations || conversations.length === 0) return;
    const otherIds = conversations.map((c) => c.otherUserId);
    const refresh = () => fetchPresence(otherIds).then(setPresence).catch(() => {});
    refresh();
    const interval = setInterval(refresh, 10000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversations?.map((c) => c.otherUserId).join(',')]);

  // Real "jump straight into the chat" hand-off from MarketplaceView's "Message
  // seller" button -- contactSeller() returns a real conversation id (either freshly
  // created or an existing one reused), which this opens directly once it shows up in
  // the real conversation list, rather than making the buyer find it themselves.
  useEffect(() => {
    if (initialConversationId && conversations?.some((c) => c.conversationId === initialConversationId)) {
      setOpenConversationId(initialConversationId);
      onConsumedInitial?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialConversationId, conversations]);

  // Real client-side-synthesized entry points for the service channel + AI chatbot
  // (itunda Talk redesign, 2026-08-28) -- no real conversation row exists for either
  // on the backend (see ServiceChannelService/AiChatService's own doc comments), so
  // this is session-local nav state, not a conversationId.
  const [openVirtualThread, setOpenVirtualThread] = useState<'service' | 'ai' | null>(null);
  if (openVirtualThread === 'service') return <TalkServiceChannelThread onBack={() => setOpenVirtualThread(null)} />;
  if (openVirtualThread === 'ai') return <TalkAiChatThread onBack={() => setOpenVirtualThread(null)} />;

  const openConversation = conversations?.find((c) => c.conversationId === openConversationId);
  if (openConversation) {
    if (isRoomLocked(openConversation.conversationId) && !unlockedRoomIds.has(openConversation.conversationId)) {
      return (
        <div style={{ maxWidth: '360px', margin: '40px auto' }}>
          <DeviceStepUpPrompt
            onVerified={() => setUnlockedRoomIds((prev) => new Set(prev).add(openConversation.conversationId))}
            onCancel={() => setOpenConversationId(null)}
          />
        </div>
      );
    }
    return (
      <ConversationThread
        conversation={openConversation}
        onBack={() => {
          setOpenConversationId(null);
          load();
        }}
      />
    );
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (conversations === null) {
    return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  // Pinned rooms float to the top of the active list, same as real KakaoTalk --
  // a stable sort so unpinned rooms keep their existing most-recent-first order.
  const activeConversations = (showArchived
    ? (archivedConversations ?? [])
    : [...conversations].sort((a, b) => Number(b.pinnedToTop) - Number(a.pinnedToTop))) as ConversationSummaryWithFavorite[];
  const visibleConversations = filterTab === 'unread' ? activeConversations.filter((c) => c.unreadCount > 0) : activeConversations;
  const archivedCount = archivedConversations?.length ?? 0;
  const visibleHasMore = showArchived ? archivedHasMore : activeHasMore;

  return (
    <div>
      <NewChatCard onStarted={(id) => { load(); setOpenConversationId(id); }} />
      {/* Real KakaoTalk chat-list filter tabs (전체/안읽음/통화) -- a flat row of
          text tabs, matching this codebase's own flat-design-over-cards convention
          for new screens rather than a pill/segmented-control card. */}
      <div style={{ display: 'flex', gap: '20px', borderBottom: '1px solid var(--itunda-grey-100)', marginBottom: '14px' }}>
        {([['all', '전체'], ['unread', '안읽음'], ['calls', '통화']] as const).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setFilterTab(key)}
            style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: '10px 2px',
              fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700,
              color: filterTab === key ? 'var(--itunda-grey-900)' : 'var(--itunda-grey-400)',
              borderBottom: filterTab === key ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {filterTab === 'calls' ? (
        <TalkCallLog calls={callHistory} currentUserId={getStoredUser()?.id ?? null} />
      ) : (
        <>
      {filterTab === 'all' && !showArchived && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '4px' }}>
          <TalkVirtualRow icon={<MessageCircle size={20} color="var(--itunda-indigo)" />} name="itunda" preview="Real-time updates about your account" onClick={() => setOpenVirtualThread('service')} />
          <TalkVirtualRow icon={<Bot size={20} color="var(--itunda-indigo)" />} name="itunda AI" preview="Ask itunda AI anything about the app" onClick={() => setOpenVirtualThread('ai')} />
        </div>
      )}
      {archivedCount > 0 && (
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setShowArchived((value) => !value)} style={{ marginBottom: '10px' }}>
          {showArchived ? 'Show active chats' : `Archived (${archivedCount})`}
        </button>
      )}
      {visibleConversations.length === 0 ? (
        <EmptyState message={emptyConversationsMessage(showArchived, filterTab)} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {visibleConversations.map((c) => (
            // Real Toss-sourced "layering illusion" reorder animation (2026-08-29,
            // toss.tech/article/interaction's own real "Account Organization
            // Animation" example -- reordering a list should animate the move, not
            // jump). Pinning/unpinning a conversation used to snap it to its new
            // position on the next render with zero motion; framer-motion's `layout`
            // prop auto-animates each row's position change via FLIP, no other logic
            // change needed since `key` was already stable.
            <motion.div layout key={c.conversationId} transition={{ type: 'spring', ...itundaSpring.medium }} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '14px 0' }}>
              <button
                onClick={() => setOpenConversationId(c.conversationId)}
                style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1, minWidth: 0, textAlign: 'left', background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
              >
                <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
                  <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <MessageCircle size={20} color="var(--itunda-indigo)" />
                  </div>
                  {presence[c.otherUserId] && (
                    <span
                      style={{
                        position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                        backgroundColor: 'var(--itunda-green)', border: '2px solid var(--itunda-white)',
                      }}
                    />
                  )}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{c.otherUserName}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {c.lastMessagePreview ?? 'No messages yet'}
                  </p>
                </div>
                {c.unreadCount > 0 && (
                  <span
                    style={{
                      fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
                      borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                    }}
                  >
                    {c.unreadCount}
                  </span>
                )}
              </button>
              {/* Real KakaoTalk 즐겨찾기 (favorite) -- only offered on the active
                  list, same reasoning as pin-to-top below. */}
              {!showArchived && (
                <button
                  type="button"
                  onClick={() => toggleFavorite(c.conversationId, !c.favorite)}
                  title={c.favorite ? 'Remove from favorites' : 'Add to favorites'}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: c.favorite ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  <Star size={18} fill={c.favorite ? 'var(--itunda-indigo)' : 'none'} />
                </button>
              )}
              {/* Real KakaoTalk 채팅방 상단 고정 (pin room to top) -- only offered on
                  the active list, not the archived one (pinning an archived room to
                  the top of a list it isn't shown in doesn't mean anything). */}
              {!showArchived && (
                <button
                  type="button"
                  onClick={() => togglePinnedToTop(c.conversationId, !c.pinnedToTop)}
                  title={c.pinnedToTop ? 'Unpin from top' : 'Pin to top'}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: c.pinnedToTop ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  {c.pinnedToTop ? <PinOff size={18} /> : <Pin size={18} />}
                </button>
              )}
              {/* Real archive action (2026-08-05) -- closes docs/DESIGN_REFERENCES.md
                  Talk recommendation #4's remaining half. An always-visible icon
                  button, not a swipe gesture: bank-mfe's own established convention
                  for per-row actions elsewhere (Pin/Delete/Forward) is always-visible
                  buttons, and desktop-web has no real touch-swipe convention to match
                  Android/iOS's native one against. */}
              <button
                type="button"
                onClick={() => toggleArchived(c.conversationId, !showArchived)}
                title={showArchived ? 'Unarchive' : 'Archive'}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: 'var(--itunda-grey-500)' }}
              >
                {showArchived ? <ArchiveRestore size={18} /> : <Archive size={18} />}
              </button>
            </motion.div>
          ))}
          {visibleHasMore && (
            <button className="itunda-btn itunda-btn-secondary" disabled={loadingMoreConversations} onClick={loadMoreConversations}>
              {loadingMoreConversations ? 'Loading…' : 'Load more'}
            </button>
          )}
        </div>
      )}
        </>
      )}
    </div>
  );
}
