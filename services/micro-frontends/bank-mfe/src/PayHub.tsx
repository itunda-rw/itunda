import { useEffect, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { QrCode, Receipt, Settings, Store, Wallet as AccountIcon } from 'lucide-react';
import { IconChevronRight, IconSend } from './icons/ItundaIcons';
import { TransferFlow, type Tab } from './BankDashboard';
import { PayMoneyDetail } from './PayMoneyDetail';
import { CouponBoxView } from './CouponBoxView';
import { MembershipView } from './MembershipView';
import { AgentCashOutView } from './AgentCashOutView';
import {
  averageCashbackRatePercent, FacePayStatusRow, GetHelpLinks, NearbyMerchantsDialog, NearbyMerchantsMap,
  PayHubOtherServicesRail, RewardsPreviewSection, RewardsSummaryRow,
} from './PayHomeExtras';
import { ScheduledTransfersCard, DelayedTransfersCard } from './PayTransferCards';
import { RequestMoneyCard, AutoTopUpCard } from './PayRequestAndTopUp';
import { MyPaymentCodeCard } from './PayCodeCard';
import { PayByCodeCard, PayByStaticQrCard, PaymentConfirmation } from './PayQrCollection';
import { useCountUp } from './hooks/useCountUp';
import { useI18n } from './i18n/I18nContext';
import { showToast } from './Toast';
import { type Account, type Transaction } from './lib/account';
import { fetchAccounts, fetchTransactions, fetchTransactionTimeline } from './lib/account';
import { type Card } from './lib/card';
import { fetchMyCard } from './lib/card';
import { enrollFacePay, fetchFacePayStatus, revokeFacePay } from './lib/facepay';
import { fetchRewardTasks, type RewardTasksResult } from './lib/rewards';
import { fetchNearbyMerchants, type NearbyMerchant, type CollectPaymentResult } from './lib/shopping';

// Real dead-tap fix (item 244): both cards used to render with whileTap's tap-down
// animation and cursor: 'pointer' unconditionally -- a UI signal both are tappable
// -- with zero onClick wired to either. "Cards" has a real destination (CardView,
// already reachable from the tab bar, just not from here) and is now wired to it.
// "Scan to Pay" was removed outright rather than re-wired (product-feel audit,
// §234): item 244's own reasoning ("no camera QR scanner anywhere") went stale the
// moment `QrScanCamera` shipped (2026-08-19) into `PayByCodeCard`, which now renders
// directly below this row and opens straight into a live camera by default -- a
// second tile pointing at the same capability already visible on the same screen is
// exactly what Toss's own real "Minimum Feature" principle ("기능이 추가될수록 제품은
// 어려워진다") asks to cut, not re-wire. See [[project_itunda_product_feel]] roadmap
// item 7 for the sourced restraint audit this was found under.
function QuickActions({ onCardsClick }: { onCardsClick: () => void }) {
  const { t } = useI18n();

  return (
    <motion.div
      whileTap={{ scale: 0.98 }}
      className="itunda-flat-section"
      onClick={onCardsClick}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onCardsClick(); } }}
      style={{ display: 'flex', alignItems: 'center', gap: '12px', cursor: 'pointer' }}
    >
      <div style={{ width: '38px', height: '38px', borderRadius: '999px', backgroundColor: 'rgba(138, 43, 226, 0.12)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <AccountIcon size={18} color="#8A2BE2" />
      </div>
      <span style={{ fontWeight: '600', fontSize: 'var(--itunda-type-scale-15-size)', color: 'var(--itunda-grey-900)' }}>{t('quickActions.cards')}</span>
    </motion.div>
  );
}

