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

data class CreateSavingsGoalRequest(
    val name: String,
    val targetAmount: java.math.BigDecimal,
    val monthlyContribution: java.math.BigDecimal? = null,
    val targetDate: String? = null,
    val category: String? = null,
)
data class CreateSavingsGoalResponse(val success: Boolean, val goal: SavingsGoal)

data class InterestJar(
    val userId: String,
    val accountId: String,
    val balance: Double,
    val rate: Double,
    val earnedThisMonth: Double,
    val earnedTotal: Double,
)

data class InterestJarResponse(val success: Boolean, val jar: InterestJar)

// Real per-bucket ledger (2026-08-31, direct user-supplied Toss Bank screenshots:
// 보관하기/매일모으기 each get their own full-screen ledger) -- mirrors backend's own
// BucketTransactionDto exactly (services/backend/savings/.../BucketTransactionDto.kt).
// One normalized shape every savings bucket's own transaction endpoint returns.
data class BucketTransactionDto(
    val id: String,
    val description: String,
    val amount: Double,
    val isCredit: Boolean,
    val balanceAfter: Double,
    val createdAt: String,
)

data class BucketTransactionsResponse(val success: Boolean, val transactions: List<BucketTransactionDto>)

// Real Deposit Protection Fund status (2026-08-11) -- see backend's
// DepositProtectionFund.kt doc comment. coverageCapPerUser/contributionRateBps are
// itunda's own chosen policy figures, not a claimed real BNR-backed scheme -- every
// client rendering this must keep that framing, not present it as real deposit
// insurance.
data class DepositProtectionStatus(
    val fundReserveBalance: java.math.BigDecimal,
    val coverageCapPerUser: java.math.BigDecimal,
    val contributionRateBps: Int,
    val lastContributionAt: String?,
    val yourTotalDeposits: java.math.BigDecimal,
    val yourCoveredBalance: java.math.BigDecimal,
)
data class DepositProtectionStatusResponse(val success: Boolean, val status: DepositProtectionStatus)

data class DiscoverItem(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val color: String,
    val isNew: Boolean,
    val badge: String?,
    // Real server-side ranking (2026-08-11) -- see backend DiscoverService's own doc
    // comment (Toss Intelligence-banner research): the backend now decides which
    // item is most worth a user's attention (KYC/compliance highest, cross-sell into
    // an untried real product next, static catalog lowest), not the client. Default
    // 0 only matters if an old cached response without this field ever deserializes.
    val priority: Int = 0
)

data class DiscoverResponse(
    val success: Boolean,
    val items: List<DiscoverItem>
)

// Mirrors services/backend/account's AccountController/TransferQuote.kt exactly
// (2026-07-12) -- the real transfer flow (RecipientEntryScreen/TransferAmountScreen
// in :features:payments:impl) was UI-only until now; these are what wire it to the
// actual quoteTransfer/confirmTransfer endpoints.
data class QuoteTransferRequest(val amount: java.math.BigDecimal, val recipient: String, val fromAccountId: String? = null, val description: String? = null)

data class TransferQuoteDto(
    val id: String,
    val fromAccountId: String,
    val recipient: String,
    val amount: Double,
    val fee: Double,
    val totalDebit: Double,
    val currency: String,
    val expiresAt: String,
)

data class QuoteTransferResponse(val success: Boolean, val quote: TransferQuoteDto)

data class ConfirmTransferRequest(val quoteId: String)

data class TransactionDto(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val type: String,
    val amount: Double,
    val fee: Double,
    val currency: String,
    val status: String,
    val description: String,
    val createdAt: String,
)

data class ConfirmTransferResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)
data class InternalTransferRequest(val fromAccountId: String, val toAccountId: String, val amount: java.math.BigDecimal)
data class InternalTransferResponse(val success: Boolean, val transaction: TransactionDto)

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- mirrors
// P2pController's real SendDirectP2pRequest exactly. Deliberately distinct from
// QuoteTransferRequest/ConfirmTransferRequest above: those always route through a
// simulated external rail and never actually credit another itunda user's account, even
// when the recipient is a real itunda account (confirmed via a direct MySQL check while
// building this on the backend/bank-mfe side one day earlier). This is the real one --
// no quote step needed, since there's no external rail decision to quote.
data class SendDirectP2pRequest(val recipient: String, val amount: java.math.BigDecimal, val description: String = "", val fromAccountId: String? = null)
// fraudWarnings added 2026-09-02 (Toss security research thread) -- real, friendly
// post-send fraud-heuristic warnings (P2pService.sendDirect's own FraudRuleEngine
// evaluation, already computed but previously only surfaced on bank-mfe). Purely
// informational: the transfer this is attached to has already completed, matching
// Toss's own real post-payment FDS notice ("Fraud Suspicion Siren"). Defaults to
// empty so no other SendDirectP2pResponse deserialization is affected.
data class SendDirectP2pResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double, val fraudWarnings: List<String> = emptyList())

