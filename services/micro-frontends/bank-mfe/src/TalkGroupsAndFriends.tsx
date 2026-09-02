// Extracted from BankDashboard.tsx (2026-09-03, itunda-vs-Toss architecture comparison
// thread's own open recommendation 4). The group-chat list, the Direct/Groups/Friends tab
// shell that switches between them, and the Kakao Friends-directory equivalent (with its
// own "Today's birthday" sub-section) -- verified via real usage grep before bundling.
import { useEffect, useState } from 'react';
import { Users } from 'lucide-react';
import { CakeGlyph } from './icons/ItundaFaceMisc';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState, ErrorCard } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';
import { GroupThread } from './GroupThread';
import {
  fetchGroups, fetchPresence, fetchTalkContacts, fetchTodaysBirthdays, startConversationWithUser,
  type GroupSummary, type TalkContact,
} from './lib/messaging';
import { NewGroupCard, OpenChatCard } from './TalkNewChatCards';
import { DirectMessagesList } from './TalkDirectMessagesList';

export function GroupsList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void } = {}) {
  const { t } = useI18n();
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const showSkeleton = useDeferredLoading(groups === null);
  const [error, setError] = useState<string | null>(null);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGroups()
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  // Real "join meetup" hand-off from CommunityView (2026-07-24) -- same pattern
  // DirectMessagesList's own initialConversationId effect already established, just
  // matched against `groups` instead of 1:1 conversations.
  useEffect(() => {
    if (initialConversationId && groups?.some((g) => g.groupId === initialConversationId)) {
      setOpenGroupId(initialConversationId);
      onConsumedInitial?.();
    }
  }, [initialConversationId, groups]);

  const openGroup = groups?.find((g) => g.groupId === openGroupId);
  if (openGroup) {
    return (
      <GroupThread
        group={openGroup}
        onBack={() => {
          setOpenGroupId(null);
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

  if (groups === null) {
    return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  }

  return (
    <div>
      <OpenChatCard
        onCreated={(id) => { load(); setOpenGroupId(id); }}
        onJoined={(id) => { load(); setOpenGroupId(id); }}
      />
      <NewGroupCard onCreated={(id) => { load(); setOpenGroupId(id); }} />
      {groups.length === 0 ? (
        <EmptyState message="No groups yet — start one to chat with more than one person at a time." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {groups.map((g) => (
            <button
              key={g.groupId}
              onClick={() => setOpenGroupId(g.groupId)}
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Users size={20} color="var(--itunda-indigo)" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{g.name} · {g.memberCount}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {g.lastMessagePreview ?? 'No messages yet'}
                </p>
              </div>
              {g.unreadCount > 0 && (
                <span
                  style={{
                    fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
                    borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                  }}
                >
                  {g.unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Real group chat (2026-07-18) folded in via a Direct/Groups toggle -- the single most
// defining KakaoTalk capability the original 1:1-only Messages tab didn't cover, added
// at the user's direct request. See GroupMessagingService.kt's own doc comment.
export function MessagesView({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  // Real fix (2026-08-19): a tapped ?joinChatCode= invite link (see OpenChatCard's own
  // buildJoinUrl doc comment) only carries the top-level ?tab=MESSAGES -- this
  // Direct/Groups/Friends split is its own local state, so without this the link would
  // silently land on Direct and OpenChatCard (which lives under Groups, and owns the
  // actual auto-join effect) would never even mount. A lazy initializer peeks at the
  // param without consuming it -- OpenChatCard's own effect is what deletes it.
  const [mode, setMode] = useState<'DIRECT' | 'GROUPS' | 'FRIENDS'>(() =>
    new URLSearchParams(window.location.search).has('joinChatCode') ? 'GROUPS' : 'DIRECT'
  );
  // Real Kakao-style Friends directory (item 237) -- see FriendsList's own doc
  // comment. Tapping a friend hands its real conversation id off to DirectMessagesList
  // through the exact same initialConversationId mechanism CommunityView's own
  // "join meetup" hand-off below already established, rather than duplicating
  // ConversationThread's own render logic inside FriendsList.
  const [friendJumpConversationId, setFriendJumpConversationId] = useState<string | null>(null);

  // Real "join meetup" hand-off from CommunityView (2026-07-24): a real
  // GroupConversation id, not a 1:1 conversation id, needs the Groups tab
  // pre-selected -- otherwise it would silently render under Direct, where neither
  // DirectMessagesList's own conversation list nor its initialConversationId check
  // would ever match it.
  useEffect(() => {
    if (!initialConversationId) return;
    fetchGroups()
      .then((groups) => {
        if (groups.some((g) => g.groupId === initialConversationId)) setMode('GROUPS');
      })
      .catch(() => {});
  }, [initialConversationId]);

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['DIRECT', 'GROUPS', 'FRIENDS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: mode === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'DIRECT' ? 'Direct' : v === 'GROUPS' ? 'Groups' : 'Friends'}
          </button>
        ))}
      </div>
      {mode === 'DIRECT' ? (
        <DirectMessagesList
          initialConversationId={friendJumpConversationId ?? initialConversationId}
          onConsumedInitial={() => { setFriendJumpConversationId(null); onConsumedInitial?.(); }}
        />
      ) : mode === 'GROUPS' ? (
        <GroupsList initialConversationId={initialConversationId} onConsumedInitial={onConsumedInitial} />
      ) : (
        <FriendsList onOpenConversation={(id) => { setFriendJumpConversationId(id); setMode('DIRECT'); }} />
      )}
    </div>
  );
}

// Real Kakao Friends-tab equivalent (item 237, sourced -- Kakao's Sept 2025 attempt
// to bury this tab caused a rating collapse and was reverted within 3 months, per
// this doc's own Talk section recommendation #1). itunda's Talk only ever let a user
// switch between chat-*history* views (Direct/Groups); there was no way to browse
// contacts who are on itunda but you haven't messaged yet -- "New chat" only worked
// as a hand-typed-phone-number or inline quick-pick composer, not a real browsable
// directory. The backend infra (`GET /messages/contacts`, `GET /messages/presence`)
// was already fully real and already used inline in the New-chat/add-group-member
// composers on all 3 platforms -- this is a client-only addition, no new endpoint.
// Real KakaoTalk "오늘의 생일" (Today's Birthday) (2026-08-17) -- KakaoTalk's own real
// feature shows friends with a birthday today at the top of the friend list with a
// cake icon, letting you message them directly without hunting through the full
// contact list. Reuses FriendsList's own onOpenConversation hand-off convention.
// Renders nothing when the caller has no real contacts with a birthday today -- never
// an empty placeholder card.
function TodaysBirthdaySection({ onOpenConversation }: { onOpenConversation: (conversationId: string) => void }) {
  const [birthdays, setBirthdays] = useState<TalkContact[] | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);

  useEffect(() => {
    fetchTodaysBirthdays().then(setBirthdays).catch(() => setBirthdays([]));
  }, []);

  const handleTap = async (contact: TalkContact) => {
    setStartingId(contact.userId);
    try {
      const conversation = await startConversationWithUser(contact.userId);
      onOpenConversation(conversation.id);
    } catch {
      // Fails quietly -- the user can still reach this same person from the regular
      // Friends list below, same non-blocking discipline FriendsList's own handleTap
      // already establishes for a failed chat start.
    } finally {
      setStartingId(null);
    }
  };

  if (!birthdays || birthdays.length === 0) return null;

  return (
    <div className="itunda-card" style={{ background: 'var(--itunda-indigo-light)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}><CakeGlyph size={16} /> Today's birthday</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {birthdays.map((c) => (
          <button
            key={c.userId}
            onClick={() => handleTap(c)}
            disabled={startingId === c.userId}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', padding: '8px 10px', borderRadius: '8px', background: 'var(--itunda-white)', border: 'none', textAlign: 'left', cursor: 'pointer' }}
          >
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{c.name}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 700 }}>{startingId === c.userId ? '…' : 'Say happy birthday'}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function FriendsList({ onOpenConversation }: { onOpenConversation: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<TalkContact[] | null>(null);
  const [presence, setPresence] = useState<Record<string, boolean>>({});
  const [error, setError] = useState<string | null>(null);
  const showSkeleton = useDeferredLoading(contacts === null);
  const [startingId, setStartingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchTalkContacts()
      .then((c) => {
        setContacts(c);
        if (c.length > 0) fetchPresence(c.map((x) => x.userId)).then(setPresence).catch(() => {});
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleTap = async (contact: TalkContact) => {
    setStartingId(contact.userId);
    setError(null);
    try {
      const conversation = await startConversationWithUser(contact.userId);
      onOpenConversation(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setStartingId(null);
    }
  };

  if (error) return <ErrorCard message={error} onRetry={load} />;
  if (contacts === null) return showSkeleton ? <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;
  if (contacts.length === 0) {
    return (
      <EmptyState message="No friends yet -- save someone's contact and they'll show up here once they're on itunda." />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <TodaysBirthdaySection onOpenConversation={onOpenConversation} />
      {contacts.map((c) => (
        <button
          key={c.userId}
          onClick={() => handleTap(c)}
          disabled={startingId === c.userId}
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', width: '100%', textAlign: 'left', background: 'var(--itunda-white)', border: 'none', cursor: 'pointer' }}
        >
          <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
            <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Users size={20} color="var(--itunda-indigo)" />
            </div>
            {presence[c.userId] && (
              <span
                style={{
                  position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                  backgroundColor: 'var(--itunda-green)', border: '2px solid var(--itunda-white)',
                }}
              />
            )}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{c.name}</p>
            {presence[c.userId] && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', fontWeight: 700 }}>Active now</p>}
          </div>
          {startingId === c.userId && <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>…</span>}
        </button>
      ))}
    </div>
  );
}
