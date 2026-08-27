// Real QR/FacePay funding-source picker (itunda Pay redesign, 2026-08-28, direct
// user reference: real Toss Pay "Facepay · QR Payment" bottom sheet, Recent/
// Account/Card tabs). Confirmed absent on every platform before this pass (only an
// inline swipeable AccountCardCarousel existed) -- this is ADDITIVE, not a
// replacement: the carousel still works for a fast swipe-through, this sheet is a
// more complete "tap to open" entry point that also surfaces linked external
// accounts and itunda's own card.
//
// "Card" tab deliberately does not let you SELECT itunda's own card as a funding
// source -- CardService.chargeWithCard is a structurally separate ledger path from
// the QR/FacePay MerchantService.collect flow this code funds, so tapping it opens
// Card management instead of pretending to fund this code (see DebitCard.kt's own
// doc comment: itunda has no real external-card-linking concept either, confirmed
// by a full backend grep before building this).

import { useEffect, useState } from 'react';
import type { Account } from './lib/account';
import { fetchLinkedAccounts, type LinkedAccount } from './lib/overview';
import { fetchMyCard, type Card } from './lib/card';
import { AccountLinkForm } from './AccountLinkForm';
import { useI18n } from './i18n/I18nContext';

type PickerTab = 'RECENT' | 'ACCOUNT' | 'CARD';

function accountLabel(account: Account): string {
  if (account.type === 'PAY') return 'itunda Pay';
  if (account.type === 'MAIN') return 'itunda Bank';
  return `itunda Pay ${account.currency}`;
}

export function PayFundingSourcePicker({
  accounts, selectedAccountId, onSelectAccount, onClose, onOpenCard, myPhoneNumber,
}: {
  accounts: Account[];
  selectedAccountId: string | null;
  onSelectAccount: (id: string) => void;
  onClose: () => void;
  onOpenCard: () => void;
  myPhoneNumber: string;
}) {
  const { t } = useI18n();
  const [tab, setTab] = useState<PickerTab>('RECENT');
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [card, setCard] = useState<Card | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchLinkedAccounts().then(setLinkedAccounts).catch(() => {});
    fetchMyCard().then(setCard).catch(() => setCard(null));
  }, []);

  const selected = accounts.find((a) => a.id === selectedAccountId) ?? accounts[0] ?? null;
  const linkedActive = linkedAccounts.filter((a) => a.status === 'LINKED');

  const TABS: { key: PickerTab; label: string }[] = [
    { key: 'RECENT', label: t('pay.pickerRecent') },
    { key: 'ACCOUNT', label: t('pay.pickerAccount') },
    { key: 'CARD', label: t('pay.pickerCard') },
  ];

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 60 }} onClick={onClose}>
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '20px 20px 0 0', padding: '20px', width: '100%', maxHeight: '80vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '14px' }}>{t('pay.pickerTitle')}</h3>

        <div role="tablist" style={{ display: 'flex', gap: '20px', borderBottom: '1px solid var(--itunda-grey-100)', marginBottom: '16px' }}>
          {TABS.map((item) => (
            <button
              key={item.key}
              role="tab"
              aria-selected={tab === item.key}
              onClick={() => setTab(item.key)}
              style={{
                background: 'none', border: 'none', padding: '4px 0 10px',
                fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: tab === item.key ? 700 : 400,
                color: tab === item.key ? 'var(--itunda-grey-900)' : 'var(--itunda-grey-500)',
                borderBottom: tab === item.key ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
              }}
            >
              {item.label}
            </button>
          ))}
        </div>

        {tab === 'RECENT' && selected && (
          <button
            onClick={() => { onSelectAccount(selected.id); onClose(); }}
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 4px', textAlign: 'left' }}
          >
            <div>
              <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{accountLabel(selected)}</p>
              <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{selected.currency} {selected.balance.toLocaleString()}</p>
            </div>
            <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>✓</span>
          </button>
        )}

        {tab === 'ACCOUNT' && (
          <div>
            {accounts.map((account) => (
              <button
                key={account.id}
                onClick={() => { onSelectAccount(account.id); onClose(); }}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '10px 4px', textAlign: 'left', borderBottom: '1px solid var(--itunda-grey-100)' }}
              >
                <div>
                  <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{accountLabel(account)}</p>
                  <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{account.currency} {account.balance.toLocaleString()}</p>
                </div>
                {account.id === selectedAccountId && <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700 }}>✓</span>}
              </button>
            ))}
            {linkedActive.length > 0 && (
              <div style={{ marginTop: '12px' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 6px' }}>{t('pay.pickerLinkedAccounts')}</p>
                {linkedActive.map((a) => (
                  // Real, deliberately non-selectable -- an external linked account is
                  // a real, honest demo-balance display (LinkedAccount.demoBalance,
                  // never counted toward anything spendable), not a real funding
                  // source MerchantService.collect can actually debit.
                  <div key={a.id} style={{ padding: '10px 4px', borderBottom: '1px solid var(--itunda-grey-100)' }}>
                    <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{a.provider}</p>
                    <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{a.externalAccountNumberMasked}</p>
                  </div>
                ))}
              </div>
            )}
            <div style={{ marginTop: '14px' }}>
              <AccountLinkForm myPhoneNumber={myPhoneNumber} onLinked={(linked) => setLinkedAccounts((prev) => [linked, ...prev])} onError={setError} />
            </div>
          </div>
        )}

        {tab === 'CARD' && (
          card === undefined ? (
            <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : card ? (
            <button
              onClick={() => { onClose(); onOpenCard(); }}
              style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 4px', textAlign: 'left' }}
            >
              <div>
                <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{t('pay.pickerCardNumber', { last4: card.last4 })}</p>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{card.frozen ? t('overview.cardFrozen') : t('overview.cardActive')}</p>
              </div>
              <span style={{ color: 'var(--itunda-grey-400)' }}>›</span>
            </button>
          ) : (
            <div style={{ border: '1px dashed var(--itunda-grey-300)', borderRadius: 'var(--itunda-radius-md)', padding: '20px', textAlign: 'center' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>{t('overview.teaserCards')}</p>
              <button className="itunda-btn itunda-btn-secondary" onClick={() => { onClose(); onOpenCard(); }}>{t('overview.teaserCardsCta')}</button>
            </div>
          )
        )}

        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
      </div>
    </div>
  );
}
