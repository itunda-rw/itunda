import { useEffect, useRef, useState } from 'react';
import { IconAdd, IconBack, IconClose } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { BucketTransactionList } from './BucketDetailScreen';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { FullScreenFlow } from './FullScreenFlow';
import { IdsButton } from './IdsButton';
import { ProgressStepper } from './BankDashboard';
import { Row } from './BankWeeklySavings';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { useDeferredLoading } from './useDeferredLoading';
import { type BucketTransaction } from './lib/bucketTransaction';
import { ApiError } from './lib/api';
import {
  cancelGrow31SavingsPlan, createGrow31SavingsPlan, depositGrow31SavingsToday, fetchGrow31SavingsPlan,
  fetchGrow31SavingsPlanTransactions, fetchGrow31SavingsPlans, grow31BonusRateForStreak, withdrawGrow31SavingsPlan,
  GROW31_TERM_DAYS, type Grow31SavingsPlan, type Grow31SavingsPlanDetail,
} from './lib/grow31Savings';

function Grow31SavingsPlanDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<Grow31SavingsPlanDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10 pattern, same as WeeklySavingsPlanDetailView above): deposit/
  // cancel/withdraw all share this one flag+prompt, so retrying has to redo whichever
  // one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  // Real per-bucket ledger (2026-08-31) -- see BucketTransactionList's own doc
  // comment. Replaces the old "Deposits" list below with the real transaction
  // ledger this plan's own dedicated account always had, just never exposed.
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);

  const load = () => {
    setError(null);
    fetchGrow31SavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchGrow31SavingsPlanTransactions(id).then(setTransactions).catch(() => {});
  };
  useEffect(load, []);

  const handleDeposit = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await depositGrow31SavingsToday(id);
      setDetail(result);
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

  const handleCancel = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await cancelGrow31SavingsPlan(id);
      setMessage(result.message);
      setConfirmingCancel(false);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleCancel;
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
      const result = await withdrawGrow31SavingsPlan(id);
      setMessage(result.message);
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

  const { plan, accountBalance } = detail;
  const pct = Math.min(100, Math.round((plan.daysElapsed / GROW31_TERM_DAYS) * 100));
  const bonus = grow31BonusRateForStreak(plan.longestStreak);
  const today = new Date().toISOString().slice(0, 10);
  const alreadyDepositedToday = plan.lastDepositDate === today;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to 31-day savings</button>

      <div className="itunda-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>{plan.name} · Day {Math.min(plan.daysElapsed, GROW31_TERM_DAYS)} of {GROW31_TERM_DAYS}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{accountBalance.toLocaleString('en-US')} RWF</p>
        <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'rgba(255,255,255,0.3)', marginTop: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, backgroundColor: '#fff' }} />
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '10px', opacity: 0.9 }}>
          Current streak {plan.currentStreak} days · longest {plan.longestStreak} days
        </p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.9 }}>
          {bonus > 0 ? `+${bonus}% bonus locked in on top of the ${plan.baseRate}% base rate` : 'Save 3 days in a row to unlock your first bonus tier'}
        </p>
      </div>

      {/* Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
          together -- reused .itunda-flat-section for section-boundary dividers.
          The hero balance card above deliberately kept its gradient itunda-card
          styling -- a real branded treatment, not reflexive wrapping. */}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Plan details</h3>
        <Row label="Status" value={plan.status} />
        <Row label="Daily amount" value={`${plan.dailyAmount.toLocaleString('en-US')} RWF`} />
        <Row label="Base rate" value={`${plan.baseRate}%`} />
        {plan.totalInterestPaid != null && <Row label="Total interest paid" value={`${plan.totalInterestPaid.toLocaleString('en-US')} RWF`} />}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Transactions</h3>
        <BucketTransactionList transactions={transactions} />
      </div>

      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', marginBottom: '10px' }}>{message}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}

      {needsDeviceVerification ? (
        <DeviceStepUpPrompt
          onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
          onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); setConfirmingCancel(false); }}
        />
      ) : (
        <>
          {plan.status === 'ACTIVE' && !confirmingCancel && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {!alreadyDepositedToday ? (
                <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={handleDeposit} disabled={busy}>
                  {busy ? '…' : `Save today (+${plan.dailyAmount.toLocaleString('en-US')} RWF)`}
                </button>
              ) : (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green)', fontWeight: 600 }}>
                  You've already saved today — come back tomorrow to keep your streak.
                </p>
              )}
              <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setConfirmingCancel(true)} disabled={busy}>
                Cancel plan (early withdrawal)
              </button>
            </div>
          )}

          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              conditional confirmation section. */}
          {plan.status === 'ACTIVE' && confirmingCancel && (
            <div style={{ padding: '10px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '10px' }}>
                Cancelling now forfeits your streak bonus — you'll only get principal plus base-rate interest, paid out immediately. This can't be undone.
              </p>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setConfirmingCancel(false)} disabled={busy}>Keep plan</button>
                <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} onClick={handleCancel} disabled={busy}>{busy ? '…' : 'Confirm cancel'}</button>
              </div>
            </div>
          )}

          {plan.status === 'MATURED' && !plan.withdrawnAt && (
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={handleWithdraw} disabled={busy}>
              {busy ? '…' : `Withdraw ${accountBalance.toLocaleString('en-US')} RWF to main account`}
            </button>
          )}
        </>
      )}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up) -- see
