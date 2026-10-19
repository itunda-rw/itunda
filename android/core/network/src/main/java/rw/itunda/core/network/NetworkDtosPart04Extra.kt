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

data class FloatTransferRequestDto(
    val id: String, val listingId: String, val requestingAgentId: String, val amount: java.math.BigDecimal,
    val status: String, val transactionId: String?, val createdAt: String,
)
data class PostFloatListingRequest(val amount: java.math.BigDecimal)
data class RequestFloatRequest(val amount: java.math.BigDecimal)
data class FloatListingResponse(val success: Boolean, val listing: FloatListingDto)
data class FloatListingsResponse(val success: Boolean, val listings: List<FloatListingDto>)
data class NearbyFloatListingsResponse(val success: Boolean, val listings: List<NearbyFloatListingDto>)
data class FloatTransferRequestResponse(val success: Boolean, val request: FloatTransferRequestDto)
data class FloatTransferRequestsResponse(val success: Boolean, val requests: List<FloatTransferRequestDto>)

// Real digital identity/signing certificate (rw.itunda.certificate) -- already real
// and wired into bank-mfe (web) since 2026-07-17, but found 2026-07-22 completely
// absent from the Android app, a platform-parity gap rather than a never-built
// feature. Mirrors bank-mfe's lib/certificate.ts field-for-field.
data class CertificateDto(
    val id: String, val userId: String, val serialNumber: String, val publicKeyBase64: String,
    val algorithm: String, val status: String, val issuedAt: String, val expiresAt: String, val revokedAt: String?,
)
data class IssueCertificateResponse(val success: Boolean, val certificate: CertificateDto, val privateKey: String)
data class MyCertificateResponse(val success: Boolean, val certificate: CertificateDto?)
data class RevokeCertificateResponse(val success: Boolean, val certificate: CertificateDto)
data class CertificateStatusResponse(val success: Boolean, val certificate: CertificateDto)
data class VerifyCertificateSignatureRequest(val serialNumber: String, val payload: String, val signature: String)
data class VerifyCertificateSignatureResponse(
    val success: Boolean, val signatureValid: Boolean, val certificateStatus: String, val userId: String, val serialNumber: String,
)

// Real KYC identity submission (rw.itunda.identity) -- found 2026-07-22 fully built on
// the backend with zero client UI anywhere. documentReference is a real, honest
// demo-mode stand-in for an uploaded ID scan/selfie (no file-storage layer exists in
// this backend, see KycSubmission.kt's own doc comment) -- a free-text reference
// string, not an actual image upload. documentType must be NATIONAL_ID or PASSPORT for
// a personal submission (BUSINESS_TIN/KYB is a separate merchant-onboarding concern,
// out of scope for this consumer-app screen).
data class SubmitIdentityRequest(val documentType: String, val documentNumber: String, val documentReference: String)
data class KycSubmissionDto(
    val id: String, val userId: String, val documentType: String, val documentNumber: String, val documentReference: String,
    val status: String, val submittedAt: String, val reviewedBy: String?, val reviewedAt: String?, val decisionReason: String?,
    val autoVerificationStatus: String?, val autoVerificationDetail: String?,
)
data class SubmitIdentityResponse(val success: Boolean, val submission: KycSubmissionDto)
data class IdentityStatusResponse(val success: Boolean, val submissions: List<KycSubmissionDto>)

