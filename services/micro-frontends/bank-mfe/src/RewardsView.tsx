// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Toss-style rewards/mission-task center (own lib/rewards.ts data layer, exactly
// one external call site -- `{tab === 'REWARDS' && <RewardsView />}`). Note:
// `fetchRewardTasks`/`RewardTasksResult` stay ALSO imported in BankDashboard.tsx
// itself -- PayHub's own rewards-preview widget uses them too, a real shared
// dependency, not a leftover.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  fetchRewardTasks, fetchReferralInfo, claimRewardTask, reportSteps, fetchTodaySteps, fetchPet,
  type RewardTasksResult, type ReferralInfo, type StepRewardTierInfo, type Pet,
} from './lib/rewards';

// Real Toss-style rewards/mission-task center -- see lib/rewards.ts's own doc
// comment: real on Android/iOS since day one via the Saronite mini-app bridge, but
// bank-mfe (the actual banking app) never had a client for it. Same task-list +
// referral-code + step-counter shape those native bridges already expose.
// Real Naver Pay 페이펫-inspired collectible companion -- see lib/rewards.ts's own
// fetchPet doc comment. Self-contained (its own load effect), so a fetch failure just
// hides this card rather than blocking the rest of RewardsView from rendering.
function PetCard() {
  const [pet, setPet] = useState<Pet | null>(null);
  useEffect(() => { fetchPet().then(setPet).catch(() => setPet(null)); }, []);
  if (!pet) return null;
  return (
    <div className="itunda-card" style={{ padding: '20px', textAlign: 'center' }}>
      <div style={{ fontSize: '48px' }}>{pet.emoji}</div>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginTop: '4px' }}>{pet.stageName}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        Level {pet.level} · {pet.claimedTaskCount} task{pet.claimedTaskCount === 1 ? '' : 's'} · {pet.activeRewardDays} active day{pet.activeRewardDays === 1 ? '' : 's'}
      </p>
    </div>
  );
}

export function RewardsView() {
  const { t } = useI18n();
  const [tasks, setTasks] = useState<RewardTasksResult | null>(null);
  const [referral, setReferral] = useState<ReferralInfo | null>(null);
  const [todaySteps, setTodaySteps] = useState<number | null>(null);
  // Real lottery-style bonus (item 248, docs/DESIGN_REFERENCES.md Section 15) -- the
  // real, stated odds per tier, shown up front rather than only surfacing after a win.
  const [stepTiers, setStepTiers] = useState<StepRewardTierInfo[]>([]);
  const [stepsInput, setStepsInput] = useState('');
  const [claimingId, setClaimingId] = useState<string | null>(null);
  const [reportingSteps, setReportingSteps] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchRewardTasks().then(setTasks).catch(() => setTasks(null));
    fetchReferralInfo().then(setReferral).catch(() => setReferral(null));
    fetchTodaySteps().then((r) => { setTodaySteps(r.steps); setStepTiers(r.tiers); }).catch(() => setTodaySteps(null));
  };
  useEffect(load, []);

  const handleClaim = async (taskId: string) => {
    setClaimingId(taskId);
    setError(null);
    setMessage(null);
    try {
      const result = await claimRewardTask(taskId);
      setMessage(`${result.message} (+${result.rewardAmount.toLocaleString('en-US')} RWF)`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setClaimingId(null);
    }
  };

  const handleReportSteps = async () => {
    const steps = Number(stepsInput);
    if (!Number.isFinite(steps) || steps <= 0) { setError('Enter a real step count.'); return; }
    setReportingSteps(true);
    setError(null);
    setMessage(null);
    try {
      const result = await reportSteps(steps);
      setTodaySteps(result.steps);
      setStepTiers(result.tiers);
      setStepsInput('');
      if (result.lotteryBonusWonAmount > 0) {
        // Real lottery-style bonus win (item 248) -- always named separately from the
        // guaranteed reward, never folded into one number, so it's clear which part was
        // guaranteed and which was the real, disclosed-odds bonus.
        setMessage(`Walking bonus unlocked: +${result.newlyEarnedAmount.toLocaleString('en-US')} RWF — plus a lottery bonus: +${result.lotteryBonusWonAmount.toLocaleString('en-US')} RWF! 🎉`);
      } else if (result.newlyEarnedAmount > 0) {
        setMessage(`Walking bonus unlocked: +${result.newlyEarnedAmount.toLocaleString('en-US')} RWF`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setReportingSteps(false);
    }
  };

  if (!tasks) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="itunda-flat-section skeleton" style={{ height: '200px' }} />;
  }

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card wrapping --
  // Total earned/Missions/Walking rewards/Invite friends are 4 real sections
  // shown together on one screen, now separated by itunda-flat-section's own
  // border-bottom divider instead of separate white cards.
  return (
    <div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}
      <PetCard />
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Total earned</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{tasks.rewardsTotal.toLocaleString('en-US')} RWF</h2>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Missions</h3>
        {tasks.tasks.map((t) => (
          <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)', padding: '8px 0' }}>
            <div>
              <p>{t.title}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{t.subtitle}</p>
            </div>
            {t.claimed ? (
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Claimed</span>
            ) : (
              <button
                className="itunda-btn itunda-btn-secondary"
                disabled={!t.eligible || claimingId === t.id}
                onClick={() => handleClaim(t.id)}
              >
                {claimingId === t.id ? '...' : `+${t.rewardAmount.toLocaleString('en-US')} RWF`}
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>🚶 Walking rewards</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Today: {todaySteps ?? 0} steps</p>
        {/* Real lottery-style bonus (item 248) -- the real, stated odds shown up front,
            same discipline this app's own dark-pattern-prevention rules require: never a
            mechanic a user only discovers by winning. */}
        {stepTiers.length > 0 && (
          <ul style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', margin: '6px 0 0', paddingLeft: '16px' }}>
            {stepTiers.map((tier) => (
              <li key={tier.stepsRequired}>
                {tier.stepsRequired.toLocaleString('en-US')} steps: +{tier.rewardAmount} RWF guaranteed, plus a {Math.round(tier.lotteryOdds * 100)}% chance of a +{tier.lotteryBonusAmount} RWF bonus
              </li>
            ))}
          </ul>
        )}
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            type="number"
            placeholder="Enter steps"
            value={stepsInput}
            onChange={(e) => setStepsInput(e.target.value)}
            style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
          />
          <button className="itunda-btn itunda-btn-secondary" disabled={reportingSteps} onClick={handleReportSteps}>
            {reportingSteps ? '...' : 'Report'}
          </button>
        </div>
      </div>
      {referral && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Invite friends</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{referral.referralCode}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {referral.completedReferralCount} completed of {referral.referredCount} referred
          </p>
        </div>
      )}
    </div>
  );
}
