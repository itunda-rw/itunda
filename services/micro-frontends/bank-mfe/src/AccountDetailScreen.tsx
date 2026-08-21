import { useEffect, useState } from 'react';
import {
  ChevronLeft, ArrowLeftRight, Car, Utensils, Gift, ShoppingBag, Percent,
  Landmark, Phone, Receipt, Smartphone, Wallet,
} from 'lucide-react';
import type { LucideIcon } from 'lucide-react';
import { EmptyState } from './EmptyState';
import { fetchAccountTransactions, type Account, type Transaction } from './lib/account';

// Real Toss Bank reference (20 screenshots, 2026-08-21, direct user instruction:
// "should look 100% like in this images pixels by pixels"): a full-screen drill-in
// showing this one account's own balance and transaction ledger -- structurally
// parallel to PayMoneyDetail.tsx, but real Toss keeps this SEPARATE from the "itunda
// Bank" home screen (SavingsView, which already has the real product catalog):
// balance/ledger live behind a tap, not folded into the catalog screen. First built
// folded directly into SavingsView; the user's own direct follow-up on the
// identical Android build ("those below they are not supposed to be in itunda
// account details screen ... they suppose to be in itunda bank home screen like
// toss does") corrected that -- this is the resulting separate screen, and
// SavingsView's own AccountLedgerHeader shrank to just a tappable summary row that
// opens it. Send is bottom-pinned (not scrolling with the ledger above it) per the
// same live follow-up ("those buttons at bottom"). Each row carries a real
// category icon (classified from the transaction's own description/type, not a
// fabricated per-merchant logo) matching Android's identical ledgerRowIcon fix.
export function AccountDetailScreen({ account, onBack, onSend }: { account: Account; onBack: () => void; onSend: (account: Account) => void }) {
  const [transactions, setTransactions] = useState<Transaction[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchAccountTransactions(account.id).then(setTransactions).catch(() => setError('Could not load your transaction history.'));
  }, [account.id]);

  const sorted = [...(transactions ?? [])].sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime());
  const groups = sorted.reduce<{ label: string; items: Transaction[] }[]>((acc, tx) => {
    const label = new Date(tx.createdAt).toLocaleDateString(undefined, { month: 'short', day: 'numeric' });
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
            <ChevronLeft size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>

        <div style={{ padding: '4px 20px 24px' }}>
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
            itunda {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
          </p>
          <p style={{ margin: '6px 0 0', fontSize: '32px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
            {account.currency} {account.balance.toLocaleString()}
          </p>
        </div>

        <div style={{ padding: '0 20px' }}>
          {error ? (
            <p role="alert" style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }}>{error}</p>
          ) : transactions === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
          ) : sorted.length === 0 ? (
            <div style={{ padding: '32px 0' }}>
              <EmptyState message="No transactions yet" />
            </div>
          ) : (
            <div>
              {groups.map((group) => (
                <div key={group.label}>
                  <p style={{ margin: 0, padding: '14px 0 6px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>{group.label}</p>
                  {group.items.map((tx) => {
                    const isCredit = tx.toAccountId === account.id && tx.fromAccountId !== account.id;
                    const RowIcon = ledgerRowIcon(tx);
                    return (
                      <div key={tx.id} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                        <div style={{ width: '38px', height: '38px', borderRadius: '999px', flexShrink: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', backgroundColor: RowIcon.color }}>
                          <RowIcon.Icon size={18} color="#fff" />
                        </div>
                        <div style={{ flex: 1, minWidth: 0 }}>
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
        </div>
      </div>

      <div style={{ padding: '12px 20px', paddingBottom: 'max(12px, env(safe-area-inset-bottom))', borderTop: '1px solid var(--itunda-grey-200)', backgroundColor: 'var(--itunda-white)' }}>
        <button onClick={() => onSend(account)} className="itunda-btn itunda-btn-primary" style={{ minHeight: '48px', borderRadius: '999px', width: '100%' }}>
          Send
        </button>
      </div>
    </div>
  );
}

// Real category classification (2026-08-21) -- see this file's own header comment.
// Description keyword first (real itunda-specific products the description already
// names), falling back to the real backend TransactionType enum (TRANSFER/PAYMENT/
// DEPOSIT/WITHDRAWAL/BILL/AIRTIME/LOAN/INTEREST, core/domain/Transaction.kt).
function ledgerRowIcon(tx: Transaction): { Icon: LucideIcon; color: string } {
  const d = tx.description.toLowerCase();
  if (d.includes('ride')) return { Icon: Car, color: 'var(--itunda-blue)' };
  if (d.includes('eats') || d.includes('booking')) return { Icon: Utensils, color: '#F2A93B' };
  if (d.includes('gift')) return { Icon: Gift, color: '#7C5CFC' };
  if (d.includes('escrow') || d.includes('marketplace')) return { Icon: ShoppingBag, color: '#14AE85' };
  if (d.includes('cashback')) return { Icon: Percent, color: '#F2A93B' };
  if (d.includes('interest')) return { Icon: Landmark, color: 'var(--itunda-blue)' };
  if (d.includes('ussd')) return { Icon: Phone, color: '#7C5CFC' };
  switch (tx.type) {
    case 'TRANSFER': return { Icon: ArrowLeftRight, color: 'var(--itunda-blue)' };
    case 'PAYMENT': return { Icon: Wallet, color: '#14AE85' };
    case 'BILL': return { Icon: Receipt, color: '#F2A93B' };
    case 'AIRTIME': return { Icon: Smartphone, color: '#7C5CFC' };
    case 'LOAN': return { Icon: Wallet, color: 'var(--itunda-blue)' };
    case 'INTEREST': return { Icon: Landmark, color: 'var(--itunda-blue)' };
    default: return { Icon: ArrowLeftRight, color: 'var(--itunda-grey-500)' };
  }
}
