import { useEffect, useState } from 'react';
import {  } from 'lucide-react';
import { IconChevronRight } from './icons/ItundaIcons';
import { fetchAccounts, type Account } from './lib/account';

// Real Toss Bank reference (20 screenshots, 2026-08-21): itunda's web "itunda
// Bank" access point (SavingsView) had every real product in its own catalog
// already (SACCO/Ikimina/savings/loans/investments) but no way to see the MAIN
// account's own balance at all. First built as a full inline balance+ledger
// section here; the user's own direct follow-up on the identical Android build
// ("those below they are not supposed to be in itunda account details screen
// ... they suppose to be in itunda bank home screen like toss does") corrected
// that -- real Toss keeps the ledger on its own separate screen, reached by a
// tap, not folded into the home screen's catalog. This shrank to just that
// tappable summary row; AccountDetailScreen.tsx (own file) is the real ledger
// drill-in it opens.
//
// Dual-balance UI (2026-08-29, closing [[project_itunda_bank_pay_separation]]'s
// last open item): the real itunda Pay/itunda Bank ledger split has been
// load-bearing on the backend since 2026-08-21, but no screen showed both
// balances without swiping a carousel. Adds a small flat secondary "itunda Pay"
// line below the Bank balance -- not a card (per this project's own flat-design
// convention), tappable straight to the Pay tab. Deliberately does NOT touch
// Home (its own redesign is separately, explicitly unscoped) -- this is additive
// to the already-stable Bank hub only.
export function AccountSummaryRow({ onOpen, onNavigateToPay }: { onOpen: (account: Account) => void; onNavigateToPay?: () => void }) {
  const [account, setAccount] = useState<Account | null | undefined>(undefined);
  const [payAccount, setPayAccount] = useState<Account | null>(null);

  useEffect(() => {
    fetchAccounts()
      .then((accounts) => {
        setAccount(accounts.find((a) => a.type === 'MAIN') ?? null);
        setPayAccount(accounts.find((a) => a.type === 'PAY') ?? null);
      })
      .catch(() => setAccount(null));
  }, []);

  if (!account) return null;

  return (
    <div>
      <button
        onClick={() => onOpen(account)}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '4px 0 4px', textAlign: 'left' }}
      >
        <div>
          <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
            itunda {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
          </p>
          <p style={{ margin: '4px 0 0', fontSize: '26px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
            {account.balance.toLocaleString()} {account.currency}
          </p>
        </div>
        <IconChevronRight size={20} color="var(--itunda-grey-400)" />
      </button>
      {payAccount && onNavigateToPay && (
        <button
          onClick={onNavigateToPay}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '4px 0 20px', textAlign: 'left', color: 'var(--itunda-grey-500)' }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>itunda Pay</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>
            {payAccount.balance.toLocaleString()} {payAccount.currency}
            <IconChevronRight size={14} color="var(--itunda-grey-400)" style={{ verticalAlign: 'middle', marginLeft: 4 }} />
          </span>
        </button>
      )}
    </div>
  );
}
