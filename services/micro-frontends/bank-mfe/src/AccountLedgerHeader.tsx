import { useEffect, useState } from 'react';
import { EmptyState } from './EmptyState';
import { fetchAccountTransactions, fetchAccounts, type Account, type Transaction } from './lib/account';

// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user instruction:
// "should look 100% like in this images pixels by pixels"): the real account-
// detail screen leads with the MAIN account's own balance and transaction ledger
// -- itunda's web "itunda Bank" access point (SavingsView) had every real product
// in its own catalog already (SACCO/Ikimina/savings/loans/investments) but no
// balance or ledger at all. This is that missing top section, rendered inline
// (not a full-screen push -- SavingsView is a normal scrollable tab), flat per
// the same 2026-08-21 flat-over-card-heavy directive PayMoneyDetail.tsx already
// follows. Its own file per docs/ARCHITECTURE_GUIDELINES.md §2 -- BankDashboard.tsx
// is already the tracked file-size-lint backlog's largest offender.
//
// "Top up" is a real, honest gap on this platform: Android's own equivalent
// button opens a real AgentCashScreen (show the customer's account to a nearby
// itunda agent for a cash deposit) with a real map filtered to agent locations --
// bank-mfe has neither a customer-facing agent-cash screen nor an agent map
// filter yet, so only Send (a real, fully-wired flow) renders here rather than a
// button that would silently do nothing.
export function AccountLedgerHeader({ onSend }: { onSend: (account: Account) => void }) {
  const [account, setAccount] = useState<Account | null | undefined>(undefined);
  const [transactions, setTransactions] = useState<Transaction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAccounts()
      .then((accounts) => {
        const main = accounts.find((a) => a.type === 'MAIN') ?? null;
        setAccount(main);
        if (main) {
          fetchAccountTransactions(main.id).then(setTransactions).catch(() => setError('Could not load your transaction history.'));
        }
      })
      .catch(() => setAccount(null));
  }, []);

  if (account === undefined) return null;
  if (account === null) return null;

  const sorted = [...(transactions ?? [])].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const groups = sorted.reduce<{ label: string; items: Transaction[] }[]>((acc, tx) => {
    const label = new Date(tx.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
    const last = acc[acc.length - 1];
    if (last && last.label === label) last.items.push(tx);
    else acc.push({ label, items: [tx] });
    return acc;
  }, []);

  return (
    <div style={{ marginBottom: '8px' }}>
      <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
        itunda {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
      </p>
      <p style={{ margin: '6px 0 20px', fontSize: '32px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
        {account.currency} {account.balance.toLocaleString()}
      </p>
      <button onClick={() => onSend(account)} className="itunda-btn itunda-btn-primary" style={{ minHeight: '48px', borderRadius: '999px', width: '100%', marginBottom: '20px' }}>
        Send
      </button>

      <div style={{ borderTop: '8px solid var(--itunda-grey-100)', margin: '0 -20px 20px' }} />

      {error ? (
        <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>
      ) : transactions === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : sorted.length === 0 ? (
        <EmptyState message="No transactions yet" />
      ) : (
        <div>
          {groups.slice(0, 3).map((group) => (
            <div key={group.label}>
              <p style={{ margin: 0, padding: '10px 0 4px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>{group.label}</p>
              {group.items.map((tx) => {
                const isCredit = tx.toAccountId === account.id && tx.fromAccountId !== account.id;
                return (
                  <div key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                    <div>
                      <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{tx.description}</p>
                      <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
                    </div>
                    <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: isCredit ? 'var(--itunda-blue)' : 'var(--itunda-grey-900)' }}>
                      {isCredit ? '+' : '-'}{tx.amount.toLocaleString()} {tx.currency}
                    </span>
                  </div>
                );
              })}
            </div>
          ))}
        </div>
      )}

      <div style={{ borderTop: '8px solid var(--itunda-grey-100)', margin: '20px -20px 0' }} />
    </div>
  );
}
