// Real "My assets" redesign (2026-08-27, direct user reference: 3 real Toss "총자산"
// screenshots, "this is how my asset screen should look like"). Toss's real pattern:
// a horizontal scrollable category tab bar, each tab showing either a real summary
// card or a masked teaser card with a dashed-border CTA -- a real up-sell-to-link
// pattern, not fabricated data. See OverviewService.kt's own doc comments for exactly
// how each of the 4 new categories (Cards/Vehicles/Tax/Points) is sourced.
//
// Deliberately reintroduces bordered cards for this screen only, a scoped exception
// to the 2026-08-24 flat-design sweep (docs/UI_UX_GUIDELINES.md §10) -- the user's own
// literal pixel reference is card-based, confirmed with them before starting.
//
// A standalone file from the start (not inline in BankDashboard.tsx), since
// BankDashboard.tsx already sits right at its file-size-lint baseline -- see
// CardExplainer.tsx's own doc comment for the same constraint.

import { useEffect, useState } from 'react';
import { ApiError, getStoredUser } from './lib/api';
import { fetchLinkedAccounts, fetchOverview, linkAccount, unlinkAccount, type AccountSummary, type LinkedAccount, type Overview } from './lib/overview';
import { useI18n } from './i18n/I18nContext';
import { useCountUp } from './hooks/useCountUp';

const LINK_PROVIDERS = ['MTN Mobile Money', 'Airtel Money', 'Bank of Kigali', 'Equity Bank Rwanda'];
const MOMO_PROVIDERS = ['MTN Mobile Money', 'Airtel Money'];

type AssetTab = 'ACCOUNTS' | 'CARDS' | 'LOANS' | 'INVESTMENT' | 'INSURANCE' | 'REAL_ESTATE' | 'CAR' | 'TAX' | 'POINTS';

// Only the destinations this screen's CTAs actually navigate to -- a subset of
// BankDashboard.tsx's own internal Tab union, which isn't exported (see this
// file's own header comment on why it can't just import that type directly).
export type OverviewDestinationTab = 'CARD' | 'LOANS' | 'STOCKS' | 'INSURANCE' | 'PROPERTY' | 'MY' | 'BILLS' | 'REWARDS';

const dashedCardStyle: React.CSSProperties = {
  border: '1px dashed var(--itunda-grey-300)',
  borderRadius: 'var(--itunda-radius-md)',
  padding: '20px',
  textAlign: 'center',
  display: 'flex',
  flexDirection: 'column',
  alignItems: 'center',
  gap: '10px',
};

function TeaserCard({ maskedValue, message, ctaLabel, onPress }: { maskedValue: string; message: string; ctaLabel: string; onPress: () => void }) {
  return (
    <div style={dashedCardStyle}>
      <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, color: 'var(--itunda-grey-300)' }}>{maskedValue}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{message}</p>
      <button className="itunda-btn itunda-btn-secondary" onClick={onPress}>{ctaLabel}</button>
    </div>
  );
}

