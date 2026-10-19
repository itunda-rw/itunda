package rw.itunda.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.

data class EatsMembershipTier(val days: Int, val priceRwf: Int)
val EATS_MEMBERSHIP_TIERS = listOf(EatsMembershipTier(30, 1500), EatsMembershipTier(90, 4000))

// Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
// backend PlatformMembership.kt's own doc comment. Deliberately distinct from Eats
// Club above: waives the fee at every restaurant, no merchant opt-in required.
typealias PlatformMembershipDto = EatsMembershipDto
data class PlatformMembershipResponse(val success: Boolean, val membership: PlatformMembershipDto?)
data class SubscribePlatformMembershipRequest(val days: Int)
val PLATFORM_MEMBERSHIP_TIERS = listOf(EatsMembershipTier(30, 2500), EatsMembershipTier(90, 6500))
data class RemoveFavoriteResponse(val success: Boolean)

// Real Toss Securities-style stock investing (2026-07-20) -- see ApiService's own
// getStocks doc comment for the full account.
// Real Toss/Naver 해외주식 (overseas stock trading, item 230) -- "RSE" (the original 6
// domestic symbols) vs "NASDAQ" (5 real US-listed symbols, same deterministic
// simulation StockCatalog.kt already establishes for RSE). bank-mfe already has this.
data class StockDto(val id: String, val symbol: String, val name: String, val price: Double, val change: Double, val changePercent: Double, val marketCap: String, val volume: Long, val market: String = "RSE")
data class StocksResponse(val success: Boolean, val stocks: List<StockDto>, val watchlist: List<StockDto>? = null)
data class StockPricePointDto(val date: String, val price: Double)
data class StockHistoryResponse(val success: Boolean, val history: List<StockPricePointDto>)
data class PortfolioValuePointDto(val date: String, val value: Double)
data class PortfolioHistoryResponse(val success: Boolean, val history: List<PortfolioValuePointDto>)
data class StockHoldingDto(val stockId: String, val symbol: String, val name: String, val shares: Double, val avgPrice: Double, val currentPrice: Double, val value: Double, val `return`: Double)
data class StockPortfolioDto(val totalValue: Double, val totalReturn: Double, val totalReturnPercent: Double, val holdings: List<StockHoldingDto>)
data class StockPortfolioResponse(val success: Boolean, val portfolio: StockPortfolioDto)
data class TradeStockRequest(val stockId: String, val shares: Double)
data class TradeStockResponse(val success: Boolean, val message: String)
// Real Investment-account top-up (2026-08-04) -- found via a fresh "defined but
// uncalled" endpoint sweep: StocksService.fundInvestmentAccount (a real MAIN ->
// INVESTMENT internal ledger transfer) had zero client anywhere, meaning a user with
// no pre-seeded investment balance had no in-app way to ever actually buy a stock.
data class FundInvestmentRequest(val amount: Double)
data class FundInvestmentTransactionDto(val id: String, val amount: Double, val completedAt: String)
data class FundInvestmentResponse(val success: Boolean, val transaction: FundInvestmentTransactionDto)
data class WatchStockResponse(val success: Boolean)
data class UnwatchStockResponse(val success: Boolean)

// Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- found via a
// fresh "defined but uncalled" endpoint sweep: the backend (set/clear/getPriceAlert +
// StockPriceAlertScheduler) shipped fully live-verified 2026-08-17 but had zero client
// anywhere, on any platform. This is Android's first wiring for it. GET returns a real
// flat shape (StocksController.getPriceAlert); POST/DELETE return the real
// StockWatchlist entity nested under "watch" (StocksController.setPriceAlert/
// clearPriceAlert, unchanged from section 113) -- two genuinely different real
// response shapes, not an inconsistency to normalize away since POST/DELETE already
// shipped and were live-verified against the deployed backend before this pass.
data class SetPriceAlertRequest(val targetPrice: Double, val direction: String)
data class PriceAlertResponse(val success: Boolean, val targetPrice: Double?, val targetDirection: String?, val alertTriggeredAt: String?)
data class StockWatchAlertDto(val targetPrice: Double?, val targetDirection: String?, val alertTriggeredAt: String?)
data class SetPriceAlertResponse(val success: Boolean, val watch: StockWatchAlertDto)