function TransactionHistory({ transactions, unusuallyLargeIds }: { transactions: Transaction[]; unusuallyLargeIds?: Set<string> }) {
  const { t } = useI18n();
  // Real Toss Pay home reference (2026-08-22): "Payment history", flat, not the
  // generic itunda-card TransactionHistory previously used.
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
      className="itunda-flat-section"
    >
      <h3 style={{ color: 'var(--itunda-grey-500)', margin: '0 0 12px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: '600' }}>Payment history</h3>

      {transactions.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-500)' }}>{t('home.noTransactions')}</p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <AnimatePresence>
            {transactions.slice(0, 10).map((tx, idx) => {
              const isCredit = tx.channel === 'CASHBACK' || tx.type === 'DEPOSIT';
              const isUnusual = unusuallyLargeIds?.has(tx.id) ?? false;
              return (
                <motion.div
                  key={tx.id}
                  initial={{ opacity: 0, x: -10 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: idx * 0.05 }}
                  style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '4px' }}
                >
                  <div style={{ display: 'flex', alignItems: 'center', gap: '16px' }}>
                    <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>
                      {tx.channel ? tx.channel.slice(0, 2) : tx.type.slice(0, 2)}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ color: 'var(--itunda-grey-900)', fontWeight: '600', fontSize: 'var(--itunda-type-scale-16-size)' }}>{tx.description}</span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: '500' }}>{new Date(tx.createdAt).toLocaleString()}</span>
                        {isUnusual && (
                          <span style={{ color: 'var(--itunda-red)', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: '700', backgroundColor: '#FEECEE', padding: '2px 6px', borderRadius: '6px' }}>
                            {t('home.unusuallyLarge')}
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <span style={{ fontWeight: '700', fontSize: 'var(--itunda-type-scale-16-size)', color: isUnusual ? 'var(--itunda-red)' : isCredit ? 'var(--itunda-indigo)' : 'var(--itunda-grey-900)' }}>
                    {isCredit ? '+' : ''}{tx.amount.toLocaleString('en-US')} RWF
                  </span>
                </motion.div>
              );
            })}
          </AnimatePresence>
        </div>
      )}
    </motion.div>
  );
}