// CreateIkiminaForm's own identical doc comment above for the full real sourcing.
// This card asked 2 real decisions at once (plan name, daily amount).
type CreateGrow31Step = 'closed' | 'intro' | 'name' | 'amount';
const GROW31_STEP_LABELS = ['Name', 'Daily amount'];
// Real, sourced streak-bonus tiers (tossbank.com/articles/savings-account,
// g-enews.com 2026-08-06, mirrored from Grow31SavingsService.bonusRateForStreak) --
// shown in full on the intro step (rule 13) rather than the single "up to +10%"
// summary line the name step used to carry alone.
const GROW31_BONUS_TIERS = [3, 7, 14, 21, 31] as const;

function CreateGrow31SavingsPlanForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateGrow31Step>('closed');
  const [name, setName] = useState('');
  const [dailyAmount, setDailyAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setDailyAmount('');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('intro')}
      >
        <IconAdd size={16} /> New 31-day plan
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createGrow31SavingsPlan(name.trim(), Number(dailyAmount));
      reset();
      showToast(t('toast.grow31PlanStarted'));
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real Toss product-intro pattern (rule 13, 2026-08-26): a dedicated screen
  // explaining the real mechanics before the creation form starts, not part of the
  // Name/Daily-amount progress count -- matches ForeignCurrencyView's own
  // OpenForeignAccountFlow, the first application of this pattern.
  if (step === 'intro') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('name')}>Continue</IdsButton>}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <h2 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, maxWidth: '260px' }}>Save a little every day, earn more the longer you keep it up</h2>
          <button type="button" aria-label="Close" onClick={reset} style={{ background: 'none', border: 'none', display: 'flex', padding: '4px' }}>
            <IconClose size={22} color="var(--itunda-grey-500)" />
          </button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>One small deposit, every day, for {GROW31_TERM_DAYS} days</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Pick a fixed amount you can realistically save every single day. A base 1% rate applies from day one.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>The longer your unbroken streak, the higher your bonus</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5, marginBottom: '10px' }}>
              Your bonus rate is locked in by the longest unbroken run of daily deposits you reach:
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {GROW31_BONUS_TIERS.map((days) => (
                <div key={days} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span style={{ color: 'var(--itunda-grey-700)' }}>{days === GROW31_TERM_DAYS ? `${days} days (full term)` : `${days}-day streak`}</span>
                  <span style={{ fontWeight: 700 }}>+{grow31BonusRateForStreak(days)}%</span>
                </div>
              ))}
            </div>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Miss a day? You keep what you already earned</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              A missed day resets your current streak, but the longest streak you already reached still locks in that bonus rate at maturity — it isn't lost.
            </p>
          </div>
        </div>
      </FullScreenFlow>
    );
  }

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('amount'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={GROW31_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('intro')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Name your 31-day streak</h3>
          </div>
          <input
            type="text" required autoFocus placeholder="Plan name" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy || !(Number(dailyAmount) > 0)}>{busy ? 'Creating…' : 'Create plan'}</IdsButton>}>
      <ProgressStepper activeStepIndex={1} steps={GROW31_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much can you save every day?</h3>
      </div>
      <input
        type="number" min="1" required autoFocus placeholder="Daily amount (RWF)" value={dailyAmount} onChange={(e) => setDailyAmount(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

export function Grow31SavingsSection() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<Grow31SavingsPlan[] | null>(null);
  const showSkeleton = useDeferredLoading(plans === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGrow31SavingsPlans().then(setPlans).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <Grow31SavingsPlanDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      {/* Real gap found live (2026-08-31) -- see WeeklySavingsSection's identical
          fix above. Sourced from Grow31SavingsService.bonusRateForStreak's own
          real tier table (base 1%, up to +10% at a 31-day streak). */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>31-day savings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>Daily streak, up to 10% bonus rate</p>
      <CreateGrow31SavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : plans.length === 0 ? (
        <EmptyState message="No 31-day plans yet — save a small fixed amount every real day for an escalating streak bonus." />
      ) : (
        plans.map((p) => {
          const pct = Math.min(100, Math.round((p.daysElapsed / GROW31_TERM_DAYS) * 100));
          const bonus = grow31BonusRateForStreak(p.longestStreak);
          return (
            // Real fix (2026-08-24): see accounts.map's own identical comment above.
            <button
              key={p.id}
              onClick={() => setOpenId(p.id)}
              style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{p.name}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{p.status}</p>
              </div>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {p.totalSaved.toLocaleString('en-US')} RWF · day {Math.min(p.daysElapsed, GROW31_TERM_DAYS)}/{GROW31_TERM_DAYS} · streak {p.currentStreak}
              </p>
              <div style={{ height: '5px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, backgroundColor: bonus > 0 ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }} />
              </div>
            </button>
          );
        })
      )}
    </div>
  );
}

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 153) -- see lib/upfrontDeposit.ts's own doc comment. The one product in this
// module where opening pays real, immediately-spendable interest -- distinct from every
// accrue-then-claim product above (InterestJar/RoundUp/goals/weekly savings).