// Real Toss-style unified account overview (rw.itunda.overview.OverviewService) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere (Android,
// iOS, or bank-mfe web). Aggregates accounts/savings/loans/investments/insurance/linked
// external accounts in one call; see OverviewService.kt's own doc comment for why
// insurance is excluded from netWorth (a sunk expense, not an asset).
data class AccountSummaryDto(val id: String, val type: String, val name: String, val balance: java.math.BigDecimal, val currency: String)
data class OverviewSavingsSummaryDto(val totalSaved: java.math.BigDecimal, val goalCount: Int)
data class OverviewLoansSummaryDto(val totalOutstanding: java.math.BigDecimal, val activeCount: Int)
data class OverviewInvestmentsSummaryDto(val totalCostBasis: java.math.BigDecimal, val holdingCount: Int)
data class OverviewInsuranceSummaryDto(val activePolicyCount: Int, val totalMonthlyPremium: java.math.BigDecimal)
data class LinkedAccountSummaryDto(
    val id: String, val provider: String, val maskedAccountNumber: String, val status: String,
    val demoBalance: java.math.BigDecimal?, val demoBalanceCurrency: String?, val isDemoBalance: Boolean,
)
// Real "My assets" tab-by-tab redesign (2026-08-27, direct user reference: 3 real
// Toss "총자산" screenshots). See OverviewService.kt's own doc comments for exactly
// how each is sourced -- Car is raw count+purchase-price only (the live valuation
// math stays private to :vehicle's VehicleValuationService), Tax/Points are always
// real even at zero.
data class OverviewCardsSummaryDto(val hasCard: Boolean, val last4: String?, val design: String?, val frozen: Boolean?)
data class OverviewVehicleSummaryDto(val vehicleCount: Int, val totalPurchasePrice: java.math.BigDecimal)
data class OverviewTaxSummaryDto(val totalPaid: java.math.BigDecimal, val paymentCount: Int)
data class OverviewPointsSummaryDto(val rewardsTotal: java.math.BigDecimal, val payMoneyBalance: java.math.BigDecimal)
data class OverviewResponse(
    val success: Boolean,
    val netWorth: java.math.BigDecimal,
    val accounts: List<AccountSummaryDto>,
    val savings: OverviewSavingsSummaryDto,
    val loans: OverviewLoansSummaryDto,
    val investments: OverviewInvestmentsSummaryDto,
    val insurance: OverviewInsuranceSummaryDto,
    val linkedAccounts: List<LinkedAccountSummaryDto>,
    val cards: OverviewCardsSummaryDto,
    val vehicles: OverviewVehicleSummaryDto,
    val tax: OverviewTaxSummaryDto,
    val points: OverviewPointsSummaryDto,
)

data class NetWorthHistoryPointDto(val month: String, val liquidTotal: java.math.BigDecimal)
data class NetWorthHistoryResponse(val success: Boolean, val history: List<NetWorthHistoryPointDto>)

// Real external bank/MoMo account linking (rw.itunda.overview.LinkedAccountService) --
// a real simulated per-rail verification (same ProviderConnector/RailCatalog
// mechanism transfers/bills/airtime use), demo balance only generated when linking
// actually succeeds. No live Open Banking access exists, so demoBalance is a real,
// honestly-labeled demo value -- see LinkedAccount.kt's own doc comment. This is the
// raw entity shape LinkedAccountController returns, distinct from the summary shape
// OverviewResponse.linkedAccounts uses above (different field names: userId/
// externalAccountNumberMasked/linkedAt/unlinkedAt here, no isDemoBalance).
data class LinkAccountRequest(val provider: String, val externalAccountNumber: String)
data class LinkedAccountEntityDto(
    val id: String, val userId: String, val provider: String, val externalAccountNumberMasked: String,
    val status: String, val failureReason: String?, val linkedAt: String, val unlinkedAt: String?,
    val demoBalance: java.math.BigDecimal?, val demoBalanceCurrency: String?,
)
data class LinkAccountResponse(val success: Boolean, val linkedAccount: LinkedAccountEntityDto)
data class LinkedAccountsResponse(val success: Boolean, val linkedAccounts: List<LinkedAccountEntityDto>)