// Real KakaoPay-style "정산하기" (chat-embedded split-bill), rw.itunda.splitbill --
// found 2026-07-22 fully built on the backend, tied to an existing real group
// conversation, with zero client UI anywhere despite group chat itself being fully
// wired. See SplitBill.kt's own doc comment: a flat, even split with the rounding
// remainder silently absorbed into one participant's share so shares always sum
// exactly to totalAmount; each participant pays their own share directly to the
// organizer via a real account-to-account push, no escrow.
data class CreateSplitBillRequest(
    val totalAmount: java.math.BigDecimal, val description: String, val participantUserIds: List<String>,
    // Real KakaoPay 사다리타기 (ladder-game) mode (2026-07-25) -- see backend
    // SplitBillService.ladderSplit's own doc comment. Omit (or "EVEN") for the
    // unchanged flat-split v1 behavior.
    val mode: String = "EVEN",
    val ladderVarianceLevel: Int? = null,
)
data class SplitBillDto(
    val id: String, val organizerId: String, val groupConversationId: String, val messageId: String,
    val totalAmount: java.math.BigDecimal, val description: String, val status: String,
    val settledAt: String?, val createdAt: String,
    val mode: String = "EVEN", val ladderVarianceLevel: Int? = null,
    // Real photo receipt attach (2026-07-28) -- see backend SplitBillService.attachReceipt's
    // own doc comment. null means no receipt attached yet.
    val receiptImageUrl: String? = null,
    // Real up-to-5 sequential settlement round counter (2026-07-28) -- see backend
    // SplitBillService.requestNextRound's own doc comment. Starts at 1.
    val currentRound: Int = 1,
)
data class AttachSplitBillReceiptRequest(val imageUrl: String)
// Distinct from SplitBillResponse -- attachReceipt/requestNextRound's controller
// responses carry only {success, splitBill}, no participants key.
data class SplitBillOnlyResponse(val success: Boolean, val splitBill: SplitBillDto)
data class SplitBillParticipantDto(
    val id: String, val splitBillId: String, val userId: String, val shareAmount: java.math.BigDecimal,
    val status: String, val paidTransactionId: String?, val paidAt: String?, val createdAt: String,
)
data class CreateSplitBillResponse(val success: Boolean, val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillResponse(val success: Boolean, val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillWithParticipants(val splitBill: SplitBillDto, val participants: List<SplitBillParticipantDto>)
data class SplitBillsForGroupResponse(val success: Boolean, val splitBills: List<SplitBillWithParticipants>)
data class PaySplitBillShareResponse(val success: Boolean, val participant: SplitBillParticipantDto)

// Real 1:1-chat split-bill request (2026-08-09) -- no participantUserIds field, unlike
// CreateSplitBillRequest above: the other person is fixed by the {otherUserId} path
// segment, since this endpoint is for exactly two people, not an existing group. See
// backend SplitBillController's CreateDirectSplitBillRequest's own doc comment.
data class CreateDirectSplitBillRequest(
    val totalAmount: java.math.BigDecimal, val description: String,
    val mode: String = "EVEN", val ladderVarianceLevel: Int? = null,
)

// Real saved-contacts list for quick transfers (rw.itunda.contacts) -- found
// 2026-07-22 fully built on the backend (a real IDOR fix ported from the original
// Express controller, see ContactsController.kt's own doc comment) with zero client
// UI anywhere. RecipientEntryScreen.kt's "Recent" row was a single hardcoded demo
// name ("TUYIZERE Eric"), not backed by any real data.
data class AddContactRequest(val name: String, val bank: String? = null, val phoneNumber: String)
data class ContactDto(val id: String, val userId: String, val name: String, val bank: String, val acc: String, val phoneNumber: String, val color: String, val letter: String)
data class ContactsResponse(val success: Boolean, val contacts: List<ContactDto>)
data class AddContactResponse(val success: Boolean, val contact: ContactDto)

// Real KakaoBank 26주적금-style escalating 26-week savings plan (2026-07-21) -- the
// first mobile UI this feature has ever had; see WeeklySavingsController.kt/
// WeeklySavingsService.kt and the domain entities they wrap
// (services/backend/core/.../domain/WeeklySavingsPlan.kt / WeeklySavingsInstallment.kt)
// for the real ledger-backed mechanics. TERM_WEEKS=26 and ESCALATION_STEP_WEEKS=4 are
// display-only constants mirrored from WeeklySavingsService.kt below -- no endpoint
// exposes them since they never change.
data class WeeklySavingsPlanDto(
    val id: String,
    val userId: String,
    val accountId: String,
    val name: String,
    val baseWeeklyAmount: Double,
    val escalationRate: Double,
    val openingWeekday: Int,
    val baseRate: Double,
    val bonusRate: Double,
    val installmentsCollected: Int,
    val weeksElapsed: Int,
    val currentAmount: Double,
    val streakBroken: Boolean,
    val status: String,
    val nextInstallmentDueAt: String,
    val createdAt: String,
    val maturedAt: String? = null,
    val cancelledAt: String? = null,
    val withdrawnAt: String? = null,
    val totalInterestPaid: Double? = null,
)

data class WeeklySavingsInstallmentDto(
    val id: String,
    val planId: String,
    val weekNumber: Int,
    val amount: Double,
    val depositedAt: String,
)

data class WeeklySavingsPlansResponse(val success: Boolean, val plans: List<WeeklySavingsPlanDto>)

data class WeeklySavingsPlanDetailResponse(
    val success: Boolean,
    val plan: WeeklySavingsPlanDto,
    val accountBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

data class CreateWeeklySavingsPlanRequest(
    val name: String,
    val baseWeeklyAmount: java.math.BigDecimal,
    val escalationRate: java.math.BigDecimal,
)

// POST /plans's real response shape is just {success, plan} -- unlike get/cancel/
// withdraw it never returns accountBalance/installments (a brand-new plan's account is
// always empty and has no installments yet), so this gets its own response type
// rather than reusing WeeklySavingsPlanDetailResponse with fields that would silently
// come back null/0.0 via Gson's reflection-based construction.
data class CreateWeeklySavingsPlanResponse(val success: Boolean, val plan: WeeklySavingsPlanDto)

data class WeeklySavingsActionResponse(
    val success: Boolean,
    val message: String,
    val plan: WeeklySavingsPlanDto,
    val accountBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

// Real Toss Bank 키워봐요 31일적금 (Grow-it 31-day savings) equivalent (2026-08-12) --
// see backend's Grow31SavingsPlan.kt doc comment for the full sourced mechanics: a real
// once-per-calendar-day USER-triggered deposit (not an auto-debit) over a fixed 31-day
// term, with a bonus rate keyed on the longest unbroken daily-deposit streak reached
// (base 1%, +3/+4/+6/+8/+10 at a 3/7/14/21/31-day streak). TERM_DAYS=31 is a
// display-only constant mirrored from Grow31SavingsService.kt -- no endpoint exposes it
// since it never changes.
data class Grow31SavingsPlanDto(
    val id: String,
    val userId: String,
    val accountId: String,
    val name: String,
    val dailyAmount: Double,
    val startDate: String,
    val daysElapsed: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    val lastDepositDate: String? = null,
    val totalSaved: Double,
    val baseRate: Double,
    val status: String,
    val createdAt: String,
    val maturedAt: String? = null,
    val cancelledAt: String? = null,
    val withdrawnAt: String? = null,
    val totalInterestPaid: Double? = null,
)

data class Grow31SavingsDepositDto(
    val id: String,
    val planId: String,
    val dayNumber: Int,
    val depositDate: String,
    val amount: Double,
    val streakAtDeposit: Int,
    val depositedAt: String,
)

data class Grow31SavingsPlansResponse(val success: Boolean, val plans: List<Grow31SavingsPlanDto>)

data class Grow31SavingsPlanDetailResponse(
    val success: Boolean,
    val plan: Grow31SavingsPlanDto,
    val accountBalance: Double,
    val deposits: List<Grow31SavingsDepositDto>,
)

data class CreateGrow31SavingsPlanRequest(val name: String, val dailyAmount: java.math.BigDecimal)

// POST /plans's real response shape is just {success, plan} -- same "brand-new plan has
// no account balance/deposits yet" reasoning CreateWeeklySavingsPlanResponse's own doc
// comment names.
data class CreateGrow31SavingsPlanResponse(val success: Boolean, val plan: Grow31SavingsPlanDto)

data class Grow31SavingsActionResponse(
    val success: Boolean,
    val message: String? = null,
    val plan: Grow31SavingsPlanDto,
    val accountBalance: Double,
    val deposits: List<Grow31SavingsDepositDto>,
)

// Retrofit Interface to map to your Spring endpoints -- all require the real
// Bearer token NetworkClient's authInterceptor now injects (2026-07-11).

data class UpfrontDepositDto(
    val id: String, val userId: String, val accountId: String, val principal: Double,
    val interestRate: Double, val interestPaid: Double, val status: String,
    val openedAt: String, val maturesAt: String, val maturedAt: String? = null, val withdrawnAt: String? = null,
)
data class OpenUpfrontDepositRequest(val principal: java.math.BigDecimal)
data class UpfrontDepositResponse(val success: Boolean, val deposit: UpfrontDepositDto, val message: String? = null)
data class UpfrontDepositsResponse(val success: Boolean, val deposits: List<UpfrontDepositDto>)

data class TransactionHistoryResponse(val success: Boolean, val transactions: List<TransactionDto>)

// Real Kakao T-style ride-hailing -- mirrors RideDriver.kt/RideTrip.kt exactly.
data class RideDriverDto(
    val id: String, val userId: String, val accountId: String, val status: String, val available: Boolean,
    val currentLatitude: Double?, val currentLongitude: Double?, val locationUpdatedAt: String?,
    // Real Uber "Destination Filter" (item 246) -- see ApiService's own
    // setRideDriverDestination doc comment.
    val destinationLatitude: Double? = null, val destinationLongitude: Double? = null,
    // Real gap found live (2026-08-31, market-readiness audit) -- see
    // RegisterRideDriverRequest's own doc comment.
    val licenseNumber: String = "",
)
data class RideDriverResponse(val success: Boolean, val driver: RideDriverDto)
// Real gap found live (2026-08-31, market-readiness audit): this, the biggest and most
// central driver-role registration in the backend, had zero identity/license info at
// all -- see backend RideDriverService.kt's own doc comment for the full account. An
// honest, self-declared informational text field, not a real license-verification gate
// this backend has no path to check.
data class RegisterRideDriverRequest(val licenseNumber: String)
data class SetRideDriverAvailabilityRequest(val available: Boolean)
data class SetRideDriverDestinationRequest(val latitude: Double, val longitude: Double)
data class RideDailyEarnings(val date: String, val tripCount: Int, val grossFare: java.math.BigDecimal, val platformFees: java.math.BigDecimal, val netEarnings: java.math.BigDecimal)
data class RideEarningsResponse(val success: Boolean, val from: String, val to: String, val days: List<RideDailyEarnings>)
data class UpdateRideDriverLocationRequest(val latitude: Double, val longitude: Double)
