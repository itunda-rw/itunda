import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { ArrowDownLeft, ArrowUpRight } from 'lucide-react';
import { IconBack } from './icons/ItundaIcons';
import { IdsButton } from './IdsButton';
import type { BucketTransaction } from './lib/bucketTransaction';

// Real per-bucket detail screen (2026-08-31, direct user-supplied Toss Bank
// screenshots: 보관하기/매일모으기 each get their own full-screen ledger, not just an
// inline card on the itunda Bank catalog). Generalizes AccountDetailScreen.tsx's own
// fixed-overlay/balance-hero/date-grouped-ledger shape to work for ANY savings bucket
// (Interest Jar, a Savings Goal, a Weekly/Grow31/Upfront plan, the Youth account) --
// takes plain primitives + a fetchTransactions() call instead of assuming a real
// primary Account, so it stays reusable across products with genuinely different
// backing data (a shared LedgerEntry-backed ledger for most buckets, a Transaction-
// backed one for the Youth account, both already normalized into the same
// BucketTransaction shape server-side, see BucketTransactionDto's own doc comment).
export function BucketDetailScreen({
  title,
  subtitle,
  balanceText,
  secondaryStat,
  fetchTransactions,
  fillLabel,
  onFill,
  withdrawLabel,
  onWithdraw,
  onBack,
}: {
  title: string;
  subtitle?: string;
  balanceText: string;
  secondaryStat?: { label: string; value: string };
  fetchTransactions: () => Promise<BucketTransaction[]>;
  fillLabel?: string;
  onFill?: () => void;
  withdrawLabel?: string;
  onWithdraw?: () => void;
  onBack: () => void;
}) {
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTransactions().then(setTransactions).catch(() => setError('Could not load this account\'s history.'));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>

        <div style={{ padding: '4px 20px 24px' }}>
          {subtitle && (
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{subtitle}</p>
          )}
          <p style={{ margin: '6px 0 0', fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{title}</p>
          <p style={{ margin: '4px 0 0', fontSize: '32px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>{balanceText}</p>
          {secondaryStat && (
            <p style={{ margin: '6px 0 0', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {secondaryStat.label}: {secondaryStat.value}
            </p>
          )}
          {(onFill || onWithdraw) && (
            <div style={{ display: 'flex', gap: '10px', marginTop: '20px' }}>
              {onFill && (
                <div style={{ flex: 1 }}>
                  <IdsButton variant="tinted" fullWidth onClick={onFill}>{fillLabel ?? 'Fill'}</IdsButton>
                </div>
              )}
              {onWithdraw && (
                <div style={{ flex: 1 }}>
                  <IdsButton variant="tinted" fullWidth onClick={onWithdraw}>{withdrawLabel ?? 'Withdraw'}</IdsButton>
                </div>
              )}
            </div>
          )}
        </div>

        <div style={{ padding: '0 20px' }}>
          <BucketTransactionList transactions={transactions} error={error} />
        </div>
      </div>
    </div>
  );
}

// Extracted (2026-08-31) so Weekly/Grow31/Upfront's own inline detail views (which
// keep their existing plan-specific summary header, not this component's full-screen
// overlay shell) can render the exact same ledger-row visual as the fixed-overlay
// buckets above, instead of duplicating this JSX three more times.
export function BucketTransactionList({ transactions, error }: { transactions: BucketTransaction[] | null; error?: string | null }) {
  const groups = transactions?.reduce<{ label: string; items: BucketTransaction[] }[]>((acc, tx) => {
    const label = new Date(tx.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const last = acc[acc.length - 1];
    if (last && last.label === label) last.items.push(tx);
    else acc.push({ label, items: [tx] });
    return acc;
  }, []);

  if (error) {
    return <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>;
  }
  if (transactions === null) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>;
  }
  if (transactions.length === 0) {
    // Real, honest empty state (2026-08-31) -- a pre-existing bucket migrated to
    // per-bucket ledger isolation on the day this shipped has no itemized history
    // before that point (see backend's ensureGoalLedgerAccount doc comment for the
    // full account); this says so rather than implying the bucket has never had any
    // activity.
    return (
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', padding: '12px 0' }}>
        No transactions to show yet. If this account already held money before today, its itemized history starts from here.
      </p>
    );
  }
  return (
    <div>
      {groups?.map((group) => (
        <div key={group.label}>
          <p style={{ margin: 0, padding: '14px 0 6px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>{group.label}</p>
          {group.items.map((tx) => (
            <motion.div
              key={tx.id}
              whileTap={{ scale: 0.98 }}
              style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}
            >
              <div style={{ width: '38px', height: '38px', borderRadius: '999px', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: tx.isCredit ? 'var(--itunda-indigo)' : 'var(--itunda-grey-400)' }}>
                {tx.isCredit ? <ArrowDownLeft size={18} color="#fff" /> : <ArrowUpRight size={18} color="#fff" />}
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{tx.description}</p>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
              </div>
              <div style={{ textAlign: 'right' }}>
                <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: tx.isCredit ? 'var(--itunda-indigo)' : 'var(--itunda-grey-900)' }}>
                  {tx.isCredit ? '+' : '-'}{tx.amount.toLocaleString('en-US')}
                </p>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>{tx.balanceAfter.toLocaleString('en-US')}</p>
              </div>
            </motion.div>
          ))}
        </div>
      ))}
    </div>
  );
}
