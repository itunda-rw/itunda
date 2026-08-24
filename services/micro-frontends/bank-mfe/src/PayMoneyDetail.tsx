import { useEffect, useState } from 'react';
import {  } from 'lucide-react';
import { IconBack } from './icons/ItundaIcons';
import { EmptyState } from './EmptyState';
import { fetchAccountTransactions, type Account, type Transaction } from './lib/account';

// Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21):
// reached by drilling into the balance row on the Pay tab's payment-code card,
// structurally parallel to the real Toss Bank account-detail screen but scoped to
// this one account's own transactions via the new account-scoped endpoint (see
// lib/account.ts's fetchAccountTransactions / AccountService.getAccountTransactionHistory
// on the backend). Deliberately flat throughout -- no itunda-card wrapper around the
// balance, the Send/Add money buttons, or the transaction rows, matching both the
// real reference screenshot and a direct 2026-08-21 user instruction to move
// itunda's designs toward flat over card-heavy. Only a thin section rule and
// per-row bottom borders separate content, same as the reference. Its own file
// (rather than inline in BankDashboard.tsx) per docs/ARCHITECTURE_GUIDELINES.md §2 --
// BankDashboard.tsx is already the tracked file-size-lint backlog's largest offender.
export function PayMoneyDetail({ account, onBack, onSend, onAddMoney }: { account: Account; onBack: () => void; onSend: () => void; onAddMoney: () => void }) {
  const [transactions, setTransactions] = useState<Transaction[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paymentsOnly, setPaymentsOnly] = useState(false);

  useEffect(() => {
    fetchAccountTransactions(account.id)
      .then(setTransactions)
      .catch(() => setError('Could not load your transaction history.'));
  }, [account.id]);

  const visible = (transactions ?? []).filter((tx) => !paymentsOnly || tx.type === 'PAYMENT');

  // Real date-grouped statement (matches the reference's own grouping), flat rows --
  // a bottom border is the only separator, no per-transaction card.
  const groups = visible.reduce<{ label: string; items: Transaction[] }[]>((acc, tx) => {
    const label = new Date(tx.createdAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric' });
    const last = acc[acc.length - 1];
    if (last && last.label === label) last.items.push(tx);
    else acc.push({ label, items: [tx] });
    return acc;
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
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>itunda Pay Money</p>
          <p style={{ margin: '6px 0 20px', fontSize: '32px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
            {account.balance.toLocaleString()} RWF
          </p>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button onClick={onSend} className="itunda-btn itunda-btn-secondary" style={{ flex: 1, minHeight: '48px', borderRadius: '999px' }}>Send</button>
            <button onClick={onAddMoney} className="itunda-btn itunda-btn-primary" style={{ flex: 1, minHeight: '48px', borderRadius: '999px' }}>Add money</button>
          </div>
        </div>

        <div style={{ borderTop: '8px solid var(--itunda-grey-100)' }} />

        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '16px 20px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
            {new Date().toLocaleDateString(undefined, { month: 'long' })}
          </span>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>See payment history only</span>
            <button
              type="button" role="switch" aria-checked={paymentsOnly} aria-label="See payment history only"
              onClick={() => setPaymentsOnly((v) => !v)}
              style={{
                width: '40px', height: '24px', borderRadius: '12px', padding: '2px', flexShrink: 0,
                background: paymentsOnly ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)', display: 'flex', justifyContent: paymentsOnly ? 'flex-end' : 'flex-start',
              }}
            >
              <span style={{ width: '20px', height: '20px', borderRadius: '10px', background: 'white', display: 'block' }} />
            </button>
          </div>
        </div>

        {error ? (
          <p role="alert" style={{ padding: '0 20px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>
        ) : transactions === null ? (
          <p style={{ padding: '0 20px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        ) : visible.length === 0 ? (
          <div style={{ padding: '48px 20px' }}>
            <EmptyState message="No details found" />
          </div>
        ) : (
          <div>
            {groups.map((group) => (
              <div key={group.label}>
                <p style={{ margin: 0, padding: '14px 20px 6px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>{group.label}</p>
                {group.items.map((tx) => {
                  const isCredit = tx.toAccountId === account.id && tx.fromAccountId !== account.id;
                  return (
                    <div key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 20px', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                      <div>
                        <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)' }}>{tx.description}</p>
                        <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{new Date(tx.createdAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</p>
                      </div>
                      <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: isCredit ? 'var(--itunda-indigo)' : 'var(--itunda-grey-900)' }}>
                        {isCredit ? '+' : '-'}{tx.amount.toLocaleString()} RWF
                      </span>
                    </div>
                  );
                })}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
