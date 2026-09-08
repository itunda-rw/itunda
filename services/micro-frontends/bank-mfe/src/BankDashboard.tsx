import { lazy, Suspense, useEffect, useId, useRef, useState, type ComponentType, type ReactElement } from 'react';
import { IconShieldCheck, IconStar } from './icons/ItundaIcons';
import { motion } from 'framer-motion';
import { Bike, Check, Clock, Landmark, LogOut, Sprout, Users } from 'lucide-react';
import { BankCardChip, CardContactlessGlyph } from './BankCardChip';import { IconBack, IconChevronRight, IconClose, IconSearch } from './icons/ItundaIcons';
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
import { getStoredUser, logout, ApiError } from './lib/api';
import { DeviceStepUpPrompt } from './DeviceStepUpPrompt';
import { MyView } from './MyView';
import { CardExplainer } from './CardExplainer';
import { OverviewAssetsView } from './OverviewAssetsView';
import { TransitScreen } from './TransitScreen';
import { TransitCollectScreen } from './TransitCollectScreen';
import { MotoFareCollectScreen } from './MotoFareCollectScreen';
import { recordEvent } from './lib/analytics';
import { useI18n } from './i18n/I18nContext';
import { LOCALES, type TranslationKey } from './i18n/translations';
import { IdsButton } from './IdsButton';
import { EmptyState, ErrorCard } from './EmptyState';
import { useDeferredLoading } from './useDeferredLoading';
import { ShopView } from './ShopView';
import { MessagesView } from './TalkGroupsAndFriends';
import { fetchAccountTransactions, fetchAccounts, type Account } from './lib/account';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, type TrustedDevice } from './lib/device';
import { fetchNotifications } from './lib/notifications';
import { fetchDiscoverItems, type DiscoverItem } from './lib/discover';
import { cardDesign, chargeCard, closeMyCard, fetchCardTransactions, fetchMyCard, freezeCard, issueCard, reissueCard, reportLostCard, setCardLimits, setCardPin, unfreezeCard, type Card, type CardTransaction } from './lib/card';
import { BucketDetailScreen } from './BucketDetailScreen';
import { transactionsToBucketTransactions } from './lib/bucketTransaction';
import { resolveRecipient, sendDirect, type P2pRecipientPreview } from './lib/p2p';
import { CertificateView } from './CertificateView';
import { IdentityVerificationConsentView } from './IdentityVerificationConsentView';
import { LoansView, type LoansMode } from './LoansView';
import { fetchCreditScore, fetchCreditScoreSuggestions, type CreditScoreFactor, type CreditScoreSuggestion } from './lib/creditScore';
import { CreditScoreView, TrustScoreView } from './ScoreViews';
import { RewardsView } from './RewardsView';
import { BillsView } from './BillsView';
import { AgentOperatorView } from './AgentOperatorView';
import { UssdSettingsView } from './UssdSettingsView';
import { ForeignCurrencyView } from './ForeignCurrencyView';
import { FullScreenFlow } from './FullScreenFlow';
import { submitHoodReport, type HoodReportTargetType } from './lib/hoodReport';
import { IdentityView } from './IdentityView';
import { addContact, fetchContacts, type Contact } from './lib/contacts';
import { SupportView } from './SupportView';
import type { SupportTicketCategory } from './lib/support';
import { SpendingInsightView } from './SpendingInsightView';
import { SubscriptionsView } from './SubscriptionsView';
import { StocksView } from './StocksView';
import { fetchConversations, type ConversationSummary } from './lib/messaging';
import { fetchGroups } from './lib/groupMessaging';
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
// Real fix (Maps product-completeness pass, 2026-09-07) -- MapView's own useI18n()
// call binds to maps-mfe's own bundled Context object, which THIS app's I18nProvider
// cannot satisfy even though both share the same React instance (same real fix
// host-app's own App.tsx already documents for bank_mfe/kyc_mfe).
const MapsI18nProvider = lazy(() => import('maps_mfe/I18nProvider'));
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
export function ProgressStepper({ activeStepIndex, steps }: { activeStepIndex: number; steps: string[] }) {
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
              {giftResult.amount.toLocaleString('en-US')} RWF is held until {recipient.trim()} claims it -- auto-refunded to you after 7 days if unclaimed.
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
                  onClick={() => navigator.share({ text: `Sent ${Number(amount).toLocaleString('en-US')} RWF to ${recipientName} via itunda` }).catch(() => {})}
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
            <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, marginBottom: '4px' }}>{Number(amount).toLocaleString('en-US')} RWF</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-grey-700)' }}>{t('transfer.toLabel')} {recipientName}</p>
            {memo.trim() && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginTop: '10px' }}>&ldquo;{memo.trim()}&rdquo;</p>}
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-400)', marginTop: '14px' }}>
              {t('transfer.newBalance', { amount: result.newBalance.toLocaleString('en-US') })}
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
                      <GiftGlyph theme={null} size={16} /> Send gift · {Number(amount).toLocaleString('en-US')} RWF
                    </span>
                  ) : t('transfer.send', { amount: Number(amount).toLocaleString('en-US') })}
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
          {t('transfer.confirmSendNow', { amount: Number(amount).toLocaleString('en-US'), recipient: recipientName })}
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
            {amount === '' ? '0' : Number(amount).toLocaleString('en-US')} <span style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-grey-500)' }}>RWF</span>
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
              {t('transfer.balanceLabel', { amount: accountBalance.toLocaleString('en-US') })}
            </button>
          </div>
          {insufficientBalance && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: '4px 0 0' }}>
              {t('transfer.insufficientBalance', { amount: accountBalance.toLocaleString('en-US') })}
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

