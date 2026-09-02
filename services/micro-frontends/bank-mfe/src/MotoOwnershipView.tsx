// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Rwanda moto-taxi ownership savings-to-loan plan (own lib/motoOwnership.ts data
// layer, exactly one external call site inside LoansView.tsx's own mode switch).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  cancelMotoOwnershipPlan, contributeToMotoOwnershipPlan, convertMotoOwnershipPlanToLoan, createMotoOwnershipPlan, fetchMyMotoOwnershipPlans, repayMotoOwnershipPlan,
  type MotoOwnershipPlan,
} from './lib/motoOwnership';
import { useDeferredLoading } from './useDeferredLoading';

// Real Rwanda moto-taxi ownership savings-to-loan plan -- see lib/motoOwnership.ts's
// own doc comment for the full sourced account. The first two-PHASE product in this
// codebase: save toward a real 30% down payment (itunda's own policy pick), then
// convert the plan into an unsecured loan for the remaining balance. Distinct from
// VupLoanView/StudentLoanView above: this is asset-purchase financing tied to a
// specific real Rwanda sector (moto-taxi ownership), not a cash microloan.
export function MotoOwnershipView() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<MotoOwnershipPlan[] | null>(null);
  const showSkeleton = useDeferredLoading(plans === null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [bikePrice, setBikePrice] = useState('');
  const [dailyContribution, setDailyContribution] = useState('');
  const [contributeAmounts, setContributeAmounts] = useState<Record<string, string>>({});
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    fetchMyMotoOwnershipPlans()
      .then(setPlans)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const hasActivePlan = (plans ?? []).some((plan) => plan.status === 'SAVING' || plan.status === 'LOAN_ACTIVE');
  const previewDownPayment = Number(bikePrice) > 0 ? Number(bikePrice) * 0.3 : 0;

  const handleCreate = async () => {
    const priceValue = Number(bikePrice);
    const contributionValue = Number(dailyContribution);
    if (!priceValue || priceValue <= 0) { setError('Enter a valid bike price.'); return; }
    if (!contributionValue || contributionValue <= 0) { setError('Enter a valid daily contribution.'); return; }
    setBusyId('create');
    setError(null);
    try {
      await createMotoOwnershipPlan(priceValue, contributionValue);
      setBikePrice(''); setDailyContribution('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleContribute = async (planId: string) => {
    const value = Number(contributeAmounts[planId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid contribution amount.'); return; }
    setBusyId(planId);
    setError(null);
    try {
      await contributeToMotoOwnershipPlan(planId, value);
      setContributeAmounts((prev) => { const next = { ...prev }; delete next[planId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleCancel = async (planId: string) => {
    setBusyId(planId);
    setError(null);
    try {
      await cancelMotoOwnershipPlan(planId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleConvert = async (planId: string) => {
    setBusyId(planId);
    setError(null);
    try {
      await convertMotoOwnershipPlanToLoan(planId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (planId: string) => {
    const value = Number(repayAmounts[planId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(planId);
    setError(null);
    try {
      await repayMotoOwnershipPlan(planId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[planId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (plans === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Moto-Taxi Ownership Plan</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Save toward a 30% down payment on your own moto-taxi bike (itunda's own down-payment policy), then convert the rest into an unsecured loan.
          {' '}A real entry-level bike costs around 600,000 RWF -- this fills the gap left since Rwanda's taxi-moto cooperatives, which used to help
          {' '}drivers become owner-operators, were dissolved.
        </p>
        {hasActivePlan ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>You already have an active moto-taxi ownership plan -- complete or cancel it before starting another.</p>
        ) : (
          <>
            <input
              type="number" value={bikePrice} onChange={(e) => setBikePrice(e.target.value)} placeholder="Bike price (RWF, 300,000-2,500,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="number" value={dailyContribution} onChange={(e) => setDailyContribution(e.target.value)} placeholder="Daily contribution (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            {previewDownPayment > 0 && (
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                Down payment target (30%): {previewDownPayment.toLocaleString()} RWF
              </p>
            )}
            <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === 'create'} onClick={handleCreate}>
              {busyId === 'create' ? 'Creating…' : 'Start plan'}
            </button>
          </>
        )}
      </div>

      {plans.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My moto-taxi ownership plans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {plans.map((plan) => {
              const progressPct = plan.downPaymentTarget > 0 ? Math.min(100, Math.round((plan.savedAmount / plan.downPaymentTarget) * 100)) : 0;
              return (
                <div key={plan.id} className="itunda-flat-section">
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{plan.bikePrice.toLocaleString()} RWF bike</p>
                    <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{plan.status}</span>
                  </div>
                  {plan.status === 'SAVING' && (
                    <>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                        Saved {plan.savedAmount.toLocaleString()} / {plan.downPaymentTarget.toLocaleString()} RWF down payment
                      </p>
                      <div style={{ height: '6px', borderRadius: '3px', background: 'var(--itunda-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${progressPct}%`, background: 'var(--itunda-indigo)' }} />
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                        <input
                          type="number" value={contributeAmounts[plan.id] ?? ''} onChange={(e) => setContributeAmounts((prev) => ({ ...prev, [plan.id]: e.target.value }))}
                          placeholder="Contribution amount (RWF)"
                          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
                        />
                        <div style={{ display: 'flex', gap: '6px' }}>
                          <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busyId === plan.id} onClick={() => handleContribute(plan.id)}>
                            {busyId === plan.id ? 'Saving…' : 'Contribute'}
                          </button>
                          <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1, color: 'var(--itunda-red)' }} disabled={busyId === plan.id} onClick={() => handleCancel(plan.id)}>
                            Cancel
                          </button>
                        </div>
                        {plan.savedAmount >= plan.downPaymentTarget && (
                          <>
                            <p style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                              This releases your full {plan.bikePrice.toLocaleString()} RWF bike price to your account (your saved down payment plus a new unsecured loan for the rest) -- itunda cannot repossess the bike if you stop repaying.
                            </p>
                            <button className="itunda-btn itunda-btn-primary" disabled={busyId === plan.id} onClick={() => handleConvert(plan.id)}>
                              {busyId === plan.id ? 'Converting…' : 'Convert to loan'}
                            </button>
                          </>
                        )}
                      </div>
                    </>
                  )}
                  {plan.status === 'LOAN_ACTIVE' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Loan outstanding: {plan.loanOutstanding.toLocaleString()} RWF</p>
                      <input
                        type="number" value={repayAmounts[plan.id] ?? ''} onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [plan.id]: e.target.value }))}
                        placeholder="Repayment amount (RWF)"
                        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
                      />
                      <button className="itunda-btn itunda-btn-secondary" disabled={busyId === plan.id} onClick={() => handleRepay(plan.id)}>
                        {busyId === plan.id ? 'Repaying…' : 'Repay'}
                      </button>
                    </div>
                  )}
                  {plan.status === 'COMPLETED' && (
                    <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>Paid off -- this bike is now fully yours.</p>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      )}
    </div>
  );
}
