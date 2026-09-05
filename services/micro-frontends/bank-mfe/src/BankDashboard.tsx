import { lazy, Suspense, useEffect, useId, useRef, useState, type ComponentType, type ReactElement } from 'react';
import { IconSend, IconShieldCheck, IconStar } from './icons/ItundaIcons';
import { motion, AnimatePresence } from 'framer-motion';
import QRCode from 'qrcode';
import JsBarcode from 'jsbarcode';
import { Bike, Check, Clock, Landmark, LogOut, QrCode, Receipt, Settings, Sprout, Store, Users, Wallet as AccountIcon } from 'lucide-react';
import { BankCardChip, CardContactlessGlyph } from './BankCardChip';import { IconAdd, IconBack, IconChevronRight, IconClose, IconSearch } from './icons/ItundaIcons';
import { IconHome, IconPay, IconExplore, IconMessages, IconYou } from './icons/ItundaIcons';
import { GiftGlyph, GiftBox } from './icons/ItundaFaceGifts';
import { WishlistHeart } from './icons/ItundaFaceHearts';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { PinGlyph, GlobeGlyph, MoneyBagGlyph, ShoppingBagGlyph, BikeGlyph, SpeechBubbleGlyph } from './icons/ItundaFaceMisc';
import { PlaceRestaurant, PlaceMarket, PlaceBusStop, PlaceItundaAgent } from './icons/ItundaFacePlaces';
// Real Explore-tab icons (2026-08-29, closing [[project_itunda_pure_tossface_icons]]'s
// "(c)" open item) -- reuses the exact same glyph choices Android's MenuScreen already
// made and live-verified against real Toss reference screenshots, not new choices.
import { ParkingGlyph } from './icons/ItundaFaceHome';
import { TravelCar, TravelHouse } from './icons/ItundaFaceTravel';
import { BriefcaseGlyph, ChartIncreasingGlyph } from './icons/ItundaFaceWork';
import { NatureStar, NatureGlowingStar } from './icons/ItundaFaceNature';
import { ObjectKey, ObjectPen } from './icons/ItundaFaceObjects';
import { averageCashbackRatePercent, FacePayStatusRow, GetHelpLinks, NearbyMerchantsDialog, NearbyMerchantsMap, PayHubOtherServicesRail, RewardsPreviewSection, RewardsSummaryRow } from './PayHomeExtras';
import { getStoredUser, logout, ApiError } from './lib/api';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { MyView } from './MyView';
import { CardExplainer } from './CardExplainer';
import { OverviewAssetsView } from './OverviewAssetsView';
import { PayFundingSourcePicker } from './PayFundingSourcePicker';
import { CouponBoxView } from './CouponBoxView';
import { MembershipView } from './MembershipView';
import { TransitScreen } from './TransitScreen';
import { TransitCollectScreen } from './TransitCollectScreen';
import { MotoFareCollectScreen } from './MotoFareCollectScreen';
import { QrScanCamera, parseQrParam } from './QrScanCamera';
import { recordEvent } from './lib/analytics';
import { useI18n } from './i18n/I18nContext';
import { LOCALES, type TranslationKey } from './i18n/translations';
import { IdsButton } from './IdsButton';
import { showToast } from './Toast';
import { EmptyState, ErrorCard } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';
import { ShopView } from './ShopView';
import { MessagesView } from './TalkGroupsAndFriends';
import { configureAutoTopUp, fetchAccountTransactions, fetchAutoTopUpSetting, fetchTransactions, fetchTransactionTimeline, fetchAccounts, triggerAutoTopUp, type AutoTopUpSetting, type Transaction, type Account } from './lib/account';
import { PayMoneyDetail } from './PayMoneyDetail';
import { AccountSummaryRow } from './AccountSummaryRow';
import { AccountDetailScreen } from './AccountDetailScreen';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, type TrustedDevice } from './lib/device';
import { fetchNotifications } from './lib/notifications';
import { fetchDiscoverItems, type DiscoverItem } from './lib/discover';
import { cardDesign, chargeCard, closeMyCard, fetchCardTransactions, fetchMyCard, freezeCard, issueCard, reissueCard, reportLostCard, setCardLimits, setCardPin, unfreezeCard, type Card, type CardTransaction } from './lib/card';
import { claimInterest, createGoal, depositToGoal, fetchDepositProtectionStatus, fetchGoalTransactions, fetchGoals, fetchInterestJar, fetchInterestJarTransactions, fetchRoundUpSettings, ROUND_UP_INCREMENTS, setRoundUpSettings, withdrawFromGoal, type DepositProtectionStatus, type InterestJar, type RoundUpSettings, type SavingsGoal } from './lib/savings';
import { BucketDetailScreen, BucketTransactionList } from './BucketDetailScreen';
import { transactionsToBucketTransactions, type BucketTransaction } from './lib/bucketTransaction';
import {
  createGroupAccount, depositToGroupAccount, fetchGroupAccount, fetchGroupAccountDues, fetchMyGroupAccounts, inviteGroupAccountMember,
  requestUnpaidGroupAccountDues, setGroupAccountDuesAmount, withdrawFromGroupAccount,
  type GroupAccount, type GroupAccountDetail, type GroupAccountDuesStatus,
} from './lib/groupAccounts';
import {
  contributeToIkimina, createIkimina, fetchIkimina, fetchMyIkiminas, inviteIkiminaMember, startIkiminaCycle, triggerIkiminaPayout,
  type Ikimina, type IkiminaDetail,
} from './lib/ikimina';
import {
  buySaccoShares, fetchMySaccoDividendHistory, fetchMySaccoShareholding, redeemSaccoShares,
  type SaccoDividendPayout, type SaccoShareholding,
} from './lib/sacco';
import {
  cancelWeeklySavingsPlan, createWeeklySavingsPlan, fetchWeeklySavingsPlan, fetchWeeklySavingsPlanTransactions, fetchWeeklySavingsPlans, withdrawWeeklySavingsPlan,
  WEEKLY_SAVINGS_ESCALATION_RATES, WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS, WEEKLY_SAVINGS_TERM_WEEKS,
  type WeeklySavingsPlan, type WeeklySavingsPlanDetail,
} from './lib/weeklySavings';
import {
  cancelGrow31SavingsPlan, createGrow31SavingsPlan, depositGrow31SavingsToday, fetchGrow31SavingsPlan, fetchGrow31SavingsPlanTransactions, fetchGrow31SavingsPlans,
  withdrawGrow31SavingsPlan, grow31BonusRateForStreak, GROW31_TERM_DAYS,
  type Grow31SavingsPlan, type Grow31SavingsPlanDetail,
} from './lib/grow31Savings';
import { collectWithFacePay, enrollFacePay, fetchFacePayStatus, revokeFacePay } from './lib/facepay';
import { fetchMyP2pRequests, generateP2pRequest, payP2pRequest, resolveRecipient, sendDirect, type P2pPaymentRequestDto, type P2pPaymentRequestStatus, type P2pRecipientPreview } from './lib/p2p';
import { CertificateView } from './CertificateView';
import { LoansView, type LoansMode } from './LoansView';
import { fetchLinkedAccounts, type LinkedAccount } from './lib/overview';
import { fetchCreditScore, fetchCreditScoreSuggestions, type CreditScoreFactor, type CreditScoreSuggestion } from './lib/creditScore';
import { CreditScoreView, TrustScoreView } from './ScoreViews';
import { fetchRewardTasks, type RewardTasksResult } from './lib/rewards';
import { RewardsView } from './RewardsView';
import { BillsView } from './BillsView';
import { AgentOperatorView } from './AgentOperatorView';
import { UssdSettingsView } from './UssdSettingsView';
import {
  fetchMyUpfrontDeposits, fetchUpfrontDepositTransactions, openUpfrontDeposit, withdrawUpfrontDeposit,
  UPFRONT_DEPOSIT_ANNUAL_RATE, UPFRONT_DEPOSIT_MIN_PRINCIPAL, UPFRONT_DEPOSIT_MAX_PRINCIPAL,
  type UpfrontInterestDeposit,
} from './lib/upfrontDeposit';
import { ForeignCurrencyView } from './ForeignCurrencyView';
import { FullScreenFlow } from './FullScreenFlow';
import { submitHoodReport, type HoodReportTargetType } from './lib/hoodReport';
import { IdentityView } from './IdentityView';
import { addContact, fetchContacts, type Contact } from './lib/contacts';
import { SupportView } from './SupportView';
import { SpendingInsightView } from './SpendingInsightView';
import { SubscriptionsView } from './SubscriptionsView';
import { collectPayment, fetchLoyaltyBalance, fetchNearbyAds, fetchNearbyMerchants, generateCustomerPaymentCode, payByStaticQr, previewPaymentIntent, type CollectPaymentResult, type CustomerPaymentCode, type MerchantCouponView, type NearbyMerchant, type NearbyMerchantAd, type PaymentIntentPreview } from './lib/shopping';
import { StocksView } from './StocksView';
import { fetchConversations, fetchGroups, type ConversationSummary } from './lib/messaging';
import {
  type HoodReview,
} from './lib/marketplace';
import { clearSecondNeighborhood, setBirthDate, setNeighborhood, setSecondNeighborhood } from './lib/neighborhood';
import { depositToYouthAccount, openYouthAccount } from './lib/youthAccount';
import { sendGift, GIFT_THEME_LABELS, type Gift, type GiftTheme } from './lib/gift';
import { searchDeliveryAddress, type AddressSuggestion, type MenuItem } from './lib/eats';
import { captureReferralCodeFromUrl } from './lib/affiliate';
// Real fix (2026-08-10): MapView pulls in the full maplibre-gl WebGL engine (+CSS)
// at module scope -- a static import here meant every user downloaded and parsed
// that whole library on first load, whether or not they ever open the Map tab. Real
// fintech UX research is explicit that speed is the #2 factor after security, with
// users trained to expect sub-3-second interactions -- for a product built around
// Rwanda's real mobile-network conditions, shipping a full map engine nobody asked
// for yet on every cold load is a direct, measurable cost against that. Lazy-loaded
// instead: maplibre-gl now only downloads when a user actually opens Map.
//
// Real maps-mfe split (2026-08-19) -- MapView now lives in its own federated remote
// (see maps-mfe/src/MapView.tsx + this package's own vite.config.ts remotes.maps_mfe
// block), the first real Module Federation *consumption* by this app (previously only
// ever exposed, never consumed) -- lazy-loading a federated remote is the same import()
// call as lazy-loading a local module, so this line barely changes.
const MapView = lazy(() => import('maps_mfe/MapView'));
const InsuranceView = lazy(() => import('./InsuranceView'));
const BikeShareView = lazy(() => import('./BikeShareView'));
const ParkingView = lazy(() => import('./ParkingView'));
const BusView = lazy(() => import('./BusView'));
import { fetchMyMapBookmarks, searchPlaces, type MapBookmark, type PlaceSearchResult } from './lib/maps';
import { checkScamStatus, reportScam, type ScamCheckResult } from './lib/scamReports';
// Real bank-mfe extraction-prep (itunda Hood redesign, 2026-08-28) -- BankDashboard.tsx
// was 24,653/24,708 lines against its frozen file-size-lint baseline (only ~55 lines of
// headroom), the same "one file doing everything" shape flagged repo-wide by
// docs/ARCHITECTURE_GUIDELINES.md §2. Marketplace/Community/Jobs/Property's real view
// assemblies + their domain-only card components move into their own files here,
// mirroring iOS's already-proven per-domain HoodScreen.swift split -- genuinely shared
// cross-domain pieces (NeighborhoodSetupPrompt/NeighborhoodSwitcherRow/WishlistButton/
// TrustBadge/HoodReportButton/HoodReviewForm/HoodReviewResultView) stay here, exported,
// since moving them would just relocate the "which domain owns this" question rather
// than answer it.
import { MarketplaceView } from './HoodMarketplace';
import { CommunityView } from './HoodCommunity';
import { JobsView } from './HoodJobs';
import { PropertyView } from './HoodProperty';
import { subscribeToProduct } from './lib/productSubscriptions';
import { cancelScheduledTransfer, createScheduledTransfer, fetchMyScheduledTransfers, type ScheduledTransfer } from './lib/scheduledTransfers';
import { cancelDelayedTransfer, fetchMyDelayedTransfers, sendDelayed, type DelayedTransfer } from './lib/delayedTransfers';
import {
  cancelAutoTransfer, createAutoTransfer, fetchMyAutoTransfers, pauseAutoTransfer, resumeAutoTransfer,
  type AutoTransfer, type AutoTransferFrequency,
} from './lib/autoTransfers';
// RidesView moved to its own file (2026-09-02, itunda-vs-Toss architecture
// comparison thread) -- see RidesView.tsx's own header for the full account.
import { RidesView } from './RidesView';
import { DesignatedDriverView } from './DesignatedDriverView';
import { EatsView } from './EatsView';
// KnowledgeView moved to its own file (2026-09-02, itunda-vs-Toss architecture
// comparison thread) -- see KnowledgeView.tsx's own header for the full account.
import { KnowledgeView } from './KnowledgeView';
import { useCountUp } from './hooks/useCountUp';

// Consumer navigation is organised around jobs, not the repository's feature
// inventory: Home / Pay / Explore / Messages / You (2026-08-10, explicit product
// decision after directly comparing this against Home/Shop/Hood/Talk/All -- see
// docs/DESIGN_REFERENCES.md Section 41 for the full research this was weighed
// against). Pay and You get dedicated primary slots.
//
// Real correction, same day: Shop/Eats and Marketplace/Community/Jobs/Property were
// first reached through Explore as two entries (Shop, Hood) each opening a
// segmented-toggle sub-screen -- the ShopHub/HoodHub shape that's the *correct* one
// for a primary tab (mirrors Android's real Shop/Eats row, iOS HoodScreen's real
// Picker), but wrong once nested inside Explore: a tab bar inside a tab is exactly
// the noise a flat catalog is supposed to avoid. Toss's own real 전체 screen is a
// flat list of individual rows, not nested toggles -- so Shop/Eats/Marketplace/
// Community/Jobs/Property are each their own flat Tab id and their own flat row in
// EXPLORE_TAB_GROUPS, same as every other Explore destination. ShopHub/HoodHub are
// retired; ShopView/EatsView/MarketplaceView/CommunityView/JobsView/PropertyView
// render directly, exactly as they did before either hub existed.
export type Tab = 'HOME' | 'PAY' | 'EXPLORE' | 'YOU' | 'CERTIFICATE' | 'SHOPPING' | 'SHOP' | 'EATS' | 'MARKETPLACE' | 'COMMUNITY' | 'JOBS' | 'PROPERTY' | 'STOCKS' | 'SAVINGS' | 'MESSAGES' | 'RIDES' | 'DESIGNATED_DRIVER' | 'BIKESHARE' | 'PARKING' | 'BUS' | 'KNOWLEDGE' | 'MAP' | 'DEVICES' | 'CARD' | 'TRANSIT' | 'TRANSIT_COLLECT' | 'MOTO_FARE_COLLECT' | 'OVERVIEW' | 'LOANS' | 'CREDIT_SCORE' | 'TRUST_SCORE' | 'IDENTITY' | 'SUPPORT' | 'MY' | 'SUBSCRIPTIONS' | 'SPENDING' | 'FOREIGN_CURRENCY' | 'REWARDS' | 'INSURANCE' | 'BILLS' | 'AGENT' | 'USSD';

// Real Explore-tab icons (2026-08-29, closing [[project_itunda_pure_tossface_icons]]'s
// "(c)" open item: "the real, larger, unscoped redesign: giving web's Explore screen
// actual per-row icons in the first place"). ExploreHub's pill buttons had no icon of
// any kind, unlike Android/iOS's real, live-verified illustrated MenuScreen/
// EntireMenuScreen rows -- reuses the EXACT SAME glyph choice already made and
// verified there for each concept, not a new choice invented for web.
// 22 of 24 real Explore tabs now covered (EATS/MARKETPLACE/COMMUNITY/BUS/KNOWLEDGE/
// AGENT closed same day by porting the missing "Place*"/speech-bubble glyphs from
// Android's features/maps/impl/ItundaFacePlaces.kt + core/designsystem/itundaface/
// ItundaFaceMisc.kt -- see icons/ItundaFacePlaces.tsx's own doc comment for why the
// port source was Android/iOS's byte-identical shape data, not the disputed
// bank-mfe-origin citation in those files' own headers). Only INSURANCE (Android uses
// a bespoke IdsIcons.ShieldCheck, not an itundaface glyph) and USSD (no established
// Android choice exists at all) are left on the plain pill deliberately, not guessed.
const EXPLORE_TAB_ICONS: Partial<Record<Tab, ComponentType<{ size?: number }>>> = {
  SHOP: ShoppingBagGlyph,
  EATS: PlaceRestaurant,
  RIDES: TravelCar,
  MAP: PinGlyph,
  MARKETPLACE: PlaceMarket,
  COMMUNITY: SpeechBubbleGlyph,
  JOBS: BriefcaseGlyph,
  PROPERTY: TravelHouse,
  DESIGNATED_DRIVER: ObjectKey,
  BIKESHARE: BikeGlyph,
  PARKING: ParkingGlyph,
  BUS: PlaceBusStop,
  SAVINGS: MoneyBagGlyph,
  STOCKS: ChartIncreasingGlyph,
  LOANS: MoneyBagGlyph,
  CREDIT_SCORE: NatureGlowingStar,
  FOREIGN_CURRENCY: GlobeGlyph,
  TRUST_SCORE: NatureStar,
  KNOWLEDGE: SpeechBubbleGlyph,
  REWARDS: GiftBox,
  CERTIFICATE: ObjectPen,
  AGENT: PlaceItundaAgent,
};

// Real gap named in docs/DESIGN_REFERENCES.md's own IA research (Section 41 item 6):
// `tab` lived only in local useState, never in the URL -- refreshing the page or
// sharing a link always landed back on Home, unlike every native app's own real
// itunda:// deep-link scheme (Section 1). This is the runtime mirror of the `Tab`
// union above (TypeScript types don't exist at runtime, so an incoming `?tab=` value
// needs a real Set to validate against, not just a cast) -- kept next to the type so
// the two can't silently drift apart when a tab is added or removed.
const ALL_TAB_IDS = new Set<Tab>(['HOME', 'PAY', 'EXPLORE', 'YOU', 'CERTIFICATE', 'SHOPPING', 'SHOP', 'EATS', 'MARKETPLACE', 'COMMUNITY', 'JOBS', 'PROPERTY', 'STOCKS', 'SAVINGS', 'MESSAGES', 'RIDES', 'DESIGNATED_DRIVER', 'BIKESHARE', 'PARKING', 'BUS', 'KNOWLEDGE', 'MAP', 'DEVICES', 'CARD', 'TRANSIT', 'TRANSIT_COLLECT', 'MOTO_FARE_COLLECT', 'OVERVIEW', 'LOANS', 'CREDIT_SCORE', 'TRUST_SCORE', 'IDENTITY', 'SUPPORT', 'MY', 'SUBSCRIPTIONS', 'SPENDING', 'FOREIGN_CURRENCY', 'REWARDS', 'INSURANCE', 'BILLS', 'AGENT', 'USSD']);
const TAB_QUERY_PARAM = 'tab';
const readTabFromUrl = (): Tab => {
  try {
    const raw = new URLSearchParams(window.location.search).get(TAB_QUERY_PARAM);
    if (!raw || !ALL_TAB_IDS.has(raw as Tab)) return 'HOME';
    // Real retirement of the legacy 'SHOPPING' tab (itunda Shopping redesign,
    // 2026-08-28) -- 'SHOPPING' stays in ALL_TAB_IDS/Tab purely so an old
    // bookmarked/shared `?tab=SHOPPING` link still validates and lands somewhere
    // real (the redesigned ShopView, its real successor) rather than rendering
    // blank content.
    if (raw === 'SHOPPING') return 'SHOP';
    return raw as Tab;
  } catch {
    return 'HOME';
  }
};

// Real direct itunda-to-itunda push-transfer (2026-07-20) -- closes a real gap found
// live while first wiring this exact button: AccountController's quote/confirm transfer
// (used by Android/iOS's sendTransfer) always routes through a simulated external rail
// and never actually credits another itunda user's account, even when the recipient is a
// real itunda account (confirmed via direct MySQL query: recipientId stayed "external").
// This now calls the new real rw.itunda.p2p.sendDirect instead -- a real recipient
// resolved by phone number or account number, credited immediately, no fee (nothing
// external to settle). No network "quote" step needed (unlike the external-rail flow,
// there's no rail decision to quote) -- the review screen below is a client-side
// confirmation only, same inline-card-replaces-trigger convention every other flow in
// this file already uses, not a modal overlay.
// Real Toss 사기계좌 조회-style report action -- see lib/scamReports.ts's own doc
// comment.
// Localized 2026-08-09 -- explicitly named as a known, out-of-scope English gap two
// localization passes ago (see docs/DESIGN_REFERENCES.md Section 19, the web
// transfer-flow pass): closing it now while finishing the rest of HomeView.
function ReportScamLink({ identifier }: { identifier: string }) {
  const { t } = useI18n();
  const [reporting, setReporting] = useState(false);
  const [done, setDone] = useState(false);

  const handleReport = async () => {
    const reason = window.prompt(t('scamReport.promptQuestion', { identifier }));
    if (!reason || !reason.trim()) return;
    setReporting(true);
    try {
      await reportScam(identifier, reason.trim());
      setDone(true);
    } catch {
      // Real, non-critical from the sender's own transfer flow's point of view --
      // a failed report shouldn't block or disrupt the transfer screen around it.
    } finally {
      setReporting(false);
    }
  };

  if (done) return <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('scamReport.thanks')}</p>;

  return (
    <button type="button" onClick={handleReport} disabled={reporting} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', textAlign: 'left' }}>
      {reporting ? t('scamReport.reporting') : t('scamReport.reportLink')}
    </button>
  );
}

// Real Toss "송금" flow -- recipient picker, then amount entry with a numeric keypad,
// then a confirm sheet ("Send X RWF to [name] now"), then a sending spinner, then a
// success screen -- rebuilt 2026-08-18 to match Toss's own real send-money screens
// exactly (reference: screenshots the user supplied directly, per this session's own
// "don't imagine, use real reference" rule) instead of the prior single-card form.
// The backend calls underneath are unchanged: this is a UI/flow rebuild, not a new
// capability, except for resolveRecipient (lib/p2p.ts), which already existed on the
// backend (P2pService.resolveRecipient) with zero client caller until now -- it's what
// lets the amount/confirm screens show the real resolved "To [name]" the same way the
// reference screenshots do.

// Real Toss ProgressStepper component, compact variant -- see
// tossmini-docs.toss.im/tds-mobile/components/progress-stepper's own real API shape
// (`<ProgressStepper variant="compact" activeStepIndex={N}><ProgressStep title="..."
// />...</ProgressStepper>`) -- mirrored here as a flat `steps` prop instead of
// compound children, matching this codebase's own established "Flat API over Compound
// API" convention (see `IdsButton`'s own header comment for the identical real
// precedent already applied once in this file). Was missing entirely from itunda's own
// new multi-step flows (`TransferFlow` below, `CreateGoalForm`, Section 198) -- a real,
// sourced gap found 2026-08-19: a user had no visual sense of how many steps remained
// or where they were in the flow. Only rendered on real navigable decision steps, not
// on a flow's transient/terminal states (a "sending" spinner or a "success" screen
// isn't a step to track progress toward, it's the destination).
function ProgressStepper({ activeStepIndex, steps }: { activeStepIndex: number; steps: string[] }) {
  return (
    <div style={{ display: 'flex', alignItems: 'flex-start', paddingTop: '4px', paddingBottom: '12px' }}>
      {steps.map((label, i) => (
        <div key={label} style={{ display: 'flex', alignItems: 'center', flex: i < steps.length - 1 ? 1 : '0 0 auto' }}>
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
            <div
              style={{
                width: '8px', height: '8px', borderRadius: '50%',
                backgroundColor: i <= activeStepIndex ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)',
              }}
            />
            <span
              style={{
                fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: i === activeStepIndex ? 700 : 500, whiteSpace: 'nowrap',
                color: i === activeStepIndex ? 'var(--itunda-indigo)' : i < activeStepIndex ? 'var(--itunda-grey-700)' : 'var(--itunda-grey-400)',
              }}
            >
              {label}
            </span>
          </div>
          {i < steps.length - 1 && (
            <div
              style={{
                flex: 1, height: '1px', marginBottom: '17px', marginLeft: '4px', marginRight: '4px',
                backgroundColor: i < activeStepIndex ? 'var(--itunda-indigo)' : 'var(--itunda-grey-200)',
              }}
            />
          )}
        </div>
      ))}
    </div>
  );
}

type TransferStep = 'recipient' | 'amount' | 'confirm' | 'sending' | 'success';

