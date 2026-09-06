// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4). Real leave-group/add-member
// view, exactly one external call site (inside GroupThread) -- extracted ahead of
// GroupThread itself to avoid a circular import.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { IconBack } from './icons/ItundaIcons';
import { fetchTalkContacts, type TalkContact } from './lib/messaging';
import {
  addGroupMember, leaveGroup, setGroupDescription, setGroupPhotoUrl,
  type GroupMember, type GroupSummary,
} from './lib/groupMessaging';

// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere.
// Add-member picks from the caller's real Talk contacts, same list used to start a
// 1:1 chat, filtered to exclude people already in the group.
export function GroupManageMembersView({
  group, members, currentUserId, onMembersChanged, onLeft, onBack,
}: {
  group: GroupSummary; members: GroupMember[]; currentUserId: string | null;
  onMembersChanged: () => void; onLeft: () => void; onBack: () => void;
}) {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<TalkContact[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);
  // Real group photo/description (2026-07-28) -- see lib/messaging.ts's own doc
  // comment. Found 2026-08-01 via a defined-but-uncalled-endpoint sweep: real on
  // backend since it shipped, zero client anywhere until now.
  const [photoUrl, setPhotoUrl] = useState(group.photoUrl ?? '');
  const [description, setDescription] = useState(group.description ?? '');
  const [savingInfo, setSavingInfo] = useState(false);
  const [infoSaved, setInfoSaved] = useState(false);

  useEffect(() => {
    fetchTalkContacts().then(setContacts).catch(() => {});
  }, []);

  const addable = contacts.filter((c) => !members.some((m) => m.userId === c.userId));

  const handleSaveInfo = async () => {
    setSavingInfo(true);
    setError(null);
    setInfoSaved(false);
    try {
      await setGroupPhotoUrl(group.groupId, photoUrl.trim());
      await setGroupDescription(group.groupId, description.trim());
      setInfoSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSavingInfo(false);
    }
  };

  const handleLeave = async () => {
    if (!window.confirm('Leave this group?')) return;
    setLeaving(true);
    setError(null);
    try {
      await leaveGroup(group.groupId);
      onLeft();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setLeaving(false);
    }
  };

  const handleAdd = async (contact: TalkContact) => {
    setBusyUserId(contact.userId);
    setError(null);
    try {
      await addGroupMember(group.groupId, contact.userId);
      onMembersChanged();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): adding a
      // contact already in the group isn't really a failure -- resolve forward.
      if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') {
        onMembersChanged();
      } else {
        setError(err instanceof ApiError ? err.message : `Could not add ${contact.name}.`);
      }
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to group">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Manage members</h3>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Group info</h4>
      <input
        type="text" placeholder="Photo URL (blank to clear)" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        placeholder="Group description (blank to clear)" value={description} onChange={(e) => setDescription(e.target.value)} rows={2}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', fontFamily: 'inherit' }}
      />
      <button className="itunda-btn itunda-btn-secondary" disabled={savingInfo} onClick={handleSaveInfo}>
        {savingInfo ? 'Saving…' : infoSaved ? 'Saved' : 'Save group info'}
      </button>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Members ({members.length})</h4>
      {members.map((m) => (
        <p key={m.userId} style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{m.userId === currentUserId ? `${m.name} (you)` : m.name}</p>
      ))}
      <button className="itunda-btn itunda-btn-secondary" disabled={leaving} onClick={handleLeave}>
        {leaving ? 'Leaving…' : 'Leave group'}
      </button>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginTop: '8px' }}>Add from your contacts</h4>
      {addable.length === 0 && <EmptyState message="No contacts left to add." />}
      {addable.map((c) => (
        <div key={c.userId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{c.name}</span>
          <button className="itunda-btn itunda-btn-secondary" disabled={busyUserId !== null} onClick={() => handleAdd(c)}>
            {busyUserId === c.userId ? 'Adding…' : 'Add'}
          </button>
        </div>
      ))}
    </div>
  );
}