// Real multi-lender loan marketplace (rw.itunda.loans) -- BNR-licensed partner banks
// (Bank of Kigali, Equity Bank Rwanda, Urwego Bank) alongside itunda's own book; only
// itunda has a real underwriting/disbursement path, see LoanOffer.kt's own doc
// comment. Found 2026-07-22 fully built on the backend, but every "Loan"/"Get a loan"
// row in this app was 100% hardcoded static text with no API call at all.
data class LoanOfferDto(val id: String, val lenderId: String, val lenderName: String, val name: String, val maxAmount: java.math.BigDecimal, val interestRate: Double, val term: String, val requirements: String)
data class LenderDto(val id: String, val name: String, val kind: String)
data class LoanOffersResponse(val success: Boolean, val offers: List<LoanOfferDto>)
data class LendersResponse(val success: Boolean, val lenders: List<LenderDto>)
data class LoanAccountDto(val id: String, val userId: String, val accountId: String, val offerId: String, val principal: java.math.BigDecimal, val outstanding: java.math.BigDecimal, val interestRate: Double, val status: String, val disbursedAt: String)
data class MyLoansResponse(val success: Boolean, val loans: List<LoanAccountDto>)
data class ApplyLoanRequest(val loanId: String, val amount: java.math.BigDecimal)
data class ApplyLoanResponse(val success: Boolean, val message: String, val loan: LoanAccountDto)
data class RepayLoanRequest(val loanId: String, val amount: java.math.BigDecimal)
data class RepayLoanTransactionDto(val id: String, val amount: java.math.BigDecimal, val type: String, val status: String, val description: String, val completedAt: String)
data class RepayLoanResponse(val success: Boolean, val message: String, val transaction: RepayLoanTransactionDto, val remaining: java.math.BigDecimal, val newBalance: java.math.BigDecimal)

// Real 대환대출 (loan refinancing) -- see LoansService.refinanceLoan's own doc comment.
// bank-mfe wired 2026-07-26; found unwired on Android/iOS via the same sweep that
// found the lender filter (item 200).
data class RefinanceLoanRequest(val loanId: String)
data class RefinanceResult(
    val success: Boolean,
    val message: String,
    val oldLoanId: String,
    val oldInterestRate: Double,
    val newLoanId: String,
    val newInterestRate: Double,
    val newLoanName: String,
    val amount: java.math.BigDecimal,
    val creditScore: Int,
)

// Real Toss Bank/KakaoBank 마이너스통장 (overdraft/revolving line-of-credit) -- see
// backend OverdraftAccount.kt's own doc comment. Found 2026-07-29 via a full-backend-
// endpoint sweep: real, live-verified backend (open/draw/repay, real daily interest
// accrual, real security-alert push) with zero client anywhere on any of the 3
// platforms.
data class OverdraftAccountDto(
    val id: String,
    val userId: String,
    val accountId: String,
    val creditLimit: java.math.BigDecimal,
    val drawnBalance: java.math.BigDecimal,
    val interestRate: Double,
    val status: String,
)
data class OverdraftAccountResponse(val success: Boolean, val account: OverdraftAccountDto?)
data class OpenOverdraftRequest(val requestedLimit: java.math.BigDecimal)
data class OverdraftAmountRequest(val amount: java.math.BigDecimal)
data class OverdraftDrawResponse(
    val success: Boolean,
    val transactionId: String,
    val amount: java.math.BigDecimal,
    val drawnBalance: java.math.BigDecimal,
    val availableCredit: java.math.BigDecimal,
)
data class OverdraftRepayResponse(
    val success: Boolean,
    val transactionId: String,
    val amount: java.math.BigDecimal,
    val drawnBalance: java.math.BigDecimal,
    val availableCredit: java.math.BigDecimal,
    val newBalance: java.math.BigDecimal,
)

// Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line) -- see backend
// PostpaidCreditLine.kt's own doc comment. Genuinely distinct from the overdraft above:
// real interest-free on-time repayment, a much lower real qualification bar, and an
// auto-computed (not user-requested) limit.
data class PostpaidCreditLineDto(
    val id: String,
    val userId: String,
    val accountId: String,
    val creditLimit: java.math.BigDecimal,
    val currentBalance: java.math.BigDecimal,
    val status: String,
    val cycleDueAt: String?,
    val lastLateFeeAccrualAt: String?,
    val createdAt: String,
    val updatedAt: String,
)
data class PostpaidCreditLineResponse(val success: Boolean, val line: PostpaidCreditLineDto?)
data class PostpaidCreditAmountRequest(val amount: java.math.BigDecimal)
data class PostpaidCreditActionResponse(
    val success: Boolean,
    val transactionId: String,
    val amount: java.math.BigDecimal,
    val currentBalance: java.math.BigDecimal,
    val availableCredit: java.math.BigDecimal,
    val newBalance: java.math.BigDecimal? = null,
)