// Real Naver Pay "가족 공유 자산 관리" (family shared asset management) -- instant
// transfer to a linked family member, see backend P2pService.sendToFamilyMember's own
// doc comment. bank-mfe already has this (2026-08-15); this is the first Android client.
data class SendToFamilyMemberRequest(val childUserId: String, val amount: java.math.BigDecimal, val description: String = "")
data class SendToFamilyMemberResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)

// Real fixed-amount person-to-person payment request (item 170) -- the P2P counterpart
// to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's own doc
// comment). A real 15-minute-expiring code the requester shares; anyone who has the
// code can pay it directly, real account-to-account, no fee. Real (rate-limited, tested,
// live-verified against a running backend) but had zero client anywhere until now.
data class GenerateP2pRequest(val amount: java.math.BigDecimal, val description: String)
data class P2pPaymentRequestDto(
    val id: String,
    val requesterUserId: String,
    val amount: java.math.BigDecimal,
    val description: String,
    val status: String,
    val expiresAt: String,
    val completedTransactionId: String? = null,
    val paidByUserId: String? = null,
    val createdAt: String,
)
data class GenerateP2pRequestResponse(val success: Boolean, val request: P2pPaymentRequestDto)
data class GetP2pRequestsResponse(val success: Boolean, val requests: List<P2pPaymentRequestDto>)
data class PayP2pRequestResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)

// Real Toss Bank 자동이체 (auto-transfer) equivalent -- mirrors AutoTransferController's
// real DTOs exactly. See AutoTransfer.kt's own doc comment on the backend for why
// execution reuses sendDirect's exact ledger movement rather than a separate rail.
enum class AutoTransferFrequency { WEEKLY, MONTHLY }
data class AutoTransferDto(
    val id: String,
    val recipientIdentifier: String,
    val recipientName: String,
    val amount: java.math.BigDecimal,
    val frequency: AutoTransferFrequency,
    val dayOfWeek: Int?,
    val dayOfMonth: Int?,
    val description: String,
    val status: String,
    val nextExecutionAt: String,
    val lastExecutedAt: String?,
    val executionCount: Int,
    val lastFailureReason: String?,
)
data class CreateAutoTransferRequest(
    val recipient: String,
    val amount: java.math.BigDecimal,
    val frequency: AutoTransferFrequency,
    val dayOfWeek: Int? = null,
    val dayOfMonth: Int? = null,
    val description: String = "",
)
data class AutoTransferResponse(val success: Boolean, val autoTransfer: AutoTransferDto)
data class AutoTransfersListResponse(val success: Boolean, val autoTransfers: List<AutoTransferDto>)

// Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see ScheduledTransfer.kt's
// own doc comment on the backend for the full sourced account. Genuinely distinct from
// AutoTransfer above: explicitly ONE-TIME on a single future date, not recurring. Had
// zero client anywhere until now despite being fully built (ScheduledTransferController,
// a real scheduler executing it via P2pService.sendDirect). Mirrors
// ScheduledTransferController's real DTOs exactly.
data class ScheduledTransferDto(
    val id: String,
    val recipientIdentifier: String,
    val recipientName: String,
    val amount: java.math.BigDecimal,
    val description: String,
    val scheduledDate: String,
    val status: String,
    val createdAt: String,
    val executedAt: String?,
    val transactionId: String?,
    val failureReason: String?,
    val cancelledAt: String?,
)
data class CreateScheduledTransferRequest(
    val recipient: String,
    val amount: java.math.BigDecimal,
    val scheduledDate: String,
    val description: String = "",
)
data class ScheduledTransferResponse(val success: Boolean, val scheduledTransfer: ScheduledTransferDto)
data class ScheduledTransfersListResponse(val success: Boolean, val scheduledTransfers: List<ScheduledTransferDto>)

// Real Korean 지연이체서비스 (Delayed Transfer Service) -- see backend
// P2pDelayedTransfer.kt's own doc comment for the full sourced account (a real,
// government-documented anti-voice-phishing safeguard every major Korean bank offers).
// Genuinely distinct from ScheduledTransfer above (a user-chosen FUTURE send date):
// this is a SAFETY delay on a transfer the sender wants to send right now. Already
// shipped on iOS (DelayedTransferListScreen.swift) and web (DelayedTransfersCard in
// PayTransferCards.tsx) -- Android had zero client anywhere until now despite the
// backend being fully built. Mirrors P2pController's real DTOs exactly.
enum class DelayedTransferStatus { PENDING, COMPLETED, CANCELLED }
data class DelayedTransferDto(
    val id: String,
    val senderUserId: String,
    val recipientUserId: String,
    val amount: java.math.BigDecimal,
    val description: String,
    val status: DelayedTransferStatus,
    val releaseAt: String,
    val createdAt: String,
)
data class SendDelayedTransferRequest(val recipient: String, val amount: java.math.BigDecimal, val description: String = "")
data class DelayedTransferResponse(val success: Boolean, val transfer: DelayedTransferDto)
data class DelayedTransfersListResponse(val success: Boolean, val transfers: List<DelayedTransferDto>)

