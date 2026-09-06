// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4). Real KakaoPay-style group
// split-bill (own lib/splitBill.ts data layer, exactly one external call site --
// inside GroupThread). Extracted ahead of GroupThread itself to break what would
// otherwise be a circular import (GroupThread renders this inline; if both stayed
// in BankDashboard.tsx, GroupThread's own future extraction would need to import
// this back from BankDashboard.tsx).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { IconBack } from './icons/ItundaIcons';
import { DiceGlyph } from './icons/ItundaFaceGifts';
import {
  attachSplitBillReceipt, createSplitBill, fetchSplitBillsForGroup, paySplitBillShare, requestSplitBillNextRound,
  type SplitBillWithParticipants,
} from './lib/splitBill';
import { type GroupMember } from './lib/groupMessaging';
import { useDeferredLoading } from './useDeferredLoading';

// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself being
// fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real account-to-account push, no escrow -- see SplitBill.kt's own doc comment.
export function GroupSplitBillsView({
  groupConversationId, members, currentUserId, onBack,
}: { groupConversationId: string; members: GroupMember[]; currentUserId: string | null; onBack: () => void }) {
  const { t } = useI18n();
  const [splitBills, setSplitBills] = useState<SplitBillWithParticipants[] | null>(null);
  const showSkeleton = useDeferredLoading(splitBills === null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [showNewForm, setShowNewForm] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25 on Android's TalkScreen.kt --
  // bank-mfe never got this despite usually shipping first) -- see backend
  // SplitBillService.ladderSplit's own doc comment for the 3 variance levels.
  const [ladderMode, setLadderMode] = useState(false);
  const [varianceLevel, setVarianceLevel] = useState(1);
  const [receiptUrlDrafts, setReceiptUrlDrafts] = useState<Record<string, string>>({});

  const refresh = () =>
    fetchSplitBillsForGroup(groupConversationId)
      .then(setSplitBills)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  useEffect(() => { refresh(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [groupConversationId]);

  const otherMembers = members.filter((m) => m.userId !== currentUserId);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusyId('new');
    setError(null);
    try {
      await createSplitBill(
        groupConversationId, Number(amount), description, Array.from(selectedIds),
        ladderMode ? 'LADDER' : 'EVEN', ladderMode ? varianceLevel : undefined,
      );
      setAmount(''); setDescription(''); setSelectedIds(new Set()); setShowNewForm(false); setLadderMode(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handlePay = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await paySplitBillShare(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleAttachReceipt = async (splitBillId: string) => {
    const url = (receiptUrlDrafts[splitBillId] ?? '').trim();
    if (!url) return;
    setBusyId(splitBillId);
    setError(null);
    try {
      await attachSplitBillReceipt(splitBillId, url);
      setReceiptUrlDrafts((prev) => ({ ...prev, [splitBillId]: '' }));
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleNextRound = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await requestSplitBillNextRound(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to group">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Split bills</h3>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowNewForm(true)}>Split a bill</button>
      ) : (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '10px 0' }}>
          <input
            type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Total amount (RWF)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was it for?" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Split with</p>
          {otherMembers.map((m) => (
            <label key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {m.name}
              <input
                type="checkbox"
                checked={selectedIds.has(m.userId)}
                onChange={(e) => {
                  const next = new Set(selectedIds);
                  if (e.target.checked) next.add(m.userId); else next.delete(m.userId);
                  setSelectedIds(next);
                }}
              />
            </label>
          ))}
          {/* Real a11y fix (item 244, web accessibility sweep): this was a plain
              <label> with an onClick and no associated form control -- a bare
              <label> isn't in the tab order and isn't activatable via
              Enter/Space, so keyboard-only and screen-reader users had no way to
              reach this real toggle at all. A <button> is the correct element:
              real keyboard focus/operability, no visual change needed beyond
              resetting the browser's default button chrome. */}
          <button
            type="button"
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', fontSize: 'var(--itunda-type-scale-13-size)', cursor: 'pointer', background: 'none', border: 'none', padding: 0, textAlign: 'left', font: 'inherit', color: 'inherit' }}
            onClick={() => setLadderMode((v) => !v)}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><DiceGlyph size={16} /> Ladder game (randomized split)</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: ladderMode ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)', fontWeight: 700 }}>
              {ladderMode ? 'On' : 'Off'}
            </span>
          </button>
          {ladderMode && (
            <div style={{ display: 'flex', gap: '8px' }}>
              {[1, 2, 3].map((level) => (
                <button
                  key={level} type="button"
                  className={level === varianceLevel ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                  style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)' }}
                  onClick={() => setVarianceLevel(level)}
                >
                  Level {level}
                </button>
              ))}
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setShowNewForm(false)}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === 'new' || selectedIds.size === 0}>
              {busyId === 'new' ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      )}
      {splitBills === null && showSkeleton && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {splitBills !== null && splitBills.length === 0 && (
        <EmptyState message="No split bills in this group yet — split one to divide a shared expense evenly." />
      )}
      {splitBills?.map(({ splitBill, participants }) => {
        const myShare = participants.find((p) => p.userId === currentUserId);
        const isOrganizer = splitBill.organizerId === currentUserId;
        const hasPending = participants.some((p) => p.status === 'PENDING');
        const modeLabel = splitBill.mode === 'LADDER' ? <> · <DiceGlyph size={12} /> Ladder L{splitBill.ladderVarianceLevel}</> : null;
        return (
          <div key={splitBill.id} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{splitBill.description}</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              Total {splitBill.totalAmount.toLocaleString('en-US')} RWF · {splitBill.status}{modeLabel}
              {splitBill.currentRound > 1 ? ` · Round ${splitBill.currentRound}` : ''}
            </p>
            {participants.map((p) => {
              const name = members.find((m) => m.userId === p.userId)?.name ?? p.userId.slice(0, 8);
              return (
                <p key={p.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>
                  {name}: {p.shareAmount.toLocaleString('en-US')} RWF ({p.status})
                </p>
              );
            })}
            {splitBill.receiptImageUrl && (
              <a href={splitBill.receiptImageUrl} target="_blank" rel="noreferrer" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>
                🧾 View receipt
              </a>
            )}
            {myShare && myShare.status === 'PENDING' && (
              <button
                className="itunda-btn itunda-btn-primary" style={{ marginTop: '6px' }}
                disabled={busyId === splitBill.id}
                onClick={() => handlePay(splitBill.id)}
              >
                {busyId === splitBill.id ? 'Paying…' : `Pay my share (${myShare.shareAmount.toLocaleString('en-US')} RWF)`}
              </button>
            )}
            {isOrganizer && (
              <>
                {!splitBill.receiptImageUrl && (
                  <div style={{ display: 'flex', gap: '6px', marginTop: '4px' }}>
                    <input
                      type="text" placeholder="Receipt photo URL" value={receiptUrlDrafts[splitBill.id] ?? ''}
                      onChange={(e) => setReceiptUrlDrafts((prev) => ({ ...prev, [splitBill.id]: e.target.value }))}
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    />
                    <button
                      type="button" className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}
                      disabled={busyId === splitBill.id || !(receiptUrlDrafts[splitBill.id] ?? '').trim()}
                      onClick={() => handleAttachReceipt(splitBill.id)}
                    >
                      Attach
                    </button>
                  </div>
                )}
                {splitBill.status === 'OPEN' && hasPending && splitBill.currentRound < 5 && (
                  <button
                    type="button" className="itunda-btn itunda-btn-secondary" style={{ marginTop: '4px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    disabled={busyId === splitBill.id}
                    onClick={() => handleNextRound(splitBill.id)}
                  >
                    Nudge unpaid → round {splitBill.currentRound + 1}
                  </button>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}