// Real Toss Bank 체크카드 (check/debit card) client (item 207) -- see backend
// DebitCard.kt's own doc comment. bank-mfe shipped first (2026-07-31); this is the
// first Android client. "Paying with your card" is itunda's own honest, ledger-backed
// simulation of a card-present purchase -- no real Visa/Mastercard rail exists.
data class CardDto(
    val id: String,
    val last4: String,
    val dailyLimit: java.math.BigDecimal,
    val monthlyLimit: java.math.BigDecimal,
    val frozen: Boolean,
    // Real "분실신고"/"카드 해지하기"/"카드 재발급"/"카드 비밀번호 변경" fields
    // (2026-09-01, direct user-supplied Toss Bank card-management screenshots) --
    // see the backend's DebitCard.kt doc comment for why lost/closedAt are
    // deliberately separate, one-way states from `frozen`.
    val lost: Boolean = false,
    val closedAt: String? = null,
    val pinSet: Boolean = false,
    val reissuedAt: String? = null,
    val issuedAt: String,
    val design: String,
    val spentToday: java.math.BigDecimal,
    val spentThisMonth: java.math.BigDecimal,
    val remainingToday: java.math.BigDecimal,
    val remainingThisMonth: java.math.BigDecimal,
)
data class CardResponse(val success: Boolean, val card: CardDto)
data class CardTransactionDto(
    val id: String,
    val cardId: String,
    val amount: java.math.BigDecimal,
    val merchantName: String,
    // Real Toss "결제 계좌" (payment account) reference (2026-09-12) -- which of the
    // user's own accounts funded this charge. See the backend's
    // CardService.chargeWithCard doc comment for why only MAIN/PAY are ever real here.
    val fundingAccountType: String = "MAIN",
    val createdAt: String,
)
data class CardTransactionsResponse(val success: Boolean, val transactions: List<CardTransactionDto>, val totalElements: Long, val totalPages: Int)
data class SetCardLimitsRequest(val dailyLimit: java.math.BigDecimal, val monthlyLimit: java.math.BigDecimal)
data class ChargeCardRequest(val amount: java.math.BigDecimal, val merchantName: String, val fundingAccountType: String = "MAIN")
data class ChargeCardResponse(val success: Boolean, val transaction: CardTransactionDto, val card: CardDto)
data class SetCardPinRequest(val newPin: String, val currentCredential: String)

// Real card-design picker (2026-08-27, direct user instruction: "update itunda bank
// with all those cards designs allowing users to choose from those designs... that's
// how toss does it too") -- `design` must be one of the 5 real ids CardDesigns.ALL
// (ItundaAppScreen.kt's own doc comment has the full colorway list); the backend's own
// DebitCardDesign whitelist real-400s anything else.
data class IssueCardRequest(val design: String)

// Real Kigali public-transit stored-value balance (2026-08-27) -- see the backend's
// TransitBalance.kt doc comment for the full sourced account of Kigali's real Tap&Go
// fare system (AC Group Ltd, Kigali Bus Services, Royal Express) and the honest
// boundary this simulates: itunda has no real partnership with any of them, so this is
// never named "Tap&Go" anywhere in this client.
data class TransitBalanceDto(val balance: java.math.BigDecimal, val createdAt: String)
data class TransitBalanceResponse(val success: Boolean, val balance: TransitBalanceDto)
data class TransitTripDto(
    val id: String,
    val userId: String,
    val operator: String,
    val fare: java.math.BigDecimal,
    val ledgerTransactionId: String,
    val createdAt: String,
)
data class TransitTripsResponse(val success: Boolean, val trips: List<TransitTripDto>, val totalElements: Long, val totalPages: Int)
data class TopUpTransitRequest(val amount: java.math.BigDecimal)
data class TapFareRequest(val operator: String, val fare: java.math.BigDecimal)
data class TapFareResponse(val success: Boolean, val trip: TransitTripDto, val balance: TransitBalanceDto)

