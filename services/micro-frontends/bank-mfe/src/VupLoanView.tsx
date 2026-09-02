// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Rwanda VUP Financial Services means-tested micro-loan (own lib/vupLoan.ts data
// layer, exactly one external call site inside LoansView.tsx's own mode switch).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  applyForVupLoan, disburseVupLoan, fetchMyVupLoans, fetchVupLoanEligibility, repayVupLoan,
  type VupLoan, type VupLoanEligibility, type VupLoanPurpose,
} from './lib/vupLoan';
import { useDeferredLoading } from './useDeferredLoading';

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
// see lib/vupLoan.ts's own doc comment for the full sourced account. The first
// MEANS-TESTED lending product in itunda, gated on a self-declared (not
// government-verified) Ubudehe category rather than credit score or collateral.
// Disbursement here is a real user-triggered step standing in for the real SACCO
// officer approval step the actual VUP/FS program uses -- named honestly below.
export function VupLoanView() {
  const { t } = useI18n();
  const [loans, setLoans] = useState<VupLoan[] | null>(null);
  const [eligibility, setEligibility] = useState<VupLoanEligibility | null>(null);
  const showSkeleton = useDeferredLoading(loans === null || eligibility === null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [category, setCategory] = useState<number>(1);
  const [purpose, setPurpose] = useState<VupLoanPurpose>('FARMING');
  const [amount, setAmount] = useState('');
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    Promise.all([fetchMyVupLoans(), fetchVupLoanEligibility()])
      .then(([l, e]) => { setLoans(l); setEligibility(e); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const handleApply = async () => {
    const value = Number(amount);
    if (!value || value <= 0) { setError('Enter a valid loan amount.'); return; }
    setBusyId('apply');
    setError(null);
    try {
      await applyForVupLoan(category, purpose, value);
      setAmount('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleDisburse = async (loanId: string) => {
    setBusyId(loanId);
    setError(null);
    try {
      await disburseVupLoan(loanId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (loanId: string) => {
    const value = Number(repayAmounts[loanId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(loanId);
    setError(null);
    try {
      await repayVupLoan(loanId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loanId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (loans === null || eligibility === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>VUP Financial Services</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Rwanda's Vision 2020 Umurenge Programme subsidized microloan for farming, livestock, or small business -- {eligibility.interestRate * 100}% interest, for
          {' '}Ubudehe categories {eligibility.minUbudeheCategory}-{eligibility.maxUbudeheCategory} only. Ubudehe category is self-declared -- not verified against a real government registry.
        </p>
        {!eligibility.canApply ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>You already have an active VUP loan -- repay it before applying for another.</p>
        ) : (
          <>
            <select
              value={category} onChange={(e) => setCategory(Number(e.target.value))}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value={1}>Ubudehe category 1</option>
              <option value={2}>Ubudehe category 2</option>
              <option value={3}>Ubudehe category 3</option>
            </select>
            <select
              value={purpose} onChange={(e) => setPurpose(e.target.value as VupLoanPurpose)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="FARMING">Farming</option>
              <option value="LIVESTOCK">Livestock</option>
              <option value="BUSINESS">Small business</option>
            </select>
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Loan amount (RWF, up to 500,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === 'apply'} onClick={handleApply}>
              {busyId === 'apply' ? 'Applying…' : 'Apply'}
            </button>
          </>
        )}
      </div>

      {loans.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My VUP loans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {loans.map((loan) => (
              <div key={loan.id} className="itunda-flat-section">
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{loan.principalAmount.toLocaleString()} RWF · {loan.purpose}</p>
                  <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: loan.status === 'OVERDUE' ? 'var(--itunda-red)' : 'var(--itunda-indigo)' }}>{loan.status}</span>
                </div>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                  Outstanding: {loan.outstandingPrincipal.toLocaleString()} RWF
                  {loan.dueDate && ` · Due ${new Date(loan.dueDate).toLocaleDateString()}`}
                </p>
                {loan.status === 'REQUESTED' && (
                  <>
                    <p style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>Demo: instantly approved -- stands in for the real SACCO officer approval step.</p>
                    <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDisburse(loan.id)}>
                      {busyId === loan.id ? 'Disbursing…' : 'Disburse'}
                    </button>
                  </>
                )}
                {(loan.status === 'DISBURSED' || loan.status === 'OVERDUE') && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                    <input
                      type="number" value={repayAmounts[loan.id] ?? ''} onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                      placeholder="Repayment amount (RWF)"
                      style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
                    />
                    <button className="itunda-btn itunda-btn-secondary" disabled={busyId === loan.id} onClick={() => handleRepay(loan.id)}>
                      {busyId === loan.id ? 'Repaying…' : 'Repay'}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}
