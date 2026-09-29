import { useState } from 'react';
import { ProgressStepper } from './BankDashboard';
import { IconAdd, IconBack, IconClose } from './icons/ItundaIcons';
import { BucketDetailScreen } from './BucketDetailScreen';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { FullScreenFlow } from './FullScreenFlow';
import { IdsButton } from './IdsButton';
import { showToast } from './Toast';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  depositToGoal, fetchGoalTransactions, createGoal, withdrawFromGoal, type SavingsGoal,
} from './lib/savings';

export function GoalCard({ goal, onChanged }: { goal: SavingsGoal; onChanged: () => void }) {
  const { t } = useI18n();
  // Real gap found live (2026-08-31, direct user reference against Toss's own real
  // 보관하기/나눠모으기 pockets -- every one supports both 채우기 (fill) and 꺼내기
  // (withdraw), never a one-way deposit): this card only ever let money go IN, with no
  // way back out -- see backend SavingsService.withdrawFromGoal's own doc comment for
  // the full account.
  const [mode, setMode] = useState<'closed' | 'deposit' | 'withdraw'>('closed');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.tsx's own doc
  // comment. Deposit/withdraw stays on this card's own existing inline form below --
  // the detail screen is a pure ledger viewer, opened by tapping the goal's own row.
  const [showDetail, setShowDetail] = useState(false);
  const pct = Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100));

  const handleDeposit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await depositToGoal(goal.id, Number(amount));
      // Real Toss UX-writing "Find Hidden Emotion" principle (toss.tech/article/
      // 8-writing-principles-of-toss, their own example: a congratulatory message
      // when a loan is fully paid off, not just a transaction confirmation) --
      // itunda already does this for loan payoff (LoansView.tsx's payoffMessage,
      // "one less thing to carry"), and the backend already fires a push
      // notification on goal completion (SavingsService.notifyGoalCompleted), but
      // the person actually watching THIS screen at the exact moment they hit
      // their target saw nothing beyond a small "· Completed" tag they'd only
      // notice on a later visit. Detects the active->completed transition from
      // the deposit response itself, not a guess from the pre-call goal prop.
      if (goal.status !== 'completed' && result.goal.status === 'completed') {
        showToast(t('toast.goalCompleted', { goalName: goal.name }));
      }
      setAmount('');
      setMode('closed');
      onChanged();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await withdrawFromGoal(goal.id, Number(amount));
      setAmount('');
      setMode('closed');
      onChanged();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      {showDetail && (
        <BucketDetailScreen
          title={goal.name}
          subtitle="Savings Goal"
          balanceText={`${goal.currentAmount.toLocaleString('en-US')} RWF`}
          secondaryStat={{ label: 'Target', value: `${goal.targetAmount.toLocaleString('en-US')} RWF` }}
          fetchTransactions={() => fetchGoalTransactions(goal.id)}
          onBack={() => setShowDetail(false)}
        />
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button onClick={() => setShowDetail(true)} style={{ textAlign: 'left' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{goal.name}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {goal.currentAmount.toLocaleString('en-US')} / {goal.targetAmount.toLocaleString('en-US')} RWF
            {goal.status === 'completed' && ' · Completed 🎉'}
          </p>
        </button>
        <div style={{ display: 'flex', gap: '6px' }}>
          {goal.currentAmount > 0 && (
            <button
              className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              onClick={() => setMode((m) => (m === 'withdraw' ? 'closed' : 'withdraw'))}
            >
              Withdraw
            </button>
          )}
          {goal.status === 'active' && (
            <button
              className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              onClick={() => setMode((m) => (m === 'deposit' ? 'closed' : 'deposit'))}
            >
              Deposit
            </button>
          )}
        </div>
      </div>
      <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '10px', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${pct}%`, backgroundColor: 'var(--itunda-indigo)' }} />
      </div>
      {goal.monthlyContribution > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>
          Auto-saves {goal.monthlyContribution.toLocaleString('en-US')} RWF/month
        </p>
      )}
      {mode !== 'closed' && (
        needsDeviceVerification ? (
          <div style={{ marginTop: '10px' }}>
            {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
                full account. handleDeposit/handleWithdraw reset needsDeviceVerification
                themselves. */}
            <DeviceStepUpPrompt onVerified={() => (mode === 'deposit' ? handleDeposit() : handleWithdraw())} onCancel={() => setMode('closed')} />
          </div>
        ) : (
          <form onSubmit={mode === 'deposit' ? handleDeposit : handleWithdraw} style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
            <input
              type="number" min="1" max={mode === 'withdraw' ? goal.currentAmount : undefined} required value={amount} onChange={(e) => setAmount(e.target.value)}
              placeholder="Amount (RWF)"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {busy ? '…' : mode === 'deposit' ? 'Add' : 'Withdraw'}
            </button>
          </form>
        )
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (2026-08-19, direct user-confirmed sourcing:
// Toss's own real, published Product Principles doc names this explicitly -- "하나의
// 화면은 하나의 메시지만 표현한다," one screen expresses one message only, excess
// information actively removed rather than just deprioritized). This card used to ask
// 3 real decisions (goal name, target amount, monthly auto-save) on one page at once --
// a real, concrete violation, not a stylistic nitpick. Rebuilt as a real step flow,
// matching the exact step-machine convention TransferFlow (Section 189) already
// established for the identical reason. Also closes a real, separate capability gap
// found along the way: lib/savings.ts's own createGoal already accepts a real
// targetDate (Android's NewSavingsGoalDialog already collects it), but this form never
// did -- added as part of the same optional final step, not a second unrelated change.
type CreateGoalStep = 'closed' | 'name' | 'amount' | 'plan';

const GOAL_STEP_LABELS = ['Name', 'Amount', 'Auto-save'];

export function CreateGoalForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateGoalStep>('closed');
  const [name, setName] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [monthlyContribution, setMonthlyContribution] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setTargetAmount('');
    setMonthlyContribution('');
    setTargetDate('');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('name')}
      >
        <IconAdd size={16} /> New savings goal
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createGoal(name.trim(), Number(targetAmount), monthlyContribution ? Number(monthlyContribution) : undefined, targetDate || undefined);
      reset();
      showToast(t('toast.savingsGoalCreated'));
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('amount'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={GOAL_STEP_LABELS} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What are you saving for?</h3>
            <button type="button" aria-label="Cancel" onClick={reset} style={{ background: 'none', border: 'none' }}>
              <IconClose size={20} color="var(--itunda-grey-500)" />
            </button>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. Emergency Fund" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'amount') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(targetAmount) > 0) setStep('plan'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(targetAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={GOAL_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much do you want to save for &ldquo;{name.trim()}&rdquo;?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Target amount (RWF)" value={targetAmount} onChange={(e) => setTargetAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy}>{busy ? 'Creating…' : 'Create goal'}</IdsButton>}>
      <ProgressStepper activeStepIndex={2} steps={GOAL_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('amount')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Add auto-save details (optional)</h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '12px' }}>
        <input
          type="number" min="0" placeholder="Monthly auto-save (optional)" value={monthlyContribution} onChange={(e) => setMonthlyContribution(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="date" placeholder="Target date (optional)" value={targetDate} onChange={(e) => setTargetDate(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
    </FullScreenFlow>
  );
}

// Real Kakao Bank 모임통장 (group/shared account) -- see lib/groupAccounts.ts's doc
// comment. Backend enforces real owner-only withdrawal/invite authority; this view's
// job is just to reflect that honestly (buttons the caller can't actually use are
// hidden, not disabled-with-no-explanation).