// Real "agent collects a fare from a rider's own presented code" flow (2026-08-27,
// direct user follow-up: "for simplification we need nfc"). `code` is the same
// CustomerPaymentCode value already generated by generateCustomerPaymentCode and shown
// on "My payment code" -- reached here via either an NFC tap (this app's own
// TransitHceService broadcasts it) or a camera QR scan, transport-agnostic.
data class TapFareByCodeRequest(val code: String, val operator: String, val fare: java.math.BigDecimal)
data class TransitCollectResultDto(val operator: String, val fare: java.math.BigDecimal, val collectedAt: String)
data class TapFareByCodeResponse(val success: Boolean, val collected: TransitCollectResultDto)

// Real "tap to pay your moto-taxi fare" (2026-08-27, direct user follow-up: "now we
// can make pay for tax and moto as well"). `code` is the same CustomerPaymentCode
// value already generated by generateCustomerPaymentCode -- see the backend's
// MotoFareService.kt doc comment for the full sourced account.
data class CollectMotoFareRequest(val code: String, val fare: java.math.BigDecimal)
data class MotoFareCollectResultDto(val fare: java.math.BigDecimal, val collectedAt: String)
data class CollectMotoFareResponse(val success: Boolean, val collected: MotoFareCollectResultDto)

// Real gap found live (uncalled-endpoint sweep, 2026-08-29): the collect flow above
// existed with zero way for a driver to ever see what they'd collected -- the
// backend's own MotoFareController.getMyTripsAsDriver ("/earnings") had zero caller
// anywhere on any platform since the feature shipped 2026-08-27.
data class MotoFareTripDto(val id: String, val riderUserId: String, val driverUserId: String, val fare: java.math.BigDecimal, val createdAt: String)
data class MotoFareEarningsResponse(val success: Boolean, val trips: List<MotoFareTripDto>, val totalElements: Long, val totalPages: Int)

// Real customer support tickets, tied to a specific transaction (rw.itunda.support) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere; the
// "Support" section in this app was five static rows (FAQ/Live chat/...) with no
// backend behind any of them. category must be one of GENERAL/PAYMENT_DISPUTE/
// ACCOUNT_TAKEOVER -- see SupportTicket.kt's own doc comment on why a ticket is always
// tied to a specific transaction, not a free-floating complaint.
data class CreateSupportTicketRequest(val transactionId: String, val category: String, val description: String)
data class SupportTicketDto(
    val id: String, val userId: String, val transactionId: String, val category: String, val description: String,
    val status: String, val resolution: String?, val resolutionNotes: String?, val refundTransactionId: String?,
    val frozeAccountId: String?, val dueBy: String, val reviewedBy: String?, val createdAt: String, val resolvedAt: String?,
)
data class CreateSupportTicketResponse(val success: Boolean, val ticket: SupportTicketDto)
data class SupportTicketsResponse(val success: Boolean, val tickets: List<SupportTicketDto>)

// Real USSD basic-banking access (item 231, rw.itunda.ussd) -- the real menu, PIN
// check, and money transfer (send money/check balance/pay a merchant) are fully built
// and working on the backend; only the real MNO/telco short-code partnership needed
// to dial *XXX# is missing (bank-mfe's own UssdSettingsView.tsx honestly discloses
// this). This is the smartphone-side companion: a real, separate 4-6 digit PIN (not
// the account password, M-Pesa-style convention) a user sets here so they can later
// use any basic phone. First Android client for this -- bank-mfe shipped first.
data class SetUssdPinRequest(val pin: String)

// Real "alternative data" credit score (rw.itunda.creditscore, computation lives in
// :core's CreditScoreService so LoansService's real risk-gating can share it) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere. Not a
// real bureau score (no regulatory access exists for one) -- computed live from
// itunda's own real transaction/loan/savings/KYC history on every call.
data class CreditScoreFactorDto(val name: String, val points: Int, val description: String)
data class CreditScoreResponse(val success: Boolean, val score: Int, val factors: List<CreditScoreFactorDto>, val computedAt: String)

