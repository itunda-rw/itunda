// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Rwanda BRD student loan (own lib/studentLoan.ts data layer, exactly one external
// call site inside LoansView.tsx's own mode switch).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  applyForStudentLoan, declareGraduated as declareStudentLoanGraduated, disburseStudentLoan, fetchMyStudentLoans, fetchSuggestedPayment, repayStudentLoan,
  type StudentLoan, type StudentLoanLevel, type StudentLoanSuggestedPayment,
} from './lib/studentLoan';
import { useDeferredLoading } from './useDeferredLoading';

// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
// the backend's StudentLoanService.kt doc comment for the full sourced account.
// Distinct from VupLoanView above: eligibility on self-declared household income
// (not Ubudehe), a mandatory grace period between disbursement and first-repayment
// obligation, and an income-percentage-SUGGESTED (not fixed-installment) repayment.
export function StudentLoanView() {
  const { t } = useI18n();
  const [loans, setLoans] = useState<StudentLoan[] | null>(null);
  const showSkeleton = useDeferredLoading(loans === null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [suggested, setSuggested] = useState<Record<string, StudentLoanSuggestedPayment>>({});

  const [level, setLevel] = useState<StudentLoanLevel>('UNDERGRADUATE');
  const [income, setIncome] = useState('');
  const [amount, setAmount] = useState('');
  const [graduationDate, setGraduationDate] = useState('');
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    fetchMyStudentLoans()
      .then((l) => {
        setLoans(l);
        l.filter((loan) => loan.status === 'REPAYING' || loan.status === 'OVERDUE').forEach((loan) => {
          fetchSuggestedPayment(loan.id).then((s) => setSuggested((prev) => ({ ...prev, [loan.id]: s }))).catch(() => undefined);
        });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const hasActiveLoan = (loans ?? []).some((loan) => loan.status !== 'REPAID');

  const handleApply = async () => {
    const incomeValue = Number(income);
    const amountValue = Number(amount);
    if (!incomeValue || incomeValue <= 0) { setError('Enter a valid declared annual household income.'); return; }
    if (!amountValue || amountValue <= 0) { setError('Enter a valid loan amount.'); return; }
    if (!graduationDate) { setError('Enter your expected graduation date.'); return; }
    setBusyId('apply');
    setError(null);
    try {
      await applyForStudentLoan(level, incomeValue, amountValue, graduationDate);
      setIncome(''); setAmount(''); setGraduationDate('');
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
      await disburseStudentLoan(loanId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleDeclareGraduated = async (loanId: string) => {
    setBusyId(loanId);
    setError(null);
    try {
      await declareStudentLoanGraduated(loanId);
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
      await repayStudentLoan(loanId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loanId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (loans === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>BRD Student Loan</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Rwanda's national higher-education student loan, run by the Development Bank of Rwanda (BRD) since 2016 -- 11% undergraduate / 12% postgraduate,
          {' '}with a grace period after graduation before repayment starts. Declared household income is self-declared -- not verified against BRD's real
          {' '}Financial Means Testing process. Repayment here is user-initiated from your account -- itunda cannot deduct from your paycheck like the real
          {' '}8%-of-income scheme BRD uses.
        </p>
        {hasActiveLoan ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>You already have an active student loan -- repay it before applying for another.</p>
        ) : (
          <>
            <select
              value={level} onChange={(e) => setLevel(e.target.value as StudentLoanLevel)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="UNDERGRADUATE">Undergraduate (11%)</option>
              <option value="POSTGRADUATE">Postgraduate (12%)</option>
            </select>
            <input
              type="number" value={income} onChange={(e) => setIncome(e.target.value)} placeholder="Declared annual household income (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Loan amount (RWF, up to 2,000,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="date" value={graduationDate} onChange={(e) => setGraduationDate(e.target.value)}
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
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>My student loans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {loans.map((loan) => (
              <div key={loan.id} className="itunda-flat-section">
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{loan.principalAmount.toLocaleString()} RWF · {loan.level}</p>
                  <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: loan.status === 'OVERDUE' ? 'var(--itunda-red)' : 'var(--itunda-indigo)' }}>{loan.status}</span>
                </div>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                  Outstanding: {loan.outstandingBalance.toLocaleString()} RWF
                  {loan.graceEndsAt && ` · Grace ends ${new Date(loan.graceEndsAt).toLocaleDateString()}`}
                </p>
                {loan.status === 'REQUESTED' && (
                  <>
                    <p style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>Demo: instantly approved -- stands in for the real BRD/MINEDUC approval step.</p>
                    <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDisburse(loan.id)}>
                      {busyId === loan.id ? 'Disbursing…' : 'Disburse'}
                    </button>
                  </>
                )}
                {loan.status === 'DISBURSED' && (
                  <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDeclareGraduated(loan.id)}>
                    {busyId === loan.id ? 'Updating…' : 'Declare graduated'}
                  </button>
                )}
                {loan.status === 'IN_GRACE_PERIOD' && (
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>In your grace period -- repayment isn't due yet.</p>
                )}
                {(loan.status === 'REPAYING' || loan.status === 'OVERDUE') && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                    {suggested[loan.id] && (
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                        Suggested: {Math.round(suggested[loan.id].suggestedMonthlyPayment).toLocaleString()} RWF/mo · {suggested[loan.id].note}
                      </p>
                    )}
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
