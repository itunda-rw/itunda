import { useEffect, useState } from 'react';
import { EmptyState } from './EmptyState';
import { BucketTransactionList } from './BucketDetailScreen';
import { useI18n } from './i18n/I18nContext';
import { type BucketTransaction } from './lib/bucketTransaction';
import { useDeferredLoading } from './useDeferredLoading';
import { ApiError } from './lib/api';
import {
  buySaccoShares, fetchMySaccoDividendHistory, fetchMySaccoShareholding, redeemSaccoShares,
  type SaccoDividendPayout, type SaccoShareholding,
} from './lib/sacco';
import {
  fetchMyUpfrontDeposits, fetchUpfrontDepositTransactions, openUpfrontDeposit, withdrawUpfrontDeposit,
  UPFRONT_DEPOSIT_ANNUAL_RATE, UPFRONT_DEPOSIT_MIN_PRINCIPAL, UPFRONT_DEPOSIT_MAX_PRINCIPAL, type UpfrontInterestDeposit,
} from './lib/upfrontDeposit';

export function SaccoSection() {
  const { t } = useI18n();
  const [shareholding, setShareholding] = useState<SaccoShareholding | null | undefined>(undefined);
  const [currentValue, setCurrentValue] = useState<number | null>(null);
  const [dividends, setDividends] = useState<SaccoDividendPayout[] | null>(null);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMySaccoShareholding()
      .then((r) => { setShareholding(r.shareholding); setCurrentValue(r.currentValue); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchMySaccoDividendHistory().then(setDividends).catch(() => setDividends([]));
  };
  useEffect(load, []);

  const handleBuy = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    setError(null);
    try {
      await buySaccoShares(value);
      setAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRedeem = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    setError(null);
    try {
      await redeemSaccoShares(value);
      setAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>SACCO shares</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>
        Buy real shares in itunda's own SACCO pool and earn periodic dividends, the same real cooperative model as Rwanda's 416 Umurenge SACCOs.
      </p>
      <div className="itunda-flat-section">
        {shareholding === undefined ? (
          <div style={{ height: '48px' }} />
        ) : (
          <>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Shares held</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700 }}>{(shareholding?.sharesHeld ?? 0).toLocaleString('en-US')} RWF</p>
            {currentValue != null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Total contributed: {(shareholding?.totalContributed ?? 0).toLocaleString('en-US')} RWF</p>
            )}
          </>
        )}
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
        {/* Real gap found live (2026-08-10) via an actual CDP screenshot of this exact
            section (just promoted to Home this session): three real elements --
            an input plus two buttons ("Buy" and "Redeem") -- in one unwrapped flex
            row with no overflow handling. "Redeem" was cut off past the visible
            edge on a real 390px viewport. flexWrap lets Buy/Redeem drop to their own
            row on a narrow screen instead of vanishing -- better here than a
            horizontal scroll, since these are primary form actions, not a nav list. */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '12px' }}>
          <input
            type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ flex: '1 1 140px', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleBuy} style={{ flexShrink: 0 }}>Buy</button>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleRedeem} style={{ flexShrink: 0 }}>Redeem</button>
        </div>
      </div>
      {dividends && dividends.length > 0 && (
        <div className="itunda-flat-section">
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '8px' }}>Dividend history</p>
          {dividends.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '4px 0' }}>
              <span style={{ color: 'var(--itunda-grey-500)' }}>{new Date(d.createdAt).toLocaleDateString()}</span>
              <span style={{ fontWeight: 700 }}>+{d.amount.toLocaleString('en-US')} RWF</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real KakaoBank 26주적금 (26-week savings) -- see lib/weeklySavings.ts's own doc
// comment. Sibling to GroupAccountDetailView/CreateGroupAccountForm/
// GroupAccountsSection above, same list -> detail shape, but this product's real
// differentiator (escalating auto-debit, streak-gated bonus rate) is surfaced
// explicitly in copy rather than looking like a generic savings account.
export function UpfrontDepositSection() {
  const { t } = useI18n();
  const [deposits, setDeposits] = useState<UpfrontInterestDeposit[] | null>(null);
  const showSkeleton = useDeferredLoading(deposits === null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyUpfrontDeposits().then(setDeposits).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  return (
    <div>
      {/* Real gap found live (2026-08-31) -- see WeeklySavingsSection's identical
          fix above. Sourced from UpfrontInterestDepositService's own real
          ANNUAL_RATE constant. */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>12-month deposit</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>2.80%/yr interest paid upfront, principal locked</p>
      <OpenUpfrontDepositForm onOpened={load} />
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '16px' }} role="alert">{error}</p>
      )}
      {deposits === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : deposits.length === 0 ? (
        <EmptyState message="No 12-month deposits yet — open one to get a full year's interest paid today, principal locked for 12 months." />
      ) : (
        deposits.map((d) => <UpfrontDepositCard key={d.id} deposit={d} onChanged={load} />)
      )}
    </div>
  );
}

