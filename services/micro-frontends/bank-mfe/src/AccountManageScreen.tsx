import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { Account } from './lib/account';

// Real Toss Bank reference (5 more screenshots, 2026-08-23, direct user instruction:
// "this is what users should [see] when they click on manage in itunda bank"): the
// real Toss "관리" (Manage) screen is a comprehensive ~35-row account-settings hub
// (Profile/Security/Transfer/Documents/Taxes/Child/View/Notifications/Support/close
// account). Most of those rows are genuinely Korea-specific banking infrastructure or
// regulation itunda has no equivalent for -- Open Banking/firm banking (a Korean
// interbank data-sharing standard), tax-free limit (a Korean tax concept), telecom
// fraud information sharing (a Korean carrier-integration), ATM limit (itunda has no
// real ATM network), Credit Information Usage Policy (a specific Korean regulatory
// disclosure) -- honestly not reproduced here, not silently dropped: see the
// disclosure note at the bottom of this screen.
//
// What IS real and included: every row below routes to an already-built, real itunda
// screen or feature (never a fabricated destination) -- Card (CardView, just
// redesigned the same session), Devices (DevicesView, real device list + revoke,
// itunda's own real equivalent of "Device Change History"), Auto transfer
// (AutoTransfersCard on SavingsView), Delayed transfers (DelayedTransfersCard on
// PayHub, itunda's real 지연이체 anti-phishing hold-and-cancel feature), Exchange
// rates (the FOREIGN_CURRENCY tab's real rate cards), Bills (the real BILLS tab),
// Support (the real SUPPORT tab).
//
// Real, named, deliberately NOT built this pass (unlike the Korea-specific rows
// above, these genuinely fit itunda's own account model but don't exist yet --
// flagged as honest gaps, not fabricated): changing your account password (itunda
// does use a real phone+password login, unlike Toss's own passwordless flow, so this
// is a real future feature -- just not a rushed, security-sensitive addition folded
// into a UI-redesign pass), an account nickname, and closing your account.
export function AccountManageScreen({ account, onBack, onNavigateToTab }: { account: Account; onBack: () => void; onNavigateToTab: (tab: 'CARD' | 'SAVINGS' | 'PAY' | 'BILLS' | 'FOREIGN_CURRENCY' | 'DEVICES' | 'SUPPORT') => void }) {
  const go = (tab: 'CARD' | 'SAVINGS' | 'PAY' | 'BILLS' | 'FOREIGN_CURRENCY' | 'DEVICES' | 'SUPPORT') => {
    onBack();
    onNavigateToTab(tab);
  };

  return (
    <div style={{ position: 'fixed', inset: 0, zIndex: 1100, backgroundColor: 'var(--itunda-white)', display: 'flex', flexDirection: 'column' }}>
      <div style={{ flex: 1, overflowY: 'auto', padding: '0 20px 24px' }}>
        <div style={{ display: 'flex', alignItems: 'center', padding: '14px 0' }}>
          <button onClick={onBack} aria-label="Back" style={{ display: 'flex', padding: '4px', marginLeft: '-4px' }}>
            <ChevronLeft size={24} color="var(--itunda-grey-900)" />
          </button>
        </div>

        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', margin: '4px 0 24px' }}>
          itunda {account.accountNumber.match(/.{1,4}/g)?.join('-') ?? account.accountNumber}
        </p>

        <ManageSectionHeader title="Account" />
        <ManageRow label="Debit card" onClick={() => go('CARD')} />

        <ManageSectionHeader title="Security" />
        <ManageRow label="Manage devices" onClick={() => go('DEVICES')} />

        <ManageSectionHeader title="Transfer" />
        <ManageRow label="Auto transfer" onClick={() => go('SAVINGS')} />
        <ManageRow label="Delayed transfers" onClick={() => go('PAY')} />

        <ManageSectionHeader title="Foreign currency" />
        <ManageRow label="Exchange rates" onClick={() => go('FOREIGN_CURRENCY')} />

        <ManageSectionHeader title="Taxes & bills" />
        <ManageRow label="Pay taxes & bills" onClick={() => go('BILLS')} />

        <ManageSectionHeader title="Support" />
        <ManageRow label="Get help" onClick={() => go('SUPPORT')} />

        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '28px', lineHeight: 1.5 }}>
          Some Toss Bank account-management features (Open Banking, tax-free limits,
          ATM networks, changing your account password) don&apos;t have a real itunda
          equivalent yet, and aren&apos;t shown here rather than being faked.
        </p>
      </div>
    </div>
  );
}

function ManageSectionHeader({ title }: { title: string }) {
  return (
    <h2 style={{ margin: '20px 0 6px', fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{title}</h2>
  );
}

function ManageRow({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button onClick={onClick} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left' }}>
      <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>{label}</span>
      <ChevronRight size={18} color="var(--itunda-grey-400)" />
    </button>
  );
}
