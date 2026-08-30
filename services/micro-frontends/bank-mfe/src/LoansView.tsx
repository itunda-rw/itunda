// Extracted from BankDashboard.tsx (2026-08-30, itunda-vs-Toss architecture
// comparison thread's own open recommendation 4: "further internal decomposition
// of BankDashboard.tsx is the Toss-aligned move" -- see
// project_itunda_architecture_vs_toss.md, ARCHITECTURE_GUIDELINES.md §2's own
// "Code that changes together lives together" rule). The whole Loans domain (own
// lib/loans.ts data layer, exactly one external call site --
// `{tab === 'LOANS' && <LoansView .../>}`) is a large, self-contained product
// vertical -- split into this file (the offers/my-loans core) plus 5 sibling files
// per lending product (LoanCreditLines.tsx for Overdraft+Postpaid,
// HarvestAdvanceView.tsx, VupLoanView.tsx, StudentLoanView.tsx,
// MotoOwnershipView.tsx), each imported here and switched on by LoansView's own
// `mode` state, rather than one 1,250+ line file.

import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useI18n } from './i18n/I18nContext';
import { ApiError } from './lib/api';
import { EmptyState } from './EmptyState';
import { OverdraftView, PostpaidCreditView } from './LoanCreditLines';
import { HarvestAdvanceView } from './HarvestAdvanceView';
import { VupLoanView } from './VupLoanView';
import { StudentLoanView } from './StudentLoanView';
import { MotoOwnershipView } from './MotoOwnershipView';
import {
  applyForLoan, fetchLenders, fetchLoanOffers, fetchMyLoans, refinanceLoan, repayLoan,
  type Lender, type LoanAccount, type LoanOffer,
} from './lib/loans';

// Real multi-lender loan marketplace (2026-07-22) -- found fully built on the backend
// (rw.itunda.loans, BNR-licensed partner banks alongside itunda's own book, see
// LoanOffer.kt's own doc comment) with zero client UI anywhere.
export type LoansMode = 'OFFERS' | 'MY_LOANS' | 'OVERDRAFT' | 'POSTPAID_CREDIT' | 'HARVEST_ADVANCE' | 'VUP' | 'STUDENT' | 'MOTO_OWNERSHIP';

