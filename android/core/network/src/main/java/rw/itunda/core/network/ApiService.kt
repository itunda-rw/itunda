package rw.itunda.core.network

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
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
data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
    val referralCode: String? = null,
    // Added 2026-07-21, same reasoning as LoginRequest's deviceId/deviceName -- the
    // device that registers proves password ownership in the same request, so it's
    // auto-trusted server-side (DeviceService.recordRegistrationDevice) with no
    // separate step-up needed.
    val deviceId: String? = null,
    val deviceName: String? = null,
)

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding login
// call exactly (lib/api.ts's login()). See DeviceStore.kt for how these are generated.
data class LoginRequest(val phoneNumber: String, val password: String, val deviceId: String? = null, val deviceName: String? = null)
data class RefreshRequest(val refreshToken: String)
data class LogoutRequest(val refreshToken: String?)

data class PublicUser(
    val id: String,
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val kycVerified: Boolean,
    val creditScore: Int,
    val createdAt: String,
    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. Set via a real coordinate, reverse-geocoded server-side; never
    // self-declared free text.
    val neighborhood: String? = null,
    val neighborhoodVerifiedAt: String? = null,
    val neighborhoodVerificationCount: Int = 0,
    // Real dual-neighborhood support (2026-08-04) -- see AuthService.setSecondNeighborhood's
    // own doc comment. Same real reverse-geocode-only provenance as neighborhood above.
    val secondNeighborhood: String? = null,
    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // MiniWalletService.kt's own doc comment. Set via AuthApi.setBirthDate.
    val birthDate: String? = null,
    // Real email/phone verification (item 169/178) -- see AuthService.requestEmailVerification/
    // requestPhoneVerification's own doc comments. Backend has returned these on every
    // profile response since 2026-07-13/26; this app just never modeled them until now.
    val emailVerified: Boolean = false,
    val phoneVerified: Boolean = false,
    // Real profile photo (URL, not a binary upload) -- also the real, buildable half
    // of Rewards' task_profile. Found 2026-07-29 via a full-backend-endpoint sweep:
    // real, working endpoint with zero client anywhere, and this field wasn't even
    // carried by this DTO until now.
    val profilePhotoUrl: String? = null,
)

data class UpdateProfilePhotoRequest(val profilePhotoUrl: String)

data class AuthResponse(
    val message: String,
    val user: PublicUser,
    val accessToken: String,
    val refreshToken: String,
)

// Real login/session flow (2026-07-11) -- see TokenStore.kt and SessionManager.kt.
// Register/login/refresh are unauthenticated per SecurityConfig.kt's permitAll list;
// logout requires the access token being revoked, passed explicitly rather than via
// the auth interceptor so it's unambiguous which token is being killed.
interface AuthApi {
    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    @POST("api/v1/auth/logout")
    suspend fun logout(@Header("Authorization") bearerAccessToken: String, @Body request: LogoutRequest)

    // Real account settings screen (2026-07-12) -- backs the "내 정보" section of
    // the new Settings screen.
    @GET("api/v1/auth/profile")
    suspend fun getProfile(): ProfileResponse

    // Real hyperlocal neighborhood (2026-07-20) -- see AuthService.setNeighborhood's own
    // doc comment. A real coordinate in, reverse-geocoded server-side into a real
    // neighborhood/sector name -- see lib/neighborhood.ts's bank-mfe equivalent this
    // mirrors exactly.
    @POST("api/v1/auth/profile/neighborhood")
    suspend fun setNeighborhood(@Body request: SetNeighborhoodRequest): ProfileResponse

    // Real dual-neighborhood support (2026-08-04) -- see AuthService.setSecondNeighborhood's
    // own doc comment. Browse endpoints (marketplace/community/jobs/property my-neighborhood)
    // automatically include this once set -- no separate client call needed to opt in.
    @POST("api/v1/auth/profile/second-neighborhood")
    suspend fun setSecondNeighborhood(@Body request: SetNeighborhoodRequest): ProfileResponse

    @DELETE("api/v1/auth/profile/second-neighborhood")
    suspend fun clearSecondNeighborhood(): ProfileResponse

    // Real profile photo (URL, not a binary upload) -- see PublicUser.profilePhotoUrl's
    // own doc comment.
    @PUT("api/v1/auth/profile/photo")
    suspend fun updateProfilePhoto(@Body request: UpdateProfilePhotoRequest): ProfileResponse

    // Real age-eligibility gate for the Mini wallet (2026-07-28) -- see
    // AuthService.setBirthDate's own doc comment. birthDate is an ISO-8601 date
    // string ("YYYY-MM-DD").
    @POST("api/v1/auth/profile/birth-date")
    suspend fun setBirthDate(@Body request: SetBirthDateRequest): ProfileResponse

    // Real device binding (2026-07-21 port) -- mirrors bank-mfe's lib/device.ts
    // fetchMyDevices/verifyDevice/revokeDevice exactly (same real endpoints, same
    // shapes). See AuthController.kt on the backend for the real contract: verify
    // always re-verifies the CURRENT device (resolved server-side from the caller's
    // own JWT deviceId claim, never a client-supplied one), so no id is passed here.
    @GET("api/v1/auth/devices")
    suspend fun getMyDevices(): DevicesResponse

    @POST("api/v1/auth/devices/verify")
    suspend fun verifyDevice(@Body request: VerifyDeviceRequest): VerifyDeviceResponse

    @DELETE("api/v1/auth/devices/{deviceId}")
    suspend fun revokeDevice(@Path("deviceId") deviceId: String): RevokeDeviceResponse

    // Real Keystore-signed-challenge device verification (item 246) -- see
    // AuthController.kt's own doc comments on the backend for the exact contract. Same
    // password re-proof as verifyDevice above, plus a hardware-backed key generated by
    // DeviceKeyManager -- makes every FUTURE step-up a biometric prompt instead of
    // retyping a password.
    @POST("api/v1/auth/devices/register-key")
    suspend fun registerDeviceKey(@Body request: RegisterDeviceKeyRequest): VerifyDeviceResponse

    @POST("api/v1/auth/devices/challenge")
    suspend fun issueDeviceChallenge(): DeviceChallengeResponse

    @POST("api/v1/auth/devices/verify-signature")
    suspend fun verifyDeviceSignature(@Body request: VerifyDeviceSignatureRequest): VerifyDeviceResponse

    // Real push device-token registration (item 120) -- see backend DeviceToken.kt's
    // own doc comment: PushNotificationService.sendToUser silently no-ops for every
    // real user because no client anywhere ever registered a token. This app has no
    // real FCM SDK integrated, so reuses the same real, stable per-install device id
    // DeviceStore already established for trusted-device binding as this demo's
    // client-generated token, mirrors bank-mfe's registerDeviceToken exactly (item 119).
    @POST("api/v1/notifications/device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): SuccessResponse

    // Real push unregister-on-logout (item 232) -- found via a defined-but-uncalled-
    // endpoint sweep, see backend DeviceTokenController.unregister's own doc comment.
    // bank-mfe shipped this first (2026-08-05); this is the Android port. Explicit
    // Authorization header, same discipline as `logout` above -- called from
    // SessionManager.logout() before tokenStore.clearSession(), so the interceptor
    // would otherwise have nothing to attach.
    @DELETE("api/v1/notifications/device-tokens/{token}")
    suspend fun unregisterDeviceToken(@Header("Authorization") bearerAccessToken: String, @Path("token") token: String): SuccessResponse

    // Real email/phone verification (item 169/178) -- see AuthService.requestEmailVerification/
    // requestPhoneVerification's own doc comments: a real code is delivered via a real
    // in-app Notification + push, no real SMS/email gateway exists. bank-mfe (item 169)
    // already has this; this is the first Android client.
    @POST("api/v1/auth/profile/verify-email")
    suspend fun requestEmailVerification(): SuccessResponse

    @POST("api/v1/auth/profile/verify-email/confirm")
    suspend fun confirmEmailVerification(@Body request: ConfirmEmailVerificationRequest): ProfileResponse

    @POST("api/v1/auth/profile/verify-phone")
    suspend fun requestPhoneVerification(): SuccessResponse

    @POST("api/v1/auth/profile/verify-phone/confirm")
    suspend fun confirmPhoneVerification(@Body request: ConfirmPhoneVerificationRequest): ProfileResponse
}

enum class DevicePlatform { ANDROID, IOS, WEB }
data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)

data class ProfileResponse(val success: Boolean, val user: PublicUser)
data class SetNeighborhoodRequest(val latitude: Double, val longitude: Double)
data class SetBirthDateRequest(val birthDate: String)
data class ConfirmEmailVerificationRequest(val token: String)
data class ConfirmPhoneVerificationRequest(val code: String)

// Mirrors services/backend/core/.../domain/TrustedDevice.kt exactly.
data class TrustedDeviceDto(
    val id: String,
    val userId: String,
    val deviceId: String,
    val deviceName: String?,
    val trusted: Boolean,
    val firstSeenAt: String,
    val lastSeenAt: String,
    val verifiedAt: String?,
    val publicKey: String? = null,
)

data class DevicesResponse(val success: Boolean, val devices: List<TrustedDeviceDto>)
data class VerifyDeviceRequest(val password: String)
data class VerifyDeviceResponse(val success: Boolean, val device: TrustedDeviceDto)
data class RevokeDeviceResponse(val success: Boolean)
data class RegisterDeviceKeyRequest(val publicKey: String, val password: String)
data class DeviceChallengeResponse(val success: Boolean, val challenge: String)
data class VerifyDeviceSignatureRequest(val signature: String)

// Mirrors services/backend/core/.../domain/Wallet.kt exactly (2026-07-11 fix) --
// the previous shape (currency/balance/isPrimary only) didn't match the real
// backend's serialized Wallet entity at all -- there is no "isPrimary" field on
// the real backend, so `wallets.firstOrNull { it.isPrimary }` silently always
// returned null and fell through to whatever wallet happened to be first, not
// actually the primary one. `type == "MAIN"` is the real signal.
data class Wallet(
    val id: String,
    val userId: String,
    val accountNumber: String,
    val accountName: String,
    val type: String,
    val balance: Double,
    val availableBalance: Double,
    val currency: String,
    val isActive: Boolean,
)

data class WalletResponse(
    val success: Boolean,
    val wallets: List<Wallet>
)

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
// rw.itunda.wallet.ForeignCurrencyWalletService on the backend for the full account,
// incl. why USD/EUR/GBP specifically (real Rwandan diaspora remittance corridors) and
// why this is real-rate conversion between a user's own wallets, not a cross-border
// receiving rail.
data class OpenForeignWalletRequest(val currency: String)
data class ForeignWalletResponse(val success: Boolean, val wallet: Wallet)
data class ForeignWalletsResponse(val success: Boolean, val wallets: List<Wallet>)
data class ExchangeRateResponse(val success: Boolean, val from: String, val to: String, val rate: Double)
data class ConvertCurrencyRequest(val fromCurrency: String, val toCurrency: String, val amount: Double)
data class CurrencyConversionDto(
    val id: String, val userId: String, val fromCurrency: String, val toCurrency: String,
    val fromAmount: Double, val toAmount: Double, val rate: Double, val marginAmount: Double,
    val transactionId: String, val createdAt: String,
)
data class CurrencyConversionResponse(val success: Boolean, val conversion: CurrencyConversionDto)
data class CurrencyConversionsResponse(val success: Boolean, val conversions: List<CurrencyConversionDto>)

// Mirrors services/backend/core/.../domain/SavingsGoal.kt / InterestJar.kt.
data class SavingsGoal(
    val id: String,
    val userId: String,
    val walletId: String,
    val name: String,
    val targetAmount: Double,
    val currentAmount: Double,
    val monthlyContribution: Double,
    val interestRate: Double,
    val targetDate: String?,
    val category: String,
    val status: String,
    val color: String,
)

data class SavingsGoalsResponse(val success: Boolean, val goals: List<SavingsGoal>)

data class InterestJar(
    val userId: String,
    val walletId: String,
    val balance: Double,
    val rate: Double,
    val earnedThisMonth: Double,
    val earnedTotal: Double,
)

data class InterestJarResponse(val success: Boolean, val jar: InterestJar)

data class DiscoverItem(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val description: String,
    val color: String,
    val isNew: Boolean,
    val badge: String?
)

data class DiscoverResponse(
    val success: Boolean,
    val items: List<DiscoverItem>
)

// Mirrors services/backend/wallet's WalletController/TransferQuote.kt exactly
// (2026-07-12) -- the real transfer flow (RecipientEntryScreen/TransferAmountScreen
// in :features:payments:impl) was UI-only until now; these are what wire it to the
// actual quoteTransfer/confirmTransfer endpoints.
data class QuoteTransferRequest(val amount: java.math.BigDecimal, val recipient: String, val fromWalletId: String? = null, val description: String? = null)

data class TransferQuoteDto(
    val id: String,
    val fromWalletId: String,
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

// Real direct itunda-to-itunda push-transfer (rw.itunda.p2p, 2026-07-20) -- mirrors
// P2pController's real SendDirectP2pRequest exactly. Deliberately distinct from
// QuoteTransferRequest/ConfirmTransferRequest above: those always route through a
// simulated external rail and never actually credit another itunda user's wallet, even
// when the recipient is a real itunda account (confirmed via a direct MySQL check while
// building this on the backend/bank-mfe side one day earlier). This is the real one --
// no quote step needed, since there's no external rail decision to quote.
data class SendDirectP2pRequest(val recipient: String, val amount: java.math.BigDecimal, val description: String = "")
data class SendDirectP2pResponse(val success: Boolean, val message: String, val transaction: TransactionDto, val newBalance: Double)

// Real fixed-amount person-to-person payment request (item 170) -- the P2P counterpart
// to a merchant's own PaymentIntent (see backend P2pPaymentRequest.kt's own doc
// comment). A real 15-minute-expiring code the requester shares; anyone who has the
// code can pay it directly, real wallet-to-wallet, no fee. Real (rate-limited, tested,
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

// Real Toss 사기계좌 조회 (fraud-account lookup before transfer) -- see backend
// ScamReportService's own doc comment. itunda's own crowd-sourced report registry,
// not a real police-database integration. Real on bank-mfe only until now (2026-07-31).
data class ScamReportDto(val id: String, val reporterId: String, val reportedIdentifier: String, val reason: String, val createdAt: String)
data class ScamCheckResultDto(val identifier: String, val reportCount: Int, val warn: Boolean)
data class ReportScamRequest(val identifier: String, val reason: String)
data class ScamCheckResponse(val success: Boolean, val result: ScamCheckResultDto)
data class ScamReportResponse(val success: Boolean, val report: ScamReportDto)
data class ScamReportsListResponse(val success: Boolean, val reports: List<ScamReportDto>)

// Mirrors services/backend/savings's SavingsController.kt.
data class DepositRequest(val goalId: String, val amount: java.math.BigDecimal, val fromWalletId: String? = null)
data class DepositResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
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
)
data class RoundUpSettingsResponse(val success: Boolean, val settings: RoundUpSettingsDto?)
data class SetRoundUpSettingsRequest(val enabled: Boolean, val roundToNearest: java.math.BigDecimal, val targetGoalId: String? = null)

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
data class PartnerMiniAppDto(
    val id: String,
    val partnerId: String,
    val name: String,
    val description: String,
    val iconUrl: String?,
    val bundleUrl: String,
    val permissions: String,
    val status: String,
    val createdAt: String,
)

data class MiniAppCatalogResponse(val success: Boolean, val miniApps: List<PartnerMiniAppDto>)

// Mirrors services/backend/messaging's real DTOs exactly (2026-07-18) -- backs the new
// "Talk" bottom-nav tab (Kakao-style 1:1 chat). See rw.itunda.messaging.MessagingService
// / MessagingController's own doc comments for the full backend account, including the
// honest "poll-based delivery, no live transport yet" scope this mobile client matches.
data class ConversationDto(
    val id: String,
    val participantAId: String,
    val participantBId: String,
    val lastMessageAt: String,
    val createdAt: String,
)

// What GET /api/v1/messages/conversations actually returns per row -- a different,
// flatter shape than ConversationDto above (MessagingService.ConversationSummary).
data class ConversationSummaryDto(
    val conversationId: String,
    val otherUserId: String,
    val otherUserName: String,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Int,
    val quiet: Boolean = false,
    val pinnedMessageId: String? = null,
    // Real recoverable archive (2026-08-05) -- see backend ConversationPreference
    // .archived's own doc comment. Same private-to-me model as quiet.
    val archived: Boolean = false,
)

// Real emoji reactions (2026-07-19) -- see MessagingService.toggleReaction's own doc
// comment for the real toggle semantics (tapping an active reaction removes it).
data class ReactionGroupDto(val emoji: String, val userIds: List<String>)

data class MessageDto(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val body: String,
    val sentAt: String,
    val readAt: String?,
    val deletedAt: String? = null,
    val replyToMessageId: String? = null,
    val reactions: List<ReactionGroupDto> = emptyList(),
    // Real KakaoTalk Emoticon Store (item 135) -- see EmoticonPackDto's own doc
    // comment. Only ever set on a message actually created via the real
    // /api/v1/emoticons/.../send endpoints.
    val emoticonId: String? = null,
    // Real photo message (2026-08-04) -- see SendMessageRequest's own doc comment.
    // Backend (MessagingService.sendMessage) already validated/enforced a real
    // /api/v1/uploads/ URL since before this field existed on the Android DTO; this
    // just finally reads it back.
    val imageUrl: String? = null,
    // Real message forwarding (2026-08-04) -- see ForwardMessageRequest's own doc
    // comment. MessageForwardService (backend, 2026-07-25) already stamped these on a
    // real forwarded message's genuine provenance; no Android DTO ever read them back.
    val forwardedFromMessageId: String? = null,
    val forwardedFromType: String? = null,
    // Real Thread support (2026-08-05) -- see docs/DESIGN_REFERENCES.md Talk section
    // recommendation #3's own account. A real, read-time-computed count of direct
    // replies to this message (0 for a message no one has replied to).
    val replyCount: Long = 0,
)

data class StartConversationRequest(val phoneNumber: String? = null, val otherUserId: String? = null)
// Real photo message (2026-08-04) -- closes docs/DESIGN_REFERENCES.md's Talk
// recommendation #6 (no photo/file send at all). The backend (MessagingService
// .sendMessage/GroupMessagingService.sendMessage) already validated a real
// /api/v1/uploads/-prefixed imageUrl before this field existed on either Android
// request DTO -- a real "defined but uncalled" gap, same class this file's own history
// already names for group emoticons/group pin. Reuses the exact real upload flow
// MarketplaceScreen/PropertyScreen already established (GetContent() picker ->
// uploadPhoto() -> real server URL), not a new upload pipeline.
data class SendMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class TalkContactDto(val userId: String, val name: String)
data class TalkContactsResponse(val success: Boolean, val contacts: List<TalkContactDto>)
data class ConversationQuietResponse(val success: Boolean, val quiet: Boolean)
// Real recoverable archive (2026-08-05) -- see backend ConversationPreference
// .archived's own doc comment.
data class ConversationArchivedResponse(val success: Boolean, val archived: Boolean)
data class SetConversationArchivedRequest(val archived: Boolean)
data class CreateChatReportRequest(val messageId: String, val reason: String)
data class SetConversationQuietRequest(val quiet: Boolean)
data class ToggleReactionRequest(val emoji: String)

data class ConversationResponse(val success: Boolean, val conversation: ConversationDto)
data class ConversationsResponse(val success: Boolean, val conversations: List<ConversationSummaryDto>)
data class MessagesResponse(val success: Boolean, val messages: List<MessageDto>)
data class MessageResponse(val success: Boolean, val message: MessageDto)
// Real message forwarding (2026-08-04) -- see MessagingController.forwardMessage's own
// doc comment on the backend. destinationType is "DIRECT" (a conversationId) or "GROUP"
// (a groupId).
data class ForwardMessageRequest(val destinationType: String, val destinationId: String)
data class PinnedMessageResponse(val success: Boolean, val message: MessageDto?)
data class ReactionsResponse(val success: Boolean, val reactions: List<ReactionGroupDto>)

// Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
// memberPhoneNumbers is the real human-friendly entry point (same reasoning as
// StartConversationRequest.phoneNumber).
data class CreateGroupRequest(val name: String, val memberUserIds: List<String> = emptyList(), val memberPhoneNumbers: List<String> = emptyList())
data class SendGroupMessageRequest(val body: String, val replyToMessageId: String? = null, val imageUrl: String? = null)
data class AddGroupMemberRequest(val userId: String)

data class GroupSummaryDto(
    val groupId: String,
    val name: String,
    val memberCount: Int,
    val lastMessageAt: String,
    val lastMessagePreview: String?,
    val unreadCount: Long,
    val quiet: Boolean = false,
    // Real group photo/description (2026-07-28) -- see GroupMessagingService
    // .setGroupPhotoUrl/setGroupDescription's own doc comments. Found 2026-08-01 via a
    // defined-but-uncalled-endpoint sweep: real on backend since it shipped, zero
    // client anywhere on any of the 3 platforms until now.
    val photoUrl: String? = null,
    val description: String? = null,
)
data class SetGroupPhotoUrlRequest(val photoUrl: String)
data class SetGroupDescriptionRequest(val description: String)
data class GroupSummaryResponse(val success: Boolean, val group: GroupSummaryDto)
data class GroupMessageDto(
    val id: String,
    val groupConversationId: String,
    val senderId: String,
    val body: String,
    val sentAt: String,
    val deletedAt: String? = null,
    val replyToMessageId: String? = null,
    val reactions: List<ReactionGroupDto> = emptyList(),
    // Real KakaoTalk Emoticon Store, group-send side (item 133/204) -- see
    // sendGroupEmoticon's own doc comment. Set only on a message actually sent via
    // EmoticonController's /groups/{id}/send endpoint. Found 2026-07-29 via the
    // defined-but-uncalled-method sweep: the backend/DTO field existed on bank-mfe's
    // equivalent type, but this DTO never carried it and no client ever sent one.
    val emoticonId: String? = null,
    // Real photo message (2026-08-04) -- see SendMessageRequest's own doc comment.
    val imageUrl: String? = null,
    // Real message forwarding (2026-08-04) -- see ForwardMessageRequest's own doc
    // comment.
    val forwardedFromMessageId: String? = null,
    val forwardedFromType: String? = null,
    // Real Kakao-style per-message unread countdown (backend since 2026-07-26, client
    // gap found 2026-08-05 via a doc-accuracy audit) -- see GroupMessagingController
    // .getMessages's own doc comment. Counts real members whose lastReadAt is still
    // before this message's sentAt; decrements live as members open the thread.
    val unreadCount: Long = 0,
    // Real Thread support (2026-08-05) -- see MessageDto.replyCount's own doc comment.
    val replyCount: Long = 0,
)
data class GroupResponse(val success: Boolean, val group: GroupSummaryDto)
data class GroupsResponse(val success: Boolean, val groups: List<GroupSummaryDto>)
// Real group-chat pin (2026-08-04) -- see ApiService's own getPinnedGroupMessage doc
// comment. Mirrors PinnedMessageResponse's own 1:1 shape.
data class GroupPinnedMessageResponse(val success: Boolean, val message: GroupMessageDto?)
data class GroupMessagesResponse(val success: Boolean, val messages: List<GroupMessageDto>)
data class GroupMessageResponse(val success: Boolean, val message: GroupMessageDto)
data class LeaveGroupResponse(val success: Boolean)

// Real member list with real resolved display names (2026-07-18) -- closes the honest,
// named limitation this UI carried since group chat first shipped: message bubbles
// showing a truncated sender id instead of a real name.
data class GroupMemberDto(val userId: String, val name: String)
data class GroupMembersResponse(val success: Boolean, val members: List<GroupMemberDto>)

// Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's own
// doc comment on the backend.
data class PresenceResponse(val success: Boolean, val presence: Map<String, Boolean>)

// Mirrors services/backend/marketplace's real DTOs exactly (2026-07-18) -- backs the
// new "Hood" bottom-nav tab (당근마켓/Danggeun-style neighborhood marketplace). See
// rw.itunda.marketplace.MarketplaceService's own doc comment for the honest "no real
// location data" scope this mobile client inherits unchanged.
data class ListingDto(
    val id: String,
    val sellerId: String,
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val status: String,
    val createdAt: String,
    // Real optional seller-set location (2026-07-18 backend) -- backs real proximity
    // search and, 2026-07-19, "Directions to this seller".
    val latitude: Double? = null,
    val longitude: Double? = null,
    val neighborhood: String? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
    // buyerId added 2026-07-24 -- real optional buyer identification captured at
    // mark-sold time, see backend Listing.kt's own doc comment. Only set once a real
    // review becomes possible for this transaction.
    val buyerId: String? = null,
    // Real seller-paid sponsored placement (2026-07-25) -- see backend Listing.kt's own
    // doc comment. Null/expired means "not boosted" -- clients should only show a
    // "Sponsored" badge when this is a real, still-future ISO instant.
    val boostedUntil: String? = null,
    // Real like count (2026-08-03) -- see backend Listing.kt's own doc comment. A
    // separate concept from favoriteIds' personal wishlist -- this is a public
    // engagement count, matching real 당근마켓's heart count on every listing row.
    val likeCount: Long = 0,
)
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class BoostListingRequest(val days: Int)
data class BoostTiersResponse(val success: Boolean, val tiers: Map<String, Double>)

// Real "pay via itunda" Marketplace escrow (2026-07-25) -- see backend
// MarketplaceEscrow.kt's own doc comment. An opt-in safer alternative to the existing
// in-person cash handoff, never replacing it.
data class MarketplaceEscrowDto(
    val id: String, val listingId: String, val buyerId: String, val sellerId: String,
    val amount: Double, val fee: Double, val status: String,
    val holdTransactionId: String, val resolutionTransactionId: String? = null,
    val disputeReason: String? = null, val createdAt: String, val updatedAt: String,
)
data class MarketplaceEscrowResponse(val success: Boolean, val escrow: MarketplaceEscrowDto)
data class DisputeEscrowRequest(val reason: String)
// Real post-transaction review with asymmetric public/private visibility (2026-07-24)
// -- see backend HoodTransactionReview.kt's own doc comment. goodPoints/
// uncomfortablePoints are preset tag ids (never free text), matching Karrot's own real
// review UX.
data class SubmitHoodReviewRequest(val goodPoints: List<String> = emptyList(), val uncomfortablePoints: List<String> = emptyList())
data class HoodReviewDto(
    val id: String, val transactionType: String, val transactionId: String, val reviewerId: String, val revieweeId: String,
    val goodPoints: List<String>, val uncomfortablePoints: List<String>, val createdAt: String,
)
data class HoodReviewResponse(val success: Boolean, val review: HoodReviewDto)
data class HoodReviewsResponse(val success: Boolean, val reviews: List<HoodReviewDto>)