// Exported (2026-08-31) so OverviewAssetsView.tsx -- a standalone file, see its own
// header comment on why -- can reuse this exact real send flow for its own per-account
// "Send" action, rather than duplicating it.
export function TransferFlow({ onClose, onSuccess, onBalanceRefresh, accountBalance, fromAccountId, fromAccountName }: { onClose: () => void; onSuccess: () => void; onBalanceRefresh?: () => void; accountBalance: number; fromAccountId?: string; fromAccountName?: string }) {
  const { t } = useI18n();
  const TRANSFER_STEP_LABELS = [t('transfer.stepRecipient'), t('transfer.stepAmount'), t('transfer.stepConfirm')];
  const [step, setStep] = useState<TransferStep>('recipient');
  const [recipient, setRecipient] = useState('');
  const [recipientPreview, setRecipientPreview] = useState<P2pRecipientPreview | null>(null);
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const [result, setResult] = useState<{ message: string; newBalance: number; fraudWarnings: string[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  // Real device binding (2026-07-20) -- a real 403 DEVICE_NOT_VERIFIED (this device
  // hasn't been step-up-verified yet) gets its own real prompt, not just a generic
  // error string, since the user has a real, actionable next step.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real saved-contacts list (found 2026-07-22 fully built on the backend with zero
  // client UI anywhere) -- this form previously had no recipient picker at all, just
  // a bare phone/account text field.
  const [contacts, setContacts] = useState<Contact[]>([]);
  const [showAddContact, setShowAddContact] = useState(false);
  const [newContactName, setNewContactName] = useState('');
  const [newContactPhone, setNewContactPhone] = useState('');
  // Real Toss 사기계좌 조회-style pre-transfer warning -- see lib/scamReports.ts's own
  // doc comment. A warning, not a hard block -- Toss's own real feature lets a sender
  // proceed past it too, it just withdraws their fraud-reimbursement protection for
  // doing so (this codebase has no such protection scheme to withdraw, so proceeding
  // here is simply the sender's own informed choice).
  const [scamCheck, setScamCheck] = useState<ScamCheckResult | null>(null);
  // Real standalone "gift" send (KakaoTalk 선물하기-style, GiftController's own
  // POST /api/v1/gifts) -- found fully built server-side with zero client caller
  // anywhere; only the chat-embedded sibling had a UI. Money moves into escrow, not
  // straight to the recipient's account, until they explicitly claim it -- so this
  // reuses the same recipient/amount fields as a plain transfer but branches at
  // confirm-time into a different backend call and a different result panel.
  const [isGift, setIsGift] = useState(false);
  const [giftTheme, setGiftTheme] = useState<GiftTheme | ''>('');
  const [giftNote, setGiftNote] = useState('');
  const [giftResult, setGiftResult] = useState<Gift | null>(null);

  const loadContacts = () => fetchContacts().then(setContacts).catch(() => {});
  useEffect(() => { loadContacts(); }, []);

  const handleAddContact = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await addContact(newContactName, newContactPhone);
      setNewContactName(''); setNewContactPhone(''); setShowAddContact(false);
      loadContacts();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('transfer.saveContactError'));
    }
  };

  // Real gap found live (2026-08-10), applying Toss Tech's own "the best error is
  // one that never occurs" principle (toss.tech/article/21021, "좋은 에러 메시지를
  // 만드는 6가지 원칙"): accountBalance is already known here (AccountBalance renders
  // it right above this form), yet an amount larger than it previously round-tripped
  // to the backend's 422 before saying anything. Same fix as Android/iOS.
  const insufficientBalance = Number(amount || 0) > 0 && Number(amount || 0) > accountBalance;
  const recipientName = recipientPreview?.displayName ?? recipient;

  const selectRecipient = (identifier: string) => {
    const trimmed = identifier.trim();
    if (!trimmed) return;
    setError(null);
    setRecipient(trimmed);
    setRecipientPreview(null);
    setScamCheck(null);
    checkScamStatus(trimmed).then(setScamCheck).catch(() => {
      // Real, non-critical -- a failed safety check must never block a real transfer
      // the sender is otherwise entitled to make.
    });
    if (!isGift) {
      // Real Toss/Kakao Bank-style recipient-name confirmation -- resolves before the
      // amount screen renders "To [name]", matching the reference screenshots. A
      // failed lookup is non-fatal here (falls back to showing the raw identifier):
      // sendDirect itself still does the real, authoritative resolution at send time.
      resolveRecipient(trimmed).then(setRecipientPreview).catch(() => {});
    }
    setStep('amount');
  };

  // Real Toss/Kakao keypad shape (…7 8 9 / 00 0 backspace) -- RWF has no minor unit in
  // this codebase (every amount elsewhere is a whole-number toLocaleString()), so
  // there's no decimal-point key. Capped at 9 digits (under 1 billion RWF) purely as a
  // fat-finger guard, not a real product limit.
  const appendDigit = (d: string) => {
    setAmount((prev) => {
      if (d === '00') return prev === '' || prev === '0' ? prev : (prev + '00').slice(0, 9);
      return (prev === '0' ? d : prev + d).slice(0, 9);
    });
  };
  const backspace = () => setAmount((prev) => prev.slice(0, -1));

  const handleConfirm = async () => {
    setError(null);
    setNeedsDeviceVerification(false);
    setBusy(true);
    setStep('sending');
    try {
      if (isGift) {
        const gift = await sendGift(recipient.trim(), Number(amount), giftNote, giftTheme || null);
        setGiftResult(gift);
        onBalanceRefresh?.();
        setStep('success');
        return;
      }
      const res = await sendDirect(recipient.trim(), Number(amount), memo.trim(), fromAccountId);
      setResult({ message: res.message, newBalance: res.newBalance, fraudWarnings: res.fraudWarnings });
      // Real fix (2026-08-13, direct live-testing catch): the top-level balance
      // (AccountBalance, rendered above this whole form) previously only refreshed
      // when onSuccess fired on the "Done" button -- but this confirmation panel
      // already has the real, correct new balance the instant the transfer succeeds.
      // For that whole in-between window, the two numbers visibly disagreed on the
      // same screen (this panel said the new balance, the balance above still showed
      // the pre-transfer one). Refresh in the background now, without closing this
      // panel -- onSuccess (Done) still fires its own close-and-reload afterward.
      onBalanceRefresh?.();
      setStep('success');
    } catch (err) {
      // Real device step-up retries this exact same handleConfirm call once verified
      // (DeviceStepUpPrompt's own doc comment), so it needs to land back on the
      // 'confirm' step rather than staying on the transient 'sending' one.
      setStep('confirm');
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        // A real, honest error surfaces here as-is -- e.g. a recipient that doesn't
        // match any real itunda account real-404s rather than silently doing nothing.
        setError(err instanceof ApiError ? err.message : t('transfer.sendError'));
      }
    } finally {
      setBusy(false);
    }
  };

  // Step 5: success -- checkmark, "Sent X RWF to [name]," fee-covered subtitle, Share
  // (real Web Share API where supported) + Done. Matches the reference screenshots'
  // "Sent" screen; gift's own distinct escrow-pending message is a separate branch
  // since it's a real, different outcome (held, not delivered, until claimed).
  if (step === 'success') {
    if (giftResult) {
      return (
        <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={onSuccess}>{t('transfer.done')}</IdsButton>}>
          <div style={{ textAlign: 'center', padding: '32px 0' }}>
            <span style={{ display: 'flex', justifyContent: 'center', marginBottom: '12px' }}><GiftGlyph theme={giftResult.theme} size={40} /></span>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-19-size)', fontWeight: 800, marginBottom: '6px' }}>Gift sent!</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {giftResult.amount.toLocaleString()} RWF is held until {recipient.trim()} claims it -- auto-refunded to you after 7 days if unclaimed.
            </p>
          </div>
        </FullScreenFlow>
      );
    }
    if (!result) return null;
    return (
      <FullScreenFlow
        bottomCTA={
          <div>
            <div style={{ display: 'flex', gap: '10px' }}>
              {typeof navigator !== 'undefined' && !!navigator.share && (
                <IdsButton
                  variant="tinted" fullWidth style={{ flex: 1 }}
                  onClick={() => navigator.share({ text: `Sent ${Number(amount).toLocaleString()} RWF to ${recipientName} via itunda` }).catch(() => {})}
                >
                  Share
                </IdsButton>
              )}
              <IdsButton fullWidth style={{ flex: 1 }} onClick={onSuccess}>{t('transfer.done')}</IdsButton>
            </div>
            {/* Real Toss reference screenshot (2026-08-23, user-supplied): the fee-
                covered reassurance sits on its own line BELOW the buttons, not crammed
                into the same line as other info -- moved here from the content area to
                match. */}
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', marginTop: '10px' }}>
              {t('transfer.feeCovered')}
            </p>
          </div>
        }
      >
        {/* Real Toss "Sent" screen (2026-08-23, user-supplied reference screenshot):
            a distinct "Sent" headline (not the generic "Done" this used to share with
            the button below it), amount and recipient as their own clear stacked
            lines rather than crammed onto one "{amount} → {name}" row, and a real
            spring entrance on the icon -- itunda had zero animation on this screen
            before. Solid green circle + plain white check (not this draft's earlier
            indigo/ShieldCheck attempt) to match Android's own already-shipped, more
            established IdsCelebrationScreen pattern (real haptics + spring + confetti,
            reused across 4 real success moments) -- found while porting this same
            screen to Android/iOS the same session: itunda's real identity for THIS
            specific moment is green+check, not a one-off invented here. Literal white,
            not the --itunda-white token, which flips to a dark grey in dark mode and
            would go invisible against the green circle. */}
        <div style={{ textAlign: 'center', padding: '32px 0' }}>
          <motion.div
            initial={{ scale: 0.4, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            transition={{ type: 'spring', stiffness: 340, damping: 22 }}
            style={{ width: '72px', height: '72px', borderRadius: '36px', backgroundColor: 'var(--itunda-green)', display: 'flex', alignItems: 'center', justifyContent: 'center', margin: '0 auto 20px' }}
          >
            <Check size={38} color="#ffffff" />
          </motion.div>
          <motion.div initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ delay: 0.12, duration: 0.3, ease: 'easeOut' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>{t('transfer.sentHeadline')}</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, marginBottom: '4px' }}>{Number(amount).toLocaleString()} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-grey-700)' }}>{t('transfer.toLabel')} {recipientName}</p>
            {memo.trim() && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '10px' }}>&ldquo;{memo.trim()}&rdquo;</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-400)', marginTop: '14px' }}>
              {t('transfer.newBalance', { amount: result.newBalance.toLocaleString() })}
            </p>
            {/* Real Toss "Fraud Suspicion Siren" (사기의심 사이렌) parity -- see
                lib/p2p.ts's own doc comment. Purely informational: the transfer this
                warning is attached to has already completed by the time it's shown,
                same as Toss's own post-payment FDS notice. */}
            {result.fraudWarnings.length > 0 && (
              <div style={{ backgroundColor: 'var(--itunda-red-light)', border: '1px solid var(--itunda-red)', borderRadius: '8px', padding: '10px 12px', marginTop: '14px', textAlign: 'left' }}>
                {result.fraudWarnings.map((warning, i) => (
                  <p key={i} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: i === 0 ? 0 : '4px 0 0' }}>
                    {warning}
                  </p>
                ))}
              </div>
            )}
          </motion.div>
        </div>
      </FullScreenFlow>
    );
  }

  // Step 4: sending -- a brief transient screen while handleConfirm's await is
  // in flight, matching the reference screenshots' own loading screen between confirm
  // and success. Full-screen too (no bottomCTA -- nothing to press while it's in
  // flight), matching Toss's own real loan-review loading screen precedent
  // (toss.tech/article/interaction) of a dedicated, real loading PAGE, not an inline
  // spinner competing with unrelated content.
  if (step === 'sending') {
    return (
      <FullScreenFlow>
        <div style={{ textAlign: 'center', padding: '48px 0' }}>
          <motion.div
            animate={{ rotate: 360 }}
            transition={{ duration: 0.8, repeat: Infinity, ease: 'linear' }}
            style={{ width: '40px', height: '40px', margin: '0 auto 16px', border: '3px solid var(--itunda-indigo-light)', borderTopColor: 'var(--itunda-indigo)', borderRadius: '50%' }}
          />
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-700)' }}>{t('transfer.sending')}</p>
        </div>
      </FullScreenFlow>
    );
  }

  // Step 3: confirm -- "Send X RWF to [name] now" full-screen flow, matching the
  // reference screenshots. Cancel returns to the amount screen (not a full close) so a
  // sender can fix a typo'd amount without re-picking the recipient. The real
  // Cancel/Send pair moves to the pinned FixedBottomCTA; DeviceStepUpPrompt (when it
  // takes over) stays in the scrollable content area instead -- it's a real,
  // self-contained component with its own submit button already, not a page-level
  // action this flow's own CTA bar should duplicate.
  if (step === 'confirm') {
    return (
      <FullScreenFlow
        bottomCTA={
          !needsDeviceVerification && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ display: 'flex', gap: '10px' }}>
                <IdsButton variant="tinted" fullWidth style={{ flex: 1 }} onClick={() => setStep('amount')} disabled={busy}>{t('transfer.cancel')}</IdsButton>
                {/* Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11): a
                    bare "Confirm" doesn't state the outcome -- Toss's own dark-pattern-
                    prevention rules require CTA labels to name the specific action, not a
                    generic verb, matching the "Clear Action" principle. */}
                <IdsButton fullWidth style={{ flex: 1 }} onClick={handleConfirm} disabled={busy}>
                  {isGift ? (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px', justifyContent: 'center' }}>
                      <GiftGlyph theme={null} size={16} /> Send gift · {Number(amount).toLocaleString()} RWF
                    </span>
                  ) : t('transfer.send', { amount: Number(amount).toLocaleString() })}
                </IdsButton>
              </div>
              {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
              <ReportScamLink identifier={recipient.trim()} />
            </div>
          )
        }
      >
        <ProgressStepper activeStepIndex={2} steps={TRANSFER_STEP_LABELS} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 800 }}>
          {t('transfer.confirmSendNow', { amount: Number(amount).toLocaleString(), recipient: recipientName })}
        </h3>
        <div style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '8px' }}>
          <span>{recipient.trim()}</span>
          {memo.trim() && <span>&ldquo;{memo.trim()}&rdquo;</span>}
        </div>
        {scamCheck?.warn && (
          <div style={{ backgroundColor: 'var(--itunda-red-light)', border: '1px solid var(--itunda-red)', borderRadius: '8px', padding: '10px 12px', marginTop: '12px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-red)' }}>{t('transfer.scamWarningTitle')}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '2px' }}>
              {t('transfer.scamWarningBody', { count: scamCheck.reportCount })}
            </p>
          </div>
        )}
        {needsDeviceVerification && (
          // Real fix (2026-08-10): re-entering a password to verify the device already
          // proves who's asking -- making the user then tap "Send" a second time for
          // the exact transfer they just reviewed and confirmed adds friction, not
          // security. handleConfirm resets needsDeviceVerification itself, so calling
          // it directly both clears the prompt and retries the same transfer.
          <div style={{ marginTop: '12px' }}>
            <DeviceStepUpPrompt onVerified={handleConfirm} onCancel={onClose} />
          </div>
        )}
      </FullScreenFlow>
    );
  }

  // Step 2: amount -- "To [name]" header with a back arrow, a big centered amount
  // readout, a tap-to-fill balance line, an optional memo, and a numeric keypad,
  // matching the reference screenshots' amount-entry screen.
  if (step === 'amount') {
    return (
      <FullScreenFlow
        bottomCTA={
          <>
            {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
            <IdsButton
              fullWidth
              disabled={!amount || Number(amount) <= 0 || insufficientBalance}
              onClick={() => { setError(null); setStep('confirm'); }}
            >
              {t('transfer.next')}
            </IdsButton>
          </>
        }
      >
        <ProgressStepper activeStepIndex={1} steps={TRANSFER_STEP_LABELS} />
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button type="button" aria-label={t('transfer.cancel')} onClick={() => setStep('recipient')} style={{ background: 'none', border: 'none', padding: '4px', display: 'flex' }}>
            <IconBack size={22} color="var(--itunda-grey-700)" />
          </button>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('transfer.toLabel')}</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{recipientName}</span>
          {recipientPreview && <IconShieldCheck size={15} color="var(--itunda-indigo)" />}
          <button type="button" aria-label={t('transfer.cancel')} onClick={onClose} style={{ marginLeft: 'auto', background: 'none', border: 'none', padding: '4px', display: 'flex' }}>
            <IconClose size={20} color="var(--itunda-grey-500)" />
          </button>
        </div>

        <div style={{ textAlign: 'center', padding: '20px 0 8px' }}>
          {/* Real Toss largeAmount token (34px/41px/700, same value Android's
              IdsTypography.LargeAmount and iOS's IDS.swift already agree on --
              see project_itunda_product_feel's own doc comment) -- this was the
              app's single largest, most prominent number on screen but used an
              invented 38px/800 pair matching no real Toss/itunda token. */}
          <span style={{ fontSize: 'var(--itunda-type-large-amount-size)', lineHeight: 'var(--itunda-type-large-amount-line-height)', fontWeight: 'var(--itunda-type-large-amount-weight)' }}>
            {amount === '' ? '0' : Number(amount).toLocaleString()} <span style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>RWF</span>
          </span>
          <div>
            {/* Real gap found live (2026-08-31, direct user reference of their own
                Toss app's "which account should the money come from" picker): a
                specific non-default source account (passed in from Overview's or the
                Bank hub's own account detail screen -- see OverviewAssetsView.tsx and
                SavingsView's onSend) previously had no visible confirmation anywhere
                on this screen that it, not the sender's MAIN account, is what's about
                to be debited. */}
            {fromAccountName && (
              <p style={{ margin: '0 0 2px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                From {fromAccountName}
              </p>
            )}
            <button type="button" onClick={() => setAmount(String(accountBalance))} style={{ marginTop: '6px', background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>
              {t('transfer.balanceLabel', { amount: accountBalance.toLocaleString() })}
            </button>
          </div>
          {insufficientBalance && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: '4px 0 0' }}>
              {t('transfer.insufficientBalance', { amount: accountBalance.toLocaleString() })}
            </p>
          )}
        </div>

        {isGift ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <input
              type="text" value={giftNote} onChange={(e) => setGiftNote(e.target.value)} placeholder="Add a note (optional)" maxLength={200}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', textAlign: 'center' }}
            />
            <select
              value={giftTheme} onChange={(e) => setGiftTheme(e.target.value as GiftTheme | '')}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            >
              <option value="">No theme (plain gift)</option>
              {(Object.keys(GIFT_THEME_LABELS) as GiftTheme[]).map((theme) => (
                <option key={theme} value={theme}>{GIFT_THEME_LABELS[theme]}</option>
              ))}
            </select>
          </div>
        ) : (
          <input
            type="text" value={memo} onChange={(e) => setMemo(e.target.value)} placeholder={t('transfer.memoPlaceholder')} maxLength={200}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', textAlign: 'center', width: '100%', boxSizing: 'border-box' }}
          />
        )}

        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '2px', marginTop: '8px' }}>
          {['1', '2', '3', '4', '5', '6', '7', '8', '9', '00', '0', '⌫'].map((k) => (
            <button
              key={k} type="button"
              onClick={() => (k === '⌫' ? backspace() : appendDigit(k))}
              aria-label={k === '⌫' ? 'Backspace' : `Enter ${k}`}
              style={{ padding: '16px 0', background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 600, color: 'var(--itunda-grey-900)', borderRadius: '10px', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
            >
              {/* Real gap found live (2026-08-31, direct user correction: "backspace
                  button of keyboard should be horizontal arrow (toss style) instead
                  of those weird icons") -- the raw "⌫" text glyph renders
                  inconsistently across fonts/platforms; reuses the same real,
                  already-cross-platform-shared IconBack chevron the app's own back
                  buttons use, instead of a second, different icon concept. */}
              {k === '⌫' ? <IconBack size={20} color="var(--itunda-grey-900)" /> : k}
            </button>
          ))}
        </div>
      </FullScreenFlow>
    );
  }

  // Step 1: recipient -- search/manual-entry field, gift toggle, and the real saved-
  // contacts "Recent" list, matching the reference screenshots' recipient screen. No
  // bottomCTA here -- "Continue" is a search-submit action tightly coupled to the
  // search field beside it, not a standalone page-level confirmation the FixedBottomCTA
  // pattern is meant for (Toss's own real SearchField-adjacent buttons work the same
  // inline way).
  return (
    <FullScreenFlow>
      <ProgressStepper activeStepIndex={0} steps={TRANSFER_STEP_LABELS} />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 800 }}>{t('transfer.recipientStepTitle')}</h3>
        <button type="button" aria-label={t('transfer.cancel')} onClick={onClose} style={{ background: 'none', border: 'none', padding: '4px', display: 'flex' }}>
          <IconClose size={20} color="var(--itunda-grey-500)" />
        </button>
      </div>
      <form onSubmit={(e) => { e.preventDefault(); selectRecipient(recipient); }} style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
        <div style={{ position: 'relative', flex: 1 }}>
          <IconSearch size={16} color="var(--itunda-grey-400)" style={{ position: 'absolute', left: '12px', top: '50%', transform: 'translateY(-50%)' }} />
          <input
            type="text" value={recipient} onChange={(e) => setRecipient(e.target.value)}
            placeholder={t('transfer.recipientPlaceholder')} required
            style={{ width: '100%', padding: '12px 14px 12px 36px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', boxSizing: 'border-box' }}
          />
        </div>
        {/* Real fix (2026-08-18, direct live-testing catch): IdsButton's own Large-size
            default (width: fullWidth ?? size === 'large' ? '100%' : undefined) claims
            100% width even without fullWidth set -- fine standalone, but fatal as a
            flex sibling of the search input's own flex:1 wrapper, which collapsed to
            icon-width because this button's width:100% left it no room to grow into.
            IdsButton spreads its own `style` prop last, so an explicit width here wins
            over that default. */}
        <IdsButton type="submit" style={{ width: 'auto' }} disabled={!recipient.trim()}>{t('transfer.continue')}</IdsButton>
      </form>

      <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginTop: '12px' }}>
        <input type="checkbox" checked={isGift} onChange={(e) => setIsGift(e.target.checked)} />
        <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><GiftGlyph theme={null} size={16} /> Send as a gift instead</span>
      </label>
      {isGift && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          Held until they claim it -- auto-refunded to you after 7 days if unclaimed. Enter their
          phone number above -- gifts can't be sent to an account number.
        </p>
      )}

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '12px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>{t('transfer.recentLabel')}</p>
        <button type="button" onClick={() => setShowAddContact((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 700, background: 'none', border: 'none' }}>
          {showAddContact ? t('transfer.cancel') : t('transfer.addContact')}
        </button>
      </div>
      {showAddContact && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="text" value={newContactName} onChange={(e) => setNewContactName(e.target.value)} placeholder={t('transfer.namePlaceholder')}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" value={newContactPhone} onChange={(e) => setNewContactPhone(e.target.value)} placeholder={t('transfer.phonePlaceholder')}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="button" className="itunda-btn itunda-btn-secondary" disabled={!newContactName || !newContactPhone} onClick={handleAddContact}>
            {t('transfer.saveContact')}
          </button>
        </div>
      )}
      {contacts.length === 0 && !showAddContact && (
        <EmptyState message={t('transfer.noContacts')} />
      )}
      {contacts.map((c) => (
        <button
          type="button" key={c.id}
          onClick={() => selectRecipient(c.phoneNumber)}
          style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 0', background: 'none', border: 'none', textAlign: 'left', width: '100%' }}
        >
          <div style={{ width: '38px', height: '38px', borderRadius: '19px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-indigo)', flexShrink: 0 }}>
            {c.name.slice(0, 1).toUpperCase()}
          </div>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{c.name}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{c.bank} · {c.phoneNumber}</span>
          </div>
        </button>
      ))}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

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
                    {isCredit ? '+' : ''}{tx.amount.toLocaleString()} RWF
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
function CooperativeSavingsRail({ onNavigateToTab, onNavigateToLoansMode, onNavigateToSavingsTarget }: { onNavigateToTab: (tab: Tab) => void; onNavigateToLoansMode: (mode: LoansMode) => void; onNavigateToSavingsTarget: (target: 'sacco' | 'ikimina') => void }) {
  const { t } = useI18n();
  const items: { key: string; title: string; subtitle: string; icon: ReactElement; tint: string; tab: Tab; loansMode?: LoansMode; savingsTarget?: 'sacco' | 'ikimina' }[] = [
    { key: 'sacco', title: t('coopRail.sacco.title'), subtitle: t('coopRail.sacco.subtitle'), icon: <Landmark size={20} color="#7C5CFC" />, tint: 'rgba(124, 92, 252, 0.12)', tab: 'SAVINGS', savingsTarget: 'sacco' },
    { key: 'ikimina', title: t('coopRail.ikimina.title'), subtitle: t('coopRail.ikimina.subtitle'), icon: <Users size={20} color="#14AE85" />, tint: 'rgba(20, 174, 133, 0.12)', tab: 'SAVINGS', savingsTarget: 'ikimina' },
    // Real gap found live (2026-08-10) while checking these two links for the first
    // time: onNavigateToTab alone only lands on LoansView's generic Offers catalog --
    // its own `mode` is separate internal state. loansMode threads the real specific
    // product through (see LoansView's own initialMode doc comment).
    { key: 'moto_ownership', title: t('coopRail.motoOwnership.title'), subtitle: t('coopRail.motoOwnership.subtitle'), icon: <Bike size={20} color="var(--itunda-indigo)" />, tint: 'var(--itunda-indigo-light)', tab: 'LOANS', loansMode: 'MOTO_OWNERSHIP' },
    { key: 'harvest_advance', title: t('coopRail.harvestAdvance.title'), subtitle: t('coopRail.harvestAdvance.subtitle'), icon: <Sprout size={20} color="#F2A93B" />, tint: 'rgba(242, 169, 59, 0.14)', tab: 'LOANS', loansMode: 'HARVEST_ADVANCE' },
  ];

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, margin: 0, color: 'var(--itunda-grey-900)' }}>{t('coopRail.title')}</h3>
      <p style={{ fontSize: '12.5px', color: 'var(--itunda-grey-500)', marginTop: '2px', marginBottom: '16px' }}>{t('coopRail.subtitle')}</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {items.map((item) => (
          <button
            key={item.key}
            onClick={() => {
              recordEvent('coop_rail_tap', item.key);
              if (item.loansMode) onNavigateToLoansMode(item.loansMode);
              // Real gap found live (2026-08-10), same investigation that found the
              // loansMode gap above: SACCO/Ikimina are sections ~45-55% of the way
              // down SavingsView's long page, not a separate mode -- see SavingsView's
              // own initialScrollTarget doc comment for the measured offsets.
              if (item.savingsTarget) onNavigateToSavingsTarget(item.savingsTarget);
              onNavigateToTab(item.tab);
            }}
            style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '10px 8px', borderRadius: '10px', textAlign: 'left', width: '100%' }}
          >
            <div style={{ width: '38px', height: '38px', borderRadius: '12px', backgroundColor: item.tint, display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              {item.icon}
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: '14.5px', fontWeight: 650, color: 'var(--itunda-grey-900)' }}>{item.title}</div>
              <div style={{ fontSize: '12.5px', color: 'var(--itunda-grey-500)' }}>{item.subtitle}</div>
            </div>
          </button>
        ))}
        <button
          onClick={() => { recordEvent('coop_rail_tap', 'see_all'); onNavigateToTab('SAVINGS'); }}
          style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '10px 8px', borderRadius: '10px', textAlign: 'left', width: '100%', marginTop: '4px' }}
        >
          <span style={{ fontSize: '13.5px', fontWeight: 600, color: 'var(--itunda-indigo)' }}>{t('coopRail.seeAll')}</span>
          <IconChevronRight size={16} color="var(--itunda-indigo)" />
        </button>
      </div>
    </div>
  );
}

// Real architectural fix (2026-08-13, matching the identical Android/iOS fix same
// session, direct user directive): "all itunda product features are independent
// and isolated -- itunda bank is a complete product... tabs are not products, are
// just access points." This tab used to render the account balance, transfer flow,
// quick actions, coop-savings teaser, transaction history, scheduled/auto
// transfers, auto top-up, request-money, and the Youth account card directly --
// real Bank- and Pay-product content baked into what's meant to be a generic
// access point. AccountBalance/QuickActions/TransactionHistory/RequestMoneyCard/
// AutoTopUpCard/ScheduledTransfersCard moved into PayHub (itunda's real,
// self-contained account product); AutoTransfersCard/YouthAccountCard moved into
// SavingsView ("itunda Bank"); CooperativeSavingsRail was only ever a teaser
// linking into SavingsView's own already-complete SaccoSection/IkiminaSection, so
// it's removed outright rather than moved -- nothing it showed was unique.
function HomeView() {
  // Real, minimal usage signal (2026-08-10) -- see lib/analytics.ts's own doc comment.
  // Fired once per real mount of Home, the baseline every retention question in the
  // "itunda: the wedge, not the mirror" memo is measured against.
  useEffect(() => { recordEvent('home_view'); }, []);

  return (
    <div>
      <DiscoverSection />
    </div>
  );
}

function ProductPageHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div style={{ margin: '4px 0 16px' }}>
      <h1 style={{ margin: 0, color: 'var(--itunda-grey-900)', fontSize: 'var(--itunda-type-scale-24-size)', letterSpacing: '-0.5px' }}>{title}</h1>
      <p style={{ margin: '5px 0 0', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-13-size)', lineHeight: 1.45 }}>{subtitle}</p>
    </div>
  );
}

// The pay surface brings the real, existing money-moving flows into one predictable
// place. It does not create another payment implementation: every action below uses
// the established transfer, bill, merchant-code, and payment-intent components.
function PayHub({ onNavigateToTab, onNavigateToCard }: { onNavigateToTab: (tab: Tab) => void; onNavigateToCard: () => void }) {
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
      showToast(wasEnrolled ? "Couldn't turn off FacePay. Try again." : "Couldn't enroll in FacePay. Try again.");
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
              {Math.round(animatedPayBalance).toLocaleString()} RWF
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
            {mainAccount.balance.toLocaleString()} {mainAccount.currency}
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
      <PayHubOtherServicesRail onCardsClick={onNavigateToCard} onTransitClick={() => onNavigateToTab('TRANSIT')} onMotoFareClick={() => onNavigateToTab('MOTO_FARE_COLLECT')} onNavigateToTab={onNavigateToTab} />
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
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{((rewardsPreview.rewardsTotal ?? 0) + (account?.balance ?? 0)).toLocaleString()} RWF</span>
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
function ExploreTabPill({ id, label, icon: Icon, onSelect }: { id: Tab; label: string; icon?: ComponentType<{ size?: number }>; onSelect: (id: Tab) => void }) {
  return (
    <button
      className="itunda-btn itunda-btn-secondary"
      style={{ display: 'flex', alignItems: 'center', gap: '6px', fontSize: 'var(--itunda-type-scale-13-size)', padding: '8px 12px', borderRadius: '999px' }}
      onClick={() => onSelect(id)}
    >
      {Icon && <Icon size={16} />}
      {label}
    </button>
  );
}

function ExploreHub({ groups, tabLabel, tabIcon, recentTabs, onSelect, autoFocusSearch, onConsumedAutoFocus }: {
  groups: { title: string; ids: Tab[] }[];
  tabLabel: (id: Tab) => string;
  tabIcon?: (id: Tab) => ComponentType<{ size?: number }> | undefined;
  recentTabs: Tab[];
  onSelect: (id: Tab) => void;
  autoFocusSearch?: boolean;
  onConsumedAutoFocus?: () => void;
}) {
  const [search, setSearch] = useState('');
  const searchInputRef = useRef<HTMLInputElement | null>(null);
  const allIds = groups.flatMap((g) => g.ids);
  const matches = search.trim()
    ? allIds.filter((id) => tabLabel(id).toLowerCase().includes(search.trim().toLowerCase()))
    : [];

  // Real hand-off from the header search icon (docs/DESIGN_REFERENCES.md Section 41
  // item 3) -- focuses this same real search box the moment ExploreHub mounts from
  // that entry point, rather than making the user find it again themselves.
  useEffect(() => {
    if (autoFocusSearch) {
      searchInputRef.current?.focus();
      onConsumedAutoFocus?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoFocusSearch]);

  return (
    <div>
      <ProductPageHeader title="Explore" subtitle="Everything beyond your everyday money tasks, in one searchable place." />
      <div style={{ position: 'relative', marginBottom: '14px' }}>
        <IconSearch size={15} color="var(--itunda-grey-500)" style={{ position: 'absolute', left: '10px', top: '50%', transform: 'translateY(-50%)' }} />
        <input
          ref={searchInputRef}
          type="text"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          placeholder="Search all services"
          aria-label="Search all services"
          style={{ width: '100%', padding: '10px 32px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)', background: 'var(--itunda-white)', color: 'var(--itunda-grey-900)' }}
        />
        {search && (
          <button onClick={() => setSearch('')} aria-label="Clear search" style={{ position: 'absolute', right: '8px', top: '50%', transform: 'translateY(-50%)', color: 'var(--itunda-grey-500)', display: 'flex' }}>
            <IconClose size={15} />
          </button>
        )}
      </div>

      {search.trim() ? (
        matches.length > 0 ? (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
            {matches.map((id) => (
              <ExploreTabPill key={id} id={id} label={tabLabel(id)} icon={tabIcon?.(id)} onSelect={onSelect} />
            ))}
          </div>
        ) : (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', padding: '4px 0' }}>No match for "{search.trim()}".</p>
        )
      ) : (
        <>
          {recentTabs.length > 0 && (
            <section className="itunda-flat-section">
              <h2 style={{ margin: '0 0 10px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', textTransform: 'uppercase', letterSpacing: '0.02em', display: 'flex', alignItems: 'center', gap: '5px' }}>
                <Clock size={12} /> Recently used
              </h2>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {recentTabs.map((id) => (
                  <ExploreTabPill key={id} id={id} label={tabLabel(id)} icon={tabIcon?.(id)} onSelect={onSelect} />
                ))}
              </div>
            </section>
          )}
          {/* Real Toss reference (16 screenshots, 2026-08-12 -- see Android's identical
              MenuScreen fix, docs/DESIGN_REFERENCES.md Section 49): every category's
              items are always fully visible in the real 전체 screen, zero collapse/
              expand mechanic anywhere. This accordion (tap-to-expand, was the only real
              way to see a group's own items) was the same over-applied Hick's Law
              pattern Android's MenuScreen had before that fix -- web just never got the
              same correction until now. */}
          {groups.map((group) => (
            <section key={group.title} className="itunda-flat-section">
              <h2 style={{ margin: '0 0 12px', fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
                {group.title} <span style={{ color: 'var(--itunda-grey-400)', fontWeight: 500, fontSize: 'var(--itunda-type-scale-12-size)' }}>· {group.ids.length}</span>
              </h2>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px' }}>
                {group.ids.map((id) => (
                  <ExploreTabPill key={id} id={id} label={tabLabel(id)} icon={tabIcon?.(id)} onSelect={onSelect} />
                ))}
              </div>
            </section>
          ))}
        </>
      )}
    </div>
  );
}

// The You tab: profile up front (mirrors the native "profile icon" placement
// itunda's own Android/iOS All screens use for My), then Insights (Overview/
// Spending/Subscriptions -- flat rows, matching how Android's MenuScreen/iOS's
// EntireMenuScreen expose these) and Account & security as their own groups.
function YouHub({ onNavigateToTab }: { onNavigateToTab: (tab: Tab) => void }) {
  return (
    <div>
      <ProductPageHeader title="You" subtitle="Your profile, insights, and account security in one place." />
      <MyView />
      <div className="itunda-flat-section">
        <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-16-size)' }}>Insights</h2>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '12px' }}>
          {[
            { label: 'Overview', tab: 'OVERVIEW' as Tab },
            { label: 'Spending insights', tab: 'SPENDING' as Tab },
            { label: 'Subscriptions', tab: 'SUBSCRIPTIONS' as Tab },
          ].map((item) => (
            <button key={item.tab} className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-13-size)', padding: '8px 10px' }} onClick={() => onNavigateToTab(item.tab)}>{item.label}</button>
          ))}
        </div>
      </div>
      <div className="itunda-flat-section">
        <h2 style={{ margin: 0, fontSize: 'var(--itunda-type-scale-16-size)' }}>Account & security</h2>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '12px' }}>
          {[
            { label: 'Cards', tab: 'CARD' as Tab },
            { label: 'Devices', tab: 'DEVICES' as Tab },
            { label: 'Verify identity', tab: 'IDENTITY' as Tab },
            { label: 'Get support', tab: 'SUPPORT' as Tab },
          ].map((item) => (
            <button key={item.tab} className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-13-size)', padding: '8px 10px' }} onClick={() => onNavigateToTab(item.tab)}>{item.label}</button>
          ))}
        </div>
      </div>
    </div>
  );
}

