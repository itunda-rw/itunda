// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Real
// Kakao Pay-style AI spending report + budgets (own lib/account.ts spending-
// specific exports, exactly one external call site --
// `{tab === 'SPENDING' && <SpendingInsightView />}`).

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';
import {
  fetchBudgets, fetchMonthlySpendingReport, fetchSpendingInsight, setBudget,
  type BudgetView, type SpendingCategory,
} from './lib/account';


// Real Kakao Pay 페이아이 소비 리포트 (AI spending report, sourced 2026-08) -- see
// lib/account.ts's own doc comment for the full account. A real month-over-month
// comparison, never a fabricated AI narrative.
function MonthlySpendingReportCard() {
  const { t } = useI18n();
  const [report, setReport] = useState<Awaited<ReturnType<typeof fetchMonthlySpendingReport>> | null>(null);
  const showSkeleton = useDeferredLoading(!report);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMonthlySpendingReport()
      .then(setReport)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  if (!report) {
    return error ? null : (showSkeleton ? <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  const changed = report.categories.filter((c) => c.percentChange !== null).sort((a, b) => Math.abs(b.percentChange ?? 0) - Math.abs(a.percentChange ?? 0));

  return (
    <div className="itunda-flat-section">
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>This month so far</p>
      <div style={{ display: 'flex', alignItems: 'baseline', gap: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700 }}>{report.currentTotal.toLocaleString('en-US')} RWF</h3>
        {report.percentChange !== null && (
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: report.percentChange > 0 ? 'var(--itunda-red)' : 'var(--itunda-green)' }}>
            {report.percentChange > 0 ? '▲' : '▼'} {Math.abs(report.percentChange)}% vs last month
          </span>
        )}
      </div>
      {changed.slice(0, 3).map((c) => (
        <p key={c.name} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
          {c.name}: {c.currentAmount.toLocaleString('en-US')} RWF ({(c.percentChange ?? 0) > 0 ? '+' : ''}{c.percentChange}% vs last month)
        </p>
      ))}
    </div>
  );
}

// Real Kakao Pay 소비 리포트-style spending categorization (2026-07-13, wired 2026-07-28
// as item 106) -- see lib/account.ts's own doc comment. Found backend-only via a fresh
// matrix scan: real, ledger-based, and live since well before this session, but never
// wired to any client anywhere.
export function SpendingInsightView() {
  const { t } = useI18n();
  const [categories, setCategories] = useState<SpendingCategory[] | null>(null);
  const showSkeleton = useDeferredLoading(!categories);
  const [totalSpent, setTotalSpent] = useState<number>(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchSpendingInsight()
      .then((r) => { setCategories(r.categories); setTotalSpent(r.totalSpent); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  if (!categories) {
    return error
      ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      : (showSkeleton ? <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} /> : null);
  }

  const maxAmount = Math.max(...categories.map((c) => c.amount), 1);

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <MonthlySpendingReportCard />
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Total spent, all time</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{totalSpent.toLocaleString('en-US')} RWF</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Real, ledger-based -- what every account debit actually paid for.</p>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>By category</h3>
        {categories.length === 0 ? (
          <EmptyState message="No spending recorded yet — your breakdown will show up here once you use your account." />
        ) : (
          categories.map((c) => (
            <div key={c.name} style={{ padding: '8px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '4px' }}>
                <span>{c.name}</span>
                <span style={{ fontWeight: 700 }}>{c.amount.toLocaleString('en-US')} RWF</span>
              </div>
              <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${(c.amount / maxAmount) * 100}%`, backgroundColor: 'var(--itunda-indigo)', borderRadius: '3px' }} />
              </div>
            </div>
          ))
        )}
      </div>
      <BudgetsSection categories={categories} />
    </div>
  );
}

// Real Toss-style monthly budgets/limits (item 165) -- see lib/account.ts's own doc
// comment. `AccountService.setBudget/getBudgets` (including real 80%/100%-threshold
// notifications, wired since 2026-07-28) had zero client anywhere until now.
function BudgetsSection({ categories }: { categories: SpendingCategory[] }) {
  const { t } = useI18n();
  const [budgets, setBudgets] = useState<BudgetView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setError(null);
    fetchBudgets().then(setBudgets).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  return (
    <div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Budgets</h3>
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : '+ Set budget'}
        </button>
      </div>
      {showForm && <SetBudgetForm categories={categories} onSet={() => { setShowForm(false); load(); }} />}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {budgets === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : budgets.length === 0 ? (
        <EmptyState message="No budgets set yet -- set a monthly limit to get alerted before you overspend." />
      ) : (
        budgets.map((b) => {
          const barColor = b.status === 'OVER' ? 'var(--itunda-red)' : b.status === 'NEAR' ? '#F5A623' : 'var(--itunda-indigo)';
          return (
            <div key={b.category ?? 'overall'} style={{ padding: '8px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '4px' }}>
                <span>{b.category ?? 'Overall'}</span>
                <span style={{ fontWeight: 700, color: barColor }}>{b.spent.toLocaleString('en-US')} / {b.monthlyLimit.toLocaleString('en-US')} RWF</span>
              </div>
              <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${Math.min(100, b.percentUsed)}%`, backgroundColor: barColor, borderRadius: '3px' }} />
              </div>
              {b.status === 'OVER' && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '2px' }}>Over budget</p>}
              {b.status === 'NEAR' && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: '#F5A623', marginTop: '2px' }}>Nearing your limit</p>}
            </div>
          );
        })
      )}
    </div>
  );
}

function SetBudgetForm({ categories, onSet }: { categories: SpendingCategory[]; onSet: () => void }) {
  const { t } = useI18n();
  const [category, setCategory] = useState('');
  const [monthlyLimit, setMonthlyLimit] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const limit = Number(monthlyLimit);
    if (!limit || limit <= 0) {
      setError('Enter a real monthly limit.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await setBudget(category || undefined, limit);
      setMonthlyLimit('');
      onSet();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '14px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <select value={category} onChange={(e) => setCategory(e.target.value)} style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
        <option value="">Overall spending</option>
        {categories.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
      </select>
      <input
        type="number"
        min="1"
        value={monthlyLimit}
        onChange={(e) => setMonthlyLimit(e.target.value)}
        placeholder="Monthly limit (RWF)"
        required
        style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ fontSize: 'var(--itunda-type-scale-13-size)', padding: '8px' }}>
        {submitting ? 'Saving…' : 'Save budget'}
      </button>
    </form>
  );
}
