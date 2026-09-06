import { useEffect, useState } from 'react';
import { ErrorCard } from './EmptyState';
import { BucketDetailScreen } from './BucketDetailScreen';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { useCountUp } from './hooks/useCountUp';
import { useI18n } from './i18n/I18nContext';
import { useDeferredLoading } from './useDeferredLoading';
import { ApiError } from './lib/api';
import {
  claimInterest, fetchDepositProtectionStatus,
  fetchInterestJar, fetchInterestJarTransactions, fetchRoundUpSettings, ROUND_UP_INCREMENTS, setRoundUpSettings,
  type DepositProtectionStatus, type InterestJar, type RoundUpSettings, type SavingsGoal,
} from './lib/savings';

export function RoundUpCard({ goals }: { goals: SavingsGoal[] }) {
  const { t } = useI18n();
  const [settings, setSettings] = useState<RoundUpSettings | null | undefined>(undefined);
  const [increment, setIncrement] = useState<number>(100);
  const [goalId, setGoalId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchRoundUpSettings()
      .then((s) => {
        setSettings(s);
        if (s) { setIncrement(s.roundToNearest); setGoalId(s.targetGoalId ?? ''); }
      })
      .catch(() => setSettings(null));
  };
  useEffect(load, []);

  const handleToggle = async (enabled: boolean) => {
    if (enabled && !goalId) { setError('Choose a savings goal first.'); return; }
    setBusy(true);
    setError(null);
    try {
      const updated = await setRoundUpSettings(enabled, increment, enabled ? goalId : (settings?.targetGoalId ?? null));
      setSettings(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (settings === undefined) return null;

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Round-up savings</h3>
        {settings?.enabled && (
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => handleToggle(false)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {busy ? '…' : 'Turn off'}
          </button>
        )}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {settings?.enabled ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Every transfer rounds up to the nearest {settings.roundToNearest.toLocaleString('en-US')} RWF, saved into your goal.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
            Round up every transfer to a real RWF increment and auto-save the spare change.
          </p>
          <div style={{ display: 'flex', gap: '6px' }}>
            {ROUND_UP_INCREMENTS.map((v) => (
              <button
                key={v} type="button" onClick={() => setIncrement(v)}
                className={increment === v ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px' }}
              >
                {v.toLocaleString('en-US')} RWF
              </button>
            ))}
          </div>
          <select
            value={goalId} onChange={(e) => setGoalId(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          >
            <option value="">Choose a savings goal</option>
            {goals.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !goalId} onClick={() => handleToggle(true)}>
            {busy ? 'Turning on…' : 'Turn on round-up'}
          </button>
        </div>
      )}
    </div>
  );
}

export function InterestJarCard() {
  const { t } = useI18n();
  const [jar, setJar] = useState<InterestJar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);
  const showSkeleton = useDeferredLoading(jar === null);
  const [claimMsg, setClaimMsg] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.tsx's own
  // doc comment. No Fill/Withdraw here -- money auto-accrues off the linked balance,
  // "claim" already has its own dedicated button on this card.
  const [showDetail, setShowDetail] = useState(false);

  const load = () => {
    setError(null);
    fetchInterestJar().then(setJar).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks), using jar?.balance so it's
  // already correct once jar loads.
  const animatedBalance = useCountUp(jar?.balance ?? 0);

  const handleClaim = async () => {
    setClaiming(true);
    setError(null);
    setClaimMsg(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await claimInterest();
      setClaimMsg(result.message);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setClaiming(false);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (jar === null) return showSkeleton ? <div className="skeleton" style={{ height: '140px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  const canClaim = jar.earnedThisMonth > 0;

  return (
    <div style={{ marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)', padding: '24px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
      {/* Real methodology-transparency fix (2026-08-11): jar.rate is the ANNUAL rate
          SavingsService.accrueInterest() divides by 365 to get the real daily accrual
          (dailyRate = rate/100/365) -- this copy called it "daily interest" outright,
          which is the actual number times ~365 too high a read for anyone taking it
          literally. Now states the real methodology instead of a bare adjective. */}
      {showDetail && (
        <BucketDetailScreen
          title="Interest Jar"
          subtitle="Safe Box"
          balanceText={`${jar.balance.toLocaleString('en-US')} RWF`}
          secondaryStat={{ label: 'Earned all-time', value: `${jar.earnedTotal.toLocaleString('en-US')} RWF` }}
          fetchTransactions={fetchInterestJarTransactions}
          onBack={() => setShowDetail(false)}
        />
      )}
      <button onClick={() => setShowDetail(true)} style={{ display: 'block', width: '100%', textAlign: 'left' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>Safe Box · {jar.rate}% annual, accrued daily on your balance</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{animatedBalance.toLocaleString('en-US')} RWF</p>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '10px' }}>
          <div>
            {/* Real fix (2026-08-11): interest now auto-credits to the account the
                instant it accrues (see backend SavingsService.accrueInterest's own
                doc comment, matching real Toss Bank passbook interest) -- this money
                is already in jar.balance above, not sitting unclaimed. */}
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.8 }}>Earned this month</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{jar.earnedThisMonth.toLocaleString('en-US')} RWF</p>
          </div>
          <div style={{ textAlign: 'right' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.8 }}>Earned all-time</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{jar.earnedTotal.toLocaleString('en-US')} RWF</p>
          </div>
        </div>
      </button>
      {needsDeviceVerification ? (
        <div style={{ marginTop: '14px' }}>
          {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
              full account. handleClaim resets needsDeviceVerification itself. */}
          <DeviceStepUpPrompt onVerified={handleClaim} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        <button
          className="itunda-btn"
          onClick={handleClaim}
          disabled={!canClaim || claiming}
          style={{ marginTop: '14px', width: '100%', backgroundColor: '#fff', color: 'var(--itunda-indigo)', fontWeight: 700, opacity: canClaim ? 1 : 0.6 }}
        >
          {claiming ? 'Clearing…' : canClaim ? `OK, ${jar.earnedThisMonth.toLocaleString('en-US')} RWF added` : 'Nothing new this month yet'}
        </button>
      )}
      {claimMsg && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '8px' }}>{claimMsg}</p>}
    </div>
  );
}

// Real Deposit Protection Fund card (2026-08-11) -- see DepositProtectionFund.kt's own
// doc comment: rather than just disclosing an absence of real banking protections, this
// shows the real, working, ledger-backed reserve itunda maintains as its own internal
// simulation of what real deposit protection could look like -- same "real mechanics,
// honestly labeled as itunda's own scheme" discipline this codebase already applies to
// VUP/RSE/SACCO.
export function DepositProtectionCard() {
  const [status, setStatus] = useState<DepositProtectionStatus | null>(null);

  useEffect(() => {
    fetchDepositProtectionStatus().then(setStatus).catch(() => {
      // Non-critical -- the disclosure copy below this card still renders without it.
    });
  }, []);

  if (status === null) return null;

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, margin: '0 0 8px' }}>Deposit Protection Fund (simulation)</h3>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your covered balance</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 650 }}>{status.yourCoveredBalance.toLocaleString('en-US')} RWF</p>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '4px' }}>
        Covered up to {status.coverageCapPerUser.toLocaleString('en-US')} RWF per user
      </p>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>
        itunda&apos;s reserve: {status.fundReserveBalance.toLocaleString('en-US')} RWF
      </p>
    </div>
  );
}

