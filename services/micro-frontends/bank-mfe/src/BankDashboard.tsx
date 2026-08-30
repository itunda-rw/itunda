import { lazy, Suspense, useEffect, useId, useRef, useState, type ComponentType, type ReactElement } from 'react';
import { IconSend, IconShieldCheck, IconStar } from './icons/ItundaIcons';
import { motion, AnimatePresence, useAnimation, useMotionValue } from 'framer-motion';
import { itundaSpring } from './lib/motion';
import QRCode from 'qrcode';
import JsBarcode from 'jsbarcode';
import { Archive, ArchiveRestore, Bike, Bot, Camera, Car, Check, Clock, Image as ImageIcon, Landmark, Link as LinkIcon, LogOut, Megaphone, MessageCircle, Phone, Pin, PinOff, QrCode, Receipt, Settings, ShoppingBag, SmilePlus, Sprout, Star, Store, Users, Utensils, Wallet as AccountIcon, Zap } from 'lucide-react';
import { BankCardChip, CardContactlessGlyph } from './BankCardChip';import { IconAdd, IconBack, IconChevronRight, IconClose, IconSearch } from './icons/ItundaIcons';
import { IconHome, IconPay, IconExplore, IconMessages, IconYou } from './icons/ItundaIcons';
import { ReactionGlyph } from './icons/ItundaFace';
import { renderTextWithEmoji, EmojiPicker } from './icons/ItundaFaceEmoji';
import { SmileySlight } from './icons/ItundaFaceSmileys';
import { GiftGlyph, DiceGlyph, VoucherTicket, GiftBox } from './icons/ItundaFaceGifts';
import { WishlistHeart, HeartFilled, HeartOutline } from './icons/ItundaFaceHearts';
import { LockGlyph } from './icons/ItundaFaceSecurity';
import { FlameGlyph, PinGlyph, SoldOutGlyph, LinkGlyph, ChatGlyph, ClockGlyph, GlobeGlyph, CameraGlyph, CakeGlyph, MoneyBagGlyph, ShoppingBagGlyph, PriceDropGlyph, BikeGlyph, SpeechBubbleGlyph } from './icons/ItundaFaceMisc';
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
import { PinSetupCard } from './PinSetupCard';
import { CardExplainer } from './CardExplainer';
import { OverviewAssetsView } from './OverviewAssetsView';
import { PayFundingSourcePicker } from './PayFundingSourcePicker';
import { CouponBoxView } from './CouponBoxView';
import { MembershipView } from './MembershipView';
import { ShopSellerContactPicker, ShopMessageSellerButton, ShopBestSellerBadge, ShopDeliveryEtaPill } from './ShopSellerContactPicker';
import { RecommendedDishesRail, PopularDishesRail, EatsNearbyAdsRail } from './EatsDishRails';
import { EatsFrequentlyOrderedWith } from './EatsFrequentlyOrderedWith';
import { TransitScreen } from './TransitScreen';
import { TransitCollectScreen } from './TransitCollectScreen';
import { MotoFareCollectScreen } from './MotoFareCollectScreen';
import { QrScanCamera, parseQrParam } from './QrScanCamera';
import { recordEvent } from './lib/analytics';
import { useI18n } from './i18n/I18nContext';
import { LOCALES, type TranslationKey } from './i18n/translations';
import { Badge } from './Badge';
import { IdsButton } from './IdsButton';
import { showToast } from './Toast';
import { EmptyState, ErrorCard } from './EmptyState';
import { configureAutoTopUp, fetchAutoTopUpSetting, fetchTransactions, fetchTransactionTimeline, fetchAccounts, triggerAutoTopUp, type AutoTopUpSetting, type Transaction, type Account } from './lib/account';
import { PayMoneyDetail } from './PayMoneyDetail';
import { AccountSummaryRow } from './AccountSummaryRow';
import { AccountDetailScreen } from './AccountDetailScreen';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, type TrustedDevice } from './lib/device';
import { fetchNotifications, markNotificationRead, markAllNotificationsRead, type NotificationItem } from './lib/notifications';
import { fetchDiscoverItems, type DiscoverItem } from './lib/discover';
import { cardDesign, chargeCard, fetchCardTransactions, fetchMyCard, freezeCard, issueCard, setCardLimits, unfreezeCard, type Card, type CardTransaction } from './lib/card';
import { claimInterest, createGoal, depositToGoal, fetchDepositProtectionStatus, fetchGoals, fetchInterestJar, fetchRoundUpSettings, ROUND_UP_INCREMENTS, setRoundUpSettings, type DepositProtectionStatus, type InterestJar, type RoundUpSettings, type SavingsGoal } from './lib/savings';
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
  cancelWeeklySavingsPlan, createWeeklySavingsPlan, fetchWeeklySavingsPlan, fetchWeeklySavingsPlans, withdrawWeeklySavingsPlan,
  WEEKLY_SAVINGS_ESCALATION_RATES, WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS, WEEKLY_SAVINGS_TERM_WEEKS,
  type WeeklySavingsPlan, type WeeklySavingsPlanDetail,
} from './lib/weeklySavings';
import {
  cancelGrow31SavingsPlan, createGrow31SavingsPlan, depositGrow31SavingsToday, fetchGrow31SavingsPlan, fetchGrow31SavingsPlans,
  withdrawGrow31SavingsPlan, grow31BonusRateForStreak, GROW31_TERM_DAYS,
  type Grow31SavingsPlan, type Grow31SavingsPlanDetail,
} from './lib/grow31Savings';
import { collectWithFacePay, enrollFacePay, fetchFacePayStatus, revokeFacePay } from './lib/facepay';
import { fetchMyP2pRequests, generateP2pRequest, payP2pRequest, resolveRecipient, sendDirect, sendToFamilyMember, type P2pPaymentRequestDto, type P2pPaymentRequestStatus, type P2pRecipientPreview } from './lib/p2p';
import { CertificateView } from './CertificateView';
import { LoansView, type LoansMode } from './LoansView';
import { fetchLinkedAccounts, type LinkedAccount } from './lib/overview';
import { fetchCreditScore, fetchCreditScoreSuggestions, type CreditScoreFactor, type CreditScoreResult, type CreditScoreSuggestion } from './lib/creditScore';
import { fetchTrustScore, type TrustScoreResult } from './lib/trustScore';
import { fetchRewardTasks, type RewardTasksResult } from './lib/rewards';
import { RewardsView } from './RewardsView';
import { BillsView } from './BillsView';
import { AgentOperatorView } from './AgentOperatorView';
import { UssdSettingsView } from './UssdSettingsView';
import {
  fetchMyUpfrontDeposits, openUpfrontDeposit, withdrawUpfrontDeposit,
  UPFRONT_DEPOSIT_ANNUAL_RATE, UPFRONT_DEPOSIT_MIN_PRINCIPAL, UPFRONT_DEPOSIT_MAX_PRINCIPAL,
  type UpfrontInterestDeposit,
} from './lib/upfrontDeposit';
import { ForeignCurrencyView } from './ForeignCurrencyView';
import { FullScreenFlow } from './FullScreenFlow';
import {
  advanceDineInOrderStatus, cancelDineInOrder, fetchMyDineInOrders, fetchRestaurantDineInOrders, placeDineInOrder,
  type DineInOrder, type DineInOrderStatus,
} from './lib/dineIn';
import { submitHoodReport, type HoodReportTargetType } from './lib/hoodReport';
import { IdentityView } from './IdentityView';
import { addContact, fetchContacts, type Contact } from './lib/contacts';
import { SupportView } from './SupportView';
import { SpendingInsightView } from './SpendingInsightView';
import { SubscriptionsView } from './SubscriptionsView';
import { cancelBillingSubscription, collectPayment, fetchLoyaltyBalance, fetchMerchantBillingPlans, fetchMerchantCategories, fetchMyBillingSubscriptions, fetchMyFollowedMerchants, fetchNearbyAds, fetchNearbyMerchants, fetchShopDeals, fetchShoppingCatalog, fetchSurplusDeals, followMerchant, generateCustomerPaymentCode, payByStaticQr, previewPaymentIntent, searchProducts, subscribeToBillingPlan, unfollowMerchant, type CollectPaymentResult, type CustomerPaymentCode, type MerchantBillingPlan, type MerchantBillingSubscription, type MerchantCouponView, type NearbyMerchant, type NearbyMerchantAd, type PaymentIntentPreview, type ProductSearchResult, type ShoppingMerchant, type SurplusDealResult } from './lib/shopping';
import { fetchActiveTimeDeals, fetchShopBanners, type TimeDealView } from './lib/timeDeal';
import { completeShoppingMission, fetchShoppingMissionStatus, type ShoppingMission, type SpinOutcome } from './lib/shoppingMissions';
import { StocksView } from './StocksView';
import {
  addGroupMember, connectMessagingSocket, createGroup, createOpenGroup, joinGroupByCode, fetchConversations, fetchGroupMembers, fetchGroupMessages, fetchGroupThread, fetchGroups, fetchMessages, fetchPinnedConversationMessage, fetchPinnedGroupMessage, fetchThread,
  blockConversationParticipant, deleteGroupMessage, deleteMessage, fetchConversationQuiet, fetchPresence, fetchTalkContacts, fetchTodaysBirthdays, forwardGroupMessage, forwardMessage, leaveGroup, pinConversationMessage, pinGroupMessage, reportChatMessage, searchConversationMessages, sendGroupMessage, sendMessage, setConversationArchived, setConversationPinnedToTop, setConversationQuiet, setGroupDescription, setGroupPhotoUrl, startConversation, startConversationWithUser, toggleGroupReaction, toggleReaction, unblockConversationParticipant, unpinConversationMessage, unpinGroupMessage,
  type ConversationSummary, type GroupMember, type GroupMessage,
  type GroupSummary, type Message, type MessagingSocketHandle, type ReactionGroup, type TalkContact,
} from './lib/messaging';
import {
  extractLinks, fetchCallHistory, setConversationFavorite,
  type CallSession, type ConversationSummaryWithFavorite,
} from './lib/talk';
import { TalkAiChatThread, TalkServiceChannelThread } from './TalkThreads';
import { TalkGroupAnnouncementPoll } from './TalkGroupAnnouncementPoll';
import { TalkLinksModal } from './TalkLinksTab';
import { TalkRoomSettings } from './TalkRoomSettings';
import { getRoomTheme, isRoomLocked, ROOM_THEMES } from './lib/roomSettings';
import {
  fetchEmoticonImageMap, fetchEmoticonPacks, fetchOwnedEmoticonPacks, fetchPackEmoticons, giftEmoticonPack, purchaseEmoticonPack, sendEmoticon,
  sendGroupEmoticon,
  type Emoticon, type EmoticonPack, type OwnedEmoticonPack,
} from './lib/emoticons';
import { extendGiftVoucherExpiry, fetchGiftVouchersForConversation, purchaseGiftVoucher, redeemGiftVoucher, type GiftVoucher, type GiftVoucherStatus } from './lib/giftVouchers';
import {
  attachSplitBillReceipt, createDirectSplitBill, createSplitBill, fetchDirectSplitBills, fetchSplitBillsForGroup, paySplitBillShare, requestSplitBillNextRound, type SplitBillWithParticipants,
} from './lib/splitBill';
import {
  fetchMyFavoriteListings, fetchMyListings, fetchOffersForConversation, respondToOffer,
  type HoodReview, type PriceOffer,
} from './lib/marketplace';
import { clearSecondNeighborhood, fetchProfile, setBirthDate, setNeighborhood, setSecondNeighborhood, updateProfilePhoto } from './lib/neighborhood';
import { confirmEmailVerification, confirmPhoneVerification, requestEmailVerification, requestPhoneVerification } from './lib/verification';
import { depositToYouthAccount, openYouthAccount } from './lib/youthAccount';
import { claimGift, fetchGiftsForConversation, sendGift, sendGiftInConversation, GIFT_THEME_LABELS, type Gift, type GiftStatus, type GiftTheme } from './lib/gift';
import { fetchMyFavoriteJobPosts, fetchMyJobPosts } from './lib/jobs';
import {
  fetchMyFavoritePropertyListings, fetchMyPropertyListings, fetchPropertyOffersForConversation, respondToPropertyOffer,
  type PropertyPriceOffer,
} from './lib/realestate';
import { uploadFile } from './lib/upload';
import {
  addFavoriteRestaurant, advanceRestaurantOrder, cancelEatsOrder, completePickupOrder, contactRestaurant,
  fetchEatsOrder, fetchMenu, fetchMyEatsOrders, fetchMyFavoriteRestaurants, fetchRestaurantCategories,
  fetchRestaurantOrders, fetchRestaurants, fetchRestaurantRating, fetchRestaurantGoodPoints, fetchRestaurantReviews, placeEatsOrder,
  removeFavoriteRestaurant, replyToRestaurantReview, reportEatsReview, searchDeliveryAddress, shareFavoritesToConversation, submitEatsReview, tipEatsOrderRider, toggleReviewHelpful, EATS_GOOD_POINT_LABELS,
  type AddressSuggestion, type EatsOrder, type EatsOrderStatus, type EatsReview, type EatsReviewReportReason, type FavoriteRestaurant, type MenuItem, type RatingSummary, type RestaurantSortMode,
} from './lib/eats';
import {
  EATS_MEMBERSHIP_TIERS, fetchMyMembership, fetchMyPlatformMembership, PLATFORM_MEMBERSHIP_TIERS, subscribeMembership, subscribePlatformMembership,
  type EatsMembership, type PlatformMembership,
} from './lib/eatsMembership';
import {
  advanceRiderOrder, claimDelivery, fetchAvailableDeliveries, fetchMyRiderProfile, fetchRiderDeliveries, registerRider, setRiderAvailability, type Rider,
} from './lib/eatsRider';
import {
  cancelGroupEatsOrder, createGroupEatsOrder, fetchGroupEatsOrder, finalizeGroupEatsOrder, joinGroupEatsOrder, setMyGroupEatsOrderItems, type GroupEatsOrderDetail,
} from './lib/eatsGroupOrders';
import {
  addProductFavorite, advanceOrderStatus, askProductInquiry, cancelOrder, decideOrderReturn, fetchMerchantOrders, fetchMerchantProducts, fetchMerchantReturnQueue,
  fetchMyFavoriteProducts, fetchMyOrders, fetchMyReturnRequests, fetchOrderDetail, fetchOrderRiderLocation, fetchPriceTiers, fetchProduct,
  fetchProductInquiries, fetchProductRating, fetchProductReviews, ORDER_RETURN_REASON_CODES, placeOrder, removeProductFavorite, requestOrderReturn, submitProductReview, toggleProductReviewHelpful,
  type CommerceOrder, type CommerceOrderItem, type CommerceOrderStatus, type CommerceProduct, type FavoriteProduct, type OrderReturnRequestDto, type OrderReturnType, type PriceTier, type ProductInquiry, type ProductReview,
} from './lib/commerce';
import {
  captureReferralCodeFromUrl, createAffiliateLink, fetchMyAffiliateCommissions, fetchMyAffiliateLinks, getStoredReferralCode,
  type AffiliateCommission, type AffiliateLink,
} from './lib/affiliate';
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
import RouteMiniMap from './RouteMiniMap';
import LiveRiderMap from './LiveRiderMap';
import SimpleLiveRiderMap from './SimpleLiveRiderMap';
import { fetchMyMapBookmarks, searchPlaces, type MapBookmark, type PlaceSearchResult } from './lib/maps';
import { fetchMiniAppCatalog, type PartnerMiniApp } from './lib/partners';
import { recentlyViewedProductsStore, recentlyViewedRestaurantsStore } from './lib/recentlyViewed';
import { checkScamStatus, reportScam, type ScamCheckResult } from './lib/scamReports';
import { fetchMyVehicles, fetchVehicleValuation, registerVehicle, removeVehicle, updateVehicleMileage, type Vehicle, type VehicleValuation } from './lib/vehicles';
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
import {
  fetchChildOverview, fetchMyChildren, fetchMyGuardians, fetchMyInvites, inviteChild, respondToInvite, revokeFamilyLink,
  type ChildOverview, type FamilyLinkView,
} from './lib/family';
import {
  cancelProductSubscription, fetchMyProductSubscriptions, pauseProductSubscription, resumeProductSubscription, skipNextProductSubscriptionDelivery, subscribeToProduct, updateProductSubscription,
  type ProductSubscription,
} from './lib/productSubscriptions';
import { cancelScheduledTransfer, createScheduledTransfer, fetchMyScheduledTransfers, type ScheduledTransfer } from './lib/scheduledTransfers';
import { cancelDelayedTransfer, fetchMyDelayedTransfers, sendDelayed, type DelayedTransfer } from './lib/delayedTransfers';
import {
  cancelAutoTransfer, createAutoTransfer, fetchMyAutoTransfers, pauseAutoTransfer, resumeAutoTransfer,
  type AutoTransfer, type AutoTransferFrequency,
} from './lib/autoTransfers';
import {
  acceptRideTrip, addTrustedContact, arriveAtRideStop, cancelRideTrip, clearDriverDestination, completeRideTrip, declineRideTrip, estimateRideFare, fetchAvailableTrips, fetchDriverRating,
  fetchDriverReviews, fetchMyDriverProfile, fetchMyDriverTrips, fetchMyEarnings, fetchMyTrips, fetchRideTripPin, fetchTripStops, fetchTrustedContacts, registerAsDriver, removeTrustedContact, requestRideTrip, sendStatusToTrustedContacts, setDriverAvailability, setDriverDestination,
  shareRideTripStatus, startRideTrip, submitRideReview, tipDriver, updateDriverLocation,
  type DriverDailyEarnings, type RideDriver, type RideDriverRating, type RideTrip, type RideTripReview, type RideTripStop, type RideTrustedContact,
} from './lib/rideshare';
import {
  acceptDesignatedDriverTrip, cancelDesignatedDriverTrip, completeDesignatedDriverTrip, fetchAvailableDesignatedDriverTrips,
  fetchMyDesignatedDriverDriverTrips, fetchMyDesignatedDriverProfile, fetchMyDesignatedDriverTrips, registerAsDesignatedDriver,
  requestDesignatedDriverTrip, setDesignatedDriverAvailability, startDesignatedDriverTrip, updateDesignatedDriverLocation,
  type DesignatedDriver, type DesignatedDriverTrip,
} from './lib/designatedDriver';
import {
  adoptKnowledgeAnswer, fetchKnowledgeAnswers, fetchKnowledgeCategories, fetchKnowledgeQuestion, fetchKnowledgeQuestions,
  fetchMyKnowledgeAnswers, fetchMyKnowledgeQuestions, fetchMyKnowledgeReputation, postKnowledgeAnswer, postKnowledgeQuestion,
  type KnowledgeAnswer, type KnowledgeCategory, type KnowledgeQuestion,
} from './lib/knowledge';
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
type Tab = 'HOME' | 'PAY' | 'EXPLORE' | 'YOU' | 'CERTIFICATE' | 'SHOPPING' | 'SHOP' | 'EATS' | 'MARKETPLACE' | 'COMMUNITY' | 'JOBS' | 'PROPERTY' | 'STOCKS' | 'SAVINGS' | 'MESSAGES' | 'RIDES' | 'DESIGNATED_DRIVER' | 'BIKESHARE' | 'PARKING' | 'BUS' | 'KNOWLEDGE' | 'MAP' | 'DEVICES' | 'CARD' | 'TRANSIT' | 'TRANSIT_COLLECT' | 'MOTO_FARE_COLLECT' | 'OVERVIEW' | 'LOANS' | 'CREDIT_SCORE' | 'TRUST_SCORE' | 'IDENTITY' | 'SUPPORT' | 'MY' | 'SUBSCRIPTIONS' | 'SPENDING' | 'FOREIGN_CURRENCY' | 'REWARDS' | 'INSURANCE' | 'BILLS' | 'AGENT' | 'USSD';

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

function TransferFlow({ onClose, onSuccess, onBalanceRefresh, accountBalance }: { onClose: () => void; onSuccess: () => void; onBalanceRefresh?: () => void; accountBalance: number }) {
  const { t } = useI18n();
  const TRANSFER_STEP_LABELS = [t('transfer.stepRecipient'), t('transfer.stepAmount'), t('transfer.stepConfirm')];
  const [step, setStep] = useState<TransferStep>('recipient');
  const [recipient, setRecipient] = useState('');
  const [recipientPreview, setRecipientPreview] = useState<P2pRecipientPreview | null>(null);
  const [amount, setAmount] = useState('');
  const [memo, setMemo] = useState('');
  const [result, setResult] = useState<{ message: string; newBalance: number } | null>(null);
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
      const res = await sendDirect(recipient.trim(), Number(amount), memo.trim());
      setResult({ message: res.message, newBalance: res.newBalance });
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
              style={{ padding: '16px 0', background: 'none', border: 'none', fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 600, color: 'var(--itunda-grey-900)', borderRadius: '10px' }}
            >
              {k}
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
    setFacePayBusy(true);
    try {
      if (facePayEnrolled) await revokeFacePay(); else await enrollFacePay();
      setFacePayEnrolled((v) => !v);
    } catch {
      // Non-critical -- the row just keeps showing the last-known enrollment state.
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

      {youthAccount && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-20-size)', fontWeight: 700 }}>{animatedBalance.toLocaleString()} RWF</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: showDeposit ? '10px' : 0 }}>{youthAccount.accountNumber}</p>
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

// Real "alternative data" credit score (2026-07-22) -- found fully built on the
// backend (rw.itunda.creditscore) with zero client UI anywhere. Not a real bureau
// score -- computed live from a user's own real transaction/loan/savings/KYC history.
function CreditScoreView() {
  const { t } = useI18n();
  const [result, setResult] = useState<CreditScoreResult | null>(null);
  const [suggestions, setSuggestions] = useState<CreditScoreSuggestion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCreditScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    // Real Toss 신용플러스-style suggestions (2026-07-26) -- see
    // CreditScoreService.getImprovementSuggestions's own doc comment. Loaded alongside
    // the score itself, not gated behind it -- a real failure here shouldn't block the
    // score from rendering.
    fetchCreditScoreSuggestions()
      .then(setSuggestions)
      .catch(() => setSuggestions([]));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Your score</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{result.score} / 850</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Based on your own account activity, not a bureau report.</p>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
      {suggestions !== null && suggestions.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What would raise your score</h3>
          {suggestions.map((s) => (
            <div key={s.action} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
              <div>
                <p>{s.action}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{s.description}</p>
              </div>
              <span style={{ color: 'var(--itunda-indigo)', fontWeight: 700 }}>+{s.pointsGain}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Karrot-Score-style trust/reputation view (item 152) -- see lib/trustScore.ts's
// own doc comment. Every Hood card already shows a batch-read trustScore badge for the
// OTHER party (seller/poster/lister); this is the separate "see your own full factor
// breakdown" screen, mirroring CreditScoreView's own shape exactly.
function TrustScoreView() {
  const { t } = useI18n();
  const [result, setResult] = useState<TrustScoreResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTrustScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p> : <div className="skeleton" style={{ height: '200px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <div className="itunda-flat-section">
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Your trust score</p>
        <h2 style={{ fontSize: 'var(--itunda-type-scale-26-size)', fontWeight: 700 }}>{result.score} / 1000</h2>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>How your neighbors see you on Marketplace, Jobs, and Property.</p>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Naver-style "My" personal hub (2026-07-22), at the user's direct request:
// "My should be like Naver style My since we have shopping and eats and other
// products where users need to easily get track of their orders, reservation,
// favorites." bank-mfe's own nav is a flat always-visible tab bar (not a mobile
// bottom-nav-plus-hamburger-menu), so the Android/iOS "split My from a KakaoPay-style
// 전체 menu" half of this redesign doesn't map here -- every tab is already directly
// reachable. This tab is purely additive: a real cross-product activity summary,
// reusing each product tab's own existing fetch functions rather than duplicating
// their per-product order/favorite views.
function MyView() {
  const [shopOrders, setShopOrders] = useState<CommerceOrder[]>([]);
  const [eatsOrders, setEatsOrders] = useState<EatsOrder[]>([]);
  const [favoriteListingsCount, setFavoriteListingsCount] = useState(0);
  const [favoriteJobPostsCount, setFavoriteJobPostsCount] = useState(0);
  const [favoritePropertyListingsCount, setFavoritePropertyListingsCount] = useState(0);
  const [favoriteRestaurantsCount, setFavoriteRestaurantsCount] = useState(0);
  const [myListingsCount, setMyListingsCount] = useState(0);
  const [myJobPostsCount, setMyJobPostsCount] = useState(0);
  const [myPropertyListingsCount, setMyPropertyListingsCount] = useState(0);
  const [miniApps, setMiniApps] = useState<PartnerMiniApp[]>([]);

  useEffect(() => {
    // Each fetch independent and best-effort -- one product's API hiccup must never
    // blank the rest of this real personal-activity summary.
    fetchMyOrders().then(setShopOrders).catch(() => {});
    fetchMyEatsOrders().then(setEatsOrders).catch(() => {});
    fetchMyFavoriteListings().then((r) => setFavoriteListingsCount(r.length)).catch(() => {});
    fetchMyFavoriteJobPosts().then((r) => setFavoriteJobPostsCount(r.length)).catch(() => {});
    fetchMyFavoritePropertyListings().then((r) => setFavoritePropertyListingsCount(r.length)).catch(() => {});
    fetchMyFavoriteRestaurants().then((r) => setFavoriteRestaurantsCount(r.length)).catch(() => {});
    fetchMyListings().then((r) => setMyListingsCount(r.listings.length)).catch(() => {});
    fetchMyJobPosts().then((r) => setMyJobPostsCount(r.posts.length)).catch(() => {});
    fetchMyPropertyListings().then((r) => setMyPropertyListingsCount(r.listings.length)).catch(() => {});
    fetchMiniAppCatalog().then(setMiniApps).catch(() => {});
  }, []);

  const rowStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', padding: '8px 0', fontSize: 'var(--itunda-type-scale-13-size)' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <ProfilePhotoCard />
      <PinSetupCard />
      <VerificationCard />
      <NotificationsCard />
      {/* MyBookingsCard moved to itunda Place (2026-08-25) -- see maps-mfe's own
          MapsBooking.tsx doc comment. */}
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card wrapping --
          My orders/My favorites/My listings/Mini apps are real sections in this
          screen's own stack of widgets, now flat matching itunda-flat-section's
          border-bottom divider convention. */}
      {(shopOrders.length > 0 || eatsOrders.length > 0) && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My orders</h3>
          {shopOrders.slice(0, 3).map((order) => (
            <div key={order.id} style={rowStyle}>
              <span>Shop order · {order.status}</span>
              <span>{order.totalAmount.toLocaleString()} RWF</span>
            </div>
          ))}
          {eatsOrders.slice(0, 3).map((order) => (
            <div key={order.id} style={rowStyle}>
              <span>Eats order · {order.status}</span>
              <span>{order.totalAmount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My favorites</h3>
        <div style={rowStyle}><span>Marketplace wishlist</span><span>{favoriteListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs wishlist</span><span>{favoriteJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property wishlist</span><span>{favoritePropertyListingsCount}</span></div>
        <div style={rowStyle}><span>Restaurant favorites</span><span>{favoriteRestaurantsCount}</span></div>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>My listings</h3>
        <div style={rowStyle}><span>Marketplace</span><span>{myListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs posted</span><span>{myJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property listed</span><span>{myPropertyListingsCount}</span></div>
      </div>
      <MyVehiclesCard />
      <FamilyLinkCard />
      <MyProductSubscriptionsCard />
      <AffiliateEarningsCard />
      {miniApps.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Mini apps</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Third-party apps reviewed and approved to run inside itunda.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {miniApps.map((app) => (
              <div key={app.id} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                {app.iconUrl ? (
                  <img src={app.iconUrl} alt="" style={{ width: '36px', height: '36px', borderRadius: '8px', flexShrink: 0 }} />
                ) : (
                  <div style={{ width: '36px', height: '36px', borderRadius: '8px', backgroundColor: 'var(--itunda-grey-100)', flexShrink: 0 }} />
                )}
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{app.name}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{app.description}</p>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real in-app notification inbox (item, found via a backend-module sweep) -- see
// lib/notifications.ts's own doc comment. Already ported to Android (SettingsScreen.kt)
// and iOS (SettingsViewModel.swift), but bank-mfe had zero client for the inbox itself.
function NotificationsCard() {
  const [notifications, setNotifications] = useState<NotificationItem[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);

  const load = () => {
    fetchNotifications()
      .then((r) => {
        setNotifications(r.notifications);
        setUnreadCount(r.unreadCount);
      })
      .catch(() => {});
  };
  useEffect(load, []);

  const handleRead = async (id: string) => {
    await markNotificationRead(id).catch(() => {});
    load();
  };
  const handleReadAll = async () => {
    await markAllNotificationsRead().catch(() => {});
    load();
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this widget
  // renders as one of many stacked sections on MyView's linear screen, not a
  // genuinely separate module (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Notifications</h3>
        {unreadCount > 0 && (
          <span
            role="button"
            onClick={handleReadAll}
            style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-indigo)', cursor: 'pointer' }}
          >
            Mark all read
          </span>
        )}
      </div>
      {notifications.length === 0 ? (
        <EmptyState message="You're all caught up — new activity will show up here." />
      ) : (
        notifications.slice(0, 10).map((n) => (
          <div
            key={n.id}
            role="button"
            tabIndex={0}
            onClick={() => !n.isRead && handleRead(n.id)}
            onKeyDown={(e) => { if ((e.key === 'Enter' || e.key === ' ') && !n.isRead) { e.preventDefault(); handleRead(n.id); } }}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '2px',
              padding: '10px 0',
              borderTop: '1px solid var(--itunda-grey-100)',
              cursor: n.isRead ? 'default' : 'pointer',
            }}
          >
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: n.isRead ? 400 : 700 }}>{n.title}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{n.body}</span>
          </div>
        ))
      )}
    </div>
  );
}

// Real Toss 내 차 시세 (my car's market value) -- see lib/vehicles.ts's own doc comment
// for the full sourced account and honest scope boundary (a documented general
// depreciation estimate, not a real Carmart-style data partnership).
// Real email/phone verification (item 169) -- see lib/verification.ts's own doc
// comment. bank-mfe (the actual banking app) had zero client for either.
// Real profile photo upload (2026-08-13) -- found via the user's own "photo link vs.
// upload" UX audit request: the previous version of this card (2026-07-29, see git
// history) asked the user to paste a URL to a photo "hosted elsewhere," reasoning
// that itunda had no upload/storage pipeline to build a real picker on top of. That
// reasoning was stale even on the day it was written -- rw.itunda.marketplace.web.
// UploadController's own real `POST /api/v1/uploads` (multipart, validated,
// rate-limited local-disk storage) had existed since 2026-07-24, and this file's own
// Talk photo-message flow (handleSendPhoto, above) already used it. Profile photo
// was the one remaining "paste a link" holdout in bank-mfe; this wires it to the
// same real upload endpoint every other photo flow in this app already uses.
function ProfilePhotoCard() {
  const { t } = useI18n();
  const [profilePhotoUrl, setProfilePhotoUrl] = useState<string | null>(null);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    fetchProfile()
      .then((u) => setProfilePhotoUrl(u.profilePhotoUrl))
      .catch(() => {
        // Real, non-critical -- the rest of "My" still works without this.
      });
  }, []);

  const handleFileSelected = async (file: File | undefined) => {
    if (!file) return;
    setUploading(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      const user = await updateProfilePhoto(url);
      setProfilePhotoUrl(user.profilePhotoUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploading(false);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <input
        ref={fileInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleFileSelected(e.target.files?.[0])}
      />
      <div style={{ display: 'flex', gap: '12px', alignItems: 'center' }}>
        <button
          type="button"
          onClick={() => fileInputRef.current?.click()}
          disabled={uploading}
          style={{ width: '56px', height: '56px', borderRadius: '28px', flexShrink: 0, padding: 0, border: 'none', overflow: 'hidden', opacity: uploading ? 0.5 : 1 }}
          aria-label={profilePhotoUrl ? 'Change profile photo' : 'Add profile photo'}
        >
          {profilePhotoUrl ? (
            <img src={profilePhotoUrl} alt="" style={{ width: '100%', height: '100%', objectFit: 'cover' }} />
          ) : (
            <div style={{ width: '100%', height: '100%', backgroundColor: 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Camera size={20} color="var(--itunda-grey-500)" />
            </div>
          )}
        </button>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600, color: 'var(--itunda-grey-900)', margin: 0 }}>Profile photo</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0 0' }}>
            {uploading ? 'Uploading…' : 'Tap to choose a photo from your device'}
          </p>
        </div>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Toss-sourced passwordless-login rollout (2026-08-24) -- see backend
// User.pinSet's own doc comment. A real, non-blocking upgrade prompt for a
// pre-PIN-era user -- their existing password keeps working exactly as before either
// way (AuthService.login is shape-agnostic); this is purely an offered convenience,
// never forced. `currentCredential` is a plain text field (their existing password
// could be any shape, not necessarily 6 digits, so PinPad doesn't apply there);
// `newPin`/confirm reuse the real PinPad component RegisterPage/LoginPage already
// established.
function VerificationCard() {
  const [status, setStatus] = useState<{ email: string | null; emailVerified: boolean; phoneVerified: boolean } | null>(null);

  const load = () => {
    fetchProfile().then((u) => setStatus({ email: u.email, emailVerified: u.emailVerified, phoneVerified: u.phoneVerified })).catch(() => {});
  };
  useEffect(load, []);

  if (!status || (status.emailVerified && status.phoneVerified)) return null;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a lone
  // conditional section on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Verify your account</h3>
      {!status.phoneVerified && <VerificationRow kind="phone" onVerified={load} />}
      {!status.emailVerified && <VerificationRow kind="email" hasEmail={status.email !== null} onVerified={load} />}
    </div>
  );
}

function VerificationRow({ kind, hasEmail = true, onVerified }: { kind: 'email' | 'phone'; hasEmail?: boolean; onVerified: () => void }) {
  const { t } = useI18n();
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [verified, setVerified] = useState(false);
  const shakeControls = useAnimation();

  const handleSend = async () => {
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? requestEmailVerification() : requestPhoneVerification());
      setSent(true);
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): only
      // reachable via stale client state (verified on another device/tab between
      // this row rendering and the tap) -- not really a failure, resolve forward by
      // refreshing so this row correctly disappears.
      if (err instanceof ApiError && (err.code === 'EMAIL_ALREADY_VERIFIED' || err.code === 'PHONE_ALREADY_VERIFIED')) {
        onVerified();
      } else {
        setError(err instanceof ApiError ? err.message : `Could not send a ${kind} verification code.`);
      }
    } finally {
      setBusy(false);
    }
  };

  const handleConfirm = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (busy || !code.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? confirmEmailVerification(code.trim()) : confirmPhoneVerification(code.trim()));
      // Real Toss-style "OTP Successful Animation" (60fps.design's own real
      // catalog of Toss's named interactions) -- a brief green checkmark moment
      // before navigating away, reusing TransferFlow's own established success-
      // checkmark language (itunda-green circle + white Check, spring scale-in)
      // rather than calling onVerified() instantly with zero feedback, which is
      // what every platform did before this pass.
      setVerified(true);
      setTimeout(onVerified, 650);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      // Real Toss-style wrong-code shake, matching PinPad's own already-
      // established error-shake pattern (itunda had it on the PIN pad but never
      // on this OTP field).
      shakeControls.start({ x: [0, -8, 8, -8, 8, 0], transition: { duration: 0.4 } });
    } finally {
      setBusy(false);
    }
  };

  // Real "Minimum Input" simplicity fix (Toss's own researched, sourced pattern --
  // toss.tech/article/4-ways-for-minimum-input, rule #2: "for fixed-digit fields like
  // ID or phone numbers, the CTA button becomes unnecessary" -- see
  // docs/DESIGN_REFERENCES.md §11). This code is a real, fixed 6-digit OTP
  // (AuthService.kt's own doc comment). Auto-confirms the instant the 6th digit is
  // typed; the button stays visible as a manual fallback rather than being removed
  // outright, since this is a security-sensitive identity-verification step.
  useEffect(() => {
    const trimmed = code.trim();
    if (trimmed.length === 6 && /^\d{6}$/.test(trimmed) && !busy) handleConfirm();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [code]);

  if (kind === 'email' && !hasEmail) {
    return <EmptyState message="No email address on file to verify." />;
  }

  return (
    <div style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
      {verified ? (
        <motion.div
          initial={{ scale: 0.4, opacity: 0 }}
          animate={{ scale: 1, opacity: 1 }}
          transition={{ type: 'spring', ...itundaSpring.medium }}
          style={{ display: 'flex', alignItems: 'center', gap: '8px' }}
        >
          <span style={{ width: '22px', height: '22px', borderRadius: '11px', backgroundColor: 'var(--itunda-green)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Check size={14} color="#ffffff" />
          </span>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600, color: 'var(--itunda-green)' }}>
            {kind === 'email' ? 'Email verified' : 'Phone number verified'}
          </span>
        </motion.div>
      ) : !sent ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{kind === 'email' ? 'Email' : 'Phone number'} not verified</span>
          <button className="itunda-btn itunda-btn-secondary" disabled={busy} onClick={handleSend} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
            {busy ? '…' : 'Send code'}
          </button>
        </div>
      ) : (
        <motion.form onSubmit={handleConfirm} animate={shakeControls} style={{ display: 'flex', gap: '8px' }}>
          <motion.input
            type="text" inputMode="numeric" pattern="[0-9]*" autoFocus placeholder="Enter code" value={code} onChange={(e) => setCode(e.target.value)} required
            animate={busy ? { opacity: [1, 0.55, 1] } : { opacity: 1 }}
            transition={busy ? { duration: 0.9, repeat: Infinity, ease: 'easeInOut' } : undefined}
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          {/* Real CTA-label-clarity fix (2026-08-24, docs/DESIGN_REFERENCES.md §11 --
              picked up the explicitly-flagged "not audited this pass" recommendation:
              cross-reference generic Confirm/Submit/OK labels against their real
              action). A bare "Confirm" doesn't state the outcome; states the specific
              action instead, matching the fix already applied to TransferFlow's own
              identical bare-"Confirm" button nearby. */}
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
            {busy ? '…' : kind === 'email' ? 'Verify email' : 'Verify phone number'}
          </button>
        </motion.form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function MyVehiclesCard() {
  const { t } = useI18n();
  const [vehicles, setVehicles] = useState<Vehicle[] | null>(null);
  const [valuations, setValuations] = useState<Record<string, VehicleValuation>>({});
  const [showCreate, setShowCreate] = useState(false);
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [modelYear, setModelYear] = useState(String(new Date().getFullYear()));
  const [purchasePrice, setPurchasePrice] = useState('');
  const [purchaseDate, setPurchaseDate] = useState('');
  const [mileageKm, setMileageKm] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyVehicles()
      .then((list) => {
        setVehicles(list);
        Promise.all(list.map((v) => fetchVehicleValuation(v.id).then((val) => [v.id, val] as const)))
          .then((pairs) => setValuations(Object.fromEntries(pairs)))
          .catch(() => {});
      })
      .catch(() => {});
  };

  useEffect(load, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    const price = Number(purchasePrice);
    const mileage = Number(mileageKm);
    const year = Number(modelYear);
    if (!make.trim() || !model.trim() || !(price > 0) || !purchaseDate || mileage < 0) return;
    setBusy(true);
    setError(null);
    try {
      await registerVehicle(make.trim(), model.trim(), year, price, purchaseDate, mileage);
      setMake('');
      setModel('');
      setPurchasePrice('');
      setPurchaseDate('');
      setMileageKm('');
      setShowCreate(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleUpdateMileage = async (vehicle: Vehicle) => {
    const input = window.prompt('Update mileage (km)', String(vehicle.mileageKm));
    if (input == null) return;
    const newMileage = Number(input);
    if (!(newMileage >= 0)) return;
    setBusyId(vehicle.id);
    setError(null);
    try {
      await updateVehicleMileage(vehicle.id, newMileage);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRemove = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await removeVehicle(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>My vehicles</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ Add'}
        </button>
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Estimated resale value based on age and mileage -- itunda's own general estimate, not a market comp.
      </p>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Make (e.g. Toyota)" value={make} onChange={(e) => setMake(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="text" placeholder="Model (e.g. RAV4)" value={model} onChange={(e) => setModel(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Model year" value={modelYear} onChange={(e) => setModelYear(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Purchase price (RWF)" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} max={new Date().toISOString().slice(0, 10)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="number" placeholder="Current mileage (km)" value={mileageKm} onChange={(e) => setMileageKm(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add vehicle'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {(vehicles ?? []).length === 0 && <EmptyState message="No vehicles added yet — add one to track its value and get real offers." />}

      {(vehicles ?? []).map((v) => {
        const valuation = valuations[v.id];
        return (
          <div key={v.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{v.modelYear} {v.make} {v.model}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                  {v.mileageKm.toLocaleString()} km
                  {valuation && (
                    <> · {valuation.ageYears} {valuation.ageYears === 1 ? 'year' : 'years'} old · expected {valuation.expectedMileageKm.toLocaleString()} km</>
                  )}
                </p>
              </div>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === v.id} onClick={() => handleUpdateMileage(v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Update km
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === v.id} onClick={() => handleRemove(v.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Remove
                </button>
              </div>
            </div>
            {valuation && (
              <div style={{ marginTop: '8px', display: 'flex', gap: '12px', fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                <span>Now: <strong style={{ color: 'var(--itunda-grey-900)' }}>{valuation.currentEstimatedValue.toLocaleString()} RWF</strong></span>
                <span>+1y: {valuation.estimatedValueIn1Year.toLocaleString()}</span>
                <span>+2y: {valuation.estimatedValueIn2Years.toLocaleString()}</span>
                <span>+3y: {valuation.estimatedValueIn3Years.toLocaleString()}</span>
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
}

// Real Toss 유스 (Toss Youth)-style guardian-child link -- see lib/family.ts's own doc
// comment for the full sourced account and honest scope boundary (real read-only
// spending oversight only; allowance reuses AutoTransfer/ScheduledTransfer above).
function FamilyLinkCard() {
  const { t } = useI18n();
  const [invites, setInvites] = useState<FamilyLinkView['link'][] | null>(null);
  const [children, setChildren] = useState<FamilyLinkView[] | null>(null);
  const [guardians, setGuardians] = useState<FamilyLinkView[] | null>(null);
  const [showInvite, setShowInvite] = useState(false);
  const [childPhone, setChildPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openOverviewFor, setOpenOverviewFor] = useState<string | null>(null);
  const [overview, setOverview] = useState<ChildOverview | null>(null);
  const [sendAmount, setSendAmount] = useState('');
  const [sendBusy, setSendBusy] = useState(false);
  const [sendDone, setSendDone] = useState(false);

  const load = () => {
    fetchMyInvites().then(setInvites).catch(() => {});
    fetchMyChildren().then(setChildren).catch(() => {});
    fetchMyGuardians().then(setGuardians).catch(() => {});
  };

  useEffect(load, []);

  const handleInvite = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!childPhone.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await inviteChild(childPhone.trim());
      setChildPhone('');
      setShowInvite(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRespond = async (id: string, accept: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await respondToInvite(id, accept);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleRevoke = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await revokeFamilyLink(id);
      setOpenOverviewFor(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleToggleOverview = async (childUserId: string) => {
    if (openOverviewFor === childUserId) {
      setOpenOverviewFor(null);
      return;
    }
    setOpenOverviewFor(childUserId);
    setSendAmount('');
    setSendDone(false);
    try {
      setOverview(await fetchChildOverview(childUserId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.loadError'));
    }
  };

  const handleSendToChild = async (childUserId: string) => {
    const amount = Number(sendAmount);
    if (!amount || amount <= 0) return;
    setSendBusy(true);
    setError(null);
    try {
      await sendToFamilyMember(childUserId, amount, 'Sent from Family');
      setSendDone(true);
      setSendAmount('');
      setOverview(await fetchChildOverview(childUserId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSendBusy(false);
    }
  };

  const hasAnything = (invites?.length ?? 0) > 0 || (children?.length ?? 0) > 0 || (guardians?.length ?? 0) > 0;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Family</h3>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowInvite((v) => !v)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
          {showInvite ? 'Cancel' : '+ Link a family member'}
        </button>
      </div>

      {showInvite && (
        <form onSubmit={handleInvite} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Phone number" value={childPhone} onChange={(e) => setChildPhone(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}

      {(invites ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Pending invitations</p>
          {(invites ?? []).map((inv) => (
            <div key={inv.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>Family link request</p>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="itunda-btn itunda-btn-primary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, true)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>Accept</button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, false)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>Decline</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {(children ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Linked children</p>
          {(children ?? []).map((c) => (
            <div key={c.link.id} style={{ padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{c.childName}</p>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <button className="itunda-btn itunda-btn-secondary" onClick={() => handleToggleOverview(c.link.childUserId)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    {openOverviewFor === c.link.childUserId ? 'Hide' : 'View'}
                  </button>
                  <button className="itunda-btn itunda-btn-secondary" disabled={busyId === c.link.id} onClick={() => handleRevoke(c.link.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    Unlink
                  </button>
                </div>
              </div>
              {openOverviewFor === c.link.childUserId && overview && (
                <div style={{ marginTop: '6px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                  <p>Balance: <strong style={{ color: 'var(--itunda-grey-900)' }}>{overview.accountBalance.toLocaleString()} RWF</strong></p>
                  {overview.recentTransactions.slice(0, 5).map((t) => (
                    <p key={t.id}>{t.description} · {t.amount.toLocaleString()} RWF</p>
                  ))}
                  {overview.recentTransactions.length === 0 && <EmptyState message="Nothing here yet — your activity will show up as you use itunda." />}
                  {/* Real Naver Pay "family shared asset management" -- instant transfer
                      to this linked family member, see lib/p2p.ts's own doc comment. */}
                  <div style={{ display: 'flex', gap: '6px', marginTop: '8px' }}>
                    <input
                      type="number" min={1} placeholder="Amount (RWF)" value={sendAmount}
                      onChange={(e) => { setSendAmount(e.target.value); setSendDone(false); }}
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    />
                    <button
                      className="itunda-btn itunda-btn-primary" disabled={sendBusy || !sendAmount}
                      onClick={() => handleSendToChild(c.link.childUserId)}
                      style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}
                    >
                      {sendBusy ? '…' : 'Send'}
                    </button>
                  </div>
                  {sendDone && <p style={{ color: 'var(--itunda-green)', marginTop: '4px' }}>Sent.</p>}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(guardians ?? []).length > 0 && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>Your guardians</p>
          {(guardians ?? []).map((g) => (
            <div key={g.link.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{g.guardianName}</p>
              <button className="itunda-btn itunda-btn-secondary" disabled={busyId === g.link.id} onClick={() => handleRevoke(g.link.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                Unlink
              </button>
            </div>
          ))}
        </div>
      )}

      {!hasAnything && <EmptyState message="No family members linked yet — invite one to manage their spending together." />}
    </div>
  );
}

// Real Coupang 정기배송 (subscribe & save) -- see lib/productSubscriptions.ts's own doc
// comment for the full sourced account. A minimal delivery-address prompt rather than a
// full address form, matching this pass's compact-card scope.
function SubscribeAndSaveButton({ merchantId, productId }: { merchantId: string; productId: string }) {
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

const PRODUCT_SUBSCRIPTION_STATUS_LABEL: Record<ProductSubscription['status'], string> = {
  ACTIVE: 'Active', PAUSED: 'Paused', CANCELLED: 'Cancelled',
};

// Real Coupang 정기배송-style subscription list -- see lib/productSubscriptions.ts's
// own doc comment.
function MyProductSubscriptionsCard() {
  const { t } = useI18n();
  const [subscriptions, setSubscriptions] = useState<ProductSubscription[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real Coupang 정기배송 수량/주기 변경 -- inline edit, one subscription open at a time.
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editQuantity, setEditQuantity] = useState('');
  const [editIntervalDays, setEditIntervalDays] = useState('');

  const load = () => {
    fetchMyProductSubscriptions().then(setSubscriptions).catch(() => {});
  };

  useEffect(load, []);

  const handleToggle = async (s: ProductSubscription) => {
    setBusyId(s.id);
    setError(null);
    try {
      if (s.status === 'ACTIVE') await pauseProductSubscription(s.id);
      else await resumeProductSubscription(s.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleCancel = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await cancelProductSubscription(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  // Real Coupang 정기배송 "건너뛰기" -- see lib/productSubscriptions.ts's own doc
  // comment. Found via scripts/uncalled-endpoint-sweep.py.
  const handleSkipNext = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await skipNextProductSubscriptionDelivery(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const startEdit = (s: ProductSubscription) => {
    setEditingId(s.id);
    setEditQuantity(String(s.quantity));
    setEditIntervalDays(String(s.intervalDays));
    setError(null);
  };

  const handleSaveEdit = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await updateProductSubscription(id, Number(editQuantity), Number(editIntervalDays));
      setEditingId(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (!subscriptions || subscriptions.length === 0) return null;

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Subscribe & save</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {subscriptions.map((s) => (
        <div key={s.id} style={{ padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Qty {s.quantity} · every {s.intervalDays}d</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
                {s.status === 'CANCELLED' && s.cancelledAt
                  ? `Cancelled ${new Date(s.cancelledAt).toLocaleDateString()}`
                  : `${PRODUCT_SUBSCRIPTION_STATUS_LABEL[s.status]} · ${s.deliveryCount} delivered · next ${new Date(s.nextDeliveryAt).toLocaleDateString()}`}
              </p>
              {s.lastFailureReason && s.status === 'ACTIVE' && (
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>Last delivery failed: {s.lastFailureReason}</p>
              )}
            </div>
            {s.status !== 'CANCELLED' && (
              <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                {s.status === 'ACTIVE' && (
                  <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleSkipNext(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                    {busyId === s.id ? '…' : 'Skip next'}
                  </button>
                )}
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => startEdit(s)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Edit
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleToggle(s)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  {busyId === s.id ? '…' : s.status === 'ACTIVE' ? 'Pause' : 'Resume'}
                </button>
                <button className="itunda-btn itunda-btn-secondary" disabled={busyId === s.id} onClick={() => handleCancel(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}>
                  Cancel
                </button>
              </div>
            )}
          </div>
          {editingId === s.id && (
            <div style={{ display: 'flex', gap: '6px', marginTop: '8px', alignItems: 'center' }}>
              <input
                type="number" min="1" value={editQuantity} onChange={(e) => setEditQuantity(e.target.value)}
                placeholder="Quantity"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
              />
              <input
                type="number" min="1" value={editIntervalDays} onChange={(e) => setEditIntervalDays(e.target.value)}
                placeholder="Every N days"
                style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" disabled={busyId === s.id || !(Number(editQuantity) > 0) || !(Number(editIntervalDays) > 0)}
                onClick={() => handleSaveEdit(s.id)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}
              >
                {busyId === s.id ? '…' : 'Save'}
              </button>
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setEditingId(null)} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
                Cancel
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate earnings summary (item 229) --
// see lib/affiliate.ts's own doc comment. Link creation itself happens inline on each
// product card (ProductCatalogView's own "🔗 Share & earn" button); this card is
// purely the read-back: how many links exist, how many clicks, and real commissions
// earned so far.
function AffiliateEarningsCard() {
  const [links, setLinks] = useState<AffiliateLink[] | null>(null);
  const [commissions, setCommissions] = useState<AffiliateCommission[] | null>(null);

  useEffect(() => {
    fetchMyAffiliateLinks().then(setLinks).catch(() => {});
    fetchMyAffiliateCommissions().then(setCommissions).catch(() => {});
  }, []);

  if (!links || links.length === 0) return null;

  const totalEarned = (commissions ?? []).reduce((sum, c) => sum + c.commissionAmount, 0);
  const totalClicks = links.reduce((sum, l) => sum + l.clickCount, 0);

  // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- one of many
  // stacked sections on MyView's linear screen (docs/UI_UX_GUIDELINES.md §10),
  // matching Android's identical "Partner earnings" conversion (4230bba1).
  return (
    <div className="itunda-flat-section">
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Partner earnings</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Earn 3% on any purchase made through a product link you've shared.
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Links shared</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{links.length}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Total clicks</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{totalClicks}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>Total earned</span>
        <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{totalEarned.toLocaleString()} RWF</span>
      </div>
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

// Real "simplify ternary operators" fix (2026-08-19) -- see PayByCodeCard's own
// `payButtonLabel` doc comment for the full account (Toss's real Frontend Fundamentals
// guide). Same shape: a nested ternary crossing two booleans, moved to a named if-chain.
function blockButtonLabel(blocking: boolean, blocked: boolean): string {
  if (blocking && blocked) return 'Unblocking…';
  if (blocking) return 'Blocking…';
  if (blocked) return 'Unblock';
  return 'Block';
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
function buildJoinUrl(tab: Tab, param: string, code: string): string {
  const url = new URL(window.location.href);
  url.search = '';
  url.searchParams.set(TAB_QUERY_PARAM, tab);
  url.searchParams.set(param, code);
  return url.toString();
}

async function shareOrCopyLink(url: string, title: string, text: string): Promise<'shared' | 'copied' | 'failed'> {
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

function readAndClearUrlParam(key: string): string | null {
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

function NewChatCard({ onStarted }: { onStarted: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [phoneNumber, setPhoneNumber] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [contacts, setContacts] = useState<{ userId: string; name: string }[] | null>(null);

  useEffect(() => { fetchTalkContacts().then(setContacts).catch(() => setContacts([])); }, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const conversation = await startConversation(phoneNumber.trim());
      setPhoneNumber('');
      onStarted(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>New chat</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '14px' }}>
        Start from an Itunda contact, or enter their phone number.
      </p>
      {contacts && contacts.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '12px' }}>
          {contacts.map((contact) => <button key={contact.userId} type="button" className="itunda-btn itunda-btn-secondary" onClick={async () => { setSubmitting(true); try { const c = await startConversationWithUser(contact.userId); onStarted(c.id); } catch { setError('Could not start this chat.'); } finally { setSubmitting(false); } }} disabled={submitting}>{contact.name}</button>)}
        </div>
      )}
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="tel"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          placeholder="+250788123456"
          required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? 'Starting…' : 'Chat'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function NewGroupCard({ onCreated }: { onCreated: (groupId: string) => void }) {
  const { t } = useI18n();
  const [name, setName] = useState('');
  const [phoneNumbers, setPhoneNumbers] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numbers = phoneNumbers.split(',').map((n) => n.trim()).filter(Boolean);
    if (numbers.length === 0) {
      setError('Enter at least one phone number, separated by commas.');
      return;
    }
    setSubmitting(true);
    try {
      const group = await createGroup(name.trim(), numbers);
      setName('');
      setPhoneNumbers('');
      onCreated(group.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>New group</h3>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
        Name your group and add real members by phone number, separated by commas.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Group name"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <input
          type="text"
          value={phoneNumbers}
          onChange={(e) => setPhoneNumbers(e.target.value)}
          placeholder="+250788123456, +250788654321"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create group'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real KakaoTalk 오픈채팅 (Open Chat)-style group -- see backend
// GroupMessagingService.createOpenGroup's own doc comment for the full sourced
// account. Distinct from NewGroupCard above: no phone numbers needed to create one,
// and anyone with the real generated code can join, not just people the creator
// explicitly invited.
function OpenChatCard({ onCreated, onJoined }: { onCreated: (groupId: string) => void; onJoined: (groupId: string) => void }) {
  const { t } = useI18n();
  const [mode, setMode] = useState<'closed' | 'create' | 'join'>('closed');
  const [name, setName] = useState('');
  const [joinCode, setJoinCode] = useState('');
  const [created, setCreated] = useState<{ id: string; joinCode: string } | null>(null);
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // Real fix (2026-08-19): joining used to require typing the raw 6-character code by
  // hand -- the exact "asking user code, instead use qr code" anti-pattern. Reuses the
  // same itunda://... QR payload convention as payments (see QrScanCamera's own doc
  // comment); the code stays as a real fallback for whoever's sharing over voice/text.
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualJoinEntry, setManualJoinEntry] = useState(false);

  useEffect(() => {
    if (created) {
      QRCode.toDataURL(`itunda://join-chat?code=${created.joinCode}`, { width: 220, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
    } else {
      setQrDataUrl(null);
    }
  }, [created]);

  const submitJoinCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setError(null);
    setSubmitting(true);
    try {
      const group = await joinGroupByCode(trimmed);
      setJoinCode('');
      setMode('closed');
      onJoined(group.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleScanJoin = (raw: string) => void submitJoinCode(parseQrParam(raw, 'code'));

  // Real remote-invite fix (2026-08-19) -- see buildJoinUrl's own doc comment: a friend
  // who taps a shared itunda link (sent via itunda talk, SMS, anywhere) lands here with
  // ?joinChatCode=... already in the URL and should join immediately, no typing or
  // scanning at all.
  useEffect(() => {
    const incoming = readAndClearUrlParam('joinChatCode');
    if (incoming) void submitJoinCode(incoming);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount only
  }, []);

  const [shareStatus, setShareStatus] = useState<'idle' | 'shared' | 'copied' | 'failed'>('idle');
  const handleShare = async (code: string) => {
    const url = buildJoinUrl('MESSAGES', 'joinChatCode', code);
    const result = await shareOrCopyLink(url, 'Join my open chat on itunda', `Join my open chat on itunda — tap to join instantly.`);
    setShareStatus(result);
  };

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const group = await createOpenGroup(name.trim());
      setName('');
      setCreated({ id: group.id, joinCode: group.joinCode });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  const handleJoin = (e: React.FormEvent) => {
    e.preventDefault();
    void submitJoinCode(joinCode);
  };

  if (mode === 'closed') {
    return (
      <div style={{ display: 'flex', gap: '10px', marginBottom: '16px' }}>
        <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('create')}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><GlobeGlyph size={16} /> Start an open chat</span>
        </button>
        <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('join')}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><CameraGlyph size={16} /> Join an open chat</span>
        </button>
      </div>
    );
  }

  if (created) {
    return (
      <div className="itunda-flat-section" style={{ textAlign: 'center', display: 'flex', flexDirection: 'column', gap: '10px', alignItems: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Send friends a link — tapping it joins instantly, wherever they are</p>
        <button className="itunda-btn itunda-btn-primary" style={{ width: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }} onClick={() => handleShare(created.joinCode)}>
          <LinkGlyph size={16} /> Share invite link
        </button>
        {shareStatus === 'copied' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>Link copied</p>}
        {shareStatus === 'failed' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }}>Could not copy the link — try the code below.</p>}
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '8px' }}>Or, if they're standing right next to you:</p>
        {qrDataUrl && <img src={qrDataUrl} alt={`QR code to join ${created.joinCode}`} width={140} height={140} style={{ borderRadius: '12px' }} />}
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Or read them this code:</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, letterSpacing: '4px' }}>{created.joinCode}</p>
        <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => { const id = created.id; setCreated(null); setMode('closed'); onCreated(id); }}>
          Done
        </button>
      </div>
    );
  }

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {mode === 'create' ? (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Start an open chat</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Anyone with the code can join — no phone numbers needed.</p>
          <input
            type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Open chat name" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
              {submitting ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      ) : !manualJoinEntry ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Scan to join</h3>
          {!scanUnavailable && !submitting && <QrScanCamera onDetect={handleScanJoin} onUnavailable={() => setScanUnavailable(true)} />}
          {submitting && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Joining…</p>}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setManualJoinEntry(true)}>
              {scanUnavailable ? 'Enter code manually' : 'No camera? Enter code'}
            </button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleJoin} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Join by code</h3>
          <input
            type="text" value={joinCode} onChange={(e) => setJoinCode(e.target.value.toUpperCase())} placeholder="6-character code" required autoFocus
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', textAlign: 'center', letterSpacing: '2px' }}
          />
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setMode('closed')}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
              {submitting ? 'Joining…' : 'Join'}
            </button>
          </div>
        </form>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
    </div>
  );
}

// Real quick-react palette -- a small fixed set (matching most real chat apps' own
// "long-press to react" quick palette) rather than a full emoji picker, kept simple
// since this web client has no native emoji-keyboard integration to lean on.
const QUICK_REACTIONS = ['👍', '❤️', '😂', '😮', '😢'];

function chatMessageTime(sentAt: string) {
  const timestamp = new Date(sentAt);
  if (Number.isNaN(timestamp.getTime())) return '';
  return timestamp.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

// Real Kakao/Toss/iMessage-style collapsed-per-run timestamp convention
// (docs/DESIGN_REFERENCES.md Talk section, recommendation #8's "remaining polish
// gap": "each message shows its own timestamp, not grouped by consecutive-run"),
// ported from the same-day Android/iOS fix (HoodShared.kt's/TalkScreen.swift's
// shouldShowChatTimestamp). A message shows its timestamp only when it's the last
// in a consecutive run from the same sender within the same local minute. Compares
// full local date+minute, not chatMessageTime's clock-face string alone -- that
// would false-positive "same run" for two messages sent at the same clock time on
// different days, a real risk in a search-results list where adjacent entries
// aren't temporally adjacent in the real conversation.
function shouldShowChatTimestamp<T extends { senderId: string; sentAt: string }>(messages: T[], index: number) {
  if (index === messages.length - 1) return true;
  const current = messages[index];
  const next = messages[index + 1];
  if (current.senderId !== next.senderId) return true;
  const currentDate = new Date(current.sentAt);
  const nextDate = new Date(next.sentAt);
  if (Number.isNaN(currentDate.getTime()) || Number.isNaN(nextDate.getTime())) return true;
  const minuteKey = (d: Date) => `${d.getFullYear()}-${d.getMonth()}-${d.getDate()}-${d.getHours()}-${d.getMinutes()}`;
  return minuteKey(currentDate) !== minuteKey(nextDate);
}

// Real emoji reactions (2026-07-19) -- shared between 1:1 and group threads, which
// differ only in which toggle call they make. Tapping an existing reaction badge
// toggles the current user's own reaction for that emoji (the fast, one-tap path real
// chat apps use); the smile button opens the quick palette for a first reaction.
function MessageReactions({
  reactions, currentUserId, onToggle, isMine,
}: {
  reactions: ReactionGroup[]; currentUserId: string | undefined; onToggle: (emoji: string) => void; isMine: boolean;
}) {
  const [pickerOpen, setPickerOpen] = useState(false);
  // Real Toss/Kakao-sourced per-glyph hover animation (index.css's itdf-anim-*,
  // see ItundaFace.tsx's ReactionGlyph doc comment) -- tracks which single picker
  // glyph is currently hovered so only that one plays its animation.
  const [hoveredReactionEmoji, setHoveredReactionEmoji] = useState<string | null>(null);
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px', justifyContent: isMine ? 'flex-end' : 'flex-start' }}>
      <AnimatePresence initial={false}>
        {reactions.filter((r) => r.userIds.length > 0).map((r) => {
          const mine = !!currentUserId && r.userIds.includes(currentUserId);
          return (
            <motion.button
              key={r.emoji}
              layout
              initial={{ scale: 0, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0, opacity: 0 }}
              transition={{ type: 'spring', stiffness: 500, damping: 22 }}
              whileTap={{ scale: 0.88 }}
              onClick={() => onToggle(r.emoji)}
              style={{
                display: 'flex', alignItems: 'center', gap: '4px', padding: '2px 8px', borderRadius: '12px', fontSize: 'var(--itunda-type-scale-12-size)',
                border: mine ? '1px solid var(--itunda-indigo)' : '1px solid var(--itunda-grey-200)',
                backgroundColor: mine ? 'var(--itunda-indigo-light)' : 'var(--itunda-white)',
              }}
            >
              <ReactionGlyph emoji={r.emoji} size={16} />
              <span style={{ color: 'var(--itunda-grey-700)' }}>{r.userIds.length}</span>
            </motion.button>
          );
        })}
      </AnimatePresence>
      <div style={{ position: 'relative' }}>
        <motion.button
          whileTap={{ scale: 0.88 }}
          onClick={() => setPickerOpen((v) => !v)}
          aria-label="Add reaction"
          style={{ display: 'flex', padding: '6px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', color: 'var(--itunda-grey-500)' }}
        >
          <SmilePlus size={14} />
        </motion.button>
        <AnimatePresence>
          {pickerOpen && (
            <motion.div
              initial={{ opacity: 0, scale: 0.85, y: 6 }}
              animate={{ opacity: 1, scale: 1, y: 0 }}
              exit={{ opacity: 0, scale: 0.85, y: 6 }}
              transition={{ type: 'spring', stiffness: 420, damping: 28 }}
              style={{
                position: 'absolute', bottom: '32px', display: 'flex', gap: '6px', padding: '8px 10px',
                borderRadius: '14px', backgroundColor: 'var(--itunda-white)', boxShadow: '0 2px 8px rgba(0,0,0,0.15)', zIndex: 10,
                left: isMine ? undefined : 0, right: isMine ? 0 : undefined,
              }}
            >
              {QUICK_REACTIONS.map((emoji) => (
                <motion.button
                  key={emoji}
                  whileHover={{ scale: 1.18, y: -3 }}
                  whileTap={{ scale: 0.82 }}
                  transition={{ type: 'spring', stiffness: 400, damping: 15 }}
                  onHoverStart={() => setHoveredReactionEmoji(emoji)}
                  onHoverEnd={() => setHoveredReactionEmoji((current) => (current === emoji ? null : current))}
                  onClick={() => { onToggle(emoji); setPickerOpen(false); }}
                  style={{ display: 'flex', padding: '2px' }}
                >
                  <ReactionGlyph emoji={emoji} size={32} variant="3d" animated={hoveredReactionEmoji === emoji} />
                </motion.button>
              ))}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
    </div>
  );
}

// Real 당근-style price-offer bubble -- see PriceOfferService's own doc comment.
// Renders inline wherever a message carries a real offer, replacing the plain-text
// bubble with amount + status + real Accept/Reject/Counter actions (only shown to
// whichever participant did NOT propose the current pending amount). Prop type
// deliberately narrowed to just the fields this component actually reads (not the full
// `PriceOffer` shape) so it structurally accepts both Marketplace's `PriceOffer` and
// Real Estate's `PropertyPriceOffer` (2026-07-19) without duplicating this component --
// the two types have different field names for listing/buyer/seller (irrelevant here),
// but identical id/amount/status/proposedByUserId shapes.
interface OfferBubbleData {
  id: string;
  amount: number;
  status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'COUNTERED';
  proposedByUserId: string;
}

function OfferBubble({
  offer, isMine, currentUserId, onRespond,
}: {
  offer: OfferBubbleData; isMine: boolean; currentUserId: string | undefined; onRespond: (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) => void;
}) {
  const [countering, setCountering] = useState(false);
  const [counterAmount, setCounterAmount] = useState('');
  const canRespond = offer.status === 'PENDING' && currentUserId && currentUserId !== offer.proposedByUserId;
  const statusLabel: Record<OfferBubbleData['status'], string> = {
    PENDING: 'Pending', ACCEPTED: 'Accepted', REJECTED: 'Declined', COUNTERED: 'Countered',
  };

  return (
    <div
      style={{
        maxWidth: '75%', padding: '12px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><MoneyBagGlyph size={16} /> {offer.amount.toLocaleString()} RWF</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[offer.status]}</p>
      {canRespond && !countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'ACCEPT')}>
            Accept
          </button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'REJECT')}>
            Decline
          </button>
          <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }} onClick={() => setCountering(true)}>
            Counter
          </button>
        </div>
      )}
      {canRespond && countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <input
            type="number"
            value={counterAmount}
            onChange={(e) => setCounterAmount(e.target.value)}
            placeholder="Counter (RWF)"
            style={{ flex: 1, padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-300)', fontSize: 'var(--itunda-type-scale-12-size)' }}
          />
          <button
            className="itunda-btn itunda-btn-secondary"
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}
            disabled={!counterAmount || Number(counterAmount) <= 0}
            onClick={() => {
              onRespond(offer.id, 'COUNTER', Number(counterAmount));
              setCountering(false);
              setCounterAmount('');
            }}
          >
            Send
          </button>
        </div>
      )}
    </div>
  );
}

// Real KakaoTalk-style gift bubble -- renders inline wherever a message carries a real
// gift (see GiftService's own doc comment), with a real Claim button shown only to the
// recipient of a still-PENDING, not-yet-expired gift.
function GiftBubble({
  gift, isMine, currentUserId, onClaim,
}: {
  gift: Gift; isMine: boolean; currentUserId: string | undefined; onClaim: (giftId: string) => void;
}) {
  const canClaim = gift.status === 'PENDING' && currentUserId === gift.recipientId && new Date(gift.expiresAt).getTime() > Date.now();
  const statusLabel: Record<GiftStatus, string> = {
    PENDING: isMine ? 'Waiting to be opened' : 'Tap to open',
    CLAIMED: 'Opened',
    EXPIRED: 'Expired — refunded',
  };

  return (
    <div
      style={{
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-16-size)', display: 'flex', alignItems: 'center', gap: '6px' }}>
        <GiftGlyph theme={gift.theme} size={18} />
        {gift.theme ? GIFT_THEME_LABELS[gift.theme].replace(/^\S+\s*/, '') : ''} {gift.amount.toLocaleString()} RWF
      </p>
      {gift.note && <p style={{ fontStyle: 'italic', opacity: 0.9 }}>&ldquo;{gift.note}&rdquo;</p>}
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[gift.status]}</p>
      {canClaim && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px', alignSelf: 'flex-start' }}
          onClick={() => onClaim(gift.id)}
        >
          Open gift
        </button>
      )}
    </div>
  );
}

// Real KakaoTalk-style 기프티콘 gift voucher bubble (item 134) -- see
// lib/giftVouchers.ts's own doc comment. Redemption is merchant-side only
// (GiftVoucherService.redeemVoucher's own doc comment: a customer presents the
// voucher in person for the merchant to validate, never a self-serve recipient
// redeem), so this bubble is status-only -- no claim action, unlike GiftBubble.
// Real Kakao-sourced one-time expiry extension (item 141) -- either the purchaser or
// recipient may trigger it (GiftVoucherService.extendExpiry's own doc comment: "either
// real party to the transaction"), only within EXTENSION_WINDOW (30 days) of expiry,
// and only once per voucher (`extended`). Client-side date check here is a soft UX
// convenience only -- the backend's own real validation is authoritative.
const GIFT_VOUCHER_EXTENSION_WINDOW_MS = 30 * 24 * 60 * 60 * 1000;

function GiftVoucherBubble({
  voucher, isMine, onExtend,
}: {
  voucher: GiftVoucher; isMine: boolean; onExtend: (voucherId: string) => void;
}) {
  const [extending, setExtending] = useState(false);
  // Real gap found live (Toss-style error-handling audit, 2026-08-30): this had NO
  // catch block at all -- a real 409 GIFT_VOUCHER_ALREADY_EXTENDED (the other real
  // party to the transaction extended it first, a genuine race given either side can
  // trigger this per the backend's own doc comment) was an unhandled promise
  // rejection, reading to the customer as the page silently breaking.
  const [extendError, setExtendError] = useState<string | null>(null);
  const statusLabel: Record<GiftVoucherStatus, string> = {
    ACTIVE: 'Present this at the store to redeem',
    REDEEMED: 'Redeemed',
    EXPIRED: 'Expired',
  };
  const withinExtensionWindow = new Date(voucher.expiresAt).getTime() - Date.now() <= GIFT_VOUCHER_EXTENSION_WINDOW_MS;
  const canExtend = voucher.status === 'ACTIVE' && !voucher.extended && withinExtensionWindow;

  return (
    <div
      style={{
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
        backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
        color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-15-size)', display: 'flex', alignItems: 'center', gap: '6px' }}><VoucherTicket size={18} /> {voucher.productNameSnapshot ?? `${voucher.amount.toLocaleString()} RWF voucher`}</p>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', opacity: 0.8 }}>{statusLabel[voucher.status]}</p>
      {voucher.status === 'ACTIVE' && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', opacity: 0.7 }}>Expires {new Date(voucher.expiresAt).toLocaleDateString()}</p>
      )}
      {extendError && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: isMine ? 'var(--itunda-white)' : 'var(--itunda-red)', opacity: isMine ? 0.9 : 1 }} role="alert">{extendError}</p>
      )}
      {canExtend && (
        <button
          className="itunda-btn itunda-btn-secondary"
          style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px', alignSelf: 'flex-start' }}
          disabled={extending}
          onClick={async () => {
            setExtending(true);
            setExtendError(null);
            try {
              await extendGiftVoucherExpiry(voucher.id);
              onExtend(voucher.id);
            } catch (err) {
              if (err instanceof ApiError && err.code === 'GIFT_VOUCHER_ALREADY_EXTENDED') {
                // The other real party to the transaction already extended it --
                // resolve forward by refreshing to show the real, already-extended
                // expiry, matching this codebase's own established pattern for an
                // "already done" conflict that isn't really a failure.
                onExtend(voucher.id);
              } else {
                setExtendError(err instanceof ApiError ? err.message : 'Could not extend this voucher.');
              }
            } finally {
              setExtending(false);
            }
          }}
        >
          {extending ? '…' : 'Extend expiry'}
        </button>
      )}
    </div>
  );
}

// Real KakaoTalk Emoticon Store (item 133) -- a real sticker message renders as just
// the image, no chat-bubble background, matching real KakaoTalk's own emoticon
// rendering (a bubble would look wrong behind a sticker that already has its own
// transparent art). See lib/emoticons.ts's own doc comment.
function EmoticonBubble({ imageUrl }: { imageUrl: string | undefined }) {
  if (!imageUrl) {
    return <div style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', fontStyle: 'italic' }}>[emoticon]</div>;
  }
  return <img src={imageUrl} alt="emoticon" style={{ width: '96px', height: '96px', objectFit: 'contain' }} />;
}

// Real emoticon picker -- shown owned packs only (each tappable emoticon sends
// immediately); a real "Get more" link opens the full store to browse/purchase.
function EmoticonPickerPanel({
  onSend, onOpenStore,
}: {
  onSend: (emoticonId: string) => void;
  onOpenStore: () => void;
}) {
  const [ownedPacks, setOwnedPacks] = useState<OwnedEmoticonPack[] | null>(null);
  const [selectedPackId, setSelectedPackId] = useState<string | null>(null);
  const [packEmoticons, setPackEmoticons] = useState<Emoticon[] | null>(null);
  const [packTitles, setPackTitles] = useState<Record<string, string>>({});

  useEffect(() => {
    Promise.all([fetchOwnedEmoticonPacks(), fetchEmoticonPacks()])
      .then(([owned, allPacks]) => {
        setOwnedPacks(owned);
        setPackTitles(Object.fromEntries(allPacks.map((p) => [p.id, p.title])));
        if (owned.length > 0) setSelectedPackId(owned[0].packId);
      })
      .catch(() => setOwnedPacks([]));
  }, []);

  useEffect(() => {
    if (!selectedPackId) return;
    setPackEmoticons(null);
    fetchPackEmoticons(selectedPackId).then(setPackEmoticons).catch(() => setPackEmoticons([]));
  }, [selectedPackId]);

  return (
    <div style={{ padding: '10px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px' }}>
      {ownedPacks === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : ownedPacks.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>You don't own any emoticon packs yet.</p>
          <button type="button" className="itunda-btn itunda-btn-primary" onClick={onOpenStore} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            Browse Emoticon Store
          </button>
        </div>
      ) : (
        <>
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '8px' }}>
            {ownedPacks.map((op) => (
              <button
                key={op.packId}
                type="button"
                onClick={() => setSelectedPackId(op.packId)}
                className={selectedPackId === op.packId ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}
              >
                {packTitles[op.packId] ?? op.packId}
              </button>
            ))}
            <button type="button" onClick={onOpenStore} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 10px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}>
              Get more
            </button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
            {packEmoticons === null ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
            ) : (
              packEmoticons.map((e) => (
                <button
                  key={e.id}
                  type="button"
                  aria-label="Send sticker"
                  onClick={() => onSend(e.id)}
                  style={{ border: 'none', background: 'none', padding: '4px', cursor: 'pointer' }}
                >
                  <img src={e.imageUrl} alt="" style={{ width: '100%', aspectRatio: '1', objectFit: 'contain' }} />
                </button>
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}

// Real Emoticon Store -- browse every real active pack, buy one (once-off purchase,
// same "buy it once, own it" model Shop/Insurance already use), or gift one to a
// friend by phone number.
function EmoticonStoreModal({ onClose }: { onClose: () => void }) {
  const { t } = useI18n();
  const [packs, setPacks] = useState<EmoticonPack[] | null>(null);
  const [ownedPackIds, setOwnedPackIds] = useState<Set<string>>(new Set());
  const [busyPackId, setBusyPackId] = useState<string | null>(null);
  const [giftingPackId, setGiftingPackId] = useState<string | null>(null);
  const [giftPhone, setGiftPhone] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    Promise.all([fetchEmoticonPacks(), fetchOwnedEmoticonPacks()])
      .then(([allPacks, owned]) => {
        setPacks(allPacks);
        setOwnedPackIds(new Set(owned.map((o) => o.packId)));
      })
      .catch(() => setError('Could not load the Emoticon Store.'));
  };

  useEffect(load, []);

  const buy = async (packId: string) => {
    setBusyPackId(packId);
    setError(null);
    try {
      await purchaseEmoticonPack(packId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyPackId(null);
    }
  };

  const gift = async (packId: string) => {
    setBusyPackId(packId);
    setError(null);
    setMessage(null);
    try {
      await giftEmoticonPack(packId, giftPhone.trim());
      setMessage('Pack gifted!');
      setGiftingPackId(null);
      setGiftPhone('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyPackId(null);
    }
  };

  // Real fix (full-app audit, docs/UI_UX_GUIDELINES.md rule 1): was a classic
  // centered-card-on-dark-overlay modal, the same "old" pattern the rest of this
  // file has already moved away from in favor of FullScreenFlow (TransferFlow,
  // CreateGoalForm, Grow31/WeeklySavings, etc.). Same fix applied to
  // ForwardPickerModal below, which had the identical shape.
  return (
    <FullScreenFlow>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><ShoppingBagGlyph size={18} /> Emoticon Store</p>
          {/* Real touch-target-size fix (item 244, web accessibility sweep):
              no padding meant the clickable area was just the bare glyph,
              well under WCAG 2.5.8's 24x24 CSS-pixel AA minimum. */}
          <button type="button" aria-label="Close" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-16-size)', padding: '8px', minWidth: '24px', minHeight: '24px' }}>×</button>
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        {message && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-indigo)' }}>{message}</p>}
        {packs === null ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
        ) : (
          packs.map((pack) => {
            const owned = ownedPackIds.has(pack.id);
            return (
              <div key={pack.id} style={{ display: 'flex', flexDirection: 'column', gap: '6px', padding: '10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)' }}>
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                  <img src={pack.thumbnailUrl} alt="" style={{ width: '48px', height: '48px', objectFit: 'contain' }} />
                  <div style={{ flex: 1 }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{pack.title}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{pack.artistName} · {pack.price.toLocaleString()} RWF</p>
                  </div>
                  <button
                    type="button"
                    className={owned ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
                    disabled={owned || busyPackId === pack.id}
                    onClick={() => buy(pack.id)}
                    style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  >
                    {owned ? 'Owned' : busyPackId === pack.id ? '…' : 'Buy'}
                  </button>
                  <button
                    type="button"
                    className="itunda-btn itunda-btn-secondary"
                    disabled={busyPackId === pack.id}
                    onClick={() => setGiftingPackId(giftingPackId === pack.id ? null : pack.id)}
                    style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  >
                    Gift
                  </button>
                </div>
                {giftingPackId === pack.id && (
                  <div style={{ display: 'flex', gap: '6px' }}>
                    <input
                      type="tel"
                      value={giftPhone}
                      onChange={(e) => setGiftPhone(e.target.value)}
                      placeholder="Recipient phone number"
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                    />
                    <button
                      type="button"
                      className="itunda-btn itunda-btn-primary"
                      disabled={busyPackId === pack.id || !giftPhone.trim()}
                      onClick={() => gift(pack.id)}
                      style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    >
                      {busyPackId === pack.id ? '…' : 'Send gift'}
                    </button>
                  </div>
                )}
              </div>
            );
          })
        )}
      </div>
    </FullScreenFlow>
  );
}

// Real gift-voucher composer -- search for a real product to gift (same real Kakao
// gifticon UX of searching for what to send, e.g. "스타벅스 아메리카노", rather than
// browsing a merchant catalog first), pick one, confirm with the recipient's phone
// number. Product-only v1 (see lib/giftVouchers.ts's own doc comment) -- the flat-
// cash-amount-at-a-merchant path is a real, deliberately deferred follow-up.
function GiftVoucherComposerPanel({
  onSent, onCancel,
}: {
  onSent: () => void;
  onCancel: () => void;
}) {
  const { t } = useI18n();
  const [phone, setPhone] = useState('');
  const [query, setQuery] = useState('');
  const [results, setResults] = useState<ProductSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [selected, setSelected] = useState<ProductSearchResult | null>(null);
  const [sending, setSending] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const search = async (e: React.FormEvent) => {
    e.preventDefault();
    if (query.trim().length < 2) return;
    setSearching(true);
    setError(null);
    try {
      setResults(await searchProducts(query.trim()));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSearching(false);
    }
  };

  const send = async () => {
    if (!selected || !phone.trim()) return;
    setSending(true);
    setError(null);
    try {
      await purchaseGiftVoucher(phone.trim(), selected.merchantId, selected.id);
      onSent();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><VoucherTicket size={16} /> Send a gift voucher</p>
      <input
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        placeholder="Recipient phone number"
        style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      {selected ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', background: 'var(--itunda-grey-100)', borderRadius: '8px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{selected.name} · {selected.merchantName} · {selected.price.toLocaleString()} RWF</span>
          <button type="button" onClick={() => setSelected(null)} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>Change</button>
        </div>
      ) : (
        <>
          <form onSubmit={search} style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search a product to gift"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={searching || query.trim().length < 2} style={{ padding: '10px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {searching ? '…' : 'Search'}
            </button>
          </form>
          {results !== null && (
            results.length === 0 ? (
              <EmptyState message="Nothing matched that search — try a different word or browse by category." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '160px', overflowY: 'auto' }}>
                {results.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setSelected(p)}
                    style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', background: 'none', fontSize: 'var(--itunda-type-scale-13-size)', textAlign: 'left' }}
                  >
                    <span>{p.name} · {p.merchantName}</span>
                    <span>{p.price.toLocaleString()} RWF</span>
                  </button>
                ))}
              </div>
            )
          )}
        </>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          type="button"
          className="itunda-btn itunda-btn-primary"
          disabled={!selected || !phone.trim() || sending}
          onClick={send}
          style={{ flex: 1, padding: '10px' }}
        >
          {sending ? '…' : 'Send gift voucher'}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ padding: '10px 16px' }} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}

function ConversationThread({ conversation, onBack }: { conversation: ConversationSummary; onBack: () => void }) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<Message[] | null>(null);
  const [offersByMessageId, setOffersByMessageId] = useState<Record<string, OfferBubbleData>>({});
  const [giftsByMessageId, setGiftsByMessageId] = useState<Record<string, Gift>>({});
  const [vouchersByMessageId, setVouchersByMessageId] = useState<Record<string, GiftVoucher>>({});
  const [voucherComposerOpen, setVoucherComposerOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<Message | null>(null);
  const [pinnedMessage, setPinnedMessage] = useState<Message | null>(null);
  const [updatingPin, setUpdatingPin] = useState(false);
  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const [forwardingMessage, setForwardingMessage] = useState<Message | null>(null);
  // Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
  // recommendation #3's own account: Kakao's confirmed 2025 toolkit includes a real
  // reply-expands-into-its-own-sub-conversation view, closing the last gap in that
  // recommendation (Copy/Reply/Forward/Pin/Delete/@mention were all already real).
  const [threadRootMessage, setThreadRootMessage] = useState<Message | null>(null);
  const [sending, setSending] = useState(false);
  const [giftComposerOpen, setGiftComposerOpen] = useState(false);
  const [giftAmount, setGiftAmount] = useState('');
  const [giftNote, setGiftNote] = useState('');
  const [giftTheme, setGiftTheme] = useState<GiftTheme | ''>('');
  const [sendingGift, setSendingGift] = useState(false);
  // Real KakaoTalk Emoticon Store (item 133) -- see lib/emoticons.ts's own doc comment.
  const [emoticonPickerOpen, setEmoticonPickerOpen] = useState(false);
  const [emoticonStoreOpen, setEmoticonStoreOpen] = useState(false);
  const [emoticonImageById, setEmoticonImageById] = useState<Record<string, string>>({});
  // Real itundaface emoji picker (see icons/ItundaFaceEmoji.tsx's own doc comment)
  // -- distinct from the KakaoTalk-style sticker emoticonPicker above: this inserts
  // a real Unicode character into the message draft, not a separate sticker message.
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);
  // Real attach ("+") menu + photo send/gallery -- see GroupThread's own identical
  // doc comment.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
  // Real Links tab (itunda Talk redesign, 2026-08-28) -- see TalkLinksTab.tsx's own
  // doc comment, mirrors showMediaGallery's exact shape.
  const [showLinks, setShowLinks] = useState(false);
  // Real 1:1-chat split-bill (2026-08-09) -- see DirectSplitBillsView's own doc
  // comment; mirrors GroupThread's own identical showSplitBills toggle.
  const [showSplitBills, setShowSplitBills] = useState(false);
  // Real per-room settings (itunda Talk redesign, 2026-08-28) -- see
  // TalkRoomSettings.tsx's own doc comment.
  const [showRoomSettings, setShowRoomSettings] = useState(false);
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const [blocking, setBlocking] = useState(false);
  // Real unblock (item 193) -- the "Block" button had no way back: blockConversationParticipant's
  // own confirmation copy already promised "you can unblock them later from this
  // conversation," but unblockConversationParticipant was defined and never called
  // anywhere on any platform. No real "am I currently blocking them" query endpoint
  // exists (MessagingService.blockConversationParticipant/unblockConversationParticipant
  // are both idempotent fire-and-forget), so this is session-local state, same honest
  // scope the pre-existing block-only button already had.
  const [blocked, setBlocked] = useState(false);
  const [quiet, setQuiet] = useState(false);
  const [updatingQuiet, setUpdatingQuiet] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<Message[] | null>(null);
  const [searching, setSearching] = useState(false);
  // Real device binding step-up (2026-07-21) -- covers Gift send/claim below.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10): two different actions (send gift / claim gift) share this
  // one flag+prompt, so re-verifying couldn't just re-call "the" handler like
  // TransferFlow's own identical fix -- it has to retry whichever one was actually
  // pending. See TransferFlow's own doc comment for the base account of why retrying
  // at all matters: re-entering a password already proves who's asking, so making the
  // user redo the original action by hand afterward is friction, not security.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);
  const [otherOnline, setOtherOnline] = useState<boolean | null>(null);
  const [otherTyping, setOtherTyping] = useState(false);
  // Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14 -- Toss's own
  // "Birth of a chatbot heard through the ears" article, toss.tech/article/38743):
  // a message pushed live over the socket only ever updated the visual message list --
  // nothing here told a screen-reader user a new message had arrived at all, since
  // nothing on this screen was an aria-live region. Announced via a visually-hidden
  // live region below, only for messages actually pushed from the OTHER participant
  // (never the current user's own sent message, which they already know they typed).
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const lastTypingSentAt = useRef(0);

  useEffect(() => {
    fetchPresence([conversation.otherUserId]).then((p) => setOtherOnline(p[conversation.otherUserId] ?? null)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.otherUserId]);

  useEffect(() => {
    fetchConversationQuiet(conversation.conversationId).then(setQuiet).catch(() => {});
  }, [conversation.conversationId]);

  useEffect(() => {
    fetchEmoticonImageMap().then(setEmoticonImageById).catch(() => {});
  }, []);

  const loadPin = () => fetchPinnedConversationMessage(conversation.conversationId).then(setPinnedMessage).catch(() => {});

  useEffect(() => { loadPin(); }, [conversation.conversationId]);

  // Real-fetches both Marketplace and Real Estate offer history for this conversation --
  // a given real conversation only ever carries one type in practice (a listing/property
  // negotiation thread), but fetching both is cheap and correct rather than guessing
  // which one applies; each failure is independently non-critical.
  const loadOffers = () => {
    Promise.all([
      fetchOffersForConversation(conversation.conversationId).catch(() => [] as PriceOffer[]),
      fetchPropertyOffersForConversation(conversation.conversationId).catch(() => [] as PropertyPriceOffer[]),
    ]).then(([marketplaceOffers, propertyOffers]) => {
      setOffersByMessageId(
        Object.fromEntries([...marketplaceOffers, ...propertyOffers].map((o) => [o.messageId, o])),
      );
    });
  };

  const loadGifts = () => {
    fetchGiftsForConversation(conversation.conversationId)
      .then((gifts) => setGiftsByMessageId(Object.fromEntries(gifts.map((g) => [g.messageId, g]))))
      .catch(() => {});
  };

  const loadVouchers = () => {
    fetchGiftVouchersForConversation(conversation.conversationId)
      .then((vouchers) => setVouchersByMessageId(Object.fromEntries(vouchers.map((v) => [v.messageId, v]))))
      .catch(() => {});
  };

  const load = () => {
    fetchMessages(conversation.conversationId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    loadOffers();
    loadGifts();
    loadVouchers();
  };

  const handleBlock = async () => {
    if (!window.confirm(`Block ${conversation.otherUserName}? They will no longer be able to message you.`)) return;
    setBlocking(true);
    try {
      await blockConversationParticipant(conversation.conversationId);
      setBlocked(true);
      setError(`You blocked ${conversation.otherUserName}. You can unblock them later from this conversation.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setBlocking(false); }
  };

  const handleUnblock = async () => {
    setBlocking(true);
    try {
      await unblockConversationParticipant(conversation.conversationId);
      setBlocked(false);
      setError(`You unblocked ${conversation.otherUserName}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setBlocking(false); }
  };

  const handleQuiet = async () => {
    setUpdatingQuiet(true);
    try {
      setQuiet(await setConversationQuiet(conversation.conversationId, !quiet));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingQuiet(false); }
  };

  const handlePin = async (message: Message) => {
    setUpdatingPin(true);
    try {
      await pinConversationMessage(conversation.conversationId, message.id);
      setPinnedMessage(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinConversationMessage(conversation.conversationId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleReport = async (messageId: string) => {
    const reason = window.prompt('Why are you reporting this message? (3–180 characters)');
    if (!reason) return;
    try {
      await reportChatMessage(messageId, reason);
      setError('Thanks. Your report was sent for review.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try {
      await deleteMessage(conversation.conversationId, messageId);
      setMessages((prev) => prev?.map((m) => m.id === messageId ? { ...m, body: 'This message was deleted', deletedAt: new Date().toISOString(), reactions: [] } : m) ?? prev);
    } catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleCopy = (body: string) => {
    navigator.clipboard?.writeText(body).catch(() => {});
  };

  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim().length < 2) return;
    setSearching(true); setError(null);
    try { setSearchResults(await searchConversationMessages(conversation.conversationId, searchQuery.trim())); }
    catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
    finally { setSearching(false); }
  };

  const handleSendGift = async (e?: React.FormEvent) => {
    e?.preventDefault();
    const amount = Number(giftAmount);
    if (!amount || amount <= 0) return;
    setSendingGift(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await sendGiftInConversation(conversation.conversationId, amount, giftNote, giftTheme || null);
      setGiftAmount('');
      setGiftNote('');
      setGiftTheme('');
      setGiftComposerOpen(false);
      load();
    } catch (err) {
      // Real device binding step-up (2026-07-21) -- Gift send was a real gap:
      // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
      // showed only a generic error, same fix already applied to Transfer/Savings.
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = () => handleSendGift();
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSendingGift(false);
    }
  };

  const handleClaimGift = async (giftId: string) => {
    setNeedsDeviceVerification(false);
    try {
      await claimGift(giftId);
      loadGifts();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        pendingDeviceRetryRef.current = () => handleClaimGift(giftId);
        setNeedsDeviceVerification(true);
      } else if (err instanceof ApiError && err.code === 'GIFT_ALREADY_RESOLVED') {
        // Real gap found live (Toss-style error-handling audit, 2026-08-30): a
        // double-tap or an already-opened-on-another-device gift isn't really a
        // failure -- resolve forward by refreshing to show the real, already-opened
        // gift instead of a generic error.
        loadGifts();
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    }
  };

  const handleSendEmoticon = async (emoticonId: string) => {
    setError(null);
    try {
      await sendEmoticon(conversation.conversationId, emoticonId);
      setEmoticonPickerOpen(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real photo message -- ports Android TalkScreen.kt's own identical addition
  // (2026-08-04) to bank-mfe.
  const handleSendPhoto = async (file: File | undefined) => {
    if (!file) return;
    setShowAttachMenu(false);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      await sendMessage(conversation.conversationId, '', replyingTo?.id, url);
      setReplyingTo(null);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingPhoto(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
    }
  };

  const handleRespondToOffer = async (offerId: string, action: 'ACCEPT' | 'REJECT' | 'COUNTER', counterAmount?: number) => {
    try {
      // Real offer ids are stably prefixed by their real owning service
      // ("price_offer_"/"property_offer_"), a reliable dispatch key -- avoids needing
      // the thread to already know which listing type this conversation is about.
      if (offerId.startsWith('property_offer_')) {
        await respondToPropertyOffer(offerId, action, counterAmount);
      } else {
        await respondToOffer(offerId, action, counterAmount);
      }
      loadOffers();
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  useEffect(() => {
    load();
    // Real 4s poll as an always-correct fallback (kept even now that a live socket
    // exists below -- if the socket never connects, silently errors, or the server
    // restarts mid-conversation, this alone still delivers messages correctly, just
    // slower). See connectMessagingSocket's own doc comment for why it's designed as
    // a latency improvement layered on top of this, not a replacement for it.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId]);

  useEffect(() => {
    // Real WebSocket live delivery (2026-07-18) -- appends a pushed message straight
    // into state the moment it arrives, rather than waiting for the next poll tick.
    // De-duped by id since the next 4s poll will also fetch the same message.
    const socket = connectMessagingSocket((payload) => {
      if (payload.type === 'presence') {
        if (payload.userId === conversation.otherUserId) setOtherOnline(payload.online);
        return;
      }
      if (payload.type === 'typing') {
        if (payload.conversationId !== conversation.conversationId || payload.userId !== conversation.otherUserId) return;
        setOtherTyping(true);
        if (typingClearTimer.current) clearTimeout(typingClearTimer.current);
        // Real, client-side "stopped typing" inference (2026-07-19) -- there's no
        // explicit "stopped typing" event, same convention every real chat app uses:
        // clear the indicator if no new typing ping arrives within a few seconds.
        typingClearTimer.current = setTimeout(() => setOtherTyping(false), 3000);
        return;
      }
      if (payload.type === 'reaction') {
        if (payload.conversationId !== conversation.conversationId) return;
        setMessages((prev) => prev?.map((m) => (m.id === payload.messageId ? { ...m, reactions: payload.reactions } : m)) ?? prev);
        return;
      }
      if (payload.type !== 'message' || payload.conversationId !== conversation.conversationId) return;
      setOtherTyping(false);
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
      if (payload.message.senderId !== currentUser?.id) {
        setLiveAnnouncement(`New message from ${conversation.otherUserName}: ${payload.message.body || 'sent an attachment'}`);
      }
      // A pushed message might be a real offer/counter/accept/reject -- refresh the
      // offer history so it renders as an offer bubble immediately rather than waiting
      // for the next 4s poll.
      loadOffers();
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
      if (typingClearTimer.current) clearTimeout(typingClearTimer.current);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversation.conversationId, conversation.otherUserId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    setError(null);
    try {
      const sent = await sendMessage(conversation.conversationId, body, replyingTo?.id);
      setMessages((prev) => [...(prev ?? []), sent]);
      setDraft('');
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSending(false);
    }
  };

  const handleToggleReaction = async (messageId: string, emoji: string) => {
    try {
      const reactions = await toggleReaction(messageId, emoji);
      setMessages((prev) => prev?.map((m) => (m.id === messageId ? { ...m, reactions } : m)) ?? prev);
    } catch {
      // Best-effort -- a failed reaction toggle just leaves the badge as it was, never
      // blocks the thread.
    }
  };

  if (showSplitBills) {
    return (
      <DirectSplitBillsView
        otherUserId={conversation.otherUserId}
        otherUserName={conversation.otherUserName}
        currentUserId={currentUser?.id ?? null}
        onBack={() => setShowSplitBills(false)}
      />
    );
  }
  if (showRoomSettings) {
    return (
      <TalkRoomSettings
        conversationId={conversation.conversationId}
        otherUserName={conversation.otherUserName}
        messages={messages ?? []}
        onBack={() => setShowRoomSettings(false)}
      />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversations">
          <IconBack size={20} />
        </button>
        <div>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{conversation.otherUserName}</h3>
          {otherOnline !== null && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: otherOnline ? 'var(--itunda-green)' : 'var(--itunda-grey-500)' }}>
              {otherOnline ? 'Online' : 'Offline'}
            </p>
          )}
        </div>
        <button type="button" onClick={() => setShowMediaGallery(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', marginLeft: 'auto' }} aria-label="Shared photos">
          <ImageIcon size={20} />
        </button>
        <button type="button" onClick={() => setShowLinks(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Shared links">
          <LinkIcon size={20} />
        </button>
        <button type="button" onClick={() => setShowSplitBills(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Split a bill">
          <Receipt size={20} />
        </button>
        <button type="button" onClick={() => setShowRoomSettings(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)' }} aria-label="Room settings">
          <Settings size={20} />
        </button>
        <button
          type="button"
          className="itunda-btn itunda-btn-secondary"
          onClick={blocked ? handleUnblock : handleBlock}
          disabled={blocking}
          style={{ padding: '8px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}
        >
          {blockButtonLabel(blocking, blocked)}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={handleQuiet} disabled={updatingQuiet} style={{ padding: '8px 10px', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          {updatingQuiet ? '…' : quiet ? 'Resume alerts' : 'Quiet room'}
        </button>
      </div>

      {showMediaGallery && (
        <MediaGalleryModal
          imageUrls={(messages ?? []).map((m) => m.imageUrl).filter((u): u is string => !!u).reverse()}
          onClose={() => setShowMediaGallery(false)}
        />
      )}
      {showLinks && (
        <TalkLinksModal links={extractLinks((messages ?? []).map((m) => m.body))} onClose={() => setShowLinks(false)} />
      )}

      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
        <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Search this conversation" minLength={2} style={{ flex: 1, padding: '9px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)' }} />
        <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={searching || searchQuery.trim().length < 2}>{searching ? '…' : 'Search'}</button>
        {searchResults !== null && <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>Clear</button>}
      </form>
      {searchResults !== null && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '6px' }}>{searchResults.length} matching message{searchResults.length === 1 ? '' : 's'}</p>}

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--itunda-grey-100)', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          <span aria-hidden="true" style={{ display: 'inline-flex' }}><PinGlyph size={14} /></span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-600)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px', backgroundColor: roomThemeColor(conversation.conversationId), borderRadius: 'var(--itunda-radius-md)' }}>
        {messages === null && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {(searchResults ?? messages)?.map((m, index, list) => {
          const isMine = m.senderId === currentUser?.id;
          const offer = offersByMessageId[m.id];
          const gift = giftsByMessageId[m.id];
          const voucher = vouchersByMessageId[m.id];
          // Never collapsed when showing search hits -- adjacent results aren't
          // temporally adjacent in the real conversation, so each needs its own
          // explicit timestamp.
          const showTimestamp = searchResults != null || shouldShowChatTimestamp(list, index);
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {/* Real message forwarding (2026-07-25) -- a genuine provenance label,
                  only ever set on a message actually created via the forward
                  endpoint, see lib/messaging.ts's own doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
              )}
              {gift ? (
                <GiftBubble gift={gift} isMine={isMine} currentUserId={currentUser?.id} onClaim={handleClaimGift} />
              ) : voucher ? (
                <GiftVoucherBubble voucher={voucher} isMine={isMine} onExtend={() => loadVouchers()} />
              ) : offer ? (
                <OfferBubble offer={offer} isMine={isMine} currentUserId={currentUser?.id} onRespond={handleRespondToOffer} />
              ) : m.emoticonId ? (
                <EmoticonBubble imageUrl={emoticonImageById[m.emoticonId]} />
              ) : m.imageUrl ? (
                // Real photo message -- ports Android TalkScreen.kt's own identical
                // addition (2026-08-04) to bank-mfe.
                <img src={m.imageUrl} alt="Shared photo" style={{ maxWidth: '220px', borderRadius: '16px' }} />
              ) : (
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '10px 14px',
                    borderRadius: '16px',
                    fontSize: 'var(--itunda-type-scale-14-size)',
                    backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                    color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                  }}
                >
                  {renderTextWithEmoji(m.body)}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              {(showTimestamp || (isMine && !m.readAt)) && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                  {[isMine && !m.readAt ? '1' : null, showTimestamp ? chatMessageTime(m.sentAt) : null].filter(Boolean).join(' · ')}
                </span>
              )}
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Reply</button>
              <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Copy</button>
              {!m.deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Forward</button>}
              {isMine && !m.deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              {!isMine && (
                <button type="button" onClick={() => handleReport(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>
                  Report message
                </button>
              )}
              {/* Real Thread support (2026-08-05) -- a message with at least one direct
                  reply gets a real "N replies" affordance opening its own sub-conversation
                  view, matching Kakao's confirmed real reply-thread pattern. */}
              {!!m.replyCount && (
                <button
                  type="button"
                  onClick={() => setThreadRootMessage(m)}
                  style={{ border: 'none', background: 'none', color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, padding: '4px 0' }}
                >
                  {m.replyCount} {m.replyCount === 1 ? 'reply' : 'replies'} →
                </button>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      {forwardingMessage && <ForwardPickerModal onForward={handleForward} onClose={() => setForwardingMessage(null)} />}
      {threadRootMessage && (
        <ThreadModal
          rootMessage={threadRootMessage}
          currentUserId={currentUser?.id}
          fetchThreadMessages={() => fetchThread(conversation.conversationId, threadRootMessage.id)}
          onSend={(body) => sendMessage(conversation.conversationId, body, threadRootMessage.id)}
          onClose={() => { setThreadRootMessage(null); load(); }}
        />
      )}

      {/* Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14) -- this
          state change was visual-only before; a screen-reader user got no signal the
          other participant started typing. */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">{liveAnnouncement}</div>
      {otherTyping && (
        <p aria-live="polite" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {conversation.otherUserName} is typing…
        </p>
      )}

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '8px' }}>
          <DeviceStepUpPrompt
            onVerified={() => { const retry = pendingDeviceRetryRef.current; pendingDeviceRetryRef.current = null; retry?.(); }}
            onCancel={() => { pendingDeviceRetryRef.current = null; setNeedsDeviceVerification(false); }}
          />
        </div>
      ) : (
        error && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>
        )
      )}

      {giftComposerOpen && (
        <form
          onSubmit={handleSendGift}
          style={{
            display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px',
            borderRadius: '12px', border: '1px solid var(--itunda-grey-200)', marginBottom: '10px',
          }}
        >
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}><GiftGlyph theme={giftTheme || null} size={16} /> Send a gift</p>
          <input
            type="number"
            value={giftAmount}
            onChange={(e) => setGiftAmount(e.target.value)}
            placeholder="Amount (RWF)"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text"
            value={giftNote}
            onChange={(e) => setGiftNote(e.target.value)}
            placeholder="Add a note (optional)"
            maxLength={200}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <select
            value={giftTheme}
            onChange={(e) => setGiftTheme(e.target.value as GiftTheme | '')}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          >
            <option value="">No theme (plain gift)</option>
            {(Object.keys(GIFT_THEME_LABELS) as GiftTheme[]).map((t) => (
              <option key={t} value={t}>{GIFT_THEME_LABELS[t]}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="submit"
              className="itunda-btn itunda-btn-primary"
              disabled={sendingGift || !giftAmount || Number(giftAmount) <= 0}
              style={{ flex: 1, padding: '10px' }}
            >
              Send gift
            </button>
            <button
              type="button"
              className="itunda-btn itunda-btn-secondary"
              style={{ padding: '10px 16px' }}
              onClick={() => setGiftComposerOpen(false)}
            >
              Cancel
            </button>
          </div>
        </form>
      )}

      {emoticonPickerOpen && (
        <EmoticonPickerPanel onSend={handleSendEmoticon} onOpenStore={() => setEmoticonStoreOpen(true)} />
      )}
      {emoticonStoreOpen && <EmoticonStoreModal onClose={() => setEmoticonStoreOpen(false)} />}
      {voucherComposerOpen && (
        <GiftVoucherComposerPanel
          onSent={() => { setVoucherComposerOpen(false); load(); }}
          onCancel={() => setVoucherComposerOpen(false)}
        />
      )}

      {replyingTo && <div style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', padding: '8px', borderLeft: '3px solid var(--itunda-indigo)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" aria-label="Cancel reply" onClick={() => setReplyingTo(null)}>×</button></div>}
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {emojiPickerOpen && <EmojiPicker onPick={(emoji) => setDraft((d) => d + emoji)} />}
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            consolidates what used to be 3 separate always-visible icons
            (gift/emoticon/gift-voucher), plus real photo send, matching Kakao's own
            real "+"-opens-a-menu pattern. */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--itunda-white)', border: '1px solid var(--itunda-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}><CameraGlyph size={16} /> Photo</span>
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmojiPickerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <SmileySlight size={16} /> Emoji
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              😊 Emoticon
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setGiftComposerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <GiftGlyph theme={null} size={16} /> Gift
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setVoucherComposerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <VoucherTicket size={16} /> Gift voucher
            </button>
          </div>
        )}
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            // Real typing indicator send (2026-07-19), client-throttled to match the
            // server's own 1-per-2s rate limit so every keystroke isn't a wasted send.
            const now = Date.now();
            if (now - lastTypingSentAt.current > 2000) {
              lastTypingSentAt.current = now;
              socketRef.current?.sendTyping({ conversationId: conversation.conversationId });
            }
          }}
          placeholder="Message"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" aria-label="Send message" className="itunda-btn itunda-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <IconSend size={16} />
        </button>
      </form>
    </div>
  );
}

// Real @mention composer UI -- ports Android TalkScreen.kt's own identical addition
// (2026-08-04) to bank-mfe. GroupMessagingService.parseMentions (backend) already
// resolves `@FirstName` tokens against real group members purely from the message body
// text -- no separate mentionedUserIds field on the send request, so this composer
// only needs to insert the right text, not call any new endpoint. v1 scope matches the
// backend's own honest limitation (first-name collisions resolve to whichever member
// matches first): only suggests/inserts a plain `@FirstName` token, not a richer
// inline chip.
function activeMentionQuery(draft: string): string | null {
  const at = draft.lastIndexOf('@');
  if (at === -1) return null;
  const tail = draft.slice(at + 1);
  if (tail.includes(' ') || tail.includes('\n')) return null;
  return tail;
}

function applyMention(draft: string, memberName: string): string {
  const at = draft.lastIndexOf('@');
  if (at === -1) return draft;
  const firstName = memberName.trim().split(' ')[0];
  return draft.slice(0, at) + `@${firstName} `;
}

function MentionSuggestions({ draft, members, currentUserId, onPick }: {
  draft: string; members: GroupMember[]; currentUserId: string | undefined; onPick: (name: string) => void;
}) {
  const query = activeMentionQuery(draft);
  if (query === null) return null;
  const matches = members.filter((m) => m.userId !== currentUserId && m.name.split(' ')[0].toLowerCase().startsWith(query.toLowerCase()));
  if (matches.length === 0) return null;
  return (
    <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '6px' }}>
      {matches.map((m) => {
        const firstName = m.name.split(' ')[0];
        return (
          <button
            key={m.userId}
            type="button"
            onClick={() => onPick(m.name)}
            style={{
              whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
            }}
          >
            @{firstName}
          </button>
        );
      })}
    </div>
  );
}

function GroupThread({ group, onBack }: { group: GroupSummary; onBack: () => void }) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<GroupMessage[] | null>(null);
  const [members, setMembers] = useState<GroupMember[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [replyingTo, setReplyingTo] = useState<GroupMessage | null>(null);
  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const [forwardingMessage, setForwardingMessage] = useState<GroupMessage | null>(null);
  // Real Thread support (2026-08-05) -- see ConversationThread's own identical state.
  const [threadRootMessage, setThreadRootMessage] = useState<GroupMessage | null>(null);
  // Real group-chat pin (2026-07-26) -- see GroupMessagingService.setPinnedMessage's
  // own doc comment; mirrors ConversationThread's own identical 1:1 state.
  const [pinnedMessage, setPinnedMessage] = useState<GroupMessage | null>(null);
  const [updatingPin, setUpdatingPin] = useState(false);
  const [sending, setSending] = useState(false);
  const [typingUserIds, setTypingUserIds] = useState<Record<string, boolean>>({});
  // Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14), same as
  // ConversationThread's own identical addition above.
  const [liveAnnouncement, setLiveAnnouncement] = useState('');
  // Real split-bill/manage-members (found 2026-07-22 fully built on the backend with
  // zero UI anywhere) -- toggles a sibling view over this same thread.
  const [showSplitBills, setShowSplitBills] = useState(false);
  const [showManageMembers, setShowManageMembers] = useState(false);
  // Real group 공지/투표 (announcement/poll) (itunda Talk redesign, 2026-08-28) --
  // toggles a sibling view over this same thread, same pattern as split-bill above.
  const [showAnnouncementPoll, setShowAnnouncementPoll] = useState(false);
  // Real attach ("+") menu + photo send/gallery -- ports Android TalkScreen.kt's own
  // identical addition (2026-08-04) to bank-mfe. Reuses lib/upload.ts's own uploadFile,
  // already real since 2026-08-01; this is just the Talk-composer wiring.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
  // Real Links tab (itunda Talk redesign, 2026-08-28) -- see TalkLinksTab.tsx's own
  // doc comment, mirrors showMediaGallery's exact shape.
  const [showLinks, setShowLinks] = useState(false);
  const photoInputRef = useRef<HTMLInputElement | null>(null);
  const currentUser = getStoredUser();
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const socketRef = useRef<MessagingSocketHandle | null>(null);
  const typingClearTimers = useRef<Record<string, ReturnType<typeof setTimeout>>>({});
  const lastTypingSentAt = useRef(0);

  const handleSendPhoto = async (file: File | undefined) => {
    if (!file) return;
    setShowAttachMenu(false);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      const sent = await sendGroupMessage(group.groupId, '', replyingTo?.id, url);
      setMessages((prev) => [...(prev ?? []), sent]);
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setUploadingPhoto(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
    }
  };

  const load = () =>
    fetchGroupMessages(group.groupId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  const loadPin = () => fetchPinnedGroupMessage(group.groupId).then(setPinnedMessage).catch(() => {});

  useEffect(() => {
    load();
    loadPin();
    // Real 4s poll as an always-correct fallback, same reasoning as ConversationThread's
    // own identical poll -- kept even with the live socket below.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

  useEffect(() => {
    // Real member list with real resolved display names (2026-07-18), fetched once per
    // thread open -- closes the honest, named limitation this UI carried since group
    // chat first shipped (a truncated sender id instead of a real name).
    fetchGroupMembers(group.groupId).then(setMembers).catch(() => {
      // Real, non-critical -- a failed member-list fetch shouldn't block the thread;
      // bubbles just fall back to a truncated sender id below.
    });
  }, [group.groupId]);

  const nameForSender = (senderId: string) => members.find((m) => m.userId === senderId)?.name ?? senderId.slice(0, 12);

  useEffect(() => {
    // Real WebSocket live delivery for group chat (2026-07-18) -- same real push
    // GroupMessagingService.sendMessage fans out to every other real member.
    const socket = connectMessagingSocket((payload) => {
      if (payload.type === 'typing') {
        if (payload.groupConversationId !== group.groupId) return;
        const userId = payload.userId;
        setTypingUserIds((prev) => ({ ...prev, [userId]: true }));
        if (typingClearTimers.current[userId]) clearTimeout(typingClearTimers.current[userId]);
        typingClearTimers.current[userId] = setTimeout(() => {
          setTypingUserIds((prev) => {
            const next = { ...prev };
            delete next[userId];
            return next;
          });
        }, 3000);
        return;
      }
      if (payload.type === 'reaction') {
        if (payload.groupConversationId !== group.groupId) return;
        setMessages((prev) => prev?.map((m) => (m.id === payload.messageId ? { ...m, reactions: payload.reactions } : m)) ?? prev);
        return;
      }
      if (payload.type === 'group_read_receipt') {
        // Real live read-receipt countdown (2026-07-26) -- see
        // RealtimeMessagePublisher.publishGroupReadReceiptChange's own doc comment. The
        // server is the source of truth for each message's exact remaining unreadCount;
        // simplest correct client response is a real refetch, not a local guess.
        if (payload.groupConversationId !== group.groupId) return;
        load();
        return;
      }
      if (payload.type !== 'group_message' || payload.groupConversationId !== group.groupId) return;
      setTypingUserIds((prev) => {
        if (!(payload.message.senderId in prev)) return prev;
        const next = { ...prev };
        delete next[payload.message.senderId];
        return next;
      });
      setMessages((prev) => {
        if (!prev) return prev;
        if (prev.some((m) => m.id === payload.message.id)) return prev;
        return [...prev, payload.message];
      });
      if (payload.message.senderId !== currentUser?.id) {
        setLiveAnnouncement(`New message from ${nameForSender(payload.message.senderId)}: ${payload.message.body || 'sent an attachment'}`);
      }
    });
    socketRef.current = socket;
    return () => {
      socket.close();
      socketRef.current = null;
      Object.values(typingClearTimers.current).forEach(clearTimeout);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [group.groupId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    setError(null);
    try {
      const sent = await sendGroupMessage(group.groupId, body, replyingTo?.id);
      setMessages((prev) => [...(prev ?? []), sent]);
      setDraft('');
      setReplyingTo(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSending(false);
    }
  };

  const handleToggleReaction = async (groupMessageId: string, emoji: string) => {
    try {
      const reactions = await toggleGroupReaction(groupMessageId, emoji);
      setMessages((prev) => prev?.map((m) => (m.id === groupMessageId ? { ...m, reactions } : m)) ?? prev);
    } catch {
      // Best-effort -- a failed reaction toggle just leaves the badge as it was, never
      // blocks the thread.
    }
  };

  const handlePin = async (message: GroupMessage) => {
    setUpdatingPin(true);
    try {
      await pinGroupMessage(group.groupId, message.id);
      setPinnedMessage(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinGroupMessage(group.groupId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setUpdatingPin(false); }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try { await deleteGroupMessage(group.groupId, messageId); load(); }
    catch (err) { setError(err instanceof ApiError ? err.message : t('common.actionError')); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardGroupMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleCopy = (body: string) => {
    navigator.clipboard?.writeText(body).catch(() => {});
  };

  // Real KakaoTalk Emoticon Store, group-send side (item 133) -- see
  // lib/emoticons.ts's sendGroupEmoticon doc comment. 1:1 chat has had this since the
  // Emoticon Store shipped; group chat never got a client for the identical, already-
  // real backend endpoint. Found 2026-07-29 via the defined-but-uncalled-method sweep.
  const [emoticonPickerOpen, setEmoticonPickerOpen] = useState(false);
  const [emoticonStoreOpen, setEmoticonStoreOpen] = useState(false);
  const [emoticonImageById, setEmoticonImageById] = useState<Record<string, string>>({});
  // Real itundaface emoji picker -- see the 1:1-thread composer's identical
  // addition and icons/ItundaFaceEmoji.tsx's own doc comment.
  const [emojiPickerOpen, setEmojiPickerOpen] = useState(false);

  useEffect(() => {
    fetchEmoticonImageMap().then(setEmoticonImageById).catch(() => {});
  }, []);

  const handleSendGroupEmoticon = async (emoticonId: string) => {
    setError(null);
    try {
      await sendGroupEmoticon(group.groupId, emoticonId);
      setEmoticonPickerOpen(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  if (showSplitBills) {
    return (
      <GroupSplitBillsView
        groupConversationId={group.groupId}
        members={members}
        currentUserId={currentUser?.id ?? null}
        onBack={() => setShowSplitBills(false)}
      />
    );
  }
  if (showManageMembers) {
    return (
      <GroupManageMembersView
        group={group}
        members={members}
        currentUserId={currentUser?.id ?? null}
        onMembersChanged={() => fetchGroupMembers(group.groupId).then(setMembers).catch(() => {})}
        onLeft={() => { setShowManageMembers(false); onBack(); }}
        onBack={() => setShowManageMembers(false)}
      />
    );
  }
  if (showAnnouncementPoll) {
    return <TalkGroupAnnouncementPoll groupId={group.groupId} onBack={() => setShowAnnouncementPoll(false)} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', marginBottom: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversations">
            <IconBack size={20} />
          </button>
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{group.name}</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{group.memberCount} members</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '6px' }}>
          <button type="button" onClick={() => setShowMediaGallery(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Shared photos">
            <ImageIcon size={20} />
          </button>
          <button type="button" onClick={() => setShowLinks(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Shared links">
            <LinkIcon size={20} />
          </button>
          <button type="button" onClick={() => setShowManageMembers(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Manage members">
            <Users size={20} />
          </button>
          <button type="button" onClick={() => setShowAnnouncementPoll(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Announcement and polls">
            <Megaphone size={20} />
          </button>
          <button type="button" onClick={() => setShowSplitBills(true)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Split a bill">
            <Receipt size={20} />
          </button>
        </div>
      </div>

      {showMediaGallery && (
        <MediaGalleryModal
          imageUrls={(messages ?? []).map((m) => m.imageUrl).filter((u): u is string => !!u).reverse()}
          onClose={() => setShowMediaGallery(false)}
        />
      )}
      {showLinks && (
        <TalkLinksModal links={extractLinks((messages ?? []).map((m) => m.body))} onClose={() => setShowLinks(false)} />
      )}

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--itunda-grey-100)', fontSize: 'var(--itunda-type-scale-12-size)' }}>
          <span aria-hidden="true" style={{ display: 'inline-flex' }}><PinGlyph size={14} /></span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-600)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px' }}>
        {messages === null && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {messages?.map((m, index, list) => {
          const isMine = m.senderId === currentUser?.id;
          const showTimestamp = shouldShowChatTimestamp(list, index);
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {!isMine && (
                <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '2px', marginLeft: '4px' }}>
                  {nameForSender(m.senderId)}
                </span>
              )}
              {/* Real message forwarding (2026-07-25) -- see lib/messaging.ts's own
                  doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
              )}
              {m.emoticonId ? (
                <EmoticonBubble imageUrl={emoticonImageById[m.emoticonId]} />
              ) : m.imageUrl ? (
                // Real photo message -- ports Android TalkScreen.kt's own identical
                // addition (2026-08-04) to bank-mfe.
                <img src={m.imageUrl} alt="Shared photo" style={{ maxWidth: '220px', borderRadius: '16px' }} />
              ) : (
                <div
                  style={{
                    maxWidth: '75%',
                    padding: '10px 14px',
                    borderRadius: '16px',
                    fontSize: 'var(--itunda-type-scale-14-size)',
                    backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                    color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                  }}
                >
                  {renderTextWithEmoji(m.body)}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Reply</button>
              {!m.imageUrl && <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Copy</button>}
              {!(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Forward</button>}
              {isMine && !(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              {(showTimestamp || (isMine && m.unreadCount > 0)) && (
                <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                  {/* Real Kakao-style read-receipt countdown -- see
                      GroupMessagingService.getUnreadCounts's own doc comment. Only shown
                      on my own messages, same convention 1:1's own "1" indicator uses;
                      disappears at 0, exactly matching real KakaoTalk. */}
                  {[isMine && m.unreadCount > 0 ? `${m.unreadCount}` : null, showTimestamp ? chatMessageTime(m.sentAt) : null].filter(Boolean).join(' · ')}
                </span>
              )}
              {/* Real Thread support (2026-08-05) -- see ConversationThread's own
                  identical affordance. */}
              {!!m.replyCount && (
                <button
                  type="button"
                  onClick={() => setThreadRootMessage(m)}
                  style={{ border: 'none', background: 'none', color: 'var(--itunda-indigo)', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, padding: '4px 0' }}
                >
                  {m.replyCount} {m.replyCount === 1 ? 'reply' : 'replies'} →
                </button>
              )}
            </div>
          );
        })}
        <div ref={bottomRef} />
      </div>
      {forwardingMessage && <ForwardPickerModal onForward={handleForward} onClose={() => setForwardingMessage(null)} />}
      {threadRootMessage && (
        <ThreadModal
          rootMessage={threadRootMessage}
          currentUserId={currentUser?.id}
          fetchThreadMessages={() => fetchGroupThread(group.groupId, threadRootMessage.id)}
          onSend={(body) => sendGroupMessage(group.groupId, body, threadRootMessage.id)}
          onClose={() => { setThreadRootMessage(null); load(); }}
        />
      )}

      {/* Real screen-reader accessibility fix (docs/DESIGN_REFERENCES.md §14) -- same
          new-message live region as ConversationThread's own identical addition. */}
      <div className="sr-only" aria-live="polite" aria-atomic="true">{liveAnnouncement}</div>
      {Object.keys(typingUserIds).length > 0 && (
        <p aria-live="polite" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {Object.keys(typingUserIds).map(nameForSender).join(', ')} {Object.keys(typingUserIds).length === 1 ? 'is' : 'are'} typing…
        </p>
      )}

      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      {emoticonPickerOpen && (
        <EmoticonPickerPanel onSend={handleSendGroupEmoticon} onOpenStore={() => setEmoticonStoreOpen(true)} />
      )}
      {emoticonStoreOpen && <EmoticonStoreModal onClose={() => setEmoticonStoreOpen(false)} />}

      {replyingTo && <div style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-600)', padding: '8px', borderLeft: '3px solid var(--itunda-indigo)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" aria-label="Cancel reply" onClick={() => setReplyingTo(null)}>×</button></div>}
      <MentionSuggestions draft={draft} members={members} currentUserId={currentUser?.id} onPick={(name) => setDraft((d) => applyMention(d, name))} />
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {emojiPickerOpen && <EmojiPicker onPick={(emoji) => setDraft((d) => d + emoji)} />}
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            Kakao's own real "+"-opens-a-menu pattern (References table: "'+' opens a
            multi-function attach menu"). */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--itunda-white)', border: '1px solid var(--itunda-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '8px' }}><CameraGlyph size={16} /> Photo</span>
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmojiPickerOpen((v) => !v); }} style={{ display: 'flex', alignItems: 'center', gap: '8px', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              <SmileySlight size={16} /> Emoji
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: 'var(--itunda-type-scale-14-size)' }}>
              😊 Emoticon
            </button>
          </div>
        )}
        <input
          type="text"
          value={draft}
          onChange={(e) => {
            setDraft(e.target.value);
            const now = Date.now();
            if (now - lastTypingSentAt.current > 2000) {
              lastTypingSentAt.current = now;
              socketRef.current?.sendTyping({ groupConversationId: group.groupId });
            }
          }}
          placeholder="Message"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
        />
        <button type="submit" aria-label="Send message" className="itunda-btn itunda-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <IconSend size={16} />
        </button>
      </form>
    </div>
  );
}

// Real per-thread shared-media gallery (Kakao's real "Chat Room Drawer") -- ports
// Android TalkScreen.kt's own identical addition (2026-08-04) to bank-mfe. Scoped
// honestly to photos only: itunda has real photo messages but no file-attachment type
// and no link-preview system, so a real "files/links" tab would have nothing genuine
// to show. Built entirely client-side from the conversation's own already-loaded
// messages (filtered to real imageUrl != null entries) -- no new backend endpoint.
function MediaGalleryModal({ imageUrls, onClose }: { imageUrls: string[]; onClose: () => void }) {
  return (
    <div
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        style={{ background: 'var(--itunda-white)', borderRadius: '16px 16px 0 0', padding: '16px', width: '100%', maxHeight: '70vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>Shared photos ({imageUrls.length})</h3>
        {imageUrls.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No photos shared in this conversation yet.</p>
        ) : (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '4px' }}>
            {imageUrls.map((url, i) => (
              <img key={i} src={url} alt="Shared photo" style={{ width: '100%', aspectRatio: '1', objectFit: 'cover', borderRadius: '6px' }} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// Real message forwarding (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Talk
// section recommendation #3. Lists the caller's own real conversations and groups
// (same fetchConversations/fetchGroups this tab's own list views already use) -- never
// a public directory, matching this whole feature's own privacy-preserving precedent.
function ForwardPickerModal({ onForward, onClose }: { onForward: (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => void; onClose: () => void }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);

  useEffect(() => {
    fetchConversations().then(setConversations).catch(() => setConversations([]));
    fetchGroups().then(setGroups).catch(() => setGroups([]));
  }, []);

  // Real fix (full-app audit, docs/UI_UX_GUIDELINES.md rule 1) -- same dark-overlay-
  // card pattern EmoticonStoreModal above had, now FullScreenFlow like the rest of
  // this file's already-modernized flows.
  return (
    <FullScreenFlow bottomCTA={<IdsButton variant="tinted" fullWidth onClick={onClose}>Cancel</IdsButton>}>
      <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '12px' }}>Forward to…</p>
      {conversations === null || groups === null ? (
        <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
      ) : conversations.length === 0 && groups.length === 0 ? (
        <EmptyState message="No conversations to forward to yet — start a chat first." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {conversations.map((c) => (
            <button
              key={c.conversationId}
              style={{ width: '100%', textAlign: 'left', padding: '10px 0' }}
              onClick={() => onForward('DIRECT', c.conversationId)}
            >
              {c.otherUserName}
            </button>
          ))}
          {groups.map((g) => (
            <button
              key={g.groupId}
              style={{ width: '100%', textAlign: 'left', padding: '10px 0' }}
              onClick={() => onForward('GROUP', g.groupId)}
            >
              {g.name} (group)
            </button>
          ))}
        </div>
      )}
    </FullScreenFlow>
  );
}

// Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
// recommendation #3's own account. A real sub-conversation view: the root message,
// every direct reply oldest-first, and a composer that replies straight into this same
// thread (never the flat top-level timeline). Generic over Message/GroupMessage since
// both share the same id/senderId/body/sentAt/deletedAt/reactions shape this view needs.
function ThreadModal<T extends { id: string; senderId: string; body: string; sentAt: string; deletedAt?: string | null; reactions: ReactionGroup[] }>({
  rootMessage,
  currentUserId,
  fetchThreadMessages,
  onSend,
  onClose,
}: {
  rootMessage: T;
  currentUserId: string | undefined;
  fetchThreadMessages: () => Promise<T[]>;
  onSend: (body: string) => Promise<unknown>;
  onClose: () => void;
}) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);

  const load = () => {
    fetchThreadMessages()
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => { load(); }, [rootMessage.id]);

  const handleSend = async () => {
    const body = draft.trim();
    if (!body) return;
    setSending(true);
    try {
      await onSend(body);
      setDraft('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally { setSending(false); }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div
        className="itunda-card"
        style={{ width: '100%', maxHeight: '80vh', display: 'flex', flexDirection: 'column', borderRadius: '16px 16px 0 0', margin: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>Thread</p>
          {/* Real touch-target-size fix (item 244, web accessibility sweep):
              same as this file's other modal-close "x" -- see its own comment. */}
          <button type="button" aria-label="Close" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-500)', padding: '8px', minWidth: '24px', minHeight: '24px' }}>×</button>
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }}>{error}</p>}
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px', paddingBottom: '8px' }}>
          {messages === null ? (
            <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : (
            messages.map((m, i) => {
              const isMine = m.senderId === currentUserId;
              return (
                <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
                  {i === 0 && <span style={{ fontSize: '10px', color: 'var(--itunda-grey-400)', marginBottom: '2px' }}>Original message</span>}
                  <div
                    style={{
                      maxWidth: '75%', padding: '10px 14px', borderRadius: '16px', fontSize: 'var(--itunda-type-scale-14-size)',
                      backgroundColor: isMine ? 'var(--itunda-indigo)' : 'var(--itunda-grey-100)',
                      color: isMine ? 'var(--itunda-white)' : 'var(--itunda-grey-900)',
                    }}
                  >
                    {m.deletedAt ? 'This message was deleted' : renderTextWithEmoji(m.body)}
                  </div>
                  <span style={{ fontSize: '10px', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>{chatMessageTime(m.sentAt)}</span>
                </div>
              );
            })
          )}
        </div>
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            className="itunda-input"
            style={{ flex: 1 }}
            placeholder="Reply in thread…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSend(); }}
          />
          <button type="button" className="itunda-btn itunda-btn-primary" style={{ padding: '10px 16px' }} onClick={handleSend} disabled={sending || !draft.trim()}>
            {sending ? '…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
}

// Real client-side-synthesized row (itunda + itunda AI) -- same visual shape as a
// real conversation row so the service channel/AI chatbot read like natural rows in
// the list, matching real KakaoTalk's own official-channel-alongside-friends layout.
function TalkVirtualRow({ icon, name, preview, onClick }: { icon: ReactElement; name: string; preview: string; onClick: () => void }) {
  return (
    <button
      onClick={onClick}
      style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', background: 'none', border: 'none', cursor: 'pointer' }}
    >
      <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        {icon}
      </div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{name}</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>{preview}</p>
      </div>
    </button>
  );
}

// Real per-room theme lookup (see lib/roomSettings.ts's own doc comment) -- reads
// fresh from localStorage on every call, no reactive store needed since a normal
// React re-render already re-evaluates this in JSX.
function roomThemeColor(conversationId: string): string | undefined {
  const id = getRoomTheme(conversationId);
  return ROOM_THEMES.find((t) => t.id === id)?.color ?? undefined;
}

function emptyConversationsMessage(showArchived: boolean, filterTab: 'all' | 'unread' | 'calls'): string {
  if (showArchived) return "You haven't archived any chats.";
  if (filterTab === 'unread') return 'No unread chats.';
  return 'No conversations yet — start one from Friends, or say hi to someone you already know.';
}

function DirectMessagesList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  const { t } = useI18n();
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);
  // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
  // .archived's own doc comment. Loaded alongside the active list so the
  // "Archived (N)" toggle has a real count without an extra round-trip.
  const [archivedConversations, setArchivedConversations] = useState<ConversationSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openConversationId, setOpenConversationId] = useState<string | null>(null);
  const [presence, setPresence] = useState<Record<string, boolean>>({});
  // Real fix, found live 2026-08-05 (same audit that found the identical bug on
  // Android/iOS): this used to filter on `quiet` (mute) and mislabel the result
  // "Archived" -- there was no real archive concept on the backend yet, so muting
  // had been repurposed to also hide a conversation from the list. Muted
  // conversations now stay visible in the main list (matching real KakaoTalk: muting
  // only silences notifications, it never hides a room); this toggle now shows the
  // real archived list.
  const [showArchived, setShowArchived] = useState(false);
  // Real KakaoTalk chat-list filter tabs (전체/안읽음/통화) (itunda Talk redesign,
  // 2026-08-28) -- 전체/안읽음 are pure client-side filters over the already-fetched
  // list (no backend change needed); 통화 shows the real call log instead of the
  // conversation list, fetched only when that tab is actually selected.
  const [filterTab, setFilterTab] = useState<'all' | 'unread' | 'calls'>('all');
  const [callHistory, setCallHistory] = useState<CallSession[] | null>(null);
  // Real room-lock gate (itunda Talk redesign, 2026-08-28) -- see
  // TalkRoomSettings.tsx's own doc comment. Session-local: unlocking a room once
  // keeps it open for the rest of this tab session, matching real KakaoTalk's own
  // per-app-open (not per-message) lock behavior.
  const [unlockedRoomIds, setUnlockedRoomIds] = useState<Set<string>>(new Set());

  const load = () => {
    setError(null);
    fetchConversations()
      .then(setConversations)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchConversations(true).then(setArchivedConversations).catch(() => {});
  };

  const toggleArchived = (conversationId: string, archived: boolean) => {
    setConversationArchived(conversationId, archived).then(load).catch(() => {});
  };

  // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) -- found on a fresh
  // uncalled-endpoint sweep: MessagingController's pin-to-top endpoints and
  // ConversationSummary.pinnedToTop were already fully built on the backend with zero
  // client anywhere. Same always-visible-icon-button convention as the Archive action
  // right next to it.
  const togglePinnedToTop = (conversationId: string, pinned: boolean) => {
    setConversationPinnedToTop(conversationId, pinned).then(load).catch(() => {});
  };

  // Real KakaoTalk 즐겨찾기 (favorite) toggle -- see backend MessagingService
  // .setConversationFavorite's own doc comment, mirrors togglePinnedToTop exactly.
  const toggleFavorite = (conversationId: string, favorite: boolean) => {
    setConversationFavorite(conversationId, favorite).then(load).catch(() => {});
  };

  useEffect(load, []);

  useEffect(() => {
    if (filterTab === 'calls' && callHistory === null) {
      fetchCallHistory().then(setCallHistory).catch(() => setCallHistory([]));
    }
  }, [filterTab, callHistory]);

  // Real online/offline presence for the list view (2026-07-19) -- a bulk on-demand
  // check for every listed contact, refreshed on a 10s cadence (a real, coarser-grained
  // signal than the 4s message poll -- presence doesn't need to be as fresh as message
  // delivery). No live WebSocket connection is opened just for this list view; the
  // per-thread real-time push happens in ConversationThread once a thread is open.
  useEffect(() => {
    if (!conversations || conversations.length === 0) return;
    const otherIds = conversations.map((c) => c.otherUserId);
    const refresh = () => fetchPresence(otherIds).then(setPresence).catch(() => {});
    refresh();
    const interval = setInterval(refresh, 10000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [conversations?.map((c) => c.otherUserId).join(',')]);

  // Real "jump straight into the chat" hand-off from MarketplaceView's "Message
  // seller" button -- contactSeller() returns a real conversation id (either freshly
  // created or an existing one reused), which this opens directly once it shows up in
  // the real conversation list, rather than making the buyer find it themselves.
  useEffect(() => {
    if (initialConversationId && conversations?.some((c) => c.conversationId === initialConversationId)) {
      setOpenConversationId(initialConversationId);
      onConsumedInitial?.();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialConversationId, conversations]);

  // Real client-side-synthesized entry points for the service channel + AI chatbot
  // (itunda Talk redesign, 2026-08-28) -- no real conversation row exists for either
  // on the backend (see ServiceChannelService/AiChatService's own doc comments), so
  // this is session-local nav state, not a conversationId.
  const [openVirtualThread, setOpenVirtualThread] = useState<'service' | 'ai' | null>(null);
  if (openVirtualThread === 'service') return <TalkServiceChannelThread onBack={() => setOpenVirtualThread(null)} />;
  if (openVirtualThread === 'ai') return <TalkAiChatThread onBack={() => setOpenVirtualThread(null)} />;

  const openConversation = conversations?.find((c) => c.conversationId === openConversationId);
  if (openConversation) {
    if (isRoomLocked(openConversation.conversationId) && !unlockedRoomIds.has(openConversation.conversationId)) {
      return (
        <div style={{ maxWidth: '360px', margin: '40px auto' }}>
          <DeviceStepUpPrompt
            onVerified={() => setUnlockedRoomIds((prev) => new Set(prev).add(openConversation.conversationId))}
            onCancel={() => setOpenConversationId(null)}
          />
        </div>
      );
    }
    return (
      <ConversationThread
        conversation={openConversation}
        onBack={() => {
          setOpenConversationId(null);
          load();
        }}
      />
    );
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (conversations === null) {
    return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  // Pinned rooms float to the top of the active list, same as real KakaoTalk --
  // a stable sort so unpinned rooms keep their existing most-recent-first order.
  const activeConversations = (showArchived
    ? (archivedConversations ?? [])
    : [...conversations].sort((a, b) => Number(b.pinnedToTop) - Number(a.pinnedToTop))) as ConversationSummaryWithFavorite[];
  const visibleConversations = filterTab === 'unread' ? activeConversations.filter((c) => c.unreadCount > 0) : activeConversations;
  const archivedCount = archivedConversations?.length ?? 0;

  return (
    <div>
      <NewChatCard onStarted={(id) => { load(); setOpenConversationId(id); }} />
      {/* Real KakaoTalk chat-list filter tabs (전체/안읽음/통화) -- a flat row of
          text tabs, matching this codebase's own flat-design-over-cards convention
          for new screens rather than a pill/segmented-control card. */}
      <div style={{ display: 'flex', gap: '20px', borderBottom: '1px solid var(--itunda-grey-100)', marginBottom: '14px' }}>
        {([['all', '전체'], ['unread', '안읽음'], ['calls', '통화']] as const).map(([key, label]) => (
          <button
            key={key}
            type="button"
            onClick={() => setFilterTab(key)}
            style={{
              background: 'none', border: 'none', cursor: 'pointer', padding: '10px 2px',
              fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700,
              color: filterTab === key ? 'var(--itunda-grey-900)' : 'var(--itunda-grey-400)',
              borderBottom: filterTab === key ? '2px solid var(--itunda-indigo)' : '2px solid transparent',
            }}
          >
            {label}
          </button>
        ))}
      </div>
      {filterTab === 'calls' ? (
        <TalkCallLog calls={callHistory} currentUserId={getStoredUser()?.id ?? null} />
      ) : (
        <>
      {filterTab === 'all' && !showArchived && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '4px' }}>
          <TalkVirtualRow icon={<MessageCircle size={20} color="var(--itunda-indigo)" />} name="itunda" preview="Real-time updates about your account" onClick={() => setOpenVirtualThread('service')} />
          <TalkVirtualRow icon={<Bot size={20} color="var(--itunda-indigo)" />} name="itunda AI" preview="Ask itunda AI anything about the app" onClick={() => setOpenVirtualThread('ai')} />
        </div>
      )}
      {archivedCount > 0 && (
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setShowArchived((value) => !value)} style={{ marginBottom: '10px' }}>
          {showArchived ? 'Show active chats' : `Archived (${archivedCount})`}
        </button>
      )}
      {visibleConversations.length === 0 ? (
        <EmptyState message={emptyConversationsMessage(showArchived, filterTab)} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {visibleConversations.map((c) => (
            // Real Toss-sourced "layering illusion" reorder animation (2026-08-29,
            // toss.tech/article/interaction's own real "Account Organization
            // Animation" example -- reordering a list should animate the move, not
            // jump). Pinning/unpinning a conversation used to snap it to its new
            // position on the next render with zero motion; framer-motion's `layout`
            // prop auto-animates each row's position change via FLIP, no other logic
            // change needed since `key` was already stable.
            <motion.div layout key={c.conversationId} transition={{ type: 'spring', ...itundaSpring.medium }} style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '14px 0' }}>
              <button
                onClick={() => setOpenConversationId(c.conversationId)}
                style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1, minWidth: 0, textAlign: 'left', background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
              >
                <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
                  <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <MessageCircle size={20} color="var(--itunda-indigo)" />
                  </div>
                  {presence[c.otherUserId] && (
                    <span
                      style={{
                        position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                        backgroundColor: 'var(--itunda-green)', border: '2px solid var(--itunda-white)',
                      }}
                    />
                  )}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{c.otherUserName}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {c.lastMessagePreview ?? 'No messages yet'}
                  </p>
                </div>
                {c.unreadCount > 0 && (
                  <span
                    style={{
                      fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
                      borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                    }}
                  >
                    {c.unreadCount}
                  </span>
                )}
              </button>
              {/* Real KakaoTalk 즐겨찾기 (favorite) -- only offered on the active
                  list, same reasoning as pin-to-top below. */}
              {!showArchived && (
                <button
                  type="button"
                  onClick={() => toggleFavorite(c.conversationId, !c.favorite)}
                  title={c.favorite ? 'Remove from favorites' : 'Add to favorites'}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: c.favorite ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  <Star size={18} fill={c.favorite ? 'var(--itunda-indigo)' : 'none'} />
                </button>
              )}
              {/* Real KakaoTalk 채팅방 상단 고정 (pin room to top) -- only offered on
                  the active list, not the archived one (pinning an archived room to
                  the top of a list it isn't shown in doesn't mean anything). */}
              {!showArchived && (
                <button
                  type="button"
                  onClick={() => togglePinnedToTop(c.conversationId, !c.pinnedToTop)}
                  title={c.pinnedToTop ? 'Unpin from top' : 'Pin to top'}
                  style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: c.pinnedToTop ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  {c.pinnedToTop ? <PinOff size={18} /> : <Pin size={18} />}
                </button>
              )}
              {/* Real archive action (2026-08-05) -- closes docs/DESIGN_REFERENCES.md
                  Talk recommendation #4's remaining half. An always-visible icon
                  button, not a swipe gesture: bank-mfe's own established convention
                  for per-row actions elsewhere (Pin/Delete/Forward) is always-visible
                  buttons, and desktop-web has no real touch-swipe convention to match
                  Android/iOS's native one against. */}
              <button
                type="button"
                onClick={() => toggleArchived(c.conversationId, !showArchived)}
                title={showArchived ? 'Unarchive' : 'Archive'}
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: 'var(--itunda-grey-500)' }}
              >
                {showArchived ? <ArchiveRestore size={18} /> : <Archive size={18} />}
              </button>
            </motion.div>
          ))}
        </div>
      )}
        </>
      )}
    </div>
  );
}

// Real 1:1 voice/video calling log (itunda Talk redesign, 2026-08-28) -- the 통화
// filter tab's content. Read-only: shows real call history from CallService's own
// persisted CallSession rows. No "place a call" affordance yet -- that's a separate,
// later piece once the calling UI itself (WebRTC/dial screen) is built.
function TalkCallLog({ calls, currentUserId }: { calls: CallSession[] | null; currentUserId: string | null }) {
  if (calls === null) {
    return <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }
  if (calls.length === 0) {
    return <EmptyState message="No calls yet." />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {calls.map((call) => {
        const outgoing = call.callerId === currentUserId;
        const missed = call.endReason === 'MISSED' || call.endReason === 'DECLINED';
        let direction = 'Incoming';
        if (outgoing) direction = 'Outgoing';
        else if (missed) direction = 'Missed call';
        return (
          <div key={call.id} style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '14px 0' }}>
            <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
              <Phone size={20} color="var(--itunda-indigo)" />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: missed ? 'var(--itunda-red)' : 'var(--itunda-grey-900)' }}>
                {direction} {call.callType === 'VIDEO' ? 'video' : 'voice'} call
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
                {new Date(call.startedAt).toLocaleString()}
              </p>
            </div>
          </div>
        );
      })}
    </div>
  );
}

// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself being
// fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real account-to-account push, no escrow -- see SplitBill.kt's own doc comment.
function GroupSplitBillsView({
  groupConversationId, members, currentUserId, onBack,
}: { groupConversationId: string; members: GroupMember[]; currentUserId: string | null; onBack: () => void }) {
  const { t } = useI18n();
  const [splitBills, setSplitBills] = useState<SplitBillWithParticipants[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [showNewForm, setShowNewForm] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [selectedIds, setSelectedIds] = useState<Set<string>>(new Set());
  // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25 on Android's TalkScreen.kt --
  // bank-mfe never got this despite usually shipping first) -- see backend
  // SplitBillService.ladderSplit's own doc comment for the 3 variance levels.
  const [ladderMode, setLadderMode] = useState(false);
  const [varianceLevel, setVarianceLevel] = useState(1);
  const [receiptUrlDrafts, setReceiptUrlDrafts] = useState<Record<string, string>>({});

  const refresh = () =>
    fetchSplitBillsForGroup(groupConversationId)
      .then(setSplitBills)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  useEffect(() => { refresh(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [groupConversationId]);

  const otherMembers = members.filter((m) => m.userId !== currentUserId);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusyId('new');
    setError(null);
    try {
      await createSplitBill(
        groupConversationId, Number(amount), description, Array.from(selectedIds),
        ladderMode ? 'LADDER' : 'EVEN', ladderMode ? varianceLevel : undefined,
      );
      setAmount(''); setDescription(''); setSelectedIds(new Set()); setShowNewForm(false); setLadderMode(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handlePay = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await paySplitBillShare(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleAttachReceipt = async (splitBillId: string) => {
    const url = (receiptUrlDrafts[splitBillId] ?? '').trim();
    if (!url) return;
    setBusyId(splitBillId);
    setError(null);
    try {
      await attachSplitBillReceipt(splitBillId, url);
      setReceiptUrlDrafts((prev) => ({ ...prev, [splitBillId]: '' }));
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleNextRound = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await requestSplitBillNextRound(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to group">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Split bills</h3>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowNewForm(true)}>Split a bill</button>
      ) : (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '10px 0' }}>
          <input
            type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Total amount (RWF)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was it for?" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Split with</p>
          {otherMembers.map((m) => (
            <label key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {m.name}
              <input
                type="checkbox"
                checked={selectedIds.has(m.userId)}
                onChange={(e) => {
                  const next = new Set(selectedIds);
                  if (e.target.checked) next.add(m.userId); else next.delete(m.userId);
                  setSelectedIds(next);
                }}
              />
            </label>
          ))}
          {/* Real a11y fix (item 244, web accessibility sweep): this was a plain
              <label> with an onClick and no associated form control -- a bare
              <label> isn't in the tab order and isn't activatable via
              Enter/Space, so keyboard-only and screen-reader users had no way to
              reach this real toggle at all. A <button> is the correct element:
              real keyboard focus/operability, no visual change needed beyond
              resetting the browser's default button chrome. */}
          <button
            type="button"
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', fontSize: 'var(--itunda-type-scale-13-size)', cursor: 'pointer', background: 'none', border: 'none', padding: 0, textAlign: 'left', font: 'inherit', color: 'inherit' }}
            onClick={() => setLadderMode((v) => !v)}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><DiceGlyph size={16} /> Ladder game (randomized split)</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: ladderMode ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)', fontWeight: 700 }}>
              {ladderMode ? 'On' : 'Off'}
            </span>
          </button>
          {ladderMode && (
            <div style={{ display: 'flex', gap: '8px' }}>
              {[1, 2, 3].map((level) => (
                <button
                  key={level} type="button"
                  className={level === varianceLevel ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                  style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)' }}
                  onClick={() => setVarianceLevel(level)}
                >
                  Level {level}
                </button>
              ))}
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setShowNewForm(false)}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === 'new' || selectedIds.size === 0}>
              {busyId === 'new' ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      )}
      {splitBills === null && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {splitBills !== null && splitBills.length === 0 && (
        <EmptyState message="No split bills in this group yet — split one to divide a shared expense evenly." />
      )}
      {splitBills?.map(({ splitBill, participants }) => {
        const myShare = participants.find((p) => p.userId === currentUserId);
        const isOrganizer = splitBill.organizerId === currentUserId;
        const hasPending = participants.some((p) => p.status === 'PENDING');
        const modeLabel = splitBill.mode === 'LADDER' ? <> · <DiceGlyph size={12} /> Ladder L{splitBill.ladderVarianceLevel}</> : null;
        return (
          <div key={splitBill.id} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{splitBill.description}</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              Total {splitBill.totalAmount.toLocaleString()} RWF · {splitBill.status}{modeLabel}
              {splitBill.currentRound > 1 ? ` · Round ${splitBill.currentRound}` : ''}
            </p>
            {participants.map((p) => {
              const name = members.find((m) => m.userId === p.userId)?.name ?? p.userId.slice(0, 8);
              return (
                <p key={p.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>
                  {name}: {p.shareAmount.toLocaleString()} RWF ({p.status})
                </p>
              );
            })}
            {splitBill.receiptImageUrl && (
              <a href={splitBill.receiptImageUrl} target="_blank" rel="noreferrer" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>
                🧾 View receipt
              </a>
            )}
            {myShare && myShare.status === 'PENDING' && (
              <button
                className="itunda-btn itunda-btn-primary" style={{ marginTop: '6px' }}
                disabled={busyId === splitBill.id}
                onClick={() => handlePay(splitBill.id)}
              >
                {busyId === splitBill.id ? 'Paying…' : `Pay my share (${myShare.shareAmount.toLocaleString()} RWF)`}
              </button>
            )}
            {isOrganizer && (
              <>
                {!splitBill.receiptImageUrl && (
                  <div style={{ display: 'flex', gap: '6px', marginTop: '4px' }}>
                    <input
                      type="text" placeholder="Receipt photo URL" value={receiptUrlDrafts[splitBill.id] ?? ''}
                      onChange={(e) => setReceiptUrlDrafts((prev) => ({ ...prev, [splitBill.id]: e.target.value }))}
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    />
                    <button
                      type="button" className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}
                      disabled={busyId === splitBill.id || !(receiptUrlDrafts[splitBill.id] ?? '').trim()}
                      onClick={() => handleAttachReceipt(splitBill.id)}
                    >
                      Attach
                    </button>
                  </div>
                )}
                {splitBill.status === 'OPEN' && hasPending && splitBill.currentRound < 5 && (
                  <button
                    type="button" className="itunda-btn itunda-btn-secondary" style={{ marginTop: '4px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    disabled={busyId === splitBill.id}
                    onClick={() => handleNextRound(splitBill.id)}
                  >
                    Nudge unpaid → round {splitBill.currentRound + 1}
                  </button>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

// Real 1:1-chat split-bill view (2026-08-09) -- see lib/splitBill.ts's own doc comment
// on createDirectSplitBill/fetchDirectSplitBills for the backend account. Same shape
// as GroupSplitBillsView above, minus the member-picker: a 1:1 split always has
// exactly one other participant, fixed by which conversation this was opened from.
function DirectSplitBillsView({
  otherUserId, otherUserName, currentUserId, onBack,
}: { otherUserId: string; otherUserName: string; currentUserId: string | null; onBack: () => void }) {
  const { t } = useI18n();
  const [splitBills, setSplitBills] = useState<SplitBillWithParticipants[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [showNewForm, setShowNewForm] = useState(false);
  const [amount, setAmount] = useState('');
  const [description, setDescription] = useState('');
  const [ladderMode, setLadderMode] = useState(false);
  const [varianceLevel, setVarianceLevel] = useState(1);
  const [receiptUrlDrafts, setReceiptUrlDrafts] = useState<Record<string, string>>({});

  const refresh = () =>
    fetchDirectSplitBills(otherUserId)
      .then(setSplitBills)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));

  useEffect(() => { refresh(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [otherUserId]);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusyId('new');
    setError(null);
    try {
      await createDirectSplitBill(
        otherUserId, Number(amount), description,
        ladderMode ? 'LADDER' : 'EVEN', ladderMode ? varianceLevel : undefined,
      );
      setAmount(''); setDescription(''); setShowNewForm(false); setLadderMode(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handlePay = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await paySplitBillShare(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleAttachReceipt = async (splitBillId: string) => {
    const url = (receiptUrlDrafts[splitBillId] ?? '').trim();
    if (!url) return;
    setBusyId(splitBillId);
    setError(null);
    try {
      await attachSplitBillReceipt(splitBillId, url);
      setReceiptUrlDrafts((prev) => ({ ...prev, [splitBillId]: '' }));
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  const handleNextRound = async (splitBillId: string) => {
    setBusyId(splitBillId);
    setError(null);
    try {
      await requestSplitBillNextRound(splitBillId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to conversation">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Split bills with {otherUserName}</h3>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowNewForm(true)}>Split a bill</button>
      ) : (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '10px 0' }}>
          <input
            type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Total amount (RWF)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <input
            type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was it for?" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Split with {otherUserName}</p>
          <button
            type="button"
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', fontSize: 'var(--itunda-type-scale-13-size)', cursor: 'pointer', background: 'none', border: 'none', padding: 0, textAlign: 'left', font: 'inherit', color: 'inherit' }}
            onClick={() => setLadderMode((v) => !v)}
          >
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><DiceGlyph size={16} /> Ladder game (randomized split)</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: ladderMode ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)', fontWeight: 700 }}>
              {ladderMode ? 'On' : 'Off'}
            </span>
          </button>
          {ladderMode && (
            <div style={{ display: 'flex', gap: '8px' }}>
              {[1, 2, 3].map((level) => (
                <button
                  key={level} type="button"
                  className={level === varianceLevel ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
                  style={{ flex: 1, fontSize: 'var(--itunda-type-scale-12-size)' }}
                  onClick={() => setVarianceLevel(level)}
                >
                  Level {level}
                </button>
              ))}
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setShowNewForm(false)}>Cancel</button>
            <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === 'new'}>
              {busyId === 'new' ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      )}
      {splitBills === null && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {splitBills !== null && splitBills.length === 0 && (
        <EmptyState message={`No split bills with ${otherUserName} yet — split one to divide a shared expense evenly.`} />
      )}
      {splitBills?.map(({ splitBill, participants }) => {
        const myShare = participants.find((p) => p.userId === currentUserId);
        const isOrganizer = splitBill.organizerId === currentUserId;
        const hasPending = participants.some((p) => p.status === 'PENDING');
        const modeLabel = splitBill.mode === 'LADDER' ? <> · <DiceGlyph size={12} /> Ladder L{splitBill.ladderVarianceLevel}</> : null;
        return (
          <div key={splitBill.id} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{splitBill.description}</h4>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              Total {splitBill.totalAmount.toLocaleString()} RWF · {splitBill.status}{modeLabel}
              {splitBill.currentRound > 1 ? ` · Round ${splitBill.currentRound}` : ''}
            </p>
            {participants.map((p) => (
              <p key={p.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}>
                {p.userId === otherUserId ? otherUserName : 'You'}: {p.shareAmount.toLocaleString()} RWF ({p.status})
              </p>
            ))}
            {splitBill.receiptImageUrl && (
              <a href={splitBill.receiptImageUrl} target="_blank" rel="noreferrer" style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)' }}>
                🧾 View receipt
              </a>
            )}
            {myShare && myShare.status === 'PENDING' && (
              <button
                className="itunda-btn itunda-btn-primary" style={{ marginTop: '6px' }}
                disabled={busyId === splitBill.id}
                onClick={() => handlePay(splitBill.id)}
              >
                {busyId === splitBill.id ? 'Paying…' : `Pay my share (${myShare.shareAmount.toLocaleString()} RWF)`}
              </button>
            )}
            {isOrganizer && (
              <>
                {!splitBill.receiptImageUrl && (
                  <div style={{ display: 'flex', gap: '6px', marginTop: '4px' }}>
                    <input
                      type="text" placeholder="Receipt photo URL" value={receiptUrlDrafts[splitBill.id] ?? ''}
                      onChange={(e) => setReceiptUrlDrafts((prev) => ({ ...prev, [splitBill.id]: e.target.value }))}
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    />
                    <button
                      type="button" className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)' }}
                      disabled={busyId === splitBill.id || !(receiptUrlDrafts[splitBill.id] ?? '').trim()}
                      onClick={() => handleAttachReceipt(splitBill.id)}
                    >
                      Attach
                    </button>
                  </div>
                )}
                {splitBill.status === 'OPEN' && hasPending && splitBill.currentRound < 5 && (
                  <button
                    type="button" className="itunda-btn itunda-btn-secondary" style={{ marginTop: '4px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                    disabled={busyId === splitBill.id}
                    onClick={() => handleNextRound(splitBill.id)}
                  >
                    Nudge unpaid → round {splitBill.currentRound + 1}
                  </button>
                )}
              </>
            )}
          </div>
        );
      })}
    </div>
  );
}

// Real leave-group/add-member (2026-07-22) -- found fully built on the backend
// (GroupMessagingController's POST/DELETE .../members) with zero client UI anywhere.
// Add-member picks from the caller's real Talk contacts, same list used to start a
// 1:1 chat, filtered to exclude people already in the group.
function GroupManageMembersView({
  group, members, currentUserId, onMembersChanged, onLeft, onBack,
}: {
  group: GroupSummary; members: GroupMember[]; currentUserId: string | null;
  onMembersChanged: () => void; onLeft: () => void; onBack: () => void;
}) {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<TalkContact[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busyUserId, setBusyUserId] = useState<string | null>(null);
  const [leaving, setLeaving] = useState(false);
  // Real group photo/description (2026-07-28) -- see lib/messaging.ts's own doc
  // comment. Found 2026-08-01 via a defined-but-uncalled-endpoint sweep: real on
  // backend since it shipped, zero client anywhere until now.
  const [photoUrl, setPhotoUrl] = useState(group.photoUrl ?? '');
  const [description, setDescription] = useState(group.description ?? '');
  const [savingInfo, setSavingInfo] = useState(false);
  const [infoSaved, setInfoSaved] = useState(false);

  useEffect(() => {
    fetchTalkContacts().then(setContacts).catch(() => {});
  }, []);

  const addable = contacts.filter((c) => !members.some((m) => m.userId === c.userId));

  const handleSaveInfo = async () => {
    setSavingInfo(true);
    setError(null);
    setInfoSaved(false);
    try {
      await setGroupPhotoUrl(group.groupId, photoUrl.trim());
      await setGroupDescription(group.groupId, description.trim());
      setInfoSaved(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSavingInfo(false);
    }
  };

  const handleLeave = async () => {
    if (!window.confirm('Leave this group?')) return;
    setLeaving(true);
    setError(null);
    try {
      await leaveGroup(group.groupId);
      onLeft();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
      setLeaving(false);
    }
  };

  const handleAdd = async (contact: TalkContact) => {
    setBusyUserId(contact.userId);
    setError(null);
    try {
      await addGroupMember(group.groupId, contact.userId);
      onMembersChanged();
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): adding a
      // contact already in the group isn't really a failure -- resolve forward.
      if (err instanceof ApiError && err.code === 'ALREADY_MEMBER') {
        onMembersChanged();
      } else {
        setError(err instanceof ApiError ? err.message : `Could not add ${contact.name}.`);
      }
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to group">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Manage members</h3>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Group info</h4>
      <input
        type="text" placeholder="Photo URL (blank to clear)" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        placeholder="Group description (blank to clear)" value={description} onChange={(e) => setDescription(e.target.value)} rows={2}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', fontFamily: 'inherit' }}
      />
      <button className="itunda-btn itunda-btn-secondary" disabled={savingInfo} onClick={handleSaveInfo}>
        {savingInfo ? 'Saving…' : infoSaved ? 'Saved' : 'Save group info'}
      </button>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>Members ({members.length})</h4>
      {members.map((m) => (
        <p key={m.userId} style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{m.userId === currentUserId ? `${m.name} (you)` : m.name}</p>
      ))}
      <button className="itunda-btn itunda-btn-secondary" disabled={leaving} onClick={handleLeave}>
        {leaving ? 'Leaving…' : 'Leave group'}
      </button>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginTop: '8px' }}>Add from your contacts</h4>
      {addable.length === 0 && <EmptyState message="No contacts left to add." />}
      {addable.map((c) => (
        <div key={c.userId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{c.name}</span>
          <button className="itunda-btn itunda-btn-secondary" disabled={busyUserId !== null} onClick={() => handleAdd(c)}>
            {busyUserId === c.userId ? 'Adding…' : 'Add'}
          </button>
        </div>
      ))}
    </div>
  );
}

function GroupsList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void } = {}) {
  const { t } = useI18n();
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGroups()
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  // Real "join meetup" hand-off from CommunityView (2026-07-24) -- same pattern
  // DirectMessagesList's own initialConversationId effect already established, just
  // matched against `groups` instead of 1:1 conversations.
  useEffect(() => {
    if (initialConversationId && groups?.some((g) => g.groupId === initialConversationId)) {
      setOpenGroupId(initialConversationId);
      onConsumedInitial?.();
    }
  }, [initialConversationId, groups]);

  const openGroup = groups?.find((g) => g.groupId === openGroupId);
  if (openGroup) {
    return (
      <GroupThread
        group={openGroup}
        onBack={() => {
          setOpenGroupId(null);
          load();
        }}
      />
    );
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (groups === null) {
    return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  return (
    <div>
      <OpenChatCard
        onCreated={(id) => { load(); setOpenGroupId(id); }}
        onJoined={(id) => { load(); setOpenGroupId(id); }}
      />
      <NewGroupCard onCreated={(id) => { load(); setOpenGroupId(id); }} />
      {groups.length === 0 ? (
        <EmptyState message="No groups yet — start one to chat with more than one person at a time." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {groups.map((g) => (
            <button
              key={g.groupId}
              onClick={() => setOpenGroupId(g.groupId)}
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Users size={20} color="var(--itunda-indigo)" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{g.name} · {g.memberCount}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {g.lastMessagePreview ?? 'No messages yet'}
                </p>
              </div>
              {g.unreadCount > 0 && (
                <span
                  style={{
                    fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)',
                    borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                  }}
                >
                  {g.unreadCount}
                </span>
              )}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

// Real group chat (2026-07-18) folded in via a Direct/Groups toggle -- the single most
// defining KakaoTalk capability the original 1:1-only Messages tab didn't cover, added
// at the user's direct request. See GroupMessagingService.kt's own doc comment.
function MessagesView({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
  // Real fix (2026-08-19): a tapped ?joinChatCode= invite link (see OpenChatCard's own
  // buildJoinUrl doc comment) only carries the top-level ?tab=MESSAGES -- this
  // Direct/Groups/Friends split is its own local state, so without this the link would
  // silently land on Direct and OpenChatCard (which lives under Groups, and owns the
  // actual auto-join effect) would never even mount. A lazy initializer peeks at the
  // param without consuming it -- OpenChatCard's own effect is what deletes it.
  const [mode, setMode] = useState<'DIRECT' | 'GROUPS' | 'FRIENDS'>(() =>
    new URLSearchParams(window.location.search).has('joinChatCode') ? 'GROUPS' : 'DIRECT'
  );
  // Real Kakao-style Friends directory (item 237) -- see FriendsList's own doc
  // comment. Tapping a friend hands its real conversation id off to DirectMessagesList
  // through the exact same initialConversationId mechanism CommunityView's own
  // "join meetup" hand-off below already established, rather than duplicating
  // ConversationThread's own render logic inside FriendsList.
  const [friendJumpConversationId, setFriendJumpConversationId] = useState<string | null>(null);

  // Real "join meetup" hand-off from CommunityView (2026-07-24): a real
  // GroupConversation id, not a 1:1 conversation id, needs the Groups tab
  // pre-selected -- otherwise it would silently render under Direct, where neither
  // DirectMessagesList's own conversation list nor its initialConversationId check
  // would ever match it.
  useEffect(() => {
    if (!initialConversationId) return;
    fetchGroups()
      .then((groups) => {
        if (groups.some((g) => g.groupId === initialConversationId)) setMode('GROUPS');
      })
      .catch(() => {});
  }, [initialConversationId]);

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['DIRECT', 'GROUPS', 'FRIENDS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: mode === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'DIRECT' ? 'Direct' : v === 'GROUPS' ? 'Groups' : 'Friends'}
          </button>
        ))}
      </div>
      {mode === 'DIRECT' ? (
        <DirectMessagesList
          initialConversationId={friendJumpConversationId ?? initialConversationId}
          onConsumedInitial={() => { setFriendJumpConversationId(null); onConsumedInitial?.(); }}
        />
      ) : mode === 'GROUPS' ? (
        <GroupsList initialConversationId={initialConversationId} onConsumedInitial={onConsumedInitial} />
      ) : (
        <FriendsList onOpenConversation={(id) => { setFriendJumpConversationId(id); setMode('DIRECT'); }} />
      )}
    </div>
  );
}

// Real Kakao Friends-tab equivalent (item 237, sourced -- Kakao's Sept 2025 attempt
// to bury this tab caused a rating collapse and was reverted within 3 months, per
// this doc's own Talk section recommendation #1). itunda's Talk only ever let a user
// switch between chat-*history* views (Direct/Groups); there was no way to browse
// contacts who are on itunda but you haven't messaged yet -- "New chat" only worked
// as a hand-typed-phone-number or inline quick-pick composer, not a real browsable
// directory. The backend infra (`GET /messages/contacts`, `GET /messages/presence`)
// was already fully real and already used inline in the New-chat/add-group-member
// composers on all 3 platforms -- this is a client-only addition, no new endpoint.
// Real KakaoTalk "오늘의 생일" (Today's Birthday) (2026-08-17) -- KakaoTalk's own real
// feature shows friends with a birthday today at the top of the friend list with a
// cake icon, letting you message them directly without hunting through the full
// contact list. Reuses FriendsList's own onOpenConversation hand-off convention.
// Renders nothing when the caller has no real contacts with a birthday today -- never
// an empty placeholder card.
function TodaysBirthdaySection({ onOpenConversation }: { onOpenConversation: (conversationId: string) => void }) {
  const [birthdays, setBirthdays] = useState<TalkContact[] | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);

  useEffect(() => {
    fetchTodaysBirthdays().then(setBirthdays).catch(() => setBirthdays([]));
  }, []);

  const handleTap = async (contact: TalkContact) => {
    setStartingId(contact.userId);
    try {
      const conversation = await startConversationWithUser(contact.userId);
      onOpenConversation(conversation.id);
    } catch {
      // Fails quietly -- the user can still reach this same person from the regular
      // Friends list below, same non-blocking discipline FriendsList's own handleTap
      // already establishes for a failed chat start.
    } finally {
      setStartingId(null);
    }
  };

  if (!birthdays || birthdays.length === 0) return null;

  return (
    <div className="itunda-card" style={{ background: 'var(--itunda-indigo-light)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '10px', display: 'flex', alignItems: 'center', gap: '6px' }}><CakeGlyph size={16} /> Today's birthday</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {birthdays.map((c) => (
          <button
            key={c.userId}
            onClick={() => handleTap(c)}
            disabled={startingId === c.userId}
            style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', padding: '8px 10px', borderRadius: '8px', background: 'var(--itunda-white)', border: 'none', textAlign: 'left', cursor: 'pointer' }}
          >
            <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 600 }}>{c.name}</span>
            <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 700 }}>{startingId === c.userId ? '…' : 'Say happy birthday'}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function FriendsList({ onOpenConversation }: { onOpenConversation: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<TalkContact[] | null>(null);
  const [presence, setPresence] = useState<Record<string, boolean>>({});
  const [error, setError] = useState<string | null>(null);
  const [startingId, setStartingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchTalkContacts()
      .then((c) => {
        setContacts(c);
        if (c.length > 0) fetchPresence(c.map((x) => x.userId)).then(setPresence).catch(() => {});
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleTap = async (contact: TalkContact) => {
    setStartingId(contact.userId);
    setError(null);
    try {
      const conversation = await startConversationWithUser(contact.userId);
      onOpenConversation(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setStartingId(null);
    }
  };

  if (error) return <ErrorCard message={error} onRetry={load} />;
  if (contacts === null) return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (contacts.length === 0) {
    return (
      <EmptyState message="No friends yet -- save someone's contact and they'll show up here once they're on itunda." />
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <TodaysBirthdaySection onOpenConversation={onOpenConversation} />
      {contacts.map((c) => (
        <button
          key={c.userId}
          onClick={() => handleTap(c)}
          disabled={startingId === c.userId}
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', width: '100%', textAlign: 'left', background: 'var(--itunda-white)', border: 'none', cursor: 'pointer' }}
        >
          <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
            <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Users size={20} color="var(--itunda-indigo)" />
            </div>
            {presence[c.userId] && (
              <span
                style={{
                  position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                  backgroundColor: 'var(--itunda-green)', border: '2px solid var(--itunda-white)',
                }}
              />
            )}
          </div>
          <div style={{ flex: 1, minWidth: 0 }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{c.name}</p>
            {presence[c.userId] && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', fontWeight: 700 }}>Active now</p>}
          </div>
          {startingId === c.userId && <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>…</span>}
        </button>
      ))}
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


const EATS_STATUS_LABEL: Record<EatsOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted by restaurant',
  PREPARING: 'Preparing',
  READY_FOR_PICKUP: 'Ready for pickup',
  RIDER_ASSIGNED: 'Rider on the way to restaurant',
  PICKED_UP: 'Picked up — on the way',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

const RESTAURANT_STATUS_CHAIN: EatsOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'READY_FOR_PICKUP'];
const RIDER_STATUS_CHAIN: EatsOrderStatus[] = ['RIDER_ASSIGNED', 'PICKED_UP', 'DELIVERED'];

function nextInChain<T>(chain: T[], current: T): T | null {
  const idx = chain.indexOf(current);
  return idx >= 0 && idx + 1 < chain.length ? chain[idx + 1] : null;
}

function EatsOrderCard({ order, restaurant, action, onMessageRestaurant }: { order: EatsOrder; restaurant?: ShoppingMerchant; action?: React.ReactNode; onMessageRestaurant?: () => void }) {
  const [showRoute, setShowRoute] = useState(false);
  const [showLiveTracking, setShowLiveTracking] = useState(false);
  // Real "message restaurant" (2026-08-16, Uber Eats' own real Live Order Chat) --
  // only offered on the buyer's own active orders (onMessageRestaurant is only ever
  // passed by MyEatsOrdersView, never the rider/restaurant-facing renders of this same
  // card), and only while there's still something to coordinate about -- a delivered
  // or cancelled order has nothing left to confirm before the fact.
  const canMessageRestaurant = onMessageRestaurant && order.status !== 'DELIVERED' && order.status !== 'CANCELLED';
  const canShowRoute = restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null;
  // Real live rider tracking (2026-07-20) -- only meaningful while a real rider is
  // actually en route, matching EatsOrderService.getRiderLocation's own real state gate
  // (RIDER_ASSIGNED/PICKED_UP only; before/after that there's honestly nothing to show).
  const canShowLiveTracking = canShowRoute && (order.status === 'RIDER_ASSIGNED' || order.status === 'PICKED_UP');

  return (
    <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{EATS_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.deliveryNotes && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.deliveryNotes}
        </p>
      )}
      {canMessageRestaurant && (
        <button className="itunda-btn itunda-btn-secondary" onClick={onMessageRestaurant}>
          <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><ChatGlyph size={16} /> Message restaurant</span>
        </button>
      )}
      {canShowLiveTracking && (
        <button className="itunda-btn itunda-btn-primary" onClick={() => { setShowLiveTracking((v) => !v); setShowRoute(false); }}>
          {showLiveTracking ? 'Hide live tracking' : '🛵 Track your rider live'}
        </button>
      )}
      {showLiveTracking && restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null && (
        <LiveRiderMap
          orderId={order.id}
          fromLat={restaurant.latitude}
          fromLng={restaurant.longitude}
          toLat={order.deliveryLatitude}
          toLng={order.deliveryLongitude}
          fromLabel={restaurant.businessName}
          toLabel="Delivery address"
        />
      )}
      {canShowRoute && !showLiveTracking && (
        <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowRoute((v) => !v)}>
          {showRoute ? 'Hide route' : '🚗 View real delivery route'}
        </button>
      )}
      {showRoute && !showLiveTracking && restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null && (
        <RouteMiniMap
          fromLat={restaurant.latitude}
          fromLng={restaurant.longitude}
          toLat={order.deliveryLatitude}
          toLng={order.deliveryLongitude}
          fromLabel={restaurant.businessName}
          toLabel="Delivery address"
        />
      )}
      {action}
    </div>
  );
}

function StarRatingInput({ value, onChange }: { value: number; onChange: (rating: number) => void }) {
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

// Real written-review list + owner-reply display (item 184, 2026-07-29) -- the backend
// (getRestaurantReviews, real since restaurant reviews shipped) and a real dead
// fetchRestaurantReviews export both existed with zero UI anywhere calling it; see
// docs/DESIGN_REFERENCES.md section 2 recommendation 5. Mirrors ProductRatingBadge's
// own expand-on-click pattern exactly.
function RestaurantRatingBadge({ restaurantId }: { restaurantId: string }) {
  const [rating, setRating] = useState<RatingSummary | null>(null);
  const [open, setOpen] = useState(false);
  const [reviews, setReviews] = useState<EatsReview[] | null>(null);
  // Real "도움돼요" (helpful) toggle -- see lib/eats.ts's own doc comment.
  const [helpfulVoted, setHelpfulVoted] = useState<Set<string>>(new Set());
  // Real preset-tag aggregate (itunda Maps redesign, 2026-08-28) -- see
  // EatsReviewService.restaurantGoodPointCounts' own doc comment on the backend.
  const [goodPointCounts, setGoodPointCounts] = useState<Record<string, number> | null>(null);

  const handleToggleHelpful = async (reviewId: string) => {
    try {
      const helpful = await toggleReviewHelpful(reviewId);
      setHelpfulVoted((prev) => {
        const next = new Set(prev);
        if (helpful) next.add(reviewId); else next.delete(reviewId);
        return next;
      });
      setReviews((prev) => prev?.map((r) => (r.id === reviewId ? { ...r, helpfulCount: (r.helpfulCount ?? 0) + (helpful ? 1 : -1) } : r)) ?? null);
    } catch {
      // Real, non-critical -- a failed helpful-vote shouldn't block reading reviews.
    }
  };

  useEffect(() => {
    fetchRestaurantRating(restaurantId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
    });
    fetchRestaurantGoodPoints(restaurantId).then((r) => setGoodPointCounts(r.counts)).catch(() => {
      // Real, non-critical -- same bar as the rating fetch above.
    });
  }, [restaurantId]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && reviews === null) {
      fetchRestaurantReviews(restaurantId).then(setReviews).catch(() => setReviews([]));
    }
  };

  if (!rating || rating.count === 0) return null;
  return (
    <div>
      <button
        type="button"
        onClick={toggle}
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', padding: 0 }}
      >
        <IconStar size={14} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {/* Real preset-tag aggregate (itunda Maps redesign, 2026-08-28, direct Naver
              Map reference: "이런 점이 좋았어요") -- real counts from real submitted
              tags only, never fabricated. */}
          {goodPointCounts && Object.keys(goodPointCounts).length > 0 && (
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginBottom: '4px' }}>
              {Object.entries(goodPointCounts)
                .sort((a, b) => b[1] - a[1])
                .map(([id, count]) => (
                  <span
                    key={id}
                    style={{
                      fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, padding: '4px 10px', borderRadius: '999px',
                      color: 'var(--itunda-grey-900)', backgroundColor: 'var(--itunda-grey-100)',
                    }}
                  >
                    {EATS_GOOD_POINT_LABELS.find(([pid]) => pid === id)?.[1] ?? id} {count}
                  </span>
                ))}
            </div>
          )}
          {reviews === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet — be the first to share how it went." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.restaurantRating)}{'☆'.repeat(5 - r.restaurantRating)}</span>
                {r.restaurantComment && <span> — {r.restaurantComment}</span>}
                {/* Real review photo (itunda Eats redesign, 2026-08-28) -- see
                    EatsReview.photoUrl's own doc comment. */}
                {r.photoUrl && (
                  <img
                    src={r.photoUrl} alt="" loading="lazy"
                    style={{ display: 'block', width: '80px', height: '80px', borderRadius: 'var(--itunda-radius-md)', objectFit: 'cover', marginTop: '6px' }}
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                )}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                    ↳ Restaurant: {r.ownerReply}
                  </div>
                )}
                <div style={{ display: 'flex', gap: '10px', marginTop: '2px', alignItems: 'flex-start' }}>
                  <button
                    type="button" onClick={() => handleToggleHelpful(r.id)}
                    style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: helpfulVoted.has(r.id) ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                  >
                    👍 Helpful{r.helpfulCount ? ` (${r.helpfulCount})` : ''}
                  </button>
                  <ReportReviewButton reviewId={r.id} />
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

const EATS_REVIEW_REPORT_REASONS: { reason: EatsReviewReportReason; label: string }[] = [
  { reason: 'DEFAMATION', label: 'False or defamatory' },
  { reason: 'PERSONAL_INFO_EXPOSURE', label: 'Shares personal information' },
  { reason: 'OBSCENE_OR_VIOLENT', label: 'Obscene or violent' },
  { reason: 'UNRELATED_ABUSE', label: 'Unrelated or abusive' },
];

// Real 배달의민족 리뷰 신고하기 (report a review) -- see lib/eats.ts's own doc comment on
// reportEatsReview. Same real preset-reason-picker shape as HoodReportButton, but this
// review-specific endpoint is genuinely separate (HoodReportButton's own 4 real targets
// don't cover reviews at all).
function ReportReviewButton({ reviewId }: { reviewId: string }) {
  const [showChoices, setShowChoices] = useState(false);
  const [sending, setSending] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const send = async (reason: EatsReviewReportReason) => {
    setShowChoices(false);
    setSending(true);
    try {
      await reportEatsReview(reviewId, reason);
      setMessage('Thanks. Your report was sent for review.');
    } catch (err) {
      setMessage(err instanceof ApiError && err.code === 'REVIEW_ALREADY_REPORTED' ? 'You already reported this review.' : 'Could not send the report.');
    } finally {
      setSending(false);
    }
  };

  if (message) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: message.startsWith('Thanks') ? 'var(--itunda-green)' : 'var(--itunda-red)' }}>{message}</p>;
  }

  if (showChoices) {
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        {EATS_REVIEW_REPORT_REASONS.map(({ reason, label }) => (
          <button key={reason} className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-11-size)', padding: '4px 10px' }} onClick={() => send(reason)}>
            {label}
          </button>
        ))}
        <button style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} onClick={() => setShowChoices(false)}>Cancel</button>
      </div>
    );
  }

  return (
    <button type="button" style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }} disabled={sending} onClick={() => setShowChoices(true)}>
      {sending ? 'Reporting…' : 'Report'}
    </button>
  );
}

// Real owner-reply management (item 184) -- a restaurant owner's own reviews, with an
// inline reply form for anything not yet replied to. Lives on RestaurantOrdersView
// (the owner's own dashboard) since that's the only place this app already resolves
// "my own restaurant id" for an Eats seller.
function RestaurantReviewsManageView({ restaurantId }: { restaurantId: string }) {
  const { t } = useI18n();
  const [reviews, setReviews] = useState<EatsReview[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantReviews(restaurantId).then(setReviews).catch((err) => {
      setError(err instanceof ApiError ? err.message : t('common.loadError'));
    });
  };
  useEffect(load, [restaurantId]);

  if (error) return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>;
  if (reviews === null) return <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (reviews.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Reviews for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {reviews.map((r) => <RestaurantReviewReplyCard key={r.id} review={r} onReplied={load} />)}
      </div>
    </div>
  );
}

function RestaurantReviewReplyCard({ review, onReplied }: { review: EatsReview; onReplied: () => void }) {
  const { t } = useI18n();
  const [replying, setReplying] = useState(false);
  const [reply, setReply] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await replyToRestaurantReview(review.id, reply.trim());
      setReplying(false);
      onReplied();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="itunda-flat-section">
      <span style={{ color: '#F5A623', fontSize: 'var(--itunda-type-scale-13-size)' }}>{'★'.repeat(review.restaurantRating)}{'☆'.repeat(5 - review.restaurantRating)}</span>
      {review.restaurantComment && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', marginTop: '4px' }}>{review.restaurantComment}</p>}
      {/* Real review photo (itunda Eats redesign, 2026-08-28) -- see
          EatsReview.photoUrl's own doc comment: real end-to-end on the backend since
          2026-08-04, never rendered anywhere on web until now. */}
      {review.photoUrl && (
        <img
          src={review.photoUrl} alt="" loading="lazy"
          style={{ width: '96px', height: '96px', borderRadius: 'var(--itunda-radius-md)', objectFit: 'cover', marginTop: '8px' }}
          onError={(e) => { e.currentTarget.style.display = 'none'; }}
        />
      )}
      {review.ownerReply ? (
        <div style={{ marginTop: '8px', paddingLeft: '10px', borderLeft: '2px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
          Your reply: {review.ownerReply}
        </div>
      ) : replying ? (
        <form onSubmit={handleSubmit} style={{ marginTop: '8px', display: 'flex', gap: '8px' }}>
          <input
            type="text" placeholder="Write a reply…" value={reply} onChange={(e) => setReply(e.target.value)} required
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }}>
            {submitting ? '…' : 'Reply'}
          </button>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setReplying(true)}
          className="itunda-btn itunda-btn-secondary"
          style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 10px' }}
        >
          Reply
        </button>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function ReviewOrderCard({ order, onSubmitted }: { order: EatsOrder; onSubmitted: () => void }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [restaurantRating, setRestaurantRating] = useState(0);
  const [restaurantComment, setRestaurantComment] = useState('');
  const [riderRating, setRiderRating] = useState(0);
  const [riderComment, setRiderComment] = useState('');
  // Real optional review photo (itunda Eats redesign, 2026-08-28) -- see
  // lib/eats.ts's submitEatsReview doc comment. itunda has no upload/storage
  // pipeline, so this is a real "paste your own already-hosted photo URL" field,
  // same honest bar as Merchant.photoUrl elsewhere in this codebase.
  const [photoUrl, setPhotoUrl] = useState('');
  // Real preset-tag checklist (itunda Maps redesign, 2026-08-28, direct Naver Map
  // reference: "이런 점이 좋았어요") -- see EatsReview.goodPoints' own doc comment on
  // the backend, ported from HoodReviewForm's own exact pill-picker pattern above.
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const toggleGoodPoint = (id: string) => {
    setSelectedGoodPoints((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  };

  // Real bug fix (2026-07-26): a PICKUP order has riderId: null for its whole
  // lifecycle -- there's genuinely no rider to rate, so the rider star row is hidden
  // and never required, matching the backend's own real fix for the same order.
  const hasRider = order.fulfillmentType !== 'PICKUP';

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (restaurantRating === 0 || (hasRider && riderRating === 0)) {
      setError(hasRider ? 'Rate both the restaurant and the rider.' : 'Rate the restaurant.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitEatsReview(order.id, restaurantRating, restaurantComment, hasRider ? riderRating : null, riderComment, photoUrl, Array.from(selectedGoodPoints));
      setDone(true);
      onSubmitted();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ORDER_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Thanks for your review!</p>;
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" onClick={() => setOpen(true)}>
        Rate this order
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '8px' }}>
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>Restaurant</p>
        <StarRatingInput value={restaurantRating} onChange={setRestaurantRating} />
        <input
          type="text"
          value={restaurantComment}
          onChange={(e) => setRestaurantComment(e.target.value)}
          placeholder="How was the food? (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <input
          type="url"
          value={photoUrl}
          onChange={(e) => setPhotoUrl(e.target.value)}
          placeholder="Photo URL (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap', marginTop: '8px' }}>
          {EATS_GOOD_POINT_LABELS.map(([id, label]) => {
            const selected = selectedGoodPoints.has(id);
            return (
              <button
                key={id}
                type="button"
                onClick={() => toggleGoodPoint(id)}
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
      </div>
      {hasRider && (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '4px' }}>Rider</p>
          <StarRatingInput value={riderRating} onChange={setRiderRating} />
          <input
            type="text"
            value={riderComment}
            onChange={(e) => setRiderComment(e.target.value)}
            placeholder="How was the delivery? (optional)"
            style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
        </div>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

// Real Uber Eats post-delivery tip -- see lib/eats.ts's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py, same real gap shape as TipDriverPrompt (rides)
// this session already closed -- mirrors it directly, reusing TIP_PRESETS. A PICKUP
// order has no rider (see ReviewOrderCard's own hasRider comment above), so the
// caller only renders this for a real DELIVERY order.
function TipRiderPrompt({ orderId, onTipped }: { orderId: string; onTipped: () => void }) {
  const { t } = useI18n();
  const [amount, setAmount] = useState<number | null>(null);
  const [customAmount, setCustomAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (overrideAmount?: number) => {
    const finalAmount = overrideAmount ?? amount ?? Number(customAmount);
    if (!(finalAmount > 0)) return;
    setSubmitting(true);
    setError(null);
    try {
      await tipEatsOrderRider(orderId, finalAmount);
      onTipped();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Tip your rider</p>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '6px' }}>
        {TIP_PRESETS.map((preset) => (
          <button
            key={preset} type="button" disabled={submitting}
            onClick={() => { setAmount(preset); setCustomAmount(''); handleSubmit(preset); }}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              border: '1px solid var(--itunda-grey-200)', background: amount === preset ? 'var(--itunda-indigo)' : 'transparent',
              color: amount === preset ? 'white' : 'var(--itunda-grey-700)',
            }}
          >
            {preset.toLocaleString()}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: '6px' }}>
        <input
          type="number" placeholder="Custom amount (RWF)" value={customAmount}
          onChange={(e) => { setCustomAmount(e.target.value); setAmount(null); }}
          style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
        />
        <button
          className="itunda-btn itunda-btn-primary" disabled={submitting || !(Number(customAmount) > 0)}
          onClick={() => handleSubmit()} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 14px' }}
        >
          {submitting ? '…' : 'Send'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function AddressAutocomplete({
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
interface EatsCartLine { productId: string; quantity: number; choiceIds: string[] }

function eatsCartKey(productId: string, choiceIds: string[]): string {
  return choiceIds.length === 0 ? productId : `${productId}::${[...choiceIds].sort().join(',')}`;
}

// Real, human-readable summary of a resolved cart line's selected options -- mirrors
// the backend's own EatsOrderService.buildSelectedOptionsJson, but purely for display;
// pricing always comes from the real menu item + real choice deltas, never this string.
function eatsOptionsSummary(item: MenuItem, choiceIds: string[]): string {
  if (choiceIds.length === 0) return '';
  const names = (item.optionGroups ?? [])
    .flatMap((g) => g.choices)
    .filter((c) => choiceIds.includes(c.id))
    .map((c) => c.name);
  return names.length ? ` (${names.join(', ')})` : '';
}

function eatsLineUnitPrice(item: MenuItem, choiceIds: string[]): number {
  const delta = (item.optionGroups ?? [])
    .flatMap((g) => g.choices)
    .filter((c) => choiceIds.includes(c.id))
    .reduce((sum, c) => sum + c.priceDelta, 0);
  return item.price + delta;
}

function MenuView({
  restaurant, onBack, onOrderPlaced, initialCart,
}: {
  restaurant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: EatsOrder) => void; initialCart?: Record<string, number>;
}) {
  const { t } = useI18n();
  const [menu, setMenu] = useState<{ businessName: string; products: MenuItem[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  // Real device binding step-up (2026-07-21) -- Eats checkout was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic error, same fix already applied to Transfer/Savings.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  const [cart, setCart] = useState<Record<string, EatsCartLine>>(
    () => Object.fromEntries(Object.entries(initialCart ?? {}).map(([productId, quantity]) => [productId, { productId, quantity, choiceIds: [] }])),
  );
  // Real menu-options selection UI (2026-07-21, v1: required single-select only) -- see
  // MenuOptionGroup.kt's own doc comment on the backend for the full account. Only one
  // item's option panel is expanded at a time, matching this file's own established
  // "inline-card-replaces-trigger" convention (no modal-overlay pattern exists anywhere
  // in this codebase).
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  // Real optional/multi-select menu option groups (itunda Eats redesign, 2026-08-28)
  // -- the backend has always supported 4 real group combinations (required x
  // multiSelect, see MenuOptionGroup.kt's own doc comment), but this UI only ever
  // rendered required-single-select radios. groupId -> the real selected choiceIds
  // for that group (0 or 1 for a single-select group, 0+ for multiSelect) -- the
  // underlying eatsCartKey/eatsLineUnitPrice/eatsOptionsSummary helpers already
  // operate on a plain choiceIds[] with no single-choice assumption, so this is a
  // real UI-layer fix, not a pricing/cart-model change. DineInMenuView has its own
  // separate, still-radio-only copy of this same pattern -- deliberately not
  // touched here, out of this pass's real scope (browse/menu/checkout, not
  // Dine-in's own structure).
  const [pendingChoices, setPendingChoices] = useState<Record<string, string[]>>({});
  const [address, setAddress] = useState('');
  const [addressCoords, setAddressCoords] = useState<{ latitude: number; longitude: number } | null>(null);
  const [deliveryNotes, setDeliveryNotes] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);
  // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- the backend has
  // supported this since 2026-07-26, but no client anywhere let a buyer choose it.
  // DELIVERY is the default, matching every existing order's real behavior.
  const [fulfillmentType, setFulfillmentType] = useState<'DELIVERY' | 'PICKUP'>('DELIVERY');

  const load = () => {
    setError(null);
    fetchMenu(restaurant.merchantId)
      .then((r) => {
        setMenu({ businessName: r.merchant.businessName, products: r.products });
        // Real reorder-cart sanitization (2026-07-21) -- a reordered past order's cart is
        // rebuilt from plain product ids with no option selections (see OrderFoodView's
        // handleReorder, unchanged). If a product now genuinely requires an option
        // selection, that bare line can never check out -- drop it rather than let
        // checkout silently fail, same "discontinued item silently dropped" precedent
        // handleReorder itself already established for a menu item that's gone entirely.
        setCart((prev) => {
          const next = { ...prev };
          for (const [key, line] of Object.entries(prev)) {
            const product = r.products.find((p) => p.id === line.productId);
            if (product && (product.optionGroups?.length ?? 0) > 0 && line.choiceIds.length === 0) {
              delete next[key];
            }
          }
          return next;
        });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, line]) => line.quantity > 0);
  const cartCount = cartItems.reduce((sum, [, line]) => sum + line.quantity, 0);
  // Real cart-bar subtotal + savings breakdown (itunda Eats redesign, 2026-08-28) --
  // the fixed bottom bar previously only showed the item count, deferring every real
  // number to the checkout screen. Purely a client-side sum over cart data already
  // fetched -- no new backend call. cartOriginalSubtotal only differs from
  // cartSubtotal when a real originalPrice is set on at least one cart line.
  const cartSubtotal = cartItems.reduce((sum, [, line]) => {
    const item = menu?.products.find((p) => p.id === line.productId);
    return item ? sum + eatsLineUnitPrice(item, line.choiceIds) * line.quantity : sum;
  }, 0);
  const cartOriginalSubtotal = cartItems.reduce((sum, [, line]) => {
    const item = menu?.products.find((p) => p.id === line.productId);
    if (!item) return sum;
    return sum + (item.originalPrice ?? item.price) * line.quantity;
  }, 0);

  // For a no-option item only -- the original single-stepper interaction, completely
  // unchanged for the overwhelming majority of menu items that have no option groups.
  const setSimpleQty = (productId: string, qty: number) => {
    const key = eatsCartKey(productId, []);
    setCart((c) => ({ ...c, [key]: { productId, quantity: Math.max(0, qty), choiceIds: [] } }));
  };

  const setLineQty = (key: string, line: EatsCartLine, qty: number) => {
    setCart((c) => ({ ...c, [key]: { ...line, quantity: Math.max(0, qty) } }));
  };

  const toggleExpand = (productId: string) => {
    setPendingChoices({});
    setExpandedProductId((current) => (current === productId ? null : productId));
  };

  const addConfiguredToCart = (item: MenuItem) => {
    const groups = item.optionGroups ?? [];
    // Real optional/multi-select support (2026-08-28) -- a required group needs at
    // least one real selected choice; an optional group is valid with zero. A
    // multiSelect group may contribute more than one choiceId, a single-select
    // group at most one -- both flow through the same flatMap.
    if (groups.some((g) => g.required && (pendingChoices[g.id]?.length ?? 0) === 0)) return;
    const choiceIds = groups.flatMap((g) => pendingChoices[g.id] ?? []);
    const key = eatsCartKey(item.id, choiceIds);
    setCart((c) => ({ ...c, [key]: { productId: item.id, quantity: (c[key]?.quantity ?? 0) + 1, choiceIds } }));
    setPendingChoices({});
    setExpandedProductId(null);
  };

  const handlePlaceOrder = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!menu) return;
    setPlacing(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      const items = cartItems.map(([, line]) => ({
        menuItemId: line.productId, quantity: line.quantity,
        selectedChoiceIds: line.choiceIds.length ? line.choiceIds : undefined,
      }));
      const result = await placeEatsOrder(
        restaurant.merchantId, items, fulfillmentType === 'PICKUP' ? '' : address.trim(),
        fulfillmentType === 'PICKUP' ? undefined : addressCoords?.latitude,
        fulfillmentType === 'PICKUP' ? undefined : addressCoords?.longitude,
        deliveryNotes.trim() || undefined, fulfillmentType,
      );
      onOrderPlaced(result.order);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setPlacing(false);
    }
  };

  if (needsDeviceVerification) {
    // Real fix (2026-08-10) -- see TransferFlow's own identical fix for the full
    // account. handlePlaceOrder resets needsDeviceVerification itself.
    // Real fix (2026-08-24, flat-design sweep): dropped the itunda-card wrapper --
    // DeviceStepUpPrompt already renders its own inset grey background, matching
    // every other real call site in this file (none of them wrap it in a card).
    return <DeviceStepUpPrompt onVerified={() => handlePlaceOrder()} onCancel={() => setNeedsDeviceVerification(false)} />;
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (menu === null) {
    return <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to menu">
            <IconBack size={20} />
          </button>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Checkout</h3>
        </div>
        {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this form
            IS the whole checkout screen's content (docs/UI_UX_GUIDELINES.md §10). */}
        <form onSubmit={handlePlaceOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-14-size)' }}>
                <span>{item.name}{eatsOptionsSummary(item, line.choiceIds)} x{line.quantity}</span>
                <span>{(unitPrice * line.quantity).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <div style={{ display: 'flex', gap: '4px', padding: '4px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
            {(['DELIVERY', 'PICKUP'] as const).map((ft) => (
              <button
                key={ft}
                type="button"
                onClick={() => setFulfillmentType(ft)}
                style={{
                  flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                  color: fulfillmentType === ft ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                  backgroundColor: fulfillmentType === ft ? 'var(--itunda-indigo)' : 'transparent',
                }}
              >
                {ft === 'DELIVERY' ? 'Delivery' : 'Pickup'}
              </button>
            ))}
          </div>
          {fulfillmentType === 'PICKUP' ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
              No delivery fee -- collect your order at {menu.businessName} once it's ready.
            </p>
          ) : (
            <>
              <AddressAutocomplete
                value={address}
                onChangeText={(text) => { setAddress(text); setAddressCoords(null); }}
                onSelectSuggestion={(s) => { setAddress(s.displayName); setAddressCoords({ latitude: s.latitude, longitude: s.longitude }); }}
              />
              {addressCoords && (
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)' }}>Pinned -- real distance-based delivery fee applies</p>
              )}
            </>
          )}
          <textarea
            value={deliveryNotes}
            onChange={(e) => setDeliveryNotes(e.target.value.slice(0, 500))}
            placeholder={fulfillmentType === 'PICKUP' ? 'Pickup notes (optional)' : 'Delivery notes (optional) -- e.g. Leave at the gate, call on arrival'}
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={placing || (fulfillmentType === 'DELIVERY' && !address.trim())}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to restaurants">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      <div style={{ marginBottom: '12px' }}>
        <RestaurantRatingBadge restaurantId={restaurant.merchantId} />
      </div>
      {menu.products.length === 0 ? (
        <EmptyState message="This restaurant hasn't added menu items yet — check back soon." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: cartCount > 0 ? '80px' : 0 }}>
          {menu.products.map((item) => {
            const groups = item.optionGroups ?? [];
            const hasOptions = groups.length > 0;
            const simpleKey = eatsCartKey(item.id, []);
            const simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0);
            const isExpanded = expandedProductId === item.id;
            const allGroupsChosen = groups.every((g) => !g.required || (pendingChoices[g.id]?.length ?? 0) > 0);
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card, reusing
            // itunda-flat-section for this real Baemin-style flat menu-item list.
            return (
              <div key={item.id} className="itunda-flat-section">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{item.name}</p>
                    {/* Real per-dish discount badge (itunda Eats redesign, 2026-08-28) --
                        see lib/eats.ts's own MenuItem.discountPercent doc comment: the
                        shared catalog endpoint has always returned this, Eats' own menu
                        never rendered it. */}
                    <ProductPriceBlock price={item.price} originalPrice={item.originalPrice} discountPercent={item.discountPercent} />
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: item.soldOut ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                      {hasOptions ? 'Options required' : ''}
                      {item.soldOut ? <> · <SoldOutGlyph size={12} /> Sold out</> : ''}
                    </p>
                    {item.isBestSeller && <ShopBestSellerBadge />}
                  </div>
                  {/* Real Baemin CEO app/DoorDash-style "86" enforcement (2026-08-16) --
                      see MenuItem.soldOut's own doc comment. Shown, not hidden -- the
                      item stays fully visible on the menu, just can't be added right
                      now, same discipline Section 101's pause-orders badge established. */}
                  {item.soldOut ? null : hasOptions ? (
                    <button onClick={() => toggleExpand(item.id)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}>
                      {isExpanded ? 'Close' : 'Choose options'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {!item.soldOut && hasOptions && isExpanded && (
                  <div style={{ marginTop: '14px', paddingTop: '14px', borderTop: '1px solid var(--itunda-grey-200)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {groups.map((group) => {
                      const selected = pendingChoices[group.id] ?? [];
                      // Real optional/multi-select labels (2026-08-28) -- honest about
                      // what this group actually requires, matching its own real
                      // required/multiSelect flags rather than always claiming "choose 1".
                      const groupHint = group.multiSelect
                        ? (group.required ? 'choose at least 1' : 'choose any (optional)')
                        : (group.required ? 'choose 1' : 'choose 1 (optional)');
                      return (
                        <div key={group.id}>
                          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '6px' }}>
                            {group.name} <span style={{ color: 'var(--itunda-grey-400)', fontWeight: 400 }}>· {groupHint}</span>
                          </p>
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                            {group.choices.map((choice) => (
                              <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)', cursor: 'pointer' }}>
                                <input
                                  type={group.multiSelect ? 'checkbox' : 'radio'}
                                  name={`eats-option-group-${group.id}`}
                                  checked={selected.includes(choice.id)}
                                  onChange={() => setPendingChoices((p) => {
                                    if (group.multiSelect) {
                                      const next = selected.includes(choice.id) ? selected.filter((id) => id !== choice.id) : [...selected, choice.id];
                                      return { ...p, [group.id]: next };
                                    }
                                    // Single-select: re-clicking the current choice clears it
                                    // when the group is optional (a real "none of these"),
                                    // never for a required group.
                                    const next = selected[0] === choice.id && !group.required ? [] : [choice.id];
                                    return { ...p, [group.id]: next };
                                  })}
                                />
                                {choice.name}{choice.priceDelta > 0 ? ` (+${choice.priceDelta.toLocaleString()} RWF)` : ''}
                              </label>
                            ))}
                          </div>
                        </div>
                      );
                    })}
                    <button
                      type="button"
                      className="itunda-btn itunda-btn-primary"
                      disabled={!allGroupsChosen}
                      onClick={() => addConfiguredToCart(item)}
                    >
                      Add to cart
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      {cartCount > 0 && (
        <div className="itunda-card" style={{ marginBottom: '80px', marginTop: '-2px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '10px' }}>Your cart</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {cartItems.map(([key, line]) => {
              const item = menu.products.find((p) => p.id === line.productId);
              if (!item) return null;
              return (
                <div key={key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{item.name}{eatsOptionsSummary(item, line.choiceIds)}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button onClick={() => setLineQty(key, line, line.quantity - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }}>−</button>
                    <span style={{ minWidth: '14px', textAlign: 'center', fontWeight: 700, fontSize: 'var(--itunda-type-scale-13-size)' }}>{line.quantity}</span>
                    <button onClick={() => setLineQty(key, line, line.quantity + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '4px 10px' }}>+</button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
      {/* Real "frequently ordered together" cross-sell (itunda Eats redesign,
          2026-08-28) -- keyed off the most recently added cart line, matching the
          reference's own per-item placement as closely as this menu's flat (no
          per-item detail page) layout allows. See EatsFrequentlyOrderedWith.tsx's
          own doc comment. */}
      {cartItems.length > 0 && (
        <EatsFrequentlyOrderedWith
          productId={cartItems[cartItems.length - 1][1].productId}
          // Real, honest limitation: a "quick add" straight from this rail always
          // adds a no-option cart line -- if the real co-purchased dish actually has
          // real required option groups, the existing per-item "Choose options" flow
          // in the main list below is how a buyer configures it; this cross-sell
          // rail intentionally doesn't duplicate that UI for a secondary rail.
          onAdd={(item) => setSimpleQty(item.id, (cart[eatsCartKey(item.id, [])]?.quantity ?? 0) + 1)}
        />
      )}
      {cartCount > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto', display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '14px 20px' }}
          onClick={() => setShowCheckout(true)}
        >
          <span>Checkout ({cartCount} item{cartCount === 1 ? '' : 's'})</span>
          {/* Real computed subtotal + savings (2026-08-28) -- see cartSubtotal's own
              doc comment just above. */}
          <span style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
            {cartOriginalSubtotal > cartSubtotal && (
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', textDecoration: 'line-through', opacity: 0.7 }}>{cartOriginalSubtotal.toLocaleString()} RWF</span>
            )}
            <span>{cartSubtotal.toLocaleString()} RWF</span>
          </span>
        </button>
      )}
    </div>
  );
}

function MyEatsOrdersView({ onReorder, reorderingId, restaurants, onMessageSeller }: { onReorder: (order: EatsOrder) => void; reorderingId: string | null; restaurants: ShoppingMerchant[] | null; onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);
  const [messagingOrderId, setMessagingOrderId] = useState<string | null>(null);
  // Real optimistic-hide for TipRiderPrompt -- same pattern reviewedTripIds/
  // tippedTripIds already established for the ride equivalent this session.
  const [tippedOrderIds, setTippedOrderIds] = useState<Set<string>>(new Set());

  const handleMessageRestaurant = async (orderId: string) => {
    setMessagingOrderId(orderId);
    setError(null);
    try {
      const conversation = await contactRestaurant(orderId);
      onMessageSeller(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setMessagingOrderId(null);
    }
  };

  const load = () => {
    setError(null);
    fetchMyEatsOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    load();
    // Real poll for order-tracking status, same 4s cadence as the Messages tab's
    // poll-based delivery -- no live push transport exists here either.
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelEatsOrder(orderId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return <EmptyState message="No orders yet — order from a nearby restaurant and it'll show up here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <EatsOrderCard
          key={o.id}
          order={o}
          restaurant={restaurants?.find((r) => r.merchantId === o.restaurantId)}
          onMessageRestaurant={messagingOrderId === o.id ? undefined : () => handleMessageRestaurant(o.id)}
          action={
            o.status === 'PLACED' ? (
              <button className="itunda-btn itunda-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
                {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            ) : o.status === 'DELIVERED' ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <ReviewOrderCard order={o} onSubmitted={load} />
                {o.riderId && !o.tipAmount && !tippedOrderIds.has(o.id) && (
                  <TipRiderPrompt orderId={o.id} onTipped={() => setTippedOrderIds((prev) => new Set(prev).add(o.id))} />
                )}
                <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                  {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
                </button>
              </div>
            ) : o.status === 'CANCELLED' ? (
              <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
              </button>
            ) : undefined
          }
        />
      ))}
    </div>
  );
}

// Real shared browse-header component (2026-07-21) -- extracted from Eats'
// OrderFoodView (the only place this pattern previously existed) so Shop's
// merchant browse can reuse the identical search+chips interaction instead of a
// second bespoke implementation. Callers own their own debounce/state; this just
// renders the field + optional chip row.
function SearchAndCategoryChips({
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

function OrderFoodView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'FAVORITES' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
  // even when that restaurant has been filtered out of the currently-browsed list.
  const [allRestaurants, setAllRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  // Real Coupang Eats-style sort picker (2026-08-19, replacing the single favorites-only
  // toggle) -- see fetchRestaurants' own RestaurantSortMode doc comment for the real
  // backend-supported modes. 'distance'/'delivery_time' need a real buyer location;
  // 'favorites'/'rating' don't. Only these 4 real modes are offered -- no fabricated
  // "Recommended"/"Newest" pill, since nothing on the backend actually sorts by either.
  const [sortMode, setSortMode] = useState<RestaurantSortMode | null>(null);
  const [buyerLocation, setBuyerLocation] = useState<{ lat: number; lng: number } | null>(null);
  // Real bug caught by live click-through (2026-08-19): an earlier version of this used a
  // plain `locatingForSort: boolean` with no record of WHICH mode triggered it, so both
  // 'distance' and 'delivery_time' pills showed "Locating..." simultaneously regardless of
  // which one was actually clicked. Tracking the specific pending mode fixes this.
  const [pendingSortMode, setPendingSortMode] = useState<RestaurantSortMode | null>(null);
  const [sortLocationError, setSortLocationError] = useState<string | null>(null);

  const selectSortMode = (mode: RestaurantSortMode | null) => {
    setSortLocationError(null);
    if (mode === null || mode === 'favorites' || mode === 'rating' || buyerLocation) {
      setSortMode(mode);
      return;
    }
    // 'distance'/'delivery_time' need a real position first -- same geolocation pattern
    // ListingCard's own handleShowDirections already uses.
    if (!navigator.geolocation) {
      setSortLocationError('This browser does not support real location access.');
      return;
    }
    setPendingSortMode(mode);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setPendingSortMode(null);
        setBuyerLocation({ lat: position.coords.latitude, lng: position.coords.longitude });
        setSortMode(mode);
      },
      () => {
        setPendingSortMode(null);
        setSortLocationError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<EatsOrder | null>(null);
  const [reorderCart, setReorderCart] = useState<Record<string, number> | null>(null);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
  // Real "recently viewed restaurants" rail (2026-08-23) -- ported from Android's
  // identical real feature (RecentlyViewedRestaurantsStore.kt), never shipped to web
  // before now -- see lib/recentlyViewed.ts's own doc comment. A plain effect on
  // `selected` (rather than wrapping every one of this view's several real
  // restaurant-opening call sites -- the browse list, favorites, dish grid, Reorder,
  // deep links) covers every real entry point uniformly.
  const [recentlyViewedRestaurants, setRecentlyViewedRestaurants] = useState(recentlyViewedRestaurantsStore.getAll());
  useEffect(() => {
    if (!selected) return;
    setRecentlyViewedRestaurants(
      recentlyViewedRestaurantsStore.add({ id: selected.merchantId, businessName: selected.businessName, category: selected.category, photoUrl: selected.photoUrl }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selected?.merchantId]);
  // Real bookmarked/favorited restaurants (2026-07-19) -- a set of restaurant ids for a
  // fast star-toggle lookup on each browse card.
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);

  const loadFavorites = () => {
    fetchMyFavoriteRestaurants().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.restaurantId)))).catch(() => {});
  };

  useEffect(() => {
    fetchRestaurants().then(setAllRestaurants).catch(() => {});
    fetchRestaurantCategories().then(setCategories).catch(() => {});
    loadFavorites();
  }, []);

  const toggleFavorite = async (restaurantId: string) => {
    setFavoritingId(restaurantId);
    try {
      if (favoriteIds.has(restaurantId)) {
        await removeFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(restaurantId); return next; });
      } else {
        await addFavoriteRestaurant(restaurantId);
        setFavoriteIds((prev) => new Set(prev).add(restaurantId));
      }
    } catch {
      // Real, non-critical -- a failed toggle just leaves the star as-is; the user can retry.
    } finally {
      setFavoritingId(null);
    }
  };

  // Real category/search filter (2026-07-19), debounced so a search box doesn't
  // re-fetch on every keystroke.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedSearch(searchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [searchInput]);

  const load = () => {
    setError(null);
    fetchRestaurants(selectedCategory ?? undefined, debouncedSearch || undefined, buyerLocation?.lat, buyerLocation?.lng, sortMode ?? undefined)
      .then(setRestaurants)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [selectedCategory, debouncedSearch, sortMode, buyerLocation]);

  // Real "Reorder" (2026-07-19): re-populates a fresh cart from a real past order's
  // real items, filtered to whatever's still real and active on the restaurant's
  // current menu -- a discontinued item is silently dropped rather than added as a
  // phantom line the buyer can't actually check out with.
  const handleReorder = async (order: EatsOrder) => {
    setReorderingId(order.id);
    setReorderError(null);
    try {
      const restaurant = allRestaurants?.find((r) => r.merchantId === order.restaurantId);
      if (!restaurant) {
        setReorderError('This restaurant is no longer available.');
        return;
      }
      const [{ items }, menu] = await Promise.all([fetchEatsOrder(order.id), fetchMenu(order.restaurantId)]);
      const activeProductIds = new Set(menu.products.filter((p) => p.active).map((p) => p.id));
      const cart: Record<string, number> = {};
      items.forEach((item) => {
        if (activeProductIds.has(item.productId)) {
          cart[item.productId] = (cart[item.productId] ?? 0) + item.quantity;
        }
      });
      if (Object.keys(cart).length === 0) {
        setReorderError('None of the items from that order are on the menu anymore.');
        return;
      }
      setReorderCart(cart);
      setSelected(restaurant);
      setView('BROWSE');
    } catch (err) {
      setReorderError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setReorderingId(null);
    }
  };

  if (confirmed) {
    // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this IS
    // the whole confirmation screen's content (docs/UI_UX_GUIDELINES.md §10).
    return (
      <div style={{ textAlign: 'center', padding: '28px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        {confirmed.promotionDiscount > 0 && (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginBottom: '4px' }}>
            {confirmed.promotionDiscount.toLocaleString()} RWF off, on us
          </p>
        )}
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>Delivering to {confirmed.deliveryAddress}</p>
        <button
          className="itunda-btn itunda-btn-secondary"
          onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}
        >
          Track order
        </button>
      </div>
    );
  }

  if (selected) {
    return (
      <MenuView
        restaurant={selected}
        onBack={() => { setSelected(null); setReorderCart(null); }}
        onOrderPlaced={setConfirmed}
        initialCart={reorderCart ?? undefined}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'FAVORITES', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : v === 'FAVORITES' ? 'Favorites' : 'My orders'}
          </button>
        ))}
      </div>

      {view === 'ORDERS' ? (
        <>
          {reorderError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{reorderError}</p>}
          <MyEatsOrdersView onReorder={handleReorder} reorderingId={reorderingId} restaurants={allRestaurants} onMessageSeller={onMessageSeller} />
        </>
      ) : view === 'FAVORITES' ? (
        <FavoriteRestaurantsView
          onOpen={(r) => setSelected({ merchantId: r.restaurantId, businessName: r.businessName, category: r.category, cashbackRate: '1%' })}
          onChanged={loadFavorites}
        />
      ) : (
        <>
          <SearchAndCategoryChips
            searchInput={searchInput}
            onSearchChange={setSearchInput}
            placeholder="Search restaurants"
            categories={categories}
            selectedCategory={selectedCategory}
            onSelectCategory={setSelectedCategory}
          />
          {/* Real Coupang Eats-style sort picker (2026-08-19) -- see selectSortMode's
              own doc comment. Only real backend-supported modes, no fabricated
              "Recommended"/"Newest" pill. discount/min_order added 2026-08-28 (itunda
              Eats redesign) -- adapts the real Coupang Eats reference's own quick-
              filter chip row (최대할인/최소주문낮은매장) onto itunda's own real
              per-merchant signals, see ShoppingMerchantBrowseService.browse's own doc
              comment on both real sort modes. Neither needs a real buyer location. */}
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '4px', paddingBottom: '2px' }}>
            {([
              { mode: null, label: 'Default' },
              { mode: 'distance' as const, label: pendingSortMode === 'distance' ? '📍 Locating…' : '📍 Nearest' },
              { mode: 'delivery_time' as const, label: pendingSortMode === 'delivery_time' ? '⏱ Locating…' : '⏱ Fastest delivery' },
              { mode: 'rating' as const, label: '⭐ Highest rated' },
              { mode: 'favorites' as const, label: '❤️ Most favorited' },
              { mode: 'discount' as const, label: '🔥 Max discount' },
              { mode: 'min_order' as const, label: '💸 Low minimum order' },
            ]).map(({ mode, label }) => (
              <button
                key={label}
                className="itunda-btn itunda-btn-secondary"
                disabled={pendingSortMode !== null}
                style={{
                  whiteSpace: 'nowrap', padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)',
                  backgroundColor: sortMode === mode ? 'var(--itunda-indigo-light)' : undefined,
                  color: sortMode === mode ? 'var(--itunda-indigo)' : undefined,
                }}
                onClick={() => selectSortMode(mode)}
              >
                {label}
              </button>
            ))}
          </div>
          {sortLocationError && (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{sortLocationError}</p>
          )}
          {/* Real dish-discovery rails (itunda Eats redesign, 2026-08-28) -- see
              EatsDishRails.tsx's own doc comment. Hidden once filtering starts, same
              "merchandising above the raw list" discipline recently-viewed already
              establishes just below. */}
          {!selectedCategory && !debouncedSearch && (
            <>
              <RecommendedDishesRail onOpenRestaurant={setSelected} />
              <PopularDishesRail onOpenRestaurant={setSelected} />
              <EatsNearbyAdsRail onOpenRestaurant={setSelected} />
            </>
          )}
          {/* Real "recently viewed restaurants" rail -- see lib/recentlyViewed.ts's own
              doc comment. Hidden once the user starts filtering, same "merchandising
              above the raw list, gone once actively searching" discipline the Shop
              rails below already establish. */}
          {!selectedCategory && !debouncedSearch && recentlyViewedRestaurants.length > 0 && (
            <div style={{ marginBottom: '16px' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>🕒 Recently viewed</p>
              <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
                {recentlyViewedRestaurants.map((rv) => (
                  <button
                    key={rv.id}
                    onClick={() => setSelected({ merchantId: rv.id, businessName: rv.businessName, category: rv.category ?? null, cashbackRate: '1%' })}
                    className="itunda-card"
                    style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '130px', flexShrink: 0, gap: '4px' }}
                  >
                    <ProductImageThumb imageUrl={rv.photoUrl} size={100} />
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{rv.businessName}</p>
                    {rv.category && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{rv.category}</p>}
                  </button>
                ))}
              </div>
            </div>
          )}
          {error ? (
            <ErrorCard message={error} onRetry={load} />
          ) : restaurants === null ? (
            <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />
          ) : restaurants.length === 0 ? (
            // Real copy-voice fix (item 244, round 5 of the empty-state pass,
            // ported from the same-day Android/iOS fix): "registered yet" is
            // honest about whose gap this is -- no restaurant has joined yet,
            // not something the reader is missing a step on.
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a
            // plain one-line empty-state message.
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
              {selectedCategory || debouncedSearch ? 'No restaurants match your search — try a different category or search term.' : 'No restaurants registered yet — check back once restaurants in your area join itunda Eats.'}
            </p>
          ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {restaurants.map((r) => (
            <div
              key={r.merchantId}
              role="button"
              tabIndex={0}
              onClick={() => setSelected(r)}
              onKeyDown={(e) => { if (e.key === 'Enter') setSelected(r); }}
              className="itunda-card"
              style={{ padding: 0, overflow: 'hidden', textAlign: 'left', width: '100%', cursor: 'pointer' }}
            >
              {/* Real photo-forward card rework (2026-08-19) -- real Coupang Eats/
                  배달의민족 both use a card UI that emphasizes food/photo over a
                  text-dense row (sourced: "카드 뷰 형태의 UI를 사용해 매장보다는
                  '음식'을 강조하는 디자인"). itunda has no per-dish photo in its real
                  data model (MenuItem carries no photo field) -- honestly uses the
                  merchant's own real photoUrl as the card image rather than
                  fabricating a per-dish one, matching the real STRUCTURAL pattern
                  (large photo-topped card) without overclaiming dish-level detail
                  that doesn't exist. */}
              <div style={{ position: 'relative', width: '100%', height: '140px', backgroundColor: 'var(--itunda-indigo-light)' }}>
                {r.photoUrl ? (
                  <img
                    src={r.photoUrl} alt=""
                    style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                    onError={(e) => { e.currentTarget.style.display = 'none'; }}
                  />
                ) : (
                  <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <Utensils size={32} color="var(--itunda-indigo)" />
                  </div>
                )}
                {r.isAcceptingOrders === false && (
                  <span style={{ position: 'absolute', top: '10px', left: '10px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, color: 'var(--itunda-white)', backgroundColor: 'rgba(0,0,0,0.6)', padding: '3px 9px', borderRadius: '99px' }}>
                    ⏸ Temporarily paused
                  </span>
                )}
                {/* Real Coupang 와우(WOW)-style per-restaurant membership badge
                    (itunda Eats redesign, 2026-08-28) -- see
                    ShoppingMerchant.participatesInEatsMembership's own doc comment.
                    A real, merchant-opted-in flag, never shown on every card. */}
                {r.participatesInEatsMembership && (
                  <span style={{ position: 'absolute', top: '10px', left: r.isAcceptingOrders === false ? '124px' : '10px', fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-white)', backgroundColor: 'var(--itunda-indigo)', padding: '3px 9px', borderRadius: '99px' }}>
                    Member — free delivery
                  </span>
                )}
                {/* Real gap found live (2026-08-10), docs/DESIGN_REFERENCES.md Section 7's
                    own "four separate bespoke favorite implementations" note: this was a
                    hand-rolled Lucide Heart button, distinct from the shared
                    WishlistButton every other favorite/heart affordance on this screen
                    already uses. Unified onto the same shared component; overlaid on the
                    photo's corner, matching real Coupang Eats/Baemin's own card
                    convention. */}
                <div onClick={(e) => e.stopPropagation()} style={{ position: 'absolute', top: '8px', right: '8px', backgroundColor: 'rgba(0,0,0,0.35)', borderRadius: '999px' }}>
                  <WishlistButton
                    favorited={favoriteIds.has(r.merchantId)}
                    busy={favoritingId === r.merchantId}
                    onToggle={() => toggleFavorite(r.merchantId)}
                  />
                </div>
              </div>
              <div style={{ padding: '14px 16px' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>
                  {r.businessName}
                </p>
                {/* Real browse-card enrichment (2026-07-21) -- rating/reviewCount/distance/
                    delivery-time estimate/min order, closing docs/DESIGN_REFERENCES.md's
                    Eats recommendations #1/#2. Every clause is conditionally rendered on
                    real data being present -- never a fabricated placeholder. */}
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                  {r.category && <span>{r.category}</span>}
                  {r.rating != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                      <IconStar size={11} color="#F5A623" fill="#F5A623" /> {r.rating.toFixed(1)} ({r.reviewCount})
                    </span>
                  )}
                  {/* Real Baemin 찜 (favorites) count (2026-08-16) -- see
                      ShoppingController.getEligibleMerchants's own doc comment. */}
                  {!!r.favoriteCount && r.favoriteCount > 0 && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <HeartFilled size={11} /> {r.favoriteCount.toLocaleString()}</span>}
                  {r.distanceKm != null && <span>· {r.distanceKm.toFixed(1)} km</span>}
                  {r.deliveryTimeMinutes != null && <span>· ~{r.deliveryTimeMinutes} min</span>}
                  {/* Real Uber Eats-style "busy kitchen" delay explanation (2026-08-16) --
                      see ShoppingController.getEligibleMerchants's own doc comment.
                      deliveryTimeMinutes above already includes the real delay bump when
                      this is true; this badge is why it's longer than usual, not a
                      separate/contradictory number. */}
                  {r.isBusy && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <FlameGlyph size={12} /> Busy, delivery may take longer</span>}
                  {/* Real Baemin CEO app 휴무일 설정 (recurring weekly closed-day
                      schedule) (2026-08-16) -- see Merchant.isClosedToday's own doc
                      comment. */}
                  {r.closedToday && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <SoldOutGlyph size={12} /> Closed today</span>}
                  {r.minOrderAmount != null && <span>· Min {r.minOrderAmount.toLocaleString()} RWF</span>}
                  {!r.category && r.rating == null && r.distanceKm == null && <span>Real menu, real delivery</span>}
                </p>
                {/* Real "Discount" badge (itunda Eats redesign, 2026-08-28) -- see
                    ShoppingMerchant.maxDiscountPercent's own doc comment: the real,
                    currently-highest discount among this restaurant's own active
                    menu, never a fabricated store-wide promo. */}
                {r.maxDiscountPercent != null && r.maxDiscountPercent > 0 && (
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-red)', marginTop: '2px' }}>
                    Up to {r.maxDiscountPercent}% off
                  </p>
                )}
              </div>
            </div>
          ))}
        </div>
          )}
        </>
      )}
    </div>
  );
}

// Real bookmarked/favorited restaurants (2026-07-19) -- self-contained, mirroring
// MyEatsOrdersView's own load/local-state pattern; onChanged resyncs OrderFoodView's
// favoriteIds set so the Browse tab's stars stay correct after an unfavorite here.
// Real Baemin-style 찜 리스트 공유하기 (share favorites list, 2026-08-16) -- DIRECT-only
// (unlike ForwardPickerModal's DIRECT+GROUP picker), matching backend
// EatsFavoriteService.shareFavoritesToConversation's own 1:1-conversation-only
// capability (built on MessagingService, not GroupMessagingService).
function ShareFavoritesModal({
  onShare, onClose, title = 'Share favorites to…',
}: { onShare: (conversationId: string) => void; onClose: () => void; title?: string }) {
  const [conversations, setConversations] = useState<ConversationSummary[] | null>(null);

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
          <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
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

function FavoriteRestaurantsView({ onOpen, onChanged }: { onOpen: (favorite: FavoriteRestaurant) => void; onChanged: () => void }) {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteRestaurant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [showShareModal, setShowShareModal] = useState(false);
  const [shareError, setShareError] = useState<string | null>(null);
  const [shared, setShared] = useState(false);

  const load = () => {
    setError(null);
    fetchMyFavoriteRestaurants().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, []);

  const handleShare = async (conversationId: string) => {
    setShowShareModal(false);
    setShareError(null);
    try {
      await shareFavoritesToConversation(conversationId);
      setShared(true);
      setTimeout(() => setShared(false), 3000);
    } catch (err) {
      setShareError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleRemove = async (restaurantId: string) => {
    setRemovingId(restaurantId);
    try {
      await removeFavoriteRestaurant(restaurantId);
      setFavorites((prev) => prev?.filter((f) => f.restaurantId !== restaurantId) ?? prev);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) {
    return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }
  if (favorites.length === 0) {
    return <EmptyState message="No favorite restaurants yet. Tap the heart on a restaurant to save it here." />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'flex-end', alignItems: 'center', gap: '10px' }}>
        {shared && <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green-600, #16a34a)' }}>Shared!</span>}
        <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', display: 'inline-flex', alignItems: 'center', gap: '6px' }} onClick={() => setShowShareModal(true)}>
          <HeartFilled size={14} /> Share favorites
        </button>
      </div>
      {shareError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{shareError}</p>}
      {showShareModal && <ShareFavoritesModal onShare={handleShare} onClose={() => setShowShareModal(false)} />}
      {favorites.map((f) => (
        <div
          key={f.restaurantId}
          role="button"
          tabIndex={0}
          onClick={() => onOpen(f)}
          onKeyDown={(e) => { if (e.key === 'Enter') onOpen(f); }}
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '14px 0', textAlign: 'left', width: '100%', cursor: 'pointer' }}
        >
          <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Utensils size={20} color="var(--itunda-indigo)" />
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{f.businessName}</p>
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{f.category ? `${f.category} · Real menu, real delivery` : 'Real menu, real delivery'}</p>
          </div>
          {/* Same shared-component fix as the restaurant list card above. */}
          <div onClick={(e) => e.stopPropagation()} style={{ flexShrink: 0 }}>
            <WishlistButton favorited busy={removingId === f.restaurantId} onToggle={() => handleRemove(f.restaurantId)} />
          </div>
        </div>
      ))}
    </div>
  );
}

function DeliverView() {
  const { t } = useI18n();
  const [rider, setRider] = useState<Rider | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const [registering, setRegistering] = useState(false);
  const [available, setAvailable] = useState<EatsOrder[] | null>(null);
  const [mine, setMine] = useState<EatsOrder[] | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const loadRider = () => {
    setError(null);
    fetchMyRiderProfile()
      .then(setRider)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDER_NOT_REGISTERED') {
          setRider(null);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(loadRider, []);

  const loadDeliveries = () => {
    Promise.all([fetchAvailableDeliveries(), fetchRiderDeliveries()])
      .then(([a, m]) => { setAvailable(a); setMine(m); })
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (!rider) return;
    loadDeliveries();
    const interval = setInterval(loadDeliveries, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rider?.id]);

  const handleRegister = async () => {
    setRegistering(true);
    setError(null);
    try {
      setRider(await registerRider());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!rider) return;
    try {
      setRider(await setRiderAvailability(!rider.available));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleClaim = async (orderId: string) => {
    setBusyOrderId(orderId);
    setError(null);
    try {
      await claimDelivery(orderId);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RIDER_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRiderOrder(order.id, next);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (rider === undefined) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;

  if (rider === null) {
    return (
      <div style={{ textAlign: 'center', padding: '28px 0' }}>
        <Bike size={32} color="var(--itunda-indigo)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '6px' }}>Deliver with Itunda</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
          Earn a real delivery fee for every order you deliver, paid straight to your account.
        </p>
        <button className="itunda-btn itunda-btn-primary" onClick={handleRegister} disabled={registering}>
          {registering ? 'Registering…' : 'Become a rider'}
        </button>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const activeDeliveries = (mine ?? []).filter((o) => o.status !== 'DELIVERED');
  const pastDeliveries = (mine ?? []).filter((o) => o.status === 'DELIVERED');

  return (
    <div>
      <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{rider.available ? "You're online" : "You're offline"}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{rider.available ? 'Visible for new deliveries' : 'Go online to see deliveries'}</p>
        </div>
        <button className={rider.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
          {rider.available ? 'Go offline' : 'Go online'}
        </button>
      </div>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}

      {activeDeliveries.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active deliveries</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {activeDeliveries.map((o) => {
              const next = nextInChain(RIDER_STATUS_CHAIN, o.status);
              return (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={next && (
                    <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                      {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                    </button>
                  )}
                />
              );
            })}
          </div>
        </div>
      )}

      {rider.available && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Available deliveries</h4>
          {available === null ? (
            <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : available.length === 0 ? (
            <EmptyState message="No deliveries waiting right now — stay online and you'll be notified." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {available.map((o) => (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={
                    <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleClaim(o.id)}>
                      {busyOrderId === o.id ? 'Claiming…' : 'Claim delivery'}
                    </button>
                  }
                />
              ))}
            </div>
          )}
        </div>
      )}

      {pastDeliveries.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastDeliveries.map((o) => <EatsOrderCard key={o.id} order={o} />)}
          </div>
        </div>
      )}
    </div>
  );
}

function RestaurantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected 404 for any account that hasn't registered as a merchant --
        // this view stays silent rather than showing an alarming error for the common
        // case of a buyer-only account that has no restaurant.
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: EatsOrder) => {
    const next = nextInChain(RESTAURANT_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceRestaurantOrder(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  // Real Baemin-style 포장주문 (Pickup) terminal edge (item 208) -- a PICKUP order at
  // READY_FOR_PICKUP has no `next` in RESTAURANT_STATUS_CHAIN (there's no rider to hand
  // off to), so it previously just sat there forever with no action anywhere to close
  // it out, despite EatsOrderService.completePickup being real and live-verified.
  const handleCompletePickup = async (order: EatsOrder) => {
    setBusyOrderId(order.id);
    setError(null);
    try {
      await completePickupOrder(order.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(RESTAURANT_STATUS_CHAIN, o.status);
          const readyForPickupHandoff = o.fulfillmentType === 'PICKUP' && o.status === 'READY_FOR_PICKUP';
          return (
            <EatsOrderCard
              key={o.id}
              order={o}
              action={
                readyForPickupHandoff ? (
                  <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleCompletePickup(o)}>
                    {busyOrderId === o.id ? 'Updating…' : 'Mark picked up'}
                  </button>
                ) : next && (
                  <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                    {busyOrderId === o.id ? 'Updating…' : `Mark ${EATS_STATUS_LABEL[next].toLowerCase()}`}
                  </button>
                )
              }
            />
          );
        })}
      </div>
      <RestaurantReviewsManageView restaurantId={orders[0].restaurantId} />
    </div>
  );
}

const RIDE_STATUS_LABEL: Record<RideTrip['status'], string> = {
  REQUESTED: 'Finding a driver…',
  DRIVER_ASSIGNED: 'Driver assigned',
  IN_PROGRESS: 'Trip in progress',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

function PlaceSearchInput({ label, placeholder, value, onSelect }: {
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

// Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10): dropped
// itunda-card. Deliberately NOT using .itunda-flat-section's per-row divider here --
// this shared component is reused across 5 different contexts in RidesView (a lone
// active trip, an active-trips list, an available-to-accept list, and 2 genuine
// past-trip history lists), and a divider baked into the component would be wrong
// for the non-list/non-history cases. Plain flat spacing instead, safe everywhere
// it's used.
function RideTripCard({ trip, action, stops }: { trip: RideTrip; action?: React.ReactNode; stops?: RideTripStop[] | null }) {
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.pickupAddress}</p>
          {stops && stops.length > 0 && stops.map((s) => (
            <p key={s.id} style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: s.arrivedAt ? 'var(--itunda-grey-400)' : 'var(--itunda-grey-700)', margin: '1px 0' }}>
              {s.arrivedAt ? '✓' : '→'} {s.address}
            </p>
          ))}
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString()} RWF</p>
          {trip.scheduledFor && (
            <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-indigo)', fontWeight: 700, marginTop: '2px' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><ClockGlyph size={16} /> Scheduled for {new Date(trip.scheduledFor).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}</span>
            </p>
          )}
        </div>
        <span style={{
          fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? 'var(--itunda-red)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)',
          backgroundColor: trip.status === 'CANCELLED' ? 'var(--itunda-red-light)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-100)' : '#E8F0FE',
        }}>
          {trip.status === 'REQUESTED' && trip.scheduledFor ? 'Scheduled' : RIDE_STATUS_LABEL[trip.status]}
        </span>
      </div>
      {action}
    </div>
  );
}

// Real "meet your driver" rating + reviews during an active trip (item 233) -- see
// lib/rideshare.ts's own doc comment on fetchDriverReviews for the full sourced
// account. Honest v1: no driver name/vehicle shown, since RideDriver carries neither.
function DriverRatingSection({ driverId }: { driverId: string }) {
  const [rating, setRating] = useState<RideDriverRating | null>(null);
  const [reviews, setReviews] = useState<RideTripReview[] | null>(null);
  const [expanded, setExpanded] = useState(false);

  useEffect(() => {
    fetchDriverRating(driverId).then(setRating).catch(() => {});
  }, [driverId]);

  if (!rating || rating.count === 0) return null;

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <button
        onClick={() => {
          setExpanded((e) => !e);
          if (!expanded && reviews === null) fetchDriverReviews(driverId).then(setReviews).catch(() => setReviews([]));
        }}
        style={{ display: 'flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: '#FFC107' }}
      >
        ★ {rating.average?.toFixed(1)} <span style={{ color: 'var(--itunda-grey-500)', fontWeight: 400 }}>({rating.count} rating{rating.count === 1 ? '' : 's'}) {expanded ? '▲' : '▼'}</span>
      </button>
      {expanded && (
        reviews === null ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>Loading reviews…</p>
        ) : reviews.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>No written reviews yet.</p>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
            {reviews.map((r) => (
              <div key={r.id} style={{ padding: '8px 10px', borderRadius: '8px', background: 'var(--itunda-grey-100)' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700 }}>{'⭐'.repeat(r.rating)}</p>
                {r.comment && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{r.comment}</p>}
              </div>
            ))}
          </div>
        )
      )}
    </div>
  );
}

// Real Kakao T-style post-trip driver rating (item 213) -- one real review per real
// trip, rating the driver who completed it. See lib/rideshare.ts's own doc comment.
function RideReviewPrompt({ tripId, onSubmitted }: { tripId: string; onSubmitted: () => void }) {
  const { t } = useI18n();
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async () => {
    if (rating === 0) return;
    setSubmitting(true);
    setError(null);
    try {
      await submitRideReview(tripId, rating, comment || undefined);
      onSubmitted();
    } catch (err) {
      // Real 409 (RIDE_TRIP_ALREADY_REVIEWED) means this trip was already rated in an
      // earlier session -- hide the prompt rather than surfacing a confusing error.
      if (err instanceof ApiError && err.code === 'RIDE_TRIP_ALREADY_REVIEWED') onSubmitted();
      else setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Rate your driver</p>
      <div style={{ display: 'flex', gap: '4px', marginBottom: '6px' }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n} type="button" onClick={() => setRating(n)}
            style={{ fontSize: 'var(--itunda-type-scale-20-size)', color: n <= rating ? '#FFC107' : 'var(--itunda-grey-300)' }}
          >
            ★
          </button>
        ))}
      </div>
      {rating > 0 && (
        <>
          <input
            type="text" value={comment} placeholder="Leave a comment (optional)" onChange={(e) => setComment(e.target.value)}
            style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', marginBottom: '6px' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={submitting} onClick={handleSubmit} style={{ width: '100%', padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {submitting ? 'Submitting…' : 'Submit rating'}
          </button>
        </>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

const TIP_PRESETS = [500, 1000, 2000];

// Real Uber post-trip tipping -- see lib/rideshare.ts's own doc comment. Found via
// scripts/uncalled-endpoint-sweep.py: fully built on the backend with zero client
// anywhere. Same real device step-up pattern every other money-moving action in this
// file already needs (tip is a real account-to-account transfer, gated by
// DeviceVerificationFilter same as TransferFlow/DelayedTransfersCard).
function TipDriverPrompt({ tripId, onTipped }: { tripId: string; onTipped: () => void }) {
  const { t } = useI18n();
  const [amount, setAmount] = useState<number | null>(null);
  const [customAmount, setCustomAmount] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleSubmit = async (overrideAmount?: number) => {
    const finalAmount = overrideAmount ?? amount ?? Number(customAmount);
    if (!(finalAmount > 0)) return;
    setSubmitting(true);
    setError(null);
    try {
      await tipDriver(tripId, finalAmount);
      onTipped();
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

  if (needsDeviceVerification) {
    return (
      <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
        <DeviceStepUpPrompt onVerified={() => { setNeedsDeviceVerification(false); handleSubmit(); }} onCancel={() => setNeedsDeviceVerification(false)} />
      </div>
    );
  }

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, marginBottom: '6px' }}>Tip your driver</p>
      <div style={{ display: 'flex', gap: '6px', marginBottom: '6px' }}>
        {TIP_PRESETS.map((preset) => (
          <button
            key={preset} type="button" disabled={submitting}
            onClick={() => { setAmount(preset); setCustomAmount(''); handleSubmit(preset); }}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              border: '1px solid var(--itunda-grey-200)', background: amount === preset ? 'var(--itunda-indigo)' : 'transparent',
              color: amount === preset ? 'white' : 'var(--itunda-grey-700)',
            }}
          >
            {preset.toLocaleString()}
          </button>
        ))}
      </div>
      <div style={{ display: 'flex', gap: '6px' }}>
        <input
          type="number" placeholder="Custom amount (RWF)" value={customAmount}
          onChange={(e) => { setCustomAmount(e.target.value); setAmount(null); }}
          style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)' }}
        />
        <button
          className="itunda-btn itunda-btn-primary" disabled={submitting || !(Number(customAmount) > 0)}
          onClick={() => handleSubmit()} style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 14px' }}
        >
          {submitting ? '…' : 'Send'}
        </button>
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Uber Safety "Trusted Contacts" (help.uber.com) -- a persistent contact list set
// up once, distinct from the per-trip "Share trip status" pick above. Found while
// triaging the uncalled-endpoint sweep: RideController already shipped a complete,
// tested list/add/remove implementation (RideTrustedContactService) with zero client
// callers on any platform. This is bank-mfe's first UI for it.
function TrustedContactsSection() {
  const { t } = useI18n();
  const [contacts, setContacts] = useState<RideTrustedContact[] | null>(null);
  const [showAdd, setShowAdd] = useState(false);
  const [name, setName] = useState('');
  const [phone, setPhone] = useState('');
  const [busy, setBusy] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => { fetchTrustedContacts().then(setContacts).catch(() => setContacts([])); };
  useEffect(load, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !phone.trim()) return;
    setBusy(true);
    setError(null);
    try {
      await addTrustedContact(phone.trim(), name.trim());
      setName('');
      setPhone('');
      setShowAdd(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleRemove = async (id: string) => {
    setBusyId(id);
    setError(null);
    try {
      await removeTrustedContact(id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ marginBottom: '20px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
          <IconShieldCheck size={16} color="var(--itunda-green)" /> Trusted contacts
        </h3>
        {(contacts?.length ?? 0) < 5 && (
          <button style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }} onClick={() => setShowAdd((v) => !v)}>
            {showAdd ? 'Cancel' : '+ Add'}
          </button>
        )}
      </div>
      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Up to 5 people who can get your live ride status in one tap, every trip.
      </p>
      {showAdd && (
        <form onSubmit={handleAdd} style={{ marginBottom: '10px' }}>
          <input
            type="text" value={name} onChange={(e) => setName(e.target.value)} placeholder="Name"
            style={{ width: '100%', padding: '8px', marginBottom: '6px', border: '1px solid var(--itunda-grey-200)', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <input
            type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} placeholder="Phone number"
            style={{ width: '100%', padding: '8px', marginBottom: '6px', border: '1px solid var(--itunda-grey-200)', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ width: '100%', padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            {busy ? 'Adding…' : 'Add trusted contact'}
          </button>
        </form>
      )}
      {contacts === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : contacts.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>No trusted contacts yet.</p>
      ) : (
        contacts.map((c) => (
          <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--itunda-grey-100)' }}>
            <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 600 }}>{c.contactName}</span>
            <button
              onClick={() => handleRemove(c.id)} disabled={busyId === c.id}
              style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-red)' }}
            >
              {busyId === c.id ? '…' : 'Remove'}
            </button>
          </div>
        ))
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', marginTop: '6px' }} role="alert">{error}</p>}
    </div>
  );
}

function RidesView({ onReportIssue }: { onReportIssue: (transactionId: string) => void }) {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'RIDE' | 'DRIVE'>('RIDE');

  // Passenger side
  const [pickup, setPickup] = useState<PlaceSearchResult | null>(null);
  const [dropoff, setDropoff] = useState<PlaceSearchResult | null>(null);
  // Real Kakao T-style multi-stop rides (item 214) -- up to 3 real extra waypoints
  // between pickup and dropoff, matching Kakao T's own real cap.
  const [stops, setStops] = useState<(PlaceSearchResult | null)[]>([]);
  const [activeTripStops, setActiveTripStops] = useState<RideTripStop[] | null>(null);
  const [myTrips, setMyTrips] = useState<RideTrip[] | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [rideError, setRideError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);
  // Real Uber "Verify Your Ride" PIN -- see lib/rideshare.ts's own doc comment. Fetched
  // once a driver is assigned so the passenger can read it aloud before pickup.
  const [activeTripPin, setActiveTripPin] = useState<string | null>(null);
  // Real Uber "Share Trip Status" -- see lib/rideshare.ts's own doc comment.
  const [showShareTripModal, setShowShareTripModal] = useState(false);
  const [shareTripError, setShareTripError] = useState<string | null>(null);
  // Real Uber Safety "Send Status" -- see TrustedContactsSection's own doc comment.
  const [sendStatusBusy, setSendStatusBusy] = useState(false);
  const [sendStatusResult, setSendStatusResult] = useState<string | null>(null);
  // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- 'now' is unchanged
  // ASAP dispatch; 'later' holds a datetime-local value the passenger picks.
  const [rideTiming, setRideTiming] = useState<'now' | 'later'>('now');
  const [scheduledAt, setScheduledAt] = useState('');
  // Real Kakao T-style post-trip driver rating (item 213) -- tracks which completed
  // trips have already been rated this session, so a submitted/already-reviewed
  // prompt doesn't linger. See RideReviewPrompt's own doc comment.
  const [reviewedTripIds, setReviewedTripIds] = useState<Set<string>>(new Set());
  // Same real optimistic-hide pattern as reviewedTripIds above -- tipDriver's own
  // response includes the updated trip with tipAmount now set, but pastTrips itself
  // isn't refetched on every tip, so this tracks which trips were tipped THIS session.
  const [tippedTripIds, setTippedTripIds] = useState<Set<string>>(new Set());
  // Real Uber "Upfront Fare" simplification (2026-08-24) -- see lib/rideshare.ts's own
  // estimateRideFare doc comment for the full sourced account. Debounced the same
  // 350ms PlaceSearchInput's own place-search request already uses, since both pickup
  // and dropoff selecting in quick succession would otherwise fire a fetch per step.
  const [estimatedFare, setEstimatedFare] = useState<number | null>(null);
  useEffect(() => {
    if (!pickup || !dropoff) { setEstimatedFare(null); return; }
    const handle = setTimeout(() => {
      estimateRideFare(pickup.latitude, pickup.longitude, dropoff.latitude, dropoff.longitude)
        .then(setEstimatedFare)
        .catch(() => setEstimatedFare(null));
    }, 350);
    return () => clearTimeout(handle);
  }, [pickup, dropoff]);

  const loadMyTrips = () => {
    fetchMyTrips().then(setMyTrips).catch((err) => setRideError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'RIDE') return;
    loadMyTrips();
    const interval = setInterval(loadMyTrips, 4000);
    return () => clearInterval(interval);
  }, [subTab]);

  const activeTrip = (myTrips ?? []).find((t) => t.status === 'REQUESTED' || t.status === 'DRIVER_ASSIGNED' || t.status === 'IN_PROGRESS');
  const pastTrips = (myTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  useEffect(() => {
    if (!activeTrip) { setActiveTripStops(null); return; }
    fetchTripStops(activeTrip.id).then((s) => setActiveTripStops(s.length > 0 ? s : null)).catch(() => {});
  }, [activeTrip?.id]);

  // Real Uber "Verify Your Ride" PIN -- fetched once a driver is assigned (before that,
  // there's no driver yet to tell it to). A pre-existing REQUESTED trip that never
  // reaches DRIVER_ASSIGNED simply never shows a PIN, matching real Uber behavior.
  useEffect(() => {
    if (!activeTrip || activeTrip.status === 'REQUESTED') { setActiveTripPin(null); return; }
    fetchRideTripPin(activeTrip.id).then(setActiveTripPin).catch(() => setActiveTripPin(null));
  }, [activeTrip?.id, activeTrip?.status]);

  const handleRequestRide = async () => {
    if (!pickup || !dropoff) return;
    if (rideTiming === 'later' && !scheduledAt) return;
    const resolvedStops = stops.filter((s): s is PlaceSearchResult => s !== null);
    setRequesting(true);
    setRideError(null);
    try {
      await requestRideTrip(
        pickup.displayName, pickup.latitude, pickup.longitude, dropoff.displayName, dropoff.latitude, dropoff.longitude,
        rideTiming === 'later' ? new Date(scheduledAt).toISOString() : undefined,
        resolvedStops.length > 0 ? resolvedStops.map((s) => ({ address: s.displayName, latitude: s.latitude, longitude: s.longitude })) : undefined,
      );
      setPickup(null);
      setDropoff(null);
      setRideTiming('now');
      setScheduledAt('');
      setStops([]);
      loadMyTrips();
    } catch (err) {
      setRideError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancelTrip = async (tripId: string) => {
    setBusyTripId(tripId);
    try {
      await cancelRideTrip(tripId);
      loadMyTrips();
    } catch (err) {
      setRideError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyTripId(null);
    }
  };

  const handleShareTripStatus = async (tripId: string, conversationId: string) => {
    setShareTripError(null);
    try {
      await shareRideTripStatus(tripId, conversationId);
      setShowShareTripModal(false);
    } catch (err) {
      setShareTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  // Real Uber Safety "Send Status" -- one tap fans this trip's live status out to every
  // real trusted contact at once, distinct from handleShareTripStatus's own per-share
  // conversation pick above.
  const handleSendStatus = async (tripId: string) => {
    setSendStatusBusy(true);
    setSendStatusResult(null);
    try {
      const sentCount = await sendStatusToTrustedContacts(tripId);
      setSendStatusResult(sentCount > 0 ? `Sent to ${sentCount} trusted contact${sentCount === 1 ? '' : 's'}.` : 'Add a trusted contact first to send your status.');
    } catch (err) {
      setSendStatusResult(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSendStatusBusy(false);
    }
  };

  // Driver side
  const [driver, setDriver] = useState<RideDriver | null | undefined>(undefined);
  const [registeringDriver, setRegisteringDriver] = useState(false);
  const [availableTrips, setAvailableTrips] = useState<RideTrip[] | null>(null);
  const [myDriverTrips, setMyDriverTrips] = useState<RideTrip[] | null>(null);
  const [driverError, setDriverError] = useState<string | null>(null);
  const [busyDriverTripId, setBusyDriverTripId] = useState<string | null>(null);
  // Real Uber "Verify Your Ride" PIN -- what the driver has typed in for each real
  // active trip, keyed by trip id so multiple trip cards don't share one input.
  const [startPinInputs, setStartPinInputs] = useState<Record<string, string>>({});
  // Real Kakao T-style post-trip driver rating (item 213) -- the real driver's own
  // aggregate rating, computed at read time from every real submitted review.
  const [driverRating, setDriverRating] = useState<RideDriverRating | null>(null);
  // Real Kakao T-style multi-stop rides (item 214) -- keyed by trip id, so each real
  // active trip's own waypoints render independently.
  const [driverTripStops, setDriverTripStops] = useState<Record<string, RideTripStop[]>>({});
  // Real Uber "Destination Filter" + earnings report (uncalled-endpoint sweep
  // follow-up, item 245) -- both real, fully-built backend endpoints found with
  // zero client anywhere on any platform before this.
  const [destinationAddress, setDestinationAddress] = useState('');
  const [destinationBusy, setDestinationBusy] = useState(false);
  const [earnings, setEarnings] = useState<DriverDailyEarnings[] | null>(null);

  const loadDriver = () => {
    fetchMyDriverProfile()
      .then((d) => {
        setDriver(d);
        fetchDriverRating(d.id).then(setDriverRating).catch(() => {});
      })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDE_DRIVER_NOT_REGISTERED') setDriver(null);
        else setDriverError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
  };

  useEffect(() => {
    if (subTab === 'DRIVE') loadDriver();
  }, [subTab]);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableTrips(), fetchMyDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'DRIVE' || !driver) return;
    loadDriverTrips();
    const interval = setInterval(loadDriverTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, driver?.id]);

  // Real Uber Driver-style earnings report + Destination Filter (uncalled-endpoint
  // sweep follow-up, item 245) -- both real, fully-built backend endpoints found
  // with zero client anywhere on any platform before this.
  useEffect(() => {
    if (subTab !== 'DRIVE' || !driver) return;
    fetchMyEarnings().then((r) => setEarnings(r.days)).catch(() => {});
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, driver?.id]);

  const handleSetDestination = async (suggestion: AddressSuggestion) => {
    setDestinationBusy(true);
    setDriverError(null);
    try {
      setDriver(await setDriverDestination(suggestion.latitude, suggestion.longitude));
      setDestinationAddress(suggestion.displayName);
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDestinationBusy(false);
    }
  };

  const handleClearDestination = async () => {
    setDestinationBusy(true);
    setDriverError(null);
    try {
      setDriver(await clearDriverDestination());
      setDestinationAddress('');
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setDestinationBusy(false);
    }
  };

  const handleRegisterDriver = async () => {
    setRegisteringDriver(true);
    setDriverError(null);
    try {
      setDriver(await registerAsDriver());
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): same
      // register-once shape as Eats' own RIDER_ALREADY_REGISTERED, apparently missed
      // when that one was fixed -- a double-tap or a second device registering first
      // isn't really a failure, resolve forward into the real existing profile.
      if (err instanceof ApiError && err.code === 'RIDE_DRIVER_ALREADY_REGISTERED') {
        loadDriver();
      } else {
        setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setRegisteringDriver(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!driver) return;
    try {
      const updated = await setDriverAvailability(!driver.available);
      setDriver(updated);
      if (updated.available && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition((pos) => {
          updateDriverLocation(pos.coords.latitude, pos.coords.longitude).then(setDriver).catch(() => {});
        });
      }
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<RideTrip>) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await action(tripId);
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  // Real Uber "Verify Your Ride" PIN -- the driver must enter the exact code the
  // passenger just told them before the trip (and the fare clock) actually starts.
  const handleStartTrip = async (tripId: string) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await startRideTrip(tripId, startPinInputs[tripId] ?? '');
      setStartPinInputs((prev) => { const next = { ...prev }; delete next[tripId]; return next; });
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  const activeDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'DRIVER_ASSIGNED' || t.status === 'IN_PROGRESS');
  const pastDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  useEffect(() => {
    activeDriverTrips.forEach((t) => {
      fetchTripStops(t.id).then((s) => setDriverTripStops((prev) => (s.length > 0 ? { ...prev, [t.id]: s } : prev))).catch(() => {});
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [activeDriverTrips.map((t) => t.id).join(',')]);

  const handleArriveAtStop = async (tripId: string) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await arriveAtRideStop(tripId);
      const refreshed = await fetchTripStops(tripId);
      setDriverTripStops((prev) => ({ ...prev, [tripId]: refreshed }));
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['RIDE', 'DRIVE'] as const).map((v) => (
          <button
            key={v} onClick={() => setSubTab(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: subTab === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: subTab === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'RIDE' ? 'Get a ride' : 'Drive'}
          </button>
        ))}
      </div>

      {subTab === 'RIDE' && (
        <div>
          {activeTrip ? (
            <div style={{ marginBottom: '20px' }}>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your ride</h4>
              <RideTripCard
                trip={activeTrip} stops={activeTripStops}
                action={(
                  <>
                    {activeTripPin && activeTrip.status === 'DRIVER_ASSIGNED' && (
                      <div
                        style={{
                          textAlign: 'center', padding: '12px', borderRadius: '10px',
                          backgroundColor: 'var(--itunda-indigo-light)', marginBottom: '4px',
                        }}
                      >
                        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Tell your driver this PIN before you get in</p>
                        <p style={{ fontSize: 'var(--itunda-type-scale-28-size)', fontWeight: 700, letterSpacing: '4px', color: 'var(--itunda-indigo)' }}>{activeTripPin}</p>
                      </div>
                    )}
                    {activeTrip.driverId && <DriverRatingSection driverId={activeTrip.driverId} />}
                    <button className="itunda-btn itunda-btn-secondary" onClick={() => setShowShareTripModal(true)}>
                      Share trip status
                    </button>
                    {shareTripError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{shareTripError}</p>}
                    <button className="itunda-btn itunda-btn-secondary" disabled={sendStatusBusy} onClick={() => handleSendStatus(activeTrip.id)}>
                      {sendStatusBusy ? 'Sending…' : 'Send status to trusted contacts'}
                    </button>
                    {sendStatusResult && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{sendStatusResult}</p>}
                    {activeTrip.status !== 'IN_PROGRESS' && (
                      <button className="itunda-btn itunda-btn-danger" disabled={busyTripId === activeTrip.id} onClick={() => handleCancelTrip(activeTrip.id)}>
                        {busyTripId === activeTrip.id ? 'Cancelling…' : 'Cancel ride'}
                      </button>
                    )}
                  </>
                )}
              />
              {showShareTripModal && (
                <ShareFavoritesModal
                  title="Share ride status to…"
                  onShare={(conversationId) => handleShareTripStatus(activeTrip.id, conversationId)}
                  onClose={() => setShowShareTripModal(false)}
                />
              )}
            </div>
          ) : (
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
            // form section shown when there's no active trip.
            <div style={{ padding: '10px 0' }}>
              <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Car size={18} color="var(--itunda-indigo)" /> Request a ride
              </h3>
              <PlaceSearchInput label="Pickup" placeholder="Where from?" value={pickup} onSelect={setPickup} />
              {stops.map((stop, i) => (
                <PlaceSearchInput
                  key={i} label={`Stop ${i + 1}`} placeholder="Add a stop" value={stop}
                  onSelect={(place) => setStops((prev) => prev.map((s, idx) => (idx === i ? place : s)))}
                />
              ))}
              <PlaceSearchInput label="Dropoff" placeholder="Where to?" value={dropoff} onSelect={setDropoff} />
              {stops.length < 3 && (
                <button
                  type="button" onClick={() => setStops((prev) => [...prev, null])}
                  style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-indigo)', marginBottom: '12px' }}
                >
                  + Add a stop
                </button>
              )}

              <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
                {(['now', 'later'] as const).map((v) => (
                  <button
                    key={v} type="button" onClick={() => setRideTiming(v)}
                    style={{
                      flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
                      color: rideTiming === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
                      backgroundColor: rideTiming === v ? 'var(--itunda-indigo)' : 'transparent',
                    }}
                  >
                    {v === 'now' ? 'Ride now' : 'Schedule'}
                  </button>
                ))}
              </div>
              {rideTiming === 'later' && (
                <input
                  type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                  style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
                />
              )}

              {pickup && dropoff && (
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
                  {estimatedFare != null
                    ? `Estimated fare: ${estimatedFare.toLocaleString()} RWF`
                    : 'Estimating fare…'}
                </p>
              )}
              <button
                className="itunda-btn itunda-btn-primary"
                disabled={!pickup || !dropoff || requesting || (rideTiming === 'later' && !scheduledAt) || stops.some((s) => s === null)}
                onClick={handleRequestRide} style={{ width: '100%' }}
              >
                {requesting ? 'Requesting…' : rideTiming === 'later' ? 'Schedule ride' : 'Request ride'}
              </button>
            </div>
          )}

          {rideError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{rideError}</p>}

          <TrustedContactsSection />

          {pastTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past rides</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pastTrips.map((t) => (
                  <RideTripCard
                    key={t.id} trip={t}
                    action={(
                      <>
                        {t.status === 'COMPLETED' && t.driverId && !reviewedTripIds.has(t.id) && (
                          <RideReviewPrompt tripId={t.id} onSubmitted={() => setReviewedTripIds((prev) => new Set(prev).add(t.id))} />
                        )}
                        {t.status === 'COMPLETED' && t.driverId && !t.tipAmount && !tippedTripIds.has(t.id) && (
                          <TipDriverPrompt tripId={t.id} onTipped={() => setTippedTripIds((prev) => new Set(prev).add(t.id))} />
                        )}
                        {t.status === 'COMPLETED' && (
                          <button
                            className="itunda-btn itunda-btn-secondary"
                            style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                            onClick={() => onReportIssue(t.transactionId)}
                          >
                            Report an issue
                          </button>
                        )}
                      </>
                    )}
                  />
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'DRIVE' && (
        <div>
          {driver === undefined ? (
            <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />
          ) : driver === null ? (
            // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
            // onboarding message.
            <div style={{ textAlign: 'center', padding: '10px 0' }}>
              <Car size={32} color="var(--itunda-indigo)" style={{ marginBottom: '10px' }} />
              <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, marginBottom: '6px' }}>Drive with Itunda</h3>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>
                Earn a real fare for every trip you complete, paid straight to your account.
              </p>
              <button className="itunda-btn itunda-btn-primary" onClick={handleRegisterDriver} disabled={registeringDriver}>
                {registeringDriver ? 'Registering…' : 'Become a driver'}
              </button>
              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '12px' }} role="alert">{driverError}</p>}
            </div>
          ) : (
            // Real fix (2026-08-24, flat-design sweep): 3 distinct non-exclusive
            // sections shown together -- reused .itunda-flat-section for
            // section-boundary dividers.
            <div>
              <div className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{driver.available ? "You're online" : "You're offline"}</p>
                  <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{driver.available ? 'Visible for new trip requests' : 'Go online to see trip requests'}</p>
                  {driverRating && driverRating.count > 0 && (
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: '#FFC107', fontWeight: 700, marginTop: '4px' }}>
                      ★ {driverRating.average?.toFixed(1)} <span style={{ color: 'var(--itunda-grey-500)', fontWeight: 400 }}>({driverRating.count} rating{driverRating.count === 1 ? '' : 's'})</span>
                    </p>
                  )}
                </div>
                <button className={driver.available ? 'itunda-btn itunda-btn-danger' : 'itunda-btn itunda-btn-primary'} onClick={handleToggleAvailable}>
                  {driver.available ? 'Go offline' : 'Go online'}
                </button>
              </div>

              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{driverError}</p>}

              {/* Real Uber "Destination Filter" -- see RideDriver.destinationLatitude's
                  own doc comment. Set once, works across sessions until cleared (no
                  expiry client-side; matches the real backend, which never expires it
                  on its own either). */}
              <div className="itunda-flat-section">
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Heading somewhere?</p>
                {driver.destinationLatitude != null ? (
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-600)' }}>
                      Only offered trips heading toward {destinationAddress || 'your destination'}.
                    </p>
                    <button className="itunda-btn itunda-btn-secondary" disabled={destinationBusy} onClick={handleClearDestination}>
                      {destinationBusy ? '…' : 'Clear'}
                    </button>
                  </div>
                ) : (
                  <>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
                      Set a destination and you'll only be offered trips heading that direction.
                    </p>
                    <AddressAutocomplete
                      value={destinationAddress}
                      onChangeText={setDestinationAddress}
                      onSelectSuggestion={handleSetDestination}
                      placeholder="Where are you heading?"
                    />
                  </>
                )}
              </div>

              {earnings && earnings.length > 0 && (
                <div className="itunda-flat-section">
                  <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>This week</p>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <div>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Trips</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{earnings.reduce((sum, d) => sum + d.tripCount, 0)}</p>
                    </div>
                    <div>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Gross fare</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{earnings.reduce((sum, d) => sum + d.grossFare, 0).toLocaleString()} RWF</p>
                    </div>
                    <div>
                      <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>Net earnings</p>
                      <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700, color: 'var(--itunda-green)' }}>{earnings.reduce((sum, d) => sum + d.netEarnings, 0).toLocaleString()} RWF</p>
                    </div>
                  </div>
                </div>
              )}

              {activeDriverTrips.length > 0 && (
                <div style={{ marginBottom: '20px' }}>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active trip</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {activeDriverTrips.map((t) => {
                      const tripStops = driverTripStops[t.id];
                      const nextStop = tripStops?.find((s) => !s.arrivedAt);
                      return (
                        <RideTripCard
                          key={t.id} trip={t} stops={tripStops}
                          action={
                            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                              {t.status === 'IN_PROGRESS' && nextStop && (
                                <button className="itunda-btn itunda-btn-secondary" disabled={busyDriverTripId === t.id} onClick={() => handleArriveAtStop(t.id)}>
                                  {busyDriverTripId === t.id ? 'Updating…' : `Arrived at ${nextStop.address}`}
                                </button>
                              )}
                              {t.status === 'DRIVER_ASSIGNED' ? (
                                <>
                                  <input
                                    type="text" inputMode="numeric" maxLength={4} placeholder="Ask passenger for their 4-digit PIN"
                                    value={startPinInputs[t.id] ?? ''}
                                    onChange={(e) => setStartPinInputs((prev) => ({ ...prev, [t.id]: e.target.value.replace(/\D/g, '') }))}
                                    style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', textAlign: 'center', letterSpacing: '2px' }}
                                  />
                                  <button
                                    className="itunda-btn itunda-btn-primary"
                                    disabled={busyDriverTripId === t.id || (startPinInputs[t.id] ?? '').length !== 4}
                                    onClick={() => handleStartTrip(t.id)}
                                  >
                                    {busyDriverTripId === t.id ? 'Updating…' : 'Start trip'}
                                  </button>
                                </>
                              ) : (
                                <button
                                  className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id}
                                  onClick={() => handleDriverTripAction(t.id, completeRideTrip)}
                                >
                                  {busyDriverTripId === t.id ? 'Updating…' : 'Complete trip'}
                                </button>
                              )}
                            </div>
                          }
                        />
                      );
                    })}
                  </div>
                </div>
              )}

              {driver.available && (
                <div style={{ marginBottom: '20px' }}>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Trip requests near you</h4>
                  {availableTrips === null ? (
                    <div className="skeleton" style={{ height: '100px', borderRadius: 'var(--itunda-radius-md)' }} />
                  ) : availableTrips.length === 0 ? (
                    <EmptyState message="No trip requests waiting right now — stay online and you'll be notified." />
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {availableTrips.map((t) => (
                        <RideTripCard
                          key={t.id} trip={t}
                          action={
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button
                                className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id}
                                onClick={() => handleDriverTripAction(t.id, acceptRideTrip)}
                              >
                                {busyDriverTripId === t.id ? 'Accepting…' : 'Accept'}
                              </button>
                              <button
                                className="itunda-btn itunda-btn-secondary" disabled={busyDriverTripId === t.id}
                                onClick={() => handleDriverTripAction(t.id, declineRideTrip)}
                              >
                                Decline
                              </button>
                            </div>
                          }
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}

              {pastDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {pastDriverTrips.map((t) => <RideTripCard key={t.id} trip={t} />)}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const DESIGNATED_DRIVER_STATUS_LABEL: Record<DesignatedDriverTrip['status'], string> = {
  REQUESTED: 'Finding a driver…',
  ACCEPTED: 'Driver on the way',
  DRIVING: 'Driver is driving you home',
  COMPLETED: 'Completed',
  CANCELLED: 'Cancelled',
};

function DesignatedDriverTripCard({ trip, action }: { trip: DesignatedDriverTrip; action?: React.ReactNode }) {
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{trip.pickupAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
            {trip.vehicleMake} {trip.vehicleModel} · {trip.vehiclePlate}
          </p>
          <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString()} RWF</p>
        </div>
        <span style={{
          fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? 'var(--itunda-red)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-500)' : 'var(--itunda-indigo)',
          backgroundColor: trip.status === 'CANCELLED' ? 'var(--itunda-red-light)' : trip.status === 'COMPLETED' ? 'var(--itunda-grey-100)' : '#E8F0FE',
        }}>
          {DESIGNATED_DRIVER_STATUS_LABEL[trip.status]}
        </span>
      </div>
      {action}
    </div>
  );
}

// Real Kakao T 대리운전 (designated driver, item 221) -- see lib/designatedDriver.ts's
// own doc comment for the full sourced account. Mirrors RidesView's own Ride/Drive
// toggle structure, but the "driver" here drives the CUSTOMER'S OWN CAR, not their own
// vehicle -- vehicleMake/vehicleModel/vehiclePlate describe that car, purely
// informational text the driver sees before arriving.
function DesignatedDriverView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'REQUEST' | 'DRIVE'>('REQUEST');

  // Customer side
  const [pickup, setPickup] = useState<PlaceSearchResult | null>(null);
  const [dropoff, setDropoff] = useState<PlaceSearchResult | null>(null);
  const [vehicleMake, setVehicleMake] = useState('');
  const [vehicleModel, setVehicleModel] = useState('');
  const [vehiclePlate, setVehiclePlate] = useState('');
  const [myTrips, setMyTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [tripError, setTripError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);

  const loadMyTrips = () => {
    fetchMyDesignatedDriverTrips().then(setMyTrips).catch((err) => setTripError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'REQUEST') return;
    loadMyTrips();
    const interval = setInterval(loadMyTrips, 4000);
    return () => clearInterval(interval);
  }, [subTab]);

  const activeTrip = (myTrips ?? []).find((t) => t.status === 'REQUESTED' || t.status === 'ACCEPTED' || t.status === 'DRIVING');
  const pastTrips = (myTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  const handleRequestTrip = async () => {
    if (!pickup || !dropoff || !vehicleMake.trim() || !vehicleModel.trim() || !vehiclePlate.trim()) return;
    setRequesting(true);
    setTripError(null);
    try {
      await requestDesignatedDriverTrip(
        pickup.displayName, pickup.latitude, pickup.longitude, dropoff.displayName, dropoff.latitude, dropoff.longitude,
        vehicleMake.trim(), vehicleModel.trim(), vehiclePlate.trim(),
      );
      setPickup(null);
      setDropoff(null);
      setVehicleMake('');
      setVehicleModel('');
      setVehiclePlate('');
      loadMyTrips();
    } catch (err) {
      setTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRequesting(false);
    }
  };

  const handleCancelTrip = async (tripId: string) => {
    setBusyTripId(tripId);
    try {
      await cancelDesignatedDriverTrip(tripId);
      loadMyTrips();
    } catch (err) {
      setTripError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyTripId(null);
    }
  };

  // Driver side
  const [driver, setDriver] = useState<DesignatedDriver | null | undefined>(undefined);
  const [licenseNumber, setLicenseNumber] = useState('');
  const [registeringDriver, setRegisteringDriver] = useState(false);
  const [availableTrips, setAvailableTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [myDriverTrips, setMyDriverTrips] = useState<DesignatedDriverTrip[] | null>(null);
  const [driverError, setDriverError] = useState<string | null>(null);
  const [busyDriverTripId, setBusyDriverTripId] = useState<string | null>(null);

  const loadDriver = () => {
    fetchMyDesignatedDriverProfile()
      .then(setDriver)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'DESIGNATED_DRIVER_NOT_REGISTERED') setDriver(null);
        else setDriverError(err instanceof ApiError ? err.message : t('common.loadError'));
      });
  };

  useEffect(() => {
    if (subTab === 'DRIVE') loadDriver();
  }, [subTab]);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableDesignatedDriverTrips(), fetchMyDesignatedDriverDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    if (subTab !== 'DRIVE' || !driver) return;
    loadDriverTrips();
    const interval = setInterval(loadDriverTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, driver?.id]);

  const handleRegisterDriver = async () => {
    if (!licenseNumber.trim()) return;
    setRegisteringDriver(true);
    setDriverError(null);
    try {
      setDriver(await registerAsDesignatedDriver(licenseNumber.trim()));
      setLicenseNumber('');
    } catch (err) {
      // Real gap found live (Toss-style error-handling audit, 2026-08-30): this
      // resolve-forward fix already shipped on Android/iOS but bank-mfe never got it
      // -- a double-tap or a second device registering first isn't really a failure.
      if (err instanceof ApiError && err.code === 'DESIGNATED_DRIVER_ALREADY_REGISTERED') {
        loadDriver();
      } else {
        setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setRegisteringDriver(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!driver) return;
    try {
      const updated = await setDesignatedDriverAvailability(!driver.available);
      setDriver(updated);
      if (updated.available && navigator.geolocation) {
        navigator.geolocation.getCurrentPosition((pos) => {
          updateDesignatedDriverLocation(pos.coords.latitude, pos.coords.longitude).then(setDriver).catch(() => {});
        });
      }
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<DesignatedDriverTrip>) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await action(tripId);
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyDriverTripId(null);
    }
  };

  const activeDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'ACCEPTED' || t.status === 'DRIVING');
  const pastDriverTrips = (myDriverTrips ?? []).filter((t) => t.status === 'COMPLETED' || t.status === 'CANCELLED');

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'REQUEST' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('REQUEST')} style={{ flex: 1 }}
        >
          Get a driver
        </button>
        <button
          className={subTab === 'DRIVE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('DRIVE')} style={{ flex: 1 }}
        >
          Drive
        </button>
      </div>

      {subTab === 'REQUEST' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {tripError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{tripError}</p>}
          {activeTrip ? (
            <DesignatedDriverTripCard
              trip={activeTrip}
              action={
                activeTrip.status === 'REQUESTED' && (
                  <button
                    className="itunda-btn itunda-btn-secondary" disabled={busyTripId === activeTrip.id}
                    onClick={() => handleCancelTrip(activeTrip.id)} style={{ marginTop: '8px', width: '100%' }}
                  >
                    {busyTripId === activeTrip.id ? '…' : 'Cancel'}
                  </button>
                )
              }
            />
          ) : (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Get a designated driver</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                A real professional driver comes to you and drives YOUR OWN CAR home.
              </p>
              <PlaceSearchInput label="Pickup" placeholder="Where are you now?" value={pickup} onSelect={setPickup} />
              <PlaceSearchInput label="Drop-off" placeholder="Where's home?" value={dropoff} onSelect={setDropoff} />
              <input
                type="text" value={vehicleMake} placeholder="Car make (e.g. Toyota)" onChange={(e) => setVehicleMake(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehicleModel} placeholder="Car model (e.g. RAV4)" onChange={(e) => setVehicleModel(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehiclePlate} placeholder="License plate" onChange={(e) => setVehiclePlate(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
                disabled={requesting || !pickup || !dropoff || !vehicleMake.trim() || !vehicleModel.trim() || !vehiclePlate.trim()}
                onClick={handleRequestTrip}
              >
                {requesting ? 'Requesting…' : 'Request a driver'}
              </button>
            </div>
          )}
          {pastTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past trips</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pastTrips.map((t) => <DesignatedDriverTripCard key={t.id} trip={t} />)}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'DRIVE' && (
        <div>
          {driver === undefined && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>}
          {driver === null && (
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Become a designated driver</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '12px' }}>
                Any itunda user can register. License number is self-declared, not verified against a real registry.
              </p>
              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{driverError}</p>}
              <input
                type="text" value={licenseNumber} placeholder="License number" onChange={(e) => setLicenseNumber(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', marginBottom: '12px' }}
              />
              <button
                className="itunda-btn itunda-btn-primary" style={{ width: '100%' }}
                disabled={registeringDriver || !licenseNumber.trim()} onClick={handleRegisterDriver}
              >
                {registeringDriver ? 'Registering…' : 'Register'}
              </button>
            </div>
          )}
          {driver && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{driver.available ? 'Online' : 'Offline'}</p>
                <button className="itunda-btn itunda-btn-secondary" onClick={handleToggleAvailable}>
                  {driver.available ? 'Go offline' : 'Go online'}
                </button>
              </div>
              {driverError && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{driverError}</p>}
              {activeDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Active</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {activeDriverTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                            {t.status === 'ACCEPTED' && (
                              <button
                                className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
                                onClick={() => handleDriverTripAction(t.id, startDesignatedDriverTrip)}
                              >
                                {busyDriverTripId === t.id ? '…' : 'Start driving'}
                              </button>
                            )}
                            {t.status === 'DRIVING' && (
                              <button
                                className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
                                onClick={() => handleDriverTripAction(t.id, completeDesignatedDriverTrip)}
                              >
                                {busyDriverTripId === t.id ? '…' : 'Complete'}
                              </button>
                            )}
                          </div>
                        }
                      />
                    ))}
                  </div>
                </div>
              )}
              {availableTrips && availableTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Nearby requests</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {availableTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <button
                            className="itunda-btn itunda-btn-primary" disabled={busyDriverTripId === t.id} style={{ width: '100%', marginTop: '8px' }}
                            onClick={() => handleDriverTripAction(t.id, acceptDesignatedDriverTrip)}
                          >
                            {busyDriverTripId === t.id ? '…' : 'Accept'}
                          </button>
                        }
                      />
                    ))}
                  </div>
                </div>
              )}
              {pastDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {pastDriverTrips.map((t) => <DesignatedDriverTripCard key={t.id} trip={t} />)}
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// Real Naver 지식iN (Knowledge iN)-style open-topic community Q&A (item 225) -- see
// lib/knowledge.ts's own doc comment for the full sourced account. A genuinely
// different shape from the trip/rental views above: no location, no booking, just a
// real question -> competing answers -> asker-adopts-one-best-answer content flow.
function KnowledgeView() {
  const { t } = useI18n();
  const [subTab, setSubTab] = useState<'BROWSE' | 'MINE'>('BROWSE');
  const [categories, setCategories] = useState<KnowledgeCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [questions, setQuestions] = useState<KnowledgeQuestion[] | null>(null);
  const [myAnswers, setMyAnswers] = useState<KnowledgeAnswer[] | null>(null);
  const [reputation, setReputation] = useState<number | null>(null);
  const [openQuestionId, setOpenQuestionId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchKnowledgeCategories().then(setCategories).catch(() => {});
    fetchMyKnowledgeReputation().then(setReputation).catch(() => {});
  }, []);

  const load = () => {
    setError(null);
    setQuestions(null);
    if (subTab === 'MINE') {
      fetchMyKnowledgeQuestions().then(setQuestions).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
      fetchMyKnowledgeAnswers().then(setMyAnswers).catch(() => {});
      return;
    }
    fetchKnowledgeQuestions(activeCategory ?? undefined)
      .then(setQuestions)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(load, [subTab, activeCategory]);

  if (openQuestionId) {
    return <KnowledgeQuestionDetailView questionId={openQuestionId} onBack={() => { setOpenQuestionId(null); load(); }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your reputation</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{reputation ?? '…'} adopted answer{reputation === 1 ? '' : 's'}</p>
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'BROWSE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('BROWSE')} style={{ flex: 1 }}
        >
          Browse
        </button>
        <button
          className={subTab === 'MINE' ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
          onClick={() => setSubTab('MINE')} style={{ flex: 1 }}
        >
          Mine
        </button>
      </div>
      {subTab === 'BROWSE' && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          <button
            className={activeCategory === null ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
            style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px' }} onClick={() => setActiveCategory(null)}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={activeCategory === c.id ? 'itunda-btn itunda-btn-primary' : 'itunda-btn itunda-btn-secondary'}
              style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '6px 12px' }} onClick={() => setActiveCategory(c.id)}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}
      {subTab === 'BROWSE' && <KnowledgeAskCard onAsked={load} categories={categories} />}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {questions === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Loading…</p>
      ) : questions.length === 0 ? (
        // Real copy-voice fix (item 244, round 6 of the empty-state pass): specific
        // to which subtab is showing -- BROWSE has the real "Ask a question" form
        // right above, MINE doesn't (it needs to point back to BROWSE instead).
        <EmptyState message={subTab === 'BROWSE' ? 'No questions yet — ask one above.' : "You haven't asked anything yet — switch to Browse to ask your first question."} />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column' }}>
          {questions.map((q) => (
            <button
              key={q.id} className="itunda-flat-section" style={{ textAlign: 'left', width: '100%' }}
              onClick={() => setOpenQuestionId(q.id)}
            >
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
                {q.adoptedAnswerId ? '✅ ' : ''}{q.title}
              </p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>
                {categories.find((c) => c.id === q.category)?.label ?? q.category}
              </p>
            </button>
          ))}
        </div>
      )}
      {subTab === 'MINE' && myAnswers !== null && myAnswers.length > 0 && (
        <div>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Your answers</h4>
          <div style={{ display: 'flex', flexDirection: 'column' }}>
            {myAnswers.map((a) => (
              <div key={a.id} className="itunda-flat-section">
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)' }}>{a.isAdopted ? '✅ Adopted' : 'Pending'}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{a.body}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function KnowledgeAskCard({ onAsked, categories }: { onAsked: () => void; categories: KnowledgeCategory[] }) {
  const { t } = useI18n();
  const [category, setCategory] = useState('');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={() => setOpen(true)}>
        + Ask a question
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!category || !title.trim() || !body.trim()) return;
    setSubmitting(true);
    setError(null);
    try {
      await postKnowledgeQuestion(category, title, body);
      setCategory(''); setTitle(''); setBody(''); setOpen(false);
      onAsked();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      >
        <option value="">Choose a category</option>
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} placeholder="Your question" onChange={(e) => setTitle(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
      />
      <textarea
        value={body} placeholder="Add more detail" onChange={(e) => setBody(e.target.value)} rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'vertical' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !category || !title.trim() || !body.trim()}>
        {submitting ? 'Posting…' : 'Post question'}
      </button>
    </form>
  );
}

function KnowledgeQuestionDetailView({ questionId, onBack }: { questionId: string; onBack: () => void }) {
  const { t } = useI18n();
  const [question, setQuestion] = useState<KnowledgeQuestion | null>(null);
  const [answers, setAnswers] = useState<KnowledgeAnswer[] | null>(null);
  const [answerBody, setAnswerBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [answering, setAnswering] = useState(false);
  const [busyAnswerId, setBusyAnswerId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    fetchKnowledgeQuestion(questionId).then(setQuestion).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchKnowledgeAnswers(questionId).then(setAnswers).catch(() => {});
  };

  useEffect(load, [questionId]);

  const handleAnswer = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!answerBody.trim()) return;
    setAnswering(true);
    setError(null);
    try {
      await postKnowledgeAnswer(questionId, answerBody);
      setAnswerBody('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAnswering(false);
    }
  };

  const handleAdopt = async (answerId: string) => {
    setBusyAnswerId(answerId);
    setError(null);
    try {
      await adoptKnowledgeAnswer(questionId, answerId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyAnswerId(null);
    }
  };

  const isAsker = !!question && !!currentUser && question.askerId === currentUser.id;

  return (
    <div>
      <button className="itunda-btn itunda-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      {!question && !error && <div className="skeleton" style={{ height: '120px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {question && (
        <div className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>{question.title}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', color: 'var(--itunda-grey-700)', whiteSpace: 'pre-wrap' }}>{question.body}</p>
        </div>
      )}
      <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px' }}>Answers</h3>
      {answers === null && <div className="skeleton" style={{ height: '80px', borderRadius: 'var(--itunda-radius-md)' }} />}
      {answers !== null && answers.length === 0 && (
        <EmptyState message="No answers yet -- be the first to help." />
      )}
      {answers !== null && answers.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', marginBottom: '12px' }}>
          {answers.map((a) => (
            <div
              key={a.id} className="itunda-flat-section"
              style={a.isAdopted ? { borderLeft: '2.5px solid var(--itunda-indigo)', paddingLeft: '10px' } : undefined}
            >
              {a.isAdopted && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 700, color: 'var(--itunda-indigo)', marginBottom: '4px' }}>✅ Adopted answer</p>}
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-900)' }}>{a.body}</p>
              {isAsker && !question?.adoptedAnswerId && (
                <button
                  className="itunda-btn itunda-btn-secondary" style={{ marginTop: '8px', fontSize: 'var(--itunda-type-scale-12-size)' }}
                  disabled={busyAnswerId === a.id} onClick={() => handleAdopt(a.id)}
                >
                  {busyAnswerId === a.id ? '…' : 'Adopt this answer'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}
      {!question?.adoptedAnswerId && (
        <form onSubmit={handleAnswer} style={{ display: 'flex', gap: '8px' }}>
          <input
            type="text" value={answerBody} onChange={(e) => setAnswerBody(e.target.value)} placeholder="Write an answer"
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={answering || !answerBody.trim()}>
            {answering ? '…' : 'Send'}
          </button>
        </form>
      )}
    </div>
  );
}

// Real 배민오더-style table/QR in-store ordering (item 155) -- see lib/dineIn.ts's own
// doc comment. Deliberately its own smaller, self-contained flow rather than bolted onto
// MenuView/OrderFoodView's already-tested delivery checkout: no address, no favorites/
// reorder (a real, honestly-narrower v1 scope than delivery ordering gets), just a
// restaurant → menu → table number → pay loop, reusing the exact same real menu-item/
// option-group cart helpers (eatsCartKey/eatsLineUnitPrice/eatsOptionsSummary) delivery
// ordering already proved out.
const DINE_IN_STATUS_LABEL: Record<DineInOrderStatus, string> = {
  PLACED: 'Placed',
  ACCEPTED: 'Accepted by restaurant',
  PREPARING: 'Preparing',
  SERVED: 'Served',
  CANCELLED: 'Cancelled — refunded',
};
const DINE_IN_STATUS_CHAIN: DineInOrderStatus[] = ['PLACED', 'ACCEPTED', 'PREPARING', 'SERVED'];

function DineInOrderCard({ order, action }: { order: DineInOrder; action?: React.ReactNode }) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{DINE_IN_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Table {order.tableNumber}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.notes && (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.notes}
        </p>
      )}
      {action}
    </div>
  );
}

function DineInRestaurantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantDineInOrders()
      .then(setOrders)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RESTAURANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: DineInOrder) => {
    const next = nextInChain(DINE_IN_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceDineInOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  const handleCancel = async (order: DineInOrder) => {
    setBusyOrderId(order.id);
    setError(null);
    try {
      await cancelDineInOrder(order.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Table orders for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(DINE_IN_STATUS_CHAIN, o.status);
          return (
            <DineInOrderCard
              key={o.id}
              order={o}
              action={
                (next || o.status === 'PLACED') && (
                  <div style={{ display: 'flex', gap: '8px' }}>
                    {next && (
                      <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                        {busyOrderId === o.id ? 'Updating…' : `Mark ${DINE_IN_STATUS_LABEL[next].toLowerCase()}`}
                      </button>
                    )}
                    {o.status === 'PLACED' && (
                      <button className="itunda-btn itunda-btn-secondary" disabled={busyOrderId === o.id} onClick={() => handleCancel(o)}>
                        Cancel
                      </button>
                    )}
                  </div>
                )
              }
            />
          );
        })}
      </div>
    </div>
  );
}

function DineInMenuView({ restaurant, onBack, onOrderPlaced }: { restaurant: ShoppingMerchant; onBack: () => void; onOrderPlaced: (order: DineInOrder) => void }) {
  const { t } = useI18n();
  const [menu, setMenu] = useState<{ businessName: string; products: MenuItem[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cart, setCart] = useState<Record<string, EatsCartLine>>({});
  const [expandedProductId, setExpandedProductId] = useState<string | null>(null);
  const [pendingChoices, setPendingChoices] = useState<Record<string, string>>({});
  const [tableNumber, setTableNumber] = useState('');
  const [notes, setNotes] = useState('');
  const [placing, setPlacing] = useState(false);
  const [showCheckout, setShowCheckout] = useState(false);

  useEffect(() => {
    fetchMenu(restaurant.merchantId)
      .then((r) => setMenu({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, line]) => line.quantity > 0);
  const cartCount = cartItems.reduce((sum, [, line]) => sum + line.quantity, 0);

  const setSimpleQty = (productId: string, qty: number) => {
    const key = eatsCartKey(productId, []);
    setCart((c) => ({ ...c, [key]: { productId, quantity: Math.max(0, qty), choiceIds: [] } }));
  };

  const toggleExpand = (productId: string) => {
    setPendingChoices({});
    setExpandedProductId((current) => (current === productId ? null : productId));
  };

  const addConfiguredToCart = (item: MenuItem) => {
    const groups = item.optionGroups ?? [];
    const choiceIds = groups.map((g) => pendingChoices[g.id]).filter((id): id is string => Boolean(id));
    if (choiceIds.length !== groups.length) return;
    const key = eatsCartKey(item.id, choiceIds);
    setCart((c) => ({ ...c, [key]: { productId: item.id, quantity: (c[key]?.quantity ?? 0) + 1, choiceIds } }));
    setPendingChoices({});
    setExpandedProductId(null);
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
    setPlacing(true);
    setError(null);
    try {
      const items = cartItems.map(([, line]) => ({
        menuItemId: line.productId, quantity: line.quantity,
        selectedChoiceIds: line.choiceIds.length ? line.choiceIds : undefined,
      }));
      const result = await placeDineInOrder(restaurant.merchantId, tableNumber.trim(), items, notes.trim() || undefined);
      onOrderPlaced(result.order);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setPlacing(false);
    }
  };

  if (error) {
    return (
      <div>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }

  if (menu === null) {
    return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to menu">
            <IconBack size={20} />
          </button>
          <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-14-size)' }}>
                <span>{item.name}{eatsOptionsSummary(item, line.choiceIds)} x{line.quantity}</span>
                <span>{(unitPrice * line.quantity).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <input
            type="text"
            value={tableNumber}
            onChange={(e) => setTableNumber(e.target.value)}
            placeholder="Table number (e.g. 12, Patio 3)"
            required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value.slice(0, 500))}
            placeholder="Notes (optional) -- e.g. No onions"
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={placing || !tableNumber.trim()}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to restaurants">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      {menu.products.length === 0 ? (
        <EmptyState message="This restaurant hasn't added menu items yet — check back soon." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: cartCount > 0 ? '80px' : 0 }}>
          {menu.products.map((item) => {
            const groups = item.optionGroups ?? [];
            const hasOptions = groups.length > 0;
            const simpleKey = eatsCartKey(item.id, []);
            const simpleQty = hasOptions ? 0 : (cart[simpleKey]?.quantity ?? 0);
            const isExpanded = expandedProductId === item.id;
            const allGroupsChosen = groups.every((g) => Boolean(pendingChoices[g.id]));
            return (
              <div key={item.id} style={{ padding: '12px 0' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{item.name}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
                  </div>
                  {hasOptions ? (
                    <button className="itunda-btn itunda-btn-secondary" onClick={() => toggleExpand(item.id)}>
                      {isExpanded ? 'Close' : 'Add'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {isExpanded && (
                  <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {groups.map((group) => (
                      <div key={group.id}>
                        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-grey-700)', marginBottom: '4px' }}>{group.name}</p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          {group.choices.map((choice) => (
                            <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                              <input
                                type="radio"
                                name={`group-${group.id}`}
                                checked={pendingChoices[group.id] === choice.id}
                                onChange={() => setPendingChoices((p) => ({ ...p, [group.id]: choice.id }))}
                              />
                              {choice.name}{choice.priceDelta ? ` (+${choice.priceDelta.toLocaleString()} RWF)` : ''}
                            </label>
                          ))}
                        </div>
                      </div>
                    ))}
                    <button
                      className="itunda-btn itunda-btn-primary"
                      disabled={!allGroupsChosen}
                      onClick={() => addConfiguredToCart(item)}
                    >
                      Add to order
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
      {cartCount > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCheckout(true)}
        >
          Review order ({cartCount} item{cartCount === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

function DineInCustomerView() {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<DineInOrder | null>(null);
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);

  useEffect(() => {
    fetchRestaurants().then(setRestaurants).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  }, []);

  useEffect(() => {
    if (view === 'ORDERS') {
      fetchMyDineInOrders().then(setOrders).catch(() => setOrders([]));
    }
  }, [view]);

  if (confirmed) {
    // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- this IS the
    // whole confirmation screen's content (docs/UI_UX_GUIDELINES.md §10).
    return (
      <div style={{ textAlign: 'center', padding: '28px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-22-size)', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '16px' }}>Table {confirmed.tableNumber}</p>
        <button className="itunda-btn itunda-btn-secondary" onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}>Done</button>
      </div>
    );
  }

  if (selected) {
    return <DineInMenuView restaurant={selected} onBack={() => setSelected(null)} onOrderPlaced={setConfirmed} />;
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : 'My orders'}
          </button>
        ))}
      </div>
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card throughout --
          a flat entity list (matching Android's VehicleValuationScreen/
          GroupAccountScreen precedent), no per-row divider (docs/UI_UX_GUIDELINES.md §10). */}
      {view === 'BROWSE' ? (
        error ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
        ) : restaurants === null ? (
          <div className="itunda-flat-section skeleton" style={{ height: '160px' }} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {restaurants.map((r) => (
              <button key={r.merchantId} onClick={() => setSelected(r)} style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none' }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.businessName}</p>
                {r.category && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.category}</p>}
              </button>
            ))}
          </div>
        )
      ) : orders === null ? (
        <div className="itunda-flat-section skeleton" style={{ height: '160px' }} />
      ) : orders.length === 0 ? (
        <EmptyState message="No table orders yet — they'll show up here as diners order from their table." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {orders.map((o) => <DineInOrderCard key={o.id} order={o} />)}
        </div>
      )}
    </div>
  );
}

function EatsView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  // Real fix (2026-08-19) -- same gap and same fix as MessagesView's identical
  // joinChatCode handling: a tapped ?joinEatsCode= link needs the Together-order sub-tab
  // pre-selected or GroupOrderView (which owns the actual auto-join effect) never mounts.
  const [mode, setMode] = useState<'ORDER' | 'TOGETHER' | 'DELIVER' | 'DINE_IN'>(() =>
    new URLSearchParams(window.location.search).has('joinEatsCode') ? 'TOGETHER' : 'ORDER'
  );

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['ORDER', 'TOGETHER', 'DELIVER', 'DINE_IN'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, whiteSpace: 'nowrap',
              color: mode === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: mode === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'ORDER' ? 'Order food' : v === 'TOGETHER' ? 'Together order' : v === 'DELIVER' ? 'Deliver' : 'Dine-in'}
          </button>
        ))}
      </div>
      {mode === 'ORDER' ? (
        <div>
          <PlatformMembershipCard />
          <EatsMembershipCard />
          <RestaurantOrdersView />
          <OrderFoodView onMessageSeller={onMessageSeller} />
        </div>
      ) : mode === 'TOGETHER' ? (
        <GroupOrderView />
      ) : mode === 'DELIVER' ? (
        <DeliverView />
      ) : (
        <div>
          <DineInRestaurantOrdersView />
          <DineInCustomerView />
        </div>
      )}
    </div>
  );
}

// Real 배달의민족 함께주문 (Baemin "Together Order") -- see lib/eats.ts's own doc
// comment for the full account. A join-code-shared cart in front of the same real
// checkout/payment path OrderFoodView already uses (GroupEatsOrderService.finalizeOrder
// calls the exact same backend EatsOrderService.placeOrder underneath).
function GroupOrderView() {
  const { t } = useI18n();
  const [groupOrderId, setGroupOrderId] = useState<string | null>(null);
  const [detail, setDetail] = useState<GroupEatsOrderDetail | null>(null);
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [restaurantId, setRestaurantId] = useState('');
  const [address, setAddress] = useState('');
  const [joinCodeInput, setJoinCodeInput] = useState('');
  const [menu, setMenu] = useState<MenuItem[] | null>(null);
  const [menuItemId, setMenuItemId] = useState('');
  const [qty, setQty] = useState(1);
  // My own accumulated selections, since setMyGroupEatsOrderItems replaces the
  // caller's entire item list on every call rather than incrementally appending --
  // this client keeps the running list locally and resends the whole thing each time,
  // same "resend full current state" convention the backend's own doc comment expects.
  const [myItems, setMyItems] = useState<{ menuItemId: string; quantity: number }[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [placedOrder, setPlacedOrder] = useState<EatsOrder | null>(null);
  // Real fix (2026-08-19): same "asking user code, instead use qr code" anti-pattern as
  // OpenChatCard -- see QrScanCamera's own doc comment for the shared itunda://... QR
  // payload convention. Typed code stays as a real fallback for voice/text sharing.
  const [qrDataUrl, setQrDataUrl] = useState<string | null>(null);
  const [scanUnavailable, setScanUnavailable] = useState(false);
  const [manualJoinEntry, setManualJoinEntry] = useState(false);

  useEffect(() => {
    fetchRestaurants().then(setRestaurants).catch(() => setRestaurants([]));
  }, []);

  useEffect(() => {
    const joinCode = detail?.groupOrder.joinCode;
    if (joinCode) {
      QRCode.toDataURL(`itunda://join-eats?code=${joinCode}`, { width: 180, margin: 1 }).then(setQrDataUrl).catch(() => setQrDataUrl(null));
    } else {
      setQrDataUrl(null);
    }
  }, [detail?.groupOrder.joinCode]);

  const refresh = (id: string) => {
    fetchGroupEatsOrder(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  // Real bug found live (2026-08-19, while verifying the share-link fix above): `detail`
  // was never populated on create or join, only after finalize -- both host and joiner
  // silently rendered a blank join code (and now, a missing QR/share button) the entire
  // time they were building their cart. Pre-existing gap, not introduced by this pass.
  useEffect(() => {
    if (groupOrderId) refresh(groupOrderId);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- refresh is a stable per-render closure over setDetail/setError, re-running on identity change would refetch every render
  }, [groupOrderId]);

  const handleCreate = async () => {
    if (!restaurantId || !address.trim()) return;
    setBusy(true);
    setError(null);
    try {
      const groupOrder = await createGroupEatsOrder(restaurantId, address.trim());
      setGroupOrderId(groupOrder.id);
      const menuResult = await fetchMenu(restaurantId);
      setMenu(menuResult.products);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const submitJoinCode = async (rawCode: string) => {
    const trimmed = rawCode.trim();
    if (!trimmed) return;
    setBusy(true);
    setError(null);
    try {
      const groupOrder = await joinGroupEatsOrder(trimmed);
      setGroupOrderId(groupOrder.id);
      const menuResult = await fetchMenu(groupOrder.restaurantId);
      setMenu(menuResult.products);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleJoin = () => void submitJoinCode(joinCodeInput);
  const handleScanJoin = (raw: string) => void submitJoinCode(parseQrParam(raw, 'code'));

  // Real remote-invite fix (2026-08-19) -- see buildJoinUrl's own doc comment. A
  // together-order invite is normally sent to friends who aren't in the room, so a
  // tapped link (via itunda talk/SMS/anywhere) should join immediately, same as
  // OpenChatCard's identical fix.
  useEffect(() => {
    const incoming = readAndClearUrlParam('joinEatsCode');
    if (incoming) void submitJoinCode(incoming);
    // eslint-disable-next-line react-hooks/exhaustive-deps -- run once on mount only
  }, []);

  const [shareStatus, setShareStatus] = useState<'idle' | 'shared' | 'copied' | 'failed'>('idle');
  const handleShare = async (code: string) => {
    const url = buildJoinUrl('EATS', 'joinEatsCode', code);
    const result = await shareOrCopyLink(url, 'Order together on itunda', 'Join my together order on itunda — tap to join instantly.');
    setShareStatus(result);
  };

  const handleAddItem = async () => {
    if (!groupOrderId || !menuItemId || qty < 1) return;
    setBusy(true);
    setError(null);
    try {
      const nextItems = [...myItems, { menuItemId, quantity: qty }];
      const nextDetail = await setMyGroupEatsOrderItems(groupOrderId, nextItems);
      setMyItems(nextItems);
      setDetail(nextDetail);
      setMenuItemId('');
      setQty(1);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleFinalize = async () => {
    if (!groupOrderId) return;
    setBusy(true);
    setError(null);
    try {
      const result = await finalizeGroupEatsOrder(groupOrderId);
      setPlacedOrder(result.order);
      refresh(groupOrderId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    if (!groupOrderId) return;
    setBusy(true);
    setError(null);
    try {
      await cancelGroupEatsOrder(groupOrderId);
      setGroupOrderId(null);
      setDetail(null);
      setMyItems([]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (placedOrder) {
    // Real fix (2026-08-24, flat-design sweep, docs/UI_UX_GUIDELINES.md §10):
    // dropped itunda-card -- the screen's only content in this state.
    return (
      <div style={{ padding: '10px 0' }}>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Order placed</h3>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Real order #{placedOrder.id.slice(-8)} placed for {placedOrder.totalAmount.toLocaleString()} RWF. Every other participant with items in the cart has been sent a real Dutch-pay request via Split Bill.
        </p>
      </div>
    );
  }

  if (!groupOrderId) {
    // Real fix (2026-08-24, flat-design sweep): 2 distinct sections shown together --
    // reused .itunda-flat-section for the section-boundary divider.
    return (
      <div>
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Start a together order</h3>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Share one restaurant's cart with friends -- everyone adds their own items, you place one real order, and itunda asks each of them for their own share afterward.
          </p>
          <select value={restaurantId} onChange={(e) => setRestaurantId(e.target.value)} className="itunda-input" style={{ marginBottom: '8px', width: '100%' }}>
            <option value="">Select a restaurant…</option>
            {(restaurants ?? []).map((r) => (
              <option key={r.merchantId} value={r.merchantId}>{r.businessName}</option>
            ))}
          </select>
          <input
            className="itunda-input"
            placeholder="Delivery address"
            value={address}
            onChange={(e) => setAddress(e.target.value)}
            style={{ marginBottom: '8px', width: '100%' }}
          />
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !restaurantId || !address.trim()} onClick={handleCreate} style={{ width: '100%' }}>
            {busy ? '…' : 'Start together order'}
          </button>
        </div>
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Join a together order</h3>
          {!manualJoinEntry ? (
            <>
              {!scanUnavailable && !busy && <QrScanCamera onDetect={handleScanJoin} onUnavailable={() => setScanUnavailable(true)} />}
              {busy && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>Joining…</p>}
              <button className="itunda-btn itunda-btn-secondary" onClick={() => setManualJoinEntry(true)} style={{ width: '100%' }}>
                {scanUnavailable ? 'Enter join code manually' : 'No camera? Enter join code'}
              </button>
            </>
          ) : (
            <>
              <input
                className="itunda-input"
                placeholder="e.g. K3F9XQ"
                value={joinCodeInput}
                onChange={(e) => setJoinCodeInput(e.target.value.toUpperCase())}
                autoFocus
                style={{ marginBottom: '8px', width: '100%' }}
              />
              <button className="itunda-btn itunda-btn-secondary" disabled={busy || !joinCodeInput.trim()} onClick={handleJoin} style={{ width: '100%' }}>
                {busy ? '…' : 'Join'}
              </button>
            </>
          )}
        </div>
        {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '10px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const currentUser = getStoredUser();
  const isHost = !!detail && !!currentUser && detail.groupOrder.hostUserId === currentUser.id;

  // Real fix (2026-08-24, flat-design sweep): 3 distinct sections shown together --
  // reused .itunda-flat-section for section-boundary dividers, :last-child auto-drops
  // the trailing one before the finalize/cancel buttons.
  return (
    <div>
      <div className="itunda-flat-section">
        <div style={{ display: 'flex', gap: '14px', alignItems: 'center', marginBottom: '10px' }}>
          {qrDataUrl && <img src={qrDataUrl} alt={`QR code to join order ${detail?.groupOrder.joinCode}`} width={72} height={72} style={{ borderRadius: '8px', flexShrink: 0 }} />}
          <div>
            <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Order together — {detail?.groupOrder.joinCode}</h3>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Send friends a link to join instantly, or let someone nearby scan the code.</p>
          </div>
        </div>
        {detail?.groupOrder.joinCode && (
          <button className="itunda-btn itunda-btn-primary" style={{ width: '100%' }} onClick={() => handleShare(detail.groupOrder.joinCode)}>
            <span style={{ display: 'inline-flex', alignItems: 'center', gap: '6px' }}><LinkGlyph size={16} /> Share invite link</span>
          </button>
        )}
        {shareStatus === 'copied' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-green)', marginTop: '6px' }}>Link copied</p>}
        {shareStatus === 'failed' && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '6px' }}>Could not copy the link — share the code above instead.</p>}
      </div>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '10px' }} role="alert">{error}</p>}
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Add your own item</h3>
        <select value={menuItemId} onChange={(e) => setMenuItemId(e.target.value)} className="itunda-input" style={{ marginBottom: '8px', width: '100%' }}>
          <option value="">Select an item…</option>
          {(menu ?? []).map((m) => (
            <option key={m.id} value={m.id}>{m.name} -- {m.price.toLocaleString()} RWF</option>
          ))}
        </select>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input type="number" min={1} value={qty} onChange={(e) => setQty(Number(e.target.value))} className="itunda-input" style={{ width: '80px' }} />
          <button className="itunda-btn itunda-btn-secondary" disabled={busy || !menuItemId} onClick={handleAddItem} style={{ flex: 1 }}>
            {busy ? '…' : 'Add to my cart'}
          </button>
        </div>
      </div>
      <div className="itunda-flat-section">
        <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '8px' }}>Everyone's items -- {detail?.grandTotal.toLocaleString() ?? 0} RWF total</h3>
        {/* Real, sourced Baemin UX writing finding (2026-08-24,
            bcut.baemin.com/6287, Baemin's own official UX writing blog on this exact
            함께주문/group-ordering feature): "함께주문을 쓸 때 대표로 주문하는 사람
            입장에선 몇 명이 골랐는지보다 몇 명이 아직 안 골랐는지가 더 중요한 정보"
            (from the lead orderer's perspective, who HASN'T picked yet matters more
            than who has) -- Baemin rewrote their own completed-count text to a
            remaining-count for exactly this reason. Only shown to the host: this is
            the same "matters to the lead orderer specifically" framing the article's
            own finding names, not a generic status line every participant needs. */}
        {isHost && detail && detail.participants.some((p) => p.items.length === 0) && (
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '8px' }}>
            {detail.participants.filter((p) => p.items.length === 0).length} of {detail.participants.length} haven't added items yet.
          </p>
        )}
        {(detail?.participants ?? []).map((p) => (
          <div key={p.userId} style={{ marginBottom: '10px', paddingBottom: '10px', borderBottom: '1px solid var(--itunda-grey-100)' }}>
            <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>
              {p.userId === detail?.groupOrder.hostUserId ? 'Host' : 'Participant'} -- {p.subtotal.toLocaleString()} RWF
            </p>
            {p.items.length === 0 ? (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>No items yet</p>
            ) : (
              p.items.map((i, idx) => (
                <p key={idx} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{i.quantity}x {i.productName} -- {i.lineTotal.toLocaleString()} RWF</p>
              ))
            )}
          </div>
        ))}
        <button className="itunda-btn" onClick={() => groupOrderId && refresh(groupOrderId)} style={{ width: '100%', fontSize: 'var(--itunda-type-scale-13-size)' }}>Refresh</button>
      </div>
      {isHost && (
        <>
          <button className="itunda-btn itunda-btn-primary" disabled={busy || !detail || detail.grandTotal <= 0} onClick={handleFinalize} style={{ width: '100%', marginBottom: '8px' }}>
            {busy ? '…' : 'Place the real order'}
          </button>
          <button className="itunda-btn" disabled={busy} onClick={handleCancel} style={{ width: '100%', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            Cancel group order
          </button>
        </>
      )}
    </div>
  );
}

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// lib/eats.ts's own doc comment. Deliberately a separate card from Eats Club below,
// not a replacement: this waives the fee at every restaurant, no merchant opt-in
// required, the same real broader guarantee Coupang Wow has over Baemin Club's
// participating-seller-only free delivery.
function PlatformMembershipCard() {
  const { t } = useI18n();
  const [membership, setMembership] = useState<PlatformMembership | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyPlatformMembership().then(setMembership).catch(() => setMembership(null));
  };
  useEffect(load, []);

  const isActive = membership != null && new Date(membership.activeUntil).getTime() > Date.now();

  const handleSubscribe = async (days: number) => {
    setBusy(true);
    setError(null);
    try {
      await subscribePlatformMembership(days);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  // Real bold hero-banner treatment (itunda Eats redesign, 2026-08-28) -- same real
  // copy/pricing as before, just matching the reference's own real Coupang WOW
  // banner visual weight (a real, already-live feature deserved better merchandising
  // than a plain subscribe card, not a new membership product).
  return (
    <div style={{ padding: '18px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-lg)', background: 'linear-gradient(135deg, var(--itunda-indigo), var(--itunda-indigo-active))', color: 'var(--itunda-white)' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 800, marginBottom: '4px' }}>⚡ itunda Plus</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-white)', opacity: 0.9, marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', opacity: 0.9 }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at every restaurant, no participation required.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', opacity: 0.9, marginBottom: '12px' }}>
            Free delivery at every restaurant, every order — no minimum, no restaurant opt-in required.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {PLATFORM_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, padding: '10px', borderRadius: 'var(--itunda-radius-md)', backgroundColor: 'var(--itunda-white)', color: 'var(--itunda-indigo)' }}
              >
                {busy ? '…' : `${tier.days} days -- ${tier.priceRwf.toLocaleString()} RWF`}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real Baemin Club (배민클럽)-style free-delivery membership -- see lib/eats.ts's own
// doc comment. First client UI for this backend feature (item 102, found backend-only
// via a fresh matrix scan for still-open "no client UI yet" notes).
function EatsMembershipCard() {
  const { t } = useI18n();
  const [membership, setMembership] = useState<EatsMembership | null | undefined>(undefined);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMyMembership().then(setMembership).catch(() => setMembership(null));
  };
  useEffect(load, []);

  const isActive = membership != null && new Date(membership.activeUntil).getTime() > Date.now();

  const handleSubscribe = async (days: number) => {
    setBusy(true);
    setError(null);
    try {
      await subscribeMembership(days);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  return (
    <div className="itunda-card" style={{ padding: '16px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, marginBottom: '4px' }}>Eats Club</h3>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at participating restaurants.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
            Free delivery at participating restaurants -- no minimum order.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {EATS_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                className="itunda-btn itunda-btn-primary"
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: 'var(--itunda-type-scale-13-size)' }}
              >
                {busy ? '…' : `${tier.days} days -- ${tier.priceRwf.toLocaleString()} RWF`}
              </button>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

const COMMERCE_STATUS_LABEL: Record<CommerceOrderStatus, string> = {
  PLACED: 'Placed',
  PACKED: 'Packed',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled — refunded',
};

const COMMERCE_STATUS_CHAIN: CommerceOrderStatus[] = ['PLACED', 'PACKED', 'SHIPPED', 'DELIVERED'];

function CommerceOrderCard({ order, action }: { order: CommerceOrder; action?: React.ReactNode }) {
  // Real live rider-location tracking (2026-08-05) -- see lib/commerce.ts's own
  // fetchOrderRiderLocation doc comment. Only ever real once a rider is actually en
  // route, matching OrderService.getRiderLocation's own real SHIPPED-only gate exactly.
  const [showLiveTracking, setShowLiveTracking] = useState(false);
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '10px 0' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-indigo)' }}>{COMMERCE_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.status === 'SHIPPED' && (
        <button className="itunda-btn itunda-btn-primary" onClick={() => setShowLiveTracking((v) => !v)}>
          {showLiveTracking ? 'Hide live tracking' : '🛵 Track your rider live'}
        </button>
      )}
      {showLiveTracking && <SimpleLiveRiderMap orderId={order.id} fetchLocation={fetchOrderRiderLocation} />}
      {action}
    </div>
  );
}

// Real post-delivery product reviews (2026-07-20), mirroring Eats' own
// RestaurantRatingBadge/ReviewOrderCard pattern -- see ProductReviewService's own doc
// comment for the full backend account. One real review per real delivered line item.
function ProductRatingBadge({ productId }: { productId: string }) {
  const [rating, setRating] = useState<{ average: number | null; count: number } | null>(null);
  const [open, setOpen] = useState(false);
  const [reviews, setReviews] = useState<ProductReview[] | null>(null);
  // Real "도움돼요" (helpful) toggle (2026-08-25) -- see lib/commerce.ts's own doc
  // comment, mirrors RestaurantRatingBadge's own identical treatment exactly.
  const [helpfulVoted, setHelpfulVoted] = useState<Set<string>>(new Set());

  const handleToggleHelpful = async (reviewId: string) => {
    try {
      const helpful = await toggleProductReviewHelpful(reviewId);
      setHelpfulVoted((prev) => {
        const next = new Set(prev);
        if (helpful) next.add(reviewId); else next.delete(reviewId);
        return next;
      });
      setReviews((prev) => prev?.map((r) => (r.id === reviewId ? { ...r, helpfulCount: (r.helpfulCount ?? 0) + (helpful ? 1 : -1) } : r)) ?? null);
    } catch {
      // Real, non-critical -- a failed helpful-vote shouldn't block reading reviews.
    }
  };

  useEffect(() => {
    fetchProductRating(productId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the catalog.
    });
  }, [productId]);

  const toggle = () => {
    const next = !open;
    setOpen(next);
    if (next && reviews === null) {
      fetchProductReviews(productId).then(setReviews).catch(() => setReviews([]));
    }
  };

  if (!rating || rating.count === 0) return null;
  return (
    <div>
      <button
        type="button"
        onClick={toggle}
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', padding: 0 }}
      >
        <IconStar size={13} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {reviews === null ? (
            <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet — be the first to share how it went." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span>
                {r.comment && <span> — {r.comment}</span>}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                    ↳ Seller: {r.ownerReply}
                  </div>
                )}
                <button
                  type="button" onClick={() => handleToggleHelpful(r.id)}
                  style={{ display: 'block', marginTop: '2px', fontSize: 'var(--itunda-type-scale-11-size)', color: helpfulVoted.has(r.id) ? 'var(--itunda-indigo)' : 'var(--itunda-grey-500)' }}
                >
                  👍 Helpful{r.helpfulCount ? ` (${r.helpfulCount})` : ''}
                </button>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

// Real bulk/wholesale pricing buyer-facing display -- see lib/commerce.ts's own
// fetchPriceTiers doc comment. merchant-mfe already has the owner-config half
// (PosScreen.tsx's "Pricing" panel); this is the first buyer-facing client anywhere.
// Real checkout money impact, not cosmetic: OrderService already applies the
// highest-qualifying tier automatically once the buyer's order quantity meets
// minQuantity, so this previews what the buyer will actually pay, not a label.
function PriceTiersDisplay({ productId, regularPrice }: { productId: string; regularPrice: number }) {
  const [tiers, setTiers] = useState<PriceTier[] | null>(null);

  useEffect(() => {
    fetchPriceTiers(productId)
      .then(setTiers)
      .catch(() => setTiers([]));
  }, [productId]);

  if (!tiers || tiers.length === 0) return null;

  return (
    <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--itunda-grey-100)' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, marginBottom: '6px' }}>Buy more, pay less</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
          <span>1+</span>
          <span>{regularPrice.toLocaleString()} RWF each</span>
        </div>
        {tiers.map((t) => (
          <div key={t.minQuantity} style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)', fontWeight: 600 }}>
            <span>{t.minQuantity}+</span>
            <span>{t.unitPrice.toLocaleString()} RWF each</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Coupang-style pre-purchase product Q&A (상품문의) (2026-07-26) -- see
// ProductInquiryService's own doc comment on the backend. Genuinely distinct from
// ProductRatingBadge's reviews above: no order/purchase required at all, so this is
// always visible on a product's detail page, not gated behind having bought it.
function ProductInquirySection({ productId }: { productId: string }) {
  const { t } = useI18n();
  const [inquiries, setInquiries] = useState<ProductInquiry[] | null>(null);
  const [question, setQuestion] = useState('');
  const [asking, setAsking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchProductInquiries(productId).then(setInquiries).catch(() => setInquiries([]));
  };

  useEffect(load, [productId]);

  const handleAsk = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!question.trim()) return;
    setAsking(true);
    setError(null);
    try {
      await askProductInquiry(productId, question.trim());
      setQuestion('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setAsking(false);
    }
  };

  return (
    <div style={{ borderTop: '1px solid var(--itunda-grey-100)', paddingTop: '12px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>Questions & answers</p>
      <form onSubmit={handleAsk} style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask the seller a question"
          style={{ flex: 1, padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
        />
        <button type="submit" className="itunda-btn itunda-btn-secondary" disabled={asking || !question.trim()} style={{ padding: '8px 14px' }}>
          Ask
        </button>
      </form>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginBottom: '8px' }} role="alert">{error}</p>}
      {inquiries === null ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Loading questions…</p>
      ) : inquiries.length === 0 ? (
        <EmptyState message="No questions yet -- be the first to ask." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {inquiries.map((q) => (
            <div key={q.id} style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>
              <span style={{ fontWeight: 700 }}>Q. </span>{q.question}
              {q.answer ? (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-500)' }}>
                  <span style={{ fontWeight: 700 }}>A. </span>{q.answer}
                </div>
              ) : (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--itunda-grey-400)', fontStyle: 'italic' }}>
                  Awaiting seller response
                </div>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function ProductReviewRow({ item }: { item: CommerceOrderItem }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [rating, setRating] = useState(0);
  const [comment, setComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (rating === 0) {
      setError('Pick a star rating.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await submitProductReview(item.id, rating, comment);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'PRODUCT_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : t('common.actionError'));
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{item.productName}: thanks for your review!</p>
    );
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ fontSize: 'var(--itunda-type-scale-12-size)', padding: '8px 12px' }} onClick={() => setOpen(true)}>
        Rate {item.productName}
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{item.productName}</p>
      <StarRatingInput value={rating} onChange={setRating} />
      <input
        type="text"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="How was it? (optional)"
        style={{ width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

function OrderItemReviews({ order }: { order: CommerceOrder }) {
  const [items, setItems] = useState<CommerceOrderItem[] | null>(null);

  useEffect(() => {
    fetchOrderDetail(order.id).then((r) => setItems(r.items)).catch(() => {
      // Real, non-critical -- if item fetch fails, the order card itself still renders fine.
    });
  }, [order.id]);

  if (!items || items.length === 0) return null;
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '4px' }}>
      {items.map((item) => <ProductReviewRow key={item.id} item={item} />)}
    </div>
  );
}

// Real Coupang-style post-delivery Return & Exchange request (item 166) -- see
// lib/commerce.ts's own doc comment. A real 7-day window from delivery, enforced
// server-side; this button stays offered regardless (a stale/expired attempt just
// real-errors with an honest message, same discipline as every other time-gated action
// in this app).
function ReturnExchangeAction({ orderId }: { orderId: string }) {
  const { t } = useI18n();
  const [open, setOpen] = useState(false);
  const [type, setType] = useState<OrderReturnType>('RETURN');
  const [reasonCode, setReasonCode] = useState<string>(ORDER_RETURN_REASON_CODES[0]);
  const [reasonNote, setReasonNote] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<OrderReturnRequestDto | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      const req = await requestOrderReturn(orderId, type, reasonCode, reasonNote.trim() || undefined);
      setResult(req);
      setOpen(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', fontWeight: 600, marginTop: '6px' }}>{result.type === 'RETURN' ? 'Return' : 'Exchange'} requested — awaiting seller review.</p>;
  }

  if (!open) {
    return (
      <button className="itunda-btn itunda-btn-secondary" style={{ marginTop: '6px', padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => setOpen(true)}>
        Return or exchange
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px', padding: '10px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <select value={type} onChange={(e) => setType(e.target.value as OrderReturnType)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          <option value="RETURN">Return</option>
          <option value="EXCHANGE">Exchange</option>
        </select>
        <select value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          {ORDER_RETURN_REASON_CODES.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ').toLowerCase()}</option>)}
        </select>
      </div>
      <input
        type="text"
        value={reasonNote}
        onChange={(e) => setReasonNote(e.target.value)}
        placeholder="Details (optional)"
        style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
      />
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting} style={{ flex: 1, padding: '8px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
          {submitting ? 'Submitting…' : 'Submit request'}
        </button>
        <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => setOpen(false)} style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-13-size)' }}>Cancel</button>
      </div>
    </form>
  );
}

function MyReturnRequestsView() {
  const [requests, setRequests] = useState<OrderReturnRequestDto[] | null>(null);

  useEffect(() => {
    fetchMyReturnRequests().then(setRequests).catch(() => setRequests([]));
  }, []);

  if (!requests || requests.length === 0) return null;

  return (
    <div style={{ marginBottom: '16px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>My return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {requests.map((r) => (
          <div key={r.id} className="itunda-flat-section" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</p>
            </div>
            <span style={{
              fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
              color: r.status === 'APPROVED' ? 'var(--itunda-green)' : r.status === 'REJECTED' ? 'var(--itunda-red)' : 'var(--itunda-indigo)',
            }}>
              {r.status === 'REQUESTED' ? 'Pending' : r.status === 'APPROVED' ? 'Approved' : 'Rejected'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function MerchantReturnQueueView() {
  const { t } = useI18n();
  const [requests, setRequests] = useState<OrderReturnRequestDto[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const load = () => {
    fetchMerchantReturnQueue()
      .then(setRequests)
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setRequests([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 8000);
    return () => clearInterval(interval);
  }, []);

  const handleDecide = async (id: string, approve: boolean) => {
    setBusyId(id);
    setError(null);
    try {
      await decideOrderReturn(id, approve);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyId(null);
    }
  };

  if (error) {
    return (
      <div style={{ marginBottom: '16px' }}>
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>
      </div>
    );
  }
  if (requests === null) return <div className="skeleton" style={{ height: '80px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)' }} />;
  const open = requests.filter((r) => r.status === 'REQUESTED');
  if (open.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        {open.map((r) => (
          <div key={r.id} className="itunda-flat-section" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'} requested</p>
              <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</span>
            </div>
            {r.reasonNote && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)' }}>{r.reasonNote}</p>}
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="itunda-btn itunda-btn-primary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, true)}>
                {busyId === r.id ? '…' : 'Approve'}
              </button>
              <button className="itunda-btn itunda-btn-secondary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, false)}>
                Reject
              </button>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real gift-voucher redemption (2026-08-16, Kakao 기프티콘-sourced) -- closes the
// terminal step of an already-shipped feature: a recipient could receive a voucher via
// purchaseGiftVoucher but no merchant had any way to actually redeem one. The recipient
// presents the voucher's id in person (same "physical presentation" convention as a
// paper gifticon barcode); the merchant types it in here. See
// GiftVoucherService.redeemVoucher's own doc comment for why this must be
// merchant-authenticated rather than recipient self-serve.
function MerchantRedeemVoucherCard() {
  const { t } = useI18n();
  const [voucherId, setVoucherId] = useState('');
  const [redeemed, setRedeemed] = useState<GiftVoucher | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setRedeemed(null);
    const trimmed = voucherId.trim();
    if (!trimmed) return;
    setSubmitting(true);
    try {
      const voucher = await redeemGiftVoucher(trimmed);
      setRedeemed(voucher);
      setVoucherId('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '4px' }}>Redeem a gift voucher</h4>
      <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginBottom: '10px' }}>
        Ask the customer for their voucher id and enter it below to redeem it in person.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text"
          value={voucherId}
          onChange={(e) => setVoucherId(e.target.value)}
          placeholder="giftvoucher_..."
          className="itunda-input"
          style={{ flex: 1 }}
        />
        <button type="submit" className="itunda-btn itunda-btn-primary" disabled={submitting || !voucherId.trim()}>
          {submitting ? 'Redeeming…' : 'Redeem'}
        </button>
      </form>
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{error}</p>}
      {redeemed && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-green-600, #16a34a)', marginTop: '8px' }}>
          ✅ Redeemed {redeemed.productNameSnapshot ?? `${redeemed.amount.toLocaleString()} RWF`}
        </p>
      )}
    </div>
  );
}

// Real cross-merchant cart (2026-07-20) -- closes the "real Coupang splits a
// multi-seller cart into per-seller orders, not attempted here" simplification this
// row's own text named. Keyed by merchantId so a buyer can browse merchant A, add
// items, go back, browse merchant B, add items there too, and check out everything
// in one pass -- each merchant's line items get a real, separate placeOrder() call
// (the backend already only ever accepted one merchantId per order; no backend
// change needed at all, this is purely a client-side cart-architecture change).
interface CommerceCartGroup {
  businessName: string;
  lines: Record<string, { product: CommerceProduct; quantity: number }>;
}
type CommerceCart = Record<string, CommerceCartGroup>;

function cartTotalItems(cart: CommerceCart): number {
  return Object.values(cart).reduce((sum, group) => sum + Object.values(group.lines).reduce((s, l) => s + l.quantity, 0), 0);
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

// Real product-image thumbnail (2026-07-21) -- imageUrl is a merchant-supplied external
// URL (see backend MerchantProduct.kt's own doc comment: no upload/storage layer exists
// in this backend, so this is a real "bring your own URL" v1, not a fake pipeline). A
// plain <img> with onError falling back to the same placeholder icon shown for a
// product that simply has no image set at all -- both are real, valid states.
function ProductImageThumb({ imageUrl, size = 96 }: { imageUrl?: string | null; size?: number }) {
  const [failed, setFailed] = useState(false);
  if (!imageUrl || failed) {
    return (
      <div style={{ width: size, height: size, borderRadius: '12px', background: 'var(--itunda-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <ShoppingBag size={size * 0.4} color="var(--itunda-indigo)" />
      </div>
    );
  }
  return (
    <img
      src={imageUrl}
      alt=""
      onError={() => setFailed(true)}
      style={{ width: size, height: size, borderRadius: '12px', objectFit: 'cover', background: 'var(--itunda-grey-100)', flexShrink: 0 }}
    />
  );
}

// Real discount-price display (2026-07-21) -- Baymard Institute's own placement
// research (docs/DESIGN_REFERENCES.md Section 5): the discount % must sit immediately
// next to the struck-through original price. discountPercent is always server-computed
// (see backend doc comment), never trusted from the client -- purely a rendering of
// numbers the server already validated.
function ProductPriceBlock({ price, originalPrice, discountPercent }: { price: number; originalPrice?: number | null; discountPercent?: number | null }) {
  if (originalPrice != null && discountPercent != null && discountPercent > 0) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'baseline', gap: '6px' }}>
          <span style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700, color: 'var(--itunda-red)' }}>{discountPercent}%</span>
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{price.toLocaleString()} RWF</span>
        </div>
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-400)', textDecoration: 'line-through' }}>{originalPrice.toLocaleString()} RWF</p>
      </div>
    );
  }
  return <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{price.toLocaleString()} RWF</p>;
}

// Real dedicated product-detail screen (2026-07-21), closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #6 -- until now tapping a product
// anywhere in Commerce only ever revealed the flat catalog grid's inline qty stepper;
// there was no tap-through view showing the full-size image, the discount breakdown, a
// description, and the written reviews together. Reuses every already-proven piece
// rather than inventing new ones: ProductImageThumb (larger), ProductPriceBlock,
// ProductRatingBadge (which already lazily expands into the real written-review list),
// and the same wishlist toggle/qty-stepper/add-to-cart plumbing ProductCatalogView
// already has -- this is a real second surface for the same real data, not new business
// logic.
function ProductDetailView({
  merchant, product, cart, onSetQty, onBack, onViewCart, onContactSeller,
}: {
  merchant: ShoppingMerchant;
  product: CommerceProduct;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
  onContactSeller: () => void;
}) {
  const [favorited, setFavorited] = useState(false);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    fetchMyFavoriteProducts()
      .then((favorites) => setFavorited(favorites.some((f) => f.productId === product.id)))
      .catch(() => {
        // Real, non-critical -- a wishlist-status fetch failure shouldn't block viewing.
      });
  }, [product.id]);

  // Real Coupang WING 상품분석 (product analytics) view count (2026-08-16) -- fetches
  // the real, freshly server-incremented count once per detail-view mount, same
  // "non-critical, falls back to nothing on failure" discipline the favorite-status
  // fetch above already establishes. This is also the real trigger the pre-existing
  // GET /shopping/products/{id} endpoint needed to ever be called at all.
  const [freshViewCount, setFreshViewCount] = useState<number | null>(null);
  useEffect(() => {
    fetchProduct(product.id).then((p) => setFreshViewCount(p.viewCount ?? null)).catch(() => {});
  }, [product.id]);

  const toggleFavorite = async () => {
    setBusy(true);
    try {
      if (favorited) {
        await removeProductFavorite(product.id);
        setFavorited(false);
      } else {
        await addProductFavorite(product.id);
        setFavorited(true);
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block viewing.
    } finally {
      setBusy(false);
    }
  };

  const qty = cart[merchant.merchantId]?.lines[product.id]?.quantity ?? 0;
  const totalCartItems = cartTotalItems(cart);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to catalog">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, flex: 1 }}>{merchant.businessName}</h3>
        <ShopMessageSellerButton onClick={onContactSeller} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '14px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <ProductImageThumb imageUrl={product.imageUrl} size={220} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
          <div>
            <p style={{ fontSize: 'var(--itunda-type-scale-18-size)', fontWeight: 700 }}>{product.name}</p>
            <ProductPriceBlock price={product.price} originalPrice={product.originalPrice} discountPercent={product.discountPercent} />
            {freshViewCount != null && (
              <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '2px' }}>Views {freshViewCount.toLocaleString()}</p>
            )}
          </div>
          <WishlistButton favorited={favorited} busy={busy} onToggle={toggleFavorite} />
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', flexWrap: 'wrap' }}>
          <ProductRatingBadge productId={product.id} />
          {product.isBestSeller && <ShopBestSellerBadge />}
          <ShopDeliveryEtaPill minutes={merchant.deliveryTimeMinutes} />
        </div>
        {product.description && (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{product.description}</p>
        )}
        <PriceTiersDisplay productId={product.id} regularPrice={product.price} />
        {/* BookingWidget/MerchantBookingInfoSection moved to itunda Place (2026-08-25)
            -- see maps-mfe's own MapsBooking.tsx doc comment. This product-detail page
            no longer reaches a bookable-service product at all (see the catalog fetch
            above's own filter), so there's nothing left to render here for booking. */}
        <ProductInquirySection productId={product.id} />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '16px', paddingTop: '4px', borderTop: '1px solid var(--itunda-grey-100)' }}>
          <button onClick={() => onSetQty(merchant, product, Math.max(0, qty - 1))} className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 16px' }}>−</button>
          <span style={{ minWidth: '24px', textAlign: 'center', fontWeight: 700, fontSize: 'var(--itunda-type-scale-16-size)' }}>{qty}</span>
          <button onClick={() => onSetQty(merchant, product, qty + 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '8px 16px' }}>+</button>
        </div>
        <SubscribeAndSaveButton merchantId={merchant.merchantId} productId={product.id} />
        <button className="itunda-btn itunda-btn-primary" onClick={() => onSetQty(merchant, product, Math.max(1, qty))}>
          {qty > 0 ? 'Update cart' : 'Add to cart'}
        </button>
      </div>
      {totalCartItems > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={onViewCart}
        >
          View cart ({totalCartItems} item{totalCartItems === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

function ProductCatalogView({
  merchant, cart, onSetQty, onBack, onViewCart, onOpenProduct, onContactSeller,
}: {
  merchant: ShoppingMerchant;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
  onOpenProduct: (product: CommerceProduct) => void;
  onContactSeller: () => void;
}) {
  const { t } = useI18n();
  const [catalog, setCatalog] = useState<{ businessName: string; products: CommerceProduct[] } | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [favoritedIds, setFavoritedIds] = useState<Set<string>>(new Set());
  const [togglingId, setTogglingId] = useState<string | null>(null);
  const [following, setFollowing] = useState(false);
  const [followBusy, setFollowBusy] = useState(false);
  const [billingPlans, setBillingPlans] = useState<MerchantBillingPlan[]>([]);
  const [mySubscriptions, setMySubscriptions] = useState<MerchantBillingSubscription[]>([]);

  const load = () => {
    setError(null);
    fetchMerchantProducts(merchant.merchantId)
      // Real filter (2026-08-25, direct user feedback: "booking... supposed to be in
      // itunda place not in itunda shopping") -- a product with a real durationMinutes
      // set is a real-time appointment at this merchant's physical location, not a
      // cart-able online good, so it no longer shows in Shop's own catalog at all.
      // Booking now lives in itunda Place (maps-mfe's own MapsBooking.tsx), reachable
      // from the same real merchant pinned on the map.
      .then((r) => setCatalog({ businessName: r.merchant.businessName, products: r.products.filter((p) => p.durationMinutes == null) }))
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
    fetchMyFavoriteProducts()
      .then((favorites) => setFavoritedIds(new Set(favorites.map((f) => f.productId))))
      .catch(() => {
        // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
      });
    // Real Naver Smart Store-style "알림받기" follow status -- non-critical, same
    // discipline as the wishlist fetch above.
    fetchMyFollowedMerchants()
      .then((follows) => setFollowing(follows.some((f) => f.merchantId === merchant.merchantId)))
      .catch(() => {});
    // Real Kakao Pay 정기결제/Toss 빌링키-style recurring billing plans this merchant
    // itself has published -- non-critical, same discipline as follow/wishlist above.
    fetchMerchantBillingPlans(merchant.merchantId)
      .then(setBillingPlans)
      .catch(() => {});
    fetchMyBillingSubscriptions()
      .then((subs) => setMySubscriptions(subs.filter((s) => s.merchantId === merchant.merchantId)))
      .catch(() => {});
  };

  useEffect(load, [merchant.merchantId]);

  const toggleFollow = async () => {
    setFollowBusy(true);
    try {
      if (following) {
        await unfollowMerchant(merchant.merchantId);
        setFollowing(false);
      } else {
        await followMerchant(merchant.merchantId);
        setFollowing(true);
      }
    } catch {
      // Real, non-critical -- a follow-toggle failure shouldn't block browsing.
    } finally {
      setFollowBusy(false);
    }
  };

  const toggleFavorite = async (productId: string) => {
    setTogglingId(productId);
    try {
      if (favoritedIds.has(productId)) {
        await removeProductFavorite(productId);
        setFavoritedIds((prev) => { const next = new Set(prev); next.delete(productId); return next; });
      } else {
        await addProductFavorite(productId);
        setFavoritedIds((prev) => new Set(prev).add(productId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setTogglingId(null);
    }
  };

  // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link generation (item 229) --
  // see lib/affiliate.ts's own doc comment. Any user can generate a real trackable
  // link for any product and earns a real 3% commission on a resulting purchase.
  const [sharingId, setSharingId] = useState<string | null>(null);
  const [shareNotice, setShareNotice] = useState<string | null>(null);
  const shareProduct = async (productId: string) => {
    setSharingId(productId);
    setShareNotice(null);
    try {
      const link = await createAffiliateLink(productId);
      const url = `${window.location.origin}${window.location.pathname}?ref=${link.code}`;
      await navigator.clipboard.writeText(url).catch(() => {});
      setShareNotice('Link copied — earn 3% on any purchase through it.');
    } catch (err) {
      setShareNotice(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setSharingId(null);
      setTimeout(() => setShareNotice(null), 4000);
    }
  };

  const myLines = cart[merchant.merchantId]?.lines ?? {};
  const qtyFor = (productId: string) => myLines[productId]?.quantity ?? 0;
  const totalCartItems = cartTotalItems(cart);

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (catalog === null) {
    return <div className="skeleton" style={{ height: '220px', borderRadius: 'var(--itunda-radius-md)' }} />;
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to merchants">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, flex: 1 }}>{catalog.businessName}</h3>
        <ShopMessageSellerButton onClick={onContactSeller} />
        <button
          type="button"
          onClick={toggleFollow}
          disabled={followBusy}
          className={following ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
          style={{ padding: '6px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}
        >
          {following ? 'Following' : 'Follow'}
        </button>
      </div>
      {shareNotice && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-indigo)', marginBottom: '12px' }} role="status">{shareNotice}</p>}
      {billingPlans.length > 0 && (
        <div style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>Subscription plans</h4>
          {billingPlans.map((plan) => (
            <BillingPlanRow
              key={plan.id}
              plan={plan}
              subscription={mySubscriptions.find((s) => s.planId === plan.id && s.status === 'ACTIVE')}
              onChanged={load}
            />
          ))}
        </div>
      )}
      {catalog.products.length === 0 ? (
        <EmptyState message="This store hasn't added products yet — check back soon." />
      ) : (
        // Real 2-column image-led grid (2026-07-21), replacing the previous
        // single-column text-only row -- closes docs/DESIGN_REFERENCES.md Section 5
        // recommendation #5 (Chloe Youn's Coupang case study: real cards are
        // image-led, with add-to-cart/wishlist directly on the card, not buried behind
        // a detail-page visit -- recommendation #7).
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
          {catalog.products.map((item) => (
            <div key={item.id} className="itunda-card" style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: '6px' }}>
              <div style={{ position: 'absolute', top: '10px', right: '10px', zIndex: 1, display: 'flex', flexDirection: 'column', gap: '6px', alignItems: 'flex-end' }}>
                <WishlistButton
                  favorited={favoritedIds.has(item.id)}
                  busy={togglingId === item.id}
                  onToggle={() => toggleFavorite(item.id)}
                />
                <button
                  type="button"
                  onClick={() => shareProduct(item.id)}
                  disabled={sharingId === item.id}
                  aria-label="Share this product and earn a commission"
                  title="Share & earn 3%"
                  style={{ background: 'var(--itunda-white)', borderRadius: '999px', padding: '6px', boxShadow: '0 1px 4px rgba(0,0,0,0.12)', fontSize: 'var(--itunda-type-scale-13-size)' }}
                >
                  🔗
                </button>
              </div>
              {/* Real tap-through to the new product-detail screen (2026-07-21) -- see
                  ProductDetailView's own doc comment. Wraps only the image/name/price so
                  the wishlist heart above stays independently tappable. */}
              <button
                type="button"
                onClick={() => onOpenProduct(item)}
                aria-label={`View ${item.name}`}
                style={{ display: 'flex', flexDirection: 'column', gap: '6px', textAlign: 'left', width: '100%', padding: 0 }}
              >
                <ProductImageThumb imageUrl={item.imageUrl} />
                <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, lineHeight: 1.3 }}>{item.name}</p>
                <ProductPriceBlock price={item.price} originalPrice={item.originalPrice} discountPercent={item.discountPercent} />
              </button>
              <p style={{ minHeight: '16px', fontSize: 'var(--itunda-type-scale-12-size)', color: item.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                {item.stockQuantity === null || item.stockQuantity === undefined ? 'Available' : item.stockQuantity === 0 ? 'Out of stock' : `${item.stockQuantity} available`}
              </p>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexWrap: 'wrap' }}>
                <ProductRatingBadge productId={item.id} />
                {item.isBestSeller && <ShopBestSellerBadge />}
              </div>
              <ShopDeliveryEtaPill minutes={merchant.deliveryTimeMinutes} />
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px', marginTop: '4px' }}>
                <button onClick={() => onSetQty(merchant, item, qtyFor(item.id) - 1)} className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{qtyFor(item.id)}</span>
                <button
                  onClick={() => onSetQty(merchant, item, qtyFor(item.id) + 1)}
                  disabled={item.stockQuantity !== null && item.stockQuantity !== undefined && qtyFor(item.id) >= item.stockQuantity}
                  className="itunda-btn itunda-btn-secondary"
                  style={{ padding: '6px 12px' }}
                >+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {totalCartItems > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={onViewCart}
        >
          View cart ({totalCartItems} item{totalCartItems === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

// Real Kakao Pay 정기결제/Toss 빌링키-style subscribe/cancel -- subscribing charges the
// first cycle immediately (real "인증 + 첫결제"), same as MerchantBillingService.subscribe's
// own doc comment. One real active subscription per plan; cancelling stops future
// charges but doesn't refund the current cycle already paid for.
function BillingPlanRow({ plan, subscription, onChanged }: { plan: MerchantBillingPlan; subscription?: MerchantBillingSubscription; onChanged: () => void }) {
  const { t } = useI18n();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubscribe = async () => {
    setError(null);
    setBusy(true);
    try {
      await subscribeToBillingPlan(plan.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  const handleCancel = async () => {
    if (!subscription) return;
    setError(null);
    setBusy(true);
    try {
      await cancelBillingSubscription(subscription.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--itunda-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{plan.name}</p>
          {plan.description && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-700)' }}>{plan.description}</p>}
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-700)', marginTop: '2px' }}>
            {plan.amount.toLocaleString()} RWF every {plan.intervalDays} day{plan.intervalDays === 1 ? '' : 's'}
          </p>
        </div>
        <button
          className={subscription ? 'itunda-btn itunda-btn-secondary' : 'itunda-btn itunda-btn-primary'}
          style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)', whiteSpace: 'nowrap' }}
          disabled={busy}
          onClick={subscription ? handleCancel : handleSubscribe}
        >
          {busy ? '…' : subscription ? 'Cancel' : 'Subscribe'}
        </button>
      </div>
      {subscription && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>
          Next charge {new Date(subscription.nextChargeAt).toLocaleDateString()}
        </p>
      )}
      {error && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', margin: 0 }} role="alert">{error}</p>}
    </div>
  );
}

interface CommerceCheckoutResult {
  merchantId: string;
  businessName: string;
  success: boolean;
  order?: CommerceOrder;
  error?: string;
}

// Real Toss "밀어서 결제하기" (swipe to pay) primitive (2026-08-25, direct user
// screenshot of Toss Shopping's real checkout sheet) -- Toss's own signature payment
// gesture, mirrors Android's SwipeToConfirmButton exactly (see that file's own doc
// comment for the full account: enabled gates dragging, busy freezes mid-swipe with a
// label swap, and a real failed attempt (busy clears without the caller navigating
// away) springs the handle back to the start so the buyer can retry).
function SwipeToConfirmButton({ label, busyLabel, enabled, busy, onConfirm }: { label: string; busyLabel: string; enabled: boolean; busy: boolean; onConfirm: () => void }) {
  const trackRef = useRef<HTMLDivElement>(null);
  const [trackWidth, setTrackWidth] = useState(0);
  const x = useMotionValue(0);
  const controls = useAnimation();
  const handleSize = 48;

  useEffect(() => {
    if (trackRef.current) setTrackWidth(trackRef.current.offsetWidth);
  }, []);

  const maxOffset = Math.max(0, trackWidth - handleSize - 8);

  useEffect(() => {
    if (!busy && enabled && x.get() > 0) {
      controls.start({ x: 0, transition: { type: 'spring', ...itundaSpring.bounce } });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [busy, enabled]);

  return (
    <div
      ref={trackRef}
      style={{
        position: 'relative', width: '100%', height: '56px', borderRadius: '28px',
        background: enabled || busy ? 'var(--itunda-indigo)' : 'var(--itunda-grey-300)', overflow: 'hidden',
      }}
    >
      <p style={{ position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', color: 'white', fontWeight: 700, fontSize: 'var(--itunda-type-scale-15-size)', paddingLeft: `${handleSize}px`, margin: 0, pointerEvents: 'none' }}>
        {busy ? busyLabel : label}
      </p>
      <motion.div
        drag={enabled && !busy ? 'x' : false}
        dragConstraints={{ left: 0, right: maxOffset }}
        dragElastic={0}
        dragMomentum={false}
        animate={controls}
        style={{
          x, position: 'absolute', top: '4px', left: '4px', width: `${handleSize}px`, height: `${handleSize}px`,
          borderRadius: '50%', background: 'white', display: 'flex', alignItems: 'center', justifyContent: 'center', touchAction: 'none',
        }}
        onDragEnd={() => {
          if (maxOffset > 0 && x.get() >= maxOffset * 0.8) {
            controls.start({ x: maxOffset, transition: { type: 'spring', ...itundaSpring.quick } }).then(() => onConfirm());
          } else {
            controls.start({ x: 0, transition: { type: 'spring', ...itundaSpring.bounce } });
          }
        }}
      >
        <IconChevronRight size={20} color="var(--itunda-indigo)" />
      </motion.div>
    </div>
  );
}

function MultiCartView({
  cart, onBack, onSetQty, onCheckedOut,
}: {
  cart: CommerceCart;
  onBack: () => void;
  onSetQty: (merchantId: string, productId: string, quantity: number) => void;
  onCheckedOut: (results: CommerceCheckoutResult[]) => void;
}) {
  const { t } = useI18n();
  const [address, setAddress] = useState('');
  const [placing, setPlacing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real device binding step-up (2026-07-21) -- Commerce checkout was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic per-order failure, same fix already applied to
  // Transfer/Savings. Every order in this batch shares the same device/session, so
  // hitting this once means every remaining order would fail identically -- the loop
  // below stops at the first one rather than collecting N duplicate failures.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10): this loop places one real order per merchant sequentially
  // and used to stop dead at the first DEVICE_NOT_VERIFIED, requiring the buyer to
  // resubmit the whole cart by hand -- which, naively retried, would have RE-PLACED
  // every order that already succeeded before the failing one (a real duplicate-order
  // bug, not just friction). These two refs let a retry resume from exactly the
  // merchant that failed, keeping every already-placed order's result instead of
  // restarting the loop from scratch.
  const checkoutResultsRef = useRef<CommerceCheckoutResult[]>([]);
  const checkoutResumeIndexRef = useRef(0);
  // Real Uber/Kakao T-style saved-places quick-select (2026-08-23) -- same real gap
  // already closed for ride booking and Eats delivery address: itunda's own "map
  // bookmarks" feature was never surfaced in Commerce checkout's own delivery-address
  // field either. Unlike those two, this field has no coordinate-capture at all
  // (placeOrder's own real contract only ever takes a plain deliveryAddress string,
  // no lat/lng) -- a plain tap-to-fill chip row rather than a full search-autocomplete
  // dropdown, since there's no coordinate value a real autocomplete would add here.
  const [bookmarks, setBookmarks] = useState<MapBookmark[]>([]);
  useEffect(() => {
    fetchMyMapBookmarks().then(setBookmarks).catch(() => {});
  }, []);

  const groups = Object.entries(cart).filter(([, g]) => Object.values(g.lines).some((l) => l.quantity > 0));
  const grandTotal = groups.reduce(
    (sum, [, g]) => sum + Object.values(g.lines).reduce((s, l) => s + l.product.price * l.quantity, 0),
    0,
  );

  // Real per-seller order splitting -- each merchant group becomes its own real,
  // independent placeOrder() call (its own Idempotency-Key, its own account-to-account
  // ledger transaction). Sequential, not Promise.all: these are real money-moving
  // calls against the same buyer account, and a clear one-at-a-time result list is
  // more honest than a swallowed Promise.allSettled. A failure on one merchant's
  // order does not block or roll back any other -- exactly how a real multi-seller
  // checkout behaves (each seller is charged/fulfilled independently in real life).
  const handlePlaceOrders = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setPlacing(true);
    setError(null);
    setNeedsDeviceVerification(false);
    for (let i = checkoutResumeIndexRef.current; i < groups.length; i++) {
      const [merchantId, group] = groups[i];
      const items = Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => ({ productId, quantity: l.quantity }));
      try {
        const result = await placeOrder(merchantId, items, address.trim(), getStoredReferralCode());
        checkoutResultsRef.current.push({ merchantId, businessName: group.businessName, success: true, order: result.order });
      } catch (err) {
        if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
          checkoutResumeIndexRef.current = i;
          setNeedsDeviceVerification(true);
          setPlacing(false);
          return;
        }
        checkoutResultsRef.current.push({ merchantId, businessName: group.businessName, success: false, error: err instanceof ApiError ? err.message : t('common.actionError') });
      }
    }
    setPlacing(false);
    onCheckedOut(checkoutResultsRef.current);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--itunda-grey-700)', padding: '4px' }} aria-label="Back to shop">
          <IconBack size={20} />
        </button>
        <h3 style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700 }}>Your cart</h3>
      </div>
      {groups.length === 0 ? (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>Your cart is empty.</p>
      ) : (
        <form onSubmit={handlePlaceOrders} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {groups.map(([merchantId, group]) => (
            <div key={merchantId} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{group.businessName}</p>
              {Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => (
                <div key={productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)' }}>
                  <span>{l.product.name} x{l.quantity}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span>{(l.product.price * l.quantity).toLocaleString()} RWF</span>
                    <button type="button" onClick={() => onSetQty(merchantId, productId, 0)} style={{ color: 'var(--itunda-grey-500)', fontSize: 'var(--itunda-type-scale-12-size)' }}>Remove</button>
                  </div>
                </div>
              ))}
            </div>
          ))}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>
              <span>Total ({groups.length} order{groups.length === 1 ? '' : 's'})</span>
              <span>{grandTotal.toLocaleString()} RWF</span>
            </div>
            <input
              type="text" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Delivery address" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
            />
            {bookmarks.length > 0 && (
              <div style={{ display: 'flex', gap: '8px', overflowX: 'auto' }}>
                {bookmarks.map((b) => (
                  <button
                    key={b.id} type="button" onClick={() => setAddress(b.displayName)}
                    style={{
                      display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0, padding: '8px 12px',
                      borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700,
                    }}
                  >
                    <span style={{ width: '8px', height: '8px', borderRadius: '50%', background: b.color, flexShrink: 0 }} />
                    {b.displayName}
                  </button>
                ))}
              </div>
            )}
            {needsDeviceVerification ? (
              // Real fix (2026-08-10) -- see checkoutResumeIndexRef's own doc comment.
              // Resumes the remaining orders from where the loop stopped instead of
              // re-placing every already-succeeded one.
              <DeviceStepUpPrompt onVerified={() => handlePlaceOrders()} onCancel={() => setNeedsDeviceVerification(false)} />
            ) : (
              <>
                {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)' }} role="alert">{error}</p>}
                {/* Real Toss "밀어서 결제하기" (swipe to pay) (2026-08-25, direct user
                    screenshot) -- replaces the plain submit button with Toss's own
                    signature deliberate-drag payment gesture. See
                    SwipeToConfirmButton's own doc comment. */}
                <SwipeToConfirmButton
                  label={`Swipe to place ${groups.length} order${groups.length === 1 ? '' : 's'}`}
                  busyLabel="Placing orders…"
                  enabled={!placing && !!address.trim()}
                  busy={placing}
                  onConfirm={() => handlePlaceOrders()}
                />
              </>
            )}
          </div>
        </form>
      )}
    </div>
  );
}

function MultiCartResultsView({ results, onDone }: { results: CommerceCheckoutResult[]; onDone: () => void }) {
  const successCount = results.filter((r) => r.success).length;
  return (
    <div style={{ padding: '10px 0' }}>
      <div style={{ textAlign: 'center', marginBottom: '20px' }}>
        <IconShieldCheck size={36} color="var(--itunda-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: 'var(--itunda-type-scale-17-size)', fontWeight: 700 }}>
          {successCount} of {results.length} order{results.length === 1 ? '' : 's'} placed
        </h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        {results.map((r) => (
          <div key={r.merchantId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: 'var(--itunda-type-scale-13-size)' }}>
            <span style={{ fontWeight: 600 }}>{r.businessName}</span>
            {r.success ? (
              <span style={{ color: 'var(--itunda-green)' }}>{r.order!.totalAmount.toLocaleString()} RWF — placed</span>
            ) : (
              <span style={{ color: 'var(--itunda-red)' }}>{r.error}</span>
            )}
          </div>
        ))}
      </div>
      <button className="itunda-btn itunda-btn-secondary" style={{ width: '100%' }} onClick={onDone}>
        {results.some((r) => !r.success) ? 'Back to cart' : 'Done'}
      </button>
    </div>
  );
}

function MyCommerceOrdersView({ onReorder, reorderingId }: { onReorder: (order: CommerceOrder) => void; reorderingId: string | null }) {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleCancel = async (orderId: string) => {
    setCancellingId(orderId);
    setError(null);
    try {
      await cancelOrder(orderId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return <EmptyState message="No orders yet — browse a merchant's shop and your first order will show up here." />;

  const renderAction = (o: CommerceOrder) => {
    if (o.status === 'PLACED') {
      return (
        <button className="itunda-btn itunda-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
          {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
        </button>
      );
    }
    if (o.status === 'DELIVERED') {
      return (
        <div>
          <OrderItemReviews order={o} />
          <ReturnExchangeAction orderId={o.id} />
          <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
            {reorderingId === o.id ? 'Reordering…' : 'Buy again'}
          </button>
        </div>
      );
    }
    if (o.status === 'CANCELLED') {
      return (
        <button className="itunda-btn itunda-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
          {reorderingId === o.id ? 'Reordering…' : 'Buy again'}
        </button>
      );
    }
    return undefined;
  };

  return (
    <div>
      <MyReturnRequestsView />
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <CommerceOrderCard key={o.id} order={o} action={renderAction(o)} />
      ))}
      </div>
    </div>
  );
}

function MerchantOrdersView() {
  const { t } = useI18n();
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyOrderId, setBusyOrderId] = useState<string | null>(null);

  const load = () => {
    fetchMerchantOrders()
      .then(setOrders)
      .catch((err) => {
        // A real, expected error for any account that hasn't registered as a merchant --
        // stays silent rather than alarming the common case of a buyer-only account.
        if (err instanceof ApiError && err.code === 'MERCHANT_NOT_FOUND') {
          setOrders([]);
        } else {
          setError(err instanceof ApiError ? err.message : t('common.loadError'));
        }
      });
  };

  useEffect(() => {
    load();
    const interval = setInterval(load, 4000);
    return () => clearInterval(interval);
  }, []);

  const handleAdvance = async (order: CommerceOrder) => {
    const next = nextInChain(COMMERCE_STATUS_CHAIN, order.status);
    if (!next) return;
    setBusyOrderId(order.id);
    setError(null);
    try {
      await advanceOrderStatus(order.id, next);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="skeleton" style={{ height: '180px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your store</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(COMMERCE_STATUS_CHAIN, o.status);
          return (
            <CommerceOrderCard
              key={o.id}
              order={o}
              action={next && (
                <button className="itunda-btn itunda-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                  {busyOrderId === o.id ? 'Updating…' : `Mark ${COMMERCE_STATUS_LABEL[next].toLowerCase()}`}
                </button>
              )}
            />
          );
        })}
      </div>
    </div>
  );
}

// Real product wishlist view (2026-07-20) -- lists every real favorited product,
// tapping one opens that merchant's real catalog (same "prove once, reuse the existing
// screen" shape as everywhere else in this file).
function WishlistView({ onOpenMerchant }: { onOpenMerchant: (merchant: ShoppingMerchant) => void }) {
  const { t } = useI18n();
  const [favorites, setFavorites] = useState<FavoriteProduct[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteProducts().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  const handleRemove = async (productId: string) => {
    setRemovingId(productId);
    try {
      await removeProductFavorite(productId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved items yet -- tap ♡ on any product to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      {favorites.map((f) => (
        <div key={f.productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px', padding: '10px 0' }}>
          <button
            onClick={() => onOpenMerchant({ merchantId: f.merchantId, businessName: f.businessName, category: null, cashbackRate: '' })}
            style={{ textAlign: 'left', flex: 1, display: 'flex', alignItems: 'center', gap: '12px' }}
          >
            <ProductImageThumb imageUrl={f.imageUrl} size={44} />
            <div>
              <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700 }}>{f.name}</p>
              <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>{f.businessName}</p>
              <ProductPriceBlock price={f.price} originalPrice={f.originalPrice} discountPercent={f.discountPercent} />
              {f.priceDropped && (
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700, color: 'var(--itunda-red, var(--itunda-red))', marginTop: '2px', display: 'flex', alignItems: 'center', gap: '4px' }}>
                  <PriceDropGlyph size={12} /> Price dropped
                </p>
              )}
            </div>
          </button>
          <button
            className="itunda-btn itunda-btn-secondary"
            disabled={removingId === f.productId}
            onClick={() => handleRemove(f.productId)}
            style={{ padding: '8px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }}
          >
            {removingId === f.productId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

function formatDealCountdown(endsAt: string): string {
  const msLeft = new Date(endsAt).getTime() - Date.now();
  if (msLeft <= 0) return 'Ending soon';
  const totalMinutes = Math.floor(msLeft / 60000);
  const hours = Math.floor(totalMinutes / 60);
  const minutes = totalMinutes % 60;
  return hours > 0 ? `${hours}h ${minutes}m left` : `${minutes}m left`;
}

// Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping reference screenshot
// -- "23:24:20 Limited time offer") -- same real TimeDeal.endsAt formatDealCountdown
// above already reads, ticking to the second, same real data Android/iOS's identical
// formatTimeDealCountdownHms now use.
function formatDealCountdownHms(endsAt: string, now: number): string {
  const msLeft = new Date(endsAt).getTime() - now;
  if (msLeft <= 0) return '00:00:00';
  const totalSeconds = Math.floor(msLeft / 1000);
  const hours = Math.floor(totalSeconds / 3600);
  const minutes = Math.floor((totalSeconds % 3600) / 60);
  const seconds = totalSeconds % 60;
  return [hours, minutes, seconds].map((n) => String(n).padStart(2, '0')).join(':');
}

function LiveDealCountdown({ endsAt }: { endsAt: string }) {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, []);
  return <>{formatDealCountdownHms(endsAt, now)}</>;
}

function ShopView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  const { t } = useI18n();
  const [view, setView] = useState<'BROWSE' | 'ORDERS' | 'WISHLIST'>('BROWSE');
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  // Real seller chat (2026-08-28) -- see ShopSellerContactPicker.tsx's own doc
  // comment. The merchant currently being contacted, or null when the picker is closed.
  const [contactingMerchant, setContactingMerchant] = useState<ShoppingMerchant | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [merchantSearchInput, setMerchantSearchInput] = useState('');
  const [debouncedMerchantSearch, setDebouncedMerchantSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [selectedProduct, setSelectedProduct] = useState<CommerceProduct | null>(null);
  const [cart, setCart] = useState<CommerceCart>({});
  const [showCart, setShowCart] = useState(false);
  const [results, setResults] = useState<CommerceCheckoutResult[] | null>(null);
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<ProductSearchResult[] | null>(null);
  const [searching, setSearching] = useState(false);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
  // Real "recently viewed products" rail (2026-08-23) -- ported from Android's
  // identical real feature (RecentlyViewedProductsStore.kt), never shipped to web
  // before now -- see lib/recentlyViewed.ts's own doc comment.
  const [recentlyViewedProducts, setRecentlyViewedProducts] = useState(recentlyViewedProductsStore.getAll());
  useEffect(() => {
    if (!selectedProduct) return;
    setRecentlyViewedProducts(
      recentlyViewedProductsStore.add({
        id: selectedProduct.id, merchantId: selectedProduct.merchantId, businessName: selected?.businessName ?? '',
        name: selectedProduct.name, price: selectedProduct.price, imageUrl: selectedProduct.imageUrl, discountPercent: selectedProduct.discountPercent,
      }),
    );
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedProduct?.id]);

  // Real Coupang/Amazon-style "Buy it again" (2026-08-23) -- direct port of this
  // file's own real Eats "Reorder" (see EatsView's handleReorder). Re-populates the
  // cross-merchant `cart` from a past order's still-active products and opens the
  // cart for review, same "review before a real-money action, not an instant one-tap
  // purchase" precedent Eats already established (a delivery address could be stale,
  // a price could have changed since). Commerce products never carry option groups
  // (only Eats' menu items do), so unlike Eats this needs no "drop items that now
  // require an option selection" sanitization -- only "drop items that are no longer
  // active."
  const handleReorder = async (order: CommerceOrder) => {
    setReorderingId(order.id);
    setReorderError(null);
    try {
      const [detail, menu] = await Promise.all([fetchOrderDetail(order.id), fetchMerchantProducts(order.merchantId)]);
      if (!detail.success || !menu.success) {
        setReorderError('Could not reorder.');
        return;
      }
      const activeProducts = new Map(menu.products.filter((p) => p.active).map((p) => [p.id, p]));
      const newLines: CommerceCartGroup['lines'] = {};
      detail.items.forEach((item) => {
        const product = activeProducts.get(item.productId);
        if (!product) return;
        newLines[product.id] = { product, quantity: (newLines[product.id]?.quantity ?? 0) + item.quantity };
      });
      if (Object.keys(newLines).length === 0) {
        setReorderError('None of the items from that order are available anymore.');
        return;
      }
      setCart((prev) => {
        const mergedLines = { ...prev[order.merchantId]?.lines };
        Object.entries(newLines).forEach(([productId, line]) => {
          mergedLines[productId] = { product: line.product, quantity: (mergedLines[productId]?.quantity ?? 0) + line.quantity };
        });
        return { ...prev, [order.merchantId]: { businessName: menu.merchant.businessName, lines: mergedLines } };
      });
      setShowCart(true);
    } catch (err) {
      setReorderError(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setReorderingId(null);
    }
  };

  // Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
  // recommendation #8. Every entry is a real merchant-set discount, never a
  // fabricated promo -- see backend MerchantProductRepository.findDeals's own doc
  // comment.
  const [deals, setDeals] = useState<ProductSearchResult[] | null>(null);
  useEffect(() => {
    fetchShopDeals().then(setDeals).catch(() => {});
  }, []);

  // Real 마감할인 (closing/surplus discount) rail (2026-08-15) -- a real,
  // government-partnered food-waste-reduction feature that launched 2026-06-15
  // (기후부/환경부 + Baemin/Yogiyo/Coupang Eats), sourced fresh, see
  // lib/shopping.ts's own doc comment. Distinct from the "🔥 Deals" rail above:
  // only genuinely time-boxed, still-in-stock closing sales.
  const [surplusDeals, setSurplusDeals] = useState<SurplusDealResult[] | null>(null);
  useEffect(() => {
    fetchSurplusDeals().then(setSurplusDeals).catch(() => {});
  }, []);

  // Real Coupang 타임특가 (Time Deal, item 226) -- see lib/timeDeal.ts's own doc
  // comment. Distinct from the always-on "🔥 Deals" rail above: a time-boxed,
  // quantity-capped event, not a permanent discount. Re-fetched every 30s so a deal
  // that just sold out or expired stops showing without a manual refresh.
  const [timeDeals, setTimeDeals] = useState<TimeDealView[] | null>(null);
  useEffect(() => {
    const load = () => fetchActiveTimeDeals().then(setTimeDeals).catch(() => {});
    load();
    const interval = setInterval(load, 30000);
    return () => clearInterval(interval);
  }, []);

  // Real Toss Shopping banner carousel (2026-08-12, direct user screenshot) -- see
  // backend TimeDealService.getBanners's own doc comment: every banner IS a real,
  // currently-active Time Deal, never fabricated promotional content. Same feature
  // Android's ShopScreen.kt already ports (docs/DESIGN_REFERENCES.md Section 53) --
  // this was the one real gap found via a grep for the endpoint's call sites: shipped
  // Android-only that pass, never actually ported to web.
  const [banners, setBanners] = useState<TimeDealView[]>([]);
  useEffect(() => { fetchShopBanners().then(setBanners).catch(() => {}); }, []);
  const [bannerIndex, setBannerIndex] = useState(0);
  const bannerScrollRef = useRef<HTMLDivElement>(null);

  // Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
  // see backend ShoppingMissionService.kt's own doc comment. Every mission credits real
  // RWF to the real account; itunda has never had a separate points currency.
  const [missions, setMissions] = useState<ShoppingMission[]>([]);
  const [spinOutcomes, setSpinOutcomes] = useState<SpinOutcome[]>([]);
  const [missionBusyType, setMissionBusyType] = useState<string | null>(null);
  const [missionFeedback, setMissionFeedback] = useState<string | null>(null);
  const loadMissions = () => {
    fetchShoppingMissionStatus().then((r) => { setMissions(r.missions); setSpinOutcomes(r.spinOutcomes); }).catch(() => {});
  };
  useEffect(loadMissions, []);
  const handleCompleteMission = async (type: string) => {
    setMissionBusyType(type);
    try {
      const result = await completeShoppingMission(type);
      setMissionFeedback(`+${result.amountEarned.toLocaleString()} RWF`);
      loadMissions();
    } catch (err) {
      setMissionFeedback(err instanceof ApiError ? err.message : t('common.actionError'));
    } finally {
      setMissionBusyType(null);
    }
  };

  // Real Karrot 반경 타기팅-style nearby ads (item 148) -- silent, non-blocking: a
  // customer who denies/lacks location just never sees this rail, same discipline
  // NeighborhoodSetupPrompt's own opt-in geolocation already establishes elsewhere.
  const [nearbyAds, setNearbyAds] = useState<NearbyMerchantAd[]>([]);
  useEffect(() => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition(
      (position) => {
        fetchNearbyAds(position.coords.latitude, position.coords.longitude).then(setNearbyAds).catch(() => {});
      },
      () => {},
    );
  }, []);

  // Real Coupang-style commerce (rw.itunda.commerce) -- deliberately reuses the same
  // GET /api/v1/shopping/merchants catalog the Shopping tab (Toss Shopping cashback
  // browsing) already uses, matching how Android/iOS's own Shop tab reuses the same
  // merchant directory rather than inventing a second one.
  const load = () => {
    setError(null);
    fetchShoppingCatalog(selectedCategory ?? undefined, debouncedMerchantSearch || undefined)
      .then(setMerchants)
      .catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };

  useEffect(() => {
    fetchMerchantCategories().then(setCategories).catch(() => {});
  }, []);

  // Real category/name filter for the merchant list (2026-07-21), debounced the same
  // way OrderFoodView's restaurant search already is -- see SearchAndCategoryChips.
  useEffect(() => {
    const timer = setTimeout(() => setDebouncedMerchantSearch(merchantSearchInput.trim()), 300);
    return () => clearTimeout(timer);
  }, [merchantSearchInput]);

  useEffect(load, [selectedCategory, debouncedMerchantSearch]);

  // Real cross-merchant product search (2026-07-20) -- see lib/shopping.ts's own doc
  // comment. Opening a result reuses ProductCatalogView as-is: it only ever reads
  // merchant.merchantId (confirmed by reading the component directly), so a minimal
  // ShoppingMerchant built from the search result -- not a second real fetch -- is
  // honest, not a shortcut that risks showing stale/wrong data.
  const handleSearch = async (e: React.FormEvent) => {
    e.preventDefault();
    setSearching(true);
    try {
      setSearchResults(await searchProducts(searchQuery.trim()));
    } catch {
      setSearchResults([]);
    } finally {
      setSearching(false);
    }
  };
  const openSearchResult = (r: ProductSearchResult) => {
    setSelected({ merchantId: r.merchantId, businessName: r.merchantName, category: null, cashbackRate: '1%' });
  };

  const setQtyByMerchant = (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => {
    setCart((prev) => {
      const next = { ...prev };
      const existing = next[merchant.merchantId] ?? { businessName: merchant.businessName, lines: {} };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[product.id];
      else lines[product.id] = { product, quantity };
      if (Object.keys(lines).length === 0) delete next[merchant.merchantId];
      else next[merchant.merchantId] = { ...existing, lines };
      return next;
    });
  };

  const setQtyByIds = (merchantId: string, productId: string, quantity: number) => {
    setCart((prev) => {
      const existing = prev[merchantId];
      if (!existing) return prev;
      const next = { ...prev };
      const lines = { ...existing.lines };
      if (quantity <= 0) delete lines[productId];
      else if (lines[productId]) lines[productId] = { ...lines[productId], quantity };
      if (Object.keys(lines).length === 0) delete next[merchantId];
      else next[merchantId] = { ...existing, lines };
      return next;
    });
  };

  const handleCheckedOut = (checkoutResults: CommerceCheckoutResult[]) => {
    // Only clear the merchants that actually succeeded -- a failed group's items
    // stay in the cart so the buyer doesn't lose their selection and can retry
    // (e.g. after fixing the delivery address or topping up their account).
    setCart((prev) => {
      const next = { ...prev };
      checkoutResults.filter((r) => r.success).forEach((r) => delete next[r.merchantId]);
      return next;
    });
    setResults(checkoutResults);
    setShowCart(false);
  };

  // Real seller chat (2026-08-28) -- see ShopSellerContactPicker.tsx's own doc
  // comment. Owned here (not inside ProductCatalogView/ProductDetailView
  // themselves) so the picker overlay renders once, shared by both sub-screens.
  const contactPickerNode = contactingMerchant && (
    <ShopSellerContactPicker
      merchantId={contactingMerchant.merchantId}
      merchantName={contactingMerchant.businessName}
      onClose={() => setContactingMerchant(null)}
      onOpened={(conversationId) => { setContactingMerchant(null); onMessageSeller(conversationId); }}
    />
  );

  if (results) {
    return (
      <MultiCartResultsView
        results={results}
        onDone={() => { setResults(null); setSelected(null); setView('ORDERS'); }}
      />
    );
  }

  if (showCart) {
    return <MultiCartView cart={cart} onBack={() => setShowCart(false)} onSetQty={setQtyByIds} onCheckedOut={handleCheckedOut} />;
  }

  if (selected && selectedProduct) {
    return (
      <>
        <ProductDetailView
          merchant={selected}
          product={selectedProduct}
          cart={cart}
          onSetQty={setQtyByMerchant}
          onBack={() => setSelectedProduct(null)}
          onViewCart={() => { setSelectedProduct(null); setShowCart(true); }}
          onContactSeller={() => setContactingMerchant(selected)}
        />
        {contactPickerNode}
      </>
    );
  }

  if (selected) {
    return (
      <>
        <ProductCatalogView
          merchant={selected}
          cart={cart}
          onSetQty={setQtyByMerchant}
          onBack={() => setSelected(null)}
          onViewCart={() => setShowCart(true)}
          onOpenProduct={setSelectedProduct}
          onContactSeller={() => setContactingMerchant(selected)}
        />
        {contactPickerNode}
      </>
    );
  }

  const totalItems = cartTotalItems(cart);

  return (
    <div>
      <MerchantOrdersView />
      <MerchantReturnQueueView />
      <MerchantRedeemVoucherCard />

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--itunda-grey-100)', borderRadius: '10px', overflowX: 'auto' }}>
        {(['BROWSE', 'ORDERS', 'WISHLIST'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700,
              color: view === v ? 'var(--itunda-white)' : 'var(--itunda-grey-700)',
              backgroundColor: view === v ? 'var(--itunda-indigo)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Merchants' : v === 'ORDERS' ? 'My orders' : <><HeartOutline size={12} /> Wishlist</>}
          </button>
        ))}
      </div>

      {view === 'BROWSE' && (
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '16px' }}>
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Search products across every merchant"
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-14-size)' }}
          />
          <button type="submit" className="itunda-btn itunda-btn-primary" disabled={searching || !searchQuery.trim()}>
            {searching ? '…' : 'Search'}
          </button>
          {searchResults !== null && (
            <button type="button" className="itunda-btn itunda-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>
              Clear
            </button>
          )}
        </form>
      )}

      {/* Real Toss Shopping banner carousel -- see the banners state's own doc comment
          above. Horizontal scroll-snap (no external carousel library) with a real
          "current | total" page indicator, matching Android's HorizontalPager reference
          exactly. */}
      {view === 'BROWSE' && searchResults === null && banners.length > 0 && (
        <div style={{ marginBottom: '16px', position: 'relative' }}>
          <div
            ref={bannerScrollRef}
            onScroll={(e) => {
              const el = e.currentTarget;
              setBannerIndex(Math.round(el.scrollLeft / el.clientWidth));
            }}
            style={{ display: 'flex', overflowX: 'auto', scrollSnapType: 'x mandatory', borderRadius: '14px', gap: '0' }}
          >
            {banners.map((v) => {
              const discountPercent = v.deal.originalPrice > 0 ? Math.round(100 - (v.deal.dealPrice / v.deal.originalPrice) * 100) : 0;
              return (
                <div
                  key={v.deal.id}
                  style={{
                    flex: '0 0 100%', scrollSnapAlign: 'start', height: '140px', background: 'color-mix(in srgb, var(--itunda-indigo) 12%, transparent)',
                    display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', boxSizing: 'border-box',
                  }}
                >
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '2px' }}>
                    {discountPercent > 0 && <span style={{ color: 'var(--itunda-red)', fontWeight: 700, fontSize: 'var(--itunda-type-scale-14-size)' }}>{discountPercent}% off</span>}
                    <span style={{ fontWeight: 700, fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-900)' }}>{v.productName}</span>
                    <span style={{ fontSize: 'var(--itunda-type-scale-15-size)', color: 'var(--itunda-grey-900)' }}>{v.deal.dealPrice.toLocaleString()} RWF</span>
                    <span style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{v.businessName}</span>
                  </div>
                  {v.productImageUrl && (
                    <img src={v.productImageUrl} alt="" style={{ width: '96px', height: '96px', borderRadius: '12px', objectFit: 'cover' }} />
                  )}
                </div>
              );
            })}
          </div>
          {banners.length > 1 && (
            <span style={{ position: 'absolute', right: '10px', bottom: '10px', background: 'rgba(0,0,0,0.5)', color: '#fff', fontSize: 'var(--itunda-type-scale-11-size)', padding: '3px 8px', borderRadius: '10px' }}>
              {bannerIndex + 1} | {banners.length}
            </span>
          )}
        </div>
      )}

      {/* Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
          see missions state's own doc comment above. */}
      {view === 'BROWSE' && searchResults === null && missions.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-16-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>Get points and coupons</p>
          <div style={{ display: 'flex', gap: '18px', overflowX: 'auto' }}>
            {missions.map((m) => {
              const done = m.type === 'WELCOME_BONUS' ? m.claimedEver : m.completedToday;
              const rewardText = m.type === 'SPIN' && spinOutcomes.length > 0
                ? `+${Math.min(...spinOutcomes.map((s) => s.amount)).toLocaleString()}~${Math.max(...spinOutcomes.map((s) => s.amount)).toLocaleString()}`
                : `+${m.rewardAmount.toLocaleString()}`;
              return (
                <button
                  key={m.type}
                  onClick={() => handleCompleteMission(m.type)}
                  disabled={done || missionBusyType !== null}
                  style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', width: '64px', flexShrink: 0, background: 'none', border: 'none', cursor: done ? 'default' : 'pointer' }}
                >
                  <div
                    style={{
                      width: '52px', height: '52px', borderRadius: '14px', display: 'flex', alignItems: 'center', justifyContent: 'center',
                      background: done ? 'var(--itunda-grey-100)' : 'color-mix(in srgb, var(--itunda-indigo) 15%, transparent)',
                    }}
                  >
                    {missionBusyType === m.type ? (
                      <span style={{ fontSize: 'var(--itunda-type-scale-18-size)', color: 'var(--itunda-grey-500)' }}>…</span>
                    ) : (
                      <Zap size={20} color={done ? 'var(--itunda-grey-400)' : 'var(--itunda-indigo)'} />
                    )}
                  </div>
                  <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', marginTop: '4px', color: done ? 'var(--itunda-grey-400)' : 'var(--itunda-grey-900)', textAlign: 'center' }}>{m.label}</span>
                  {!done && <span style={{ fontSize: '10px', fontWeight: 600, color: 'var(--itunda-indigo)' }}>{rewardText}</span>}
                </button>
              );
            })}
          </div>
          {missionFeedback && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>{missionFeedback}</p>}
        </div>
      )}

      {/* Real Karrot 반경 타기팅-style nearby ads rail (item 148) -- see lib/shopping.ts's
          own doc comment. Tapping one opens that merchant's real catalog, same
          minimal-ShoppingMerchant shortcut openSearchResult already uses just above. */}
      {view === 'BROWSE' && searchResults === null && nearbyAds.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>📍 Near you</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {nearbyAds.map((a) => (
              <button
                key={a.ad.id}
                onClick={() => setSelected({ merchantId: a.ad.merchantId, businessName: a.businessName, category: null, cashbackRate: '1%' })}
                className="itunda-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '160px', flexShrink: 0, gap: '4px' }}
              >
                <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', fontWeight: 700 }}>{a.ad.title}</p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>{a.businessName}</p>
                {a.ad.description && <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-700)' }}>{a.ad.description}</p>}
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-indigo)', fontWeight: 600 }}>{a.distanceKm.toFixed(1)} km away</p>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Real "recently viewed products" rail -- see lib/recentlyViewed.ts's own doc
          comment. Same "merchandising above the raw list, hidden once the user starts
          filtering" discipline the Deals rail just below already establishes.
          Reopens the merchant (same shortcut the Deals/Time Deals rails use), not a
          possibly-stale cached product snapshot. */}
      {view === 'BROWSE' && searchResults === null && recentlyViewedProducts.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>🕒 Recently viewed</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {recentlyViewedProducts.map((rv) => (
              <button
                key={rv.id}
                onClick={() => setSelected({ merchantId: rv.merchantId, businessName: rv.businessName, category: null, cashbackRate: '1%' })}
                className="itunda-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={rv.imageUrl} size={96} />
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{rv.name}</p>
                <ProductPriceBlock price={rv.price} originalPrice={null} discountPercent={rv.discountPercent} />
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Real "Deals" rail (2026-07-25) -- only shown on the unfiltered landing state,
          same "merchandising above the raw list, hidden once the user starts
          filtering" discipline a real Coupang/Naver home surface follows. Reuses
          openSearchResult exactly -- a real product-search result and a real deal are
          the same underlying row shape. */}
      {view === 'BROWSE' && searchResults === null && deals && deals.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px', display: 'flex', alignItems: 'center', gap: '6px' }}><FlameGlyph size={17} /> Deals</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {deals.map((d) => (
              <button
                key={d.id}
                onClick={() => openSearchResult(d)}
                className="itunda-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={d.imageUrl} size={96} />
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.name}</p>
                <ProductPriceBlock price={d.price} originalPrice={d.originalPrice} discountPercent={d.discountPercent} />
                {/* rating/reviewCount added 2026-08-25 -- real batched ProductReview
                    data already on this row (see lib/shopping.ts's ProductSearchResult
                    comment), same IconStar treatment the merchant browse card already
                    uses -- no per-item fetch needed. */}
                {d.rating != null && d.reviewCount ? (
                  <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '2px' }}>
                    <IconStar size={11} color="#F5A623" fill="#F5A623" /> {d.rating.toFixed(1)} ({d.reviewCount})
                  </p>
                ) : null}
                {d.isBestSeller && <ShopBestSellerBadge />}
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: d.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                  {d.stockQuantity === null || d.stockQuantity === undefined ? 'Available' : d.stockQuantity === 0 ? 'Out of stock' : `${d.stockQuantity} available`}
                </p>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Real 마감할인 (closing/surplus discount) rail (2026-08-15) -- see
          lib/shopping.ts's own doc comment for the full sourced account. Shows a real
          "closes at HH:mm" time, never a fabricated urgency banner. */}
      {view === 'BROWSE' && searchResults === null && surplusDeals && surplusDeals.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>⏳ Closing deals</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {surplusDeals.map((d) => (
              <button
                key={d.id}
                onClick={() => openSearchResult(d)}
                className="itunda-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={d.imageUrl} size={96} />
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{d.name}</p>
                <ProductPriceBlock price={d.price} originalPrice={d.originalPrice} discountPercent={d.discountPercent} />
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)' }}>
                  Closes {new Date(d.surplusExpiresAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </p>
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)' }}>{d.stockQuantity} left</p>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Real Coupang 타임특가 (Time Deal, item 226) -- see lib/timeDeal.ts's own doc
          comment. A time-boxed, quantity-capped event, distinct from the always-on
          "🔥 Deals" rail above. Clicking a card opens that merchant's catalog, same
          simplification the Deals rail above already uses (not a deep-link straight
          to the specific product). */}
      {view === 'BROWSE' && searchResults === null && timeDeals && timeDeals.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', marginBottom: '8px' }}>⏰ Time Deals</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {timeDeals.map((v) => (
              <button
                key={v.deal.id}
                onClick={() => setSelected({ merchantId: v.deal.merchantId, businessName: v.businessName, category: null, cashbackRate: '1%' })}
                className="itunda-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={v.productImageUrl} size={96} />
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', fontWeight: 700 }}>{v.productName}</p>
                <ProductPriceBlock
                  price={v.deal.dealPrice} originalPrice={v.deal.originalPrice}
                  discountPercent={Math.round((1 - v.deal.dealPrice / v.deal.originalPrice) * 100)}
                />
                {/* Real Coupang badge system (2026-08-05) -- see Badge.tsx's own doc
                    comment. Matches Android ShopScreen.kt's own identical StatusBadge
                    treatment (this was plain <p> text on bank-mfe until now). */}
                <Badge text={formatDealCountdown(v.deal.endsAt)} filled={false} tint="var(--itunda-indigo)" />
                {/* Real live HH:MM:SS countdown (2026-08-25, direct Toss Shopping
                    reference screenshot -- "23:24:20 Limited time offer") -- same
                    real v.deal.endsAt the rounded badge above already reads. */}
                <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-red)', fontWeight: 700 }}>
                  ⏰ <LiveDealCountdown endsAt={v.deal.endsAt} />
                </p>
                <Badge text={`${v.deal.remainingQuantity} left`} tint="var(--itunda-red)" />
              </button>
            ))}
          </div>
        </div>
      )}

      {view === 'BROWSE' && searchResults === null && (
        <SearchAndCategoryChips
          searchInput={merchantSearchInput}
          onSearchChange={setMerchantSearchInput}
          placeholder="Search merchants"
          categories={categories}
          selectedCategory={selectedCategory}
          onSelectCategory={setSelectedCategory}
        />
      )}

      {view === 'ORDERS' ? (
        <div>
          <MyCommerceOrdersView onReorder={handleReorder} reorderingId={reorderingId} />
          {reorderError && <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-red)', marginTop: '8px' }} role="alert">{reorderError}</p>}
        </div>
      ) : view === 'WISHLIST' ? (
        <WishlistView onOpenMerchant={setSelected} />
      ) : view === 'BROWSE' && searchResults !== null ? (
        searchResults.length === 0 ? (
          <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>No products matched "{searchQuery}".</p>
        ) : (
          // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a real
          // product-search catalog list, matching the same flat-entity-list convention
          // Android's VehicleValuationScreen/GroupAccountScreen already established
          // (docs/UI_UX_GUIDELINES.md §10).
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {searchResults.map((r) => (
              <button
                key={r.id}
                onClick={() => openSearchResult(r)}
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', textAlign: 'left', width: '100%', gap: '12px' }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <ProductImageThumb imageUrl={r.imageUrl} size={44} />
                  <div>
                    <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{r.name}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>Sold by {r.merchantName}</p>
                    <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: r.stockQuantity === 0 ? 'var(--itunda-red)' : 'var(--itunda-grey-500)' }}>
                      {r.stockQuantity === null || r.stockQuantity === undefined ? 'Available' : r.stockQuantity === 0 ? 'Out of stock' : `${r.stockQuantity} available`}
                    </p>
                    {r.isBestSeller && <ShopBestSellerBadge />}
                  </div>
                </div>
                <ProductPriceBlock price={r.price} originalPrice={r.originalPrice} discountPercent={r.discountPercent} />
              </button>
            ))}
          </div>
        )
      ) : error ? (
        <ErrorCard message={error} onRetry={load} />
      ) : merchants === null ? (
        <div className="itunda-flat-section skeleton" style={{ height: '220px' }} />
      ) : merchants.length === 0 ? (
        // Real copy-voice fix (item 244, round 5 of the empty-state pass, ported
        // from the same-day Android/iOS fix): "registered yet" is honest about
        // whose gap this is -- no merchant has joined yet, not something the
        // reader is missing a step on.
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a plain
        // one-line empty-state message.
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-grey-500)' }}>
          {selectedCategory || debouncedMerchantSearch ? 'No merchants match your search — try a different category or search term.' : 'No merchants registered yet — check back once merchants in your area join itunda Shop.'}
        </p>
      ) : (
        // Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- a real
        // merchant catalog list, same flat-entity-list convention as the product
        // search list above.
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: totalItems > 0 ? '80px' : 0 }}>
          {merchants.map((m) => (
            <button
              key={m.merchantId}
              onClick={() => setSelected(m)}
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              {/* Real fix (2026-08-19): this row used to show a generic icon and the exact
                  same hardcoded subtitle for every merchant, ignoring the real photoUrl/
                  rating/reviewCount/distanceKm/deliveryTimeMinutes/favoriteCount fields
                  ShoppingMerchant already carries -- OrderFoodView's restaurant row (same
                  ShoppingMerchant type) already had this real enrichment; this was simply
                  never ported over to Shop's own merchant list. */}
              {m.photoUrl ? (
                <img
                  src={m.photoUrl} alt=""
                  style={{ width: '44px', height: '44px', borderRadius: '12px', objectFit: 'cover', flexShrink: 0, backgroundColor: 'var(--itunda-indigo-light)' }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
              ) : (
                <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--itunda-indigo-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <ShoppingBag size={20} color="var(--itunda-indigo)" />
                </div>
              )}
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, color: 'var(--itunda-grey-900)', display: 'flex', alignItems: 'center', gap: '6px' }}>
                  {m.businessName}
                  {m.isAcceptingOrders === false && (
                    <span style={{ fontSize: 'var(--itunda-type-scale-11-size)', fontWeight: 600, color: 'var(--itunda-grey-500)', backgroundColor: 'var(--itunda-grey-100)', padding: '2px 8px', borderRadius: '99px' }}>
                      ⏸ Temporarily paused
                    </span>
                  )}
                </p>
                <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                  {m.category && <span>{m.category}</span>}
                  {m.rating != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                      <IconStar size={11} color="#F5A623" fill="#F5A623" /> {m.rating.toFixed(1)} ({m.reviewCount})
                    </span>
                  )}
                  {!!m.favoriteCount && m.favoriteCount > 0 && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <HeartFilled size={11} /> {m.favoriteCount.toLocaleString()}</span>}
                  {m.distanceKm != null && <span>· {m.distanceKm.toFixed(1)} km</span>}
                  {m.deliveryTimeMinutes != null && <span>· ~{m.deliveryTimeMinutes} min</span>}
                  {m.isBusy && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <FlameGlyph size={12} /> Busy, delivery may take longer</span>}
                  {m.closedToday && <span style={{ display: 'inline-flex', alignItems: 'center', gap: '3px' }}>· <SoldOutGlyph size={12} /> Closed today</span>}
                  {m.minOrderAmount != null && <span>· Min {m.minOrderAmount.toLocaleString()} RWF</span>}
                  {!m.category && m.rating == null && m.distanceKm == null && <span>Real cart checkout, real delivery tracking</span>}
                </p>
              </div>
            </button>
          ))}
        </div>
      )}
      {view === 'BROWSE' && totalItems > 0 && (
        <button
          className="itunda-btn itunda-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCart(true)}
        >
          View cart ({totalItems} item{totalItems === 1 ? '' : 's'})
        </button>
      )}
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
  if (devices === null) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;

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

  // Real Toss Bank reference (2026-08-23, user-supplied 분실신고/"Report lost"
  // screenshot): itunda has no distinct lost-card-report flow on the backend, only
  // freeze/unfreeze -- but freezing genuinely accomplishes the real protective intent
  // of "report lost or stolen" (no purchases can go through), so this reuses the same
  // real freezeCard() call rather than inventing a separate endpoint. Unlike the
  // Freeze/Unfreeze toggle button above (which flips either direction), this always
  // freezes -- matching "report lost" real one-way meaning.
  const handleReportLost = async () => {
    if (!card || card.frozen) return;
    setBusy(true);
    setError(null);
    try {
      setCard(await freezeCard());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : t('common.actionError'));
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
  if (card === undefined) return <div className="skeleton" style={{ height: '160px', borderRadius: 'var(--itunda-radius-md)' }} />;

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
  // specific, itunda has no backend for any of them). "Report lost or stolen"
  // reuses the same real freezeCard() the Freeze/Unfreeze toggle already calls --
  // freezing genuinely accomplishes that real protective intent, not a fabricated
  // separate flow (see handleReportLost's own doc comment).
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
        •••• {card.last4} · {card.frozen ? 'Frozen — no purchases can be made' : 'Active'}
      </p>

      {error && <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '12px' }} role="alert">{error}</p>}

      <button className={`itunda-btn ${card.frozen ? 'itunda-btn-primary' : 'itunda-btn-danger'}`} disabled={busy} onClick={handleToggleFreeze} style={{ marginBottom: '20px' }}>
        {card.frozen ? 'Unfreeze card' : 'Freeze card'}
      </button>

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
          onClick={handleReportLost}
          disabled={busy || card.frozen}
          style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%', padding: '12px 0', textAlign: 'left', opacity: card.frozen ? 0.5 : 1 }}
        >
          <span style={{ fontSize: 'var(--itunda-type-scale-14-size)' }}>{card.frozen ? 'Reported lost or stolen' : 'Report lost or stolen'}</span>
          <IconChevronRight size={18} color="var(--itunda-grey-400)" />
        </button>
      </div>

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
            {card.frozen ? 'Card is frozen' : busy ? 'Paying…' : 'Pay'}
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
  const [claimMsg, setClaimMsg] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

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
  if (jar === null) return <div className="skeleton" style={{ height: '140px', marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)' }} />;

  const canClaim = jar.earnedThisMonth > 0;

  return (
    <div style={{ marginBottom: '16px', borderRadius: 'var(--itunda-radius-md)', padding: '24px', background: 'linear-gradient(135deg, var(--itunda-indigo) 0%, #4A90E2 100%)', color: '#fff' }}>
      {/* Real methodology-transparency fix (2026-08-11): jar.rate is the ANNUAL rate
          SavingsService.accrueInterest() divides by 365 to get the real daily accrual
          (dailyRate = rate/100/365) -- this copy called it "daily interest" outright,
          which is the actual number times ~365 too high a read for anyone taking it
          literally. Now states the real methodology instead of a bare adjective. */}
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
  const [depositing, setDepositing] = useState(false);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  const pct = Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100));

  const handleDeposit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await depositToGoal(goal.id, Number(amount));
      setAmount('');
      setDepositing(false);
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700 }}>{goal.name}</p>
          <p style={{ fontSize: 'var(--itunda-type-scale-12-size)', color: 'var(--itunda-grey-500)' }}>
            {goal.currentAmount.toLocaleString()} / {goal.targetAmount.toLocaleString()} RWF
            {goal.status === 'completed' && ' · Completed 🎉'}
          </p>
        </div>
        {goal.status === 'active' && (
          <button className="itunda-btn itunda-btn-secondary" style={{ padding: '6px 12px', fontSize: 'var(--itunda-type-scale-12-size)' }} onClick={() => setDepositing((d) => !d)}>
            Deposit
          </button>
        )}
      </div>
      <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--itunda-grey-100)', marginTop: '10px', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${pct}%`, backgroundColor: 'var(--itunda-indigo)' }} />
      </div>
      {goal.monthlyContribution > 0 && (
        <p style={{ fontSize: 'var(--itunda-type-scale-11-size)', color: 'var(--itunda-grey-500)', marginTop: '6px' }}>
          Auto-saves {goal.monthlyContribution.toLocaleString()} RWF/month
        </p>
      )}
      {depositing && (
        needsDeviceVerification ? (
          <div style={{ marginTop: '10px' }}>
            {/* Real fix (2026-08-10) -- see TransferFlow's own identical fix for the
                full account. handleDeposit resets needsDeviceVerification itself. */}
            <DeviceStepUpPrompt onVerified={() => handleDeposit()} onCancel={() => setDepositing(false)} />
          </div>
        ) : (
          <form onSubmit={handleDeposit} style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
            <input
              type="number" min="1" required value={amount} onChange={(e) => setAmount(e.target.value)}
              placeholder="Amount (RWF)"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--itunda-grey-200)', fontSize: 'var(--itunda-type-scale-13-size)' }}
            />
            <button type="submit" className="itunda-btn itunda-btn-primary" disabled={busy} style={{ padding: '8px 14px', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              {busy ? '…' : 'Add'}
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
  if (detail === null) return <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} />;

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
          <div className="skeleton" style={{ height: '40px', borderRadius: '8px' }} />
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
        <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} />
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
        <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} />
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
  if (detail === null) return <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} />;

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
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10) -- see the Talk conversation view's own identical
  // pendingDeviceRetryRef for the full account: cancel and withdraw share this one
  // flag+prompt, so retrying has to redo whichever one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
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
  if (detail === null) return <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} />;

  const { plan, accountBalance, installments } = detail;
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

      {installments.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Installments</h3>
          {installments.map((inst) => (
            <div key={inst.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <span style={{ color: 'var(--itunda-grey-500)' }}>Week {inst.weekNumber}</span>
              <span style={{ fontWeight: 600 }}>{inst.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}

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
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>26-week savings</h3>
      <CreateWeeklySavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} />
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
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  // Real fix (2026-08-10 pattern, same as WeeklySavingsPlanDetailView above): deposit/
  // cancel/withdraw all share this one flag+prompt, so retrying has to redo whichever
  // one was actually pending.
  const pendingDeviceRetryRef = useRef<(() => void) | null>(null);

  const load = () => {
    setError(null);
    fetchGrow31SavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
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
  if (detail === null) return <div className="skeleton" style={{ height: '260px', borderRadius: 'var(--itunda-radius-md)' }} />;

  const { plan, accountBalance, deposits } = detail;
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

      {deposits.length > 0 && (
        <div className="itunda-flat-section">
          <h3 style={{ fontSize: 'var(--itunda-type-scale-14-size)', fontWeight: 700, marginBottom: '8px' }}>Deposits</h3>
          {[...deposits].sort((a, b) => b.dayNumber - a.dayNumber).map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: 'var(--itunda-type-scale-13-size)' }}>
              <span style={{ color: 'var(--itunda-grey-500)' }}>Day {d.dayNumber} · streak {d.streakAtDeposit}</span>
              <span style={{ fontWeight: 600 }}>{d.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}

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
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>31-day savings</h3>
      <CreateGrow31SavingsPlanForm onCreated={load} />
      {/* Real fix (2026-08-24, flat-design sweep): dropped itunda-card -- lone
          conditional error message. */}
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', margin: '10px 0' }} role="alert">{error}</p>
      )}
      {plans === null ? (
        <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} />
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
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyUpfrontDeposits().then(setDeposits).catch((err) => setError(err instanceof ApiError ? err.message : t('common.loadError')));
  };
  useEffect(load, []);

  return (
    <div>
      <h3 style={{ fontSize: 'var(--itunda-type-scale-15-size)', fontWeight: 700, margin: '4px 4px 10px' }}>12-month deposit</h3>
      <OpenUpfrontDepositForm onOpened={load} />
      {error && (
        <p style={{ fontSize: 'var(--itunda-type-scale-13-size)', color: 'var(--itunda-red)', marginBottom: '16px' }} role="alert">{error}</p>
      )}
      {deposits === null ? (
        <div className="skeleton" style={{ height: '64px', borderRadius: 'var(--itunda-radius-md)' }} />
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
          onSend={(account) => { setOpenAccountDetail(null); setTransferAccountBalance(account.balance); setShowTransfer(true); }}
          onNavigateToTab={onNavigateToTab ? (tab) => { setOpenAccountDetail(null); onNavigateToTab(tab); } : undefined}
        />
      )}
      {showTransfer && (
        <TransferFlow
          accountBalance={transferAccountBalance}
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