// Real gap found live (2026-08-10) while checking the coop rail's own Loans-tab
// destinations for the first time (this session just added them to Home): the rail's
// "Moto-Taxi Ownership"/"Harvest advance" taps only switch the top-level Tab to
// 'LOANS' -- this view's own `mode` was always pure internal state with no way to set
// it from outside, so both taps landed on the generic Offers catalog, not the actual
// product. `initialMode` closes that -- optional, defaults to the pre-existing
// behavior, so LoansView's other caller (the "Loans" row inside "More") is unaffected.
export function LoansView({ initialMode, onConsumedInitialMode }: { initialMode?: LoansMode; onConsumedInitialMode?: () => void } = {}) {
  const { t } = useI18n();
  const [mode, setMode] = useState<LoansMode>(initialMode ?? 'OFFERS');
  // Consume once so a later, normal navigation into Loans (e.g. via "More") doesn't
  // keep landing on the same specific mode -- same pattern Android/iOS's HoodTab
  // initialMode/onConsumedInitialMode already establishes.
  useEffect(() => {
    if (initialMode) onConsumedInitialMode?.();
  }, []);
  const [offers, setOffers] = useState<LoanOffer[] | null>(null);
  const [myLoans, setMyLoans] = useState<LoanAccount[] | null>(null);
  const [lenders, setLenders] = useState<Lender[] | null>(null);
  const [lenderId, setLenderId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    Promise.all([fetchLoanOffers(), fetchMyLoans(), fetchLenders()])
      .then(([o, l, ln]) => { setOffers(o); setMyLoans(l); setLenders(ln); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  // Real "browse by lender" filter (2026-07-29) -- `getLenders`/`lenderId`-filtered
  // `getOffers` were both real backend endpoints with zero client anywhere: every offer
  // already showed its lenderName, but there was no way to browse the BNR-licensed
  // partner banks (Bank of Kigali/Equity/Urwego) alongside itunda's own book as a group.
  const selectLender = (id: string | null) => {
    setLenderId(id);
    setError(null);
    fetchLoanOffers(id ?? undefined)
      .then(setOffers)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  const handleApply = async (offer: LoanOffer, amount: number) => {
    setBusyId(offer.id);
    setError(null);
    try {
      await applyForLoan(offer.id, amount);
      setMode('MY_LOANS');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real Toss writing-principle adoption ("숨은 감정 찾기" -- find the hidden emotion):
  // toss.tech/article/8-writing-principles-of-toss names a fully-repaid loan as their
  // own example of a moment that deserves more than transactional silence. Paying off
  // a loan just refreshed the list silently before this, even though the repay response
  // already tells us `remaining` hit zero.
  const [payoffMessage, setPayoffMessage] = useState<string | null>(null);
  const handleRepay = async (loan: LoanAccount) => {
    const amount = Number(repayAmounts[loan.id] ?? '');
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(loan.id);
    setError(null);
    try {
      const result = await repayLoan(loan.id, amount);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loan.id]; return next; });
      setPayoffMessage(result.remaining <= 0 ? 'You paid off this loan in full — one less thing to carry.' : null);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real 대환대출 (loan refinancing, 2026-07-26) -- see LoansService.refinanceLoan's
  // own doc comment.
  const [refinanceResult, setRefinanceResult] = useState<{ oldRate: number; newRate: number; newLoanName: string } | null>(null);
  const handleRefinance = async (loan: LoanAccount) => {
    setBusyId(loan.id);
    setError(null);
    setRefinanceResult(null);
    try {
      const result = await refinanceLoan(loan.id);
      setRefinanceResult({ oldRate: result.oldInterestRate, newRate: result.newInterestRate, newLoanName: result.newLoanName });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {/* Real gap found live (2026-08-10) via an actual CDP screenshot: same silent-
          overflow bug as the 13 sub-tab bars already fixed this session
          (c6cb9099/78371230/f0903fab) -- 4 of these 8 buttons, including "Harvest
          advance"/"Moto-Taxi Ownership" (both just linked from Home), were cut off
          past the visible edge on a real 390px viewport with zero scroll affordance. */}
      <div style={{ display: 'flex', gap: '8px', overflowX: 'auto' }}>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('OFFERS')} style={{ flexShrink: 0 }}>Offers</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('MY_LOANS')} style={{ flexShrink: 0 }}>My loans ({myLoans?.length ?? 0})</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('OVERDRAFT')} style={{ flexShrink: 0 }}>Overdraft</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('POSTPAID_CREDIT')} style={{ flexShrink: 0 }}>Postpaid credit</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('HARVEST_ADVANCE')} style={{ flexShrink: 0 }}>Harvest advance</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('VUP')} style={{ flexShrink: 0 }}>VUP Financial Services</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('STUDENT')} style={{ flexShrink: 0 }}>BRD Student Loan</button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setMode('MOTO_OWNERSHIP')} style={{ flexShrink: 0 }}>Moto-Taxi Ownership</button>
      </div>
      {mode === 'OVERDRAFT' && <OverdraftView />}
      {mode === 'POSTPAID_CREDIT' && <PostpaidCreditView />}
      {mode === 'HARVEST_ADVANCE' && <HarvestAdvanceView />}
      {mode === 'VUP' && <VupLoanView />}
      {mode === 'STUDENT' && <StudentLoanView />}
      {mode === 'MOTO_OWNERSHIP' && <MotoOwnershipView />}
      {mode !== 'OVERDRAFT' && mode !== 'POSTPAID_CREDIT' && mode !== 'HARVEST_ADVANCE' && mode !== 'VUP' && mode !== 'STUDENT' && mode !== 'MOTO_OWNERSHIP' && error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {mode !== 'OVERDRAFT' && mode !== 'POSTPAID_CREDIT' && mode !== 'HARVEST_ADVANCE' && mode !== 'VUP' && mode !== 'STUDENT' && mode !== 'MOTO_OWNERSHIP' && payoffMessage && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{payoffMessage}</p>}
      {mode !== 'OVERDRAFT' && mode !== 'POSTPAID_CREDIT' && mode !== 'HARVEST_ADVANCE' && mode !== 'VUP' && mode !== 'STUDENT' && mode !== 'MOTO_OWNERSHIP' && refinanceResult && (
        <div style={{ padding: '12px 0', borderLeft: '2px solid var(--itunda-indigo)', paddingLeft: '12px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Refinanced into {refinanceResult.newLoanName}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{refinanceResult.oldRate}% → {refinanceResult.newRate}%</p>
        </div>
      )}
      {mode === 'OFFERS' && (
        <>
          {lenders && (
            <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', paddingBottom: '2px' }}>
              <button
                className="itunda-btn itunda-btn-secondary"
                style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px', whiteSpace: 'nowrap', ...(lenderId === null ? { border: '1px solid var(--itunda-indigo)', color: 'var(--itunda-indigo)' } : {}) }}
                onClick={() => selectLender(null)}
              >
                All lenders
              </button>
              {lenders.map((lender) => (
                <button
                  key={lender.id}
                  className="itunda-btn itunda-btn-secondary"
                  style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px', whiteSpace: 'nowrap', ...(lenderId === lender.id ? { border: '1px solid var(--itunda-indigo)', color: 'var(--itunda-indigo)' } : {}) }}
                  onClick={() => selectLender(lender.id)}
                >
                  {lender.name}
                </button>
              ))}
            </div>
          )}
          {offers === null ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> :
           offers.length === 0 ? <EmptyState message="No offers right now — check back later or explore another lender." /> :
           offers.map((offer) => (
            <LoanOfferCard key={offer.id} offer={offer} busy={busyId === offer.id} onApply={(amount) => handleApply(offer, amount)} />
          ))}
        </>
      )}
      {mode === 'MY_LOANS' && (
        myLoans === null ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> :
        myLoans.length === 0 ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>You have no loans yet.</p> :
        myLoans.map((loan) => (
          <div key={loan.id} className="itunda-flat-section">
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{loan.principal.toLocaleString()} RWF loan</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Outstanding: {loan.outstanding.toLocaleString()} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Status: {loan.status} · {loan.interestRate}%</p>
            {loan.status === 'ACTIVE' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                <input
                  type="number" value={repayAmounts[loan.id] ?? ''}
                  onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                  placeholder="Repay amount (RWF)"
                  style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                />
                <button className="itunda-btn itunda-btn-primary" disabled={busyId === loan.id} onClick={() => handleRepay(loan)}>
                  {busyId === loan.id ? 'Repaying…' : 'Repay'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === loan.id} onClick={() => handleRefinance(loan)}>
                  {busyId === loan.id ? 'Checking…' : 'Refinance to a lower rate'}
                </button>
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}

// Real Toss decision framework, applied directly (2026-08-29, toss.tech/article/
// interaction's own real, sourced principle: prioritize high-abandonment/trust-
// building moments over decorative flourishes -- their own named example is a loan
// ASSESSMENT loading screen, changed from a static placeholder to real-time content
// that incrementally builds confidence while a lending decision is made). itunda's
// own real loan-apply moment had ZERO acknowledgment before this -- not even a
// spinner overlay, just an inline button-label swap to "Applying…" (found via a
// real audit fork this session). Steps below are the REAL gates
// LoansService.applyForLoan actually runs in order (credit-score check, account-type
// check, ledger disbursement) -- not invented filler copy, matching Toss's own
// principle of using real product content to build trust, not generic decoration.
const LOAN_APPLY_STEPS = ['Checking your credit score', 'Confirming loan terms', 'Disbursing your funds'];

function LoanApplyProgress() {
  const [stepIndex, setStepIndex] = useState(0);
  useEffect(() => {
    const id = setInterval(() => setStepIndex((i) => Math.min(i + 1, LOAN_APPLY_STEPS.length - 1)), 900);
    return () => clearInterval(id);
  }, []);
  return (
    <div style={{ padding: '20px 0', textAlign: 'center' }}>
      <motion.div
        animate={{ rotate: 360 }}
        transition={{ duration: 0.8, repeat: Infinity, ease: 'linear' }}
        style={{ width: '32px', height: '32px', margin: '0 auto 14px', border: '3px solid var(--itunda-indigo-light)', borderTopColor: 'var(--itunda-indigo)', borderRadius: '50%' }}
      />
      <AnimatePresence mode="wait">
        <motion.p
          key={stepIndex}
          initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }}
          transition={{ duration: 0.25 }}
          style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}
        >
          {LOAN_APPLY_STEPS[stepIndex]}
        </motion.p>
      </AnimatePresence>
    </div>
  );
}

function LoanOfferCard({ offer, busy, onApply }: { offer: LoanOffer; busy: boolean; onApply: (amount: number) => void }) {
  const [amount, setAmount] = useState(String(offer.maxAmount));
  if (busy) return <LoanApplyProgress />;
  return (
    <div style={{ padding: '12px 0' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{offer.name}</h4>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{offer.lenderName}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Up to {offer.maxAmount.toLocaleString()} RWF · {offer.interestRate}% · {offer.term}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{offer.requirements}</p>
      <input
        type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '8px', width: '100%', boxSizing: 'border-box' }}
      />
      <button
        className="itunda-btn itunda-btn-primary" style={{ marginTop: '8px' }} disabled={busy}
        onClick={() => { const n = Number(amount); if (n > 0) onApply(n); }}
      >
        Apply
      </button>
    </div>
  );
}