export function CooperativeSavingsRail({ onNavigateToTab, onNavigateToLoansMode, onNavigateToSavingsTarget }: { onNavigateToTab: (tab: Tab) => void; onNavigateToLoansMode: (mode: LoansMode) => void; onNavigateToSavingsTarget: (target: 'sacco' | 'ikimina') => void }) {
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

export function ProductPageHeader({ title, subtitle }: { title: string; subtitle: string }) {
  return (
    <div style={{ margin: '4px 0 16px' }}>
      <h1 style={{ margin: 0, color: 'var(--itunda-grey-900)', fontSize: 'var(--itunda-type-scale-24-size)', letterSpacing: '-0.5px' }}>{title}</h1>
      <p style={{ margin: '5px 0 0', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-13-size)', lineHeight: 1.45 }}>{subtitle}</p>
    </div>
  );
}

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
export function YouthAccountCard() {
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
          balanceText={`${youthAccount.balance.toLocaleString('en-US')} RWF`}
          fetchTransactions={() => fetchAccountTransactions(youthAccount.id).then((txs) => transactionsToBucketTransactions(txs, youthAccount.id, youthAccount.balance))}
          onBack={() => setShowDetail(false)}
        />
      )}
      {youthAccount && (
        <div>
          <button onClick={() => setShowDetail(true)} style={{ textAlign: 'left', display: 'block' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700 }}>{animatedBalance.toLocaleString('en-US')} RWF</p>
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

const AUTO_TRANSFER_STATUS_KEY: Record<AutoTransfer['status'], TranslationKey> = {
  ACTIVE: 'autoTransfers.statusActive', PAUSED: 'autoTransfers.statusPaused', CANCELLED: 'autoTransfers.statusCancelled',
};
const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];
const WEEKDAY_KEYS: TranslationKey[] = ['weekday.monday', 'weekday.tuesday', 'weekday.wednesday', 'weekday.thursday', 'weekday.friday', 'weekday.saturday', 'weekday.sunday'];

// Real Toss Bank 자동이체 (auto-transfer) -- see lib/autoTransfers.ts's own doc
// comment. Recurring, genuinely distinct from ScheduledTransfersCard's own one-time
// 예약송금 above (now in PayTransferCards.tsx). First bank-mfe client for a backend
// that previously had none.
export function AutoTransfersCard() {
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
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{at.recipientName} · {at.amount.toLocaleString('en-US')} RWF</p>
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
    fetchConversations().then((r) => setConversations(r.conversations)).catch(() => setConversations([]));
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
      setChargeSuccess(`Paid ${result.transaction.amount.toLocaleString('en-US')} RWF at ${result.transaction.merchantName}`);
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
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{t('card.thisMonth')}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 800, margin: '4px 0 0' }}>{card.spentThisMonth.toLocaleString('en-US')} RWF</p>
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
        {card.closedAt ? t('card.statusClosed') : card.lost ? t('card.statusLost') : card.frozen ? t('card.statusFrozen') : t('card.statusActive')}
      </p>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}

      {card.lost || card.closedAt ? (
        <button className="itunda-btn itunda-btn-primary" disabled={busy} onClick={handleReissue} style={{ marginBottom: '20px' }}>
          {busy ? '…' : t('card.getNewCard')}
        </button>
      ) : (
        <button className={`itunda-btn ${card.frozen ? 'itunda-btn-primary' : 'itunda-btn-danger'}`} disabled={busy} onClick={handleToggleFreeze} style={{ marginBottom: '20px' }}>
          {card.frozen ? t('card.unfreezeCard') : t('card.freezeCard')}
        </button>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>{t('card.usageHistory')}</h3>
        {transactions.length === 0 ? (
          <EmptyState message={t('card.noPurchasesYet')} />
        ) : (
          transactions.map((t) => (
            <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{t.merchantName}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{new Date(t.createdAt).toLocaleString()}</p>
              </div>
              <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{t.amount.toLocaleString('en-US')} RWF</span>
            </div>
          ))
        )}
      </div>

      {(cardUsageFactor || cardSuggestion) && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>{t('card.benefitsTitle')}</h3>
          {cardUsageFactor && (
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{cardUsageFactor.description}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-green)' }}>{t('card.creditScorePoints', { points: cardUsageFactor.points })}</span>
            </div>
          )}
          {cardSuggestion && (
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{cardSuggestion.description}</span>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{t('card.morePoints', { points: cardSuggestion.pointsGain })}</span>
            </div>
          )}
        </div>
      )}

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{t('card.convenientFeatures')}</h3>
        <button
          onClick={() => document.getElementById('card-spend-limits-section')?.scrollIntoView({ behavior: 'smooth' })}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left' }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{t('card.spendLimits')}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={() => setShowPinForm((v) => !v)}
          disabled={busy || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{card.pinSet ? t('card.changePin') : t('card.setPin')}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={handleReportLost}
          disabled={busy || card.lost || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.lost || card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{card.lost ? t('card.statusLost') : t('card.reportLostOrStolen')}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
        <button
          onClick={handleCloseCard}
          disabled={busy || !!card.closedAt}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.closedAt ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: card.closedAt ? 'var(--itunda-grey-500)' : 'var(--itunda-red)' }}>{card.closedAt ? t('card.cardClosed') : t('card.closeCard')}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
      </div>

      {showPinForm && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{card.pinSet ? t('card.changePin') : t('card.setPin')}</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            {t('card.pinFormSubtitle')}
          </p>
          <form onSubmit={handleSetPin} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {pinError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{pinError}</p>}
            <input
              type="password" inputMode="numeric" placeholder={t('card.newPinPlaceholder')} value={newPinInput}
              onChange={(e) => setNewPinInput(e.target.value)} required maxLength={4} pattern="\d{4}"
              style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
            />
            <input
              type="password" placeholder={t('card.currentPasswordPlaceholder')} value={pinPasswordInput}
              onChange={(e) => setPinPasswordInput(e.target.value)} required
              style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>
              {busy ? '…' : t('card.savePin')}
            </button>
          </form>
        </div>
      )}
      {pinSuccess && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginBottom: '8px' }}>{t('card.pinSaved')}</p>}

      <div id="card-spend-limits-section" className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '10px' }}>{t('card.spendLimits')}</h3>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('card.today')}</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>{card.spentToday.toLocaleString('en-US')} / {card.dailyLimit.toLocaleString('en-US')} RWF</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{t('card.thisMonth')}</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>{card.spentThisMonth.toLocaleString('en-US')} / {card.monthlyLimit.toLocaleString('en-US')} RWF</span>
        </div>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
          <input
            type="number" placeholder={t('card.dailyLimitPlaceholder')} value={dailyLimitInput} onChange={(e) => setDailyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <input
            type="number" placeholder={t('card.monthlyLimitPlaceholder')} value={monthlyLimitInput} onChange={(e) => setMonthlyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
        </div>
        <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSaveLimits} style={{ width: '100%' }}>
          {t('card.saveLimits')}
        </button>
      </div>

      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>{t('card.payWithCard')}</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
          {t('card.payDisclaimer')}
        </p>
        <form onSubmit={handleCharge} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {chargeError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{chargeError}</p>}
          {chargeSuccess && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>{chargeSuccess}</p>}
          <input
            placeholder={t('card.merchantNamePlaceholder')} value={merchantName} onChange={(e) => setMerchantName(e.target.value)} required
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <input
            type="number" placeholder={t('card.amountPlaceholder')} value={chargeAmount} onChange={(e) => setChargeAmount(e.target.value)} required min="1"
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy || card.frozen}>
            {card.closedAt ? t('card.payStateClosed') : card.lost ? t('card.payStateLost') : card.frozen ? t('card.payStateFrozen') : busy ? t('card.paying') : t('card.pay')}
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
import { SavingsView } from './BankHub';
import { PayHub } from './PayHub';

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
  // Real "verify with itunda" identity-verification-for-partners deep link
  // (Partners product-completeness pass, 2026-09-07) -- web's own analog to the
  // native apps' itunda://verify/{requestId} deep link, resolved via a
  // `?verifyRequestId=` URL param instead (bank-mfe's own established pattern for a
  // param-triggered full-screen view independent of `tab`, mirroring
  // TalkGroupsAndFriends.tsx's/EatsView.tsx's own ?joinChatCode=/?joinEatsCode=
  // precedent). The actual early return is below the rest of this component's own
  // hooks (Rules of Hooks -- every hook here must run unconditionally on every
  // render). All real UI lives in IdentityVerificationConsentView.tsx.
  const [verifyRequestId, setVerifyRequestId] = useState<string | null>(
    () => new URLSearchParams(window.location.search).get('verifyRequestId'),
  );

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
  // Real Eats order "report an issue" hand-off (2026-09-06, Eats product-completeness
  // pass) -- same pending-hand-off shape as pendingRideIssueTransactionId above, kept
  // as its own state rather than generalized since a user can only ever be reporting
  // one specific completed order/trip at a time.
  const [pendingEatsOrderIssueTransactionId, setPendingEatsOrderIssueTransactionId] = useState<string | null>(null);
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
      // Real, pre-existing accuracy gap (not introduced by the 2026-09-09
      // pagination fix, just now more visible since fetchConversations's
      // return shape changed): this only sums unreadCount across each list's
      // own first page (20 rows), so a user with more than 20 real
      // conversations or groups gets an undercounted badge. Fixing this
      // properly needs a real dedicated total-unread-count backend endpoint
      // (summing across ALL of a user's conversations/groups, not a page),
      // which doesn't exist yet -- named here rather than silently
      // papered over, not attempted this pass.
      Promise.all([fetchConversations(false), fetchGroups()])
        .then(([conversationsRes, groupsRes]) => {
          if (cancelled) return;
          const total = conversationsRes.conversations.reduce((sum, c) => sum + c.unreadCount, 0) + groupsRes.groups.reduce((sum, g) => sum + g.unreadCount, 0);
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

  // Real Eats order "report an issue" hand-off (2026-09-06, Eats product-completeness
  // pass) -- same shape as handleReportRideIssue above.
  const handleReportEatsOrderIssue = (transactionId: string) => {
    setPendingEatsOrderIssueTransactionId(transactionId);
    setTab('SUPPORT');
  };

  let supportInitialCategory: SupportTicketCategory | undefined;
  if (pendingRideIssueTransactionId) supportInitialCategory = 'RIDE_ISSUE';
  else if (pendingEatsOrderIssueTransactionId) supportInitialCategory = 'EATS_ORDER_ISSUE';

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

  if (verifyRequestId) {
    return <IdentityVerificationConsentView requestId={verifyRequestId} onDone={() => setVerifyRequestId(null)} />;
  }

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
      {tab === 'EATS' && <EatsView onMessageSeller={handleMessageSeller} onReportIssue={handleReportEatsOrderIssue} />}
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
          <MapsI18nProvider>
            <MapView />
          </MapsI18nProvider>
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
          initialTransactionId={pendingRideIssueTransactionId ?? pendingEatsOrderIssueTransactionId}
          initialCategory={supportInitialCategory}
          onConsumedInitial={() => {
            setPendingRideIssueTransactionId(null);
            setPendingEatsOrderIssueTransactionId(null);
          }}
        />
      )}
    </div>
  );
}