// Real product-positioning fix (2026-08-10, see the "itunda: the wedge, not the
// mirror" strategy memo from this same session): SACCO shares, Ikimina, Moto-Taxi
// Ownership, and Harvest advance are itunda's only real Rwanda-specific products --
// the ones MTN MoMo's own roadmap can't trivially replicate (SACCO shares/Ikimina were
// rendered as plain scrollable sections inside Savings, past 5 other sections;
// Harvest advance/Moto-Taxi Ownership were one of 8 identical buttons inside Loans).
// Zero discoverability from Home, same visual weight as "Foreign currency" and
// "Digital certificate" everywhere they did appear. This doesn't add a feature -- it
// gives the four real, working, already-shipped features that are actually
// differentiated a home-screen presence that matches what they're worth, with real
// explanatory copy instead of a bare label. Routes to the real tab each already lives
// in (SAVINGS for the two cooperative-savings products, LOANS for the two credit
// products) -- not a deep link to the exact scroll position, but real, honest, and a
// large improvement over not being reachable from Home at all.
// The pay surface brings the real, existing money-moving flows into one predictable
// place. It does not create another payment implementation: every action below uses
// the established transfer, bill, merchant-code, and payment-intent components.
export function PayHub({ onNavigateToTab, onNavigateToCard }: { onNavigateToTab: (tab: Tab) => void; onNavigateToCard: () => void }) {
  const { t } = useI18n();
  const [account, setAccount] = useState<Account | null>(null);
  // Real swipeable funding-source cards (Pay-parity port, §240) -- see
  // AccountCardCarousel's own doc comment. Only the real payment-eligible accounts
  // (PAY + MAIN + any opened foreign-currency ones), not the customer's full account
  // list -- a real gap found+fixed 2026-08-21 (direct user confirmation): this used
  // to include every account type (SAVINGS/INVESTMENT/LOAN/GROUP among them), none
  // of which are real payment products, matching the backend's own new
  // PAYMENT_ELIGIBLE_ACCOUNT_TYPES allowlist (MerchantService.kt). Kept alongside
  // `account` rather than replacing it, since every other card on this screen
  // (Send/Bills/Transfer) is deliberately still MAIN-only.
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [unusuallyLargeIds, setUnusuallyLargeIds] = useState<Set<string>>(new Set());
  const [showTransfer, setShowTransfer] = useState(false);
  const [paymentResult, setPaymentResult] = useState<CollectPaymentResult | null>(null);
  const [facePayEnrolled, setFacePayEnrolled] = useState(false);
  const [facePayBusy, setFacePayBusy] = useState(false);
  // Real Toss Pay home reference (2026-08-22) -- "Get more rewards" preview; see
  // PayHomeExtras.tsx's own top doc comment for the full honest-scoping rationale.
  const [rewardsPreview, setRewardsPreview] = useState<RewardTasksResult | null>(null);
  // Real embedded nearby-merchants map -- see PayHomeExtras.tsx's NearbyMerchantsMap.
  const [nearbyMerchants, setNearbyMerchants] = useState<NearbyMerchant[]>([]);
  const [userLocation, setUserLocation] = useState<{ latitude: number; longitude: number } | null>(null);
  const [showNearbyMerchantsDialog, setShowNearbyMerchantsDialog] = useState(false);
  // Real "Toss Pay Money" detail/statement screen -- see PayMoneyDetail's own doc
  // comment. Holds the specific account drilled into, not just a boolean, since
  // MyPaymentCodeCard's own real funding-source picker can select MAIN too.
  const [openAccountDetail, setOpenAccountDetail] = useState<Account | null>(null);
  // Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
  // screenshots) -- new Coupon Box / Membership screens, both presented the same
  // full-screen-overlay way PayMoneyDetail already is above.
  const [showCouponBox, setShowCouponBox] = useState(false);
  const [showMembership, setShowMembership] = useState(false);
  const [showAgentCash, setShowAgentCash] = useState(false);
  // Real itunda-issued card summary row -- see DebitCard.kt's own doc comment for
  // why this is itunda's own real, ledger-backed card simulation, not a real
  // Visa/Mastercard rail. null = genuinely not issued yet (real teaser state),
  // undefined = still loading.
  const [card, setCard] = useState<Card | null | undefined>(undefined);

  // Real architectural fix (2026-08-13) -- see HomeView's own doc comment for why
  // the account balance, quick actions, and transaction history moved here from
  // Home: this is itunda's real, complete Pay product now, matching Android's
  // identical AccountHeroCard -> PayTab move the same session.
  const loadAccount = () => {
    Promise.all([fetchAccounts(), fetchTransactions()])
      .then(([fetchedAccounts, txs]) => {
        // Real Toss Bank/Toss Pay separation (2026-08-21): this is the Pay tab, so its
        // headline balance is itunda Pay money, not the Bank account -- MAIN kept only
        // as a defensive fallback for an account predating the real PayAccountBackfillRunner.
        setAccount(fetchedAccounts.find((item) => item.type === 'PAY') ?? fetchedAccounts.find((item) => item.type === 'MAIN') ?? fetchedAccounts[0] ?? null);
        setAccounts(fetchedAccounts.filter((item) => item.type === 'PAY' || item.type === 'MAIN' || item.type === 'FOREIGN_CURRENCY'));
        setTransactions(txs);
      })
      .catch(() => setAccount(null));
    // Real Toss Timeline-style unusual-spend flag -- fetched independently of the main
    // account/transactions load so a failure here never blocks the core balance view.
    fetchTransactionTimeline()
      .then((entries) => setUnusuallyLargeIds(new Set(entries.filter((e) => e.unusuallyLarge).map((e) => e.transaction.id))))
      .catch(() => {});
  };

  useEffect(() => {
    loadAccount();
    fetchFacePayStatus().then((result) => setFacePayEnrolled(result.enrolled)).catch(() => setFacePayEnrolled(false));
    fetchRewardTasks().then(setRewardsPreview).catch(() => setRewardsPreview(null));
    fetchMyCard().then(setCard).catch(() => setCard(null));
  }, []);

  // Silent when location is denied -- same pattern as fetchNearbyAds above.
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setUserLocation({ latitude: position.coords.latitude, longitude: position.coords.longitude });
        fetchNearbyMerchants(position.coords.latitude, position.coords.longitude).then(setNearbyMerchants).catch(() => {});
      },
      () => {},
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }, []);

  const cashbackRatePercent = averageCashbackRatePercent(nearbyMerchants);
  // Real Toss motion pattern (toss.im/tossfeed/article/why-motion-in-finance) -- the
  // real "Pay money" summary row below (2026-08-26 redesign, direct user reference to
  // itunda Bank hub's own AccountSummaryRow.tsx pattern) needs its own animated
  // balance, promoted out of MyPaymentCodeCard's embedded row into a real top-level
  // row matching AccountSummaryRow's exact shape.
  const animatedPayBalance = useCountUp(account?.balance ?? 0);
  const mainAccount = accounts.find((a) => a.type === 'MAIN');

  const handleFacePayToggle = async () => {
    const wasEnrolled = facePayEnrolled;
    setFacePayBusy(true);
    try {
      if (facePayEnrolled) await revokeFacePay(); else await enrollFacePay();
      setFacePayEnrolled((v) => !v);
    } catch {
      // Real gap found 2026-09-05 (Android's sibling ShopPay.kt Face Pay toggle was
      // already fixed to surface a real failure message -- this was silent, which is
      // worse: a security-relevant biometric-payment enrollment failing with zero
      // feedback could leave the user believing FacePay is on/off when it isn't).
      // The row's own enrollment state is untouched on failure (setFacePayEnrolled
      // above only runs after a successful await), so this toast is purely
      // informational, not correcting a false UI state.
      showToast(wasEnrolled ? t('toast.facePayDisableFailed') : t('toast.facePayEnrollFailed'));
    } finally {
      setFacePayBusy(false);
    }
  };

  if (paymentResult) return <PaymentConfirmation result={paymentResult} onDone={() => { setPaymentResult(null); loadAccount(); }} />;

  if (openAccountDetail) {
    return (
      <PayMoneyDetail
        account={openAccountDetail}
        onBack={() => setOpenAccountDetail(null)}
        onSend={() => { setOpenAccountDetail(null); setShowTransfer(true); }}
        // Real gap, honestly scoped out for now: itunda has no self-service
        // "pull an amount from my linked account right now" flow -- only
        // AutoTopUpCard's threshold-based auto top-up exists (configureAutoTopUp/
        // triggerAutoTopUp below), which isn't the same real capability the
        // reference's "Add money" button performs. Closing back to PayHub, where
        // AutoTopUpCard is already visible, rather than routing this button
        // somewhere unrelated (e.g. Bills) that would silently do the wrong thing.
        onAddMoney={() => setOpenAccountDetail(null)}
      />
    );
  }

  if (showCouponBox) {
    return <CouponBoxView onBack={() => setShowCouponBox(false)} onBrowseMerchants={() => { setShowCouponBox(false); onNavigateToTab('SHOP'); }} />;
  }

  if (showAgentCash) {
    return <AgentCashOutView onBack={() => setShowAgentCash(false)} onFindNearbyAgent={() => { setShowAgentCash(false); onNavigateToTab('MAP'); }} />;
  }

  if (showMembership) {
    return (
      <MembershipView
        onBack={() => setShowMembership(false)}
        onOpenRewards={() => { setShowMembership(false); onNavigateToTab('REWARDS'); }}
        onOpenPayMoney={() => { setShowMembership(false); if (account) setOpenAccountDetail(account); }}
      />
    );
  }

  return (
    <div>
      {/* Real redesign (2026-08-26, direct user reference to itunda Bank hub's own
          real structure -- SavingsView's ProductPageHeader/AccountSummaryRow/
          CooperativeSavingsRail): "itunda Pay" branded header (matching Bank hub's
          own "itunda Bank" title convention, not the bare "Pay" this used to say) +
          a real QR scan shortcut + settings icon (routes to You -- no dedicated
          Pay-settings screen exists). The QR button jumps straight to
          PayByCodeCard's own already-real camera-scan flow
          (id="pay-by-code-section" below) -- not a new scanner, just a faster,
          top-bar-level entry point to the existing real one. */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', margin: '4px 4px 16px' }}>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: 800, margin: 0, letterSpacing: '-0.5px' }}>itunda Pay</h2>
        <div style={{ display: 'flex', gap: '4px' }}>
          <button
            onClick={() => document.getElementById('pay-by-code-section')?.scrollIntoView({ behavior: 'smooth' })}
            aria-label="Scan to pay"
            style={{ color: 'var(--itunda-grey-500)', display: 'flex', padding: '4px' }}
          >
            <QrCode size={20} />
          </button>
          <button onClick={() => onNavigateToTab('YOU')} aria-label="Pay settings" style={{ color: 'var(--itunda-grey-500)', display: 'flex', padding: '4px' }}>
            <Settings size={20} />
          </button>
        </div>
      </div>
      {/* Real "Pay money" summary row -- the exact same real shape as itunda Bank
          hub's own AccountSummaryRow.tsx (big balance, chevron, opens the real
          ledger/statement screen on tap), promoted out of MyPaymentCodeCard's own
          smaller embedded row so it reads as Pay's own top-level "account" the same
          way AccountSummaryRow does for Bank. */}
      {account && (
        <button
          onClick={() => setOpenAccountDetail(account)}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '4px 0 4px', textAlign: 'left' }}
        >
          <div>
            <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Pay money</p>
            <p style={{ margin: '4px 0 0', fontSize: '26px', fontWeight: 700, color: 'var(--itunda-grey-900)', letterSpacing: '-0.5px' }}>
              {Math.round(animatedPayBalance).toLocaleString('en-US')} RWF
            </p>
          </div>
          <IconChevronRight size={20} color="var(--itunda-grey-400)" />
        </button>
      )}
      {/* Dual-balance UI (2026-08-29, closing [[project_itunda_bank_pay_separation]]'s
          last open item): symmetric secondary "itunda Bank" line, matching the one
          added to AccountSummaryRow.tsx for the Bank hub -- `accounts` already
          includes MAIN (fetched above for the carousel), just never surfaced as its
          own line here. Flat, not a card, tappable straight to the Bank hub. */}
      {mainAccount && (
        <button
          onClick={() => onNavigateToTab('SAVINGS')}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '4px 0 20px', textAlign: 'left', color: 'var(--itunda-grey-500)' }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>itunda Bank</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>
            {mainAccount.balance.toLocaleString('en-US')} {mainAccount.currency}
            <IconChevronRight size={14} color="var(--itunda-grey-400)" style={{ verticalAlign: 'middle', marginLeft: 4 }} />
          </span>
        </button>
      )}
      {/* Real embedded nearby-merchants map, paired with an explicit "Find store"
          button (the map's own overlay pill already opens the same real
          NearbyMerchantsDialog on tap; this adds a clearly-labeled second entry
          point next to it, matching the real Toss Pay reference's separate map +
          store-finder affordance). */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
        <NearbyMerchantsMap merchants={nearbyMerchants} userLocation={userLocation} onTap={() => setShowNearbyMerchantsDialog(true)} />
        {nearbyMerchants.length > 0 && (
          <button
            onClick={() => setShowNearbyMerchantsDialog(true)}
            className="itunda-btn itunda-btn-secondary"
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px', minHeight: '44px' }}
          >
            <Store size={16} /> Find store
          </button>
        )}
      </div>
      {showNearbyMerchantsDialog && <NearbyMerchantsDialog merchants={nearbyMerchants} onClose={() => setShowNearbyMerchantsDialog(false)} />}
      <FacePayStatusRow enrolled={facePayEnrolled} busy={facePayBusy} cashbackRatePercent={cashbackRatePercent} onToggle={handleFacePayToggle} />
      <MyPaymentCodeCard accounts={accounts} onOpenCard={onNavigateToCard} />
      {showTransfer && (
        <TransferFlow
          accountBalance={account?.balance ?? 0}
          onClose={() => setShowTransfer(false)}
          onBalanceRefresh={loadAccount}
          onSuccess={() => { setShowTransfer(false); loadAccount(); }}
        />
      )}
      <div style={{ display: 'flex', gap: '8px', margin: '4px 0 16px' }}>
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowTransfer(true)} disabled={!account} style={{ flex: 1, minHeight: '48px' }}>
          <IconSend size={17} /> Send money
        </button>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => onNavigateToTab('BILLS')} style={{ flex: 1, minHeight: '48px' }}>
          <Receipt size={17} /> Bills & airtime
        </button>
      </div>
      {/* Real "list of other pay services" -- the exact same flat-row shape itunda
          Bank hub's own CooperativeSavingsRail already uses (icon-in-tinted-square +
          title + subtitle), replacing the previous scattered mix of standalone cards
          with one real navigable list. Each row scrolls to its own already-real
          section below (or, for Cards/Rewards, the same existing real destination
          those rows already had) rather than duplicating any of their logic.
          Real, sourced money-transfer copy in `IconChevronRight` — this file's own
          existing icon set, nothing new invented for these rows beyond the icons
          imported at the top of this file (all real lucide-react icons already a
          dependency here). */}
      <PayHubOtherServicesRail onCardsClick={onNavigateToCard} onTransitClick={() => onNavigateToTab('TRANSIT')} onMotoFareClick={() => onNavigateToTab('MOTO_FARE_COLLECT')} onAgentCashClick={() => setShowAgentCash(true)} onNavigateToTab={onNavigateToTab} />
      <QuickActions onCardsClick={onNavigateToCard} />
      {rewardsPreview && <RewardsSummaryRow rewardsTotal={rewardsPreview.rewardsTotal} payBalance={account?.balance ?? null} />}
      {/* Real itunda-issued card summary row (itunda Pay redesign, 2026-08-28) --
          mirrors the real reference's own linked-card row using 100% real itunda
          data (hasCard/last4/frozen from GET /api/v1/card/my-card), never a
          fabricated "auto-apply points" claim a real external card issuer would
          make. Teaser state reuses the exact same dashed-border pattern the
          Overview redesign already established for an unissued card. */}
      {card !== undefined && (
        <div className="itunda-flat-section">
          {card ? (
            <button onClick={onNavigateToCard} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', textAlign: 'left' }}>
              <div>
                <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{t('overview.cardNumber', { last4: card.last4 })}</p>
                <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{card.frozen ? t('overview.cardFrozen') : t('overview.cardActive')}</p>
              </div>
              <IconChevronRight size={18} color="var(--itunda-grey-400)" />
            </button>
          ) : (
            <div style={{ border: '1px dashed var(--itunda-grey-300)', borderRadius: 'var(--itunda-radius-md)', padding: '16px', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('overview.teaserCards')}</p>
              <button className="itunda-btn itunda-btn-secondary" onClick={onNavigateToCard}>{t('overview.teaserCardsCta')}</button>
            </div>
          )}
        </div>
      )}
      {/* Real "Points · Pay Money" summary row (itunda Pay redesign, 2026-08-28) --
          the real reference's own Membership-screen entry point. Real
          rewardsTotal + real Pay balance, same two numbers RewardsSummaryRow
          above already shows separately, combined here to match the reference's
          own single-row layout. */}
      {rewardsPreview && (
        <button onClick={() => setShowMembership(true)} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', textAlign: 'left' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{t('pay.pointsPayMoneyRow')}</span>
          <span style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{((rewardsPreview.rewardsTotal ?? 0) + (account?.balance ?? 0)).toLocaleString('en-US')} RWF</span>
            <IconChevronRight size={18} color="var(--itunda-grey-400)" />
          </span>
        </button>
      )}
      {/* Real "Your Coupons" row -- see CouponBoxView.tsx's own doc comment for the
          real GET /api/v1/merchant/coupons/browse endpoint this now leads to. */}
      <button onClick={() => setShowCouponBox(true)} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', textAlign: 'left' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{t('pay.yourCouponsRow')}</span>
        <IconChevronRight size={18} color="var(--itunda-grey-400)" />
      </button>
      {rewardsPreview && <RewardsPreviewSection tasks={rewardsPreview} onViewAll={() => onNavigateToTab('REWARDS')} />}
      <div id="pay-request-money-section">
        <RequestMoneyCard />
      </div>
      <div id="pay-by-code-section">
        <PayByCodeCard onPaid={setPaymentResult} facePayEnrolled={facePayEnrolled} />
      </div>
      <PayByStaticQrCard onPaid={setPaymentResult} />
      <div id="pay-scheduled-transfers-section">
        <ScheduledTransfersCard />
      </div>
      <div id="pay-delayed-transfers-section">
        <DelayedTransfersCard />
      </div>
      <div id="pay-auto-topup-section">
        {account && <AutoTopUpCard accountId={account.id} />}
      </div>
      <TransactionHistory transactions={transactions} unusuallyLargeIds={unusuallyLargeIds} />
      <GetHelpLinks onOpenSupport={() => onNavigateToTab('SUPPORT')} />
    </div>
  );
}

// The Explore tab -- every real destination beyond Home/Pay/Messages/You, as a flat
// searchable catalog: no nested toggles or sub-tabs (that shape is correct for a
// primary tab, per ShopHub/HoodHub's own retired doc comment -- see Tab's own note
// above for why it was wrong here), matching Toss's real 전체 screen's own flat-list
// convention instead. groups/tabLabel/recentTabs/onSelect all come from one
// EXPLORE_TAB_GROUPS source of truth shared by both browsing and search, so a
// service can't land in one category when browsed and a different one when searched.
// Real Explore-tab pill, now with an optional leading itundaface icon (2026-08-29) --
// see EXPLORE_TAB_ICONS's own doc comment. Extracted since all 3 ExploreHub render
// sites (search matches, recently used, grouped catalog) render the exact same pill.