export function OverviewAssetsView({ onNavigateToTab }: { onNavigateToTab: (tab: OverviewDestinationTab) => void }) {
  const { t } = useI18n();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showLinkForm, setShowLinkForm] = useState(false);
  const [provider, setProvider] = useState('');
  const [accountNumber, setAccountNumber] = useState('');
  const [activeTab, setActiveTab] = useState<AssetTab>('ACCOUNTS');
  const [showAllAccounts, setShowAllAccounts] = useState(false);
  const myPhoneNumber = getStoredUser()?.phoneNumber ?? '';

  const refresh = () => {
    setError(null);
    Promise.all([fetchOverview(), fetchLinkedAccounts()])
      .then(([o, linked]) => { setOverview(o); setLinkedAccounts(linked); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('overview.loadError')));
  };

  useEffect(refresh, []);

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      const linked = await linkAccount(provider, accountNumber);
      setProvider(''); setAccountNumber(''); setShowLinkForm(false);
      refresh();
      // Real gap found via Toss Simplicity21 research (2026-08-08): a declined provider
      // verification is still a 200 response (the account is saved as VERIFICATION_FAILED
      // so it shows up in history) -- without this check the form just closed as if the
      // link had worked, and the only trace was the status text buried in the list below.
      if (linked.status === 'VERIFICATION_FAILED') {
        setError(linked.failureReason ?? t('overview.verificationFailed', { provider }));
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('overview.linkError'));
    } finally {
      setBusy(false);
    }
  };

  const handleUnlink = async (id: string) => {
    setBusy(true);
    setError(null);
    try {
      await unlinkAccount(id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('overview.unlinkError'));
    } finally {
      setBusy(false);
    }
  };

  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before the
  // null check below (Rules of Hooks) with a ?? 0 fallback, same convention as this
  // file's other overview/detail balance hooks.
  const animatedNetWorth = useCountUp(overview?.netWorth ?? 0);

  if (!overview) {
    return <div className="itunda-flat-section skeleton" style={{ height: '260px' }} />;
  }

  const TABS: { key: AssetTab; label: string }[] = [
    { key: 'ACCOUNTS', label: t('overview.accounts') },
    { key: 'CARDS', label: t('overview.tabCards') },
    { key: 'LOANS', label: t('overview.tabLoans') },
    { key: 'INVESTMENT', label: t('overview.tabInvestment') },
    { key: 'INSURANCE', label: t('overview.tabInsurance') },
    { key: 'REAL_ESTATE', label: t('overview.tabRealEstate') },
    { key: 'CAR', label: t('overview.tabCar') },
    { key: 'TAX', label: t('overview.tabTax') },
    { key: 'POINTS', label: t('overview.tabPoints') },
  ];

  const visibleAccounts = showAllAccounts ? overview.accounts : overview.accounts.slice(0, 3);

  return (
    <div>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('overview.netWorth')}</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{animatedNetWorth.toLocaleString()} RWF</h2>
      </div>

      <div
        role="tablist"
        style={{ display: 'flex', gap: '20px', overflowX: 'auto', padding: '4px 0 12px', borderBottom: '1px solid var(--itunda-grey-100)' }}
      >
        {TABS.map((tab) => (
          <button
            key={tab.key}
            role="tab"
            aria-selected={activeTab === tab.key}
            onClick={() => setActiveTab(tab.key)}
            style={{
              flexShrink: 0,
              background: 'none',
              border: 'none',
              padding: '4px 0',
              fontSize: 'var(--itunda-type-scale-14-size)',
              fontWeight: activeTab === tab.key ? 700 : 400,
              color: activeTab === tab.key ? 'var(--itunda-grey-900)' : 'var(--itunda-grey-500)',
              borderBottom: activeTab === tab.key ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
              cursor: 'pointer',
            }}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="itunda-flat-section">
        {activeTab === 'ACCOUNTS' && (
          <div>
            {visibleAccounts.map((a) => (
              <OverviewAccountRow key={a.id} account={a} />
            ))}
            {overview.accounts.length > 3 && (
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px' }} onClick={() => setShowAllAccounts((v) => !v)}>
                {showAllAccounts ? t('overview.showFewerAccounts') : t('overview.viewAllAccounts', { count: overview.accounts.length })}
              </button>
            )}
          </div>
        )}

        {activeTab === 'CARDS' && (
          overview.cards.hasCard ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{t('overview.cardNumber', { last4: overview.cards.last4 ?? '' })}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>
                {overview.cards.frozen ? t('overview.cardFrozen') : t('overview.cardActive')}
              </p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('CARD')}>
                {t('overview.manage')}
              </button>
            </div>
          ) : (
            <TeaserCard maskedValue="••••" message={t('overview.teaserCards')} ctaLabel={t('overview.teaserCardsCta')} onPress={() => onNavigateToTab('CARD')} />
          )
        )}

        {activeTab === 'LOANS' && (
          overview.loans.activeCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.loans', { amount: overview.loans.totalOutstanding.toLocaleString(), count: overview.loans.activeCount })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('LOANS')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="₩???" message={t('overview.teaserLoans')} ctaLabel={t('overview.teaserLoansCta')} onPress={() => onNavigateToTab('LOANS')} />
          )
        )}

        {activeTab === 'INVESTMENT' && (
          overview.investments.holdingCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.investments', { amount: overview.investments.totalCostBasis.toLocaleString(), count: overview.investments.holdingCount })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('STOCKS')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="??%" message={t('overview.teaserInvestment')} ctaLabel={t('overview.teaserInvestmentCta')} onPress={() => onNavigateToTab('STOCKS')} />
          )
        )}

        {activeTab === 'INSURANCE' && (
          overview.insurance.activePolicyCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.insurance', { count: overview.insurance.activePolicyCount, amount: overview.insurance.totalMonthlyPremium.toLocaleString() })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('INSURANCE')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="???" message={t('overview.teaserInsurance')} ctaLabel={t('overview.teaserInsuranceCta')} onPress={() => onNavigateToTab('INSURANCE')} />
          )
        )}

        {activeTab === 'REAL_ESTATE' && (
          // Real estate has no home-valuation/ownership-tracking backend feature at
          // all (the realestate module is a marketplace listing flow, not a "track
          // your own home" asset feature) -- always a teaser, matches Toss's own
          // screenshot showing this tab in teaser state too.
          <TeaserCard maskedValue="₩???" message={t('overview.teaserRealEstate')} ctaLabel={t('overview.teaserRealEstateCta')} onPress={() => onNavigateToTab('PROPERTY')} />
        )}

        {activeTab === 'CAR' && (
          overview.vehicles.vehicleCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.carSummary', { count: overview.vehicles.vehicleCount, amount: overview.vehicles.totalPurchasePrice.toLocaleString() })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('MY')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="₩???" message={t('overview.teaserCar')} ctaLabel={t('overview.teaserCarCta')} onPress={() => onNavigateToTab('MY')} />
          )
        )}

        {activeTab === 'TAX' && (
          // Always a real card, even at zero payments -- matches Toss's own always-
          // populated Tax tab (no "link a tax account" step exists; paying a real RRA
          // bill through Bills IS the real activity this reflects).
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.taxSummary', { count: overview.tax.paymentCount, amount: overview.tax.totalPaid.toLocaleString() })}</p>
            <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('BILLS')}>{t('overview.manage')}</button>
          </div>
        )}

        {activeTab === 'POINTS' && (
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.pointsSummary', { amount: overview.points.rewardsTotal.toLocaleString() })}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>{t('overview.payMoneyBalance', { amount: overview.points.payMoneyBalance.toLocaleString() })}</p>
            <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('REWARDS')}>{t('overview.manage')}</button>
          </div>
        )}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>{t('overview.linkedAccounts')}</h3>
        {linkedAccounts.map((a) => (
          <div key={a.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{a.provider}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{a.externalAccountNumberMasked} · {a.status}</p>
            {a.demoBalance != null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('overview.demoBalance', { currency: a.demoBalanceCurrency ?? '', amount: a.demoBalance.toLocaleString() })}</p>
            )}
            {a.status === 'LINKED' && (
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '4px' }} disabled={busy} onClick={() => handleUnlink(a.id)}>{t('overview.unlink')}</button>
            )}
          </div>
        ))}
        {!showLinkForm ? (
          <button className="itunda-btn itunda-btn-primary" style={{ marginTop: '10px' }} onClick={() => setShowLinkForm(true)}>
            {t('overview.linkAccountPrompt')}
          </button>
        ) : (
          <form onSubmit={handleLink} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
              {LINK_PROVIDERS.map((p) => (
                <button
                  type="button" key={p} className="itunda-btn itunda-btn-secondary"
                  onClick={() => {
                    setProvider(p);
                    // Real friction fix (2026-08-10) -- pre-fill with the caller's own
                    // already-known phone number for a MoMo provider, still editable in
                    // case they want to link a different number. Left blank for a real
                    // bank, where the account number is genuinely a different, unknown value.
                    if (MOMO_PROVIDERS.includes(p) && !accountNumber) setAccountNumber(myPhoneNumber);
                  }}
                >{p}</button>
              ))}
            </div>
            <input
              type="text" value={provider} onChange={(e) => setProvider(e.target.value)} placeholder={t('overview.providerNamePlaceholder')} required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <input
              type="text" value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} placeholder={t('overview.accountPhonePlaceholder')} required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('overview.linking') : t('overview.linkAccount')}</button>
          </form>
        )}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Extracted so useCountUp -- see its own doc comment -- can be called once per real
// row rather than inside the parent's accounts.map() callback, which the Rules of
// Hooks forbid (same pattern as ForeignCurrencyAccountRow in BankDashboard.tsx).
function OverviewAccountRow({ account }: { account: AccountSummary }) {
  const animatedBalance = useCountUp(account.balance);
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
      <span>{account.name} ({account.type})</span>
      <span>{account.currency} {animatedBalance.toLocaleString()}</span>
    </div>
  );
}