// Real actionable next-steps -- CreditScoreService.getImprovementSuggestions existed on
// the backend with a client on bank-mfe only; zero Android/iOS UI until now. Distinct
// from the factor breakdown above (what makes up your score today): this is what to do
// NEXT to raise it (action + real point gain + why), computed from the same real
// account-activity gaps (unverified KYC, loan history, transaction count) CreditScoreScreen
// already reads factors from.
data class CreditScoreSuggestionDto(val action: String, val pointsGain: Int, val description: String)
data class CreditScoreSuggestionsResponse(val success: Boolean, val suggestions: List<CreditScoreSuggestionDto>)

// Real Karrot-Score-style numeric trust/reputation badge (rw.itunda.trustscore) --
// distinct from the per-listing trustScores batch map used for seller/poster/lister
// badges on Hood cards (see MarketplaceDto etc.) -- this is GET /api/v1/trust-score,
// a user's own full factor breakdown, mirroring CreditScoreResponse's shape exactly.
// Found 2026-07-31 real on bank-mfe only, zero UI on Android/iOS despite that.
data class TrustScoreFactorDto(val name: String, val points: Int, val description: String)
data class TrustScoreResponse(val success: Boolean, val score: Int, val factors: List<TrustScoreFactorDto>, val computedAt: String)

// Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own doc
// comment: "Store-facing API: the operator's JWT determines the agent; callers never
// supply an agent id." Distinct from AgentCashScreen.kt's own customer-facing
// withdrawal-code creation -- this is the STAFF side, real till balance + real
// cash-in/cash-out + real till reconciliation. bank-mfe already has this
// (AgentOperatorView); this is the first native client (Android/iOS).
data class AgentTillReconciliationDto(
    val id: String, val agentId: String, val businessDate: String, val expectedCash: java.math.BigDecimal,
    val countedCash: java.math.BigDecimal, val variance: java.math.BigDecimal, val submittedByUserId: String,
    val status: String, val reviewedByUserId: String?, val reviewNote: String?, val reviewedAt: String?, val createdAt: String,
)
data class AgentTillSnapshotDto(
    val agentId: String, val agentName: String, val expectedCash: java.math.BigDecimal,
    val todayCashIn: java.math.BigDecimal, val todayCashOut: java.math.BigDecimal,
    val reconciliation: AgentTillReconciliationDto?,
)
data class AgentActivityItemDto(
    val id: String, val type: String, val amount: java.math.BigDecimal, val receiptNumber: String,
    val ledgerTransactionId: String, val createdAt: String,
)
data class AgentTillResponse(val success: Boolean, val till: AgentTillSnapshotDto)
data class AgentActivityResponse(val success: Boolean, val activity: List<AgentActivityItemDto>)
data class AgentCashInRequest(val accountNumber: String, val amount: java.math.BigDecimal, val receiptNumber: String)
data class AgentCashOutRequest(val accountNumber: String, val amount: java.math.BigDecimal, val receiptNumber: String, val authorizationCode: String)
data class AgentCashResultResponse(val success: Boolean, val newBalance: java.math.BigDecimal, val operatorCommission: java.math.BigDecimal)
data class SubmitTillCountRequest(val countedCash: java.math.BigDecimal)
data class SetAgentLocationRequest(val latitude: Double, val longitude: Double)
// Real, minimal fields only -- deliberately not NearbyAgentDto (a different real shape:
// that one is search-result specific, with a computed distanceKm this raw Agent entity
// doesn't have). See backend Agent.kt's own field list.
data class AgentLocationDto(val id: String, val displayName: String, val latitude: Double?, val longitude: Double?)
data class AgentResponse(val success: Boolean, val agent: AgentLocationDto)
data class AgentTillReconciliationResponse(val success: Boolean, val reconciliation: AgentTillReconciliationDto)

// Real peer-to-peer agent float rebalancing marketplace -- see the backend's
// FloatMarketplaceService.kt doc comment for the full sourced account (a real
// documented top-2 operational challenge for mobile money agents, distinct from
// AgentOperatorController's admin-to-agent till funding). bank-mfe already has
// this; this is the first native client (Android/iOS).
data class FloatListingDto(
    val id: String, val agentId: String, val amount: java.math.BigDecimal,
    val claimedAmount: java.math.BigDecimal, val status: String, val createdAt: String,
)
data class NearbyFloatListingDto(
    val listing: FloatListingDto, val agentDisplayName: String, val distanceKm: Double, val remainingAmount: java.math.BigDecimal,
)
