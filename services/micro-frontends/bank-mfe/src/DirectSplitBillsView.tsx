// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4). Real 1:1-chat split-bill view
// (own lib/splitBill.ts data layer, exactly one external call site -- inside
// ConversationThread). Same circular-import-avoidance reason as
// GroupSplitBillsView.tsx's own doc comment.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { IconBack } from './icons/ItundaIcons';
import { DiceGlyph } from './icons/ItundaFaceGifts';
import {
  attachSplitBillReceipt, createDirectSplitBill, fetchDirectSplitBills, paySplitBillShare, requestSplitBillNextRound,
  type SplitBillWithParticipants,
} from './lib/splitBill';

// Real 1:1-chat split-bill view (2026-08-09) -- see lib/splitBill.ts's own doc comment
// on createDirectSplitBill/fetchDirectSplitBills for the backend account. Same shape
// as GroupSplitBillsView above, minus the member-picker: a 1:1 split always has
// exactly one other participant, fixed by which conversation this was opened from.
export function DirectSplitBillsView({
  otherUserId, otherUserName, currentUserId, onBack,
}: { otherUserId: string; otherUserName: string; currentUserId: string | null; onBack: () => void }) {
  const { t } = useI18n();
  const [splitBills, setSplitBills] = useState<SplitBillWithParticipants[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [showNewForm, setShowNewForm] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [ladderMode, setLadderMode] = useState(false);
  const [varianceLevel, setVarianceLevel] = useState(1);
  const [receiptUrlDrafts, setReceiptUrlDrafts] = useState<Record<string, string>>({});

  const refresh = () =>
    fetchDirectSplitBills(otherUserId)
      .then(setSplitBills)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  useEffect(() => { refresh(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [otherUserId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusyId('new');
    setError(null);
    try {
      await createDirectSplitBill(
        otherUserId, Number(amount), description,
        ladderMode ? 'LADDER' : 'EVEN', ladderMode ? varianceLevel : undefined,
      );
      setAmount(''); setDescription(''); setShowNewForm(false); setLadderMode(false);
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
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversation">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Split bills with {otherUserName}</h3>
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
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Split with {otherUserName}</p>
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
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === 'new'}>
              {busyId === 'new' ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      )}
      {splitBills === null && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {splitBills !== null && splitBills.length === 0 && (
        <EmptyState message={`No split bills with ${otherUserName} yet — split one to divide a shared expense evenly.`} />
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
              Total {splitBill.totalAmount.toLocaleString()} RWF · {splitBill.status}{modeLabel}
              {splitBill.currentRound > 1 ? ` · Round ${splitBill.currentRound}` : ''}
            </p>
            {participants.map((p) => (
              <p key={p.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>
                {p.userId === otherUserId ? otherUserName : 'You'}: {p.shareAmount.toLocaleString()} RWF ({p.status})
              </p>
            ))}
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
                {busyId === splitBill.id ? 'Paying…' : `Pay my share (${myShare.shareAmount.toLocaleString()} RWF)`}
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