// Real Toss 사기계좌 조회 (fraud-account lookup before transfer) -- see backend
// ScamReportService's own doc comment. itunda's own crowd-sourced report registry,
// not a real police-database integration. Real on bank-mfe only until now (2026-07-31).
data class ScamReportDto(val id: String, val reporterId: String, val reportedIdentifier: String, val reason: String, val createdAt: String)
data class ScamCheckResultDto(val identifier: String, val reportCount: Int, val warn: Boolean)
data class ReportScamRequest(val identifier: String, val reason: String)
data class ScamCheckResponse(val success: Boolean, val result: ScamCheckResultDto)
data class ScamReportResponse(val success: Boolean, val report: ScamReportDto)
data class ScamReportsListResponse(val success: Boolean, val reports: List<ScamReportDto>)

// Real Toss/Kakao Bank-style recipient-name confirmation ("받는분 성함 확인") -- see
// P2pService.resolveRecipient's own doc comment. Resolves a phone/account identifier to
// the real account holder's name before the amount screen renders it, matching the
// reference Toss screenshots' "To [name]" display. Already had a real web client
// (bank-mfe's lib/p2p.ts resolveRecipient) but no Android client at all until this
// (2026-08-23, found while porting the same real Toss "Sent" success-screen reference
// screenshot to Android) -- meaning Android's transfer flow only ever showed the raw
// account number, never a resolved name, throughout Recipient/Amount/Success.
data class P2pRecipientPreviewDto(val recipientUserId: String, val displayName: String)
data class ResolveRecipientResponse(val success: Boolean, val recipient: P2pRecipientPreviewDto)

// Real Toss Bank "Transfer limit" row (2026-09-01) -- mirrors web's lib/p2p.ts
// fetchTransferLimit / backend P2pController's GET /api/v1/p2p/transfer-limit.
data class TransferLimitResponse(
    val success: Boolean,
    val perTransferLimit: Double,
    val dailyLimit: Double,
    val remainingToday: Double,
)

// Mirrors services/backend/savings's SavingsController.kt.
data class DepositRequest(val goalId: String, val amount: java.math.BigDecimal, val fromAccountId: String? = null)
data class DepositResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
// Real gap found live (2026-08-31, direct user re-reference of the real Toss
// "얼마나 꺼낼까요?" (withdraw) screenshot) -- see backend SavingsService
// .withdrawFromGoal's own doc comment for the full account.
data class WithdrawRequest(val goalId: String, val amount: java.math.BigDecimal, val toAccountId: String? = null)
data class WithdrawResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
data class ClaimInterestResponse(val success: Boolean, val message: String, val claimed: Double? = null)

// Real Kakao Pay 머니굴리기 (round-up auto-save) equivalent (2026-07-25) -- see
// RoundUpSettings.kt's own doc comment. P2P transfers only in this v1, not every
// payment flow -- see RoundUpService.kt's processRoundUp doc comment.
data class RoundUpSettingsDto(
    val id: String,
    val userId: String,
    val enabled: Boolean,
    val roundToNearest: java.math.BigDecimal,
    val targetGoalId: String?,
    // Real stock-destination option (backend since 2026-07-27,
    // RoundUpService.processRoundUp's own targetStockId branch) -- wired into
    // this client (Wealth product-completeness pass, 2026-09-06). Mutually
    // exclusive with targetGoalId, enforced server-side.
    val targetStockId: String? = null,
)
data class RoundUpSettingsResponse(val success: Boolean, val settings: RoundUpSettingsDto?)
data class SetRoundUpSettingsRequest(
    val enabled: Boolean,
    val roundToNearest: java.math.BigDecimal,
    val targetGoalId: String? = null,
    val targetStockId: String? = null,
)

data class CreateAgentWithdrawalAuthorizationRequest(val amount: java.math.BigDecimal)
data class CancelAgentWithdrawalAuthorizationRequest(val code: String)
data class AgentWithdrawalAuthorizationDto(val id: String, val code: String, val amount: java.math.BigDecimal, val expiresAt: String, val status: String, val createdAt: String)
data class AgentWithdrawalAuthorizationResponse(val success: Boolean, val authorization: AgentWithdrawalAuthorizationDto)
data class AgentWithdrawalAuthorizationsResponse(val success: Boolean, val authorizations: List<AgentWithdrawalAuthorizationDto>)

// Mirrors services/backend/notifications's NotificationController.kt (2026-07-12) --
// backs the Settings screen's notifications list.
data class NotificationDto(
    val id: String,
    val userId: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: String,
)

data class NotificationsResponse(val success: Boolean, val notifications: List<NotificationDto>, val unreadCount: Int)
data class MarkReadResponse(val success: Boolean)

// Mirrors services/backend/partners's PartnerMiniApp entity exactly (field names match
// its real Jackson-serialized JSON) -- the real "app store" catalog a mobile Saronite
// host client fetches to know which third-party mini-apps are approved and available
// (2026-07-17, closing the mobile half of the Partner SDK gap named in
// docs/TOSS_PARITY_MATRIX.md's Partner SDK row). `permissions` is the same real
// comma-joined scope string PartnerService.submitMiniApp stored at submission time
// (validated then against PartnerMiniAppPermissions.ALLOWED) -- split on "," client-side
// by MiniAppSecurityContext before a partner bundle is actually loaded.
