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
import { fetchLinkedAccounts, fetchOverview, unlinkAccount, type AccountSummary, type LinkedAccount, type Overview } from './lib/overview';
import { AccountLinkForm } from './AccountLinkForm';
import { TransferFlow } from './BankDashboard';
import { TotalAssetsDetailScreen } from './TotalAssetsDetailScreen';
import { IconChevronRight } from './icons/ItundaIcons';
import { useI18n } from './i18n/I18nContext';
import { useCountUp } from './hooks/useCountUp';

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

// Real itunda-owned, freely-spendable wallet types -- distinct from locked-purpose
// product ledgers (SAVINGS/INVESTMENT/LOAN/GROUP/WEEKLY_SAVINGS/UPFRONT_DEPOSIT/
// GROW31_SAVINGS, each with its own dedicated withdraw/close flow, same as the real
// savings-goal withdraw shipped the same day) and from PAY (kept asymmetric from Bank
// on purpose, see [[project_itunda_bank_pay_separation]]) -- only these can realistically
// fund an arbitrary P2P send the way a real Toss checking/foreign-currency/business
// account can.
const SENDABLE_ACCOUNT_TYPES = new Set(['MAIN', 'FOREIGN_CURRENCY', 'BUSINESS', 'MINI']);

export function OverviewAssetsView({ onNavigateToTab }: { onNavigateToTab: (tab: OverviewDestinationTab) => void }) {
  const { t } = useI18n();
  const [overview, setOverview] = useState<Overview | null>(null);
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [activeTab, setActiveTab] = useState<AssetTab>('ACCOUNTS');
  const [showAllAccounts, setShowAllAccounts] = useState(false);
  const myPhoneNumber = getStoredUser()?.phoneNumber ?? '';
  // Real gap found live (2026-08-31, direct user reference of their own Toss app's
  // "My accounts" screen: every account row -- checking, savings pockets, even a
  // linked external bank account -- carries a "Send" action). This screen's own
  // account rows previously had no action at all. Linked external accounts
  // deliberately do NOT get one here: itunda only ever shows a real, honest
  // simulated demoBalance for those, it has no real access to move money out of an
  // account it doesn't control -- a fake "Send" there would be exactly the kind of
  // dishonest UX this codebase's own AI_AGENT_SELF_CHECK.md warns against.
  const [transferAccount, setTransferAccount] = useState<AccountSummary | null>(null);
  // Real Toss "총자산" detail screen (2026-09-12) -- tapping the net worth header
  // opens it. See TotalAssetsDetailScreen.tsx's own doc comment.
  const [showTotalAssetsDetail, setShowTotalAssetsDetail] = useState(false);

  const refresh = () => {
    setError(null);
    Promise.all([fetchOverview(), fetchLinkedAccounts()])
      .then(([o, linked]) => { setOverview(o); setLinkedAccounts(linked); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(refresh, []);

  const handleUnlink = async (id: string) => {
    setBusy(true);
    setError(null);
    try {
      await unlinkAccount(id);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
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

  if (showTotalAssetsDetail) {
    return <TotalAssetsDetailScreen overview={overview} onBack={() => setShowTotalAssetsDetail(false)} />;
  }

  return (
    <div>
      <button
        onClick={() => setShowTotalAssetsDetail(true)}
        className="itunda-flat-section"
        style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', width: '100%', textAlign: 'left' }}
      >
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('overview.netWorth')}</p>
        <span style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{animatedNetWorth.toLocaleString('en-US')} RWF</h2>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </span>
      </button>

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
              <OverviewAccountRow key={a.id} account={a} onSend={SENDABLE_ACCOUNT_TYPES.has(a.type) ? () => setTransferAccount(a) : undefined} />
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
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.loans', { amount: overview.loans.totalOutstanding.toLocaleString('en-US'), count: overview.loans.activeCount })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('LOANS')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="₩???" message={t('overview.teaserLoans')} ctaLabel={t('overview.teaserLoansCta')} onPress={() => onNavigateToTab('LOANS')} />
          )
        )}

        {activeTab === 'INVESTMENT' && (
          overview.investments.holdingCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.investments', { amount: overview.investments.totalCostBasis.toLocaleString('en-US'), count: overview.investments.holdingCount })}</p>
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('STOCKS')}>{t('overview.manage')}</button>
            </div>
          ) : (
            <TeaserCard maskedValue="??%" message={t('overview.teaserInvestment')} ctaLabel={t('overview.teaserInvestmentCta')} onPress={() => onNavigateToTab('STOCKS')} />
          )
        )}

        {activeTab === 'INSURANCE' && (
          overview.insurance.activePolicyCount > 0 ? (
            <div className="itunda-card">
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.insurance', { count: overview.insurance.activePolicyCount, amount: overview.insurance.totalMonthlyPremium.toLocaleString('en-US') })}</p>
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
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.carSummary', { count: overview.vehicles.vehicleCount, amount: overview.vehicles.totalPurchasePrice.toLocaleString('en-US') })}</p>
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
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.taxSummary', { count: overview.tax.paymentCount, amount: overview.tax.totalPaid.toLocaleString('en-US') })}</p>
            <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '12px' }} onClick={() => onNavigateToTab('BILLS')}>{t('overview.manage')}</button>
          </div>
        )}

        {activeTab === 'POINTS' && (
          <div className="itunda-card">
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t('overview.pointsSummary', { amount: overview.points.rewardsTotal.toLocaleString('en-US') })}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '4px' }}>{t('overview.payMoneyBalance', { amount: overview.points.payMoneyBalance.toLocaleString('en-US') })}</p>
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
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('overview.demoBalance', { currency: a.demoBalanceCurrency ?? '', amount: a.demoBalance.toLocaleString('en-US') })}</p>
            )}
            {a.status === 'LINKED' && (
              <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '4px' }} disabled={busy} onClick={() => handleUnlink(a.id)}>{t('overview.unlink')}</button>
            )}
          </div>
        ))}
        <div style={{ marginTop: '10px' }}>
          <AccountLinkForm myPhoneNumber={myPhoneNumber} onLinked={() => refresh()} onError={setError} />
        </div>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {transferAccount && (
        <TransferFlow
          accountBalance={transferAccount.balance}
          fromAccountId={transferAccount.id}
          fromAccountName={transferAccount.name}
          onClose={() => setTransferAccount(null)}
          onSuccess={() => { setTransferAccount(null); refresh(); }}
          onBalanceRefresh={refresh}
        />
      )}
    </div>
  );
}

// Extracted so useCountUp -- see its own doc comment -- can be called once per real
// row rather than inside the parent's accounts.map() callback, which the Rules of
// Hooks forbid (same pattern as ForeignCurrencyAccountRow in BankDashboard.tsx).
function OverviewAccountRow({ account, onSend }: { account: AccountSummary; onSend?: () => void }) {
  const { t } = useI18n();
  const animatedBalance = useCountUp(account.balance);
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
      <span>{account.name} ({account.type})</span>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <span>{account.currency} {animatedBalance.toLocaleString('en-US')}</span>
        {onSend && (
          <button
            type="button"
            onClick={onSend}
            className="itunda-btn itunda-btn-secondary"
            style={{ padding: '4px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {t('overview.send')}
          </button>
        )}
      </div>
    </div>
  );
}