// Real curated promo rail -- see lib/discover.ts's own doc comment. Android already has
// this (DiscoverSection in ItundaAppScreen.kt, found real on backend + Android with zero
// client anywhere else); this is the first bank-mfe/iOS client. Purely informational --
// no click-through action or money movement, mirroring Android's own honest scope.
function DiscoverSection() {
  const { t } = useI18n();
  const [items, setItems] = useState<DiscoverItem[]>([]);

  useEffect(() => {
    // Real server-side ranking (2026-08-11) -- see DiscoverItem's own doc comment.
    // Backend already returns items sorted by priority; re-sorting here just makes
    // that explicit and correct even if a future backend response ever isn't
    // pre-sorted, same defensive-but-cheap sort Android's heroDiscoverItem uses.
    fetchDiscoverItems()
      .then((fetched) => setItems([...fetched].sort((a, b) => b.priority - a.priority)))
      .catch(() => {});
  }, []);

  if (items.length === 0) return null;

  return (
    <div style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-19-size)', fontWeight: 700, marginBottom: '10px' }}>{t('discover.title')}</h3>
      {/* Real flat-design fix (docs/UI_UX_GUIDELINES.md rule 10, "Flat over
          card-heavy") -- full-app audit found Home's own Discover section was still
          boxing every row in .itunda-card, missed by the 2026-08-24 flat-design
          sweep since this section wasn't touched that pass. Rows now sit directly
          on the page with a divider between them, matching every other
          already-flattened list in this file. */}
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {items.map((item, i) => (
          <div
            key={item.id}
            style={{
              display: 'flex', alignItems: 'center', gap: '12px', padding: '12px 0',
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
            {item.badge && <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: item.color }}>{item.badge}</span>}
          </div>
        ))}
      </div>
    </div>
  );
}

