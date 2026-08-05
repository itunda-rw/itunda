import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { Archive, ArchiveRestore, ArrowLeft, ArrowUpRight, Bike, Car, Heart, Image as ImageIcon, LogOut, MessageCircle, Plus, Receipt, ScanFace, Send, ShieldCheck, ShoppingBag, SmilePlus, Star, TrendingDown, TrendingUp, Users, Utensils, Wallet as WalletIcon } from 'lucide-react';
import { getStoredUser, logout, ApiError } from './lib/api';
import { Badge } from './Badge';
import { EmptyState, ErrorCard } from './EmptyState';
import { configureAutoTopUp, fetchAutoTopUpSetting, fetchBudgets, fetchSpendingInsight, fetchSubscriptions, fetchTransactions, fetchTransactionTimeline, fetchWallets, setBudget, triggerAutoTopUp, type AutoTopUpSetting, type BudgetView, type DetectedSubscription, type SpendingCategory, type Transaction, type Wallet } from './lib/wallet';
import { fetchMyDevices, getOrCreateDeviceId, revokeDevice, verifyDevice, type TrustedDevice } from './lib/device';
import { fetchNotifications, markNotificationRead, markAllNotificationsRead, type NotificationItem } from './lib/notifications';
import { fetchDiscoverItems, type DiscoverItem } from './lib/discover';
import { chargeCard, fetchCardTransactions, fetchMyCard, freezeCard, issueCard, setCardLimits, unfreezeCard, type Card, type CardTransaction } from './lib/card';
import { claimInterest, createGoal, depositToGoal, fetchGoals, fetchInterestJar, fetchRoundUpSettings, ROUND_UP_INCREMENTS, setRoundUpSettings, type InterestJar, type RoundUpSettings, type SavingsGoal } from './lib/savings';
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
  disburseHarvestAdvance, fetchMyCooperativeMemberships, fetchMyHarvestAdvances, joinCooperative,
  registerCooperative, repayHarvestAdvance, requestHarvestAdvance,
  type CooperativeMembership, type HarvestAdvance,
} from './lib/harvestAdvance';
import {
  applyForVupLoan, disburseVupLoan, fetchMyVupLoans, fetchVupLoanEligibility, repayVupLoan,
  type VupLoan, type VupLoanEligibility, type VupLoanPurpose,
} from './lib/vupLoan';
import {
  applyForStudentLoan, declareGraduated as declareStudentLoanGraduated, disburseStudentLoan, fetchMyStudentLoans, fetchSuggestedPayment, repayStudentLoan,
  type StudentLoan, type StudentLoanLevel, type StudentLoanSuggestedPayment,
} from './lib/studentLoan';
import {
  cancelMotoOwnershipPlan, contributeToMotoOwnershipPlan, convertMotoOwnershipPlanToLoan, createMotoOwnershipPlan, fetchMyMotoOwnershipPlans, repayMotoOwnershipPlan,
  type MotoOwnershipPlan,
} from './lib/motoOwnership';
import {
  cancelWeeklySavingsPlan, createWeeklySavingsPlan, fetchWeeklySavingsPlan, fetchWeeklySavingsPlans, withdrawWeeklySavingsPlan,
  WEEKLY_SAVINGS_ESCALATION_RATES, WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS, WEEKLY_SAVINGS_TERM_WEEKS,
  type WeeklySavingsPlan, type WeeklySavingsPlanDetail,
} from './lib/weeklySavings';
import { collectWithFacePay, enrollFacePay, fetchFacePayStatus, revokeFacePay } from './lib/facepay';
import { fetchMyP2pRequests, generateP2pRequest, payP2pRequest, sendDirect, type P2pPaymentRequestDto, type P2pPaymentRequestStatus } from './lib/p2p';
import { getCertificateStatus, getMyCertificate, issueCertificate, revokeCertificate, verifyCertificateSignature, type Certificate, type VerifyCertificateSignatureResult } from './lib/certificate';
import { fetchLinkedAccounts, fetchOverview, linkAccount, unlinkAccount, type LinkedAccount, type Overview } from './lib/overview';
import {
  applyForLoan, applyForPostpaidCredit, drawOverdraft, fetchLenders, fetchLoanOffers, fetchMyLoans, fetchMyOverdraft,
  fetchMyPostpaidCredit, openOverdraft, refinanceLoan, repayLoan, repayOverdraft, repayPostpaidCredit, spendPostpaidCredit,
  type Lender, type LoanAccount, type LoanOffer, type OverdraftAccount, type PostpaidCreditLine,
} from './lib/loans';
import { fetchCreditScore, fetchCreditScoreSuggestions, type CreditScoreResult, type CreditScoreSuggestion } from './lib/creditScore';
import { fetchTrustScore, type TrustScoreResult } from './lib/trustScore';
import { fetchRewardTasks, fetchReferralInfo, claimRewardTask, reportSteps, fetchTodaySteps, type RewardTasksResult, type ReferralInfo } from './lib/rewards';
import { fetchInsurancePlans, fetchMyPolicies, enrollInPlan, submitClaim, fetchMyClaims, createPremiumFund, contributeToFund, cancelFund, fetchMyPremiumFunds, type InsurancePlan, type InsurancePolicy, type InsuranceClaim, type InsurancePremiumFund } from './lib/insurance';
import { fetchCropIndexCatalog, enrollInCropIndexPolicy, fetchMyCropIndexPolicies, cancelCropIndexPolicy, type CropIndexCatalogEntry, type CropIndexPolicy, type WeatherIndexCropType } from './lib/weatherIndexInsurance';
import { fetchBillProviders, fetchPendingBills, payBill, buyAirtime, type BillProvider, type PendingBill } from './lib/bills';
import {
  fetchAgentTill, fetchAgentActivity, agentCashIn, agentCashOut, submitAgentTillCount,
  isNotAgentOperatorError, type AgentTillSnapshot, type AgentActivityItem,
} from './lib/agentOperator';
import {
  postFloatListing, fetchNearbyFloatListings, fetchMyFloatListings, cancelFloatListing,
  requestFloat, fetchMyFloatRequests, fetchIncomingFloatRequests, acceptFloatRequest, declineFloatRequest,
  type NearbyFloatListing, type FloatListing, type FloatTransferRequest,
} from './lib/floatMarketplace';
import { setUssdPin } from './lib/ussd';
import {
  fetchMyUpfrontDeposits, openUpfrontDeposit, withdrawUpfrontDeposit,
  UPFRONT_DEPOSIT_ANNUAL_RATE, UPFRONT_DEPOSIT_MIN_PRINCIPAL, UPFRONT_DEPOSIT_MAX_PRINCIPAL,
  type UpfrontInterestDeposit,
} from './lib/upfrontDeposit';
import {
  convertCurrency, fetchExchangeRate, fetchMyCurrencyConversions, fetchMyForeignCurrencyWallets, openForeignCurrencyWallet,
  FOREIGN_CURRENCY_SUPPORTED, type CurrencyConversion, type ForeignCurrencyCode, type ForeignCurrencyWallet,
} from './lib/foreignCurrency';
import {
  advanceDineInOrderStatus, cancelDineInOrder, fetchMyDineInOrders, fetchRestaurantDineInOrders, placeDineInOrder,
  type DineInOrder, type DineInOrderStatus,
} from './lib/dineIn';
import { submitHoodReport, type HoodReportTargetType } from './lib/hoodReport';
import { fetchIdentityStatus, submitIdentity, type IdentityDocumentType, type KycSubmission } from './lib/identity';
import { addContact, fetchContacts, type Contact } from './lib/contacts';
import { createSupportTicket, fetchSupportTickets, type SupportTicket, type SupportTicketCategory } from './lib/support';
import { cancelBillingSubscription, collectPayment, fetchMembershipDayStatus, fetchMerchantBillingPlans, fetchMerchantCategories, fetchMyBillingSubscriptions, fetchMyFollowedMerchants, fetchNearbyAds, fetchShopDeals, fetchShoppingCatalog, followMerchant, payByStaticQr, previewPaymentIntent, searchProducts, subscribeToBillingPlan, unfollowMerchant, type CollectPaymentResult, type MerchantBillingPlan, type MerchantBillingSubscription, type MerchantCouponView, type NearbyMerchantAd, type PaymentIntentPreview, type ProductSearchResult, type ShoppingMerchant } from './lib/shopping';
import { fetchActiveTimeDeals, type TimeDealView } from './lib/timeDeal';
import {
  buyStock, fetchPortfolio, fetchPortfolioHistory, fetchStockHistory, fetchStocks, fetchWatchlist,
  fundInvestmentWallet,
  sellStock, unwatchStock, watchStock,
  type Portfolio, type PortfolioValuePoint, type PricePoint, type Stock,
} from './lib/stocks';
import {
  addGroupMember, connectMessagingSocket, createGroup, fetchConversations, fetchGroupMembers, fetchGroupMessages, fetchGroupThread, fetchGroups, fetchMessages, fetchPinnedConversationMessage, fetchPinnedGroupMessage, fetchThread,
  blockConversationParticipant, deleteGroupMessage, deleteMessage, fetchConversationQuiet, fetchPresence, fetchTalkContacts, forwardGroupMessage, forwardMessage, leaveGroup, pinConversationMessage, pinGroupMessage, reportChatMessage, searchConversationMessages, sendGroupMessage, sendMessage, setConversationArchived, setConversationQuiet, setGroupDescription, setGroupPhotoUrl, startConversation, startConversationWithUser, toggleGroupReaction, toggleReaction, unblockConversationParticipant, unpinConversationMessage, unpinGroupMessage,
  type ConversationSummary, type GroupMember, type GroupMessage,
  type GroupSummary, type Message, type MessagingSocketHandle, type ReactionGroup, type TalkContact,
} from './lib/messaging';
import {
  fetchEmoticonImageMap, fetchEmoticonPacks, fetchOwnedEmoticonPacks, fetchPackEmoticons, giftEmoticonPack, purchaseEmoticonPack, sendEmoticon,
  sendGroupEmoticon,
  type Emoticon, type EmoticonPack, type OwnedEmoticonPack,
} from './lib/emoticons';
import { extendGiftVoucherExpiry, fetchGiftVouchersForConversation, purchaseGiftVoucher, type GiftVoucher, type GiftVoucherStatus } from './lib/giftVouchers';
import {
  attachSplitBillReceipt, createSplitBill, fetchSplitBillsForGroup, paySplitBillShare, requestSplitBillNextRound, type SplitBillWithParticipants,
} from './lib/splitBill';
import {
  addKeywordAlert, addListingFavorite, contactSeller, createListing, fetchKeywordAlertQuietHours, fetchKeywordAlerts, fetchListingReviews, fetchListings,
  fetchListingsMyNeighborhood, fetchMyFavoriteListings, fetchMyListings, fetchMyPurchases, fetchOffersForConversation, makeOffer,
  markListingSold, removeKeywordAlert, removeListing, removeListingFavorite, respondToOffer, setKeywordAlertQuietHours,
  submitListingReview, type FavoriteListing, type HoodReview, type KeywordAlert, type KeywordAlertQuietHours, type Listing, type PriceOffer, type TrustScores,
} from './lib/marketplace';
import { clearSecondNeighborhood, fetchProfile, setBirthDate, setNeighborhood, setSecondNeighborhood, updateProfilePhoto } from './lib/neighborhood';
import { confirmEmailVerification, confirmPhoneVerification, requestEmailVerification, requestPhoneVerification } from './lib/verification';
import { depositToMiniWallet, openMiniWallet } from './lib/miniWallet';
import { claimGift, fetchGiftsForConversation, sendGiftInConversation, GIFT_THEME_LABELS, type Gift, type GiftStatus, type GiftTheme } from './lib/gift';
import {
  addCommunityComment, checkIntoMeetupSession, createCommunityPost, fetchCommunityCategories, fetchCommunityComments, fetchCommunityPost,
  fetchCommunityPosts, fetchCommunityPostsMyNeighborhood, fetchMeetupSessions, fetchMyCommunityPosts, finalizeGroupBuy, joinCommunityMeetup,
  removeCommunityPost, scheduleMeetupSessions, toggleCommunityLike,
  type CommunityCategory, type CommunityComment, type CommunityPost, type JoinedCounts, type MeetupSession,
} from './lib/community';
import {
  addJobPostFavorite, applyToJob, contactPoster, createJobPost, fetchApplicationsForJobPost, fetchJobCategories, fetchJobPost, fetchJobPostReviews,
  fetchJobPosts, fetchJobPostsMyNeighborhood, fetchMyFavoriteJobPosts, fetchMyJobApplications, fetchMyJobPosts, fetchMyWorkedJobPosts,
  markJobPostFilled, removeJobPost, removeJobPostFavorite, respondToJobApplication, submitJobPostReview,
  type FavoriteJobPost, type JobApplication, type JobCategory, type JobPayType, type JobPost,
} from './lib/jobs';
import {
  addPropertyListingFavorite, contactLister, createPropertyListing, fetchMyAcquiredPropertyListings, fetchMyFavoritePropertyListings, fetchMyPropertyListings,
  fetchPropertyListingReviews, fetchPropertyListings, fetchPropertyListingsMyNeighborhood, fetchPropertyOffersForConversation, fetchPropertyTypes,
  fetchPropertyValuation, makePropertyOffer, markPropertyListingTaken, removePropertyListing, removePropertyListingFavorite, respondToPropertyOffer,
  submitPropertyListingReview, submitPropertyOwnershipVerification,
  type FavoritePropertyListing, type PropertyListing, type PropertyListingType, type PropertyPriceOffer, type PropertyType, type PropertyValuationEstimate,
} from './lib/realestate';
import { uploadFile } from './lib/upload';
import {
  addFavoriteRestaurant, advanceRestaurantOrder, advanceRiderOrder, cancelEatsOrder, claimDelivery, completePickupOrder, EATS_MEMBERSHIP_TIERS, fetchAvailableDeliveries,
  fetchEatsOrder, fetchMenu, fetchMyEatsOrders, fetchMyFavoriteRestaurants, fetchMyMembership, fetchMyPlatformMembership, fetchMyRiderProfile, fetchRestaurantCategories,
  fetchRestaurantOrders, fetchRestaurants, fetchRestaurantRating, fetchRestaurantReviews, fetchRiderDeliveries, placeEatsOrder, PLATFORM_MEMBERSHIP_TIERS, registerRider,
  removeFavoriteRestaurant, replyToRestaurantReview, searchDeliveryAddress, setRiderAvailability, subscribeMembership, subscribePlatformMembership, submitEatsReview,
  type AddressSuggestion, type EatsMembership, type EatsOrder, type EatsOrderStatus, type EatsReview, type FavoriteRestaurant, type MenuItem, type PlatformMembership, type RatingSummary, type Rider,
} from './lib/eats';
import {
  addProductFavorite, advanceOrderStatus, askProductInquiry, cancelOrder, decideOrderReturn, fetchMerchantOrders, fetchMerchantProducts, fetchMerchantReturnQueue,
  fetchMyFavoriteProducts, fetchMyOrders, fetchMyReturnRequests, fetchOrderDetail, fetchOrderRiderLocation, fetchPriceTiers,
  fetchProductInquiries, fetchProductRating, fetchProductReviews, ORDER_RETURN_REASON_CODES, placeOrder, removeProductFavorite, requestOrderReturn, submitProductReview,
  type CommerceOrder, type CommerceOrderItem, type CommerceOrderStatus, type CommerceProduct, type FavoriteProduct, type OrderReturnRequestDto, type OrderReturnType, type PriceTier, type ProductInquiry, type ProductReview,
} from './lib/commerce';
import {
  captureReferralCodeFromUrl, createAffiliateLink, fetchMyAffiliateCommissions, fetchMyAffiliateLinks, getStoredReferralCode,
  type AffiliateCommission, type AffiliateLink,
} from './lib/affiliate';
import { cancelBooking, createBooking, fetchAvailableSlots, fetchCouponsForCustomer, fetchMerchantReviews, fetchMyBookings, submitBookingReview, type BookingSlot, type MerchantBooking, type MerchantBookingReview, type MerchantCoupon } from './lib/booking';
import MapView from './MapView';
import RouteMiniMap from './RouteMiniMap';
import LiveRiderMap from './LiveRiderMap';
import SimpleLiveRiderMap from './SimpleLiveRiderMap';
import { searchPlaces, type PlaceSearchResult } from './lib/maps';
import { fetchMiniAppCatalog, type PartnerMiniApp } from './lib/partners';
import { checkScamStatus, reportScam, type ScamCheckResult } from './lib/scamReports';
import { fetchMyVehicles, fetchVehicleValuation, registerVehicle, removeVehicle, updateVehicleMileage, type Vehicle, type VehicleValuation } from './lib/vehicles';
import {
  fetchChildOverview, fetchMyChildren, fetchMyGuardians, fetchMyInvites, inviteChild, respondToInvite, revokeFamilyLink,
  type ChildOverview, type FamilyLinkView,
} from './lib/family';
import {
  cancelProductSubscription, fetchMyProductSubscriptions, pauseProductSubscription, resumeProductSubscription, subscribeToProduct,
  type ProductSubscription,
} from './lib/productSubscriptions';
import { cancelScheduledTransfer, createScheduledTransfer, fetchMyScheduledTransfers, type ScheduledTransfer } from './lib/scheduledTransfers';
import {
  cancelAutoTransfer, createAutoTransfer, fetchMyAutoTransfers, pauseAutoTransfer, resumeAutoTransfer,
  type AutoTransfer, type AutoTransferFrequency,
} from './lib/autoTransfers';
import {
  acceptRideTrip, arriveAtRideStop, cancelRideTrip, completeRideTrip, declineRideTrip, fetchAvailableTrips, fetchDriverRating,
  fetchMyDriverProfile, fetchMyDriverTrips, fetchMyTrips, fetchTripStops, registerAsDriver, requestRideTrip, setDriverAvailability,
  startRideTrip, submitRideReview, updateDriverLocation,
  type RideDriver, type RideDriverRating, type RideTrip, type RideTripStop,
} from './lib/rideshare';
import {
  acceptDesignatedDriverTrip, cancelDesignatedDriverTrip, completeDesignatedDriverTrip, fetchAvailableDesignatedDriverTrips,
  fetchMyDesignatedDriverDriverTrips, fetchMyDesignatedDriverProfile, fetchMyDesignatedDriverTrips, registerAsDesignatedDriver,
  requestDesignatedDriverTrip, setDesignatedDriverAvailability, startDesignatedDriverTrip, updateDesignatedDriverLocation,
  type DesignatedDriver, type DesignatedDriverTrip,
} from './lib/designatedDriver';
import {
  endBikeAssetRental, fetchMyBikeAssetRentalHistory, fetchMyBikeAssets, fetchNearbyBikeAssets, registerBikeAsset, setBikeAssetAvailability,
  startBikeAssetRental, type BikeAsset, type BikeAssetRentalSession, type BikeAssetType,
} from './lib/bikeshare';
import {
  endParkingSession, fetchMyParkingHistory, fetchMyParkingSpots, fetchNearbyParkingSpots, registerParkingSpot, setParkingSpotAvailability,
  startParkingSession, type ParkingSession, type ParkingSpot,
} from './lib/parking';
import {
  bookBusSeats, cancelBusBooking, fetchBusTripBookings, fetchMyBusBookings, fetchMyBusTrips, postBusTrip, searchBusTrips,
  type BusBooking, type BusTrip,
} from './lib/bus';
import {
  adoptKnowledgeAnswer, fetchKnowledgeAnswers, fetchKnowledgeCategories, fetchKnowledgeQuestion, fetchKnowledgeQuestions,
  fetchMyKnowledgeAnswers, fetchMyKnowledgeQuestions, fetchMyKnowledgeReputation, postKnowledgeAnswer, postKnowledgeQuestion,
  type KnowledgeAnswer, type KnowledgeCategory, type KnowledgeQuestion,
} from './lib/knowledge';
import {
  acceptInspection, cancelInspection, completeInspection, fetchAvailableMechanics, fetchMyInspectionBookings,
  fetchMyMechanicBookings, fetchMyMechanicProfile, registerAsMechanic, requestInspection, setMechanicAvailability,
  type VehicleInspectionBooking, type VehicleInspectionMechanic,
} from './lib/vehicleInspection';

type Tab = 'HOME' | 'CERTIFICATE' | 'SHOPPING' | 'SHOP' | 'STOCKS' | 'SAVINGS' | 'MESSAGES' | 'MARKETPLACE' | 'COMMUNITY' | 'JOBS' | 'PROPERTY' | 'EATS' | 'RIDES' | 'DESIGNATED_DRIVER' | 'BIKESHARE' | 'PARKING' | 'BUS' | 'KNOWLEDGE' | 'MAP' | 'DEVICES' | 'CARD' | 'OVERVIEW' | 'LOANS' | 'CREDIT_SCORE' | 'TRUST_SCORE' | 'IDENTITY' | 'SUPPORT' | 'MY' | 'SUBSCRIPTIONS' | 'SPENDING' | 'FOREIGN_CURRENCY' | 'REWARDS' | 'INSURANCE' | 'BILLS' | 'AGENT' | 'USSD';

function AccountBalance({ wallet, onTransferClick }: { wallet: Wallet | null; onTransferClick: () => void }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, ease: 'easeOut' }}
      className="toss-card"
      style={{ padding: '28px', position: 'relative', overflow: 'hidden' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
        <p style={{ color: 'var(--toss-grey-700)', fontSize: '15px', fontWeight: '600' }}>{wallet?.accountName ?? 'Main Account'}</p>
        <ShieldCheck size={20} color="var(--toss-green)" />
      </div>

      <h1 style={{ color: 'var(--toss-grey-900)', fontSize: '36px', fontWeight: '700', margin: '0 0 28px 0', letterSpacing: '-0.5px', display: 'flex', alignItems: 'baseline', gap: '4px' }}>
        {(wallet?.balance ?? 0).toLocaleString()} <span style={{ fontSize: '20px', color: 'var(--toss-grey-500)', fontWeight: '600' }}>{wallet?.currency ?? 'RWF'}</span>
      </h1>

      <div style={{ display: 'flex', gap: '12px' }}>
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-primary" style={{ flex: 1, gap: '8px' }} onClick={onTransferClick}>
          <ArrowUpRight size={18} /> Transfer
        </motion.button>
        <motion.button whileTap={{ scale: 0.96 }} className="toss-btn toss-btn-secondary" style={{ flex: 1, gap: '8px' }} disabled title="Real mobile-money top-up needs a live MTN/Airtel/bank provider relationship this backend doesn't have yet -- see docs/TOSS_PARITY_MATRIX.md's Transfer row">
          <Plus size={18} /> Top up
        </motion.button>
      </div>
    </motion.div>
  );
}

// Real direct itunda-to-itunda push-transfer (2026-07-20) -- closes a real gap found
// live while first wiring this exact button: WalletController's quote/confirm transfer
// (used by Android/iOS's sendTransfer) always routes through a simulated external rail
// and never actually credits another itunda user's wallet, even when the recipient is a
// real itunda account (confirmed via direct MySQL query: recipientId stayed "external").
// This now calls the new real rw.itunda.p2p.sendDirect instead -- a real recipient
// resolved by phone number or account number, credited immediately, no fee (nothing
// external to settle). No network "quote" step needed (unlike the external-rail flow,
// there's no rail decision to quote) -- the review screen below is a client-side
// confirmation only, same inline-card-replaces-trigger convention every other flow in
// this file already uses, not a modal overlay.
// Real device binding step-up (2026-07-20) -- shown wherever a money-moving call
// real-403s with DEVICE_NOT_VERIFIED. Re-proves password ownership on THIS device
// (resolved server-side from the caller's own JWT, never a client-supplied id) and
// marks it trusted, matching the same real re-verification Toss requires before a
// new device can move money. See lib/device.ts's own doc comment for the full account.
function DeviceStepUpPrompt({ onVerified, onCancel }: { onVerified: () => void; onCancel: () => void }) {
  const [password, setPassword] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setBusy(true);
    try {
      await verifyDevice(password);
      onVerified();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not verify this device.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>🔒 Verify this device</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        This is a new device for your account. Re-enter your password to allow it to send money, then try again.
      </p>
      <input
        type="password"
        value={password}
        onChange={(e) => setPassword(e.target.value)}
        placeholder="Password"
        required
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onCancel} disabled={busy}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>
          {busy ? 'Verifying…' : 'Verify device'}
        </button>
      </div>
    </form>
  );
}

// Real Toss 사기계좌 조회-style report action -- see lib/scamReports.ts's own doc
// comment.
function ReportScamLink({ identifier }: { identifier: string }) {
  const [reporting, setReporting] = useState(false);
  const [done, setDone] = useState(false);

  const handleReport = async () => {
    const reason = window.prompt(`Why are you reporting ${identifier}?`);
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

  if (done) return <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Thanks -- this number has been reported.</p>;

  return (
    <button type="button" onClick={handleReport} disabled={reporting} style={{ fontSize: '12px', color: 'var(--toss-grey-500)', textAlign: 'left' }}>
      {reporting ? 'Reporting…' : 'Report this number as a scam'}
    </button>
  );
}

function TransferFlow({ onClose, onSuccess }: { onClose: () => void; onSuccess: () => void }) {
  const [recipient, setRecipient] = useState('');
  const [amount, setAmount] = useState('');
  const [reviewing, setReviewing] = useState(false);
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

  const loadContacts = () => fetchContacts().then(setContacts).catch(() => {});
  useEffect(() => { loadContacts(); }, []);

  const handleAddContact = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await addContact(newContactName, newContactPhone);
      setNewContactName(''); setNewContactPhone(''); setShowAddContact(false);
      loadContacts();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save that contact.');
    }
  };

  const handleReview = (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setScamCheck(null);
    setReviewing(true);
    checkScamStatus(recipient.trim()).then(setScamCheck).catch(() => {
      // Real, non-critical -- a failed safety check must never block a real transfer
      // the sender is otherwise entitled to make.
    });
  };

  const handleConfirm = async () => {
    setError(null);
    setNeedsDeviceVerification(false);
    setBusy(true);
    try {
      const res = await sendDirect(recipient.trim(), Number(amount), '');
      setResult({ message: res.message, newBalance: res.newBalance });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        // A real, honest error surfaces here as-is -- e.g. a recipient that doesn't
        // match any real itunda account real-404s rather than silently doing nothing.
        setError(err instanceof ApiError ? err.message : 'Could not complete this transfer.');
      }
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px', marginBottom: '16px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>{result.message}</h3>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          New balance: {result.newBalance.toLocaleString()} RWF
        </p>
        <button className="toss-btn toss-btn-secondary" onClick={onSuccess}>Done</button>
      </div>
    );
  }

  if (reviewing) {
    return (
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Confirm transfer</h3>
        <div style={{ fontSize: '13px', color: 'var(--toss-grey-700)', display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <span>To {recipient}</span>
          <span style={{ fontWeight: 700 }}>Amount: {Number(amount).toLocaleString()} RWF</span>
        </div>
        {scamCheck?.warn && (
          <div style={{ backgroundColor: '#FDECEA', border: '1px solid #E53935', borderRadius: '8px', padding: '10px 12px' }}>
            <p style={{ fontSize: '13px', fontWeight: 700, color: '#E53935' }}>Caution needed before this transfer</p>
            <p style={{ fontSize: '12px', color: '#E53935', marginTop: '2px' }}>
              This recipient has been reported by {scamCheck.reportCount} other itunda users. Double-check before sending.
            </p>
          </div>
        )}
        {needsDeviceVerification ? (
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={onClose} />
        ) : (
          <>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onClose} disabled={busy}>Cancel</button>
              <button type="button" className="toss-btn toss-btn-primary" style={{ flex: 1 }} onClick={handleConfirm} disabled={busy}>
                {busy ? 'Sending…' : 'Confirm'}
              </button>
            </div>
            {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
            <ReportScamLink identifier={recipient.trim()} />
          </>
        )}
      </div>
    );
  }

  return (
    <form onSubmit={handleReview} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Transfer</h3>
      <input
        type="text" value={recipient} onChange={(e) => setRecipient(e.target.value)} placeholder="Recipient phone or account number" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required min="1"
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-500)' }}>Contacts</p>
        <button type="button" onClick={() => setShowAddContact((v) => !v)} style={{ fontSize: '12px', color: 'var(--toss-blue)', fontWeight: 700, background: 'none', border: 'none' }}>
          {showAddContact ? 'Cancel' : '+ Add'}
        </button>
      </div>
      {showAddContact && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="text" value={newContactName} onChange={(e) => setNewContactName(e.target.value)} placeholder="Name"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="text" value={newContactPhone} onChange={(e) => setNewContactPhone(e.target.value)} placeholder="Phone number"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="button" className="toss-btn toss-btn-secondary" disabled={!newContactName || !newContactPhone} onClick={handleAddContact}>
            Save contact
          </button>
        </div>
      )}
      {contacts.length === 0 && !showAddContact && (
        <EmptyState message="No saved contacts yet." />
      )}
      {contacts.map((c) => (
        <button
          type="button" key={c.id}
          onClick={() => setRecipient(c.phoneNumber)}
          style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 0', background: 'none', border: 'none', textAlign: 'left' }}
        >
          <span style={{ fontSize: '13px', fontWeight: 700 }}>{c.name}</span>
          <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{c.bank} · {c.phoneNumber}</span>
        </button>
      ))}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={onClose}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }}>Continue</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function QuickActions() {
  const actions = [
    { title: 'Scan to Pay', icon: <ScanFace size={24} color="var(--toss-blue)" />, bg: 'var(--toss-blue-light)' },
    { title: 'Cards', icon: <WalletIcon size={24} color="#8A2BE2" />, bg: 'rgba(138, 43, 226, 0.1)' },
  ];

  return (
    <div style={{ display: 'flex', gap: '12px', marginBottom: '16px' }}>
      {actions.map((action, i) => (
        <motion.div
          key={i}
          whileTap={{ scale: 0.96 }}
          className="toss-card"
          style={{ flex: 1, padding: '20px', margin: 0, display: 'flex', flexDirection: 'column', alignItems: 'flex-start', gap: '16px', cursor: 'pointer' }}
        >
          <div style={{ width: '48px', height: '48px', borderRadius: '16px', backgroundColor: action.bg, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            {action.icon}
          </div>
          <span style={{ fontWeight: '600', fontSize: '15px', color: 'var(--toss-grey-900)' }}>{action.title}</span>
        </motion.div>
      ))}
    </div>
  );
}

function TransactionHistory({ transactions, unusuallyLargeIds }: { transactions: Transaction[]; unusuallyLargeIds?: Set<string> }) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 20 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ duration: 0.5, delay: 0.1, ease: 'easeOut' }}
      className="toss-card"
      style={{ padding: '24px 20px' }}
    >
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '24px', padding: '0 4px' }}>
        <h3 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '18px', fontWeight: '700' }}>Recent Activity</h3>
      </div>

      {transactions.length === 0 ? (
        <EmptyState message="No transactions yet." />
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
                    <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-500)' }}>
                      {tx.channel ? tx.channel.slice(0, 2) : tx.type.slice(0, 2)}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                      <span style={{ color: 'var(--toss-grey-900)', fontWeight: '600', fontSize: '16px' }}>{tx.description}</span>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                        <span style={{ color: 'var(--toss-grey-500)', fontSize: '13px', fontWeight: '500' }}>{new Date(tx.createdAt).toLocaleString()}</span>
                        {isUnusual && (
                          <span style={{ color: '#E53935', fontSize: '11px', fontWeight: '700', backgroundColor: '#FEECEE', padding: '2px 6px', borderRadius: '6px' }}>
                            Unusually large
                          </span>
                        )}
                      </div>
                    </div>
                  </div>
                  <span style={{ fontWeight: '700', fontSize: '16px', color: isUnusual ? '#E53935' : isCredit ? 'var(--toss-blue)' : 'var(--toss-grey-900)' }}>
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

function HomeView() {
  const [wallet, setWallet] = useState<Wallet | null | undefined>(undefined);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [unusuallyLargeIds, setUnusuallyLargeIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);
  const [showTransfer, setShowTransfer] = useState(false);

  const load = () => {
    setError(null);
    Promise.all([fetchWallets(), fetchTransactions()])
      .then(([wallets, txs]) => {
        setWallet(wallets.find((w) => w.type === 'MAIN') ?? wallets[0] ?? null);
        setTransactions(txs);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your account.'));
    // Real Toss Timeline-style unusual-spend flag -- fetched independently of the main
    // wallet/transactions load so a failure here never blocks the core balance view.
    fetchTransactionTimeline()
      .then((entries) => setUnusuallyLargeIds(new Set(entries.filter((e) => e.unusuallyLarge).map((e) => e.transaction.id))))
      .catch(() => {});
  };

  useEffect(load, []);

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (wallet === undefined) {
    return (
      <div>
        <div className="toss-card skeleton" style={{ height: '180px', marginBottom: '16px' }} />
        <div className="toss-card skeleton" style={{ height: '300px' }} />
      </div>
    );
  }

  return (
    <div>
      <AccountBalance wallet={wallet} onTransferClick={() => setShowTransfer(true)} />
      {showTransfer && (
        <TransferFlow
          onClose={() => setShowTransfer(false)}
          onSuccess={() => {
            setShowTransfer(false);
            load();
          }}
        />
      )}
      <QuickActions />
      <TransactionHistory transactions={transactions} unusuallyLargeIds={unusuallyLargeIds} />
      <ScheduledTransfersCard />
      <AutoTransfersCard />
      {wallet && <AutoTopUpCard walletId={wallet.id} />}
      <RequestMoneyCard />
      <MiniWalletCard />
      <DiscoverSection />
    </div>
  );
}

// Real curated promo rail -- see lib/discover.ts's own doc comment. Android already has
// this (DiscoverSection in ItundaAppScreen.kt, found real on backend + Android with zero
// client anywhere else); this is the first bank-mfe/iOS client. Purely informational --
// no click-through action or money movement, mirroring Android's own honest scope.
function DiscoverSection() {
  const [items, setItems] = useState<DiscoverItem[]>([]);

  useEffect(() => {
    fetchDiscoverItems().then(setItems).catch(() => {});
  }, []);

  if (items.length === 0) return null;

  return (
    <div style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: '19px', fontWeight: 700, marginBottom: '10px' }}>Discover</h3>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {items.map((item) => (
          <div key={item.id} className="toss-card" style={{ display: 'flex', alignItems: 'center', gap: '12px', borderRadius: '20px' }}>
            <div style={{ width: '8px', height: '8px', borderRadius: '4px', backgroundColor: item.color, flexShrink: 0 }} />
            <div style={{ flex: 1 }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
                <span style={{ fontSize: '16px', fontWeight: 600 }}>{item.title}</span>
                {item.isNew && <span style={{ fontSize: '11px', fontWeight: 700, color: item.color }}>NEW</span>}
              </div>
              <p style={{ fontSize: '14px', color: 'var(--toss-grey-500)' }}>{item.subtitle}</p>
            </div>
            {item.badge && <span style={{ fontSize: '13px', fontWeight: 600, color: item.color }}>{item.badge}</span>}
          </div>
        ))}
      </div>
    </div>
  );
}

// Real KakaoBank mini-style capped starter wallet -- see lib/miniWallet.ts's own doc
// comment. First client UI for this backend feature on any platform (item 99, found
// with zero client anywhere despite the backend being real and live since 2026-07-28).
function MiniWalletCard() {
  const [miniWallet, setMiniWallet] = useState<Wallet | null | undefined>(undefined);
  const [needsBirthDate, setNeedsBirthDate] = useState(false);
  const [birthDate, setBirthDateInput] = useState('');
  const [amount, setAmount] = useState('');
  const [showDeposit, setShowDeposit] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchWallets().then((wallets) => setMiniWallet(wallets.find((w) => w.type === 'MINI') ?? null)).catch(() => setMiniWallet(null));
  };

  useEffect(load, []);

  const handleOpen = async () => {
    setBusy(true);
    setError(null);
    try {
      const wallet = await openMiniWallet();
      setMiniWallet(wallet);
      setNeedsBirthDate(false);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'MINI_WALLET_BIRTH_DATE_REQUIRED') {
        setNeedsBirthDate(true);
      } else if (err instanceof ApiError && err.code === 'MINI_WALLET_AGE_INELIGIBLE') {
        setError('Mini accounts are only available for ages 7-18.');
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not open a Mini account.');
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
      setError(err instanceof ApiError ? err.message : 'Could not save your birth date.');
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
      await depositToMiniWallet(parsedAmount);
      setAmount('');
      setShowDeposit(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add money to your Mini account.');
    } finally {
      setBusy(false);
    }
  };

  if (miniWallet === undefined) return null;

  return (
    <div className="toss-card" style={{ padding: '16px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Mini account</h3>
        {miniWallet && (
          <button className="toss-btn toss-btn-secondary" onClick={() => setShowDeposit((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
            {showDeposit ? 'Cancel' : '+ Add money'}
          </button>
        )}
      </div>

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {!miniWallet && !needsBirthDate && (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
            A capped starter account for ages 7-18 -- a 500,000 RWF balance cap, 300,000 RWF daily and 2,000,000 RWF monthly deposit limits.
          </p>
          <button className="toss-btn toss-btn-primary" onClick={handleOpen} disabled={busy}>{busy ? 'Opening…' : 'Open a Mini account'}</button>
        </div>
      )}

      {!miniWallet && needsBirthDate && (
        <form onSubmit={handleSetBirthDateAndOpen} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Enter your birth date to check eligibility.</p>
          <input
            type="date" value={birthDate} onChange={(e) => setBirthDateInput(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Checking…' : 'Continue'}</button>
        </form>
      )}

      {miniWallet && (
        <div>
          <p style={{ fontSize: '20px', fontWeight: 700 }}>{miniWallet.balance.toLocaleString()} RWF</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: showDeposit ? '10px' : 0 }}>{miniWallet.accountNumber}</p>
          {showDeposit && (
            <form onSubmit={handleDeposit} style={{ display: 'flex', gap: '8px' }}>
              <input
                type="number" placeholder="Amount (RWF)" value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
                style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add'}</button>
            </form>
          )}
        </div>
      )}
    </div>
  );
}

const SCHEDULED_TRANSFER_STATUS_LABEL: Record<ScheduledTransfer['status'], string> = {
  PENDING: 'Scheduled',
  EXECUTED: 'Sent',
  CANCELLED: 'Cancelled',
  FAILED: 'Failed',
};

// Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
// lib/scheduledTransfers.ts's own doc comment. Distinct from AutoTransfer (recurring,
// which itself still has no bank-mfe client anywhere -- left as its own separately
// named, still-deferred gap; not expanded in this pass).
function ScheduledTransfersCard() {
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
      setError(err instanceof ApiError ? err.message : 'Could not schedule this transfer.');
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this scheduled transfer.');
    } finally {
      setBusyId(null);
    }
  };

  const pending = (transfers ?? []).filter((t) => t.status === 'PENDING');
  const past = (transfers ?? []).filter((t) => t.status !== 'PENDING');
  const minDate = new Date(Date.now() + 24 * 3600 * 1000).toISOString().slice(0, 10);

  return (
    <div className="toss-card" style={{ padding: '16px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Scheduled transfers</h3>
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ Schedule'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Phone or account number" value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" placeholder="Amount (RWF)" value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="date" value={scheduledDate} onChange={(e) => setScheduledDate(e.target.value)} min={minDate} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="text" placeholder="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Scheduling…' : 'Schedule transfer'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {pending.length === 0 && past.length === 0 && (
        <EmptyState message="No scheduled transfers yet." />
      )}

      {[...pending, ...past.slice(0, 3)].map((t) => (
        <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
          <div>
            <p style={{ fontSize: '13px', fontWeight: 700 }}>{t.recipientName} · {t.amount.toLocaleString()} RWF</p>
            <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{t.scheduledDate} · {SCHEDULED_TRANSFER_STATUS_LABEL[t.status]}</p>
          </div>
          {t.status === 'PENDING' && (
            <button className="toss-btn toss-btn-secondary" disabled={busyId === t.id} onClick={() => handleCancel(t.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
              {busyId === t.id ? '…' : 'Cancel'}
            </button>
          )}
        </div>
      ))}
    </div>
  );
}

const AUTO_TRANSFER_STATUS_LABEL: Record<AutoTransfer['status'], string> = {
  ACTIVE: 'Active', PAUSED: 'Paused', CANCELLED: 'Cancelled',
};
const WEEKDAY_NAMES = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'];

// Real Toss Bank 자동이체 (auto-transfer) -- see lib/autoTransfers.ts's own doc
// comment. Recurring, genuinely distinct from ScheduledTransfersCard's own one-time
// 예약송금 above. First bank-mfe client for a backend that previously had none.
function AutoTransfersCard() {
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
      setError(err instanceof ApiError ? err.message : 'Could not set up this auto-transfer.');
    } finally {
      setBusy(false);
    }
  };

  const handleToggle = async (t: AutoTransfer) => {
    setBusyId(t.id);
    setError(null);
    try {
      if (t.status === 'ACTIVE') await pauseAutoTransfer(t.id);
      else await resumeAutoTransfer(t.id);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this auto-transfer.');
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this auto-transfer.');
    } finally {
      setBusyId(null);
    }
  };

  const active = (transfers ?? []).filter((t) => t.status !== 'CANCELLED');
  const cancelled = (transfers ?? []).filter((t) => t.status === 'CANCELLED');

  return (
    <div className="toss-card" style={{ padding: '16px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Auto-transfers</h3>
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ Set up'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Phone or account number" value={recipient} onChange={(e) => setRecipient(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" placeholder="Amount (RWF)" value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <select
            value={frequency} onChange={(e) => setFrequency(e.target.value as AutoTransferFrequency)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          >
            <option value="WEEKLY">Weekly</option>
            <option value="MONTHLY">Monthly</option>
          </select>
          {frequency === 'WEEKLY' ? (
            <select
              value={dayOfWeek} onChange={(e) => setDayOfWeek(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            >
              {WEEKDAY_NAMES.map((name, i) => <option key={name} value={i + 1}>{name}</option>)}
            </select>
          ) : (
            <select
              value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            >
              {Array.from({ length: 28 }, (_, i) => i + 1).map((d) => <option key={d} value={d}>Day {d} of the month</option>)}
            </select>
          )}
          <input
            type="text" placeholder="Description (optional)" value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Setting up…' : 'Set up auto-transfer'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {active.length === 0 && cancelled.length === 0 && (
        <EmptyState message="No auto-transfers set up yet." />
      )}

      {[...active, ...cancelled.slice(0, 2)].map((t) => (
        <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
          <div>
            <p style={{ fontSize: '13px', fontWeight: 700 }}>{t.recipientName} · {t.amount.toLocaleString()} RWF</p>
            <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
              {t.frequency === 'WEEKLY' ? `Weekly (${WEEKDAY_NAMES[(t.dayOfWeek ?? 1) - 1]})` : `Monthly (day ${t.dayOfMonth})`} · {AUTO_TRANSFER_STATUS_LABEL[t.status]}
              {t.lastFailureReason && ` · ${t.lastFailureReason}`}
            </p>
          </div>
          {t.status !== 'CANCELLED' && (
            <div style={{ display: 'flex', gap: '6px' }}>
              <button className="toss-btn toss-btn-secondary" disabled={busyId === t.id} onClick={() => handleToggle(t)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                {busyId === t.id ? '…' : t.status === 'ACTIVE' ? 'Pause' : 'Resume'}
              </button>
              <button className="toss-btn toss-btn-secondary" disabled={busyId === t.id} onClick={() => handleCancel(t.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                Cancel
              </button>
            </div>
          )}
        </div>
      ))}
    </div>
  );
}

const P2P_REQUEST_STATUS_LABEL: Record<P2pPaymentRequestStatus, string> = {
  PENDING: 'Pending', COMPLETED: 'Paid', EXPIRED: 'Expired',
};

// Real fixed-amount person-to-person payment request (item 167) -- see lib/p2p.ts's
// own doc comment. A real 15-minute-expiring code the requester shares (typed/pasted,
// same real manual-code-entry convention MerchantController.collect's own bank-mfe
// client already established -- this app has no camera QR scanner anywhere); anyone
// who has the code can pay it directly, real wallet-to-wallet, no fee.
function RequestMoneyCard() {
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
      setError(err instanceof ApiError ? err.message : 'Could not create this request.');
    } finally {
      setCreating(false);
    }
  };

  const handlePay = async (e: React.FormEvent) => {
    e.preventDefault();
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
        setError(err instanceof ApiError ? err.message : 'Could not pay this request.');
      }
    } finally {
      setPaying(false);
    }
  };

  return (
    <div className="toss-card" style={{ padding: '16px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Request money</h3>
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ New request'}
        </button>
      </div>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="number" placeholder="Amount (RWF)" value={amount} onChange={(e) => setAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="text" placeholder="What's it for? (optional)" value={description} onChange={(e) => setDescription(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={creating}>{creating ? 'Creating…' : 'Create request'}</button>
        </form>
      )}

      {created && (
        <div style={{ padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px', marginBottom: '12px' }}>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Share this code -- expires in 15 minutes</p>
          <p style={{ fontSize: '16px', fontWeight: 700, fontFamily: 'monospace', wordBreak: 'break-all' }}>{created.id}</p>
        </div>
      )}

      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : (
        <form onSubmit={handlePay} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Pay a request code" value={payCode} onChange={(e) => setPayCode(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={paying} style={{ padding: '10px 16px', fontSize: '13px' }}>
            {paying ? 'Paying…' : 'Pay'}
          </button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {requests !== null && requests.length > 0 && (
        <div>
          <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-500)', marginBottom: '4px' }}>My requests</p>
          {requests.slice(0, 5).map((r) => (
            <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{r.amount.toLocaleString()} RWF{r.description ? ` · ${r.description}` : ''}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{P2P_REQUEST_STATUS_LABEL[r.status]}</p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168) -- see
// lib/wallet.ts's own doc comment.
function AutoTopUpCard({ walletId }: { walletId: string }) {
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
    fetchAutoTopUpSetting(walletId)
      .then(setSetting)
      .catch(() => setSetting(null));
    fetchLinkedAccounts().then((accounts) => setLinkedAccounts(accounts.filter((a) => a.status === 'LINKED'))).catch(() => {});
  };
  useEffect(load, [walletId]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const threshold = Number(thresholdAmount);
    const topUp = Number(topUpAmount);
    if (!linkedAccountId || !(threshold >= 0) || !(topUp > 0)) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(walletId, linkedAccountId, threshold, topUp, Number(dailyTriggerCap) || 3, true);
      setShowForm(false);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save this setting.');
    } finally {
      setBusy(false);
    }
  };

  const handleToggle = async () => {
    if (!setting) return;
    setBusy(true);
    setError(null);
    try {
      await configureAutoTopUp(walletId, setting.linkedAccountId, setting.thresholdAmount, setting.topUpAmount, setting.dailyTriggerCap, !setting.enabled);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this setting.');
    } finally {
      setBusy(false);
    }
  };

  const handleTrigger = async () => {
    setTriggering(true);
    setTriggerResult(null);
    try {
      const r = await triggerAutoTopUp(walletId);
      setTriggerResult(r.reason);
      load();
    } catch (err) {
      setTriggerResult(err instanceof ApiError ? err.message : 'Could not check auto top-up.');
    } finally {
      setTriggering(false);
    }
  };

  if (setting === undefined) return null;

  return (
    <div className="toss-card" style={{ padding: '16px', marginTop: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Auto top-up</h3>
        {linkedAccounts.length > 0 && (
          <button className="toss-btn toss-btn-secondary" onClick={() => setShowForm((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
            {showForm ? 'Cancel' : setting ? 'Edit' : '+ Set up'}
          </button>
        )}
      </div>

      {linkedAccounts.length === 0 && !setting && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Link an external bank/mobile money account first to enable auto top-up.</p>
      )}

      {showForm && (
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <select value={linkedAccountId} onChange={(e) => setLinkedAccountId(e.target.value)} required style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}>
            <option value="">Select linked account</option>
            {linkedAccounts.map((a) => <option key={a.id} value={a.id}>{a.provider} · {a.externalAccountNumberMasked}</option>)}
          </select>
          <input type="number" placeholder="Top up when balance falls below (RWF)" value={thresholdAmount} onChange={(e) => setThresholdAmount(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }} />
          <input type="number" placeholder="Top-up amount (RWF)" value={topUpAmount} onChange={(e) => setTopUpAmount(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }} />
          <input type="number" placeholder="Max times per day" value={dailyTriggerCap} onChange={(e) => setDailyTriggerCap(e.target.value)} min="1"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }} />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Saving…' : 'Save'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {setting && !showForm && (
        <div>
          <p style={{ fontSize: '13px' }}>
            {setting.enabled ? 'On' : 'Off'} — top up {setting.topUpAmount.toLocaleString()} RWF when balance falls below {setting.thresholdAmount.toLocaleString()} RWF
          </p>
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginBottom: '8px' }}>
            Up to {setting.dailyTriggerCap}x/day · {setting.triggersToday} triggered today
          </p>
          {triggerResult && <p style={{ fontSize: '12px', color: 'var(--toss-blue)', marginBottom: '8px' }}>{triggerResult}</p>}
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleToggle} style={{ fontSize: '12px', padding: '6px 10px' }}>
              {setting.enabled ? 'Turn off' : 'Turn on'}
            </button>
            <button className="toss-btn toss-btn-secondary" disabled={triggering} onClick={handleTrigger} style={{ fontSize: '12px', padding: '6px 10px' }}>
              {triggering ? 'Checking…' : 'Check now'}
            </button>
          </div>
        </div>
      )}
    </div>
  );
}

function CertificateView() {
  const [certificate, setCertificate] = useState<Certificate | null | undefined>(undefined);
  const [issuedPrivateKey, setIssuedPrivateKey] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const load = () => {
    setError(null);
    getMyCertificate()
      .then(setCertificate)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your certificate.'));
  };

  useEffect(load, []);

  const handleIssue = async () => {
    setBusy(true);
    setError(null);
    try {
      const result = await issueCertificate();
      setCertificate(result.certificate);
      setIssuedPrivateKey(result.privateKey);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not issue a certificate.');
    } finally {
      setBusy(false);
    }
  };

  const handleRevoke = async () => {
    setBusy(true);
    setError(null);
    try {
      const revoked = await revokeCertificate();
      setCertificate(revoked);
      setIssuedPrivateKey(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not revoke your certificate.');
    } finally {
      setBusy(false);
    }
  };

  if (certificate === undefined) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div className="toss-card" style={{ padding: '28px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '4px' }}>
        <ShieldCheck size={22} color={certificate?.status === 'ACTIVE' ? 'var(--toss-green)' : 'var(--toss-grey-500)'} />
        <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Itunda Certificate</h2>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '20px' }}>
        A digital certificate you can use to sign agreements in Itunda. You'll need a verified identity first.
      </p>

      {certificate && certificate.status === 'ACTIVE' ? (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-green)', fontWeight: 700, marginBottom: '8px' }}>Active</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', fontFamily: 'monospace', marginBottom: '4px' }}>
            Serial {certificate.serialNumber}
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '20px' }}>
            Expires {new Date(certificate.expiresAt).toLocaleDateString()}
          </p>
          <button className="toss-btn toss-btn-secondary" onClick={handleRevoke} disabled={busy}>
            {busy ? 'Revoking…' : 'Revoke certificate'}
          </button>
        </div>
      ) : (
        <div>
          {certificate && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
              Your previous certificate was {certificate.status.toLowerCase()}.
            </p>
          )}
          <button className="toss-btn toss-btn-primary" onClick={handleIssue} disabled={busy}>
            {busy ? 'Issuing…' : 'Issue a certificate'}
          </button>
        </div>
      )}

      {issuedPrivateKey && (
        <div style={{ marginTop: '20px', padding: '16px', borderRadius: '12px', backgroundColor: '#FFF4E5' }}>
          <p style={{ fontSize: '13px', fontWeight: 700, color: '#B25E09', marginBottom: '6px' }}>
            Save this private key now — you won't be able to see it again.
          </p>
          <p style={{ fontSize: '11px', fontFamily: 'monospace', wordBreak: 'break-all', color: '#B25E09' }}>{issuedPrivateKey}</p>
        </div>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '16px' }} role="alert">{error}</p>
      )}

      <VerifyCertificateCard />
    </div>
  );
}

// Real public certificate status/verify (2026-08-04) -- see lib/certificate.ts's own
// doc comment on getCertificateStatus/verifyCertificateSignature: the two endpoints
// that answer "does this signed thing check out," found via a fresh backend-endpoint
// sweep with zero client anywhere. Deliberately separate from CertificateView above --
// that one manages the caller's own certificate; this one checks someone else's.
function VerifyCertificateCard() {
  const [serialNumber, setSerialNumber] = useState('');
  const [payload, setPayload] = useState('');
  const [signature, setSignature] = useState('');
  const [statusResult, setStatusResult] = useState<Certificate | null>(null);
  const [verifyResult, setVerifyResult] = useState<VerifyCertificateSignatureResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleCheckStatus = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    setVerifyResult(null);
    try {
      setStatusResult(await getCertificateStatus(serialNumber));
    } catch (err) {
      setStatusResult(null);
      setError('No certificate found with that serial number.');
    } finally {
      setBusy(false);
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      setVerifyResult(await verifyCertificateSignature(serialNumber, payload, signature));
    } catch (err) {
      setVerifyResult(null);
      setError('Could not verify this signature.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ marginTop: '20px', paddingTop: '20px', borderTop: '1px solid var(--toss-grey-100)' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Verify a certificate</h3>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
        Check whether a certificate serial number is still active, or verify a document someone signed with theirs.
      </p>
      <form onSubmit={handleCheckStatus} style={{ display: 'flex', gap: '10px', marginBottom: '10px' }}>
        <input
          value={serialNumber} onChange={(e) => { setSerialNumber(e.target.value); setStatusResult(null); setVerifyResult(null); }}
          placeholder="Serial number" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn" disabled={busy}>{busy ? 'Checking…' : 'Check status'}</button>
      </form>
      {statusResult && (
        <p style={{ fontSize: '13px', marginBottom: '10px' }}>
          Status: <strong>{statusResult.status}</strong> · Expires {new Date(statusResult.expiresAt).toLocaleDateString()}
        </p>
      )}

      <h4 style={{ fontSize: '13px', fontWeight: 700, marginTop: '16px', marginBottom: '8px' }}>Verify a signature</h4>
      <form onSubmit={handleVerify} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <input
          value={payload} onChange={(e) => { setPayload(e.target.value); setVerifyResult(null); }}
          placeholder="Payload (the exact text they signed)" required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          value={signature} onChange={(e) => { setSignature(e.target.value); setVerifyResult(null); }}
          placeholder="Signature (base64)" required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={busy || !serialNumber}>
          {busy ? 'Verifying…' : 'Verify signature'}
        </button>
      </form>
      {verifyResult && (
        <div style={{ marginTop: '10px' }}>
          <p style={{ fontSize: '13px', fontWeight: 700, color: verifyResult.signatureValid ? 'var(--toss-green)' : '#E53935' }}>
            {verifyResult.signatureValid ? '✓ Signature is valid' : '✗ Signature does not match'}
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Certificate status: {verifyResult.certificateStatus}</p>
        </div>
      )}
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real Toss-style unified account overview (2026-07-22) -- found fully built on the
// backend (rw.itunda.overview) with zero client UI anywhere until the Android port
// the same day. See OverviewService.kt's own doc comment for why insurance is
// excluded from net worth (a sunk expense, not an asset) and LinkedAccount.kt's for
// why linked balances are honestly labeled demo -- itunda has no live Open Banking
// access to fetch a real one.
const LINK_PROVIDERS = ['MTN Mobile Money', 'Airtel Money', 'Bank of Kigali', 'Equity Bank Rwanda'];

function OverviewView() {
  const [overview, setOverview] = useState<Overview | null>(null);
  const [linkedAccounts, setLinkedAccounts] = useState<LinkedAccount[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showLinkForm, setShowLinkForm] = useState(false);
  const [provider, setProvider] = useState('');
  const [accountNumber, setAccountNumber] = useState('');

  const refresh = () => {
    setError(null);
    Promise.all([fetchOverview(), fetchLinkedAccounts()])
      .then(([o, linked]) => { setOverview(o); setLinkedAccounts(linked); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your overview.'));
  };

  useEffect(refresh, []);

  const handleLink = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await linkAccount(provider, accountNumber);
      setProvider(''); setAccountNumber(''); setShowLinkForm(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not link that account.');
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
      setError(err instanceof ApiError ? err.message : 'Could not unlink this account.');
    } finally {
      setBusy(false);
    }
  };

  if (!overview) {
    return <div className="toss-card skeleton" style={{ height: '260px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Net worth</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{overview.netWorth.toLocaleString()} RWF</h2>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Accounts</h3>
        {overview.accounts.map((a) => (
          <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <span>{a.name} ({a.type})</span>
            <span>{a.currency} {a.balance.toLocaleString()}</span>
          </div>
        ))}
      </div>
      <div className="toss-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <p style={{ fontSize: '13px' }}>Savings: {overview.savings.totalSaved.toLocaleString()} RWF across {overview.savings.goalCount} goal(s)</p>
        <p style={{ fontSize: '13px' }}>Loans: {overview.loans.totalOutstanding.toLocaleString()} RWF outstanding, {overview.loans.activeCount} active</p>
        <p style={{ fontSize: '13px' }}>Investments: {overview.investments.totalCostBasis.toLocaleString()} RWF cost basis, {overview.investments.holdingCount} holding(s)</p>
        <p style={{ fontSize: '13px' }}>Insurance: {overview.insurance.activePolicyCount} active plan(s), {overview.insurance.totalMonthlyPremium.toLocaleString()} RWF/month</p>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Linked accounts</h3>
        {linkedAccounts.map((a) => (
          <div key={a.id} style={{ padding: '8px 0', borderBottom: '1px solid var(--toss-grey-100)' }}>
            <p style={{ fontSize: '13px', fontWeight: 700 }}>{a.provider}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{a.externalAccountNumberMasked} · {a.status}</p>
            {a.demoBalance != null && (
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Demo balance: {a.demoBalanceCurrency} {a.demoBalance.toLocaleString()}</p>
            )}
            {a.status === 'LINKED' && (
              <button className="toss-btn toss-btn-secondary" style={{ marginTop: '4px' }} disabled={busy} onClick={() => handleUnlink(a.id)}>Unlink</button>
            )}
          </div>
        ))}
        {!showLinkForm ? (
          <button className="toss-btn toss-btn-primary" style={{ marginTop: '10px' }} onClick={() => setShowLinkForm(true)}>
            Link a bank or mobile money account
          </button>
        ) : (
          <form onSubmit={handleLink} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
              {LINK_PROVIDERS.map((p) => (
                <button type="button" key={p} className="toss-btn toss-btn-secondary" onClick={() => setProvider(p)}>{p}</button>
              ))}
            </div>
            <input
              type="text" value={provider} onChange={(e) => setProvider(e.target.value)} placeholder="Provider name" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            <input
              type="text" value={accountNumber} onChange={(e) => setAccountNumber(e.target.value)} placeholder="Account / phone number" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Linking…' : 'Link account'}</button>
          </form>
        )}
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

// Real multi-lender loan marketplace (2026-07-22) -- found fully built on the backend
// (rw.itunda.loans, BNR-licensed partner banks alongside itunda's own book, see
// LoanOffer.kt's own doc comment) with zero client UI anywhere.
function LoansView() {
  const [mode, setMode] = useState<'OFFERS' | 'MY_LOANS' | 'OVERDRAFT' | 'POSTPAID_CREDIT' | 'HARVEST_ADVANCE' | 'VUP' | 'STUDENT' | 'MOTO_OWNERSHIP'>('OFFERS');
  const [offers, setOffers] = useState<LoanOffer[] | null>(null);
  const [myLoans, setMyLoans] = useState<LoanAccount[] | null>(null);
  const [lenders, setLenders] = useState<Lender[] | null>(null);
  const [lenderId, setLenderId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    Promise.all([fetchLoanOffers(), fetchMyLoans(), fetchLenders()])
      .then(([o, l, ln]) => { setOffers(o); setMyLoans(l); setLenders(ln); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load loans.'));
  };

  useEffect(refresh, []);

  // Real "browse by lender" filter (2026-07-29) -- `getLenders`/`lenderId`-filtered
  // `getOffers` were both real backend endpoints with zero client anywhere: every offer
  // already showed its lenderName, but there was no way to browse the BNR-licensed
  // partner banks (Bank of Kigali/Equity/Urwego) alongside itunda's own book as a group.
  const selectLender = (id: string | null) => {
    setLenderId(id);
    setError(null);
    fetchLoanOffers(id ?? undefined)
      .then(setOffers)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load offers.'));
  };

  const handleApply = async (offer: LoanOffer, amount: number) => {
    setBusyId(offer.id);
    setError(null);
    try {
      await applyForLoan(offer.id, amount);
      setMode('MY_LOANS');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That loan application could not be completed.');
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (loan: LoanAccount) => {
    const amount = Number(repayAmounts[loan.id] ?? '');
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(loan.id);
    setError(null);
    try {
      await repayLoan(loan.id, amount);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loan.id]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That repayment could not be completed.');
    } finally {
      setBusyId(null);
    }
  };

  // Real 대환대출 (loan refinancing, 2026-07-26) -- see LoansService.refinanceLoan's
  // own doc comment.
  const [refinanceResult, setRefinanceResult] = useState<{ oldRate: number; newRate: number; newLoanName: string } | null>(null);
  const handleRefinance = async (loan: LoanAccount) => {
    setBusyId(loan.id);
    setError(null);
    setRefinanceResult(null);
    try {
      const result = await refinanceLoan(loan.id);
      setRefinanceResult({ oldRate: result.oldInterestRate, newRate: result.newInterestRate, newLoanName: result.newLoanName });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'No better rate is available for this loan right now.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('OFFERS')}>Offers</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('MY_LOANS')}>My loans ({myLoans?.length ?? 0})</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('OVERDRAFT')}>Overdraft</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('POSTPAID_CREDIT')}>Postpaid credit</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('HARVEST_ADVANCE')}>Harvest advance</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('VUP')}>VUP Financial Services</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('STUDENT')}>BRD Student Loan</button>
        <button className="toss-btn toss-btn-secondary" onClick={() => setMode('MOTO_OWNERSHIP')}>Moto-Taxi Ownership</button>
      </div>
      {mode === 'OVERDRAFT' && <OverdraftView />}
      {mode === 'POSTPAID_CREDIT' && <PostpaidCreditView />}
      {mode === 'HARVEST_ADVANCE' && <HarvestAdvanceView />}
      {mode === 'VUP' && <VupLoanView />}
      {mode === 'STUDENT' && <StudentLoanView />}
      {mode === 'MOTO_OWNERSHIP' && <MotoOwnershipView />}
      {mode !== 'OVERDRAFT' && mode !== 'POSTPAID_CREDIT' && mode !== 'HARVEST_ADVANCE' && mode !== 'VUP' && mode !== 'STUDENT' && mode !== 'MOTO_OWNERSHIP' && error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {mode !== 'OVERDRAFT' && mode !== 'POSTPAID_CREDIT' && mode !== 'HARVEST_ADVANCE' && mode !== 'VUP' && mode !== 'STUDENT' && mode !== 'MOTO_OWNERSHIP' && refinanceResult && (
        <div className="toss-card" style={{ padding: '16px', border: '1px solid var(--toss-blue)' }}>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>Refinanced into {refinanceResult.newLoanName}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{refinanceResult.oldRate}% → {refinanceResult.newRate}%</p>
        </div>
      )}
      {mode === 'OFFERS' && (
        <>
          {lenders && (
            <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', paddingBottom: '2px' }}>
              <button
                className="toss-btn toss-btn-secondary"
                style={{ fontSize: '12px', padding: '6px 12px', whiteSpace: 'nowrap', ...(lenderId === null ? { border: '1px solid var(--toss-blue)', color: 'var(--toss-blue)' } : {}) }}
                onClick={() => selectLender(null)}
              >
                All lenders
              </button>
              {lenders.map((lender) => (
                <button
                  key={lender.id}
                  className="toss-btn toss-btn-secondary"
                  style={{ fontSize: '12px', padding: '6px 12px', whiteSpace: 'nowrap', ...(lenderId === lender.id ? { border: '1px solid var(--toss-blue)', color: 'var(--toss-blue)' } : {}) }}
                  onClick={() => selectLender(lender.id)}
                >
                  {lender.name}
                </button>
              ))}
            </div>
          )}
          {offers === null ? <div className="toss-card skeleton" style={{ height: '160px' }} /> :
           offers.length === 0 ? <EmptyState message="No offers from this lender right now." /> :
           offers.map((offer) => (
            <LoanOfferCard key={offer.id} offer={offer} busy={busyId === offer.id} onApply={(amount) => handleApply(offer, amount)} />
          ))}
        </>
      )}
      {mode === 'MY_LOANS' && (
        myLoans === null ? <div className="toss-card skeleton" style={{ height: '160px' }} /> :
        myLoans.length === 0 ? <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You have no loans yet.</p> :
        myLoans.map((loan) => (
          <div key={loan.id} className="toss-card" style={{ padding: '16px' }}>
            <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{loan.principal.toLocaleString()} RWF loan</h4>
            <p style={{ fontSize: '13px' }}>Outstanding: {loan.outstanding.toLocaleString()} RWF</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Status: {loan.status} · {loan.interestRate}%</p>
            {loan.status === 'ACTIVE' && (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                <input
                  type="number" value={repayAmounts[loan.id] ?? ''}
                  onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                  placeholder="Repay amount (RWF)"
                  style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
                />
                <button className="toss-btn toss-btn-primary" disabled={busyId === loan.id} onClick={() => handleRepay(loan)}>
                  {busyId === loan.id ? 'Repaying…' : 'Repay'}
                </button>
                <button className="toss-btn toss-btn-secondary" disabled={busyId === loan.id} onClick={() => handleRefinance(loan)}>
                  {busyId === loan.id ? 'Checking…' : 'Refinance to a lower rate'}
                </button>
              </div>
            )}
          </div>
        ))
      )}
    </div>
  );
}

function LoanOfferCard({ offer, busy, onApply }: { offer: LoanOffer; busy: boolean; onApply: (amount: number) => void }) {
  const [amount, setAmount] = useState(String(offer.maxAmount));
  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{offer.name}</h4>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{offer.lenderName}</p>
      <p style={{ fontSize: '13px' }}>Up to {offer.maxAmount.toLocaleString()} RWF · {offer.interestRate}% · {offer.term}</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{offer.requirements}</p>
      <input
        type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', marginTop: '8px', width: '100%', boxSizing: 'border-box' }}
      />
      <button
        className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy}
        onClick={() => { const n = Number(amount); if (n > 0) onApply(n); }}
      >
        {busy ? 'Applying…' : 'Apply'}
      </button>
    </div>
  );
}

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// lib/loans.ts's OverdraftAccount doc comment for the full sourced account. Found
// 2026-07-29 via a full-backend-endpoint sweep: real, live-verified backend (open/
// draw/repay, real daily interest accrual, real security-alert push) with zero client
// anywhere on any of the 3 platforms.
function OverdraftView() {
  const [account, setAccount] = useState<OverdraftAccount | null | undefined>(undefined);
  const [requestedLimit, setRequestedLimit] = useState('100000');
  const [drawAmount, setDrawAmount] = useState('');
  const [repayAmount, setRepayAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOverdraft()
      .then(setAccount)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your overdraft account.'));
  };

  useEffect(load, []);

  const handleOpen = async () => {
    const limit = Number(requestedLimit);
    if (!limit || limit <= 0) { setError('Enter a valid credit limit.'); return; }
    setBusy(true);
    setError(null);
    try {
      const opened = await openOverdraft(limit);
      setAccount(opened);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open an overdraft account.');
    } finally {
      setBusy(false);
    }
  };

  const handleDraw = async () => {
    const amount = Number(drawAmount);
    if (!amount || amount <= 0) { setError('Enter a valid amount to draw.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await drawOverdraft(amount);
      setAccount((prev) => (prev ? { ...prev, drawnBalance: res.drawnBalance } : prev));
      setDrawAmount('');
      setNotice(`Drew ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF still available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not draw from your overdraft.');
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async () => {
    const amount = Number(repayAmount);
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await repayOverdraft(amount);
      setAccount((prev) => (prev ? { ...prev, drawnBalance: res.drawnBalance } : prev));
      setRepayAmount('');
      setNotice(`Repaid ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF now available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay your overdraft.');
    } finally {
      setBusy(false);
    }
  };

  if (account === undefined) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  if (account === null) {
    return (
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Open an overdraft line</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          A pre-approved credit limit you can draw from anytime -- pay interest only on what you actually use, up to 500,000 RWF.
        </p>
        {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
        <input
          type="number" value={requestedLimit} onChange={(e) => setRequestedLimit(e.target.value)} placeholder="Requested limit (RWF)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', marginTop: '8px', width: '100%', boxSizing: 'border-box' }}
        />
        <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleOpen}>
          {busy ? 'Opening…' : 'Open overdraft'}
        </button>
      </div>
    );
  }

  const availableCredit = account.creditLimit - account.drawnBalance;
  return (
    <div className="toss-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Overdraft line</h4>
      <p style={{ fontSize: '13px' }}>Drawn: {account.drawnBalance.toLocaleString()} RWF of {account.creditLimit.toLocaleString()} RWF</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Available to draw: {availableCredit.toLocaleString()} RWF · {account.interestRate}% annual, interest only on what's drawn</p>
      {notice && <p style={{ fontSize: '12px', color: 'var(--toss-blue)' }}>{notice}</p>}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <input
        type="number" value={drawAmount} onChange={(e) => setDrawAmount(e.target.value)} placeholder="Draw amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handleDraw}>{busy ? 'Drawing…' : 'Draw'}</button>
      <input
        type="number" value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} placeholder="Repay amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="toss-btn toss-btn-secondary" disabled={busy || account.drawnBalance <= 0} onClick={handleRepay}>{busy ? 'Repaying…' : 'Repay'}</button>
    </div>
  );
}

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see
// lib/loans.ts's PostpaidCreditLine doc comment for the full sourced account. Genuinely
// distinct from OverdraftView above: no requested-limit input (the limit is
// auto-computed from the caller's own real credit score), no interest shown for
// spending (only a real late fee if a cycle goes unpaid).
function PostpaidCreditView() {
  const [line, setLine] = useState<PostpaidCreditLine | null | undefined>(undefined);
  const [spendAmount, setSpendAmount] = useState('');
  const [repayAmount, setRepayAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyPostpaidCredit()
      .then(setLine)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your postpaid credit line.'));
  };

  useEffect(load, []);

  const handleApply = async () => {
    setBusy(true);
    setError(null);
    try {
      setLine(await applyForPostpaidCredit());
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open a postpaid credit line.');
    } finally {
      setBusy(false);
    }
  };

  const handleSpend = async () => {
    const amount = Number(spendAmount);
    if (!amount || amount <= 0) { setError('Enter a valid amount to spend.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await spendPostpaidCredit(amount);
      setLine((prev) => (prev ? { ...prev, currentBalance: res.currentBalance } : prev));
      setSpendAmount('');
      setNotice(`Added ${res.amount.toLocaleString()} RWF to your wallet -- ${res.availableCredit.toLocaleString()} RWF still available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not spend from your postpaid credit line.');
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async () => {
    const amount = Number(repayAmount);
    if (!amount || amount <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusy(true);
    setError(null);
    setNotice(null);
    try {
      const res = await repayPostpaidCredit(amount);
      setLine((prev) => (prev ? { ...prev, currentBalance: res.currentBalance, status: res.currentBalance <= 0 ? 'ACTIVE' : prev.status } : prev));
      setRepayAmount('');
      setNotice(`Repaid ${res.amount.toLocaleString()} RWF -- ${res.availableCredit.toLocaleString()} RWF now available.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay your postpaid credit line.');
    } finally {
      setBusy(false);
    }
  };

  if (line === undefined) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  if (line === null) {
    return (
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Get postpaid credit</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          A small credit line for real purchases, interest-free if you pay within 30 days -- your limit is set automatically from your credit score, up to 300,000 RWF.
        </p>
        {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
        <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleApply}>
          {busy ? 'Applying…' : 'Get postpaid credit'}
        </button>
      </div>
    );
  }

  const availableCredit = line.creditLimit - line.currentBalance;
  return (
    <div className="toss-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Postpaid credit</h4>
      <p style={{ fontSize: '13px' }}>Owed: {line.currentBalance.toLocaleString()} RWF of {line.creditLimit.toLocaleString()} RWF</p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Available: {availableCredit.toLocaleString()} RWF · interest-free if repaid within 30 days</p>
      {line.status === 'SUSPENDED' && (
        <p style={{ fontSize: '12px', color: '#E53935', fontWeight: 700 }}>Suspended -- repay your overdue balance to keep spending.</p>
      )}
      {line.cycleDueAt && line.status === 'ACTIVE' && (
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Due by {new Date(line.cycleDueAt).toLocaleDateString()}</p>
      )}
      {notice && <p style={{ fontSize: '12px', color: 'var(--toss-blue)' }}>{notice}</p>}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <input
        type="number" value={spendAmount} onChange={(e) => setSpendAmount(e.target.value)} placeholder="Spend amount (RWF)"
        disabled={line.status === 'SUSPENDED'}
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="toss-btn toss-btn-primary" disabled={busy || line.status === 'SUSPENDED'} onClick={handleSpend}>{busy ? 'Adding…' : 'Add to wallet'}</button>
      <input
        type="number" value={repayAmount} onChange={(e) => setRepayAmount(e.target.value)} placeholder="Repay amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
      />
      <button className="toss-btn toss-btn-secondary" disabled={busy || line.currentBalance <= 0} onClick={handleRepay}>{busy ? 'Repaying…' : 'Repay'}</button>
    </div>
  );
}

// Real Rwanda coffee-cooperative harvest-advance / input financing -- see
// lib/harvestAdvance.ts's own doc comment for the full sourced account. Sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Itunda is the
// sole real lender here (the same real underwriting-free wallet-to-wallet pattern the
// Offers/My-loans views above already use for itunda's own book), disbursed from
// itunda's own real loan_payable receivable -- never a shared pool, distinct from the
// Ikimina/SACCO shapes above.
function HarvestAdvanceView() {
  const [memberships, setMemberships] = useState<CooperativeMembership[] | null>(null);
  const [advances, setAdvances] = useState<HarvestAdvance[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [coopName, setCoopName] = useState('');
  const [coopId, setCoopId] = useState('');
  const [advanceAmount, setAdvanceAmount] = useState('');
  const [advancePurpose, setAdvancePurpose] = useState('INPUT_FINANCING');
  const [harvestDate, setHarvestDate] = useState('');

  const refresh = () => {
    setError(null);
    Promise.all([fetchMyCooperativeMemberships(), fetchMyHarvestAdvances()])
      .then(([m, a]) => { setMemberships(m); setAdvances(a); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your cooperative memberships.'));
  };

  useEffect(refresh, []);

  const handleRegisterAndJoin = async () => {
    if (!coopName.trim()) { setError('Enter a real cooperative name.'); return; }
    setBusy(true);
    setError(null);
    try {
      const coop = await registerCooperative(coopName.trim(), 'COFFEE');
      await joinCooperative(coop.id);
      setCoopName('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not register this cooperative.');
    } finally {
      setBusy(false);
    }
  };

  const handleJoinExisting = async () => {
    if (!coopId.trim()) { setError('Enter a real cooperative id.'); return; }
    setBusy(true);
    setError(null);
    try {
      await joinCooperative(coopId.trim());
      setCoopId('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not join this cooperative.');
    } finally {
      setBusy(false);
    }
  };

  const handleRequestAdvance = async (membershipId: string) => {
    const amount = Number(advanceAmount);
    if (!amount || amount <= 0 || !harvestDate) { setError('Enter a real advance amount and expected harvest date.'); return; }
    setBusy(true);
    setError(null);
    try {
      await requestHarvestAdvance(membershipId, amount, advancePurpose, new Date(harvestDate).toISOString());
      setAdvanceAmount('');
      setHarvestDate('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not request this harvest advance.');
    } finally {
      setBusy(false);
    }
  };

  const handleDisburse = async (advanceId: string) => {
    setBusy(true);
    setError(null);
    try {
      await disburseHarvestAdvance(advanceId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not disburse this advance.');
    } finally {
      setBusy(false);
    }
  };

  const handleRepay = async (advanceId: string, principalAmount: number) => {
    setBusy(true);
    setError(null);
    try {
      await repayHarvestAdvance(advanceId, principalAmount);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay this advance.');
    } finally {
      setBusy(false);
    }
  };

  if (memberships === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Cooperative harvest advance</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Input financing or post-harvest advances for coffee cooperative members. Cooperative registration is self-declared -- not verified against a real RCA registry.
        </p>
        <input
          type="text" value={coopName} onChange={(e) => setCoopName(e.target.value)} placeholder="Register a new cooperative (name)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
        />
        <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleRegisterAndJoin}>
          {busy ? 'Working…' : 'Register & join'}
        </button>
        <input
          type="text" value={coopId} onChange={(e) => setCoopId(e.target.value)} placeholder="Or join an existing cooperative (id)"
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
        />
        <button className="toss-btn toss-btn-secondary" style={{ marginTop: '8px' }} disabled={busy} onClick={handleJoinExisting}>
          {busy ? 'Working…' : 'Join'}
        </button>
      </div>

      {memberships.length === 0 ? (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>You're not a member of any cooperative yet.</p>
      ) : (
        memberships.map((m) => (
          <div key={m.id} className="toss-card" style={{ padding: '16px' }}>
            <p style={{ fontSize: '13px', fontWeight: 700 }}>Cooperative membership {m.cooperativeId}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Member since {new Date(m.memberSince).toLocaleDateString()}</p>
            <input
              type="number" value={advanceAmount} onChange={(e) => setAdvanceAmount(e.target.value)} placeholder="Advance amount (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <select
              value={advancePurpose} onChange={(e) => setAdvancePurpose(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="INPUT_FINANCING">Input financing (seeds/fertilizer)</option>
              <option value="POST_HARVEST">Post-harvest advance</option>
            </select>
            <input
              type="date" value={harvestDate} onChange={(e) => setHarvestDate(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleRequestAdvance(m.id)}>
              {busy ? 'Requesting…' : 'Request advance'}
            </button>
          </div>
        ))
      )}

      {advances && advances.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My advances</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {advances.map((a) => (
              <div key={a.id} className="toss-card" style={{ padding: '14px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>{a.principalAmount.toLocaleString()} RWF · {a.purpose}</p>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{a.status}</span>
                </div>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Repay by {new Date(a.repaymentDueDate).toLocaleDateString()}</p>
                {a.status === 'REQUESTED' && (
                  <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleDisburse(a.id)}>
                    {busy ? 'Disbursing…' : 'Disburse'}
                  </button>
                )}
                {a.status === 'DISBURSED' && (
                  // Real bug caught during this feature's own build-time review: partial
                  // repayment isn't tracked anywhere on this entity, so accepting a
                  // free-form amount let a token repayment silently close out the full
                  // debt. Repayment is full-settlement-only -- no amount to type, just
                  // the real outstanding principal shown up front.
                  <button className="toss-btn toss-btn-secondary" style={{ marginTop: '8px' }} disabled={busy} onClick={() => handleRepay(a.id, a.principalAmount)}>
                    {busy ? 'Repaying…' : `Repay in full (${a.principalAmount.toLocaleString()} RWF)`}
                  </button>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services micro-loan --
// see lib/vupLoan.ts's own doc comment for the full sourced account. The first
// MEANS-TESTED lending product in itunda, gated on a self-declared (not
// government-verified) Ubudehe category rather than credit score or collateral.
// Disbursement here is a real user-triggered step standing in for the real SACCO
// officer approval step the actual VUP/FS program uses -- named honestly below.
function VupLoanView() {
  const [loans, setLoans] = useState<VupLoan[] | null>(null);
  const [eligibility, setEligibility] = useState<VupLoanEligibility | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [category, setCategory] = useState<number>(1);
  const [purpose, setPurpose] = useState<VupLoanPurpose>('FARMING');
  const [amount, setAmount] = useState('');
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    Promise.all([fetchMyVupLoans(), fetchVupLoanEligibility()])
      .then(([l, e]) => { setLoans(l); setEligibility(e); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your VUP loans.'));
  };

  useEffect(refresh, []);

  const handleApply = async () => {
    const value = Number(amount);
    if (!value || value <= 0) { setError('Enter a valid loan amount.'); return; }
    setBusyId('apply');
    setError(null);
    try {
      await applyForVupLoan(category, purpose, value);
      setAmount('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not apply for this VUP loan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleDisburse = async (loanId: string) => {
    setBusyId(loanId);
    setError(null);
    try {
      await disburseVupLoan(loanId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not disburse this loan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (loanId: string) => {
    const value = Number(repayAmounts[loanId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(loanId);
    setError(null);
    try {
      await repayVupLoan(loanId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loanId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay this loan.');
    } finally {
      setBusyId(null);
    }
  };

  if (loans === null || eligibility === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>VUP Financial Services</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Rwanda's Vision 2020 Umurenge Programme subsidized microloan for farming, livestock, or small business -- {eligibility.interestRate * 100}% interest, for
          {' '}Ubudehe categories {eligibility.minUbudeheCategory}-{eligibility.maxUbudeheCategory} only. Ubudehe category is self-declared -- not verified against a real government registry.
        </p>
        {!eligibility.canApply ? (
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '8px' }}>You already have an active VUP loan -- repay it before applying for another.</p>
        ) : (
          <>
            <select
              value={category} onChange={(e) => setCategory(Number(e.target.value))}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value={1}>Ubudehe category 1</option>
              <option value={2}>Ubudehe category 2</option>
              <option value={3}>Ubudehe category 3</option>
            </select>
            <select
              value={purpose} onChange={(e) => setPurpose(e.target.value as VupLoanPurpose)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="FARMING">Farming</option>
              <option value="LIVESTOCK">Livestock</option>
              <option value="BUSINESS">Small business</option>
            </select>
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Loan amount (RWF, up to 500,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === 'apply'} onClick={handleApply}>
              {busyId === 'apply' ? 'Applying…' : 'Apply'}
            </button>
          </>
        )}
      </div>

      {loans.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My VUP loans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {loans.map((loan) => (
              <div key={loan.id} className="toss-card" style={{ padding: '14px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>{loan.principalAmount.toLocaleString()} RWF · {loan.purpose}</p>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: loan.status === 'OVERDUE' ? '#E53935' : 'var(--toss-blue)' }}>{loan.status}</span>
                </div>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                  Outstanding: {loan.outstandingPrincipal.toLocaleString()} RWF
                  {loan.dueDate && ` · Due ${new Date(loan.dueDate).toLocaleDateString()}`}
                </p>
                {loan.status === 'REQUESTED' && (
                  <>
                    <p style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>Demo: instantly approved -- stands in for the real SACCO officer approval step.</p>
                    <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDisburse(loan.id)}>
                      {busyId === loan.id ? 'Disbursing…' : 'Disburse'}
                    </button>
                  </>
                )}
                {(loan.status === 'DISBURSED' || loan.status === 'OVERDUE') && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                    <input
                      type="number" value={repayAmounts[loan.id] ?? ''} onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                      placeholder="Repayment amount (RWF)"
                      style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
                    />
                    <button className="toss-btn toss-btn-secondary" disabled={busyId === loan.id} onClick={() => handleRepay(loan.id)}>
                      {busyId === loan.id ? 'Repaying…' : 'Repay'}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- see
// the backend's StudentLoanService.kt doc comment for the full sourced account.
// Distinct from VupLoanView above: eligibility on self-declared household income
// (not Ubudehe), a mandatory grace period between disbursement and first-repayment
// obligation, and an income-percentage-SUGGESTED (not fixed-installment) repayment.
function StudentLoanView() {
  const [loans, setLoans] = useState<StudentLoan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [suggested, setSuggested] = useState<Record<string, StudentLoanSuggestedPayment>>({});

  const [level, setLevel] = useState<StudentLoanLevel>('UNDERGRADUATE');
  const [income, setIncome] = useState('');
  const [amount, setAmount] = useState('');
  const [graduationDate, setGraduationDate] = useState('');
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    fetchMyStudentLoans()
      .then((l) => {
        setLoans(l);
        l.filter((loan) => loan.status === 'REPAYING' || loan.status === 'OVERDUE').forEach((loan) => {
          fetchSuggestedPayment(loan.id).then((s) => setSuggested((prev) => ({ ...prev, [loan.id]: s }))).catch(() => undefined);
        });
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your student loans.'));
  };

  useEffect(refresh, []);

  const hasActiveLoan = (loans ?? []).some((loan) => loan.status !== 'REPAID');

  const handleApply = async () => {
    const incomeValue = Number(income);
    const amountValue = Number(amount);
    if (!incomeValue || incomeValue <= 0) { setError('Enter a valid declared annual household income.'); return; }
    if (!amountValue || amountValue <= 0) { setError('Enter a valid loan amount.'); return; }
    if (!graduationDate) { setError('Enter your expected graduation date.'); return; }
    setBusyId('apply');
    setError(null);
    try {
      await applyForStudentLoan(level, incomeValue, amountValue, graduationDate);
      setIncome(''); setAmount(''); setGraduationDate('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not apply for this student loan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleDisburse = async (loanId: string) => {
    setBusyId(loanId);
    setError(null);
    try {
      await disburseStudentLoan(loanId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not disburse this loan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleDeclareGraduated = async (loanId: string) => {
    setBusyId(loanId);
    setError(null);
    try {
      await declareStudentLoanGraduated(loanId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not mark this loan as graduated.');
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (loanId: string) => {
    const value = Number(repayAmounts[loanId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(loanId);
    setError(null);
    try {
      await repayStudentLoan(loanId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[loanId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay this loan.');
    } finally {
      setBusyId(null);
    }
  };

  if (loans === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>BRD Student Loan</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Rwanda's national higher-education student loan, run by the Development Bank of Rwanda (BRD) since 2016 -- 11% undergraduate / 12% postgraduate,
          {' '}with a grace period after graduation before repayment starts. Declared household income is self-declared -- not verified against BRD's real
          {' '}Financial Means Testing process. Repayment here is user-initiated from your wallet -- itunda cannot deduct from your paycheck like the real
          {' '}8%-of-income scheme BRD uses.
        </p>
        {hasActiveLoan ? (
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '8px' }}>You already have an active student loan -- repay it before applying for another.</p>
        ) : (
          <>
            <select
              value={level} onChange={(e) => setLevel(e.target.value as StudentLoanLevel)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            >
              <option value="UNDERGRADUATE">Undergraduate (11%)</option>
              <option value="POSTGRADUATE">Postgraduate (12%)</option>
            </select>
            <input
              type="number" value={income} onChange={(e) => setIncome(e.target.value)} placeholder="Declared annual household income (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Loan amount (RWF, up to 2,000,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="date" value={graduationDate} onChange={(e) => setGraduationDate(e.target.value)}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === 'apply'} onClick={handleApply}>
              {busyId === 'apply' ? 'Applying…' : 'Apply'}
            </button>
          </>
        )}
      </div>

      {loans.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My student loans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {loans.map((loan) => (
              <div key={loan.id} className="toss-card" style={{ padding: '14px' }}>
                <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>{loan.principalAmount.toLocaleString()} RWF · {loan.level}</p>
                  <span style={{ fontSize: '11px', fontWeight: 700, color: loan.status === 'OVERDUE' ? '#E53935' : 'var(--toss-blue)' }}>{loan.status}</span>
                </div>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                  Outstanding: {loan.outstandingBalance.toLocaleString()} RWF
                  {loan.graceEndsAt && ` · Grace ends ${new Date(loan.graceEndsAt).toLocaleDateString()}`}
                </p>
                {loan.status === 'REQUESTED' && (
                  <>
                    <p style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>Demo: instantly approved -- stands in for the real BRD/MINEDUC approval step.</p>
                    <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDisburse(loan.id)}>
                      {busyId === loan.id ? 'Disbursing…' : 'Disburse'}
                    </button>
                  </>
                )}
                {loan.status === 'DISBURSED' && (
                  <button className="toss-btn toss-btn-secondary" style={{ marginTop: '8px' }} disabled={busyId === loan.id} onClick={() => handleDeclareGraduated(loan.id)}>
                    {busyId === loan.id ? 'Updating…' : 'Declare graduated'}
                  </button>
                )}
                {loan.status === 'IN_GRACE_PERIOD' && (
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>In your grace period -- repayment isn't due yet.</p>
                )}
                {(loan.status === 'REPAYING' || loan.status === 'OVERDUE') && (
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                    {suggested[loan.id] && (
                      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                        Suggested: {Math.round(suggested[loan.id].suggestedMonthlyPayment).toLocaleString()} RWF/mo · {suggested[loan.id].note}
                      </p>
                    )}
                    <input
                      type="number" value={repayAmounts[loan.id] ?? ''} onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [loan.id]: e.target.value }))}
                      placeholder="Repayment amount (RWF)"
                      style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
                    />
                    <button className="toss-btn toss-btn-secondary" disabled={busyId === loan.id} onClick={() => handleRepay(loan.id)}>
                      {busyId === loan.id ? 'Repaying…' : 'Repay'}
                    </button>
                  </div>
                )}
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

// Real Rwanda moto-taxi ownership savings-to-loan plan -- see lib/motoOwnership.ts's
// own doc comment for the full sourced account. The first two-PHASE product in this
// codebase: save toward a real 30% down payment (itunda's own policy pick), then
// convert the plan into an unsecured loan for the remaining balance. Distinct from
// VupLoanView/StudentLoanView above: this is asset-purchase financing tied to a
// specific real Rwanda sector (moto-taxi ownership), not a cash microloan.
function MotoOwnershipView() {
  const [plans, setPlans] = useState<MotoOwnershipPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  const [bikePrice, setBikePrice] = useState('');
  const [dailyContribution, setDailyContribution] = useState('');
  const [contributeAmounts, setContributeAmounts] = useState<Record<string, string>>({});
  const [repayAmounts, setRepayAmounts] = useState<Record<string, string>>({});

  const refresh = () => {
    setError(null);
    fetchMyMotoOwnershipPlans()
      .then(setPlans)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your moto-taxi ownership plans.'));
  };

  useEffect(refresh, []);

  const hasActivePlan = (plans ?? []).some((plan) => plan.status === 'SAVING' || plan.status === 'LOAN_ACTIVE');
  const previewDownPayment = Number(bikePrice) > 0 ? Number(bikePrice) * 0.3 : 0;

  const handleCreate = async () => {
    const priceValue = Number(bikePrice);
    const contributionValue = Number(dailyContribution);
    if (!priceValue || priceValue <= 0) { setError('Enter a valid bike price.'); return; }
    if (!contributionValue || contributionValue <= 0) { setError('Enter a valid daily contribution.'); return; }
    setBusyId('create');
    setError(null);
    try {
      await createMotoOwnershipPlan(priceValue, contributionValue);
      setBikePrice(''); setDailyContribution('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this moto-taxi ownership plan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleContribute = async (planId: string) => {
    const value = Number(contributeAmounts[planId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid contribution amount.'); return; }
    setBusyId(planId);
    setError(null);
    try {
      await contributeToMotoOwnershipPlan(planId, value);
      setContributeAmounts((prev) => { const next = { ...prev }; delete next[planId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not contribute to this plan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleCancel = async (planId: string) => {
    setBusyId(planId);
    setError(null);
    try {
      await cancelMotoOwnershipPlan(planId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this plan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleConvert = async (planId: string) => {
    setBusyId(planId);
    setError(null);
    try {
      await convertMotoOwnershipPlanToLoan(planId);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not convert this plan to a loan.');
    } finally {
      setBusyId(null);
    }
  };

  const handleRepay = async (planId: string) => {
    const value = Number(repayAmounts[planId] ?? '');
    if (!value || value <= 0) { setError('Enter a valid repayment amount.'); return; }
    setBusyId(planId);
    setError(null);
    try {
      await repayMotoOwnershipPlan(planId, value);
      setRepayAmounts((prev) => { const next = { ...prev }; delete next[planId]; return next; });
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not repay this loan.');
    } finally {
      setBusyId(null);
    }
  };

  if (plans === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      <div className="toss-card" style={{ padding: '16px' }}>
        <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Moto-Taxi Ownership Plan</h4>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Save toward a 30% down payment on your own moto-taxi bike (itunda's own down-payment policy), then convert the rest into an unsecured loan.
          {' '}A real entry-level bike costs around 600,000 RWF -- this fills the gap left since Rwanda's taxi-moto cooperatives, which used to help
          {' '}drivers become owner-operators, were dissolved.
        </p>
        {hasActivePlan ? (
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '8px' }}>You already have an active moto-taxi ownership plan -- complete or cancel it before starting another.</p>
        ) : (
          <>
            <input
              type="number" value={bikePrice} onChange={(e) => setBikePrice(e.target.value)} placeholder="Bike price (RWF, 300,000-2,500,000)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            <input
              type="number" value={dailyContribution} onChange={(e) => setDailyContribution(e.target.value)} placeholder="Daily contribution (RWF)"
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginTop: '8px' }}
            />
            {previewDownPayment > 0 && (
              <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                Down payment target (30%): {previewDownPayment.toLocaleString()} RWF
              </p>
            )}
            <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyId === 'create'} onClick={handleCreate}>
              {busyId === 'create' ? 'Creating…' : 'Start plan'}
            </button>
          </>
        )}
      </div>

      {plans.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My moto-taxi ownership plans</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {plans.map((plan) => {
              const progressPct = plan.downPaymentTarget > 0 ? Math.min(100, Math.round((plan.savedAmount / plan.downPaymentTarget) * 100)) : 0;
              return (
                <div key={plan.id} className="toss-card" style={{ padding: '14px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{plan.bikePrice.toLocaleString()} RWF bike</p>
                    <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{plan.status}</span>
                  </div>
                  {plan.status === 'SAVING' && (
                    <>
                      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                        Saved {plan.savedAmount.toLocaleString()} / {plan.downPaymentTarget.toLocaleString()} RWF down payment
                      </p>
                      <div style={{ height: '6px', borderRadius: '3px', background: 'var(--toss-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                        <div style={{ height: '100%', width: `${progressPct}%`, background: 'var(--toss-blue)' }} />
                      </div>
                      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                        <input
                          type="number" value={contributeAmounts[plan.id] ?? ''} onChange={(e) => setContributeAmounts((prev) => ({ ...prev, [plan.id]: e.target.value }))}
                          placeholder="Contribution amount (RWF)"
                          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
                        />
                        <div style={{ display: 'flex', gap: '6px' }}>
                          <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busyId === plan.id} onClick={() => handleContribute(plan.id)}>
                            {busyId === plan.id ? 'Saving…' : 'Contribute'}
                          </button>
                          <button className="toss-btn toss-btn-secondary" style={{ flex: 1, color: '#E53935' }} disabled={busyId === plan.id} onClick={() => handleCancel(plan.id)}>
                            Cancel
                          </button>
                        </div>
                        {plan.savedAmount >= plan.downPaymentTarget && (
                          <>
                            <p style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>
                              This releases your full {plan.bikePrice.toLocaleString()} RWF bike price to your wallet (your saved down payment plus a new unsecured loan for the rest) -- itunda cannot repossess the bike if you stop repaying.
                            </p>
                            <button className="toss-btn toss-btn-primary" disabled={busyId === plan.id} onClick={() => handleConvert(plan.id)}>
                              {busyId === plan.id ? 'Converting…' : 'Convert to loan'}
                            </button>
                          </>
                        )}
                      </div>
                    </>
                  )}
                  {plan.status === 'LOAN_ACTIVE' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Loan outstanding: {plan.loanOutstanding.toLocaleString()} RWF</p>
                      <input
                        type="number" value={repayAmounts[plan.id] ?? ''} onChange={(e) => setRepayAmounts((prev) => ({ ...prev, [plan.id]: e.target.value }))}
                        placeholder="Repayment amount (RWF)"
                        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box' }}
                      />
                      <button className="toss-btn toss-btn-secondary" disabled={busyId === plan.id} onClick={() => handleRepay(plan.id)}>
                        {busyId === plan.id ? 'Repaying…' : 'Repay'}
                      </button>
                    </div>
                  )}
                  {plan.status === 'COMPLETED' && (
                    <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>Paid off -- this bike is now fully yours.</p>
                  )}
                </div>
              );
            })}
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
  const [result, setResult] = useState<CreditScoreResult | null>(null);
  const [suggestions, setSuggestions] = useState<CreditScoreSuggestion[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchCreditScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your credit score.'));
    // Real Toss 신용플러스-style suggestions (2026-07-26) -- see
    // CreditScoreService.getImprovementSuggestions's own doc comment. Loaded alongside
    // the score itself, not gated behind it -- a real failure here shouldn't block the
    // score from rendering.
    fetchCreditScoreSuggestions()
      .then(setSuggestions)
      .catch(() => setSuggestions([]));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Your score</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{result.score} / 850</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Based on your own account activity, not a bureau report.</p>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
      {suggestions !== null && suggestions.length > 0 && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>What would raise your score</h3>
          {suggestions.map((s) => (
            <div key={s.action} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
              <div>
                <p>{s.action}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{s.description}</p>
              </div>
              <span style={{ color: 'var(--toss-blue)', fontWeight: 700 }}>+{s.pointsGain}</span>
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
  const [result, setResult] = useState<TrustScoreResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchTrustScore()
      .then(setResult)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your trust score.'));
  }, []);

  if (!result) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Your trust score</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{result.score} / 1000</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>How your neighbors see you on Marketplace, Jobs, and Property.</p>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>What makes up your score</h3>
        {result.factors.map((f) => (
          <div key={f.name} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <div>
              <p>{f.name}</p>
              <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{f.description}</p>
            </div>
            <span>+{f.points}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Toss-style rewards/mission-task center -- see lib/rewards.ts's own doc
// comment: real on Android/iOS since day one via the Saronite mini-app bridge, but
// bank-mfe (the actual banking app) never had a client for it. Same task-list +
// referral-code + step-counter shape those native bridges already expose.
function RewardsView() {
  const [tasks, setTasks] = useState<RewardTasksResult | null>(null);
  const [referral, setReferral] = useState<ReferralInfo | null>(null);
  const [todaySteps, setTodaySteps] = useState<number | null>(null);
  const [stepsInput, setStepsInput] = useState('');
  const [claimingId, setClaimingId] = useState<string | null>(null);
  const [reportingSteps, setReportingSteps] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchRewardTasks().then(setTasks).catch(() => setTasks(null));
    fetchReferralInfo().then(setReferral).catch(() => setReferral(null));
    fetchTodaySteps().then(setTodaySteps).catch(() => setTodaySteps(null));
  };
  useEffect(load, []);

  const handleClaim = async (taskId: string) => {
    setClaimingId(taskId);
    setError(null);
    setMessage(null);
    try {
      const result = await claimRewardTask(taskId);
      setMessage(`${result.message} (+${result.rewardAmount} RWF)`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not claim this reward.');
    } finally {
      setClaimingId(null);
    }
  };

  const handleReportSteps = async () => {
    const steps = Number(stepsInput);
    if (!Number.isFinite(steps) || steps <= 0) { setError('Enter a real step count.'); return; }
    setReportingSteps(true);
    setError(null);
    setMessage(null);
    try {
      const result = await reportSteps(steps);
      setTodaySteps(result.steps);
      setStepsInput('');
      if (result.newlyEarnedAmount > 0) {
        setMessage(`Walking bonus unlocked: +${result.newlyEarnedAmount} RWF`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not report steps.');
    } finally {
      setReportingSteps(false);
    }
  };

  if (!tasks) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Total earned</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{tasks.rewardsTotal} RWF</h2>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Missions</h3>
        {tasks.tasks.map((t) => (
          <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', padding: '8px 0' }}>
            <div>
              <p>{t.title}</p>
              <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{t.subtitle}</p>
            </div>
            {t.claimed ? (
              <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Claimed</span>
            ) : (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={!t.eligible || claimingId === t.id}
                onClick={() => handleClaim(t.id)}
              >
                {claimingId === t.id ? '...' : `+${t.rewardAmount} RWF`}
              </button>
            )}
          </div>
        ))}
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>🚶 Walking rewards</h3>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Today: {todaySteps ?? 0} steps</p>
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            type="number"
            placeholder="Enter steps"
            value={stepsInput}
            onChange={(e) => setStepsInput(e.target.value)}
            style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
          />
          <button className="toss-btn toss-btn-secondary" disabled={reportingSteps} onClick={handleReportSteps}>
            {reportingSteps ? '...' : 'Report'}
          </button>
        </div>
      </div>
      {referral && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Invite friends</h3>
          <p style={{ fontSize: '18px', fontWeight: 700 }}>{referral.referralCode}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            {referral.completedReferralCount} completed of {referral.referredCount} referred
          </p>
        </div>
      )}
    </div>
  );
}

// Real insurance browse/enroll/my-policies/claims client -- see lib/insurance.ts's
// own doc comment. Previously bank-mfe only rendered a read-only "Insurance: N active
// plan(s)" summary line inside OverviewView; this is the actual self-service flow.
function InsuranceView() {
  const [plans, setPlans] = useState<InsurancePlan[] | null>(null);
  const [policies, setPolicies] = useState<InsurancePolicy[]>([]);
  const [claims, setClaims] = useState<InsuranceClaim[]>([]);
  const [funds, setFunds] = useState<InsurancePremiumFund[]>([]);
  const [enrollingId, setEnrollingId] = useState<string | null>(null);
  const [claimPolicyId, setClaimPolicyId] = useState<string | null>(null);
  const [claimDescription, setClaimDescription] = useState('');
  const [claimAmount, setClaimAmount] = useState('');
  const [submittingClaim, setSubmittingClaim] = useState(false);
  const [creatingFundPolicyId, setCreatingFundPolicyId] = useState<string | null>(null);
  const [newFundDaily, setNewFundDaily] = useState('0');
  const [fundBusyId, setFundBusyId] = useState<string | null>(null);
  const [contributeAmount, setContributeAmount] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchInsurancePlans().then(setPlans).catch(() => setPlans([]));
    fetchMyPolicies().then(setPolicies).catch(() => setPolicies([]));
    fetchMyClaims().then(setClaims).catch(() => setClaims([]));
    fetchMyPremiumFunds().then(setFunds).catch(() => setFunds([]));
  };
  useEffect(load, []);

  // Real Ejo Heza ya Moto-style premium savings fund -- see lib/insurance.ts's own doc
  // comment. Lets a user save toward a specific policy's next premium ahead of time.
  const handleCreateFund = async (policyId: string) => {
    const daily = Number(newFundDaily || '0');
    if (!Number.isFinite(daily) || daily < 0) {
      setError('Daily contribution must be zero or a positive number.');
      return;
    }
    setFundBusyId(policyId);
    setError(null);
    setMessage(null);
    try {
      await createPremiumFund(policyId, daily);
      setMessage('Started saving toward your next premium.');
      setCreatingFundPolicyId(null);
      setNewFundDaily('0');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not start a premium fund.');
    } finally {
      setFundBusyId(null);
    }
  };

  const handleContribute = async (fundId: string) => {
    const amount = Number(contributeAmount[fundId] || '0');
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Contribution amount must be greater than zero.');
      return;
    }
    setFundBusyId(fundId);
    setError(null);
    setMessage(null);
    try {
      await contributeToFund(fundId, amount);
      setMessage('Contribution added toward your next premium.');
      setContributeAmount((prev) => ({ ...prev, [fundId]: '' }));
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this contribution.');
    } finally {
      setFundBusyId(null);
    }
  };

  const handleCancelFund = async (fundId: string) => {
    setFundBusyId(fundId);
    setError(null);
    setMessage(null);
    try {
      await cancelFund(fundId);
      setMessage('Premium fund cancelled and refunded to your wallet.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this premium fund.');
    } finally {
      setFundBusyId(null);
    }
  };

  const handleEnroll = async (planId: string) => {
    setEnrollingId(planId);
    setError(null);
    setMessage(null);
    try {
      const policy = await enrollInPlan(planId);
      setMessage(`Enrolled in ${policy.planName}`);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not enroll in this plan.');
    } finally {
      setEnrollingId(null);
    }
  };

  const handleSubmitClaim = async () => {
    const amount = Number(claimAmount);
    if (!claimPolicyId || !claimDescription.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Fill in a real description and a claim amount greater than zero.');
      return;
    }
    setSubmittingClaim(true);
    setError(null);
    setMessage(null);
    try {
      await submitClaim(claimPolicyId, claimDescription.trim(), amount);
      setMessage('Claim submitted for review.');
      setClaimPolicyId(null);
      setClaimDescription('');
      setClaimAmount('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this claim.');
    } finally {
      setSubmittingClaim(false);
    }
  };

  if (!plans) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  const enrolledPlanIds = new Set(policies.map((p) => p.planId));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}

      {policies.length > 0 && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My policies</h3>
          {policies.map((p) => (
            <div key={p.id} style={{ padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div>
                  <p style={{ fontSize: '13px', fontWeight: 600 }}>{p.planName}</p>
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{p.policyNumber} · {p.status} · {p.monthlyPremium.toLocaleString()} RWF/mo</p>
                </div>
                <button
                  className="toss-btn toss-btn-secondary"
                  disabled={p.status !== 'active'}
                  onClick={() => setClaimPolicyId(p.id)}
                >
                  File a claim
                </button>
              </div>
              {claimPolicyId === p.id && (
                <div style={{ marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <input
                    placeholder="What happened?"
                    value={claimDescription}
                    onChange={(e) => setClaimDescription(e.target.value)}
                    style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
                  />
                  <input
                    type="number"
                    placeholder="Claim amount (RWF)"
                    value={claimAmount}
                    onChange={(e) => setClaimAmount(e.target.value)}
                    style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
                  />
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="toss-btn toss-btn-secondary" disabled={submittingClaim} onClick={handleSubmitClaim}>
                      {submittingClaim ? '...' : 'Submit claim'}
                    </button>
                    <button className="toss-btn toss-btn-secondary" onClick={() => setClaimPolicyId(null)}>Cancel</button>
                  </div>
                </div>
              )}
              {p.status === 'active' && (() => {
                const fund = funds.find((f) => f.policyId === p.id && f.status === 'active');
                if (!fund) {
                  return (
                    <div style={{ marginTop: '8px' }}>
                      {creatingFundPolicyId === p.id ? (
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                          <input
                            type="number"
                            placeholder="Daily contribution (0 = manual only)"
                            value={newFundDaily}
                            onChange={(e) => setNewFundDaily(e.target.value)}
                            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
                          />
                          <div style={{ display: 'flex', gap: '8px' }}>
                            <button className="toss-btn toss-btn-secondary" disabled={fundBusyId === p.id} onClick={() => handleCreateFund(p.id)}>
                              {fundBusyId === p.id ? '...' : 'Start saving'}
                            </button>
                            <button className="toss-btn toss-btn-secondary" onClick={() => setCreatingFundPolicyId(null)}>Cancel</button>
                          </div>
                        </div>
                      ) : (
                        <button className="toss-btn toss-btn-secondary" onClick={() => setCreatingFundPolicyId(p.id)}>
                          Save for next premium
                        </button>
                      )}
                    </div>
                  );
                }
                const pct = fund.targetAmount > 0 ? Math.min(100, Math.round((fund.currentAmount / fund.targetAmount) * 100)) : 0;
                return (
                  <div style={{ marginTop: '8px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                      Saved toward next premium: {fund.currentAmount.toLocaleString()} / {fund.targetAmount.toLocaleString()} RWF
                    </p>
                    <div style={{ height: '6px', borderRadius: '3px', background: 'var(--toss-grey-100)', overflow: 'hidden' }}>
                      <div style={{ height: '100%', width: `${pct}%`, background: 'var(--toss-blue)' }} />
                    </div>
                    <div style={{ display: 'flex', gap: '8px' }}>
                      <input
                        type="number"
                        placeholder="Add amount (RWF)"
                        value={contributeAmount[fund.id] ?? ''}
                        onChange={(e) => setContributeAmount((prev) => ({ ...prev, [fund.id]: e.target.value }))}
                        style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
                      />
                      <button className="toss-btn toss-btn-secondary" disabled={fundBusyId === fund.id} onClick={() => handleContribute(fund.id)}>
                        {fundBusyId === fund.id ? '...' : 'Add'}
                      </button>
                      <button className="toss-btn toss-btn-secondary" disabled={fundBusyId === fund.id} onClick={() => handleCancelFund(fund.id)}>
                        Cancel fund
                      </button>
                    </div>
                  </div>
                );
              })()}
            </div>
          ))}
        </div>
      )}

      {claims.length > 0 && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My claims</h3>
          {claims.map((c) => (
            <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
              <div>
                <p>{c.description}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{c.status}</p>
              </div>
              <span>{c.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}

      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Browse plans</h3>
        {plans.map((plan) => (
          <div key={plan.id} style={{ padding: '10px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 600 }}>{plan.name}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{plan.provider} · {plan.monthlyPremium.toLocaleString()} RWF/mo · cover {plan.coverageAmount.toLocaleString()} RWF</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{plan.description}</p>
              </div>
              <button
                className="toss-btn toss-btn-secondary"
                disabled={enrolledPlanIds.has(plan.id) || enrollingId === plan.id}
                onClick={() => handleEnroll(plan.id)}
              >
                {enrolledPlanIds.has(plan.id) ? 'Enrolled' : enrollingId === plan.id ? '...' : 'Enroll'}
              </button>
            </div>
          </div>
        ))}
      </div>

      <CropWeatherIndexSection />
    </div>
  );
}

// Real Rwanda National Agricultural Insurance Scheme (NAIS)-style parametric/weather-index
// crop insurance -- see lib/weatherIndexInsurance.ts's own doc comment for the full sourced
// account. Genuinely, structurally distinct from the claims-based plans above: no individual
// claim is ever filed here. Rendered as a section inside InsuranceView rather than a
// separate top-level tab -- it's still "insurance" from the customer's point of view.
function CropWeatherIndexSection() {
  const [catalog, setCatalog] = useState<CropIndexCatalogEntry[] | null>(null);
  const [policies, setPolicies] = useState<CropIndexPolicy[]>([]);
  const [cropType, setCropType] = useState<WeatherIndexCropType>('MAIZE');
  const [district, setDistrict] = useState('');
  const [season, setSeason] = useState('2026B');
  const [insuredAmount, setInsuredAmount] = useState('100000');
  const [enrolling, setEnrolling] = useState(false);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchCropIndexCatalog().then(setCatalog).catch(() => setCatalog([]));
    fetchMyCropIndexPolicies().then(setPolicies).catch(() => setPolicies([]));
  };
  useEffect(load, []);

  const selectedRate = catalog?.find((c) => c.cropType === cropType)?.premiumRatePercent ?? 0;
  const amountNum = Number(insuredAmount || '0');
  const computedPremium = Number.isFinite(amountNum) ? Math.round(amountNum * selectedRate) / 100 : 0;

  const handleEnroll = async () => {
    if (!district.trim() || !season.trim() || !Number.isFinite(amountNum) || amountNum <= 0) {
      setError('Enter a district, a season, and an insured amount greater than zero.');
      return;
    }
    setEnrolling(true);
    setError(null);
    setMessage(null);
    try {
      const policy = await enrollInCropIndexPolicy(cropType, district.trim(), season.trim(), amountNum);
      setMessage(`Enrolled — premium ${policy.premiumAmount.toLocaleString()} RWF charged to your wallet.`);
      setDistrict('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not enroll in crop weather-index cover.');
    } finally {
      setEnrolling(false);
    }
  };

  const handleCancel = async (policyId: string) => {
    setBusyId(policyId);
    setError(null);
    setMessage(null);
    try {
      await cancelCropIndexPolicy(policyId);
      setMessage('Policy cancelled and premium refunded to your wallet.');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this policy.');
    } finally {
      setBusyId(null);
    }
  };

  if (!catalog) {
    return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  }

  return (
    <div className="toss-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div>
        <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Crop Weather Insurance</h3>
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
          Rwanda's real National Agricultural Insurance Scheme model: if your district's official rainfall for this
          season falls below the drought threshold, every enrolled farmer in that district and season is paid
          automatically — no claim needed. The season's rainfall figure is transcribed by an admin from the real
          published NISR/Rwanda Meteorology Agency bulletin, not a live satellite feed.
        </p>
      </div>

      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}

      {policies.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          {policies.map((p) => (
            <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 600 }}>{p.cropType.replace('_', ' ')} · {p.district} {p.season}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                  {p.status} · insured {p.insuredAmount.toLocaleString()} RWF · premium {p.premiumAmount.toLocaleString()} RWF
                </p>
              </div>
              {p.status === 'ENROLLED' && (
                <button className="toss-btn toss-btn-secondary" disabled={busyId === p.id} onClick={() => handleCancel(p.id)}>
                  {busyId === p.id ? '...' : 'Cancel'}
                </button>
              )}
            </div>
          ))}
        </div>
      )}

      <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
        <select value={cropType} onChange={(e) => setCropType(e.target.value as WeatherIndexCropType)} style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}>
          {catalog.map((c) => (
            <option key={c.cropType} value={c.cropType}>{c.name} — {c.premiumRatePercent}% premium rate</option>
          ))}
        </select>
        <input
          placeholder="District (e.g. Nyagatare)"
          value={district}
          onChange={(e) => setDistrict(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
        />
        <input
          placeholder="Season (e.g. 2026B)"
          value={season}
          onChange={(e) => setSeason(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
        />
        <input
          type="number"
          placeholder="Insured amount (RWF, max 500,000)"
          value={insuredAmount}
          onChange={(e) => setInsuredAmount(e.target.value)}
          style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
        />
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Premium: {computedPremium.toLocaleString()} RWF, charged now to your wallet.</p>
        <button className="toss-btn toss-btn-secondary" disabled={enrolling} onClick={handleEnroll}>
          {enrolling ? '...' : 'Enroll'}
        </button>
      </div>
    </div>
  );
}

// Real bill-pay/airtime client -- see lib/bills.ts's own doc comment. Android/iOS
// already have this via the Saronite RN mini-app bridge; bank-mfe itself never had a
// screen for it despite the real, ledger-backed backend.
function BillsView() {
  const [providers, setProviders] = useState<BillProvider[] | null>(null);
  const [pending, setPending] = useState<PendingBill[]>([]);
  const [payingId, setPayingId] = useState<string | null>(null);
  const [airtimePhone, setAirtimePhone] = useState('');
  const [airtimeAmount, setAirtimeAmount] = useState('');
  const [airtimeProvider, setAirtimeProvider] = useState('');
  const [buyingAirtime, setBuyingAirtime] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  const load = () => {
    fetchBillProviders().then(setProviders).catch(() => setProviders([]));
    fetchPendingBills().then(setPending).catch(() => setPending([]));
  };
  useEffect(load, []);

  const handlePay = async (bill: PendingBill) => {
    setPayingId(bill.id);
    setError(null);
    setMessage(null);
    try {
      const result = await payBill(bill.id, bill.amount, bill.accountNumber, bill.provider);
      setMessage(`Paid ${result.amount.toLocaleString()} RWF — ${result.referenceNumber}`);
      setPending((prev) => prev.filter((b) => b.id !== bill.id));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pay this bill.');
    } finally {
      setPayingId(null);
    }
  };

  const handleBuyAirtime = async () => {
    const amount = Number(airtimeAmount);
    if (!airtimePhone.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real phone number and an amount greater than zero.');
      return;
    }
    setBuyingAirtime(true);
    setError(null);
    setMessage(null);
    try {
      const result = await buyAirtime(airtimePhone.trim(), amount, airtimeProvider || undefined);
      setMessage(`Sent ${result.amount.toLocaleString()} RWF airtime — ${result.referenceNumber}`);
      setAirtimePhone('');
      setAirtimeAmount('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not buy airtime.');
    } finally {
      setBuyingAirtime(false);
    }
  };

  if (!providers) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}

      {pending.length > 0 && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Pending bills</h3>
          {pending.map((b) => (
            <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 600 }}>{b.provider}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{b.accountNumber} · due {b.dueDate} · {b.amount.toLocaleString()} RWF</p>
              </div>
              <button className="toss-btn toss-btn-secondary" disabled={payingId === b.id} onClick={() => handlePay(b)}>
                {payingId === b.id ? '...' : 'Pay'}
              </button>
            </div>
          ))}
        </div>
      )}

      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Buy airtime</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <input
            placeholder="Phone number"
            value={airtimePhone}
            onChange={(e) => setAirtimePhone(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
          />
          <input
            type="number"
            placeholder="Amount (RWF)"
            value={airtimeAmount}
            onChange={(e) => setAirtimeAmount(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
          />
          <select
            value={airtimeProvider}
            onChange={(e) => setAirtimeProvider(e.target.value)}
            style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)' }}
          >
            <option value="">Default provider</option>
            {providers.filter((p) => p.category === 'airtime').map((p) => (
              <option key={p.id} value={p.name}>{p.logo} {p.name}</option>
            ))}
          </select>
          <button className="toss-btn toss-btn-secondary" disabled={buyingAirtime} onClick={handleBuyAirtime}>
            {buyingAirtime ? '...' : 'Buy airtime'}
          </button>
        </div>
      </div>

      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>All billers</h3>
        {providers.map((p) => (
          <div key={p.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <span>{p.logo} {p.name}</span>
            <span style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{p.category}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Itunda cash-agent operator console -- see lib/agentOperator.ts's own doc
// comment. A regular account only sees this view usefully once admin-assigned as an
// operator (AgentAdminController.assignOperator); an unassigned account gets a clean
// "you are not an agent operator" state instead of a generic error.
function AgentOperatorView() {
  const [section, setSection] = useState<'till' | 'float'>('till');
  const [till, setTill] = useState<AgentTillSnapshot | null>(null);
  const [activity, setActivity] = useState<AgentActivityItem[]>([]);
  const [notOperator, setNotOperator] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [cashInAccount, setCashInAccount] = useState('');
  const [cashInAmount, setCashInAmount] = useState('');
  const [cashInReceipt, setCashInReceipt] = useState('');
  const [cashOutAccount, setCashOutAccount] = useState('');
  const [cashOutAmount, setCashOutAmount] = useState('');
  const [cashOutReceipt, setCashOutReceipt] = useState('');
  const [cashOutCode, setCashOutCode] = useState('');
  const [countedCash, setCountedCash] = useState('');

  const load = () => {
    setError(null);
    fetchAgentTill()
      .then((t) => { setTill(t); setNotOperator(false); })
      .catch((err) => {
        if (isNotAgentOperatorError(err)) { setNotOperator(true); return; }
        setError(err instanceof ApiError ? err.message : 'Could not load your till.');
      });
    fetchAgentActivity().then(setActivity).catch(() => setActivity([]));
  };
  useEffect(load, []);

  const handleCashIn = async () => {
    const amount = Number(cashInAmount);
    if (!cashInAccount.trim() || !cashInReceipt.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real account number, receipt number, and a positive amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await agentCashIn(cashInAccount.trim(), amount, cashInReceipt.trim());
      setMessage(`Cash in accepted — new customer balance ${result.newBalance.toLocaleString()} RWF`);
      setCashInAccount(''); setCashInAmount(''); setCashInReceipt('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not accept this cash-in.');
    } finally {
      setBusy(false);
    }
  };

  const handleCashOut = async () => {
    const amount = Number(cashOutAmount);
    if (!cashOutAccount.trim() || !cashOutReceipt.trim() || !cashOutCode.trim() || !Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real account number, receipt number, withdrawal code, and a positive amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const result = await agentCashOut(cashOutAccount.trim(), amount, cashOutReceipt.trim(), cashOutCode.trim());
      setMessage(`Cash out paid — new customer balance ${result.newBalance.toLocaleString()} RWF`);
      setCashOutAccount(''); setCashOutAmount(''); setCashOutReceipt(''); setCashOutCode('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pay this cash-out. Check the withdrawal code.');
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitTillCount = async () => {
    const counted = Number(countedCash);
    if (!Number.isFinite(counted) || counted < 0) {
      setError('Enter a real counted-cash amount.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      const reconciliation = await submitAgentTillCount(counted);
      setMessage(`Till count submitted — variance ${reconciliation.variance.toLocaleString()} RWF (${reconciliation.status})`);
      setCountedCash('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this till count.');
    } finally {
      setBusy(false);
    }
  };

  if (notOperator) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          You are not assigned as an Itunda agent till operator. Ask an Itunda staff admin to assign your account to a store.
        </p>
      </div>
    );
  }

  if (!till) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button className={section === 'till' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'} onClick={() => setSection('till')} style={{ flex: 1 }}>Till</button>
        <button className={section === 'float' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'} onClick={() => setSection('float')} style={{ flex: 1 }}>Float marketplace</button>
      </div>
      {section === 'float' ? <FloatMarketplaceSection /> : (
      <>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{till.agentName}</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{till.expectedCash.toLocaleString()} RWF expected in till</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          Today: {till.todayCashIn.toLocaleString()} RWF in · {till.todayCashOut.toLocaleString()} RWF out
        </p>
        {till.reconciliation && (
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            Last count: {till.reconciliation.countedCash.toLocaleString()} RWF ({till.reconciliation.status}, variance {till.reconciliation.variance.toLocaleString()})
          </p>
        )}
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Accept cash-in</h3>
        <input type="text" placeholder="Customer account number" value={cashInAccount} onChange={(e) => setCashInAccount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <input type="number" placeholder="Amount (RWF)" value={cashInAmount} onChange={(e) => setCashInAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <input type="text" placeholder="Receipt number" value={cashInReceipt} onChange={(e) => setCashInReceipt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handleCashIn}>{busy ? 'Working…' : 'Accept cash-in'}</button>
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Pay cash-out</h3>
        <input type="text" placeholder="Customer account number" value={cashOutAccount} onChange={(e) => setCashOutAccount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <input type="number" placeholder="Amount (RWF)" value={cashOutAmount} onChange={(e) => setCashOutAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <input type="text" placeholder="Receipt number" value={cashOutReceipt} onChange={(e) => setCashOutReceipt(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <input type="text" placeholder="Customer's withdrawal code" value={cashOutCode} onChange={(e) => setCashOutCode(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handleCashOut}>{busy ? 'Working…' : 'Pay cash-out'}</button>
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Submit today's till count</h3>
        <input type="number" placeholder="Counted cash (RWF)" value={countedCash} onChange={(e) => setCountedCash(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleSubmitTillCount}>{busy ? 'Working…' : 'Submit count'}</button>
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Recent activity</h3>
        {activity.length === 0 && <EmptyState message="No cash movements yet today." />}
        {activity.map((a) => (
          <div key={a.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <span>{a.type === 'CASH_IN' ? '↓ Cash in' : '↑ Cash out'} · {a.receiptNumber}</span>
            <span style={{ fontWeight: 600 }}>{a.amount.toLocaleString()} RWF</span>
          </div>
        ))}
      </div>
      </>
      )}
    </div>
  );
}

// Real Rwanda-native peer-to-peer agent float rebalancing marketplace -- the fifth
// feature in this codebase not sourced from Toss/당근/Coupang/Naver/Kakao. See
// lib/floatMarketplace.ts's own doc comment for the full sourced account.
function FloatMarketplaceSection() {
  const [nearby, setNearby] = useState<NearbyFloatListing[]>([]);
  const [myListings, setMyListings] = useState<FloatListing[]>([]);
  const [myRequests, setMyRequests] = useState<FloatTransferRequest[]>([]);
  const [incomingRequests, setIncomingRequests] = useState<FloatTransferRequest[]>([]);
  const [locating, setLocating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [listAmount, setListAmount] = useState('');
  const [requestAmounts, setRequestAmounts] = useState<Record<string, string>>({});

  const loadMine = () => {
    fetchMyFloatListings().then(setMyListings).catch(() => setMyListings([]));
    fetchMyFloatRequests().then(setMyRequests).catch(() => setMyRequests([]));
    fetchIncomingFloatRequests().then(setIncomingRequests).catch(() => setIncomingRequests([]));
  };
  useEffect(loadMine, []);

  const handleFindNearby = () => {
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        fetchNearbyFloatListings(position.coords.latitude, position.coords.longitude)
          .then(setNearby)
          .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load nearby float listings.'))
          .finally(() => setLocating(false));
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handlePostListing = async () => {
    const amount = Number(listAmount);
    if (!Number.isFinite(amount) || amount <= 0) {
      setError('Enter a real positive amount of float to offer.');
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await postFloatListing(amount);
      setMessage('Listing posted -- other nearby agents can now request this float.');
      setListAmount('');
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleCancelListing = async (listingId: string) => {
    setBusy(true);
    setError(null);
    try {
      await cancelFloatListing(listingId);
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleRequestFloat = async (listingId: string, remainingAmount: number) => {
    const raw = requestAmounts[listingId];
    const amount = Number(raw);
    if (!Number.isFinite(amount) || amount <= 0 || amount > remainingAmount) {
      setError(`Enter a real amount up to the ${remainingAmount.toLocaleString()} RWF still available on this listing.`);
      return;
    }
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await requestFloat(listingId, amount);
      setMessage('Request sent -- the listing owner will accept or decline it.');
      setRequestAmounts((prev) => ({ ...prev, [listingId]: '' }));
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this request.');
    } finally {
      setBusy(false);
    }
  };

  const handleAcceptRequest = async (requestId: string) => {
    setBusy(true);
    setError(null);
    setMessage(null);
    try {
      await acceptFloatRequest(requestId);
      setMessage('Float transferred to the requesting agent.');
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not accept this request.');
    } finally {
      setBusy(false);
    }
  };

  const handleDeclineRequest = async (requestId: string) => {
    setBusy(true);
    setError(null);
    try {
      await declineFloatRequest(requestId);
      loadMine();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not decline this request.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Offer surplus float</h3>
        <input type="number" placeholder="Amount to offer (RWF)" value={listAmount} onChange={(e) => setListAmount(e.target.value)}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', width: '100%', marginBottom: '8px' }} />
        <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handlePostListing}>{busy ? 'Working…' : 'Post listing'}</button>
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Nearby agents with float to spare</h3>
        <button className="toss-btn toss-btn-secondary" disabled={locating} onClick={handleFindNearby} style={{ marginBottom: '8px' }}>
          {locating ? 'Finding…' : 'Find nearby listings'}
        </button>
        {nearby.length === 0 && <EmptyState message="No nearby listings loaded yet." />}
        {nearby.map((n) => (
          <div key={n.listing.id} style={{ padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              <span>{n.agentDisplayName} · {n.distanceKm.toFixed(1)} km</span>
              <span style={{ fontWeight: 600 }}>{n.remainingAmount.toLocaleString()} RWF available</span>
            </div>
            <div style={{ display: 'flex', gap: '8px', marginTop: '6px' }}>
              <input type="number" placeholder="Amount to request" value={requestAmounts[n.listing.id] || ''}
                onChange={(e) => setRequestAmounts((prev) => ({ ...prev, [n.listing.id]: e.target.value }))}
                style={{ padding: '8px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', flex: 1 }} />
              <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => handleRequestFloat(n.listing.id, n.remainingAmount)}>Request</button>
            </div>
          </div>
        ))}
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My listings</h3>
        {myListings.length === 0 && <EmptyState message="No listings posted yet." />}
        {myListings.map((l) => (
          <div key={l.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', padding: '6px 0' }}>
            <span>{l.amount.toLocaleString()} RWF offered · {l.claimedAmount.toLocaleString()} claimed · {l.status}</span>
            {l.status === 'OPEN' && <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => handleCancelListing(l.id)}>Cancel</button>}
          </div>
        ))}
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Requests against my listings</h3>
        {incomingRequests.length === 0 && <EmptyState message="No requests received yet." />}
        {incomingRequests.map((r) => (
          <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', padding: '6px 0' }}>
            <span>{r.amount.toLocaleString()} RWF · {r.status}</span>
            {r.status === 'REQUESTED' && (
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="toss-btn toss-btn-primary" disabled={busy} onClick={() => handleAcceptRequest(r.id)}>Accept</button>
                <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => handleDeclineRequest(r.id)}>Decline</button>
              </div>
            )}
          </div>
        ))}
      </div>

      <div className="toss-card">
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>My requests</h3>
        {myRequests.length === 0 && <EmptyState message="No requests sent yet." />}
        {myRequests.map((r) => (
          <div key={r.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
            <span>{r.amount.toLocaleString()} RWF</span>
            <span style={{ fontWeight: 600 }}>{r.status}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real USSD basic-banking access (item 231) -- the fourth feature in this codebase
// not sourced from Toss/당근/Coupang/Naver/Kakao. See lib/ussd.ts's own doc comment
// for the full sourced account (Rwanda's real ~34-35% smartphone penetration).
function UssdSettingsView() {
  const [pin, setPin] = useState('');
  const [confirmPin, setConfirmPin] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSuccess(false);
    if (!/^\d{4,6}$/.test(pin)) {
      setError('PIN must be 4-6 digits.');
      return;
    }
    if (pin !== confirmPin) {
      setError('PINs did not match.');
      return;
    }
    setSubmitting(true);
    try {
      await setUssdPin(pin);
      setPin('');
      setConfirmPin('');
      setSuccess(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not set your USSD PIN.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '20px' }}>
        <p style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>USSD access</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          Roughly two-thirds of people in Rwanda have a feature phone, not a smartphone. Set a real 4-6 digit
          USSD PIN so you can check your balance and send money from any phone, no app or internet needed.
        </p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          Honestly scoped: the real menu, PIN check, and money transfer are fully built and working today. Dialing
          a short code like <code>*123#</code> to reach them needs a real partnership with a mobile network operator
          this project doesn't have yet -- the same honest limitation as our ID-verification integration.
        </p>
        <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <input
            type="password" inputMode="numeric" value={pin} onChange={(e) => setPin(e.target.value)}
            placeholder="New USSD PIN (4-6 digits)" maxLength={6}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
          <input
            type="password" inputMode="numeric" value={confirmPin} onChange={(e) => setConfirmPin(e.target.value)}
            placeholder="Confirm PIN" maxLength={6}
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
          />
          {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
          {success && <p style={{ fontSize: '13px', color: '#1E8E4F' }}>Your USSD PIN has been set.</p>}
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
            {submitting ? 'Saving…' : 'Set USSD PIN'}
          </button>
        </form>
      </div>
    </div>
  );
}

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (item 154) -- see
// lib/foreignCurrency.ts's own doc comment.
function ForeignCurrencyView() {
  const [wallets, setWallets] = useState<ForeignCurrencyWallet[] | null>(null);
  const [conversions, setConversions] = useState<CurrencyConversion[]>([]);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyForeignCurrencyWallets()
      .then(setWallets)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your foreign-currency accounts.'));
    fetchMyCurrencyConversions().then(setConversions).catch(() => setConversions([]));
  };
  useEffect(load, []);

  if (wallets === null) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '160px' }} />;
  }

  const openCurrencies = new Set(wallets.map((w) => w.currency));
  const availableToOpen = FOREIGN_CURRENCY_SUPPORTED.filter((c) => !openCurrencies.has(c));

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {wallets.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            Open a USD, EUR, or GBP account to hold foreign currency and convert between it and RWF at a real live rate.
          </p>
        </div>
      ) : (
        wallets.map((w) => (
          <div key={w.id} className="toss-card" style={{ padding: '20px' }}>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{w.currency} account</p>
            <h2 style={{ fontSize: '24px', fontWeight: 700 }}>{w.balance.toLocaleString()} {w.currency}</h2>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{w.accountNumber}</p>
          </div>
        ))
      )}

      {availableToOpen.length > 0 && <OpenForeignWalletCard currencies={availableToOpen} onOpened={load} />}
      {wallets.length > 0 && <ConvertCurrencyCard wallets={wallets} onConverted={load} />}

      {conversions.length > 0 && (
        <div className="toss-card">
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Recent conversions</h3>
          {conversions.map((c) => (
            <div key={c.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '6px 0' }}>
              <p>{c.fromCurrency} → {c.toCurrency}</p>
              <p>{c.fromAmount.toLocaleString()} {c.fromCurrency} → {c.toAmount.toLocaleString()} {c.toCurrency}</p>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

function OpenForeignWalletCard({ currencies, onOpened }: { currencies: readonly ForeignCurrencyCode[]; onOpened: () => void }) {
  const [opening, setOpening] = useState<ForeignCurrencyCode | null>(null);
  const [error, setError] = useState<string | null>(null);

  const handleOpen = async (currency: ForeignCurrencyCode) => {
    setError(null);
    setOpening(currency);
    try {
      await openForeignCurrencyWallet(currency);
      onOpened();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not open this account.');
    } finally {
      setOpening(null);
    }
  };

  return (
    <div className="toss-card">
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Open an account</h3>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        {currencies.map((c) => (
          <button
            key={c}
            className="toss-btn toss-btn-secondary"
            style={{ flex: 1 }}
            disabled={opening !== null}
            onClick={() => handleOpen(c)}
          >
            {opening === c ? '…' : c}
          </button>
        ))}
      </div>
    </div>
  );
}

function ConvertCurrencyCard({ wallets, onConverted }: { wallets: ForeignCurrencyWallet[]; onConverted: () => void }) {
  const [direction, setDirection] = useState<'TO_FOREIGN' | 'TO_RWF'>('TO_FOREIGN');
  const [currency, setCurrency] = useState(wallets[0]?.currency ?? '');
  const [amount, setAmount] = useState('');
  const [rate, setRate] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<CurrencyConversion | null>(null);

  const fromCurrency = direction === 'TO_FOREIGN' ? 'RWF' : currency;
  const toCurrency = direction === 'TO_FOREIGN' ? currency : 'RWF';

  useEffect(() => {
    if (!currency) return;
    fetchExchangeRate(fromCurrency, toCurrency).then((r) => setRate(r.rate)).catch(() => setRate(null));
  }, [fromCurrency, toCurrency, currency]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setResult(null);
    setSubmitting(true);
    try {
      const conversion = await convertCurrency(fromCurrency, toCurrency, Number(amount));
      setResult(conversion);
      setAmount('');
      onConverted();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not convert this amount.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Convert</h3>
      <div style={{ display: 'flex', gap: '8px' }}>
        <select value={currency} onChange={(e) => setCurrency(e.target.value)} style={{ flex: 1, padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)' }}>
          {wallets.map((w) => <option key={w.currency} value={w.currency}>{w.currency}</option>)}
        </select>
        <select value={direction} onChange={(e) => setDirection(e.target.value as 'TO_FOREIGN' | 'TO_RWF')} style={{ flex: 1, padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)' }}>
          <option value="TO_FOREIGN">RWF → {currency}</option>
          <option value="TO_RWF">{currency} → RWF</option>
        </select>
      </div>
      <input
        type="number"
        min="0"
        step="0.01"
        value={amount}
        onChange={(e) => setAmount(e.target.value)}
        placeholder={`Amount (${fromCurrency})`}
        required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
      />
      {rate !== null && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Live rate: 1 {fromCurrency} ≈ {rate.toFixed(4)} {toCurrency} (before itunda's 1.5% margin)</p>
      )}
      {error && <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
      {result && (
        <p style={{ fontSize: '13px', color: 'var(--toss-blue)', fontWeight: 700, margin: 0 }}>
          Converted {result.fromAmount.toLocaleString()} {result.fromCurrency} → {result.toAmount.toLocaleString()} {result.toCurrency}
        </p>
      )}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
        {submitting ? 'Converting…' : 'Convert'}
      </button>
    </form>
  );
}

// Real Kakao Pay 소비 리포트-style spending categorization (2026-07-13, wired 2026-07-28
// as item 106) -- see lib/wallet.ts's own doc comment. Found backend-only via a fresh
// matrix scan: real, ledger-based, and live since well before this session, but never
// wired to any client anywhere.
function SpendingInsightView() {
  const [categories, setCategories] = useState<SpendingCategory[] | null>(null);
  const [totalSpent, setTotalSpent] = useState<number>(0);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchSpendingInsight()
      .then((r) => { setCategories(r.categories); setTotalSpent(r.totalSpent); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your spending.'));
  }, []);

  if (!categories) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '200px' }} />;
  }

  const maxAmount = Math.max(...categories.map((c) => c.amount), 1);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Total spent, all time</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{totalSpent.toLocaleString()} RWF</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Real, ledger-based -- what every wallet debit actually paid for.</p>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>By category</h3>
        {categories.length === 0 ? (
          <EmptyState message="No spending recorded yet." />
        ) : (
          categories.map((c) => (
            <div key={c.name} style={{ padding: '8px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px' }}>
                <span>{c.name}</span>
                <span style={{ fontWeight: 700 }}>{c.amount.toLocaleString()} RWF</span>
              </div>
              <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--toss-grey-100)', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${(c.amount / maxAmount) * 100}%`, backgroundColor: 'var(--toss-blue)', borderRadius: '3px' }} />
              </div>
            </div>
          ))
        )}
      </div>
      <BudgetsSection categories={categories} />
    </div>
  );
}

// Real Toss-style monthly budgets/limits (item 165) -- see lib/wallet.ts's own doc
// comment. `WalletService.setBudget/getBudgets` (including real 80%/100%-threshold
// notifications, wired since 2026-07-28) had zero client anywhere until now.
function BudgetsSection({ categories }: { categories: SpendingCategory[] }) {
  const [budgets, setBudgets] = useState<BudgetView[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [showForm, setShowForm] = useState(false);

  const load = () => {
    setError(null);
    fetchBudgets().then(setBudgets).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your budgets.'));
  };
  useEffect(load, []);

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Budgets</h3>
        <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => setShowForm((v) => !v)}>
          {showForm ? 'Cancel' : '+ Set budget'}
        </button>
      </div>
      {showForm && <SetBudgetForm categories={categories} onSet={() => { setShowForm(false); load(); }} />}
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {budgets === null ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
      ) : budgets.length === 0 ? (
        <EmptyState message="No budgets set yet -- set a monthly limit to get alerted before you overspend." />
      ) : (
        budgets.map((b) => {
          const barColor = b.status === 'OVER' ? '#E53935' : b.status === 'NEAR' ? '#F5A623' : 'var(--toss-blue)';
          return (
            <div key={b.category ?? 'overall'} style={{ padding: '8px 0' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', marginBottom: '4px' }}>
                <span>{b.category ?? 'Overall'}</span>
                <span style={{ fontWeight: 700, color: barColor }}>{b.spent.toLocaleString()} / {b.monthlyLimit.toLocaleString()} RWF</span>
              </div>
              <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--toss-grey-100)', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${Math.min(100, b.percentUsed)}%`, backgroundColor: barColor, borderRadius: '3px' }} />
              </div>
              {b.status === 'OVER' && <p style={{ fontSize: '11px', color: '#E53935', marginTop: '2px' }}>Over budget</p>}
              {b.status === 'NEAR' && <p style={{ fontSize: '11px', color: '#F5A623', marginTop: '2px' }}>Nearing your limit</p>}
            </div>
          );
        })
      )}
    </div>
  );
}

function SetBudgetForm({ categories, onSet }: { categories: SpendingCategory[]; onSet: () => void }) {
  const [category, setCategory] = useState('');
  const [monthlyLimit, setMonthlyLimit] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const limit = Number(monthlyLimit);
    if (!limit || limit <= 0) {
      setError('Enter a real monthly limit.');
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await setBudget(category || undefined, limit);
      setMonthlyLimit('');
      onSet();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not set this budget.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '14px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <select value={category} onChange={(e) => setCategory(e.target.value)} style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}>
        <option value="">Overall spending</option>
        {categories.map((c) => <option key={c.name} value={c.name}>{c.name}</option>)}
      </select>
      <input
        type="number"
        min="1"
        value={monthlyLimit}
        onChange={(e) => setMonthlyLimit(e.target.value)}
        placeholder="Monthly limit (RWF)"
        required
        style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ fontSize: '13px', padding: '8px' }}>
        {submitting ? 'Saving…' : 'Save budget'}
      </button>
    </form>
  );
}

// Real recurring-payment ("subscription") detection (2026-07-26) -- see
// SubscriptionDetectionService's own doc comment for the real Toss "구독 관리"
// capability this closes, including the same-day price-change alert. Found with zero
// client UI anywhere.
function SubscriptionsView() {
  const [subscriptions, setSubscriptions] = useState<DetectedSubscription[] | null>(null);
  const [total, setTotal] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [billingSubs, setBillingSubs] = useState<MerchantBillingSubscription[] | null>(null);
  const [billingError, setBillingError] = useState<string | null>(null);

  const loadBilling = () => {
    setBillingError(null);
    fetchMyBillingSubscriptions()
      .then(setBillingSubs)
      .catch((err) => setBillingError(err instanceof ApiError ? err.message : 'Could not load your subscriptions.'));
  };

  useEffect(() => {
    fetchSubscriptions()
      .then((r) => { setSubscriptions(r.subscriptions); setTotal(r.estimatedMonthlyTotal); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your subscriptions.'));
    loadBilling();
  }, []);

  if (subscriptions === null) {
    return error ? <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p> : <div className="toss-card skeleton" style={{ height: '160px' }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div className="toss-card" style={{ padding: '24px' }}>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Estimated monthly total</p>
        <h2 style={{ fontSize: '26px', fontWeight: 700 }}>{total.toLocaleString()} RWF</h2>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Detected from your own real payment history, not a linked-card feed.</p>
      </div>
      {subscriptions.length === 0 ? (
        <EmptyState message="No recurring payments detected yet." />
      ) : (
        subscriptions.map((s) => (
          <div key={`${s.displayName}-${s.cadence}`} className="toss-card" style={{ padding: '16px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{s.displayName}</h4>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{s.cadence === 'WEEKLY' ? 'Weekly' : 'Monthly'} · {s.occurrenceCount} payments seen</p>
              </div>
              <div style={{ textAlign: 'right' }}>
                <p style={{ fontSize: '14px', fontWeight: 700 }}>{s.amount.toLocaleString()} RWF</p>
                {s.priceIncreased && s.previousAmount !== null && (
                  <p style={{ fontSize: '11px', color: '#E53935' }}>↑ from {s.previousAmount.toLocaleString()} RWF</p>
                )}
              </div>
            </div>
          </div>
        ))
      )}

      {/* Real Kakao Pay 정기결제/Toss 빌링키-style merchant subscriptions the customer
          actually authorized (item 145) -- distinct from the detected-from-history
          section above: these are real active billing-key authorizations that charge
          automatically until cancelled, not a heuristic guess. */}
      <div className="toss-card" style={{ padding: '24px' }}>
        <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>Merchant subscriptions</h3>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
          Plans you've subscribed to. These charge your wallet automatically until you cancel.
        </p>
        {billingError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{billingError}</p>}
        {billingSubs === null && !billingError ? (
          <div className="toss-card skeleton" style={{ height: '80px' }} />
        ) : billingSubs && billingSubs.length === 0 ? (
          <EmptyState message="No merchant subscriptions yet." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {billingSubs?.map((sub) => (
              <MerchantBillingSubscriptionRow key={sub.id} subscription={sub} onChanged={loadBilling} />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

function MerchantBillingSubscriptionRow({ subscription, onChanged }: { subscription: MerchantBillingSubscription; onChanged: () => void }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleCancel = async () => {
    setError(null);
    setBusy(true);
    try {
      await cancelBillingSubscription(subscription.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not cancel this subscription.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>{subscription.chargeCount} charge{subscription.chargeCount === 1 ? '' : 's'} so far</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            {subscription.status === 'ACTIVE'
              ? `Next charge ${new Date(subscription.nextChargeAt).toLocaleDateString()}`
              : `Cancelled ${subscription.cancelledAt ? new Date(subscription.cancelledAt).toLocaleDateString() : ''}`}
          </p>
          {subscription.lastFailureReason && subscription.status === 'ACTIVE' && (
            <p style={{ fontSize: '11px', color: '#E53935' }}>Last charge failed: {subscription.lastFailureReason}</p>
          )}
        </div>
        {subscription.status === 'ACTIVE' && (
          <button
            className="toss-btn toss-btn-secondary"
            style={{ padding: '6px 12px', fontSize: '12px', whiteSpace: 'nowrap' }}
            disabled={busy}
            onClick={handleCancel}
          >
            {busy ? '…' : 'Cancel'}
          </button>
        )}
      </div>
      {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
    </div>
  );
}

// Real personal KYC identity submission (2026-07-22) -- found fully built on the
// backend (rw.itunda.identity) with zero client UI anywhere; merchant-mfe already has
// KYB submission and ops-mfe the review queue, but this ordinary personal
// NATIONAL_ID/PASSPORT submission had zero UI on any client -- kyc-mfe, checked
// directly, is an unwired mock shell with no real API calls at all.
function IdentityView() {
  const [submissions, setSubmissions] = useState<KycSubmission[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [documentType, setDocumentType] = useState<IdentityDocumentType>('NATIONAL_ID');
  const [documentNumber, setDocumentNumber] = useState('');
  const [documentReference, setDocumentReference] = useState('');

  const refresh = () => {
    setError(null);
    fetchIdentityStatus()
      .then(setSubmissions)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your identity status.'));
  };

  useEffect(refresh, []);

  const hasPending = submissions?.some((s) => s.status === 'PENDING') ?? false;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await submitIdentity(documentType, documentNumber, documentReference);
      setDocumentNumber(''); setDocumentReference('');
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That submission could not be completed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <h2 style={{ fontSize: '18px', fontWeight: 700 }}>Verify your identity</h2>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {hasPending ? (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Submission pending review</h4>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>We'll update your status once it's reviewed.</p>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '16px' }}>
          <div style={{ display: 'flex', gap: '6px' }}>
            {(['NATIONAL_ID', 'PASSPORT'] as IdentityDocumentType[]).map((t) => (
              <button
                type="button" key={t}
                className={documentType === t ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                onClick={() => setDocumentType(t)}
              >
                {t}
              </button>
            ))}
          </div>
          <input
            type="text" value={documentNumber} onChange={(e) => setDocumentNumber(e.target.value)} placeholder="Document number" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <input
            type="text" value={documentReference} onChange={(e) => setDocumentReference(e.target.value)} placeholder="Document reference (scan/photo reference)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Submitting…' : 'Submit for review'}</button>
        </form>
      )}
      <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Your submissions</h3>
      {submissions === null ? <div className="toss-card skeleton" style={{ height: '80px' }} /> :
        submissions.length === 0 ? <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You have no submissions yet.</p> :
        submissions.map((s) => (
          <div key={s.id} className="toss-card" style={{ padding: '16px' }}>
            <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{s.documentType} · {s.documentNumber}</h4>
            <p style={{ fontSize: '13px' }}>Status: {s.status}</p>
            {s.decisionReason && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{s.decisionReason}</p>}
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Filed: {s.submittedAt}</p>
          </div>
        ))}
    </div>
  );
}

// Real customer support tickets (2026-07-22) -- found fully built on the backend
// (rw.itunda.support) with zero client UI anywhere. A ticket is always tied to a
// specific transaction (see SupportTicket.kt's own doc comment for why), so this view
// has the user pick one from their real transaction history rather than filing a
// free-floating complaint.
const SUPPORT_CATEGORIES: SupportTicketCategory[] = ['GENERAL', 'PAYMENT_DISPUTE', 'ACCOUNT_TAKEOVER'];

function SupportView() {
  const [tickets, setTickets] = useState<SupportTicket[] | null>(null);
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [showNewForm, setShowNewForm] = useState(false);
  const [selectedTransactionId, setSelectedTransactionId] = useState<string | null>(null);
  const [category, setCategory] = useState<SupportTicketCategory>('GENERAL');
  const [description, setDescription] = useState('');

  const refresh = () => {
    setError(null);
    Promise.all([fetchSupportTickets(), fetchTransactions()])
      .then(([t, tx]) => { setTickets(t); setTransactions(tx); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load support tickets.'));
  };

  useEffect(refresh, []);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedTransactionId) return;
    setBusy(true);
    setError(null);
    try {
      await createSupportTicket(selectedTransactionId, category, description);
      setSelectedTransactionId(null); setDescription(''); setShowNewForm(false);
      refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'That ticket could not be submitted.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="toss-btn toss-btn-primary" onClick={() => setShowNewForm(true)}>Report an issue with a transaction</button>
      ) : (
        <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '16px' }}>
          <p style={{ fontSize: '12px', fontWeight: 700 }}>Which transaction?</p>
          {transactions.slice(0, 10).map((tx) => (
            <label key={tx.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
              {tx.description} · {tx.currency} {tx.amount.toLocaleString()}
              <input type="radio" name="tx" checked={selectedTransactionId === tx.id} onChange={() => setSelectedTransactionId(tx.id)} />
            </label>
          ))}
          <p style={{ fontSize: '12px', fontWeight: 700 }}>Category</p>
          <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
            {SUPPORT_CATEGORIES.map((c) => (
              <button
                type="button" key={c}
                className={category === c ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                onClick={() => setCategory(c)}
              >
                {c}
              </button>
            ))}
          </div>
          <textarea
            value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the issue" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy || !selectedTransactionId || !description}>
            {busy ? 'Submitting…' : 'Submit ticket'}
          </button>
        </form>
      )}
      <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Your tickets</h3>
      {tickets === null ? <div className="toss-card skeleton" style={{ height: '80px' }} /> :
        tickets.length === 0 ? <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You have no support tickets.</p> :
        tickets.map((t) => (
          <div key={t.id} className="toss-card" style={{ padding: '16px' }}>
            <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{t.category}</h4>
            <p style={{ fontSize: '13px' }}>{t.description}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Status: {t.status}</p>
            {t.resolution && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Resolution: {t.resolution}</p>}
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Filed: {t.createdAt}</p>
          </div>
        ))}
    </div>
  );
}

// Real customer-side view of merchant bookings requested via BookingWidget above --
// see lib/booking.ts's own doc comment. Cancel is the only customer action here
// (confirm/decline/complete are owner-side, already real on merchant-mfe).
function MyBookingsCard() {
  const [bookings, setBookings] = useState<MerchantBooking[] | null>(null);
  const [cancelling, setCancelling] = useState<string | null>(null);

  const load = () => {
    fetchMyBookings().then(setBookings).catch(() => setBookings([]));
  };
  useEffect(load, []);

  if (!bookings || bookings.length === 0) return null;

  const cancel = async (id: string) => {
    setCancelling(id);
    try {
      await cancelBooking(id);
      load();
    } catch {
      // Real, non-critical -- a failed cancel just leaves the booking as-is; the user
      // can retry.
    } finally {
      setCancelling(null);
    }
  };

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>My bookings</h3>
      {bookings.slice(0, 5).map((b) => (
        <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', fontSize: '13px', borderTop: '1px solid var(--toss-grey-100)' }}>
          <div>
            <p style={{ fontWeight: 600 }}>{b.serviceName}</p>
            <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{b.bookingDate} · {b.startTime.slice(0, 5)} · {b.status}</p>
          </div>
          {(b.status === 'REQUESTED' || b.status === 'CONFIRMED') && (
            <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 10px', fontSize: '12px' }} onClick={() => cancel(b.id)} disabled={cancelling === b.id}>
              {cancelling === b.id ? '…' : 'Cancel'}
            </button>
          )}
          {b.status === 'COMPLETED' && <BookingReviewButton booking={b} />}
        </div>
      ))}
    </div>
  );
}

// Real customer-side post-appointment review (item 143) -- see lib/booking.ts's own
// doc comment. Mirrors ProductReviewRow's exact shape (star rating + optional comment,
// a real BOOKING_ALREADY_REVIEWED 409 is treated as already-done, not an error).
function BookingReviewButton({ booking }: { booking: MerchantBooking }) {
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
      await submitBookingReview(booking.id, rating, comment);
      setDone(true);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'BOOKING_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Thanks for your review!</span>;
  }

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 10px', fontSize: '12px' }} onClick={() => setOpen(true)}>
        Rate this visit
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', width: '100%' }}>
      <StarRatingInput value={rating} onChange={setRating} />
      <input
        type="text"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="How was it? (optional)"
        style={{ width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
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

  const rowStyle: React.CSSProperties = { display: 'flex', justifyContent: 'space-between', padding: '8px 0', fontSize: '13px' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <ProfilePhotoCard />
      <VerificationCard />
      <NotificationsCard />
      <MyBookingsCard />
      {(shopOrders.length > 0 || eatsOrders.length > 0) && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>My orders</h3>
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
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>My favorites</h3>
        <div style={rowStyle}><span>Marketplace wishlist</span><span>{favoriteListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs wishlist</span><span>{favoriteJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property wishlist</span><span>{favoritePropertyListingsCount}</span></div>
        <div style={rowStyle}><span>Restaurant favorites</span><span>{favoriteRestaurantsCount}</span></div>
      </div>
      <div className="toss-card" style={{ padding: '16px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>My listings</h3>
        <div style={rowStyle}><span>Marketplace</span><span>{myListingsCount}</span></div>
        <div style={rowStyle}><span>Jobs posted</span><span>{myJobPostsCount}</span></div>
        <div style={rowStyle}><span>Property listed</span><span>{myPropertyListingsCount}</span></div>
      </div>
      <MyVehiclesCard />
      <FamilyLinkCard />
      <MyProductSubscriptionsCard />
      <AffiliateEarningsCard />
      {miniApps.length > 0 && (
        <div className="toss-card" style={{ padding: '16px' }}>
          <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Mini apps</h3>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
            Third-party apps reviewed and approved to run inside itunda.
          </p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {miniApps.map((app) => (
              <div key={app.id} style={{ display: 'flex', gap: '10px', alignItems: 'flex-start' }}>
                {app.iconUrl ? (
                  <img src={app.iconUrl} alt="" style={{ width: '36px', height: '36px', borderRadius: '8px', flexShrink: 0 }} />
                ) : (
                  <div style={{ width: '36px', height: '36px', borderRadius: '8px', backgroundColor: 'var(--toss-grey-100)', flexShrink: 0 }} />
                )}
                <div>
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>{app.name}</p>
                  <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{app.description}</p>
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

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Notifications</h3>
        {unreadCount > 0 && (
          <span
            role="button"
            onClick={handleReadAll}
            style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-blue)', cursor: 'pointer' }}
          >
            Mark all read
          </span>
        )}
      </div>
      {notifications.length === 0 ? (
        <EmptyState message="No notifications" />
      ) : (
        notifications.slice(0, 10).map((n) => (
          <div
            key={n.id}
            onClick={() => !n.isRead && handleRead(n.id)}
            style={{
              display: 'flex',
              flexDirection: 'column',
              gap: '2px',
              padding: '10px 0',
              borderTop: '1px solid var(--toss-grey-100)',
              cursor: n.isRead ? 'default' : 'pointer',
            }}
          >
            <span style={{ fontSize: '14px', fontWeight: n.isRead ? 400 : 700 }}>{n.title}</span>
            <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{n.body}</span>
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
// Real profile photo (URL, not a binary upload) -- also the real, buildable half of
// Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep: a real,
// working `PUT /api/v1/auth/profile/photo` endpoint with zero client anywhere, and
// `PublicUser.profilePhotoUrl` wasn't even carried by any client's own User type.
function ProfilePhotoCard() {
  const [profilePhotoUrl, setProfilePhotoUrl] = useState<string | null>(null);
  const [urlInput, setUrlInput] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchProfile()
      .then((u) => { setProfilePhotoUrl(u.profilePhotoUrl); setUrlInput(u.profilePhotoUrl ?? ''); })
      .catch(() => {
        // Real, non-critical -- the rest of "My" still works without this.
      });
  }, []);

  const handleSave = async () => {
    const trimmed = urlInput.trim();
    if (!trimmed) { setError('Enter a photo URL.'); return; }
    setSaving(true);
    setError(null);
    try {
      const user = await updateProfilePhoto(trimmed);
      setProfilePhotoUrl(user.profilePhotoUrl);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your profile photo.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="toss-card" style={{ padding: '16px', display: 'flex', gap: '12px', alignItems: 'center' }}>
      {profilePhotoUrl ? (
        <img src={profilePhotoUrl} alt="" style={{ width: '56px', height: '56px', borderRadius: '28px', objectFit: 'cover', flexShrink: 0 }} />
      ) : (
        <div style={{ width: '56px', height: '56px', borderRadius: '28px', backgroundColor: 'var(--toss-grey-100)', flexShrink: 0 }} />
      )}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: '6px' }}>
        {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
        <input
          type="url" value={urlInput} onChange={(e) => setUrlInput(e.target.value)} placeholder="Profile photo URL"
          style={{ padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '12px' }}
        />
        <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px', alignSelf: 'flex-start' }} disabled={saving} onClick={handleSave}>
          {saving ? 'Saving…' : 'Save photo'}
        </button>
      </div>
    </div>
  );
}

function VerificationCard() {
  const [status, setStatus] = useState<{ email: string | null; emailVerified: boolean; phoneVerified: boolean } | null>(null);

  const load = () => {
    fetchProfile().then((u) => setStatus({ email: u.email, emailVerified: u.emailVerified, phoneVerified: u.phoneVerified })).catch(() => {});
  };
  useEffect(load, []);

  if (!status || (status.emailVerified && status.phoneVerified)) return null;

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>Verify your account</h3>
      {!status.phoneVerified && <VerificationRow kind="phone" onVerified={load} />}
      {!status.emailVerified && <VerificationRow kind="email" hasEmail={status.email !== null} onVerified={load} />}
    </div>
  );
}

function VerificationRow({ kind, hasEmail = true, onVerified }: { kind: 'email' | 'phone'; hasEmail?: boolean; onVerified: () => void }) {
  const [sent, setSent] = useState(false);
  const [code, setCode] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSend = async () => {
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? requestEmailVerification() : requestPhoneVerification());
      setSent(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : `Could not send a ${kind} verification code.`);
    } finally {
      setBusy(false);
    }
  };

  const handleConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await (kind === 'email' ? confirmEmailVerification(code.trim()) : confirmPhoneVerification(code.trim()));
      onVerified();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Invalid or expired code.');
    } finally {
      setBusy(false);
    }
  };

  if (kind === 'email' && !hasEmail) {
    return <EmptyState message="No email address on file to verify." />;
  }

  return (
    <div style={{ padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
      {!sent ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '13px' }}>{kind === 'email' ? 'Email' : 'Phone number'} not verified</span>
          <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleSend} style={{ fontSize: '12px', padding: '6px 10px' }}>
            {busy ? '…' : 'Send code'}
          </button>
        </div>
      ) : (
        <form onSubmit={handleConfirm} style={{ display: 'flex', gap: '8px' }}>
          <input
            type="text" placeholder="Enter code" value={code} onChange={(e) => setCode(e.target.value)} required
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy} style={{ fontSize: '12px', padding: '8px 12px' }}>
            {busy ? '…' : 'Confirm'}
          </button>
        </form>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function MyVehiclesCard() {
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
      setError(err instanceof ApiError ? err.message : 'Could not register this vehicle.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update mileage.');
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
      setError(err instanceof ApiError ? err.message : 'Could not remove this vehicle.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '4px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>My vehicles</h3>
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowCreate((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
          {showCreate ? 'Cancel' : '+ Add'}
        </button>
      </div>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
        Estimated resale value based on age and mileage -- itunda's own general estimate, not a market comp.
      </p>

      {showCreate && (
        <form onSubmit={handleCreate} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Make (e.g. Toyota)" value={make} onChange={(e) => setMake(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="text" placeholder="Model (e.g. RAV4)" value={model} onChange={(e) => setModel(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" placeholder="Model year" value={modelYear} onChange={(e) => setModelYear(e.target.value)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" placeholder="Purchase price (RWF)" value={purchasePrice} onChange={(e) => setPurchasePrice(e.target.value)} min="1" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="date" value={purchaseDate} onChange={(e) => setPurchaseDate(e.target.value)} max={new Date().toISOString().slice(0, 10)} required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="number" placeholder="Current mileage (km)" value={mileageKm} onChange={(e) => setMileageKm(e.target.value)} min="0" required
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? 'Adding…' : 'Add vehicle'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {(vehicles ?? []).length === 0 && <EmptyState message="No vehicles added yet." />}

      {(vehicles ?? []).map((v) => {
        const valuation = valuations[v.id];
        return (
          <div key={v.id} style={{ padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{v.modelYear} {v.make} {v.model}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                  {v.mileageKm.toLocaleString()} km
                  {valuation && (
                    <> · {valuation.ageYears} {valuation.ageYears === 1 ? 'year' : 'years'} old · expected {valuation.expectedMileageKm.toLocaleString()} km</>
                  )}
                </p>
              </div>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="toss-btn toss-btn-secondary" disabled={busyId === v.id} onClick={() => handleUpdateMileage(v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                  Update km
                </button>
                <button className="toss-btn toss-btn-secondary" disabled={busyId === v.id} onClick={() => handleRemove(v.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                  Remove
                </button>
              </div>
            </div>
            {valuation && (
              <div style={{ marginTop: '8px', display: 'flex', gap: '12px', fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                <span>Now: <strong style={{ color: 'var(--toss-grey-900)' }}>{valuation.currentEstimatedValue.toLocaleString()} RWF</strong></span>
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
      setError(err instanceof ApiError ? err.message : 'Could not send this invitation.');
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
      setError(err instanceof ApiError ? err.message : 'Could not respond to this invitation.');
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
      setError(err instanceof ApiError ? err.message : 'Could not unlink this account.');
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
    try {
      setOverview(await fetchChildOverview(childUserId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not load this overview.');
    }
  };

  const hasAnything = (invites?.length ?? 0) > 0 || (children?.length ?? 0) > 0 || (guardians?.length ?? 0) > 0;

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Family</h3>
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowInvite((v) => !v)} style={{ fontSize: '12px', padding: '6px 10px' }}>
          {showInvite ? 'Cancel' : '+ Link a family member'}
        </button>
      </div>

      {showInvite && (
        <form onSubmit={handleInvite} style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
          <input
            type="text" placeholder="Phone number" value={childPhone} onChange={(e) => setChildPhone(e.target.value)} required
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}

      {(invites ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-500)', marginBottom: '6px' }}>Pending invitations</p>
          {(invites ?? []).map((inv) => (
            <div key={inv.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0' }}>
              <p style={{ fontSize: '13px' }}>Family link request</p>
              <div style={{ display: 'flex', gap: '6px' }}>
                <button className="toss-btn toss-btn-primary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, true)} style={{ fontSize: '12px', padding: '6px 10px' }}>Accept</button>
                <button className="toss-btn toss-btn-secondary" disabled={busyId === inv.id} onClick={() => handleRespond(inv.id, false)} style={{ fontSize: '12px', padding: '6px 10px' }}>Decline</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {(children ?? []).length > 0 && (
        <div style={{ marginBottom: '10px' }}>
          <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-500)', marginBottom: '6px' }}>Linked children</p>
          {(children ?? []).map((c) => (
            <div key={c.link.id} style={{ padding: '6px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{c.childName}</p>
                <div style={{ display: 'flex', gap: '6px' }}>
                  <button className="toss-btn toss-btn-secondary" onClick={() => handleToggleOverview(c.link.childUserId)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                    {openOverviewFor === c.link.childUserId ? 'Hide' : 'View'}
                  </button>
                  <button className="toss-btn toss-btn-secondary" disabled={busyId === c.link.id} onClick={() => handleRevoke(c.link.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                    Unlink
                  </button>
                </div>
              </div>
              {openOverviewFor === c.link.childUserId && overview && (
                <div style={{ marginTop: '6px', fontSize: '12px', color: 'var(--toss-grey-500)' }}>
                  <p>Balance: <strong style={{ color: 'var(--toss-grey-900)' }}>{overview.walletBalance.toLocaleString()} RWF</strong></p>
                  {overview.recentTransactions.slice(0, 5).map((t) => (
                    <p key={t.id}>{t.description} · {t.amount.toLocaleString()} RWF</p>
                  ))}
                  {overview.recentTransactions.length === 0 && <EmptyState message="No transactions yet." />}
                </div>
              )}
            </div>
          ))}
        </div>
      )}

      {(guardians ?? []).length > 0 && (
        <div>
          <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-500)', marginBottom: '6px' }}>Your guardians</p>
          {(guardians ?? []).map((g) => (
            <div key={g.link.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
              <p style={{ fontSize: '13px' }}>{g.guardianName}</p>
              <button className="toss-btn toss-btn-secondary" disabled={busyId === g.link.id} onClick={() => handleRevoke(g.link.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                Unlink
              </button>
            </div>
          ))}
        </div>
      )}

      {!hasAnything && <EmptyState message="No family members linked yet." />}
    </div>
  );
}

// Real Coupang 정기배송 (subscribe & save) -- see lib/productSubscriptions.ts's own doc
// comment for the full sourced account. A minimal delivery-address prompt rather than a
// full address form, matching this pass's compact-card scope.
function SubscribeAndSaveButton({ merchantId, productId }: { merchantId: string; productId: string }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not set up this subscription.');
    } finally {
      setBusy(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: '12px', color: 'var(--toss-blue)', textAlign: 'center' }}>Subscribed -- 5% off every delivery, every 30 days.</p>;
  }

  return (
    <div style={{ textAlign: 'center' }}>
      <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleSubscribe} style={{ fontSize: '12px', padding: '8px 14px' }}>
        {busy ? 'Setting up…' : 'Subscribe & save 5% (every 30 days)'}
      </button>
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

const PRODUCT_SUBSCRIPTION_STATUS_LABEL: Record<ProductSubscription['status'], string> = {
  ACTIVE: 'Active', PAUSED: 'Paused', CANCELLED: 'Cancelled',
};

// Real Coupang 정기배송-style subscription list -- see lib/productSubscriptions.ts's
// own doc comment.
function MyProductSubscriptionsCard() {
  const [subscriptions, setSubscriptions] = useState<ProductSubscription[] | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

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
      setError(err instanceof ApiError ? err.message : 'Could not update this subscription.');
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this subscription.');
    } finally {
      setBusyId(null);
    }
  };

  if (!subscriptions || subscriptions.length === 0) return null;

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>Subscribe & save</h3>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      {subscriptions.map((s) => (
        <div key={s.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
          <div>
            <p style={{ fontSize: '13px', fontWeight: 700 }}>Qty {s.quantity} · every {s.intervalDays}d</p>
            <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
              {s.status === 'CANCELLED' && s.cancelledAt
                ? `Cancelled ${new Date(s.cancelledAt).toLocaleDateString()}`
                : `${PRODUCT_SUBSCRIPTION_STATUS_LABEL[s.status]} · ${s.deliveryCount} delivered`}
            </p>
            {s.lastFailureReason && s.status === 'ACTIVE' && (
              <p style={{ fontSize: '11px', color: '#E53935' }}>Last delivery failed: {s.lastFailureReason}</p>
            )}
          </div>
          {s.status !== 'CANCELLED' && (
            <div style={{ display: 'flex', gap: '6px' }}>
              <button className="toss-btn toss-btn-secondary" disabled={busyId === s.id} onClick={() => handleToggle(s)} style={{ fontSize: '12px', padding: '6px 10px' }}>
                {busyId === s.id ? '…' : s.status === 'ACTIVE' ? 'Pause' : 'Resume'}
              </button>
              <button className="toss-btn toss-btn-secondary" disabled={busyId === s.id} onClick={() => handleCancel(s.id)} style={{ fontSize: '12px', padding: '6px 10px' }}>
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

  return (
    <div className="toss-card" style={{ padding: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Partner earnings</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
        Earn 3% on any purchase made through a product link you've shared.
      </p>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
        <span style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>Links shared</span>
        <span style={{ fontSize: '13px', fontWeight: 700 }}>{links.length}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
        <span style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>Total clicks</span>
        <span style={{ fontSize: '13px', fontWeight: 700 }}>{totalClicks}</span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', borderTop: '1px solid var(--toss-grey-100)' }}>
        <span style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>Total earned</span>
        <span style={{ fontSize: '13px', fontWeight: 700 }}>{totalEarned.toLocaleString()} RWF</span>
      </div>
    </div>
  );
}

// Real Face Pay enroll/revoke toggle -- see lib/facepay.ts's doc comment for the full
// account of the gap this closes (backend fully real since 2026-07-13, zero UI until now).
// `enrolled`/`onChanged` are lifted to ShoppingView -- found live that this card and
// PayByCodeCard each fetching their own status independently meant PayByCodeCard never
// learned about an enrollment that happened in the same session until a full reload.
function FacePaySettingsCard({ enrolled, onChanged }: { enrolled: boolean | null; onChanged: () => void }) {
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const handleToggle = async () => {
    setBusy(true);
    setError(null);
    try {
      if (enrolled) await revokeFacePay();
      else await enrollFacePay();
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update Face Pay.');
    } finally {
      setBusy(false);
    }
  };

  if (enrolled === null) return <div className="toss-card skeleton" style={{ height: '64px', marginBottom: '16px' }} />;

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: '14px', fontWeight: 700 }}>😊 Face Pay</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            {enrolled ? 'Enabled — authorize payment codes with your face, no code re-entry needed' : 'Not enabled on this account'}
          </p>
        </div>
        <button
          className={`toss-btn ${enrolled ? 'toss-btn-danger' : 'toss-btn-primary'}`}
          onClick={handleToggle}
          disabled={busy}
          style={{ padding: '8px 14px', fontSize: '12px' }}
        >
          {busy ? '…' : enrolled ? 'Disable' : 'Enable'}
        </button>
      </div>
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }} role="alert">{error}</p>}
    </div>
  );
}

function couponDiscountLabel(c: MerchantCouponView['coupon']) {
  return c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString()} RWF off`;
}

function PayByCodeCard({ onPaid, facePayEnrolled }: { onPaid: (result: CollectPaymentResult) => void; facePayEnrolled: boolean }) {
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

  const payDirect = async (couponId?: string) => {
    setNeedsDeviceVerification(false);
    setSubmitting(true);
    try {
      const result = facePayEnrolled ? await collectWithFacePay(code.trim()) : await collectPayment(code.trim(), couponId);
      onPaid(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not complete this payment.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (facePayEnrolled) {
      await payDirect();
      return;
    }
    setSubmitting(true);
    try {
      const r = await previewPaymentIntent(code.trim());
      const eligible = r.coupons.filter((c) => c.eligible && !c.alreadyRedeemed);
      if (eligible.length === 0) {
        await payDirect();
      } else {
        setPreview(r);
        setEligibleCoupons(eligible);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not look up this payment code.');
      setSubmitting(false);
    }
  };

  const handleConfirm = () => payDirect(selectedCouponId ?? undefined);

  const handleCancel = () => {
    setPreview(null);
    setEligibleCoupons([]);
    setSelectedCouponId(null);
    setError(null);
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Pay by code</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
        {facePayEnrolled
          ? 'Face Pay is on — enter the code the merchant shows you to authorize with your face.'
          : 'No scanner handy? Enter the payment code the merchant shows you to pay instantly and earn cashback.'}
      </p>
      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      ) : preview ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <p style={{ fontSize: '14px', fontWeight: 700 }}>{preview.businessName}</p>
          <p style={{ fontSize: '20px', fontWeight: 700 }}>{preview.amount.toLocaleString()} RWF</p>
          <p style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-grey-700)' }}>Apply a coupon?</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <label style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
              <input type="radio" name="coupon" checked={selectedCouponId === null} onChange={() => setSelectedCouponId(null)} />
              No coupon
            </label>
            {eligibleCoupons.map((c) => (
              <label key={c.coupon.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
                <input type="radio" name="coupon" checked={selectedCouponId === c.coupon.id} onChange={() => setSelectedCouponId(c.coupon.id)} />
                {c.coupon.title} — {couponDiscountLabel(c.coupon)}
              </label>
            ))}
          </div>
          {error && <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting} onClick={handleConfirm}>
              {submitting ? 'Paying…' : 'Pay'}
            </button>
            <button className="toss-btn toss-btn-secondary" onClick={handleCancel} disabled={submitting}>Cancel</button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
          <input
            type="text"
            value={code}
            onChange={(e) => setCode(e.target.value)}
            placeholder="Payment code"
            required
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
            {submitting ? (facePayEnrolled ? 'Authorizing…' : 'Paying…') : facePayEnrolled ? '😊 Pay' : 'Pay'}
          </button>
        </form>
      )}
      {error && !preview && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
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
  const [merchantId, setMerchantId] = useState('');
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    const numericAmount = Number(amount);
    if (!numericAmount || numericAmount <= 0) { setError('Enter a valid amount.'); return; }
    setSubmitting(true);
    try {
      const result = await payByStaticQr(merchantId.trim(), numericAmount);
      onPaid(result);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not complete this payment.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Pay a merchant's static QR</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
        For a merchant with one permanent code (like a market stall) -- enter their merchant ID and how much you're paying.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text" value={merchantId} onChange={(e) => setMerchantId(e.target.value)} placeholder="Merchant ID" required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <div style={{ display: 'flex', gap: '10px' }}>
          <input
            type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)" required
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>{submitting ? 'Paying…' : 'Pay'}</button>
        </div>
      </form>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>}
    </div>
  );
}

function PaymentConfirmation({ result, onDone }: { result: CollectPaymentResult; onDone: () => void }) {
  return (
    <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
      <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
      <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Paid {result.merchantName}</h3>
      <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{result.amount.toLocaleString()} RWF</p>
      {result.channel === 'FACE_PAY' && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>😊 Authorized with Face Pay</p>
      )}
      {result.cashbackEarned > 0 && (
        <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-green)', marginBottom: '16px' }}>
          +{result.cashbackEarned.toLocaleString()} RWF cashback earned
        </p>
      )}
      <button className="toss-btn toss-btn-secondary" onClick={onDone} style={{ marginTop: '8px' }}>Done</button>
    </div>
  );
}

function ShoppingView() {
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [paymentResult, setPaymentResult] = useState<CollectPaymentResult | null>(null);
  const [facePayEnrolled, setFacePayEnrolled] = useState<boolean | null>(null);
  // Real Naver Pay 멤버십 데이 (Membership Day) boost -- see lib/shopping.ts's own
  // fetchMembershipDayStatus doc comment.
  const [membershipDay, setMembershipDay] = useState<{ isMembershipDay: boolean; multiplier: number } | null>(null);

  const load = () => {
    setError(null);
    fetchShoppingCatalog()
      .then(setMerchants)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the shopping catalog.'));
  };
  const loadFacePayStatus = () => {
    fetchFacePayStatus().then((r) => setFacePayEnrolled(r.enrolled)).catch(() => setFacePayEnrolled(false));
  };
  const loadMembershipDayStatus = () => {
    fetchMembershipDayStatus().then(setMembershipDay).catch(() => {});
  };

  useEffect(load, []);
  useEffect(loadFacePayStatus, []);
  useEffect(loadMembershipDayStatus, []);

  if (paymentResult) {
    return <PaymentConfirmation result={paymentResult} onDone={() => setPaymentResult(null)} />;
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (merchants === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      {membershipDay?.isMembershipDay && (
        <div className="toss-card" style={{ marginBottom: '16px', backgroundColor: 'var(--toss-blue-light)', border: '1px solid var(--toss-blue)' }}>
          <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-blue)' }}>🎉 Membership Day -- {membershipDay.multiplier}x cashback today</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>Every purchase you make today earns {membershipDay.multiplier}x the usual cashback.</p>
        </div>
      )}
      <FacePaySettingsCard enrolled={facePayEnrolled} onChanged={loadFacePayStatus} />
      <PayByCodeCard onPaid={setPaymentResult} facePayEnrolled={facePayEnrolled ?? false} />
      <PayByStaticQrCard onPaid={setPaymentResult} />
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px', padding: '0 4px' }}>
        Earn cashback every time you shop with Itunda merchants.
      </p>
      {merchants.length === 0 ? (
        <div className="toss-card">
          <EmptyState message="No merchants registered yet." />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {merchants.map((m) => (
            <div key={m.merchantId} className="toss-card" style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px' }}>
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                <ShoppingBag size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{m.businessName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Pay by QR or code to earn cashback</p>
              </div>
              <span style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-green)' }}>{m.cashbackRate} back</span>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Lightweight dependency-free bar sparkline -- no charting library exists anywhere in
// this app yet, and pulling one in just for this would be disproportionate to a real
// MVP chart. Real values, real relative scaling, just rendered as flexbox bars instead
// of an SVG line chart.
function Sparkline({ values, positive }: { values: number[]; positive: boolean }) {
  if (values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const range = max - min || 1;
  return (
    <div style={{ display: 'flex', alignItems: 'flex-end', gap: '2px', height: '48px' }}>
      {values.map((v, i) => (
        <div
          key={i}
          style={{
            flex: 1,
            height: `${Math.max(8, ((v - min) / range) * 100)}%`,
            backgroundColor: positive ? 'var(--toss-green)' : '#E53935',
            borderRadius: '2px',
            opacity: 0.3 + (0.7 * i) / values.length,
          }}
        />
      ))}
    </div>
  );
}

function StockDetailSheet({ stock, isWatched, onClose, onTraded, onWatchToggled }: {
  stock: Stock;
  isWatched: boolean;
  onClose: () => void;
  onTraded: () => void;
  onWatchToggled: () => void;
}) {
  const [history, setHistory] = useState<PricePoint[] | null>(null);
  const [shares, setShares] = useState('');
  const [mode, setMode] = useState<'BUY' | 'SELL'>('BUY');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [watching, setWatching] = useState(isWatched);
  const [watchBusy, setWatchBusy] = useState(false);
  // Real device binding step-up (2026-07-21) -- Stocks buy/sell was a real gap:
  // already correctly enforced server-side (a real 403 DEVICE_NOT_VERIFIED) but
  // showed only a generic error, same fix already applied to Transfer/Savings/Group
  // Account above.
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  useEffect(() => {
    fetchStockHistory(stock.id, 14).then(setHistory).catch(() => setHistory([]));
  }, [stock.id]);

  const handleTrade = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setNeedsDeviceVerification(false);
    const shareCount = Number(shares);
    if (!shareCount || shareCount <= 0) {
      setError('Enter a real number of shares.');
      return;
    }
    setSubmitting(true);
    try {
      if (mode === 'BUY') await buyStock(stock.id, shareCount);
      else await sellStock(stock.id, shareCount);
      setShares('');
      onTraded();
      onClose();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : `Could not ${mode === 'BUY' ? 'buy' : 'sell'} this stock.`);
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleToggleWatch = async () => {
    setWatchBusy(true);
    try {
      if (watching) {
        await unwatchStock(stock.id);
        setWatching(false);
      } else {
        await watchStock(stock.id);
        setWatching(true);
      }
      onWatchToggled();
    } catch {
      // Non-critical -- the star just doesn't flip, no error surfaced for a real
      // watch/unwatch toggle failure.
    } finally {
      setWatchBusy(false);
    }
  };

  const positive = stock.changePercent >= 0;

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '4px' }}>
        <button onClick={onClose} style={{ color: 'var(--toss-grey-500)', display: 'flex' }} aria-label="Back">
          <ArrowLeft size={18} />
        </button>
        <button onClick={handleToggleWatch} disabled={watchBusy} style={{ color: watching ? '#FFC107' : 'var(--toss-grey-300)', display: 'flex' }} aria-label="Toggle watch">
          <Star size={20} fill={watching ? '#FFC107' : 'none'} />
        </button>
      </div>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', fontWeight: 600 }}>{stock.symbol} · {stock.marketCap}</p>
      <h3 style={{ fontSize: '18px', fontWeight: 700, marginBottom: '6px' }}>{stock.name}</h3>
      <p style={{ fontSize: '26px', fontWeight: 700, marginBottom: '4px' }}>{stock.price.toLocaleString()} RWF</p>
      <p style={{ fontSize: '14px', fontWeight: 700, color: positive ? 'var(--toss-green)' : '#E53935', display: 'flex', alignItems: 'center', gap: '4px', marginBottom: '16px' }}>
        {positive ? <TrendingUp size={16} /> : <TrendingDown size={16} />}
        {positive ? '+' : ''}{stock.change.toLocaleString()} ({positive ? '+' : ''}{stock.changePercent.toFixed(2)}%) today
      </p>

      {history === null ? (
        <div className="skeleton" style={{ height: '48px', borderRadius: '8px', marginBottom: '16px' }} />
      ) : history.length > 0 ? (
        <div style={{ marginBottom: '16px' }}>
          <Sparkline values={history.map((h) => h.price)} positive={positive} />
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>Last 14 days -- real deterministic simulation, not live RSE data</p>
        </div>
      ) : null}

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BUY', 'SELL'] as const).map((m) => (
          <button
            key={m}
            onClick={() => setMode(m)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === m ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === m ? (m === 'BUY' ? 'var(--toss-blue)' : '#E53935') : 'transparent',
            }}
          >
            {m === 'BUY' ? 'Buy' : 'Sell'}
          </button>
        ))}
      </div>
      <form onSubmit={handleTrade} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" min="0.0001" step="any" value={shares} onChange={(e) => setShares(e.target.value)}
          placeholder="Shares" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className={mode === 'BUY' ? 'toss-btn toss-btn-primary' : 'toss-btn'} style={mode === 'SELL' ? { backgroundColor: '#E53935', color: 'white' } : undefined} disabled={submitting}>
          {submitting ? 'Working…' : mode === 'BUY' ? 'Buy' : 'Sell'}
        </button>
      </form>
      {needsDeviceVerification ? (
        <div style={{ marginTop: '10px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

// Real Investment-wallet top-up (2026-08-04) -- see lib/stocks.ts's own
// fundInvestmentWallet doc comment. Without this, a user with no pre-seeded
// investment balance had no in-app way to ever actually buy a stock.
function AddFundsCard({ onFunded }: { onFunded: () => void }) {
  const [expanded, setExpanded] = useState(false);
  const [amount, setAmount] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const handleFund = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setNeedsDeviceVerification(false);
    const value = Number(amount);
    if (!value || value <= 0) {
      setError('Enter a real amount.');
      return;
    }
    setBusy(true);
    try {
      await fundInvestmentWallet(value);
      setAmount('');
      setExpanded(false);
      onFunded();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not add funds.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '15px', fontWeight: 700 }}>Investment cash</p>
        <button onClick={() => { setExpanded(!expanded); setError(null); }} style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>
          {expanded ? 'Cancel' : 'Add funds'}
        </button>
      </div>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Move money from your main wallet into your investment account.</p>
      {expanded && (
        <form onSubmit={handleFund} style={{ display: 'flex', gap: '10px', marginTop: '10px' }}>
          <input
            type="number" min="1" step="any" value={amount} onChange={(e) => setAmount(e.target.value)}
            placeholder="Amount (RWF)" required
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>
            {busy ? 'Working…' : 'Add'}
          </button>
        </form>
      )}
      {needsDeviceVerification ? (
        <div style={{ marginTop: '10px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function StocksView() {
  const [subTab, setSubTab] = useState<'MARKET' | 'PORTFOLIO' | 'WATCHLIST'>('MARKET');
  // Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- a market filter on
  // the existing Market browse, distinguishing the original 6 real RSE-domestic
  // symbols from the real US-listed names added 2026-08-01. See lib/stocks.ts's own
  // doc comment for the full sourced account.
  const [marketFilter, setMarketFilter] = useState<'ALL' | 'RSE' | 'NASDAQ'>('ALL');
  const [stocks, setStocks] = useState<Stock[] | null>(null);
  const [portfolio, setPortfolio] = useState<Portfolio | null>(null);
  const [portfolioHistory, setPortfolioHistory] = useState<PortfolioValuePoint[] | null>(null);
  const [watchlist, setWatchlist] = useState<Stock[] | null>(null);
  const [selectedStock, setSelectedStock] = useState<Stock | null>(null);
  const [error, setError] = useState<string | null>(null);

  const loadMarket = () => {
    setError(null);
    fetchStocks().then(setStocks).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load the real market.'));
  };
  const loadPortfolio = () => {
    setError(null);
    Promise.all([fetchPortfolio(), fetchPortfolioHistory(30)])
      .then(([p, h]) => { setPortfolio(p); setPortfolioHistory(h); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your real portfolio.'));
  };
  const loadWatchlist = () => {
    setError(null);
    fetchWatchlist().then(setWatchlist).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your real watchlist.'));
  };

  useEffect(() => {
    if (subTab === 'MARKET') loadMarket();
    else if (subTab === 'PORTFOLIO') loadPortfolio();
    else loadWatchlist();
    setSelectedStock(null);
  }, [subTab]);

  const watchedIds = new Set((watchlist ?? []).map((s) => s.id));

  const renderStockRow = (stock: Stock) => {
    const positive = stock.changePercent >= 0;
    return (
      <div
        key={stock.id}
        onClick={() => setSelectedStock(stock)}
        className="toss-card"
        style={{ display: 'flex', alignItems: 'center', gap: '14px', padding: '16px 18px', cursor: 'pointer' }}
      >
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
            {stock.symbol}
            <span style={{ fontSize: '10px', fontWeight: 700, color: 'var(--toss-grey-500)', marginLeft: '6px' }}>{stock.market}</span>
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{stock.name}</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: '15px', fontWeight: 700 }}>{stock.price.toLocaleString()} RWF</p>
          <p style={{ fontSize: '12px', fontWeight: 700, color: positive ? 'var(--toss-green)' : '#E53935', display: 'flex', alignItems: 'center', gap: '2px', justifyContent: 'flex-end' }}>
            {positive ? <TrendingUp size={12} /> : <TrendingDown size={12} />}
            {positive ? '+' : ''}{stock.changePercent.toFixed(2)}%
          </p>
        </div>
      </div>
    );
  };

  if (selectedStock) {
    return (
      <StockDetailSheet
        stock={selectedStock}
        isWatched={watchedIds.has(selectedStock.id)}
        onClose={() => setSelectedStock(null)}
        onTraded={() => { loadPortfolio(); if (subTab === 'MARKET') loadMarket(); }}
        onWatchToggled={loadWatchlist}
      />
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {([{ id: 'MARKET', label: 'Market' }, { id: 'PORTFOLIO', label: 'Portfolio' }, { id: 'WATCHLIST', label: 'Watchlist' }] as const).map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setSubTab(id)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: subTab === id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: subTab === id ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {error && (
        <ErrorCard message={error} onRetry={subTab === 'MARKET' ? loadMarket : subTab === 'PORTFOLIO' ? loadPortfolio : loadWatchlist} />
      )}

      {subTab === 'MARKET' && (
        stocks === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', gap: '6px', marginBottom: '4px' }}>
              {([{ id: 'ALL', label: 'All' }, { id: 'RSE', label: 'Rwanda (RSE)' }, { id: 'NASDAQ', label: 'Overseas' }] as const).map(({ id, label }) => (
                <button
                  key={id}
                  onClick={() => setMarketFilter(id)}
                  style={{
                    padding: '6px 12px', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
                    color: marketFilter === id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                    backgroundColor: marketFilter === id ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                  }}
                >
                  {label}
                </button>
              ))}
            </div>
            {stocks.filter((s) => marketFilter === 'ALL' || s.market === marketFilter).map(renderStockRow)}
          </div>
        )
      )}

      {subTab === 'PORTFOLIO' && (
        portfolio === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : (
          <div>
            <div className="toss-card" style={{ marginBottom: '16px' }}>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', fontWeight: 600 }}>Total value</p>
              <p style={{ fontSize: '26px', fontWeight: 700, marginBottom: '4px' }}>{portfolio.totalValue.toLocaleString()} RWF</p>
              <p style={{ fontSize: '14px', fontWeight: 700, color: portfolio.totalReturn >= 0 ? 'var(--toss-green)' : '#E53935', marginBottom: '12px' }}>
                {portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturn.toLocaleString()} RWF ({portfolio.totalReturn >= 0 ? '+' : ''}{portfolio.totalReturnPercent.toFixed(2)}%)
              </p>
              {portfolioHistory && portfolioHistory.length > 0 && (
                <div>
                  <Sparkline values={portfolioHistory.map((h) => h.value)} positive={portfolio.totalReturn >= 0} />
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>
                    Last 30 days -- based on your current holdings applied to real historical prices, not a full historical reconstruction
                  </p>
                </div>
              )}
            </div>
            <AddFundsCard onFunded={loadPortfolio} />
            {portfolio.holdings.length === 0 ? (
              <div className="toss-card">
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You don't hold any real shares yet. Browse the Market tab to buy some.</p>
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {portfolio.holdings.map((h) => (
                  <div key={h.stockId} className="toss-card" style={{ padding: '16px 18px' }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: '15px', fontWeight: 700 }}>{h.symbol}</p>
                      <p style={{ fontSize: '15px', fontWeight: 700 }}>{h.value.toLocaleString()} RWF</p>
                    </div>
                    <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{h.shares} shares @ {h.avgPrice.toLocaleString()} avg</p>
                      <p style={{ fontSize: '12px', fontWeight: 700, color: h.return >= 0 ? 'var(--toss-green)' : '#E53935' }}>
                        {h.return >= 0 ? '+' : ''}{h.return.toFixed(2)}%
                      </p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )
      )}

      {subTab === 'WATCHLIST' && (
        watchlist === null ? <div className="toss-card skeleton" style={{ height: '220px' }} /> : watchlist.length === 0 ? (
          <div className="toss-card">
            <EmptyState message="No stocks watched yet. Tap the star on any stock in the Market tab to follow it." />
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {watchlist.map(renderStockRow)}
          </div>
        )
      )}
    </div>
  );
}

function NewChatCard({ onStarted }: { onStarted: (conversationId: string) => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not start this chat.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>New chat</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '14px' }}>
        Start from an Itunda contact, or enter their phone number.
      </p>
      {contacts && contacts.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '12px' }}>
          {contacts.map((contact) => <button key={contact.userId} type="button" className="toss-btn toss-btn-secondary" onClick={async () => { setSubmitting(true); try { const c = await startConversationWithUser(contact.userId); onStarted(c.id); } catch { setError('Could not start this chat.'); } finally { setSubmitting(false); } }} disabled={submitting}>{contact.name}</button>)}
        </div>
      )}
      <form onSubmit={handleSubmit} style={{ display: 'flex', gap: '10px' }}>
        <input
          type="tel"
          value={phoneNumber}
          onChange={(e) => setPhoneNumber(e.target.value)}
          placeholder="+250788123456"
          required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Starting…' : 'Chat'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginTop: '10px' }} role="alert">{error}</p>
      )}
    </div>
  );
}

function NewGroupCard({ onCreated }: { onCreated: (groupId: string) => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not create this group.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>New group</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        Name your group and add real members by phone number, separated by commas.
      </p>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <input
          type="text"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="Group name"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text"
          value={phoneNumbers}
          onChange={(e) => setPhoneNumbers(e.target.value)}
          placeholder="+250788123456, +250788654321"
          required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
          {submitting ? 'Creating…' : 'Create group'}
        </button>
      </form>
      {error && (
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
      )}
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
  return (
    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px', marginTop: '4px', justifyContent: isMine ? 'flex-end' : 'flex-start' }}>
      {reactions.filter((r) => r.userIds.length > 0).map((r) => {
        const mine = !!currentUserId && r.userIds.includes(currentUserId);
        return (
          <button
            key={r.emoji}
            onClick={() => onToggle(r.emoji)}
            style={{
              display: 'flex', alignItems: 'center', gap: '4px', padding: '2px 8px', borderRadius: '12px', fontSize: '12px',
              border: mine ? '1px solid var(--toss-blue)' : '1px solid var(--toss-grey-200)',
              backgroundColor: mine ? 'var(--toss-blue-light)' : 'var(--toss-white)',
            }}
          >
            <span>{r.emoji}</span>
            <span style={{ color: 'var(--toss-grey-700)' }}>{r.userIds.length}</span>
          </button>
        );
      })}
      <div style={{ position: 'relative' }}>
        <button
          onClick={() => setPickerOpen((v) => !v)}
          aria-label="Add reaction"
          style={{ display: 'flex', padding: '2px 6px', borderRadius: '12px', border: '1px solid var(--toss-grey-200)', color: 'var(--toss-grey-500)' }}
        >
          <SmilePlus size={14} />
        </button>
        {pickerOpen && (
          <div
            style={{
              position: 'absolute', bottom: '28px', display: 'flex', gap: '4px', padding: '6px 8px',
              borderRadius: '12px', backgroundColor: 'var(--toss-white)', boxShadow: '0 2px 8px rgba(0,0,0,0.15)', zIndex: 10,
              left: isMine ? undefined : 0, right: isMine ? 0 : undefined,
            }}
          >
            {QUICK_REACTIONS.map((emoji) => (
              <button
                key={emoji}
                onClick={() => { onToggle(emoji); setPickerOpen(false); }}
                style={{ fontSize: '18px', padding: '2px' }}
              >
                {emoji}
              </button>
            ))}
          </div>
        )}
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
        maxWidth: '75%', padding: '12px 14px', borderRadius: '16px', fontSize: '14px',
        backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
        color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700 }}>💰 {offer.amount.toLocaleString()} RWF</p>
      <p style={{ fontSize: '12px', opacity: 0.8 }}>{statusLabel[offer.status]}</p>
      {canRespond && !countering && (
        <div style={{ display: 'flex', gap: '6px' }}>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'ACCEPT')}>
            Accept
          </button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => onRespond(offer.id, 'REJECT')}>
            Decline
          </button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 10px' }} onClick={() => setCountering(true)}>
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
            style={{ flex: 1, padding: '6px 8px', borderRadius: '8px', border: '1px solid var(--toss-grey-300)', fontSize: '12px' }}
          />
          <button
            className="toss-btn toss-btn-secondary"
            style={{ fontSize: '12px', padding: '6px 10px' }}
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
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: '14px',
        backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
        color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: '16px' }}>
        {gift.theme ? GIFT_THEME_LABELS[gift.theme] : '🎁'} {gift.amount.toLocaleString()} RWF
      </p>
      {gift.note && <p style={{ fontStyle: 'italic', opacity: 0.9 }}>&ldquo;{gift.note}&rdquo;</p>}
      <p style={{ fontSize: '12px', opacity: 0.8 }}>{statusLabel[gift.status]}</p>
      {canClaim && (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ fontSize: '12px', padding: '6px 10px', alignSelf: 'flex-start' }}
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
        maxWidth: '75%', padding: '14px 16px', borderRadius: '16px', fontSize: '14px',
        backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
        color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
        display: 'flex', flexDirection: 'column', gap: '6px',
      }}
    >
      <p style={{ fontWeight: 700, fontSize: '15px' }}>🎟️ {voucher.productNameSnapshot ?? `${voucher.amount.toLocaleString()} RWF voucher`}</p>
      <p style={{ fontSize: '12px', opacity: 0.8 }}>{statusLabel[voucher.status]}</p>
      {voucher.status === 'ACTIVE' && (
        <p style={{ fontSize: '11px', opacity: 0.7 }}>Expires {new Date(voucher.expiresAt).toLocaleDateString()}</p>
      )}
      {canExtend && (
        <button
          className="toss-btn toss-btn-secondary"
          style={{ fontSize: '12px', padding: '6px 10px', alignSelf: 'flex-start' }}
          disabled={extending}
          onClick={async () => {
            setExtending(true);
            try {
              await extendGiftVoucherExpiry(voucher.id);
              onExtend(voucher.id);
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
    return <div style={{ fontSize: '13px', color: 'var(--toss-grey-500)', fontStyle: 'italic' }}>[emoticon]</div>;
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
    <div style={{ padding: '10px', borderRadius: '12px', border: '1px solid var(--toss-grey-200)', marginBottom: '10px' }}>
      {ownedPacks === null ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
      ) : ownedPacks.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '16px' }}>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '8px' }}>You don't own any emoticon packs yet.</p>
          <button type="button" className="toss-btn toss-btn-primary" onClick={onOpenStore} style={{ padding: '8px 14px', fontSize: '13px' }}>
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
                className={selectedPackId === op.packId ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                style={{ padding: '6px 10px', fontSize: '12px', whiteSpace: 'nowrap' }}
              >
                {packTitles[op.packId] ?? op.packId}
              </button>
            ))}
            <button type="button" onClick={onOpenStore} className="toss-btn toss-btn-secondary" style={{ padding: '6px 10px', fontSize: '12px', whiteSpace: 'nowrap' }}>
              Get more
            </button>
          </div>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
            {packEmoticons === null ? (
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Loading…</p>
            ) : (
              packEmoticons.map((e) => (
                <button
                  key={e.id}
                  type="button"
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
      setError(err instanceof ApiError ? err.message : 'Could not purchase this pack.');
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
      setError(err instanceof ApiError ? err.message : 'Could not gift this pack.');
    } finally {
      setBusyPackId(null);
    }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 100 }}>
      <div className="toss-card" style={{ width: '90%', maxWidth: '420px', maxHeight: '80vh', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <p style={{ fontSize: '16px', fontWeight: 700 }}>🛍 Emoticon Store</p>
          <button type="button" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: '16px' }}>×</button>
        </div>
        {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
        {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)' }}>{message}</p>}
        {packs === null ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
        ) : (
          packs.map((pack) => {
            const owned = ownedPackIds.has(pack.id);
            return (
              <div key={pack.id} style={{ display: 'flex', flexDirection: 'column', gap: '6px', padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)' }}>
                <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                  <img src={pack.thumbnailUrl} alt="" style={{ width: '48px', height: '48px', objectFit: 'contain' }} />
                  <div style={{ flex: 1 }}>
                    <p style={{ fontSize: '14px', fontWeight: 600 }}>{pack.title}</p>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{pack.artistName} · {pack.price.toLocaleString()} RWF</p>
                  </div>
                  <button
                    type="button"
                    className={owned ? 'toss-btn toss-btn-secondary' : 'toss-btn toss-btn-primary'}
                    disabled={owned || busyPackId === pack.id}
                    onClick={() => buy(pack.id)}
                    style={{ padding: '8px 12px', fontSize: '12px' }}
                  >
                    {owned ? 'Owned' : busyPackId === pack.id ? '…' : 'Buy'}
                  </button>
                  <button
                    type="button"
                    className="toss-btn toss-btn-secondary"
                    disabled={busyPackId === pack.id}
                    onClick={() => setGiftingPackId(giftingPackId === pack.id ? null : pack.id)}
                    style={{ padding: '8px 12px', fontSize: '12px' }}
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
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
                    />
                    <button
                      type="button"
                      className="toss-btn toss-btn-primary"
                      disabled={busyPackId === pack.id || !giftPhone.trim()}
                      onClick={() => gift(pack.id)}
                      style={{ padding: '8px 12px', fontSize: '12px' }}
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
    </div>
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
      setError(err instanceof ApiError ? err.message : 'Could not search products.');
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
      setError(err instanceof ApiError ? err.message : 'Could not send this gift voucher.');
    } finally {
      setSending(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px', borderRadius: '12px', border: '1px solid var(--toss-grey-200)', marginBottom: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>🎟️ Send a gift voucher</p>
      <input
        type="tel"
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        placeholder="Recipient phone number"
        style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      {selected ? (
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 10px', background: 'var(--toss-grey-100)', borderRadius: '8px' }}>
          <span style={{ fontSize: '13px' }}>{selected.name} · {selected.merchantName} · {selected.price.toLocaleString()} RWF</span>
          <button type="button" onClick={() => setSelected(null)} style={{ border: 'none', background: 'none', fontSize: '12px', color: 'var(--toss-blue)' }}>Change</button>
        </div>
      ) : (
        <>
          <form onSubmit={search} style={{ display: 'flex', gap: '8px' }}>
            <input
              type="text"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Search a product to gift"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            <button type="submit" className="toss-btn toss-btn-secondary" disabled={searching || query.trim().length < 2} style={{ padding: '10px 14px', fontSize: '13px' }}>
              {searching ? '…' : 'Search'}
            </button>
          </form>
          {results !== null && (
            results.length === 0 ? (
              <EmptyState message="No products found." />
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', maxHeight: '160px', overflowY: 'auto' }}>
                {results.map((p) => (
                  <button
                    key={p.id}
                    type="button"
                    onClick={() => setSelected(p)}
                    style={{ display: 'flex', justifyContent: 'space-between', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', background: 'none', fontSize: '13px', textAlign: 'left' }}
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
      {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          type="button"
          className="toss-btn toss-btn-primary"
          disabled={!selected || !phone.trim() || sending}
          onClick={send}
          style={{ flex: 1, padding: '10px' }}
        >
          {sending ? '…' : 'Send gift voucher'}
        </button>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ padding: '10px 16px' }} onClick={onCancel}>
          Cancel
        </button>
      </div>
    </div>
  );
}

function ConversationThread({ conversation, onBack }: { conversation: ConversationSummary; onBack: () => void }) {
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
  // Real attach ("+") menu + photo send/gallery -- see GroupThread's own identical
  // doc comment.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
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
  const [otherOnline, setOtherOnline] = useState<boolean | null>(null);
  const [otherTyping, setOtherTyping] = useState(false);
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this conversation.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not block this person.');
    } finally { setBlocking(false); }
  };

  const handleUnblock = async () => {
    setBlocking(true);
    try {
      await unblockConversationParticipant(conversation.conversationId);
      setBlocked(false);
      setError(`You unblocked ${conversation.otherUserName}.`);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not unblock this person.');
    } finally { setBlocking(false); }
  };

  const handleQuiet = async () => {
    setUpdatingQuiet(true);
    try {
      setQuiet(await setConversationQuiet(conversation.conversationId, !quiet));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this quiet room.');
    } finally { setUpdatingQuiet(false); }
  };

  const handlePin = async (message: Message) => {
    setUpdatingPin(true);
    try {
      await pinConversationMessage(conversation.conversationId, message.id);
      setPinnedMessage(message);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not pin this message.');
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinConversationMessage(conversation.conversationId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not unpin this message.');
    } finally { setUpdatingPin(false); }
  };

  const handleReport = async (messageId: string) => {
    const reason = window.prompt('Why are you reporting this message? (3–180 characters)');
    if (!reason) return;
    try {
      await reportChatMessage(messageId, reason);
      setError('Thanks. Your report was sent for review.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this report.');
    }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try {
      await deleteMessage(conversation.conversationId, messageId);
      setMessages((prev) => prev?.map((m) => m.id === messageId ? { ...m, body: 'This message was deleted', deletedAt: new Date().toISOString(), reactions: [] } : m) ?? prev);
    } catch (err) { setError(err instanceof ApiError ? err.message : 'Could not delete this message.'); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not forward this message.');
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
    catch (err) { setError(err instanceof ApiError ? err.message : 'Could not search this conversation.'); }
    finally { setSearching(false); }
  };

  const handleSendGift = async (e: React.FormEvent) => {
    e.preventDefault();
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
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not send this gift.');
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
        setNeedsDeviceVerification(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not open this gift.');
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
      setError(err instanceof ApiError ? err.message : 'Could not send this emoticon.');
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
      setError(err instanceof ApiError ? err.message : "Couldn't upload that photo. Check your connection and try again.");
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
      setError(err instanceof ApiError ? err.message : 'Could not respond to this offer.');
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
      setError(err instanceof ApiError ? err.message : 'Could not send this message.');
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '12px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to conversations">
          <ArrowLeft size={20} />
        </button>
        <div>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{conversation.otherUserName}</h3>
          {otherOnline !== null && (
            <p style={{ fontSize: '12px', color: otherOnline ? 'var(--toss-green)' : 'var(--toss-grey-500)' }}>
              {otherOnline ? 'Online' : 'Offline'}
            </p>
          )}
        </div>
        <button type="button" onClick={() => setShowMediaGallery(true)} style={{ display: 'flex', color: 'var(--toss-grey-700)', marginLeft: 'auto' }} aria-label="Shared photos">
          <ImageIcon size={20} />
        </button>
        <button
          type="button"
          className="toss-btn toss-btn-secondary"
          onClick={blocked ? handleUnblock : handleBlock}
          disabled={blocking}
          style={{ padding: '8px 10px', fontSize: '12px' }}
        >
          {blocking ? (blocked ? 'Unblocking…' : 'Blocking…') : blocked ? 'Unblock' : 'Block'}
        </button>
        <button type="button" className="toss-btn toss-btn-secondary" onClick={handleQuiet} disabled={updatingQuiet} style={{ padding: '8px 10px', fontSize: '12px' }}>
          {updatingQuiet ? '…' : quiet ? 'Resume alerts' : 'Quiet room'}
        </button>
      </div>

      {showMediaGallery && (
        <MediaGalleryModal
          imageUrls={(messages ?? []).map((m) => m.imageUrl).filter((u): u is string => !!u).reverse()}
          onClose={() => setShowMediaGallery(false)}
        />
      )}

      <form onSubmit={handleSearch} style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
        <input value={searchQuery} onChange={(e) => setSearchQuery(e.target.value)} placeholder="Search this conversation" minLength={2} style={{ flex: 1, padding: '9px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)' }} />
        <button type="submit" className="toss-btn toss-btn-secondary" disabled={searching || searchQuery.trim().length < 2}>{searching ? '…' : 'Search'}</button>
        {searchResults !== null && <button type="button" className="toss-btn toss-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>Clear</button>}
      </form>
      {searchResults !== null && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '6px' }}>{searchResults.length} matching message{searchResults.length === 1 ? '' : 's'}</p>}

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--toss-grey-100)', fontSize: '12px' }}>
          <span aria-hidden="true">📌</span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-600)', fontSize: '12px' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px' }}>
        {messages === null && <div className="toss-card skeleton" style={{ height: '120px' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {(searchResults ?? messages)?.map((m) => {
          const isMine = m.senderId === currentUser?.id;
          const offer = offersByMessageId[m.id];
          const gift = giftsByMessageId[m.id];
          const voucher = vouchersByMessageId[m.id];
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {/* Real message forwarding (2026-07-25) -- a genuine provenance label,
                  only ever set on a message actually created via the forward
                  endpoint, see lib/messaging.ts's own doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--toss-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
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
                    fontSize: '14px',
                    backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                    color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                  }}
                >
                  {m.body}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              <span style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>
                {isMine && !m.readAt ? '1 · ' : ''}{chatMessageTime(m.sentAt)}
              </span>
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Reply</button>
              <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Copy</button>
              {!m.deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Forward</button>}
              {isMine && !m.deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              {!isMine && (
                <button type="button" onClick={() => handleReport(m.id)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>
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
                  style={{ border: 'none', background: 'none', color: 'var(--toss-blue)', fontSize: '11px', fontWeight: 600, padding: '4px 0' }}
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

      {otherTyping && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {conversation.otherUserName} is typing…
        </p>
      )}

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '8px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        error && (
          <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>
        )
      )}

      {giftComposerOpen && (
        <form
          onSubmit={handleSendGift}
          style={{
            display: 'flex', flexDirection: 'column', gap: '8px', padding: '12px',
            borderRadius: '12px', border: '1px solid var(--toss-grey-200)', marginBottom: '10px',
          }}
        >
          <p style={{ fontSize: '13px', fontWeight: 700 }}>🎁 Send a gift</p>
          <input
            type="number"
            value={giftAmount}
            onChange={(e) => setGiftAmount(e.target.value)}
            placeholder="Amount (RWF)"
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <input
            type="text"
            value={giftNote}
            onChange={(e) => setGiftNote(e.target.value)}
            placeholder="Add a note (optional)"
            maxLength={200}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <select
            value={giftTheme}
            onChange={(e) => setGiftTheme(e.target.value as GiftTheme | '')}
            style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          >
            <option value="">No theme (plain gift)</option>
            {(Object.keys(GIFT_THEME_LABELS) as GiftTheme[]).map((t) => (
              <option key={t} value={t}>{GIFT_THEME_LABELS[t]}</option>
            ))}
          </select>
          <div style={{ display: 'flex', gap: '8px' }}>
            <button
              type="submit"
              className="toss-btn toss-btn-primary"
              disabled={sendingGift || !giftAmount || Number(giftAmount) <= 0}
              style={{ flex: 1, padding: '10px' }}
            >
              Send gift
            </button>
            <button
              type="button"
              className="toss-btn toss-btn-secondary"
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

      {replyingTo && <div style={{ fontSize: '12px', color: 'var(--toss-grey-600)', padding: '8px', borderLeft: '3px solid var(--toss-blue)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" onClick={() => setReplyingTo(null)}>×</button></div>}
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            consolidates what used to be 3 separate always-visible icons
            (gift/emoticon/gift-voucher), plus real photo send, matching Kakao's own
            real "+"-opens-a-menu pattern. */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '18px', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--toss-white)', border: '1px solid var(--toss-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
              📷 Photo
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
              😊 Emoticon
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setGiftComposerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
              🎁 Gift
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setVoucherComposerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
              🎟️ Gift voucher
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
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <Send size={16} />
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
              whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
              color: 'var(--toss-white)', backgroundColor: 'var(--toss-blue)',
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
  // Real split-bill/manage-members (found 2026-07-22 fully built on the backend with
  // zero UI anywhere) -- toggles a sibling view over this same thread.
  const [showSplitBills, setShowSplitBills] = useState(false);
  const [showManageMembers, setShowManageMembers] = useState(false);
  // Real attach ("+") menu + photo send/gallery -- ports Android TalkScreen.kt's own
  // identical addition (2026-08-04) to bank-mfe. Reuses lib/upload.ts's own uploadFile,
  // already real since 2026-08-01; this is just the Talk-composer wiring.
  const [showAttachMenu, setShowAttachMenu] = useState(false);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  const [showMediaGallery, setShowMediaGallery] = useState(false);
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
      setError(err instanceof ApiError ? err.message : "Couldn't upload that photo. Check your connection and try again.");
    } finally {
      setUploadingPhoto(false);
      if (photoInputRef.current) photoInputRef.current.value = '';
    }
  };

  const load = () =>
    fetchGroupMessages(group.groupId)
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this group.'));

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
      setError(err instanceof ApiError ? err.message : 'Could not send this message.');
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
      setError(err instanceof ApiError ? err.message : 'Could not pin this message.');
    } finally { setUpdatingPin(false); }
  };

  const handleUnpin = async () => {
    setUpdatingPin(true);
    try {
      await unpinGroupMessage(group.groupId);
      setPinnedMessage(null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not unpin this message.');
    } finally { setUpdatingPin(false); }
  };

  const handleDelete = async (messageId: string) => {
    if (!window.confirm('Delete this message for everyone?')) return;
    try { await deleteGroupMessage(group.groupId, messageId); load(); }
    catch (err) { setError(err instanceof ApiError ? err.message : 'Could not delete this message.'); }
  };

  // Real message forwarding (2026-07-25) -- see lib/messaging.ts's own doc comment.
  const handleForward = async (destinationType: 'DIRECT' | 'GROUP', destinationId: string) => {
    if (!forwardingMessage) return;
    try {
      await forwardGroupMessage(forwardingMessage.id, destinationType, destinationId);
      setForwardingMessage(null);
      setError('Message forwarded.');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not forward this message.');
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
      setError(err instanceof ApiError ? err.message : 'Could not send this emoticon.');
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

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100svh - 180px)' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '10px', marginBottom: '12px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to conversations">
            <ArrowLeft size={20} />
          </button>
          <div>
            <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{group.name}</h3>
            <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{group.memberCount} members</p>
          </div>
        </div>
        <div style={{ display: 'flex', gap: '6px' }}>
          <button type="button" onClick={() => setShowMediaGallery(true)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Shared photos">
            <ImageIcon size={20} />
          </button>
          <button type="button" onClick={() => setShowManageMembers(true)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Manage members">
            <Users size={20} />
          </button>
          <button type="button" onClick={() => setShowSplitBills(true)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Split a bill">
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

      {pinnedMessage && (
        <div style={{ display: 'flex', gap: '8px', alignItems: 'center', padding: '8px 10px', marginBottom: '8px', borderRadius: '10px', background: 'var(--toss-grey-100)', fontSize: '12px' }}>
          <span aria-hidden="true">📌</span><span style={{ flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{pinnedMessage.body}</span>
          <button type="button" onClick={handleUnpin} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-600)', fontSize: '12px' }}>Unpin</button>
        </div>
      )}

      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '8px', padding: '4px' }}>
        {messages === null && <div className="toss-card skeleton" style={{ height: '120px' }} />}
        {messages !== null && messages.length === 0 && (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', textAlign: 'center', marginTop: '20px' }}>
            Say hello — no messages yet.
          </p>
        )}
        {messages?.map((m) => {
          const isMine = m.senderId === currentUser?.id;
          return (
            <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
              {!isMine && (
                <span style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginBottom: '2px', marginLeft: '4px' }}>
                  {nameForSender(m.senderId)}
                </span>
              )}
              {/* Real message forwarding (2026-07-25) -- see lib/messaging.ts's own
                  doc comment. */}
              {m.forwardedFromMessageId && (
                <span style={{ fontSize: '10px', color: 'var(--toss-grey-400)', fontStyle: 'italic', marginBottom: '2px' }}>Forwarded</span>
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
                    fontSize: '14px',
                    backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                    color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                  }}
                >
                  {m.body}
                </div>
              )}
              <MessageReactions
                reactions={m.reactions}
                currentUserId={currentUser?.id}
                isMine={isMine}
                onToggle={(emoji) => handleToggleReaction(m.id, emoji)}
              />
              <button type="button" onClick={() => setReplyingTo(m)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Reply</button>
              {!m.imageUrl && <button type="button" onClick={() => handleCopy(m.body)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Copy</button>}
              {!(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => setForwardingMessage(m)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Forward</button>}
              {isMine && !(m as GroupMessage & { deletedAt?: string | null }).deletedAt && <button type="button" onClick={() => handleDelete(m.id)} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>Delete</button>}
              <button type="button" onClick={() => handlePin(m)} disabled={updatingPin} style={{ border: 'none', background: 'none', color: 'var(--toss-grey-500)', fontSize: '11px', padding: '4px 0' }}>{pinnedMessage?.id === m.id ? 'Pinned' : 'Pin'}</button>
              <span style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>
                {/* Real Kakao-style read-receipt countdown -- see
                    GroupMessagingService.getUnreadCounts's own doc comment. Only shown
                    on my own messages, same convention 1:1's own "1" indicator uses;
                    disappears at 0, exactly matching real KakaoTalk. */}
                {isMine && m.unreadCount > 0 ? `${m.unreadCount} · ` : ''}{chatMessageTime(m.sentAt)}
              </span>
              {/* Real Thread support (2026-08-05) -- see ConversationThread's own
                  identical affordance. */}
              {!!m.replyCount && (
                <button
                  type="button"
                  onClick={() => setThreadRootMessage(m)}
                  style={{ border: 'none', background: 'none', color: 'var(--toss-blue)', fontSize: '11px', fontWeight: 600, padding: '4px 0' }}
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

      {Object.keys(typingUserIds).length > 0 && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px', fontStyle: 'italic' }}>
          {Object.keys(typingUserIds).map(nameForSender).join(', ')} {Object.keys(typingUserIds).length === 1 ? 'is' : 'are'} typing…
        </p>
      )}

      {error && (
        <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>
      )}

      {emoticonPickerOpen && (
        <EmoticonPickerPanel onSend={handleSendGroupEmoticon} onOpenStore={() => setEmoticonStoreOpen(true)} />
      )}
      {emoticonStoreOpen && <EmoticonStoreModal onClose={() => setEmoticonStoreOpen(false)} />}

      {replyingTo && <div style={{ fontSize: '12px', color: 'var(--toss-grey-600)', padding: '8px', borderLeft: '3px solid var(--toss-blue)', marginBottom: '6px' }}>Replying to: {replyingTo.body.slice(0, 80)} <button type="button" onClick={() => setReplyingTo(null)}>×</button></div>}
      <MentionSuggestions draft={draft} members={members} currentUserId={currentUser?.id} onPick={(name) => setDraft((d) => applyMention(d, name))} />
      <input
        ref={photoInputRef}
        type="file"
        accept="image/jpeg,image/png,image/webp"
        style={{ display: 'none' }}
        onChange={(e) => handleSendPhoto(e.target.files?.[0])}
      />
      <form onSubmit={handleSend} style={{ display: 'flex', gap: '10px', position: 'relative' }}>
        {/* Real attach ("+") menu (2026-08-04 on Android, ported to bank-mfe) --
            Kakao's own real "+"-opens-a-menu pattern (References table: "'+' opens a
            multi-function attach menu"). */}
        <button
          type="button"
          aria-label="Attach"
          disabled={uploadingPhoto}
          onClick={() => setShowAttachMenu((v) => !v)}
          style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '18px', fontWeight: 700 }}
        >
          {uploadingPhoto ? '…' : '+'}
        </button>
        {showAttachMenu && (
          <div style={{ position: 'absolute', bottom: '52px', left: 0, background: 'var(--toss-white)', border: '1px solid var(--toss-grey-200)', borderRadius: '10px', boxShadow: '0 4px 12px rgba(0,0,0,0.1)', overflow: 'hidden', zIndex: 10 }}>
            <button type="button" onClick={() => { setShowAttachMenu(false); photoInputRef.current?.click(); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
              📷 Photo
            </button>
            <button type="button" onClick={() => { setShowAttachMenu(false); setEmoticonPickerOpen((v) => !v); }} style={{ display: 'block', width: '100%', padding: '10px 16px', textAlign: 'left', fontSize: '14px' }}>
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
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={sending || !draft.trim()} style={{ padding: '10px 16px' }}>
          <Send size={16} />
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
        style={{ background: 'var(--toss-white)', borderRadius: '16px 16px 0 0', padding: '16px', width: '100%', maxHeight: '70vh', overflowY: 'auto' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Shared photos ({imageUrls.length})</h3>
        {imageUrls.length === 0 ? (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No photos shared in this conversation yet.</p>
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

  return (
    <div
      style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }}
      onClick={onClose}
    >
      <div
        className="toss-card"
        style={{ width: '100%', maxHeight: '60vh', overflowY: 'auto', borderRadius: '16px 16px 0 0', margin: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <p style={{ fontSize: '15px', fontWeight: 700, marginBottom: '12px' }}>Forward to…</p>
        {conversations === null || groups === null ? (
          <div className="toss-card skeleton" style={{ height: '100px' }} />
        ) : conversations.length === 0 && groups.length === 0 ? (
          <EmptyState message="No conversations or groups to forward to yet." />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {conversations.map((c) => (
              <button
                key={c.conversationId}
                className="toss-card"
                style={{ width: '100%', textAlign: 'left' }}
                onClick={() => onForward('DIRECT', c.conversationId)}
              >
                {c.otherUserName}
              </button>
            ))}
            {groups.map((g) => (
              <button
                key={g.groupId}
                className="toss-card"
                style={{ width: '100%', textAlign: 'left' }}
                onClick={() => onForward('GROUP', g.groupId)}
              >
                {g.name} (group)
              </button>
            ))}
          </div>
        )}
        <button type="button" className="toss-btn toss-btn-secondary" style={{ width: '100%', marginTop: '12px' }} onClick={onClose}>
          Cancel
        </button>
      </div>
    </div>
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
  const [messages, setMessages] = useState<T[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [draft, setDraft] = useState('');
  const [sending, setSending] = useState(false);

  const load = () => {
    fetchThreadMessages()
      .then(setMessages)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this thread.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not send this reply.');
    } finally { setSending(false); }
  };

  return (
    <div style={{ position: 'fixed', inset: 0, backgroundColor: 'rgba(0,0,0,0.4)', display: 'flex', alignItems: 'flex-end', zIndex: 1000 }} onClick={onClose}>
      <div
        className="toss-card"
        style={{ width: '100%', maxHeight: '80vh', display: 'flex', flexDirection: 'column', borderRadius: '16px 16px 0 0', margin: 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '12px' }}>
          <p style={{ fontSize: '15px', fontWeight: 700 }}>Thread</p>
          <button type="button" onClick={onClose} style={{ border: 'none', background: 'none', fontSize: '18px', color: 'var(--toss-grey-500)' }}>×</button>
        </div>
        {error && <p style={{ fontSize: '12px', color: 'var(--toss-red)', marginBottom: '8px' }}>{error}</p>}
        <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '10px', paddingBottom: '8px' }}>
          {messages === null ? (
            <div className="toss-card skeleton" style={{ height: '80px' }} />
          ) : (
            messages.map((m, i) => {
              const isMine = m.senderId === currentUserId;
              return (
                <div key={m.id} style={{ display: 'flex', flexDirection: 'column', alignItems: isMine ? 'flex-end' : 'flex-start' }}>
                  {i === 0 && <span style={{ fontSize: '10px', color: 'var(--toss-grey-400)', marginBottom: '2px' }}>Original message</span>}
                  <div
                    style={{
                      maxWidth: '75%', padding: '10px 14px', borderRadius: '16px', fontSize: '14px',
                      backgroundColor: isMine ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
                      color: isMine ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                    }}
                  >
                    {m.deletedAt ? 'This message was deleted' : m.body}
                  </div>
                  <span style={{ fontSize: '10px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>{chatMessageTime(m.sentAt)}</span>
                </div>
              );
            })
          )}
        </div>
        <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
          <input
            className="toss-input"
            style={{ flex: 1 }}
            placeholder="Reply in thread…"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') handleSend(); }}
          />
          <button type="button" className="toss-btn toss-btn-primary" style={{ padding: '10px 16px' }} onClick={handleSend} disabled={sending || !draft.trim()}>
            {sending ? '…' : 'Send'}
          </button>
        </div>
      </div>
    </div>
  );
}

function DirectMessagesList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void }) {
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

  const load = () => {
    setError(null);
    fetchConversations()
      .then(setConversations)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your conversations.'));
    fetchConversations(true).then(setArchivedConversations).catch(() => {});
  };

  const toggleArchived = (conversationId: string, archived: boolean) => {
    setConversationArchived(conversationId, archived).then(load).catch(() => {});
  };

  useEffect(load, []);

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

  const openConversation = conversations?.find((c) => c.conversationId === openConversationId);
  if (openConversation) {
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
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  const visibleConversations = showArchived ? (archivedConversations ?? []) : conversations;
  const archivedCount = archivedConversations?.length ?? 0;

  return (
    <div>
      <NewChatCard onStarted={(id) => { load(); setOpenConversationId(id); }} />
      {archivedCount > 0 && (
        <button type="button" className="toss-btn toss-btn-secondary" onClick={() => setShowArchived((value) => !value)} style={{ marginBottom: '10px' }}>
          {showArchived ? 'Show active chats' : `Archived (${archivedCount})`}
        </button>
      )}
      {visibleConversations.length === 0 ? (
        <div className="toss-card">
          <EmptyState message={showArchived ? 'No archived chats.' : 'No conversations yet.'} />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {visibleConversations.map((c) => (
            <div key={c.conversationId} className="toss-card" style={{ display: 'flex', alignItems: 'center', gap: '10px', padding: '18px 20px' }}>
              <button
                onClick={() => setOpenConversationId(c.conversationId)}
                style={{ display: 'flex', alignItems: 'center', gap: '16px', flex: 1, minWidth: 0, textAlign: 'left', background: 'none', border: 'none', padding: 0, cursor: 'pointer' }}
              >
                <div style={{ position: 'relative', width: '44px', height: '44px', flexShrink: 0 }}>
                  <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <MessageCircle size={20} color="var(--toss-blue)" />
                  </div>
                  {presence[c.otherUserId] && (
                    <span
                      style={{
                        position: 'absolute', bottom: 0, right: 0, width: '12px', height: '12px', borderRadius: '6px',
                        backgroundColor: 'var(--toss-green)', border: '2px solid var(--toss-white)',
                      }}
                    />
                  )}
                </div>
                <div style={{ flex: 1, minWidth: 0 }}>
                  <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{c.otherUserName}</p>
                  <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    {c.lastMessagePreview ?? 'No messages yet'}
                  </p>
                </div>
                {c.unreadCount > 0 && (
                  <span
                    style={{
                      fontSize: '11px', fontWeight: 700, color: 'var(--toss-white)', backgroundColor: 'var(--toss-blue)',
                      borderRadius: '10px', padding: '2px 8px', flexShrink: 0,
                    }}
                  >
                    {c.unreadCount}
                  </span>
                )}
              </button>
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
                style={{ background: 'none', border: 'none', cursor: 'pointer', padding: '8px', flexShrink: 0, color: 'var(--toss-grey-500)' }}
              >
                {showArchived ? <ArchiveRestore size={18} /> : <Archive size={18} />}
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real KakaoPay-style split bill (2026-07-22) -- found fully built on the backend
// (rw.itunda.splitbill) with zero client UI anywhere, despite group chat itself being
// fully wired. A flat, even split among picked group members (excluding the
// organizer); each participant pays their own share directly to the organizer via a
// real wallet-to-wallet push, no escrow -- see SplitBill.kt's own doc comment.
function GroupSplitBillsView({
  groupConversationId, members, currentUserId, onBack,
}: { groupConversationId: string; members: GroupMember[]; currentUserId: string | null; onBack: () => void }) {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load split bills.'));

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
      setError(err instanceof ApiError ? err.message : 'That split bill could not be created.');
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
      setError(err instanceof ApiError ? err.message : 'That payment could not be completed.');
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
      setError(err instanceof ApiError ? err.message : 'That receipt could not be attached.');
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
      setError(err instanceof ApiError ? err.message : 'Could not start the next settlement round.');
    } finally {
      setBusyId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to group">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Split bills</h3>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {!showNewForm ? (
        <button className="toss-btn toss-btn-primary" onClick={() => setShowNewForm(true)}>Split a bill</button>
      ) : (
        <form onSubmit={handleCreate} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', padding: '16px' }}>
          <input
            type="number" min="1" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Total amount (RWF)" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <input
            type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was it for?" required
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Split with</p>
          {otherMembers.map((m) => (
            <label key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
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
          <label
            style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px', cursor: 'pointer' }}
            onClick={(e) => { e.preventDefault(); setLadderMode((v) => !v); }}
          >
            🎲 Ladder game (randomized split)
            <span style={{ fontSize: '12px', color: ladderMode ? 'var(--toss-blue)' : 'var(--toss-grey-500)', fontWeight: 700 }}>
              {ladderMode ? 'On' : 'Off'}
            </span>
          </label>
          {ladderMode && (
            <div style={{ display: 'flex', gap: '8px' }}>
              {[1, 2, 3].map((level) => (
                <button
                  key={level} type="button"
                  className={level === varianceLevel ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                  style={{ flex: 1, fontSize: '12px' }}
                  onClick={() => setVarianceLevel(level)}
                >
                  Level {level}
                </button>
              ))}
            </div>
          )}
          <div style={{ display: 'flex', gap: '10px' }}>
            <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setShowNewForm(false)}>Cancel</button>
            <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busyId === 'new' || selectedIds.size === 0}>
              {busyId === 'new' ? 'Creating…' : 'Create'}
            </button>
          </div>
        </form>
      )}
      {splitBills === null && <div className="toss-card skeleton" style={{ height: '80px' }} />}
      {splitBills !== null && splitBills.length === 0 && (
        <EmptyState message="No split bills in this group yet." />
      )}
      {splitBills?.map(({ splitBill, participants }) => {
        const myShare = participants.find((p) => p.userId === currentUserId);
        const isOrganizer = splitBill.organizerId === currentUserId;
        const hasPending = participants.some((p) => p.status === 'PENDING');
        const modeLabel = splitBill.mode === 'LADDER' ? ` · 🎲 Ladder L${splitBill.ladderVarianceLevel}` : '';
        return (
          <div key={splitBill.id} className="toss-card" style={{ padding: '16px', display: 'flex', flexDirection: 'column', gap: '4px' }}>
            <h4 style={{ fontSize: '14px', fontWeight: 700 }}>{splitBill.description}</h4>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
              Total {splitBill.totalAmount.toLocaleString()} RWF · {splitBill.status}{modeLabel}
              {splitBill.currentRound > 1 ? ` · Round ${splitBill.currentRound}` : ''}
            </p>
            {participants.map((p) => {
              const name = members.find((m) => m.userId === p.userId)?.name ?? p.userId.slice(0, 8);
              return (
                <p key={p.id} style={{ fontSize: '12px' }}>
                  {name}: {p.shareAmount.toLocaleString()} RWF ({p.status})
                </p>
              );
            })}
            {splitBill.receiptImageUrl && (
              <a href={splitBill.receiptImageUrl} target="_blank" rel="noreferrer" style={{ fontSize: '12px', color: 'var(--toss-blue)' }}>
                🧾 View receipt
              </a>
            )}
            {myShare && myShare.status === 'PENDING' && (
              <button
                className="toss-btn toss-btn-primary" style={{ marginTop: '6px' }}
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
                      style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '12px' }}
                    />
                    <button
                      type="button" className="toss-btn toss-btn-secondary" style={{ fontSize: '12px' }}
                      disabled={busyId === splitBill.id || !(receiptUrlDrafts[splitBill.id] ?? '').trim()}
                      onClick={() => handleAttachReceipt(splitBill.id)}
                    >
                      Attach
                    </button>
                  </div>
                )}
                {splitBill.status === 'OPEN' && hasPending && splitBill.currentRound < 5 && (
                  <button
                    type="button" className="toss-btn toss-btn-secondary" style={{ marginTop: '4px', fontSize: '12px' }}
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
      setError(err instanceof ApiError ? err.message : 'Could not update group info.');
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
      setError(err instanceof ApiError ? err.message : 'Could not leave this group.');
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
      setError(err instanceof ApiError ? err.message : `Could not add ${contact.name}.`);
    } finally {
      setBusyUserId(null);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to group">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Manage members</h3>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      <h4 style={{ fontSize: '13px', fontWeight: 700 }}>Group info</h4>
      <input
        type="text" placeholder="Photo URL (blank to clear)" value={photoUrl} onChange={(e) => setPhotoUrl(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        placeholder="Group description (blank to clear)" value={description} onChange={(e) => setDescription(e.target.value)} rows={2}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', fontFamily: 'inherit' }}
      />
      <button className="toss-btn toss-btn-secondary" disabled={savingInfo} onClick={handleSaveInfo}>
        {savingInfo ? 'Saving…' : infoSaved ? 'Saved' : 'Save group info'}
      </button>
      <h4 style={{ fontSize: '13px', fontWeight: 700 }}>Members ({members.length})</h4>
      {members.map((m) => (
        <p key={m.userId} style={{ fontSize: '13px' }}>{m.userId === currentUserId ? `${m.name} (you)` : m.name}</p>
      ))}
      <button className="toss-btn toss-btn-secondary" disabled={leaving} onClick={handleLeave}>
        {leaving ? 'Leaving…' : 'Leave group'}
      </button>
      <h4 style={{ fontSize: '13px', fontWeight: 700, marginTop: '8px' }}>Add from your contacts</h4>
      {addable.length === 0 && <EmptyState message="No contacts left to add." />}
      {addable.map((c) => (
        <div key={c.userId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ fontSize: '13px' }}>{c.name}</span>
          <button className="toss-btn toss-btn-secondary" disabled={busyUserId !== null} onClick={() => handleAdd(c)}>
            {busyUserId === c.userId ? 'Adding…' : 'Add'}
          </button>
        </div>
      ))}
    </div>
  );
}

function GroupsList({ initialConversationId, onConsumedInitial }: { initialConversationId?: string | null; onConsumedInitial?: () => void } = {}) {
  const [groups, setGroups] = useState<GroupSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openGroupId, setOpenGroupId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGroups()
      .then(setGroups)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your groups.'));
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
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      <NewGroupCard onCreated={(id) => { load(); setOpenGroupId(id); }} />
      {groups.length === 0 ? (
        <div className="toss-card">
          <EmptyState message="No groups yet." />
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {groups.map((g) => (
            <button
              key={g.groupId}
              onClick={() => setOpenGroupId(g.groupId)}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <Users size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1, minWidth: 0 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{g.name} · {g.memberCount}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {g.lastMessagePreview ?? 'No messages yet'}
                </p>
              </div>
              {g.unreadCount > 0 && (
                <span
                  style={{
                    fontSize: '11px', fontWeight: 700, color: 'var(--toss-white)', backgroundColor: 'var(--toss-blue)',
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
  const [mode, setMode] = useState<'DIRECT' | 'GROUPS'>('DIRECT');

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
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['DIRECT', 'GROUPS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'DIRECT' ? 'Direct' : 'Groups'}
          </button>
        ))}
      </div>
      {mode === 'DIRECT' ? (
        <DirectMessagesList initialConversationId={initialConversationId} onConsumedInitial={onConsumedInitial} />
      ) : (
        <GroupsList initialConversationId={initialConversationId} onConsumedInitial={onConsumedInitial} />
      )}
    </div>
  );
}

function NewListingCard({ onCreated }: { onCreated: () => void }) {
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [category, setCategory] = useState('');
  const [meetingPlace, setMeetingPlace] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);
  // Real optional seller location (2026-07-18 backend support, 2026-07-19 this UI) --
  // powers real proximity search and "Directions to this seller"; a listing without it
  // simply doesn't appear in either, an honest opt-in, never assumed.
  const [shareLocation, setShareLocation] = useState(false);
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [locating, setLocating] = useState(false);

  // Real seller-uploaded photo (2026-08-01) -- see lib/marketplace.ts's own doc
  // comment on Listing.photoUrl. Android already has this (MarketplaceScreen.kt's
  // pickPhoto flow); bank-mfe never had a photo field at all until now.
  const [photoUrl, setPhotoUrl] = useState<string | null>(null);
  const [uploadingPhoto, setUploadingPhoto] = useState(false);

  const handlePhotoSelected = async (file: File | undefined) => {
    if (!file) return;
    setPhotoUrl(null);
    setUploadingPhoto(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      setPhotoUrl(url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload this photo.');
    } finally {
      setUploadingPhoto(false);
    }
  };

  const handleToggleShareLocation = () => {
    if (shareLocation) {
      setShareLocation(false);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShareLocation(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const [lat, lng] = shareLocation && myLocation ? myLocation : [undefined, undefined];
      await createListing(title, description, Number(price), category, lat, lng, meetingPlace.trim() || undefined, photoUrl ?? undefined);
      setTitle('');
      setDescription('');
      setPrice('');
      setCategory('');
      setMeetingPlace('');
      setShareLocation(false);
      setMyLocation(null);
      setPhotoUrl(null);
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this listing.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + List an item
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>List an item</h3>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What are you selling?" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Description" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)} placeholder="Price (RWF)" required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="text" value={category} onChange={(e) => setCategory(e.target.value)} placeholder="Category" required
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <input
        type="text" value={meetingPlace} maxLength={120} onChange={(e) => setMeetingPlace(e.target.value)}
        placeholder="Suggested meeting place (optional)"
        aria-describedby="meeting-place-help"
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <small id="meeting-place-help" style={{ color: 'var(--toss-grey-600)' }}>Use a public landmark, not a home address.</small>
      <input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        disabled={uploadingPhoto}
        onChange={(e) => handlePhotoSelected(e.target.files?.[0])}
        style={{ fontSize: '13px' }}
      />
      {uploadingPhoto && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Uploading…</p>}
      {photoUrl && !uploadingPhoto && <p style={{ fontSize: '12px', color: 'var(--toss-green)' }}>✓ Photo uploaded</p>}
      <button
        type="button"
        className="toss-btn toss-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: '13px' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- buyers can see distance & get directions' : '📍 Share my real location (optional)'}
      </button>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting || uploadingPhoto}>
          {submitting ? 'Listing…' : 'List it'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function ListingCard({ listing, isMine, onChanged, onMessageSeller, favorited, favoriteBusy, onToggleFavorite, sellerTrustScore }: {
  listing: Listing;
  isMine: boolean;
  onChanged: () => void;
  onMessageSeller: (conversationId: string) => void;
  favorited: boolean;
  favoriteBusy: boolean;
  onToggleFavorite: () => void;
  sellerTrustScore?: number;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null); // [lat, lng]
  const [showRoute, setShowRoute] = useState(false);
  const [locating, setLocating] = useState(false);

  // Real optional "who bought this?" prompt (2026-07-24) -- see backend
  // MarketplaceService.markSold's own doc comment. Confirm with a phone number or
  // Skip, either way the sale completes.
  const [markingSold, setMarkingSold] = useState(false);
  const [buyerPhone, setBuyerPhone] = useState('');

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const [showReviewSheet, setShowReviewSheet] = useState(false);
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [selectedUncomfortablePoints, setSelectedUncomfortablePoints] = useState<Set<string>>(new Set());
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  // Real read-back (item 192) -- see lib/marketplace.ts's fetchListingReviews doc
  // comment: without this, reviewSubmitted above was purely local/optimistic and reset
  // on every refresh, silently re-offering the form for an already-reviewed sale.
  const [hoodReviews, setHoodReviews] = useState<HoodReview[] | null>(null);
  const myUserId = getStoredUser()?.id;
  useEffect(() => {
    if (!(isMine && listing.status === 'SOLD' && listing.buyerId)) return;
    fetchListingReviews(listing.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [listing.id, listing.status, listing.buyerId, isMine]);

  const handleShowDirections = () => {
    if (showRoute) {
      setShowRoute(false);
      return;
    }
    if (myLocation) {
      setShowRoute(true);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShowRoute(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleMarkSold = async (buyerPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markListingSold(listing.id, buyerPhoneNumber || undefined);
      setMarkingSold(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this listing.');
    } finally {
      setBusy(false);
    }
  };

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const handleSubmitReview = async () => {
    setSubmittingReview(true);
    setError(null);
    try {
      const review = await submitListingReview(listing.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
    } finally {
      setSubmittingReview(false);
    }
  };

  const handleRemove = async () => {
    setBusy(true);
    setError(null);
    try {
      await removeListing(listing.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleMessage = async () => {
    setBusy(true);
    setError(null);
    try {
      const conversation = await contactSeller(listing.id);
      onMessageSeller(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this seller.');
    } finally {
      setBusy(false);
    }
  };

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makeOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageSeller(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this offer.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
            {listing.title}
            {listing.status === 'SOLD' && (
              <span style={{ marginLeft: '8px', fontSize: '11px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
                SOLD
              </span>
            )}
          </p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{listing.category}</p>
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{listing.price.toLocaleString()} RWF</span>
        </div>
      </div>
      {/* Real seller-uploaded photo (2026-08-01) -- see lib/marketplace.ts's own doc
          comment on Listing.photoUrl. Android already renders this; bank-mfe never
          had a photo field at all until now. */}
      {listing.photoUrl && (
        <img
          src={listing.photoUrl}
          alt={listing.title}
          style={{ width: '100%', maxHeight: '220px', objectFit: 'cover', borderRadius: '10px' }}
        />
      )}
      {/* Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
          comment. Only shown for someone else's listing. */}
      {!isMine && sellerTrustScore != null && <TrustBadge score={sellerTrustScore} />}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{listing.description}</p>
      {listing.meetingPlace && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-600)', margin: 0 }}>Suggested hand-off: {listing.meetingPlace}</p>
      )}
      {offering && (
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number"
            value={offerAmount}
            onChange={(e) => setOfferAmount(e.target.value)}
            placeholder="Your offer (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={busy || !offerAmount} onClick={handleMakeOffer}>
            Send
          </button>
        </div>
      )}
      {/* Real optional "who bought this?" prompt (2026-07-24) -- see backend
          MarketplaceService.markSold's own doc comment. */}
      {markingSold && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={buyerPhone}
            onChange={(e) => setBuyerPhone(e.target.value)}
            placeholder="Buyer's phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkSold()}>
              Skip
            </button>
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkSold(buyerPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility (2026-07-24) -- see backend HoodReviewService's own doc comment. */}
      {isMine && listing.status === 'SOLD' && listing.buyerId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && listing.status === 'SOLD' && listing.buyerId && !reviewSubmitted && (
        showReviewSheet ? (
          <HoodReviewForm
            selectedGoodPoints={selectedGoodPoints}
            onToggleGoodPoint={(id) => setSelectedGoodPoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            selectedUncomfortablePoints={selectedUncomfortablePoints}
            onToggleUncomfortablePoint={(id) => setSelectedUncomfortablePoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            submitting={submittingReview}
            onCancel={() => setShowReviewSheet(false)}
            onSubmit={handleSubmitReview}
          />
        ) : (
          <button className="toss-btn toss-btn-primary" disabled={busy} onClick={() => setShowReviewSheet(true)}>
            Rate this buyer
          </button>
        )
      )}
      <div style={{ display: 'flex', gap: '8px' }}>
        {isMine ? (
          <>
            {listing.status === 'ACTIVE' && !markingSold && (
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => setMarkingSold(true)}>
                Mark sold
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button className="toss-btn toss-btn-danger" style={{ flex: 1 }} disabled={busy} onClick={handleRemove}>
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'ACTIVE' && !offering && (
            <>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={handleMessage}>
                {busy ? 'Starting…' : 'Message seller'}
              </button>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
            </>
          )
        )}
      </div>
      {!isMine && <HoodReportButton targetType="MARKETPLACE_LISTING" targetId={listing.id} />}
      {!isMine && listing.status === 'ACTIVE' && listing.latitude != null && listing.longitude != null && (
        <button className="toss-btn toss-btn-secondary" disabled={locating} onClick={handleShowDirections}>
          {locating ? 'Finding your real location…' : showRoute ? 'Hide directions' : '🚗 Directions to this seller'}
        </button>
      )}
      {showRoute && myLocation && listing.latitude != null && listing.longitude != null && (
        <RouteMiniMap
          fromLat={myLocation[0]}
          fromLng={myLocation[1]}
          toLat={listing.latitude}
          toLng={listing.longitude}
          fromLabel="You"
          toLabel={listing.title}
        />
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

// Real hyperlocal neighborhood setup (2026-07-20) -- shared across every Hood-tab
// module (Marketplace/Community/Jobs/Property), same "one small component, four real
// call sites" shape this project already uses for offer bubbles etc. See
// lib/neighborhood.ts's own doc comment for the full backend account.
// Real second neighborhood (2026-08-04) -- isSecond mirrors Android HoodShared.kt's own
// NeighborhoodSetupPrompt(isSecond) and iOS's own isSecond port exactly, same copy.
function NeighborhoodSetupPrompt({ isSecond = false, onDone }: { isSecond?: boolean; onDone: (neighborhood: string) => void }) {
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
            setError(err instanceof ApiError ? err.message : 'Could not determine your neighborhood.');
          });
      },
      () => {
        setBusy(false);
        setError('Could not get your real location. Check your browser permissions.');
      },
    );
  };

  return (
    <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
      <p style={{ fontSize: '15px', fontWeight: 700, marginBottom: '8px' }}>{isSecond ? 'Add a second neighborhood' : 'Set your neighborhood'}</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
        {isSecond ? "Share a second real place -- like work -- to see what's happening there too." : "Share your real location once to see what's happening near you."}
      </p>
      <button className="toss-btn toss-btn-primary" onClick={handleShare} disabled={busy}>
        {busy ? 'Finding your neighborhood…' : '📍 Share my location'}
      </button>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '12px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real dual-neighborhood add/change/remove row (2026-08-04) -- mirrors Android
// SuperAppTabs.kt's HoodTab showNeighborhoodPrompt second-neighborhood card and iOS's
// own NeighborhoodSwitcherOverlay exactly. Shown alongside the primary
// NeighborhoodSetupPrompt in every Hood-tab module's NEIGHBORHOOD view.
function NeighborhoodSwitcherRow({
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
    <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px', marginTop: '10px' }}>
      <span style={{ fontSize: '13px', fontWeight: 600 }}>{secondNeighborhoodName ? `Second: ${secondNeighborhoodName}` : 'Add a second neighborhood'}</span>
      <div style={{ display: 'flex', gap: '12px' }}>
        <button style={{ fontSize: '13px', fontWeight: 600, color: 'var(--toss-blue)' }} onClick={onAddTapped}>
          {secondNeighborhoodName ? 'Change' : 'Add'}
        </button>
        {secondNeighborhoodName && (
          <button style={{ fontSize: '13px', fontWeight: 600, color: '#E53935' }} onClick={handleRemove} disabled={removing}>
            Remove
          </button>
        )}
      </div>
    </div>
  );
}

function ListingWishlistView() {
  const [favorites, setFavorites] = useState<FavoriteListing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteListings().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your wishlist.'));
  };
  useEffect(load, []);

  const handleRemove = async (listingId: string) => {
    setRemovingId(listingId);
    try {
      await removeListingFavorite(listingId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this item.');
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved listings yet -- tap ♡ on any listing to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.listingId} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <p style={{ fontSize: '15px', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{f.category} · {f.price.toLocaleString()} RWF</p>
          </div>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={removingId === f.listingId}
            onClick={() => handleRemove(f.listingId)}
            style={{ padding: '8px 12px', fontSize: '12px' }}
          >
            {removingId === f.listingId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

// Real 당근마켓-style Keyword Alert -- see lib/marketplace.ts's own doc comment. First
// client UI for this feature on any platform (item 114, found via a content-grep
// sweep confirming zero client anywhere despite a mature backend). Real, published
// Karrot 30-keyword-per-user cap enforced server-side; this view surfaces the
// backend's own real KEYWORD_ALERT_CAP_REACHED error rather than guessing the limit.
function KeywordAlertsView() {
  const [alerts, setAlerts] = useState<KeywordAlert[] | null>(null);
  const [keyword, setKeyword] = useState('');
  const [adding, setAdding] = useState(false);
  const [removingId, setRemovingId] = useState<string | null>(null);
  const [quietHours, setQuietHoursState] = useState<KeywordAlertQuietHours | null | undefined>(undefined);
  const [quietStart, setQuietStart] = useState('22:00');
  const [quietEnd, setQuietEnd] = useState('08:00');
  const [savingQuietHours, setSavingQuietHours] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchKeywordAlerts().then(setAlerts).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your alerts.'));
    fetchKeywordAlertQuietHours()
      .then((qh) => {
        setQuietHoursState(qh);
        if (qh) { setQuietStart(qh.startTime); setQuietEnd(qh.endTime); }
      })
      .catch(() => setQuietHoursState(null));
  };
  useEffect(load, []);

  const handleAdd = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyword.trim()) return;
    setAdding(true);
    setError(null);
    try {
      await addKeywordAlert(keyword.trim());
      setKeyword('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not add this alert.');
    } finally {
      setAdding(false);
    }
  };

  const handleRemove = async (alertId: string) => {
    setRemovingId(alertId);
    try {
      await removeKeywordAlert(alertId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this alert.');
    } finally {
      setRemovingId(null);
    }
  };

  const handleSaveQuietHours = async (enabled: boolean) => {
    setSavingQuietHours(true);
    setError(null);
    try {
      const updated = await setKeywordAlertQuietHours(quietStart, quietEnd, enabled);
      setQuietHoursState(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not save quiet hours.');
    } finally {
      setSavingQuietHours(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <form onSubmit={handleAdd} className="toss-card" style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text" value={keyword} onChange={(e) => setKeyword(e.target.value)} placeholder="Alert me for (e.g. iPhone 15)"
          style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={adding || !keyword.trim()}>{adding ? '…' : 'Add'}</button>
      </form>

      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}

      {alerts === null ? (
        <div className="toss-card skeleton" style={{ height: '80px' }} />
      ) : alerts.length === 0 ? (
        <EmptyState message="No keyword alerts yet -- add one to get notified when a matching listing is posted." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {alerts.map((a) => (
            <div key={a.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <p style={{ fontSize: '14px', fontWeight: 700 }}>{a.keyword}</p>
              <button
                className="toss-btn toss-btn-secondary" disabled={removingId === a.id} onClick={() => handleRemove(a.id)}
                style={{ padding: '8px 12px', fontSize: '12px' }}
              >
                {removingId === a.id ? 'Removing…' : 'Remove'}
              </button>
            </div>
          ))}
        </div>
      )}

      {quietHours !== undefined && (
        <div className="toss-card">
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Quiet hours</h3>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
            Don't send alert notifications during these hours.
          </p>
          <div style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
            <input
              type="time" value={quietStart} onChange={(e) => setQuietStart(e.target.value)}
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
            <input
              type="time" value={quietEnd} onChange={(e) => setQuietEnd(e.target.value)}
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
          </div>
          <button
            className={quietHours?.enabled ? 'toss-btn toss-btn-secondary' : 'toss-btn toss-btn-primary'}
            disabled={savingQuietHours}
            onClick={() => handleSaveQuietHours(!quietHours?.enabled)}
            style={{ width: '100%' }}
          >
            {savingQuietHours ? '…' : quietHours?.enabled ? 'Turn off quiet hours' : 'Turn on quiet hours'}
          </button>
        </div>
      )}
    </div>
  );
}

function MarketplaceView({ onMessageSeller }: { onMessageSeller: (conversationId: string) => void }) {
  // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend ListingRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'PURCHASES' | 'NEIGHBORHOOD' | 'WISHLIST' | 'ALERTS' | 'INSPECTIONS'>('BROWSE');
  const [listings, setListings] = useState<Listing[] | null>(null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  const loadFavoriteIds = () => {
    fetchMyFavoriteListings().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.listingId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setListings(null);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchListingsMyNeighborhood()])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setListings(result.listings);
          setTrustScores(result.trustScores);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setListings([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    if (view === 'WISHLIST' || view === 'ALERTS' || view === 'INSPECTIONS') return;
    const fetcher = view === 'BROWSE' ? fetchListings() : view === 'PURCHASES' ? fetchMyPurchases() : fetchMyListings();
    fetcher
      .then((result) => {
        setListings(result.listings);
        setTrustScores(result.trustScores);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load listings.'));
  };

  useEffect(load, [view]);

  // Real Marketplace listing wishlist (2026-07-21) -- mirrors Shop's own product
  // wishlist toggle (ProductCatalogView.toggleFavorite) field-for-field.
  const toggleFavorite = async (listingId: string) => {
    setFavoritingId(listingId);
    try {
      if (favoriteIds.has(listingId)) {
        await removeListingFavorite(listingId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(listingId); return next; });
      } else {
        await addListingFavorite(listingId);
        setFavoriteIds((prev) => new Set(prev).add(listingId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setFavoritingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'PURCHASES', 'WISHLIST', 'ALERTS', 'INSPECTIONS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My listings' : v === 'PURCHASES' ? 'Purchases' : v === 'WISHLIST' ? '♡ Wishlist' : v === 'ALERTS' ? '🔔 Alerts' : '🔧 Inspections'}
          </button>
        ))}
      </div>

      {view === 'WISHLIST' ? (
        <ListingWishlistView />
      ) : view === 'ALERTS' ? (
        <KeywordAlertsView />
      ) : view === 'INSPECTIONS' ? (
        <VehicleInspectionsView />
      ) : (
        <>
          {view === 'MINE' && <NewListingCard onCreated={load} />}

          {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
            <NeighborhoodSetupPrompt onDone={() => load()} />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <NeighborhoodSwitcherRow
              secondNeighborhoodName={secondNeighborhoodName}
              onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
              onRemoved={(next) => setSecondNeighborhoodName(next)}
            />
          )}

          {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
            <NeighborhoodSetupPrompt
              isSecond
              onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
            />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
              Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
            </p>
          )}

          {error && (
            <ErrorCard message={error} onRetry={load} />
          )}
          {!error && listings === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
          {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                {view === 'BROWSE' ? 'No listings yet.' : view === 'NEIGHBORHOOD' ? 'No listings in your neighborhood yet.' : view === 'PURCHASES' ? 'No purchases recorded yet.' : "You haven't listed anything yet."}
              </p>
            </div>
          )}
          {!error && listings !== null && listings.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {listings.map((listing) => (
                <ListingCard
                  key={listing.id}
                  listing={listing}
                  isMine={view === 'MINE' || listing.sellerId === currentUser?.id}
                  onChanged={load}
                  onMessageSeller={onMessageSeller}
                  favorited={favoriteIds.has(listing.id)}
                  favoriteBusy={favoritingId === listing.id}
                  onToggleFavorite={() => toggleFavorite(listing.id)}
                  sellerTrustScore={trustScores[listing.sellerId]}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
// lib/vehicleInspection.ts's own doc comment for the full sourced account. A buyer
// books and 100%-prepays a real mechanic to inspect a real used-car listing before
// purchase; a mechanic can register, browse incoming bookings, and deliver findings.
function VehicleInspectionsView() {
  const [tab, setTab] = useState<'BUYER' | 'MECHANIC'>('BUYER');

  // Buyer side
  const [mechanics, setMechanics] = useState<VehicleInspectionMechanic[] | null>(null);
  const [myBookings, setMyBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const [listingId, setListingId] = useState('');
  const [mechanicId, setMechanicId] = useState('');
  const [fee, setFee] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [requesting, setRequesting] = useState(false);
  const [buyerError, setBuyerError] = useState<string | null>(null);
  const [busyBookingId, setBusyBookingId] = useState<string | null>(null);

  const loadBuyerData = () => {
    Promise.all([fetchAvailableMechanics(), fetchMyInspectionBookings()])
      .then(([m, b]) => { setMechanics(m); setMyBookings(b); })
      .catch((err) => setBuyerError(err instanceof ApiError ? err.message : 'Could not load inspections.'));
  };

  useEffect(() => {
    if (tab === 'BUYER') loadBuyerData();
  }, [tab]);

  const handleRequest = async () => {
    const numericFee = Number(fee);
    if (!listingId.trim() || !mechanicId || !numericFee || numericFee <= 0 || !scheduledAt) {
      setBuyerError('Fill in the listing id, a mechanic, a valid fee, and a scheduled time.');
      return;
    }
    setRequesting(true);
    setBuyerError(null);
    try {
      await requestInspection(listingId.trim(), mechanicId, numericFee, new Date(scheduledAt).toISOString());
      setListingId('');
      setMechanicId('');
      setFee('');
      setScheduledAt('');
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : 'Could not request this inspection.');
    } finally {
      setRequesting(false);
    }
  };

  const handleCancel = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await cancelInspection(bookingId);
      loadBuyerData();
    } catch (err) {
      setBuyerError(err instanceof ApiError ? err.message : 'Could not cancel this booking.');
    } finally {
      setBusyBookingId(null);
    }
  };

  // Mechanic side
  const [mechanicProfile, setMechanicProfile] = useState<VehicleInspectionMechanic | null | undefined>(undefined);
  const [businessName, setBusinessName] = useState('');
  const [registering, setRegistering] = useState(false);
  const [mechanicBookings, setMechanicBookings] = useState<VehicleInspectionBooking[] | null>(null);
  const [mechanicError, setMechanicError] = useState<string | null>(null);
  const [findings, setFindings] = useState<Record<string, string>>({});

  const loadMechanicData = () => {
    fetchMyMechanicProfile()
      .then((m) => {
        setMechanicProfile(m);
        if (m) fetchMyMechanicBookings().then(setMechanicBookings).catch(() => {});
      })
      .catch((err) => setMechanicError(err instanceof ApiError ? err.message : 'Could not load your mechanic profile.'));
  };

  useEffect(() => {
    if (tab === 'MECHANIC') loadMechanicData();
  }, [tab]);

  const handleRegister = async () => {
    if (!businessName.trim()) return;
    setRegistering(true);
    setMechanicError(null);
    try {
      setMechanicProfile(await registerAsMechanic(businessName.trim()));
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : 'Could not register as a mechanic.');
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!mechanicProfile) return;
    try {
      setMechanicProfile(await setMechanicAvailability(!mechanicProfile.available));
    } catch {
      // Real, non-critical -- an availability toggle failure isn't worth a hard error.
    }
  };

  const handleAccept = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await acceptInspection(bookingId);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : 'Could not accept this booking.');
    } finally {
      setBusyBookingId(null);
    }
  };

  const handleComplete = async (bookingId: string) => {
    setBusyBookingId(bookingId);
    try {
      await completeInspection(bookingId, findings[bookingId]);
      loadMechanicData();
    } catch (err) {
      setMechanicError(err instanceof ApiError ? err.message : 'Could not complete this booking.');
    } finally {
      setBusyBookingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BUYER', 'MECHANIC'] as const).map((t) => (
          <button
            key={t} onClick={() => setTab(t)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: tab === t ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: tab === t ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {t === 'BUYER' ? 'Get a car inspected' : 'Mechanic'}
          </button>
        ))}
      </div>

      {tab === 'BUYER' ? (
        <div>
          <div className="toss-card" style={{ marginBottom: '16px' }}>
            <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Book an inspection</h3>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
              Pay a local mechanic to inspect a used car before you buy it -- held until they deliver their findings.
            </p>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <input
                type="text" value={listingId} onChange={(e) => setListingId(e.target.value)} placeholder="Listing ID"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <select
                value={mechanicId} onChange={(e) => setMechanicId(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              >
                <option value="">Choose a mechanic</option>
                {(mechanics ?? []).map((m) => <option key={m.id} value={m.id}>{m.businessName}</option>)}
              </select>
              <input
                type="number" value={fee} onChange={(e) => setFee(e.target.value)} placeholder="Inspection fee (RWF)"
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <input
                type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <button className="toss-btn toss-btn-primary" disabled={requesting} onClick={handleRequest}>
                {requesting ? 'Booking…' : 'Book & pay'}
              </button>
            </div>
            {buyerError && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }} role="alert">{buyerError}</p>}
          </div>

          {myBookings === null ? (
            <div className="toss-card skeleton" style={{ height: '100px' }} />
          ) : myBookings.length === 0 ? (
            <EmptyState message="No inspections booked yet." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {myBookings.map((b) => (
                <div key={b.id} className="toss-card">
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.findings && <p style={{ fontSize: '13px', marginTop: '6px' }}>{b.findings}</p>}
                  {(b.status === 'REQUESTED' || b.status === 'ACCEPTED') && (
                    <button
                      className="toss-btn toss-btn-danger" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id}
                      onClick={() => handleCancel(b.id)}
                    >
                      {busyBookingId === b.id ? 'Cancelling…' : 'Cancel'}
                    </button>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      ) : mechanicProfile === undefined ? (
        <div className="toss-card skeleton" style={{ height: '160px' }} />
      ) : mechanicProfile === null ? (
        <div className="toss-card">
          <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Become an inspection mechanic</h3>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
            Get booked and paid to inspect used cars for real buyers before they purchase.
          </p>
          <input
            type="text" value={businessName} onChange={(e) => setBusinessName(e.target.value)} placeholder="Business name"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={registering} onClick={handleRegister}>
            {registering ? 'Registering…' : 'Register'}
          </button>
          {mechanicError && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }} role="alert">{mechanicError}</p>}
        </div>
      ) : (
        <div>
          <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
            <div>
              <p style={{ fontSize: '15px', fontWeight: 700 }}>{mechanicProfile.businessName}</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{mechanicProfile.available ? 'Visible for new bookings' : 'Not accepting bookings'}</p>
            </div>
            <button className={mechanicProfile.available ? 'toss-btn toss-btn-danger' : 'toss-btn toss-btn-primary'} onClick={handleToggleAvailable}>
              {mechanicProfile.available ? 'Go unavailable' : 'Go available'}
            </button>
          </div>
          {mechanicError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{mechanicError}</p>}
          {mechanicBookings === null ? (
            <div className="toss-card skeleton" style={{ height: '100px' }} />
          ) : mechanicBookings.length === 0 ? (
            <EmptyState message="No inspection bookings yet." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {mechanicBookings.map((b) => (
                <div key={b.id} className="toss-card">
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>Listing {b.listingId}</p>
                  <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{b.fee.toLocaleString()} RWF · {b.status}</p>
                  {b.status === 'REQUESTED' && (
                    <button className="toss-btn toss-btn-primary" style={{ marginTop: '8px' }} disabled={busyBookingId === b.id} onClick={() => handleAccept(b.id)}>
                      {busyBookingId === b.id ? 'Accepting…' : 'Accept'}
                    </button>
                  )}
                  {b.status === 'ACCEPTED' && (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '8px' }}>
                      <textarea
                        value={findings[b.id] ?? ''} onChange={(e) => setFindings((prev) => ({ ...prev, [b.id]: e.target.value }))}
                        placeholder="Inspection findings" rows={2}
                        style={{ padding: '10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', resize: 'vertical' }}
                      />
                      <button className="toss-btn toss-btn-primary" disabled={busyBookingId === b.id} onClick={() => handleComplete(b.id)}>
                        {busyBookingId === b.id ? 'Completing…' : 'Mark complete'}
                      </button>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// ============================== COMMUNITY (동네생활) ==============================

function NewCommunityPostCard({ categories, onCreated }: { categories: CommunityCategory[]; onCreated: () => void }) {
  const [category, setCategory] = useState(categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);
  // Real optional post location (opt-in, same pattern NewListingCard already
  // established) -- powers a real "near me" browse.
  const [shareLocation, setShareLocation] = useState(false);
  const [myLocation, setMyLocation] = useState<[number, number] | null>(null);
  const [locating, setLocating] = useState(false);
  // Real 당근모임/같이사요 structured fields -- see lib/community.ts's own
  // createCommunityPost doc comment for the real live bug this closes.
  const [eventDate, setEventDate] = useState('');
  const [capacity, setCapacity] = useState('');

  const handleToggleShareLocation = () => {
    if (shareLocation) {
      setShareLocation(false);
      return;
    }
    if (!navigator.geolocation) {
      setError('This browser does not support real location access.');
      return;
    }
    setLocating(true);
    setError(null);
    navigator.geolocation.getCurrentPosition(
      (position) => {
        setLocating(false);
        setMyLocation([position.coords.latitude, position.coords.longitude]);
        setShareLocation(true);
      },
      () => {
        setLocating(false);
        setError('Could not access your real location. Check your browser permissions.');
      },
      { enableHighAccuracy: true, timeout: 10000 },
    );
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      const [lat, lng] = shareLocation && myLocation ? myLocation : [undefined, undefined];
      const isoEventDate = eventDate ? new Date(eventDate).toISOString() : undefined;
      const numericCapacity = capacity ? Number(capacity) : undefined;
      await createCommunityPost(category, title, body, lat, lng, isoEventDate, numericCapacity);
      setTitle('');
      setBody('');
      setShareLocation(false);
      setMyLocation(null);
      setEventDate('');
      setCapacity('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this post.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Write a post
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Write a post</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="Title" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={body} onChange={(e) => setBody(e.target.value)} placeholder="What's going on in the neighborhood?" required rows={4}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      {category === 'meetup' && (
        <input
          type="datetime-local" value={eventDate} onChange={(e) => setEventDate(e.target.value)} required
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      )}
      {(category === 'meetup' || category === 'group_buy') && (
        <input
          type="number" min={2} max={category === 'group_buy' ? 4 : undefined} value={capacity}
          onChange={(e) => setCapacity(e.target.value)}
          placeholder={category === 'group_buy' ? 'Max people (up to 4, including you)' : 'Max people (optional)'}
          required={category === 'group_buy'}
          style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      )}
      <button
        type="button"
        className="toss-btn toss-btn-secondary"
        disabled={locating}
        onClick={handleToggleShareLocation}
        style={{ fontSize: '13px' }}
      >
        {locating ? 'Finding your real location…' : shareLocation ? '📍 Real location shared -- others nearby can find this post' : '📍 Share my real location (optional)'}
      </button>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function CommunityPostCard({ post, categoryLabel, isMine, onOpen, onChanged, joinedCount, joining, onJoin }: {
  post: CommunityPost; categoryLabel: string; isMine: boolean; onOpen: () => void; onChanged: () => void;
  joinedCount?: number; joining?: boolean; onJoin?: () => void;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleRemove = async () => {
    setBusy(true);
    try {
      await removeCommunityPost(post.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this post.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px', cursor: 'pointer' }} onClick={onOpen}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{categoryLabel}</span>
        {isMine && (
          <button
            className="toss-btn toss-btn-secondary"
            style={{ fontSize: '11px', padding: '4px 10px' }}
            disabled={busy}
            onClick={(e) => { e.stopPropagation(); void handleRemove(); }}
          >
            {busy ? 'Removing…' : 'Remove'}
          </button>
        )}
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' as const }}>
        {post.body}
      </p>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-400)' }}>
        ❤️ {post.likeCount} · 💬 {post.commentCount}
      </p>
      {/* Real 참여하기 (join) tap (2026-07-24) -- a real join, not just a "view"
          navigation: it adds the tapper to a real GroupConversation (see backend
          CommunityService.joinMeetup's own doc comment), shown with a real "N joined"
          count rather than a bare label. */}
      {!isMine && post.category === 'meetup' && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ fontSize: '12px', padding: '6px 12px', alignSelf: 'flex-start' }}
          disabled={joining}
          onClick={(e) => { e.stopPropagation(); onJoin?.(); }}
        >
          {joining ? 'Joining…' : `참여하기 · ${joinedCount ?? 0} joined`}
        </button>
      )}
      {!isMine && <HoodReportButton targetType="COMMUNITY_POST" targetId={post.id} />}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
// lib/community.ts's own doc comment for the full sourced account. Only rendered for a
// real category === 'meetup' post; the author gets a real schedule form, any real
// joined member gets a real per-session check-in button.
function MeetupSessionsSection({ post, currentUserId }: { post: CommunityPost; currentUserId: string | undefined }) {
  const [sessions, setSessions] = useState<MeetupSession[] | null>(null);
  const [dates, setDates] = useState<string[]>(['']);
  const [scheduling, setScheduling] = useState(false);
  const [checkingInId, setCheckingInId] = useState<string | null>(null);
  const [checkedInIds, setCheckedInIds] = useState<Set<string>>(new Set());
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchMeetupSessions(post.id).then(setSessions).catch(() => setSessions([]));
  };

  useEffect(load, [post.id]);

  const isAuthor = currentUserId != null && currentUserId === post.authorId;

  const handleSchedule = async () => {
    const isoDates = dates.filter((d) => d).map((d) => new Date(d).toISOString());
    if (isoDates.length === 0) { setError('Add at least one session date.'); return; }
    setScheduling(true);
    setError(null);
    try {
      await scheduleMeetupSessions(post.id, isoDates);
      setDates(['']);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not schedule these sessions.');
    } finally {
      setScheduling(false);
    }
  };

  const handleCheckIn = async (sessionId: string) => {
    setCheckingInId(sessionId);
    setError(null);
    try {
      await checkIntoMeetupSession(sessionId);
      setCheckedInIds((prev) => new Set(prev).add(sessionId));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not check in to this session.');
    } finally {
      setCheckingInId(null);
    }
  };

  return (
    <div style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Sessions</h3>
      {sessions === null && <div className="toss-card skeleton" style={{ height: '60px' }} />}
      {sessions !== null && sessions.length === 0 && (
        <EmptyState message="No sessions scheduled yet." />
      )}
      {sessions !== null && sessions.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          {sessions.map((s) => (
            <div key={s.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '10px 14px' }}>
              <p style={{ fontSize: '13px' }}>{new Date(s.scheduledFor).toLocaleString()}</p>
              <button
                className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '6px 12px' }}
                disabled={checkingInId === s.id || checkedInIds.has(s.id)}
                onClick={() => handleCheckIn(s.id)}
              >
                {checkedInIds.has(s.id) ? '✓ Checked in' : checkingInId === s.id ? '…' : 'Check in'}
              </button>
            </div>
          ))}
        </div>
      )}
      {isAuthor && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>Schedule sessions (up to 6)</p>
          {dates.map((d, i) => (
            <input
              key={i} type="datetime-local" value={d}
              onChange={(e) => setDates((prev) => prev.map((v, idx) => (idx === i ? e.target.value : v)))}
              style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginBottom: '6px' }}
            />
          ))}
          <div style={{ display: 'flex', gap: '8px' }}>
            {dates.length < 6 && (
              <button className="toss-btn toss-btn-secondary" onClick={() => setDates((prev) => [...prev, ''])}>+ Add date</button>
            )}
            <button className="toss-btn toss-btn-primary" disabled={scheduling} onClick={handleSchedule}>
              {scheduling ? 'Scheduling…' : 'Schedule'}
            </button>
          </div>
        </div>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }} role="alert">{error}</p>}
    </div>
  );
}

// Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see lib/community.ts's own
// finalizeGroupBuy doc comment. Author-only: once real participants have joined via the
// same 참여하기 flow a meetup already uses, the organizer fronts the total cost and
// splits it via the already-real SplitBill mechanic.
function GroupBuyFinalizeSection({ post, currentUserId }: { post: CommunityPost; currentUserId: string | undefined }) {
  const [totalAmount, setTotalAmount] = useState('');
  const [description, setDescription] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (currentUserId == null || currentUserId !== post.authorId) return null;

  const handleFinalize = async () => {
    const amount = Number(totalAmount);
    if (!amount || amount <= 0 || !description.trim()) { setError('Enter a real total amount and a short description.'); return; }
    setSubmitting(true);
    setError(null);
    try {
      await finalizeGroupBuy(post.id, amount, description.trim());
      setDone(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not split this cost.');
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <div className="toss-card" style={{ marginTop: '16px' }}>
        <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>Split request sent -- see it in your group chat's Split bill tab.</p>
      </div>
    );
  }

  return (
    <div className="toss-card" style={{ marginTop: '16px' }}>
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Split the cost</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
        Enter what you paid up front -- every real member who joined will be asked for their even share.
      </p>
      <input
        type="number" value={totalAmount} onChange={(e) => setTotalAmount(e.target.value)} placeholder="Total amount (RWF)"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
      />
      <input
        type="text" value={description} onChange={(e) => setDescription(e.target.value)} placeholder="What was this for?"
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px', width: '100%', boxSizing: 'border-box', marginBottom: '8px' }}
      />
      <button className="toss-btn toss-btn-primary" disabled={submitting} onClick={handleFinalize}>
        {submitting ? 'Splitting…' : 'Request even split'}
      </button>
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '8px' }} role="alert">{error}</p>}
    </div>
  );
}

function CommunityPostDetailView({ postId, onBack }: { postId: string; onBack: () => void }) {
  const [post, setPost] = useState<CommunityPost | null>(null);
  const [authorName, setAuthorName] = useState('');
  const [likedByMe, setLikedByMe] = useState(false);
  const [comments, setComments] = useState<{ comment: CommunityComment; authorName: string }[] | null>(null);
  const [commentBody, setCommentBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [liking, setLiking] = useState(false);
  const [commenting, setCommenting] = useState(false);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    fetchCommunityPost(postId)
      .then((r) => { setPost(r.post); setAuthorName(r.authorName); setLikedByMe(r.likedByMe); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this post.'));
    fetchCommunityComments(postId)
      .then(setComments)
      .catch(() => { /* non-critical -- the post itself still renders */ });
  };

  useEffect(load, [postId]);

  const handleLike = async () => {
    setLiking(true);
    try {
      const liked = await toggleCommunityLike(postId);
      setLikedByMe(liked);
      setPost((p) => (p ? { ...p, likeCount: p.likeCount + (liked ? 1 : -1) } : p));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your like.');
    } finally {
      setLiking(false);
    }
  };

  const handleComment = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!commentBody.trim()) return;
    setCommenting(true);
    setError(null);
    try {
      await addCommunityComment(postId, commentBody);
      setCommentBody('');
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post your comment.');
    } finally {
      setCommenting(false);
    }
  };

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {!post && !error && <div className="toss-card skeleton" style={{ height: '160px' }} />}
      {post && (
        <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
          <p style={{ fontSize: '17px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>by {authorName}</p>
          <p style={{ fontSize: '14px', color: 'var(--toss-grey-700)', whiteSpace: 'pre-wrap' }}>{post.body}</p>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={liking}
            onClick={handleLike}
            style={{ alignSelf: 'flex-start', fontSize: '13px' }}
          >
            {likedByMe ? '❤️' : '🤍'} {post.likeCount}
          </button>
        </div>
      )}
      {post && post.category === 'meetup' && <MeetupSessionsSection post={post} currentUserId={currentUser?.id} />}
      {post && post.category === 'group_buy' && <GroupBuyFinalizeSection post={post} currentUserId={currentUser?.id} />}
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Comments</h3>
      {comments === null && <div className="toss-card skeleton" style={{ height: '80px' }} />}
      {comments !== null && comments.length === 0 && (
        <EmptyState message="No comments yet -- be the first to reply." />
      )}
      {comments !== null && comments.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          {comments.map(({ comment, authorName: name }) => (
            <div key={comment.id} className="toss-card" style={{ padding: '10px 14px' }}>
              <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-700)' }}>{name}</p>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-900)' }}>{comment.body}</p>
            </div>
          ))}
        </div>
      )}
      <form onSubmit={handleComment} style={{ display: 'flex', gap: '8px' }}>
        <input
          type="text" value={commentBody} onChange={(e) => setCommentBody(e.target.value)} placeholder="Add a comment"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <button type="submit" className="toss-btn toss-btn-primary" disabled={commenting || !commentBody.trim()}>
          {commenting ? '…' : 'Send'}
        </button>
      </form>
    </div>
  );
}

function CommunityView({ onOpenGroupChat }: { onOpenGroupChat: (groupId: string) => void }) {
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'NEIGHBORHOOD'>('BROWSE');
  const [categories, setCategories] = useState<CommunityCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [posts, setPosts] = useState<CommunityPost[] | null>(null);
  // Real 같이해요 (join-together) group join counts (2026-07-24) -- see TrustBadge's
  // sibling doc comments; closes docs/DESIGN_REFERENCES.md Section 4 recommendation #4.
  const [joinedCounts, setJoinedCounts] = useState<JoinedCounts>({});
  const [joiningPostId, setJoiningPostId] = useState<string | null>(null);
  const [openPostId, setOpenPostId] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchCommunityCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const load = () => {
    setError(null);
    setPosts(null);
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchCommunityPostsMyNeighborhood(activeCategory ?? undefined)])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setPosts(result.posts);
          setJoinedCounts(result.joinedCounts);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setPosts([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    const fetcher = view === 'BROWSE' ? fetchCommunityPosts(activeCategory ?? undefined) : fetchMyCommunityPosts();
    fetcher
      .then((result) => { setPosts(result.posts); setJoinedCounts(result.joinedCounts); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load posts.'));
  };

  useEffect(load, [view, activeCategory]);

  // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
  // CommunityService.joinMeetup's own doc comment.
  const joinMeetup = async (postId: string) => {
    setJoiningPostId(postId);
    try {
      const groupId = await joinCommunityMeetup(postId);
      setJoinedCounts((prev) => ({ ...prev, [postId]: (prev[postId] ?? 0) + 1 }));
      onOpenGroupChat(groupId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not join this meetup.');
    } finally {
      setJoiningPostId(null);
    }
  };

  if (openPostId) {
    return <CommunityPostDetailView postId={openPostId} onBack={() => { setOpenPostId(null); load(); }} />;
  }

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Feed' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : 'My posts'}
          </button>
        ))}
      </div>

      {(view === 'BROWSE' || view === 'NEIGHBORHOOD') && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
          {categories.map((c) => (
            <button
              key={c.id}
              onClick={() => setActiveCategory(activeCategory === c.id ? null : c.id)}
              style={{
                whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${activeCategory === c.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: activeCategory === c.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: activeCategory === c.id ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}

      {view === 'MINE' && <NewCommunityPostCard categories={categories} onCreated={load} />}

      {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
        <NeighborhoodSetupPrompt onDone={() => load()} />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <NeighborhoodSwitcherRow
          secondNeighborhoodName={secondNeighborhoodName}
          onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
          onRemoved={(next) => setSecondNeighborhoodName(next)}
        />
      )}

      {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
        <NeighborhoodSetupPrompt
          isSecond
          onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
        />
      )}

      {view === 'NEIGHBORHOOD' && neighborhoodName && (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
          Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
        </p>
      )}

      {error && (
        <ErrorCard message={error} onRetry={load} />
      )}
      {!error && posts === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
      {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {view === 'BROWSE' ? 'No posts yet.' : view === 'NEIGHBORHOOD' ? 'No posts in your neighborhood yet.' : "You haven't posted anything yet."}
          </p>
        </div>
      )}
      {!error && posts !== null && posts.length > 0 && (() => {
        // Real 같이해요 (join-together) pinned mid-feed slot (2026-07-24) -- Karrot's
        // real board gives meetup posts a dedicated slot instead of mixing them purely
        // chronologically (docs/DESIGN_REFERENCES.md Section 4 recommendation #4). "My
        // posts" stays plain chronological.
        const meetups = view !== 'MINE' ? posts.filter((p) => p.category === 'meetup') : [];
        const regular = view !== 'MINE' ? posts.filter((p) => p.category !== 'meetup') : posts;
        const renderCard = (post: CommunityPost) => (
          <CommunityPostCard
            key={post.id}
            post={post}
            categoryLabel={categoryLabel(post.category)}
            isMine={view === 'MINE' || post.authorId === currentUser?.id}
            onOpen={() => setOpenPostId(post.id)}
            onChanged={load}
            joinedCount={joinedCounts[post.id] ?? 0}
            joining={joiningPostId === post.id}
            onJoin={() => joinMeetup(post.id)}
          />
        );
        return (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {meetups.length > 0 && (
              <>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>🎉 Meetups</p>
                {meetups.map(renderCard)}
              </>
            )}
            {regular.map(renderCard)}
          </div>
        );
      })()}
    </div>
  );
}

// ============================== JOBS (당근알바) ==============================

function NewJobPostCard({ categories, onCreated }: { categories: JobCategory[]; onCreated: () => void }) {
  const [category, setCategory] = useState(categories[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [payType, setPayType] = useState<JobPayType>('HOURLY');
  const [payAmount, setPayAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createJobPost(category, title, description, payType, Number(payAmount));
      setTitle('');
      setDescription('');
      setPayAmount('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not post this job.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + Post a job
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Post a job</h3>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      >
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="What do you need done?" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the work" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={payType} onChange={(e) => setPayType(e.target.value as JobPayType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          <option value="HOURLY">Per hour</option>
          <option value="FIXED">Fixed price</option>
        </select>
        <input
          type="number" value={payAmount} onChange={(e) => setPayAmount(e.target.value)} placeholder="Pay (RWF)" required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Posting…' : 'Post job'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function JobPostCard({ post, categoryLabel, isMine, onChanged, onContact, favorited, favoriteBusy, onToggleFavorite, posterTrustScore }: {
  post: JobPost; categoryLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
  favorited: boolean; favoriteBusy: boolean; onToggleFavorite: () => void; posterTrustScore?: number;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Real optional worker identification at mark-filled time (2026-07-24) -- see
  // backend JobPostService.markFilled's own doc comment.
  const [markingFilled, setMarkingFilled] = useState(false);
  const [workerPhone, setWorkerPhone] = useState('');

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const [showReviewSheet, setShowReviewSheet] = useState(false);
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [selectedUncomfortablePoints, setSelectedUncomfortablePoints] = useState<Set<string>>(new Set());
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  // Real read-back (item 192) -- see lib/marketplace.ts's fetchListingReviews doc comment.
  const [hoodReviews, setHoodReviews] = useState<HoodReview[] | null>(null);
  const myUserId = getStoredUser()?.id;
  useEffect(() => {
    if (!(isMine && post.status === 'FILLED' && post.workerId)) return;
    fetchJobPostReviews(post.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [post.id, post.status, post.workerId, isMine]);

  // Real 당근알바-style structured application (2026-07-25 on Android/iOS, ported here
  // 2026-07-29) -- the applicant's real self-introduction, not a bare DM. See backend
  // JobApplicationService's own doc comment. "Message poster" above still exists as a
  // separate, unstructured hand-off.
  const [applying, setApplying] = useState(false);
  const [applicationMessage, setApplicationMessage] = useState('');
  const [applicationSubmitted, setApplicationSubmitted] = useState(false);
  const [submittingApplication, setSubmittingApplication] = useState(false);
  const [showApplicants, setShowApplicants] = useState(false);
  const [applications, setApplications] = useState<JobApplication[] | null>(null);
  const [respondingToId, setRespondingToId] = useState<string | null>(null);

  useEffect(() => {
    if (!showApplicants || applications !== null) return;
    fetchApplicationsForJobPost(post.id)
      .then(setApplications)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load applicants.'));
  }, [showApplicants]);

  const handleSubmitApplication = async () => {
    setSubmittingApplication(true);
    setError(null);
    try {
      await applyToJob(post.id, applicationMessage.trim());
      setApplying(false);
      setApplicationSubmitted(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this application.');
    } finally {
      setSubmittingApplication(false);
    }
  };

  const handleRespond = async (applicationId: string, accept: boolean) => {
    setRespondingToId(applicationId);
    setError(null);
    try {
      await respondToJobApplication(applicationId, accept);
      setApplications((prev) => prev?.filter((a) => a.id !== applicationId) ?? null);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not respond to this application.');
    } finally {
      setRespondingToId(null);
    }
  };

  const handleMarkFilled = async (workerPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markJobPostFilled(post.id, workerPhoneNumber || undefined);
      setMarkingFilled(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this job.');
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitReview = async () => {
    setSubmittingReview(true);
    setError(null);
    try {
      const review = await submitJobPostReview(post.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
    } finally {
      setSubmittingReview(false);
    }
  };

  const payLabel = `${post.payAmount.toLocaleString()} RWF${post.payType === 'HOURLY' ? '/hr' : ''}`;

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>{categoryLabel}</span>
          {post.status === 'FILLED' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              FILLED
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Real 당근알바 job-post wishlist (2026-07-22) -- mirrors ListingCard's own
              WishlistButton reuse exactly, closing a docs/DESIGN_REFERENCES.md-named
              gap: Marketplace listings already had this, Jobs never did. */}
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{payLabel}</span>
        </div>
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{post.title}</p>
      {/* Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
          comment. Only shown for someone else's post. */}
      {!isMine && posterTrustScore != null && <TrustBadge score={posterTrustScore} />}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{post.description}</p>
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      {/* Real optional "who did you hire?" prompt (2026-07-24) -- see backend
          JobPostService.markFilled's own doc comment. */}
      {markingFilled && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={workerPhone}
            onChange={(e) => setWorkerPhone(e.target.value)}
            placeholder="Worker's phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkFilled()}>
              Skip
            </button>
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkFilled(workerPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility (2026-07-24) -- see backend HoodReviewService's own doc comment.
          Only offered once a real worker was recorded at mark-filled time. */}
      {isMine && post.status === 'FILLED' && post.workerId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && post.status === 'FILLED' && post.workerId && !reviewSubmitted && (
        showReviewSheet ? (
          <HoodReviewForm
            selectedGoodPoints={selectedGoodPoints}
            onToggleGoodPoint={(id) => setSelectedGoodPoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            selectedUncomfortablePoints={selectedUncomfortablePoints}
            onToggleUncomfortablePoint={(id) => setSelectedUncomfortablePoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            submitting={submittingReview}
            onCancel={() => setShowReviewSheet(false)}
            onSubmit={handleSubmitReview}
          />
        ) : (
          <button className="toss-btn toss-btn-primary" disabled={busy} onClick={() => setShowReviewSheet(true)}>
            Rate this worker
          </button>
        )
      )}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {post.status === 'OPEN' && !markingFilled && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={() => setMarkingFilled(true)}
              >
                Mark filled
              </button>
            )}
            {post.status !== 'REMOVED' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removeJobPost(post.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not remove this job.'); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          post.status === 'OPEN' && (
            <>
              <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={onContact}>
                Message poster
              </button>
              {!applicationSubmitted && !applying && (
                <button className="toss-btn toss-btn-primary" disabled={busy} onClick={() => setApplying(true)}>
                  Apply
                </button>
              )}
            </>
          )
        )}
      </div>
      {!isMine && applying && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <textarea
            value={applicationMessage}
            onChange={(e) => setApplicationMessage(e.target.value)}
            placeholder="Why should the poster pick you? (required)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', minHeight: '72px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={submittingApplication} onClick={() => setApplying(false)}>
              Cancel
            </button>
            <button
              className="toss-btn toss-btn-primary" style={{ flex: 1 }}
              disabled={submittingApplication || applicationMessage.trim().length === 0}
              onClick={handleSubmitApplication}
            >
              {submittingApplication ? 'Submitting…' : 'Submit application'}
            </button>
          </div>
        </div>
      )}
      {!isMine && applicationSubmitted && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Application sent — you'll hear back once the poster reviews it</p>
      )}
      {isMine && post.status === 'OPEN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => setShowApplicants((v) => !v)}>
            {showApplicants ? 'Hide applicants' : 'View applicants'}
          </button>
          {showApplicants && (
            applications === null ? <div className="toss-card skeleton" style={{ height: '60px' }} /> :
            applications.filter((a) => a.status === 'PENDING').length === 0 ? (
              <EmptyState message="No applications yet" />
            ) : (
              applications.filter((a) => a.status === 'PENDING').map((app) => (
                <div key={app.id} style={{ backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px', padding: '12px', display: 'flex', flexDirection: 'column', gap: '6px' }}>
                  <p style={{ fontSize: '13px' }}>{app.message}</p>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={respondingToId === app.id} onClick={() => handleRespond(app.id, false)}>
                      Decline
                    </button>
                    <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={respondingToId === app.id} onClick={() => handleRespond(app.id, true)}>
                      Accept &amp; message
                    </button>
                  </div>
                </div>
              ))
            )
          )}
        </div>
      )}
      {!isMine && <HoodReportButton targetType="JOB_POST" targetId={post.id} />}
    </div>
  );
}

// Real "My applications" status view (2026-07-25 on Android as item 196, ported here
// 2026-07-29) -- an applicant could submit a real structured application and message
// the poster, but never see whether it was pending/accepted/declined. JobApplication
// carries no job-post title snapshot, so this fans out one real fetchJobPost per
// application to resolve the title, same N+1 shape Android's own port uses.
function MyJobApplicationsView() {
  const [applications, setApplications] = useState<Array<{ application: JobApplication; title: string | null }> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchMyJobApplications()
      .then(async (apps) => {
        const withTitles = await Promise.all(
          apps.map(async (application) => {
            const title = await fetchJobPost(application.jobPostId).then((post) => post.title).catch(() => null);
            return { application, title };
          }),
        );
        setApplications(withTitles);
      })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your applications.'));
  }, []);

  if (error) return <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>;
  if (applications === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  if (applications.length === 0) return <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>You haven't applied to any jobs yet.</p>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
      {applications.map(({ application, title }) => (
        <div key={application.id} className="toss-card">
          <p style={{ fontSize: '14px', fontWeight: 700 }}>{title ?? 'Job post'}</p>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{application.message}</p>
          <span
            style={{
              display: 'inline-block', marginTop: '6px', fontSize: '11px', fontWeight: 700, padding: '2px 8px', borderRadius: '8px',
              color: application.status === 'ACCEPTED' ? 'var(--toss-blue)' : application.status === 'DECLINED' ? '#E53935' : 'var(--toss-grey-700)',
              backgroundColor: application.status === 'ACCEPTED' ? 'rgba(49, 130, 246, 0.1)' : application.status === 'DECLINED' ? 'rgba(229, 57, 53, 0.1)' : 'var(--toss-grey-100)',
            }}
          >
            {application.status}
          </span>
        </div>
      ))}
    </div>
  );
}

// Real 당근알바 job-post wishlist view (2026-07-22) -- mirrors ListingWishlistView
// exactly, closing a docs/DESIGN_REFERENCES.md-named gap.
function JobPostWishlistView() {
  const [favorites, setFavorites] = useState<FavoriteJobPost[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteJobPosts().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your wishlist.'));
  };
  useEffect(load, []);

  const handleRemove = async (jobPostId: string) => {
    setRemovingId(jobPostId);
    try {
      await removeJobPostFavorite(jobPostId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this item.');
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved jobs yet -- tap ♡ on any job post to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.jobPostId} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <p style={{ fontSize: '15px', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{f.category} · {f.payAmount.toLocaleString()} RWF</p>
          </div>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={removingId === f.jobPostId}
            onClick={() => handleRemove(f.jobPostId)}
            style={{ padding: '8px 12px', fontSize: '12px' }}
          >
            {removingId === f.jobPostId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

function JobsView({ onMessagePoster }: { onMessagePoster: (conversationId: string) => void }) {
  // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend JobPostRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'WORKED' | 'NEIGHBORHOOD' | 'WISHLIST' | 'APPLICATIONS'>('BROWSE');
  const [categories, setCategories] = useState<JobCategory[]>([]);
  const [activeCategory, setActiveCategory] = useState<string | null>(null);
  const [posts, setPosts] = useState<JobPost[] | null>(null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchJobCategories().then(setCategories).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const loadFavoriteIds = () => {
    fetchMyFavoriteJobPosts().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.jobPostId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setPosts(null);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchJobPostsMyNeighborhood(activeCategory ?? undefined)])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setPosts(result.posts);
          setTrustScores(result.trustScores);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setPosts([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    if (view === 'WISHLIST' || view === 'APPLICATIONS') return;
    const fetcher = view === 'BROWSE' ? fetchJobPosts(activeCategory ?? undefined) : view === 'WORKED' ? fetchMyWorkedJobPosts() : fetchMyJobPosts();
    fetcher
      .then((result) => { setPosts(result.posts); setTrustScores(result.trustScores); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load jobs.'));
  };

  useEffect(load, [view, activeCategory]);

  const categoryLabel = (id: string) => categories.find((c) => c.id === id)?.label ?? id;

  const handleContact = async (jobPostId: string) => {
    try {
      const conversation = await contactPoster(jobPostId);
      onMessagePoster(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this poster.');
    }
  };

  // Real 당근알바 job-post wishlist (2026-07-22) -- mirrors MarketplaceView's own
  // toggleFavorite field-for-field.
  const toggleFavorite = async (jobPostId: string) => {
    setFavoritingId(jobPostId);
    try {
      if (favoriteIds.has(jobPostId)) {
        await removeJobPostFavorite(jobPostId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(jobPostId); return next; });
      } else {
        await addJobPostFavorite(jobPostId);
        setFavoriteIds((prev) => new Set(prev).add(jobPostId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setFavoritingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'WORKED', 'APPLICATIONS', 'WISHLIST'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Find work' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My posts' : v === 'WORKED' ? 'Jobs I did' : v === 'APPLICATIONS' ? 'My applications' : '♡ Wishlist'}
          </button>
        ))}
      </div>

      {view === 'WISHLIST' ? (
        <JobPostWishlistView />
      ) : view === 'APPLICATIONS' ? (
        <MyJobApplicationsView />
      ) : (
        <>
          {(view === 'BROWSE' || view === 'NEIGHBORHOOD') && categories.length > 0 && (
            <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
              {categories.map((c) => (
                <button
                  key={c.id}
                  onClick={() => setActiveCategory(activeCategory === c.id ? null : c.id)}
                  style={{
                    whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                    border: `1px solid ${activeCategory === c.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                    color: activeCategory === c.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                    backgroundColor: activeCategory === c.id ? 'var(--toss-blue)' : 'transparent',
                  }}
                >
                  {c.label}
                </button>
              ))}
            </div>
          )}

          {view === 'MINE' && <NewJobPostCard categories={categories} onCreated={load} />}

          {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
            <NeighborhoodSetupPrompt onDone={() => load()} />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <NeighborhoodSwitcherRow
              secondNeighborhoodName={secondNeighborhoodName}
              onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
              onRemoved={(next) => setSecondNeighborhoodName(next)}
            />
          )}

          {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
            <NeighborhoodSetupPrompt
              isSecond
              onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
            />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
              Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
            </p>
          )}

          {error && (
            <ErrorCard message={error} onRetry={load} />
          )}
          {!error && posts === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
          {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && posts !== null && posts.length === 0 && (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                {view === 'BROWSE' ? 'No jobs posted yet.' : view === 'NEIGHBORHOOD' ? 'No jobs in your neighborhood yet.' : view === 'WORKED' ? 'No completed jobs recorded yet.' : "You haven't posted any jobs yet."}
              </p>
            </div>
          )}
          {!error && posts !== null && posts.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {posts.map((post) => (
                <JobPostCard
                  key={post.id}
                  post={post}
                  categoryLabel={categoryLabel(post.category)}
                  isMine={view === 'MINE' || post.posterId === currentUser?.id}
                  onChanged={load}
                  onContact={() => handleContact(post.id)}
                  favorited={favoriteIds.has(post.id)}
                  favoriteBusy={favoritingId === post.id}
                  onToggleFavorite={() => toggleFavorite(post.id)}
                  posterTrustScore={trustScores[post.posterId]}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

// ============================== PROPERTY (당근부동산) ==============================

function NewPropertyListingCard({ propertyTypes, onCreated }: { propertyTypes: PropertyType[]; onCreated: () => void }) {
  const [listingType, setListingType] = useState<PropertyListingType>('RENT');
  const [propertyType, setPropertyType] = useState(propertyTypes[0]?.id ?? '');
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [price, setPrice] = useState('');
  const [bedrooms, setBedrooms] = useState('');
  const [sizeSqm, setSizeSqm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await createPropertyListing(
        listingType, propertyType, title, description, Number(price),
        bedrooms ? Number(bedrooms) : undefined, sizeSqm ? Number(sizeSqm) : undefined,
      );
      setTitle('');
      setDescription('');
      setPrice('');
      setBedrooms('');
      setSizeSqm('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this listing.');
    } finally {
      setSubmitting(false);
    }
  };

  if (!open) {
    return (
      <button className="toss-btn toss-btn-primary" style={{ width: '100%', marginBottom: '16px' }} onClick={() => setOpen(true)}>
        + List a property
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700 }}>List a property</h3>
      <div style={{ display: 'flex', gap: '10px' }}>
        <select
          value={listingType} onChange={(e) => setListingType(e.target.value as PropertyListingType)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          <option value="RENT">For rent</option>
          <option value="SALE">For sale</option>
        </select>
        <select
          value={propertyType} onChange={(e) => setPropertyType(e.target.value)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          {propertyTypes.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
      </div>
      <input
        type="text" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. 2-bedroom apartment in Kacyiru" required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={description} onChange={(e) => setDescription(e.target.value)} placeholder="Describe the property" required rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      <div style={{ display: 'flex', gap: '10px' }}>
        <input
          type="number" value={price} onChange={(e) => setPrice(e.target.value)}
          placeholder={listingType === 'RENT' ? 'Rent per month (RWF)' : 'Price (RWF)'} required min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" value={bedrooms} onChange={(e) => setBedrooms(e.target.value)} placeholder="Bedrooms" min="0"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
        <input
          type="number" value={sizeSqm} onChange={(e) => setSizeSqm(e.target.value)} placeholder="Size (m²)" min="1"
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Listing…' : 'List it'}
        </button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function PropertyListingCard({ listing, propertyTypeLabel, isMine, onChanged, onContact, onMessageLister, favorited, favoriteBusy, onToggleFavorite, listerTrustScore }: {
  listing: PropertyListing; propertyTypeLabel: string; isMine: boolean; onChanged: () => void; onContact: () => void;
  onMessageLister: (conversationId: string) => void;
  favorited: boolean; favoriteBusy: boolean; onToggleFavorite: () => void; listerTrustScore?: number;
}) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService's
  // own doc comment; mirrors ListingCard's own offering state exactly.
  const [offering, setOffering] = useState(false);
  const [offerAmount, setOfferAmount] = useState('');

  // Real optional buyer/tenant identification at mark-taken time (2026-07-24) -- see
  // backend PropertyListingService.markTaken's own doc comment.
  const [markingTaken, setMarkingTaken] = useState(false);
  const [counterpartyPhone, setCounterpartyPhone] = useState('');

  // Real post-transaction review with asymmetric public/private visibility
  // (2026-07-24) -- see backend HoodReviewService's own doc comment.
  const [showReviewSheet, setShowReviewSheet] = useState(false);
  const [selectedGoodPoints, setSelectedGoodPoints] = useState<Set<string>>(new Set());
  const [selectedUncomfortablePoints, setSelectedUncomfortablePoints] = useState<Set<string>>(new Set());
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewSubmitted, setReviewSubmitted] = useState(false);
  // Real read-back (item 192) -- see lib/marketplace.ts's fetchListingReviews doc comment.
  const [hoodReviews, setHoodReviews] = useState<HoodReview[] | null>(null);
  const myUserId = getStoredUser()?.id;

  // Real ownership verification (2026-07-25, real upload added 2026-08-01) -- see
  // lib/realestate.ts's own doc comment on ownershipVerificationStatus. Was a
  // paste-a-URL text field (an honest v1 scope-down) until lib/upload.ts's real
  // POST /api/v1/uploads client -- mirrors PropertyScreen.kt's own pickOwnershipDoc
  // flow exactly now. submittedStatus is local so "pending" shows immediately without
  // a full listing refetch, mirroring Android's own submittedOwnershipStatus.
  const [showOwnershipForm, setShowOwnershipForm] = useState(false);
  const [ownershipDocUrl, setOwnershipDocUrl] = useState('');
  const [uploadingOwnershipDoc, setUploadingOwnershipDoc] = useState(false);
  const [submittingOwnership, setSubmittingOwnership] = useState(false);
  const [submittedOwnershipStatus, setSubmittedOwnershipStatus] = useState<string | null>(null);
  const ownershipStatus = submittedOwnershipStatus ?? listing.ownershipVerificationStatus ?? 'NONE';

  const handleOwnershipDocSelected = async (file: File | undefined) => {
    if (!file) return;
    setUploadingOwnershipDoc(true);
    setError(null);
    try {
      const { url } = await uploadFile(file);
      setOwnershipDocUrl(url);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not upload this document.');
    } finally {
      setUploadingOwnershipDoc(false);
    }
  };

  const handleSubmitOwnership = async () => {
    if (!ownershipDocUrl.trim()) return;
    setSubmittingOwnership(true);
    setError(null);
    try {
      await submitPropertyOwnershipVerification(listing.id, ownershipDocUrl.trim());
      setSubmittedOwnershipStatus('PENDING');
      setShowOwnershipForm(false);
      setOwnershipDocUrl('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this document.');
    } finally {
      setSubmittingOwnership(false);
    }
  };
  useEffect(() => {
    if (!(isMine && listing.status === 'TAKEN' && listing.counterpartyId)) return;
    fetchPropertyListingReviews(listing.id)
      .then((reviews) => {
        setHoodReviews(reviews);
        if (reviews.some((r) => r.reviewerId === myUserId)) setReviewSubmitted(true);
      })
      .catch(() => {
        // Real, non-critical -- the review form itself still works without this.
      });
  }, [listing.id, listing.status, listing.counterpartyId, isMine]);

  const priceLabel = `${listing.price.toLocaleString()} RWF${listing.listingType === 'RENT' ? '/mo' : ''}`;
  const detailsLabel = [
    listing.bedrooms != null ? `${listing.bedrooms} bd` : null,
    listing.sizeSqm != null ? `${listing.sizeSqm} m²` : null,
  ].filter(Boolean).join(' · ');

  const handleMakeOffer = async () => {
    const amount = Number(offerAmount);
    if (!amount || amount <= 0) return;
    setBusy(true);
    setError(null);
    try {
      const offer = await makePropertyOffer(listing.id, amount);
      setOffering(false);
      setOfferAmount('');
      onMessageLister(offer.conversationId);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not send this offer.');
    } finally {
      setBusy(false);
    }
  };

  const handleMarkTaken = async (counterpartyPhoneNumber?: string) => {
    setBusy(true);
    setError(null);
    try {
      await markPropertyListingTaken(listing.id, counterpartyPhoneNumber || undefined);
      setMarkingTaken(false);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update this listing.');
    } finally {
      setBusy(false);
    }
  };

  const handleSubmitReview = async () => {
    setSubmittingReview(true);
    setError(null);
    try {
      const review = await submitPropertyListingReview(listing.id, Array.from(selectedGoodPoints), Array.from(selectedUncomfortablePoints));
      setReviewSubmitted(true);
      setShowReviewSheet(false);
      setHoodReviews((prev) => [...(prev ?? []), review]);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
    } finally {
      setSubmittingReview(false);
    }
  };

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)' }}>
            {listing.listingType === 'RENT' ? 'For rent' : 'For sale'} · {propertyTypeLabel}
          </span>
          {listing.status === 'TAKEN' && (
            <span style={{ marginLeft: '8px', fontSize: '10px', fontWeight: 700, color: 'var(--toss-grey-500)', backgroundColor: 'var(--toss-grey-100)', padding: '2px 8px', borderRadius: '8px' }}>
              TAKEN
            </span>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          {/* Real 당근부동산 property-listing wishlist (2026-07-22) -- mirrors
              ListingCard's own WishlistButton reuse exactly, closing a
              docs/DESIGN_REFERENCES.md-named gap: Marketplace listings already had
              this, Property never did. */}
          {!isMine && <WishlistButton favorited={favorited} busy={favoriteBusy} onToggle={onToggleFavorite} />}
          <span style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{priceLabel}</span>
        </div>
      </div>
      <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{listing.title}</p>
      {detailsLabel && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{detailsLabel}</p>}
      {/* Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc
          comment. Only shown for someone else's listing. */}
      {!isMine && listerTrustScore != null && <TrustBadge score={listerTrustScore} />}
      {/* Real ownership verification badge (2026-07-25) -- shown to every viewer, not
          just the lister, a trust signal for the buyer/tenant deciding whether to
          contact this listing. Mirrors PropertyScreen.kt's own badge exactly. */}
      {ownershipStatus === 'VERIFIED' && (
        <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)', backgroundColor: 'rgba(49,130,246,0.12)', padding: '2px 8px', borderRadius: '8px', width: 'fit-content' }}>
          ✓ Owner verified
        </span>
      )}
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{listing.description}</p>
      {offering && (
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="number"
            value={offerAmount}
            onChange={(e) => setOfferAmount(e.target.value)}
            placeholder="Your offer (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={busy || !offerAmount} onClick={handleMakeOffer}>
            Send
          </button>
        </div>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      {/* Real optional "who's the buyer/tenant?" prompt (2026-07-24) -- see backend
          PropertyListingService.markTaken's own doc comment. */}
      {markingTaken && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <input
            type="tel"
            value={counterpartyPhone}
            onChange={(e) => setCounterpartyPhone(e.target.value)}
            placeholder="Their phone (optional)"
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkTaken()}>
              Skip
            </button>
            <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => handleMarkTaken(counterpartyPhone.trim())}>
              Confirm
            </button>
          </div>
        </div>
      )}
      {/* Real post-transaction review, preset checklist with asymmetric public/private
          visibility (2026-07-24) -- see backend HoodReviewService's own doc comment.
          Only offered once a real counterparty was recorded at mark-taken time. */}
      {isMine && listing.status === 'TAKEN' && listing.counterpartyId && reviewSubmitted && hoodReviews && (
        <HoodReviewResultView reviews={hoodReviews} myUserId={myUserId} />
      )}
      {isMine && listing.status === 'TAKEN' && listing.counterpartyId && !reviewSubmitted && (
        showReviewSheet ? (
          <HoodReviewForm
            selectedGoodPoints={selectedGoodPoints}
            onToggleGoodPoint={(id) => setSelectedGoodPoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            selectedUncomfortablePoints={selectedUncomfortablePoints}
            onToggleUncomfortablePoint={(id) => setSelectedUncomfortablePoints((prev) => { const next = new Set(prev); next.has(id) ? next.delete(id) : next.add(id); return next; })}
            submitting={submittingReview}
            onCancel={() => setShowReviewSheet(false)}
            onSubmit={handleSubmitReview}
          />
        ) : (
          <button className="toss-btn toss-btn-primary" disabled={busy} onClick={() => setShowReviewSheet(true)}>
            {listing.listingType === 'RENT' ? 'Rate this tenant' : 'Rate this buyer'}
          </button>
        )
      )}
      {/* Real ownership verification action (2026-07-25, real upload 2026-08-01) --
          NONE -> offer to upload a real deed/title photo; PENDING -> awaiting a real
          human reviewer, nothing to do; VERIFIED -> already covered by the badge
          above. Mirrors PropertyScreen.kt's own pickOwnershipDoc flow exactly. */}
      {isMine && ownershipStatus === 'NONE' && (
        showOwnershipForm ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              disabled={uploadingOwnershipDoc || submittingOwnership}
              onChange={(e) => handleOwnershipDocSelected(e.target.files?.[0])}
              style={{ fontSize: '13px' }}
            />
            {uploadingOwnershipDoc && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Uploading…</p>}
            {ownershipDocUrl && !uploadingOwnershipDoc && <p style={{ fontSize: '12px', color: 'var(--toss-green)' }}>✓ Document uploaded</p>}
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={submittingOwnership} onClick={() => { setShowOwnershipForm(false); setOwnershipDocUrl(''); }}>
                Cancel
              </button>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submittingOwnership || uploadingOwnershipDoc || !ownershipDocUrl.trim()} onClick={handleSubmitOwnership}>
                {submittingOwnership ? 'Submitting…' : 'Submit'}
              </button>
            </div>
          </div>
        ) : (
          <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => setShowOwnershipForm(true)}>
            Verify ownership
          </button>
        )
      )}
      {isMine && ownershipStatus === 'PENDING' && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Verification pending review</p>
      )}
      <div style={{ display: 'flex', gap: '10px' }}>
        {isMine ? (
          <>
            {listing.status === 'AVAILABLE' && !markingTaken && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={() => setMarkingTaken(true)}
              >
                Mark taken
              </button>
            )}
            {listing.status !== 'REMOVED' && (
              <button
                className="toss-btn toss-btn-secondary"
                disabled={busy}
                onClick={async () => {
                  setBusy(true);
                  try { await removePropertyListing(listing.id); onChanged(); }
                  catch (err) { setError(err instanceof ApiError ? err.message : 'Could not remove this listing.'); }
                  finally { setBusy(false); }
                }}
              >
                Remove
              </button>
            )}
          </>
        ) : (
          listing.status === 'AVAILABLE' && !offering && (
            <>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy} onClick={onContact}>
                Message lister
              </button>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy} onClick={() => setOffering(true)}>
                Make an offer
              </button>
            </>
          )
        )}
      </div>
      {!isMine && <HoodReportButton targetType="PROPERTY_LISTING" targetId={listing.id} />}
    </div>
  );
}

// Real 당근부동산 property-listing wishlist view (2026-07-22) -- mirrors
// ListingWishlistView exactly, closing a docs/DESIGN_REFERENCES.md-named gap.
function PropertyListingWishlistView() {
  const [favorites, setFavorites] = useState<FavoritePropertyListing[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoritePropertyListings().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your wishlist.'));
  };
  useEffect(load, []);

  const handleRemove = async (propertyListingId: string) => {
    setRemovingId(propertyListingId);
    try {
      await removePropertyListingFavorite(propertyListingId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this item.');
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved properties yet -- tap ♡ on any listing to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.propertyListingId} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <div>
            <p style={{ fontSize: '15px', fontWeight: 700 }}>{f.title}</p>
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{f.propertyType} · {f.price.toLocaleString()} RWF</p>
          </div>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={removingId === f.propertyListingId}
            onClick={() => handleRemove(f.propertyListingId)}
            style={{ padding: '8px 12px', fontSize: '12px' }}
          >
            {removingId === f.propertyListingId ? 'Removing…' : 'Remove'}
          </button>
        </div>
      ))}
    </div>
  );
}

function PropertyView({ onMessageLister }: { onMessageLister: (conversationId: string) => void }) {
  // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
  // recommendation #6, see backend PropertyListingRepository's own doc comment.
  const [view, setView] = useState<'BROWSE' | 'MINE' | 'ACQUIRED' | 'NEIGHBORHOOD' | 'WISHLIST' | 'VALUATION'>('BROWSE');
  const [propertyTypes, setPropertyTypes] = useState<PropertyType[]>([]);
  const [listingTypeFilter, setListingTypeFilter] = useState<PropertyListingType | null>(null);
  const [propertyTypeFilter, setPropertyTypeFilter] = useState<string | null>(null);
  const [listings, setListings] = useState<PropertyListing[] | null>(null);
  // Real Karrot-Score trust badge (2026-07-24) -- see TrustBadge's own doc comment.
  const [trustScores, setTrustScores] = useState<TrustScores>({});
  const [error, setError] = useState<string | null>(null);
  const [neighborhoodName, setNeighborhoodName] = useState<string | null | undefined>(undefined);
  const [secondNeighborhoodName, setSecondNeighborhoodName] = useState<string | null>(null);
  const [showSecondNeighborhoodPrompt, setShowSecondNeighborhoodPrompt] = useState(false);
  const [favoriteIds, setFavoriteIds] = useState<Set<string>>(new Set());
  const [favoritingId, setFavoritingId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  useEffect(() => {
    fetchPropertyTypes().then(setPropertyTypes).catch(() => { /* chips just won't render, browse still works */ });
  }, []);

  const loadFavoriteIds = () => {
    fetchMyFavoritePropertyListings().then((favs) => setFavoriteIds(new Set(favs.map((f) => f.propertyListingId)))).catch(() => {
      // Real, non-critical -- a wishlist-status fetch failure shouldn't block browsing.
    });
  };

  const load = () => {
    setError(null);
    setListings(null);
    loadFavoriteIds();
    if (view === 'NEIGHBORHOOD') {
      Promise.all([fetchProfile(), fetchPropertyListingsMyNeighborhood()])
        .then(([profile, result]) => {
          setNeighborhoodName(profile.neighborhood);
          setSecondNeighborhoodName(profile.secondNeighborhood);
          setListings(result.listings);
          setTrustScores(result.trustScores);
        })
        .catch((err) => {
          if (err instanceof ApiError && err.code === 'NEIGHBORHOOD_NOT_SET') {
            setNeighborhoodName(null);
            setListings([]);
          } else {
            setError(err instanceof ApiError ? err.message : 'Could not load your neighborhood.');
          }
        });
      return;
    }
    if (view === 'WISHLIST' || view === 'VALUATION') return;
    const fetcher = view === 'BROWSE'
      ? fetchPropertyListings(listingTypeFilter ?? undefined, propertyTypeFilter ?? undefined)
      : view === 'ACQUIRED'
        ? fetchMyAcquiredPropertyListings()
        : fetchMyPropertyListings();
    fetcher
      .then((result) => { setListings(result.listings); setTrustScores(result.trustScores); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load listings.'));
  };

  useEffect(load, [view, listingTypeFilter, propertyTypeFilter]);

  const propertyTypeLabel = (id: string) => propertyTypes.find((t) => t.id === id)?.label ?? id;

  const handleContact = async (propertyListingId: string) => {
    try {
      const conversation = await contactLister(propertyListingId);
      onMessageLister(conversation.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not message this lister.');
    }
  };

  // Real 당근부동산 property-listing wishlist (2026-07-22) -- mirrors MarketplaceView's
  // own toggleFavorite field-for-field.
  const toggleFavorite = async (propertyListingId: string) => {
    setFavoritingId(propertyListingId);
    try {
      if (favoriteIds.has(propertyListingId)) {
        await removePropertyListingFavorite(propertyListingId);
        setFavoriteIds((prev) => { const next = new Set(prev); next.delete(propertyListingId); return next; });
      } else {
        await addPropertyListingFavorite(propertyListingId);
        setFavoriteIds((prev) => new Set(prev).add(propertyListingId));
      }
    } catch {
      // Real, non-critical -- a wishlist toggle failure shouldn't block browsing.
    } finally {
      setFavoritingId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'NEIGHBORHOOD', 'MINE', 'ACQUIRED', 'WISHLIST', 'VALUATION'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Browse' : v === 'NEIGHBORHOOD' ? 'Neighborhood' : v === 'MINE' ? 'My listings' : v === 'ACQUIRED' ? 'Places I got' : v === 'WISHLIST' ? '♡ Wishlist' : '시세 Value'}
          </button>
        ))}
      </div>

      {view === 'WISHLIST' ? (
        <PropertyListingWishlistView />
      ) : view === 'VALUATION' ? (
        <PropertyValuationCard propertyTypes={propertyTypes} />
      ) : (
        <>
          {view === 'BROWSE' && (
            <div style={{ display: 'flex', gap: '6px', marginBottom: '10px' }}>
              {(['RENT', 'SALE'] as const).map((t) => (
                <button
                  key={t}
                  onClick={() => setListingTypeFilter(listingTypeFilter === t ? null : t)}
                  style={{
                    padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                    border: `1px solid ${listingTypeFilter === t ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                    color: listingTypeFilter === t ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                    backgroundColor: listingTypeFilter === t ? 'var(--toss-blue)' : 'transparent',
                  }}
                >
                  {t === 'RENT' ? 'For rent' : 'For sale'}
                </button>
              ))}
            </div>
          )}

          {view === 'BROWSE' && propertyTypes.length > 0 && (
            <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', marginBottom: '12px', paddingBottom: '2px' }}>
              {propertyTypes.map((t) => (
                <button
                  key={t.id}
                  onClick={() => setPropertyTypeFilter(propertyTypeFilter === t.id ? null : t.id)}
                  style={{
                    whiteSpace: 'nowrap', padding: '6px 12px', borderRadius: '999px', fontSize: '12px', fontWeight: 700,
                    border: `1px solid ${propertyTypeFilter === t.id ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                    color: propertyTypeFilter === t.id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                    backgroundColor: propertyTypeFilter === t.id ? 'var(--toss-blue)' : 'transparent',
                  }}
                >
                  {t.label}
                </button>
              ))}
            </div>
          )}

          {view === 'MINE' && <NewPropertyListingCard propertyTypes={propertyTypes} onCreated={load} />}

          {view === 'NEIGHBORHOOD' && neighborhoodName === null && (
            <NeighborhoodSetupPrompt onDone={() => load()} />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <NeighborhoodSwitcherRow
              secondNeighborhoodName={secondNeighborhoodName}
              onAddTapped={() => setShowSecondNeighborhoodPrompt(true)}
              onRemoved={(next) => setSecondNeighborhoodName(next)}
            />
          )}

          {view === 'NEIGHBORHOOD' && showSecondNeighborhoodPrompt && (
            <NeighborhoodSetupPrompt
              isSecond
              onDone={(name) => { setSecondNeighborhoodName(name); setShowSecondNeighborhoodPrompt(false); }}
            />
          )}

          {view === 'NEIGHBORHOOD' && neighborhoodName && (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '12px', padding: '0 4px' }}>
              Your neighborhood: <strong style={{ color: 'var(--toss-grey-900)' }}>{neighborhoodName}</strong>
            </p>
          )}

          {error && (
            <ErrorCard message={error} onRetry={load} />
          )}
          {!error && listings === null && <div className="toss-card skeleton" style={{ height: '220px' }} />}
          {!error && (view !== 'NEIGHBORHOOD' || neighborhoodName) && listings !== null && listings.length === 0 && (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                {view === 'BROWSE' ? 'No properties listed yet.' : view === 'NEIGHBORHOOD' ? 'No properties in your neighborhood yet.' : view === 'ACQUIRED' ? 'No properties acquired yet.' : "You haven't listed any properties yet."}
              </p>
            </div>
          )}
          {!error && listings !== null && listings.length > 0 && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {listings.map((listing) => (
                <PropertyListingCard
                  key={listing.id}
                  listing={listing}
                  propertyTypeLabel={propertyTypeLabel(listing.propertyType)}
                  isMine={view === 'MINE' || listing.listerId === currentUser?.id}
                  onChanged={load}
                  onContact={() => handleContact(listing.id)}
                  onMessageLister={onMessageLister}
                  favorited={favoriteIds.has(listing.id)}
                  favoriteBusy={favoritingId === listing.id}
                  onToggleFavorite={() => toggleFavorite(listing.id)}
                  listerTrustScore={trustScores[listing.listerId]}
                />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see
// lib/realestate.ts's own doc comment. Read-only: enter a location + size, get a real
// comparable-listings-based estimate, nothing persisted.
function PropertyValuationCard({ propertyTypes }: { propertyTypes: PropertyType[] }) {
  const [latitude, setLatitude] = useState('');
  const [longitude, setLongitude] = useState('');
  const [propertyType, setPropertyType] = useState('');
  const [listingType, setListingType] = useState<PropertyListingType>('SALE');
  const [sizeSqm, setSizeSqm] = useState('');
  const [estimate, setEstimate] = useState<PropertyValuationEstimate | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  const useMyLocation = () => {
    if (!navigator.geolocation) return;
    navigator.geolocation.getCurrentPosition((pos) => {
      setLatitude(String(pos.coords.latitude));
      setLongitude(String(pos.coords.longitude));
    });
  };

  const handleEstimate = async () => {
    const lat = Number(latitude);
    const lng = Number(longitude);
    const size = Number(sizeSqm);
    if (!propertyType || Number.isNaN(lat) || Number.isNaN(lng) || Number.isNaN(size) || size <= 0) {
      setError('Fill in a real location, property type, and size.');
      return;
    }
    setLoading(true);
    setError(null);
    setEstimate(null);
    try {
      const result = await fetchPropertyValuation(lat, lng, propertyType, listingType, size);
      setEstimate(result);
    } catch (err) {
      if (err instanceof ApiError && err.code === 'INSUFFICIENT_COMPARABLES') {
        setError(err.message);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not estimate a value.');
      }
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="toss-card">
      <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '4px' }}>우리집 시세 — Estimate my home's value</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
        A real estimate based on comparable listings near you, not a fabricated number.
      </p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        <div style={{ display: 'flex', gap: '8px' }}>
          <input
            type="text" value={latitude} placeholder="Latitude" onChange={(e) => setLatitude(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <input
            type="text" value={longitude} placeholder="Longitude" onChange={(e) => setLongitude(e.target.value)}
            style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button className="toss-btn toss-btn-secondary" onClick={useMyLocation} style={{ padding: '10px 12px', fontSize: '12px' }}>
            📍
          </button>
        </div>
        <select
          value={propertyType} onChange={(e) => setPropertyType(e.target.value)}
          style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        >
          <option value="">Property type…</option>
          {propertyTypes.map((t) => <option key={t.id} value={t.id}>{t.label}</option>)}
        </select>
        <div style={{ display: 'flex', gap: '8px' }}>
          {(['SALE', 'RENT'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setListingType(t)}
              style={{
                flex: 1, padding: '8px', borderRadius: '8px', fontSize: '12px', fontWeight: 700,
                border: `1px solid ${listingType === t ? 'var(--toss-blue)' : 'var(--toss-grey-200)'}`,
                color: listingType === t ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: listingType === t ? 'var(--toss-blue)' : 'transparent',
              }}
            >
              {t === 'SALE' ? 'For sale' : 'For rent'}
            </button>
          ))}
        </div>
        <input
          type="text" value={sizeSqm} placeholder="Size (sqm)" onChange={(e) => setSizeSqm(e.target.value)}
          style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
        {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
        <button className="toss-btn toss-btn-primary" disabled={loading} onClick={handleEstimate}>
          {loading ? 'Estimating…' : 'Estimate value'}
        </button>
        {estimate && (
          <div style={{ marginTop: '8px', padding: '12px', borderRadius: '10px', backgroundColor: 'var(--toss-grey-100)' }}>
            <p style={{ fontSize: '24px', fontWeight: 700 }}>{estimate.estimatedValue.toLocaleString()} RWF</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
              Based on {estimate.comparableCount} comparable listing{estimate.comparableCount === 1 ? '' : 's'} within {estimate.radiusKm}km ({estimate.averagePricePerSqm.toLocaleString()} RWF/sqm avg)
            </p>
          </div>
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

function EatsOrderCard({ order, restaurant, action }: { order: EatsOrder; restaurant?: ShoppingMerchant; action?: React.ReactNode }) {
  const [showRoute, setShowRoute] = useState(false);
  const [showLiveTracking, setShowLiveTracking] = useState(false);
  const canShowRoute = restaurant?.latitude != null && restaurant?.longitude != null && order.deliveryLatitude != null && order.deliveryLongitude != null;
  // Real live rider tracking (2026-07-20) -- only meaningful while a real rider is
  // actually en route, matching EatsOrderService.getRiderLocation's own real state gate
  // (RIDER_ASSIGNED/PICKED_UP only; before/after that there's honestly nothing to show).
  const canShowLiveTracking = canShowRoute && (order.status === 'RIDER_ASSIGNED' || order.status === 'PICKED_UP');

  return (
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{EATS_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.deliveryNotes && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)', backgroundColor: 'var(--toss-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.deliveryNotes}
        </p>
      )}
      {canShowLiveTracking && (
        <button className="toss-btn toss-btn-primary" onClick={() => { setShowLiveTracking((v) => !v); setShowRoute(false); }}>
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
        <button className="toss-btn toss-btn-secondary" onClick={() => setShowRoute((v) => !v)}>
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
          <Star size={22} color={n <= value ? '#F5A623' : 'var(--toss-grey-200)'} fill={n <= value ? '#F5A623' : 'none'} />
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

  useEffect(() => {
    fetchRestaurantRating(restaurantId).then(setRating).catch(() => {
      // Real, non-critical -- a rating fetch failure shouldn't block browsing the menu.
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
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '13px', color: 'var(--toss-grey-700)', padding: 0 }}
      >
        <Star size={14} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {reviews === null ? (
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.restaurantRating)}{'☆'.repeat(5 - r.restaurantRating)}</span>
                {r.restaurantComment && <span> — {r.restaurantComment}</span>}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--toss-grey-500)' }}>
                    ↳ Restaurant: {r.ownerReply}
                  </div>
                )}
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}

// Real owner-reply management (item 184) -- a restaurant owner's own reviews, with an
// inline reply form for anything not yet replied to. Lives on RestaurantOrdersView
// (the owner's own dashboard) since that's the only place this app already resolves
// "my own restaurant id" for an Eats seller.
function RestaurantReviewsManageView({ restaurantId }: { restaurantId: string }) {
  const [reviews, setReviews] = useState<EatsReview[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    fetchRestaurantReviews(restaurantId).then(setReviews).catch((err) => {
      setError(err instanceof ApiError ? err.message : 'Could not load your reviews.');
    });
  };
  useEffect(load, [restaurantId]);

  if (error) return <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>;
  if (reviews === null) return <div className="toss-card skeleton" style={{ height: '80px' }} />;
  if (reviews.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Reviews for your restaurant</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {reviews.map((r) => <RestaurantReviewReplyCard key={r.id} review={r} onReplied={load} />)}
      </div>
    </div>
  );
}

function RestaurantReviewReplyCard({ review, onReplied }: { review: EatsReview; onReplied: () => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not submit your reply.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="toss-card" style={{ padding: '14px' }}>
      <span style={{ color: '#F5A623', fontSize: '13px' }}>{'★'.repeat(review.restaurantRating)}{'☆'.repeat(5 - review.restaurantRating)}</span>
      {review.restaurantComment && <p style={{ fontSize: '13px', marginTop: '4px' }}>{review.restaurantComment}</p>}
      {review.ownerReply ? (
        <div style={{ marginTop: '8px', paddingLeft: '10px', borderLeft: '2px solid var(--toss-grey-200)', fontSize: '12px', color: 'var(--toss-grey-700)' }}>
          Your reply: {review.ownerReply}
        </div>
      ) : replying ? (
        <form onSubmit={handleSubmit} style={{ marginTop: '8px', display: 'flex', gap: '8px' }}>
          <input
            type="text" placeholder="Write a reply…" value={reply} onChange={(e) => setReply(e.target.value)} required
            style={{ flex: 1, padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ fontSize: '12px', padding: '8px 12px' }}>
            {submitting ? '…' : 'Reply'}
          </button>
        </form>
      ) : (
        <button
          type="button"
          onClick={() => setReplying(true)}
          className="toss-btn toss-btn-secondary"
          style={{ marginTop: '8px', fontSize: '12px', padding: '6px 10px' }}
        >
          Reply
        </button>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function ReviewOrderCard({ order, onSubmitted }: { order: EatsOrder; onSubmitted: () => void }) {
  const [open, setOpen] = useState(false);
  const [restaurantRating, setRestaurantRating] = useState(0);
  const [restaurantComment, setRestaurantComment] = useState('');
  const [riderRating, setRiderRating] = useState(0);
  const [riderComment, setRiderComment] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

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
      await submitEatsReview(order.id, restaurantRating, restaurantComment, hasRider ? riderRating : null, riderComment);
      setDone(true);
      onSubmitted();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ORDER_ALREADY_REVIEWED') {
        setDone(true);
      } else {
        setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Thanks for your review!</p>;
  }

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" onClick={() => setOpen(true)}>
        Rate this order
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginTop: '8px' }}>
      <div>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>Restaurant</p>
        <StarRatingInput value={restaurantRating} onChange={setRestaurantRating} />
        <input
          type="text"
          value={restaurantComment}
          onChange={(e) => setRestaurantComment(e.target.value)}
          placeholder="How was the food? (optional)"
          style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
      </div>
      {hasRider && (
        <div>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '4px' }}>Rider</p>
          <StarRatingInput value={riderRating} onChange={setRiderRating} />
          <input
            type="text"
            value={riderComment}
            onChange={(e) => setRiderComment(e.target.value)}
            placeholder="How was the delivery? (optional)"
            style={{ marginTop: '6px', width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          />
        </div>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
          {submitting ? 'Submitting…' : 'Submit review'}
        </button>
      </div>
    </form>
  );
}

function AddressAutocomplete({
  value, onChangeText, onSelectSuggestion,
}: {
  value: string;
  onChangeText: (text: string) => void;
  onSelectSuggestion: (suggestion: AddressSuggestion) => void;
}) {
  const [suggestions, setSuggestions] = useState<AddressSuggestion[]>([]);
  const [open, setOpen] = useState(false);
  const [searching, setSearching] = useState(false);
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null);

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
        onFocus={() => setOpen(suggestions.length > 0)}
        onBlur={() => setTimeout(() => setOpen(false), 150)}
        placeholder="Delivery address" required autoComplete="off"
        style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      {searching && (
        <span style={{ position: 'absolute', right: '12px', top: '12px', fontSize: '12px', color: 'var(--toss-grey-500)' }}>…</span>
      )}
      {open && (
        <div
          className="toss-card"
          style={{ position: 'absolute', top: '100%', left: 0, right: 0, marginTop: '4px', padding: '6px', zIndex: 10, maxHeight: '220px', overflowY: 'auto' }}
        >
          {suggestions.map((s, i) => (
            <button
              key={i}
              type="button"
              onMouseDown={() => { onSelectSuggestion(s); setOpen(false); }}
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '8px 6px', fontSize: '13px', borderRadius: '6px' }}
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
  const [pendingChoices, setPendingChoices] = useState<Record<string, string>>({});
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this menu.'));
  };

  useEffect(load, [restaurant.merchantId]);

  const cartItems = Object.entries(cart).filter(([, line]) => line.quantity > 0);
  const cartCount = cartItems.reduce((sum, [, line]) => sum + line.quantity, 0);

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
    const choiceIds = groups.map((g) => pendingChoices[g.id]).filter((id): id is string => Boolean(id));
    if (choiceIds.length !== groups.length) return; // one real required choice per group, enforced client-side too
    const key = eatsCartKey(item.id, choiceIds);
    setCart((c) => ({ ...c, [key]: { productId: item.id, quantity: (c[key]?.quantity ?? 0) + 1, choiceIds } }));
    setPendingChoices({});
    setExpandedProductId(null);
  };

  const handlePlaceOrder = async (e: React.FormEvent) => {
    e.preventDefault();
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
        setError(err instanceof ApiError ? err.message : 'Could not place this order.');
      }
    } finally {
      setPlacing(false);
    }
  };

  if (needsDeviceVerification) {
    return (
      <div className="toss-card">
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
      </div>
    );
  }

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }

  if (menu === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to menu">
            <ArrowLeft size={20} />
          </button>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '14px' }}>
                <span>{item.name}{eatsOptionsSummary(item, line.choiceIds)} x{line.quantity}</span>
                <span>{(unitPrice * line.quantity).toLocaleString()} RWF</span>
              </div>
            );
          })}
          <div style={{ display: 'flex', gap: '4px', padding: '4px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
            {(['DELIVERY', 'PICKUP'] as const).map((ft) => (
              <button
                key={ft}
                type="button"
                onClick={() => setFulfillmentType(ft)}
                style={{
                  flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
                  color: fulfillmentType === ft ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                  backgroundColor: fulfillmentType === ft ? 'var(--toss-blue)' : 'transparent',
                }}
              >
                {ft === 'DELIVERY' ? 'Delivery' : 'Pickup'}
              </button>
            ))}
          </div>
          {fulfillmentType === 'PICKUP' ? (
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
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
                <p style={{ fontSize: '12px', color: 'var(--toss-green)' }}>Pinned -- real distance-based delivery fee applies</p>
              )}
            </>
          )}
          <textarea
            value={deliveryNotes}
            onChange={(e) => setDeliveryNotes(e.target.value.slice(0, 500))}
            placeholder={fulfillmentType === 'PICKUP' ? 'Pickup notes (optional)' : 'Delivery notes (optional) -- e.g. Leave at the gate, call on arrival'}
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || (fulfillmentType === 'DELIVERY' && !address.trim())}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to restaurants">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      <div style={{ marginBottom: '12px' }}>
        <RestaurantRatingBadge restaurantId={restaurant.merchantId} />
      </div>
      {menu.products.length === 0 ? (
        <EmptyState message="No menu items yet." />
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
              <div key={item.id} className="toss-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: '15px', fontWeight: 700 }}>{item.name}</p>
                    <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                      {item.price.toLocaleString()} RWF{hasOptions ? ' · options required' : ''}
                    </p>
                  </div>
                  {hasOptions ? (
                    <button onClick={() => toggleExpand(item.id)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px', fontSize: '12px' }}>
                      {isExpanded ? 'Close' : 'Choose options'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {hasOptions && isExpanded && (
                  <div style={{ marginTop: '14px', paddingTop: '14px', borderTop: '1px solid var(--toss-grey-200)', display: 'flex', flexDirection: 'column', gap: '12px' }}>
                    {groups.map((group) => (
                      <div key={group.id}>
                        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '6px' }}>
                          {group.name} <span style={{ color: 'var(--toss-grey-400)', fontWeight: 400 }}>· choose 1</span>
                        </p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                          {group.choices.map((choice) => (
                            <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px', cursor: 'pointer' }}>
                              <input
                                type="radio"
                                name={`eats-option-group-${group.id}`}
                                checked={pendingChoices[group.id] === choice.id}
                                onChange={() => setPendingChoices((p) => ({ ...p, [group.id]: choice.id }))}
                              />
                              {choice.name}{choice.priceDelta > 0 ? ` (+${choice.priceDelta.toLocaleString()} RWF)` : ''}
                            </label>
                          ))}
                        </div>
                      </div>
                    ))}
                    <button
                      type="button"
                      className="toss-btn toss-btn-primary"
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
        <div className="toss-card" style={{ marginBottom: '80px', marginTop: '-2px' }}>
          <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '10px' }}>Your cart</p>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {cartItems.map(([key, line]) => {
              const item = menu.products.find((p) => p.id === line.productId);
              if (!item) return null;
              return (
                <div key={key} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '13px' }}>{item.name}{eatsOptionsSummary(item, line.choiceIds)}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <button onClick={() => setLineQty(key, line, line.quantity - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '4px 10px' }}>−</button>
                    <span style={{ minWidth: '14px', textAlign: 'center', fontWeight: 700, fontSize: '13px' }}>{line.quantity}</span>
                    <button onClick={() => setLineQty(key, line, line.quantity + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '4px 10px' }}>+</button>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}
      {cartCount > 0 && (
        <button
          className="toss-btn toss-btn-primary"
          style={{ position: 'fixed', bottom: '24px', left: '20px', right: '20px', maxWidth: '440px', margin: '0 auto' }}
          onClick={() => setShowCheckout(true)}
        >
          Checkout ({cartCount} item{cartCount === 1 ? '' : 's'})
        </button>
      )}
    </div>
  );
}

function MyEatsOrdersView({ onReorder, reorderingId, restaurants }: { onReorder: (order: EatsOrder) => void; reorderingId: string | null; restaurants: ShoppingMerchant[] | null }) {
  const [orders, setOrders] = useState<EatsOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyEatsOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your orders.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this order.');
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return <EmptyState message="No orders yet." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <EatsOrderCard
          key={o.id}
          order={o}
          restaurant={restaurants?.find((r) => r.merchantId === o.restaurantId)}
          action={
            o.status === 'PLACED' ? (
              <button className="toss-btn toss-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
                {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            ) : o.status === 'DELIVERED' ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                <ReviewOrderCard order={o} onSubmitted={load} />
                <button className="toss-btn toss-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
                  {reorderingId === o.id ? 'Reordering…' : 'Reorder'}
                </button>
              </div>
            ) : o.status === 'CANCELLED' ? (
              <button className="toss-btn toss-btn-secondary" disabled={reorderingId === o.id} onClick={() => onReorder(o)}>
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
        className="toss-card"
        style={{ width: '100%', padding: '12px 16px', fontSize: '14px', marginBottom: '10px', border: 'none' }}
      />
      {categories.length > 0 && (
        <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px', marginBottom: '14px' }}>
          <button
            onClick={() => onSelectCategory(null)}
            style={{
              flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: '12px', fontWeight: 700,
              color: selectedCategory === null ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: selectedCategory === null ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
            }}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c}
              onClick={() => onSelectCategory(c === selectedCategory ? null : c)}
              style={{
                flexShrink: 0, padding: '6px 14px', borderRadius: '16px', fontSize: '12px', fontWeight: 700,
                color: selectedCategory === c ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                backgroundColor: selectedCategory === c ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
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

function OrderFoodView() {
  const [view, setView] = useState<'BROWSE' | 'FAVORITES' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  // Unfiltered, fetched once -- used to resolve a past order's restaurant for Reorder
  // even when that restaurant has been filtered out of the currently-browsed list.
  const [allRestaurants, setAllRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [categories, setCategories] = useState<string[]>([]);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [searchInput, setSearchInput] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<EatsOrder | null>(null);
  const [reorderCart, setReorderCart] = useState<Record<string, number> | null>(null);
  const [reorderingId, setReorderingId] = useState<string | null>(null);
  const [reorderError, setReorderError] = useState<string | null>(null);
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
    fetchRestaurants(selectedCategory ?? undefined, debouncedSearch || undefined)
      .then(setRestaurants)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load restaurants.'));
  };

  useEffect(load, [selectedCategory, debouncedSearch]);

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
      setReorderError(err instanceof ApiError ? err.message : 'Could not reorder.');
    } finally {
      setReorderingId(null);
    }
  };

  if (confirmed) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>Delivering to {confirmed.deliveryAddress}</p>
        <button
          className="toss-btn toss-btn-secondary"
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
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'FAVORITES', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : v === 'FAVORITES' ? 'Favorites' : 'My orders'}
          </button>
        ))}
      </div>

      {view === 'ORDERS' ? (
        <>
          {reorderError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '10px' }} role="alert">{reorderError}</p>}
          <MyEatsOrdersView onReorder={handleReorder} reorderingId={reorderingId} restaurants={allRestaurants} />
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
          {error ? (
            <ErrorCard message={error} onRetry={load} />
          ) : restaurants === null ? (
            <div className="toss-card skeleton" style={{ height: '220px' }} />
          ) : restaurants.length === 0 ? (
            <div className="toss-card">
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
                {selectedCategory || debouncedSearch ? 'No restaurants match your search.' : 'No restaurants registered yet.'}
              </p>
            </div>
          ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {restaurants.map((r) => (
            <div
              key={r.merchantId}
              role="button"
              tabIndex={0}
              onClick={() => setSelected(r)}
              onKeyDown={(e) => { if (e.key === 'Enter') setSelected(r); }}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%', cursor: 'pointer' }}
            >
              {r.photoUrl ? (
                <img
                  src={r.photoUrl} alt=""
                  style={{ width: '44px', height: '44px', borderRadius: '12px', objectFit: 'cover', flexShrink: 0, backgroundColor: 'var(--toss-blue-light)' }}
                  onError={(e) => { e.currentTarget.style.display = 'none'; }}
                />
              ) : (
                <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                  <Utensils size={20} color="var(--toss-blue)" />
                </div>
              )}
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{r.businessName}</p>
                {/* Real browse-card enrichment (2026-07-21) -- rating/reviewCount/distance/
                    delivery-time estimate/min order, closing docs/DESIGN_REFERENCES.md's
                    Eats recommendations #1/#2. Every clause is conditionally rendered on
                    real data being present -- never a fabricated placeholder. */}
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', display: 'flex', alignItems: 'center', gap: '4px', flexWrap: 'wrap' }}>
                  {r.category && <span>{r.category}</span>}
                  {r.rating != null && (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: '2px' }}>
                      <Star size={11} color="#F5A623" fill="#F5A623" /> {r.rating.toFixed(1)} ({r.reviewCount})
                    </span>
                  )}
                  {r.distanceKm != null && <span>· {r.distanceKm.toFixed(1)} km</span>}
                  {r.deliveryTimeMinutes != null && <span>· ~{r.deliveryTimeMinutes} min</span>}
                  {r.minOrderAmount != null && <span>· Min {r.minOrderAmount.toLocaleString()} RWF</span>}
                  {!r.category && r.rating == null && r.distanceKm == null && <span>Real menu, real delivery</span>}
                </p>
              </div>
              <button
                onClick={(e) => { e.stopPropagation(); toggleFavorite(r.merchantId); }}
                disabled={favoritingId === r.merchantId}
                aria-label={favoriteIds.has(r.merchantId) ? 'Remove from favorites' : 'Add to favorites'}
                style={{ padding: '6px', flexShrink: 0 }}
              >
                <Heart size={20} color={favoriteIds.has(r.merchantId) ? '#E53935' : 'var(--toss-grey-400)'} fill={favoriteIds.has(r.merchantId) ? '#E53935' : 'none'} />
              </button>
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
function FavoriteRestaurantsView({ onOpen, onChanged }: { onOpen: (favorite: FavoriteRestaurant) => void; onChanged: () => void }) {
  const [favorites, setFavorites] = useState<FavoriteRestaurant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteRestaurants().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load favorites.'));
  };

  useEffect(load, []);

  const handleRemove = async (restaurantId: string) => {
    setRemovingId(restaurantId);
    try {
      await removeFavoriteRestaurant(restaurantId);
      setFavorites((prev) => prev?.filter((f) => f.restaurantId !== restaurantId) ?? prev);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove favorite.');
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
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }
  if (favorites.length === 0) {
    return <EmptyState message="No favorite restaurants yet. Tap the heart on a restaurant to save it here." />;
  }
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div
          key={f.restaurantId}
          role="button"
          tabIndex={0}
          onClick={() => onOpen(f)}
          onKeyDown={(e) => { if (e.key === 'Enter') onOpen(f); }}
          className="toss-card"
          style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%', cursor: 'pointer' }}
        >
          <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
            <Utensils size={20} color="var(--toss-blue)" />
          </div>
          <div style={{ flex: 1 }}>
            <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{f.businessName}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{f.category ? `${f.category} · Real menu, real delivery` : 'Real menu, real delivery'}</p>
          </div>
          <button
            onClick={(e) => { e.stopPropagation(); handleRemove(f.restaurantId); }}
            disabled={removingId === f.restaurantId}
            aria-label="Remove from favorites"
            style={{ padding: '6px', flexShrink: 0 }}
          >
            <Heart size={20} color="#E53935" fill="#E53935" />
          </button>
        </div>
      ))}
    </div>
  );
}

function DeliverView() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your rider profile.');
        }
      });
  };

  useEffect(loadRider, []);

  const loadDeliveries = () => {
    Promise.all([fetchAvailableDeliveries(), fetchRiderDeliveries()])
      .then(([a, m]) => { setAvailable(a); setMine(m); })
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load deliveries.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not register as a rider.');
    } finally {
      setRegistering(false);
    }
  };

  const handleToggleAvailable = async () => {
    if (!rider) return;
    try {
      setRider(await setRiderAvailability(!rider.available));
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not update your availability.');
    }
  };

  const handleClaim = async (orderId: string) => {
    setBusyOrderId(orderId);
    setError(null);
    try {
      await claimDelivery(orderId);
      loadDeliveries();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not claim this delivery.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update this delivery.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (rider === undefined) return <div className="toss-card skeleton" style={{ height: '180px' }} />;

  if (rider === null) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <Bike size={32} color="var(--toss-blue)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Deliver with Itunda</h3>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          Earn a real delivery fee for every order you deliver, paid straight to your wallet.
        </p>
        <button className="toss-btn toss-btn-primary" onClick={handleRegister} disabled={registering}>
          {registering ? 'Registering…' : 'Become a rider'}
        </button>
        {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '12px' }} role="alert">{error}</p>}
      </div>
    );
  }

  const activeDeliveries = (mine ?? []).filter((o) => o.status !== 'DELIVERED');
  const pastDeliveries = (mine ?? []).filter((o) => o.status === 'DELIVERED');

  return (
    <div>
      <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
        <div>
          <p style={{ fontSize: '15px', fontWeight: 700 }}>{rider.available ? "You're online" : "You're offline"}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{rider.available ? 'Visible for new deliveries' : 'Go online to see deliveries'}</p>
        </div>
        <button className={rider.available ? 'toss-btn toss-btn-danger' : 'toss-btn toss-btn-primary'} onClick={handleToggleAvailable}>
          {rider.available ? 'Go offline' : 'Go online'}
        </button>
      </div>

      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{error}</p>}

      {activeDeliveries.length > 0 && (
        <div style={{ marginBottom: '20px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active deliveries</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {activeDeliveries.map((o) => {
              const next = nextInChain(RIDER_STATUS_CHAIN, o.status);
              return (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={next && (
                    <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
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
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Available deliveries</h4>
          {available === null ? (
            <div className="toss-card skeleton" style={{ height: '100px' }} />
          ) : available.length === 0 ? (
            <EmptyState message="No deliveries waiting right now." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {available.map((o) => (
                <EatsOrderCard
                  key={o.id}
                  order={o}
                  action={
                    <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleClaim(o.id)}>
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
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {pastDeliveries.map((o) => <EatsOrderCard key={o.id} order={o} />)}
          </div>
        </div>
      )}
    </div>
  );
}

function RestaurantOrdersView() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your restaurant orders.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
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
      setError(err instanceof ApiError ? err.message : 'Could not complete this pickup.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your restaurant</h4>
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
                  <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleCompletePickup(o)}>
                    {busyOrderId === o.id ? 'Updating…' : 'Mark picked up'}
                  </button>
                ) : next && (
                  <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
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

  useEffect(() => {
    if (!query || query === value?.displayName) { setResults(null); return; }
    const handle = setTimeout(() => {
      searchPlaces(query).then(setResults).catch(() => setResults([]));
    }, 350);
    return () => clearTimeout(handle);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query]);

  return (
    <div style={{ position: 'relative', marginBottom: '12px' }}>
      <label style={{ fontSize: '12px', color: 'var(--toss-grey-500)', fontWeight: 700, display: 'block', marginBottom: '4px' }}>{label}</label>
      <input
        type="text" value={query} placeholder={placeholder}
        onChange={(e) => setQuery(e.target.value)}
        style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      {results && results.length > 0 && (
        <div className="toss-card" style={{ position: 'absolute', zIndex: 10, width: '100%', marginTop: '4px', padding: '4px', maxHeight: '220px', overflowY: 'auto' }}>
          {results.map((r, i) => (
            <button
              key={`${r.latitude}-${r.longitude}-${i}`} type="button"
              style={{ display: 'block', width: '100%', textAlign: 'left', padding: '10px', fontSize: '13px', borderRadius: '6px' }}
              onClick={() => { onSelect(r); setQuery(r.displayName); setResults(null); }}
            >
              {r.displayName}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}

function RideTripCard({ trip, action, stops }: { trip: RideTrip; action?: React.ReactNode; stops?: RideTripStop[] | null }) {
  return (
    <div className="toss-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>{trip.pickupAddress}</p>
          {stops && stops.length > 0 && stops.map((s) => (
            <p key={s.id} style={{ fontSize: '11px', color: s.arrivedAt ? 'var(--toss-grey-400)' : 'var(--toss-grey-700)', margin: '1px 0' }}>
              {s.arrivedAt ? '✓' : '→'} {s.address}
            </p>
          ))}
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString()} RWF</p>
          {trip.scheduledFor && (
            <p style={{ fontSize: '11px', color: 'var(--toss-blue)', fontWeight: 700, marginTop: '2px' }}>
              🕒 Scheduled for {new Date(trip.scheduledFor).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
            </p>
          )}
        </div>
        <span style={{
          fontSize: '11px', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? '#E53935' : trip.status === 'COMPLETED' ? 'var(--toss-grey-500)' : 'var(--toss-blue)',
          backgroundColor: trip.status === 'CANCELLED' ? '#FDECEA' : trip.status === 'COMPLETED' ? 'var(--toss-grey-100)' : '#E8F0FE',
        }}>
          {trip.status === 'REQUESTED' && trip.scheduledFor ? 'Scheduled' : RIDE_STATUS_LABEL[trip.status]}
        </span>
      </div>
      {action}
    </div>
  );
}

// Real Kakao T-style post-trip driver rating (item 213) -- one real review per real
// trip, rating the driver who completed it. See lib/rideshare.ts's own doc comment.
function RideReviewPrompt({ tripId, onSubmitted }: { tripId: string; onSubmitted: () => void }) {
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
      else setError(err instanceof ApiError ? err.message : 'Could not submit your rating.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--toss-grey-100)' }}>
      <p style={{ fontSize: '12px', fontWeight: 700, marginBottom: '6px' }}>Rate your driver</p>
      <div style={{ display: 'flex', gap: '4px', marginBottom: '6px' }}>
        {[1, 2, 3, 4, 5].map((n) => (
          <button
            key={n} type="button" onClick={() => setRating(n)}
            style={{ fontSize: '20px', color: n <= rating ? '#FFC107' : 'var(--toss-grey-300)' }}
          >
            ★
          </button>
        ))}
      </div>
      {rating > 0 && (
        <>
          <input
            type="text" value={comment} placeholder="Leave a comment (optional)" onChange={(e) => setComment(e.target.value)}
            style={{ width: '100%', padding: '8px 10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '12px', marginBottom: '6px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={submitting} onClick={handleSubmit} style={{ width: '100%', padding: '8px', fontSize: '13px' }}>
            {submitting ? 'Submitting…' : 'Submit rating'}
          </button>
        </>
      )}
      {error && <p style={{ fontSize: '11px', color: '#E53935', marginTop: '4px' }} role="alert">{error}</p>}
    </div>
  );
}

function RidesView() {
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
  // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- 'now' is unchanged
  // ASAP dispatch; 'later' holds a datetime-local value the passenger picks.
  const [rideTiming, setRideTiming] = useState<'now' | 'later'>('now');
  const [scheduledAt, setScheduledAt] = useState('');
  // Real Kakao T-style post-trip driver rating (item 213) -- tracks which completed
  // trips have already been rated this session, so a submitted/already-reviewed
  // prompt doesn't linger. See RideReviewPrompt's own doc comment.
  const [reviewedTripIds, setReviewedTripIds] = useState<Set<string>>(new Set());

  const loadMyTrips = () => {
    fetchMyTrips().then(setMyTrips).catch((err) => setRideError(err instanceof ApiError ? err.message : 'Could not load your trips.'));
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
      setRideError(err instanceof ApiError ? err.message : 'Could not request a ride.');
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
      setRideError(err instanceof ApiError ? err.message : 'Could not cancel this trip.');
    } finally {
      setBusyTripId(null);
    }
  };

  // Driver side
  const [driver, setDriver] = useState<RideDriver | null | undefined>(undefined);
  const [registeringDriver, setRegisteringDriver] = useState(false);
  const [availableTrips, setAvailableTrips] = useState<RideTrip[] | null>(null);
  const [myDriverTrips, setMyDriverTrips] = useState<RideTrip[] | null>(null);
  const [driverError, setDriverError] = useState<string | null>(null);
  const [busyDriverTripId, setBusyDriverTripId] = useState<string | null>(null);
  // Real Kakao T-style post-trip driver rating (item 213) -- the real driver's own
  // aggregate rating, computed at read time from every real submitted review.
  const [driverRating, setDriverRating] = useState<RideDriverRating | null>(null);
  // Real Kakao T-style multi-stop rides (item 214) -- keyed by trip id, so each real
  // active trip's own waypoints render independently.
  const [driverTripStops, setDriverTripStops] = useState<Record<string, RideTripStop[]>>({});

  const loadDriver = () => {
    fetchMyDriverProfile()
      .then((d) => {
        setDriver(d);
        fetchDriverRating(d.id).then(setDriverRating).catch(() => {});
      })
      .catch((err) => {
        if (err instanceof ApiError && err.code === 'RIDE_DRIVER_NOT_REGISTERED') setDriver(null);
        else setDriverError(err instanceof ApiError ? err.message : 'Could not load your driver profile.');
      });
  };

  useEffect(() => {
    if (subTab === 'DRIVE') loadDriver();
  }, [subTab]);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableTrips(), fetchMyDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : 'Could not load trips.'));
  };

  useEffect(() => {
    if (subTab !== 'DRIVE' || !driver) return;
    loadDriverTrips();
    const interval = setInterval(loadDriverTrips, 4000);
    return () => clearInterval(interval);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab, driver?.id]);

  const handleRegisterDriver = async () => {
    setRegisteringDriver(true);
    setDriverError(null);
    try {
      setDriver(await registerAsDriver());
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : 'Could not register as a driver.');
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
      setDriverError(err instanceof ApiError ? err.message : 'Could not update your availability.');
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<RideTrip>) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await action(tripId);
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : 'Could not update this trip.');
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
      setDriverError(err instanceof ApiError ? err.message : 'Could not mark this stop arrived.');
    } finally {
      setBusyDriverTripId(null);
    }
  };

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['RIDE', 'DRIVE'] as const).map((v) => (
          <button
            key={v} onClick={() => setSubTab(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: subTab === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: subTab === v ? 'var(--toss-blue)' : 'transparent',
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
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your ride</h4>
              <RideTripCard
                trip={activeTrip} stops={activeTripStops}
                action={activeTrip.status !== 'IN_PROGRESS' && (
                  <button className="toss-btn toss-btn-danger" disabled={busyTripId === activeTrip.id} onClick={() => handleCancelTrip(activeTrip.id)}>
                    {busyTripId === activeTrip.id ? 'Cancelling…' : 'Cancel ride'}
                  </button>
                )}
              />
            </div>
          ) : (
            <div className="toss-card" style={{ marginBottom: '20px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '12px', display: 'flex', alignItems: 'center', gap: '6px' }}>
                <Car size={18} color="var(--toss-blue)" /> Request a ride
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
                  style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-blue)', marginBottom: '12px' }}
                >
                  + Add a stop
                </button>
              )}

              <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '12px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
                {(['now', 'later'] as const).map((v) => (
                  <button
                    key={v} type="button" onClick={() => setRideTiming(v)}
                    style={{
                      flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
                      color: rideTiming === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
                      backgroundColor: rideTiming === v ? 'var(--toss-blue)' : 'transparent',
                    }}
                  >
                    {v === 'now' ? 'Ride now' : 'Schedule'}
                  </button>
                ))}
              </div>
              {rideTiming === 'later' && (
                <input
                  type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)}
                  style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '12px' }}
                />
              )}

              <button
                className="toss-btn toss-btn-primary"
                disabled={!pickup || !dropoff || requesting || (rideTiming === 'later' && !scheduledAt) || stops.some((s) => s === null)}
                onClick={handleRequestRide} style={{ width: '100%' }}
              >
                {requesting ? 'Requesting…' : rideTiming === 'later' ? 'Schedule ride' : 'Request ride'}
              </button>
            </div>
          )}

          {rideError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{rideError}</p>}

          {pastTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past rides</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pastTrips.map((t) => (
                  <RideTripCard
                    key={t.id} trip={t}
                    action={t.status === 'COMPLETED' && t.driverId && !reviewedTripIds.has(t.id) && (
                      <RideReviewPrompt tripId={t.id} onSubmitted={() => setReviewedTripIds((prev) => new Set(prev).add(t.id))} />
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
            <div className="toss-card skeleton" style={{ height: '180px' }} />
          ) : driver === null ? (
            <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
              <Car size={32} color="var(--toss-blue)" style={{ marginBottom: '10px' }} />
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '6px' }}>Drive with Itunda</h3>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
                Earn a real fare for every trip you complete, paid straight to your wallet.
              </p>
              <button className="toss-btn toss-btn-primary" onClick={handleRegisterDriver} disabled={registeringDriver}>
                {registeringDriver ? 'Registering…' : 'Become a driver'}
              </button>
              {driverError && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '12px' }} role="alert">{driverError}</p>}
            </div>
          ) : (
            <div>
              <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                <div>
                  <p style={{ fontSize: '15px', fontWeight: 700 }}>{driver.available ? "You're online" : "You're offline"}</p>
                  <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{driver.available ? 'Visible for new trip requests' : 'Go online to see trip requests'}</p>
                  {driverRating && driverRating.count > 0 && (
                    <p style={{ fontSize: '12px', color: '#FFC107', fontWeight: 700, marginTop: '4px' }}>
                      ★ {driverRating.average?.toFixed(1)} <span style={{ color: 'var(--toss-grey-500)', fontWeight: 400 }}>({driverRating.count} rating{driverRating.count === 1 ? '' : 's'})</span>
                    </p>
                  )}
                </div>
                <button className={driver.available ? 'toss-btn toss-btn-danger' : 'toss-btn toss-btn-primary'} onClick={handleToggleAvailable}>
                  {driver.available ? 'Go offline' : 'Go online'}
                </button>
              </div>

              {driverError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '12px' }} role="alert">{driverError}</p>}

              {activeDriverTrips.length > 0 && (
                <div style={{ marginBottom: '20px' }}>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your active trip</h4>
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
                                <button className="toss-btn toss-btn-secondary" disabled={busyDriverTripId === t.id} onClick={() => handleArriveAtStop(t.id)}>
                                  {busyDriverTripId === t.id ? 'Updating…' : `Arrived at ${nextStop.address}`}
                                </button>
                              )}
                              <button
                                className="toss-btn toss-btn-primary" disabled={busyDriverTripId === t.id}
                                onClick={() => handleDriverTripAction(t.id, t.status === 'DRIVER_ASSIGNED' ? startRideTrip : completeRideTrip)}
                              >
                                {busyDriverTripId === t.id ? 'Updating…' : t.status === 'DRIVER_ASSIGNED' ? 'Start trip' : 'Complete trip'}
                              </button>
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
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Trip requests near you</h4>
                  {availableTrips === null ? (
                    <div className="toss-card skeleton" style={{ height: '100px' }} />
                  ) : availableTrips.length === 0 ? (
                    <EmptyState message="No trip requests waiting right now." />
                  ) : (
                    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                      {availableTrips.map((t) => (
                        <RideTripCard
                          key={t.id} trip={t}
                          action={
                            <div style={{ display: 'flex', gap: '8px' }}>
                              <button
                                className="toss-btn toss-btn-primary" disabled={busyDriverTripId === t.id}
                                onClick={() => handleDriverTripAction(t.id, acceptRideTrip)}
                              >
                                {busyDriverTripId === t.id ? 'Accepting…' : 'Accept'}
                              </button>
                              <button
                                className="toss-btn toss-btn-secondary" disabled={busyDriverTripId === t.id}
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
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
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
    <div className="toss-card">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: '8px' }}>
        <div style={{ flex: 1 }}>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>{trip.pickupAddress}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', margin: '2px 0' }}>→ {trip.dropoffAddress}</p>
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
            {trip.vehicleMake} {trip.vehicleModel} · {trip.vehiclePlate}
          </p>
          <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{trip.distanceKm.toFixed(1)} km · {trip.fare.toLocaleString()} RWF</p>
        </div>
        <span style={{
          fontSize: '11px', fontWeight: 700, padding: '4px 8px', borderRadius: '6px',
          color: trip.status === 'CANCELLED' ? '#E53935' : trip.status === 'COMPLETED' ? 'var(--toss-grey-500)' : 'var(--toss-blue)',
          backgroundColor: trip.status === 'CANCELLED' ? '#FDECEA' : trip.status === 'COMPLETED' ? 'var(--toss-grey-100)' : '#E8F0FE',
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
    fetchMyDesignatedDriverTrips().then(setMyTrips).catch((err) => setTripError(err instanceof ApiError ? err.message : 'Could not load your trips.'));
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
      setTripError(err instanceof ApiError ? err.message : 'Could not request a designated driver.');
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
      setTripError(err instanceof ApiError ? err.message : 'Could not cancel this trip.');
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
        else setDriverError(err instanceof ApiError ? err.message : 'Could not load your driver profile.');
      });
  };

  useEffect(() => {
    if (subTab === 'DRIVE') loadDriver();
  }, [subTab]);

  const loadDriverTrips = () => {
    Promise.all([fetchAvailableDesignatedDriverTrips(), fetchMyDesignatedDriverDriverTrips()])
      .then(([a, m]) => { setAvailableTrips(a); setMyDriverTrips(m); })
      .catch((err) => setDriverError(err instanceof ApiError ? err.message : 'Could not load trips.'));
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
      setDriverError(err instanceof ApiError ? err.message : 'Could not register as a designated driver.');
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
      setDriverError(err instanceof ApiError ? err.message : 'Could not update your availability.');
    }
  };

  const handleDriverTripAction = async (tripId: string, action: (id: string) => Promise<DesignatedDriverTrip>) => {
    setBusyDriverTripId(tripId);
    setDriverError(null);
    try {
      await action(tripId);
      loadDriverTrips();
    } catch (err) {
      setDriverError(err instanceof ApiError ? err.message : 'Could not update this trip.');
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
          className={subTab === 'REQUEST' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('REQUEST')} style={{ flex: 1 }}
        >
          Get a driver
        </button>
        <button
          className={subTab === 'DRIVE' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('DRIVE')} style={{ flex: 1 }}
        >
          Drive
        </button>
      </div>

      {subTab === 'REQUEST' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {tripError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{tripError}</p>}
          {activeTrip ? (
            <DesignatedDriverTripCard
              trip={activeTrip}
              action={
                activeTrip.status === 'REQUESTED' && (
                  <button
                    className="toss-btn toss-btn-secondary" disabled={busyTripId === activeTrip.id}
                    onClick={() => handleCancelTrip(activeTrip.id)} style={{ marginTop: '8px', width: '100%' }}
                  >
                    {busyTripId === activeTrip.id ? '…' : 'Cancel'}
                  </button>
                )
              }
            />
          ) : (
            <div className="toss-card">
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Get a designated driver</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                A real professional driver comes to you and drives YOUR OWN CAR home.
              </p>
              <PlaceSearchInput label="Pickup" placeholder="Where are you now?" value={pickup} onSelect={setPickup} />
              <PlaceSearchInput label="Drop-off" placeholder="Where's home?" value={dropoff} onSelect={setDropoff} />
              <input
                type="text" value={vehicleMake} placeholder="Car make (e.g. Toyota)" onChange={(e) => setVehicleMake(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehicleModel} placeholder="Car model (e.g. RAV4)" onChange={(e) => setVehicleModel(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
              />
              <input
                type="text" value={vehiclePlate} placeholder="License plate" onChange={(e) => setVehiclePlate(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '12px' }}
              />
              <button
                className="toss-btn toss-btn-primary" style={{ width: '100%' }}
                disabled={requesting || !pickup || !dropoff || !vehicleMake.trim() || !vehicleModel.trim() || !vehiclePlate.trim()}
                onClick={handleRequestTrip}
              >
                {requesting ? 'Requesting…' : 'Request a driver'}
              </button>
            </div>
          )}
          {pastTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past trips</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {pastTrips.map((t) => <DesignatedDriverTripCard key={t.id} trip={t} />)}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'DRIVE' && (
        <div>
          {driver === undefined && <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>}
          {driver === null && (
            <div className="toss-card">
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Become a designated driver</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                Any itunda user can register. License number is self-declared, not verified against a real registry.
              </p>
              {driverError && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{driverError}</p>}
              <input
                type="text" value={licenseNumber} placeholder="License number" onChange={(e) => setLicenseNumber(e.target.value)}
                style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '12px' }}
              />
              <button
                className="toss-btn toss-btn-primary" style={{ width: '100%' }}
                disabled={registeringDriver || !licenseNumber.trim()} onClick={handleRegisterDriver}
              >
                {registeringDriver ? 'Registering…' : 'Register'}
              </button>
            </div>
          )}
          {driver && (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
              <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{driver.available ? 'Online' : 'Offline'}</p>
                <button className="toss-btn toss-btn-secondary" onClick={handleToggleAvailable}>
                  {driver.available ? 'Go offline' : 'Go online'}
                </button>
              </div>
              {driverError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{driverError}</p>}
              {activeDriverTrips.length > 0 && (
                <div>
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Active</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {activeDriverTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                            {t.status === 'ACCEPTED' && (
                              <button
                                className="toss-btn toss-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
                                onClick={() => handleDriverTripAction(t.id, startDesignatedDriverTrip)}
                              >
                                {busyDriverTripId === t.id ? '…' : 'Start driving'}
                              </button>
                            )}
                            {t.status === 'DRIVING' && (
                              <button
                                className="toss-btn toss-btn-primary" disabled={busyDriverTripId === t.id} style={{ flex: 1 }}
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
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Nearby requests</h4>
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {availableTrips.map((t) => (
                      <DesignatedDriverTripCard
                        key={t.id} trip={t}
                        action={
                          <button
                            className="toss-btn toss-btn-primary" disabled={busyDriverTripId === t.id} style={{ width: '100%', marginTop: '8px' }}
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
                  <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Completed</h4>
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

// Real Kakao T 바이크 (Kakao T Bike, item 222) -- see lib/bikeshare.ts's own doc
// comment for the full sourced account. Unlike Rides/Designated driver above, there's
// no live-polling trip status here -- a rental is a simple start-now/end-now action,
// the fare only becomes known once the rider ends it.
function BikeShareView() {
  const [subTab, setSubTab] = useState<'RENT' | 'OWN'>('RENT');

  // Rider side
  const [nearbyBikes, setNearbyBikes] = useState<BikeAsset[] | null>(null);
  const [activeRental, setActiveRental] = useState<BikeAssetRentalSession | null>(null);
  const [rentalHistory, setRentalHistory] = useState<BikeAssetRentalSession[] | null>(null);
  const [riderError, setRiderError] = useState<string | null>(null);
  const [busyBikeId, setBusyBikeId] = useState<string | null>(null);
  const [endingRental, setEndingRental] = useState(false);
  const [justCompletedRental, setJustCompletedRental] = useState<BikeAssetRentalSession | null>(null);

  const loadRiderData = () => {
    if (!navigator.geolocation) {
      setRiderError('Location access is required to find nearby bikes.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        fetchNearbyBikeAssets(pos.coords.latitude, pos.coords.longitude)
          .then(setNearbyBikes)
          .catch((err) => setRiderError(err instanceof ApiError ? err.message : 'Could not load nearby bikes.'));
      },
      () => setRiderError('Could not access your location.'),
    );
    fetchMyBikeAssetRentalHistory().then(setRentalHistory).catch(() => {});
  };

  useEffect(() => {
    if (subTab !== 'RENT') return;
    loadRiderData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  useEffect(() => {
    const active = (rentalHistory ?? []).find((r) => r.status === 'ACTIVE');
    setActiveRental(active ?? null);
  }, [rentalHistory]);

  const handleStartRental = (bikeId: string) => {
    if (!navigator.geolocation) return;
    setBusyBikeId(bikeId);
    setRiderError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        startBikeAssetRental(bikeId, pos.coords.latitude, pos.coords.longitude)
          .then((rental) => { setActiveRental(rental); setBusyBikeId(null); })
          .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not start this rental.'); setBusyBikeId(null); });
      },
      () => { setRiderError('Could not access your location.'); setBusyBikeId(null); },
    );
  };

  const handleEndRental = () => {
    if (!activeRental || !navigator.geolocation) return;
    setEndingRental(true);
    setRiderError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        endBikeAssetRental(activeRental.id, pos.coords.latitude, pos.coords.longitude)
          .then((rental) => {
            setActiveRental(null);
            setJustCompletedRental(rental);
            setEndingRental(false);
            loadRiderData();
          })
          .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not end this rental.'); setEndingRental(false); });
      },
      () => { setRiderError('Could not access your location.'); setEndingRental(false); },
    );
  };

  // Owner side
  const [myBikes, setMyBikes] = useState<BikeAsset[] | null>(null);
  const [bikeType, setBikeType] = useState<BikeAssetType>('REGULAR');
  const [registering, setRegistering] = useState(false);
  const [ownerError, setOwnerError] = useState<string | null>(null);

  const loadMyBikes = () => {
    fetchMyBikeAssets().then(setMyBikes).catch((err) => setOwnerError(err instanceof ApiError ? err.message : 'Could not load your bikes.'));
  };

  useEffect(() => {
    if (subTab === 'OWN') loadMyBikes();
  }, [subTab]);

  const handleRegisterBike = () => {
    if (!navigator.geolocation) return;
    setRegistering(true);
    setOwnerError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        registerBikeAsset(bikeType, pos.coords.latitude, pos.coords.longitude)
          .then(() => { loadMyBikes(); setRegistering(false); })
          .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not register this bike.'); setRegistering(false); });
      },
      () => { setOwnerError('Could not access your location.'); setRegistering(false); },
    );
  };

  const handleToggleBikeAvailable = (bike: BikeAsset) => {
    setBusyBikeId(bike.id);
    setOwnerError(null);
    setBikeAssetAvailability(bike.id, !bike.available)
      .then(() => { loadMyBikes(); setBusyBikeId(null); })
      .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not update this bike.'); setBusyBikeId(null); });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RENT' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('RENT')} style={{ flex: 1 }}
        >
          Rent a bike
        </button>
        <button
          className={subTab === 'OWN' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('OWN')} style={{ flex: 1 }}
        >
          My bikes
        </button>
      </div>

      {subTab === 'RENT' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {riderError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{riderError}</p>}
          {justCompletedRental && (
            <div className="toss-card" style={{ textAlign: 'center', padding: '24px' }}>
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Rental complete</p>
              <p style={{ fontSize: '24px', fontWeight: 700, margin: '8px 0' }}>{(justCompletedRental.totalFare ?? 0).toLocaleString()} RWF</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{justCompletedRental.durationMinutes} minutes</p>
              <button className="toss-btn toss-btn-secondary" onClick={() => setJustCompletedRental(null)} style={{ marginTop: '12px' }}>
                Done
              </button>
            </div>
          )}
          {!justCompletedRental && activeRental && (
            <div className="toss-card">
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>🚲 Riding now</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                Fare is calculated by elapsed time once you end the rental.
              </p>
              <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} disabled={endingRental} onClick={handleEndRental}>
                {endingRental ? 'Ending…' : 'End rental (park the bike here)'}
              </button>
            </div>
          )}
          {!justCompletedRental && !activeRental && (
            <div>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                Nearby bikes, within 5 km of your real location.
              </p>
              {nearbyBikes === null ? (
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
              ) : nearbyBikes.length === 0 ? (
                <EmptyState message="No bikes nearby right now." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {nearbyBikes.map((bike) => (
                    <div key={bike.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <p style={{ fontSize: '13px', fontWeight: 700 }}>{bike.type === 'ELECTRIC' ? '⚡ Electric' : '🚲 Regular'}</p>
                        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{bike.type === 'ELECTRIC' ? '150' : '80'} RWF/minute</p>
                      </div>
                      <button
                        className="toss-btn toss-btn-primary" disabled={busyBikeId === bike.id}
                        onClick={() => handleStartRental(bike.id)}
                      >
                        {busyBikeId === bike.id ? '…' : 'Unlock'}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {rentalHistory && rentalHistory.filter((r) => r.status === 'COMPLETED').length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past rentals</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {rentalHistory.filter((r) => r.status === 'COMPLETED').map((r) => (
                  <div key={r.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.durationMinutes} min</p>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{(r.totalFare ?? 0).toLocaleString()} RWF</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OWN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {ownerError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{ownerError}</p>}
          <div className="toss-card">
            <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Add your bike to the pool</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
              Uses your real current location as the bike's starting spot.
            </p>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '12px' }}>
              <button
                className={bikeType === 'REGULAR' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                onClick={() => setBikeType('REGULAR')} style={{ flex: 1 }}
              >
                Regular
              </button>
              <button
                className={bikeType === 'ELECTRIC' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                onClick={() => setBikeType('ELECTRIC')} style={{ flex: 1 }}
              >
                Electric
              </button>
            </div>
            <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} disabled={registering} onClick={handleRegisterBike}>
              {registering ? 'Registering…' : 'Register bike'}
            </button>
          </div>
          {myBikes && myBikes.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your bikes</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myBikes.map((bike) => (
                  <div key={bike.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{bike.type === 'ELECTRIC' ? '⚡ Electric' : '🚲 Regular'} bike</p>
                    <button className="toss-btn toss-btn-secondary" disabled={busyBikeId === bike.id} onClick={() => handleToggleBikeAvailable(bike)}>
                      {busyBikeId === bike.id ? '…' : bike.available ? 'Available' : 'Unavailable'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// Real Kakao T 주차 (Kakao T Parking, item 223) -- see lib/parking.ts's own doc
// comment for the full sourced account. Same "no live-polling, fare only known at
// checkout" shape BikeShareView above establishes -- a session is a simple check-in/
// check-out action, billed hourly (not per-minute like Bike, since parking sessions
// genuinely run longer).
function ParkingView() {
  const [subTab, setSubTab] = useState<'RENT' | 'OWN'>('RENT');

  // Renter side
  const [nearbySpots, setNearbySpots] = useState<ParkingSpot[] | null>(null);
  const [activeSession, setActiveSession] = useState<ParkingSession | null>(null);
  const [rentalHistory, setRentalHistory] = useState<ParkingSession[] | null>(null);
  const [renterError, setRenterError] = useState<string | null>(null);
  const [busySpotId, setBusySpotId] = useState<string | null>(null);
  const [endingSession, setEndingSession] = useState(false);
  const [justCompletedSession, setJustCompletedSession] = useState<ParkingSession | null>(null);

  const loadRenterData = () => {
    if (!navigator.geolocation) {
      setRenterError('Location access is required to find nearby parking.');
      return;
    }
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        fetchNearbyParkingSpots(pos.coords.latitude, pos.coords.longitude)
          .then(setNearbySpots)
          .catch((err) => setRenterError(err instanceof ApiError ? err.message : 'Could not load nearby parking.'));
      },
      () => setRenterError('Could not access your location.'),
    );
    fetchMyParkingHistory().then(setRentalHistory).catch(() => {});
  };

  useEffect(() => {
    if (subTab !== 'RENT') return;
    loadRenterData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  useEffect(() => {
    const active = (rentalHistory ?? []).find((r) => r.status === 'ACTIVE');
    setActiveSession(active ?? null);
  }, [rentalHistory]);

  const handleStartSession = (spotId: string) => {
    setBusySpotId(spotId);
    setRenterError(null);
    startParkingSession(spotId)
      .then((session) => { setActiveSession(session); setBusySpotId(null); })
      .catch((err) => { setRenterError(err instanceof ApiError ? err.message : 'Could not check in to this spot.'); setBusySpotId(null); });
  };

  const handleEndSession = () => {
    if (!activeSession) return;
    setEndingSession(true);
    setRenterError(null);
    endParkingSession(activeSession.id)
      .then((session) => {
        setActiveSession(null);
        setJustCompletedSession(session);
        setEndingSession(false);
        loadRenterData();
      })
      .catch((err) => { setRenterError(err instanceof ApiError ? err.message : 'Could not check out of this spot.'); setEndingSession(false); });
  };

  // Owner side
  const [mySpots, setMySpots] = useState<ParkingSpot[] | null>(null);
  const [spotAddress, setSpotAddress] = useState('');
  const [spotHourlyRate, setSpotHourlyRate] = useState('');
  const [registering, setRegistering] = useState(false);
  const [ownerError, setOwnerError] = useState<string | null>(null);

  const loadMySpots = () => {
    fetchMyParkingSpots().then(setMySpots).catch((err) => setOwnerError(err instanceof ApiError ? err.message : 'Could not load your parking spots.'));
  };

  useEffect(() => {
    if (subTab === 'OWN') loadMySpots();
  }, [subTab]);

  const handleRegisterSpot = () => {
    if (!navigator.geolocation || !spotAddress.trim() || !spotHourlyRate) return;
    const rate = Number(spotHourlyRate);
    if (!Number.isFinite(rate) || rate <= 0) return;
    setRegistering(true);
    setOwnerError(null);
    navigator.geolocation.getCurrentPosition(
      (pos) => {
        registerParkingSpot(spotAddress.trim(), pos.coords.latitude, pos.coords.longitude, rate)
          .then(() => { setSpotAddress(''); setSpotHourlyRate(''); loadMySpots(); setRegistering(false); })
          .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not register this spot.'); setRegistering(false); });
      },
      () => { setOwnerError('Could not access your location.'); setRegistering(false); },
    );
  };

  const handleToggleSpotAvailable = (spot: ParkingSpot) => {
    setBusySpotId(spot.id);
    setOwnerError(null);
    setParkingSpotAvailability(spot.id, !spot.available)
      .then(() => { loadMySpots(); setBusySpotId(null); })
      .catch((err) => { setOwnerError(err instanceof ApiError ? err.message : 'Could not update this spot.'); setBusySpotId(null); });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RENT' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('RENT')} style={{ flex: 1 }}
        >
          Find parking
        </button>
        <button
          className={subTab === 'OWN' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('OWN')} style={{ flex: 1 }}
        >
          My spots
        </button>
      </div>

      {subTab === 'RENT' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {renterError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{renterError}</p>}
          {justCompletedSession && (
            <div className="toss-card" style={{ textAlign: 'center', padding: '24px' }}>
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Parking complete</p>
              <p style={{ fontSize: '24px', fontWeight: 700, margin: '8px 0' }}>{(justCompletedSession.totalFare ?? 0).toLocaleString()} RWF</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{justCompletedSession.durationMinutes} minutes</p>
              <button className="toss-btn toss-btn-secondary" onClick={() => setJustCompletedSession(null)} style={{ marginTop: '12px' }}>
                Done
              </button>
            </div>
          )}
          {!justCompletedSession && activeSession && (
            <div className="toss-card">
              <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>🅿️ Parked now</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                Fare is calculated by elapsed time (rounded up to the next hour) once you check out.
              </p>
              <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} disabled={endingSession} onClick={handleEndSession}>
                {endingSession ? 'Checking out…' : 'Check out'}
              </button>
            </div>
          )}
          {!justCompletedSession && !activeSession && (
            <div>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
                Nearby parking, within 5 km of your real location.
              </p>
              {nearbySpots === null ? (
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
              ) : nearbySpots.length === 0 ? (
                <EmptyState message="No parking nearby right now." />
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                  {nearbySpots.map((spot) => (
                    <div key={spot.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                      <div>
                        <p style={{ fontSize: '13px', fontWeight: 700 }}>{spot.address}</p>
                        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{spot.hourlyRate.toLocaleString()} RWF/hour</p>
                      </div>
                      <button
                        className="toss-btn toss-btn-primary" disabled={busySpotId === spot.id}
                        onClick={() => handleStartSession(spot.id)}
                      >
                        {busySpotId === spot.id ? '…' : 'Check in'}
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
          {rentalHistory && rentalHistory.filter((r) => r.status === 'COMPLETED').length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Past sessions</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {rentalHistory.filter((r) => r.status === 'COMPLETED').map((r) => (
                  <div key={r.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between' }}>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.durationMinutes} min</p>
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{(r.totalFare ?? 0).toLocaleString()} RWF</p>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OWN' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {ownerError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{ownerError}</p>}
          <div className="toss-card">
            <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>List your spot</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
              Uses your real current location as the spot's location.
            </p>
            <input
              type="text" value={spotAddress} placeholder="Address (e.g. Kigali Heights driveway)" onChange={(e) => setSpotAddress(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
            />
            <input
              type="number" value={spotHourlyRate} placeholder="Hourly rate (RWF)" onChange={(e) => setSpotHourlyRate(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '12px' }}
            />
            <button
              className="toss-btn toss-btn-primary" style={{ width: '100%' }} disabled={registering || !spotAddress.trim() || !spotHourlyRate}
              onClick={handleRegisterSpot}
            >
              {registering ? 'Registering…' : 'Register spot'}
            </button>
          </div>
          {mySpots && mySpots.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your spots</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {mySpots.map((spot) => (
                  <div key={spot.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <p style={{ fontSize: '13px', fontWeight: 700 }}>{spot.address}</p>
                      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{spot.hourlyRate.toLocaleString()} RWF/hour</p>
                    </div>
                    <button className="toss-btn toss-btn-secondary" disabled={busySpotId === spot.id} onClick={() => handleToggleSpotAvailable(spot)}>
                      {busySpotId === spot.id ? '…' : spot.available ? 'Available' : 'Unavailable'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

// Real Kakao T 시외버스 (intercity bus booking, item 224) -- see lib/bus.ts's own
// doc comment for the full sourced account. Fare is known and charged in full at
// booking time -- distinct from Bike/Parking's settle-at-checkout shape above.
function BusView() {
  const [subTab, setSubTab] = useState<'RIDE' | 'OPERATE'>('RIDE');

  // Rider side
  const [searchOrigin, setSearchOrigin] = useState('');
  const [searchDestination, setSearchDestination] = useState('');
  const [trips, setTrips] = useState<BusTrip[] | null>(null);
  const [myBookings, setMyBookings] = useState<BusBooking[] | null>(null);
  const [seatCounts, setSeatCounts] = useState<Record<string, string>>({});
  const [riderError, setRiderError] = useState<string | null>(null);
  const [busyTripId, setBusyTripId] = useState<string | null>(null);
  const [busyBookingId, setBusyBookingId] = useState<string | null>(null);

  const loadTrips = () => {
    searchBusTrips(searchOrigin.trim() || undefined, searchDestination.trim() || undefined)
      .then(setTrips)
      .catch((err) => setRiderError(err instanceof ApiError ? err.message : 'Could not search trips.'));
  };

  const loadMyBookings = () => {
    fetchMyBusBookings().then(setMyBookings).catch(() => {});
  };

  useEffect(() => {
    if (subTab !== 'RIDE') return;
    loadTrips();
    loadMyBookings();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [subTab]);

  const handleBookSeats = (tripId: string) => {
    const seatCount = Number(seatCounts[tripId] || '1');
    if (!Number.isFinite(seatCount) || seatCount < 1) return;
    setBusyTripId(tripId);
    setRiderError(null);
    bookBusSeats(tripId, seatCount)
      .then(() => { loadTrips(); loadMyBookings(); setBusyTripId(null); })
      .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not book these seats.'); setBusyTripId(null); });
  };

  const handleCancelBooking = (bookingId: string) => {
    setBusyBookingId(bookingId);
    setRiderError(null);
    cancelBusBooking(bookingId)
      .then(() => { loadMyBookings(); setBusyBookingId(null); })
      .catch((err) => { setRiderError(err instanceof ApiError ? err.message : 'Could not cancel this booking.'); setBusyBookingId(null); });
  };

  // Operator side
  const [myTrips, setMyTrips] = useState<BusTrip[] | null>(null);
  const [tripOrigin, setTripOrigin] = useState('');
  const [tripDestination, setTripDestination] = useState('');
  const [tripDeparture, setTripDeparture] = useState('');
  const [tripSeats, setTripSeats] = useState('');
  const [tripFare, setTripFare] = useState('');
  const [posting, setPosting] = useState(false);
  const [operatorError, setOperatorError] = useState<string | null>(null);

  const loadMyTrips = () => {
    fetchMyBusTrips().then(setMyTrips).catch((err) => setOperatorError(err instanceof ApiError ? err.message : 'Could not load your trips.'));
  };

  // Real trip manifest -- see fetchBusTripBookings's own doc comment. Previously a
  // real, tested backend endpoint (GET /bus/trips/{id}/bookings) with zero client
  // anywhere on any platform: an operator could post a route and see the seat
  // countdown, but never who actually booked. Lazily loaded per trip, not prefetched
  // for every route at once.
  const [expandedTripId, setExpandedTripId] = useState<string | null>(null);
  const [tripBookings, setTripBookings] = useState<BusBooking[] | null>(null);
  const [manifestError, setManifestError] = useState<string | null>(null);

  const toggleManifest = (tripId: string) => {
    if (expandedTripId === tripId) {
      setExpandedTripId(null);
      return;
    }
    setExpandedTripId(tripId);
    setTripBookings(null);
    setManifestError(null);
    fetchBusTripBookings(tripId)
      .then(setTripBookings)
      .catch((err) => setManifestError(err instanceof ApiError ? err.message : 'Could not load bookings for this route.'));
  };

  useEffect(() => {
    if (subTab === 'OPERATE') loadMyTrips();
  }, [subTab]);

  const handlePostTrip = () => {
    const totalSeats = Number(tripSeats);
    const farePerSeat = Number(tripFare);
    if (!tripOrigin.trim() || !tripDestination.trim() || !tripDeparture || !Number.isFinite(totalSeats) || totalSeats <= 0 || !Number.isFinite(farePerSeat) || farePerSeat <= 0) {
      return;
    }
    setPosting(true);
    setOperatorError(null);
    postBusTrip(tripOrigin.trim(), tripDestination.trim(), new Date(tripDeparture).toISOString(), totalSeats, farePerSeat)
      .then(() => {
        setTripOrigin(''); setTripDestination(''); setTripDeparture(''); setTripSeats(''); setTripFare('');
        loadMyTrips();
        setPosting(false);
      })
      .catch((err) => { setOperatorError(err instanceof ApiError ? err.message : 'Could not post this trip.'); setPosting(false); });
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'RIDE' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('RIDE')} style={{ flex: 1 }}
        >
          Find a bus
        </button>
        <button
          className={subTab === 'OPERATE' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('OPERATE')} style={{ flex: 1 }}
        >
          My routes
        </button>
      </div>

      {subTab === 'RIDE' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {riderError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{riderError}</p>}
          <div className="toss-card">
            <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Search routes</p>
            <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
              <input
                type="text" value={searchOrigin} placeholder="From (e.g. Kigali)" onChange={(e) => setSearchOrigin(e.target.value)}
                style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
              <input
                type="text" value={searchDestination} placeholder="To (e.g. Musanze)" onChange={(e) => setSearchDestination(e.target.value)}
                style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
              />
            </div>
            <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} onClick={loadTrips}>Search</button>
          </div>
          {trips === null ? (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
          ) : trips.length === 0 ? (
            <EmptyState message="No upcoming trips found." />
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
              {trips.map((trip) => (
                <div key={trip.id} className="toss-card">
                  <p style={{ fontSize: '13px', fontWeight: 700 }}>{trip.origin} → {trip.destination}</p>
                  <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                    {new Date(trip.departureTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                    {' · '}{trip.farePerSeat.toLocaleString()} RWF/seat · {trip.availableSeats} seat(s) left
                  </p>
                  <div style={{ display: 'flex', gap: '8px', marginTop: '8px' }}>
                    <input
                      type="number" min={1} max={trip.availableSeats} value={seatCounts[trip.id] ?? '1'}
                      onChange={(e) => setSeatCounts((prev) => ({ ...prev, [trip.id]: e.target.value }))}
                      style={{ width: '60px', padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
                    />
                    <button
                      className="toss-btn toss-btn-primary" disabled={busyTripId === trip.id} style={{ flex: 1 }}
                      onClick={() => handleBookSeats(trip.id)}
                    >
                      {busyTripId === trip.id ? '…' : 'Book seats'}
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
          {myBookings && myBookings.filter((b) => b.status === 'BOOKED').length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your bookings</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myBookings.filter((b) => b.status === 'BOOKED').map((b) => (
                  <div key={b.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                    <div>
                      <p style={{ fontSize: '13px', fontWeight: 700 }}>{b.seatCount} seat(s)</p>
                      <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{b.totalFare.toLocaleString()} RWF</p>
                    </div>
                    <button className="toss-btn toss-btn-secondary" disabled={busyBookingId === b.id} onClick={() => handleCancelBooking(b.id)}>
                      {busyBookingId === b.id ? '…' : 'Cancel'}
                    </button>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}

      {subTab === 'OPERATE' && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          {operatorError && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{operatorError}</p>}
          <div className="toss-card">
            <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '4px' }}>Post a route</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '12px' }}>
              Any itunda user can post a scheduled trip -- no transport-licensing check.
            </p>
            <input
              type="text" value={tripOrigin} placeholder="Origin" onChange={(e) => setTripOrigin(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
            />
            <input
              type="text" value={tripDestination} placeholder="Destination" onChange={(e) => setTripDestination(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
            />
            <input
              type="datetime-local" value={tripDeparture} onChange={(e) => setTripDeparture(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
            />
            <input
              type="number" value={tripSeats} placeholder="Total seats" onChange={(e) => setTripSeats(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '8px' }}
            />
            <input
              type="number" value={tripFare} placeholder="Fare per seat (RWF)" onChange={(e) => setTripFare(e.target.value)}
              style={{ width: '100%', padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', marginBottom: '12px' }}
            />
            <button
              className="toss-btn toss-btn-primary" style={{ width: '100%' }}
              disabled={posting || !tripOrigin.trim() || !tripDestination.trim() || !tripDeparture || !tripSeats || !tripFare}
              onClick={handlePostTrip}
            >
              {posting ? 'Posting…' : 'Post route'}
            </button>
          </div>
          {myTrips && myTrips.length > 0 && (
            <div>
              <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Your routes</h4>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
                {myTrips.map((trip) => (
                  <div key={trip.id} className="toss-card">
                    <p style={{ fontSize: '13px', fontWeight: 700 }}>{trip.origin} → {trip.destination}</p>
                    <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
                      {new Date(trip.departureTime).toLocaleString([], { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })}
                      {' · '}{trip.availableSeats}/{trip.totalSeats} seats left · {trip.farePerSeat.toLocaleString()} RWF/seat
                    </p>
                    <button
                      className="toss-btn toss-btn-secondary" style={{ marginTop: '8px', fontSize: '12px', padding: '6px 10px' }}
                      onClick={() => toggleManifest(trip.id)}
                    >
                      {expandedTripId === trip.id ? 'Hide bookings' : 'View bookings'}
                    </button>
                    {expandedTripId === trip.id && (
                      <div style={{ marginTop: '8px', paddingTop: '8px', borderTop: '1px solid var(--toss-grey-100)' }}>
                        {manifestError && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{manifestError}</p>}
                        {!manifestError && tripBookings === null && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Loading…</p>}
                        {tripBookings !== null && tripBookings.length === 0 && (
                          <EmptyState message="No bookings yet." />
                        )}
                        {tripBookings !== null && tripBookings.length > 0 && (
                          <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                            {tripBookings.map((b) => (
                              <div key={b.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px' }}>
                                <span style={{ color: 'var(--toss-grey-700)' }}>
                                  Rider #{b.riderUserId.slice(-6)} · {b.seatCount} seat{b.seatCount > 1 ? 's' : ''}
                                </span>
                                <span style={{ fontWeight: 700, color: b.status === 'CANCELLED' ? 'var(--toss-grey-400)' : 'var(--toss-grey-900)' }}>
                                  {b.status === 'CANCELLED' ? 'Cancelled' : `${b.totalFare.toLocaleString()} RWF`}
                                </span>
                              </div>
                            ))}
                          </div>
                        )}
                      </div>
                    )}
                  </div>
                ))}
              </div>
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
      fetchMyKnowledgeQuestions().then(setQuestions).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your questions.'));
      fetchMyKnowledgeAnswers().then(setMyAnswers).catch(() => {});
      return;
    }
    fetchKnowledgeQuestions(activeCategory ?? undefined)
      .then(setQuestions)
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load questions.'));
  };

  useEffect(load, [subTab, activeCategory]);

  if (openQuestionId) {
    return <KnowledgeQuestionDetailView questionId={openQuestionId} onBack={() => { setOpenQuestionId(null); load(); }} />;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Your reputation</p>
        <p style={{ fontSize: '15px', fontWeight: 700 }}>{reputation ?? '…'} adopted answer{reputation === 1 ? '' : 's'}</p>
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button
          className={subTab === 'BROWSE' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('BROWSE')} style={{ flex: 1 }}
        >
          Browse
        </button>
        <button
          className={subTab === 'MINE' ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
          onClick={() => setSubTab('MINE')} style={{ flex: 1 }}
        >
          Mine
        </button>
      </div>
      {subTab === 'BROWSE' && categories.length > 0 && (
        <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
          <button
            className={activeCategory === null ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
            style={{ fontSize: '12px', padding: '6px 12px' }} onClick={() => setActiveCategory(null)}
          >
            All
          </button>
          {categories.map((c) => (
            <button
              key={c.id}
              className={activeCategory === c.id ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
              style={{ fontSize: '12px', padding: '6px 12px' }} onClick={() => setActiveCategory(c.id)}
            >
              {c.label}
            </button>
          ))}
        </div>
      )}
      {subTab === 'BROWSE' && <KnowledgeAskCard onAsked={load} categories={categories} />}
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {questions === null ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Loading…</p>
      ) : questions.length === 0 ? (
        <EmptyState message="No questions yet." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {questions.map((q) => (
            <button
              key={q.id} className="toss-card" style={{ textAlign: 'left', width: '100%' }}
              onClick={() => setOpenQuestionId(q.id)}
            >
              <p style={{ fontSize: '13px', fontWeight: 700 }}>
                {q.adoptedAnswerId ? '✅ ' : ''}{q.title}
              </p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginTop: '2px' }}>
                {categories.find((c) => c.id === q.category)?.label ?? q.category}
              </p>
            </button>
          ))}
        </div>
      )}
      {subTab === 'MINE' && myAnswers !== null && myAnswers.length > 0 && (
        <div>
          <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Your answers</h4>
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            {myAnswers.map((a) => (
              <div key={a.id} className="toss-card" style={{ padding: '10px 14px' }}>
                <p style={{ fontSize: '13px' }}>{a.isAdopted ? '✅ Adopted' : 'Pending'}</p>
                <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{a.body}</p>
              </div>
            ))}
          </div>
        </div>
      )}
    </div>
  );
}

function KnowledgeAskCard({ onAsked, categories }: { onAsked: () => void; categories: KnowledgeCategory[] }) {
  const [category, setCategory] = useState('');
  const [title, setTitle] = useState('');
  const [body, setBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [open, setOpen] = useState(false);

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" style={{ width: '100%' }} onClick={() => setOpen(true)}>
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
      setError(err instanceof ApiError ? err.message : 'Could not post this question.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <select
        value={category} onChange={(e) => setCategory(e.target.value)}
        style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      >
        <option value="">Choose a category</option>
        {categories.map((c) => <option key={c.id} value={c.id}>{c.label}</option>)}
      </select>
      <input
        type="text" value={title} placeholder="Your question" onChange={(e) => setTitle(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <textarea
        value={body} placeholder="Add more detail" onChange={(e) => setBody(e.target.value)} rows={3}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'vertical' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting || !category || !title.trim() || !body.trim()}>
        {submitting ? 'Posting…' : 'Post question'}
      </button>
    </form>
  );
}

function KnowledgeQuestionDetailView({ questionId, onBack }: { questionId: string; onBack: () => void }) {
  const [question, setQuestion] = useState<KnowledgeQuestion | null>(null);
  const [answers, setAnswers] = useState<KnowledgeAnswer[] | null>(null);
  const [answerBody, setAnswerBody] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [answering, setAnswering] = useState(false);
  const [busyAnswerId, setBusyAnswerId] = useState<string | null>(null);
  const currentUser = getStoredUser();

  const load = () => {
    setError(null);
    fetchKnowledgeQuestion(questionId).then(setQuestion).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this question.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not post your answer.');
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
      setError(err instanceof ApiError ? err.message : 'Could not adopt this answer.');
    } finally {
      setBusyAnswerId(null);
    }
  };

  const isAsker = !!question && !!currentUser && question.askerId === currentUser.id;

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" style={{ marginBottom: '12px' }} onClick={onBack}>← Back</button>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
      {!question && !error && <div className="toss-card skeleton" style={{ height: '120px' }} />}
      {question && (
        <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '16px' }}>
          <p style={{ fontSize: '17px', fontWeight: 700 }}>{question.title}</p>
          <p style={{ fontSize: '14px', color: 'var(--toss-grey-700)', whiteSpace: 'pre-wrap' }}>{question.body}</p>
        </div>
      )}
      <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Answers</h3>
      {answers === null && <div className="toss-card skeleton" style={{ height: '80px' }} />}
      {answers !== null && answers.length === 0 && (
        <EmptyState message="No answers yet -- be the first to help." />
      )}
      {answers !== null && answers.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginBottom: '12px' }}>
          {answers.map((a) => (
            <div
              key={a.id} className="toss-card"
              style={{ padding: '10px 14px', border: a.isAdopted ? '1.5px solid var(--toss-blue)' : undefined }}
            >
              {a.isAdopted && <p style={{ fontSize: '11px', fontWeight: 700, color: 'var(--toss-blue)', marginBottom: '4px' }}>✅ Adopted answer</p>}
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-900)' }}>{a.body}</p>
              {isAsker && !question?.adoptedAnswerId && (
                <button
                  className="toss-btn toss-btn-secondary" style={{ marginTop: '8px', fontSize: '12px' }}
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
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={answering || !answerBody.trim()}>
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
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{DINE_IN_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Table {order.tableNumber}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.notes && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)', backgroundColor: 'var(--toss-grey-100)', borderRadius: '8px', padding: '8px 10px' }}>
          Note: {order.notes}
        </p>
      )}
      {action}
    </div>
  );
}

function DineInRestaurantOrdersView() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your dine-in orders.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this order.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Table orders for your restaurant</h4>
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
                      <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
                        {busyOrderId === o.id ? 'Updating…' : `Mark ${DINE_IN_STATUS_LABEL[next].toLowerCase()}`}
                      </button>
                    )}
                    {o.status === 'PLACED' && (
                      <button className="toss-btn toss-btn-secondary" disabled={busyOrderId === o.id} onClick={() => handleCancel(o)}>
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this menu.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not place this order.');
    } finally {
      setPlacing(false);
    }
  };

  if (error) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }

  if (menu === null) {
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  if (showCheckout) {
    return (
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
          <button onClick={() => setShowCheckout(false)} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to menu">
            <ArrowLeft size={20} />
          </button>
          <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Checkout</h3>
        </div>
        <form onSubmit={handlePlaceOrder} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {cartItems.map(([key, line]) => {
            const item = menu.products.find((p) => p.id === line.productId);
            if (!item) return null;
            const unitPrice = eatsLineUnitPrice(item, line.choiceIds);
            return (
              <div key={key} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '14px' }}>
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
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <textarea
            value={notes}
            onChange={(e) => setNotes(e.target.value.slice(0, 500))}
            placeholder="Notes (optional) -- e.g. No onions"
            rows={2}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px', resize: 'none', fontFamily: 'inherit' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || !tableNumber.trim()}>
            {placing ? 'Placing order…' : 'Place order'}
          </button>
          {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
        </form>
      </div>
    );
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to restaurants">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{menu.businessName}</h3>
      </div>
      {menu.products.length === 0 ? (
        <EmptyState message="No menu items yet." />
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
              <div key={item.id} className="toss-card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <div>
                    <p style={{ fontSize: '14px', fontWeight: 700 }}>{item.name}</p>
                    <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{item.price.toLocaleString()} RWF</p>
                  </div>
                  {hasOptions ? (
                    <button className="toss-btn toss-btn-secondary" onClick={() => toggleExpand(item.id)}>
                      {isExpanded ? 'Close' : 'Add'}
                    </button>
                  ) : (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                      <button onClick={() => setSimpleQty(item.id, simpleQty - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                      <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{simpleQty}</span>
                      <button onClick={() => setSimpleQty(item.id, simpleQty + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>+</button>
                    </div>
                  )}
                </div>
                {isExpanded && (
                  <div style={{ marginTop: '10px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
                    {groups.map((group) => (
                      <div key={group.id}>
                        <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-700)', marginBottom: '4px' }}>{group.name}</p>
                        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                          {group.choices.map((choice) => (
                            <label key={choice.id} style={{ display: 'flex', alignItems: 'center', gap: '8px', fontSize: '13px' }}>
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
                      className="toss-btn toss-btn-primary"
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
          className="toss-btn toss-btn-primary"
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
  const [view, setView] = useState<'BROWSE' | 'ORDERS'>('BROWSE');
  const [restaurants, setRestaurants] = useState<ShoppingMerchant[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [selected, setSelected] = useState<ShoppingMerchant | null>(null);
  const [confirmed, setConfirmed] = useState<DineInOrder | null>(null);
  const [orders, setOrders] = useState<DineInOrder[] | null>(null);

  useEffect(() => {
    fetchRestaurants().then(setRestaurants).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load restaurants.'));
  }, []);

  useEffect(() => {
    if (view === 'ORDERS') {
      fetchMyDineInOrders().then(setOrders).catch(() => setOrders([]));
    }
  }, [view]);

  if (confirmed) {
    return (
      <div className="toss-card" style={{ textAlign: 'center', padding: '28px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700, marginBottom: '4px' }}>Order placed</h3>
        <p style={{ fontSize: '22px', fontWeight: 700, marginBottom: '4px' }}>{confirmed.totalAmount.toLocaleString()} RWF</p>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>Table {confirmed.tableNumber}</p>
        <button className="toss-btn toss-btn-secondary" onClick={() => { setConfirmed(null); setSelected(null); setView('ORDERS'); }}>Done</button>
      </div>
    );
  }

  if (selected) {
    return <DineInMenuView restaurant={selected} onBack={() => setSelected(null)} onOrderPlaced={setConfirmed} />;
  }

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'ORDERS'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Restaurants' : 'My orders'}
          </button>
        ))}
      </div>
      {view === 'BROWSE' ? (
        error ? (
          <div className="toss-card"><p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p></div>
        ) : restaurants === null ? (
          <div className="toss-card skeleton" style={{ height: '160px' }} />
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {restaurants.map((r) => (
              <button key={r.merchantId} onClick={() => setSelected(r)} className="toss-card" style={{ display: 'block', width: '100%', textAlign: 'left', border: 'none' }}>
                <p style={{ fontSize: '14px', fontWeight: 700 }}>{r.businessName}</p>
                {r.category && <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.category}</p>}
              </button>
            ))}
          </div>
        )
      ) : orders === null ? (
        <div className="toss-card skeleton" style={{ height: '160px' }} />
      ) : orders.length === 0 ? (
        <EmptyState message="No table orders yet." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {orders.map((o) => <DineInOrderCard key={o.id} order={o} />)}
        </div>
      )}
    </div>
  );
}

function EatsView() {
  const [mode, setMode] = useState<'ORDER' | 'DELIVER' | 'DINE_IN'>('ORDER');

  return (
    <div>
      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['ORDER', 'DELIVER', 'DINE_IN'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setMode(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: mode === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: mode === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'ORDER' ? 'Order food' : v === 'DELIVER' ? 'Deliver' : 'Dine-in'}
          </button>
        ))}
      </div>
      {mode === 'ORDER' ? (
        <div>
          <PlatformMembershipCard />
          <EatsMembershipCard />
          <RestaurantOrdersView />
          <OrderFoodView />
        </div>
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

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// lib/eats.ts's own doc comment. Deliberately a separate card from Eats Club below,
// not a replacement: this waives the fee at every restaurant, no merchant opt-in
// required, the same real broader guarantee Coupang Wow has over Baemin Club's
// participating-seller-only free delivery.
function PlatformMembershipCard() {
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
      setError(err instanceof ApiError ? err.message : 'Could not subscribe.');
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  return (
    <div className="toss-card" style={{ padding: '16px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>itunda Plus</h3>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at every restaurant, no participation required.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
            Free delivery at every restaurant -- no minimum order, no restaurant opt-in required.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {PLATFORM_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                className="toss-btn toss-btn-primary"
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: '13px' }}
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
      setError(err instanceof ApiError ? err.message : 'Could not subscribe to Eats Club.');
    } finally {
      setBusy(false);
    }
  };

  if (membership === undefined) return null;

  return (
    <div className="toss-card" style={{ padding: '16px', marginBottom: '16px' }}>
      <h3 style={{ fontSize: '15px', fontWeight: 700, marginBottom: '4px' }}>Eats Club</h3>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      {isActive ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          Free delivery active until {new Date(membership!.activeUntil).toLocaleDateString()} at participating restaurants.
        </p>
      ) : (
        <div>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
            Free delivery at participating restaurants -- no minimum order.
          </p>
          <div style={{ display: 'flex', gap: '8px' }}>
            {EATS_MEMBERSHIP_TIERS.map((tier) => (
              <button
                key={tier.days}
                className="toss-btn toss-btn-primary"
                disabled={busy}
                onClick={() => handleSubscribe(tier.days)}
                style={{ flex: 1, fontSize: '13px' }}
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
    <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
        <div>
          <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>{COMMERCE_STATUS_LABEL[order.status]}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{order.deliveryAddress}</p>
        </div>
        <span style={{ fontSize: '16px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{order.totalAmount.toLocaleString()} RWF</span>
      </div>
      {order.status === 'SHIPPED' && (
        <button className="toss-btn toss-btn-primary" onClick={() => setShowLiveTracking((v) => !v)}>
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
        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '12px', color: 'var(--toss-grey-700)', padding: 0 }}
      >
        <Star size={13} color="#F5A623" fill="#F5A623" />
        {rating.average?.toFixed(1)} ({rating.count})
      </button>
      {open && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
          {reviews === null ? (
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Loading reviews…</p>
          ) : reviews.length === 0 ? (
            <EmptyState message="No written reviews yet." />
          ) : (
            reviews.map((r) => (
              <div key={r.id} style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>
                <span style={{ color: '#F5A623' }}>{'★'.repeat(r.rating)}{'☆'.repeat(5 - r.rating)}</span>
                {r.comment && <span> — {r.comment}</span>}
                {r.ownerReply && (
                  <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--toss-grey-500)' }}>
                    ↳ Seller: {r.ownerReply}
                  </div>
                )}
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
    <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--toss-grey-100)' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '6px' }}>Buy more, pay less</p>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          <span>1+</span>
          <span>{regularPrice.toLocaleString()} RWF each</span>
        </div>
        {tiers.map((t) => (
          <div key={t.minQuantity} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '12px', color: 'var(--toss-grey-700)', fontWeight: 600 }}>
            <span>{t.minQuantity}+</span>
            <span>{t.unitPrice.toLocaleString()} RWF each</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// Real Naver Smart Place/Kakao Hair Shop/Karrot Business-Profile-style local business
// appointment booking (item 220) -- see lib/booking.ts's own doc comment. A product
// with durationMinutes set is bookable; requiresPrepay means the customer's deposit is
// held automatically the moment they request the slot (no separate payment step).
function BookingWidget({ merchantId, product }: { merchantId: string; product: CommerceProduct }) {
  const [date, setDate] = useState('');
  const [slots, setSlots] = useState<BookingSlot[] | null>(null);
  const [slotsError, setSlotsError] = useState<string | null>(null);
  const [requesting, setRequesting] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [requested, setRequested] = useState(false);

  if (!product.durationMinutes) return null;

  const loadSlots = (d: string) => {
    setDate(d);
    setSlots(null);
    setSlotsError(null);
    if (!d) return;
    fetchAvailableSlots(merchantId, product.id, d)
      .then(setSlots)
      .catch((err) => setSlotsError(err instanceof ApiError ? err.message : 'Could not load available times.'));
  };

  const book = async (slot: BookingSlot) => {
    setRequesting(slot.startTime);
    setError(null);
    try {
      await createBooking(merchantId, product.id, date, slot.startTime);
      setRequested(true);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not request this booking.');
    } finally {
      setRequesting(null);
    }
  };

  if (requested) {
    return (
      <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--toss-grey-100)' }}>
        <p style={{ fontSize: '13px', fontWeight: 700 }}>Booking requested</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>The business will confirm or decline your appointment. See it under My &gt; My bookings.</p>
      </div>
    );
  }

  return (
    <div style={{ padding: '12px', borderRadius: '10px', background: 'var(--toss-grey-100)', display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700 }}>Book an appointment ({product.durationMinutes} min)</p>
      {product.requiresPrepay && (
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
          Requesting this slot holds a {product.price.toLocaleString()} RWF deposit from your wallet.
        </p>
      )}
      <input
        type="date"
        value={date}
        min={new Date().toISOString().slice(0, 10)}
        onChange={(e) => loadSlots(e.target.value)}
        style={{ padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {slotsError && <p style={{ fontSize: '12px', color: '#E53935' }}>{slotsError}</p>}
      {error && <p style={{ fontSize: '12px', color: '#E53935' }}>{error}</p>}
      {date && slots !== null && (
        slots.length === 0 ? (
          <EmptyState message="No open times on this date." />
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
            {slots.map((slot) => (
              <button
                key={slot.startTime}
                className="toss-btn toss-btn-secondary"
                style={{ padding: '8px 12px', fontSize: '12px' }}
                onClick={() => book(slot)}
                disabled={requesting !== null}
              >
                {requesting === slot.startTime ? '…' : slot.startTime.slice(0, 5)}
              </button>
            ))}
          </div>
        )
      )}
    </div>
  );
}

// Real pre-booking browsing (item 231) -- see lib/booking.ts's own doc comment for the
// full sourced account. Two genuinely distinct real backend endpoints combined into one
// section since both only matter at the moment a buyer is deciding whether to book:
// this merchant's real review history/rating, and which of this merchant's real
// coupons the buyer is eligible for (regularsOnly-gated ones are already filtered out
// server-side by getCouponsForCustomer, not client-side).
function MerchantBookingInfoSection({ merchantId }: { merchantId: string }) {
  const [reviews, setReviews] = useState<MerchantBookingReview[] | null>(null);
  const [rating, setRating] = useState<{ average: number | null; count: number } | null>(null);
  const [coupons, setCoupons] = useState<MerchantCoupon[] | null>(null);

  useEffect(() => {
    fetchMerchantReviews(merchantId)
      .then((r) => {
        setReviews(r.reviews);
        setRating(r.rating);
      })
      .catch(() => {
        setReviews([]);
        setRating(null);
      });
    fetchCouponsForCustomer(merchantId).then(setCoupons).catch(() => setCoupons([]));
  }, [merchantId]);

  if ((reviews === null || reviews.length === 0) && (coupons === null || coupons.length === 0)) return null;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {coupons !== null && coupons.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>Coupons for you</p>
          {coupons.map((c) => (
            <div key={c.id} style={{ padding: '10px 12px', borderRadius: '10px', background: 'var(--toss-blue-50, #EAF2FF)', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <div>
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{c.title}</p>
                {c.description && <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{c.description}</p>}
              </div>
              <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-blue)' }}>
                {c.discountType === 'PERCENT' ? `${c.discountValue}% off` : `${c.discountValue.toLocaleString()} RWF off`}
              </p>
            </div>
          ))}
        </div>
      )}
      {reviews !== null && reviews.length > 0 && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
          <p style={{ fontSize: '13px', fontWeight: 700 }}>
            Reviews{rating?.average != null && ` · ⭐ ${rating.average.toFixed(1)} (${rating.count})`}
          </p>
          {reviews.slice(0, 3).map((r) => (
            <div key={r.id} style={{ padding: '10px 12px', borderRadius: '10px', background: 'var(--toss-grey-100)' }}>
              <p style={{ fontSize: '12px', fontWeight: 700 }}>{'⭐'.repeat(r.rating)} · {r.serviceName}</p>
              {r.comment && <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>{r.comment}</p>}
              {r.ownerReply && <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '4px' }}>↳ {r.ownerReply}</p>}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}

// Real Coupang-style pre-purchase product Q&A (상품문의) (2026-07-26) -- see
// ProductInquiryService's own doc comment on the backend. Genuinely distinct from
// ProductRatingBadge's reviews above: no order/purchase required at all, so this is
// always visible on a product's detail page, not gated behind having bought it.
function ProductInquirySection({ productId }: { productId: string }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not submit your question.');
    } finally {
      setAsking(false);
    }
  };

  return (
    <div style={{ borderTop: '1px solid var(--toss-grey-100)', paddingTop: '12px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-900)', marginBottom: '8px' }}>Questions & answers</p>
      <form onSubmit={handleAsk} style={{ display: 'flex', gap: '8px', marginBottom: '10px' }}>
        <input
          type="text"
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder="Ask the seller a question"
          style={{ flex: 1, padding: '8px 10px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
        />
        <button type="submit" className="toss-btn toss-btn-secondary" disabled={asking || !question.trim()} style={{ padding: '8px 14px' }}>
          Ask
        </button>
      </form>
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      {inquiries === null ? (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Loading questions…</p>
      ) : inquiries.length === 0 ? (
        <EmptyState message="No questions yet -- be the first to ask." />
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
          {inquiries.map((q) => (
            <div key={q.id} style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>
              <span style={{ fontWeight: 700 }}>Q. </span>{q.question}
              {q.answer ? (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--toss-grey-500)' }}>
                  <span style={{ fontWeight: 700 }}>A. </span>{q.answer}
                </div>
              ) : (
                <div style={{ marginTop: '2px', marginLeft: '12px', color: 'var(--toss-grey-400)', fontStyle: 'italic' }}>
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
        setError(err instanceof ApiError ? err.message : 'Could not submit this review.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (done) {
    return (
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{item.productName}: thanks for your review!</p>
    );
  }

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" style={{ fontSize: '12px', padding: '8px 12px' }} onClick={() => setOpen(true)}>
        Rate {item.productName}
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{item.productName}</p>
      <StarRatingInput value={rating} onChange={setRating} />
      <input
        type="text"
        value={comment}
        onChange={(e) => setComment(e.target.value)}
        placeholder="How was it? (optional)"
        style={{ width: '100%', padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '10px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting}>
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
      setError(err instanceof ApiError ? err.message : 'Could not submit this request.');
    } finally {
      setSubmitting(false);
    }
  };

  if (result) {
    return <p style={{ fontSize: '12px', color: 'var(--toss-blue)', fontWeight: 600, marginTop: '6px' }}>{result.type === 'RETURN' ? 'Return' : 'Exchange'} requested — awaiting seller review.</p>;
  }

  if (!open) {
    return (
      <button className="toss-btn toss-btn-secondary" style={{ marginTop: '6px', padding: '6px 12px', fontSize: '12px' }} onClick={() => setOpen(true)}>
        Return or exchange
      </button>
    );
  }

  return (
    <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '8px', marginTop: '8px', padding: '10px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', gap: '8px' }}>
        <select value={type} onChange={(e) => setType(e.target.value as OrderReturnType)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}>
          <option value="RETURN">Return</option>
          <option value="EXCHANGE">Exchange</option>
        </select>
        <select value={reasonCode} onChange={(e) => setReasonCode(e.target.value)} style={{ flex: 1, padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}>
          {ORDER_RETURN_REASON_CODES.map((r) => <option key={r} value={r}>{r.replace(/_/g, ' ').toLowerCase()}</option>)}
        </select>
      </div>
      <input
        type="text"
        value={reasonNote}
        onChange={(e) => setReasonNote(e.target.value)}
        placeholder="Details (optional)"
        style={{ padding: '8px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
      />
      {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting} style={{ flex: 1, padding: '8px', fontSize: '13px' }}>
          {submitting ? 'Submitting…' : 'Submit request'}
        </button>
        <button type="button" className="toss-btn toss-btn-secondary" onClick={() => setOpen(false)} style={{ padding: '8px 12px', fontSize: '13px' }}>Cancel</button>
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
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>My return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
        {requests.map((r) => (
          <div key={r.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: '13px', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'}</p>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</p>
            </div>
            <span style={{
              fontSize: '12px', fontWeight: 700,
              color: r.status === 'APPROVED' ? 'var(--toss-green)' : r.status === 'REJECTED' ? '#E53935' : 'var(--toss-blue)',
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
          setError(err instanceof ApiError ? err.message : 'Could not load return requests.');
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
      setError(err instanceof ApiError ? err.message : 'Could not decide this request.');
    } finally {
      setBusyId(null);
    }
  };

  if (error) {
    return (
      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
      </div>
    );
  }
  if (requests === null) return <div className="toss-card skeleton" style={{ height: '80px', marginBottom: '16px' }} />;
  const open = requests.filter((r) => r.status === 'REQUESTED');
  if (open.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Return &amp; exchange requests</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {open.map((r) => (
          <div key={r.id} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between' }}>
              <p style={{ fontSize: '14px', fontWeight: 700 }}>{r.type === 'RETURN' ? 'Return' : 'Exchange'} requested</p>
              <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{r.reasonCode.replace(/_/g, ' ').toLowerCase()}</span>
            </div>
            {r.reasonNote && <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)' }}>{r.reasonNote}</p>}
            <div style={{ display: 'flex', gap: '8px' }}>
              <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, true)}>
                {busyId === r.id ? '…' : 'Approve'}
              </button>
              <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busyId === r.id} onClick={() => handleDecide(r.id, false)}>
                Reject
              </button>
            </div>
          </div>
        ))}
      </div>
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
function WishlistButton({ favorited, busy, onToggle }: { favorited: boolean; busy: boolean; onToggle: () => void }) {
  return (
    <button
      type="button"
      onClick={onToggle}
      disabled={busy}
      aria-label={favorited ? 'Remove from wishlist' : 'Add to wishlist'}
      style={{ fontSize: '18px', lineHeight: 1, color: favorited ? '#E53935' : 'var(--toss-grey-300)' }}
    >
      {favorited ? '♥' : '♡'}
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
function TrustBadge({ score }: { score: number }) {
  return (
    <span
      style={{
        fontSize: '11px', fontWeight: 700, color: 'var(--toss-grey-600)',
        backgroundColor: 'var(--toss-grey-100)', padding: '2px 6px', borderRadius: '6px',
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
function HoodReportButton({ targetType, targetId }: { targetType: HoodReportTargetType; targetId: string }) {
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
        <p style={{ fontSize: '11px', color: message.startsWith('Thanks') ? 'var(--toss-green)' : '#E53935' }}>{message}</p>
      ) : showChoices ? (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => send('Unsafe payment, contact request, or scam')}>Unsafe or scam</button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => send('Misleading, unavailable, or spam content')}>Misleading or spam</button>
          <button className="toss-btn toss-btn-secondary" style={{ fontSize: '11px', padding: '4px 10px' }} onClick={() => send('Harassment, hateful, illegal, or prohibited content')}>Abusive or illegal</button>
          <button style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }} onClick={() => setShowChoices(false)}>Cancel</button>
        </div>
      ) : (
        <button style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }} disabled={sending} onClick={() => setShowChoices(true)}>
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
function HoodReviewForm({
  selectedGoodPoints, onToggleGoodPoint, selectedUncomfortablePoints, onToggleUncomfortablePoint, submitting, onCancel, onSubmit,
}: {
  selectedGoodPoints: Set<string>; onToggleGoodPoint: (id: string) => void;
  selectedUncomfortablePoints: Set<string>; onToggleUncomfortablePoint: (id: string) => void;
  submitting: boolean; onCancel: () => void; onSubmit: () => void;
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>What went well? (shown publicly)</p>
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {HOOD_GOOD_POINT_LABELS.map(([id, label]) => {
          const selected = selectedGoodPoints.has(id);
          return (
            <button
              key={id}
              onClick={() => onToggleGoodPoint(id)}
              style={{
                fontSize: '12px', fontWeight: 700, padding: '6px 12px', borderRadius: '999px',
                color: selected ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                backgroundColor: selected ? 'var(--toss-blue)' : 'var(--toss-grey-100)',
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
      <p style={{ fontSize: '13px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>Anything uncomfortable? (private -- only you two see this)</p>
      <div style={{ display: 'flex', gap: '6px', flexWrap: 'wrap' }}>
        {HOOD_UNCOMFORTABLE_POINT_LABELS.map(([id, label]) => {
          const selected = selectedUncomfortablePoints.has(id);
          return (
            <button
              key={id}
              onClick={() => onToggleUncomfortablePoint(id)}
              style={{
                fontSize: '12px', fontWeight: 700, padding: '6px 12px', borderRadius: '999px',
                color: selected ? 'var(--toss-white)' : 'var(--toss-grey-900)',
                backgroundColor: selected ? '#E53935' : 'var(--toss-grey-100)',
              }}
            >
              {label}
            </button>
          );
        })}
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={submitting} onClick={onCancel}>
          Cancel
        </button>
        <button className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={submitting} onClick={onSubmit}>
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
function HoodReviewResultView({ reviews, myUserId }: { reviews: HoodReview[]; myUserId: string | undefined }) {
  const mine = reviews.find((r) => r.reviewerId === myUserId);
  const theirs = reviews.find((r) => r.reviewerId !== myUserId);
  if (!mine && !theirs) return null;
  const block = (title: string, review: HoodReview) => (
    <div style={{ padding: '10px 12px', borderRadius: '8px', background: 'var(--toss-grey-100)', display: 'flex', flexDirection: 'column', gap: '2px' }}>
      <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{title}</p>
      {review.goodPoints.length > 0 && (
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>👍 {review.goodPoints.map(hoodGoodPointLabel).join(', ')}</p>
      )}
      {review.uncomfortablePoints.length > 0 && (
        <p style={{ fontSize: '12px', color: '#E53935' }}>⚠️ {review.uncomfortablePoints.map(hoodUncomfortablePointLabel).join(', ')}</p>
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
      <div style={{ width: size, height: size, borderRadius: '12px', background: 'var(--toss-grey-100)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
        <ShoppingBag size={size * 0.4} color="var(--toss-blue)" />
      </div>
    );
  }
  return (
    <img
      src={imageUrl}
      alt=""
      onError={() => setFailed(true)}
      style={{ width: size, height: size, borderRadius: '12px', objectFit: 'cover', background: 'var(--toss-grey-100)', flexShrink: 0 }}
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
          <span style={{ fontSize: '13px', fontWeight: 700, color: '#E53935' }}>{discountPercent}%</span>
          <span style={{ fontSize: '14px', fontWeight: 700 }}>{price.toLocaleString()} RWF</span>
        </div>
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-400)', textDecoration: 'line-through' }}>{originalPrice.toLocaleString()} RWF</p>
      </div>
    );
  }
  return <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{price.toLocaleString()} RWF</p>;
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
  merchant, product, cart, onSetQty, onBack, onViewCart,
}: {
  merchant: ShoppingMerchant;
  product: CommerceProduct;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
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
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to catalog">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{merchant.businessName}</h3>
      </div>
      <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '14px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
        <div style={{ display: 'flex', justifyContent: 'center' }}>
          <ProductImageThumb imageUrl={product.imageUrl} size={220} />
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
          <div>
            <p style={{ fontSize: '18px', fontWeight: 700 }}>{product.name}</p>
            <ProductPriceBlock price={product.price} originalPrice={product.originalPrice} discountPercent={product.discountPercent} />
          </div>
          <WishlistButton favorited={favorited} busy={busy} onToggle={toggleFavorite} />
        </div>
        <ProductRatingBadge productId={product.id} />
        {product.description && (
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)', lineHeight: 1.5, whiteSpace: 'pre-wrap' }}>{product.description}</p>
        )}
        <PriceTiersDisplay productId={product.id} regularPrice={product.price} />
        <BookingWidget merchantId={merchant.merchantId} product={product} />
        {product.durationMinutes != null && <MerchantBookingInfoSection merchantId={merchant.merchantId} />}
        <ProductInquirySection productId={product.id} />
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '16px', paddingTop: '4px', borderTop: '1px solid var(--toss-grey-100)' }}>
          <button onClick={() => onSetQty(merchant, product, Math.max(0, qty - 1))} className="toss-btn toss-btn-secondary" style={{ padding: '8px 16px' }}>−</button>
          <span style={{ minWidth: '24px', textAlign: 'center', fontWeight: 700, fontSize: '16px' }}>{qty}</span>
          <button onClick={() => onSetQty(merchant, product, qty + 1)} className="toss-btn toss-btn-secondary" style={{ padding: '8px 16px' }}>+</button>
        </div>
        <SubscribeAndSaveButton merchantId={merchant.merchantId} productId={product.id} />
        <button className="toss-btn toss-btn-primary" onClick={() => onSetQty(merchant, product, Math.max(1, qty))}>
          {qty > 0 ? 'Update cart' : 'Add to cart'}
        </button>
      </div>
      {totalCartItems > 0 && (
        <button
          className="toss-btn toss-btn-primary"
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
  merchant, cart, onSetQty, onBack, onViewCart, onOpenProduct,
}: {
  merchant: ShoppingMerchant;
  cart: CommerceCart;
  onSetQty: (merchant: ShoppingMerchant, product: CommerceProduct, quantity: number) => void;
  onBack: () => void;
  onViewCart: () => void;
  onOpenProduct: (product: CommerceProduct) => void;
}) {
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
      .then((r) => setCatalog({ businessName: r.merchant.businessName, products: r.products }))
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this catalog.'));
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
      setShareNotice(err instanceof ApiError ? err.message : 'Could not create a share link.');
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
    return <div className="toss-card skeleton" style={{ height: '220px' }} />;
  }

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to merchants">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700, flex: 1 }}>{catalog.businessName}</h3>
        <button
          type="button"
          onClick={toggleFollow}
          disabled={followBusy}
          className={following ? 'toss-btn toss-btn-secondary' : 'toss-btn toss-btn-primary'}
          style={{ padding: '6px 14px', fontSize: '13px' }}
        >
          {following ? 'Following' : 'Follow'}
        </button>
      </div>
      {shareNotice && <p style={{ fontSize: '12px', color: 'var(--toss-blue)', marginBottom: '12px' }} role="status">{shareNotice}</p>}
      {billingPlans.length > 0 && (
        <div className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
          <h4 style={{ fontSize: '14px', fontWeight: 700 }}>Subscription plans</h4>
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
        <EmptyState message="No products yet." />
      ) : (
        // Real 2-column image-led grid (2026-07-21), replacing the previous
        // single-column text-only row -- closes docs/DESIGN_REFERENCES.md Section 5
        // recommendation #5 (Chloe Youn's Coupang case study: real cards are
        // image-led, with add-to-cart/wishlist directly on the card, not buried behind
        // a detail-page visit -- recommendation #7).
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '10px', marginBottom: totalCartItems > 0 ? '80px' : 0 }}>
          {catalog.products.map((item) => (
            <div key={item.id} className="toss-card" style={{ position: 'relative', display: 'flex', flexDirection: 'column', gap: '6px' }}>
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
                  style={{ background: 'var(--toss-white)', borderRadius: '999px', padding: '6px', boxShadow: '0 1px 4px rgba(0,0,0,0.12)', fontSize: '13px' }}
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
                <p style={{ fontSize: '14px', fontWeight: 700, lineHeight: 1.3 }}>{item.name}</p>
                <ProductPriceBlock price={item.price} originalPrice={item.originalPrice} discountPercent={item.discountPercent} />
              </button>
              <p style={{ minHeight: '16px', fontSize: '12px', color: item.stockQuantity === 0 ? '#E53935' : 'var(--toss-grey-500)' }}>
                {item.stockQuantity === null || item.stockQuantity === undefined ? 'Available' : item.stockQuantity === 0 ? 'Out of stock' : `${item.stockQuantity} available`}
              </p>
              <ProductRatingBadge productId={item.id} />
              <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px', marginTop: '4px' }}>
                <button onClick={() => onSetQty(merchant, item, qtyFor(item.id) - 1)} className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px' }}>−</button>
                <span style={{ minWidth: '16px', textAlign: 'center', fontWeight: 700 }}>{qtyFor(item.id)}</span>
                <button
                  onClick={() => onSetQty(merchant, item, qtyFor(item.id) + 1)}
                  disabled={item.stockQuantity !== null && item.stockQuantity !== undefined && qtyFor(item.id) >= item.stockQuantity}
                  className="toss-btn toss-btn-secondary"
                  style={{ padding: '6px 12px' }}
                >+</button>
              </div>
            </div>
          ))}
        </div>
      )}
      {totalCartItems > 0 && (
        <button
          className="toss-btn toss-btn-primary"
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
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubscribe = async () => {
    setError(null);
    setBusy(true);
    try {
      await subscribeToBillingPlan(plan.id);
      onChanged();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not subscribe to this plan.');
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this subscription.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', padding: '12px', background: 'var(--toss-grey-100)', borderRadius: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: '10px' }}>
        <div>
          <p style={{ fontSize: '14px', fontWeight: 700 }}>{plan.name}</p>
          {plan.description && <p style={{ fontSize: '12px', color: 'var(--toss-grey-700)' }}>{plan.description}</p>}
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-700)', marginTop: '2px' }}>
            {plan.amount.toLocaleString()} RWF every {plan.intervalDays} day{plan.intervalDays === 1 ? '' : 's'}
          </p>
        </div>
        <button
          className={subscription ? 'toss-btn toss-btn-secondary' : 'toss-btn toss-btn-primary'}
          style={{ padding: '6px 12px', fontSize: '12px', whiteSpace: 'nowrap' }}
          disabled={busy}
          onClick={subscription ? handleCancel : handleSubscribe}
        >
          {busy ? '…' : subscription ? 'Cancel' : 'Subscribe'}
        </button>
      </div>
      {subscription && (
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>
          Next charge {new Date(subscription.nextChargeAt).toLocaleDateString()}
        </p>
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
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

function MultiCartView({
  cart, onBack, onSetQty, onCheckedOut,
}: {
  cart: CommerceCart;
  onBack: () => void;
  onSetQty: (merchantId: string, productId: string, quantity: number) => void;
  onCheckedOut: (results: CommerceCheckoutResult[]) => void;
}) {
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

  const groups = Object.entries(cart).filter(([, g]) => Object.values(g.lines).some((l) => l.quantity > 0));
  const grandTotal = groups.reduce(
    (sum, [, g]) => sum + Object.values(g.lines).reduce((s, l) => s + l.product.price * l.quantity, 0),
    0,
  );

  // Real per-seller order splitting -- each merchant group becomes its own real,
  // independent placeOrder() call (its own Idempotency-Key, its own wallet-to-wallet
  // ledger transaction). Sequential, not Promise.all: these are real money-moving
  // calls against the same buyer wallet, and a clear one-at-a-time result list is
  // more honest than a swallowed Promise.allSettled. A failure on one merchant's
  // order does not block or roll back any other -- exactly how a real multi-seller
  // checkout behaves (each seller is charged/fulfilled independently in real life).
  const handlePlaceOrders = async (e: React.FormEvent) => {
    e.preventDefault();
    setPlacing(true);
    setError(null);
    setNeedsDeviceVerification(false);
    const results: CommerceCheckoutResult[] = [];
    for (const [merchantId, group] of groups) {
      const items = Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => ({ productId, quantity: l.quantity }));
      try {
        const result = await placeOrder(merchantId, items, address.trim(), getStoredReferralCode());
        results.push({ merchantId, businessName: group.businessName, success: true, order: result.order });
      } catch (err) {
        if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') {
          setNeedsDeviceVerification(true);
          setPlacing(false);
          return;
        }
        results.push({ merchantId, businessName: group.businessName, success: false, error: err instanceof ApiError ? err.message : 'Could not place this order.' });
      }
    }
    setPlacing(false);
    onCheckedOut(results);
  };

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: '10px', marginBottom: '16px' }}>
        <button onClick={onBack} style={{ display: 'flex', color: 'var(--toss-grey-700)' }} aria-label="Back to shop">
          <ArrowLeft size={20} />
        </button>
        <h3 style={{ fontSize: '16px', fontWeight: 700 }}>Your cart</h3>
      </div>
      {groups.length === 0 ? (
        <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>Your cart is empty.</p></div>
      ) : (
        <form onSubmit={handlePlaceOrders} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
          {groups.map(([merchantId, group]) => (
            <div key={merchantId} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <p style={{ fontSize: '14px', fontWeight: 700 }}>{group.businessName}</p>
              {Object.entries(group.lines).filter(([, l]) => l.quantity > 0).map(([productId, l]) => (
                <div key={productId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
                  <span>{l.product.name} x{l.quantity}</span>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
                    <span>{(l.product.price * l.quantity).toLocaleString()} RWF</span>
                    <button type="button" onClick={() => onSetQty(merchantId, productId, 0)} style={{ color: 'var(--toss-grey-500)', fontSize: '12px' }}>Remove</button>
                  </div>
                </div>
              ))}
            </div>
          ))}
          <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '15px', fontWeight: 700 }}>
              <span>Total ({groups.length} order{groups.length === 1 ? '' : 's'})</span>
              <span>{grandTotal.toLocaleString()} RWF</span>
            </div>
            <input
              type="text" value={address} onChange={(e) => setAddress(e.target.value)} placeholder="Delivery address" required
              style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            {needsDeviceVerification ? (
              <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
            ) : (
              <>
                {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
                <button type="submit" className="toss-btn toss-btn-primary" disabled={placing || !address.trim()}>
                  {placing ? 'Placing orders…' : `Place ${groups.length} order${groups.length === 1 ? '' : 's'}`}
                </button>
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
    <div className="toss-card" style={{ padding: '28px' }}>
      <div style={{ textAlign: 'center', marginBottom: '20px' }}>
        <ShieldCheck size={36} color="var(--toss-green)" style={{ marginBottom: '10px' }} />
        <h3 style={{ fontSize: '17px', fontWeight: 700 }}>
          {successCount} of {results.length} order{results.length === 1 ? '' : 's'} placed
        </h3>
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
        {results.map((r) => (
          <div key={r.merchantId} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', fontSize: '13px' }}>
            <span style={{ fontWeight: 600 }}>{r.businessName}</span>
            {r.success ? (
              <span style={{ color: 'var(--toss-green)' }}>{r.order!.totalAmount.toLocaleString()} RWF — placed</span>
            ) : (
              <span style={{ color: '#E53935' }}>{r.error}</span>
            )}
          </div>
        ))}
      </div>
      <button className="toss-btn toss-btn-secondary" style={{ width: '100%' }} onClick={onDone}>
        {results.some((r) => !r.success) ? 'Back to cart' : 'Done'}
      </button>
    </div>
  );
}

function MyCommerceOrdersView() {
  const [orders, setOrders] = useState<CommerceOrder[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [cancellingId, setCancellingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyOrders().then(setOrders).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your orders.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not cancel this order.');
    } finally {
      setCancellingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return <EmptyState message="No orders yet." />;

  return (
    <div>
      <MyReturnRequestsView />
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {orders.map((o) => (
        <CommerceOrderCard
          key={o.id}
          order={o}
          action={
            o.status === 'PLACED' ? (
              <button className="toss-btn toss-btn-danger" disabled={cancellingId === o.id} onClick={() => handleCancel(o.id)}>
                {cancellingId === o.id ? 'Cancelling…' : 'Cancel order'}
              </button>
            ) : o.status === 'DELIVERED' ? (
              <div>
                <OrderItemReviews order={o} />
                <ReturnExchangeAction orderId={o.id} />
              </div>
            ) : undefined
          }
        />
      ))}
      </div>
    </div>
  );
}

function MerchantOrdersView() {
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
          setError(err instanceof ApiError ? err.message : 'Could not load your store orders.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update this order.');
    } finally {
      setBusyOrderId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (orders === null) return <div className="toss-card skeleton" style={{ height: '180px' }} />;
  if (orders.length === 0) return null;

  return (
    <div style={{ marginBottom: '20px' }}>
      <h4 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px', padding: '0 4px' }}>Orders for your store</h4>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
        {orders.map((o) => {
          const next = nextInChain(COMMERCE_STATUS_CHAIN, o.status);
          return (
            <CommerceOrderCard
              key={o.id}
              order={o}
              action={next && (
                <button className="toss-btn toss-btn-primary" disabled={busyOrderId === o.id} onClick={() => handleAdvance(o)}>
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
  const [favorites, setFavorites] = useState<FavoriteProduct[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [removingId, setRemovingId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyFavoriteProducts().then(setFavorites).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your wishlist.'));
  };
  useEffect(load, []);

  const handleRemove = async (productId: string) => {
    setRemovingId(productId);
    try {
      await removeProductFavorite(productId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this item.');
    } finally {
      setRemovingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (favorites === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;
  if (favorites.length === 0) return <EmptyState message="No saved items yet -- tap ♡ on any product to save it here." />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      {favorites.map((f) => (
        <div key={f.productId} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '12px' }}>
          <button
            onClick={() => onOpenMerchant({ merchantId: f.merchantId, businessName: f.businessName, category: null, cashbackRate: '' })}
            style={{ textAlign: 'left', flex: 1, display: 'flex', alignItems: 'center', gap: '12px' }}
          >
            <ProductImageThumb imageUrl={f.imageUrl} size={44} />
            <div>
              <p style={{ fontSize: '15px', fontWeight: 700 }}>{f.name}</p>
              <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{f.businessName}</p>
              <ProductPriceBlock price={f.price} originalPrice={f.originalPrice} discountPercent={f.discountPercent} />
              {f.priceDropped && (
                <p style={{ fontSize: '12px', fontWeight: 700, color: 'var(--toss-red, #E53935)', marginTop: '2px' }}>
                  🔻 Price dropped
                </p>
              )}
            </div>
          </button>
          <button
            className="toss-btn toss-btn-secondary"
            disabled={removingId === f.productId}
            onClick={() => handleRemove(f.productId)}
            style={{ padding: '8px 12px', fontSize: '12px' }}
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

function ShopView() {
  const [view, setView] = useState<'BROWSE' | 'ORDERS' | 'WISHLIST'>('BROWSE');
  const [merchants, setMerchants] = useState<ShoppingMerchant[] | null>(null);
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

  // Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
  // recommendation #8. Every entry is a real merchant-set discount, never a
  // fabricated promo -- see backend MerchantProductRepository.findDeals's own doc
  // comment.
  const [deals, setDeals] = useState<ProductSearchResult[] | null>(null);
  useEffect(() => {
    fetchShopDeals().then(setDeals).catch(() => {});
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load merchants.'));
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
    // (e.g. after fixing the delivery address or topping up their wallet).
    setCart((prev) => {
      const next = { ...prev };
      checkoutResults.filter((r) => r.success).forEach((r) => delete next[r.merchantId]);
      return next;
    });
    setResults(checkoutResults);
    setShowCart(false);
  };

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
      <ProductDetailView
        merchant={selected}
        product={selectedProduct}
        cart={cart}
        onSetQty={setQtyByMerchant}
        onBack={() => setSelectedProduct(null)}
        onViewCart={() => { setSelectedProduct(null); setShowCart(true); }}
      />
    );
  }

  if (selected) {
    return (
      <ProductCatalogView
        merchant={selected}
        cart={cart}
        onSetQty={setQtyByMerchant}
        onBack={() => setSelected(null)}
        onViewCart={() => setShowCart(true)}
        onOpenProduct={setSelectedProduct}
      />
    );
  }

  const totalItems = cartTotalItems(cart);

  return (
    <div>
      <MerchantOrdersView />
      <MerchantReturnQueueView />

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {(['BROWSE', 'ORDERS', 'WISHLIST'] as const).map((v) => (
          <button
            key={v}
            onClick={() => setView(v)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: view === v ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: view === v ? 'var(--toss-blue)' : 'transparent',
            }}
          >
            {v === 'BROWSE' ? 'Merchants' : v === 'ORDERS' ? 'My orders' : '♡ Wishlist'}
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
            style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={searching || !searchQuery.trim()}>
            {searching ? '…' : 'Search'}
          </button>
          {searchResults !== null && (
            <button type="button" className="toss-btn toss-btn-secondary" onClick={() => { setSearchResults(null); setSearchQuery(''); }}>
              Clear
            </button>
          )}
        </form>
      )}

      {/* Real Karrot 반경 타기팅-style nearby ads rail (item 148) -- see lib/shopping.ts's
          own doc comment. Tapping one opens that merchant's real catalog, same
          minimal-ShoppingMerchant shortcut openSearchResult already uses just above. */}
      {view === 'BROWSE' && searchResults === null && nearbyAds.length > 0 && (
        <div style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)', marginBottom: '8px' }}>📍 Near you</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {nearbyAds.map((a) => (
              <button
                key={a.ad.id}
                onClick={() => setSelected({ merchantId: a.ad.merchantId, businessName: a.businessName, category: null, cashbackRate: '1%' })}
                className="toss-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '160px', flexShrink: 0, gap: '4px' }}
              >
                <p style={{ fontSize: '13px', fontWeight: 700 }}>{a.ad.title}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{a.businessName}</p>
                {a.ad.description && <p style={{ fontSize: '11px', color: 'var(--toss-grey-700)' }}>{a.ad.description}</p>}
                <p style={{ fontSize: '11px', color: 'var(--toss-blue)', fontWeight: 600 }}>{a.distanceKm.toFixed(1)} km away</p>
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
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)', marginBottom: '8px' }}>🔥 Deals</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {deals.map((d) => (
              <button
                key={d.id}
                onClick={() => openSearchResult(d)}
                className="toss-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={d.imageUrl} size={96} />
                <p style={{ fontSize: '12px', fontWeight: 700 }}>{d.name}</p>
                <ProductPriceBlock price={d.price} originalPrice={d.originalPrice} discountPercent={d.discountPercent} />
                <p style={{ fontSize: '11px', color: d.stockQuantity === 0 ? '#E53935' : 'var(--toss-grey-500)' }}>
                  {d.stockQuantity === null || d.stockQuantity === undefined ? 'Available' : d.stockQuantity === 0 ? 'Out of stock' : `${d.stockQuantity} available`}
                </p>
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
          <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)', marginBottom: '8px' }}>⏰ Time Deals</p>
          <div style={{ display: 'flex', gap: '10px', overflowX: 'auto' }}>
            {timeDeals.map((v) => (
              <button
                key={v.deal.id}
                onClick={() => setSelected({ merchantId: v.deal.merchantId, businessName: v.businessName, category: null, cashbackRate: '1%' })}
                className="toss-card"
                style={{ display: 'flex', flexDirection: 'column', alignItems: 'flex-start', textAlign: 'left', width: '120px', flexShrink: 0, gap: '4px' }}
              >
                <ProductImageThumb imageUrl={v.productImageUrl} size={96} />
                <p style={{ fontSize: '12px', fontWeight: 700 }}>{v.productName}</p>
                <ProductPriceBlock
                  price={v.deal.dealPrice} originalPrice={v.deal.originalPrice}
                  discountPercent={Math.round((1 - v.deal.dealPrice / v.deal.originalPrice) * 100)}
                />
                {/* Real Coupang badge system (2026-08-05) -- see Badge.tsx's own doc
                    comment. Matches Android ShopScreen.kt's own identical StatusBadge
                    treatment (this was plain <p> text on bank-mfe until now). */}
                <Badge text={formatDealCountdown(v.deal.endsAt)} filled={false} tint="var(--toss-blue)" />
                <Badge text={`${v.deal.remainingQuantity} left`} tint="var(--toss-red)" />
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
        <MyCommerceOrdersView />
      ) : view === 'WISHLIST' ? (
        <WishlistView onOpenMerchant={setSelected} />
      ) : view === 'BROWSE' && searchResults !== null ? (
        searchResults.length === 0 ? (
          <div className="toss-card"><p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>No products matched "{searchQuery}".</p></div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {searchResults.map((r) => (
              <button
                key={r.id}
                onClick={() => openSearchResult(r)}
                className="toss-card"
                style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', textAlign: 'left', width: '100%', gap: '12px' }}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
                  <ProductImageThumb imageUrl={r.imageUrl} size={44} />
                  <div>
                    <p style={{ fontSize: '14px', fontWeight: 700 }}>{r.name}</p>
                    <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Sold by {r.merchantName}</p>
                    <p style={{ fontSize: '11px', color: r.stockQuantity === 0 ? '#E53935' : 'var(--toss-grey-500)' }}>
                      {r.stockQuantity === null || r.stockQuantity === undefined ? 'Available' : r.stockQuantity === 0 ? 'Out of stock' : `${r.stockQuantity} available`}
                    </p>
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
        <div className="toss-card skeleton" style={{ height: '220px' }} />
      ) : merchants.length === 0 ? (
        <div className="toss-card">
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            {selectedCategory || debouncedMerchantSearch ? 'No merchants match your search.' : 'No merchants registered yet.'}
          </p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: totalItems > 0 ? '80px' : 0 }}>
          {merchants.map((m) => (
            <button
              key={m.merchantId}
              onClick={() => setSelected(m)}
              className="toss-card"
              style={{ display: 'flex', alignItems: 'center', gap: '16px', padding: '18px 20px', textAlign: 'left', width: '100%' }}
            >
              <div style={{ width: '44px', height: '44px', borderRadius: '22px', backgroundColor: 'var(--toss-blue-light)', display: 'flex', alignItems: 'center', justifyContent: 'center', flexShrink: 0 }}>
                <ShoppingBag size={20} color="var(--toss-blue)" />
              </div>
              <div style={{ flex: 1 }}>
                <p style={{ fontSize: '15px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>{m.businessName}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Real cart checkout, real delivery tracking</p>
              </div>
            </button>
          ))}
        </div>
      )}
      {view === 'BROWSE' && totalItems > 0 && (
        <button
          className="toss-btn toss-btn-primary"
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
  const [devices, setDevices] = useState<TrustedDevice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [revokingId, setRevokingId] = useState<string | null>(null);
  const myDeviceId = getOrCreateDeviceId();

  const load = () => {
    setError(null);
    fetchMyDevices().then(setDevices).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your devices.'));
  };
  useEffect(load, []);

  const handleRevoke = async (deviceId: string) => {
    setRevokingId(deviceId);
    setError(null);
    try {
      await revokeDevice(deviceId);
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not remove this device.');
    } finally {
      setRevokingId(null);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (devices === null) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', padding: '0 4px' }}>
        Devices that have signed in to your account. A device must be verified before it can send money.
      </p>
      {devices.length === 0 ? (
        <EmptyState message="No devices recorded yet." />
      ) : (
        devices.map((d) => (
          <div key={d.id} className="toss-card" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <div>
              <p style={{ fontSize: '14px', fontWeight: 700, color: 'var(--toss-grey-900)' }}>
                {d.deviceName ?? 'Unknown device'} {d.deviceId === myDeviceId && <span style={{ color: 'var(--toss-blue)' }}>(this device)</span>}
              </p>
              <p style={{ fontSize: '12px', color: d.trusted ? 'var(--toss-green)' : '#E53935' }}>
                {d.trusted ? '✓ Verified — can send money' : '⚠ Not verified — sign-in only'}
              </p>
              <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>Last seen {new Date(d.lastSeenAt).toLocaleString()}</p>
            </div>
            <button
              className="toss-btn toss-btn-danger"
              disabled={revokingId === d.deviceId}
              onClick={() => handleRevoke(d.deviceId)}
              style={{ padding: '8px 12px', fontSize: '12px' }}
            >
              {revokingId === d.deviceId ? 'Removing…' : 'Remove'}
            </button>
          </div>
        ))
      )}
    </div>
  );
}

// Real Toss Bank 체크카드 (check/debit card) -- see the backend's DebitCard.kt doc
// comment for the full sourced account (item 207) and the honest boundary around this
// not riding a real Visa/Mastercard rail. "Pay with card" below is itunda's own real,
// ledger-backed simulation of a card-present purchase (real money moves, real limits
// are enforced), the same honest "demo the part that can be real" convention
// DemoCardAuthorizationService already established for the merchant-side equivalent.
function CardView() {
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
        setError(err instanceof ApiError ? err.message : 'Could not load your card.');
      });
    fetchCardTransactions().then((r) => setTransactions(r.transactions)).catch(() => {});
  };
  useEffect(load, []);

  const handleIssue = async () => {
    setBusy(true);
    setError(null);
    try {
      await issueCard();
      load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not issue a card.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update your card.');
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
      setError(err instanceof ApiError ? err.message : 'Could not update your limits.');
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
      setChargeError(err instanceof ApiError ? err.message : 'Could not complete this purchase.');
    } finally {
      setBusy(false);
    }
  };

  if (error) {
    return (
      <ErrorCard message={error} onRetry={load} />
    );
  }
  if (card === undefined) return <div className="toss-card skeleton" style={{ height: '160px' }} />;

  if (card === null) {
    return (
      <div className="toss-card" style={{ textAlign: 'center' }}>
        <p style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>You don't have an itunda debit card yet</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '16px' }}>
          App-controlled spend limits and one-tap freeze — no branch visit, no waiting.
        </p>
        <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handleIssue}>
          {busy ? 'Issuing…' : 'Get your itunda card'}
        </button>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <div
        className="toss-card"
        style={{
          background: card.frozen ? 'var(--toss-grey-500)' : 'linear-gradient(135deg, var(--toss-blue), #1B64DA)',
          color: 'white', padding: '20px',
        }}
      >
        <p style={{ fontSize: '13px', opacity: 0.85 }}>itunda card</p>
        <p style={{ fontSize: '20px', fontWeight: 700, letterSpacing: '2px', margin: '10px 0' }}>•••• •••• •••• {card.last4}</p>
        <p style={{ fontSize: '12px', opacity: 0.85 }}>{card.frozen ? '🔒 Frozen — no purchases can be made' : '✓ Active'}</p>
      </div>

      <button className={`toss-btn ${card.frozen ? 'toss-btn-primary' : 'toss-btn-danger'}`} disabled={busy} onClick={handleToggleFreeze}>
        {card.frozen ? 'Unfreeze card' : 'Freeze card'}
      </button>

      <div className="toss-card">
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '10px' }}>Spend limits</p>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '6px' }}>
          <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Today</span>
          <span style={{ fontSize: '12px' }}>{card.spentToday.toLocaleString()} / {card.dailyLimit.toLocaleString()} RWF</span>
        </div>
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '12px' }}>
          <span style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>This month</span>
          <span style={{ fontSize: '12px' }}>{card.spentThisMonth.toLocaleString()} / {card.monthlyLimit.toLocaleString()} RWF</span>
        </div>
        <div style={{ display: 'flex', gap: '8px', marginBottom: '8px' }}>
          <input
            type="number" placeholder="Daily limit" value={dailyLimitInput} onChange={(e) => setDailyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)' }}
          />
          <input
            type="number" placeholder="Monthly limit" value={monthlyLimitInput} onChange={(e) => setMonthlyLimitInput(e.target.value)}
            style={{ flex: 1, padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)' }}
          />
        </div>
        <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleSaveLimits} style={{ width: '100%' }}>
          Save limits
        </button>
      </div>

      <div className="toss-card">
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '4px' }}>Pay with your card</p>
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginBottom: '10px' }}>
          itunda has no real card-network partnership yet, so this simulates a real card-present purchase — real money moves, real limits apply.
        </p>
        <form onSubmit={handleCharge} style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          {chargeError && <p style={{ fontSize: '12px', color: '#E53935' }} role="alert">{chargeError}</p>}
          {chargeSuccess && <p style={{ fontSize: '12px', color: 'var(--toss-green)' }}>{chargeSuccess}</p>}
          <input
            placeholder="Merchant name" value={merchantName} onChange={(e) => setMerchantName(e.target.value)} required
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)' }}
          />
          <input
            type="number" placeholder="Amount (RWF)" value={chargeAmount} onChange={(e) => setChargeAmount(e.target.value)} required min="1"
            style={{ padding: '10px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)' }}
          />
          <button type="submit" className="toss-btn toss-btn-primary" disabled={busy || card.frozen}>
            {card.frozen ? 'Card is frozen' : busy ? 'Paying…' : 'Pay'}
          </button>
        </form>
      </div>

      <div className="toss-card">
        <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '10px' }}>Recent card activity</p>
        {transactions.length === 0 ? (
          <EmptyState message="No purchases yet." />
        ) : (
          transactions.map((t) => (
            <div key={t.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0' }}>
              <div>
                <p style={{ fontSize: '13px' }}>{t.merchantName}</p>
                <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)' }}>{new Date(t.createdAt).toLocaleString()}</p>
              </div>
              <span style={{ fontSize: '13px', fontWeight: 700 }}>{t.amount.toLocaleString()} RWF</span>
            </div>
          ))
        )}
      </div>
    </div>
  );
}

// Real Kakao Bank SafeBox (세이프박스) equivalent -- claim-anytime interest that grows
// for real off the actual SAVINGS wallet balance (InterestAccrualScheduler, 2026-07-20).
// Real Kakao Pay 머니굴리기 ("rolling money") round-up auto-saving -- see
// lib/savings.ts's own doc comment. First client UI for this feature anywhere
// (item 112, found via a content-grep sweep: Android has a real client, bank-mfe
// and iOS never did). Every real P2P transfer rounds up to the chosen increment and
// deposits the spare change into the chosen goal.
function RoundUpCard({ goals }: { goals: SavingsGoal[] }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not update round-up settings.');
    } finally {
      setBusy(false);
    }
  };

  if (settings === undefined) return null;

  return (
    <div className="toss-card" style={{ marginBottom: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '8px' }}>
        <h3 style={{ fontSize: '15px', fontWeight: 700 }}>Round-up savings</h3>
        {settings?.enabled && (
          <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={() => handleToggle(false)} style={{ fontSize: '12px', padding: '6px 10px' }}>
            {busy ? '…' : 'Turn off'}
          </button>
        )}
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '8px' }} role="alert">{error}</p>}
      {settings?.enabled ? (
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
          Every transfer rounds up to the nearest {settings.roundToNearest.toLocaleString()} RWF, saved into your goal.
        </p>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
          <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
            Round up every transfer to a real RWF increment and auto-save the spare change.
          </p>
          <div style={{ display: 'flex', gap: '6px' }}>
            {ROUND_UP_INCREMENTS.map((v) => (
              <button
                key={v} type="button" onClick={() => setIncrement(v)}
                className={increment === v ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
                style={{ flex: 1, fontSize: '12px', padding: '8px' }}
              >
                {v} RWF
              </button>
            ))}
          </div>
          <select
            value={goalId} onChange={(e) => setGoalId(e.target.value)}
            style={{ padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
          >
            <option value="">Choose a savings goal</option>
            {goals.map((g) => <option key={g.id} value={g.id}>{g.name}</option>)}
          </select>
          <button className="toss-btn toss-btn-primary" disabled={busy || !goalId} onClick={() => handleToggle(true)}>
            {busy ? 'Turning on…' : 'Turn on round-up'}
          </button>
        </div>
      )}
    </div>
  );
}

function InterestJarCard() {
  const [jar, setJar] = useState<InterestJar | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);
  const [claimMsg, setClaimMsg] = useState<string | null>(null);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const load = () => {
    setError(null);
    fetchInterestJar().then(setJar).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your Safe Box.'));
  };
  useEffect(load, []);

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
        setError(err instanceof ApiError ? err.message : 'Could not claim interest.');
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
  if (jar === null) return <div className="toss-card skeleton" style={{ height: '140px', marginBottom: '16px' }} />;

  const canClaim = jar.earnedThisMonth > 0;

  return (
    <div className="toss-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--toss-blue) 0%, #4A90E2 100%)', color: '#fff' }}>
      <p style={{ fontSize: '13px', opacity: 0.85 }}>Safe Box · {jar.rate}% real daily interest</p>
      <p style={{ fontSize: '28px', fontWeight: 800, margin: '6px 0' }}>{jar.balance.toLocaleString()} RWF</p>
      <div style={{ display: 'flex', justifyContent: 'space-between', marginTop: '10px' }}>
        <div>
          <p style={{ fontSize: '11px', opacity: 0.8 }}>Earned, unclaimed</p>
          <p style={{ fontSize: '16px', fontWeight: 700 }}>{jar.earnedThisMonth.toLocaleString()} RWF</p>
        </div>
        <div style={{ textAlign: 'right' }}>
          <p style={{ fontSize: '11px', opacity: 0.8 }}>Earned all-time</p>
          <p style={{ fontSize: '16px', fontWeight: 700 }}>{jar.earnedTotal.toLocaleString()} RWF</p>
        </div>
      </div>
      {needsDeviceVerification ? (
        <div style={{ marginTop: '14px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        <button
          className="toss-btn"
          onClick={handleClaim}
          disabled={!canClaim || claiming}
          style={{ marginTop: '14px', width: '100%', backgroundColor: '#fff', color: 'var(--toss-blue)', fontWeight: 700, opacity: canClaim ? 1 : 0.6 }}
        >
          {claiming ? 'Claiming…' : canClaim ? `Claim ${jar.earnedThisMonth.toLocaleString()} RWF` : 'Nothing to claim yet'}
        </button>
      )}
      {claimMsg && <p style={{ fontSize: '12px', marginTop: '8px' }}>{claimMsg}</p>}
    </div>
  );
}

function GoalCard({ goal, onChanged }: { goal: SavingsGoal; onChanged: () => void }) {
  const [depositing, setDepositing] = useState(false);
  const [amount, setAmount] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
  const pct = Math.min(100, Math.round((goal.currentAmount / goal.targetAmount) * 100));

  const handleDeposit = async (e: React.FormEvent) => {
    e.preventDefault();
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
        setError(err instanceof ApiError ? err.message : 'Could not deposit.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <p style={{ fontSize: '14px', fontWeight: 700 }}>{goal.name}</p>
          <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
            {goal.currentAmount.toLocaleString()} / {goal.targetAmount.toLocaleString()} RWF
            {goal.status === 'completed' && ' · Completed 🎉'}
          </p>
        </div>
        {goal.status === 'active' && (
          <button className="toss-btn toss-btn-secondary" style={{ padding: '6px 12px', fontSize: '12px' }} onClick={() => setDepositing((d) => !d)}>
            Deposit
          </button>
        )}
      </div>
      <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'var(--toss-grey-100)', marginTop: '10px', overflow: 'hidden' }}>
        <div style={{ height: '100%', width: `${pct}%`, backgroundColor: 'var(--toss-blue)' }} />
      </div>
      {goal.monthlyContribution > 0 && (
        <p style={{ fontSize: '11px', color: 'var(--toss-grey-500)', marginTop: '6px' }}>
          Auto-saves {goal.monthlyContribution.toLocaleString()} RWF/month
        </p>
      )}
      {depositing && (
        needsDeviceVerification ? (
          <div style={{ marginTop: '10px' }}>
            <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setDepositing(false)} />
          </div>
        ) : (
          <form onSubmit={handleDeposit} style={{ display: 'flex', gap: '8px', marginTop: '10px' }}>
            <input
              type="number" min="1" required value={amount} onChange={(e) => setAmount(e.target.value)}
              placeholder="Amount (RWF)"
              style={{ flex: 1, padding: '10px 12px', borderRadius: '8px', border: '1px solid var(--toss-grey-200)', fontSize: '13px' }}
            />
            <button type="submit" className="toss-btn toss-btn-primary" disabled={busy} style={{ padding: '8px 14px', fontSize: '13px' }}>
              {busy ? '…' : 'Add'}
            </button>
          </form>
        )
      )}
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '6px' }} role="alert">{error}</p>}
    </div>
  );
}

function CreateGoalForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [targetAmount, setTargetAmount] = useState('');
  const [monthlyContribution, setMonthlyContribution] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="toss-btn toss-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <Plus size={16} /> New savings goal
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createGoal(name, Number(targetAmount), monthlyContribution ? Number(monthlyContribution) : undefined);
      setName('');
      setTargetAmount('');
      setMonthlyContribution('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create goal.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <input
        type="text" required placeholder="Goal name (e.g. Emergency Fund)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" min="1" required placeholder="Target amount (RWF)" value={targetAmount} onChange={(e) => setTargetAmount(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" min="0" placeholder="Monthly auto-save (optional)" value={monthlyContribution} onChange={(e) => setMonthlyContribution(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

// Real Kakao Bank 모임통장 (group/shared account) -- see lib/groupAccounts.ts's doc
// comment. Backend enforces real owner-only withdrawal/invite authority; this view's
// job is just to reflect that honestly (buttons the caller can't actually use are
// hidden, not disabled-with-no-explanation).
function GroupAccountDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const [detail, setDetail] = useState<GroupAccountDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [amount, setAmount] = useState('');
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);
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
    fetchGroupAccount(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this group account.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not set the dues amount.');
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
      setError(err instanceof ApiError ? err.message : 'Could not clear the dues amount.');
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
      setError(err instanceof ApiError ? err.message : 'Could not send reminders.');
    } finally {
      setDuesBusy(false);
    }
  };

  const isOwner = detail?.groupAccount.ownerId === myUserId;

  const handleDeposit = async () => {
    setBusy(true);
    setError(null);
    setNeedsDeviceVerification(false);
    try {
      await depositToGroupAccount(id, Number(amount));
      setAmount('');
      load();
    } catch (err) {
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') setNeedsDeviceVerification(true);
      else setError(err instanceof ApiError ? err.message : 'Could not deposit.');
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
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') setNeedsDeviceVerification(true);
      else setError(err instanceof ApiError ? err.message : 'Could not withdraw.');
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
      setError(err instanceof ApiError ? err.message : 'Could not invite this member.');
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return <div className="toss-card skeleton" style={{ height: '260px' }} />;

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to group accounts</button>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{detail.groupAccount.name}</p>
        <p style={{ fontSize: '28px', fontWeight: 800, margin: '4px 0' }}>{detail.balance.toLocaleString()} RWF</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{detail.members.length} member{detail.members.length === 1 ? '' : 's'}</p>
      </div>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Members</h3>
        {detail.members.map((m) => (
          <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: '13px' }}>
            <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
            {m.isOwner && <span style={{ color: 'var(--toss-blue)', fontWeight: 700 }}>Organizer</span>}
          </div>
        ))}
      </div>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Monthly dues</h3>
        {dues === null ? (
          <div className="skeleton" style={{ height: '40px', borderRadius: '8px' }} />
        ) : dues.duesAmount === null ? (
          isOwner ? (
            <form onSubmit={handleSetDues} style={{ display: 'flex', gap: '8px' }}>
              <input
                type="number" min="1" required value={duesAmountInput} onChange={(e) => setDuesAmountInput(e.target.value)}
                placeholder="Monthly dues (RWF)"
                style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
              />
              <button type="submit" className="toss-btn toss-btn-primary" disabled={duesBusy}>{duesBusy ? '…' : 'Set'}</button>
            </form>
          ) : (
            <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>The organizer hasn't set a monthly dues amount.</p>
          )
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
            <p style={{ fontSize: '13px' }}>{dues.duesAmount.toLocaleString()} RWF / month · {dues.cycleMonth}</p>
            {dues.members.map((m) => {
              const duesAmount = dues.duesAmount as number;
              return (
                <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px' }}>
                  <span>{m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}</span>
                  <span style={{ color: m.paid ? '#1E8E4F' : 'var(--toss-grey-500)', fontWeight: m.paid ? 700 : 400 }}>
                    {m.paid ? '✓ Paid' : `${m.contributedAmount.toLocaleString()} / ${duesAmount.toLocaleString()}`}
                  </span>
                </div>
              );
            })}
            {isOwner && (
              <div style={{ display: 'flex', gap: '8px', marginTop: '4px' }}>
                <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={duesBusy} onClick={handleRemindUnpaid}>
                  {duesBusy ? '…' : 'Remind unpaid members'}
                </button>
                <button className="toss-btn toss-btn-secondary" disabled={duesBusy} onClick={handleClearDues}>Clear</button>
              </div>
            )}
            {remindedCount !== null && (
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
                {remindedCount === 0 ? 'Everyone has already paid or been reminded this month.' : `Reminded ${remindedCount} member${remindedCount === 1 ? '' : 's'}.`}
              </p>
            )}
          </div>
        )}
      </div>

      {needsDeviceVerification ? (
        <div style={{ marginBottom: '16px' }}>
          <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => setNeedsDeviceVerification(false)} />
        </div>
      ) : (
        <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>{isOwner ? 'Deposit or withdraw' : 'Deposit'}</h3>
          <input
            type="number" min="1" required value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <div style={{ display: 'flex', gap: '8px' }}>
            <button type="button" onClick={handleDeposit} className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy || !amount}>
              {busy ? '…' : 'Deposit'}
            </button>
            {isOwner && (
              // Real Kakao Bank behavior: only the organizer can withdraw/settle --
              // this button is only rendered for the owner, not just disabled.
              <button type="button" onClick={handleWithdraw} className="toss-btn toss-btn-secondary" style={{ flex: 1 }} disabled={busy || !amount}>
                {busy ? '…' : 'Withdraw'}
              </button>
            )}
          </div>
        </div>
      )}

      {isOwner && (
        <form onSubmit={handleInvite} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Invite a member</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Phone number"
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
          </div>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

function CreateGroupAccountForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="toss-btn toss-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <Plus size={16} /> New group account
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
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this group account.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <input
        type="text" required placeholder="Group name (e.g. Roommates)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function GroupAccountsSection() {
  const [accounts, setAccounts] = useState<GroupAccount[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyGroupAccounts().then(setAccounts).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your group accounts.'));
  };
  useEffect(load, []);

  if (openId) {
    return <GroupAccountDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 4px 10px' }}>Group accounts</h3>
      <CreateGroupAccountForm onCreated={load} />
      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {accounts === null ? (
        <div className="toss-card skeleton" style={{ height: '64px' }} />
      ) : accounts.length === 0 ? (
        <EmptyState message="No group accounts yet -- start one to save or split expenses with others." />
      ) : (
        accounts.map((a) => (
          <button
            key={a.id}
            onClick={() => setOpenId(a.id)}
            className="toss-card"
            style={{ display: 'block', width: '100%', textAlign: 'left', marginBottom: '10px', border: 'none' }}
          >
            <p style={{ fontSize: '14px', fontWeight: 700 }}>{a.name}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Tap to view balance and members</p>
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
  const [ikiminas, setIkiminas] = useState<Ikimina[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyIkiminas().then(setIkiminas).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your ikimina groups.'));
  };
  useEffect(load, []);

  if (openId) {
    return <IkiminaDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 4px 10px' }}>Ikimina (rotating savings)</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', margin: '0 4px 10px' }}>
        Everyone contributes the same amount each round; one member takes home the full pot, in turn.
      </p>
      <CreateIkiminaForm onCreated={load} />
      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {ikiminas === null ? (
        <div className="toss-card skeleton" style={{ height: '64px' }} />
      ) : ikiminas.length === 0 ? (
        <EmptyState message="No ikimina groups yet -- start one with people you trust." />
      ) : (
        ikiminas.map((k) => (
          <button
            key={k.id}
            onClick={() => setOpenId(k.id)}
            className="toss-card"
            style={{ display: 'block', width: '100%', textAlign: 'left', marginBottom: '10px', border: 'none' }}
          >
            <p style={{ fontSize: '14px', fontWeight: 700 }}>{k.name}</p>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
              {k.status === 'FORMING' ? 'Forming — invite members before starting' : k.status === 'ACTIVE' ? `Round ${k.currentRound}` : 'Completed'}
            </p>
          </button>
        ))
      )}
    </div>
  );
}

function CreateIkiminaForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [contributionAmount, setContributionAmount] = useState('');
  const [cycleFrequencyDays, setCycleFrequencyDays] = useState('30');
  const [memberCap, setMemberCap] = useState('10');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="toss-btn toss-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <Plus size={16} /> New ikimina
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createIkimina(name, Number(contributionAmount), Number(cycleFrequencyDays), Number(memberCap));
      setName(''); setContributionAmount('');
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this ikimina.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <input
        type="text" required placeholder="Group name (e.g. Umuryango)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" min="1" required placeholder="Contribution per round (RWF)" value={contributionAmount} onChange={(e) => setContributionAmount(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div style={{ display: 'flex', gap: '8px' }}>
        <select
          value={cycleFrequencyDays} onChange={(e) => setCycleFrequencyDays(e.target.value)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        >
          <option value="7">Weekly</option>
          <option value="30">Monthly</option>
        </select>
        <input
          type="number" min="2" max="15" required placeholder="Max members" value={memberCap} onChange={(e) => setMemberCap(e.target.value)}
          style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
        />
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function IkiminaDetailView({ id, onBack }: { id: string; onBack: () => void }) {
  const [detail, setDetail] = useState<IkiminaDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [phoneNumber, setPhoneNumber] = useState('');
  const [busy, setBusy] = useState(false);
  const [payoutMessage, setPayoutMessage] = useState<string | null>(null);
  const myUserId = getStoredUser()?.id;

  const load = () => {
    setError(null);
    fetchIkimina(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this ikimina.'));
  };
  useEffect(load, []);

  if (error && !detail) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return <div className="toss-card skeleton" style={{ height: '260px' }} />;

  const { ikimina, balance, members, currentRoundContributions } = detail;
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
      setError(err instanceof ApiError ? err.message : 'Could not invite this member.');
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
      setError(err instanceof ApiError ? err.message : 'Could not start this cycle.');
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
      setError(err instanceof ApiError ? err.message : 'Could not contribute.');
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
      setError(err instanceof ApiError ? err.message : 'Could not trigger the payout yet.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to ikimina</button>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{ikimina.name}</p>
        <p style={{ fontSize: '28px', fontWeight: 800, margin: '4px 0' }}>{balance.toLocaleString()} RWF</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
          {ikimina.status === 'FORMING'
            ? `Forming — ${members.length} of up to ${ikimina.memberCap} members`
            : ikimina.status === 'ACTIVE'
              ? `Round ${ikimina.currentRound} of ${members.length} · ${ikimina.contributionAmount.toLocaleString()} RWF each · pot ${pot.toLocaleString()} RWF`
              : 'Every member has been paid — this ikimina is complete'}
        </p>
      </div>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '10px' }}>Rotation order</h3>
        {members.map((m) => {
          const contributed = currentRoundContributions.find((c) => c.userId === m.userId)?.contributed ?? false;
          return (
            <div key={m.userId} style={{ display: 'flex', justifyContent: 'space-between', padding: '6px 0', fontSize: '13px' }}>
              <span>
                #{m.payoutOrder} {m.firstName} {m.lastName}{m.userId === myUserId ? ' (you)' : ''}{m.isOrganizer ? ' · Organizer' : ''}
              </span>
              <span style={{ color: m.hasReceivedPayout ? '#1E8E4F' : ikimina.status === 'ACTIVE' && contributed ? '#1E8E4F' : 'var(--toss-grey-500)', fontWeight: 700 }}>
                {m.hasReceivedPayout ? '✓ Paid' : ikimina.status === 'ACTIVE' ? (contributed ? '✓ Contributed' : 'Pending') : ''}
              </span>
            </div>
          );
        })}
      </div>

      {ikimina.status === 'FORMING' && isOrganizer && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} disabled={busy || members.length < 2} onClick={handleStart}>
            {busy ? '…' : members.length < 2 ? 'Invite at least 1 more member to start' : 'Start the cycle'}
          </button>
        </div>
      )}

      {ikimina.status === 'ACTIVE' && myMember && (
        <div className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Round {ikimina.currentRound}</h3>
          <button className="toss-btn toss-btn-primary" disabled={busy || iContributed} onClick={handleContribute}>
            {busy ? '…' : iContributed ? '✓ You contributed this round' : `Contribute ${ikimina.contributionAmount.toLocaleString()} RWF`}
          </button>
          <button className="toss-btn toss-btn-secondary" disabled={busy || !allContributed} onClick={handlePayout}>
            {busy ? '…' : allContributed ? 'Release this round\'s payout' : 'Waiting for everyone to contribute'}
          </button>
          {payoutMessage && <p style={{ fontSize: '12px', color: '#1E8E4F' }}>{payoutMessage}</p>}
        </div>
      )}

      {ikimina.status === 'FORMING' && isOrganizer && (
        <form onSubmit={handleInvite} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700 }}>Invite a member</h3>
          <div style={{ display: 'flex', gap: '8px' }}>
            <input
              type="tel" required value={phoneNumber} onChange={(e) => setPhoneNumber(e.target.value)} placeholder="Phone number"
              style={{ flex: 1, padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
            />
            <button type="submit" className="toss-btn toss-btn-primary" disabled={busy}>{busy ? '…' : 'Invite'}</button>
          </div>
        </form>
      )}

      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </div>
  );
}

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model. See lib/sacco.ts's own doc comment for the full sourced
// account. Sibling to IkiminaSection above (both are Rwanda-specific, not sourced
// from Toss/Kakao/Naver/Coupang) but a genuinely distinct mechanic: real shares +
// periodic real dividends, not a rotating pot.
function SaccoSection() {
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
      .catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your SACCO shares.'));
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
      setError(err instanceof ApiError ? err.message : 'Could not buy shares.');
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
      setError(err instanceof ApiError ? err.message : 'Could not redeem shares.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 4px 10px' }}>SACCO shares</h3>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', margin: '0 4px 10px' }}>
        Buy real shares in itunda's own SACCO pool and earn periodic dividends, the same real cooperative model as Rwanda's 416 Umurenge SACCOs.
      </p>
      <div className="toss-card" style={{ marginBottom: '16px' }}>
        {shareholding === undefined ? (
          <div style={{ height: '48px' }} />
        ) : (
          <>
            <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Shares held</p>
            <p style={{ fontSize: '22px', fontWeight: 700 }}>{(shareholding?.sharesHeld ?? 0).toLocaleString()} RWF</p>
            {currentValue != null && (
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>Total contributed: {(shareholding?.totalContributed ?? 0).toLocaleString()} RWF</p>
            )}
          </>
        )}
        {error && <p style={{ fontSize: '13px', color: '#E53935', marginTop: '8px' }} role="alert">{error}</p>}
        <div style={{ display: 'flex', gap: '8px', marginTop: '12px' }}>
          <input
            type="number" value={amount} onChange={(e) => setAmount(e.target.value)} placeholder="Amount (RWF)"
            style={{ flex: 1, padding: '10px 12px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
          />
          <button className="toss-btn toss-btn-primary" disabled={busy} onClick={handleBuy}>Buy</button>
          <button className="toss-btn toss-btn-secondary" disabled={busy} onClick={handleRedeem}>Redeem</button>
        </div>
      </div>
      {dividends && dividends.length > 0 && (
        <div className="toss-card">
          <p style={{ fontSize: '13px', fontWeight: 700, marginBottom: '8px' }}>Dividend history</p>
          {dividends.map((d) => (
            <div key={d.id} style={{ display: 'flex', justifyContent: 'space-between', fontSize: '13px', padding: '4px 0' }}>
              <span style={{ color: 'var(--toss-grey-500)' }}>{new Date(d.createdAt).toLocaleDateString()}</span>
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
  const [detail, setDetail] = useState<WeeklySavingsPlanDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [confirmingCancel, setConfirmingCancel] = useState(false);
  const [needsDeviceVerification, setNeedsDeviceVerification] = useState(false);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlan(id).then(setDetail).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load this plan.'));
  };
  useEffect(load, []);

  // Same real device step-up gate as GroupAccountDetailView/GoalCard's deposit/
  // withdraw handlers above -- cancel/withdraw both move real money out of this
  // plan's wallet, so an untrusted device hits the same DEVICE_NOT_VERIFIED 403.
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
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') setNeedsDeviceVerification(true);
      else setError(err instanceof ApiError ? err.message : 'Could not cancel this plan.');
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
      if (err instanceof ApiError && err.code === 'DEVICE_NOT_VERIFIED') setNeedsDeviceVerification(true);
      else setError(err instanceof ApiError ? err.message : 'Could not withdraw this plan.');
    } finally {
      setBusy(false);
    }
  };

  if (error && !detail) {
    return (
      <div className="toss-card">
        <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginTop: '12px' }}>Back</button>
      </div>
    );
  }
  if (detail === null) return <div className="toss-card skeleton" style={{ height: '260px' }} />;

  const { plan, walletBalance, installments } = detail;
  const pct = Math.min(100, Math.round((plan.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
  const currentRate = plan.streakBroken ? plan.baseRate : plan.baseRate + plan.bonusRate;

  return (
    <div>
      <button className="toss-btn toss-btn-secondary" onClick={onBack} style={{ marginBottom: '12px' }}>← Back to 26-week savings</button>

      <div className="toss-card" style={{ marginBottom: '16px', background: 'linear-gradient(135deg, var(--toss-blue) 0%, #4A90E2 100%)', color: '#fff' }}>
        <p style={{ fontSize: '13px', opacity: 0.85 }}>{plan.name} · Week {plan.weeksElapsed} of {WEEKLY_SAVINGS_TERM_WEEKS}</p>
        <p style={{ fontSize: '28px', fontWeight: 800, margin: '6px 0' }}>{walletBalance.toLocaleString()} RWF</p>
        <div style={{ height: '6px', borderRadius: '3px', backgroundColor: 'rgba(255,255,255,0.3)', marginTop: '6px', overflow: 'hidden' }}>
          <div style={{ height: '100%', width: `${pct}%`, backgroundColor: '#fff' }} />
        </div>
        <p style={{ fontSize: '12px', marginTop: '10px', opacity: 0.9 }}>
          {plan.installmentsCollected} installment{plan.installmentsCollected === 1 ? '' : 's'} collected · earning {currentRate}% real annual rate
        </p>
        <p style={{ fontSize: '12px', opacity: 0.9 }}>
          {plan.streakBroken
            ? 'Streak broken — bonus rate forfeited for the rest of this plan'
            : `On streak — stay unbroken to keep the +${plan.bonusRate}% bonus at maturity`}
        </p>
      </div>

      <div className="toss-card" style={{ marginBottom: '16px' }}>
        <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Plan details</h3>
        <Row label="Status" value={plan.status} />
        <Row label="Base weekly amount" value={`${plan.baseWeeklyAmount.toLocaleString()} RWF`} />
        <Row label="Escalation" value={escalationLabel(plan.escalationRate)} />
        <Row label="Base rate + streak bonus" value={`${plan.baseRate}% + ${plan.bonusRate}%`} />
        {plan.status === 'ACTIVE' && <Row label="Next installment due" value={new Date(plan.nextInstallmentDueAt).toLocaleDateString()} />}
        {plan.totalInterestPaid != null && <Row label="Interest paid" value={`${plan.totalInterestPaid.toLocaleString()} RWF`} />}
      </div>

      {installments.length > 0 && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <h3 style={{ fontSize: '14px', fontWeight: 700, marginBottom: '8px' }}>Installments</h3>
          {installments.map((inst) => (
            <div key={inst.id} style={{ display: 'flex', justifyContent: 'space-between', padding: '5px 0', fontSize: '13px' }}>
              <span style={{ color: 'var(--toss-grey-500)' }}>Week {inst.weekNumber}</span>
              <span style={{ fontWeight: 600 }}>{inst.amount.toLocaleString()} RWF</span>
            </div>
          ))}
        </div>
      )}

      {message && <p style={{ fontSize: '13px', color: 'var(--toss-blue)', marginBottom: '10px' }}>{message}</p>}
      {error && <p style={{ fontSize: '13px', color: '#E53935', marginBottom: '10px' }} role="alert">{error}</p>}

      {needsDeviceVerification ? (
        <DeviceStepUpPrompt onVerified={() => setNeedsDeviceVerification(false)} onCancel={() => { setNeedsDeviceVerification(false); setConfirmingCancel(false); }} />
      ) : (
        <>
          {plan.status === 'ACTIVE' && (
            <div className="toss-card">
              {confirmingCancel ? (
                <div>
                  <p style={{ fontSize: '13px', marginBottom: '10px' }}>
                    Cancelling now pays out your principal plus base-rate interest, but permanently forfeits the +{plan.bonusRate}% streak bonus. Continue?
                  </p>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setConfirmingCancel(false)} disabled={busy}>Keep saving</button>
                    <button className="toss-btn toss-btn-danger" style={{ flex: 1 }} onClick={handleCancel} disabled={busy}>{busy ? '…' : 'Cancel plan'}</button>
                  </div>
                </div>
              ) : (
                <button className="toss-btn toss-btn-secondary" style={{ width: '100%' }} onClick={() => setConfirmingCancel(true)} disabled={busy}>
                  Cancel plan (early withdrawal)
                </button>
              )}
            </div>
          )}

          {plan.status === 'MATURED' && !plan.withdrawnAt && (
            <button className="toss-btn toss-btn-primary" style={{ width: '100%' }} onClick={handleWithdraw} disabled={busy}>
              {busy ? '…' : `Withdraw ${walletBalance.toLocaleString()} RWF to main wallet`}
            </button>
          )}
        </>
      )}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'space-between', padding: '4px 0', fontSize: '13px' }}>
      <span style={{ color: 'var(--toss-grey-500)' }}>{label}</span>
      <span style={{ fontWeight: 600 }}>{value}</span>
    </div>
  );
}

function CreateWeeklySavingsPlanForm({ onCreated }: { onCreated: () => void }) {
  const [open, setOpen] = useState(false);
  const [name, setName] = useState('');
  const [baseWeeklyAmount, setBaseWeeklyAmount] = useState('');
  const [escalationRate, setEscalationRate] = useState(0.10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  if (!open) {
    return (
      <button
        className="toss-btn toss-btn-secondary"
        style={{ width: '100%', marginBottom: '16px', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
        onClick={() => setOpen(true)}
      >
        <Plus size={16} /> New 26-week savings plan
      </button>
    );
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await createWeeklySavingsPlan(name, Number(baseWeeklyAmount), escalationRate);
      setName('');
      setBaseWeeklyAmount('');
      setEscalationRate(0.10);
      setOpen(false);
      onCreated();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Could not create this plan.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ display: 'flex', flexDirection: 'column', gap: '10px', marginBottom: '16px' }}>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        A real 26-week term deposit, like KakaoBank's 26주적금: your weekly amount auto-debits from your main wallet
        and can step up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks. Stay unbroken all 26 weeks to earn a bonus interest rate on top of the base rate.
      </p>
      <input
        type="text" required placeholder="Plan name (e.g. New Laptop Fund)" value={name} onChange={(e) => setName(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <input
        type="number" min="1" required placeholder="Base weekly amount (RWF)" value={baseWeeklyAmount} onChange={(e) => setBaseWeeklyAmount(e.target.value)}
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '14px' }}
      />
      <div>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)', marginBottom: '6px' }}>Escalation rate (steps up every {WEEKLY_SAVINGS_ESCALATION_STEP_WEEKS} weeks)</p>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
          {WEEKLY_SAVINGS_ESCALATION_RATES.map((rate) => (
            <button
              key={rate}
              type="button"
              onClick={() => setEscalationRate(rate)}
              className={escalationRate === rate ? 'toss-btn toss-btn-primary' : 'toss-btn toss-btn-secondary'}
              style={{ padding: '6px 12px', fontSize: '12px' }}
            >
              {rate === 0 ? 'Flat' : `+${Math.round(rate * 100)}%`}
            </button>
          ))}
        </div>
      </div>
      <div style={{ display: 'flex', gap: '8px' }}>
        <button type="button" className="toss-btn toss-btn-secondary" style={{ flex: 1 }} onClick={() => setOpen(false)}>Cancel</button>
        <button type="submit" className="toss-btn toss-btn-primary" style={{ flex: 1 }} disabled={busy}>{busy ? 'Creating…' : 'Create'}</button>
      </div>
      {error && <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>}
    </form>
  );
}

function WeeklySavingsSection() {
  const [plans, setPlans] = useState<WeeklySavingsPlan[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [openId, setOpenId] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchWeeklySavingsPlans().then(setPlans).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your 26-week savings plans.'));
  };
  useEffect(load, []);

  if (openId) {
    return <WeeklySavingsPlanDetailView id={openId} onBack={() => { setOpenId(null); load(); }} />;
  }

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 4px 10px' }}>26-week savings</h3>
      <CreateWeeklySavingsPlanForm onCreated={load} />
      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {plans === null ? (
        <div className="toss-card skeleton" style={{ height: '64px' }} />
      ) : plans.length === 0 ? (
        <EmptyState message="No 26-week savings plans yet — start one with an escalating weekly auto-debit and a streak-gated bonus rate." />
      ) : (
        plans.map((p) => {
          const pct = Math.min(100, Math.round((p.weeksElapsed / WEEKLY_SAVINGS_TERM_WEEKS) * 100));
          return (
            <button
              key={p.id}
              onClick={() => setOpenId(p.id)}
              className="toss-card"
              style={{ display: 'block', width: '100%', textAlign: 'left', marginBottom: '10px', border: 'none' }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between' }}>
                <p style={{ fontSize: '14px', fontWeight: 700 }}>{p.name}</p>
                <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{p.status}</p>
              </div>
              <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
                {p.currentAmount.toLocaleString()} RWF · week {p.weeksElapsed}/{WEEKLY_SAVINGS_TERM_WEEKS}
                {p.streakBroken ? ' · streak broken' : ' · on streak'}
              </p>
              <div style={{ height: '5px', borderRadius: '3px', backgroundColor: 'var(--toss-grey-100)', marginTop: '6px', overflow: 'hidden' }}>
                <div style={{ height: '100%', width: `${pct}%`, backgroundColor: p.streakBroken ? 'var(--toss-grey-500)' : 'var(--toss-blue)' }} />
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
  const [deposits, setDeposits] = useState<UpfrontInterestDeposit[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchMyUpfrontDeposits().then(setDeposits).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your 12-month deposits.'));
  };
  useEffect(load, []);

  return (
    <div>
      <h3 style={{ fontSize: '15px', fontWeight: 700, margin: '4px 4px 10px' }}>12-month deposit</h3>
      <OpenUpfrontDepositForm onOpened={load} />
      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {deposits === null ? (
        <div className="toss-card skeleton" style={{ height: '64px' }} />
      ) : deposits.length === 0 ? (
        <EmptyState message="No 12-month deposits yet — open one to get a full year's interest paid today, principal locked for 12 months." />
      ) : (
        deposits.map((d) => <UpfrontDepositCard key={d.id} deposit={d} onChanged={load} />)
      )}
    </div>
  );
}

function OpenUpfrontDepositForm({ onOpened }: { onOpened: () => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not open this deposit.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form onSubmit={handleSubmit} className="toss-card" style={{ marginBottom: '16px', display: 'flex', flexDirection: 'column', gap: '10px' }}>
      <p style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>
        {UPFRONT_DEPOSIT_ANNUAL_RATE}% interest for the full year, paid to your wallet today. Principal is locked for 12 months — no early withdrawal.
      </p>
      <input
        type="number"
        min={UPFRONT_DEPOSIT_MIN_PRINCIPAL}
        max={UPFRONT_DEPOSIT_MAX_PRINCIPAL}
        value={principal}
        onChange={(e) => setPrincipal(e.target.value)}
        placeholder={`Principal (${UPFRONT_DEPOSIT_MIN_PRINCIPAL.toLocaleString()} - ${UPFRONT_DEPOSIT_MAX_PRINCIPAL.toLocaleString()} RWF)`}
        required
        style={{ padding: '12px 14px', borderRadius: '10px', border: '1px solid var(--toss-grey-200)', fontSize: '15px' }}
      />
      {error && <p style={{ fontSize: '13px', color: '#E53935', margin: 0 }} role="alert">{error}</p>}
      <button type="submit" className="toss-btn toss-btn-primary" disabled={submitting}>
        {submitting ? 'Opening…' : 'Open deposit'}
      </button>
    </form>
  );
}

function UpfrontDepositCard({ deposit, onChanged }: { deposit: UpfrontInterestDeposit; onChanged: () => void }) {
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
      setError(err instanceof ApiError ? err.message : 'Could not withdraw this deposit.');
    } finally {
      setWithdrawing(false);
    }
  };

  return (
    <div className="toss-card" style={{ marginBottom: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between' }}>
        <p style={{ fontSize: '14px', fontWeight: 700 }}>{deposit.principal.toLocaleString()} RWF</p>
        <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>{deposit.withdrawnAt ? 'WITHDRAWN' : deposit.status}</p>
      </div>
      <p style={{ fontSize: '12px', color: 'var(--toss-grey-500)' }}>
        +{deposit.interestPaid.toLocaleString()} RWF interest already paid · matures {new Date(deposit.maturesAt).toLocaleDateString()}
      </p>
      {error && <p style={{ fontSize: '12px', color: '#E53935', marginTop: '6px' }} role="alert">{error}</p>}
      {matured && !deposit.withdrawnAt && (
        <button className="toss-btn toss-btn-secondary" style={{ marginTop: '10px' }} disabled={withdrawing} onClick={handleWithdraw}>
          {withdrawing ? 'Withdrawing…' : 'Withdraw principal'}
        </button>
      )}
    </div>
  );
}

function SavingsView() {
  const [goals, setGoals] = useState<SavingsGoal[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setError(null);
    fetchGoals().then(setGoals).catch((err) => setError(err instanceof ApiError ? err.message : 'Could not load your goals.'));
  };
  useEffect(load, []);

  return (
    <div>
      <InterestJarCard />
      <RoundUpCard goals={goals ?? []} />
      <CreateGoalForm onCreated={load} />
      {error && (
        <div className="toss-card" style={{ marginBottom: '16px' }}>
          <p style={{ fontSize: '13px', color: '#E53935' }} role="alert">{error}</p>
        </div>
      )}
      {goals === null ? (
        <div className="toss-card skeleton" style={{ height: '100px' }} />
      ) : goals.length === 0 ? (
        <EmptyState message="No savings goals yet." />
      ) : (
        goals.map((g) => <GoalCard key={g.id} goal={g} onChanged={load} />)
      )}
      <div style={{ marginTop: '24px' }}>
        <GroupAccountsSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <IkiminaSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <SaccoSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <WeeklySavingsSection />
      </div>
      <div style={{ marginTop: '24px' }}>
        <UpfrontDepositSection />
      </div>
    </div>
  );
}

export default function BankDashboard({ onLogout }: { onLogout: () => void }) {
  const [tab, setTab] = useState<Tab>('HOME');
  const [pendingConversationId, setPendingConversationId] = useState<string | null>(null);
  const user = getStoredUser();

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

  const TABS: { id: Tab; label: string }[] = [
    { id: 'HOME', label: 'Home' },
    { id: 'MY', label: 'My' },
    { id: 'SHOP', label: 'Shop' },
    { id: 'EATS', label: 'Eats' },
    { id: 'STOCKS', label: 'Invest' },
    { id: 'SAVINGS', label: 'Savings' },
    { id: 'MESSAGES', label: 'Messages' },
    { id: 'MARKETPLACE', label: 'Marketplace' },
    { id: 'COMMUNITY', label: 'Community' },
    { id: 'JOBS', label: 'Jobs' },
    { id: 'PROPERTY', label: 'Property' },
    { id: 'RIDES', label: 'Rides' },
    { id: 'DESIGNATED_DRIVER', label: 'Designated driver' },
    { id: 'BIKESHARE', label: 'Bike' },
    { id: 'PARKING', label: 'Parking' },
    { id: 'BUS', label: 'Bus' },
    { id: 'KNOWLEDGE', label: 'Q&A' },
    { id: 'MAP', label: 'Map' },
    { id: 'CERTIFICATE', label: 'Certificate' },
    { id: 'SHOPPING', label: 'Shopping' },
    { id: 'DEVICES', label: 'Devices' },
    { id: 'CARD', label: 'Card' },
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

  return (
    <div style={{ padding: '20px', paddingBottom: '100px', maxWidth: '480px', margin: '0 auto' }}>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px', padding: '0 8px' }}
      >
        <h2 style={{ color: 'var(--toss-grey-900)', margin: 0, fontSize: '24px', fontWeight: '700', letterSpacing: '-0.5px' }}>Itunda</h2>
        <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
          {user && <span style={{ fontSize: '13px', color: 'var(--toss-grey-500)' }}>{user.firstName}</span>}
          <button onClick={handleLogout} style={{ color: 'var(--toss-grey-500)', display: 'flex' }} aria-label="Sign out">
            <LogOut size={18} />
          </button>
        </div>
      </motion.div>

      <div style={{ display: 'flex', gap: '4px', padding: '4px', marginBottom: '16px', backgroundColor: 'var(--toss-grey-100)', borderRadius: '10px' }}>
        {TABS.map(({ id, label }) => (
          <button
            key={id}
            onClick={() => setTab(id)}
            style={{
              flex: 1, padding: '8px', borderRadius: '8px', fontSize: '13px', fontWeight: 700,
              color: tab === id ? 'var(--toss-white)' : 'var(--toss-grey-700)',
              backgroundColor: tab === id ? 'var(--toss-blue)' : 'transparent',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === 'HOME' && <HomeView />}
      {tab === 'MY' && <MyView />}
      {tab === 'SHOP' && <ShopView />}
      {tab === 'EATS' && <EatsView />}
      {tab === 'STOCKS' && <StocksView />}
      {tab === 'SAVINGS' && <SavingsView />}
      {tab === 'MESSAGES' && (
        <MessagesView
          initialConversationId={pendingConversationId}
          onConsumedInitial={() => setPendingConversationId(null)}
        />
      )}
      {tab === 'MARKETPLACE' && <MarketplaceView onMessageSeller={handleMessageSeller} />}
      {tab === 'COMMUNITY' && <CommunityView onOpenGroupChat={handleMessageSeller} />}
      {tab === 'JOBS' && <JobsView onMessagePoster={handleMessageSeller} />}
      {tab === 'PROPERTY' && <PropertyView onMessageLister={handleMessageSeller} />}
      {tab === 'RIDES' && <RidesView />}
      {tab === 'DESIGNATED_DRIVER' && <DesignatedDriverView />}
      {tab === 'BIKESHARE' && <BikeShareView />}
      {tab === 'PARKING' && <ParkingView />}
      {tab === 'BUS' && <BusView />}
      {tab === 'KNOWLEDGE' && <KnowledgeView />}
      {tab === 'MAP' && <MapView />}
      {tab === 'CERTIFICATE' && <CertificateView />}
      {tab === 'SHOPPING' && <ShoppingView />}
      {tab === 'DEVICES' && <DevicesView />}
      {tab === 'CARD' && <CardView />}
      {tab === 'OVERVIEW' && <OverviewView />}
      {tab === 'LOANS' && <LoansView />}
      {tab === 'CREDIT_SCORE' && <CreditScoreView />}
      {tab === 'TRUST_SCORE' && <TrustScoreView />}
      {tab === 'REWARDS' && <RewardsView />}
      {tab === 'INSURANCE' && <InsuranceView />}
      {tab === 'BILLS' && <BillsView />}
      {tab === 'AGENT' && <AgentOperatorView />}
      {tab === 'USSD' && <UssdSettingsView />}
      {tab === 'FOREIGN_CURRENCY' && <ForeignCurrencyView />}
      {tab === 'SPENDING' && <SpendingInsightView />}
      {tab === 'SUBSCRIPTIONS' && <SubscriptionsView />}
      {tab === 'IDENTITY' && <IdentityView />}
      {tab === 'SUPPORT' && <SupportView />}
    </div>
  );
}
