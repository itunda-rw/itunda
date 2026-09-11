import { useEffect, useState } from 'react';
import { motion } from 'framer-motion';
import { CooperativeSavingsRail, YouthAccountCard, AutoTransfersCard, ProductPageHeader, TransferFlow, type Tab } from './BankDashboard';
import { IconChevronRight } from './icons/ItundaIcons';
import { type LoansMode } from './LoansView';
import { AccountDetailScreen } from './AccountDetailScreen';
import { AccountSummaryRow } from './AccountSummaryRow';
import { EmptyState } from './EmptyState';
import { useI18n } from './i18n/I18nContext';
import { type Account } from './lib/account';
import { ApiError } from './lib/api';
import { fetchDiscoverItems, type DiscoverItem } from './lib/discover';
import { fetchGoals, type SavingsGoal } from './lib/savings';
import { RoundUpCard, InterestJarCard, DepositProtectionCard } from './BankSavingsCards';
import { GoalCard, CreateGoalForm } from './BankSavingsGoals';
import { GroupAccountsSection } from './BankGroupAccounts';
import { IkiminaSection } from './BankIkimina';
import { SaccoSection, UpfrontDepositSection, CatalogSectionHeader } from './BankSaccoAndUpfrontDeposit';
import { WeeklySavingsSection } from './BankWeeklySavings';
import { Grow31SavingsSection } from './BankGrow31Savings';

// Real Toss Bank reference (2026-09-11, 7 real account-detail/전체 screenshots) --
// Section 65 (docs/DESIGN_REFERENCES.md) named a "추천" (Recommended) rail leading
// the product catalog as a gap in 2026-08-13 and it was never built on any platform.
// DiscoverService already emits real, per-user, priority-ranked items -- only these
// 4 are Bank-catalog-relevant (the rest -- government/rewards/lifestyle/social,
// plus the account-identity nudge p_kyc -- belong on Home, where they already
// render). Reuses BankDashboard's own DiscoverSection row markup, made tappable
// (unlike Home's purely-informational rail) since each of these already has a real
// destination on this same screen -- no invented navigation.
const RECOMMENDATION_IDS = new Set(['p_first_goal', 'p_try_sacco', 'p_try_ikimina', 'p_try_loan']);
const RECOMMENDATION_ANCHORS: Record<string, string> = {
  p_first_goal: 'savings-goals-section',
  p_try_sacco: 'savings-sacco-section',
  p_try_ikimina: 'savings-ikimina-section',
};

function RecommendationsSection({ onNavigateToLoansMode }: { onNavigateToLoansMode?: (mode: LoansMode) => void }) {
  const { t } = useI18n();
  const [items, setItems] = useState<DiscoverItem[]>([]);

  useEffect(() => {
    fetchDiscoverItems()
      .then((fetched) => setItems(fetched.filter((item) => RECOMMENDATION_IDS.has(item.id)).sort((a, b) => b.priority - a.priority)))
      .catch(() => {});
  }, []);

  if (items.length === 0) return null;

  const handleTap = (id: string) => {
    if (id === 'p_try_loan') { onNavigateToLoansMode?.('OFFERS'); return; }
    const anchorId = RECOMMENDATION_ANCHORS[id];
    if (anchorId) document.getElementById(anchorId)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
  };

  return (
    <div style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-19-size)', fontWeight: 700, marginBottom: '10px' }}>{t('bank.recommendations.title')}</h3>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {items.map((item, i) => (
          <motion.div
            key={item.id}
            whileTap={{ scale: 0.98 }}
            onClick={() => handleTap(item.id)}
            role="button"
            tabIndex={0}
            onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); handleTap(item.id); } }}
            style={{
              display: 'flex', alignItems: 'center', gap: '12px', padding: '12px 0', cursor: 'pointer',
              borderBottom: i < items.length - 1 ? '1px solid var(--itunda-grey-200)' : 'none',
            }}
          >
            <div style={{ width: '8px', height: '8px', borderRadius: '4px', backgroundColor: item.color, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 600 }}>{item.title}</span>
                {item.isNew && <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: item.color }}>{t('discover.new')}</span>}
              </div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-500)' }}>{item.subtitle}</p>
            </div>
            <IconChevronRight size={16} color="var(--itunda-grey-400)" />
          </motion.div>
        ))}
      </div>
    </div>
  );
}

