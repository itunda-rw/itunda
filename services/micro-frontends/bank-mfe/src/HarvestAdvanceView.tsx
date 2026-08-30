// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Rwanda coffee-cooperative harvest-advance product (own lib/harvestAdvance.ts data
// layer, exactly one external call site inside LoansView.tsx's own mode switch).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  disburseHarvestAdvance, fetchMyCooperativeMemberships, fetchMyHarvestAdvances, joinCooperative,
  registerCooperative, repayHarvestAdvance, requestHarvestAdvance,
  type CooperativeMembership, type HarvestAdvance,
} from './lib/harvestAdvance';

// Real Rwanda coffee-cooperative harvest-advance / input financing -- see
// lib/harvestAdvance.ts's own doc comment for the full sourced account. Sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Itunda is the
// sole real lender here (the same real underwriting-free account-to-account pattern the
// Offers/My-loans views above already use for itunda's own book), disbursed from
// itunda's own real loan_payable receivable -- never a shared pool, distinct from the
// Ikimina/SACCO shapes above.
export function HarvestAdvanceView() {
  const { t } = useI18n();
  const [memberships, setMemberships] = useState<CooperativeMembership[] | null>(null);
  const [advances, setAdvances] = useState<HarvestAdvance[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [coopName, setCoopName] = useState('');
  const [coopId, setCoopId] = useState('');
  const [advanceAmount, setAdvanceAmount] = useState('');
  const [advancePurpose, setAdvancePurpose] = useState('INPUT_FINANCING');
  const [harvestDate, setHarvestDate] = useState('');

  const refresh = () => {
    setError(null);
    Promise.all([fetchMyCooperativeMemberships(), fetchMyHarvestAdvances()])
      .then(([m, a]) => { setMemberships(m); setAdvances(a); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const handleRegisterAndJoin = async () => {
    if (!coopName.trim()) { setError('Enter a real cooperative name.'); return; }
    setBusy(true);
    setError(null);
    try {
      const coop = await registerCooperative(coopName.trim(), 'COFFEE');
      await joinCooperative(coop.id);
      setCoopName('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleJoinExisting = async () => {
    if (!coopId.trim()) { setError('Enter a real cooperative id.'); return; }
    setBusy(true);
    setError(null);
    try {
      await joinCooperative(coopId.trim());
      setCoopId('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRequestAdvance = async (membershipId: string) => {
    const amount = Number(advanceAmount);
    if (!amount || amount <= 0 || !harvestDate) { setError('Enter a real advance amount and expected harvest date.'); return; }
    setBusy(true);
    setError(null);
    try {
      await requestHarvestAdvance(membershipId, amount, advancePurpose, new Date(harvestDate).toISOString());
      setAdvanceAmount('');
      setHarvestDate('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleDisburse = async (advanceId: string) => {
    setBusy(true);
    setError(null);
    try {
      await disburseHarvestAdvance(advanceId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async (advanceId: string, principalAmount: number) => {
    setBusy(true);
    setError(null);
    try {
      await repayHarvestAdvance(advanceId, principalAmount);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (memberships === null) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Cooperative harvest advance</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Input financing or post-harvest advances for coffee cooperative members. Cooperative registration is self-declared -- not verified against a real RCA registry.
        </p>
        <input
          type="text" value={coopName} onChange={(e) => setCoopName(e.target.value)} placeholder="Register a new cooperative (name)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
        />
        <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleRegisterAndJoin}>
          {busy ? 'Working…' : 'Register & join'}
        </button>
        <input
          type="text" value={coopId} onChange={(e) => setCoopId(e.target.value)} placeholder="Or join an existing cooperative (id)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
        />
        <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleJoinExisting}>
          {busy ? 'Working…' : 'Join'}
        </button>
      </div>

      {memberships.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>You're not a member of any cooperative yet.</p>
      ) : (
        memberships.map((m) => (
          <div key={m.id} style={{ padding: '12px 0' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Cooperative membership {m.cooperativeId}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Member since {new Date(m.memberSince).toLocaleDateString()}</p>
            <input
              type="number" value={advanceAmount} onChange={(e) => setAdvanceAmount(e.target.value)} placeholder="Advance amount (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <select
              value={advancePurpose} onChange={(e) => setAdvancePurpose(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="INPUT_FINANCING">Input financing (seeds/fertilizer)</option>
              <option value="POST_HARVEST">Post-harvest advance</option>
            </select>
            <input
              type="date" value={harvestDate} onChange={(e) => setHarvestDate(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleRequestAdvance(m.id)}>
              {busy ? 'Requesting…' : 'Request advance'}
            </button>
          </div>
        ))
      )}

      {advances && advances.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My advances</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {advances.map((a) => (
              <div key={a.id} className="itunda-flat-section">
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{a.principalAmount.toLocaleString()} RWF · {a.purpose}</p>
                  <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{a.status}</span>
                </div>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Repay by {new Date(a.repaymentDueDate).toLocaleDateString()}</p>
                {a.status === 'REQUESTED' && (
                  <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleDisburse(a.id)}>
                    {busy ? 'Disbursing…' : 'Disburse'}
                  </button>
                )}
                {a.status === 'DISBURSED' && (
                  // Real bug caught during this feature's own build-time review: partial
                  // repayment isn't tracked anywhere on this entity, so accepting a
                  // free-form amount let a token repayment silently close out the full
                  // debt. Repayment is full-settlement-only -- no amount to type, just
                  // the real outstanding principal shown up front.
                  <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleRepay(a.id, a.principalAmount)}>
                    {busy ? 'Repaying…' : `Repay in full (${a.principalAmount.toLocaleString()} RWF)`}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
