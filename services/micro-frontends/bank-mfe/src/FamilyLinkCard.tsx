// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Toss Youth-style guardian-child link (own lib/family.ts data layer, exactly one
// external call site -- `<FamilyLinkCard />` inside MyView). Note:
// `sendToFamilyMember` stays ALSO imported in BankDashboard.tsx's own lib/p2p
// import -- this is the only real caller, but the import line itself groups it
// with sendDirect/resolveRecipient/etc which ARE used elsewhere, so only this one
// name got trimmed there rather than the whole line moving.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import {
  fetchChildOverview, fetchMyChildren, fetchMyGuardians, fetchMyInvites, inviteChild, respondToInvite, revokeFamilyLink,
  type ChildOverview, type FamilyLinkView,
} from './lib/family';
import { sendToFamilyMember } from './lib/p2p';

// Real Toss 유스 (Toss Youth)-style guardian-child link -- see lib/family.ts's own doc
// comment for the full sourced account and honest scope boundary (real read-only
// spending oversight only; allowance reuses AutoTransfer/ScheduledTransfer above).
export function FamilyLinkCard() {
  const { t } = useI18n();
  const [invites, setInvites] = useState<FamilyLinkView['link'][] | null>(null);
  const [children, setChildren] = useState<FamilyLinkView[] | null>(null);
  const [guardians, setGuardians] = useState<FamilyLinkView[] | null>(null);
  const [showInvite, setShowInvite] = useState(false);
  const [childPhone, setChildPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openOverviewFor, setOpenOverviewFor] = useState<string | null>(null);
  const [overview, setOverview] = useState<ChildOverview | null>(null);
  const [sendAmount, setSendAmount] = useState('');
  const [sendBusy, setSendBusy] = useState(false);
  const [sendDone, setSendDone] = useState(false);

  const load = () => {
    fetchMyInvites().then(setInvites).catch(() => {});
    fetchMyChildren().then(setChildren).catch(() => {});
    fetchMyGuardians().then(setGuardians).catch(() => {});
  };

  useEffect(load, []);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!childPhone.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await inviteChild(childPhone.trim());
      setChildPhone('');
      setShowInvite(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRespond = async (id: string, accept: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await respondToInvite(id, accept);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRevoke = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await revokeFamilyLink(id);
      setOpenOverviewFor(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleToggleOverview = async (childUserId: string) => {
    if (openOverviewFor === childUserId) {
      setOpenOverviewFor(null);
      return;
    }
    setOpenOverviewFor(childUserId);
    setSendAmount('');
    setSendDone(false);
    try {
      setOverview(await fetchChildOverview(childUserId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.loadError'));
    }
  };

  const handleSendToChild = async (childUserId: string) => {
    const amount = Number(sendAmount);
    if (!amount || amount <= 0) return;
    setSendBusy(true);
    setError(null);
    try {
      await sendToFamilyMember(childUserId, amount, 'Sent from Family');
      setSendDone(true);
      setSendAmount('');
      setOverview(await fetchChildOverview(childUserId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSendBusy(false);
    }
  };

  const hasAnything = (invites?.length ?? 0) > 0 || (children?.length ?? 0) > 0 || (guardians?.length ?? 0) > 0;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Family</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowInvite((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showInvite ? 'Cancel' : '+ Link a family member'}
        </button>
      </div>

      {showInvite && (
        <form onSubmit={handleInvite} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Phone number" value={childPhone} onChange={(e) => setChildPhone(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {(invites ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Pending invitations</p>
          {(invites ?? []).map((inv) => (
            <div key={inv.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Family link request</p>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="itunda-btn itunda-btn-primary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, true)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>Accept</button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, false)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>Decline</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {(children ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Linked children</p>
          {(children ?? []).map((c) => (
            <div key={c.link.id} style={{ padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{c.childName}</p>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <button className="itunda-btn itunda-btn-secondary" onClick={() => handleToggleOverview(c.link.childUserId)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    {openOverviewFor === c.link.childUserId ? 'Hide' : 'View'}
                  </button>
                  <button className="itunda-btn itunda-btn-secondary" disabled={busyId === c.link.id} onClick={() => handleRevoke(c.link.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    Unlink
                  </button>
                </div>
              </div>
              {openOverviewFor === c.link.childUserId && overview && (
                <div style={{ marginTop: '6px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                  <p>Balance: <strong style={{ color: 'var(--itunda-grey-900)' }}>{overview.accountBalance.toLocaleString('en-US')} RWF</strong></p>
                  {overview.recentTransactions.slice(0, 5).map((t) => (
                    <p key={t.id}>{t.description} · {t.amount.toLocaleString('en-US')} RWF</p>
                  ))}
                  {overview.recentTransactions.length === 0 && <EmptyState message="Nothing here yet — your activity will show up as you use itunda." />}
                  {/* Real Naver Pay "family shared asset management" -- instant transfer
                      to this linked family member, see lib/p2p.ts's own doc comment. */}
                  <div style={{ display: 'flex', gap: '6px', marginTop: '8px' }}>
                    <input
                      type="number" min={1} placeholder="Amount (RWF)" value={sendAmount}
                      onChange={(e) => { setSendAmount(e.target.value); setSendDone(false); }}
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    />
                    <button
                      className="itunda-btn itunda-btn-primary" disabled={sendBusy || !sendAmount}
                      onClick={() => handleSendToChild(c.link.childUserId)}
                      style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}
                    >
                      {sendBusy ? '…' : 'Send'}
                    </button>
                  </div>
                  {sendDone && <p style={{ color: 'var(--itunda-green)', marginTop: '4px' }}>Sent.</p>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(guardians ?? []).length > 0 && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Your guardians</p>
          {(guardians ?? []).map((g) => (
            <div key={g.link.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{g.guardianName}</p>
              <button className="itunda-btn itunda-btn-secondary" disabled={busyId === g.link.id} onClick={() => handleRevoke(g.link.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                Unlink
              </button>
            </div>
          ))}
        </div>
      )}

      {!hasAnything && <EmptyState message="No family members linked yet — invite one to manage their spending together." />}
    </div>
  );
}
