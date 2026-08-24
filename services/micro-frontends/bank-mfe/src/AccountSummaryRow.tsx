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
export function AccountSummaryRow({ onOpen }: { onOpen: (account: Account) => void }) {
  const [account, setAccount] = useState<Account | null | undefined>(undefined);

  useEffect(() => {
    fetchAccounts()
      .then((accounts) => setAccount(accounts.find((a) => a.type === 'MAIN') ?? null))
      .catch(() => setAccount(null));
  }, []);

  if (!account) return null;

  return (
    <button
      onClick={() => onOpen(account)}
      style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '4px 0 20px', textAlign: 'left' }}
    >
      <div>
        <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          itunda {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
        </p>
        <p style={{ margin: '4px 0 0', fontSize: '26px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
          {account.currency} {account.balance.toLocaleString()}
        </p>
      </div>
      <IconChevronRight size={20} color="var(--itunda-grey-400)" />
    </button>
  );
}
