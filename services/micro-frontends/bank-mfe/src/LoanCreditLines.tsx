// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4 -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2). Overdraft
// and Postpaid Credit are itunda's two short-term revolving-credit products (own
// lib/loans.ts data layer, each with exactly one external call site inside
// LoansView.tsx's own mode switch) -- paired in one file since they share the same
// shape and are small enough together to stay well under the 500-line guideline.

import { useEffect, useState } from 'react';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import {
  applyForPostpaidCredit, drawOverdraft, fetchMyOverdraft, fetchMyPostpaidCredit,
  openOverdraft, repayOverdraft, repayPostpaidCredit, spendPostpaidCredit,
  type OverdraftAccount, type PostpaidCreditLine,
} from './lib/loans';

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// lib/loans.ts's OverdraftAccount doc comment for the full sourced account. Found
// 2026-07-29 via a full-backend-endpoint sweep: real, live-verified backend (open/
// draw/repay, real daily interest accrual, real security-alert push) with zero client
// anywhere on any of the 3 platforms.
export function OverdraftView() {
  const { t } = useI18n();
  const [account, setAccount] = useState<OverdraftAccount | null | undefined>(undefined);
  const [requestedLimit, setRequestedLimit] = useState('100000');
  const [drawAmount, setDrawAmount] = useState('');
  const [repayAmount, setRepayAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOverdraft()
      .then(setAccount)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const handleOpen = async () => {
    const limit = Number(requestedLimit);
    if (!limit || limit <= 0) { setError('Enter a valid credit limit.'); return; }
    setBusy(true);
    setError(null);
    try {
      const opened = await openOverdraft(limit);
      setAccount(opened);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleDraw = async () => {
    const amount = Number(drawAmount);
    if (!amount || amount <= 0) { setError('Enter a valid amount to draw.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await drawOverdraft(amount);
      setAccount((prev) => (prev ? { ...prev, drawnBalance: res.drawnBalance } : prev));
      setDrawAmount('');
      setNotice(`Drew ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF still available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async () => {
    const amount = Number(repayAmount);
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await repayOverdraft(amount);
      setAccount((prev) => (prev ? { ...prev, drawnBalance: res.drawnBalance } : prev));
      setRepayAmount('');
      setNotice(`Repaid ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF now available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (account === undefined) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;

  if (account === null) {
    return (
      <div>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Open an overdraft line</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          A pre-approved credit limit you can draw from anytime -- pay interest only on what you actually use, up to 500,000 RWF.
        </p>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        <input
          type="number" value={requestedLimit} onChange={(e) => setRequestedLimit(e.target.value)} placeholder="Requested limit (RWF)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '8px', width: '100%', boxSizing: 'border-box' }}
        />
        <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleOpen}>
          {busy ? 'Opening…' : 'Open overdraft'}
        </button>
      </div>
    );
  }

  const availableCredit = account.creditLimit - account.drawnBalance;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Overdraft line</h4>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Drawn: {account.drawnBalance.toLocaleString()} RWF of {account.creditLimit.toLocaleString()} RWF</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Available to draw: {availableCredit.toLocaleString()} RWF · {account.interestRate}% annual, interest only on what's drawn</p>
      {notice && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>{notice}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <input
        type="number" value={drawAmount} onChange={(e) => setDrawAmount(e.target.value)} placeholder="Draw amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleDraw}>{busy ? 'Drawing…' : 'Draw'}</button>
      <input
        type="number" value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} placeholder="Repay amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="itunda-btn itunda-btn-secondary" disabled={busy || account.drawnBalance <= 0} onClick={handleRepay}>{busy ? 'Repaying…' : 'Repay'}</button>
    </div>
  );
}

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see
// lib/loans.ts's PostpaidCreditLine doc comment for the full sourced account. Genuinely
// distinct from OverdraftView above: no requested-limit input (the limit is
// auto-computed from the caller's own real credit score), no interest shown for
// spending (only a real late fee if a cycle goes unpaid).
export function PostpaidCreditView() {
  const { t } = useI18n();
  const [line, setLine] = useState<PostpaidCreditLine | null | undefined>(undefined);
  const [spendAmount, setSpendAmount] = useState('');
  const [repayAmount, setRepayAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyPostpaidCredit()
      .then(setLine)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const handleApply = async () => {
    setBusy(true);
    setError(null);
    try {
      setLine(await applyForPostpaidCredit());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleSpend = async () => {
    const amount = Number(spendAmount);
    if (!amount || amount <= 0) { setError('Enter a valid amount to spend.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await spendPostpaidCredit(amount);
      setLine((prev) => (prev ? { ...prev, currentBalance: res.currentBalance } : prev));
      setSpendAmount('');
      setNotice(`Added ${res.amount.toLocaleString()} RWF to your account -- ${res.availableCredit.toLocaleString()} RWF still available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async () => {
    const amount = Number(repayAmount);
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await repayPostpaidCredit(amount);
      setLine((prev) => (prev ? { ...prev, currentBalance: res.currentBalance, status: res.currentBalance <= 0 ? 'ACTIVE' : prev.status } : prev));
      setRepayAmount('');
      setNotice(`Repaid ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF now available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (line === undefined) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;

  if (line === null) {
    return (
      <div>
        <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Get postpaid credit</h4>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          A small credit line for real purchases, interest-free if you pay within 30 days -- your limit is set automatically from your credit score, up to 300,000 RWF.
        </p>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleApply}>
          {busy ? 'Applying…' : 'Get postpaid credit'}
        </button>
      </div>
    );
  }

  const availableCredit = line.creditLimit - line.currentBalance;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Postpaid credit</h4>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Owed: {line.currentBalance.toLocaleString()} RWF of {line.creditLimit.toLocaleString()} RWF</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Available: {availableCredit.toLocaleString()} RWF · interest-free if repaid within 30 days</p>
      {line.status === 'SUSPENDED' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', fontWeight: 700 }}>Suspended -- repay your overdue balance to keep spending.</p>
      )}
      {line.cycleDueAt && line.status === 'ACTIVE' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Due by {new Date(line.cycleDueAt).toLocaleDateString()}</p>
      )}
      {notice && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>{notice}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <input
        type="number" value={spendAmount} onChange={(e) => setSpendAmount(e.target.value)} placeholder="Spend amount (RWF)"
        disabled={line.status === 'SUSPENDED'}
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="itunda-btn itunda-btn-primary" disabled={busy || line.status === 'SUSPENDED'} onClick={handleSpend}>{busy ? 'Adding…' : 'Add to account'}</button>
      <input
        type="number" value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} placeholder="Repay amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="itunda-btn itunda-btn-secondary" disabled={busy || line.currentBalance <= 0} onClick={handleRepay}>{busy ? 'Repaying…' : 'Repay'}</button>
    </div>
  );
}