data class CreateListingRequest(
    val title: String,
    val description: String,
    val price: Double,
    val category: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val meetingPlace: String? = null,
    val photoUrl: String? = null,
)
data class UploadResponse(val success: Boolean, val url: String)
data class ListingResponse(val success: Boolean, val listing: ListingDto)
// trustScores added 2026-07-24 -- backend has spread this alongside every
// listing/job-post/property-listing browse response since 2026-07-21
// (rw.itunda.core.web.TrustScoreSupport), but no client ever parsed or rendered it.
// A sellerId/posterId/listerId -> User.trustScore map (Karrot-Score-style, 0-1000,
// starting at 30 -- see backend User.kt's own doc comment for why not a literal
// manner-temperature metaphor).
// likedByMe added 2026-08-03 -- see MarketplaceController.kt's own doc comment: real
// on myNeighborhood/getMyListings/getMyPurchases (all authenticated), absent on
// browse/nearby (deliberately unauthenticated, guest-browsable) -- an honest gap, not
// a client bug; the heart's count is always real either way, just starts unfilled on
// those two endpoints until a real tap.
data class ListingsResponse(val success: Boolean, val listings: List<ListingDto>, val trustScores: Map<String, Int> = emptyMap(), val likedByMe: Set<String> = emptySet())
data class ToggleLikeResponse(val success: Boolean, val liked: Boolean)

// Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe, ported here) --
// mirrors FavoriteRestaurantDto's exact shape; see ListingFavoriteService.kt's own doc
// comment on the backend for why add/remove are both idempotent.
data class FavoriteListingDto(val listingId: String, val title: String, val price: Double, val category: String, val favoritedAt: String)
data class FavoriteListingsResponse(val success: Boolean, val favorites: List<FavoriteListingDto>)

// Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService's own
// doc comment. Each offer/counter/accept/reject is a real message in the same real
// conversation contactSeller establishes, rendered inline as an offer bubble.
data class PriceOfferDto(
    val id: String,
    val listingId: String,
    val messageId: String,
    val conversationId: String,
    val buyerId: String,
    val sellerId: String,
    val proposedByUserId: String,
    val amount: Double,
    val status: String,
    val createdAt: String,
    val respondedAt: String?,
)
data class MakeOfferRequest(val amount: Double)
data class RespondToOfferRequest(val action: String, val counterAmount: Double? = null)
data class PriceOfferResponse(val success: Boolean, val offer: PriceOfferDto)
data class PriceOffersResponse(val success: Boolean, val offers: List<PriceOfferDto>)
data class ContactSellerResponse(val success: Boolean, val conversation: ConversationDto)

// Real KakaoTalk-style "선물하기" money gift (2026-07-20) -- see GiftService's own doc
// comment. Money leaves the sender's wallet into a real escrow account the moment a
// gift is sent, and only reaches the recipient's wallet once they explicitly claim it
// (or is auto-refunded after 7 days). Rendered inline as a gift bubble, same "special
// message body" convention PriceOfferDto already established.
data class GiftDto(
    val id: String,
    val senderId: String,
    val recipientId: String,
    val conversationId: String,
    val messageId: String,
    val amount: Double,
    val note: String?,
    val theme: String?,
    val status: String,
    val holdTransactionId: String,
    val claimTransactionId: String?,
    val expiresAt: String,
    val claimedAt: String?,
    val createdAt: String,
)
data class SendGiftInConversationRequest(val amount: Double, val note: String? = null, val theme: String? = null)

// Real KakaoPay 송금봉투 (money envelope) themed presets (backend since 2026-07-26,
// GiftTheme's own doc comment) -- exactly these 4 real, sourced presets, optional and
// additive alongside the free-text note. Had zero client anywhere until now.
val GIFT_THEME_LABELS: Map<String, String> = mapOf(
    "CONGRATULATIONS" to "🎉 Congratulations",
    "HEARTFELT" to "💌 From the heart",
    "GOOD_LUCK" to "🍀 Good luck",
    "SETTLE_UP" to "🧾 Settling up",
)
data class GiftResponse(val success: Boolean, val gift: GiftDto)
data class GiftsResponse(val success: Boolean, val gifts: List<GiftDto>)

// Real KakaoTalk Emoticon Store (item 135) -- mirrors bank-mfe's lib/emoticons.ts
// exactly (item 133).
data class EmoticonPackDto(val id: String, val title: String, val artistName: String, val thumbnailUrl: String, val price: Double, val active: Boolean, val createdAt: String)
data class EmoticonDto(val id: String, val packId: String, val imageUrl: String, val sortOrder: Int)
data class OwnedEmoticonPackDto(val id: String, val userId: String, val packId: String, val source: String, val acquiredAt: String)
data class EmoticonPacksResponse(val success: Boolean, val packs: List<EmoticonPackDto>)
data class EmoticonsResponse(val success: Boolean, val emoticons: List<EmoticonDto>)
data class OwnedEmoticonPacksResponse(val success: Boolean, val packs: List<OwnedEmoticonPackDto>)
data class OwnedEmoticonPackResponse(val success: Boolean, val ownedPack: OwnedEmoticonPackDto)
// Real bug caught before compiling: EmoticonController.giftPack returns the key
// "giftedPack", not "ownedPack" -- a distinct response shape, not reusable.
data class GiftedEmoticonPackResponse(val success: Boolean, val giftedPack: OwnedEmoticonPackDto)
data class GiftEmoticonPackRequest(val recipientPhoneNumber: String)
data class SendEmoticonRequest(val emoticonId: String)

// Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
data class CommunityCategoryDto(val id: String, val label: String)
data class CommunityPostDto(
    val id: String, val authorId: String, val category: String, val title: String, val body: String,
    val status: String, val likeCount: Long, val commentCount: Long, val createdAt: String,
    val latitude: Double? = null, val longitude: Double? = null,
    // groupConversationId added 2026-07-24 -- see backend CommunityPost.kt's own doc
    // comment. Only ever set for category == "meetup" posts that have had at least one
    // real join.
    val groupConversationId: String? = null,
    // Real 당근모임-style structured meetup fields (2026-07-25) -- see backend
    // CommunityPost.kt's own doc comment. Only ever set for category == "meetup";
    // eventDate stays a plain ISO string, same convention every other temporal field in
    // this file already uses. capacity null means unlimited.
    val eventDate: String? = null,
    val capacity: Int? = null,
)
data class CreateCommunityPostRequest(
    val category: String, val title: String, val body: String,
    val latitude: Double? = null, val longitude: Double? = null,
    val eventDate: String? = null, val capacity: Int? = null,
)
data class CommunityPostResponse(val success: Boolean, val post: CommunityPostDto)
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "같이해요
// (join-together) posts get a dedicated pinned mid-feed slot."
data class CommunityPostsResponse(val success: Boolean, val posts: List<CommunityPostDto>, val joinedCounts: Map<String, Int> = emptyMap())
data class CommunityCategoriesResponse(val success: Boolean, val categories: List<CommunityCategoryDto>)
data class CommunityPostDetailResponse(val success: Boolean, val post: CommunityPostDto, val authorName: String, val likedByMe: Boolean)
data class CommunityCommentDto(val id: String, val postId: String, val authorId: String, val body: String, val createdAt: String)
data class CommunityCommentWithAuthorDto(val comment: CommunityCommentDto, val authorName: String)
data class CommunityCommentsResponse(val success: Boolean, val comments: List<CommunityCommentWithAuthorDto>)
data class AddCommunityCommentRequest(val body: String)
data class CommunityCommentResponse(val success: Boolean, val comment: CommunityCommentDto)
data class ToggleCommunityLikeResponse(val success: Boolean, val liked: Boolean)
// Real 같이해요 (join-together) explicit join (2026-07-24) -- see backend
// CommunityService.joinMeetup's own doc comment.
data class JoinMeetupResponse(val success: Boolean, val groupId: String)

// Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- mirrors
// bank-mfe's lib/community.ts exactly.
data class ScheduleMeetupSessionsRequest(val dates: List<String>)
data class MeetupSessionDto(val id: String, val postId: String, val sequence: Int, val scheduledFor: String, val createdAt: String)
data class MeetupSessionsResponse(val success: Boolean, val sessions: List<MeetupSessionDto>)
data class MeetupAttendanceDto(val id: String, val sessionId: String, val userId: String, val checkedInAt: String)
data class MeetupAttendanceResponse(val success: Boolean, val attendance: MeetupAttendanceDto)
data class FinalizeGroupBuyRequest(val totalAmount: java.math.BigDecimal, val description: String)

// Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
data class JobCategoryDto(val id: String, val label: String)
data class JobPostDto(
    val id: String, val posterId: String, val category: String, val title: String, val description: String,
    val payType: String, val payAmount: Double, val status: String, val createdAt: String,
    val latitude: Double? = null, val longitude: Double? = null,
    // workerId added 2026-07-24 -- real optional worker identification captured at
    // mark-filled time, see backend JobPost.kt's own doc comment. Only set once a
    // real review becomes possible for this transaction.
    val workerId: String? = null,
)
data class MarkFilledRequest(val workerPhoneNumber: String? = null)
data class CreateJobPostRequest(
    val category: String, val title: String, val description: String, val payType: String, val payAmount: Double,
    val latitude: Double? = null, val longitude: Double? = null,
)
data class JobPostResponse(val success: Boolean, val post: JobPostDto)
data class SuccessResponse(val success: Boolean)
data class JobPostsResponse(val success: Boolean, val posts: List<JobPostDto>, val trustScores: Map<String, Int> = emptyMap())
data class JobCategoriesResponse(val success: Boolean, val categories: List<JobCategoryDto>)
data class FavoriteJobPostDto(val jobPostId: String, val title: String, val payAmount: Double, val category: String, val favoritedAt: String)

// Real 당근마켓-style Keyword Alert -- mirrors KeywordAlert.kt/KeywordAlertQuietHours.kt
// exactly.
data class AddKeywordAlertRequest(val keyword: String)
data class KeywordAlertDto(val id: String, val userId: String, val keyword: String, val createdAt: String)
data class KeywordAlertResponse(val success: Boolean, val alert: KeywordAlertDto)
data class KeywordAlertsResponse(val success: Boolean, val alerts: List<KeywordAlertDto>)
data class SetKeywordAlertQuietHoursRequest(val startTime: String, val endTime: String, val enabled: Boolean)
data class KeywordAlertQuietHoursDto(val id: String, val userId: String, val startTime: String, val endTime: String, val enabled: Boolean)
data class KeywordAlertQuietHoursResponse(val success: Boolean, val quietHours: KeywordAlertQuietHoursDto?)
data class FavoriteJobPostsResponse(val success: Boolean, val favorites: List<FavoriteJobPostDto>)

// Real 당근알바-style structured application (2026-07-25) -- see backend
// JobApplicationService's own doc comment.
data class ApplyToJobRequest(val message: String)
data class JobApplicationDto(
    val id: String, val jobPostId: String, val applicantId: String, val message: String,
    val status: String, val submittedAt: String, val respondedAt: String? = null,
)
data class JobApplicationResponse(val success: Boolean, val application: JobApplicationDto, val conversation: ConversationDto? = null)
data class JobApplicationsResponse(val success: Boolean, val applications: List<JobApplicationDto>)
data class RespondToApplicationRequest(val accept: Boolean)
data class ContactPosterResponse(val success: Boolean, val conversation: ConversationDto)

// Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
data class PropertyTypeDto(val id: String, val label: String)
data class PropertyListingDto(
    val id: String, val listerId: String, val listingType: String, val propertyType: String,
    val title: String, val description: String, val price: Double, val bedrooms: Int? = null, val sizeSqm: Double? = null,
    val status: String, val createdAt: String, val latitude: Double? = null, val longitude: Double? = null,
    // counterpartyId added 2026-07-24 -- real optional buyer/tenant identification
    // captured at mark-taken time, see backend PropertyListing.kt's own doc comment.
    // Only set once a real review becomes possible for this transaction.
    val counterpartyId: String? = null,
    // Real ownership verification (2026-07-25) -- NONE/PENDING/VERIFIED, see backend
    // PropertyOwnershipSubmission's own doc comment.
    val ownershipVerificationStatus: String = "NONE",
)
data class MarkTakenRequest(val counterpartyPhoneNumber: String? = null)
data class SubmitOwnershipVerificationRequest(val documentUrl: String)
data class PropertyOwnershipSubmissionDto(
    val id: String, val listingId: String, val userId: String, val documentUrl: String,
    val status: String, val submittedAt: String,
)
data class PropertyOwnershipSubmissionResponse(val success: Boolean, val submission: PropertyOwnershipSubmissionDto)
data class CreatePropertyListingRequest(
    val listingType: String, val propertyType: String, val title: String, val description: String, val price: Double,
    val bedrooms: Int? = null, val sizeSqm: Double? = null, val latitude: Double? = null, val longitude: Double? = null,
)
data class PropertyListingResponse(val success: Boolean, val listing: PropertyListingDto)
data class PropertyListingsResponse(val success: Boolean, val listings: List<PropertyListingDto>, val trustScores: Map<String, Int> = emptyMap())
data class FavoritePropertyListingDto(val propertyListingId: String, val title: String, val price: Double, val listingType: String, val favoritedAt: String)
data class FavoritePropertyListingsResponse(val success: Boolean, val favorites: List<FavoritePropertyListingDto>)
data class PropertyTypesResponse(val success: Boolean, val propertyTypes: List<PropertyTypeDto>)
data class ContactListerResponse(val success: Boolean, val conversation: ConversationDto)

// Real Toss Bank 우리집 시세 (my home's estimated value, item 228) -- see the backend's
// PropertyListingService.estimateValue doc comment. Read-only, computed fresh from
// real comparable listings on every call, nothing persisted.
data class PropertyValuationEstimateDto(
    val estimatedValue: Double, val comparableCount: Int, val averagePricePerSqm: Double, val radiusKm: Double,
)
data class PropertyValuationResponse(val success: Boolean, val estimate: PropertyValuationEstimateDto)

// Real 당근-style price-offer negotiation on a real property listing (2026-07-19) -- see
// PropertyPriceOfferService's own doc comment. Mirrors PriceOfferDto field-for-field.
data class PropertyPriceOfferDto(
    val id: String,
    val propertyListingId: String,
    val messageId: String,
    val conversationId: String,
    val inquirerId: String,
    val listerId: String,
    val proposedByUserId: String,
    val amount: Double,
    val status: String,
    val createdAt: String,
    val respondedAt: String?,
)
data class MakePropertyOfferRequest(val amount: Double)
data class RespondToPropertyOfferRequest(val action: String, val counterAmount: Double? = null)
data class PropertyPriceOfferResponse(val success: Boolean, val offer: PropertyPriceOfferDto)
data class PropertyPriceOffersResponse(val success: Boolean, val offers: List<PropertyPriceOfferDto>)

data class CreateHoodReportRequest(val targetType: String, val targetId: String, val reason: String)
data class HoodReportResponse(val success: Boolean)

// Mirrors services/backend/commerce's real DTOs exactly (2026-07-18) -- backs the new
// "Shop" bottom-nav tab (Coupang-style multi-item checkout), replacing the old Shop
// tab's Toss-Shopping-cashback content. See rw.itunda.commerce.OrderService's own doc
// comment for the honest "self-declared fulfillment, no real courier network" scope.
data class ShoppingMerchantDto(
    val merchantId: String, val businessName: String, val category: String?, val cashbackRate: String,
    // Real optional location (2026-07-19) -- backs the real self-hosted Map view.
    val latitude: Double? = null, val longitude: Double? = null,
    // Real browse-card enrichment (2026-07-21) -- closes docs/DESIGN_REFERENCES.md's
    // Eats recommendations #1/#2. photoUrl/minOrderAmount are real, merchant-set (null
    // when unset); rating/reviewCount are real, batch-aggregated from EatsReview.
    // distanceKm/deliveryTimeMinutes are only present when this call supplied its own
    // real buyerLat/buyerLng -- deliveryTimeMinutes is a real, clearly-an-ESTIMATE
    // derived from that distance (see ShoppingController.estimateDeliveryMinutes's own
    // doc comment on the backend), never a fabricated/measured number.
    val photoUrl: String? = null,
    val minOrderAmount: Double? = null,
    val rating: Double? = null,
    val reviewCount: Long = 0,
    val distanceKm: Double? = null,
    val deliveryTimeMinutes: Int? = null,
    // Real merchant-set phone/hours (2026-08-09) -- see Merchant.kt's own doc comment on
    // the backend. Null unless the merchant has actually set one.
    val phoneNumber: String? = null,
    val openingHours: String? = null,
)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Real "search this map" + "directions" (2026-07-19) -- see rw.itunda.maps.MapsService's
// own doc comment on the backend for why these are a new, general-purpose front door
// onto itunda's already-deployed self-hosted Nominatim/OSRM.
data class PlaceSearchResultDto(val displayName: String, val latitude: Double, val longitude: Double)
data class MapsSearchResponse(val success: Boolean, val results: List<PlaceSearchResultDto>)
data class MapsReverseGeocodeResponse(val success: Boolean, val placeName: String?)
data class RouteStepDto(val instruction: String, val distanceMeters: Double, val streetName: String?)
data class RouteResultDto(val distanceKm: Double, val durationMinutes: Double, val geometry: List<List<Double>>, val steps: List<RouteStepDto> = emptyList())
data class MapsDirectionsResponse(val success: Boolean, val route: RouteResultDto)
// Real alternative routes (2026-07-22) -- see OsrmRoutingClient.routeAlternatives' own
// doc comment on the backend. Often just a single-element list -- OSRM itself decides
// whether a real alternative exists for a given trip.
data class MapsDirectionsAlternativesResponse(val success: Boolean, val routes: List<RouteResultDto>)
// Ordered multi-stop directions (2026-07-22). The backend deliberately accepts only
// 2–5 Rwanda waypoints so the self-hosted OSRM request and the mobile itinerary stay
// legible. The returned RouteResultDto is one continuous road route through that order.
data class ItineraryWaypointRequest(val latitude: Double, val longitude: Double)
data class ItineraryDirectionsRequest(val waypoints: List<ItineraryWaypointRequest>, val mode: String = "DRIVING")
data class MapsItineraryResponse(val success: Boolean, val route: RouteResultDto)
data class MerchantCategoriesResponse(val success: Boolean, val categories: List<String>)

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment.
data class DealProductDto(
    val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double,
    val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val description: String? = null,
    val stockQuantity: Int? = null,
)
data class DealsResponse(val success: Boolean, val products: List<DealProductDto>)

// Real Coupang Eats-style dish grid (2026-08-03) -- see backend EatsController.kt's
// own doc comment for the full 100%-UI/UX-parity sourcing. Same shape as
// DealProductDto above (this app's established "product-in-a-list" DTO shape) minus
// the discount fields, which don't apply here.
data class EatsDishDto(val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double, val imageUrl: String? = null)
data class EatsDishesResponse(val success: Boolean, val dishes: List<EatsDishDto>)
data class MembershipDayStatusResponse(val success: Boolean, val isMembershipDay: Boolean, val multiplier: Double)

// Real Coupang 타임특가 (Time Deal, item 226) -- see the backend TimeDeal.kt's own doc
// comment. A time-boxed, quantity-capped discount OVERLAY on an existing product,
// distinct from the always-on "Deals" rail above (DealProductDto), which surfaces a
// permanent discountPercent/originalPrice, not a scheduled event. bank-mfe already has
// this; this is the first Android client (consumer browse only -- merchant creation
// lives on merchant-mfe, mirroring this app's existing consumer/merchant app split).
data class TimeDealDto(
    val id: String, val merchantId: String, val productId: String, val dealPrice: Double, val originalPrice: Double,
    val totalQuantity: Int, val remainingQuantity: Int, val startsAt: String, val endsAt: String, val createdAt: String,
)
data class TimeDealViewDto(val deal: TimeDealDto, val productName: String, val productImageUrl: String?, val businessName: String)
data class TimeDealsResponse(val success: Boolean, val deals: List<TimeDealViewDto>)

// Real Coupang 정기배송 (subscribe & save)-style recurring product delivery
// (rw.itunda.commerce's ProductSubscriptionService) -- real on bank-mfe since
// 2026-07-25, found 2026-08-01 with zero client on Android/iOS despite that.
// Honest scope boundary matches bank-mfe's own: 5% single-item discount only.
data class ProductSubscriptionDto(
    val id: String, val merchantId: String, val productId: String, val quantity: Int, val intervalDays: Int,
    val deliveryAddress: String, val status: String, val nextDeliveryAt: String, val createdAt: String,
    val lastDeliveredAt: String?, val deliveryCount: Int, val lastFailureReason: String?, val cancelledAt: String?,
)
data class CreateProductSubscriptionRequest(val merchantId: String, val productId: String, val quantity: Int, val intervalDays: Int, val deliveryAddress: String)
data class ProductSubscriptionResponse(val success: Boolean, val subscription: ProductSubscriptionDto)
data class ProductSubscriptionsResponse(val success: Boolean, val subscriptions: List<ProductSubscriptionDto>)

