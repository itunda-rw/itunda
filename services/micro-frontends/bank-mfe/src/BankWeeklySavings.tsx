import { useEffect, useRef, useState } from 'react';
import { IconAdd, IconBack, IconClose } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { BucketTransactionList } from './BucketDetailScreen';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { FullScreenFlow } from './FullScreenFlow';
import { IdsButton } from './IdsButton';
import { ProgressStepper } from './BankDashboard';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { useDeferredLoading } from './useDeferredLoading';
import { type BucketTransaction } from './lib/bucketTransaction';
import { ApiError } from './lib/api';
import {
  cancelWeeklySavingsPlan, createWeeklySavingsPlan, fetchWeeklySavingsPlan, fetchWeeklySavingsPlanTransactions,
  fetchWeeklySavingsPlans, withdrawWeeklySavingsPlan, WEEKLY_SAVINGS_ESCALATION_RATES,
  WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS, WEEKLY_SAVINGS_TERM_WEEKS, type WeeklySavingsPlan, type WeeklySavingsPlanDetail,
} from './lib/weeklySavings';

function escalationLabel(rate: number): string {
  return rate === 0 ? 'Flat (no step-up)' : `+${Math.round(rate * 100)}% every ${WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks`;
}

function WeeklySavingsPlanDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<WeeklySavingsPlanDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket ledger (2026-08-31) -- see BucketTransactionList's own doc
  // comment. Replaces the old "Installments" list below with the real transaction
  // ledger this plan's own dedicated account always had, just never exposed.
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);
  // Real fix (2026-08-10) -- see the Talk conversation view's own identical
  // pendingDeviceRetryRef for the full account: cancel and withdraw share this one
  // flag+prompt, so retrying has to redo whichever one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchWeeklySavingsPlanTransactions(id).then(setTransactions).catch(() => {});
  };
  useEffect(load, []);

  // Same real device step-up gate as GroupAccountDetailView/GoalCard's deposit/
  // withdraw handlers above -- cancel/withdraw both move real money out of this
  // plan's account, so an untrusted device hits the same DEVICE_NOT_VERIFIED 403.
  const handleCancel = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await cancelWeeklySavingsPlan(id);
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
      const result = await withdrawWeeklySavingsPlan(id);
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
  const pct = Math.min(100, Math.round((plan.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
  const currentRate = plan.streakBroken ? plan.baseRate : plan.baseRate + plan.bonusRate;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to 26-week savings</button>

      <div className="itunda-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>{plan.name} · Week {plan.weeksElapsed} of {WEEKLY_SAVINGS_TERM_WEEKS}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{accountBalance.toLocaleString('en-US')} RWF</p>
        <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'rgba(255,255,255,0.3)', marginTop: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, backgroundColor: '#fff' }} />
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '10px', opacity: 0.9 }}>
          {plan.installmentsCollected} installment{plan.installmentsCollected === 1 ? '' : 's'} collected · earning {currentRate}% real annual rate
        </p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.9 }}>
          {plan.streakBroken
            ? 'Streak broken — bonus rate forfeited for the rest of this plan'
            : `On streak — stay unbroken to keep the +${plan.bonusRate}% bonus at maturity`}
        </p>
      </div>

      {/* Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
          together -- reused .itunda-flat-section for section-boundary dividers.
          The hero balance card above deliberately kept its gradient itunda-card
          styling -- a real branded treatment, not reflexive wrapping. */}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Plan details</h3>
        <Row label="Status" value={plan.status} />
        <Row label="Base weekly amount" value={`${plan.baseWeeklyAmount.toLocaleString('en-US')} RWF`} />
        <Row label="Escalation" value={escalationLabel(plan.escalationRate)} />
        <Row label="Base rate + streak bonus" value={`${plan.baseRate}% + ${plan.bonusRate}%`} />
        {plan.status === 'ACTIVE' && <Row label="Next installment due" value={new Date(plan.nextInstallmentDueAt).toLocaleDateString()} />}
        {plan.totalInterestPaid != null && <Row label="Interest paid" value={`${plan.totalInterestPaid.toLocaleString('en-US')} RWF`} />}
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
          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              conditional action section. */}
          {plan.status === 'ACTIVE' && (
            <div style={{ padding: '10px 0' }}>
              {confirmingCancel ? (
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '10px' }}>
                    Cancelling now pays out your principal plus base-rate interest, but permanently forfeits the +{plan.bonusRate}% streak bonus. Continue?
                  </p>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setConfirmingCancel(false)} disabled={busy}>Keep saving</button>
                    <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} onClick={handleCancel} disabled={busy}>{busy ? '…' : 'Cancel plan'}</button>
                  </div>
                </div>
              ) : (
                <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setConfirmingCancel(true)} disabled={busy}>
                  Cancel plan (early withdrawal)
                </button>
              )}
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

export function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
      <span style={{ color: 'var(--itunda-grey-500)' }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up) -- see
// CreateIkiminaForm's own identical doc comment above for the full real sourcing.
// This card asked 3 real decisions at once (plan name, weekly amount, escalation
// rate) -- rebuilt as a real step flow with the real ProgressStepper indicator.
type CreateWeeklySavingsPlanStep = 'closed' | 'intro' | 'name' | 'amount' | 'escalation';
const WEEKLY_SAVINGS_STEP_LABELS = ['Name', 'Amount', 'Escalation'];

function CreateWeeklySavingsPlanForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateWeeklySavingsPlanStep>('closed');
  const [name, setName] = useState('');
  const [baseWeeklyAmount, setBaseWeeklyAmount] = useState('');
  const [escalationRate, setEscalationRate] = useState(0.10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setBaseWeeklyAmount('');
    setEscalationRate(0.10);
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('intro')}
      >
        <IconAdd size={16} /> New 26-week savings plan
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createWeeklySavingsPlan(name.trim(), Number(baseWeeklyAmount), escalationRate);
      reset();
      showToast(t('toast.weeklyPlanStarted'));
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real Toss product-intro pattern (rule 13) -- see OpenForeignAccountFlow and
  // CreateGrow31SavingsPlanForm's own 'intro' step, the first two applications. Not
  // counted in WEEKLY_SAVINGS_STEP_LABELS's progress -- it's a preamble, not a wizard
  // step. WeeklySavings' bonus is stricter than Grow31's: Grow31 locks in a partial
  // bonus for the longest streak reached even after a break, but WeeklySavings'
  // bonus is all-or-nothing (WeeklySavingsService.matures: `plan.baseRate + (if
  // (plan.streakBroken) 0.0 else plan.bonusRate)`) -- one missed week, or any early
  // withdrawal, forfeits it permanently. Worth stating plainly rather than blurring
  // the two products' real mechanics together.
  if (step === 'intro') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('name')}>Continue</IdsButton>}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <h2 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, maxWidth: '260px' }}>A weekly habit that grows on its own</h2>
          <button type="button" aria-label="Close" onClick={reset} style={{ background: 'none', border: 'none', display: 'flex', padding: '4px' }}>
            <IconClose size={22} color="var(--itunda-grey-500)" />
          </button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>A real 26-week term deposit</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Like KakaoBank's 26주적금: your weekly amount auto-debits from your main account every week for {WEEKLY_SAVINGS_TERM_WEEKS} weeks — nothing to top up manually.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Base 5% + a 3% bonus for staying unbroken</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              The 3% bonus is all-or-nothing: miss even one week's installment, or withdraw early, and the bonus is forfeited for good — unlike a 31-day plan's partial-credit streak, this one doesn't have a middle ground.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Your weekly amount can step up automatically</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Choose an escalation rate and your weekly amount compounds up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks — start small and build up, instead of committing to one fixed amount for all 26 weeks.
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
          <ProgressStepper activeStepIndex={0} steps={WEEKLY_SAVINGS_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('intro')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What are you saving toward?</h3>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. New Laptop Fund" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'amount') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(baseWeeklyAmount) > 0) setStep('escalation'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(baseWeeklyAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={WEEKLY_SAVINGS_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much per week, to start?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Base weekly amount (RWF)" value={baseWeeklyAmount} onChange={(e) => setBaseWeeklyAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy}>{busy ? 'Creating…' : 'Create plan'}</IdsButton>}>
      <ProgressStepper activeStepIndex={2} steps={WEEKLY_SAVINGS_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('amount')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Step up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks?</h3>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '8px' }}>
        {WEEKLY_SAVINGS_ESCALATION_RATES.map((rate) => (
          <button
            key={rate}
            type="button"
            onClick={() => setEscalationRate(rate)}
            className={escalationRate === rate ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
            style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {rate === 0 ? 'Flat' : `+${Math.round(rate * 100)}%`}
          </button>
        ))}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

export function WeeklySavingsSection() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<WeeklySavingsPlan[] | null>(null);
  const showSkeleton = useDeferredLoading(plans === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlans().then(setPlans).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <WeeklySavingsPlanDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      {/* Real gap found live (2026-08-31, direct user follow-up: "keep improving
          itunda bank to more like toss bank"): matches the real Toss Bank
          reference's own catalog pattern (e.g. "31일 적금 -- 1%~10% p.a." shown
          directly in the product list, no tap required) -- Android's own
          BankHubScreen already states this exact real rate inline
          ("$BANK_HUB_WEEKLY_SAVINGS_BASE_RATE% base rate, escalates weekly"),
          web/iOS never did. Sourced from WeeklySavingsService's own real
          BASE_RATE/BONUS_RATE constants, not invented. */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>26-week savings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>5% base rate, escalates weekly</p>
      <CreateWeeklySavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : plans.length === 0 ? (
        <EmptyState message="No 26-week savings plans yet — start one with an escalating weekly auto-debit and a streak-gated bonus rate." />
      ) : (
        plans.map((p) => {
          const pct = Math.min(100, Math.round((p.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
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
                {p.currentAmount.toLocaleString('en-US')} RWF · week {p.weeksElapsed}/{WEEKLY_SAVINGS_TERM_WEEKS}
                {p.streakBroken ? ' · streak broken' : ' · on streak'}
              </p>
              <div style={{ height: '5px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, backgroundColor: p.streakBroken ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)' }} />
              </div>
            </button>
          );
        })
      )}
    </div>
  );
}

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- see
// lib/grow31Savings.ts's own doc comment. Distinct from WeeklySavings above: a deposit
// is an explicit daily user action ("Save today"), not a scheduled auto-debit.
