import { useEffect, useState } from 'react';
import { ApiError } from './lib/api';
import {
  fetchInsurancePlans, fetchMyPolicies, enrollInPlan, submitClaim, fetchMyClaims, createPremiumFund,
  contributeToFund, cancelFund, fetchMyPremiumFunds,
  type InsurancePlan, type InsurancePolicy, type InsuranceClaim, type InsurancePremiumFund,
} from './lib/insurance';
import {
  fetchCropIndexCatalog, enrollInCropIndexPolicy, fetchMyCropIndexPolicies, cancelCropIndexPolicy,
  type CropIndexCatalogEntry, type CropIndexPolicy, type WeatherIndexCropType,
} from './lib/weatherIndexInsurance';

// Real insurance browse/enroll/my-policies/claims client -- see lib/insurance.ts's
// own doc comment. Previously bank-mfe only rendered a read-only "Insurance: N active
// plan(s)" summary line inside OverviewView; this is the actual self-service flow.
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
  const [plans, setPlans] = useState<InsurancePlan[] | null>(null);
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
      setError(err instanceof ApiError ? err.message : 'Could not start a premium fund.');
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
      setError(err instanceof ApiError ? err.message : 'Could not add this contribution.');
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
      setMessage('Premium fund cancelled and refunded to your wallet.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this premium fund.');
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
      setError(err instanceof ApiError ? err.message : 'Could not enroll in this plan.');
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
      setError(err instanceof ApiError ? err.message : 'Could not submit this claim.');
    } finally {
      setSubmittingClaim(false);
    }
  };

  if (!plans) {
    return error ? <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="itunda-card skeleton" style={{ height: '200px' }} />;
  }

  const enrolledPlanIds = new Set(policies.map((p) => p.planId));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--itunda-blue)' }}>{message}</p>}

      {policies.length > 0 && (
        <div className="itunda-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My policies</h3>
          {policies.map((p) => (
            <div key={p.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontSize: '13px', fontWeight: 600 }}>{p.planName}</p>
                  <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{p.policyNumber} · {p.status} · {p.monthlyPremium.toLocaleString()} RWF/mo</p>
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
                    <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>
                      Saved toward next premium: {fund.currentAmount.toLocaleString()} / {fund.targetAmount.toLocaleString()} RWF
                    </p>
                    <div style={{ height: '6px', borderRadius: '3px', background: 'var(--itunda-grey-100)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${pct}%`, background: 'var(--itunda-blue)' }} />
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
        <div className="itunda-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My claims</h3>
          {claims.map((c) => (
            <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
              <div>
                <p>{c.description}</p>
                <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{c.status}</p>
              </div>
              <span>{c.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}

      <div className="itunda-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Browse plans</h3>
        {plans.map((plan) => (
          <div key={plan.id} style={{ padding: '10px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: '13px', fontWeight: 600 }}>{plan.name}</p>
                <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{plan.provider} · {plan.monthlyPremium.toLocaleString()} RWF/mo · cover {plan.coverageAmount.toLocaleString()} RWF</p>
                <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>{plan.description}</p>
              </div>
              {enrolledPlanIds.has(plan.id) ? (
                <span style={{ fontSize: '12px', fontWeight: 600, color: 'var(--itunda-green)', flexShrink: 0, whiteSpace: 'nowrap' }}>✓ Enrolled</span>
              ) : (
                <button
                  className="itunda-btn itunda-btn-secondary"
                  style={{ width: 'auto', flexShrink: 0, padding: '8px 14px', fontSize: '13px' }}
                  disabled={enrollingId === plan.id}
                  onClick={() => handleEnroll(plan.id)}
                >
                  {enrollingId === plan.id ? '...' : 'Enroll'}
                </button>
              )}
            </div>
          </div>
        ))}
      </div>

      <CropWeatherIndexSection />
    </div>
  );
}

// Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
// crop insurance -- see lib/weatherIndexInsurance.ts's own doc comment for the full sourced
// account. Genuinely, structurally distinct from the claims-based plans above: no individual
// claim is ever filed here. Rendered as a section inside InsuranceView rather than a
// separate top-level tab -- it's still "insurance" from the customer's point of view.
function CropWeatherIndexSection() {
  const [catalog, setCatalog] = useState<CropIndexCatalogEntry[] | null>(null);
  const [policies, setPolicies] = useState<CropIndexPolicy[]>([]);
  const [cropType, setCropType] = useState<WeatherIndexCropType>('MAIZE');
  const [district, setDistrict] = useState('');
  const [season, setSeason] = useState('2026B');
  const [insuredAmount, setInsuredAmount] = useState('100000');
  const [enrolling, setEnrolling] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchCropIndexCatalog().then(setCatalog).catch(() => setCatalog([]));
    fetchMyCropIndexPolicies().then(setPolicies).catch(() => setPolicies([]));
  };
  useEffect(load, []);

  const selectedRate = catalog?.find((c) => c.cropType === cropType)?.premiumRatePercent ?? 0;
  const amountNum = Number(insuredAmount || '0');
  const computedPremium = Number.isFinite(amountNum) ? Math.round(amountNum * selectedRate) / 100 : 0;

  const handleEnroll = async () => {
    if (!district.trim() || !season.trim() || !Number.isFinite(amountNum) || amountNum <= 0) {
      setError('Enter a district, a season, and an insured amount greater than zero.');
      return;
    }
    setEnrolling(true);
    setError(null);
    setMessage(null);
    try {
      const policy = await enrollInCropIndexPolicy(cropType, district.trim(), season.trim(), amountNum);
      setMessage(`Enrolled — premium ${policy.premiumAmount.toLocaleString()} RWF charged to your wallet.`);
      setDistrict('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not enroll in crop weather-index cover.');
    } finally {
      setEnrolling(false);
    }
  };

  const handleCancel = async (policyId: string) => {
    setBusyId(policyId);
    setError(null);
    setMessage(null);
    try {
      await cancelCropIndexPolicy(policyId);
      setMessage('Policy cancelled and premium refunded to your wallet.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this policy.');
    } finally {
      setBusyId(null);
    }
  };

  if (!catalog) {
    return <div className="itunda-card skeleton" style={{ height: '160px' }} />;
  }

  return (
    <div className="itunda-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div>
        <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Crop Weather Insurance</h3>
        <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
          Rwanda's real National Agricultural Insurance Scheme model: if your district's official rainfall for this
          season falls below the drought threshold, every enrolled farmer in that district and season is paid
          automatically — no claim needed. The season's rainfall figure is transcribed by an admin from the real
          published NISR/Rwanda Meteorology Agency bulletin, not a live satellite feed.
        </p>
      </div>

      {error && <p style={{ fontSize: '13px', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--itunda-blue)' }}>{message}</p>}

      {policies.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {policies.map((p) => (
            <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 600 }}>{p.cropType.replace('_', ' ')} · {p.district} {p.season}</p>
                <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>
                  {p.status} · insured {p.insuredAmount.toLocaleString()} RWF · premium {p.premiumAmount.toLocaleString()} RWF
                </p>
              </div>
              {p.status === 'ENROLLED' && (
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === p.id} onClick={() => handleCancel(p.id)}>
                  {busyId === p.id ? '...' : 'Cancel'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <select value={cropType} onChange={(e) => setCropType(e.target.value as WeatherIndexCropType)} style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}>
          {catalog.map((c) => (
            <option key={c.cropType} value={c.cropType}>{c.name} — {c.premiumRatePercent}% premium rate</option>
          ))}
        </select>
        <input
          placeholder="District (e.g. Nyagatare)"
          value={district}
          onChange={(e) => setDistrict(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <input
          placeholder="Season (e.g. 2026B)"
          value={season}
          onChange={(e) => setSeason(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <input
          type="number"
          placeholder="Insured amount (RWF, max 500,000)"
          value={insuredAmount}
          onChange={(e) => setInsuredAmount(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)' }}
        />
        <p style={{ fontSize: '11px', color: 'var(--itunda-grey-500)' }}>Premium: {computedPremium.toLocaleString()} RWF, charged now to your wallet.</p>
        <button className="itunda-btn itunda-btn-secondary" disabled={enrolling} onClick={handleEnroll}>
          {enrolling ? '...' : 'Enroll'}
        </button>
      </div>
    </div>
  );
}
