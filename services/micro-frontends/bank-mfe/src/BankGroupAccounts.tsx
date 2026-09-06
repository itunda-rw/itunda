import { useEffect, useRef, useState } from 'react';
import { IconAdd } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { showToast } from './Toast';
import { useCountUp } from './hooks/useCountUp';
import { useI18n } from './i18n/I18nContext';
import { useDeferredLoading } from './useDeferredLoading';
import { ApiError, getStoredUser } from './lib/api';
import {
  createGroupAccount, depositToGroupAccount, fetchGroupAccount, fetchGroupAccountDues, fetchMyGroupAccounts,
  inviteGroupAccountMember, requestUnpaidGroupAccountDues, setGroupAccountDuesAmount, withdrawFromGroupAccount,
  type GroupAccount, type GroupAccountDetail, type GroupAccountDuesStatus,
} from './lib/groupAccounts';

function GroupAccountDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<GroupAccountDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10) -- see the Talk conversation view's own identical
  // pendingDeviceRetryRef for the full account: deposit and withdraw share this one
  // flag+prompt, so retrying has to redo whichever one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  const myUserId = getStoredUser()?.id;

  // Real KakaoBank 회비 (dues) management (2026-07-26) -- see
  // GroupAccountService.setDuesAmount's own doc comment.
  const [dues, setDues] = useState<GroupAccountDuesStatus | null>(null);
  const showDuesSkeleton = useDeferredLoading(dues === null);
  const [duesAmountInput, setDuesAmountInput] = useState('');
  const [duesBusy, setDuesBusy] = useState(false);
  const [remindedCount, setRemindedCount] = useState<number | null>(null);

  const loadDues = () => {
    fetchGroupAccountDues(id).then(setDues).catch(() => {});
  };

  const load = () => {
    setError(null);
    fetchGroupAccount(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    loadDues();
  };
  useEffect(load, []);

  const handleSetDues = async (e: React.FormEvent) => {
    e.preventDefault();
    setDuesBusy(true);
    setError(null);
    try {
      await setGroupAccountDuesAmount(id, Number(duesAmountInput));
      setDuesAmountInput('');
      loadDues();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const handleClearDues = async () => {
    setDuesBusy(true);
    setError(null);
    try {
      await setGroupAccountDuesAmount(id, null);
      loadDues();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const handleRemindUnpaid = async () => {
    setDuesBusy(true);
    setError(null);
    setRemindedCount(null);
    try {
      const count = await requestUnpaidGroupAccountDues(id);
      setRemindedCount(count);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const isOwner = detail?.groupAccount.ownerId === myUserId;
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks: a hook can't be skipped on some
  // renders), using detail?.balance so it's already correct once detail loads.
  const animatedBalance = useCountUp(detail?.balance ?? 0);

  const handleDeposit = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await depositToGroupAccount(id, Number(amount));
      setAmount('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleDeposit;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await withdrawFromGroupAccount(id, Number(amount));
      setAmount('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleWithdraw;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await inviteGroupAccountMember(id, phoneNumber.trim());
      setPhoneNumber('');
      load();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): the
      // organizer inviting a phone number already in the group isn't really a
      // failure -- the desired end state (that person being a member) is already
      // true. Resolve forward the same way a self-registration retry would.
      if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') {
        setPhoneNumber('');
        load();
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return showSkeleton ? <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  // Real fix (2026-08-24, flat-design sweep): 5 distinct non-exclusive sections
  // shown together -- reused .itunda-flat-section for section-boundary dividers.
  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to group accounts</button>

      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{detail.groupAccount.name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0' }}>{animatedBalance.toLocaleString('en-US')} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{detail.members.length} member{detail.members.length === 1 ? '' : 's'}</p>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Members</h3>
        {detail.members.map((m) => (
          <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
            {m.isOwner && <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700 }}>Organizer</span>}
          </div>
        ))}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Monthly dues</h3>
        {dues === null ? (
          showDuesSkeleton ? <div className="skeleton" style={{ height: '40px', borderRadius: '8px' }} /> : null
        ) : dues.duesAmount === null ? (
          isOwner ? (
            <form onSubmit={handleSetDues} style={{ display: 'flex', gap: '8px' }}>
              <input
                type="number" min="1" required value={duesAmountInput} onChange={(e) => setDuesAmountInput(e.target.value)}
                placeholder="Monthly dues (RWF)"
                style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary" disabled={duesBusy}>{duesBusy ? '…' : 'Set'}</button>
            </form>
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>The organizer hasn't set a monthly dues amount.</p>
          )
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{dues.duesAmount.toLocaleString('en-US')} RWF / month · {dues.cycleMonth}</p>
            {dues.members.map((m) => {
              const duesAmount = dues.duesAmount as number;
              return (
                <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
                  <span style={{ color: m.paid ? '#1E8E4F' : 'var(--itunda-grey-500)', fontWeight: m.paid ? 700 : 400 }}>
                    {m.paid ? '✓ Paid' : `${m.contributedAmount.toLocaleString('en-US')} / ${duesAmount.toLocaleString('en-US')}`}
                  </span>
                </div>
              );
            })}
            {isOwner && (
              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={duesBusy} onClick={handleRemindUnpaid}>
                  {duesBusy ? '…' : 'Remind unpaid members'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={duesBusy} onClick={handleClearDues}>Clear</button>
              </div>
            )}
            {remindedCount !== null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {remindedCount === 0 ? 'Everyone has already paid or been reminded this month.' : `Reminded ${remindedCount} member${remindedCount === 1 ? '' : 's'}.`}
              </p>
            )}
          </div>
        )}
      </div>

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '16px' }}>
          <DeviceStepUpPrompt
            onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
            onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); }}
          />
        </div>
      ) : (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{isOwner ? 'Deposit or withdraw' : 'Deposit'}</h3>
          <input
            type="number" min="1" required value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="button" onClick={handleDeposit} className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy || !amount}>
              {busy ? '…' : 'Deposit'}
            </button>
            {isOwner && (
              // Real Kakao Bank behavior: only the organizer can withdraw/settle --
              // this button is only rendered for the owner, not just disabled.
              <button type="button" onClick={handleWithdraw} className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy || !amount}>
                {busy ? '…' : 'Withdraw'}
              </button>
            )}
          </div>
        </div>
      )}

      {isOwner && (
        <form onSubmit={handleInvite} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Invite a member</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Phone number"
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
          </div>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

function CreateGroupAccountForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <IconAdd size={16} /> New group account
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createGroupAccount(name);
      setName('');
      setOpen(false);
      showToast(t('toast.groupAccountCreated'));
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a lone toggled
  // form section (docs/UI_UX_GUIDELINES.md §10).
  return (
    <form onSubmit={handleSubmit} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        type="text" required placeholder="Group name (e.g. Roommates)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

export function GroupAccountsSection() {
  const { t } = useI18n();
  const [accounts, setAccounts] = useState<GroupAccount[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  // Real, sourced finding (KakaoPay tech blog) -- see useDeferredLoading's own doc
  // comment for the full account. Proof-of-concept site for a real, multi-session
  // sweep: this file alone has ~105 other `=== null` skeleton call sites still
  // showing instantly, not yet migrated.
  const showSkeleton = useDeferredLoading(accounts === null);

  const load = () => {
    setError(null);
    fetchMyGroupAccounts().then(setAccounts).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <GroupAccountDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>Group accounts</h3>
      <CreateGroupAccountForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {accounts === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : accounts.length === 0 ? (
        <EmptyState message="No group accounts yet -- start one to save or split expenses with others." />
      ) : (
        accounts.map((a) => (
          // Real fix (2026-08-24, direct user directive, real Toss reference): dropped
          // itunda-flat-section -- that class's border-bottom divider is for separating
          // distinct sections, not individual rows within one repeated list, matching
          // the same fix just made to Android's ShellSection.
          <button
            key={a.id}
            onClick={() => setOpenId(a.id)}
            style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{a.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Tap to view balance and members</p>
          </button>
        ))
      )}
    </div>
  );
}

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See
// lib/ikimina.ts's own doc comment for the full sourced account. Genuinely the first
// feature in this codebase not sourced from Toss/Kakao/Naver/Coupang -- a real,
// currently-live Rwandan financial practice, sibling to GroupAccountsSection above but
// structurally distinct (a rotating payout recipient, not one permanent owner).