// Real "nearby places" category search + bookmarked/favorite places (2026-07-19) -- see
// rw.itunda.maps.MapsService's own doc comment on the backend. `MAP_NEARBY_CATEGORIES`
// mirrors bank-mfe's own hardcoded `NEARBY_CATEGORIES` list exactly.
data class NearbyPlaceDto(val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class MapNearbyResponse(val success: Boolean, val places: List<NearbyPlaceDto>)
// Real "Smart Around"-style default map state (2026-08-04) -- see MapsService's own
// getAroundMe/getTrendingSavedPlaces doc comments for the real, re-verified Naver Map
// sourcing (brunch.co.kr/@bydot/4) and the honest scope decision.
data class MapAroundMeResponse(val success: Boolean, val places: List<NearbyPlaceDto>)
data class TrendingPlaceDto(val displayName: String, val latitude: Double, val longitude: Double, val saveCount: Long)
data class MapTrendingResponse(val success: Boolean, val places: List<TrendingPlaceDto>)
// Public-safe cash-point discovery. The backend deliberately omits tills, operator
// details, and cash availability; customers only need a name, location and distance.
data class NearbyAgentDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class NearbyAgentsResponse(val success: Boolean, val agents: List<NearbyAgentDto>)
// folderName/color added 2026-07-22 -- see MapBookmark.kt's own doc comment on the
// backend (migration V73). Every bookmark belongs to exactly one named folder with its
// own pin color; a bookmark saved before this existed defaults into "Saved places" /
// "#F5A623" (the same star-yellow the ★ icon already used).
data class MapBookmarkDto(val id: String, val displayName: String, val latitude: Double, val longitude: Double, val folderName: String, val color: String, val isPublic: Boolean = false, val createdAt: String)
data class MapBookmarksResponse(val success: Boolean, val bookmarks: List<MapBookmarkDto>)
data class AddMapBookmarkRequest(val displayName: String, val latitude: Double, val longitude: Double, val folderName: String? = null, val color: String? = null)
data class AddMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
// Real "move to folder" (2026-07-22) -- see MapsService.moveBookmark's own doc comment.
data class MoveMapBookmarkRequest(val folderName: String, val color: String)
data class MoveMapBookmarkResponse(val success: Boolean, val bookmark: MapBookmarkDto)
data class RemoveMapBookmarkResponse(val success: Boolean)
// Real Naver Map-style public/private folder + share (2026-08-04) -- see
// MapBookmark.isPublic's own doc comment on the backend.
data class SetMapFolderVisibilityRequest(val folderName: String, val isPublic: Boolean)
data class SetMapFolderVisibilityResponse(val success: Boolean, val updatedCount: Int)
data class SharedMapFolderResponse(val success: Boolean, val bookmarks: List<MapBookmarkDto>)

data class MapPlaceCategory(val id: String, val label: String)
val MAP_NEARBY_CATEGORIES = listOf(
    MapPlaceCategory("RESTAURANT", "Restaurants"),
    MapPlaceCategory("CAFE", "Cafes"),
    MapPlaceCategory("HOSPITAL", "Hospitals"),
    MapPlaceCategory("PHARMACY", "Pharmacies"),
    MapPlaceCategory("BANK", "Banks"),
    MapPlaceCategory("ATM", "ATMs"),
    MapPlaceCategory("HOTEL", "Hotels"),
    MapPlaceCategory("SUPERMARKET", "Supermarkets"),
    MapPlaceCategory("GAS_STATION", "Gas stations"),
    MapPlaceCategory("SCHOOL", "Schools"),
    MapPlaceCategory("ITUNDA_AGENT", "Itunda agents"),
    // Real additions (2026-08-09), same pass as MapPlaceCategory.kt's own backend enum --
    // live-verified against itunda's real self-hosted Nominatim before adding (see that
    // file's own doc comment for the real curl results).
    MapPlaceCategory("MARKET", "Markets"),
    MapPlaceCategory("BUS_STOP", "Bus stops"),
)

// Mirrors services/backend/core's real MerchantProduct entity exactly.
// imageUrl/originalPrice/discountPercent added 2026-07-21, closing
// docs/DESIGN_REFERENCES.md Section 5 recommendation #4 -- see backend
// MerchantProduct.kt's own doc comment for the full account (merchant-supplied external
// URL, no upload/storage layer; discountPercent is server-computed, never client-set).
// Real menu-item option groups (2026-07-21, v1: required single-select only) -- ports
// bank-mfe's own MenuOptionChoice/MenuOptionGroup interfaces (lib/eats.ts). See
// MenuOptionGroup.kt's own doc comment on the backend for the full, honestly-scoped
// account.
data class EatsMenuOptionChoiceDto(val id: String, val name: String, val priceDelta: Double)
data class EatsMenuOptionGroupDto(val id: String, val name: String, val choices: List<EatsMenuOptionChoiceDto> = emptyList())

data class MerchantProductDto(
    val id: String,
    val merchantId: String,
    val name: String,
    val price: Double,
    val active: Boolean,
    val createdAt: String,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    // description added 2026-07-21, backing the new dedicated product-detail screen
    // (closes docs/DESIGN_REFERENCES.md Section 5 recommendation #6).
    val description: String? = null,
    // Absent/empty on endpoints that don't fold it in (e.g. product search) -- only
    // ShoppingController.getMerchantProducts (Eats' menu) populates this today.
    val optionGroups: List<EatsMenuOptionGroupDto> = emptyList(),
    // Real bookable-service duration (2026-07-25) -- a non-null value means this
    // "product" is actually a real appointment-bookable service (e.g. a 30-minute
    // haircut). See MerchantProduct.kt's own doc comment on the backend.
    val durationMinutes: Int? = null,
    // Real bulk/wholesale pricing (2026-07-25) -- closes the gap named in Baemin's own
    // real 배민상회 B2B supplies marketplace research. Empty for every product with no
    // real tiers set, the pre-existing behavior for every product before this field
    // existed. See ProductPriceTier.kt's own doc comment on the backend.
    val priceTiers: List<PriceTierDto> = emptyList(),
    val stockQuantity: Int? = null,
)
data class PriceTierDto(val minQuantity: Int, val unitPrice: Double)

// Real local-business appointment booking (2026-07-25) -- see
// rw.itunda.merchant.MerchantBookingService on the backend for the full account. Date
// ("yyyy-MM-dd") and time ("HH:mm:ss") fields stay plain ISO strings here, same
// convention every other temporal field (createdAt etc.) in this file already uses --
// Gson has no built-in java.time.LocalDate/LocalTime adapter registered, so this avoids
// that pitfall entirely rather than registering one just for this feature.
data class BookingSlotDto(val startTime: String, val endTime: String)
data class BookingSlotsResponse(val success: Boolean, val slots: List<BookingSlotDto>)
data class CreateBookingRequest(val merchantId: String, val serviceId: String, val date: String, val startTime: String, val notes: String? = null)
data class MerchantBookingDto(
    val id: String,
    val merchantId: String,
    val customerId: String,
    val serviceId: String,
    val serviceName: String,
    val bookingDate: String,
    val startTime: String,
    val endTime: String,
    val status: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
data class MerchantBookingDetailResponse(val success: Boolean, val booking: MerchantBookingDto)
data class MerchantBookingsResponse(val success: Boolean, val bookings: List<MerchantBookingDto>)

// Real post-appointment reviews (item 143) -- see MerchantBookingReview.kt's own doc
// comment. The owner-side list+reply half is real on merchant-mfe; this is the
// CUSTOMER-facing submit-a-review half, real on bank-mfe since 2026-08-01 -- this is
// the first native client.
data class SubmitBookingReviewRequest(val rating: Int, val comment: String? = null)
data class MerchantBookingReviewDto(
    val id: String,
    val bookingId: String,
    val merchantId: String,
    val customerId: String,
    val serviceName: String,
    val rating: Int,
    val comment: String? = null,
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    val createdAt: String,
)
data class MerchantBookingReviewResponse(val success: Boolean, val review: MerchantBookingReviewDto)
data class MerchantBookingReviewsResponse(val success: Boolean, val reviews: List<MerchantBookingReviewDto>)
data class MerchantBookingRatingDto(val average: Double?, val count: Long)
data class MerchantReviewsWithRatingResponse(val success: Boolean, val reviews: List<MerchantBookingReviewDto>, val rating: MerchantBookingRatingDto)
data class MerchantSummaryDto(val id: String, val businessName: String)
data class MerchantProductsResponse(val success: Boolean, val merchant: MerchantSummaryDto, val products: List<MerchantProductDto>)

// Real cross-merchant product search (item 137) -- see backend
// MerchantProductRepository.search's own doc comment. Mirrors bank-mfe's
// lib/shopping.ts ProductSearchResult exactly; Android never had this endpoint at all.
data class ProductSearchResultDto(
    val id: String,
    val merchantId: String,
    val merchantName: String,
    val name: String,
    val price: Double,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val description: String? = null,
    val stockQuantity: Int? = null,
)
data class ProductSearchResponse(val success: Boolean, val products: List<ProductSearchResultDto>)

// Real KakaoTalk-style 기프티콘 (mobile gift voucher, item 137) -- see backend
// GiftVoucher.kt's own doc comment. Mirrors bank-mfe's lib/giftVouchers.ts (item 134)
// exactly.
data class GiftVoucherDto(
    val id: String,
    val purchaserId: String,
    val recipientId: String,
    val conversationId: String,
    val messageId: String,
    val merchantId: String,
    val merchantProductId: String?,
    val productNameSnapshot: String?,
    val amount: Double,
    val status: String,
    val holdTransactionId: String,
    val redeemTransactionId: String?,
    val refundTransactionId: String?,
    val expiresAt: String,
    val redeemedAt: String?,
    val extended: Boolean,
    val createdAt: String,
)
data class PurchaseGiftVoucherRequest(val recipientPhoneNumber: String, val merchantId: String, val merchantProductId: String? = null, val amount: Double? = null)
data class GiftVoucherResponse(val success: Boolean, val voucher: GiftVoucherDto)
data class GiftVouchersResponse(val success: Boolean, val vouchers: List<GiftVoucherDto>)

// Real Naver Smart Store-style "알림받기" (follow a store for its own broadcast
// notices) -- see backend MerchantFollowService.kt's own doc comment. Mirrors
// bank-mfe's lib/shopping.ts FollowedMerchant exactly.
data class FollowedMerchantDto(val merchantId: String, val businessName: String, val category: String?, val followedAt: String)
data class FollowedMerchantsResponse(val success: Boolean, val follows: List<FollowedMerchantDto>)
data class MerchantFollowDto(val id: String, val userId: String, val merchantId: String, val createdAt: String)
data class MerchantFollowResponse(val success: Boolean, val follow: MerchantFollowDto)

// Real "pay a merchant" -- mirrors bank-mfe's lib/shopping.ts CollectPaymentResult
// exactly (a flat response, not nested under a key).
data class CollectPaymentRequest(val couponId: String? = null)
data class StaticQrPayRequest(val amount: java.math.BigDecimal, val description: String? = null)
data class CollectPaymentResultDto(
    val success: Boolean, val transactionId: String, val merchantName: String,
    val amount: java.math.BigDecimal, val fee: java.math.BigDecimal, val status: String,
    val channel: String, val completedAt: String, val cashbackEarned: java.math.BigDecimal,
)

// Real read-only payment-code preview (item 149) -- see MerchantService.previewIntent's
// own doc comment. Lets a payer see the merchant/amount/their own real coupon
// eligibility before committing to collectPayment -- mirrors bank-mfe's lib/shopping.ts
// PaymentIntentPreview/MerchantCouponView exactly. bank-mfe already has this
// (previewPaymentIntent, item 146); this is the first Android client.
data class MerchantCouponPreviewDto(
    val id: String, val merchantId: String, val title: String, val description: String?,
    val discountType: String, val discountValue: java.math.BigDecimal, val regularsOnly: Boolean,
    val active: Boolean, val expiresAt: String?, val createdAt: String,
)
data class MerchantCouponViewDto(val coupon: MerchantCouponPreviewDto, val eligible: Boolean, val alreadyRedeemed: Boolean)
data class MerchantCouponsForCustomerResponse(val success: Boolean, val coupons: List<MerchantCouponPreviewDto>)
data class PaymentIntentPreviewResponse(
    val success: Boolean, val merchantId: String, val businessName: String,
    val amount: java.math.BigDecimal, val description: String?, val coupons: List<MerchantCouponViewDto>,
)

// Real Face Pay -- mirrors bank-mfe's lib/facepay.ts exactly.
data class FacePayEnrollmentDto(val id: String, val userId: String, val active: Boolean, val enrolledAt: String, val revokedAt: String?)
data class FacePayEnrollmentResponse(val success: Boolean, val enrollment: FacePayEnrollmentDto)
data class FacePayStatusResponse(val success: Boolean, val enrolled: Boolean, val enrollment: FacePayEnrollmentDto?)

// Real Shop product wishlist (2026-07-24) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #3: the backend (ProductFavoriteService, shipped 2026-07-20) and
// bank-mfe (ProductCatalogView.toggleFavorite) already had this; Android had zero
// wiring. Mirrors FavoriteListingDto/FavoriteJobPostDto/FavoritePropertyListingDto
// field-for-field.
data class FavoriteProductDto(
    val productId: String,
    val merchantId: String,
    val name: String,
    val price: Double,
    val businessName: String,
    val favoritedAt: String,
    val imageUrl: String? = null,
    val originalPrice: Double? = null,
    val discountPercent: Int? = null,
    val description: String? = null,
    // Real Naver Shopping 가격 변동 알림 (price-drop alert, item 227) -- true once the
    // product's real current price has dropped below the price it was at when
    // favorited. See the backend's ProductFavoriteService.getMyFavorites doc comment.
    val priceDropped: Boolean = false,
)
data class FavoriteProductsResponse(val success: Boolean, val favorites: List<FavoriteProductDto>)

data class OrderItemRequest(val productId: String, val quantity: Int)
// referralCode added for the real 쿠팡파트너스-style affiliate program, item 229 --
// see AffiliateLinkDto's own doc comment.
data class PlaceOrderRequest(val merchantId: String, val items: List<OrderItemRequest>, val deliveryAddress: String, val referralCode: String? = null)
data class UpdateOrderStatusRequest(val status: String)

// Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) -- see
// the backend's AffiliateService doc comment. bank-mfe already has this; this is the
// first Android client, generation + earnings only (referral capture-at-checkout
// stays bank-mfe-only, since that relies on a real ?ref= URL query param this native
// app has no deep-link precedent for -- an honest v1 scope-down, not an invented one).
data class CreateAffiliateLinkRequest(val productId: String)
data class AffiliateLinkDto(val id: String, val userId: String, val productId: String, val code: String, val clickCount: Long, val createdAt: String)
data class AffiliateLinkResponse(val success: Boolean, val link: AffiliateLinkDto)
data class AffiliateLinksResponse(val success: Boolean, val links: List<AffiliateLinkDto>)
data class AffiliateCommissionDto(
    val id: String, val linkId: String, val referrerId: String, val orderId: String, val buyerId: String,
    val commissionAmount: Double, val payoutTransactionId: String, val createdAt: String,
)
data class AffiliateCommissionsResponse(val success: Boolean, val commissions: List<AffiliateCommissionDto>)

data class OrderDto(
    val id: String,
    val buyerId: String,
    val merchantId: String,
    val deliveryAddress: String,
    val totalAmount: Double,
    val fee: Double,
    val transactionId: String,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
)

data class OrderItemDto(val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int)
data class OrderDetailResponse(val success: Boolean, val order: OrderDto, val items: List<OrderItemDto>)
data class OrdersResponse(val success: Boolean, val orders: List<OrderDto>)

// Real Coupang-style post-delivery Return & Exchange requests (item 166/174) -- see
// OrderReturnService's own doc comment.
val ORDER_RETURN_REASON_CODES = listOf("DEFECTIVE", "WRONG_ITEM", "NOT_AS_DESCRIBED", "NO_LONGER_NEEDED", "SIZE_FIT", "OTHER")
data class RequestOrderReturnRequest(val type: String, val reasonCode: String, val reasonNote: String? = null)
data class OrderReturnRequestDto(
    val id: String,
    val orderId: String,
    val buyerId: String,
    val merchantId: String,
    val type: String,
    val reasonCode: String,
    val reasonNote: String?,
    val status: String,
    val refundTransactionId: String? = null,
    val requestedAt: String,
    val decidedAt: String? = null,
)
data class OrderReturnRequestResponse(val success: Boolean, val returnRequest: OrderReturnRequestDto)
data class OrderReturnRequestsResponse(val success: Boolean, val returnRequests: List<OrderReturnRequestDto>)
data class DecideOrderReturnRequest(val approve: Boolean)

// Real post-delivery product reviews (2026-07-20) -- see ProductReviewService's own doc
// comment, mirroring Eats' SubmitEatsReviewRequest/EatsReviewDto pattern above but keyed
// to one order line item rather than the whole order (a Commerce order can carry
// several different products from one merchant, and real Coupang reviews are per-product).
data class SubmitProductReviewRequest(val rating: Int, val comment: String? = null)
data class ProductReviewDto(
    val id: String,
    val orderItemId: String,
    val orderId: String,
    val buyerId: String,
    val productId: String,
    val merchantId: String,
    val rating: Int,
    val comment: String?,
    // Real owner-side reply (item 187/188) -- see ProductReviewService.replyToProductReview's
    // own doc comment. merchant-mfe already has this (item 187); customer-side display only
    // here (the reply-writing side lives on merchantapp, the merchant-owner app).
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    val createdAt: String,
)
data class ProductReviewResponse(val success: Boolean, val review: ProductReviewDto)
data class ProductReviewsResponse(val success: Boolean, val reviews: List<ProductReviewDto>)
data class ProductRatingResponse(val success: Boolean, val average: Double?, val count: Long)

// Real Coupang-style pre-purchase product Q&A -- mirrors bank-mfe's lib/commerce.ts
// ProductInquiry exactly.
data class AskProductInquiryRequest(val question: String)
data class ProductInquiryDto(
    val id: String, val productId: String, val merchantId: String, val buyerId: String,
    val question: String, val answer: String?, val answeredAt: String?, val createdAt: String,
)
data class ProductInquiryResponse(val success: Boolean, val inquiry: ProductInquiryDto)
data class ProductInquiriesResponse(val success: Boolean, val inquiries: List<ProductInquiryDto>)

// Real Kakao Pay 정기결제/Toss 빌링키-style recurring merchant billing -- mirrors
// bank-mfe's lib/shopping.ts exactly.
data class MerchantBillingPlanDto(
    val id: String, val merchantId: String, val name: String, val description: String?,
    val amount: java.math.BigDecimal, val intervalDays: Int, val active: Boolean, val createdAt: String,
)
data class MerchantBillingSubscriptionDto(
    val id: String, val planId: String, val merchantId: String, val customerId: String,
    val status: String, val nextChargeAt: String, val lastChargedAt: String?,
    val chargeCount: Int, val lastFailureReason: String?, val createdAt: String, val cancelledAt: String?,
)
data class MerchantBillingPlansResponse(val success: Boolean, val plans: List<MerchantBillingPlanDto>)
data class MerchantBillingSubscriptionResponse(val success: Boolean, val subscription: MerchantBillingSubscriptionDto)
data class MerchantBillingSubscriptionsResponse(val success: Boolean, val subscriptions: List<MerchantBillingSubscriptionDto>)

// Real recurring-payment ("subscription") detection -- mirrors bank-mfe's lib/wallet.ts
// DetectedSubscription exactly.
data class DetectedSubscriptionDto(
    val displayName: String, val amount: java.math.BigDecimal, val cadence: String, val occurrenceCount: Int,
    val lastPaidAt: String, val nextExpectedAt: String, val monthlyEquivalent: java.math.BigDecimal,
    val priceIncreased: Boolean, val previousAmount: java.math.BigDecimal?,
)
data class DetectedSubscriptionsResponse(val success: Boolean, val subscriptions: List<DetectedSubscriptionDto>, val estimatedMonthlyTotal: java.math.BigDecimal)

// Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- mirrors bank-mfe's
// lib/shopping.ts NearbyMerchantAd exactly.
data class NearbyAdDto(val id: String, val merchantId: String, val title: String, val description: String?, val radiusMeters: Int, val activeUntil: String)
data class NearbyMerchantAdDto(val ad: NearbyAdDto, val businessName: String, val distanceKm: Double)
data class NearbyMerchantAdsResponse(val success: Boolean, val ads: List<NearbyMerchantAdDto>)

// Mirrors services/backend/eats's real DTOs exactly (2026-07-18) -- backs the Eats mode
// folded into the Shop tab. Restaurant/menu browsing reuses ShoppingMerchantDto/
// MerchantProductDto above (a restaurant IS a Merchant, a menu item IS a
// MerchantProduct -- see rw.itunda.eats.EatsOrderService's own doc comment).
// selectedChoiceIds added 2026-07-21 (v1: required single-select only) -- one choice
// id per required option group on this menu item; omitted/null for any item with no
// option groups, the pre-existing, unaffected case. See MenuOptionGroup.kt's own doc
// comment on the backend for the full account.
data class EatsOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class PlaceEatsOrderRequest(
    val restaurantId: String,
    val items: List<EatsOrderItemRequest>,
    val deliveryAddress: String,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val deliveryNotes: String? = null,
    // Real Baemin-style 포장주문 (Pickup) order type (item 208) -- backend-complete
    // since 2026-07-26, bank-mfe client 2026-07-31; this is the first Android client.
    val fulfillmentType: String = "DELIVERY",
)
data class UpdateEatsOrderStatusRequest(val status: String)
data class SetRiderAvailabilityRequest(val available: Boolean)

// Real self-hosted address-search autocomplete (2026-07-18) -- backed by itunda's own
// Nominatim geocoder, not a third-party Maps API. See EatsOrderService.searchDeliveryAddress's
// own doc comment.
data class AddressSuggestionDto(val displayName: String, val latitude: Double, val longitude: Double)
data class AddressSearchResponse(val success: Boolean, val suggestions: List<AddressSuggestionDto>)

// Real post-delivery ratings & reviews (2026-07-18) -- see EatsReviewService's own doc
// comment. Ported from bank-mfe's own review UI, the template for this Android version.
data class SubmitEatsReviewRequest(
    val restaurantRating: Int,
    val restaurantComment: String? = null,
    val riderRating: Int,
    val riderComment: String? = null,
    // Real optional review photo (2026-08-04) -- see EatsReviewDto.photoUrl's own doc
    // comment.
    val photoUrl: String? = null,
)
data class EatsReviewDto(
    val id: String,
    val orderId: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String,
    val restaurantRating: Int,
    val restaurantComment: String?,
    val riderRating: Int,
    val riderComment: String?,
    // Real owner-side reply (item 184/185) -- see EatsReviewService.replyToRestaurantReview's
    // own doc comment. bank-mfe already has this (item 184); this is the first Android client.
    val ownerReply: String? = null,
    val ownerRepliedAt: String? = null,
    // Real optional review photo (2026-08-04) -- see docs/DESIGN_REFERENCES.md's Eats
    // recommendation #5 (food-delivery trust leans on real plated-food photos). Same
    // real-external-URL-only convention as Merchant.photoUrl -- a real URL the buyer
    // supplies, never an upload/storage pipeline.
    val photoUrl: String? = null,
    val createdAt: String,
)
data class EatsReviewResponse(val success: Boolean, val review: EatsReviewDto)
data class EatsReviewsResponse(val success: Boolean, val reviews: List<EatsReviewDto>)
data class ReplyToEatsReviewRequest(val reply: String)
data class EatsRatingResponse(val success: Boolean, val average: Double?, val count: Long)

data class EatsOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String?,
    val deliveryAddress: String,
    val itemsSubtotal: Double,
    val deliveryFee: Double,
    val platformFee: Double,
    val totalAmount: Double,
    val transactionId: String,
    val deliveryPayoutTransactionId: String?,
    val status: String,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val distanceKm: Double? = null,
    val deliveryNotes: String? = null,
)

// selectedOptionsJson added 2026-07-21 -- unitPrice above already includes every
// selected choice's priceDelta; this is purely a human-readable receipt summary, never
// a second pricing source. See EatsOrderItem.kt's own doc comment.
data class EatsOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)

data class RiderLocationDto(val latitude: Double, val longitude: Double, val updatedAt: String)
data class EatsRiderLocationResponse(val success: Boolean, val available: Boolean, val location: RiderLocationDto?)

// Real 배민오더-style table/QR in-store ordering (2026-07-25) -- see
// rw.itunda.eats.web.DineInOrderController. No delivery address/rider fields at all;
// totalAmount == itemsSubtotal since there's no delivery fee to add.
data class DineInOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class PlaceDineInOrderRequest(
    val restaurantId: String,
    val tableNumber: String,
    val items: List<DineInOrderItemRequest>,
    val notes: String? = null,
)
data class UpdateDineInOrderStatusRequest(val status: String)
data class DineInOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val tableNumber: String,
    val itemsSubtotal: Double,
    val platformFee: Double,
    val totalAmount: Double,
    val transactionId: String,
    val status: String,
    val notes: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val refundTransactionId: String? = null,
)
data class DineInOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class DineInOrderDetailResponse(val success: Boolean, val order: DineInOrderDto, val items: List<DineInOrderItemDto>)
data class DineInOrdersResponse(val success: Boolean, val orders: List<DineInOrderDto>)

data class RiderDto(val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
data class FavoriteRestaurantDto(val restaurantId: String, val businessName: String, val category: String?, val favoritedAt: String)
data class FavoriteRestaurantsResponse(val success: Boolean, val favorites: List<FavoriteRestaurantDto>)
data class AddFavoriteResponse(val success: Boolean)

// Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- see
// getMyEatsMembership's own doc comment.
data class EatsMembershipDto(
    val id: String,
    val userId: String,
    val activeUntil: String,
    val createdAt: String,
    val updatedAt: String,
)
data class EatsMembershipResponse(val success: Boolean, val membership: EatsMembershipDto?)
data class SubscribeEatsMembershipRequest(val days: Int)
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
// Real Investment-wallet top-up (2026-08-04) -- found via a fresh "defined but
// uncalled" endpoint sweep: StocksService.fundInvestmentWallet (a real MAIN ->
// INVESTMENT internal ledger transfer) had zero client anywhere, meaning a user with
// no pre-seeded investment balance had no in-app way to ever actually buy a stock.
data class FundInvestmentRequest(val amount: Double)
data class FundInvestmentTransactionDto(val id: String, val amount: Double, val completedAt: String)
data class FundInvestmentResponse(val success: Boolean, val transaction: FundInvestmentTransactionDto)
data class WatchStockResponse(val success: Boolean)
data class UnwatchStockResponse(val success: Boolean)

// Real Toss-style unified account overview (rw.itunda.overview.OverviewService) --
// found 2026-07-22 fully built on the backend with zero client UI anywhere (Android,
// iOS, or bank-mfe web). Aggregates wallets/savings/loans/investments/insurance/linked
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
data class OverviewResponse(
    val success: Boolean,
    val netWorth: java.math.BigDecimal,
    val accounts: List<AccountSummaryDto>,
    val savings: OverviewSavingsSummaryDto,
    val loans: OverviewLoansSummaryDto,
    val investments: OverviewInvestmentsSummaryDto,
    val insurance: OverviewInsuranceSummaryDto,
    val linkedAccounts: List<LinkedAccountSummaryDto>,
)

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
data class LoanAccountDto(val id: String, val userId: String, val walletId: String, val offerId: String, val principal: java.math.BigDecimal, val outstanding: java.math.BigDecimal, val interestRate: Double, val status: String, val disbursedAt: String)
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
    val walletId: String,
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
    val walletId: String,
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
    val issuedAt: String,
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
    val createdAt: String,
)
data class CardTransactionsResponse(val success: Boolean, val transactions: List<CardTransactionDto>, val totalElements: Long, val totalPages: Int)
data class SetCardLimitsRequest(val dailyLimit: java.math.BigDecimal, val monthlyLimit: java.math.BigDecimal)
data class ChargeCardRequest(val amount: java.math.BigDecimal, val merchantName: String)
data class ChargeCardResponse(val success: Boolean, val transaction: CardTransactionDto, val card: CardDto)

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
    val frozeWalletId: String?, val dueBy: String, val reviewedBy: String?, val createdAt: String, val resolvedAt: String?,
)
data class CreateSupportTicketResponse(val success: Boolean, val ticket: SupportTicketDto)
data class SupportTicketsResponse(val success: Boolean, val tickets: List<SupportTicketDto>)

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
// organizer via a real wallet-to-wallet push, no escrow.
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
    val walletId: String,
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
    val walletBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

data class CreateWeeklySavingsPlanRequest(
    val name: String,
    val baseWeeklyAmount: java.math.BigDecimal,
    val escalationRate: java.math.BigDecimal,
)

// POST /plans's real response shape is just {success, plan} -- unlike get/cancel/
// withdraw it never returns walletBalance/installments (a brand-new plan's wallet is
// always empty and has no installments yet), so this gets its own response type
// rather than reusing WeeklySavingsPlanDetailResponse with fields that would silently
// come back null/0.0 via Gson's reflection-based construction.
data class CreateWeeklySavingsPlanResponse(val success: Boolean, val plan: WeeklySavingsPlanDto)

data class WeeklySavingsActionResponse(
    val success: Boolean,
    val message: String,
    val plan: WeeklySavingsPlanDto,
    val walletBalance: Double,
    val installments: List<WeeklySavingsInstallmentDto>,
)

// Retrofit Interface to map to your Spring endpoints -- all require the real
// Bearer token NetworkClient's authInterceptor now injects (2026-07-11).
interface ApiService {
    @GET("api/v1/wallet")
    suspend fun getWallets(): WalletResponse

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.wallet.
    // WalletService.getSpendingInsight, real since 2026-07-13) -- first Android client
    // for this feature (item 107, found backend-only via a fresh matrix scan; bank-mfe
    // ported the same day as item 106). Ledger-based, not the transactions table -- see
    // the backend's own doc comment for the full account.
    @GET("api/v1/wallet/spending")
    suspend fun getSpendingInsight(): SpendingInsightResponse

    // Real Toss budgets/limits equivalent (item 165/172) -- WalletService.setBudget/
    // getBudgets, exposed on the pre-existing WalletController (no dedicated
    // controller). Per-category or overall (category == null) monthly limit, with
    // a real 80%/100%-threshold in-app Notification + push (maybeNotifyBudgetThreshold).
    @GET("api/v1/wallet/budgets")
    suspend fun getBudgets(): GetBudgetsResponse

    @POST("api/v1/wallet/budgets")
    suspend fun setBudget(@Body request: SetBudgetRequest): SetBudgetResponse

    // Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/176) -- see
    // AutoTopUpService's own doc comment. getSetting real-404s (AUTO_TOPUP_SETTING_NOT_
    // FOUND) if this wallet has no setting configured yet -- normal, not caught here.
    @GET("api/v1/wallet/{walletId}/auto-topup")
    suspend fun getAutoTopUpSetting(@Path("walletId") walletId: String): GetAutoTopUpSettingResponse

    @PUT("api/v1/wallet/{walletId}/auto-topup")
    suspend fun configureAutoTopUp(@Path("walletId") walletId: String, @Body request: ConfigureAutoTopUpRequest): GetAutoTopUpSettingResponse

    @POST("api/v1/wallet/{walletId}/auto-topup/trigger")
    suspend fun triggerAutoTopUp(@Path("walletId") walletId: String): TriggerAutoTopUpResponse

    // Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
    // rw.itunda.wallet.web.ForeignCurrencyController.
    @POST("api/v1/wallet/foreign-currency/wallets")
    suspend fun openForeignWallet(@Body request: OpenForeignWalletRequest): ForeignWalletResponse

    @GET("api/v1/wallet/foreign-currency/wallets")
    suspend fun getForeignWallets(): ForeignWalletsResponse

    @GET("api/v1/wallet/foreign-currency/rate")
    suspend fun getExchangeRate(@Query("from") from: String, @Query("to") to: String): ExchangeRateResponse

    @POST("api/v1/wallet/foreign-currency/convert")
    suspend fun convertCurrency(@Body request: ConvertCurrencyRequest): CurrencyConversionResponse

    @GET("api/v1/wallet/foreign-currency/conversions")
    suspend fun getMyConversions(): CurrencyConversionsResponse

    @GET("api/v1/discover")
    suspend fun getDiscoverItems(): DiscoverResponse

    @GET("api/v1/savings/goals")
    suspend fun getSavingsGoals(): SavingsGoalsResponse

    @GET("api/v1/savings/interest-jar")
    suspend fun getInterestJar(): InterestJarResponse