// Real KakaoBank mini-style capped starter account -- see lib/youthAccount.ts's own doc
// comment. First client UI for this backend feature on any platform (item 99, found
// with zero client anywhere despite the backend being real and live since 2026-07-28).
function YouthAccountCard() {
  const { t } = useI18n();
  const [youthAccount, setYouthAccount] = useState<Account | null | undefined>(undefined);
  const [needsBirthDate, setNeedsBirthDate] = useState(false);
  const [birthDate, setBirthDateInput] = useState('');
  const [amount, setAmount] = useState('');
  const [showDeposit, setShowDeposit] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.tsx's own doc
  // comment. Unlike every other bucket, Youth is a real Account, so its history comes
  // from the existing per-account transactions endpoint via transactionsToBucketTransactions.
  const [showDetail, setShowDetail] = useState(false);

  const load = () => {
    fetchAccounts().then((accounts) => setYouthAccount(accounts.find((w) => w.type === 'MINI') ?? null)).catch(() => setYouthAccount(null));
  };

  useEffect(load, []);
  // Real Toss motion pattern -- see useCountUp's own doc comment.
  const animatedBalance = useCountUp(youthAccount?.balance ?? 0);

  const handleOpen = async () => {
    setBusy(true);
    setError(null);
    try {
      const account = await openYouthAccount();
      setYouthAccount(account);
      setNeedsBirthDate(false);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'YOUTH_ACCOUNT_BIRTH_DATE_REQUIRED') {
        setNeedsBirthDate(true);
      } else if (err instanceof ApiError && err.code === 'YOUTH_ACCOUNT_AGE_INELIGIBLE') {
        setError(t('youthAccount.ageIneligible'));
      } else {
        setError(err instanceof ApiError ? err.message : t('youthAccount.openError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleSetBirthDateAndOpen = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!birthDate) return;
    setBusy(true);
    setError(null);
    try {
      await setBirthDate(birthDate);
      await handleOpen();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('youthAccount.birthDateError'));
      setBusy(false);
    }
  };

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!(parsedAmount > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await depositToYouthAccount(parsedAmount);
      setAmount('');
      setShowDeposit(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('youthAccount.depositError'));
    } finally {
      setBusy(false);
    }
  };

  if (youthAccount === undefined) return null;

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('youthAccount.title')}</h3>
        {youthAccount && (
          <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowDeposit((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {showDeposit ? t('youthAccount.cancel') : t('youthAccount.addMoney')}
          </button>
        )}
      </div>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {!youthAccount && !needsBirthDate && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            {t('youthAccount.description')}
          </p>
          <button className="itunda-btn itunda-btn-primary" onClick={handleOpen} disabled={busy}>{busy ? t('youthAccount.opening') : t('youthAccount.open')}</button>
        </div>
      )}

      {!youthAccount && needsBirthDate && (
        <form onSubmit={handleSetBirthDateAndOpen} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('youthAccount.birthDatePrompt')}</p>
          <input
            type="date" value={birthDate} onChange={(e) => setBirthDateInput(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          {/* Real CTA-label-clarity fix (item 244, docs/DESIGN_REFERENCES.md §11): "Continue"
              doesn't say what happens next -- the paragraph above already names the real
              outcome ("check eligibility"), so the button says it too. */}
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('youthAccount.checking') : t('youthAccount.checkEligibility')}</button>
        </form>
      )}

      {youthAccount && showDetail && (
        <BucketDetailScreen
          title={t('youthAccount.title')}
          subtitle={youthAccount.accountNumber}
          balanceText={`${youthAccount.balance.toLocaleString()} RWF`}
          fetchTransactions={() => fetchAccountTransactions(youthAccount.id).then((txs) => transactionsToBucketTransactions(txs, youthAccount.id, youthAccount.balance))}
          onBack={() => setShowDetail(false)}
        />
      )}
      {youthAccount && (
        <div>
          <button onClick={() => setShowDetail(true)} style={{ textAlign: 'left', display: 'block' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700 }}>{animatedBalance.toLocaleString()} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: showDeposit ? '10px' : 0 }}>{youthAccount.accountNumber}</p>
          </button>
          {showDeposit && (
            <form onSubmit={handleDeposit} style={{ display: 'flex', gap: '8px' }}>
              <input
                type="number" placeholder={t('youthAccount.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
                style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('youthAccount.adding') : t('youthAccount.add')}</button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}

const SCHEDULED_TRANSFER_STATUS_KEY: Record<ScheduledTransfer['status'], TranslationKey> = {
  PENDING: 'scheduledTransfers.statusScheduled',
  EXECUTED: 'scheduledTransfers.statusSent',
  CANCELLED: 'scheduledTransfers.statusCancelled',
  FAILED: 'scheduledTransfers.statusFailed',
};

// Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
// lib/scheduledTransfers.ts's own doc comment. Distinct from AutoTransfer (recurring,
// which itself still has no bank-mfe client anywhere -- left as its own separately
// named, still-deferred gap; not expanded in this pass).
function ScheduledTransfersCard() {
  const { t } = useI18n();
  const [transfers, setTransfers] = useState<ScheduledTransfer[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [recipient, setRecipient] = useState('');
  const [amount, setAmount] = useState('');
  const [scheduledDate, setScheduledDate] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyScheduledTransfers().then(setTransfers).catch(() => {});
  };

  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!recipient.trim() || !(parsedAmount > 0) || !scheduledDate) return;
    setBusy(true);
    setError(null);
    try {
      await createScheduledTransfer(recipient.trim(), parsedAmount, scheduledDate, description);
      setRecipient('');
      setAmount('');
      setScheduledDate('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('scheduledTransfers.createError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelScheduledTransfer(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('scheduledTransfers.cancelError'));
    } finally {
      setBusyId(null);
    }
  };

  const pending = (transfers ?? []).filter((tr) => tr.status === 'PENDING');
  const past = (transfers ?? []).filter((tr) => tr.status !== 'PENDING');
  const minDate = new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 10);

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('scheduledTransfers.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('scheduledTransfers.cancel') : t('scheduledTransfers.schedule')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('scheduledTransfers.recipientPlaceholder')} value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder={t('scheduledTransfers.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} min={minDate} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('scheduledTransfers.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('scheduledTransfers.scheduling') : t('scheduledTransfers.scheduleButton')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {pending.length === 0 && past.length === 0 && (
        <EmptyState message={t('scheduledTransfers.noTransfers')} />
      )}

      {[...pending, ...past.slice(0, 3)].map((tr) => (
        <div key={tr.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{tr.recipientName} · {tr.amount.toLocaleString()} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{tr.scheduledDate} · {t(SCHEDULED_TRANSFER_STATUS_KEY[tr.status])}</p>
          </div>
          {tr.status === 'PENDING' && (
            <button className="itunda-btn itunda-btn-secondary" disabled={busyId === tr.id} onClick={() => handleCancel(tr.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {busyId === tr.id ? '…' : t('scheduledTransfers.cancel')}
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

const DELAYED_TRANSFER_STATUS_KEY: Record<DelayedTransfer['status'], TranslationKey> = {
  PENDING: 'delayedTransfers.statusPending', COMPLETED: 'delayedTransfers.statusCompleted', CANCELLED: 'delayedTransfers.statusCancelled',
};

function formatReleaseCountdown(releaseAt: string): string {
  const ms = new Date(releaseAt).getTime() - Date.now();
  if (ms <= 0) return '';
  const hours = Math.floor(ms / 3600000);
  const minutes = Math.floor((ms % 3600000) / 60000);
  return hours > 0 ? `${hours}h ${minutes}m` : `${minutes}m`;
}

// Real Korean 지연이체서비스 (Delayed Transfer Service) client -- see
// lib/delayedTransfers.ts's own doc comment for the full sourced account. Reuses
// resolveRecipient (lib/p2p.ts) the exact same way TransferFlow's own instant-send
// step already does, so the sender sees the real resolved account-holder name before
// committing here too -- the whole point of a "send safely" option is a real chance to
// catch a wrong recipient, so skipping that same confirmation here would defeat it.
function DelayedTransfersCard() {
  const { t } = useI18n();
  const [transfers, setTransfers] = useState<DelayedTransfer[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [recipient, setRecipient] = useState('');
  const [recipientPreview, setRecipientPreview] = useState<P2pRecipientPreview | null>(null);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real device step-up (see TransferFlow's own handleConfirm) -- send-delayed is a
  // real money-moving endpoint carrying an Idempotency-Key header, so it's gated by
  // the same DeviceVerificationFilter every other one is.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real, honest v1 limitation: the backend's delayed-transfer list has no resolved
  // recipient name (P2pDelayedTransfer only stores recipientUserId) -- names resolved
  // at send time in THIS session are remembered here for display, a transfer loaded
  // fresh from a prior session honestly falls back to a generic label instead of
  // fabricating one.
  const [knownNames, setKnownNames] = useState<Record<string, string>>({});

  const load = () => {
    fetchMyDelayedTransfers().then(setTransfers).catch(() => {});
  };

  useEffect(load, []);

  useEffect(() => {
    const trimmed = recipient.trim();
    if (!trimmed) { setRecipientPreview(null); return; }
    const handle = setTimeout(() => {
      resolveRecipient(trimmed).then(setRecipientPreview).catch(() => setRecipientPreview(null));
    }, 400);
    return () => clearTimeout(handle);
  }, [recipient]);

  const handleCreate = async (e?: React.FormEvent) => {
    e?.preventDefault();
    const parsedAmount = Number(amount);
    if (!recipient.trim() || !(parsedAmount > 0)) return;
    setBusy(true);
    setError(null);
    try {
      const transfer = await sendDelayed(recipient.trim(), parsedAmount, description);
      if (recipientPreview) {
        setKnownNames((prev) => ({ ...prev, [transfer.recipientUserId]: recipientPreview.displayName }));
      }
      setRecipient('');
      setRecipientPreview(null);
      setAmount('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      // Real device step-up retries this exact same handleCreate call once verified,
      // same pattern as TransferFlow's own handleConfirm.
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('delayedTransfers.createError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelDelayedTransfer(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('delayedTransfers.cancelError'));
    } finally {
      setBusyId(null);
    }
  };

  const pending = (transfers ?? []).filter((tr) => tr.status === 'PENDING');
  const past = (transfers ?? []).filter((tr) => tr.status !== 'PENDING');

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('delayedTransfers.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('scheduledTransfers.cancel') : t('delayedTransfers.new')}
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>{t('delayedTransfers.subtitle')}</p>

      {showCreate && needsDeviceVerification && (
        <div style={{ marginBottom: '12px' }}>
          <DeviceStepUpPrompt onVerified={() => { setNeedsDeviceVerification(false); handleCreate(); }} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      )}

      {showCreate && !needsDeviceVerification && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('scheduledTransfers.recipientPlaceholder')} value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          {recipientPreview && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>{t('delayedTransfers.sendingTo')} {recipientPreview.displayName}</p>
          )}
          <input
            type="number" placeholder={t('scheduledTransfers.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('scheduledTransfers.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('scheduledTransfers.scheduling') : t('delayedTransfers.sendButton')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {pending.length === 0 && past.length === 0 && (
        <EmptyState message={t('delayedTransfers.noTransfers')} />
      )}

      {[...pending, ...past.slice(0, 3)].map((tr) => (
        <div key={tr.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
              {knownNames[tr.recipientUserId] ?? t('delayedTransfers.recipientFallback')} · {tr.amount.toLocaleString()} RWF
            </p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
              {tr.status === 'PENDING'
                ? `${t('delayedTransfers.releasesIn')} ${formatReleaseCountdown(tr.releaseAt)}`
                : t(DELAYED_TRANSFER_STATUS_KEY[tr.status])}
            </p>
          </div>
          {tr.status === 'PENDING' && (
            <button className="itunda-btn itunda-btn-secondary" disabled={busyId === tr.id} onClick={() => handleCancel(tr.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {busyId === tr.id ? '…' : t('scheduledTransfers.cancel')}
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

const AUTO_TRANSFER_STATUS_KEY: Record<AutoTransfer['status'], TranslationKey> = {
  ACTIVE: 'autoTransfers.statusActive', PAUSED: 'autoTransfers.statusPaused', CANCELLED: 'autoTransfers.statusCancelled',
};
const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const WEEKDAY_KEYS: TranslationKey[] = ['weekday.monday', 'weekday.tuesday', 'weekday.wednesday', 'weekday.thursday', 'weekday.friday', 'weekday.saturday', 'weekday.sunday'];

// Real Toss Bank 자동이체 (auto-transfer) -- see lib/autoTransfers.ts's own doc
// comment. Recurring, genuinely distinct from ScheduledTransfersCard's own one-time
// 예약송금 above. First bank-mfe client for a backend that previously had none.
function AutoTransfersCard() {
  const { t } = useI18n();
  const [transfers, setTransfers] = useState<AutoTransfer[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [recipient, setRecipient] = useState('');
  const [amount, setAmount] = useState('');
  const [frequency, setFrequency] = useState<AutoTransferFrequency>('MONTHLY');
  const [dayOfWeek, setDayOfWeek] = useState('1');
  const [dayOfMonth, setDayOfMonth] = useState('1');
  const [description, setDescription] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyAutoTransfers().then(setTransfers).catch(() => {});
  };

  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!recipient.trim() || !(parsedAmount > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await createAutoTransfer(
        recipient.trim(), parsedAmount, frequency,
        frequency === 'WEEKLY' ? Number(dayOfWeek) : null,
        frequency === 'MONTHLY' ? Number(dayOfMonth) : null,
        description,
      );
      setRecipient('');
      setAmount('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTransfers.createError'));
    } finally {
      setBusy(false);
    }
  };

  const handleToggle = async (at: AutoTransfer) => {
    setBusyId(at.id);
    setError(null);
    try {
      if (at.status === 'ACTIVE') await pauseAutoTransfer(at.id);
      else await resumeAutoTransfer(at.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTransfers.toggleError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelAutoTransfer(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTransfers.cancelError'));
    } finally {
      setBusyId(null);
    }
  };

  const active = (transfers ?? []).filter((at) => at.status !== 'CANCELLED');
  const cancelled = (transfers ?? []).filter((at) => at.status === 'CANCELLED');

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('autoTransfers.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('autoTransfers.cancel') : t('autoTransfers.setUp')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('autoTransfers.recipientPlaceholder')} value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder={t('autoTransfers.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <select
            value={frequency} onChange={(e) => setFrequency(e.target.value as AutoTransferFrequency)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          >
            <option value="WEEKLY">{t('autoTransfers.weekly')}</option>
            <option value="MONTHLY">{t('autoTransfers.monthly')}</option>
          </select>
          {frequency === 'WEEKLY' ? (
            <select
              value={dayOfWeek} onChange={(e) => setDayOfWeek(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            >
              {WEEKDAY_NAMES.map((name, i) => <option key={name} value={i + 1}>{t(WEEKDAY_KEYS[i])}</option>)}
            </select>
          ) : (
            <select
              value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            >
              {Array.from({ length: 28 }, (_, i) => i + 1).map((d) => <option key={d} value={d}>{t('autoTransfers.dayOfMonth', { day: d })}</option>)}
            </select>
          )}
          <input
            type="text" placeholder={t('autoTransfers.descriptionPlaceholder')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('autoTransfers.settingUp') : t('autoTransfers.setUpButton')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {active.length === 0 && cancelled.length === 0 && (
        <EmptyState message={t('autoTransfers.noTransfers')} />
      )}

      {[...active, ...cancelled.slice(0, 2)].map((at) => (
        <div key={at.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{at.recipientName} · {at.amount.toLocaleString()} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
              {at.frequency === 'WEEKLY' ? t('autoTransfers.weeklyLabel', { day: t(WEEKDAY_KEYS[(at.dayOfWeek ?? 1) - 1]) }) : t('autoTransfers.monthlyLabel', { day: at.dayOfMonth ?? 1 })} · {t(AUTO_TRANSFER_STATUS_KEY[at.status])}
              {at.lastFailureReason && ` · ${at.lastFailureReason}`}
            </p>
          </div>
          {at.status !== 'CANCELLED' && (
            <div style={{ display: 'flex', gap: '6px' }}>
              <button className="itunda-btn itunda-btn-secondary" disabled={busyId === at.id} onClick={() => handleToggle(at)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                {busyId === at.id ? '…' : at.status === 'ACTIVE' ? t('autoTransfers.pause') : t('autoTransfers.resume')}
              </button>
              <button className="itunda-btn itunda-btn-secondary" disabled={busyId === at.id} onClick={() => handleCancel(at.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                {t('autoTransfers.cancelAction')}
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

const P2P_REQUEST_STATUS_KEY: Record<P2pPaymentRequestStatus, TranslationKey> = {
  PENDING: 'requestMoney.statusPending', COMPLETED: 'requestMoney.statusPaid', EXPIRED: 'requestMoney.statusExpired',
};

// Real fixed-amount person-to-person payment request (item 167) -- see lib/p2p.ts's
// own doc comment. A real 15-minute-expiring code the requester shares (typed/pasted,
// same real manual-code-entry convention MerchantController.collect's own bank-mfe
// client already established -- this app has no camera QR scanner anywhere); anyone
// who has the code can pay it directly, real account-to-account, no fee.
function RequestMoneyCard() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<P2pPaymentRequestDto[] | null>(null);
  const [showCreate, setShowCreate] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [creating, setCreating] = useState(false);
  const [created, setCreated] = useState<P2pPaymentRequestDto | null>(null);
  const [payCode, setPayCode] = useState('');
  const [paying, setPaying] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const load = () => {
    fetchMyP2pRequests().then(setRequests).catch(() => {});
  };
  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const parsedAmount = Number(amount);
    if (!(parsedAmount > 0)) return;
    setCreating(true);
    setError(null);
    try {
      const req = await generateP2pRequest(parsedAmount, description.trim());
      setCreated(req);
      setAmount('');
      setDescription('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('requestMoney.createError'));
    } finally {
      setCreating(false);
    }
  };

  const handlePay = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setPaying(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await payP2pRequest(payCode.trim());
      setPayCode('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('requestMoney.payError'));
      }
    } finally {
      setPaying(false);
    }
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('requestMoney.title')}</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? t('requestMoney.cancel') : t('requestMoney.newRequest')}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="number" placeholder={t('requestMoney.amountPlaceholder')} value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder={t('requestMoney.whatsItFor')} value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={creating}>{creating ? t('requestMoney.creating') : t('requestMoney.createButton')}</button>
        </form>
      )}

      {created && (
        <div style={{ padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px', marginBottom: '12px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('requestMoney.shareCode')}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, fontFamily: 'monospace', wordBreak: 'break-all' }}>{created.id}</p>
        </div>
      )}

      {needsDeviceVerification ? (
        // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
        // account. handlePay resets needsDeviceVerification itself.
        <DeviceStepUpPrompt onVerified={() => handlePay()} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <form onSubmit={handlePay} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder={t('requestMoney.payCodePlaceholder')} value={payCode} onChange={(e) => setPayCode(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={paying} style={{ padding: '10px 16px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {paying ? t('requestMoney.paying') : t('requestMoney.pay')}
          </button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {requests !== null && requests.length > 0 && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>{t('requestMoney.myRequests')}</p>
          {requests.slice(0, 5).map((r) => (
            <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{r.amount.toLocaleString()} RWF{r.description ? ` · ${r.description}` : ''}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{t(P2P_REQUEST_STATUS_KEY[r.status])}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168) -- see
// lib/account.ts's own doc comment.
function AutoTopUpCard({ accountId }: { accountId: string }) {
  const { t } = useI18n();
  const [setting, setSetting] = useState<AutoTopUpSetting | null | undefined>(undefined);
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [showForm, setShowForm] = useState(false);
  const [linkedAccountId, setLinkedAccountId] = useState('');
  const [thresholdAmount, setThresholdAmount] = useState('');
  const [topUpAmount, setTopUpAmount] = useState('');
  const [dailyTriggerCap, setDailyTriggerCap] = useState('3');
  const [busy, setBusy] = useState(false);
  const [triggering, setTriggering] = useState(false);
  const [triggerResult, setTriggerResult] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchAutoTopUpSetting(accountId)
      .then(setSetting)
      .catch(() => setSetting(null));
    fetchLinkedAccounts().then((accounts) => setLinkedAccounts(accounts.filter((a) => a.status === 'LINKED'))).catch(() => {});
  };
  useEffect(load, [accountId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const threshold = Number(thresholdAmount);
    const topUp = Number(topUpAmount);
    if (!linkedAccountId || !(threshold >= 0) || !(topUp > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(accountId, linkedAccountId, threshold, topUp, Number(dailyTriggerCap) || 3, true);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTopUp.saveError'));
    } finally {
      setBusy(false);
    }
  };

  const handleToggle = async () => {
    if (!setting) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(accountId, setting.linkedAccountId, setting.thresholdAmount, setting.topUpAmount, setting.dailyTriggerCap, !setting.enabled);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('autoTopUp.saveError'));
    } finally {
      setBusy(false);
    }
  };

  const handleTrigger = async () => {
    setTriggering(true);
    setTriggerResult(null);
    try {
      const r = await triggerAutoTopUp(accountId);
      setTriggerResult(r.reason);
      load();
    } catch (err) {
      setTriggerResult(err instanceof ApiError ? err.message : t('autoTopUp.checkError'));
    } finally {
      setTriggering(false);
    }
  };

  if (setting === undefined) return null;

  return (
    <div style={{ marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{t('autoTopUp.title')}</h3>
        {linkedAccounts.length > 0 && (
          <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowForm((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {showForm ? t('autoTopUp.cancel') : setting ? t('autoTopUp.edit') : t('autoTopUp.setUp')}
          </button>
        )}
      </div>

      {linkedAccounts.length === 0 && !setting && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('autoTopUp.linkFirst')}</p>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <select value={linkedAccountId} onChange={(e) => setLinkedAccountId(e.target.value)} required style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <option value="">{t('autoTopUp.selectAccount')}</option>
            {linkedAccounts.map((a) => <option key={a.id} value={a.id}>{a.provider} · {a.externalAccountNumberMasked}</option>)}
          </select>
          <input type="number" placeholder={t('autoTopUp.thresholdPlaceholder')} value={thresholdAmount} onChange={(e) => setThresholdAmount(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <input type="number" placeholder={t('autoTopUp.topUpPlaceholder')} value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <input type="number" placeholder={t('autoTopUp.maxPerDay')} value={dailyTriggerCap} onChange={(e) => setDailyTriggerCap(e.target.value)} min="1"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }} />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? t('autoTopUp.saving') : t('autoTopUp.save')}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {setting && !showForm && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {t('autoTopUp.summaryLine', { state: setting.enabled ? t('autoTopUp.on') : t('autoTopUp.off'), topUp: setting.topUpAmount.toLocaleString(), threshold: setting.thresholdAmount.toLocaleString() })}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
            {t('autoTopUp.upToPerDay', { cap: setting.dailyTriggerCap, count: setting.triggersToday })}
          </p>
          {triggerResult && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginBottom: '8px' }}>{triggerResult}</p>}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleToggle} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {setting.enabled ? t('autoTopUp.turnOff') : t('autoTopUp.turnOn')}
            </button>
            <button className="itunda-btn itunda-btn-secondary" disabled={triggering} onClick={handleTrigger} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
              {triggering ? t('autoTopUp.checking') : t('autoTopUp.checkNow')}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

// Real Coupang 정기배송 (subscribe & save) -- see lib/productSubscriptions.ts's own doc
// comment for the full sourced account. A minimal delivery-address prompt rather than a
// full address form, matching this pass's compact-card scope.
export function SubscribeAndSaveButton({ merchantId, productId }: { merchantId: string; productId: string }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubscribe = async () => {
    const address = window.prompt('Delivery address for this recurring order');
    if (!address) return;
    setBusy(true);
    setError(null);
    try {
      await subscribeToProduct(merchantId, productId, 1, 30, address);
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', textAlign: 'center' }}>Subscribed -- 5% off every delivery, every 30 days.</p>;
  }

  return (
    <div style={{ textAlign: 'center' }}>
      <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSubscribe} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 14px' }}>
        {busy ? 'Setting up…' : 'Subscribe & save 5% (every 30 days)'}
      </button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Face Pay enroll/revoke toggle -- see lib/facepay.ts's doc comment for the full
// Real fix (2026-08-25, direct user follow-up: "why do we have pay in there?"):
// this card was only ever rendered from ShoppingView (the Shop tab), which just
// got its whole Pay-a-merchant block removed for the same reason. It's now a true
// dead duplicate, not a real gap -- the Pay tab's own real Face Pay toggle is
// FacePayStatusRow (PayHomeExtras.tsx), already wired into PayHub above.

function couponDiscountLabel(c: MerchantCouponView['coupon']) {
  return c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString()} RWF off`;
}

// Real correction (2026-08-19, same session as QrScanCamera above): a QR code only
// works between two people physically in front of each other -- someone can't point
// their camera at a code that's on their OWN phone screen. QR-scanning is genuinely
// right for the payment cards above (paying a merchant you're standing in front of),
// but Open Chat/Group Eats "join" invites are normally sent to a friend who ISN'T in
// the room, over itunda talk or any other messenger -- exactly Kakao's own real invite
// pattern (a tap-to-join link sent in chat, not a QR held up to a camera). Reuses the
// existing `?tab=` deep-link convention (see readTabFromUrl's own doc comment) so the
// link both switches to the right tab AND carries the join code; the joining screen's
// own mount effect below strips the param and completes the join automatically.
export function buildJoinUrl(tab: Tab, param: string, code: string): string {
  const url = new URL(window.location.href);
  url.search = '';
  url.searchParams.set(TAB_QUERY_PARAM, tab);
  url.searchParams.set(param, code);
  return url.toString();
}

export async function shareOrCopyLink(url: string, title: string, text: string): Promise<'shared' | 'copied' | 'failed'> {
  if (navigator.share) {
    try {
      await navigator.share({ title, text, url });
      return 'shared';
    } catch {
      // User cancelled the native share sheet, or it's unsupported for this payload --
      // fall through to clipboard rather than treating cancel as an error.
    }
  }
  try {
    await navigator.clipboard.writeText(url);
    return 'copied';
  } catch {
    return 'failed';
  }
}

// Real customer-presented payment code (Pay-parity port, §238, corrected §240) --
// see lib/shopping.ts's own generateCustomerPaymentCode doc comment for the full
// sourced contract. §238's first pass was QR-only, based on secondhand reasoning
// about what KakaoPay "probably" shows -- the user then had us fetch KakaoPay's own
// real App Store screenshots directly (apps.apple.com), and separately sent 3 real
// screenshots of their own actual KakaoPay app. Both real, first-party sources
// agree on a materially different real design than §238 built:
// - The primary code is a real linear BARCODE (Code128, Korea's real 바코드결제
//   standard, works with plain laser POS scanners), with a small QR secondary next
//   to it -- not a big centered QR alone.
// - The real card also shows the funding account and a real "nearby benefits" row
//   (real nearby merchant discounts+distance) -- §238 deliberately dropped both as
//   "supplementary," but the real reference confirms they're part of the actual
//   core screen, not optional extras.
// - A real "포인트 사용" (use points) toggle exists in the reference, but itunda has
//   no separate points balance (cashback credits straight to the account, same
//   honest scope-down Android's own original MyPaymentCodeCard already
//   established) -- deliberately still not faked here.
// - The real screen's bottom card row mixes account + real linked bank cards +
//   Samsung-Pay NFC + membership -- itunda has no NFC/membership equivalent, so
//   Android's own account-only carousel was already an honest, deliberate
//   simplification of that row, not an inaccuracy -- kept as-is when this pass
//   restores everything else.
function MyPaymentCodeCard({ accounts, onOpenCard }: { accounts: Account[]; onOpenCard: () => void }) {
  const [revealed, setRevealed] = useState(false);
  const [code, setCode] = useState<CustomerPaymentCode | null>(null);
  const [barcodeDataUrl, setBarcodeDataUrl] = useState<string | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);
  const [linkedAccount, setLinkedAccount] = useState<LinkedAccount | null>(null);
  const [nearbyAds, setNearbyAds] = useState<NearbyMerchantAd[]>([]);
  // Real swipeable funding-source selection (§240) -- defaults to the real itunda Pay
  // account (§257: this code always pays out of Pay money unless the customer
  // explicitly swipes to a different account), MAIN kept only as a defensive fallback.
  const [selectedAccountId, setSelectedAccountId] = useState<string | null>(null);
  // Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
  // "Facepay · QR Payment" Recent/Account/Card picker sheet) -- additive to the
  // existing AccountCardCarousel swipe below, not a replacement (see
  // PayFundingSourcePicker.tsx's own doc comment for why).
  const [showFundingPicker, setShowFundingPicker] = useState(false);
  const myPhoneNumber = getStoredUser()?.phoneNumber ?? '';
  const account = accounts.find((w) => w.id === selectedAccountId) ?? accounts.find((w) => w.type === 'PAY') ?? accounts.find((w) => w.type === 'MAIN') ?? accounts[0] ?? null;

  // Real auto-refresh shortly before the code's own real 2-minute expiry, matching
  // Android's identical MyPaymentCodeCard -- a customer standing at a register
  // should never have the code silently go stale mid-checkout.
  useEffect(() => {
    if (!revealed) return;
    let cancelled = false;
    let timeoutId: ReturnType<typeof setTimeout>;

    const refresh = () => {
      generateCustomerPaymentCode(account?.id)
        .then((result) => {
          if (cancelled) return;
          setCode(result);
          setError(null);
          const expiresInMs = new Date(result.expiresAt).getTime() - Date.now();
          timeoutId = setTimeout(refresh, Math.max(expiresInMs - 10_000, 5_000));
        })
        .catch(() => { if (!cancelled) setError('Could not load your payment code.'); });
    };
    refresh();

    return () => { cancelled = true; clearTimeout(timeoutId); };
    // Re-fetches against the newly-selected account if `account` changes while
    // already revealed, same real "don't silently keep charging the old account"
    // discipline Android's own LaunchedEffect(revealed, selectedAccount?.id) key
    // establishes.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [revealed, account?.id]);

  useEffect(() => {
    if (!code) { setBarcodeDataUrl(null); setQrDataUrl(null); return; }
    // Real contract: both the barcode and QR encode the RAW code, no
    // itunda://... URL wrapping -- see lib/shopping.ts's own doc comment for why
    // (a merchant's real camera scanner passes the decoded string straight
    // through as the code).
    const canvas = document.createElement('canvas');
    try {
      JsBarcode(canvas, code.code, { format: 'CODE128', displayValue: false, margin: 0, height: 60, width: 2 });
      setBarcodeDataUrl(canvas.toDataURL());
    } catch {
      setBarcodeDataUrl(null);
    }
    QRCode.toDataURL(code.code, { width: 140, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
  }, [code]);

  useEffect(() => {
    if (!code) return;
    const tick = () => setSecondsLeft(Math.max(Math.round((new Date(code.expiresAt).getTime() - Date.now()) / 1000), 0));
    tick();
    const interval = setInterval(tick, 1000);
    return () => clearInterval(interval);
  }, [code]);

  // Real funding account -- same data AutoTopUpScreen/OverviewScreen already
  // fetch, read-only display here (matches the real reference's "충전계좌" row).
  useEffect(() => {
    fetchLinkedAccounts()
      .then((accounts) => setLinkedAccount(accounts.find((a) => a.status === 'LINKED') ?? null))
      .catch(() => {});
  }, []);

  // Real 당근(Karrot)-style radius-targeted nearby merchant benefits (matches the
  // real reference's own "주변 혜택" row) -- silent when location is denied or
  // nothing is nearby, same as every other nearby() caller in this codebase.
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => { fetchNearbyAds(position.coords.latitude, position.coords.longitude).then(setNearbyAds).catch(() => {}); },
      () => {},
      { enableHighAccuracy: true, timeout: 10000 },
    );
  }, []);

  return (
    <div className="itunda-card" style={{ padding: '20px', marginBottom: '16px' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', minHeight: '140px', borderRadius: '14px', backgroundColor: 'var(--itunda-grey-100)' }}>
        {!revealed ? (
          // Real reveal gate (matching Android's identical 2026-08-13 fix, itself a
          // direct response to real KakaoPay research: the code screen requires an
          // explicit tap before showing the real barcode/QR, not an unprotected
          // display -- protects a customer whose unlocked phone someone else picks
          // up from having their payment code immediately visible).
          <button
            onClick={() => setRevealed(true)}
            style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '14px', padding: '20px', width: '100%' }}
          >
            <div style={{ width: '56px', height: '56px', borderRadius: '999px', backgroundColor: 'var(--itunda-white)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              {/* Real fix (2026-08-24): was a raw Lucide Lock icon, itunda already
                  has its own real LockGlyph (itundaface) for this exact security
                  concept -- routes to it instead of a second, visually different
                  lock icon existing side by side. */}
              <LockGlyph size={24} />
            </div>
            <div>
              <p style={{ margin: 0, fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-900)' }}>Your payment code is hidden</p>
              <p style={{ margin: '2px 0 0', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>Protects you if someone else has your phone</p>
            </div>
            <span style={{ padding: '10px 28px', borderRadius: '999px', backgroundColor: 'var(--itunda-indigo)', color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>
              Tap to show
            </span>
          </button>
        ) : code && barcodeDataUrl && qrDataUrl ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', width: '100%', padding: '16px' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <img src={barcodeDataUrl} alt="Your payment barcode" style={{ flex: '1 1 0%', minWidth: 0, width: '100%', height: '60px', objectFit: 'contain', backgroundColor: 'var(--itunda-white)', borderRadius: '6px', padding: '4px' }} />
              <img src={qrDataUrl} alt="Your payment QR code" width={56} height={56} style={{ borderRadius: '6px', backgroundColor: 'var(--itunda-white)', padding: '3px' }} />
            </div>
            <p style={{ margin: 0, textAlign: 'center', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)' }}>
              {secondsLeft > 0 ? `Refreshes in ${secondsLeft}s` : 'Refreshing…'}
            </p>
          </div>
        ) : error ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        ) : (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        )}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: '10px' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-600)' }}>
          {/* Real fold-in (2026-08-28) of what used to be a separate, read-only
              "Funding account" row -- now folded into the picker sheet's own
              Account tab, matching the real reference's single funding-source
              entry point instead of two separate real UI affordances. */}
          {account ? (account.type === 'PAY' ? 'itunda Pay' : account.type === 'MAIN' ? 'itunda Bank' : `itunda Pay ${account.currency}`) : linkedAccount?.provider}
        </span>
        <button onClick={() => setShowFundingPicker(true)} style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>
          Change
        </button>
      </div>
      {showFundingPicker && (
        <PayFundingSourcePicker
          accounts={accounts}
          selectedAccountId={account?.id ?? null}
          onSelectAccount={setSelectedAccountId}
          onClose={() => setShowFundingPicker(false)}
          onOpenCard={onOpenCard}
          myPhoneNumber={myPhoneNumber}
        />
      )}
      {nearbyAds.length > 0 && (
        <div style={{ marginTop: '16px' }}>
          <p style={{ margin: '0 0 10px', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>Nearby benefits</p>
          <div style={{ display: 'flex', gap: '16px', overflowX: 'auto' }}>
            {nearbyAds.map((nearbyAd) => (
              <div key={nearbyAd.ad.id} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '64px', flexShrink: 0 }}>
                <div style={{ width: '40px', height: '40px', borderRadius: '999px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontWeight: 700, color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-16-size)' }}>
                  {nearbyAd.businessName.charAt(0).toUpperCase()}
                </div>
                <p style={{ margin: '4px 0 0', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-800)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', width: '100%', textAlign: 'center' }}>{nearbyAd.businessName}</p>
                <p style={{ margin: 0, fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-600)' }}>{Math.round(nearbyAd.distanceKm * 1000)}m</p>
              </div>
            ))}
          </div>
        </div>
      )}
      {accounts.length > 1 && (
        <AccountCardCarousel accounts={accounts} selectedAccountId={account?.id ?? null} onSelect={setSelectedAccountId} />
      )}
    </div>
  );
}

// Real swipeable funding-source cards -- see MyPaymentCodeCard's own doc comment
// for why this stays a deliberate, honest simplification of the real reference's
// mixed account/card/membership row (itunda has no Samsung-Pay NFC or membership
// equivalent to include honestly). Settling on a card is a real selection: it's
// the accountId MyPaymentCodeCard's own code is generated against, matching the
// real "swipe to choose what you pay with" KakaoPay behavior. CSS scroll-snap is
// the web equivalent of Android's HorizontalPager -- no extra dependency needed.
function AccountCardCarousel({ accounts, selectedAccountId, onSelect }: { accounts: Account[]; selectedAccountId: string | null; onSelect: (id: string) => void }) {
  const containerRef = useRef<HTMLDivElement>(null);

  const handleScroll = () => {
    const el = containerRef.current;
    if (!el || accounts.length === 0) return;
    const cardWidth = el.scrollWidth / accounts.length;
    const index = Math.min(Math.round(el.scrollLeft / cardWidth), accounts.length - 1);
    const w = accounts[index];
    if (w && w.id !== selectedAccountId) onSelect(w.id);
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <div
        ref={containerRef}
        onScroll={handleScroll}
        style={{ display: 'flex', gap: '12px', overflowX: 'auto', scrollSnapType: 'x mandatory', paddingBottom: '4px' }}
      >
        {accounts.map((w) => (
          <div
            key={w.id}
            style={{
              scrollSnapAlign: 'center', flexShrink: 0, width: '220px', height: '139px', borderRadius: '16px', position: 'relative', overflow: 'hidden',
              background: `linear-gradient(135deg, ${accountCardColor(w.currency)} 0%, ${accountCardColor(w.currency)} 60%, rgba(0,0,0,0.18) 100%)`,
              padding: '16px 18px', display: 'flex', flexDirection: 'column', justifyContent: 'space-between',
              boxShadow: '0 10px 20px -10px rgba(0,0,0,0.4)',
            }}
          >
            {/* Diagonal sheen, painted first so it sits behind the chip/wordmark/
                balance -- the same "flat color read as a card" fix (2026-08-26,
                direct user instruction: "all cards designs should resemble real
                card") applied to every card-shaped visual in this file. */}
            <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(115deg, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 40%)' }} />
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <BankCardChip size={30} />
              <CardContactlessGlyph size={18} />
            </div>
            <div>
              <p style={{ margin: 0, color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>{w.type === 'PAY' ? 'itunda Pay' : w.type === 'MAIN' ? 'itunda Bank' : `itunda Pay ${w.currency}`}</p>
              <p style={{ margin: '2px 0 0', color: 'var(--itunda-white)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-19-size)' }}>
                {w.currency} {w.currency === 'RWF' ? w.availableBalance.toLocaleString() : w.availableBalance.toFixed(2)}
              </p>
            </div>
          </div>
        ))}
      </div>
      <div style={{ display: 'flex', justifyContent: 'center', gap: '6px', marginTop: '10px' }}>
        {accounts.map((w) => (
          <div
            key={w.id}
            style={{
              width: w.id === selectedAccountId ? '8px' : '6px', height: w.id === selectedAccountId ? '8px' : '6px',
              borderRadius: '999px', backgroundColor: w.id === selectedAccountId ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)',
            }}
          />
        ))}
      </div>
    </div>
  );
}

function accountCardColor(currency: string): string {
  switch (currency) {
    case 'RWF': return '#2272EB';
    case 'USD': return '#04C065';
    case 'EUR': return '#7C5CFC';
    case 'GBP': return '#00898A';
    default: return 'var(--itunda-grey-700)';
  }
}

export function readAndClearUrlParam(key: string): string | null {
  const params = new URLSearchParams(window.location.search);
  const value = params.get(key);
  if (value) {
    params.delete(key);
    const next = params.toString();
    window.history.replaceState(null, '', `${window.location.pathname}${next ? `?${next}` : ''}`);
  }
  return value;
}

function PayByCodeCard({ onPaid, facePayEnrolled }: { onPaid: (result: CollectPaymentResult) => void; facePayEnrolled: boolean }) {
  const { t } = useI18n();
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real device binding (2026-07-20) -- found while wiring Face Pay into this card:
  // like TransferFlow, a code payment carries a real Idempotency-Key and can real-403
  // with DEVICE_NOT_VERIFIED on a device's first money-moving action, but this card
  // never handled it -- it just showed the raw error string with no actionable next
  // step. Same fix as TransferFlow/Savings: a real step-up prompt, not a dead end.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  // Real coupon-apply-at-payment (item 149) -- see lib/shopping.ts's own doc comment
  // on previewPaymentIntent. Only reachable on the non-Face-Pay path: FacePayService's
  // own collect() has no couponId param at all (a real, separate, smaller gap), so
  // Face Pay stays a direct one-step pay exactly as before.
  const [preview, setPreview] = useState<PaymentIntentPreview | null>(null);
  const [eligibleCoupons, setEligibleCoupons] = useState<MerchantCouponView[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<string | null>(null);
  // Real Toss Place-style loyalty balance check (see lib/shopping.ts's own doc
  // comment on fetchLoyaltyBalance) -- shown alongside the coupon picker in this same
  // pre-payment preview step, since it's the identical real moment a customer would
  // want to know before choosing to redeem.
  const [loyaltyBalance, setLoyaltyBalance] = useState(0);
  const [redeemPoints, setRedeemPoints] = useState(false);

  // Real camera-scan support (2026-08-19): scanning sets `code` state AND passes the
  // scanned value straight through as an explicit param, since a scan's payDirect/
  // preview call happens in the same tick as setCode and can't rely on the (still stale)
  // `code` closure -- the typed-code path still reads from state via handleConfirm below,
  // which only ever runs after a real render (the preview step) so state is fresh there.
  //
  // Real bug fix (product-feel audit, §234): `manualEntry` used to be gated behind
  // `!facePayEnrolled` everywhere it was checked, so a Face-Pay-enrolled user could
  // never reach the camera at all -- typed-code entry was their ONLY path, a direct
  // violation of the standing "no manual codes" law (see
  // [[feedback_no_manual_codes_ux]]: scan/QR beats typing whenever both exist).
  // Face Pay only changes how the payment is AUTHORIZED after a code is found (face
  // vs. nothing extra), not how the code itself is captured -- scanning and Face Pay
  // are orthogonal, so `manualEntry` alone (not `facePayEnrolled`) now decides which
  // capture mode renders.
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualEntry, setManualEntry] = useState(false);

  const payDirect = async (rawCode: string, couponId?: string, pointsToRedeem?: number) => {
    setNeedsDeviceVerification(false);
    setSubmitting(true);
    try {
      const result = facePayEnrolled ? await collectWithFacePay(rawCode) : await collectPayment(rawCode, couponId, pointsToRedeem);
      onPaid(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  const submitCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setCode(trimmed);
    setError(null);
    if (facePayEnrolled) {
      await payDirect(trimmed);
      return;
    }
    setSubmitting(true);
    try {
      const r = await previewPaymentIntent(trimmed);
      const eligible = r.coupons.filter((c) => c.eligible && !c.alreadyRedeemed);
      const balance = await fetchLoyaltyBalance(r.merchantId).catch(() => 0);
      if (eligible.length === 0 && balance <= 0) {
        await payDirect(trimmed);
      } else {
        setPreview(r);
        setEligibleCoupons(eligible);
        setLoyaltyBalance(balance);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setSubmitting(false);
    }
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    void submitCode(code);
  };

  const handleScan = (raw: string) => void submitCode(parseQrParam(raw, 'intentId'));

  // Real "naming complex conditions" / "simplify ternary operators" fix (2026-08-19) --
  // Toss's own real Frontend Fundamentals guide (frontend-fundamentals.com/code-quality/
  // code), directly requested via "deep search... make sure we don't have that weird vibe
  // coded code and architecture mistakes." This used to be a nested ternary crossing
  // `submitting`/`facePayEnrolled` inline in the render -- readable as 4 unnamed branches
  // is exactly what that guide calls out; an if-chain (its own suggested refactor target)
  // reads top-to-bottom instead of requiring the reader to track two crossed booleans.
  let payButtonLabel: string;
  if (submitting && facePayEnrolled) payButtonLabel = 'Authorizing…';
  else if (submitting) payButtonLabel = 'Paying…';
  else if (facePayEnrolled) payButtonLabel = '😊 Pay';
  else payButtonLabel = 'Pay';

  // Real cap: MerchantLoyaltyPointsService.validateAndComputeRedemption's own real
  // rule is min(pointsToRedeem, paymentAmount) -- mirrored here so the client sends
  // exactly what the backend would actually apply, not an inflated request.
  const redeemableAmount = Math.min(loyaltyBalance, preview?.amount ?? 0);
  const handleConfirm = () => payDirect(code.trim(), selectedCouponId ?? undefined, redeemPoints ? redeemableAmount : undefined);

  const handleCancel = () => {
    setPreview(null);
    setEligibleCoupons([]);
    setSelectedCouponId(null);
    setLoyaltyBalance(0);
    setRedeemPoints(false);
    setError(null);
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{manualEntry ? 'Pay by code' : 'Scan to pay'}</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        {manualEntry
          ? (facePayEnrolled ? 'Enter the code the merchant shows you to authorize with your face.' : 'Enter the payment code the merchant shows you.')
          : (facePayEnrolled ? 'Point your camera at the merchant\'s QR code — you\'ll confirm with your face.' : 'Point your camera at the merchant\'s QR code to pay instantly and earn cashback.')}
      </p>
      {needsDeviceVerification ? (
        // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
        // account. handleConfirm -> payDirect resets needsDeviceVerification itself.
        <DeviceStepUpPrompt onVerified={handleConfirm} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : !manualEntry && !preview ? (
        <>
          {!scanUnavailable && !submitting && (
            <QrScanCamera onDetect={handleScan} onUnavailable={() => setScanUnavailable(true)} />
          )}
          {submitting && (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>Looking up code…</p>
          )}
          <button
            type="button"
            className="itunda-btn itunda-btn-secondary"
            style={{ width: '100%' }}
            onClick={() => setManualEntry(true)}
          >
            {scanUnavailable ? 'Enter code manually' : 'No camera? Enter code instead'}
          </button>
        </>
      ) : preview ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{preview.businessName}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700 }}>{preview.amount.toLocaleString()} RWF</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-grey-700)' }}>Apply a coupon?</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <input type="radio" name="coupon" checked={selectedCouponId === null} onChange={() => setSelectedCouponId(null)} />
              No coupon
            </label>
            {eligibleCoupons.map((c) => (
              <label key={c.coupon.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                <input type="radio" name="coupon" checked={selectedCouponId === c.coupon.id} onChange={() => setSelectedCouponId(c.coupon.id)} />
                {c.coupon.title} — {couponDiscountLabel(c.coupon)}
              </label>
            ))}
          </div>
          {loyaltyBalance > 0 && (
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <input type="checkbox" checked={redeemPoints} onChange={(e) => setRedeemPoints(e.target.checked)} />
              Use my {loyaltyBalance.toLocaleString()} points ({redeemableAmount.toLocaleString()} RWF off)
            </label>
          )}
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
          <div style={{ display: 'flex', gap: '10px' }}>
            <IdsButton fullWidth style={{ flex: 1 }} disabled={submitting} onClick={handleConfirm}>
              {submitting ? 'Paying…' : 'Pay'}
            </IdsButton>
            <IdsButton variant="tinted" onClick={handleCancel} disabled={submitting}>Cancel</IdsButton>
          </div>
        </div>
      ) : (
        <>
          <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
            <input
              type="text"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="Payment code"
              required
              autoFocus
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
              {payButtonLabel}
            </button>
          </form>
          {!scanUnavailable && (
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              style={{ width: '100%', marginTop: '10px' }}
              onClick={() => setManualEntry(false)}
            >
              Scan a QR code instead
            </button>
          )}
        </>
      )}
      {error && !preview && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real Kakao Pay 정액 QR (static/fixed merchant QR) -- see lib/shopping.ts's own
// payByStaticQr doc comment. Genuinely distinct from PayByCodeCard above: that pays a
// merchant-preset amount off a fresh per-sale code; this pays a merchant's own
// permanent merchantId with the CUSTOMER choosing the amount, matching Kakao's own real
// small-vendor use case (a market stall's one printed, unchanging code).
function PayByStaticQrCard({ onPaid }: { onPaid: (result: CollectPaymentResult) => void }) {
  const { t } = useI18n();
  const [merchantId, setMerchantId] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualEntry, setManualEntry] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numericAmount = Number(amount);
    if (!merchantId.trim()) { setError('Scan or enter the merchant\'s code first.'); return; }
    if (!numericAmount || numericAmount <= 0) { setError('Enter a valid amount.'); return; }
    setSubmitting(true);
    try {
      const result = await payByStaticQr(merchantId.trim(), numericAmount);
      onPaid(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleScan = (raw: string) => {
    setMerchantId(parseQrParam(raw, 'merchantId'));
    setManualEntry(true);
  };

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Pay a merchant's static QR</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        For a merchant with one permanent code (like a market stall) -- scan their code, then say how much you're paying.
      </p>
      {!manualEntry && !merchantId ? (
        <>
          {!scanUnavailable && <QrScanCamera onDetect={handleScan} onUnavailable={() => setScanUnavailable(true)} />}
          <button
            type="button"
            className="itunda-btn itunda-btn-secondary"
            style={{ width: '100%' }}
            onClick={() => setManualEntry(true)}
          >
            {scanUnavailable ? 'Enter merchant ID manually' : 'No camera? Enter merchant ID instead'}
          </button>
        </>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <input
            type="text" value={merchantId} onChange={(e) => setMerchantId(e.target.value)} placeholder="Merchant ID" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required autoFocus
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>{submitting ? 'Paying…' : 'Pay'}</button>
          </div>
          {!scanUnavailable && (
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              onClick={() => { setManualEntry(false); setMerchantId(''); }}
            >
              Scan a QR code instead
            </button>
          )}
        </form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
    </div>
  );
}

function PaymentConfirmation({ result, onDone }: { result: CollectPaymentResult; onDone: () => void }) {
  return (
    <div style={{ textAlign: 'center', padding: '28px 0' }}>
      <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
      <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Paid {result.merchantName}</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{result.amount.toLocaleString()} RWF</p>
      {result.channel === 'FACE_PAY' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>😊 Authorized with Face Pay</p>
      )}
      {result.cashbackEarned > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-green)', marginBottom: '16px' }}>
          +{result.cashbackEarned.toLocaleString()} RWF cashback earned
        </p>
      )}
      <button className="itunda-btn itunda-btn-secondary" onClick={onDone} style={{ marginTop: '8px' }}>Done</button>
    </div>
  );
}

// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-tab
// module (Marketplace/Community/Jobs/Property), same "one small component, four real
// call sites" shape this project already uses for offer bubbles etc. See
// lib/neighborhood.ts's own doc comment for the full backend account.
// Real second neighborhood (2026-08-04) -- isSecond mirrors Android HoodShared.kt's own
// NeighborhoodSetupPrompt(isSecond) and iOS's own isSecond port exactly, same copy.
export function NeighborhoodSetupPrompt({ isSecond = false, onDone }: { isSecond?: boolean; onDone: (neighborhood: string) => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleShare = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setBusy(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        (isSecond ? setSecondNeighborhood(position.coords.latitude, position.coords.longitude) : setNeighborhood(position.coords.latitude, position.coords.longitude))
          .then((user) => {
            setBusy(false);
            const value = isSecond ? (user as { secondNeighborhood: string | null }).secondNeighborhood : (user as { neighborhood: string | null }).neighborhood;
            if (value) onDone(value);
          })
          .catch((err) => {
            setBusy(false);
            setError(err instanceof ApiError ? err.message : t('common.actionError'));
          });
      },
      () => {
        setBusy(false);
        setError('Could not get your real location. Check your browser permissions.');
      },
    );
  };

  return (
    <div className="itunda-flat-section" style={{ textAlign: 'center' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>{isSecond ? 'Add a second neighborhood' : 'Set your neighborhood'}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        {isSecond ? "Share a second real place -- like work -- to see what's happening there too." : "Share your real location once to see what's happening near you."}
      </p>
      <button className="itunda-btn itunda-btn-primary" onClick={handleShare} disabled={busy}>
        {busy ? 'Finding your neighborhood…' : '📍 Share my location'}
      </button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real dual-neighborhood add/change/remove row (2026-08-04) -- mirrors Android
// SuperAppTabs.kt's HoodTab showNeighborhoodPrompt second-neighborhood card and iOS's
// own NeighborhoodSwitcherOverlay exactly. Shown alongside the primary
// NeighborhoodSetupPrompt in every Hood-tab module's NEIGHBORHOOD view.
export function NeighborhoodSwitcherRow({
  secondNeighborhoodName,
  onAddTapped,
  onRemoved,
}: {
  secondNeighborhoodName: string | null;
  onAddTapped: () => void;
  onRemoved: (neighborhood: string | null) => void;
}) {
  const [removing, setRemoving] = useState(false);

  const handleRemove = () => {
    setRemoving(true);
    clearSecondNeighborhood()
      .then((user) => onRemoved(user.secondNeighborhood))
      .catch(() => {
        // Best-effort -- the row stays as-is so the user can retry.
      })
      .finally(() => setRemoving(false));
  };

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
      <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{secondNeighborhoodName ? `Second: ${secondNeighborhoodName}` : 'Add a second neighborhood'}</span>
      <div style={{ display: 'flex', gap: '12px' }}>
        <button style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-indigo)' }} onClick={onAddTapped}>
          {secondNeighborhoodName ? 'Change' : 'Add'}
        </button>
        {secondNeighborhoodName && (
          <button style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-red)' }} onClick={handleRemove} disabled={removing}>
            Remove
          </button>
        )}
      </div>
    </div>
  );
}

export function nextInChain<T>(chain: T[], current: T): T | null {
  const idx = chain.indexOf(current);
  return idx >= 0 && idx + 1 < chain.length ? chain[idx + 1] : null;
}

export function StarRatingInput({ value, onChange }: { value: number; onChange: (rating: number) => void }) {
  return (
    <div style={{ display: 'flex', gap: '4px' }}>
      {[1, 2, 3, 4, 5].map((n) => (
        <button key={n} type="button" onClick={() => onChange(n)} style={{ display: 'flex', padding: 0 }} aria-label={`${n} star${n === 1 ? '' : 's'}`}>
          <IconStar size={22} color={n <= value ? '#F5A623' : 'var(--itunda-grey-200)'} fill={n <= value ? '#F5A623' : 'none'} />
        </button>
      ))}
    </div>
  );
}

// Exported 2026-09-02 (Rides domain-split thread) -- RidesView.tsx's own driver
// "Destination Filter" also needs this, same real cross-domain shared-helper
// pattern as MarketplaceView/CommunityView/JobsView/PropertyView's own export
// above.
export function AddressAutocomplete({
  value, onChangeText, onSelectSuggestion, placeholder = 'Delivery address',
}: {
  value: string;
  onChangeText: (text: string) => void;
  onSelectSuggestion: (suggestion: AddressSuggestion) => void;
  placeholder?: string;
}) {
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- same real gap
  // already closed for ride booking (ShopView's PlaceSearchInput) and Android's Eats
  // AddressAutocompleteField: itunda's own "map bookmarks" feature (the Maps tab's
  // star/save) was never surfaced here either, despite a delivery address being an
  // even more universal need than a ride destination. No new backend work -- the same
  // existing GET /api/v1/maps/bookmarks this field's own search suggestions already
  // sit alongside.
  const [bookmarks, setBookmarks] = useState<MapBookmark[]>([]);
  const [focused, setFocused] = useState(false);
  useEffect(() => {
    fetchMyMapBookmarks().then(setBookmarks).catch(() => {});
  }, []);

  const handleChange = (text: string) => {
    onChangeText(text);
    setOpen(false);
    if (debounceRef.current) clearTimeout(debounceRef.current);
    const trimmed = text.trim();
    if (trimmed.length < 3) {
      setSuggestions([]);
      return;
    }
    // Real self-hosted Nominatim search, debounced so a full sentence of typing
    // doesn't fire a request per keystroke -- see EatsController's own doc comment.
    debounceRef.current = setTimeout(() => {
      setSearching(true);
      searchDeliveryAddress(trimmed)
        .then((results) => {
          setSuggestions(results);
          setOpen(results.length > 0);
        })
        .catch(() => {
          // Real, non-critical -- a failed suggestion fetch shouldn't block typing a
          // plain address; the order still places, just without a confirmed pin.
          setSuggestions([]);
        })
        .finally(() => setSearching(false));
    }, 400);
  };

  return (
    <div style={{ position: 'relative' }}>
      <input
        type="text" value={value} onChange={(e) => handleChange(e.target.value)}
        onFocus={() => { setFocused(true); setOpen(suggestions.length > 0); }}
        onBlur={() => setTimeout(() => { setFocused(false); setOpen(false); }, 150)}
        placeholder={placeholder} required autoComplete="off"
        style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      {searching && (
        <span style={{ position: 'absolute', right: '12px', top: '12px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>…</span>
      )}
      {!open && focused && !value.trim() && bookmarks.length > 0 && (
        <div
          className="itunda-card"
          style={{ position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '4px', padding: '6px', zIndex: 10, maxHeight: '220px', overflowY: 'auto' }}
        >
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', fontWeight: 700, padding: '4px 6px 2px' }}>Saved places</p>
          {bookmarks.map((b) => (
            <button
              key={b.id}
              type="button"
              onMouseDown={() => { onSelectSuggestion({ displayName: b.displayName, latitude: b.latitude, longitude: b.longitude }); onChangeText(b.displayName); }}
              style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', textAlign: 'left', padding: '8px 6px', fontSize: 'var(--itunda-type-scale-13-size)', borderRadius: '6px' }}
            >
              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: b.color, flexShrink: 0 }} />
              {b.displayName}
            </button>
          ))}
        </div>
      )}
      {open && (
        <div
          className="itunda-card"
          style={{ position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '4px', padding: '6px', zIndex: 10, maxHeight: '220px', overflowY: 'auto' }}
        >
          {suggestions.map((s, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={() => { onSelectSuggestion(s); setOpen(false); }}
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '8px 6px', fontSize: 'var(--itunda-type-scale-13-size)', borderRadius: '6px' }}
            >
              {s.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Real per-configuration cart line (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's
// Eats recommendation #6: a cart previously could not hold two configurations of the
// same item at all (keyed by raw product id only). `choiceIds` is empty for any item
// with no option groups -- the pre-existing, unaffected case. Two lines for the same
// productId with DIFFERENT choiceIds are genuinely distinct cart entries (e.g. a
// Regular and a Large of the same burger, side by side).
export interface EatsCartLine { productId: string; quantity: number; choiceIds: string[] }

export function eatsCartKey(productId: string, choiceIds: string[]): string {
  return choiceIds.length === 0 ? productId : `${productId}::${[...choiceIds].sort().join(',')}`;
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
export function eatsOptionsSummary(item: MenuItem, choiceIds: string[]): string {
  if (choiceIds.length === 0) return '';
  const names = (item.optionGroups ?? [])
    .flatMap((g) => g.choices)
    .filter((c) => choiceIds.includes(c.id))
    .map((c) => c.name);
  return names.length ? ` (${names.join(', ')})` : '';
}

export function eatsLineUnitPrice(item: MenuItem, choiceIds: string[]): number {
  const delta = (item.optionGroups ?? [])
    .flatMap((g) => g.choices)
    .filter((c) => choiceIds.includes(c.id))
    .reduce((sum, c) => sum + c.priceDelta, 0);
  return item.price + delta;
}

// Real shared browse-header component (2026-07-21) -- extracted from Eats'
// OrderFoodView (the only place this pattern previously existed) so Shop's
// merchant browse can reuse the identical search+chips interaction instead of a
// second bespoke implementation. Callers own their own debounce/state; this just
// renders the field + optional chip row.
export function SearchAndCategoryChips({
  searchInput,
  onSearchChange,
  placeholder,
  categories,
  selectedCategory,
  onSelectCategory,
}: {
  searchInput: string;
  onSearchChange: (value: string) => void;
  placeholder: string;
  categories: string[];
  selectedCategory: string | null;
  onSelectCategory: (category: string | null) => void;
}) {
  return (
    <>
      <input
        type="text"
        value={searchInput}
        onChange={(e) => onSearchChange(e.target.value)}
        placeholder={placeholder}
        className="itunda-card"
        style={{ width: '100%', padding: '12px 16px', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '10px', border: 'none' }}
      />
      {categories.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', marginBottom: '14px' }}>
          <button
            onClick={() => onSelectCategory(null)}
            style={{
              flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: selectedCategory === null ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: selectedCategory === null ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
            }}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c}
              onClick={() => onSelectCategory(c === selectedCategory ? null : c)}
              style={{
                flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                color: selectedCategory === c ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                backgroundColor: selectedCategory === c ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
              }}
            >
              {c}
            </button>
          ))}
        </div>
      )}
    </>
  );
}

// Real Baemin-style 찜 리스트 공유하기 (share favorites list, 2026-08-16) -- DIRECT-only
// (unlike ForwardPickerModal's DIRECT+GROUP picker), matching backend
// EatsFavoriteService.shareFavoritesToConversation's own 1:1-conversation-only
// capability (built on MessagingService, not GroupMessagingService).
// Exported 2026-09-02 (Rides domain-split thread) -- RidesView.tsx's own
// "Share Trip Status" also needs this, same real cross-domain shared-helper
// pattern as AddressAutocomplete's own export above.
export function ShareFavoritesModal({
  onShare, onClose, title = 'Share favorites to…',
}: { onShare: (conversationId: string) => void; onClose: () => void; title?: string }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const showSkeleton = useDeferredLoading(conversations === null);

  useEffect(() => {
    fetchConversations().then(setConversations).catch(() => setConversations([]));
  }, []);

  return (
    <div
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        className="itunda-card"
        style={{ width: '100%', maxHeight: '60vh', overflowY: 'auto', borderRadius: '16px 16px 0 0', margin: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>{title}</p>
        {conversations === null ? (
          showSkeleton ? <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
        ) : conversations.length === 0 ? (
          <EmptyState message="No conversations to share to yet — start a chat first." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {conversations.map((c) => (
              <button
                key={c.conversationId}
                style={{ width: '100%', textAlign: 'left', padding: '10px 0' }}
                onClick={() => onShare(c.conversationId)}
              >
                {c.otherUserName}
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Exported 2026-09-02 (Rides domain-split thread) -- RidesView.tsx's own
// pickup/dropoff pickers also need this, same real cross-domain shared-helper
// pattern as AddressAutocomplete/ShareFavoritesModal's own exports above.
export function PlaceSearchInput({ label, placeholder, value, onSelect }: {
  label: string; placeholder: string; value: PlaceSearchResult | null; onSelect: (place: PlaceSearchResult) => void;
}) {
  const [query, setQuery] = useState(value?.displayName ?? '');
  const [results, setResults] = useState<PlaceSearchResult[] | null>(null);
  // Real a11y fix (item 244, web accessibility sweep -- docs/ACCESSIBILITY.md had
  // never covered the web micro-frontends at all before this pass): the <label>
  // above used to be a plain sibling of <input>, with no htmlFor/id association at
  // all -- unlike every other form field in this codebase (LoginPage/RegisterPage/
  // merchant-mfe screens), which wrap the input as the label's own descendant.
  // Neither clicking the label nor a screen reader's field name worked here. This
  // component is real, live UI used 5 times across ride/rental pickup+dropoff
  // fields (BankDashboard.tsx), not dead code.
  const inputId = useId();

  // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- itunda already
  // has a real, backend-synced "map bookmarks" feature (the Maps tab's own star/save,
  // folders/colors and all), never surfaced anywhere in ride booking despite being
  // exactly the real "Home"/"Work" shortcut every real ride-hailing app shows before
  // you type anything -- a real, confirmed gap (grep found zero references to
  // bookmarks anywhere in the ride-booking code). Fetched once per mount (a small,
  // per-account list, no pagination needed) rather than threaded in as a prop, so all
  // 5 existing call sites of this shared component (ride + rental pickup/dropoff/stops)
  // get it for free.
  const [bookmarks, setBookmarks] = useState<MapBookmark[]>([]);
  const [focused, setFocused] = useState(false);
  useEffect(() => {
    fetchMyMapBookmarks().then(setBookmarks).catch(() => {});
  }, []);

  useEffect(() => {
    if (!query || query === value?.displayName) { setResults(null); return; }
    const handle = setTimeout(() => {
      searchPlaces(query).then(setResults).catch(() => setResults([]));
    }, 350);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  const selectBookmark = (b: MapBookmark) => {
    onSelect({ displayName: b.displayName, latitude: b.latitude, longitude: b.longitude });
    setQuery(b.displayName);
    setFocused(false);
  };

  return (
    <div style={{ position: 'relative', marginBottom: '12px' }}>
      <label htmlFor={inputId} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', fontWeight: 700, display: 'block', marginBottom: '4px' }}>{label}</label>
      <input
        id={inputId}
        type="text" value={query} placeholder={placeholder}
        onChange={(e) => setQuery(e.target.value)}
        onFocus={() => setFocused(true)}
        onBlur={() => setTimeout(() => setFocused(false), 150)}
        style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      {results && results.length > 0 && (
        <div className="itunda-card" style={{ position: 'absolute', zIndex: 10, width: '100%', marginTop: '4px', padding: '4px', maxHeight: '220px', overflowY: 'auto' }}>
          {results.map((r, i) => (
            <button
              key={`${r.latitude}-${r.longitude}-${i}`} type="button"
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '10px', fontSize: 'var(--itunda-type-scale-13-size)', borderRadius: '6px' }}
              onClick={() => { onSelect(r); setQuery(r.displayName); setResults(null); }}
            >
              {r.displayName}
            </button>
          ))}
        </div>
      )}
      {!query && focused && bookmarks.length > 0 && (
        <div className="itunda-card" style={{ position: 'absolute', zIndex: 10, width: '100%', marginTop: '4px', padding: '4px', maxHeight: '220px', overflowY: 'auto' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', fontWeight: 700, padding: '6px 10px 2px' }}>Saved places</p>
          {bookmarks.map((b) => (
            <button
              key={b.id} type="button"
              style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', textAlign: 'left', padding: '10px', fontSize: 'var(--itunda-type-scale-13-size)', borderRadius: '6px' }}
              onClick={() => selectBookmark(b)}
            >
              <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: b.color, flexShrink: 0 }} />
              <span>
                <span style={{ display: 'block', fontWeight: 700 }}>{b.displayName}</span>
                <span style={{ display: 'block', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{b.folderName}</span>
              </span>
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Real product wishlist toggle (2026-07-20) -- the real "찜하기" heart every real
// Coupang/Naver/Kakao/Toss Shopping-style catalog card has. Purely presentational --
// favorited-state is lifted to ProductCatalogView and fetched once for the whole
// catalog, not once per product card, to avoid N duplicate list fetches.
export function WishlistButton({ favorited, busy, onToggle }: { favorited: boolean; busy: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={busy}
      aria-label={favorited ? 'Remove from wishlist' : 'Add to wishlist'}
      style={{ display: 'flex', lineHeight: 1 }}
    >
      <WishlistHeart favorited={favorited} size={18} />
    </button>
  );
}

// Real Karrot-Score-style numeric trust/reputation badge (2026-07-24) -- backend
// (User.trustScore, TrustScoreService) and the trustScores map on every Hood browse
// response have existed since 2026-07-21, and this file already fetched it into state
// via fetchListings/fetchMyListings/etc, but never rendered it anywhere -- closes
// docs/DESIGN_REFERENCES.md Section 4 recommendation #1. Deliberately a plain 0-1000
// number, never a manner-temperature/Celsius metaphor (see backend User.kt's own doc
// comment on why that's specifically wrong for a non-Korean market).
export function TrustBadge({ score }: { score: number }) {
  return (
    <span
      style={{
        fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-grey-600)',
        backgroundColor: 'var(--itunda-grey-100)', padding: '2px 6px', borderRadius: '6px',
      }}
    >
      Trust {score}
    </span>
  );
}

// Real content-report submission (item 156) -- see lib/hoodReport.ts's own doc comment.
// Shared across Marketplace/Community/Jobs/Property (the same 4-target scope Android's
// own HoodReportAction/HoodShared.kt already established), same real preset reasons
// Android's own dialog uses. Wraps its own click in stopPropagation since every caller
// renders this inside a whole-card onClick/onOpen handler.
export function HoodReportButton({ targetType, targetId }: { targetType: HoodReportTargetType; targetId: string }) {
  const [showChoices, setShowChoices] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const send = async (reason: string) => {
    setShowChoices(false);
    setSending(true);
    try {
      await submitHoodReport(targetType, targetId, reason);
      setMessage('Thanks. Your report was sent for review.');
    } catch (err) {
      setMessage(err instanceof ApiError && err.code === 'HOOD_REPORT_ALREADY_OPEN' ? 'You already reported this post.' : 'Could not send the report.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div onClick={(e) => e.stopPropagation()} style={{ display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-start' }}>
      {message ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: message.startsWith('Thanks') ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>{message}</p>
      ) : showChoices ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }} onClick={() => send('Unsafe payment, contact request, or scam')}>Unsafe or scam</button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }} onClick={() => send('Misleading, unavailable, or spam content')}>Misleading or spam</button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }} onClick={() => send('Harassment, hateful, illegal, or prohibited content')}>Abusive or illegal</button>
          <button style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} onClick={() => setShowChoices(false)}>Cancel</button>
        </div>
      ) : (
        <button style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} disabled={sending} onClick={() => setShowChoices(true)}>
          {sending ? 'Reporting…' : 'Report'}
        </button>
      )}
    </div>
  );
}

// Real post-transaction review preset checklist labels (2026-07-24) -- ids must match
// backend HoodReviewService.GOOD_POINTS/UNCOMFORTABLE_POINTS exactly.
const HOOD_GOOD_POINT_LABELS: [string, string][] = [
  ['RESPONSIVE', 'Quick to respond'], ['AS_DESCRIBED', 'As described'], ['ON_TIME', 'On time'],
  ['FRIENDLY', 'Friendly'], ['FAIR_PRICE', 'Fair price'],
];
const HOOD_UNCOMFORTABLE_POINT_LABELS: [string, string][] = [
  ['LATE', 'Was late'], ['NOT_AS_DESCRIBED', 'Not as described'], ['UNRESPONSIVE', 'Hard to reach'],
  ['RUDE', 'Rude'], ['PRICE_ISSUE', 'Price disagreement'],
];
const hoodGoodPointLabel = (id: string) => HOOD_GOOD_POINT_LABELS.find(([pid]) => pid === id)?.[1] ?? id;
const hoodUncomfortablePointLabel = (id: string) => HOOD_UNCOMFORTABLE_POINT_LABELS.find(([pid]) => pid === id)?.[1] ?? id;

// Real post-transaction review with Karrot's own asymmetric public/private visibility
// (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 4 recommendation #2. A
// preset checklist, not free text, matching Karrot's own real review UX: "good points"
// are shown publicly (feed into the trust score), "uncomfortable points" stay private
// between the two real parties to the transaction. Shared by Marketplace/Jobs/Property.
export function HoodReviewForm({
  selectedGoodPoints, onToggleGoodPoint, selectedUncomfortablePoints, onToggleUncomfortablePoint, submitting, onCancel, onSubmit,
}: {
  selectedGoodPoints: Set<string>; onToggleGoodPoint: (id: string) => void;
  selectedUncomfortablePoints: Set<string>; onToggleUncomfortablePoint: (id: string) => void;
  submitting: boolean; onCancel: () => void; onSubmit: () => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>What went well? (shown publicly)</p>
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {HOOD_GOOD_POINT_LABELS.map(([id, label]) => {
          const selected = selectedGoodPoints.has(id);
          return (
            <button
              key={id}
              onClick={() => onToggleGoodPoint(id)}
              style={{
                fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, padding: '6px 12px', borderRadius: '999px',
                color: selected ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                backgroundColor: selected ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>Anything uncomfortable? (private -- only you two see this)</p>
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {HOOD_UNCOMFORTABLE_POINT_LABELS.map(([id, label]) => {
          const selected = selectedUncomfortablePoints.has(id);
          return (
            <button
              key={id}
              onClick={() => onToggleUncomfortablePoint(id)}
              style={{
                fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, padding: '6px 12px', borderRadius: '999px',
                color: selected ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                backgroundColor: selected ? 'var(--itunda-red)' : 'var(--itunda-grey-100)',
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={submitting} onClick={onCancel}>
          Cancel
        </button>
        <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting} onClick={onSubmit}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </div>
  );
}

// Real read-back for a submitted Hood transaction review (item 192) -- see
// lib/marketplace.ts's fetchListingReviews doc comment. Only ever rendered for a real
// party to the transaction (the fetch itself real-403s otherwise), so both "your
// review" and "their review of you" -- including its uncomfortablePoints -- are
// honestly shown here, matching Karrot's own asymmetric visibility: private between the
// two real parties, not public to anyone else.
export function HoodReviewResultView({ reviews, myUserId }: { reviews: HoodReview[]; myUserId: string | undefined }) {
  const mine = reviews.find((r) => r.reviewerId === myUserId);
  const theirs = reviews.find((r) => r.reviewerId !== myUserId);
  if (!mine && !theirs) return null;
  const block = (title: string, review: HoodReview) => (
    <div style={{ padding: '10px 12px', borderRadius: '8px', background: 'var(--itunda-grey-100)', display: 'flex', flexDirection: 'column', gap: '2px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{title}</p>
      {review.goodPoints.length > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>👍 {review.goodPoints.map(hoodGoodPointLabel).join(', ')}</p>
      )}
      {review.uncomfortablePoints.length > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>⚠️ {review.uncomfortablePoints.map(hoodUncomfortablePointLabel).join(', ')}</p>
      )}
    </div>
  );
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      {mine && block('Your review', mine)}
      {theirs && block('Their review of you', theirs)}
    </div>
  );
}


// Real device management (2026-07-20) -- the same self-service "your devices" control
// Toss's own security settings page offers. See lib/device.ts's own doc comment.
function DevicesView() {
  const { t } = useI18n();
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const showSkeleton = useDeferredLoading(devices === null);
  const myDeviceId = getOrCreateDeviceId();

  const load = () => {
    setError(null);
    fetchMyDevices().then(setDevices).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRevoke = async (deviceId: string) => {
    setRevokingId(deviceId);
    setError(null);
    try {
      await revokeDevice(deviceId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRevokingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (devices === null) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', padding: '0 4px' }}>
        Devices that have signed in to your account. A device must be verified before it can send money.
      </p>
      {devices.length === 0 ? (
        <EmptyState message="No other devices yet — this is the only one signed in right now." />
      ) : (
        devices.map((d) => (
          <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 0' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
                {d.deviceName ?? 'Unknown device'} {d.deviceId === myDeviceId && <span style={{ color: 'var(--itunda-indigo)' }}>(this device)</span>}
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: d.trusted ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>
                {d.trusted ? '✓ Verified — can send money' : '⚠ Not verified — sign-in only'}
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Last seen {new Date(d.lastSeenAt).toLocaleString()}</p>
            </div>
            <button
              className="itunda-btn itunda-btn-danger"
              disabled={revokingId === d.deviceId}
              onClick={() => handleRevoke(d.deviceId)}
              style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
            >
              {revokingId === d.deviceId ? 'Removing…' : 'Remove'}
            </button>
          </div>
        ))
      )}
    </div>
  );
}

// Real, sourced Toss Bank card-marketing-page rebuild (2026-08-24, direct user-supplied
// reference: 8 real Toss Bank Debit Card screenshots -- hero headline over a card
// illustration, a real feature-benefit list, "clear UX, clear graphics, smooth
// animation" explaining a product before the user commits, not a bare form). itunda's
// own pre-issue state used to be two lines of plain text and a button -- functionally
// complete, but none of the "explain the product first" craft the reference shows.
//
// Deliberately does NOT copy Toss's own screenshot content literally -- their card page
// features (K-Pass automatic transit refunds, NFC tap-to-pay-as-OTP, a choice of card
// colors) are all real Korean-market/NFC-hardware features itunda's own CardService.kt
// genuinely doesn't have. Every claim below is grounded in that file's own real
// capabilities instead: free issuance (no fee field exists on DebitCard at all),
// instant issue via one API call (no branch visit), app-controlled daily/monthly spend
// limits, one-tap freeze/unfreeze. The illustration is a plain flat SVG card in
// itunda's own real brand indigo (packages/design-tokens/tokens.css) -- not a
// photorealistic 3D render, matching itundaface's own established flat/geometric
// illustration language (see project_itunda_own_icons_graphics) rather than inventing a
// new visual style for one screen.
// Real Toss Bank "which color do you like?" issuance step (namu.wiki: 5 real named
// colorways; toss.tech's own engineering post on the picker's 3D touch-and-rotate
// interaction) -- direct user instruction 2026-08-27: "update itunda bank with all
// those cards designs allowing users to choose from those designs... that's how toss
// does it too". Picks from CARD_DESIGNS (lib/card.ts), itunda's own real front/back
// colorways validated in the standalone card-lineup design pass. Front-only during
// picking, matching that same pass's own real-photo-sourced finding: the real card's
// front is color and chip, nothing else -- no fabricated printed number here either,
// same "fully masked, no card exists yet" reasoning the previous single-design mockup
// already established.
// Real Toss Bank 체크카드 (check/debit card) -- see the backend's DebitCard.kt doc
// comment for the full sourced account (item 207) and the honest boundary around this
// not riding a real Visa/Mastercard rail. "Pay with card" below is itunda's own real,
// ledger-backed simulation of a card-present purchase (real money moves, real limits
// are enforced), the same honest "demo the part that can be real" convention
// DemoCardAuthorizationService already established for the merchant-side equivalent.
function CardView() {
  const { t } = useI18n();
  const [card, setCard] = useState<Card | null | undefined>(undefined);
  const [transactions, setTransactions] = useState<CardTransaction[]>([]);
  const [error, setError] = useState<string | null>(null);
  const showSkeleton = useDeferredLoading(card === undefined);
  const [busy, setBusy] = useState(false);
  const [dailyLimitInput, setDailyLimitInput] = useState('');
  const [monthlyLimitInput, setMonthlyLimitInput] = useState('');
  const [merchantName, setMerchantName] = useState('');
  const [chargeAmount, setChargeAmount] = useState('');
  const [chargeError, setChargeError] = useState<string | null>(null);
  const [chargeSuccess, setChargeSuccess] = useState<string | null>(null);
  // Real KakaoBank 결제홈 (Payment Home)-style unified spend+benefits view (2026-08-16,
  // launching August 2026 per KakaoBank's own H1 earnings coverage: "카드 결제 내역과
  // 혜택을 통합 관리할 수 있는 '결제홈'") -- CreditScoreService already computes a real
  // "Card usage" factor from real card-transaction counts (Section 76), but nothing on
  // this screen ever surfaced it. Both endpoints already existed and are already used
  // elsewhere (lib/creditScore.ts) -- this is purely wiring the same real data into the
  // one screen where a cardholder would naturally look for "what is my card earning me."
  const [cardUsageFactor, setCardUsageFactor] = useState<CreditScoreFactor | null>(null);
  const [cardSuggestion, setCardSuggestion] = useState<CreditScoreSuggestion | null>(null);
  // Real "카드 비밀번호 변경" (change card PIN) inline form (2026-09-01, direct
  // user-supplied Toss Bank card-management screenshots) -- see lib/card.ts's
  // setCardPin doc comment for the real step-up-auth this posts to.
  const [showPinForm, setShowPinForm] = useState(false);
  const [newPinInput, setNewPinInput] = useState('');
  const [pinPasswordInput, setPinPasswordInput] = useState('');
  const [pinError, setPinError] = useState<string | null>(null);
  const [pinSuccess, setPinSuccess] = useState(false);

  const load = () => {
    setError(null);
    fetchMyCard()
      .then((c) => {
        setCard(c);
        setDailyLimitInput(String(c.dailyLimit));
        setMonthlyLimitInput(String(c.monthlyLimit));
      })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'CARD_NOT_FOUND') {
          setCard(null);
          return;
        }
        setError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
    fetchCardTransactions().then((r) => setTransactions(r.transactions)).catch(() => {});
    fetchCreditScore()
      .then((r) => setCardUsageFactor(r.factors.find((f) => f.name === 'Card usage') ?? null))
      .catch(() => {});
    fetchCreditScoreSuggestions()
      .then((suggestions) => setCardSuggestion(suggestions.find((s) => s.action === 'Use your itunda Card more' || s.action === 'Get an itunda Card') ?? null))
      .catch(() => {});
  };
  useEffect(load, []);

  const handleIssue = async (design: string) => {
    setBusy(true);
    setError(null);
    try {
      await issueCard(design);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleToggleFreeze = async () => {
    if (!card) return;
    setBusy(true);
    setError(null);
    try {
      const updated = card.frozen ? await unfreezeCard() : await freezeCard();
      setCard(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real "분실신고" (report lost or stolen) -- closes the gap this file's own
  // previous version disclosed: a distinct, one-way backend state now exists
  // (POST /api/v1/card/report-lost), so this no longer relabels the ordinary,
  // self-reversible freezeCard() call.
  const handleReportLost = async () => {
    if (!card || card.lost || card.closedAt) return;
    setBusy(true);
    setError(null);
    try {
      setCard(await reportLostCard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real "카드 해지하기" (close card) -- a deliberate, one-way retirement distinct
  // from a lost/stolen report; only reissue below can recover from either.
  const handleCloseCard = async () => {
    if (!card || card.closedAt) return;
    if (!window.confirm('Close this card? You can get a new one afterward, but this card will stop working immediately.')) return;
    setBusy(true);
    setError(null);
    try {
      setCard(await closeMyCard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real "카드 재발급" (reissue) -- the real recovery path from a lost/stolen or
  // closed card; regenerates last4 and clears the old PIN in place (backend
  // enforces one card per user, see CardService.reissue's own doc comment).
  const handleReissue = async () => {
    if (!card) return;
    setBusy(true);
    setError(null);
    try {
      setCard(await reissueCard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleSetPin = async (e: React.FormEvent) => {
    e.preventDefault();
    setPinError(null);
    setPinSuccess(false);
    setBusy(true);
    try {
      setCard(await setCardPin(newPinInput, pinPasswordInput));
      setNewPinInput('');
      setPinPasswordInput('');
      setShowPinForm(false);
      setPinSuccess(true);
    } catch (err) {
      setPinError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleSaveLimits = async () => {
    setBusy(true);
    setError(null);
    try {
      const updated = await setCardLimits(Number(dailyLimitInput), Number(monthlyLimitInput));
      setCard(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCharge = async (e: React.FormEvent) => {
    e.preventDefault();
    setChargeError(null);
    setChargeSuccess(null);
    setBusy(true);
    try {
      const result = await chargeCard(Number(chargeAmount), merchantName);
      setCard(result.card);
      setChargeSuccess(`Paid ${result.transaction.amount.toLocaleString()} RWF at ${result.transaction.merchantName}`);
      setMerchantName('');
      setChargeAmount('');
      load();
    } catch (err) {
      setChargeError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real bug found+fixed while restructuring this screen (2026-08-23): `error` is
  // shared between the initial load AND every later action (freeze/save-limits/
  // report-lost/charge) -- this early-return used to fire unconditionally, so any
  // one of those LATER action failures replaced the entire, already-loaded card
  // screen with a full-page ErrorCard, not just an inline message next to the
  // control that actually failed. Scoped to the real initial-load-failure case only
  // (card never successfully loaded) -- a later action error now renders inline
  // within the still-visible screen instead (see the error <p> below).
  if (error && card === undefined) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (card === undefined) return showSkeleton ? <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  if (card === null) {
    return <CardExplainer busy={busy} onIssue={handleIssue} />;
  }

  // Real Toss Bank reference (2 more screenshots, 2026-08-23, direct user
  // instruction: "when user click on card in topbar of itunda bank this is what
  // they should see"): real Toss's own card screen leads with a month-spend
  // headline + a small card thumbnail, a real usage-history list, then a flat
  // "convenient features" row list -- not the card-first, form-heavy layout this
  // screen used to have. Restructured to that same order using itunda's own real
  // capabilities only: no "My card number" (itunda never stores/exposes a full
  // card number, only last4 -- a real, honest gap, not fabricated), no month
  // navigation arrows (spentThisMonth is a live running total, no per-past-month
  // breakdown endpoint exists), no postpaid-transit-card/reissue/ATM-guide/
  // overseas-fee/ongoing-events rows (all genuinely Korea-transit/card-network-
  // specific, itunda has no backend for any of them). "Report lost or stolen",
  // "Close card", and "Card PIN" (2026-09-01) are now real, distinct backend
  // flows -- see CardService.reportLost/closeCard/setPin's own doc comments --
  // no longer relabeling freezeCard() the way this comment used to describe.
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>This month</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0 0' }}>{card.spentThisMonth.toLocaleString()} RWF</p>
        </div>
        <div
          style={{
            width: '72px', height: '46px', borderRadius: '8px', flexShrink: 0, position: 'relative', overflow: 'hidden',
            background: card.frozen ? 'var(--itunda-grey-500)' : cardDesign(card.design).front,
            border: !card.frozen && cardDesign(card.design).frontLight ? '1px solid #e2e2de' : 'none',
            display: 'flex', flexDirection: 'column', justifyContent: 'space-between', padding: '6px',
          }}
        >
          <div style={{ position: 'absolute', inset: 0, background: 'linear-gradient(115deg, rgba(255,255,255,0.18) 0%, rgba(255,255,255,0) 40%)' }} />
          <BankCardChip size={16} />
          {card.frozen ? (
            <LockGlyph size={14} color="#fff" style={{ alignSelf: 'flex-end' }} />
          ) : (
            <CardContactlessGlyph size={12} color={cardDesign(card.design).frontLight ? 'rgba(25,31,40,0.55)' : 'rgba(255,255,255,0.85)'} />
          )}
        </div>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
        •••• {card.last4} ·{' '}
        {card.closedAt ? 'Closed' : card.lost ? 'Reported lost or stolen' : card.frozen ? 'Frozen — no purchases can be made' : 'Active'}
      </p>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}

      {card.lost || card.closedAt ? (
        <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleReissue} style={{ marginBottom: '20px' }}>
          {busy ? '…' : 'Get a new card'}
        </button>
      ) : (
        <button className={`itunda-btn ${card.frozen ? 'itunda-btn-primary' : 'itunda-btn-danger'}`} disabled={busy} onClick={handleToggleFreeze} style={{ marginBottom: '20px' }}>
          {card.frozen ? 'Unfreeze card' : 'Freeze card'}
        </button>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Usage history</h3>
        {transactions.length === 0 ? (
          <EmptyState message="No card purchases yet — once you use your card, they'll show up here." />
        ) : (
          transactions.map((t) => (
            <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t.merchantName}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{new Date(t.createdAt).toLocaleString()}</p>
              </div>
              <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{t.amount.toLocaleString()} RWF</span>
            </div>
          ))
        )}
      </div>

      {(cardUsageFactor || cardSuggestion) && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Card benefits</h3>
          {cardUsageFactor && (
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{cardUsageFactor.description}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-green)' }}>+{cardUsageFactor.points} credit score</span>
            </div>
          )}
          {cardSuggestion && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{cardSuggestion.description}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>+{cardSuggestion.pointsGain} more</span>
            </div>
          )}
        </div>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Convenient features</h3>
        <button
          onClick={() => document.getElementById('card-spend-limits-section')?.scrollIntoView({ behavior: 'smooth' })}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left' }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>Spend limits</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={() => setShowPinForm((v) => !v)}
          disabled={busy || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{card.pinSet ? 'Change card PIN' : 'Set card PIN'}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={handleReportLost}
          disabled={busy || card.lost || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.lost || card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{card.lost ? 'Reported lost or stolen' : 'Report lost or stolen'}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={handleCloseCard}
          disabled={busy || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: card.closedAt ? 'var(--itunda-grey-500)' : 'var(--itunda-red)' }}>{card.closedAt ? 'Card closed' : 'Close card'}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
      </div>

      {showPinForm && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{card.pinSet ? 'Change card PIN' : 'Set card PIN'}</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            A real 4-digit card PIN, separate from your login password. Confirm your current login password to change it.
          </p>
          <form onSubmit={handleSetPin} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {pinError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{pinError}</p>}
            <input
              type="password" inputMode="numeric" placeholder="New 4-digit PIN" value={newPinInput}
              onChange={(e) => setNewPinInput(e.target.value)} required maxLength={4} pattern="\d{4}"
              style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
            />
            <input
              type="password" placeholder="Current login password" value={pinPasswordInput}
              onChange={(e) => setPinPasswordInput(e.target.value)} required
              style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>
              {busy ? '…' : 'Save PIN'}
            </button>
          </form>
        </div>
      )}
      {pinSuccess && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginBottom: '8px' }}>Card PIN saved.</p>}

      <div id="card-spend-limits-section" className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>Spend limits</h3>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Today</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>{card.spentToday.toLocaleString()} / {card.dailyLimit.toLocaleString()} RWF</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>This month</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>{card.spentThisMonth.toLocaleString()} / {card.monthlyLimit.toLocaleString()} RWF</span>
        </div>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
          <input
            type="number" placeholder="Daily limit" value={dailyLimitInput} onChange={(e) => setDailyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <input
            type="number" placeholder="Monthly limit" value={monthlyLimitInput} onChange={(e) => setMonthlyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
        </div>
        <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSaveLimits} style={{ width: '100%' }}>
          Save limits
        </button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Pay with your card</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          itunda has no real card-network partnership yet, so this simulates a real card-present purchase — real money moves, real limits apply.
        </p>
        <form onSubmit={handleCharge} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {chargeError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{chargeError}</p>}
          {chargeSuccess && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>{chargeSuccess}</p>}
          <input
            placeholder="Merchant name" value={merchantName} onChange={(e) => setMerchantName(e.target.value)} required
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <input
            type="number" placeholder="Amount (RWF)" value={chargeAmount} onChange={(e) => setChargeAmount(e.target.value)} required min="1"
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy || card.frozen}>
            {card.closedAt ? 'Card is closed' : card.lost ? 'Card reported lost' : card.frozen ? 'Card is frozen' : busy ? 'Paying…' : 'Pay'}
          </button>
        </form>
      </div>
    </div>
  );
}

// Real Kakao Bank SafeBox (세이프박스) equivalent -- claim-anytime interest that grows
// for real off the actual SAVINGS account balance (InterestAccrualScheduler, 2026-07-20).
// Real Kakao Pay 머니굴리기 ("rolling money") round-up auto-saving -- see
// lib/savings.ts's own doc comment. First client UI for this feature anywhere
// (item 112, found via a content-grep sweep: Android has a real client, bank-mfe
// and iOS never did). Every real P2P transfer rounds up to the chosen increment and
// deposits the spare change into the chosen goal.
function RoundUpCard({ goals }: { goals: SavingsGoal[] }) {
  const { t } = useI18n();
  const [settings, setSettings] = useState<RoundUpSettings | null | undefined>(undefined);
  const [increment, setIncrement] = useState<number>(100);
  const [goalId, setGoalId] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchRoundUpSettings()
      .then((s) => {
        setSettings(s);
        if (s) { setIncrement(s.roundToNearest); setGoalId(s.targetGoalId ?? ''); }
      })
      .catch(() => setSettings(null));
  };
  useEffect(load, []);

  const handleToggle = async (enabled: boolean) => {
    if (enabled && !goalId) { setError('Choose a savings goal first.'); return; }
    setBusy(true);
    setError(null);
    try {
      const updated = await setRoundUpSettings(enabled, increment, enabled ? goalId : (settings?.targetGoalId ?? null));
      setSettings(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (settings === undefined) return null;

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Round-up savings</h3>
        {settings?.enabled && (
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={() => handleToggle(false)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {busy ? '…' : 'Turn off'}
          </button>
        )}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {settings?.enabled ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Every transfer rounds up to the nearest {settings.roundToNearest.toLocaleString()} RWF, saved into your goal.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
            Round up every transfer to a real RWF increment and auto-save the spare change.
          </p>
          <div style={{ display: 'flex', gap: '6px' }}>
            {ROUND_UP_INCREMENTS.map((v) => (
              <button
                key={v} type="button" onClick={() => setIncrement(v)}
                className={increment === v ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px' }}
              >
                {v.toLocaleString()} RWF
              </button>
            ))}
          </div>
          <select
            value={goalId} onChange={(e) => setGoalId(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          >
            <option value="">Choose a savings goal</option>
            {goals.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !goalId} onClick={() => handleToggle(true)}>
            {busy ? 'Turning on…' : 'Turn on round-up'}
          </button>
        </div>
      )}
    </div>
  );
}

function InterestJarCard() {
  const { t } = useI18n();
  const [jar, setJar] = useState<InterestJar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);
  const showSkeleton = useDeferredLoading(jar === null);
  const [claimMsg, setClaimMsg] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.tsx's own
  // doc comment. No Fill/Withdraw here -- money auto-accrues off the linked balance,
  // "claim" already has its own dedicated button on this card.
  const [showDetail, setShowDetail] = useState(false);

  const load = () => {
    setError(null);
    fetchInterestJar().then(setJar).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks), using jar?.balance so it's
  // already correct once jar loads.
  const animatedBalance = useCountUp(jar?.balance ?? 0);

  const handleClaim = async () => {
    setClaiming(true);
    setError(null);
    setClaimMsg(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await claimInterest();
      setClaimMsg(result.message);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setClaiming(false);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (jar === null) return showSkeleton ? <div className="skeleton" style={{ height: '140px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  const canClaim = jar.earnedThisMonth > 0;

  return (
    <div style={{ marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)', padding: '24px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
      {/* Real methodology-transparency fix (2026-08-11): jar.rate is the ANNUAL rate
          SavingsService.accrueInterest() divides by 365 to get the real daily accrual
          (dailyRate = rate/100/365) -- this copy called it "daily interest" outright,
          which is the actual number times ~365 too high a read for anyone taking it
          literally. Now states the real methodology instead of a bare adjective. */}
      {showDetail && (
        <BucketDetailScreen
          title="Interest Jar"
          subtitle="Safe Box"
          balanceText={`${jar.balance.toLocaleString()} RWF`}
          secondaryStat={{ label: 'Earned all-time', value: `${jar.earnedTotal.toLocaleString()} RWF` }}
          fetchTransactions={fetchInterestJarTransactions}
          onBack={() => setShowDetail(false)}
        />
      )}
      <button onClick={() => setShowDetail(true)} style={{ display: 'block', width: '100%', textAlign: 'left' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>Safe Box · {jar.rate}% annual, accrued daily on your balance</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{animatedBalance.toLocaleString()} RWF</p>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '10px' }}>
          <div>
            {/* Real fix (2026-08-11): interest now auto-credits to the account the
                instant it accrues (see backend SavingsService.accrueInterest's own
                doc comment, matching real Toss Bank passbook interest) -- this money
                is already in jar.balance above, not sitting unclaimed. */}
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.8 }}>Earned this month</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{jar.earnedThisMonth.toLocaleString()} RWF</p>
          </div>
          <div style={{ textAlign: 'right' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.8 }}>Earned all-time</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{jar.earnedTotal.toLocaleString()} RWF</p>
          </div>
        </div>
      </button>
      {needsDeviceVerification ? (
        <div style={{ marginTop: '14px' }}>
          {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
              full account. handleClaim resets needsDeviceVerification itself. */}
          <DeviceStepUpPrompt onVerified={handleClaim} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        <button
          className="itunda-btn"
          onClick={handleClaim}
          disabled={!canClaim || claiming}
          style={{ marginTop: '14px', width: '100%', backgroundColor: '#fff', color: 'var(--itunda-indigo)', fontWeight: 700, opacity: canClaim ? 1 : 0.6 }}
        >
          {claiming ? 'Clearing…' : canClaim ? `OK, ${jar.earnedThisMonth.toLocaleString()} RWF added` : 'Nothing new this month yet'}
        </button>
      )}
      {claimMsg && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '8px' }}>{claimMsg}</p>}
    </div>
  );
}

// Real Deposit Protection Fund card (2026-08-11) -- see DepositProtectionFund.kt's own
// doc comment: rather than just disclosing an absence of real banking protections, this
// shows the real, working, ledger-backed reserve itunda maintains as its own internal
// simulation of what real deposit protection could look like -- same "real mechanics,
// honestly labeled as itunda's own scheme" discipline this codebase already applies to
// VUP/RSE/SACCO.
function DepositProtectionCard() {
  const [status, setStatus] = useState<DepositProtectionStatus | null>(null);

  useEffect(() => {
    fetchDepositProtectionStatus().then(setStatus).catch(() => {
      // Non-critical -- the disclosure copy below this card still renders without it.
    });
  }, []);

  if (status === null) return null;

  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, margin: '0 0 8px' }}>Deposit Protection Fund (simulation)</h3>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your covered balance</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 650 }}>{status.yourCoveredBalance.toLocaleString()} RWF</p>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', marginTop: '4px' }}>
        Covered up to {status.coverageCapPerUser.toLocaleString()} RWF per user
      </p>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)' }}>
        itunda&apos;s reserve: {status.fundReserveBalance.toLocaleString()} RWF
      </p>
    </div>
  );
}

function GoalCard({ goal, onChanged }: { goal: SavingsGoal; onChanged: () => void }) {
  const { t } = useI18n();
  // Real gap found live (2026-08-31, direct user reference against Toss's own real
  // 보관하기/나눠모으기 pockets -- every one supports both 채우기 (fill) and 꺼내기
  // (withdraw), never a one-way deposit): this card only ever let money go IN, with no
  // way back out -- see backend SavingsService.withdrawFromGoal's own doc comment for
  // the full account.
  const [mode, setMode] = useState<'closed' | 'deposit' | 'withdraw'>('closed');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket detail screen (2026-08-31) -- see BucketDetailScreen.tsx's own doc
  // comment. Deposit/withdraw stays on this card's own existing inline form below --
  // the detail screen is a pure ledger viewer, opened by tapping the goal's own row.
  const [showDetail, setShowDetail] = useState(false);
  const pct = Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100));

  const handleDeposit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await depositToGoal(goal.id, Number(amount));
      // Real Toss UX-writing "Find Hidden Emotion" principle (toss.tech/article/
      // 8-writing-principles-of-toss, their own example: a congratulatory message
      // when a loan is fully paid off, not just a transaction confirmation) --
      // itunda already does this for loan payoff (LoansView.tsx's payoffMessage,
      // "one less thing to carry"), and the backend already fires a push
      // notification on goal completion (SavingsService.notifyGoalCompleted), but
      // the person actually watching THIS screen at the exact moment they hit
      // their target saw nothing beyond a small "· Completed" tag they'd only
      // notice on a later visit. Detects the active->completed transition from
      // the deposit response itself, not a guess from the pre-call goal prop.
      if (goal.status !== 'completed' && result.goal.status === 'completed') {
        showToast(`You did it — "${goal.name}" is fully funded! 🎉`);
      }
      setAmount('');
      setMode('closed');
      onChanged();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await withdrawFromGoal(goal.id, Number(amount));
      setAmount('');
      setMode('closed');
      onChanged();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      {showDetail && (
        <BucketDetailScreen
          title={goal.name}
          subtitle="Savings Goal"
          balanceText={`${goal.currentAmount.toLocaleString()} RWF`}
          secondaryStat={{ label: 'Target', value: `${goal.targetAmount.toLocaleString()} RWF` }}
          fetchTransactions={() => fetchGoalTransactions(goal.id)}
          onBack={() => setShowDetail(false)}
        />
      )}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <button onClick={() => setShowDetail(true)} style={{ textAlign: 'left' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{goal.name}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {goal.currentAmount.toLocaleString()} / {goal.targetAmount.toLocaleString()} RWF
            {goal.status === 'completed' && ' · Completed 🎉'}
          </p>
        </button>
        <div style={{ display: 'flex', gap: '6px' }}>
          {goal.currentAmount > 0 && (
            <button
              className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              onClick={() => setMode((m) => (m === 'withdraw' ? 'closed' : 'withdraw'))}
            >
              Withdraw
            </button>
          )}
          {goal.status === 'active' && (
            <button
              className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
              onClick={() => setMode((m) => (m === 'deposit' ? 'closed' : 'deposit'))}
            >
              Deposit
            </button>
          )}
        </div>
      </div>
      <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '10px', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${pct}%`, backgroundColor: 'var(--itunda-indigo)' }} />
      </div>
      {goal.monthlyContribution > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>
          Auto-saves {goal.monthlyContribution.toLocaleString()} RWF/month
        </p>
      )}
      {mode !== 'closed' && (
        needsDeviceVerification ? (
          <div style={{ marginTop: '10px' }}>
            {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
                full account. handleDeposit/handleWithdraw reset needsDeviceVerification
                themselves. */}
            <DeviceStepUpPrompt onVerified={() => (mode === 'deposit' ? handleDeposit() : handleWithdraw())} onCancel={() => setMode('closed')} />
          </div>
        ) : (
          <form onSubmit={mode === 'deposit' ? handleDeposit : handleWithdraw} style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
            <input
              type="number" min="1" max={mode === 'withdraw' ? goal.currentAmount : undefined} required value={amount} onChange={(e) => setAmount(e.target.value)}
              placeholder="Amount (RWF)"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {busy ? '…' : mode === 'deposit' ? 'Add' : 'Withdraw'}
            </button>
          </form>
        )
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (2026-08-19, direct user-confirmed sourcing:
// Toss's own real, published Product Principles doc names this explicitly -- "하나의
// 화면은 하나의 메시지만 표현한다," one screen expresses one message only, excess
// information actively removed rather than just deprioritized). This card used to ask
// 3 real decisions (goal name, target amount, monthly auto-save) on one page at once --
// a real, concrete violation, not a stylistic nitpick. Rebuilt as a real step flow,
// matching the exact step-machine convention TransferFlow (Section 189) already
// established for the identical reason. Also closes a real, separate capability gap
// found along the way: lib/savings.ts's own createGoal already accepts a real
// targetDate (Android's NewSavingsGoalDialog already collects it), but this form never
// did -- added as part of the same optional final step, not a second unrelated change.
type CreateGoalStep = 'closed' | 'name' | 'amount' | 'plan';

const GOAL_STEP_LABELS = ['Name', 'Amount', 'Auto-save'];

function CreateGoalForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateGoalStep>('closed');
  const [name, setName] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [monthlyContribution, setMonthlyContribution] = useState('');
  const [targetDate, setTargetDate] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setTargetAmount('');
    setMonthlyContribution('');
    setTargetDate('');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('name')}
      >
        <IconAdd size={16} /> New savings goal
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createGoal(name.trim(), Number(targetAmount), monthlyContribution ? Number(monthlyContribution) : undefined, targetDate || undefined);
      reset();
      showToast('Savings goal created.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('amount'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={GOAL_STEP_LABELS} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What are you saving for?</h3>
            <button type="button" aria-label="Cancel" onClick={reset} style={{ background: 'none', border: 'none' }}>
              <IconClose size={20} color="var(--itunda-grey-500)" />
            </button>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. Emergency Fund" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'amount') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(targetAmount) > 0) setStep('plan'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(targetAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={GOAL_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much do you want to save for &ldquo;{name.trim()}&rdquo;?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Target amount (RWF)" value={targetAmount} onChange={(e) => setTargetAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy}>{busy ? 'Creating…' : 'Create goal'}</IdsButton>}>
      <ProgressStepper activeStepIndex={2} steps={GOAL_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('amount')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Add auto-save details (optional)</h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '12px' }}>
        <input
          type="number" min="0" placeholder="Monthly auto-save (optional)" value={monthlyContribution} onChange={(e) => setMonthlyContribution(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="date" placeholder="Target date (optional)" value={targetDate} onChange={(e) => setTargetDate(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      </div>
    </FullScreenFlow>
  );
}

// Real Kakao Bank 모임통장 (group/shared account) -- see lib/groupAccounts.ts's doc
// comment. Backend enforces real owner-only withdrawal/invite authority; this view's
// job is just to reflect that honestly (buttons the caller can't actually use are
// hidden, not disabled-with-no-explanation).
function GroupAccountDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<GroupAccountDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10) -- see the Talk conversation view's own identical
  // pendingDeviceRetryRef for the full account: deposit and withdraw share this one
  // flag+prompt, so retrying has to redo whichever one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  const myUserId = getStoredUser()?.id;

  // Real KakaoBank 회비 (dues) management (2026-07-26) -- see
  // GroupAccountService.setDuesAmount's own doc comment.
  const [dues, setDues] = useState<GroupAccountDuesStatus | null>(null);
  const showDuesSkeleton = useDeferredLoading(dues === null);
  const [duesAmountInput, setDuesAmountInput] = useState('');
  const [duesBusy, setDuesBusy] = useState(false);
  const [remindedCount, setRemindedCount] = useState<number | null>(null);

  const loadDues = () => {
    fetchGroupAccountDues(id).then(setDues).catch(() => {});
  };

  const load = () => {
    setError(null);
    fetchGroupAccount(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    loadDues();
  };
  useEffect(load, []);

  const handleSetDues = async (e: React.FormEvent) => {
    e.preventDefault();
    setDuesBusy(true);
    setError(null);
    try {
      await setGroupAccountDuesAmount(id, Number(duesAmountInput));
      setDuesAmountInput('');
      loadDues();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const handleClearDues = async () => {
    setDuesBusy(true);
    setError(null);
    try {
      await setGroupAccountDuesAmount(id, null);
      loadDues();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const handleRemindUnpaid = async () => {
    setDuesBusy(true);
    setError(null);
    setRemindedCount(null);
    try {
      const count = await requestUnpaidGroupAccountDues(id);
      setRemindedCount(count);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDuesBusy(false);
    }
  };

  const isOwner = detail?.groupAccount.ownerId === myUserId;
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks: a hook can't be skipped on some
  // renders), using detail?.balance so it's already correct once detail loads.
  const animatedBalance = useCountUp(detail?.balance ?? 0);

  const handleDeposit = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await depositToGroupAccount(id, Number(amount));
      setAmount('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleDeposit;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await withdrawFromGroupAccount(id, Number(amount));
      setAmount('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleWithdraw;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await inviteGroupAccountMember(id, phoneNumber.trim());
      setPhoneNumber('');
      load();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): the
      // organizer inviting a phone number already in the group isn't really a
      // failure -- the desired end state (that person being a member) is already
      // true. Resolve forward the same way a self-registration retry would.
      if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') {
        setPhoneNumber('');
        load();
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return showSkeleton ? <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  // Real fix (2026-08-24, flat-design sweep): 5 distinct non-exclusive sections
  // shown together -- reused .itunda-flat-section for section-boundary dividers.
  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to group accounts</button>

      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{detail.groupAccount.name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0' }}>{animatedBalance.toLocaleString()} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{detail.members.length} member{detail.members.length === 1 ? '' : 's'}</p>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Members</h3>
        {detail.members.map((m) => (
          <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
            {m.isOwner && <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700 }}>Organizer</span>}
          </div>
        ))}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Monthly dues</h3>
        {dues === null ? (
          showDuesSkeleton ? <div className="skeleton" style={{ height: '40px', borderRadius: '8px' }} /> : null
        ) : dues.duesAmount === null ? (
          isOwner ? (
            <form onSubmit={handleSetDues} style={{ display: 'flex', gap: '8px' }}>
              <input
                type="number" min="1" required value={duesAmountInput} onChange={(e) => setDuesAmountInput(e.target.value)}
                placeholder="Monthly dues (RWF)"
                style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
              />
              <button type="submit" className="itunda-btn itunda-btn-primary" disabled={duesBusy}>{duesBusy ? '…' : 'Set'}</button>
            </form>
          ) : (
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>The organizer hasn't set a monthly dues amount.</p>
          )
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{dues.duesAmount.toLocaleString()} RWF / month · {dues.cycleMonth}</p>
            {dues.members.map((m) => {
              const duesAmount = dues.duesAmount as number;
              return (
                <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
                  <span style={{ color: m.paid ? '#1E8E4F' : 'var(--itunda-grey-500)', fontWeight: m.paid ? 700 : 400 }}>
                    {m.paid ? '✓ Paid' : `${m.contributedAmount.toLocaleString()} / ${duesAmount.toLocaleString()}`}
                  </span>
                </div>
              );
            })}
            {isOwner && (
              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={duesBusy} onClick={handleRemindUnpaid}>
                  {duesBusy ? '…' : 'Remind unpaid members'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={duesBusy} onClick={handleClearDues}>Clear</button>
              </div>
            )}
            {remindedCount !== null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {remindedCount === 0 ? 'Everyone has already paid or been reminded this month.' : `Reminded ${remindedCount} member${remindedCount === 1 ? '' : 's'}.`}
              </p>
            )}
          </div>
        )}
      </div>

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '16px' }}>
          <DeviceStepUpPrompt
            onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
            onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); }}
          />
        </div>
      ) : (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{isOwner ? 'Deposit or withdraw' : 'Deposit'}</h3>
          <input
            type="number" min="1" required value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="button" onClick={handleDeposit} className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy || !amount}>
              {busy ? '…' : 'Deposit'}
            </button>
            {isOwner && (
              // Real Kakao Bank behavior: only the organizer can withdraw/settle --
              // this button is only rendered for the owner, not just disabled.
              <button type="button" onClick={handleWithdraw} className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busy || !amount}>
                {busy ? '…' : 'Withdraw'}
              </button>
            )}
          </div>
        </div>
      )}

      {isOwner && (
        <form onSubmit={handleInvite} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Invite a member</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Phone number"
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
          </div>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

function CreateGroupAccountForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <IconAdd size={16} /> New group account
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createGroupAccount(name);
      setName('');
      setOpen(false);
      showToast('Group account created.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a lone toggled
  // form section (docs/UI_UX_GUIDELINES.md §10).
  return (
    <form onSubmit={handleSubmit} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        type="text" required placeholder="Group name (e.g. Roommates)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </form>
  );
}

function GroupAccountsSection() {
  const { t } = useI18n();
  const [accounts, setAccounts] = useState<GroupAccount[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);
  // Real, sourced finding (KakaoPay tech blog) -- see useDeferredLoading's own doc
  // comment for the full account. Proof-of-concept site for a real, multi-session
  // sweep: this file alone has ~105 other `=== null` skeleton call sites still
  // showing instantly, not yet migrated.
  const showSkeleton = useDeferredLoading(accounts === null);

  const load = () => {
    setError(null);
    fetchMyGroupAccounts().then(setAccounts).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <GroupAccountDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>Group accounts</h3>
      <CreateGroupAccountForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {accounts === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : accounts.length === 0 ? (
        <EmptyState message="No group accounts yet -- start one to save or split expenses with others." />
      ) : (
        accounts.map((a) => (
          // Real fix (2026-08-24, direct user directive, real Toss reference): dropped
          // itunda-flat-section -- that class's border-bottom divider is for separating
          // distinct sections, not individual rows within one repeated list, matching
          // the same fix just made to Android's ShellSection.
          <button
            key={a.id}
            onClick={() => setOpenId(a.id)}
            style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{a.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Tap to view balance and members</p>
          </button>
        ))
      )}
    </div>
  );
}

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See
// lib/ikimina.ts's own doc comment for the full sourced account. Genuinely the first
// feature in this codebase not sourced from Toss/Kakao/Naver/Coupang -- a real,
// currently-live Rwandan financial practice, sibling to GroupAccountsSection above but
// structurally distinct (a rotating payout recipient, not one permanent owner).
function IkiminaSection() {
  const { t } = useI18n();
  const [ikiminas, setIkiminas] = useState<Ikimina[] | null>(null);
  const showSkeleton = useDeferredLoading(ikiminas === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyIkiminas().then(setIkiminas).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <IkiminaDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>Ikimina (rotating savings)</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>
        Everyone contributes the same amount each round; one member takes home the full pot, in turn.
      </p>
      <CreateIkiminaForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {ikiminas === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : ikiminas.length === 0 ? (
        <EmptyState message="No ikimina groups yet -- start one with people you trust." />
      ) : (
        ikiminas.map((k) => (
          // Real fix (2026-08-24): see accounts.map's own identical comment above.
          <button
            key={k.id}
            onClick={() => setOpenId(k.id)}
            style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
          >
            <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{k.name}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              {k.status === 'FORMING' ? 'Forming — invite members before starting' : k.status === 'ACTIVE' ? `Round ${k.currentRound}` : 'Completed'}
            </p>
          </button>
        ))
      )}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up, same real sourcing:
// Toss's own published Product Principles doc, "하나의 화면은 하나의 메시지만 표현한다").
// This card asked 4 real decisions at once (group name, contribution amount, cycle
// frequency, member cap) -- rebuilt as a real step flow with the real ProgressStepper
// indicator, matching the exact convention CreateGoalForm (Section 198) already
// established. All 4 fields are genuinely required (unlike CreateGoalForm's optional
// final step), so each gets its own real step rather than being grouped.
type CreateIkiminaStep = 'closed' | 'name' | 'contribution' | 'frequency' | 'members';
const IKIMINA_STEP_LABELS = ['Name', 'Contribution', 'Frequency', 'Members'];

function CreateIkiminaForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateIkiminaStep>('closed');
  const [name, setName] = useState('');
  const [contributionAmount, setContributionAmount] = useState('');
  const [cycleFrequencyDays, setCycleFrequencyDays] = useState('30');
  const [memberCap, setMemberCap] = useState('10');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setContributionAmount('');
    setCycleFrequencyDays('30');
    setMemberCap('10');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('name')}
      >
        <IconAdd size={16} /> New ikimina
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createIkimina(name.trim(), Number(contributionAmount), Number(cycleFrequencyDays), Number(memberCap));
      reset();
      showToast('Ikimina group created.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('contribution'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={IKIMINA_STEP_LABELS} />
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What's your group called?</h3>
            <button type="button" aria-label="Cancel" onClick={reset} style={{ background: 'none', border: 'none' }}>
              <IconClose size={20} color="var(--itunda-grey-500)" />
            </button>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. Umuryango" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'contribution') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(contributionAmount) > 0) setStep('frequency'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(contributionAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={IKIMINA_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much does each member contribute per round?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Contribution (RWF)" value={contributionAmount} onChange={(e) => setContributionAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'frequency') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('members')}>Next</IdsButton>}>
        <ProgressStepper activeStepIndex={2} steps={IKIMINA_STEP_LABELS} />
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button type="button" aria-label="Back" onClick={() => setStep('contribution')} style={{ background: 'none', border: 'none', display: 'flex' }}>
            <IconBack size={20} color="var(--itunda-grey-700)" />
          </button>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How often does each round happen?</h3>
        </div>
        <select
          value={cycleFrequencyDays} onChange={(e) => setCycleFrequencyDays(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
        >
          <option value="7">Weekly</option>
          <option value="30">Monthly</option>
        </select>
      </FullScreenFlow>
    );
  }

  return (
    <FullScreenFlow
      bottomCTA={
        <IdsButton fullWidth onClick={handleCreate} disabled={busy || !(Number(memberCap) >= 2 && Number(memberCap) <= 15)}>
          {busy ? 'Creating…' : 'Create ikimina'}
        </IdsButton>
      }
    >
      <ProgressStepper activeStepIndex={3} steps={IKIMINA_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('frequency')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How many members, at most?</h3>
      </div>
      <input
        type="number" min="2" max="15" required autoFocus placeholder="Max members (2-15)" value={memberCap} onChange={(e) => setMemberCap(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

function IkiminaDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<IkiminaDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [payoutMessage, setPayoutMessage] = useState<string | null>(null);
  const myUserId = getStoredUser()?.id;

  const load = () => {
    setError(null);
    fetchIkimina(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);
  // Real Toss motion pattern -- see useCountUp's own doc comment. Called before
  // either early return below (Rules of Hooks), using detail?.balance so it's
  // already correct once detail loads.
  const animatedBalance = useCountUp(detail?.balance ?? 0);

  if (error && !detail) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return showSkeleton ? <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  const { ikimina, members, currentRoundContributions } = detail;
  const isOrganizer = ikimina.organizerId === myUserId;
  const myMember = members.find((m) => m.userId === myUserId);
  const iContributed = currentRoundContributions.find((c) => c.userId === myUserId)?.contributed ?? false;
  const allContributed = currentRoundContributions.length > 0 && currentRoundContributions.every((c) => c.contributed);
  const pot = ikimina.contributionAmount * members.length;

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await inviteIkiminaMember(id, phoneNumber.trim());
      setPhoneNumber('');
      load();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): same
      // resolve-forward as the identical GroupAccount invite shape.
      if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') {
        setPhoneNumber('');
        load();
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setBusy(false);
    }
  };

  const handleStart = async () => {
    setBusy(true);
    setError(null);
    try {
      await startIkiminaCycle(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleContribute = async () => {
    setBusy(true);
    setError(null);
    setPayoutMessage(null);
    try {
      const result = await contributeToIkimina(id);
      // Real bug fix: this contribution may have just completed the round, in which
      // case the backend already auto-triggered the payout -- surface that instead of
      // silently leaving the member to wonder why the round advanced.
      if (result.payout) {
        setPayoutMessage(`Round complete — ${result.payout.amount.toLocaleString()} RWF paid out.`);
      }
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handlePayout = async () => {
    setBusy(true);
    setError(null);
    setPayoutMessage(null);
    try {
      const result = await triggerIkiminaPayout(id);
      setPayoutMessage(`${result.amount.toLocaleString()} RWF paid out for round ${result.ikimina.currentRound - 1}.`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): distinct non-exclusive sections shown
  // together -- reused .itunda-flat-section for section-boundary dividers.
  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to ikimina</button>

      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{ikimina.name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0' }}>{animatedBalance.toLocaleString()} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          {ikimina.status === 'FORMING'
            ? `Forming — ${members.length} of up to ${ikimina.memberCap} members`
            : ikimina.status === 'ACTIVE'
              ? `Round ${ikimina.currentRound} of ${members.length} · ${ikimina.contributionAmount.toLocaleString()} RWF each · pot ${pot.toLocaleString()} RWF`
              : 'Every member has been paid — this ikimina is complete'}
        </p>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Rotation order</h3>
        {members.map((m) => {
          const contributed = currentRoundContributions.find((c) => c.userId === m.userId)?.contributed ?? false;
          return (
            <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <span>
                #{m.payoutOrder} {m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}{m.isOrganizer ? ' · Organizer' : ''}
              </span>
              <span style={{ color: m.hasReceivedPayout ? '#1E8E4F' : ikimina.status === 'ACTIVE' && contributed ? '#1E8E4F' : 'var(--itunda-grey-500)', fontWeight: 700 }}>
                {m.hasReceivedPayout ? '✓ Paid' : ikimina.status === 'ACTIVE' ? (contributed ? '✓ Contributed' : 'Pending') : ''}
              </span>
            </div>
          );
        })}
      </div>

      {ikimina.status === 'FORMING' && isOrganizer && (
        <div className="itunda-flat-section">
          <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} disabled={busy || members.length < 2} onClick={handleStart}>
            {busy ? '…' : members.length < 2 ? 'Invite at least 1 more member to start' : 'Start the cycle'}
          </button>
        </div>
      )}

      {ikimina.status === 'ACTIVE' && myMember && (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Round {ikimina.currentRound}</h3>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || iContributed} onClick={handleContribute}>
            {busy ? '…' : iContributed ? '✓ You contributed this round' : `Contribute ${ikimina.contributionAmount.toLocaleString()} RWF`}
          </button>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy || !allContributed} onClick={handlePayout}>
            {busy ? '…' : allContributed ? 'Release this round\'s payout' : 'Waiting for everyone to contribute'}
          </button>
          {payoutMessage && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: '#1E8E4F' }}>{payoutMessage}</p>}
        </div>
      )}

      {ikimina.status === 'FORMING' && isOrganizer && (
        <form onSubmit={handleInvite} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Invite a member</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Phone number"
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
          </div>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model. See lib/sacco.ts's own doc comment for the full sourced
// account. Sibling to IkiminaSection above (both are Rwanda-specific, not sourced
// from Toss/Kakao/Naver/Coupang) but a genuinely distinct mechanic: real shares +
// periodic real dividends, not a rotating pot.
function SaccoSection() {
  const { t } = useI18n();
  const [shareholding, setShareholding] = useState<SaccoShareholding | null | undefined>(undefined);
  const [currentValue, setCurrentValue] = useState<number | null>(null);
  const [dividends, setDividends] = useState<SaccoDividendPayout[] | null>(null);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMySaccoShareholding()
      .then((r) => { setShareholding(r.shareholding); setCurrentValue(r.currentValue); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchMySaccoDividendHistory().then(setDividends).catch(() => setDividends([]));
  };
  useEffect(load, []);

  const handleBuy = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    setError(null);
    try {
      await buySaccoShares(value);
      setAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRedeem = async () => {
    const value = Number(amount);
    if (!value || value <= 0) return;
    setBusy(true);
    setError(null);
    try {
      await redeemSaccoShares(value);
      setAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>SACCO shares</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>
        Buy real shares in itunda's own SACCO pool and earn periodic dividends, the same real cooperative model as Rwanda's 416 Umurenge SACCOs.
      </p>
      <div className="itunda-flat-section">
        {shareholding === undefined ? (
          <div style={{ height: '48px' }} />
        ) : (
          <>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Shares held</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700 }}>{(shareholding?.sharesHeld ?? 0).toLocaleString()} RWF</p>
            {currentValue != null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Total contributed: {(shareholding?.totalContributed ?? 0).toLocaleString()} RWF</p>
            )}
          </>
        )}
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
        {/* Real gap found live (2026-08-10) via an actual CDP screenshot of this exact
            section (just promoted to Home this session): three real elements --
            an input plus two buttons ("Buy" and "Redeem") -- in one unwrapped flex
            row with no overflow handling. "Redeem" was cut off past the visible
            edge on a real 390px viewport. flexWrap lets Buy/Redeem drop to their own
            row on a narrow screen instead of vanishing -- better here than a
            horizontal scroll, since these are primary form actions, not a nav list. */}
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '8px', marginTop: '12px' }}>
          <input
            type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ flex: '1 1 140px', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleBuy} style={{ flexShrink: 0 }}>Buy</button>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleRedeem} style={{ flexShrink: 0 }}>Redeem</button>
        </div>
      </div>
      {dividends && dividends.length > 0 && (
        <div className="itunda-flat-section">
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '8px' }}>Dividend history</p>
          {dividends.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '4px 0' }}>
              <span style={{ color: 'var(--itunda-grey-500)' }}>{new Date(d.createdAt).toLocaleDateString()}</span>
              <span style={{ fontWeight: 700 }}>+{d.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real KakaoBank 26주적금 (26-week savings) -- see lib/weeklySavings.ts's own doc
// comment. Sibling to GroupAccountDetailView/CreateGroupAccountForm/
// GroupAccountsSection above, same list -> detail shape, but this product's real
// differentiator (escalating auto-debit, streak-gated bonus rate) is surfaced
// explicitly in copy rather than looking like a generic savings account.
function escalationLabel(rate: number): string {
  return rate === 0 ? 'Flat (no step-up)' : `+${Math.round(rate * 100)}% every ${WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks`;
}

function WeeklySavingsPlanDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<WeeklySavingsPlanDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real per-bucket ledger (2026-08-31) -- see BucketTransactionList's own doc
  // comment. Replaces the old "Installments" list below with the real transaction
  // ledger this plan's own dedicated account always had, just never exposed.
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);
  // Real fix (2026-08-10) -- see the Talk conversation view's own identical
  // pendingDeviceRetryRef for the full account: cancel and withdraw share this one
  // flag+prompt, so retrying has to redo whichever one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchWeeklySavingsPlanTransactions(id).then(setTransactions).catch(() => {});
  };
  useEffect(load, []);

  // Same real device step-up gate as GroupAccountDetailView/GoalCard's deposit/
  // withdraw handlers above -- cancel/withdraw both move real money out of this
  // plan's account, so an untrusted device hits the same DEVICE_NOT_VERIFIED 403.
  const handleCancel = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await cancelWeeklySavingsPlan(id);
      setMessage(result.message);
      setConfirmingCancel(false);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleCancel;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await withdrawWeeklySavingsPlan(id);
      setMessage(result.message);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleWithdraw;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return showSkeleton ? <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  const { plan, accountBalance } = detail;
  const pct = Math.min(100, Math.round((plan.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
  const currentRate = plan.streakBroken ? plan.baseRate : plan.baseRate + plan.bonusRate;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to 26-week savings</button>

      <div className="itunda-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>{plan.name} · Week {plan.weeksElapsed} of {WEEKLY_SAVINGS_TERM_WEEKS}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{accountBalance.toLocaleString()} RWF</p>
        <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'rgba(255,255,255,0.3)', marginTop: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, backgroundColor: '#fff' }} />
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '10px', opacity: 0.9 }}>
          {plan.installmentsCollected} installment{plan.installmentsCollected === 1 ? '' : 's'} collected · earning {currentRate}% real annual rate
        </p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.9 }}>
          {plan.streakBroken
            ? 'Streak broken — bonus rate forfeited for the rest of this plan'
            : `On streak — stay unbroken to keep the +${plan.bonusRate}% bonus at maturity`}
        </p>
      </div>

      {/* Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
          together -- reused .itunda-flat-section for section-boundary dividers.
          The hero balance card above deliberately kept its gradient itunda-card
          styling -- a real branded treatment, not reflexive wrapping. */}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Plan details</h3>
        <Row label="Status" value={plan.status} />
        <Row label="Base weekly amount" value={`${plan.baseWeeklyAmount.toLocaleString()} RWF`} />
        <Row label="Escalation" value={escalationLabel(plan.escalationRate)} />
        <Row label="Base rate + streak bonus" value={`${plan.baseRate}% + ${plan.bonusRate}%`} />
        {plan.status === 'ACTIVE' && <Row label="Next installment due" value={new Date(plan.nextInstallmentDueAt).toLocaleDateString()} />}
        {plan.totalInterestPaid != null && <Row label="Interest paid" value={`${plan.totalInterestPaid.toLocaleString()} RWF`} />}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Transactions</h3>
        <BucketTransactionList transactions={transactions} />
      </div>

      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', marginBottom: '10px' }}>{message}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}

      {needsDeviceVerification ? (
        <DeviceStepUpPrompt
          onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
          onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); setConfirmingCancel(false); }}
        />
      ) : (
        <>
          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              conditional action section. */}
          {plan.status === 'ACTIVE' && (
            <div style={{ padding: '10px 0' }}>
              {confirmingCancel ? (
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '10px' }}>
                    Cancelling now pays out your principal plus base-rate interest, but permanently forfeits the +{plan.bonusRate}% streak bonus. Continue?
                  </p>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setConfirmingCancel(false)} disabled={busy}>Keep saving</button>
                    <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} onClick={handleCancel} disabled={busy}>{busy ? '…' : 'Cancel plan'}</button>
                  </div>
                </div>
              ) : (
                <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setConfirmingCancel(true)} disabled={busy}>
                  Cancel plan (early withdrawal)
                </button>
              )}
            </div>
          )}

          {plan.status === 'MATURED' && !plan.withdrawnAt && (
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={handleWithdraw} disabled={busy}>
              {busy ? '…' : `Withdraw ${accountBalance.toLocaleString()} RWF to main account`}
            </button>
          )}
        </>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
      <span style={{ color: 'var(--itunda-grey-500)' }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up) -- see
// CreateIkiminaForm's own identical doc comment above for the full real sourcing.
// This card asked 3 real decisions at once (plan name, weekly amount, escalation
// rate) -- rebuilt as a real step flow with the real ProgressStepper indicator.
type CreateWeeklySavingsPlanStep = 'closed' | 'intro' | 'name' | 'amount' | 'escalation';
const WEEKLY_SAVINGS_STEP_LABELS = ['Name', 'Amount', 'Escalation'];

function CreateWeeklySavingsPlanForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateWeeklySavingsPlanStep>('closed');
  const [name, setName] = useState('');
  const [baseWeeklyAmount, setBaseWeeklyAmount] = useState('');
  const [escalationRate, setEscalationRate] = useState(0.10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setBaseWeeklyAmount('');
    setEscalationRate(0.10);
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('intro')}
      >
        <IconAdd size={16} /> New 26-week savings plan
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createWeeklySavingsPlan(name.trim(), Number(baseWeeklyAmount), escalationRate);
      reset();
      showToast('26-week plan started.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real Toss product-intro pattern (rule 13) -- see OpenForeignAccountFlow and
  // CreateGrow31SavingsPlanForm's own 'intro' step, the first two applications. Not
  // counted in WEEKLY_SAVINGS_STEP_LABELS's progress -- it's a preamble, not a wizard
  // step. WeeklySavings' bonus is stricter than Grow31's: Grow31 locks in a partial
  // bonus for the longest streak reached even after a break, but WeeklySavings'
  // bonus is all-or-nothing (WeeklySavingsService.matures: `plan.baseRate + (if
  // (plan.streakBroken) 0.0 else plan.bonusRate)`) -- one missed week, or any early
  // withdrawal, forfeits it permanently. Worth stating plainly rather than blurring
  // the two products' real mechanics together.
  if (step === 'intro') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('name')}>Continue</IdsButton>}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <h2 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, maxWidth: '260px' }}>A weekly habit that grows on its own</h2>
          <button type="button" aria-label="Close" onClick={reset} style={{ background: 'none', border: 'none', display: 'flex', padding: '4px' }}>
            <IconClose size={22} color="var(--itunda-grey-500)" />
          </button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>A real 26-week term deposit</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Like KakaoBank's 26주적금: your weekly amount auto-debits from your main account every week for {WEEKLY_SAVINGS_TERM_WEEKS} weeks — nothing to top up manually.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Base 5% + a 3% bonus for staying unbroken</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              The 3% bonus is all-or-nothing: miss even one week's installment, or withdraw early, and the bonus is forfeited for good — unlike a 31-day plan's partial-credit streak, this one doesn't have a middle ground.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Your weekly amount can step up automatically</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Choose an escalation rate and your weekly amount compounds up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks — start small and build up, instead of committing to one fixed amount for all 26 weeks.
            </p>
          </div>
        </div>
      </FullScreenFlow>
    );
  }

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('amount'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={WEEKLY_SAVINGS_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('intro')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>What are you saving toward?</h3>
          </div>
          <input
            type="text" required autoFocus placeholder="e.g. New Laptop Fund" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  if (step === 'amount') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (Number(baseWeeklyAmount) > 0) setStep('escalation'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!(Number(baseWeeklyAmount) > 0)}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={1} steps={WEEKLY_SAVINGS_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much per week, to start?</h3>
          </div>
          <input
            type="number" min="1" required autoFocus placeholder="Base weekly amount (RWF)" value={baseWeeklyAmount} onChange={(e) => setBaseWeeklyAmount(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy}>{busy ? 'Creating…' : 'Create plan'}</IdsButton>}>
      <ProgressStepper activeStepIndex={2} steps={WEEKLY_SAVINGS_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('amount')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Step up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks?</h3>
      </div>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px', marginTop: '8px' }}>
        {WEEKLY_SAVINGS_ESCALATION_RATES.map((rate) => (
          <button
            key={rate}
            type="button"
            onClick={() => setEscalationRate(rate)}
            className={escalationRate === rate ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
            style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {rate === 0 ? 'Flat' : `+${Math.round(rate * 100)}%`}
          </button>
        ))}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

function WeeklySavingsSection() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<WeeklySavingsPlan[] | null>(null);
  const showSkeleton = useDeferredLoading(plans === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlans().then(setPlans).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <WeeklySavingsPlanDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      {/* Real gap found live (2026-08-31, direct user follow-up: "keep improving
          itunda bank to more like toss bank"): matches the real Toss Bank
          reference's own catalog pattern (e.g. "31일 적금 -- 1%~10% p.a." shown
          directly in the product list, no tap required) -- Android's own
          BankHubScreen already states this exact real rate inline
          ("$BANK_HUB_WEEKLY_SAVINGS_BASE_RATE% base rate, escalates weekly"),
          web/iOS never did. Sourced from WeeklySavingsService's own real
          BASE_RATE/BONUS_RATE constants, not invented. */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>26-week savings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>5% base rate, escalates weekly</p>
      <CreateWeeklySavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : plans.length === 0 ? (
        <EmptyState message="No 26-week savings plans yet — start one with an escalating weekly auto-debit and a streak-gated bonus rate." />
      ) : (
        plans.map((p) => {
          const pct = Math.min(100, Math.round((p.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
          return (
            // Real fix (2026-08-24): see accounts.map's own identical comment above.
            <button
              key={p.id}
              onClick={() => setOpenId(p.id)}
              style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{p.name}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{p.status}</p>
              </div>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {p.currentAmount.toLocaleString()} RWF · week {p.weeksElapsed}/{WEEKLY_SAVINGS_TERM_WEEKS}
                {p.streakBroken ? ' · streak broken' : ' · on streak'}
              </p>
              <div style={{ height: '5px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, backgroundColor: p.streakBroken ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)' }} />
              </div>
            </button>
          );
        })
      )}
    </div>
  );
}

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent -- see
// lib/grow31Savings.ts's own doc comment. Distinct from WeeklySavings above: a deposit
// is an explicit daily user action ("Save today"), not a scheduled auto-debit.
function Grow31SavingsPlanDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const { t } = useI18n();
  const [detail, setDetail] = useState<Grow31SavingsPlanDetail | null>(null);
  const showSkeleton = useDeferredLoading(detail === null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10 pattern, same as WeeklySavingsPlanDetailView above): deposit/
  // cancel/withdraw all share this one flag+prompt, so retrying has to redo whichever
  // one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  // Real per-bucket ledger (2026-08-31) -- see BucketTransactionList's own doc
  // comment. Replaces the old "Deposits" list below with the real transaction
  // ledger this plan's own dedicated account always had, just never exposed.
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);

  const load = () => {
    setError(null);
    fetchGrow31SavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchGrow31SavingsPlanTransactions(id).then(setTransactions).catch(() => {});
  };
  useEffect(load, []);

  const handleDeposit = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await depositGrow31SavingsToday(id);
      setDetail(result);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleDeposit;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await cancelGrow31SavingsPlan(id);
      setMessage(result.message);
      setConfirmingCancel(false);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleCancel;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleWithdraw = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const result = await withdrawGrow31SavingsPlan(id);
      setMessage(result.message);
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = handleWithdraw;
        setNeedsDeviceVerification(true);
      } else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return showSkeleton ? <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} /> : null;

  const { plan, accountBalance } = detail;
  const pct = Math.min(100, Math.round((plan.daysElapsed / GROW31_TERM_DAYS) * 100));
  const bonus = grow31BonusRateForStreak(plan.longestStreak);
  const today = new Date().toISOString().slice(0, 10);
  const alreadyDepositedToday = plan.lastDepositDate === today;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to 31-day savings</button>

      <div className="itunda-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', opacity: 0.85 }}>{plan.name} · Day {Math.min(plan.daysElapsed, GROW31_TERM_DAYS)} of {GROW31_TERM_DAYS}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '6px 0' }}>{accountBalance.toLocaleString()} RWF</p>
        <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'rgba(255,255,255,0.3)', marginTop: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, backgroundColor: '#fff' }} />
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', marginTop: '10px', opacity: 0.9 }}>
          Current streak {plan.currentStreak} days · longest {plan.longestStreak} days
        </p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.9 }}>
          {bonus > 0 ? `+${bonus}% bonus locked in on top of the ${plan.baseRate}% base rate` : 'Save 3 days in a row to unlock your first bonus tier'}
        </p>
      </div>

      {/* Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown
          together -- reused .itunda-flat-section for section-boundary dividers.
          The hero balance card above deliberately kept its gradient itunda-card
          styling -- a real branded treatment, not reflexive wrapping. */}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Plan details</h3>
        <Row label="Status" value={plan.status} />
        <Row label="Daily amount" value={`${plan.dailyAmount.toLocaleString()} RWF`} />
        <Row label="Base rate" value={`${plan.baseRate}%`} />
        {plan.totalInterestPaid != null && <Row label="Total interest paid" value={`${plan.totalInterestPaid.toLocaleString()} RWF`} />}
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Transactions</h3>
        <BucketTransactionList transactions={transactions} />
      </div>

      {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)', marginBottom: '10px' }}>{message}</p>}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}

      {needsDeviceVerification ? (
        <DeviceStepUpPrompt
          onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
          onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); setConfirmingCancel(false); }}
        />
      ) : (
        <>
          {plan.status === 'ACTIVE' && !confirmingCancel && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {!alreadyDepositedToday ? (
                <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={handleDeposit} disabled={busy}>
                  {busy ? '…' : `Save today (+${plan.dailyAmount.toLocaleString()} RWF)`}
                </button>
              ) : (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green)', fontWeight: 600 }}>
                  You've already saved today — come back tomorrow to keep your streak.
                </p>
              )}
              <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setConfirmingCancel(true)} disabled={busy}>
                Cancel plan (early withdrawal)
              </button>
            </div>
          )}

          {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
              conditional confirmation section. */}
          {plan.status === 'ACTIVE' && confirmingCancel && (
            <div style={{ padding: '10px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginBottom: '10px' }}>
                Cancelling now forfeits your streak bonus — you'll only get principal plus base-rate interest, paid out immediately. This can't be undone.
              </p>
              <div style={{ display: 'flex', gap: '8px' }}>
                <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setConfirmingCancel(false)} disabled={busy}>Keep plan</button>
                <button className="itunda-btn itunda-btn-danger" style={{ flex: 1 }} onClick={handleCancel} disabled={busy}>{busy ? '…' : 'Confirm cancel'}</button>
              </div>
            </div>
          )}

          {plan.status === 'MATURED' && !plan.withdrawnAt && (
            <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={handleWithdraw} disabled={busy}>
              {busy ? '…' : `Withdraw ${accountBalance.toLocaleString()} RWF to main account`}
            </button>
          )}
        </>
      )}
    </div>
  );
}

// Real Toss "One Thing per One Page" fix (Section 199 follow-up) -- see
// CreateIkiminaForm's own identical doc comment above for the full real sourcing.
// This card asked 2 real decisions at once (plan name, daily amount).
type CreateGrow31Step = 'closed' | 'intro' | 'name' | 'amount';
const GROW31_STEP_LABELS = ['Name', 'Daily amount'];
// Real, sourced streak-bonus tiers (tossbank.com/articles/savings-account,
// g-enews.com 2026-08-06, mirrored from Grow31SavingsService.bonusRateForStreak) --
// shown in full on the intro step (rule 13) rather than the single "up to +10%"
// summary line the name step used to carry alone.
const GROW31_BONUS_TIERS = [3, 7, 14, 21, 31] as const;

function CreateGrow31SavingsPlanForm({ onCreated }: { onCreated: () => void }) {
  const { t } = useI18n();
  const [step, setStep] = useState<CreateGrow31Step>('closed');
  const [name, setName] = useState('');
  const [dailyAmount, setDailyAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const reset = () => {
    setStep('closed');
    setName('');
    setDailyAmount('');
    setError(null);
  };

  if (step === 'closed') {
    return (
      <button
        className="itunda-btn itunda-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setStep('intro')}
      >
        <IconAdd size={16} /> New 31-day plan
      </button>
    );
  }

  const handleCreate = async () => {
    setBusy(true);
    setError(null);
    try {
      await createGrow31SavingsPlan(name.trim(), Number(dailyAmount));
      reset();
      showToast('31-day plan started.');
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  // Real Toss product-intro pattern (rule 13, 2026-08-26): a dedicated screen
  // explaining the real mechanics before the creation form starts, not part of the
  // Name/Daily-amount progress count -- matches ForeignCurrencyView's own
  // OpenForeignAccountFlow, the first application of this pattern.
  if (step === 'intro') {
    return (
      <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={() => setStep('name')}>Continue</IdsButton>}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '24px' }}>
          <h2 style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700, maxWidth: '260px' }}>Save a little every day, earn more the longer you keep it up</h2>
          <button type="button" aria-label="Close" onClick={reset} style={{ background: 'none', border: 'none', display: 'flex', padding: '4px' }}>
            <IconClose size={22} color="var(--itunda-grey-500)" />
          </button>
        </div>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '20px' }}>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>One small deposit, every day, for {GROW31_TERM_DAYS} days</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              Pick a fixed amount you can realistically save every single day. A base 1% rate applies from day one.
            </p>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>The longer your unbroken streak, the higher your bonus</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5, marginBottom: '10px' }}>
              Your bonus rate is locked in by the longest unbroken run of daily deposits you reach:
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {GROW31_BONUS_TIERS.map((days) => (
                <div key={days} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span style={{ color: 'var(--itunda-grey-700)' }}>{days === GROW31_TERM_DAYS ? `${days} days (full term)` : `${days}-day streak`}</span>
                  <span style={{ fontWeight: 700 }}>+{grow31BonusRateForStreak(days)}%</span>
                </div>
              ))}
            </div>
          </div>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Miss a day? You keep what you already earned</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', lineHeight: 1.5 }}>
              A missed day resets your current streak, but the longest streak you already reached still locks in that bonus rate at maturity — it isn't lost.
            </p>
          </div>
        </div>
      </FullScreenFlow>
    );
  }

  if (step === 'name') {
    return (
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) setStep('amount'); }}>
        <FullScreenFlow bottomCTA={<IdsButton type="submit" fullWidth disabled={!name.trim()}>Next</IdsButton>}>
          <ProgressStepper activeStepIndex={0} steps={GROW31_STEP_LABELS} />
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <button type="button" aria-label="Back" onClick={() => setStep('intro')} style={{ background: 'none', border: 'none', display: 'flex' }}>
              <IconBack size={20} color="var(--itunda-grey-700)" />
            </button>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Name your 31-day streak</h3>
          </div>
          <input
            type="text" required autoFocus placeholder="Plan name" value={name} onChange={(e) => setName(e.target.value)}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
          />
        </FullScreenFlow>
      </form>
    );
  }

  return (
    <FullScreenFlow bottomCTA={<IdsButton fullWidth onClick={handleCreate} disabled={busy || !(Number(dailyAmount) > 0)}>{busy ? 'Creating…' : 'Create plan'}</IdsButton>}>
      <ProgressStepper activeStepIndex={1} steps={GROW31_STEP_LABELS} />
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button type="button" aria-label="Back" onClick={() => setStep('name')} style={{ background: 'none', border: 'none', display: 'flex' }}>
          <IconBack size={20} color="var(--itunda-grey-700)" />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>How much can you save every day?</h3>
      </div>
      <input
        type="number" min="1" required autoFocus placeholder="Daily amount (RWF)" value={dailyAmount} onChange={(e) => setDailyAmount(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', width: '100%', boxSizing: 'border-box', marginTop: '12px' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
    </FullScreenFlow>
  );
}

function Grow31SavingsSection() {
  const { t } = useI18n();
  const [plans, setPlans] = useState<Grow31SavingsPlan[] | null>(null);
  const showSkeleton = useDeferredLoading(plans === null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGrow31SavingsPlans().then(setPlans).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  if (openId) {
    return <Grow31SavingsPlanDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      {/* Real gap found live (2026-08-31) -- see WeeklySavingsSection's identical
          fix above. Sourced from Grow31SavingsService.bonusRateForStreak's own
          real tier table (base 1%, up to +10% at a 31-day streak). */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>31-day savings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>Daily streak, up to 10% bonus rate</p>
      <CreateGrow31SavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : plans.length === 0 ? (
        <EmptyState message="No 31-day plans yet — save a small fixed amount every real day for an escalating streak bonus." />
      ) : (
        plans.map((p) => {
          const pct = Math.min(100, Math.round((p.daysElapsed / GROW31_TERM_DAYS) * 100));
          const bonus = grow31BonusRateForStreak(p.longestStreak);
          return (
            // Real fix (2026-08-24): see accounts.map's own identical comment above.
            <button
              key={p.id}
              onClick={() => setOpenId(p.id)}
              style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none', padding: '10px 0' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{p.name}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{p.status}</p>
              </div>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {p.totalSaved.toLocaleString()} RWF · day {Math.min(p.daysElapsed, GROW31_TERM_DAYS)}/{GROW31_TERM_DAYS} · streak {p.currentStreak}
              </p>
              <div style={{ height: '5px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, backgroundColor: bonus > 0 ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }} />
              </div>
            </button>
          );
        })
      )}
    </div>
  );
}

// Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit) equivalent
// (item 153) -- see lib/upfrontDeposit.ts's own doc comment. The one product in this
// module where opening pays real, immediately-spendable interest -- distinct from every
// accrue-then-claim product above (InterestJar/RoundUp/goals/weekly savings).
function UpfrontDepositSection() {
  const { t } = useI18n();
  const [deposits, setDeposits] = useState<UpfrontInterestDeposit[] | null>(null);
  const showSkeleton = useDeferredLoading(deposits === null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyUpfrontDeposits().then(setDeposits).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  return (
    <div>
      {/* Real gap found live (2026-08-31) -- see WeeklySavingsSection's identical
          fix above. Sourced from UpfrontInterestDepositService's own real
          ANNUAL_RATE constant. */}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 2px' }}>12-month deposit</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '0 4px 10px' }}>2.80%/yr interest paid upfront, principal locked</p>
      <OpenUpfrontDepositForm onOpened={load} />
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '16px' }} role="alert">{error}</p>
      )}
      {deposits === null ? (
        showSkeleton ? <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} /> : null
      ) : deposits.length === 0 ? (
        <EmptyState message="No 12-month deposits yet — open one to get a full year's interest paid today, principal locked for 12 months." />
      ) : (
        deposits.map((d) => <UpfrontDepositCard key={d.id} deposit={d} onChanged={load} />)
      )}
    </div>
  );
}

function OpenUpfrontDepositForm({ onOpened }: { onOpened: () => void }) {
  const { t } = useI18n();
  const [principal, setPrincipal] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await openUpfrontDeposit(Number(principal));
      setPrincipal('');
      onOpened();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
        {UPFRONT_DEPOSIT_ANNUAL_RATE}% interest for the full year, paid to your account today. Principal is locked for 12 months — no early withdrawal.
      </p>
      <input
        type="number"
        min={UPFRONT_DEPOSIT_MIN_PRINCIPAL}
        max={UPFRONT_DEPOSIT_MAX_PRINCIPAL}
        value={principal}
        onChange={(e) => setPrincipal(e.target.value)}
        placeholder={`Principal (${UPFRONT_DEPOSIT_MIN_PRINCIPAL.toLocaleString()} - ${UPFRONT_DEPOSIT_MAX_PRINCIPAL.toLocaleString()} RWF)`}
        required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-15-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
        {submitting ? 'Opening…' : 'Open deposit'}
      </button>
    </form>
  );
}

function UpfrontDepositCard({ deposit, onChanged }: { deposit: UpfrontInterestDeposit; onChanged: () => void }) {
  const { t } = useI18n();
  const [withdrawing, setWithdrawing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const matured = deposit.status === 'MATURED';
  // Real per-bucket ledger (2026-08-31) -- this card previously had no drill-in of
  // any kind (its own doc history noted "everything a deposit needs fits on its list
  // row," true for the summary fields but not for a real transaction history, which
  // its dedicated account has always had, just never exposed).
  const [showHistory, setShowHistory] = useState(false);
  const [transactions, setTransactions] = useState<BucketTransaction[] | null>(null);
  const toggleHistory = () => {
    setShowHistory((v) => !v);
    if (!showHistory && transactions === null) {
      fetchUpfrontDepositTransactions(deposit.id).then(setTransactions).catch(() => setTransactions([]));
    }
  };

  const handleWithdraw = async () => {
    setError(null);
    setWithdrawing(true);
    try {
      await withdrawUpfrontDeposit(deposit.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setWithdrawing(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{deposit.principal.toLocaleString()} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{deposit.withdrawnAt ? 'WITHDRAWN' : deposit.status}</p>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        +{deposit.interestPaid.toLocaleString()} RWF interest already paid · matures {new Date(deposit.maturesAt).toLocaleDateString()}
      </p>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
      <button onClick={toggleHistory} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginTop: '8px', fontWeight: 600 }}>
        {showHistory ? 'Hide history' : 'View history'}
      </button>
      {showHistory && (
        <div style={{ marginTop: '6px' }}>
          <BucketTransactionList transactions={transactions} />
        </div>
      )}
      {matured && !deposit.withdrawnAt && (
        <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '10px' }} disabled={withdrawing} onClick={handleWithdraw}>
          {withdrawing ? 'Withdrawing…' : 'Withdraw principal'}
        </button>
      )}
    </div>
  );
}

// Real Toss Bank reference (9 screenshots, 2026-08-23, direct user instruction: "all
// services itunda provide with all in clear UX and UX writing as it's in those
// pictures"): the real Toss Bank account screen organizes its whole product catalog
// under bold, flat category headers (Demand Deposits / Savings / Foreign Currency /
// Loan / Service / ...), not one undifferentiated scroll -- this had been a real,
// previously-BLOCKED gap (SavingsView's own stacked sections had no headers at all,
// and an earlier pass explicitly couldn't find a real Toss reference for organizing a
// long product list -- see docs/DESIGN_REFERENCES.md). Flat text, no card wrapper,
// matching this codebase's own standing flat-design law and Toss's real screenshots
// exactly (a bold label sits directly on the page background, not inside a boxed
// section). Names itunda's own REAL product categories, not Toss's invented ones --
// no "Refinancing"/"Bonds & notes"/"Mortgage Finder" here, since itunda doesn't have
// those; "Cooperative & Group" replaces Toss's own category shape with itunda's real,
// Rwanda-specific SACCO/Ikimina/group-account products instead.
function CatalogSectionHeader({ title }: { title: string }) {
  return (
    <h2 style={{ margin: '32px 4px 8px', fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
      {title}
    </h2>
  );
}

function SavingsView({ initialScrollTarget, onConsumedInitialScrollTarget, onNavigateToTab, onNavigateToLoansMode, onNavigateToSavingsTarget }: { initialScrollTarget?: 'sacco' | 'ikimina' | null; onConsumedInitialScrollTarget?: () => void; onNavigateToTab?: (tab: Tab) => void; onNavigateToLoansMode?: (mode: LoansMode) => void; onNavigateToSavingsTarget?: (target: 'sacco' | 'ikimina') => void } = {}) {
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

      <CatalogSectionHeader title="Savings" />
      <InterestJarCard />
      <RoundUpCard goals={goals ?? []} />
      <CreateGoalForm onCreated={load} />
      {goals === null ? (
        <div className="itunda-flat-section skeleton" style={{ height: '100px' }} />
      ) : goals.length === 0 ? (
        <EmptyState message="No savings goals yet — set one to start putting money aside for something specific." />
      ) : (
        goals.map((g) => <GoalCard key={g.id} goal={g} onChanged={load} />)
      )}
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

// Real IA fix (2026-08-10) -- see PRIMARY_TABS/EXPLORE_TAB_GROUPS's own doc comment
// (BankDashboard) for the full account of how ExploreHub got here. Showing every
// category expanded at once is the exact anti-pattern Hick's Law describes --
// decision time rises with visible choice count, and UX research puts 1-5 visible
// options as the target when speed matters, which is why ExploreHub's groups start
// collapsed. This tracks which of the non-primary tabs a user actually opens,
// most-recent-first, so Explore can surface what THEY use instead of a static
// alphabetical/enum-order dump every time.
const RECENT_TABS_KEY = 'itunda_bank_recent_more_tabs';
const loadRecentTabs = (): Tab[] => {
  try {
    const raw = localStorage.getItem(RECENT_TABS_KEY);
    return raw ? (JSON.parse(raw) as Tab[]) : [];
  } catch {
    return [];
  }
};
const saveRecentTab = (id: Tab, current: Tab[]): Tab[] => {
  const next = [id, ...current.filter((t) => t !== id)].slice(0, 6);
  try {
    localStorage.setItem(RECENT_TABS_KEY, JSON.stringify(next));
  } catch {
    // localStorage unavailable (private browsing, quota) -- recent row just stays empty, not fatal
  }
  return next;
};

export default function BankDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTabState] = useState<Tab>(() => readTabFromUrl());
  // Keeps `tab` deep-linkable: every real navigation both updates state and pushes a
  // real URL (?tab=X) so refresh/share/back-button all land where the user actually
  // was, not always Home. `history.pushState` (not `replaceState`) so the browser's
  // real back button steps through tab history one screen at a time, matching every
  // native app's own real back-stack behavior (Section 1's itunda:// deep links).
  const setTab = (next: Tab) => {
    setTabState(next);
    const url = new URL(window.location.href);
    url.searchParams.set(TAB_QUERY_PARAM, next);
    window.history.pushState({ tab: next }, '', url);
  };
  useEffect(() => {
    const onPopState = () => setTabState(readTabFromUrl());
    window.addEventListener('popstate', onPopState);
    return () => window.removeEventListener('popstate', onPopState);
  }, []);
  const [pendingConversationId, setPendingConversationId] = useState<string | null>(null);
  // Real Uber "trip issue report" hand-off -- see SupportView's own doc comment on
  // initialTransactionId. Same pending-hand-off shape as pendingConversationId above.
  const [pendingRideIssueTransactionId, setPendingRideIssueTransactionId] = useState<string | null>(null);
  // Real gap named in docs/DESIGN_REFERENCES.md's own IA research (Section 41 item 3):
  // search only ever existed buried inside the Explore tab, not reachable from
  // anywhere else without navigating there first and scrolling to find it. The
  // sourced, named-product rule: an icon-in-header that expands search is the right
  // choice for a super-app this size (search is secondary to browsing but genuinely
  // needed for "I know exactly what I want"). Reuses ExploreHub's own already-real,
  // already-working search box rather than building a second one -- this just adds a
  // one-tap header entry point that switches tab and focuses it, same
  // pending-hand-off pattern already used for pendingConversationId above.
  const [focusExploreSearch, setFocusExploreSearch] = useState(false);
  // Real gap named in docs/DESIGN_REFERENCES.md's own IA research (Section 41 item 4):
  // ConversationSummary.unreadCount/GroupSummary.unreadCount were already fetched and
  // rendered per-row *inside* MessagesView's own Direct/Groups lists, but neither
  // total ever reached PRIMARY_TABS's own render, so a user got zero ambient signal
  // that a message needed attention without opening Messages first. Named-product rule
  // cited there: a numeric badge (not a dot) on the tab whose exact count drives the
  // next action, capped at "99+" so a large count never pushes neighboring tab labels.
  // Lightweight top-level poll, independent of MessagesView's own -- this only needs
  // the two totals, not the full conversation/group lists MessagesView renders.
  const [messagesUnreadCount, setMessagesUnreadCount] = useState(0);
  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      Promise.all([fetchConversations(false), fetchGroups()])
        .then(([conversations, groups]) => {
          if (cancelled) return;
          const total = conversations.reduce((sum, c) => sum + c.unreadCount, 0) + groups.reduce((sum, g) => sum + g.unreadCount, 0);
          setMessagesUnreadCount(total);
        })
        .catch(() => {
          // Non-critical -- a poll failure just leaves the last-known badge count.
        });
    };
    poll();
    const interval = setInterval(poll, 15000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);
  // Real, smaller follow-up named alongside the Messages badge above (Section 41
  // item 4): NotificationsCard's own real unreadCount was already fetched and shown
  // *inside* the You tab, but never reached PRIMARY_TABS either, so opening You was
  // the only way to learn something needed attention. Named-product rule cited in the
  // same doc section: a plain dot (not a number) is correct here, since this is a
  // general "something changed" signal, not an exact count that drives the next
  // action the way an unread message count does -- deliberately not mixed with the
  // numeric Messages badge on the same bar.
  const [hasUnreadNotifications, setHasUnreadNotifications] = useState(false);
  useEffect(() => {
    let cancelled = false;
    const poll = () => {
      fetchNotifications()
        .then((r) => { if (!cancelled) setHasUnreadNotifications(r.unreadCount > 0); })
        .catch(() => {
          // Non-critical -- a poll failure just leaves the last-known dot state.
        });
    };
    poll();
    const interval = setInterval(poll, 15000);
    return () => { cancelled = true; clearInterval(interval); };
  }, []);
  // Real deep-link from the Home coop rail into LoansView's own specific mode
  // (2026-08-10) -- see LoansView's own initialMode doc comment for the full account.
  const [pendingLoansMode, setPendingLoansMode] = useState<LoansMode | null>(null);
  // Real deep-link from the Home coop rail into SavingsView's own SACCO/Ikimina
  // sections (2026-08-10) -- see SavingsView's own initialScrollTarget doc comment.
  const [pendingSavingsTarget, setPendingSavingsTarget] = useState<'sacco' | 'ikimina' | null>(null);
  const user = getStoredUser();
  // Real gap caught while adding Android/iOS's 4th localization screen (2026-08-08,
  // docs/DESIGN_REFERENCES.md Section 19): LoginPage.tsx's own switcher only renders
  // pre-login, so a signed-in user had no way to change language short of logging
  // out -- Android had the same gap in a different shape (see
  // the-switch-that-only-flipped-one-room.ts). This header renders on every tab, so
  // putting it here (not buried in one tab like MyView) fixes it for the whole app in
  // one place rather than one screen.
  const { locale, setLocale } = useI18n();

  // Real 쿠팡파트너스-style affiliate link capture (item 229) -- see
  // lib/affiliate.ts's own doc comment. Best-effort, runs once per real page load.
  useEffect(() => { captureReferralCodeFromUrl(); }, []);

  const handleLogout = () => {
    logout();
    onLogout();
  };

  // Real "message seller" hand-off from MarketplaceView: switches straight to the
  // Messages tab with that real conversation already open, rather than dropping the
  // buyer on a conversation list they'd have to search through themselves.
  const handleMessageSeller = (conversationId: string) => {
    setPendingConversationId(conversationId);
    setTab('MESSAGES');
  };

  // Real "report an issue" hand-off from a completed ride: switches straight to
  // Support with that ride's real payment transaction + RIDE_ISSUE category already
  // selected, same shape as handleMessageSeller above.
  const handleReportRideIssue = (transactionId: string) => {
    setPendingRideIssueTransactionId(transactionId);
    setTab('SUPPORT');
  };

  const TABS: { id: Tab; label: string }[] = [
    { id: 'HOME', label: 'Home' },
    { id: 'PAY', label: 'Pay' },
    { id: 'EXPLORE', label: 'Explore' },
    { id: 'YOU', label: 'You' },
    { id: 'MY', label: 'My' },
    { id: 'SHOP', label: 'Shop' },
    { id: 'EATS', label: 'Eats' },
    { id: 'MARKETPLACE', label: 'Marketplace' },
    { id: 'COMMUNITY', label: 'Community' },
    { id: 'JOBS', label: 'Jobs' },
    { id: 'PROPERTY', label: 'Property' },
    { id: 'STOCKS', label: 'Invest' },
    { id: 'SAVINGS', label: 'itunda Bank' },
    { id: 'MESSAGES', label: 'Messages' },
    { id: 'RIDES', label: 'Rides' },
    { id: 'DESIGNATED_DRIVER', label: 'Designated driver' },
    { id: 'BIKESHARE', label: 'Bike' },
    { id: 'PARKING', label: 'Parking' },
    { id: 'BUS', label: 'Bus' },
    { id: 'KNOWLEDGE', label: 'Q&A' },
    { id: 'MAP', label: 'Map' },
    { id: 'CERTIFICATE', label: 'Certificate' },
    { id: 'DEVICES', label: 'Devices' },
    { id: 'CARD', label: 'Card' },
    { id: 'TRANSIT', label: 'Transit' },
    { id: 'OVERVIEW', label: 'Overview' },
    { id: 'LOANS', label: 'Loans' },
    { id: 'CREDIT_SCORE', label: 'Credit score' },
    { id: 'TRUST_SCORE', label: 'Trust score' },
    { id: 'REWARDS', label: 'Rewards' },
    { id: 'INSURANCE', label: 'Insurance' },
    { id: 'BILLS', label: 'Pay bills' },
    { id: 'AGENT', label: 'Agent till' },
    { id: 'USSD', label: 'USSD access' },
    { id: 'FOREIGN_CURRENCY', label: 'Foreign currency' },
    { id: 'SPENDING', label: 'Spending' },
    { id: 'SUBSCRIPTIONS', label: 'Subscriptions' },
    { id: 'IDENTITY', label: 'Verify' },
    { id: 'SUPPORT', label: 'Support' },
  ];

  // Real gap found live (2026-08-10) via an actual headless-Chrome screenshot (through
  // a real CDP device-metrics capture, not code review): all 36 entries in TABS above
  // rendered as one `display:flex` row with `flex:1` on every button and no
  // overflow-x/wrap -- at any real viewport width, the buttons hit their own text's
  // intrinsic minimum width and the row silently overflowed with NO scroll affordance,
  // so most tabs -- including real, fully-built features like Marketplace/Jobs/
  // Property/Rides/Loans -- were completely unreachable on any realistic device width.
  //
  // A 4-primary-tabs-plus-"More" fix, then a Home/Pay/Explore/Activity/You fix, then
  // a Home/Shop/Hood/Talk/All fix matching Android/iOS's own independently-converged
  // 5-tab bar (both citing the real Toss reference 홈/혜택/쇼핑/페이/전체) all landed
  // and were each superseded the same day (2026-08-10) -- see
  // docs/DESIGN_REFERENCES.md Section 41 for the full research trail. This is the
  // version that stuck, after directly comparing it against Home/Shop/Hood/Talk/All:
  // HOME (unchanged), PAY (dedicated primary slot -- itunda is bank-first, and Pay
  // was judged to deserve first-class visibility Android/iOS currently bury one tap
  // into All), EXPLORE (Shop/Eats/Marketplace/Community/Jobs/Property each their own
  // flat row here now, not nested behind a segmented-toggle sub-screen -- a tab bar
  // inside a tab is real noise a flat catalog shouldn't have; the ShopHub/HoodHub
  // toggle shape was correct when Shop/Hood were primary tabs, wrong once demoted
  // into Explore, corrected same day -- alongside every other real destination,
  // searchable and grouped), MESSAGES (itunda's
  // Marketplace/Community/Jobs/Property flows lean on the same "message the other
  // person" mechanic Karrot/당근마켓 keeps chat primary for), YOU (profile up front,
  // matching where Android/iOS put My inside All, plus Insights and Account &
  // security as their own groups rather than flat rows). Android
  // (`ItundaAppScreen.kt`'s `ItundaTab` enum) and iOS (`ContentView.swift`) are being
  // rebuilt to this same five in the same pass specifically so this doesn't reopen
  // the cross-platform inconsistency the earlier fix closed. Every one of the
  // original tab ids and its `{tab === 'X' && <XView />}` routing further below is
  // unchanged -- this only changes how a destination is reached. EXPLORE_TAB_GROUPS
  // is the single source of truth for "everything else," used by both ExploreHub's
  // browsable groups and its search box, so a service can't be filed under one
  // category when browsed and a different one when searched.
  const PRIMARY_TABS: { id: Tab; label: string; icon: typeof IconHome }[] = [
    { id: 'HOME', label: 'Home', icon: IconHome },
    { id: 'PAY', label: 'Pay', icon: IconPay },
    { id: 'EXPLORE', label: 'Explore', icon: IconExplore },
    { id: 'MESSAGES', label: 'Messages', icon: IconMessages },
    { id: 'YOU', label: 'You', icon: IconYou },
  ];
  const EXPLORE_TAB_GROUPS: { title: string; ids: Tab[] }[] = [
    { title: 'Everyday', ids: ['SHOP', 'EATS', 'RIDES', 'MAP'] },
    { title: 'Your neighbourhood', ids: ['MARKETPLACE', 'COMMUNITY', 'JOBS', 'PROPERTY'] },
    { title: 'Get around', ids: ['DESIGNATED_DRIVER', 'BIKESHARE', 'PARKING', 'BUS'] },
    { title: 'Money tools', ids: ['SAVINGS', 'STOCKS', 'LOANS', 'CREDIT_SCORE', 'INSURANCE', 'FOREIGN_CURRENCY'] },
    { title: 'Trust & community', ids: ['TRUST_SCORE', 'KNOWLEDGE', 'REWARDS'] },
    { title: 'More', ids: ['CERTIFICATE', 'AGENT', 'USSD'] },
  ];
  const tabLabel = (id: Tab) => TABS.find((t) => t.id === id)?.label ?? id;
  const tabIcon = (id: Tab) => EXPLORE_TAB_ICONS[id];
  const [recentMoreTabs, setRecentMoreTabs] = useState<Tab[]>([]);
  useEffect(() => { setRecentMoreTabs(loadRecentTabs()); }, []);
  const navigateFromExplore = (id: Tab) => {
    setTab(id);
    setRecentMoreTabs(saveRecentTab(id, recentMoreTabs));
  };

  return (
    <div style={{ padding: '20px', paddingBottom: '100px', maxWidth: '480px', margin: '0 auto' }}>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', padding: '0 8px' }}
      >
        <h2 style={{ color: 'var(--itunda-grey-900)', margin: 0, fontSize: 'var(--itunda-type-scale-24-size)', fontWeight: '700', letterSpacing: '-0.5px' }}>Itunda</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {user && <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{user.firstName}</span>}
          <button
            onClick={() => { setFocusExploreSearch(true); setTab('EXPLORE'); }}
            style={{ color: 'var(--itunda-grey-500)', display: 'flex', padding: '4px' }}
            aria-label="Search all services"
          >
            <IconSearch size={18} />
          </button>
          <select
            value={locale}
            onChange={(e) => setLocale(e.target.value as 'en' | 'rw' | 'fr')}
            aria-label="Language"
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '4px 6px', borderRadius: '6px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-700)', background: 'var(--itunda-white)' }}
          >
            {LOCALES.map((l) => (
              <option key={l.code} value={l.code}>{l.label}</option>
            ))}
          </select>
          <button onClick={handleLogout} style={{ color: 'var(--itunda-grey-500)', display: 'flex', padding: '4px' }} aria-label="Sign out">
            <LogOut size={18} />
          </button>
        </div>
      </motion.div>

      <div style={{ display: 'flex', gap: '2px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
        {PRIMARY_TABS.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            aria-current={tab === id ? 'page' : undefined}
            style={{
              flex: 1, padding: '7px 2px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700,
              color: tab === id ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: tab === id ? 'var(--itunda-indigo)' : 'transparent',
              display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: '3px',
            }}
          >
            <div style={{ position: 'relative' }}>
              <Icon size={18} />
              {id === 'MESSAGES' && messagesUnreadCount > 0 && (
                <span
                  style={{
                    position: 'absolute', top: '-6px', right: '-10px', minWidth: '16px', height: '16px', padding: '0 3px',
                    borderRadius: '999px', backgroundColor: 'var(--itunda-red)', color: 'var(--itunda-white)',
                    fontSize: '10px', fontWeight: 700, display: 'flex', alignItems: 'center', justifyContent: 'center',
                    lineHeight: 1,
                  }}
                >
                  {messagesUnreadCount > 99 ? '99+' : messagesUnreadCount}
                </span>
              )}
              {id === 'YOU' && hasUnreadNotifications && (
                <span
                  style={{
                    position: 'absolute', top: '-2px', right: '-4px', width: '9px', height: '9px',
                    borderRadius: '999px', backgroundColor: 'var(--itunda-red)',
                    border: '1.5px solid var(--itunda-grey-100)',
                  }}
                  aria-label="Unread notifications"
                />
              )}
            </div>
            {label}
          </button>
        ))}
      </div>

      {tab === 'HOME' && <HomeView />}
      {tab === 'PAY' && <PayHub onNavigateToTab={setTab} onNavigateToCard={() => setTab('CARD')} />}
      {tab === 'EXPLORE' && (
        <ExploreHub
          groups={EXPLORE_TAB_GROUPS}
          tabLabel={tabLabel}
          tabIcon={tabIcon}
          recentTabs={recentMoreTabs}
          onSelect={navigateFromExplore}
          autoFocusSearch={focusExploreSearch}
          onConsumedAutoFocus={() => setFocusExploreSearch(false)}
        />
      )}
      {tab === 'YOU' && <YouHub onNavigateToTab={setTab} />}
      {tab === 'MY' && <MyView />}
      {tab === 'SHOP' && <ShopView onMessageSeller={handleMessageSeller} />}
      {tab === 'EATS' && <EatsView onMessageSeller={handleMessageSeller} />}
      {tab === 'MARKETPLACE' && <MarketplaceView onMessageSeller={handleMessageSeller} />}
      {tab === 'COMMUNITY' && <CommunityView onOpenGroupChat={handleMessageSeller} />}
      {tab === 'JOBS' && <JobsView onMessagePoster={handleMessageSeller} />}
      {tab === 'PROPERTY' && <PropertyView onMessageLister={handleMessageSeller} />}
      {tab === 'STOCKS' && <StocksView />}
      {tab === 'SAVINGS' && (
        <SavingsView
          initialScrollTarget={pendingSavingsTarget}
          onConsumedInitialScrollTarget={() => setPendingSavingsTarget(null)}
          onNavigateToTab={setTab}
          onNavigateToLoansMode={setPendingLoansMode}
          onNavigateToSavingsTarget={setPendingSavingsTarget}
        />
      )}
      {tab === 'MESSAGES' && (
        <MessagesView
          initialConversationId={pendingConversationId}
          onConsumedInitial={() => setPendingConversationId(null)}
        />
      )}
      {tab === 'RIDES' && <RidesView onReportIssue={handleReportRideIssue} />}
      {tab === 'DESIGNATED_DRIVER' && <DesignatedDriverView />}
      {tab === 'BIKESHARE' && (
        <Suspense fallback={<div className="itunda-flat-section skeleton" style={{ height: '200px' }} />}>
          <BikeShareView />
        </Suspense>
      )}
      {tab === 'PARKING' && (
        <Suspense fallback={<div className="itunda-flat-section skeleton" style={{ height: '200px' }} />}>
          <ParkingView />
        </Suspense>
      )}
      {tab === 'BUS' && (
        <Suspense fallback={<div className="itunda-flat-section skeleton" style={{ height: '200px' }} />}>
          <BusView />
        </Suspense>
      )}
      {tab === 'KNOWLEDGE' && <KnowledgeView />}
      {tab === 'MAP' && (
        <Suspense fallback={<div className="itunda-flat-section skeleton" style={{ height: '300px' }} />}>
          <MapView />
        </Suspense>
      )}
      {tab === 'CERTIFICATE' && <CertificateView />}
      {tab === 'DEVICES' && <DevicesView />}
      {tab === 'CARD' && <CardView />}
      {tab === 'TRANSIT' && <TransitScreen onOpenCollect={() => setTab('TRANSIT_COLLECT')} />}
      {tab === 'TRANSIT_COLLECT' && <TransitCollectScreen />}
      {tab === 'MOTO_FARE_COLLECT' && <MotoFareCollectScreen />}
      {tab === 'OVERVIEW' && <OverviewAssetsView onNavigateToTab={setTab} />}
      {tab === 'LOANS' && <LoansView initialMode={pendingLoansMode ?? undefined} onConsumedInitialMode={() => setPendingLoansMode(null)} />}
      {tab === 'CREDIT_SCORE' && <CreditScoreView />}
      {tab === 'TRUST_SCORE' && <TrustScoreView />}
      {tab === 'REWARDS' && <RewardsView />}
      {tab === 'INSURANCE' && (
        <Suspense fallback={<div className="itunda-flat-section skeleton" style={{ height: '200px' }} />}>
          <InsuranceView />
        </Suspense>
      )}
      {tab === 'BILLS' && <BillsView />}
      {tab === 'AGENT' && <AgentOperatorView />}
      {tab === 'USSD' && <UssdSettingsView />}
      {tab === 'FOREIGN_CURRENCY' && <ForeignCurrencyView />}
      {tab === 'SPENDING' && <SpendingInsightView />}
      {tab === 'SUBSCRIPTIONS' && <SubscriptionsView />}
      {tab === 'IDENTITY' && <IdentityView />}
      {tab === 'SUPPORT' && (
        <SupportView
          initialTransactionId={pendingRideIssueTransactionId}
          initialCategory={pendingRideIssueTransactionId ? 'RIDE_ISSUE' : undefined}
          onConsumedInitial={() => setPendingRideIssueTransactionId(null)}
        />
      )}
    </div>
  );
}
