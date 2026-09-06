import { useEffect, useState } from 'react';
import { IconAdd, IconBack, IconClose } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { FullScreenFlow } from './FullScreenFlow';
import { IdsButton } from './IdsButton';
import { ProgressStepper } from './BankDashboard';
import { showToast } from './Toast';
import { useCountUp } from './hooks/useCountUp';
import { useI18n } from './i18n/I18nContext';
import { useDeferredLoading } from './useDeferredLoading';
import { ApiError, getStoredUser } from './lib/api';
import {
  contributeToIkimina, createIkimina, fetchIkimina, fetchMyIkiminas, inviteIkiminaMember, startIkiminaCycle,
  triggerIkiminaPayout, type Ikimina, type IkiminaDetail,
} from './lib/ikimina';

export function IkiminaSection() {
  const { t } = useI18n();
  const [ikiminas, setIkiminas] = useState<Ikimina[] | null>(null);
  const showSkeleton = useDeferredLoading(ikiminas === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyIkiminas().then(setIkiminas).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <IkiminaDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>Ikimina (rotating savings)</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>
        Everyone contributes the same amount each round; one member takes home the full pot, in turn.
      </p>
      <CreateIkiminaForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {ikiminas === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : ikiminas.length === 0 ? (
        <EmptyState message="No ikimina groups yet -- start one with people you trust." />
      ) : (
        ikiminas.map((k) => (
          // Real fix (2026-08-24): see accounts.map's own identical comment above.
          <button
            key={k.id}
            onClick={() => setOpenId(k.id)}
            style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{k.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              {k.status === 'FORMING' ? 'Forming — invite members before starting' : k.status === 'ACTIVE' ? `Round ${k.currentRound}` : 'Completed'}
            </p>
          </button>
        ))
      )}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up, same real sourcing:
// Toss's own published Product Principles doc, "하나의 화면은 하나의 메시지만 표현한다").
// This card asked 4 real decisions at once (group name, contribution amount, cycle
// frequency, member cap) -- rebuilt as a real step flow with the real ProgressStepper
// indicator, matching the exact convention CreateGoalForm (Section 198) already
// established. All 4 fields are genuinely required (unlike CreateGoalForm's optional
// final step), so each gets its own real step rather than being grouped.
type CreateIkiminaStep = 'closed' | 'name' | 'contribution' | 'frequency' | 'members';
const IKIMINA_STEP_LABELS = ['Name', 'Contribution', 'Frequency', 'Members'];

function CreateIkiminaForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateIkiminaStep>('closed');
  const [name, setName] = useState('');
  const [contributionAmount, setContributionAmount] = useState('');
  const [cycleFrequencyDays, setCycleFrequencyDays] = useState('30');
  const [memberCap, setMemberCap] = useState('10');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setContributionAmount('');
    setCycleFrequencyDays('30');
    setMemberCap('10');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('name')}
      >
        <IconAdd size={16} /> New ikimina
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createIkimina(name.trim(), Number(contributionAmount), Number(cycleFrequencyDays), Number(memberCap));
      reset();
      showToast(t('toast.ikiminaCreated'));
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('contribution'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={IKIMINA_STEP_LABELS} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What's your group called?</h3>
            <button type="button" aria-label="Cancel" onClick={reset} style={{ background: 'none', border: 'none' }}>
              <IconClose size={20} color="var(--itunda-grey-500)" />
            </button>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. Umuryango" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'contribution') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(contributionAmount) > 0) setStep('frequency'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(contributionAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={IKIMINA_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much does each member contribute per round?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Contribution (RWF)" value={contributionAmount} onChange={(e) => setContributionAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'frequency') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('members')}>Next</IdsButton>}>
        <ProgressStepper activeStepIndex={2} steps={IKIMINA_STEP_LABELS} />
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button type="button" aria-label="Back" onClick={() => setStep('contribution')} style={{ background: 'none', border: 'none', display: 'flex' }}>
            <IconBack size={20} color="var(--itunda-grey-700)" />
          </button>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How often does each round happen?</h3>
        </div>
        <select
          value={cycleFrequencyDays} onChange={(e) => setCycleFrequencyDays(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
        >
          <option value="7">Weekly</option>
          <option value="30">Monthly</option>
        </select>
      </FullScreenFlow>
    );
  }

  return (
    <FullScreenFlow
      bottomCTA={
        <IdsButton fullWidth onClick={handleCreate} disabled={busy || !(Number(memberCap) >= 2 && Number(memberCap) <= 15)}>
          {busy ? 'Creating…' : 'Create ikimina'}
        </IdsButton>
      }
    >
      <ProgressStepper activeStepIndex={3} steps={IKIMINA_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('frequency')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How many members, at most?</h3>
      </div>
      <input
        type="number" min="2" max="15" required autoFocus placeholder="Max members (2-15)" value={memberCap} onChange={(e) => setMemberCap(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

function IkiminaDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<IkiminaDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [payoutMessage, setPayoutMessage] = useState<string | null>(null);
  const myUserId = getStoredUser()?.id;

  const load = () => {
    setError(null);
    fetchIkimina(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks), using detail?.balance so it's
  // already correct once detail loads.
  const animatedBalance = useCountUp(detail?.balance ?? 0);

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

  const { ikimina, members, currentRoundContributions } = detail;
  const isOrganizer = ikimina.organizerId === myUserId;
  const myMember = members.find((m) => m.userId === myUserId);
  const iContributed = currentRoundContributions.find((c) => c.userId === myUserId)?.contributed ?? false;
  const allContributed = currentRoundContributions.length > 0 && currentRoundContributions.every((c) => c.contributed);
  const pot = ikimina.contributionAmount * members.length;

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await inviteIkiminaMember(id, phoneNumber.trim());
      setPhoneNumber('');
      load();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): same
      // resolve-forward as the identical GroupAccount invite shape.
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

  const handleStart = async () => {
    setBusy(true);
    setError(null);
    try {
      await startIkiminaCycle(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleContribute = async () => {
    setBusy(true);
    setError(null);
    setPayoutMessage(null);
    try {
      const result = await contributeToIkimina(id);
      // Real bug fix: this contribution may have just completed the round, in which
      // case the backend already auto-triggered the payout -- surface that instead of
      // silently leaving the member to wonder why the round advanced.
      if (result.payout) {
        setPayoutMessage(`Round complete — ${result.payout.amount.toLocaleString('en-US')} RWF paid out.`);
      }
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handlePayout = async () => {
    setBusy(true);
    setError(null);
    setPayoutMessage(null);
    try {
      const result = await triggerIkiminaPayout(id);
      setPayoutMessage(`${result.amount.toLocaleString('en-US')} RWF paid out for round ${result.ikimina.currentRound - 1}.`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): distinct non-exclusive sections shown
  // together -- reused .itunda-flat-section for section-boundary dividers.
  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to ikimina</button>

      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{ikimina.name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0' }}>{animatedBalance.toLocaleString('en-US')} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          {ikimina.status === 'FORMING'
            ? `Forming — ${members.length} of up to ${ikimina.memberCap} members`
            : ikimina.status === 'ACTIVE'
              ? `Round ${ikimina.currentRound} of ${members.length} · ${ikimina.contributionAmount.toLocaleString('en-US')} RWF each · pot ${pot.toLocaleString('en-US')} RWF`
              : 'Every member has been paid — this ikimina is complete'}
        </p>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Rotation order</h3>
        {members.map((m) => {
          const contributed = currentRoundContributions.find((c) => c.userId === m.userId)?.contributed ?? false;
          return (
            <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <span>
                #{m.payoutOrder} {m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}{m.isOrganizer ? ' · Organizer' : ''}
              </span>
              <span style={{ color: m.hasReceivedPayout ? '#1E8E4F' : ikimina.status === 'ACTIVE' && contributed ? '#1E8E4F' : 'var(--itunda-grey-500)', fontWeight: 700 }}>
                {m.hasReceivedPayout ? '✓ Paid' : ikimina.status === 'ACTIVE' ? (contributed ? '✓ Contributed' : 'Pending') : ''}
              </span>
            </div>
          );
        })}
      </div>

      {ikimina.status === 'FORMING' && isOrganizer && (
        <div className="itunda-flat-section">
          <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={busy || members.length < 2} onClick={handleStart}>
            {busy ? '…' : members.length < 2 ? 'Invite at least 1 more member to start' : 'Start the cycle'}
          </button>
        </div>
      )}

      {ikimina.status === 'ACTIVE' && myMember && (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Round {ikimina.currentRound}</h3>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || iContributed} onClick={handleContribute}>
            {busy ? '…' : iContributed ? '✓ You contributed this round' : `Contribute ${ikimina.contributionAmount.toLocaleString('en-US')} RWF`}
          </button>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy || !allContributed} onClick={handlePayout}>
            {busy ? '…' : allContributed ? 'Release this round\'s payout' : 'Waiting for everyone to contribute'}
          </button>
          {payoutMessage && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: '#1E8E4F' }}>{payoutMessage}</p>}
        </div>
      )}

      {ikimina.status === 'FORMING' && isOrganizer && (
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

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model. See lib/sacco.ts's own doc comment for the full sourced
// account. Sibling to IkiminaSection above (both are Rwanda-specific, not sourced
// from Toss/Kakao/Naver/Coupang) but a genuinely distinct mechanic: real shares +
// periodic real dividends, not a rotating pot.
