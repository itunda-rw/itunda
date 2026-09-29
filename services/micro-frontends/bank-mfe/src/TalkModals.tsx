// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4). Shared modals both
// ConversationThread (1:1 chat) and GroupThread (group chat) render inline --
// verified via real usage grep before bundling (MediaGalleryModal/
// ForwardPickerModal/ThreadModal each have real call sites in both screens).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { FullScreenFlow } from './FullScreenFlow';
import { IdsButton } from './IdsButton';
import { renderTextWithEmoji } from './icons/ItundaFaceEmoji';
import { chatMessageTime } from './TalkBubbles';
import { useDeferredLoading } from './useDeferredLoading';
import { fetchConversations, type ConversationSummary, type ReactionGroup } from './lib/messaging';
import { fetchGroups, type GroupSummary } from './lib/groupMessaging';

// Real per-thread shared-media gallery (Kakao's real "Chat Room Drawer") -- ports
// Android TalkScreen.kt's own identical addition (2026-08-04) to bank-mfe. Scoped
// honestly to photos only: itunda has real photo messages but no file-attachment type
// and no link-preview system, so a real "files/links" tab would have nothing genuine
// to show. Built entirely client-side from the conversation's own already-loaded
// messages (filtered to real imageUrl != null entries) -- no new backend endpoint.
export function MediaGalleryModal({ imageUrls, onClose }: { imageUrls: string[]; onClose: () => void }) {
  return (
    <div
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '16px 16px 0 0', padding: '16px', width: '100%', maxHeight: '70vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>Shared photos ({imageUrls.length})</h3>
        {imageUrls.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No photos shared in this conversation yet.</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '4px' }}>
            {imageUrls.map((url, i) => (
              <img key={i} src={url} alt="Shared photo" style={{ width: '100%', aspectRatio: '1', objectFit: 'cover', borderRadius: '6px' }} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Real message forwarding (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Talk
// section recommendation #3. Lists the caller's own real conversations and groups
// (same fetchConversations/fetchGroups this tab's own list views already use) -- never
// a public directory, matching this whole feature's own privacy-preserving precedent.
export function ForwardPickerModal({ onForward, onClose }: { onForward: (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => void; onClose: () => void }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const showSkeleton = useDeferredLoading(conversations === null || groups === null);
  // Real pagination-discard fix (2026-09-09, same systemic gap fixed for
  // this tab's own DirectMessagesList/GroupsList) -- this picker used to
  // silently cap both lists at their first 20 rows, with no way to forward
  // to an older conversation or group not on the initial page.
  const [conversationsPage, setConversationsPage] = useState(0);
  const [conversationsHasMore, setConversationsHasMore] = useState(false);
  const [groupsPage, setGroupsPage] = useState(0);
  const [groupsHasMore, setGroupsHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  useEffect(() => {
    fetchConversations(false, 0)
      .then((r) => { setConversations(r.conversations); setConversationsHasMore(r.page + 1 < r.totalPages); })
      .catch(() => setConversations([]));
    fetchGroups(0)
      .then((r) => { setGroups(r.groups); setGroupsHasMore(r.page + 1 < r.totalPages); })
      .catch(() => setGroups([]));
  }, []);

  const loadMore = () => {
    setLoadingMore(true);
    Promise.all([
      conversationsHasMore ? fetchConversations(false, conversationsPage + 1) : null,
      groupsHasMore ? fetchGroups(groupsPage + 1) : null,
    ])
      .then(([conversationsRes, groupsRes]) => {
        if (conversationsRes) {
          setConversations((prev) => [...(prev ?? []), ...conversationsRes.conversations]);
          setConversationsPage((p) => p + 1);
          setConversationsHasMore(conversationsRes.page + 1 < conversationsRes.totalPages);
        }
        if (groupsRes) {
          setGroups((prev) => [...(prev ?? []), ...groupsRes.groups]);
          setGroupsPage((p) => p + 1);
          setGroupsHasMore(groupsRes.page + 1 < groupsRes.totalPages);
        }
      })
      .catch(() => {})
      .finally(() => setLoadingMore(false));
  };
  const hasMore = conversationsHasMore || groupsHasMore;

  // Real fix (full-app audit, docs/UI_UX_GUIDELINES.md rule 1) -- same dark-overlay-
  // card pattern EmoticonStoreModal above had, now FullScreenFlow like the rest of
  // this file's already-modernized flows.
  return (
    <FullScreenFlow bottomCTA={<IdsButton variant="tinted" fullWidth onClick={onClose}>Cancel</IdsButton>}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>Forward to…</p>
      {conversations === null || groups === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : conversations.length === 0 && groups.length === 0 ? (
        <EmptyState message="No conversations to forward to yet — start a chat first." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {conversations.map((c) => (
            <button
              key={c.conversationId}
              style={{ width: '100%', textAlign: 'left', padding: '10px 0' }}
              onClick={() => onForward('DIRECT', c.conversationId)}
            >
              {c.otherUserName}
            </button>
          ))}
          {groups.map((g) => (
            <button
              key={g.groupId}
              style={{ width: '100%', textAlign: 'left', padding: '10px 0' }}
              onClick={() => onForward('GROUP', g.groupId)}
            >
              {g.name} (group)
            </button>
          ))}
          {hasMore && (
            <button className="itunda-btn itunda-btn-secondary" disabled={loadingMore} onClick={loadMore}>
              {loadingMore ? 'Loading…' : 'Load more'}
            </button>
          )}
        </div>
      )}
    </FullScreenFlow>
  );
}

// Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
// recommendation #3's own account. A real sub-conversation view: the root message,
// every direct reply oldest-first, and a composer that replies straight into this same
// thread (never the flat top-level timeline). Generic over Message/GroupMessage since
// both share the same id/senderId/body/sentAt/deletedAt/reactions shape this view needs.
export function ThreadModal<T extends { id: string; senderId: string; body: string; sentAt: string; deletedAt?: string | null; reactions: ReactionGroup[] }>({
  rootMessage,
  currentUserId,
  fetchThreadMessages,
  onSend,
  onClose,
}: {
  rootMessage: T;
  currentUserId: string | undefined;
  fetchThreadMessages: () => Promise<T[]>;
  onSend: (body: string) => Promise<unknown>;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<T[] | null>(null);
  const showSkeleton = useDeferredLoading(messages === null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);

  const load = () => {
    fetchThreadMessages()
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => { load(); }, [rootMessage.id]);

  const handleSend = async () => {
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    try {
      await onSend(body);
      setDraft('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setSending(false); }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div
        className="itunda-card"
        style={{ width: '100%', maxHeight: '80vh', display: 'flex', flexDirection: 'column', borderRadius: '16px 16px 0 0', margin: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Thread</p>
          {/* Real touch-target-size fix (item 244, web accessibility sweep):
              same as this file's other modal-close "x" -- see its own comment. */}
          <button type="button" aria-label="Close" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-500)', padding: '8px', minWidth: '24px', minHeight: '24px' }}>×</button>
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }}>{error}</p>}
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px', paddingBottom: '8px' }}>
          {messages === null ? (
            showSkeleton ? <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
          ) : (
            messages.map((m, i) => {
              const isMine = m.senderId === currentUserId;
              return (
                <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
                  {i === 0 && <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', marginBottom: '2px' }}>Original message</span>}
                  <div
                    style={{
                      maxWidth: '75%', padding: '10px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
                      backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                      color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                    }}
                  >
                    {m.deletedAt ? 'This message was deleted' : renderTextWithEmoji(m.body)}
                  </div>
                  <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>{chatMessageTime(m.sentAt)}</span>
                </div>
              );
            })
          )}
        </div>
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            className="itunda-input"
            style={{ flex: 1 }}
            placeholder="Reply in thread…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSend(); }}
          />
          <button type="button" className="itunda-btn itunda-btn-primary" style={{ padding: '10px 16px' }} onClick={handleSend} disabled={sending || !draft.trim()}>
            {sending ? '…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
}