function OpenUpfrontDepositForm({ onOpened }: { onOpened: () => void }) {
  const { t } = useI18n();
  const [principal, setPrincipal] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await openUpfrontDeposit(Number(principal));
      setPrincipal('');
      onOpened();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
        {UPFRONT_DEPOSIT_ANNUAL_RATE}% interest for the full year, paid to your account today. Principal is locked for 12 months — no early withdrawal.
      </p>
      <input
        type="number"
        min={UPFRONT_DEPOSIT_MIN_PRINCIPAL}
        max={UPFRONT_DEPOSIT_MAX_PRINCIPAL}
        value={principal}
        onChange={(e) => setPrincipal(e.target.value)}
        placeholder={`Principal (${UPFRONT_DEPOSIT_MIN_PRINCIPAL.toLocaleString('en-US')} - ${UPFRONT_DEPOSIT_MAX_PRINCIPAL.toLocaleString('en-US')} RWF)`}
        required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? 'Opening…' : 'Open deposit'}
      </button>
    </form>
  );
}

function UpfrontDepositCard({ deposit, onChanged }: { deposit: UpfrontInterestDeposit; onChanged: () => void }) {
  const { t } = useI18n();
  const [withdrawing, setWithdrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const matured = deposit.status === 'MATURED';
  // Real per-bucket ledger (2026-08-31) -- this card previously had no drill-in of
  // any kind (its own doc history noted "everything a deposit needs fits on its list
  // row," true for the summary fields but not for a real transaction history, which
  // its dedicated account has always had, just never exposed).
  const [showHistory, setShowHistory] = useState(false);
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);
  const toggleHistory = () => {
    setShowHistory((v) => !v);
    if (!showHistory && transactions === null) {
      fetchUpfrontDepositTransactions(deposit.id).then(setTransactions).catch(() => setTransactions([]));
    }
  };

  const handleWithdraw = async () => {
    setError(null);
    setWithdrawing(true);
    try {
      await withdrawUpfrontDeposit(deposit.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setWithdrawing(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{deposit.principal.toLocaleString('en-US')} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{deposit.withdrawnAt ? 'WITHDRAWN' : deposit.status}</p>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        +{deposit.interestPaid.toLocaleString('en-US')} RWF interest already paid · matures {new Date(deposit.maturesAt).toLocaleDateString()}
      </p>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
      <button onClick={toggleHistory} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginTop: '8px', fontWeight: 600 }}>
        {showHistory ? 'Hide history' : 'View history'}
      </button>
      {showHistory && (
        <div style={{ marginTop: '6px' }}>
          <BucketTransactionList transactions={transactions} />
        </div>
      )}
      {matured && !deposit.withdrawnAt && (
        <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '10px' }} disabled={withdrawing} onClick={handleWithdraw}>
          {withdrawing ? 'Withdrawing…' : 'Withdraw principal'}
        </button>
      )}
    </div>
  );
}

// Real Toss Bank reference (9 screenshots, 2026-08-23, direct user instruction: "all
// services itunda provide with all in clear UX and UX writing as it's in those
// pictures"): the real Toss Bank account screen organizes its whole product catalog
// under bold, flat category headers (Demand Deposits / Savings / Foreign Currency /
// Loan / Service / ...), not one undifferentiated scroll -- this had been a real,
// previously-BLOCKED gap (SavingsView's own stacked sections had no headers at all,
// and an earlier pass explicitly couldn't find a real Toss reference for organizing a
// long product list -- see docs/DESIGN_REFERENCES.md). Flat text, no card wrapper,
// matching this codebase's own standing flat-design law and Toss's real screenshots
// exactly (a bold label sits directly on the page background, not inside a boxed
// section). Names itunda's own REAL product categories, not Toss's invented ones --
// no "Refinancing"/"Bonds & notes"/"Mortgage Finder" here, since itunda doesn't have
// those; "Cooperative & Group" replaces Toss's own category shape with itunda's real,
// Rwanda-specific SACCO/Ikimina/group-account products instead.
export function CatalogSectionHeader({ title }: { title: string }) {
  return (
    <h2 style={{ margin: '32px 4px 8px', fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
      {title}
    </h2>
  );
}