export function SavingsView({ initialScrollTarget, onConsumedInitialScrollTarget, onNavigateToTab, onNavigateToLoansMode, onNavigateToSavingsTarget }: { initialScrollTarget?: 'sacco' | 'ikimina' | null; onConsumedInitialScrollTarget?: () => void; onNavigateToTab?: (tab: Tab) => void; onNavigateToLoansMode?: (mode: LoansMode) => void; onNavigateToSavingsTarget?: (target: 'sacco' | 'ikimina') => void } = {}) {
  const { t } = useI18n();
  const [goals, setGoals] = useState<SavingsGoal[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real Toss Bank reference (2026-08-21) -- see AccountSummaryRow's own doc
  // comment: the account ledger is its own drill-in screen, not folded into this
  // catalog screen.
  const [openAccountDetail, setOpenAccountDetail] = useState<Account | null>(null);
  const [showTransfer, setShowTransfer] = useState(false);
  const [transferAccountBalance, setTransferAccountBalance] = useState(0);
  // Real gap found live (2026-08-31): capturing which specific account was tapped
  // into (not just its balance) so TransferFlow can debit THAT account instead of
  // always defaulting to MAIN -- see P2pService.sendDirect's own new fromAccountId
  // parameter.
  const [transferFromAccountId, setTransferFromAccountId] = useState<string | undefined>();
  const [transferFromAccountName, setTransferFromAccountName] = useState<string | undefined>();

  const load = () => {
    setError(null);
    fetchGoals().then(setGoals).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  // Real gap found live (2026-08-10), same investigation that found LoansView's own
  // missing deep-link: the Home coop rail's "SACCO shares"/"Ikimina" links landed on
  // this whole SavingsView, but SACCO/Ikimina are static sections roughly 45-55% of
  // the way down a long single scrolling page (measured live: SACCO's heading sits at
  // 1465px on a 2658px-tall page) -- behind Safe Box, Round-up savings, goals, and
  // Group accounts. Not "wrong screen" like Loans was, but the same "not convenient"
  // gap: landing at the top and making the user scroll past everything else to reach
  // what they actually tapped for.
  useEffect(() => {
    if (!initialScrollTarget) return;
    const id = initialScrollTarget === 'sacco' ? 'savings-sacco-section' : 'savings-ikimina-section';
    const el = document.getElementById(id);
    el?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    onConsumedInitialScrollTarget?.();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialScrollTarget]);

  // Real itunda Bank product identity (2026-08-11) -- see Android's identical
  // BankHubScreen for the full research this came out of: KakaoPay/KakaoBank and
  // Toss's own Payments/Bank are genuinely distinct products, confirmed via
  // docs/TOSS_PARITY_MATRIX.md before this was added, not a generic/specific naming
  // pair. This tab already aggregated itunda's real savings/SACCO/Ikimina/weekly/
  // upfront-deposit products (everything below); it just had no product identity of
  // its own before now, and no way to reach the two real product families that live
  // on their own tabs (Loans, Invest/STOCKS) without leaving through Explore. Kept as
  // real navigation to those existing tabs, not a duplicate implementation.
  return (
    <div>
      <ProductPageHeader title="itunda Bank" subtitle="Savings, SACCO, Ikimina, loans & investments" />
      <AccountSummaryRow onOpen={setOpenAccountDetail} onNavigateToPay={onNavigateToTab ? () => onNavigateToTab('PAY') : undefined} />
      {openAccountDetail && (
        <AccountDetailScreen
          account={openAccountDetail}
          onBack={() => setOpenAccountDetail(null)}
          onSend={(account) => { setOpenAccountDetail(null); setTransferAccountBalance(account.balance); setTransferFromAccountId(account.id); setTransferFromAccountName(account.accountName); setShowTransfer(true); }}
          onNavigateToTab={onNavigateToTab ? (tab) => { setOpenAccountDetail(null); onNavigateToTab(tab); } : undefined}
        />
      )}
      {showTransfer && (
        <TransferFlow
          accountBalance={transferAccountBalance}
          fromAccountId={transferFromAccountId}
          fromAccountName={transferFromAccountName}
          onClose={() => setShowTransfer(false)}
          onSuccess={() => setShowTransfer(false)}
        />
      )}
      {/* Real architectural fix (2026-08-13, matching the identical Android/iOS fix
          same session): this rail (SACCO/Ikimina/Moto-Taxi Ownership/Harvest advance)
          used to render on Home -- real Bank-product content on what's meant to be a
          generic access point. Moved here, its actual home, since this is itunda's
          real Bank product; nothing about the rail itself changed (same
          onNavigateToLoansMode/onNavigateToSavingsTarget wiring the parent already
          threads through, see this view's own initialScrollTarget doc comment for why
          the Sacco/Ikimina taps still work correctly even without a tab switch). */}
      {onNavigateToTab && onNavigateToLoansMode && onNavigateToSavingsTarget && (
        <CooperativeSavingsRail onNavigateToTab={onNavigateToTab} onNavigateToLoansMode={onNavigateToLoansMode} onNavigateToSavingsTarget={onNavigateToSavingsTarget} />
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '16px' }} role="alert">{error}</p>}

      <RecommendationsSection onNavigateToLoansMode={onNavigateToLoansMode} />

      <CatalogSectionHeader title="Savings" />
      <InterestJarCard />
      <RoundUpCard goals={goals ?? []} />
      <div id="savings-goals-section">
        <CreateGoalForm onCreated={load} />
        {goals === null ? (
          <div className="itunda-flat-section skeleton" style={{ height: '100px' }} />
        ) : goals.length === 0 ? (
          <EmptyState message="No savings goals yet — set one to start putting money aside for something specific." />
        ) : (
          goals.map((g) => <GoalCard key={g.id} goal={g} onChanged={load} />)
        )}
      </div>
      <div style={{ marginTop: '24px' }}>
        <WeeklySavingsSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <Grow31SavingsSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <UpfrontDepositSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <YouthAccountCard />
      </div>

      {/* itunda's own real category, standing in for Toss's category shape here --
          SACCO/Ikimina/group accounts are genuine, distinct, Rwanda-specific
          cooperative-savings products, not a Toss import. */}
      <CatalogSectionHeader title="Cooperative & Group" />
      <div id="savings-ikimina-section">
        <IkiminaSection />
      </div>
      <div id="savings-sacco-section" style={{ marginTop: '24px' }}>
        <SaccoSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <GroupAccountsSection />
      </div>

      {/* Real architectural fix (2026-08-13) -- see this view's own coop-rail doc
          comment above: AutoTransfersCard (recurring 자동이체) used to render on Home
          too, same "real Bank-product content on a generic access point" violation.
          Homed here now, matching Android's identical "Auto Transfer -> BankHubScreen"
          move -- and matching the real Toss reference's own "Service" category, which
          also houses Auto Transfer. */}
      <CatalogSectionHeader title="Service" />
      <AutoTransfersCard />

      {onNavigateToTab && (
        <>
          <CatalogSectionHeader title="More from itunda Bank" />
          <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <button
              onClick={() => onNavigateToTab('LOANS')}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 8px', borderRadius: '10px', textAlign: 'left', width: '100%' }}
            >
              <div>
                <div style={{ fontSize: '14.5px', fontWeight: 650, color: 'var(--itunda-grey-900)' }}>Borrow</div>
                <div style={{ fontSize: '12.5px', color: 'var(--itunda-grey-500)' }}>Personal loans, VUP, student loans, Moto-Taxi Ownership</div>
              </div>
              <IconChevronRight size={18} color="var(--itunda-grey-400)" />
            </button>
            <button
              onClick={() => onNavigateToTab('STOCKS')}
              style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 8px', borderRadius: '10px', textAlign: 'left', width: '100%' }}
            >
              <div>
                <div style={{ fontSize: '14.5px', fontWeight: 650, color: 'var(--itunda-grey-900)' }}>Grow your money</div>
                <div style={{ fontSize: '12.5px', color: 'var(--itunda-grey-500)' }}>RSE stocks, bonds & fixed income, IPOs</div>
              </div>
              <IconChevronRight size={18} color="var(--itunda-grey-400)" />
            </button>
          </div>
        </>
      )}
      {/* Real licensed-bank disclosure (2026-08-11) -- see docs/TOSS_PARITY_MATRIX.md's
          own confirmation of "zero real banking-license implementation anywhere" and
          TOSS_FEATURE_SPECIFICATION.md's Pillar 3 listing "itunda Bank... RBDB
          licensed in Rwanda" as roadmap-only, never built. Rather than just disclosing
          an absence, DepositProtectionCard above shows the real, working reserve
          itunda maintains as its own internal simulation of real deposit protection --
          same "real mechanics, honestly labeled as itunda's own scheme" discipline
          this codebase already applies to VUP/RSE/SACCO. Same fix on Android's
          BankHubScreen and iOS's BankView the same day. */}
      <DepositProtectionCard />
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '12px', padding: '0 4px' }}>
        itunda is not a licensed bank, and this is not real government deposit insurance.
        &quot;itunda Bank&quot; is itunda&apos;s own product name for these savings,
        SACCO/Ikimina, loan, and investment features — not a separate licensed banking entity.
      </p>
    </div>
  );
}
