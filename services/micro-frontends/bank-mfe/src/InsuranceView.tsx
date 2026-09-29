import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import {
  fetchInsurancePlans, fetchMyPolicies, enrollInPlan, submitClaim, fetchMyClaims, createPremiumFund,
  contributeToFund, cancelFund, fetchMyPremiumFunds,
  type InsurancePlan, type InsurancePolicy, type InsuranceClaim, type InsurancePremiumFund,
} from './lib/insurance';
import { useDeferredLoading } from './useDeferredLoading';
import { CropWeatherIndexSection } from './CropWeatherIndexSection';
import { useI18n } from './i18n/I18nContext';

// Real insurance browse/enroll/my-policies/claims client -- see lib/insurance.ts's
// own doc comment. Previously bank-mfe only rendered a read-only "Insurance: N active
// plan(s)" summary line inside OverviewView; this is the actual self-service flow.

// Real, sourced Toss simplification (2026-08-24, toss.tech/article/insurance-claim-process,
// Toss's own "Easy to Answer" product principle -- "make questions answerable in 3
// seconds or less"). Toss's own real, measured example: a hospital-refund claim's
// opening question was "전문가 도움받을까요, 직접 할래요?" (Get expert help, or do it
// yourself?) -- users hesitated because they couldn't predict what happened next after
// either choice. Replaced with "서류 있나요?" (Do you have the documents?), a question
// users could answer immediately -- drop-off at that step fell 50%, overall abandonment
// 70% -> 60%.
//
// itunda's own claim form had the same shape of problem: a bare "What happened?" free-
// text field with zero guidance, the exact kind of open-ended, hard-to-answer prompt the
// article's principle warns against. Can't honestly replicate Toss's specific
// documents-vs-no-documents ROUTING (itunda's backend `submitClaim` takes only a
// description + amount, no branching claim-intake flow to route into) -- that would be
// fabricated backend behavior, not a real port. What IS real and honest: every policy
// already carries its own real `category` (health/life/travel/motor), so a set of
// category-grounded quick-reason chips makes the SAME open question concretely
// answerable with a tap, using only data already on the policy object, not invented
// claim taxonomy.
const CLAIM_REASON_CHIPS: Record<string, string[]> = {
  health: ['Hospital admission', 'Outpatient visit', 'Prescription cost', 'Dental/vision care'],
  life: ['Critical illness diagnosis', 'Disability', 'Death benefit'],
  travel: ['Trip cancellation', 'Medical emergency abroad', 'Lost/delayed baggage'],
  motor: ['Accident damage', 'Theft', 'Fire damage', 'Third-party claim'],
};
//
// Extracted into its own file (2026-08-10) as the first of BankDashboard.tsx's ~35
// inline view components to be split out -- that 21k-line single file meant every
// screen, including this one, shipped in the same 1.5MB eager-loaded JS bundle
// regardless of whether a session ever opened Insurance. Making this a real module
// lets BankDashboard.tsx lazy-load it (`const InsuranceView = lazy(() =>
// import('./InsuranceView'))`) the same way MapView.tsx already was the one
// exception to the inline-everything pattern. No behavior change -- same component,
// same props (none), same render output.
export default function InsuranceView() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<InsurancePlan[] | null>(null);
  const showPlansSkeleton = useDeferredLoading(!plans);
  const [policies, setPolicies] = useState<InsurancePolicy[]>([]);
  const [claims, setClaims] = useState<InsuranceClaim[]>([]);
  const [funds, setFunds] = useState<InsurancePremiumFund[]>([]);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [claimPolicyId, setClaimPolicyId] = useState<string | null>(null);
  const [claimDescription, setClaimDescription] = useState('');
  const [claimAmount, setClaimAmount] = useState('');
  const [submittingClaim, setSubmittingClaim] = useState(false);
  const [creatingFundPolicyId, setCreatingFundPolicyId] = useState<string | null>(null);
  const [newFundDaily, setNewFundDaily] = useState('0');
  const [fundBusyId, setFundBusyId] = useState<string | null>(null);
  const [contributeAmount, setContributeAmount] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchInsurancePlans().then(setPlans).catch(() => setPlans([]));
    fetchMyPolicies().then(setPolicies).catch(() => setPolicies([]));
    fetchMyClaims().then(setClaims).catch(() => setClaims([]));
    fetchMyPremiumFunds().then(setFunds).catch(() => setFunds([]));
  };
  useEffect(load, []);

  // Real Ejo Heza ya Moto-style premium savings fund -- see lib/insurance.ts's own doc
  // comment. Lets a user save toward a specific policy's next premium ahead of time.
  const handleCreateFund = async (policyId: string) => {
    const daily = Number(newFundDaily || '0');
    if (!Number.isFinite(daily) || daily < 0) {
      setError('Daily contribution must be zero or a positive number.');
      return;
    }
    setFundBusyId(policyId);
    setError(null);
    setMessage(null);
    try {
      await createPremiumFund(policyId, daily);
      setMessage('Started saving toward your next premium.');
      setCreatingFundPolicyId(null);
      setNewFundDaily('0');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setFundBusyId(null);
    }
  };

  const handleContribute = async (fundId: string) => {
    const amount = Number(contributeAmount[fundId] || '0');
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Contribution amount must be greater than zero.');
      return;
    }
    setFundBusyId(fundId);
    setError(null);
    setMessage(null);
    try {
      await contributeToFund(fundId, amount);
      setMessage('Contribution added toward your next premium.');
      setContributeAmount((prev) => ({ ...prev, [fundId]: '' }));
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setFundBusyId(null);
    }
  };

  const handleCancelFund = async (fundId: string) => {
    setFundBusyId(fundId);
    setError(null);
    setMessage(null);
    try {
      await cancelFund(fundId);
      setMessage('Premium fund cancelled and refunded to your account.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setFundBusyId(null);
    }
  };

  const handleEnroll = async (planId: string) => {
    setEnrollingId(planId);
    setError(null);
    setMessage(null);
    try {
      const policy = await enrollInPlan(planId);
      setMessage(`Enrolled in ${policy.planName}`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setEnrollingId(null);
    }
  };

  const handleSubmitClaim = async () => {
    const amount = Number(claimAmount);
    if (!claimPolicyId || !claimDescription.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Fill in a real description and a claim amount greater than zero.');
      return;
    }
    setSubmittingClaim(true);
    setError(null);
    setMessage(null);
    try {
      await submitClaim(claimPolicyId, claimDescription.trim(), amount);
      setMessage('Claim submitted for review.');
      setClaimPolicyId(null);
      setClaimDescription('');
      setClaimAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmittingClaim(false);
    }
  };

  if (!plans) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : (showPlansSkeleton ? <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  const enrolledPlanIds = new Set(policies.map((p) => p.planId));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}

      {policies.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My policies</h3>
          {policies.map((p) => (
            <div key={p.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{p.planName}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{p.policyNumber} · {p.status} · {p.monthlyPremium.toLocaleString('en-US')} RWF/mo</p>
                </div>
                <button
                  className="itunda-btn itunda-btn-secondary"
                  disabled={p.status !== 'active'}
                  onClick={() => setClaimPolicyId(p.id)}
                >
                  File a claim
                </button>
              </div>
              {claimPolicyId === p.id && (
                <div style={{ marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  {/* Real "Easy to Answer" fix -- see this file's own CLAIM_REASON_CHIPS
                      doc comment. Tapping a chip fills the field with a concrete starting
                      point instead of leaving the user facing a blank, open-ended prompt;
                      still freely editable afterward, nothing is locked in by the tap. */}
                  {(CLAIM_REASON_CHIPS[p.category] ?? []).length > 0 && (
                    <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
                      {CLAIM_REASON_CHIPS[p.category].map((reason) => (
                        <button
                          key={reason}
                          type="button"
                          className="itunda-btn itunda-btn-secondary"
                          style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                          onClick={() => setClaimDescription(reason)}
                        >
                          {reason}
                        </button>
                      ))}
                    </div>
                  )}
                  <input
                    placeholder="What happened?"
                    value={claimDescription}
                    onChange={(e) => setClaimDescription(e.target.value)}
                    style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
                  />
                  <input
                    type="number"
                    placeholder="Claim amount (RWF)"
                    value={claimAmount}
                    onChange={(e) => setClaimAmount(e.target.value)}
                    style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
                  />
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="itunda-btn itunda-btn-secondary" disabled={submittingClaim} onClick={handleSubmitClaim}>
                      {submittingClaim ? '...' : 'Submit claim'}
                    </button>
                    <button className="itunda-btn itunda-btn-secondary" onClick={() => setClaimPolicyId(null)}>Cancel</button>
                  </div>
                </div>
              )}
              {p.status === 'active' && (() => {
                const fund = funds.find((f) => f.policyId === p.id && f.status === 'active');
                if (!fund) {
                  return (
                    <div style={{ marginTop: '8px' }}>
                      {creatingFundPolicyId === p.id ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                          <input
                            type="number"
                            placeholder="Daily contribution (0 = manual only)"
                            value={newFundDaily}
                            onChange={(e) => setNewFundDaily(e.target.value)}
                            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
                          />
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button className="itunda-btn itunda-btn-secondary" disabled={fundBusyId === p.id} onClick={() => handleCreateFund(p.id)}>
                              {fundBusyId === p.id ? '...' : 'Start saving'}
                            </button>
                            <button className="itunda-btn itunda-btn-secondary" onClick={() => setCreatingFundPolicyId(null)}>Cancel</button>
                          </div>
                        </div>
                      ) : (
                        <button className="itunda-btn itunda-btn-secondary" onClick={() => setCreatingFundPolicyId(p.id)}>
                          Save for next premium
                        </button>
                      )}
                    </div>
                  );
                }
                const pct = fund.targetAmount > 0 ? Math.min(100, Math.round((fund.currentAmount / fund.targetAmount) * 100)) : 0;
                return (
                  <div style={{ marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                      Saved toward next premium: {fund.currentAmount.toLocaleString('en-US')} / {fund.targetAmount.toLocaleString('en-US')} RWF
                    </p>
                    <div style={{ height: '6px', borderRadius: '3px', background: 'var(--itunda-grey-100)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${pct}%`, background: 'var(--itunda-indigo)' }} />
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <input
                        type="number"
                        placeholder="Add amount (RWF)"
                        value={contributeAmount[fund.id] ?? ''}
                        onChange={(e) => setContributeAmount((prev) => ({ ...prev, [fund.id]: e.target.value }))}
                        style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
                      />
                      <button className="itunda-btn itunda-btn-secondary" disabled={fundBusyId === fund.id} onClick={() => handleContribute(fund.id)}>
                        {fundBusyId === fund.id ? '...' : 'Add'}
                      </button>
                      <button className="itunda-btn itunda-btn-secondary" disabled={fundBusyId === fund.id} onClick={() => handleCancelFund(fund.id)}>
                        Cancel fund
                      </button>
                    </div>
                  </div>
                );
              })()}
            </div>
          ))}
        </div>
      )}

      {claims.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My claims</h3>
          {claims.map((c) => (
            <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
              <div>
                <p>{c.description}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{c.status}</p>
              </div>
              <span>{c.amount.toLocaleString('en-US')} RWF</span>
            </div>
          ))}
        </div>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Browse plans</h3>
        {/* Real Toss finding (2026-08-30, toss.tech/article/recommend-just-one): a
            flat list of options caused decision paralysis; highlighting ONE best
            pick (not removing the rest) measurably raised conversion. rating is
            real, already-fetched data, not an invented metric. */}
        {(() => { const topRating = plans.length > 0 ? Math.max(...plans.map((p) => p.rating)) : 0; return plans.map((plan) => {
          const isTopRated = plan.rating === topRating;
          return (
          <div key={plan.id} style={{ padding: '10px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                {isTopRated && (
                  <span style={{ display: 'inline-block', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', backgroundColor: 'var(--itunda-indigo-light)', padding: '2px 8px', borderRadius: '999px', marginBottom: '4px' }}>
                    Top rated
                  </span>
                )}
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{plan.name}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{plan.provider} · {plan.monthlyPremium.toLocaleString('en-US')} RWF/mo · cover {plan.coverageAmount.toLocaleString('en-US')} RWF</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{plan.description}</p>
              </div>
              {enrolledPlanIds.has(plan.id) ? (
                <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 600, color: 'var(--itunda-green)', flexShrink: 0, whiteSpace: 'nowrap' }}>✓ Enrolled</span>
              ) : (
                <button
                  className="itunda-btn itunda-btn-secondary"
                  style={{ width: 'auto', flexShrink: 0, padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}
                  disabled={enrollingId === plan.id}
                  onClick={() => handleEnroll(plan.id)}
                >
                  {enrollingId === plan.id ? '...' : 'Enroll'}
                </button>
              )}
            </div>
          </div>
          );
          });
        })()}
      </div>

      <CropWeatherIndexSection />
    </div>
  );
}