    @POST("api/v1/wallet/transfer/quote")
    suspend fun quoteTransfer(@Body request: QuoteTransferRequest): QuoteTransferResponse

    @POST("api/v1/wallet/transfer/confirm")
    suspend fun confirmTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConfirmTransferRequest): ConfirmTransferResponse

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // MainViewModel.sendTransfer.
    @POST("api/v1/p2p/send")
    suspend fun sendDirect(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendDirectP2pRequest): SendDirectP2pResponse

    @POST("api/v1/p2p/request")
    suspend fun generateP2pRequest(@Body request: GenerateP2pRequest): GenerateP2pRequestResponse

    @GET("api/v1/p2p/requests")
    suspend fun getMyP2pRequests(): GetP2pRequestsResponse

    @POST("api/v1/p2p/pay/{requestId}")
    suspend fun payP2pRequest(@Path("requestId") requestId: String, @Header("Idempotency-Key") idempotencyKey: String): PayP2pRequestResponse

    // Real idempotency fix (item 235, found via a periodic Idempotency-Key coverage
    // audit) -- a retry created a second active recurring-transfer row to the same
    // recipient, which the scheduler then executes independently: a real duplicate
    // charge every period going forward. See backend AutoTransferController.create's
    // own doc comment for the full account.
    @POST("api/v1/p2p/auto-transfers")
    suspend fun createAutoTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateAutoTransferRequest): AutoTransferResponse

    @GET("api/v1/p2p/auto-transfers")
    suspend fun getMyAutoTransfers(): AutoTransfersListResponse

    @POST("api/v1/p2p/auto-transfers/{id}/pause")
    suspend fun pauseAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @POST("api/v1/p2p/auto-transfers/{id}/resume")
    suspend fun resumeAutoTransfer(@Path("id") id: String): AutoTransferResponse

    @DELETE("api/v1/p2p/auto-transfers/{id}")
    suspend fun cancelAutoTransfer(@Path("id") id: String): AutoTransferResponse

    // Real Toss 예약송금 (scheduled/reserved one-time transfer) -- see
    // ScheduledTransferDto's own doc comment. ScheduledTransferController existed fully
    // on the backend with zero client anywhere until now.
    @POST("api/v1/p2p/scheduled-transfers")
    suspend fun createScheduledTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateScheduledTransferRequest): ScheduledTransferResponse

    @GET("api/v1/p2p/scheduled-transfers")
    suspend fun getMyScheduledTransfers(): ScheduledTransfersListResponse

    @POST("api/v1/p2p/scheduled-transfers/{id}/cancel")
    suspend fun cancelScheduledTransfer(@Path("id") id: String): ScheduledTransferResponse

    @GET("api/v1/p2p/scam-reports/check")
    suspend fun checkScamStatus(@Query("identifier") identifier: String): ScamCheckResponse

    @POST("api/v1/p2p/scam-reports")
    suspend fun reportScam(@Body request: ReportScamRequest): ScamReportResponse

    @GET("api/v1/p2p/scam-reports/mine")
    suspend fun getMyScamReports(): ScamReportsListResponse

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // No Idempotency-Key -- a settings write, not money movement itself. See
    // RoundUpController.kt's own doc comment.
    @GET("api/v1/savings/round-up")
    suspend fun getRoundUpSettings(): RoundUpSettingsResponse

    @POST("api/v1/savings/round-up")
    suspend fun setRoundUpSettings(@Body request: SetRoundUpSettingsRequest): RoundUpSettingsResponse

    @POST("api/v1/wallet/agent-withdrawal-authorizations")
    suspend fun createAgentWithdrawalAuthorization(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateAgentWithdrawalAuthorizationRequest,
    ): AgentWithdrawalAuthorizationResponse

    @GET("api/v1/wallet/agent-withdrawal-authorizations")
    suspend fun getAgentWithdrawalAuthorizations(): AgentWithdrawalAuthorizationsResponse

    @POST("api/v1/wallet/agent-withdrawal-authorizations/cancel")
    suspend fun cancelAgentWithdrawalAuthorization(@Body request: CancelAgentWithdrawalAuthorizationRequest): AgentWithdrawalAuthorizationResponse

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen; see services/backend/wallet's new WalletController endpoint.
    @GET("api/v1/wallet/transactions")
    suspend fun getTransactionHistory(): TransactionHistoryResponse

    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): MarkReadResponse

    // Real offline-action-queue replay (2026-07-13) -- see network/OfflineActionQueue.kt.
    @POST("api/v1/actions/batch")
    suspend fun submitActionBatch(@Body request: BatchRequest): BatchResponse

    // Real Partner SDK catalog (2026-07-17) -- services/backend/partners's
    // MiniAppCatalogController, itunda-user-JWT-gated like every other endpoint on this
    // interface (no ADMIN role, no partner API key -- this is the public "app store"
    // surface a logged-in itunda user's own client fetches). See
    // miniapps/PartnerMiniAppLoader.kt for what actually happens when one of these is tapped.
    @GET("api/v1/mini-apps/catalog")
    suspend fun getMiniAppCatalog(): MiniAppCatalogResponse

    // Real 1:1 messaging (2026-07-18) -- see rw.itunda.messaging.web.MessagingController.
    @POST("api/v1/messages/conversations")
    suspend fun startConversation(@Body request: StartConversationRequest): ConversationResponse

    @GET("api/v1/messages/conversations")
    suspend fun getConversations(@Query("archived") archived: Boolean = false): ConversationsResponse

    @GET("api/v1/messages/contacts")
    suspend fun getTalkContacts(): TalkContactsResponse

    @GET("api/v1/messages/conversations/{id}/messages")
    suspend fun getMessages(@Path("id") conversationId: String): MessagesResponse

    @GET("api/v1/messages/conversations/{id}/messages/search")
    suspend fun searchMessages(@Path("id") conversationId: String, @Query("query") query: String): MessagesResponse

    @POST("api/v1/messages/conversations/{id}/messages")
    suspend fun sendMessage(@Path("id") conversationId: String, @Body request: SendMessageRequest): MessageResponse

    @DELETE("api/v1/messages/conversations/{id}/messages/{messageId}")
    suspend fun deleteMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    // Real Thread support (2026-08-05) -- see MessageDto.replyCount's own doc comment.
    // Root message first, then every direct reply oldest-first.
    @GET("api/v1/messages/conversations/{id}/messages/{messageId}/thread")
    suspend fun getThread(@Path("id") conversationId: String, @Path("messageId") messageId: String): MessagesResponse

    // Real message forwarding (2026-08-04) -- see ForwardMessageRequest's own doc
    // comment: MessageForwardService (backend, 2026-07-25) already fully supported
    // forwarding any 1:1 or group message to any 1:1 or group destination, with zero
    // Retrofit method anywhere -- the same "defined but uncalled" pattern this session
    // already found for group pin/photo messages. Two methods per real source (this
    // message is the source) since the response shape depends on which destination type
    // the caller picks -- both hit the identical real endpoint, Retrofit routes purely
    // by Kotlin signature, not a conflict.
    @POST("api/v1/messages/messages/{id}/forward")
    suspend fun forwardDirectMessageToConversation(@Path("id") messageId: String, @Body request: ForwardMessageRequest): MessageResponse

    @POST("api/v1/messages/messages/{id}/forward")
    suspend fun forwardDirectMessageToGroup(@Path("id") messageId: String, @Body request: ForwardMessageRequest): GroupMessageResponse

    @POST("api/v1/messages/groups/messages/{id}/forward")
    suspend fun forwardGroupMessageToConversation(@Path("id") messageId: String, @Body request: ForwardMessageRequest): MessageResponse

    @POST("api/v1/messages/groups/messages/{id}/forward")
    suspend fun forwardGroupMessageToGroup(@Path("id") messageId: String, @Body request: ForwardMessageRequest): GroupMessageResponse

    @GET("api/v1/messages/conversations/{id}/pin")
    suspend fun getPinnedConversationMessage(@Path("id") conversationId: String): PinnedMessageResponse

    @POST("api/v1/messages/conversations/{id}/pin/{messageId}")
    suspend fun pinConversationMessage(@Path("id") conversationId: String, @Path("messageId") messageId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/pin")
    suspend fun unpinConversationMessage(@Path("id") conversationId: String): SuccessResponse

    @POST("api/v1/messages/conversations/{id}/block")
    suspend fun blockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @DELETE("api/v1/messages/conversations/{id}/block")
    suspend fun unblockConversationParticipant(@Path("id") conversationId: String): SuccessResponse

    @GET("api/v1/messages/conversations/{id}/quiet")
    suspend fun getConversationQuiet(@Path("id") conversationId: String): ConversationQuietResponse

    @POST("api/v1/messages/conversations/{id}/quiet")
    suspend fun setConversationQuiet(@Path("id") conversationId: String, @Body request: SetConversationQuietRequest): ConversationQuietResponse

    @GET("api/v1/messages/conversations/{id}/archive")
    suspend fun getConversationArchived(@Path("id") conversationId: String): ConversationArchivedResponse

    @POST("api/v1/messages/conversations/{id}/archive")
    suspend fun setConversationArchived(@Path("id") conversationId: String, @Body request: SetConversationArchivedRequest): ConversationArchivedResponse

    @POST("api/v1/chat/reports")
    suspend fun reportChatMessage(@Body request: CreateChatReportRequest): SuccessResponse

    // Real toggle -- tapping an already-active reaction removes it, same semantics as
    // MessagingService.toggleReaction on the backend.
    @POST("api/v1/messages/messages/{id}/reactions")
    suspend fun toggleReaction(@Path("id") messageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    // Real group chat (2026-07-18) -- see rw.itunda.messaging.web.GroupMessagingController.
    @POST("api/v1/messages/groups")
    suspend fun createGroup(@Body request: CreateGroupRequest): GroupResponse

    @GET("api/v1/messages/groups")
    suspend fun getMyGroups(): GroupsResponse

    @GET("api/v1/messages/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): GroupMessagesResponse

    @POST("api/v1/messages/groups/{id}/messages")
    suspend fun sendGroupMessage(@Path("id") groupId: String, @Body request: SendGroupMessageRequest): GroupMessageResponse

    @DELETE("api/v1/messages/groups/{id}/messages/{messageId}")
    suspend fun deleteGroupMessage(@Path("id") groupId: String, @Path("messageId") messageId: String): SuccessResponse

    // Real Thread support (2026-08-05) -- see getThread's own doc comment; identical
    // shape for group chat.
    @GET("api/v1/messages/groups/{id}/messages/{messageId}/thread")
    suspend fun getGroupThread(@Path("id") groupId: String, @Path("messageId") messageId: String): GroupMessagesResponse

    // Real group-chat pin (2026-08-04) -- see GroupThreadView's own doc comment: the
    // real backend (GroupMessagingController, 2026-07-26) had no Retrofit client method
    // or UI anywhere until now. Mirrors getPinnedConversationMessage/
    // pinConversationMessage/unpinConversationMessage's own 1:1 shape exactly.
    @GET("api/v1/messages/groups/{id}/pin")
    suspend fun getPinnedGroupMessage(@Path("id") groupId: String): GroupPinnedMessageResponse

    @POST("api/v1/messages/groups/{id}/pin/{messageId}")
    suspend fun pinGroupMessage(@Path("id") groupId: String, @Path("messageId") messageId: String): SuccessResponse

    @DELETE("api/v1/messages/groups/{id}/pin")
    suspend fun unpinGroupMessage(@Path("id") groupId: String): SuccessResponse

    @POST("api/v1/messages/groups/messages/{id}/reactions")
    suspend fun toggleGroupReaction(@Path("id") groupMessageId: String, @Body request: ToggleReactionRequest): ReactionsResponse

    @POST("api/v1/messages/groups/{id}/members")
    suspend fun addGroupMember(@Path("id") groupId: String, @Body request: AddGroupMemberRequest): GroupResponse

    @DELETE("api/v1/messages/groups/{id}/members/me")
    suspend fun leaveGroup(@Path("id") groupId: String): LeaveGroupResponse

    @GET("api/v1/messages/groups/{id}/members")
    suspend fun getGroupMembers(@Path("id") groupId: String): GroupMembersResponse

    @POST("api/v1/messages/groups/{id}/photo")
    suspend fun setGroupPhotoUrl(@Path("id") groupId: String, @Body request: SetGroupPhotoUrlRequest): GroupSummaryResponse

    @POST("api/v1/messages/groups/{id}/description")
    suspend fun setGroupDescription(@Path("id") groupId: String, @Body request: SetGroupDescriptionRequest): GroupSummaryResponse

    // Real online/offline presence (2026-07-19) -- see MessagingService.getPresence's
    // own doc comment on the backend. Works for any set of user ids, not just 1:1
    // conversation partners -- e.g. a group thread can pass every member's id.
    @GET("api/v1/messages/presence")
    suspend fun getPresence(@Query("userIds") userIds: List<String>): PresenceResponse

    // Real photo upload (2026-07-24) -- see rw.itunda.marketplace.web.UploadController.
    @Multipart
    @POST("api/v1/uploads")
    suspend fun uploadPhoto(@Part file: okhttp3.MultipartBody.Part): UploadResponse

    // Real 당근마켓-style marketplace (2026-07-18) -- see rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings")
    suspend fun createListing(@Body request: CreateListingRequest): ListingResponse

    @GET("api/v1/marketplace/listings")
    suspend fun browseListings(@Query("category") category: String? = null): ListingsResponse

    @GET("api/v1/marketplace/listings/nearby")
    suspend fun getNearbyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): ListingsResponse

    @GET("api/v1/marketplace/my-listings")
    suspend fun getMyListings(): ListingsResponse

    // Real "My purchases" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend ListingRepository's own doc comment.
    @GET("api/v1/marketplace/my-purchases")
    suspend fun getMyPurchases(): ListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Real 400 NEIGHBORHOOD_NOT_SET if the caller hasn't set one yet.
    @GET("api/v1/marketplace/listings/my-neighborhood")
    suspend fun getListingsMyNeighborhood(@Query("category") category: String? = null): ListingsResponse

    @POST("api/v1/marketplace/listings/{id}/mark-sold")
    suspend fun markListingSold(@Path("id") listingId: String, @Body request: MarkSoldRequest = MarkSoldRequest()): ListingResponse

    // Real seller-paid sponsored placement (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.boostListing.
    @GET("api/v1/marketplace/boost-tiers")
    suspend fun getBoostTiers(): BoostTiersResponse

    @POST("api/v1/marketplace/listings/{id}/boost")
    suspend fun boostListing(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: BoostListingRequest): ListingResponse

    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings/{id}/pay-escrow")
    suspend fun payEscrow(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/confirm-receipt")
    suspend fun confirmEscrowReceipt(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String): MarketplaceEscrowResponse

    @POST("api/v1/marketplace/listings/{id}/dispute-escrow")
    suspend fun disputeEscrow(@Path("id") listingId: String, @Body request: DisputeEscrowRequest): MarketplaceEscrowResponse

    @GET("api/v1/marketplace/listings/{id}/escrow")
    suspend fun getEscrow(@Path("id") listingId: String): MarketplaceEscrowResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/marketplace/listings/{id}/review")
    suspend fun submitListingReview(@Path("id") listingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/marketplace/listings/{id}/review")
    suspend fun getListingReviews(@Path("id") listingId: String): HoodReviewsResponse

    @DELETE("api/v1/marketplace/listings/{id}")
    suspend fun removeListing(@Path("id") listingId: String): ListingResponse

    @POST("api/v1/marketplace/listings/{id}/contact-seller")
    suspend fun contactSeller(@Path("id") listingId: String): ContactSellerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PriceOfferService.
    @POST("api/v1/marketplace/listings/{id}/offers")
    suspend fun makeOffer(@Path("id") listingId: String, @Body request: MakeOfferRequest): PriceOfferResponse

    @POST("api/v1/marketplace/offers/{id}/respond")
    suspend fun respondToOffer(@Path("id") offerId: String, @Body request: RespondToOfferRequest): PriceOfferResponse

    @GET("api/v1/marketplace/conversations/{id}/offers")
    suspend fun getOffersForConversation(@Path("id") conversationId: String): PriceOffersResponse

    // Real Marketplace listing wishlist (2026-07-21 backend + bank-mfe) -- ported here,
    // closing the "Android/iOS don't have this yet" gap that row's own doc comment
    // named. Mirrors addFavoriteRestaurant/removeFavoriteRestaurant exactly.
    @POST("api/v1/marketplace/listings/{id}/favorite")
    suspend fun addListingFavorite(@Path("id") listingId: String): AddFavoriteResponse

    @DELETE("api/v1/marketplace/listings/{id}/favorite")
    suspend fun removeListingFavorite(@Path("id") listingId: String): RemoveFavoriteResponse

    @GET("api/v1/marketplace/listings/favorites")
    suspend fun getMyFavoriteListings(): FavoriteListingsResponse

    // Real like/unlike toggle (2026-08-03) -- see backend MarketplaceController.kt's
    // own doc comment. Idempotent, matching addListingFavorite/removeListingFavorite's
    // own real toggle discipline above.
    @POST("api/v1/marketplace/listings/{id}/like")
    suspend fun toggleListingLike(@Path("id") listingId: String): ToggleLikeResponse

    // Real 당근마켓-style Keyword Alert (rw.itunda.marketplace.KeywordAlertService, real
    // since before this session) -- first Android client for this feature (item 115,
    // found via a content-grep sweep confirming zero client anywhere; bank-mfe ported
    // it the same day as item 114). Real, published Karrot 30-keyword-per-user cap
    // enforced server-side.
    @POST("api/v1/marketplace/keyword-alerts")
    suspend fun addKeywordAlert(@Body request: AddKeywordAlertRequest): KeywordAlertResponse

    @GET("api/v1/marketplace/keyword-alerts")
    suspend fun getKeywordAlerts(): KeywordAlertsResponse

    @DELETE("api/v1/marketplace/keyword-alerts/{id}")
    suspend fun removeKeywordAlert(@Path("id") alertId: String): SuccessResponse

    @POST("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun setKeywordAlertQuietHours(@Body request: SetKeywordAlertQuietHoursRequest): KeywordAlertQuietHoursResponse

    @GET("api/v1/marketplace/keyword-alerts/quiet-hours")
    suspend fun getKeywordAlertQuietHours(): KeywordAlertQuietHoursResponse

    @POST("api/v1/hood/reports")
    suspend fun reportHoodContent(@Body request: CreateHoodReportRequest): HoodReportResponse

    // Real KakaoTalk-style gift send/claim (2026-07-20) -- see GiftService.
    @POST("api/v1/gifts/conversations/{id}")
    suspend fun sendGiftInConversation(
        @Path("id") conversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendGiftInConversationRequest,
    ): GiftResponse

    @POST("api/v1/gifts/{id}/claim")
    suspend fun claimGift(@Path("id") giftId: String, @Header("Idempotency-Key") idempotencyKey: String): GiftResponse

    @GET("api/v1/gifts/conversations/{id}")
    suspend fun getGiftsForConversation(@Path("id") conversationId: String): GiftsResponse

    // Real KakaoTalk Emoticon Store (item 135) -- see backend Emoticon.kt's own doc
    // comment. Mirrors bank-mfe's lib/emoticons.ts exactly (item 133, this session's
    // first client for this feature).
    @GET("api/v1/emoticons/packs")
    suspend fun getEmoticonPacks(): EmoticonPacksResponse

    @GET("api/v1/emoticons/packs/{packId}")
    suspend fun getPackEmoticons(@Path("packId") packId: String): EmoticonsResponse

    @GET("api/v1/emoticons/packs/owned")
    suspend fun getOwnedEmoticonPacks(): OwnedEmoticonPacksResponse

    @POST("api/v1/emoticons/packs/{packId}/purchase")
    suspend fun purchaseEmoticonPack(@Path("packId") packId: String): OwnedEmoticonPackResponse

    @POST("api/v1/emoticons/packs/{packId}/gift")
    suspend fun giftEmoticonPack(@Path("packId") packId: String, @Body request: GiftEmoticonPackRequest): GiftedEmoticonPackResponse

    @POST("api/v1/emoticons/conversations/{id}/send")
    suspend fun sendEmoticon(@Path("id") conversationId: String, @Body request: SendEmoticonRequest): MessageResponse

    @POST("api/v1/emoticons/groups/{id}/send")
    suspend fun sendGroupEmoticon(@Path("id") groupId: String, @Body request: SendEmoticonRequest): GroupMessageResponse

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    @GET("api/v1/community/categories")
    suspend fun getCommunityCategories(): CommunityCategoriesResponse

    @POST("api/v1/community/posts")
    suspend fun createCommunityPost(@Body request: CreateCommunityPostRequest): CommunityPostResponse

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // rw.itunda.community.web.CommunityController.upcomingMeetups.
    @GET("api/v1/community/meetups/upcoming")
    suspend fun getUpcomingMeetups(): CommunityPostsResponse

    @GET("api/v1/community/posts")
    suspend fun browseCommunityPosts(@Query("category") category: String? = null): CommunityPostsResponse

    @GET("api/v1/community/posts/nearby")
    suspend fun getNearbyCommunityPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): CommunityPostsResponse

    @GET("api/v1/community/my-posts")
    suspend fun getMyCommunityPosts(): CommunityPostsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/community/posts/my-neighborhood")
    suspend fun getCommunityPostsMyNeighborhood(@Query("category") category: String? = null): CommunityPostsResponse

    @GET("api/v1/community/posts/{id}")
    suspend fun getCommunityPost(@Path("id") postId: String): CommunityPostDetailResponse

    @DELETE("api/v1/community/posts/{id}")
    suspend fun removeCommunityPost(@Path("id") postId: String): CommunityPostResponse

    @GET("api/v1/community/posts/{id}/comments")
    suspend fun getCommunityComments(@Path("id") postId: String): CommunityCommentsResponse

    @POST("api/v1/community/posts/{id}/comments")
    suspend fun addCommunityComment(@Path("id") postId: String, @Body request: AddCommunityCommentRequest): CommunityCommentResponse

    @POST("api/v1/community/posts/{id}/like")
    suspend fun toggleCommunityLike(@Path("id") postId: String): ToggleCommunityLikeResponse

    // Real 같이해요 (join-together) explicit 참여하기 tap (2026-07-24) -- see backend
    // CommunityService.joinMeetup's own doc comment.
    @POST("api/v1/community/posts/{id}/join")
    suspend fun joinCommunityMeetup(@Path("id") postId: String): JoinMeetupResponse

    // Real 당근모임 (Karrot Meetups) recurring schedule + attendance check-in -- see
    // rw.itunda.community.CommunityService.scheduleMeetupSessions/checkIntoSession's own
    // doc comments. bank-mfe already has this; this is the first Android client.
    @POST("api/v1/community/posts/{postId}/sessions")
    suspend fun scheduleMeetupSessions(@Path("postId") postId: String, @Body request: ScheduleMeetupSessionsRequest): MeetupSessionsResponse

    @GET("api/v1/community/posts/{postId}/sessions")
    suspend fun getMeetupSessions(@Path("postId") postId: String): MeetupSessionsResponse

    @POST("api/v1/community/sessions/{sessionId}/check-in")
    suspend fun checkIntoMeetupSession(@Path("sessionId") sessionId: String): MeetupAttendanceResponse

    // Real 당근마켓 같이사요 (Karrot "Let's Buy Together") -- see
    // rw.itunda.community.CommunityService.finalizeGroupBuy's own doc comment. bank-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/community/posts/{postId}/finalize-group-buy")
    suspend fun finalizeGroupBuy(@Path("postId") postId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: FinalizeGroupBuyRequest): SuccessResponse

    // Real 당근알바-style local job board (2026-07-19) -- see rw.itunda.jobs.web.JobPostController.
    @GET("api/v1/jobs/categories")
    suspend fun getJobCategories(): JobCategoriesResponse

    @POST("api/v1/jobs/posts")
    suspend fun createJobPost(@Body request: CreateJobPostRequest): JobPostResponse

    @GET("api/v1/jobs/posts")
    suspend fun browseJobPosts(@Query("category") category: String? = null): JobPostsResponse

    @GET("api/v1/jobs/posts/nearby")
    suspend fun getNearbyJobPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): JobPostsResponse

    @GET("api/v1/jobs/my-posts")
    suspend fun getMyJobPosts(): JobPostsResponse

    // Real "Jobs I did" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend JobPostRepository's own doc comment.
    @GET("api/v1/jobs/my-worked-posts")
    suspend fun getMyWorkedJobPosts(): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/favorite")
    suspend fun addJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @DELETE("api/v1/jobs/posts/{id}/favorite")
    suspend fun removeJobPostFavorite(@Path("id") jobPostId: String): SuccessResponse

    @GET("api/v1/jobs/posts/favorites")
    suspend fun getMyFavoriteJobPosts(): FavoriteJobPostsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/jobs/posts/my-neighborhood")
    suspend fun getJobPostsMyNeighborhood(@Query("category") category: String? = null): JobPostsResponse

    @POST("api/v1/jobs/posts/{id}/mark-filled")
    suspend fun markJobPostFilled(@Path("id") jobPostId: String, @Body request: MarkFilledRequest = MarkFilledRequest()): JobPostResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/review")
    suspend fun submitJobPostReview(@Path("id") jobPostId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/jobs/posts/{id}/review")
    suspend fun getJobPostReviews(@Path("id") jobPostId: String): HoodReviewsResponse

    @DELETE("api/v1/jobs/posts/{id}")
    suspend fun removeJobPost(@Path("id") jobPostId: String): JobPostResponse

    @POST("api/v1/jobs/posts/{id}/contact-poster")
    suspend fun contactPoster(@Path("id") jobPostId: String): ContactPosterResponse

    // Real single-post detail fetch -- see JobPostController.getPost's own contract.
    @GET("api/v1/jobs/posts/{id}")
    suspend fun getJobPost(@Path("id") jobPostId: String): JobPostResponse

    // Real 당근알바-style structured application (2026-07-25) -- see backend
    // JobApplicationService's own doc comment.
    @POST("api/v1/jobs/posts/{id}/apply")
    suspend fun applyToJob(@Path("id") jobPostId: String, @Body request: ApplyToJobRequest): JobApplicationResponse

    @GET("api/v1/jobs/posts/{id}/applications")
    suspend fun getApplicationsForJobPost(@Path("id") jobPostId: String): JobApplicationsResponse

    @GET("api/v1/jobs/my-applications")
    suspend fun getMyJobApplications(): JobApplicationsResponse

    @POST("api/v1/jobs/applications/{id}/respond")
    suspend fun respondToJobApplication(@Path("id") applicationId: String, @Body request: RespondToApplicationRequest): JobApplicationResponse

    // Real 당근부동산-style property listing (2026-07-19) -- see rw.itunda.realestate.web.PropertyListingController.
    @GET("api/v1/realestate/property-types")
    suspend fun getPropertyTypes(): PropertyTypesResponse

    @POST("api/v1/realestate/listings")
    suspend fun createPropertyListing(@Body request: CreatePropertyListingRequest): PropertyListingResponse

    @GET("api/v1/realestate/listings")
    suspend fun browsePropertyListings(
        @Query("listingType") listingType: String? = null,
        @Query("propertyType") propertyType: String? = null,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/valuation")
    suspend fun getPropertyValuation(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("propertyType") propertyType: String,
        @Query("listingType") listingType: String,
        @Query("sizeSqm") sizeSqm: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): PropertyValuationResponse

    @GET("api/v1/realestate/listings/nearby")
    suspend fun getNearbyPropertyListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): PropertyListingsResponse

    @GET("api/v1/realestate/my-listings")
    suspend fun getMyPropertyListings(): PropertyListingsResponse

    // Real "Places I got" (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 4
    // recommendation #6. See backend PropertyListingRepository's own doc comment.
    @GET("api/v1/realestate/my-acquired-listings")
    suspend fun getMyAcquiredPropertyListings(): PropertyListingsResponse
    @POST("api/v1/realestate/listings/{id}/favorite") suspend fun addPropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @DELETE("api/v1/realestate/listings/{id}/favorite") suspend fun removePropertyListingFavorite(@Path("id") id: String): SuccessResponse
    @GET("api/v1/realestate/listings/favorites") suspend fun getMyFavoritePropertyListings(): FavoritePropertyListingsResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    // Deliberately not combined with listingType/propertyType filters -- an honest v1
    // scoping choice, same as the real backend endpoint this calls.
    @GET("api/v1/realestate/listings/my-neighborhood")
    suspend fun getPropertyListingsMyNeighborhood(): PropertyListingsResponse

    @POST("api/v1/realestate/listings/{id}/mark-taken")
    suspend fun markPropertyListingTaken(@Path("id") propertyListingId: String, @Body request: MarkTakenRequest = MarkTakenRequest()): PropertyListingResponse

    // Real ownership verification (2026-07-25) -- see backend PropertyOwnershipService's
    // own doc comment. documentUrl comes from uploadPhoto() above.
    @POST("api/v1/realestate/listings/{id}/verify-ownership")
    suspend fun submitPropertyOwnershipVerification(
        @Path("id") propertyListingId: String,
        @Body request: SubmitOwnershipVerificationRequest,
    ): PropertyOwnershipSubmissionResponse

    // Real post-transaction review with asymmetric public/private visibility
    // (2026-07-24) -- see backend HoodReviewService's own doc comment.
    @POST("api/v1/realestate/listings/{id}/review")
    suspend fun submitPropertyListingReview(@Path("id") propertyListingId: String, @Body request: SubmitHoodReviewRequest): HoodReviewResponse

    @GET("api/v1/realestate/listings/{id}/review")
    suspend fun getPropertyListingReviews(@Path("id") propertyListingId: String): HoodReviewsResponse

    @DELETE("api/v1/realestate/listings/{id}")
    suspend fun removePropertyListing(@Path("id") propertyListingId: String): PropertyListingResponse

    @POST("api/v1/realestate/listings/{id}/contact-lister")
    suspend fun contactLister(@Path("id") propertyListingId: String): ContactListerResponse

    // Real 당근-style price-offer negotiation (2026-07-19) -- see PropertyPriceOfferService.
    @POST("api/v1/realestate/listings/{id}/offers")
    suspend fun makePropertyOffer(@Path("id") propertyListingId: String, @Body request: MakePropertyOfferRequest): PropertyPriceOfferResponse

    @POST("api/v1/realestate/offers/{id}/respond")
    suspend fun respondToPropertyOffer(@Path("id") offerId: String, @Body request: RespondToPropertyOfferRequest): PropertyPriceOfferResponse

    @GET("api/v1/realestate/conversations/{id}/offers")
    suspend fun getPropertyOffersForConversation(@Path("id") conversationId: String): PropertyPriceOffersResponse

    // Real per-merchant public product browse (2026-07-18) -- see
    // rw.itunda.merchant.web.ShoppingController.getMerchantProducts.
    @GET("api/v1/shopping/merchants")
    suspend fun getShoppingMerchants(
        @Query("category") category: String? = null,
        @Query("q") q: String? = null,
        // Real browse-card enrichment (2026-07-21) -- see ShoppingMerchantDto's own doc
        // comment. Omitted (null) means no real distanceKm/deliveryTimeMinutes back.
        @Query("buyerLat") buyerLat: Double? = null,
        @Query("buyerLng") buyerLng: Double? = null,
    ): ShoppingMerchantsResponse

    // Real "search this map" + "directions" (2026-07-19) -- see MapsService.
    @GET("api/v1/maps/search")
    suspend fun searchPlaces(@Query("q") q: String): MapsSearchResponse

    // Backs the ruler (distance-measurement) tool's "what's here" label -- ported from
    // bank-mfe's own real reverseGeocode call, 2026-07-23.
    @GET("api/v1/maps/reverse")
    suspend fun reverseGeocode(@Query("lat") lat: Double, @Query("lng") lng: Double): MapsReverseGeocodeResponse

    // mode added 2026-07-22 (default "DRIVING") -- see OsrmRoutingClient.route's own doc
    // comment on the backend for the real, separately-deployed foot-profile OSRM
    // instance this now lets a caller reach.
    @GET("api/v1/maps/directions")
    suspend fun getDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsResponse

    // Real alternative routes (2026-07-22) -- see MapsDirectionsAlternativesResponse's
    // own doc comment.
    @GET("api/v1/maps/directions/alternatives")
    suspend fun getDirectionsAlternatives(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
        @Query("mode") mode: String = "DRIVING",
    ): MapsDirectionsAlternativesResponse

    @POST("api/v1/maps/directions/itinerary")
    suspend fun getItineraryDirections(
        @Body request: ItineraryDirectionsRequest,
    ): MapsItineraryResponse

    // Real "nearby places" category search + bookmarked/favorite places (2026-07-19) --
    // see rw.itunda.maps.MapsService's own doc comment on the backend.
    @GET("api/v1/maps/nearby")
    suspend fun searchNearbyPlaces(
        @Query("category") category: String,
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 2.0,
    ): MapNearbyResponse

    // Real "Smart Around"-style default map state (2026-08-04) -- see MapAroundMeResponse's
    // own doc comment.
    @GET("api/v1/maps/around-me")
    suspend fun getMapAroundMe(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radiusKm") radiusKm: Double = 2.0,
    ): MapAroundMeResponse

    @GET("api/v1/maps/trending")
    suspend fun getMapTrending(
        @Query("days") days: Int = 7,
        @Query("limit") limit: Int = 10,
    ): MapTrendingResponse

    @GET("api/v1/agents/nearby")
    suspend fun searchNearbyAgents(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): NearbyAgentsResponse

    @GET("api/v1/maps/bookmarks")
    suspend fun getMyMapBookmarks(): MapBookmarksResponse

    @POST("api/v1/maps/bookmarks")
    suspend fun addMapBookmark(@Body request: AddMapBookmarkRequest): AddMapBookmarkResponse

    // Real "move to folder" (2026-07-22) -- see MoveMapBookmarkRequest's own doc comment.
    @PATCH("api/v1/maps/bookmarks")
    suspend fun moveMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double, @Body request: MoveMapBookmarkRequest): MoveMapBookmarkResponse

    @DELETE("api/v1/maps/bookmarks")
    suspend fun removeMapBookmark(@Query("lat") lat: Double, @Query("lng") lng: Double): RemoveMapBookmarkResponse

    // Real Naver Map-style public/private folder + share (2026-08-04) -- see
    // SetMapFolderVisibilityRequest's own doc comment.
    @PATCH("api/v1/maps/bookmarks/folder-visibility")
    suspend fun setMapFolderVisibility(@Body request: SetMapFolderVisibilityRequest): SetMapFolderVisibilityResponse

    // Deliberately unauthenticated on the backend (SecurityConfig permitAll) -- whoever
    // opens a real share link doesn't need an itunda session. Reuses the same NetworkClient
    // Retrofit instance, which is harmless: a caller who IS logged in just attaches a token
    // the backend never required for this specific path.
    @GET("api/v1/maps/shared/{userId}/{folderName}")
    suspend fun getSharedMapFolder(@Path("userId") userId: String, @Path("folderName") folderName: String): SharedMapFolderResponse

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    @GET("api/v1/shopping/merchants/categories")
    suspend fun getMerchantCategories(): MerchantCategoriesResponse

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    @GET("api/v1/shopping/products/deals")
    suspend fun getShopDeals(): DealsResponse

    // Real Coupang Eats-style dish grid (2026-08-03) -- see EatsDishDto's own doc
    // comment for the sourcing.
    @GET("api/v1/eats/dishes")
    suspend fun getEatsDishes(@Query("category") category: String? = null): EatsDishesResponse

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment.
    @GET("api/v1/time-deals")
    suspend fun getActiveTimeDeals(): TimeDealsResponse

    // Real Naver Pay 멤버십 데이 (Membership Day) cashback boost -- see
    // rw.itunda.merchant.ShoppingCashbackService's own doc comment. bank-mfe already has
    // this; this is the first Android client.
    @GET("api/v1/shopping/membership-day")
    suspend fun getMembershipDayStatus(): MembershipDayStatusResponse

    @POST("api/v1/product-subscriptions")
    suspend fun subscribeToProduct(@Body request: CreateProductSubscriptionRequest, @Header("Idempotency-Key") idempotencyKey: String): ProductSubscriptionResponse

    @GET("api/v1/product-subscriptions")
    suspend fun getMyProductSubscriptions(): ProductSubscriptionsResponse

    @POST("api/v1/product-subscriptions/{id}/pause")
    suspend fun pauseProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    @POST("api/v1/product-subscriptions/{id}/resume")
    suspend fun resumeProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    @POST("api/v1/product-subscriptions/{id}/cancel")
    suspend fun cancelProductSubscription(@Path("id") id: String): ProductSubscriptionResponse

    @GET("api/v1/shopping/merchants/{id}/products")
    suspend fun getMerchantProducts(@Path("id") merchantId: String): MerchantProductsResponse

    // Real cross-merchant product search (item 137) -- see ProductSearchResultDto's own
    // doc comment.
    @GET("api/v1/shopping/products/search")
    suspend fun searchProducts(@Query("q") query: String): ProductSearchResponse

    // Real KakaoTalk-style 기프티콘 gift voucher (item 137) -- see GiftVoucherDto's own
    // doc comment.
    @POST("api/v1/gift-vouchers")
    suspend fun purchaseGiftVoucher(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PurchaseGiftVoucherRequest): GiftVoucherResponse

    @GET("api/v1/gift-vouchers/conversations/{id}")
    suspend fun getGiftVouchersForConversation(@Path("id") conversationId: String): GiftVouchersResponse

    @POST("api/v1/gift-vouchers/{id}/extend")
    suspend fun extendGiftVoucherExpiry(@Path("id") voucherId: String): GiftVoucherResponse

    // Real Naver Smart Store-style "알림받기" (follow a store) -- first Android client
    // for this feature (item 117, found via a content-grep sweep: bank-mfe has it,
    // Android/iOS didn't). Mirrors bank-mfe's lib/shopping.ts exactly.
    @POST("api/v1/merchant/{merchantId}/follow")
    suspend fun followMerchant(@Path("merchantId") merchantId: String): MerchantFollowResponse

    @DELETE("api/v1/merchant/{merchantId}/follow")
    suspend fun unfollowMerchant(@Path("merchantId") merchantId: String): SuccessResponse

    @GET("api/v1/merchant/follows")
    suspend fun getMyFollowedMerchants(@Query("size") size: Int = 200): FollowedMerchantsResponse

    // Real local-business appointment booking (2026-07-25) -- see
    // rw.itunda.merchant.web.MerchantBookingController.
    @GET("api/v1/merchant/{merchantId}/booking-slots")
    suspend fun getBookingSlots(@Path("merchantId") merchantId: String, @Query("serviceId") serviceId: String, @Query("date") date: String): BookingSlotsResponse

    @POST("api/v1/merchant/bookings")
    suspend fun createBooking(@Body request: CreateBookingRequest): MerchantBookingDetailResponse

    @GET("api/v1/merchant/bookings/my-bookings")
    suspend fun getMyBookings(): MerchantBookingsResponse

    @POST("api/v1/merchant/bookings/{id}/cancel")
    suspend fun cancelBooking(@Path("id") bookingId: String): MerchantBookingDetailResponse

    // Real customer-facing post-appointment review submission (item 143) -- see
    // rw.itunda.merchant.MerchantBookingReviewController. bank-mfe already has this;
    // this is the first native client.
    @POST("api/v1/merchant/bookings/{bookingId}/review")
    suspend fun submitBookingReview(@Path("bookingId") bookingId: String, @Body request: SubmitBookingReviewRequest): MerchantBookingReviewResponse

    @GET("api/v1/merchant/reviews/my-reviews")
    suspend fun getMyBookingReviews(@Query("size") size: Int = 50): MerchantBookingReviewsResponse

    // Real pre-booking browsing (item 231) -- found via a defined-but-uncalled-endpoint
    // sweep: both real, fully-authorized backend endpoints, but had zero client callers
    // anywhere. bank-mfe shipped this first (2026-08-05); this is the Android port. Reuses
    // MerchantCouponPreviewDto (already exact-shape-identical, from the payment-preview
    // feature) rather than declaring a duplicate DTO.
    @GET("api/v1/merchant/{merchantId}/reviews")
    suspend fun getMerchantReviews(@Path("merchantId") merchantId: String, @Query("size") size: Int = 20): MerchantReviewsWithRatingResponse

    @GET("api/v1/merchant/{merchantId}/coupons")
    suspend fun getCouponsForCustomer(@Path("merchantId") merchantId: String): MerchantCouponsForCustomerResponse

    // Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
    // (this app has no scanner), mirrors bank-mfe's lib/shopping.ts collectPayment/
    // payByStaticQr exactly. bank-mfe already has both; this is the first Android client
    // for either.
    @POST("api/v1/merchant/collect/{intentId}")
    suspend fun collectPayment(@Path("intentId") intentId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: CollectPaymentRequest): CollectPaymentResultDto

    @POST("api/v1/merchant/{merchantId}/static-qr/pay")
    suspend fun payByStaticQr(@Path("merchantId") merchantId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: StaticQrPayRequest): CollectPaymentResultDto

    // Real coupon-preview-before-pay (item 149/146) -- closes the deliberate scope-down
    // this file's own collectPayment comment previously named. bank-mfe already has this
    // (previewPaymentIntent); this is the first Android client.
    @GET("api/v1/merchant/intent/{intentId}")
    suspend fun previewPaymentIntent(@Path("intentId") intentId: String): PaymentIntentPreviewResponse

    // Real Face Pay -- see rw.itunda.merchant.FacePayService's own doc comment. The
    // backend has been fully real since 2026-07-13; bank-mfe wired it 2026-07-20; this
    // is the first Android client. Enrolling swaps Pay-by-code's own collect call to
    // this channel -- same manual code entry, just a different real ledger channel
    // label ("Face Pay" vs "QR"), matching bank-mfe's own honest scope exactly (no
    // device biometric prompt gates it on any client, itunda's own).
    @POST("api/v1/facepay/enroll")
    suspend fun enrollFacePay(): FacePayEnrollmentResponse

    @POST("api/v1/facepay/revoke")
    suspend fun revokeFacePay(): FacePayEnrollmentResponse

    @GET("api/v1/facepay/status")
    suspend fun getFacePayStatus(): FacePayStatusResponse

    @POST("api/v1/facepay/collect/{intentId}")
    suspend fun collectWithFacePay(@Path("intentId") intentId: String, @Header("Idempotency-Key") idempotencyKey: String): CollectPaymentResultDto

    // Real Coupang-style multi-item checkout (2026-07-18) -- see rw.itunda.commerce.web.OrderController.
    @POST("api/v1/orders")
    suspend fun placeOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceOrderRequest): OrderDetailResponse

    @GET("api/v1/orders/my-orders")
    suspend fun getMyOrders(): OrdersResponse

    @GET("api/v1/orders/{id}")
    suspend fun getOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real merchant-side Commerce order fulfillment queue (item 234) -- found via a
    // sibling-consistency audit against bank-mfe's own MerchantOrdersView/
    // MerchantReturnQueueView, which have had this since before this session: a real
    // itunda user who also runs a merchant storefront could manage their store's
    // orders on bank-mfe but had zero client anywhere on Android or iOS. Mirrors
    // bank-mfe's own COMMERCE_STATUS_CHAIN (PLACED -> PACKED -> SHIPPED -> DELIVERED)
    // exactly.
    @GET("api/v1/orders/merchant-orders")
    suspend fun getMerchantOrders(): OrdersResponse

    @POST("api/v1/orders/{orderId}/status")
    suspend fun updateOrderStatus(@Path("orderId") orderId: String, @Body request: UpdateOrderStatusRequest): OrderDetailResponse

    @GET("api/v1/orders/returns/merchant-queue")
    suspend fun getMerchantReturnQueue(): OrderReturnRequestsResponse

    @POST("api/v1/orders/returns/{returnRequestId}/decide")
    suspend fun decideOrderReturn(@Path("returnRequestId") returnRequestId: String, @Body request: DecideOrderReturnRequest): OrderReturnRequestResponse

    // Real live rider-location tracking for Commerce orders (item 230) -- found via a
    // defined-but-uncalled-endpoint sweep: mirrors getEatsRiderLocation exactly (same
    // RiderLocationDto shape, same real "available: false" while genuinely nothing to
    // show), but had zero client callers on any platform until now. bank-mfe shipped
    // this first (SimpleLiveRiderMap.tsx, 2026-08-05); this is the Android port. Honest
    // v1 scope-down: Commerce's OrderDto has no delivery-coordinate fields, so this is a
    // single rider marker, not a route.
    @GET("api/v1/orders/{orderId}/rider-location")
    suspend fun getOrderRiderLocation(@Path("orderId") orderId: String): EatsRiderLocationResponse

    // Real 쿠팡파트너스 (Coupang Partners)-style affiliate link program (item 229) --
    // see AffiliateLinkDto's own doc comment.
    @POST("api/v1/affiliate/links")
    suspend fun createAffiliateLink(@Body request: CreateAffiliateLinkRequest): AffiliateLinkResponse

    @GET("api/v1/affiliate/links/my-links")
    suspend fun getMyAffiliateLinks(): AffiliateLinksResponse

    @GET("api/v1/affiliate/commissions/my-commissions")
    suspend fun getMyAffiliateCommissions(): AffiliateCommissionsResponse

    // Real cancellation + refund (2026-07-18) -- buyer or seller, PLACED orders only.
    // See rw.itunda.commerce.OrderService.cancelOrder's own doc comment.
    @POST("api/v1/orders/{id}/cancel")
    suspend fun cancelOrder(@Path("id") orderId: String): OrderDetailResponse

    // Real Coupang-style post-delivery Return & Exchange requests (item 166/174) -- see
    // OrderReturnService's own doc comment. A real 7-day window from delivery; an
    // approved RETURN triggers a real refund via reversed ledger legs, an approved
    // EXCHANGE moves no money. Merchant-side approve/reject queue closed item 234 above.
    @POST("api/v1/orders/{orderId}/return")
    suspend fun requestOrderReturn(@Path("orderId") orderId: String, @Body request: RequestOrderReturnRequest): OrderReturnRequestResponse

    @GET("api/v1/orders/returns/my-requests")
    suspend fun getMyReturnRequests(): OrderReturnRequestsResponse

    // Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    @POST("api/v1/orders/items/{id}/review")
    suspend fun submitProductReview(@Path("id") orderItemId: String, @Body request: SubmitProductReviewRequest): ProductReviewResponse

    @GET("api/v1/orders/products/{id}/rating")
    suspend fun getProductRating(@Path("id") productId: String): ProductRatingResponse

    @GET("api/v1/orders/products/{id}/reviews")
    suspend fun getProductReviews(@Path("id") productId: String): ProductReviewsResponse

    // Real Coupang-style pre-purchase product Q&A (상품문의) -- see
    // rw.itunda.commerce.ProductInquiryService's own doc comment. Genuinely distinct
    // from a review: no order/purchase required at all. bank-mfe already has the
    // buyer-side ask/view flow; this is the first Android client. The seller-answer
    // flow (2026-08-04) now lives on the merchant app -- see that app's
    // ProductInquiryAnswerRow.
    @POST("api/v1/orders/products/{id}/inquiries")
    suspend fun askProductInquiry(@Path("id") productId: String, @Body request: AskProductInquiryRequest): ProductInquiryResponse

    @GET("api/v1/orders/products/{id}/inquiries")
    suspend fun getProductInquiries(@Path("id") productId: String): ProductInquiriesResponse

    // Real "my questions across every product I've ever asked about" (2026-08-04) --
    // OrderController.getMyInquiries existed on the backend with zero client anywhere;
    // ShopScreen's ProductInquirySection only ever showed one product's Q&A at a time.
    @GET("api/v1/orders/inquiries/my-questions")
    suspend fun getMyProductInquiries(): ProductInquiriesResponse

    // Real Kakao Pay 정기결제/Toss Payments 빌링키-style recurring merchant billing --
    // see rw.itunda.merchant.MerchantBillingService's own doc comment. Customer-facing
    // half only (browse a merchant's own plans, subscribe, view/cancel), matching
    // bank-mfe's own scope -- plan creation is merchant-owner-only, a merchant-mfe/
    // merchant-app concern, not built here. bank-mfe already has this; this is the
    // first Android client.
    @GET("api/v1/merchant/{merchantId}/billing-plans")
    suspend fun getMerchantBillingPlans(@Path("merchantId") merchantId: String): MerchantBillingPlansResponse

    @POST("api/v1/merchant/billing-plans/{planId}/subscribe")
    suspend fun subscribeToBillingPlan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MerchantBillingSubscriptionResponse

    @GET("api/v1/merchant/billing-subscriptions/my")
    suspend fun getMyBillingSubscriptions(): MerchantBillingSubscriptionsResponse

    @POST("api/v1/merchant/billing-subscriptions/{subscriptionId}/cancel")
    suspend fun cancelBillingSubscription(@Path("subscriptionId") subscriptionId: String): MerchantBillingSubscriptionResponse

    // Real recurring-payment ("subscription") detection over a user's own real
    // transaction history -- see rw.itunda.wallet.SubscriptionDetectionService's own
    // doc comment. bank-mfe already has this; this is the first Android client.
    @GET("api/v1/wallet/subscriptions")
    suspend fun getDetectedSubscriptions(): DetectedSubscriptionsResponse

    // Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- the
    // customer-facing browse half (merchant-mfe owns the paid create/extend side).
    // "Pull" discovery, same as every other nearby() in this codebase: the caller's
    // live coordinate is a request param, not a stored location itunda doesn't keep.
    // bank-mfe already has this; this is the first Android client.
    @GET("api/v1/merchant/ads/nearby")
    suspend fun getNearbyMerchantAds(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double): NearbyMerchantAdsResponse

    // Real Shop product wishlist (2026-07-24) -- backend shipped 2026-07-20
    // (ProductFavoriteService), bank-mfe wired the same day; this closes the
    // Android-side gap. Mirrors addListingFavorite/addJobPostFavorite exactly.
    @POST("api/v1/orders/products/{id}/favorite")
    suspend fun addProductFavorite(@Path("id") productId: String): SuccessResponse

    @DELETE("api/v1/orders/products/{id}/favorite")
    suspend fun removeProductFavorite(@Path("id") productId: String): SuccessResponse

    @GET("api/v1/orders/products/favorites")
    suspend fun getMyFavoriteProducts(): FavoriteProductsResponse

    // Real Coupang Eats-style food delivery (2026-07-18) -- see rw.itunda.eats.web.EatsController.
    @POST("api/v1/eats/orders")
    suspend fun placeEatsOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceEatsOrderRequest): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/my-orders")
    suspend fun getMyEatsOrders(): EatsOrdersResponse

    // Real order detail, including items -- backs the real "Reorder" button
    // (2026-07-19): a buyer can re-populate a cart from a past order's real items
    // rather than retyping their whole order from scratch.
    @GET("api/v1/eats/orders/{id}")
    suspend fun getEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    // Real address-search autocomplete (2026-07-18) -- see EatsController.searchDeliveryAddress.
    @GET("api/v1/eats/geocode/search")
    suspend fun searchDeliveryAddress(@Query("q") query: String): AddressSearchResponse

    // Real post-delivery ratings & reviews (2026-07-18) -- see EatsController.submitReview.
    @POST("api/v1/eats/orders/{id}/review")
    suspend fun submitEatsReview(@Path("id") orderId: String, @Body request: SubmitEatsReviewRequest): EatsReviewResponse

    @GET("api/v1/eats/restaurants/{id}/rating")
    suspend fun getRestaurantRating(@Path("id") restaurantId: String): EatsRatingResponse

    // Real written-review list + owner-reply (item 184/185) -- see
    // EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe already has
    // this (item 184); this is the first Android client.
    @GET("api/v1/eats/restaurants/{id}/reviews")
    suspend fun getRestaurantReviews(@Path("id") restaurantId: String): EatsReviewsResponse

    @POST("api/v1/eats/reviews/{reviewId}/reply")
    suspend fun replyToRestaurantReview(@Path("reviewId") reviewId: String, @Body request: ReplyToEatsReviewRequest): EatsReviewResponse

    // Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    // only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    @POST("api/v1/eats/orders/{id}/cancel")
    suspend fun cancelEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/rider-deliveries")
    suspend fun getRiderDeliveries(): EatsOrdersResponse

    @GET("api/v1/eats/orders/available")
    suspend fun getAvailableDeliveries(): EatsOrdersResponse

    @POST("api/v1/eats/orders/{id}/claim")
    suspend fun claimDelivery(@Path("id") orderId: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/rider-status")
    suspend fun updateRiderOrderStatus(@Path("id") orderId: String, @Body request: UpdateEatsOrderStatusRequest): EatsOrderDetailResponse

    // Real live rider-location tracking (2026-07-19 backend, first mobile client 2026-07-29,
    // item 182) -- "the defining 'watch your order arrive' moment," see EatsOrderService.
    // getRiderLocation's own doc comment. bank-mfe has had this since 2026-07-20
    // (LiveRiderMap.tsx); Android/iOS main apps never did. `available: false` (not an
    // error) is the real, honest response whenever there's genuinely nothing to show yet.
    @GET("api/v1/eats/orders/{orderId}/rider-location")
    suspend fun getEatsRiderLocation(@Path("orderId") orderId: String): EatsRiderLocationResponse

    @POST("api/v1/eats/riders/register")
    suspend fun registerRider(): RiderResponse

    @GET("api/v1/eats/riders/me")
    suspend fun getMyRiderProfile(): RiderResponse

    @POST("api/v1/eats/riders/availability")
    suspend fun setRiderAvailability(@Body request: SetRiderAvailabilityRequest): RiderResponse

    // Real bookmarked/favorited restaurants (2026-07-19) -- see
    // EatsFavoriteService.kt's own doc comment for why add/remove are both idempotent.
    @POST("api/v1/eats/restaurants/{id}/favorite")
    suspend fun addFavoriteRestaurant(@Path("id") restaurantId: String): AddFavoriteResponse

    @DELETE("api/v1/eats/restaurants/{id}/favorite")
    suspend fun removeFavoriteRestaurant(@Path("id") restaurantId: String): RemoveFavoriteResponse

    @GET("api/v1/eats/favorites")
    suspend fun getMyFavoriteRestaurants(): FavoriteRestaurantsResponse

    // Real Baemin Club (배민클럽)-style free-delivery membership (item 209) -- backend
    // real since 2026-07-26, bank-mfe client since 2026-07-28 (item 102); this is the
    // first Android client. See lib/eats.ts's own doc comment (bank-mfe) for the full
    // sourced account: free delivery only at a restaurant that has itself opted in,
    // never a blanket waiver.
    @GET("api/v1/eats/membership/me")
    suspend fun getMyEatsMembership(): EatsMembershipResponse

    @POST("api/v1/eats/membership/subscribe")
    suspend fun subscribeEatsMembership(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SubscribeEatsMembershipRequest): EatsMembershipResponse

    // Real Coupang 와우 (Wow)-style unconditional delivery-fee waiver (item 211) -- see
    // PlatformMembershipDto's own doc comment.
    @GET("api/v1/eats/platform-membership/me")
    suspend fun getMyPlatformMembership(): PlatformMembershipResponse

    @POST("api/v1/eats/platform-membership/subscribe")
    suspend fun subscribePlatformMembership(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SubscribePlatformMembershipRequest): PlatformMembershipResponse

    // Real 배민오더-style table/QR in-store ordering (2026-07-25) -- see
    // rw.itunda.eats.web.DineInOrderController.
    @POST("api/v1/eats/dine-in/orders")
    suspend fun placeDineInOrder(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PlaceDineInOrderRequest): DineInOrderDetailResponse

    @GET("api/v1/eats/dine-in/orders/my-orders")
    suspend fun getMyDineInOrders(): DineInOrdersResponse

    @GET("api/v1/eats/dine-in/orders/{id}")
    suspend fun getDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    @POST("api/v1/eats/dine-in/orders/{id}/cancel")
    suspend fun cancelDineInOrder(@Path("id") orderId: String): DineInOrderDetailResponse

    // Real Toss Securities-style stock investing (rw.itunda.stocks) -- this is the
    // first mobile UI this feature has ever had (bank-mfe just got its own real UI the
    // same session, closing what had been a zero-client-UI gap even for the original
    // pre-existing buy/sell/portfolio backend). See StockCatalog.kt's own doc comment
    // on the backend for why day-over-day movement/history is a real deterministic
    // simulation, not fabricated randomness or live RSE data.
    @GET("api/v1/stocks")
    suspend fun getStocks(): StocksResponse

    @GET("api/v1/stocks/{id}/history")
    suspend fun getStockHistory(@Path("id") stockId: String, @Query("days") days: Int = 14): StockHistoryResponse

    @GET("api/v1/stocks/portfolio")
    suspend fun getStockPortfolio(): StockPortfolioResponse

    @GET("api/v1/stocks/portfolio/history")
    suspend fun getPortfolioHistory(@Query("days") days: Int = 30): PortfolioHistoryResponse

    @POST("api/v1/stocks/fund")
    suspend fun fundInvestmentWallet(@Header("Idempotency-Key") idempotencyKey: String, @Body request: FundInvestmentRequest): FundInvestmentResponse

    @POST("api/v1/stocks/buy")
    suspend fun buyStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @POST("api/v1/stocks/sell")
    suspend fun sellStock(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TradeStockRequest): TradeStockResponse

    @GET("api/v1/stocks/watchlist")
    suspend fun getStockWatchlist(): StocksResponse

    @POST("api/v1/stocks/{id}/watch")
    suspend fun watchStock(@Path("id") stockId: String): WatchStockResponse

    @DELETE("api/v1/stocks/{id}/watch")
    suspend fun unwatchStock(@Path("id") stockId: String): UnwatchStockResponse

    @GET("api/v1/overview")
    suspend fun getOverview(): OverviewResponse

    @POST("api/v1/accounts/link")
    suspend fun linkAccount(@Body request: LinkAccountRequest): LinkAccountResponse

    @GET("api/v1/accounts/linked")
    suspend fun getLinkedAccounts(): LinkedAccountsResponse

    @POST("api/v1/accounts/link/{accountId}/unlink")
    suspend fun unlinkAccount(@Path("accountId") accountId: String): LinkAccountResponse

    @GET("api/v1/loans/offers")
    suspend fun getLoanOffers(@Query("lenderId") lenderId: String? = null): LoanOffersResponse

    @GET("api/v1/loans/lenders")
    suspend fun getLenders(): LendersResponse

    @GET("api/v1/loans/my-loans")
    suspend fun getMyLoans(): MyLoansResponse

    @POST("api/v1/loans/apply")
    suspend fun applyForLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ApplyLoanRequest): ApplyLoanResponse

    @POST("api/v1/loans/repay")
    suspend fun repayLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RepayLoanRequest): RepayLoanResponse

    @POST("api/v1/loans/refinance")
    suspend fun refinanceLoan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RefinanceLoanRequest): RefinanceResult

    @GET("api/v1/loans/overdraft")
    suspend fun getMyOverdraft(): OverdraftAccountResponse

    @POST("api/v1/loans/overdraft/open")
    suspend fun openOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OpenOverdraftRequest): OverdraftAccountResponse

    @POST("api/v1/loans/overdraft/draw")
    suspend fun drawOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OverdraftAmountRequest): OverdraftDrawResponse

    @POST("api/v1/loans/overdraft/repay")
    suspend fun repayOverdraft(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OverdraftAmountRequest): OverdraftRepayResponse

    // Real Naver Pay/Kakao Pay/Toss 후불결제 (postpaid/BNPL credit line, real since
    // 2026-07-31) -- first Android client for this feature, mirroring bank-mfe's
    // lib/loans.ts and Android's own OverdraftPanel shape exactly.
    @GET("api/v1/loans/postpaid-credit")
    suspend fun getMyPostpaidCredit(): PostpaidCreditLineResponse

    @POST("api/v1/loans/postpaid-credit/apply")
    suspend fun applyForPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String): PostpaidCreditLineResponse

    @POST("api/v1/loans/postpaid-credit/spend")
    suspend fun spendPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PostpaidCreditAmountRequest): PostpaidCreditActionResponse

    @POST("api/v1/loans/postpaid-credit/repay")
    suspend fun repayPostpaidCredit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: PostpaidCreditAmountRequest): PostpaidCreditActionResponse

    @POST("api/v1/card/issue")
    suspend fun issueCard(): CardResponse

    @GET("api/v1/card/my-card")
    suspend fun getMyCard(): CardResponse

    @GET("api/v1/card/transactions")
    suspend fun getCardTransactions(@Query("page") page: Int = 0, @Query("size") size: Int = 20): CardTransactionsResponse

    @PUT("api/v1/card/limits")
    suspend fun setCardLimits(@Body request: SetCardLimitsRequest): CardResponse

    @POST("api/v1/card/freeze")
    suspend fun freezeCard(): CardResponse

    @POST("api/v1/card/unfreeze")
    suspend fun unfreezeCard(): CardResponse

    @POST("api/v1/card/charge")
    suspend fun chargeCard(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ChargeCardRequest): ChargeCardResponse

    @POST("api/v1/support/tickets")
    suspend fun createSupportTicket(@Body request: CreateSupportTicketRequest): CreateSupportTicketResponse

    @GET("api/v1/support/tickets")
    suspend fun getSupportTickets(): SupportTicketsResponse

    @GET("api/v1/credit-score")
    suspend fun getCreditScore(): CreditScoreResponse

    @GET("api/v1/credit-score/suggestions")
    suspend fun getCreditScoreSuggestions(): CreditScoreSuggestionsResponse

    @GET("api/v1/trust-score")
    suspend fun getTrustScore(): TrustScoreResponse

    // Real Itunda cash-agent operator console -- see AgentOperatorController.kt's own
    // doc comment. bank-mfe already has this; this is the first native client.
    @GET("api/v1/agent/till")
    suspend fun getAgentTill(): AgentTillResponse

    @GET("api/v1/agent/activity")
    suspend fun getAgentActivity(@Query("limit") limit: Int = 30): AgentActivityResponse

    @POST("api/v1/agent/cash-ins")
    suspend fun agentCashIn(@Header("Idempotency-Key") idempotencyKey: String, @Body request: AgentCashInRequest): AgentCashResultResponse

    @POST("api/v1/agent/cash-outs")
    suspend fun agentCashOut(@Header("Idempotency-Key") idempotencyKey: String, @Body request: AgentCashOutRequest): AgentCashResultResponse

    @POST("api/v1/agent/till-reconciliations")
    suspend fun submitAgentTillCount(@Body request: SubmitTillCountRequest): AgentTillReconciliationResponse

    // Real peer-to-peer agent float rebalancing marketplace -- see FloatMarketplaceController.kt.
    @POST("api/v1/float-marketplace/listings")
    suspend fun postFloatListing(@Body request: PostFloatListingRequest): FloatListingResponse

    @GET("api/v1/float-marketplace/listings/nearby")
    suspend fun getNearbyFloatListings(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 20.0,
    ): NearbyFloatListingsResponse

    @GET("api/v1/float-marketplace/listings/mine")
    suspend fun getMyFloatListings(): FloatListingsResponse

    @POST("api/v1/float-marketplace/listings/{listingId}/cancel")
    suspend fun cancelFloatListing(@Path("listingId") listingId: String): FloatListingResponse

    @POST("api/v1/float-marketplace/listings/{listingId}/requests")
    suspend fun requestFloat(@Path("listingId") listingId: String, @Body request: RequestFloatRequest): FloatTransferRequestResponse

    @GET("api/v1/float-marketplace/requests/mine")
    suspend fun getMyFloatRequests(): FloatTransferRequestsResponse

    @GET("api/v1/float-marketplace/requests/incoming")
    suspend fun getIncomingFloatRequests(): FloatTransferRequestsResponse

    @POST("api/v1/float-marketplace/requests/{requestId}/accept")
    suspend fun acceptFloatRequest(@Path("requestId") requestId: String, @Header("Idempotency-Key") idempotencyKey: String): FloatTransferRequestResponse

    @POST("api/v1/float-marketplace/requests/{requestId}/decline")
    suspend fun declineFloatRequest(@Path("requestId") requestId: String): FloatTransferRequestResponse

    @POST("api/v1/certificate/issue")
    suspend fun issueCertificate(): IssueCertificateResponse

    @GET("api/v1/certificate/me")
    suspend fun getMyCertificate(): MyCertificateResponse

    @POST("api/v1/certificate/revoke")
    suspend fun revokeCertificate(): RevokeCertificateResponse

    // Real public certificate status/verify (2026-08-04) -- found via a fresh
    // "defined but uncalled" endpoint sweep: real, working, deliberately unauthenticated
    // endpoints (see CertificateController's own doc comment on why /status and /verify
    // are permitAll, unlike /issue-/me/-revoke) with zero client anywhere, including
    // this app which already wires the other three. Lets any itunda user check whether
    // a certificate serial number a counterpart shared with them is still valid, and
    // verify a signed payload against it -- the actual "does this signed thing check
    // out" use case a personal signing certificate exists for.
    @GET("api/v1/certificate/status/{serialNumber}")
    suspend fun getCertificateStatus(@Path("serialNumber") serialNumber: String): CertificateStatusResponse

    @POST("api/v1/certificate/verify")
    suspend fun verifyCertificateSignature(@Body request: VerifyCertificateSignatureRequest): VerifyCertificateSignatureResponse

    @POST("api/v1/identity/submit")
    suspend fun submitIdentity(@Body request: SubmitIdentityRequest): SubmitIdentityResponse

    @GET("api/v1/identity/status")
    suspend fun getIdentityStatus(): IdentityStatusResponse

    @POST("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun createSplitBill(
        @Path("groupConversationId") groupConversationId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateSplitBillRequest,
    ): CreateSplitBillResponse

    @GET("api/v1/split-bills/conversations/{groupConversationId}")
    suspend fun getSplitBillsForGroup(@Path("groupConversationId") groupConversationId: String): SplitBillsForGroupResponse

    @GET("api/v1/split-bills/{id}")
    suspend fun getSplitBill(@Path("id") splitBillId: String): SplitBillResponse

    @POST("api/v1/split-bills/{id}/pay")
    suspend fun paySplitBillShare(@Path("id") splitBillId: String, @Header("Idempotency-Key") idempotencyKey: String): PaySplitBillShareResponse

    @POST("api/v1/split-bills/{id}/receipt")
    suspend fun attachSplitBillReceipt(@Path("id") splitBillId: String, @Body request: AttachSplitBillReceiptRequest): SplitBillOnlyResponse

    // Real 1:1-chat split-bill entry point (2026-08-09) -- see backend
    // SplitBillService.createDirectSplitBill's own doc comment: resolves a hidden
    // 2-person group between the caller and otherUserId first, so this never needs an
    // existing named group the way createSplitBill above does.
    @POST("api/v1/split-bills/direct/{otherUserId}")
    suspend fun createDirectSplitBill(
        @Path("otherUserId") otherUserId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateDirectSplitBillRequest,
    ): CreateSplitBillResponse

    // Real read-only counterpart -- never creates a hidden group as a side effect of
    // just viewing this tab; see backend SplitBillService.getDirectSplitBills's own
    // doc comment.
    @GET("api/v1/split-bills/direct/{otherUserId}")
    suspend fun getDirectSplitBills(@Path("otherUserId") otherUserId: String): SplitBillsForGroupResponse

    @POST("api/v1/split-bills/{id}/next-round")
    suspend fun requestSplitBillNextRound(@Path("id") splitBillId: String): SplitBillOnlyResponse

    @GET("api/v1/contacts")
    suspend fun getContacts(): ContactsResponse

    @POST("api/v1/contacts")
    suspend fun addContact(@Body request: AddContactRequest): AddContactResponse

    // Real 26-week savings plan (2026-07-21) -- see WeeklySavingsPlanDto's own doc
    // comment. No Idempotency-Key header on any of these -- WeeklySavingsController
    // genuinely doesn't declare that header for this feature, unlike deposit/
    // claimInterest/sendDirect above.
    @GET("api/v1/weekly-savings/plans")
    suspend fun getWeeklySavingsPlans(): WeeklySavingsPlansResponse

    @GET("api/v1/weekly-savings/plans/{id}")
    suspend fun getWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsPlanDetailResponse

    @POST("api/v1/weekly-savings/plans")
    suspend fun createWeeklySavingsPlan(@Body request: CreateWeeklySavingsPlanRequest): CreateWeeklySavingsPlanResponse

    @POST("api/v1/weekly-savings/plans/{id}/cancel")
    suspend fun cancelWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    @POST("api/v1/weekly-savings/plans/{id}/withdraw")
    suspend fun withdrawWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
    // equivalent (2026-07-25) -- see backend UpfrontInterestDeposit's own doc comment.
    @GET("api/v1/upfront-deposits")
    suspend fun getUpfrontDeposits(): UpfrontDepositsResponse

    @POST("api/v1/upfront-deposits")
    suspend fun openUpfrontDeposit(@Body request: OpenUpfrontDepositRequest): UpfrontDepositResponse

    @POST("api/v1/upfront-deposits/{id}/withdraw")
    suspend fun withdrawUpfrontDeposit(@Path("id") id: String): UpfrontDepositResponse

    // Real KakaoBank mini-style capped starter wallet (rw.itunda.wallet.
    // MiniWalletService, 2026-07-28) -- first mobile client for this feature (item 100),
    // mirroring bank-mfe's lib/miniWallet.ts equivalent added one item earlier.
    @POST("api/v1/wallet/mini/open")
    suspend fun openMiniWallet(): OpenMiniWalletResponse

    @POST("api/v1/wallet/mini/deposit")
    suspend fun depositMiniWallet(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositMiniWalletRequest): DepositMiniWalletResponse

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first Android client for this feature (item 109, found via a fresh matrix scan:
    // bank-mfe has had it since the same day, Android/iOS never did). Mirrors
    // bank-mfe's lib/rideshare.ts exactly.
    @POST("api/v1/rides/drivers/register")
    suspend fun registerAsRideDriver(): RideDriverResponse

    @GET("api/v1/rides/drivers/me")
    suspend fun getMyRideDriverProfile(): RideDriverResponse

    @POST("api/v1/rides/drivers/availability")
    suspend fun setRideDriverAvailability(@Body request: SetRideDriverAvailabilityRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/location")
    suspend fun updateRideDriverLocation(@Body request: UpdateRideDriverLocationRequest): RideDriverResponse

    @POST("api/v1/rides/trips")
    suspend fun requestRideTrip(@Body request: RequestRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

    @GET("api/v1/rides/trips/available")
    suspend fun getAvailableRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-trips")
    suspend fun getMyRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-driver-trips")
    suspend fun getMyRideDriverTrips(): RideTripsResponse

    @POST("api/v1/rides/trips/{tripId}/accept")
    suspend fun acceptRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/decline")
    suspend fun declineRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/start")
    suspend fun startRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/complete")
    suspend fun completeRideTrip(@Path("tripId") tripId: String): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/cancel")
    suspend fun cancelRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Kakao T 예약 호출 (scheduled ride booking, item 212)/multi-stop (item 214)/
    // driver rating (item 213) -- first Android client for these three, backend and
    // bank-mfe real since 2026-07-31. Mirrors bank-mfe's lib/rideshare.ts exactly.
    @GET("api/v1/rides/trips/{tripId}/stops")
    suspend fun getRideTripStops(@Path("tripId") tripId: String): RideTripStopsResponse

    @POST("api/v1/rides/trips/{tripId}/stops/arrive")
    suspend fun arriveAtRideStop(@Path("tripId") tripId: String): RideTripStopResponse

    @POST("api/v1/rides/trips/{tripId}/review")
    suspend fun submitRideReview(@Path("tripId") tripId: String, @Body request: SubmitRideReviewRequest): RideTripReviewResponse

    @GET("api/v1/rides/drivers/{driverId}/rating")
    suspend fun getRideDriverRating(@Path("driverId") driverId: String): RideDriverRatingResponse

    // Real "meet your driver" rating + reviews during an active trip (item 233) --
    // found via the uncalled-endpoint sweep, see bank-mfe's lib/rideshare.ts own doc
    // comment on fetchDriverReviews for the full sourced account. bank-mfe shipped
    // this first (2026-08-05); this is the Android port.
    @GET("api/v1/rides/drivers/{driverId}/reviews")
    suspend fun getRideDriverReviews(@Path("driverId") driverId: String, @Query("size") size: Int = 10): RideDriverReviewsResponse

    // Real Kakao T 대리운전 (designated driver, item 221) -- first Android client for
    // this feature. bank-mfe already has this; mirrors lib/designatedDriver.ts exactly.
    @POST("api/v1/designated-driver/drivers/register")
    suspend fun registerAsDesignatedDriver(@Body request: RegisterDesignatedDriverRequest): DesignatedDriverResponse

    @GET("api/v1/designated-driver/drivers/me")
    suspend fun getMyDesignatedDriverProfile(): DesignatedDriverResponse

    @POST("api/v1/designated-driver/drivers/availability")
    suspend fun setDesignatedDriverAvailability(@Body request: SetDesignatedDriverAvailabilityRequest): DesignatedDriverResponse

    @POST("api/v1/designated-driver/drivers/location")
    suspend fun updateDesignatedDriverLocation(@Body request: UpdateDesignatedDriverLocationRequest): DesignatedDriverResponse

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // DesignatedDriverController.requestTrip doc comment for the full account.
    @POST("api/v1/designated-driver/trips")
    suspend fun requestDesignatedDriverTrip(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RequestDesignatedDriverTripRequest): DesignatedDriverTripResponse

    @GET("api/v1/designated-driver/trips/available")
    suspend fun getAvailableDesignatedDriverTrips(): DesignatedDriverTripsResponse

    @GET("api/v1/designated-driver/trips/my-trips")
    suspend fun getMyDesignatedDriverTrips(): DesignatedDriverTripsResponse

    @GET("api/v1/designated-driver/trips/my-driver-trips")
    suspend fun getMyDesignatedDriverDriverTrips(): DesignatedDriverTripsResponse

    @POST("api/v1/designated-driver/trips/{tripId}/accept")
    suspend fun acceptDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/start-driving")
    suspend fun startDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/complete")
    suspend fun completeDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    @POST("api/v1/designated-driver/trips/{tripId}/cancel")
    suspend fun cancelDesignatedDriverTrip(@Path("tripId") tripId: String): DesignatedDriverTripResponse

    // Real Kakao T 바이크 (Kakao T Bike, item 222) -- real peer-to-peer bike/scooter
    // rental, billed by elapsed time (not a pre-known fare like ride-hailing/designated-
    // driver). First Android client. bank-mfe already has this; mirrors lib/bikeshare.ts
    // exactly.
    @POST("api/v1/bikeshare/bikes")
    suspend fun registerBike(@Body request: RegisterBikeRequest): BikeResponse

    @GET("api/v1/bikeshare/bikes/mine")
    suspend fun getMyBikes(): BikesResponse

    @POST("api/v1/bikeshare/bikes/{bikeId}/availability")
    suspend fun setBikeAvailability(@Path("bikeId") bikeId: String, @Body request: SetBikeAvailabilityRequest): BikeResponse

    @POST("api/v1/bikeshare/bikes/{bikeId}/location")
    suspend fun updateBikeLocation(@Path("bikeId") bikeId: String, @Body request: UpdateBikeLocationRequest): BikeResponse

    @GET("api/v1/bikeshare/bikes/nearby")
    suspend fun getNearbyBikes(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): BikesResponse

    @POST("api/v1/bikeshare/rentals")
    suspend fun startBikeRental(@Body request: StartBikeRentalRequest): BikeRentalResponse

    @POST("api/v1/bikeshare/rentals/{sessionId}/end")
    suspend fun endBikeRental(@Path("sessionId") sessionId: String, @Body request: EndBikeRentalRequest): BikeRentalResponse

    @GET("api/v1/bikeshare/rentals/my-history")
    suspend fun getMyBikeRentalHistory(): BikeRentalsResponse

    // Real Kakao T 주차 (Kakao T Parking, item 223) -- real peer-to-peer parking-spot
    // rental, billed by elapsed hours (not a pre-known fare, same "settle at checkout"
    // shape Bike already establishes). First Android client. bank-mfe already has this;
    // mirrors lib/parking.ts exactly.
    @POST("api/v1/parking/spots")
    suspend fun registerParkingSpot(@Body request: RegisterParkingSpotRequest): ParkingSpotResponse

    @GET("api/v1/parking/spots/mine")
    suspend fun getMyParkingSpots(): ParkingSpotsResponse

    @POST("api/v1/parking/spots/{spotId}/availability")
    suspend fun setParkingSpotAvailability(@Path("spotId") spotId: String, @Body request: SetParkingSpotAvailabilityRequest): ParkingSpotResponse

    @GET("api/v1/parking/spots/nearby")
    suspend fun getNearbyParkingSpots(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 5.0,
    ): ParkingSpotsResponse

    @POST("api/v1/parking/sessions")
    suspend fun startParkingSession(@Body request: StartParkingSessionRequest): ParkingSessionResponse

    @POST("api/v1/parking/sessions/{sessionId}/end")
    suspend fun endParkingSession(@Path("sessionId") sessionId: String): ParkingSessionResponse

    @GET("api/v1/parking/sessions/my-history")
    suspend fun getMyParkingHistory(): ParkingSessionsResponse

    // Real Kakao T 시외버스 (intercity bus booking, item 224) -- real peer-to-peer
    // coach-operator trip pool, fare known and charged in full at booking time (unlike
    // Parking/Bike's settle-at-checkout shape). First Android client. bank-mfe already
    // has this; mirrors lib/bus.ts exactly.
    @POST("api/v1/bus/trips")
    suspend fun postBusTrip(@Body request: PostBusTripRequest): BusTripResponse

    @GET("api/v1/bus/trips/mine")
    suspend fun getMyBusTrips(): BusTripsResponse

    @GET("api/v1/bus/trips/{tripId}/bookings")
    suspend fun getBusTripBookings(@Path("tripId") tripId: String): BusBookingsResponse

    @GET("api/v1/bus/trips/search")
    suspend fun searchBusTrips(
        @Query("origin") origin: String? = null,
        @Query("destination") destination: String? = null,
    ): BusTripsResponse

    // Real bug found live (2026-08-02): missing Idempotency-Key -- see the backend's
    // BusController.bookSeats doc comment for the full account.
    @POST("api/v1/bus/bookings")
    suspend fun bookBusSeats(@Header("Idempotency-Key") idempotencyKey: String, @Body request: BookBusSeatsRequest): BusBookingResponse

    @POST("api/v1/bus/bookings/{bookingId}/cancel")
    suspend fun cancelBusBooking(@Path("bookingId") bookingId: String): BusBookingResponse

    @GET("api/v1/bus/bookings/my-history")
    suspend fun getMyBusBookings(): BusBookingsResponse

    // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a
    // genuinely different shape from the trip/rental features above: no wallet
    // movement, no location, just a real question -> competing answers ->
    // asker-adopts-one-best-answer content flow. First Android client. bank-mfe
    // already has this; mirrors lib/knowledge.ts exactly.
    @GET("api/v1/knowledge/categories")
    suspend fun getKnowledgeCategories(): KnowledgeCategoriesResponse

    @POST("api/v1/knowledge/questions")
    suspend fun postKnowledgeQuestion(@Body request: PostKnowledgeQuestionRequest): KnowledgeQuestionResponse

    @GET("api/v1/knowledge/questions")
    suspend fun getKnowledgeQuestions(@Query("category") category: String? = null): KnowledgeQuestionsResponse

    @GET("api/v1/knowledge/questions/my-questions")
    suspend fun getMyKnowledgeQuestions(): KnowledgeQuestionsResponse

    @GET("api/v1/knowledge/answers/my-answers")
    suspend fun getMyKnowledgeAnswers(): KnowledgeAnswersResponse

    @GET("api/v1/knowledge/reputation/me")
    suspend fun getMyKnowledgeReputation(): KnowledgeReputationResponse

    @GET("api/v1/knowledge/questions/{questionId}")
    suspend fun getKnowledgeQuestion(@Path("questionId") questionId: String): KnowledgeQuestionResponse

    @GET("api/v1/knowledge/questions/{questionId}/answers")
    suspend fun getKnowledgeAnswers(@Path("questionId") questionId: String): KnowledgeAnswersResponse

    @POST("api/v1/knowledge/questions/{questionId}/answers")
    suspend fun postKnowledgeAnswer(@Path("questionId") questionId: String, @Body request: PostKnowledgeAnswerRequest): KnowledgeAnswerResponse

    @POST("api/v1/knowledge/questions/{questionId}/answers/{answerId}/adopt")
    suspend fun adoptKnowledgeAnswer(@Path("questionId") questionId: String, @Path("answerId") answerId: String): KnowledgeAnswerResponse

    // Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- see
    // rw.itunda.marketplace.VehicleInspectionService's own doc comment. A buyer books
    // and 100%-prepays a real mechanic to inspect a real Marketplace used-car listing
    // before purchase. bank-mfe already has this; this is the first Android client.
    @POST("api/v1/marketplace/inspections/mechanics/register")
    suspend fun registerAsInspectionMechanic(@Body request: RegisterInspectionMechanicRequest): VehicleInspectionMechanicResponse

    @GET("api/v1/marketplace/inspections/mechanics/me")
    suspend fun getMyInspectionMechanicProfile(): VehicleInspectionMechanicOrNullResponse

    @GET("api/v1/marketplace/inspections/mechanics")
    suspend fun getAvailableInspectionMechanics(): VehicleInspectionMechanicsResponse

    @POST("api/v1/marketplace/inspections/mechanics/availability")
    suspend fun setInspectionMechanicAvailability(@Body request: SetInspectionMechanicAvailabilityRequest): VehicleInspectionMechanicResponse

    @POST("api/v1/marketplace/inspections")
    suspend fun requestVehicleInspection(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RequestVehicleInspectionRequest): VehicleInspectionBookingResponse

    @GET("api/v1/marketplace/inspections/my-bookings")
    suspend fun getMyInspectionBookings(): VehicleInspectionBookingsResponse

    @GET("api/v1/marketplace/inspections/my-mechanic-bookings")
    suspend fun getMyInspectionMechanicBookings(): VehicleInspectionBookingsResponse

    @POST("api/v1/marketplace/inspections/{bookingId}/accept")
    suspend fun acceptVehicleInspection(@Path("bookingId") bookingId: String): VehicleInspectionBookingResponse

    @POST("api/v1/marketplace/inspections/{bookingId}/complete")
    suspend fun completeVehicleInspection(@Path("bookingId") bookingId: String, @Body request: CompleteVehicleInspectionRequest): VehicleInspectionBookingResponse

    @POST("api/v1/marketplace/inspections/{bookingId}/cancel")
    suspend fun cancelVehicleInspection(@Path("bookingId") bookingId: String): VehicleInspectionBookingResponse

    // Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- see
    // rw.itunda.vehicle.VehicleValuationService's own doc comment. bank-mfe already has
    // this; this is the first Android client.
    @POST("api/v1/vehicles")
    suspend fun registerVehicle(@Body request: RegisterVehicleRequest): VehicleResponse

    @GET("api/v1/vehicles")
    suspend fun getMyVehicles(): VehiclesResponse

    @GET("api/v1/vehicles/{id}/valuation")
    suspend fun getVehicleValuation(@Path("id") id: String): VehicleValuationResponse

    @POST("api/v1/vehicles/{id}/mileage")
    suspend fun updateVehicleMileage(@Path("id") id: String, @Body request: UpdateVehicleMileageRequest): VehicleResponse

    @DELETE("api/v1/vehicles/{id}")
    suspend fun removeVehicle(@Path("id") id: String): SuccessResponse

    // Real Toss 유스 (Toss Youth)-style guardian-child account link -- see
    // rw.itunda.family.FamilyLinkService's own doc comment. Honest scope boundary: real
    // read-only spending oversight only, no new allowance mechanism (point an existing
    // AutoTransfer/ScheduledTransfer at the child's phone number instead). bank-mfe
    // already has this; this is the first Android client.
    @POST("api/v1/family/invite")
    suspend fun inviteFamilyChild(@Body request: InviteChildRequest): FamilyLinkResponse

    @GET("api/v1/family/invites")
    suspend fun getMyFamilyInvites(): FamilyLinksResponse

    @POST("api/v1/family/invites/{id}/respond")
    suspend fun respondToFamilyInvite(@Path("id") id: String, @Body request: RespondToInviteRequest): FamilyLinkResponse

    @GET("api/v1/family/children")
    suspend fun getMyFamilyChildren(): FamilyLinkViewsResponse

    @GET("api/v1/family/guardians")
    suspend fun getMyFamilyGuardians(): FamilyLinkViewsResponse

    @GET("api/v1/family/children/{childUserId}/overview")
    suspend fun getChildOverview(@Path("childUserId") childUserId: String): ChildOverviewResponse

    @POST("api/v1/family/links/{id}/revoke")
    suspend fun revokeFamilyLink(@Path("id") id: String): FamilyLinkResponse

    // Real spend-limit enforcement (2026-07-27) -- see FamilyLinkDto.dailySpendLimit's
    // own doc comment. FamilyLinkController.setSpendLimit existed on the backend, already
    // real-enforced, with zero client anywhere until now. Pass null to clear the limit.
    @POST("api/v1/family/children/{childUserId}/spend-limit")
    suspend fun setFamilySpendLimit(@Path("childUserId") childUserId: String, @Body request: SetSpendLimitRequest): FamilyLinkResponse

    // Real Kakao Bank 모임통장 (group/shared account) equivalent -- first Android client
    // for this feature (item 104, found via a fresh matrix scan: zero client on either
    // mobile platform despite being real and live since well before this session).
    // Mirrors bank-mfe's lib/groupAccounts.ts exactly.
    @POST("api/v1/group-accounts")
    suspend fun createGroupAccount(@Body request: CreateGroupAccountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts")
    suspend fun getMyGroupAccounts(): GroupAccountsResponse

    @GET("api/v1/group-accounts/{id}")
    suspend fun getGroupAccount(@Path("id") id: String): GroupAccountDetailResponse

    @POST("api/v1/group-accounts/{id}/members")
    suspend fun inviteGroupAccountMember(@Path("id") id: String, @Body request: InviteMemberRequest): InviteMemberResponse

    @POST("api/v1/group-accounts/{id}/deposit")
    suspend fun depositToGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @POST("api/v1/group-accounts/{id}/withdraw")
    suspend fun withdrawFromGroupAccount(
        @Path("id") id: String,
        @Body request: GroupAccountAmountRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): GroupAccountDetailResponse

    @PUT("api/v1/group-accounts/{id}/dues")
    suspend fun setGroupAccountDuesAmount(@Path("id") id: String, @Body request: SetDuesAmountRequest): CreateGroupAccountResponse

    @GET("api/v1/group-accounts/{id}/dues")
    suspend fun getGroupAccountDues(@Path("id") id: String): GroupAccountDuesResponse

    @POST("api/v1/group-accounts/{id}/dues/remind")
    suspend fun requestUnpaidGroupAccountDues(@Path("id") id: String): RemindUnpaidDuesResponse

    // Real ikimina (Rwanda's own rotating savings & credit association) -- see
    // IkiminaDto's own doc comment. Genuinely distinct from every Toss/Kakao/Naver/
    // Coupang-sourced feature in this backend.
    @POST("api/v1/ikiminas")
    suspend fun createIkimina(@Body request: CreateIkiminaRequest): CreateIkiminaResponse

    @GET("api/v1/ikiminas")
    suspend fun getMyIkiminas(): IkiminasResponse

    @GET("api/v1/ikiminas/{id}")
    suspend fun getIkimina(@Path("id") id: String): IkiminaDetailResponse

    @POST("api/v1/ikiminas/{id}/members")
    suspend fun inviteIkiminaMember(@Path("id") id: String, @Body request: InviteIkiminaMemberRequest): InviteIkiminaMemberResponse

    @POST("api/v1/ikiminas/{id}/start")
    suspend fun startIkiminaCycle(@Path("id") id: String): CreateIkiminaResponse

    @POST("api/v1/ikiminas/{id}/contribute")
    suspend fun contributeToIkimina(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): CreateIkiminaResponse

    @POST("api/v1/ikiminas/{id}/payout")
    suspend fun triggerIkiminaPayout(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): IkiminaPayoutResponse

    // Real Umurenge SACCO-style shares & dividends -- see SaccoShareholdingDto's own
    // doc comment. Genuinely distinct from every Toss/Kakao/Naver/Coupang-sourced
    // feature in this backend and from Ikimina (rotating-pot ROSCA).
    @POST("api/v1/sacco/shares/buy")
    suspend fun buySaccoShares(@Body request: SaccoAmountRequest, @Header("Idempotency-Key") idempotencyKey: String): SaccoShareholdingResponse

    @POST("api/v1/sacco/shares/redeem")
    suspend fun redeemSaccoShares(@Body request: SaccoAmountRequest, @Header("Idempotency-Key") idempotencyKey: String): SaccoShareholdingResponse

    @GET("api/v1/sacco/shares/me")
    suspend fun getMySaccoShareholding(): SaccoShareholdingResponse

    @GET("api/v1/sacco/dividends/me")
    suspend fun getMySaccoDividendHistory(): SaccoDividendPayoutsResponse

    // Real Rwanda coffee-cooperative harvest-advance / input financing -- see
    // HarvestAdvanceDto's own doc comment. The third feature in this backend not
    // sourced from Toss/Kakao/Naver/Coupang. A direct itunda-to-farmer lending
    // relationship (loan_payable), not a cooperative-pool redistribution.
    @POST("api/v1/cooperatives")
    suspend fun registerCooperative(@Body request: RegisterCooperativeRequest): CooperativeResponse

    @POST("api/v1/cooperatives/{cooperativeId}/join")
    suspend fun joinCooperative(@Path("cooperativeId") cooperativeId: String): CooperativeMembershipResponse

    @GET("api/v1/cooperatives/my-memberships")
    suspend fun getMyCooperativeMemberships(): CooperativeMembershipsResponse

    // Real member-facing cooperative detail (2026-08-04) -- CooperativeController.
    // getCooperativeOverview existed on the backend (real 403 via NotMemberException for
    // a non-member) with zero client anywhere: a member could request/repay advances but
    // never actually saw their own cooperative's name, crop type, or member count.
    @GET("api/v1/cooperatives/{cooperativeId}/overview")
    suspend fun getCooperativeOverview(@Path("cooperativeId") cooperativeId: String): CooperativeOverviewResponse

    @POST("api/v1/cooperatives/advances")
    suspend fun requestHarvestAdvance(@Body request: RequestAdvanceRequest): HarvestAdvanceResponse

    @POST("api/v1/cooperatives/advances/{advanceId}/disburse")
    suspend fun disburseHarvestAdvance(@Path("advanceId") advanceId: String, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

    // amount must exactly equal the advance's own principalAmount -- real bug caught
    // and fixed before this feature shipped: a free-form amount let a token repayment
    // silently forgive the rest of a real debt. No amount is user-editable client-side.
    @POST("api/v1/cooperatives/advances/{advanceId}/repay")
    suspend fun repayHarvestAdvance(@Path("advanceId") advanceId: String, @Body request: RepayAdvanceRequest, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

    @GET("api/v1/cooperatives/advances/my-advances")
    suspend fun getMyHarvestAdvances(): HarvestAdvancesResponse

    // Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
    // microloan -- see VupLoanDto's own doc comment. No Idempotency-Key on apply (not
    // money movement itself, matching the backend's own contract); disburse/repay both
    // require one, same convention as every other money-moving call in this file.
    @POST("api/v1/loans/vup/apply")
    suspend fun applyForVupLoan(@Body request: ApplyForVupLoanRequest): VupLoanResponse

    @POST("api/v1/loans/vup/{loanId}/disburse")
    suspend fun disburseVupLoan(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

    @POST("api/v1/loans/vup/{loanId}/repay")
    suspend fun repayVupLoan(@Path("loanId") loanId: String, @Body request: RepayVupLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

    @GET("api/v1/loans/vup/my")
    suspend fun getMyVupLoans(): VupLoansResponse

    @GET("api/v1/loans/vup/eligibility")
    suspend fun getVupLoanEligibility(): VupLoanEligibilityResponse

    @GET("api/v1/loans/vup/{loanId}")
    suspend fun getVupLoan(@Path("loanId") loanId: String): VupLoanResponse

    // Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan --
    // see StudentLoanDto's own doc comment. bank-mfe shipped first (lib/studentLoan.ts);
    // this is the first native client. No Idempotency-Key on apply/declare-graduated
    // (not money movement); disburse/repay both require one, matching every other
    // money-moving call in this file.
    @POST("api/v1/loans/student/apply")
    suspend fun applyForStudentLoan(@Body request: ApplyForStudentLoanRequest): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/disburse")
    suspend fun disburseStudentLoan(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/declare-graduated")
    suspend fun declareStudentLoanGraduated(@Path("loanId") loanId: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/repay")
    suspend fun repayStudentLoan(@Path("loanId") loanId: String, @Body request: RepayStudentLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @GET("api/v1/loans/student/my")
    suspend fun getMyStudentLoans(): StudentLoansResponse

    @GET("api/v1/loans/student/{loanId}/suggested-payment")
    suspend fun getStudentLoanSuggestedPayment(@Path("loanId") loanId: String): StudentLoanSuggestedPaymentResponse

    // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
    // MotoOwnershipPlanDto's own doc comment for the full sourced account. No
    // Idempotency-Key on create (not money movement itself, matching the backend's
    // own contract); contribute/cancel/convert-to-loan/repay all require one, same
    // convention as every other money-moving call in this file.
    @POST("api/v1/moto-ownership/plans")
    suspend fun createMotoOwnershipPlan(@Body request: CreateMotoOwnershipPlanRequest): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/contribute")
    suspend fun contributeToMotoOwnershipPlan(@Path("planId") planId: String, @Body request: ContributeToMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/cancel")
    suspend fun cancelMotoOwnershipPlan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/convert-to-loan")
    suspend fun convertMotoOwnershipPlanToLoan(@Path("planId") planId: String, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @POST("api/v1/moto-ownership/plans/{planId}/repay")
    suspend fun repayMotoOwnershipPlan(@Path("planId") planId: String, @Body request: RepayMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

    @GET("api/v1/moto-ownership/plans/me")
    suspend fun getMyMotoOwnershipPlans(): MotoOwnershipPlansResponse

    @GET("api/v1/moto-ownership/plans/{planId}")
    suspend fun getMotoOwnershipPlan(@Path("planId") planId: String): MotoOwnershipPlanResponse
}

data class UpfrontDepositDto(
    val id: String, val userId: String, val walletId: String, val principal: Double,
    val interestRate: Double, val interestPaid: Double, val status: String,
    val openedAt: String, val maturesAt: String, val maturedAt: String? = null, val withdrawnAt: String? = null,
)
data class OpenUpfrontDepositRequest(val principal: java.math.BigDecimal)
data class UpfrontDepositResponse(val success: Boolean, val deposit: UpfrontDepositDto, val message: String? = null)
data class UpfrontDepositsResponse(val success: Boolean, val deposits: List<UpfrontDepositDto>)

data class TransactionHistoryResponse(val success: Boolean, val transactions: List<TransactionDto>)

// Real Kakao T-style ride-hailing -- mirrors RideDriver.kt/RideTrip.kt exactly.
data class RideDriverDto(
    val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean,
    val currentLatitude: Double?, val currentLongitude: Double?, val locationUpdatedAt: String?,
)
data class RideDriverResponse(val success: Boolean, val driver: RideDriverDto)
data class SetRideDriverAvailabilityRequest(val available: Boolean)
data class UpdateRideDriverLocationRequest(val latitude: Double, val longitude: Double)
data class RideTripDto(
    val id: String, val passengerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val distanceKm: Double,
    val fare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null means an ASAP
    // request, unchanged from before.
    val scheduledFor: String? = null,
)
data class RideTripResponse(val success: Boolean, val trip: RideTripDto)
data class RideTripsResponse(val success: Boolean, val trips: List<RideTripDto>)
// Real Kakao T-style multi-stop rides (item 214) -- see the backend's RideTripStop.kt
// doc comment.
data class RideStopRequestDto(val address: String, val latitude: Double, val longitude: Double)
data class RequestRideTripRequest(
    val pickupAddress: String, val pickupLatitude: Double, val pickupLongitude: Double,
    val dropoffAddress: String, val dropoffLatitude: Double, val dropoffLongitude: Double,
    val scheduledFor: String? = null,
    val stops: List<RideStopRequestDto>? = null,
)
data class RideTripStopDto(
    val id: String, val tripId: String, val sequence: Int, val address: String,
    val latitude: Double, val longitude: Double, val arrivedAt: String?,
)
data class RideTripStopResponse(val success: Boolean, val stop: RideTripStopDto)
data class RideTripStopsResponse(val success: Boolean, val stops: List<RideTripStopDto>)

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes
// to the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from ride-hailing above (driver uses their own vehicle). Mirrors
// DesignatedDriver.kt/DesignatedDriverTrip.kt exactly.
data class DesignatedDriverDto(
    val id: String, val userId: String, val walletId: String, val licenseNumber: String, val available: Boolean,
    val currentLatitude: Double?, val currentLongitude: Double?, val createdAt: String,
)
data class DesignatedDriverResponse(val success: Boolean, val driver: DesignatedDriverDto?)
data class RegisterDesignatedDriverRequest(val licenseNumber: String)
data class SetDesignatedDriverAvailabilityRequest(val available: Boolean)
data class UpdateDesignatedDriverLocationRequest(val latitude: Double, val longitude: Double)
data class DesignatedDriverTripDto(
    val id: String, val customerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val vehicleMake: String, val vehicleModel: String,
    val vehiclePlate: String, val distanceKm: Double, val fare: java.math.BigDecimal,
    val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
)
data class DesignatedDriverTripResponse(val success: Boolean, val trip: DesignatedDriverTripDto)
data class DesignatedDriverTripsResponse(val success: Boolean, val trips: List<DesignatedDriverTripDto>)
data class RequestDesignatedDriverTripRequest(
    val pickupAddress: String, val pickupLatitude: Double, val pickupLongitude: Double,
    val dropoffAddress: String, val dropoffLatitude: Double, val dropoffLongitude: Double,
    val vehicleMake: String, val vehicleModel: String, val vehiclePlate: String,
)

// Real Kakao T 바이크 (Kakao T Bike, item 222) -- real PEER-TO-PEER bike/scooter rental
// pool (any user self-registers a bike they own), billed by elapsed TIME at rental end
// -- distinct from ride-hailing/designated-driver, which both know their fare up front.
// Mirrors Bike.kt/BikeRentalSession.kt exactly.
data class RegisterBikeRequest(val type: String, val latitude: Double, val longitude: Double)
data class BikeDto(
    val id: String, val ownerUserId: String, val walletId: String, val type: String,
    val currentLatitude: Double, val currentLongitude: Double, val available: Boolean, val createdAt: String,
)
data class BikeResponse(val success: Boolean, val bike: BikeDto)
data class BikesResponse(val success: Boolean, val bikes: List<BikeDto>)
data class SetBikeAvailabilityRequest(val available: Boolean)
data class UpdateBikeLocationRequest(val latitude: Double, val longitude: Double)
data class StartBikeRentalRequest(val bikeId: String, val startLatitude: Double, val startLongitude: Double)
data class EndBikeRentalRequest(val endLatitude: Double, val endLongitude: Double)
data class BikeRentalSessionDto(
    val id: String, val bikeId: String, val riderUserId: String, val startedAt: String, val endedAt: String?,
    val startLatitude: Double, val startLongitude: Double, val endLatitude: Double?, val endLongitude: Double?,
    val durationMinutes: Int?, val totalFare: java.math.BigDecimal?, val platformFee: java.math.BigDecimal?,
    val status: String,
)
data class BikeRentalResponse(val success: Boolean, val rental: BikeRentalSessionDto)
data class BikeRentalsResponse(val success: Boolean, val rentals: List<BikeRentalSessionDto>)

// Real Kakao T 주차 (Kakao T Parking, item 223) -- real PEER-TO-PEER parking-spot
// rental pool (any user self-lists a spot they own/control), billed by elapsed HOURS
// at checkout -- same "settle at end, no fare known up front" shape Bike already
// establishes, just hourly instead of per-minute. Mirrors ParkingSpot.kt/
// ParkingSession.kt exactly.
data class RegisterParkingSpotRequest(val address: String, val latitude: Double, val longitude: Double, val hourlyRate: java.math.BigDecimal)
data class ParkingSpotDto(
    val id: String, val ownerUserId: String, val walletId: String, val address: String,
    val latitude: Double, val longitude: Double, val hourlyRate: java.math.BigDecimal,
    val available: Boolean, val createdAt: String,
)
data class ParkingSpotResponse(val success: Boolean, val spot: ParkingSpotDto)
data class ParkingSpotsResponse(val success: Boolean, val spots: List<ParkingSpotDto>)
data class SetParkingSpotAvailabilityRequest(val available: Boolean)
data class StartParkingSessionRequest(val spotId: String)
data class ParkingSessionDto(
    val id: String, val spotId: String, val renterUserId: String, val startedAt: String, val endedAt: String?,
    val durationMinutes: Int?, val totalFare: java.math.BigDecimal?, val platformFee: java.math.BigDecimal?,
    val status: String,
)
data class ParkingSessionResponse(val success: Boolean, val session: ParkingSessionDto)
data class ParkingSessionsResponse(val success: Boolean, val sessions: List<ParkingSessionDto>)

// Real Kakao T 시외버스 (intercity bus booking, item 224) -- real peer-to-peer
// coach-operator trip pool, fare charged in FULL at booking time (not settled at end
// like Parking/Bike, since a bus ticket's fare is known up front). Mirrors BusTrip.kt/
// BusBooking.kt exactly.
data class PostBusTripRequest(
    val origin: String, val destination: String, val departureTime: String,
    val totalSeats: Int, val farePerSeat: java.math.BigDecimal,
)
data class BusTripDto(
    val id: String, val operatorUserId: String, val walletId: String, val origin: String, val destination: String,
    val departureTime: String, val totalSeats: Int, val availableSeats: Int, val farePerSeat: java.math.BigDecimal,
    val createdAt: String,
)
data class BusTripResponse(val success: Boolean, val trip: BusTripDto)
data class BusTripsResponse(val success: Boolean, val trips: List<BusTripDto>)
data class BookBusSeatsRequest(val tripId: String, val seatCount: Int)
data class BusBookingDto(
    val id: String, val tripId: String, val riderUserId: String, val seatCount: Int,
    val totalFare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val paymentTransactionId: String,
    val status: String, val refundTransactionId: String?, val createdAt: String,
)
data class BusBookingResponse(val success: Boolean, val booking: BusBookingDto)
data class BusBookingsResponse(val success: Boolean, val bookings: List<BusBookingDto>)

// Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- see the
// backend's KnowledgeQuestion.kt/KnowledgeAnswer.kt doc comments for the full sourced
// account. A genuinely different shape from the trip/rental DTOs above -- no wallet
// movement, no location. Mirrors those entities' field names exactly.
data class KnowledgeCategory(val id: String, val label: String)
data class KnowledgeCategoriesResponse(val success: Boolean, val categories: List<KnowledgeCategory>)
data class PostKnowledgeQuestionRequest(val category: String, val title: String, val body: String)
data class KnowledgeQuestionDto(
    val id: String, val askerId: String, val category: String, val title: String, val body: String,
    val adoptedAnswerId: String?, val createdAt: String,
)
data class KnowledgeQuestionResponse(val success: Boolean, val question: KnowledgeQuestionDto)
data class KnowledgeQuestionsResponse(val success: Boolean, val questions: List<KnowledgeQuestionDto>)
data class PostKnowledgeAnswerRequest(val body: String)
data class KnowledgeAnswerDto(
    val id: String, val questionId: String, val answererId: String, val body: String,
    val isAdopted: Boolean, val createdAt: String,
)
data class KnowledgeAnswerResponse(val success: Boolean, val answer: KnowledgeAnswerDto)
data class KnowledgeAnswersResponse(val success: Boolean, val answers: List<KnowledgeAnswerDto>)
data class KnowledgeReputationResponse(val success: Boolean, val adoptedAnswerCount: Int)

// Real Kakao T-style post-trip driver rating (item 213) -- see the backend's
// RideTripReview.kt doc comment.
data class SubmitRideReviewRequest(val rating: Int, val comment: String? = null)
data class RideTripReviewDto(
    val id: String, val tripId: String, val passengerId: String, val driverId: String,
    val rating: Int, val comment: String?, val createdAt: String,
)
data class RideTripReviewResponse(val success: Boolean, val review: RideTripReviewDto)
data class RideDriverRatingResponse(val success: Boolean, val average: Double?, val count: Long)
data class RideDriverReviewsResponse(val success: Boolean, val reviews: List<RideTripReviewDto>)

// Real 당근마켓 중고차 정비소 동행 (used-car mechanic-inspection accompaniment) -- mirrors
// bank-mfe's lib/vehicleInspection.ts exactly.
data class VehicleInspectionMechanicDto(
    val id: String, val userId: String, val walletId: String, val businessName: String,
    val available: Boolean, val createdAt: String,
)
data class RegisterInspectionMechanicRequest(val businessName: String)
data class SetInspectionMechanicAvailabilityRequest(val available: Boolean)
data class RequestVehicleInspectionRequest(val listingId: String, val mechanicId: String, val fee: java.math.BigDecimal, val scheduledFor: String)
data class CompleteVehicleInspectionRequest(val findings: String? = null)
data class VehicleInspectionBookingDto(
    val id: String, val listingId: String, val buyerId: String, val mechanicId: String,
    val fee: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val scheduledFor: String,
    val status: String, val findings: String?, val createdAt: String,
)
data class VehicleInspectionMechanicResponse(val success: Boolean, val mechanic: VehicleInspectionMechanicDto)
data class VehicleInspectionMechanicOrNullResponse(val success: Boolean, val mechanic: VehicleInspectionMechanicDto?)
data class VehicleInspectionMechanicsResponse(val success: Boolean, val mechanics: List<VehicleInspectionMechanicDto>)
data class VehicleInspectionBookingResponse(val success: Boolean, val booking: VehicleInspectionBookingDto)
data class VehicleInspectionBookingsResponse(val success: Boolean, val bookings: List<VehicleInspectionBookingDto>)

// Real Toss 내 차 시세 (my car's market value)-style vehicle value estimator -- mirrors
// bank-mfe's lib/vehicles.ts exactly.
data class VehicleDto(
    val id: String, val make: String, val model: String, val modelYear: Int,
    val purchasePrice: java.math.BigDecimal, val purchaseDate: String, val mileageKm: Int, val createdAt: String,
)
data class RegisterVehicleRequest(
    val make: String, val model: String, val modelYear: Int,
    val purchasePrice: java.math.BigDecimal, val purchaseDate: String, val mileageKm: Int,
)
data class UpdateVehicleMileageRequest(val mileageKm: Int)
data class VehicleValuationDto(
    val vehicle: VehicleDto, val ageYears: Int, val expectedMileageKm: Int,
    val currentEstimatedValue: java.math.BigDecimal, val estimatedValueIn1Year: java.math.BigDecimal,
    val estimatedValueIn2Years: java.math.BigDecimal, val estimatedValueIn3Years: java.math.BigDecimal,
)
data class VehicleResponse(val success: Boolean, val vehicle: VehicleDto)
data class VehiclesResponse(val success: Boolean, val vehicles: List<VehicleDto>)
data class VehicleValuationResponse(val success: Boolean, val valuation: VehicleValuationDto)

// Real Toss 유스 (Toss Youth)-style guardian-child account link -- mirrors bank-mfe's
// lib/family.ts exactly.
data class InviteChildRequest(val childPhoneNumber: String)
data class RespondToInviteRequest(val accept: Boolean)
data class FamilyLinkDto(
    val id: String, val guardianUserId: String, val childUserId: String,
    val status: String, val createdAt: String, val respondedAt: String?,
    // Real spend-limit enforcement (2026-07-27) -- see FamilyLinkService.setSpendLimit's
    // own doc comment on the backend. Already real-enforced against every P2P send a
    // child makes (P2pService.sendDirect calls enforceSpendLimit before the ledger
    // movement) -- but a guardian had no way to ever SET one until now, so the
    // enforcement path could never actually trigger.
    val dailySpendLimit: java.math.BigDecimal? = null,
)
data class FamilyLinkViewDto(val link: FamilyLinkDto, val guardianName: String, val childName: String)
data class ChildOverviewDto(val childUserId: String, val childName: String, val walletBalance: Double, val recentTransactions: List<TransactionDto>)
data class FamilyLinkResponse(val success: Boolean, val link: FamilyLinkDto)
data class FamilyLinksResponse(val success: Boolean, val invites: List<FamilyLinkDto>)
data class FamilyLinkViewsResponse(val success: Boolean, val children: List<FamilyLinkViewDto> = emptyList(), val guardians: List<FamilyLinkViewDto> = emptyList())
data class ChildOverviewResponse(val success: Boolean, val overview: ChildOverviewDto)
data class SetSpendLimitRequest(val dailySpendLimit: java.math.BigDecimal?)

data class SpendingCategoryDto(val name: String, val amount: java.math.BigDecimal)
data class SpendingInsightResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal)

data class SetBudgetRequest(val category: String? = null, val monthlyLimit: java.math.BigDecimal)
data class BudgetViewDto(
    val category: String?,
    val monthlyLimit: java.math.BigDecimal,
    val spent: java.math.BigDecimal,
    val remaining: java.math.BigDecimal,
    val percentUsed: Int,
    val status: String,
)
data class GetBudgetsResponse(val success: Boolean, val budgets: List<BudgetViewDto>)
data class BudgetSummaryDto(val category: String?, val monthlyLimit: java.math.BigDecimal)
data class SetBudgetResponse(val success: Boolean, val budget: BudgetSummaryDto)

data class ConfigureAutoTopUpRequest(
    val linkedAccountId: String,
    val thresholdAmount: java.math.BigDecimal,
    val topUpAmount: java.math.BigDecimal,
    val dailyTriggerCap: Int = 3,
    val enabled: Boolean = true,
)
data class AutoTopUpSettingDto(
    val id: String,
    val userId: String,
    val walletId: String,
    val linkedAccountId: String,
    val enabled: Boolean,
    val thresholdAmount: java.math.BigDecimal,
    val topUpAmount: java.math.BigDecimal,
    val dailyTriggerCap: Int,
    val triggersToday: Int,
    val lastTriggerDate: String? = null,
    val lastTriggeredAt: String? = null,
)
data class GetAutoTopUpSettingResponse(val success: Boolean, val setting: AutoTopUpSettingDto)
data class TriggerAutoTopUpResponse(val success: Boolean, val triggered: Boolean, val reason: String?)

// Real Kakao Bank 모임통장 (group/shared account) equivalent -- mirrors
// GroupAccount.kt/GroupAccountService.kt exactly.
data class GroupAccountDto(val id: String, val name: String, val ownerId: String, val walletId: String, val monthlyDuesAmount: java.math.BigDecimal?, val createdAt: String)
data class GroupAccountMemberDto(val userId: String, val firstName: String, val lastName: String, val isOwner: Boolean, val joinedAt: String)
data class CreateGroupAccountRequest(val name: String)
data class CreateGroupAccountResponse(val success: Boolean, val groupAccount: GroupAccountDto)
data class GroupAccountsResponse(val success: Boolean, val groupAccounts: List<GroupAccountDto>)
data class GroupAccountDetailResponse(val success: Boolean, val groupAccount: GroupAccountDto, val balance: java.math.BigDecimal, val members: List<GroupAccountMemberDto>, val message: String? = null)
data class InviteMemberRequest(val phoneNumber: String)
data class InviteMemberResponse(val success: Boolean, val member: GroupAccountMemberDto)
data class GroupAccountAmountRequest(val amount: java.math.BigDecimal)
data class SetDuesAmountRequest(val amount: java.math.BigDecimal?)
data class GroupAccountDuesMemberDto(val userId: String, val firstName: String, val lastName: String, val contributedAmount: java.math.BigDecimal, val paid: Boolean)
data class GroupAccountDuesDto(val duesAmount: java.math.BigDecimal?, val cycleMonth: String, val members: List<GroupAccountDuesMemberDto>)
data class GroupAccountDuesResponse(val success: Boolean, val dues: GroupAccountDuesDto)
data class RemindUnpaidDuesResponse(val success: Boolean, val remindedCount: Int)

// Real ikimina -- Rwanda's own rotating savings & credit association (ROSCA). See the
// backend's Ikimina.kt doc comment for the full sourced account. Distinct from
// GroupAccountDto above (Kakao Bank 모임통장): that feature has one permanent owner
// with sole withdrawal authority; an ikimina rotates the full pot to a different
// member each real round, until everyone has been paid exactly once. Genuinely the
// first feature in this codebase not sourced from Toss/Kakao/Naver/Coupang. Mirrors
// bank-mfe's lib/ikimina.ts exactly.
data class IkiminaDto(
    val id: String, val name: String, val organizerId: String, val walletId: String,
    val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int,
    val currentRound: Int, val status: String, val createdAt: String,
)
data class IkiminaMemberDto(
    val userId: String, val firstName: String, val lastName: String,
    val payoutOrder: Int, val hasReceivedPayout: Boolean, val isOrganizer: Boolean,
)
data class IkiminaContributionStatusDto(val userId: String, val contributed: Boolean)
data class CreateIkiminaRequest(val name: String, val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int)
data class CreateIkiminaResponse(val success: Boolean, val ikimina: IkiminaDto)
data class IkiminasResponse(val success: Boolean, val ikiminas: List<IkiminaDto>)
data class IkiminaDetailResponse(
    val success: Boolean, val ikimina: IkiminaDto, val balance: java.math.BigDecimal,
    val members: List<IkiminaMemberDto>, val currentRoundContributions: List<IkiminaContributionStatusDto>,
)
data class InviteIkiminaMemberRequest(val phoneNumber: String)
data class InviteIkiminaMemberResponse(val success: Boolean, val member: IkiminaMemberDto)
data class IkiminaPayoutResponse(val success: Boolean, val ikimina: IkiminaDto, val recipientUserId: String, val amount: java.math.BigDecimal)

// Real Umurenge SACCO-style shares & dividends -- Rwanda's own government-backed
// cooperative savings model (416 real sector SACCOs, 4M+ members, RWF 200B+ deposits
// as of 2024). Distinct from IkiminaDto (informal rotating-pot ROSCA, no shares/
// dividends): a SACCO member buys real shares and receives periodic real dividend
// distributions tied to the pool's real performance. Mirrors bank-mfe's lib/sacco.ts
// exactly.
data class SaccoShareholdingDto(
    val id: String, val userId: String, val walletId: String,
    val sharesHeld: java.math.BigDecimal, val totalContributed: java.math.BigDecimal, val createdAt: String,
)
data class SaccoAmountRequest(val amount: java.math.BigDecimal)
data class SaccoShareholdingResponse(val success: Boolean, val shareholding: SaccoShareholdingDto?, val currentValue: java.math.BigDecimal?)
data class SaccoDividendPayoutDto(
    val id: String, val distributionId: String, val shareholdingId: String,
    val amount: java.math.BigDecimal, val payoutTransactionId: String, val createdAt: String,
)
data class SaccoDividendPayoutsResponse(val success: Boolean, val payouts: List<SaccoDividendPayoutDto>)

// Real Rwanda coffee-cooperative harvest-advance / input financing -- sourced beyond
// this session's usual Toss/Kakao/Naver/Coupang reference ecosystems, grounded in
// Rwanda's own real coffee sector (Rwanda Coffee Cooperatives Federation: 13 member
// cooperatives, ~19,000 producer members). A direct itunda-to-farmer lending
// relationship mirroring the regular Loans feature's own loan_payable receivable
// shape -- never a shared/pooled wallet. Mirrors bank-mfe's lib/harvestAdvance.ts
// exactly, including the post-fix repay contract (amount must equal the full real
// outstanding principal, no partial repayment).
data class CooperativeDto(
    val id: String, val name: String, val cropType: String, val registrationNumber: String?, val createdAt: String,
)
data class CooperativeMembershipDto(
    val id: String, val cooperativeId: String, val userId: String, val walletId: String,
    val memberSince: String, val active: Boolean,
)
data class HarvestAdvanceDto(
    val id: String, val membershipId: String, val walletId: String, val principalAmount: java.math.BigDecimal,
    val purpose: String, val expectedHarvestDate: String, val repaymentDueDate: String, val status: String,
    val disbursedAt: String?, val repaidAt: String?, val createdAt: String,
)
data class RegisterCooperativeRequest(val name: String, val cropType: String, val registrationNumber: String?)
data class RequestAdvanceRequest(
    val membershipId: String, val principalAmount: java.math.BigDecimal, val purpose: String, val expectedHarvestDate: String,
)
data class RepayAdvanceRequest(val amount: java.math.BigDecimal)
data class CooperativeResponse(val success: Boolean, val cooperative: CooperativeDto)
data class CooperativeMembershipResponse(val success: Boolean, val membership: CooperativeMembershipDto)
data class CooperativeMembershipsResponse(val success: Boolean, val memberships: List<CooperativeMembershipDto>)
data class CooperativeOverviewResponse(
    val success: Boolean, val cooperative: CooperativeDto, val myMembership: CooperativeMembershipDto, val memberCount: Int,
)
data class HarvestAdvanceResponse(val success: Boolean, val advance: HarvestAdvanceDto)
data class HarvestAdvancesResponse(val success: Boolean, val advances: List<HarvestAdvanceDto>)

// Real Rwanda VUP (Vision 2020 Umurenge Programme) Financial Services means-tested
// microloan -- sourced beyond this session's usual Toss/Kakao/Naver/Coupang reference
// ecosystems. VUP, run by LODA since 2008, subsidizes microloans for income-generating
// activities (farming, livestock, small business) targeted at households in poorer
// Ubudehe categories (NISR EICV7 2023/24: ~100,000 RWF average loan). Since a real
// 2014-07-29 Cabinet decision, administration moved to Umurenge SACCOs, which set the
// rate at 11% (Rwanda Inspirer: uptake fell after that rate hike). Honest v1
// limitation: declaredUbudeheCategory is self-declared by the user, not verified
// against Rwanda's real government Ubudehe household-classification registry. Mirrors
// bank-mfe's lib/vupLoan.ts exactly.
data class VupLoanDto(
    val id: String, val userId: String, val declaredUbudeheCategory: Int, val purpose: String,
    val principalAmount: java.math.BigDecimal, val outstandingPrincipal: java.math.BigDecimal,
    val interestRate: Double, val status: String, val appliedAt: String, val disbursedAt: String?, val dueDate: String?,
)
data class ApplyForVupLoanRequest(val declaredUbudeheCategory: Int, val purpose: String, val amount: java.math.BigDecimal)
data class RepayVupLoanRequest(val amount: java.math.BigDecimal)
data class VupLoanResponse(val success: Boolean, val loan: VupLoanDto)
data class VupLoansResponse(val success: Boolean, val loans: List<VupLoanDto>)
data class VupLoanEligibilityResponse(
    val success: Boolean, val hasActiveLoan: Boolean, val canApply: Boolean,
    val minUbudeheCategory: Int, val maxUbudeheCategory: Int, val interestRate: Double, val maxAmount: java.math.BigDecimal,
)

// Real Rwanda BRD (Development Bank of Rwanda) higher-education student loan -- sourced
// beyond this session's usual Toss/Kakao/Naver/Coupang reference ecosystems. Rwanda has
// run a national student-loan-and-bursary scheme since Law No. 44/2015, administered by
// BRD since an October 2016 MINEDUC agreement. Real scale: RWF 221.85 billion disbursed
// to 139,925 students (through mid-2023), fixed interest rates of 11% undergraduate /
// 12% postgraduate, repayment terms of 2-10 years (brd.rw). Honest v1 limitation:
// declaredAnnualHouseholdIncome is self-declared, not verified against BRD's real
// Financial Means Testing (FMT) process, and the real 8%-of-income payroll deduction is
// only ever a SUGGESTED amount here -- itunda has no payroll/RRA-integration path to
// enforce it. Mirrors bank-mfe's lib/studentLoan.ts exactly.
data class StudentLoanDto(
    val id: String, val userId: String, val level: String, val declaredAnnualHouseholdIncome: java.math.BigDecimal,
    val principalAmount: java.math.BigDecimal, val outstandingBalance: java.math.BigDecimal, val interestRate: Double,
    val status: String, val appliedAt: String, val disbursedAt: String?, val expectedGraduationDate: String, val graceEndsAt: String?,
)
data class ApplyForStudentLoanRequest(
    val level: String, val declaredAnnualHouseholdIncome: java.math.BigDecimal, val amount: java.math.BigDecimal, val expectedGraduationDate: String,
)
data class RepayStudentLoanRequest(val amount: java.math.BigDecimal)
data class StudentLoanResponse(val success: Boolean, val loan: StudentLoanDto)
data class StudentLoansResponse(val success: Boolean, val loans: List<StudentLoanDto>)
data class StudentLoanSuggestedPaymentResponse(
    val success: Boolean, val loanId: String, val outstandingBalance: java.math.BigDecimal, val suggestedMonthlyPayment: java.math.BigDecimal, val note: String,
)

// Real Rwanda moto-taxi ownership savings-to-loan plan -- sourced beyond this
// session's usual Toss/Kakao/Naver/Coupang reference ecosystems. A real ~600,000 RWF
// entry-level moto-taxi bike is a documented purchase price (Anadolu Agency, 14 May
// 2021 -- profiles a rider who saved for years to buy her own bike after paying daily
// rent to a bike owner). Rent-to-own is a proven-relevant mechanic in this exact
// sector (Frontier Tech Hub's Kigali e-moto pilot: Ampersand's rent-to-own model
// increased driver revenue 78%/month; WeeTracker/WEF coverage of the same). This
// fills the gap left by Rwanda's dissolved taxi-moto cooperatives (Africa-Press,
// 2026). Honest v1 limitation: once converted to a loan, this is an UNSECURED
// facility -- itunda has no path to a real chattel lien or RURA vehicle-registry
// hold, so it cannot repossess the bike or verify it was actually purchased. Mirrors
// bank-mfe's lib/motoOwnership.ts exactly.
data class MotoOwnershipPlanDto(
    val id: String, val userId: String, val bikePrice: java.math.BigDecimal, val downPaymentTarget: java.math.BigDecimal,
    val savedAmount: java.math.BigDecimal, val dailyContribution: java.math.BigDecimal, val loanOutstanding: java.math.BigDecimal,
    val status: String, val lastAutoContributionAt: String?, val createdAt: String,
)
data class CreateMotoOwnershipPlanRequest(val bikePrice: java.math.BigDecimal, val dailyContribution: java.math.BigDecimal)
data class ContributeToMotoOwnershipPlanRequest(val amount: java.math.BigDecimal)
data class RepayMotoOwnershipPlanRequest(val amount: java.math.BigDecimal)
data class MotoOwnershipPlanResponse(val success: Boolean, val plan: MotoOwnershipPlanDto)
data class MotoOwnershipPlansResponse(val success: Boolean, val plans: List<MotoOwnershipPlanDto>)

// Real KakaoBank mini-style capped starter wallet -- see MiniWalletService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
data class OpenMiniWalletResponse(val success: Boolean, val wallet: Wallet)
data class DepositMiniWalletRequest(val amount: java.math.BigDecimal)
data class DepositMiniWalletResponse(val success: Boolean, val id: String, val amount: java.math.BigDecimal, val completedAt: String)

// Real offline-action-queue replay (2026-07-13) -- mirrors
// services/backend/offline/src/main/kotlin/rw/itunda/offline/web/ActionsBatchController.kt
// exactly. See network/OfflineActionQueue.kt for the local persisted queue this replays.
data class BatchActionRequest(
    val clientActionId: String,
    val type: String,
    val idempotencyKey: String,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchRequest(val actions: List<BatchActionRequest>)

data class BatchActionResultDto(
    val clientActionId: String,
    val type: String,
    val status: Int,
    val body: Map<String, @JvmSuppressWildcards Any?>,
)

data class BatchResponse(val success: Boolean, val results: List<BatchActionResultDto>)

// Real device binding (2026-07-21) -- shared, public so any money-moving call site
// can check it, not just MainViewModel's original three (sendTransfer/
// depositToSavingsGoal/claimInterest). A 403 alone isn't enough (other real 403s
// exist elsewhere in this backend); the real `ApiError.code` field in the response
// body is what DeviceVerificationFilter actually sets, so that's what's checked.
// Mirrors bank-mfe's own `err.code === 'DEVICE_NOT_VERIFIED'` check on its ApiError
// exactly.
fun isDeviceNotVerifiedError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "DEVICE_NOT_VERIFIED"
    } catch (_: Exception) {
        false
    }
}

// Same pattern as isDeviceNotVerifiedError above -- CertificateController's
// /certificate/issue real-403s with code KYC_REQUIRED when the caller's identity
// isn't verified yet (CertificateUserNotVerifiedException), so CreditScoreScreen/
// CertificateScreen can show a real, specific message instead of a generic failure.
fun isKycRequiredError(e: retrofit2.HttpException): Boolean {
    if (e.code() != 403) return false
    return try {
        val body = e.response()?.errorBody()?.string() ?: return false
        com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString == "KYC_REQUIRED"
    } catch (_: Exception) {
        false
    }
}

// Generic form of isDeviceNotVerifiedError/isKycRequiredError above, for call sites
// (like the Mini wallet's birth-date/age gate, 2026-07-28) that need to distinguish
// between multiple real ApiError codes on the same HTTP status rather than just a
// single yes/no check.
fun apiErrorCode(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString
} catch (_: Exception) {
    null
}

// Same pattern as apiErrorCode above, for the `message` field of core.web.ApiError --
// found missing 2026-08-08 (Toss Simplicity21 "adding innovation upon innovation"
// research pass, checking whether P2P transfer's own mature/assumed-solid error
// handling actually was): bank-mfe's ApiError already parses and shows this real
// backend text (lib/api.ts), but MainViewModel.backendErrorMessage's status-code-only
// switch meant a wallet-frozen/family-spend-limit/self-payment/rate-limit decline all
// fell through to a generic "Something went wrong" on Android (and iOS, mirrored) --
// distinct backend errors the sender could otherwise never tell apart.
fun apiErrorMessage(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("message")?.asString
} catch (_: Exception) {
    null
}

// Network Client Singleton
object NetworkClient {
    // Was hardcoded to "http://10.0.2.2:8080/" -- the emulator-only loopback alias, at
    // the wrong port (services/backend listens on 4001). Real base URL is now passed
    // in from :app's own real BuildConfig.API_BASE_URL via init() below (2026-07-22
    // change, made while relocating this whole file from :app to :core:network so
    // Feature modules can depend on it directly -- a Gradle library module can't read
    // an application module's BuildConfig, and passing the value in at runtime avoids
    // needing to relocate the buildConfigField/`-PapiBaseUrl=` override plumbing too).
    // Overridable at build time for a physical device -- see app/build.gradle.kts's
    // apiBaseUrl comment (2026-07-11 fix, still the actual place that's set).
    private var BASE_URL: String = "http://10.0.2.2:4001/"

    // Must be initialized once, from ItundaApplication.onCreate(), before any request
    // fires -- see that file. Held nullable rather than lateinit so a request made
    // before init() (which should never happen, but interceptors must never crash the
    // whole app over it) just goes out unauthenticated instead of throwing.
    private var tokenStore: TokenStore? = null

    // Real device binding (2026-07-21 port) -- same nullable-not-lateinit reasoning
    // as tokenStore above.
    private var deviceStore: DeviceStore? = null

    fun init(context: Context, baseUrl: String) {
        BASE_URL = baseUrl
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    fun currentDeviceStore(): DeviceStore =
        deviceStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    // Real bearer-token injection (2026-07-11) -- previously commented out entirely
    // (see this file's git history / the removed "TODO: Inject Token" line), which is
    // exactly the gap SaroniteBridge.kt's ItundaSaroniteHostBridge.getAuthToken()
    // named as the reason it honestly returned null instead of a fabricated token.
    private val authInterceptor = Interceptor { chain ->
        val token = tokenStore?.getAccessToken()
        val newRequest = chain.request().newBuilder().apply {
            if (token != null) addHeader("Authorization", "Bearer $token")
        }.build()
        chain.proceed(newRequest)
    }

    private val diagnosticLoggingInterceptor = Interceptor { chain ->
        val request = chain.request()
        android.util.Log.d("ITUNDA_NET", "--> ${request.method} ${request.url}")
        try {
            val response = chain.proceed(request)
            android.util.Log.d("ITUNDA_NET", "<-- ${response.code} ${request.url}")
            response
        } catch (e: Exception) {
            android.util.Log.e("ITUNDA_NET", "<-- FAILED ${request.url}: ${e.javaClass.simpleName}: ${e.message}")
            throw e
        }
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(diagnosticLoggingInterceptor)
        .addInterceptor(authInterceptor)
        .build()

    private val retrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    val apiService: ApiService by lazy { retrofit.create(ApiService::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }

    // Real WebSocket live-transport (2026-07-18) -- see
    // rw.itunda.app.websocket.MessagingWebSocketHandler's own doc comment for the real
    // backend push shape this mirrors exactly. Ported from bank-mfe's own
    // connectMessagingSocket, which established this session's push-payload contract.
    // Now routes both "message" (1:1) and "group_message" pushes -- group chat gained a
    // real mobile UI the same day this was extended.
    private val gson = Gson()

    fun connectMessagingSocket(onPush: (MessagingSocketPush) -> Unit): WebSocket {
        val token = tokenStore?.getAccessToken().orEmpty()
        val wsUrl = BASE_URL.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") + "ws/messaging?token=$token"
        val request = Request.Builder().url(wsUrl).build()
        return okHttpClient.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val json = gson.fromJson(text, JsonObject::class.java)
                        when (json.get("type")?.asString) {
                            "message" -> onPush(MessagingSocketPush.DirectMessage(gson.fromJson(json.get("message"), MessageDto::class.java)))
                            "group_message" -> {
                                val groupId = json.get("groupConversationId")?.asString ?: return
                                onPush(MessagingSocketPush.GroupMessagePush(groupId, gson.fromJson(json.get("message"), GroupMessageDto::class.java)))
                            }
                            "presence" -> {
                                val userId = json.get("userId")?.asString ?: return
                                val online = json.get("online")?.asBoolean ?: return
                                onPush(MessagingSocketPush.PresenceChange(userId, online))
                            }
                            "typing" -> {
                                val userId = json.get("userId")?.asString ?: return
                                onPush(
                                    MessagingSocketPush.TypingChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        userId = userId,
                                    ),
                                )
                            }
                            "reaction" -> {
                                val messageId = json.get("messageId")?.asString ?: return
                                val reactionsType = object : com.google.gson.reflect.TypeToken<List<ReactionGroupDto>>() {}.type
                                val reactions: List<ReactionGroupDto> = gson.fromJson(json.get("reactions"), reactionsType)
                                onPush(
                                    MessagingSocketPush.ReactionChange(
                                        conversationId = json.get("conversationId")?.asString,
                                        groupConversationId = json.get("groupConversationId")?.asString,
                                        messageId = messageId,
                                        reactions = reactions,
                                    ),
                                )
                            }
                        }
                    } catch (e: Exception) {
                        // Real, non-critical -- a malformed/unexpected push shouldn't
                        // crash the socket listener; the 4s poll stays as the real
                        // fallback delivery path regardless.
                    }
                }
            },
        )
    }

    // Real typing indicator send (2026-07-19) -- best-effort, matching bank-mfe's own
    // sendTyping helper; OkHttp's WebSocket.send already silently no-ops on a
    // closed/never-connected socket.
    fun sendTyping(socket: WebSocket, conversationId: String? = null, groupConversationId: String? = null) {
        val payload = mutableMapOf<String, Any>("type" to "typing")
        conversationId?.let { payload["conversationId"] = it }
        groupConversationId?.let { payload["groupConversationId"] = it }
        socket.send(gson.toJson(payload))
    }
}

sealed class MessagingSocketPush {
    data class DirectMessage(val message: MessageDto) : MessagingSocketPush()
    data class GroupMessagePush(val groupConversationId: String, val message: GroupMessageDto) : MessagingSocketPush()
    // Real online/offline presence (2026-07-19) -- see
    // rw.itunda.core.realtime.RealtimeMessagePublisher.publishPresenceChange's own doc
    // comment for the real transition-only/1:1-only scoping.
    data class PresenceChange(val userId: String, val online: Boolean) : MessagingSocketPush()
    // Real typing indicator (2026-07-19) -- see
    // MessagingWebSocketHandler.handleTextMessage's own doc comment on the backend.
    // Ephemeral, never persisted; server-ratelimited to one relay per (user,
    // conversation) per 2s. Exactly one of conversationId/groupConversationId is set.
    data class TypingChange(val conversationId: String?, val groupConversationId: String?, val userId: String) : MessagingSocketPush()
    // Real live reaction push (2026-07-19) -- see
    // MessagingWebSocketHandler.publishReactionChange/publishGroupReactionChange's own
    // doc comments. Exactly one of conversationId/groupConversationId is set.
    data class ReactionChange(
        val conversationId: String?, val groupConversationId: String?, val messageId: String, val reactions: List<ReactionGroupDto>,
    ) : MessagingSocketPush()
}
