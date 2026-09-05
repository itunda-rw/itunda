import { useEffect, useState } from 'react';
import { IconBack, IconChevronRight } from './icons/ItundaIcons';
import { BucketDetailScreen } from './BucketDetailScreen';
import { transactionsToBucketTransactions } from './lib/bucketTransaction';
import { fetchAccountTransactions, fetchAccounts, type Account } from './lib/account';
import { fetchGoals, fetchGoalTransactions, fetchInterestJar, fetchInterestJarTransactions, type InterestJar, type SavingsGoal } from './lib/savings';
import { fetchWeeklySavingsPlans, fetchWeeklySavingsPlanTransactions, type WeeklySavingsPlan } from './lib/weeklySavings';
import { fetchGrow31SavingsPlans, fetchGrow31SavingsPlanTransactions, type Grow31SavingsPlan } from './lib/grow31Savings';
import { fetchMyUpfrontDeposits, fetchUpfrontDepositTransactions, type UpfrontInterestDeposit } from './lib/upfrontDeposit';

// Real "itunda Bank assets" hub (2026-08-31, direct user-supplied Toss Bank
// screenshots + explicit correction: "my asset screen is hub of all assets, itunda
// bank assets only itunda bank assets" -- scoped ONLY to itunda Bank's own money
// buckets, deliberately NOT the app-wide net-worth hub (OverviewAssetsView/OVERVIEW
// tab already covers Bank+Pay+everything else; don't conflate the two). A flat
// balance-summary list matching the real "My Toss Bank assets" screen, each row
// opening its own BucketDetailScreen.
type Row =
  | { kind: 'interestJar'; jar: InterestJar }
  | { kind: 'goal'; goal: SavingsGoal }
  | { kind: 'weekly'; plan: WeeklySavingsPlan }
  | { kind: 'grow31'; plan: Grow31SavingsPlan }
  | { kind: 'upfront'; deposit: UpfrontInterestDeposit }
  | { kind: 'youth'; account: Account };

function rowLabel(row: Row): string {
  switch (row.kind) {
    case 'interestJar': return 'Interest Jar';
    case 'goal': return row.goal.name;
    case 'weekly': return row.plan.name;
    case 'grow31': return row.plan.name;
    case 'upfront': return '12-Month Deposit';
    case 'youth': return 'Youth Account';
  }
}

function rowBalance(row: Row): number {
  switch (row.kind) {
    case 'interestJar': return row.jar.balance;
    case 'goal': return row.goal.currentAmount;
    case 'weekly': return row.plan.currentAmount;
    case 'grow31': return row.plan.totalSaved;
    case 'upfront': return row.deposit.principal;
    case 'youth': return row.account.balance;
  }
}

export function ItundaBankAssetsScreen({ onBack }: { onBack: () => void }) {
  const [rows, setRows] = useState<Row[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openRow, setOpenRow] = useState<Row | null>(null);

  useEffect(() => {
    Promise.all([
      fetchInterestJar().catch(() => null),
      fetchGoals().catch(() => []),
      fetchWeeklySavingsPlans().catch(() => []),
      fetchGrow31SavingsPlans().catch(() => []),
      fetchMyUpfrontDeposits().catch(() => []),
      fetchAccounts().catch(() => []),
    ])
      .then(([jar, goals, weekly, grow31, upfront, accounts]) => {
        const youthAccount = accounts.find((a) => a.type === 'MINI');
        const all: Row[] = [
          ...(jar ? [{ kind: 'interestJar', jar } as Row] : []),
          ...goals.filter((g) => g.status === 'active').map((goal) => ({ kind: 'goal', goal } as Row)),
          ...weekly.filter((p) => p.status === 'ACTIVE').map((plan) => ({ kind: 'weekly', plan } as Row)),
          ...grow31.filter((p) => p.status === 'ACTIVE').map((plan) => ({ kind: 'grow31', plan } as Row)),
          ...upfront.filter((d) => !d.withdrawnAt).map((deposit) => ({ kind: 'upfront', deposit } as Row)),
          ...(youthAccount ? [{ kind: 'youth', account: youthAccount } as Row] : []),
        ];
        setRows(all);
      })
      .catch(() => setError('Could not load your itunda Bank assets.'));
  }, []);

  if (openRow) {
    const common = { onBack: () => setOpenRow(null) };
    switch (openRow.kind) {
      case 'interestJar':
        return <BucketDetailScreen {...common} title="Interest Jar" subtitle="Safe Box" balanceText={`${openRow.jar.balance.toLocaleString('en-US')} RWF`} fetchTransactions={fetchInterestJarTransactions} />;
      case 'goal':
        return <BucketDetailScreen {...common} title={openRow.goal.name} subtitle="Savings Goal" balanceText={`${openRow.goal.currentAmount.toLocaleString('en-US')} RWF`} fetchTransactions={() => fetchGoalTransactions(openRow.goal.id)} />;
      case 'weekly':
        return <BucketDetailScreen {...common} title={openRow.plan.name} subtitle="26-Week Savings" balanceText={`${openRow.plan.currentAmount.toLocaleString('en-US')} RWF`} fetchTransactions={() => fetchWeeklySavingsPlanTransactions(openRow.plan.id)} />;
      case 'grow31':
        return <BucketDetailScreen {...common} title={openRow.plan.name} subtitle="31-Day Savings" balanceText={`${openRow.plan.totalSaved.toLocaleString('en-US')} RWF`} fetchTransactions={() => fetchGrow31SavingsPlanTransactions(openRow.plan.id)} />;
      case 'upfront':
        return <BucketDetailScreen {...common} title="12-Month Deposit" subtitle="Upfront Interest Deposit" balanceText={`${openRow.deposit.principal.toLocaleString('en-US')} RWF`} fetchTransactions={() => fetchUpfrontDepositTransactions(openRow.deposit.id)} />;
      case 'youth':
        return <BucketDetailScreen {...common} title="Youth Account" subtitle={openRow.account.accountNumber} balanceText={`${openRow.account.balance.toLocaleString('en-US')} RWF`} fetchTransactions={() => fetchAccountTransactions(openRow.account.id).then((txs) => transactionsToBucketTransactions(txs, openRow.account.id, openRow.account.balance))} />;
    }
  }

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1000, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 16px' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px' }}>
            <IconBack size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>
        <div style={{ padding: '4px 20px 24px' }}>
          <p style={{ margin: 0, fontSize: '20px', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>itunda Bank assets</p>
        </div>
        <div style={{ padding: '0 20px' }}>
          {error ? (
            <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>
          ) : rows === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
          ) : rows.length === 0 ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', padding: '24px 0' }}>
              You don&apos;t have any itunda Bank products open yet.
            </p>
          ) : (
            rows.map((row, i) => (
              <button
                key={i}
                onClick={() => setOpenRow(row)}
                style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', padding: '12px 0', borderBottom: '1px solid var(--itunda-grey-100)', textAlign: 'left' }}
              >
                <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{rowLabel(row)}</span>
                <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{rowBalance(row).toLocaleString('en-US')} RWF</span>
                  <IconChevronRight size={16} color="var(--itunda-grey-400)" />
                </span>
              </button>
            ))
          )}
        </div>
      </div>
    </div>
  );
}
