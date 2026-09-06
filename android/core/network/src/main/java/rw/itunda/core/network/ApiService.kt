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
data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
    val referralCode: String? = null,
    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) enforcement, added to
    // the backend/bank-mfe 2026-08-18 but never ported to this DTO -- found 2026-08-30
    // during a market-readiness audit: AuthService.register real-400s
    // (RequiredTermsNotAcceptedException) whenever the required terms ids are missing,
    // and this field's absence meant every single native registration silently sent an
    // empty list, so registration on this platform has been completely broken since
    // that date. See TermsCatalog.kt's own doc comment for the full sourced account.
    val acceptedTermsIds: List<String> = emptyList(),
    // Added 2026-07-21, same reasoning as LoginRequest's deviceId/deviceName -- the
    // device that registers proves password ownership in the same request, so it's
    // auto-trusted server-side (DeviceService.recordRegistrationDevice) with no
    // separate step-up needed.
    val deviceId: String? = null,
    val deviceName: String? = null,
    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see
    // DeviceKeyManager.exportPublicKeyIfPresent's own doc comment. The SAME real
    // hardware-backed Keystore key item 246 already established, just published at
    // register time too (not only via the separate opt-in Settings toggle) so a brand
    // new device can go straight to biometric-only login next time -- see
    // AuthService.register's own doc comment on the backend for
    // DeviceService.registerKeyDuringAuth.
    val devicePublicKey: String? = null,
)

// deviceId/deviceName added 2026-07-21 -- mirrors bank-mfe's real device-binding login
// call exactly (lib/api.ts's login()). See DeviceStore.kt for how these are generated.
// devicePublicKey added 2026-08-23 -- mirrors RegisterRequest's own field exactly, same
// real reasoning (see its own doc comment).
data class LoginRequest(
    val phoneNumber: String,
    val password: String,
    val deviceId: String? = null,
    val deviceName: String? = null,
    val devicePublicKey: String? = null,
)

data class PhoneCheckRequest(val phoneNumber: String)
data class PhoneCheckResponse(val exists: Boolean)

// Mirrors services/backend/core/.../TermsDocument.kt exactly -- see RegisterRequest
// .acceptedTermsIds' own doc comment for why this exists on this platform now.
data class TermsDocument(val id: String, val title: String, val version: String, val required: Boolean, val summary: String)
data class TermsResponse(val success: Boolean, val terms: List<TermsDocument>)

// Mirrors services/backend/core/.../LegalDocumentCatalog.kt exactly -- real
// itunda-branded Terms of Service/Privacy Policy/Credit Data Policy full text,
// closing SettingsScreen.kt's own long-documented "nowhere real to link to" gap.
data class LegalDocument(val id: String, val title: String, val version: String, val bodyMarkdown: String)
data class LegalDocumentsResponse(val success: Boolean, val documents: List<LegalDocument>)
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
    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
    // YouthAccountService.kt's own doc comment. Set via AuthApi.setBirthDate.
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
    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see backend
    // User.pinSet's own doc comment. false only for a pre-PIN-era account whose
    // existing password hasn't been upgraded to a real 6-digit PIN yet -- gates
    // PinUpgradeCard, never blocks the existing password login either way.
    val pinSet: Boolean = true,
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
    // Real unified phone-first entry (2026-08-13) -- see services/backend's identical
    // PhoneCheckRequest/AuthController.checkPhone doc comment. Called before the user
    // has picked "log in" or "sign up" at all; the response drives which one happens.
    @POST("api/v1/auth/check-phone")
    suspend fun checkPhone(@Body request: PhoneCheckRequest): PhoneCheckResponse

    // Real Toss/Korean-fintech-style 약관 동의 (terms consent) catalog -- see
    // RegisterRequest.acceptedTermsIds' own doc comment for why this is only being
    // added now. Public (SecurityConfig permitAll), called before registration.
    @GET("api/v1/auth/terms")
    suspend fun getTerms(): TermsResponse

    // Real itunda-branded legal document bodies -- see LegalDocument's own doc
    // comment. Public (SecurityConfig permitAll), reference material, not a
    // registration consent gate.
    @GET("api/v1/auth/legal-documents")
    suspend fun getLegalDocuments(): LegalDocumentsResponse

    @POST("api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @POST("api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshRequest): AuthResponse

    // Synchronous twin of refresh() above, for OkHttp's Authenticator -- see
    // NetworkClient's real gap found 2026-08-15: a dead-endpoint sweep found refresh()
    // had ZERO callers anywhere in the client, meaning an expired 24h access token
    // (JwtService.kt's real expiry) just surfaced as a raw, unrecoverable 401 on every
    // subsequent screen with no path forward -- exactly the dead-end this codebase's
    // own standing Toss-style error-handling philosophy exists to eliminate.
    // Authenticator.authenticate() runs synchronously on a background thread (same
    // constraint TokenStore's own header comment already documents for the auth
    // interceptor), so it needs a blocking Call, not a suspend fun.
    @POST("api/v1/auth/refresh")
    fun refreshSync(@Body request: RefreshRequest): retrofit2.Call<AuthResponse>

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

    // Real age-eligibility gate for the Youth account (2026-07-28) -- see
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

    // Real Toss-sourced passwordless-login rollout (2026-08-23) -- see this session's
    // real, sourced research on Toss's own actual mechanism (support.toss.im/
    // toss.im/tosscert): registration is phone + OTP, then a real 6-digit "비밀번호"
    // (Toss's own literal term -- not zero credential), with biometric as day-to-day
    // login's real fast path and the PIN as its standing fallback -- exactly what
    // AuthController.kt's own doc comment on the backend implements. Unauthenticated
    // (permitAll, see SecurityConfig.kt) since this IS the initial login itself, not a
    // step-up re-verification of an already-authenticated session like
    // issueDeviceChallenge/verifyDeviceSignature above -- resolves the user from
    // phoneNumber, not a JWT claim.
    @POST("api/v1/auth/login/device/challenge")
    suspend fun loginDeviceChallenge(@Body request: LoginDeviceChallengeRequest): LoginDeviceChallengeResponse

    @POST("api/v1/auth/login/device/verify")
    suspend fun loginWithDeviceSignature(@Body request: LoginWithDeviceSignatureRequest): AuthResponse

    // Real PIN upgrade (2026-08-23) -- see AuthService.setPin's own doc comment on the
    // backend. currentCredential re-proves ownership of the EXISTING password/PIN
    // (whatever shape it currently is) before it's replaced -- same real cost as
    // registerDeviceKey's own password re-entry above, never trusting a client-only
    // check for a credential change.
    @PUT("api/v1/auth/pin")
    suspend fun setAccountPin(@Body request: SetAccountPinRequest): SetAccountPinResponse
}

data class LoginDeviceChallengeRequest(val phoneNumber: String, val deviceId: String)
data class LoginDeviceChallengeResponse(val success: Boolean, val challenge: String)
data class LoginWithDeviceSignatureRequest(val phoneNumber: String, val deviceId: String, val signature: String)
data class SetAccountPinRequest(val currentCredential: String, val newPin: String)
data class SetAccountPinResponse(val success: Boolean, val user: PublicUser)

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

// Mirrors services/backend/core/.../domain/Account.kt exactly (2026-07-11 fix) --
// the previous shape (currency/balance/isPrimary only) didn't match the real
// backend's serialized Account entity at all -- there is no "isPrimary" field on
// the real backend, so `accounts.firstOrNull { it.isPrimary }` silently always
// returned null and fell through to whatever account happened to be first, not
// actually the primary one. `type == "MAIN"` is the real signal.
data class Account(
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

data class AccountResponse(
    val success: Boolean,
    val accounts: List<Account>
)

// Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
// rw.itunda.account.ForeignCurrencyAccountService on the backend for the full account,
// incl. why USD/EUR/GBP specifically (real Rwandan diaspora remittance corridors) and
// why this is real-rate conversion between a user's own accounts, not a cross-border
// receiving rail.
data class OpenForeignAccountRequest(val currency: String)
data class ForeignAccountResponse(val success: Boolean, val account: Account)
data class ForeignAccountsResponse(val success: Boolean, val accounts: List<Account>)
data class ExchangeRateResponse(val success: Boolean, val from: String, val to: String, val rate: Double)
data class ConvertCurrencyRequest(val fromCurrency: String, val toCurrency: String, val amount: Double)
data class CurrencyConversionDto(
    val id: String, val userId: String, val fromCurrency: String, val toCurrency: String,
    val fromAmount: Double, val toAmount: Double, val rate: Double, val marginAmount: Double,
    val transactionId: String, val createdAt: String,
)
data class CurrencyConversionResponse(val success: Boolean, val conversion: CurrencyConversionDto)
data class CurrencyConversionsResponse(val success: Boolean, val conversions: List<CurrencyConversionDto>)

// Real Toss 외환 환율 알림 (exchange rate alert, section 121/168) -- see
// rw.itunda.core.domain.ExchangeRateAlert's own doc comment. Backend shipped fully
// live-verified-safe (ExchangeRateAlertScheduler) with zero client caller anywhere,
// found via a fresh uncalled-endpoint sweep -- the same pattern as section 113/167's
// stock target-price alert.
data class SetRateAlertRequest(val fromCurrency: String, val toCurrency: String, val targetRate: Double, val direction: String)
data class ExchangeRateAlertDto(
    val id: String, val fromCurrency: String, val toCurrency: String,
    val targetRate: Double, val direction: String, val alertTriggeredAt: String?,
)
data class SetRateAlertResponse(val success: Boolean, val alert: ExchangeRateAlertDto)
data class RateAlertsResponse(val success: Boolean, val alerts: List<ExchangeRateAlertDto>)

// Mirrors services/backend/core/.../domain/SavingsGoal.kt / InterestJar.kt.
data class SavingsGoal(
    val id: String,
    val userId: String,
    val accountId: String,
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
data class SimpleSuccessResponse(val success: Boolean)
data class IdentityVerificationRequestResponse(
    val success: Boolean,
    val partnerName: String,
    val status: String,
    val expiresAt: String,
    val requestedFields: List<String>,
)
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
    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see backend
    // MessagingController's own doc comment distinguishing this from the existing
    // per-message pin (pinnedMessageId above). Already returned by GET /conversations
    // for every summary since MessagingController shipped it; bank-mfe wired a client
    // for it (Section 165) but Android never read this field back. Same
    // defined-but-uncalled shape as archived above.
    val pinnedToTop: Boolean = false,
    // Real KakaoTalk favorite chat toggle (itunda Talk redesign, 2026-08-28) -- see
    // TalkApi.kt's own doc comment.
    val favorite: Boolean = false,
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
// Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
// ConversationSummaryDto.pinnedToTop's own doc comment. Named ConversationPinnedToTop
// (not ConversationPinned) to stay distinct from the existing per-message
// pinConversationMessage/unpinConversationMessage pair below, same naming discipline
// bank-mfe's lib/messaging.ts already established for this endpoint.
data class ConversationPinnedToTopResponse(val success: Boolean, val pinned: Boolean)
data class SetConversationPinnedToTopRequest(val pinned: Boolean)
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

// Real open-group DTOs (item 244) -- distinct shape from GroupSummaryDto (a real
// joinCode, but no memberCount/lastMessage/etc. yet since the group was just
// created).
data class CreateOpenGroupRequest(val name: String)
data class OpenGroupDto(val id: String, val name: String, val joinCode: String)
data class OpenGroupResponse(val success: Boolean, val group: OpenGroupDto)
data class JoinGroupByCodeRequest(val joinCode: String)
data class JoinedGroupDto(val id: String, val name: String)
data class JoinGroupResponse(val success: Boolean, val group: JoinedGroupDto)
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
data class MarkSoldRequest(val buyerPhoneNumber: String? = null)
data class BoostListingRequest(val days: Int)
data class UpdateListingPriceRequest(val price: Double)
data class BoostTiersResponse(val success: Boolean, val tiers: Map<String, Double>)

// Real "pay via itunda" Marketplace escrow (2026-07-25) -- see backend
// MarketplaceEscrow.kt's own doc comment. An opt-in safer alternative to the existing
// in-person cash handoff, never replacing it.
data class MarketplaceEscrowDto(
    val id: String, val listingId: String, val buyerId: String, val sellerId: String,
    val amount: Double, val fee: Double, val status: String,
    val holdTransactionId: String, val resolutionTransactionId: String? = null,
    val disputeReason: String? = null,
    // Real gap closed 2026-08-15 -- see the backend MarketplaceEscrow.deliveryAddress's
    // own doc comment (당근마켓 바로구매-style shipped-item support, escrow previously
    // only ever assumed an in-person handoff). Null for the original in-person case.
    val deliveryAddress: String? = null,
    val createdAt: String, val updatedAt: String,
)
data class MarketplaceEscrowResponse(val success: Boolean, val escrow: MarketplaceEscrowDto)
data class DisputeEscrowRequest(val reason: String)
data class PayEscrowRequest(val deliveryAddress: String? = null)
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
// comment. Money leaves the sender's account into a real escrow account the moment a
// gift is sent, and only reaches the recipient's account once they explicitly claim it
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
data class SendGiftRequest(val recipientPhoneNumber: String, val amount: Double, val note: String? = null, val theme: String? = null)

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
    // Real hyperlocal neighborhood -- same gap as JobPostDto/PropertyListingDto's
    // identical fix, see [[project_itunda_full_ecosystem_polish]]. The backend has
    // stamped this on every post since 2026-07-20 and serializes the entity directly.
    val neighborhood: String? = null,
    // Real 동네생활 topic chip (2026-08-28) -- see backend CommunityPost.topic's own doc
    // comment, a lifestyle axis independent of the functional `category` above.
    val topic: String? = null,
    // Real AI-generated 모임 summary (2026-08-28) -- see backend HoodAiSummaryService's
    // own doc comment. Null means either not a meetup post or the honesty gate declined
    // (thin body) -- never fabricate a summary client-side when this is null.
    val aiSummary: String? = null,
    val aiSummaryGeneratedAt: String? = null,
)
data class CreateCommunityPostRequest(
    val category: String, val title: String, val body: String,
    val latitude: Double? = null, val longitude: Double? = null,
    val eventDate: String? = null, val capacity: Int? = null, val topic: String? = null,
)
data class CommunityPostResponse(val success: Boolean, val post: CommunityPostDto)
// joinedCounts added 2026-07-24 -- postId -> real member count of that meetup's group
// chat, closing docs/DESIGN_REFERENCES.md Section 4 recommendation #4's "같이해요
// (join-together) posts get a dedicated pinned mid-feed slot."
data class CommunityPostsResponse(val success: Boolean, val posts: List<CommunityPostDto>, val joinedCounts: Map<String, Int> = emptyMap())
data class CommunityCategoriesResponse(val success: Boolean, val categories: List<CommunityCategoryDto>)
data class CommunityTopicsResponse(val success: Boolean, val topics: List<CommunityCategoryDto>)
data class CommentNotificationsEnabledResponse(val success: Boolean, val commentNotificationsEnabled: Boolean)
data class SetCommentNotificationsEnabledRequest(val enabled: Boolean)
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
    // Real hyperlocal neighborhood -- the backend has stamped this on every job post
    // since 2026-07-20 (same reverse-geocode-or-poster-fallback JobPost.kt's own doc
    // comment describes for Listing/PropertyListing) and serializes the entity
    // directly, but this DTO omitted the field until 2026-08-15, same class of gap as
    // PropertyListingDto's identical fix -- see
    // [[project_itunda_full_ecosystem_polish]] for the full pattern.
    val neighborhood: String? = null,
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
    // Real résumé attach at submission time (2026-08-28) -- see backend
    // JobApplication.resumeSnapshotJson's own doc comment. A raw JSON string; only
    // ever parsed to show "Résumé attached" -- never re-rendered field-by-field here.
    val resumeSnapshotJson: String? = null,
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
    // Real hyperlocal neighborhood -- the backend has stamped this on every listing
    // since 2026-07-20 (PropertyListingService reverse-geocodes it, falling back to the
    // lister's own neighborhood) and serializes the entity directly on every browse
    // endpoint, but this DTO omitted the field until 2026-08-14, so Jackson dropped it
    // and Property rows could never show the hyperlocal label Hood shows on every row.
    val neighborhood: String? = null,
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
    // Real per-restaurant WOW membership badge + max-active-discount (2026-08-28,
    // see backend Merchant.participatesInEatsMembership / getMaxDiscountByMerchantIds).
    val participatesInEatsMembership: Boolean = false,
    val maxDiscountPercent: Int? = null,
)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Maps DTOs (search/directions/transit/place-detail/weather) live in MapsDtos.kt
// (extracted 2026-08-28, itunda Maps redesign, to stay under this file's own baseline).
data class MerchantCategoriesResponse(val success: Boolean, val categories: List<String>)

// Real "Deals" rail (2026-07-25) -- closes docs/DESIGN_REFERENCES.md Section 5
// recommendation #8: a curated deal rail on the Shop landing surface. Every entry is a
// real merchant-set discount, never a fabricated promo -- see backend
// MerchantProductRepository.findDeals's own doc comment.
data class DealProductDto(
    val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double,
    val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val description: String? = null,
    val stockQuantity: Int? = null,
    // rating/reviewCount added 2026-08-25 -- real ProductReview data (batched GROUP BY
    // lookup, see ShoppingController.getDeals' own doc comment), not fabricated. null
    // rating means the product genuinely has zero reviews yet -- render no stars, not a
    // fake default.
    val rating: Double? = null, val reviewCount: Long = 0L,
    // Real "Best seller" badge (2026-08-28) -- a genuine, derived signal (real gross
    // order count per product, see backend ShoppingController.bestSellerProductIds'
    // own doc comment), not fabricated marketing copy.
    val isBestSeller: Boolean = false,
)
data class DealsResponse(val success: Boolean, val products: List<DealProductDto>)

// Real "frequently ordered together" item (2026-08-28) -- see getFrequentlyOrderedWith.
data class FrequentlyOrderedWithItemDto(val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double, val imageUrl: String? = null, val originalPrice: Double? = null, val discountPercent: Int? = null, val stockQuantity: Int? = null)
data class FrequentlyOrderedWithResponse(val success: Boolean, val products: List<FrequentlyOrderedWithItemDto>)

// Real Coupang Eats-style dish grid (2026-08-03) -- see backend EatsController.kt's
// own doc comment for the full 100%-UI/UX-parity sourcing. Same shape as
// DealProductDto above (this app's established "product-in-a-list" DTO shape) minus
// the discount fields, which don't apply here.
// Real Coupang Eats-style budget filter + "recommended for you" ranking (2026-08-16,
// "AI 개인화 메뉴 추천") -- recommended is real, not fabricated: true only when the
// buyer has actually ordered from that dish's restaurant before. See
// EatsController.getDishes' own doc comment on the backend.
data class EatsDishDto(val id: String, val merchantId: String, val merchantName: String, val name: String, val price: Double, val imageUrl: String? = null, val recommended: Boolean = false)
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

// Real Toss Shopping banner carousel (2026-08-12) -- see backend
// TimeDealService.getBanners's own doc comment. Same real shape as TimeDealViewDto
// above -- a banner IS a real active Time Deal, not a separate DTO for fabricated
// promotional content.
data class ShoppingBannersResponse(val success: Boolean, val banners: List<TimeDealViewDto>)

// Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
// see backend ShoppingMissionService's own doc comment. Every mission pays real RWF
// straight into the real account -- itunda has never had a separate "points" currency.
data class ShoppingMissionDto(
    val type: String,
    val label: String,
    val rewardAmount: java.math.BigDecimal,
    val completedToday: Boolean,
    val claimedEver: Boolean = false,
)
data class SpinOutcomeDto(val amount: java.math.BigDecimal, val odds: Double)
data class ShoppingMissionsResponse(val success: Boolean, val missions: List<ShoppingMissionDto>, val spinOutcomes: List<SpinOutcomeDto>)
data class MissionCompleteResponse(val success: Boolean, val type: String, val amountEarned: java.math.BigDecimal, val newAccountBalance: java.math.BigDecimal)

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
// Real Kakao Map-style "구독" (subscribe) -- see MapsService.subscribeToSharedFolder's
// own doc comment on the backend. Ported from bank-mfe (2026-08-18); Android already had
// the view half (SharedFolderSection) but no way to keep what it showed.
data class SubscribeToSharedMapFolderResponse(val success: Boolean, val copiedCount: Int)

// Real Kakao Map-style "친구위치" (Friend Location) live location sharing -- a real,
// moving position shared for a bounded window, distinct from the static bookmark-folder
// share/subscribe above. Ported from bank-mfe/maps-mfe (2026-09-03) -- itunda's own v1 is
// ALWAYS time-bounded (no "unlimited" option); "live" means periodically-refreshed via
// polling, not a push channel (itunda has no WebSocket infra for this feature specifically).
data class LiveLocationShareDto(
    val id: String, val sharerUserId: String, val recipientUserId: String,
    val latitude: Double?, val longitude: Double?, val locationUpdatedAt: String?,
    val expiresAt: String, val revoked: Boolean, val createdAt: String,
)
data class StartLocationShareRequest(val recipientPhoneNumber: String, val durationHours: Int = 1)
data class StartLocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class UpdateLocationShareRequest(val latitude: Double, val longitude: Double)
data class UpdateLocationShareResponse(val success: Boolean, val updatedShareCount: Int)
data class ExtendLocationShareRequest(val additionalHours: Int = 1)
data class ExtendLocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class LocationSharesResponse(val success: Boolean, val shares: List<LiveLocationShareDto>)
data class LocationShareResponse(val success: Boolean, val share: LiveLocationShareDto)
data class StopLocationShareResponse(val success: Boolean)

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
    MapPlaceCategory("ITUNDA_AGENT", "Cash agents"),
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
// required/multiSelect added 2026-08-28 -- the backend has always returned these
// real fields (4 real group combinations, MenuOptionGroup.kt), never declared here.
data class EatsMenuOptionGroupDto(val id: String, val name: String, val choices: List<EatsMenuOptionChoiceDto> = emptyList(), val required: Boolean = true, val multiSelect: Boolean = false)

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
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    val isBestSeller: Boolean = false,
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
    // Real "Best seller" badge (2026-08-28) -- see DealProductDto's own doc comment.
    val isBestSeller: Boolean = false,
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
// Real customer-presented payment code (2026-08-11) -- see backend's
// MerchantService.generateCustomerPaymentCode/chargeByCustomerCode doc comments.
data class CustomerPaymentCodeResponse(val success: Boolean, val code: String, val expiresAt: String, val accountId: String? = null)
data class GenerateCustomerPaymentCodeRequest(val accountId: String? = null)
data class ChargeByCustomerCodeRequest(val code: String, val amount: java.math.BigDecimal)
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

// Real itunda Pay redesign (2026-08-28, direct user reference: real Toss Pay
// Coupon box + Membership screens). Both endpoints these DTOs back had ZERO
// controller endpoint anywhere before this pass -- see MerchantCouponService.kt's
// own doc comments (browseCoupons/getMyRedemptions) and
// MerchantLoyaltyPointsService.kt's (getMyBalances). Mirrors bank-mfe's
// lib/coupons.ts exactly.
data class CouponBrowseViewDto(val coupon: MerchantCouponPreviewDto, val merchantName: String, val eligible: Boolean, val alreadyRedeemed: Boolean)
data class CouponBrowseResponse(val success: Boolean, val coupons: List<CouponBrowseViewDto>)
data class CouponRedemptionDto(
    val id: String, val couponId: String, val merchantId: String, val transactionId: String,
    val discountAmount: java.math.BigDecimal, val redeemedAt: String,
)
data class CouponRedemptionsResponse(val success: Boolean, val redemptions: List<CouponRedemptionDto>)
data class LoyaltyBalanceDto(val merchantId: String, val merchantName: String, val pointBalance: java.math.BigDecimal)
data class LoyaltyBalancesResponse(val success: Boolean, val balances: List<LoyaltyBalanceDto>, val total: java.math.BigDecimal)
data class PaymentIntentPreviewResponse(
    val success: Boolean, val merchantId: String, val businessName: String,
    val amount: java.math.BigDecimal, val description: String?, val coupons: List<MerchantCouponViewDto>,
)

// Real Face Pay -- mirrors bank-mfe's lib/facepay.ts exactly.
data class FacePayEnrollmentDto(val id: String, val userId: String, val active: Boolean, val enrolledAt: String, val revokedAt: String?)
data class FacePayEnrollmentResponse(val success: Boolean, val enrollment: FacePayEnrollmentDto)
data class FacePayStatusResponse(val success: Boolean, val enrolled: Boolean, val enrollment: FacePayEnrollmentDto?)

// Real RewardsService task list -- mirrors bank-mfe's lib/rewards.ts RewardTasksResult.
data class RewardTaskDto(val id: String, val title: String, val subtitle: String, val rewardAmount: Double, val claimed: Boolean, val claimedAt: String?, val eligible: Boolean)
data class RewardTasksResponse(val success: Boolean, val tasks: List<RewardTaskDto>, val rewardsTotal: Double)
data class ClaimRewardTaskRequest(val taskId: String)
data class ClaimRewardTaskResponse(val success: Boolean, val message: String, val rewardAmount: Double, val newBalance: Double)

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
    // helpfulCount added 2026-08-25 -- real Coupang/Naver-style "도움돼요" counter, see
    // ProductReview.kt's own doc comment on the backend.
    val helpfulCount: Long = 0,
    val createdAt: String,
)
data class ToggleHelpfulReviewResponse(val success: Boolean, val helpful: Boolean)
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

// Real recurring-payment ("subscription") detection -- mirrors bank-mfe's lib/account.ts
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

// Real Toss Pay home reference -- mirrors backend's MerchantDiscoveryService.kt
// NearbyMerchant exactly.
data class NearbyMerchantDto(val id: String, val businessName: String, val category: String?, val cashbackRate: Double, val latitude: Double, val longitude: Double, val distanceKm: Double)
data class NearbyMerchantsResponse(val success: Boolean, val merchants: List<NearbyMerchantDto>)

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
    // Real Coupang/Naver-style "도움돼요" (helpful) counter -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check.
    val helpfulCount: Long = 0,
)
data class EatsReviewResponse(val success: Boolean, val review: EatsReviewDto)
data class EatsReviewsResponse(val success: Boolean, val reviews: List<EatsReviewDto>)
data class ReplyToEatsReviewRequest(val reply: String)
data class EatsRatingResponse(val success: Boolean, val average: Double?, val count: Long)
data class ToggleEatsReviewHelpfulResponse(val success: Boolean, val helpful: Boolean)
data class ReportEatsReviewRequest(val reason: String, val details: String? = null)

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
    // Real Baemin-style tiered order-amount promotion (2026-08-16) -- itunda-funded,
    // not restaurant-funded. See EatsPromotionCalculator's own doc comment on the
    // backend. Zero for every order below the lowest real tier.
    val promotionDiscount: Double = 0.0,
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
    // Real fresh Uber Eats research (2026-08-15) -- see EatsController's own
    // withRiderEtaFields doc comment: itunda's order-tracking screen already had a real
    // stepped status UI and live rider-location map, but never resolved riderId to a
    // real name or turned the already-stored distanceKm into a customer-facing arrival
    // estimate, both real gaps against Uber Eats' own sourced tracker redesign. Only
    // returned by GET my-orders (the customer's own order-tracking endpoint), null
    // elsewhere -- never fabricated when no rider is assigned yet.
    val riderName: String? = null,
    val estimatedArrivalMinutes: Int? = null,
    // Real Uber Eats post-delivery tip -- see EatsOrderService.tipRider's own doc
    // comment. Ported from bank-mfe (2026-09-03). Non-null once tipped; used to hide
    // the tip prompt for an order the caller already tipped.
    val tipAmount: Double? = null,
)
data class TipEatsOrderRequest(val amount: Double)
data class TipEatsOrderResponse(val success: Boolean, val order: EatsOrderDto)

// selectedOptionsJson added 2026-07-21 -- unitPrice above already includes every
// selected choice's priceDelta; this is purely a human-readable receipt summary, never
// a second pricing source. See EatsOrderItem.kt's own doc comment.
data class EatsOrderItemDto(
    val id: String, val orderId: String, val productId: String, val productName: String, val unitPrice: Double, val quantity: Int,
    val selectedOptionsJson: String? = null,
)
data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)

// Real 배달의민족 함께주문 (Baemin "Together Order") -- ported from bank-mfe
// (2026-09-03), see lib/eatsGroupOrders.ts's own doc comment. A join-code-shared cart
// in front of the same real checkout/payment path finalizeGroupEatsOrder already
// reuses underneath (GroupEatsOrderService.finalizeOrder calls the exact same backend
// EatsOrderService.placeOrder).
data class GroupEatsOrderDto(
    val id: String, val hostUserId: String, val restaurantId: String, val joinCode: String,
    val deliveryAddress: String, val deliveryLatitude: Double?, val deliveryLongitude: Double?,
    val fulfillmentType: String, val status: String, val resultingOrderId: String?,
    val createdAt: String, val finalizedAt: String?,
)
data class GroupEatsOrderItemView(val productId: String, val productName: String, val quantity: Int, val unitPrice: Double, val lineTotal: Double)
data class GroupEatsOrderParticipantView(val userId: String, val joinedAt: String, val subtotal: Double, val items: List<GroupEatsOrderItemView>)
data class CreateGroupEatsOrderRequest(val restaurantId: String, val deliveryAddress: String = "", val deliveryLatitude: Double? = null, val deliveryLongitude: Double? = null, val fulfillmentType: String = "DELIVERY")
data class JoinGroupEatsOrderRequest(val joinCode: String)
data class GroupEatsOrderItemRequest(val menuItemId: String, val quantity: Int, val selectedChoiceIds: List<String>? = null)
data class SetGroupEatsOrderItemsRequest(val items: List<GroupEatsOrderItemRequest>)
data class GroupEatsOrderResponse(val success: Boolean, val groupOrder: GroupEatsOrderDto)
data class GroupEatsOrderDetailResponse(val success: Boolean, val groupOrder: GroupEatsOrderDto, val grandTotal: Double, val participants: List<GroupEatsOrderParticipantView>)
data class FinalizeGroupEatsOrderResponse(val success: Boolean, val order: EatsOrderDto, val items: List<EatsOrderItemDto>)

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

data class RiderDto(val id: String, val userId: String, val accountId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

// Real bookmarked/favorited restaurants (2026-07-19) -- add/remove are both idempotent
// on the backend, see EatsFavoriteService.kt's own doc comment.
data class FavoriteRestaurantDto(val restaurantId: String, val businessName: String, val category: String?, val favoritedAt: String)
data class FavoriteRestaurantsResponse(val success: Boolean, val favorites: List<FavoriteRestaurantDto>)
data class AddFavoriteResponse(val success: Boolean)

// Real Karrot "이 글 숨기기" (hide this post) -- the client only needs to know the call
// succeeded (same as AddFavoriteResponse), the real ListingHide object's own fields
// aren't rendered anywhere, matching bank-mfe's own hideListing() usage.
data class HideListingResponse(val success: Boolean)

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
    val createdAt: String,
)
data class CardTransactionsResponse(val success: Boolean, val transactions: List<CardTransactionDto>, val totalElements: Long, val totalPages: Int)
data class SetCardLimitsRequest(val dailyLimit: java.math.BigDecimal, val monthlyLimit: java.math.BigDecimal)
data class ChargeCardRequest(val amount: java.math.BigDecimal, val merchantName: String)
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
// Real, minimal product-analytics event (2026-08-10) -- see the backend's own
// AnalyticsEvent.kt doc comment and the "itunda: the wedge, not the mirror" strategy
// memo, recommendation (ii). eventName must be one of AnalyticsController.KNOWN_EVENTS
// on the backend (currently "home_view"/"coop_rail_tap") -- a mismatched name real-400s
// rather than silently recording garbage.
data class RecordAnalyticsEventRequest(val eventName: String, val platform: String = "android", val metadata: String? = null)

interface ApiService {
    @POST("api/v1/analytics/events")
    suspend fun recordAnalyticsEvent(@Body request: RecordAnalyticsEventRequest): SuccessResponse

    @GET("api/v1/account")
    suspend fun getAccounts(): AccountResponse

    // Real Kakao Pay 소비 리포트-style spending categorization (rw.itunda.account.
    // AccountService.getSpendingInsight, real since 2026-07-13) -- first Android client
    // for this feature (item 107, found backend-only via a fresh matrix scan; bank-mfe
    // ported the same day as item 106). Ledger-based, not the transactions table -- see
    // the backend's own doc comment for the full account.
    @GET("api/v1/account/spending")
    suspend fun getSpendingInsight(): SpendingInsightResponse

    // Real business expense summary (2026-08-11) -- see backend's
    // AccountService.getBusinessExpenseSummary doc comment for the real Toss Bank
    // 세금 신고용 이용내역 자동발송 (tax-filing usage summary) pattern this closes the
    // honest slice of: a categorized, period-scoped summary of the BUSINESS account's
    // own real ledger history, same categorization as getSpendingInsight above.
    @GET("api/v1/account/business-expense-summary")
    suspend fun getBusinessExpenseSummary(@Query("sinceMonthsAgo") sinceMonthsAgo: Long = 3): BusinessExpenseSummaryResponse

    // Real Toss budgets/limits equivalent (item 165/172) -- AccountService.setBudget/
    // getBudgets, exposed on the pre-existing AccountController (no dedicated
    // controller). Per-category or overall (category == null) monthly limit, with
    // a real 80%/100%-threshold in-app Notification + push (maybeNotifyBudgetThreshold).
    @GET("api/v1/account/budgets")
    suspend fun getBudgets(): GetBudgetsResponse

    @POST("api/v1/account/budgets")
    suspend fun setBudget(@Body request: SetBudgetRequest): SetBudgetResponse

    // Real Naver Pay Money 자동충전 (auto-charge) equivalent (item 168/176) -- see
    // AutoTopUpService's own doc comment. getSetting real-404s (AUTO_TOPUP_SETTING_NOT_
    // FOUND) if this account has no setting configured yet -- normal, not caught here.
    @GET("api/v1/account/{accountId}/auto-topup")
    suspend fun getAutoTopUpSetting(@Path("accountId") accountId: String): GetAutoTopUpSettingResponse

    @PUT("api/v1/account/{accountId}/auto-topup")
    suspend fun configureAutoTopUp(@Path("accountId") accountId: String, @Body request: ConfigureAutoTopUpRequest): GetAutoTopUpSettingResponse

    @POST("api/v1/account/{accountId}/auto-topup/trigger")
    suspend fun triggerAutoTopUp(@Path("accountId") accountId: String, @Header("Idempotency-Key") idempotencyKey: String): TriggerAutoTopUpResponse

    // Real 토스뱅크 외화통장 (foreign-currency account) equivalent (2026-07-25) -- see
    // rw.itunda.account.web.ForeignCurrencyController.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful open would previously resubmit here
    // and hit the backend's own ForeignCurrencyAccountAlreadyExistsException guard
    // on retry.
    @POST("api/v1/account/foreign-currency/accounts")
    suspend fun openForeignAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: OpenForeignAccountRequest): ForeignAccountResponse

    @GET("api/v1/account/foreign-currency/accounts")
    suspend fun getForeignAccounts(): ForeignAccountsResponse

    @GET("api/v1/account/foreign-currency/rate")
    suspend fun getExchangeRate(@Query("from") from: String, @Query("to") to: String): ExchangeRateResponse

    // Idempotency-Key added 2026-09-05 -- convert genuinely moves real money
    // between the caller's own accounts with NO duplicate-prevention guard at all,
    // so a lost response after a successful conversion would previously resubmit
    // here and silently execute the SAME conversion twice.
    @POST("api/v1/account/foreign-currency/convert")
    suspend fun convertCurrency(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConvertCurrencyRequest): CurrencyConversionResponse

    @GET("api/v1/account/foreign-currency/conversions")
    suspend fun getMyConversions(): CurrencyConversionsResponse

    // SetRateAlertRequest's own doc comment.
    @POST("api/v1/account/foreign-currency/rate-alert")
    suspend fun setRateAlert(@Body request: SetRateAlertRequest): SetRateAlertResponse

    @DELETE("api/v1/account/foreign-currency/rate-alert")
    suspend fun clearRateAlert(@Query("fromCurrency") fromCurrency: String, @Query("toCurrency") toCurrency: String): SimpleSuccessResponse

    @GET("api/v1/account/foreign-currency/rate-alerts")
    suspend fun getMyRateAlerts(): RateAlertsResponse

    @GET("api/v1/discover")
    suspend fun getDiscoverItems(): DiscoverResponse

    // Real "verify with itunda" partner consent flow. IdentityVerificationService's own
    // doc comment states the itunda app "shows the user a real consent screen naming the
    // partner and exactly what will be shared" -- but no client on any platform had ever
    // called these, so a partner could create a request the user could never answer and
    // the flow could only ever expire (found 2026-08-14). getIdentityVerificationRequest
    // is deliberately unauthenticated on the backend (it names only the partner and the
    // field labels, never the user's own data); approve/decline both require the real
    // signed-in user, which is what makes the consent meaningful.
    @GET("api/v1/identity/verification/{requestId}")
    suspend fun getIdentityVerificationRequest(@Path("requestId") requestId: String): IdentityVerificationRequestResponse

    @POST("api/v1/identity/verification/{requestId}/approve")
    suspend fun approveIdentityVerification(@Path("requestId") requestId: String): SimpleSuccessResponse

    @POST("api/v1/identity/verification/{requestId}/decline")
    suspend fun declineIdentityVerification(@Path("requestId") requestId: String): SimpleSuccessResponse

    @GET("api/v1/savings/goals")
    suspend fun getSavingsGoals(): SavingsGoalsResponse

    // Real savings-goal creation. The backend endpoint and bank-mfe's own createGoal
    // have both existed for a long time, but Android had only the GET above -- so the
    // "Save & grow" goal list could never be anything but empty on this platform
    // (found 2026-08-14). Mirrors backend SavingsController.CreateGoalRequest exactly.
    @POST("api/v1/savings/goals")
    suspend fun createSavingsGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateSavingsGoalRequest): CreateSavingsGoalResponse

    @GET("api/v1/savings/goals/{id}/transactions")
    suspend fun getSavingsGoalTransactions(@Path("id") id: String): BucketTransactionsResponse

    @GET("api/v1/savings/interest-jar")
    suspend fun getInterestJar(): InterestJarResponse

    @GET("api/v1/savings/interest-jar/transactions")
    suspend fun getInterestJarTransactions(): BucketTransactionsResponse

    // Real Deposit Protection Fund status (2026-08-11) -- see backend's
    // DepositProtectionFund.kt doc comment for the full honesty framing.
    @GET("api/v1/savings/deposit-protection")
    suspend fun getDepositProtectionStatus(): DepositProtectionStatusResponse

    @POST("api/v1/account/transfer/quote")
    suspend fun quoteTransfer(@Body request: QuoteTransferRequest): QuoteTransferResponse

    @POST("api/v1/account/transfer/confirm")
    suspend fun confirmTransfer(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ConfirmTransferRequest): ConfirmTransferResponse

    // Real direct P2P push-transfer (2026-07-20) -- see SendDirectP2pRequest's own doc
    // comment for why this replaces quoteTransfer/confirmTransfer above in
    // MainViewModel.sendTransfer.
    @POST("api/v1/p2p/send")
    suspend fun sendDirect(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendDirectP2pRequest): SendDirectP2pResponse

    // Real Naver Pay "가족 공유 자산 관리" -- see SendToFamilyMemberRequest's own doc
    // comment.
    @POST("api/v1/p2p/send-to-family")
    suspend fun sendToFamilyMember(@Header("Idempotency-Key") idempotencyKey: String, @Body request: SendToFamilyMemberRequest): SendToFamilyMemberResponse

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

    @GET("api/v1/p2p/recipient")
    suspend fun resolveRecipient(@Query("identifier") identifier: String): ResolveRecipientResponse

    @GET("api/v1/p2p/transfer-limit")
    suspend fun getTransferLimit(): TransferLimitResponse

    @POST("api/v1/p2p/scam-reports")
    suspend fun reportScam(@Body request: ReportScamRequest): ScamReportResponse

    @GET("api/v1/p2p/scam-reports/mine")
    suspend fun getMyScamReports(): ScamReportsListResponse

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/withdraw")
    suspend fun withdrawFromGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: WithdrawRequest): WithdrawResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // No Idempotency-Key -- a settings write, not money movement itself. See
    // RoundUpController.kt's own doc comment.
    @GET("api/v1/savings/round-up")
    suspend fun getRoundUpSettings(): RoundUpSettingsResponse

    @POST("api/v1/savings/round-up")
    suspend fun setRoundUpSettings(@Body request: SetRoundUpSettingsRequest): RoundUpSettingsResponse

    @POST("api/v1/account/agent-withdrawal-authorizations")
    suspend fun createAgentWithdrawalAuthorization(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: CreateAgentWithdrawalAuthorizationRequest,
    ): AgentWithdrawalAuthorizationResponse

    @GET("api/v1/account/agent-withdrawal-authorizations")
    suspend fun getAgentWithdrawalAuthorizations(): AgentWithdrawalAuthorizationsResponse

    @POST("api/v1/account/agent-withdrawal-authorizations/cancel")
    suspend fun cancelAgentWithdrawalAuthorization(@Body request: CancelAgentWithdrawalAuthorizationRequest): AgentWithdrawalAuthorizationResponse

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen; see services/backend/account's new AccountController endpoint.
    @GET("api/v1/account/transactions")
    suspend fun getTransactionHistory(): TransactionHistoryResponse

    // Real "Toss Pay Money" detail/statement screen (user screenshots, 2026-08-21) --
    // scoped to one account's own transactions, not getTransactionHistory's mix of
    // every account. See AccountService.getAccountTransactionHistory on the backend.
    @GET("api/v1/account/{id}/transactions")
    suspend fun getAccountTransactionHistory(@retrofit2.http.Path("id") id: String): TransactionHistoryResponse

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

    // Real KakaoTalk-style "오늘의 생일" (Today's Birthday) -- ported from bank-mfe
    // (2026-09-03). Reuses the same TalkContactDto shape as getTalkContacts.
    @GET("api/v1/messages/contacts/birthdays-today")
    suspend fun getTodaysBirthdays(): TalkContactsResponse

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

    // Real KakaoTalk 채팅방 상단 고정 (pin chat room to top) (2026-08-18) -- see
    // ConversationSummaryDto.pinnedToTop's own doc comment. Found fully built on the
    // backend (MessagingController POST/GET .../pin-to-top) with zero Android caller,
    // same defined-but-uncalled shape this session already found and closed for
    // bank-mfe (Section 165) -- Android/iOS were explicitly left for follow-up then.
    @GET("api/v1/messages/conversations/{id}/pin-to-top")
    suspend fun getConversationPinnedToTop(@Path("id") conversationId: String): ConversationPinnedToTopResponse

    @POST("api/v1/messages/conversations/{id}/pin-to-top")
    suspend fun setConversationPinnedToTop(@Path("id") conversationId: String, @Body request: SetConversationPinnedToTopRequest): ConversationPinnedToTopResponse

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

    // Real KakaoTalk 오픈채팅-style open group (Talk-parity port, item 244) -- see
    // GroupMessagingService.createOpenGroup's own doc comment. bank-mfe/iOS already
    // have this; this is the Android port. Anyone with the real joinCode can join
    // without being invited by phone number first -- distinct from createGroup
    // above, which requires knowing everyone's real number up front.
    @POST("api/v1/messages/groups/open")
    suspend fun createOpenGroup(@Body request: CreateOpenGroupRequest): OpenGroupResponse

    @POST("api/v1/messages/groups/join")
    suspend fun joinGroupByCode(@Body request: JoinGroupByCodeRequest): JoinGroupResponse

    @GET("api/v1/messages/groups/{id}/messages")
    suspend fun getGroupMessages(@Path("id") groupId: String): GroupMessagesResponse

    // Real group-chat message search (Talk product-completeness pass, 2026-09-06) --
    // see searchMessages's own doc comment for the 1:1 equivalent this mirrors.
    @GET("api/v1/messages/groups/{id}/messages/search")
    suspend fun searchGroupMessages(@Path("id") groupId: String, @Query("query") query: String): GroupMessagesResponse

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

    // Real single-listing fetch (item 270) -- a real, defined-but-uncalled backend
    // endpoint found via this session's own "check before assuming a gap needs new
    // backend work" discipline: MarketplaceController.getListing has existed since
    // 2026-07-18 with zero callers on any client (Android/iOS/web all only ever
    // called this listing's own SUB-resources -- mark-sold/boost/review -- never the
    // plain listing itself). Needed for a real "recently viewed listings" rail entry
    // to reopen a listing that's since scrolled out of the currently-loaded feed.
    @GET("api/v1/marketplace/listings/{id}")
    suspend fun getListing(@Path("id") listingId: String): ListingResponse

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

    // Real relevance-ranked search (2026-08-14) -- see backend MarketplaceService
    // .search's own doc comment. Not neighborhood-scoped, unlike the browse above.
    @GET("api/v1/marketplace/listings/search")
    suspend fun searchListings(@Query("q") query: String): ListingsResponse

    @POST("api/v1/marketplace/listings/{id}/mark-sold")
    suspend fun markListingSold(@Path("id") listingId: String, @Body request: MarkSoldRequest = MarkSoldRequest()): ListingResponse

    // Real 당근마켓 끌어올리기 (bump to top of feed), 2026-08-10 -- see
    // rw.itunda.marketplace.web.MarketplaceController.bumpListing. Free, no real money
    // moves, so no Idempotency-Key, unlike boostListing below.
    @POST("api/v1/marketplace/listings/{id}/bump")
    suspend fun bumpListing(@Path("id") listingId: String): ListingResponse

    // Real 가격 수정 (price edit) + Karrot 가격 하락 알림 -- see backend
    // MarketplaceService.updatePrice's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check. Not money-moving itself, so no Idempotency-Key, matching bumpListing above.
    @PATCH("api/v1/marketplace/listings/{id}/price")
    suspend fun updateListingPrice(@Path("id") listingId: String, @Body request: UpdateListingPriceRequest): ListingResponse

    // Real seller-paid sponsored placement (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.boostListing.
    @GET("api/v1/marketplace/boost-tiers")
    suspend fun getBoostTiers(): BoostTiersResponse

    @POST("api/v1/marketplace/listings/{id}/boost")
    suspend fun boostListing(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: BoostListingRequest): ListingResponse

    // Real "pay via itunda" Marketplace escrow (2026-07-25) -- see
    // rw.itunda.marketplace.web.MarketplaceController.
    @POST("api/v1/marketplace/listings/{id}/pay-escrow")
    suspend fun payEscrow(@Path("id") listingId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: PayEscrowRequest = PayEscrowRequest()): MarketplaceEscrowResponse

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

    // Real Karrot "이 글 숨기기" (hide this post) -- see backend ListingHideService's own
    // doc comment. Real, shipped on the backend + bank-mfe (2026-08-24) with zero
    // Android/iOS client until now -- found via a cross-platform-parity check. Mirrors
    // addListingFavorite/removeListingFavorite exactly (POST to hide, DELETE to unhide).
    @POST("api/v1/marketplace/listings/{id}/hide")
    suspend fun hideListing(@Path("id") listingId: String): HideListingResponse

    @DELETE("api/v1/marketplace/listings/{id}/hide")
    suspend fun unhideListing(@Path("id") listingId: String): AddFavoriteResponse

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

    // Real standalone send-by-phone-number (found via an uncalled-endpoint sweep
    // 2026-08-16, backend/bank-mfe/iOS docs Section 88) -- distinct from the chat-
    // embedded call above, this is the general "gift anyone with an itunda account"
    // entry point.
    @POST("api/v1/gifts")
    suspend fun sendGift(
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: SendGiftRequest,
    ): GiftResponse

    @GET("api/v1/gifts/{id}")
    suspend fun getGift(@Path("id") giftId: String): GiftResponse

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
    suspend fun purchaseEmoticonPack(@Path("packId") packId: String, @Header("Idempotency-Key") idempotencyKey: String): OwnedEmoticonPackResponse

    @POST("api/v1/emoticons/packs/{packId}/gift")
    suspend fun giftEmoticonPack(@Path("packId") packId: String, @Body request: GiftEmoticonPackRequest, @Header("Idempotency-Key") idempotencyKey: String): GiftedEmoticonPackResponse

    @POST("api/v1/emoticons/conversations/{id}/send")
    suspend fun sendEmoticon(@Path("id") conversationId: String, @Body request: SendEmoticonRequest): MessageResponse

    @POST("api/v1/emoticons/groups/{id}/send")
    suspend fun sendGroupEmoticon(@Path("id") groupId: String, @Body request: SendEmoticonRequest): GroupMessageResponse

    // Real 동네생활-style community board (2026-07-19) -- see rw.itunda.community.web.CommunityController.
    @GET("api/v1/community/categories")
    suspend fun getCommunityCategories(): CommunityCategoriesResponse

    // Real 동네생활 topic-chip filter row (2026-08-28) -- see backend
    // CommunityService.TOPICS' own doc comment.
    @GET("api/v1/community/topics")
    suspend fun getCommunityTopics(): CommunityTopicsResponse

    @POST("api/v1/community/posts")
    suspend fun createCommunityPost(@Body request: CreateCommunityPostRequest): CommunityPostResponse

    // Real 당근모임-style "upcoming meetups" browse (2026-07-25) -- see
    // rw.itunda.community.web.CommunityController.upcomingMeetups.
    @GET("api/v1/community/meetups/upcoming")
    suspend fun getUpcomingMeetups(): CommunityPostsResponse

    @GET("api/v1/community/posts")
    suspend fun browseCommunityPosts(@Query("category") category: String? = null, @Query("topic") topic: String? = null): CommunityPostsResponse

    @GET("api/v1/community/posts/nearby")
    suspend fun getNearbyCommunityPosts(
        @Query("latitude") latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm") radiusKm: Double = 3.0,
    ): CommunityPostsResponse

    @GET("api/v1/community/my-posts")
    suspend fun getMyCommunityPosts(): CommunityPostsResponse

    // Real Karrot 동네생활 "새 댓글 알림 끄기" (turn off new-comment notifications) -- ported
    // from bank-mfe (2026-09-03). Scoped to MY posts only (the preference only affects
    // notifications about comments on posts the caller authored).
    @GET("api/v1/community/notification-preference")
    suspend fun getCommentNotificationsEnabled(): CommentNotificationsEnabledResponse

    @POST("api/v1/community/notification-preference")
    suspend fun setCommentNotificationsEnabled(@Body request: SetCommentNotificationsEnabledRequest): CommentNotificationsEnabledResponse

    // Real hyperlocal "my neighborhood" browse (2026-07-20) -- see AuthApi.setNeighborhood.
    @GET("api/v1/community/posts/my-neighborhood")
    suspend fun getCommunityPostsMyNeighborhood(@Query("category") category: String? = null): CommunityPostsResponse

    // Real relevance-ranked search (2026-08-14) -- see backend CommunityService
    // .search's own doc comment.
    @GET("api/v1/community/posts/search")
    suspend fun searchCommunityPosts(@Query("q") query: String): CommunityPostsResponse

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

    // Real relevance-ranked search (2026-08-14) -- see backend JobPostService
    // .search's own doc comment.
    @GET("api/v1/jobs/posts/search")
    suspend fun searchJobPosts(@Query("q") query: String): JobPostsResponse

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

    // Real relevance-ranked search (2026-08-14) -- see backend PropertyListingService
    // .search's own doc comment.
    @GET("api/v1/realestate/listings/search")
    suspend fun searchPropertyListings(@Query("q") query: String): PropertyListingsResponse

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

    // Real Karrot(당근마켓)-style price-drop notification -- see backend
    // PropertyListingService.updatePrice's own doc comment. Real, shipped on the
    // backend + bank-mfe with zero Android client until now -- found via a
    // cross-platform-parity check, the real-estate mirror of the same fix already
    // ported to Marketplace listings.
    @POST("api/v1/realestate/listings/{id}/price")
    suspend fun updatePropertyListingPrice(@Path("id") propertyListingId: String, @Body request: UpdateListingPriceRequest): PropertyListingResponse

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
        // Real fix (2026-08-13, direct user report against a live screenshot): Eats'
        // own restaurant list showed Electronics/Fashion merchants alongside real
        // restaurants -- both browse the exact same unfiltered Merchant directory. Pass
        // "RESTAURANT" from EatsScreen.kt, leave null (unfiltered, unchanged) from Shop.
        @Query("businessType") businessType: String? = null,
        @Query("q") q: String? = null,
        // Real browse-card enrichment (2026-07-21) -- see ShoppingMerchantDto's own doc
        // comment. Omitted (null) means no real distanceKm/deliveryTimeMinutes back.
        @Query("buyerLat") buyerLat: Double? = null,
        @Query("buyerLng") buyerLng: Double? = null,
        // Real Baemin/Coupang Eats-style "fastest delivery" sort tab (2026-08-16) --
        // only takes effect server-side when buyerLat/buyerLng are also supplied.
        @Query("sortBy") sortBy: String? = null,
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

    // Real Kigali GTFS transit journeys (2026-08-28) -- see TransitJourneyDto's own doc comment.
    @GET("api/v1/maps/directions/transit")
    suspend fun getTransitDirections(
        @Query("fromLat") fromLat: Double,
        @Query("fromLng") fromLng: Double,
        @Query("toLat") toLat: Double,
        @Query("toLng") toLng: Double,
    ): MapsTransitDirectionsResponse

    // Real consolidated place-detail (2026-08-28) -- see MapPlaceDetailDto's own doc comment.
    @GET("api/v1/maps/places/{merchantId}")
    suspend fun getMapPlaceDetail(@Path("merchantId") merchantId: String): MapPlaceDetailResponse

    // Real gap found live (uncalled-endpoint sweep, 2026-08-29) -- see
    // ToggleMerchantUpdateLikeResponse's own doc comment.
    @POST("api/v1/merchant/updates/{updateId}/like")
    suspend fun toggleMerchantUpdateLike(@Path("updateId") updateId: String): ToggleMerchantUpdateLikeResponse

    // Real, free, keyless Kigali weather (2026-08-28) -- see KigaliWeatherDto's own doc comment.
    @GET("api/v1/maps/weather")
    suspend fun getKigaliWeather(): MapsWeatherResponse

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

    // Real Kakao Map-style "구독" (subscribe) -- the write half of sharing a folder, not
    // just viewing it. Unlike the GET above, this is authenticated (writes real rows into
    // the caller's own bookmarks).
    @POST("api/v1/maps/shared/{userId}/{folderName}/subscribe")
    suspend fun subscribeToSharedMapFolder(@Path("userId") userId: String, @Path("folderName") folderName: String): SubscribeToSharedMapFolderResponse

    // Real Kakao Map-style "친구위치" live location sharing -- see LiveLocationShareDto's
    // own doc comment.
    @POST("api/v1/maps/location-share")
    suspend fun startLocationShare(@Body request: StartLocationShareRequest): StartLocationShareResponse

    @POST("api/v1/maps/location-share/_/update-location")
    suspend fun updateMyLocationShare(@Body request: UpdateLocationShareRequest): UpdateLocationShareResponse

    @POST("api/v1/maps/location-share/{id}/extend")
    suspend fun extendLocationShare(@Path("id") id: String, @Body request: ExtendLocationShareRequest): ExtendLocationShareResponse

    @POST("api/v1/maps/location-share/{id}/stop")
    suspend fun stopLocationShare(@Path("id") id: String): StopLocationShareResponse

    @GET("api/v1/maps/location-share/mine")
    suspend fun getMyLocationShares(): LocationSharesResponse

    @GET("api/v1/maps/location-share/shared-with-me")
    suspend fun getLocationSharesWithMe(): LocationSharesResponse

    // Real recipient-side poll -- call this on a real interval (e.g. every 15s) while
    // watching a share to see the sharer's latest pushed position.
    @GET("api/v1/maps/location-share/{id}")
    suspend fun getLocationShare(@Path("id") id: String): LocationShareResponse

    // Real distinct category list -- see MerchantRepository.findDistinctCategories's own
    // doc comment on the backend.
    @GET("api/v1/shopping/merchants/categories")
    suspend fun getMerchantCategories(@Query("businessType") businessType: String? = null): MerchantCategoriesResponse

    // Real "Deals" rail (2026-07-25) -- see backend MerchantProductRepository.findDeals's
    // own doc comment.
    @GET("api/v1/shopping/products/deals")
    suspend fun getShopDeals(): DealsResponse

    // Real Coupang Eats-style dish grid (2026-08-03) -- see EatsDishDto's own doc
    // comment for the sourcing.
    @GET("api/v1/eats/dishes")
    // sortBy="popular" added 2026-08-28 -- real order-count-derived ranking,
    // distinct from the default real "recommended for you" personal-history sort.
    suspend fun getEatsDishes(@Query("category") category: String? = null, @Query("maxBudget") maxBudget: Double? = null, @Query("sortBy") sortBy: String? = null): EatsDishesResponse

    // Real "frequently ordered together" cross-sell (2026-08-28) -- see backend
    // OrderItemRepository.getFrequentlyOrderedWith. A dedicated lean DTO (not
    // DealProductDto): Gson deserializes a genuinely-absent field to null, not a
    // Kotlin default, so reusing DealProductDto here would risk a silent mismatch.
    @GET("api/v1/shopping/products/{id}/frequently-ordered-with")
    suspend fun getFrequentlyOrderedWith(@Path("id") productId: String): FrequentlyOrderedWithResponse

    // Real Coupang 타임특가 (Time Deal, item 226) -- see TimeDealDto's own doc comment.
    @GET("api/v1/time-deals")
    suspend fun getActiveTimeDeals(): TimeDealsResponse

    // Real Toss Shopping banner carousel (2026-08-12) -- see backend
    // TimeDealService.getBanners's own doc comment: every banner IS a real, currently
    // active Time Deal, never fabricated promotional content.
    @GET("api/v1/time-deals/banners")
    suspend fun getShoppingBanners(): ShoppingBannersResponse

    // Real Toss Shopping "포인트 및 쿠폰받기" (get points and coupons) mission row --
    // see backend ShoppingMissionService's own doc comment.
    @GET("api/v1/shopping/points")
    suspend fun getShoppingMissions(): ShoppingMissionsResponse

    @POST("api/v1/shopping/points/missions/{type}/complete")
    suspend fun completeShoppingMission(@Path("type") type: String, @Header("Idempotency-Key") idempotencyKey: String): MissionCompleteResponse

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

    // Real Coupang 정기배송 "건너뛰기" (skip next) -- ported from bank-mfe (2026-09-03).
    @POST("api/v1/product-subscriptions/{id}/skip-next")
    suspend fun skipNextProductSubscriptionDelivery(@Path("id") id: String): ProductSubscriptionResponse

    @GET("api/v1/shopping/merchants/{id}/products")
    suspend fun getMerchantProducts(@Path("id") merchantId: String): MerchantProductsResponse

    // Real seller chat (2026-08-28) -- mirrors contactSeller (Marketplace) above
    // exactly: resolves the real merchant owner's userId server-side, hands off to
    // the same real shared messaging system every other vertical already uses.
    @POST("api/v1/shopping/merchants/{id}/contact-seller")
    suspend fun contactMerchantSeller(@Path("id") merchantId: String): ContactSellerResponse

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
    suspend fun extendGiftVoucherExpiry(@Path("id") voucherId: String, @Header("Idempotency-Key") idempotencyKey: String): GiftVoucherResponse

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

    // Real "Coupon box" cross-merchant browse (itunda Pay redesign, 2026-08-28) --
    // itunda's first ever unscoped coupon read, see MerchantCouponService.
    // browseCoupons's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    @GET("api/v1/merchant/coupons/browse")
    suspend fun browseCoupons(): CouponBrowseResponse

    @GET("api/v1/merchant/coupons/my-redemptions")
    suspend fun getMyCouponRedemptions(): CouponRedemptionsResponse

    // Real Membership-screen "Store points" row -- see MerchantLoyaltyPointsService.
    // getMyBalances's own doc comment. Mirrors bank-mfe's lib/coupons.ts exactly.
    @GET("api/v1/merchant/loyalty/my-balances")
    suspend fun getMyLoyaltyBalances(): LoyaltyBalancesResponse

    // Real "pay a merchant" -- the manual-code-entry alternative to camera QR scanning
    // (this app has no scanner), mirrors bank-mfe's lib/shopping.ts collectPayment/
    // payByStaticQr exactly. bank-mfe already has both; this is the first Android client
    // for either.
    @POST("api/v1/merchant/collect/{intentId}")
    suspend fun collectPayment(@Path("intentId") intentId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: CollectPaymentRequest): CollectPaymentResultDto

    @POST("api/v1/merchant/{merchantId}/static-qr/pay")
    suspend fun payByStaticQr(@Path("merchantId") merchantId: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: StaticQrPayRequest): CollectPaymentResultDto

    // Real customer-presented payment code (2026-08-11) -- see backend's
    // MerchantService.generateCustomerPaymentCode/chargeByCustomerCode doc comments.
    // Matches real KakaoPay/Toss Pay's actual primary in-store flow: the CUSTOMER
    // opens Pay and a scannable code is already on screen, no typing on either side --
    // the reverse direction of collectPayment/payByStaticQr above.
    @POST("api/v1/merchant/pay/customer-code")
    suspend fun generateCustomerPaymentCode(@Body request: GenerateCustomerPaymentCodeRequest = GenerateCustomerPaymentCodeRequest()): CustomerPaymentCodeResponse

    @POST("api/v1/merchant/pay/charge-by-code")
    suspend fun chargeByCustomerCode(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ChargeByCustomerCodeRequest): CollectPaymentResultDto

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

    // Real RewardsService task list -- see rw.itunda.rewards.web.RewardsController's own
    // doc comment: the Saronite reward-tasks mini-app's native bridge has called
    // /api/v1/rewards/tasks since it was built, but this Compose-native surface (PayTab's
    // real Toss Pay home "Get more rewards" preview, 2026-08-22) never had a direct
    // client. Mirrors bank-mfe's lib/rewards.ts RewardTasksResult exactly.
    @GET("api/v1/rewards/tasks")
    suspend fun getRewardTasks(): RewardTasksResponse

    @POST("api/v1/rewards/claim")
    suspend fun claimRewardTask(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ClaimRewardTaskRequest): ClaimRewardTaskResponse

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
    suspend fun decideOrderReturn(@Path("returnRequestId") returnRequestId: String, @Body request: DecideOrderReturnRequest, @Header("Idempotency-Key") idempotencyKey: String): OrderReturnRequestResponse

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
    suspend fun requestOrderReturn(@Path("orderId") orderId: String, @Body request: RequestOrderReturnRequest, @Header("Idempotency-Key") idempotencyKey: String): OrderReturnRequestResponse

    @GET("api/v1/orders/returns/my-requests")
    suspend fun getMyReturnRequests(): OrderReturnRequestsResponse

    // Real post-delivery product reviews (2026-07-20) -- see OrderController.submitProductReview.
    @POST("api/v1/orders/items/{id}/review")
    suspend fun submitProductReview(@Path("id") orderItemId: String, @Body request: SubmitProductReviewRequest): ProductReviewResponse

    @GET("api/v1/orders/products/{id}/rating")
    suspend fun getProductRating(@Path("id") productId: String): ProductRatingResponse

    @GET("api/v1/orders/products/{id}/reviews")
    suspend fun getProductReviews(@Path("id") productId: String): ProductReviewsResponse

    // Real Coupang/Naver-style "helpful" idempotent toggle (2026-08-25) -- see
    // ProductReviewService.toggleHelpful's own doc comment on the backend.
    @POST("api/v1/orders/reviews/{id}/helpful")
    suspend fun toggleProductReviewHelpful(@Path("id") reviewId: String): ToggleHelpfulReviewResponse

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
    // transaction history -- see rw.itunda.account.SubscriptionDetectionService's own
    // doc comment. bank-mfe already has this; this is the first Android client.
    @GET("api/v1/account/subscriptions")
    suspend fun getDetectedSubscriptions(): DetectedSubscriptionsResponse

    // Real 당근(Karrot) 반경 타기팅-style radius-targeted local ads -- the
    // customer-facing browse half (merchant-mfe owns the paid create/extend side).
    // "Pull" discovery, same as every other nearby() in this codebase: the caller's
    // live coordinate is a request param, not a stored location itunda doesn't keep.
    // bank-mfe already has this; this is the first Android client.
    @GET("api/v1/merchant/ads/nearby")
    suspend fun getNearbyMerchantAds(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double): NearbyMerchantAdsResponse

    // Real Toss Pay home reference (4 screenshots, 2026-08-22, direct user follow-up:
    // "it should look 100% like toss pay UI/UX features everything") -- the reference's
    // own "345 stores nearby where you can earn rewards" banner. Distinct from
    // getNearbyMerchantAds above -- that's paid ad placements (a subset), this is every
    // real ACTIVE merchant nearby (MerchantDiscoveryService.kt), which is what "how many
    // stores can I actually pay near me" honestly means. Every merchant earns the payer
    // real cashback on collect() (ShoppingCashbackService), so "earn cashback" is a true
    // claim for all of them, not just FacePay-enrolled ones.
    @GET("api/v1/merchant/nearby")
    suspend fun getNearbyMerchants(@Query("latitude") latitude: Double, @Query("longitude") longitude: Double, @Query("radiusKm") radiusKm: Double = 5.0): NearbyMerchantsResponse

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

    // Real 배달의민족 함께주문 (Baemin "Together Order") -- see GroupEatsOrderDto's own
    // doc comment.
    @POST("api/v1/eats/group-orders")
    suspend fun createGroupEatsOrder(@Body request: CreateGroupEatsOrderRequest): GroupEatsOrderResponse

    @POST("api/v1/eats/group-orders/join")
    suspend fun joinGroupEatsOrder(@Body request: JoinGroupEatsOrderRequest): GroupEatsOrderResponse

    @GET("api/v1/eats/group-orders/{id}")
    suspend fun getGroupEatsOrder(@Path("id") id: String): GroupEatsOrderDetailResponse

    @POST("api/v1/eats/group-orders/{id}/items")
    suspend fun setGroupEatsOrderItems(@Path("id") id: String, @Body request: SetGroupEatsOrderItemsRequest): GroupEatsOrderDetailResponse

    @POST("api/v1/eats/group-orders/{id}/finalize")
    suspend fun finalizeGroupEatsOrder(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): FinalizeGroupEatsOrderResponse

    @POST("api/v1/eats/group-orders/{id}/cancel")
    suspend fun cancelGroupEatsOrder(@Path("id") id: String): GroupEatsOrderResponse

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

    // Real Uber Eats post-delivery tip -- see EatsOrderDto.tipAmount's own doc comment.
    // Real Idempotency-Key required, matching tipRideDriver's own identical fix (2026-09-03).
    @POST("api/v1/eats/orders/{id}/tip")
    suspend fun tipEatsOrderRider(@Path("id") orderId: String, @Body request: TipEatsOrderRequest, @Header("Idempotency-Key") idempotencyKey: String): TipEatsOrderResponse

    @GET("api/v1/eats/restaurants/{id}/rating")
    suspend fun getRestaurantRating(@Path("id") restaurantId: String): EatsRatingResponse

    // Real written-review list + owner-reply (item 184/185) -- see
    // EatsReviewService.replyToRestaurantReview's own doc comment. bank-mfe already has
    // this (item 184); this is the first Android client.
    @GET("api/v1/eats/restaurants/{id}/reviews")
    suspend fun getRestaurantReviews(@Path("id") restaurantId: String): EatsReviewsResponse

    @POST("api/v1/eats/reviews/{reviewId}/reply")
    suspend fun replyToRestaurantReview(@Path("reviewId") reviewId: String, @Body request: ReplyToEatsReviewRequest): EatsReviewResponse

    // Real Coupang/Naver-style "도움돼요" (helpful) idempotent toggle -- see backend
    // EatsReviewService.toggleHelpful's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via a cross-platform-parity
    // check.
    @POST("api/v1/eats/reviews/{reviewId}/helpful")
    suspend fun toggleEatsReviewHelpful(@Path("reviewId") reviewId: String): ToggleEatsReviewHelpfulResponse

    // Real 배달의민족 리뷰 신고하기 (report a review) -- see backend
    // EatsReviewService.reportReview's own doc comment. Real, shipped on the backend +
    // bank-mfe with zero Android client until now -- found via the same
    // cross-platform-parity check. Genuinely NOT covered by the generic
    // HoodReportButton mechanism (no REVIEW target exists there), same reasoning
    // bank-mfe's own lib/eats.ts doc comment already established.
    @POST("api/v1/eats/reviews/{reviewId}/report")
    suspend fun reportEatsReview(@Path("reviewId") reviewId: String, @Body request: ReportEatsReviewRequest): SuccessResponse

    // Real cancellation + refund (2026-07-18) -- buyer or restaurant, PLACED orders
    // only. See rw.itunda.eats.EatsOrderService.cancelOrder's own doc comment.
    @POST("api/v1/eats/orders/{id}/cancel")
    suspend fun cancelEatsOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    @GET("api/v1/eats/orders/rider-deliveries")
    suspend fun getRiderDeliveries(): EatsOrdersResponse

    @GET("api/v1/eats/orders/available")
    suspend fun getAvailableDeliveries(): EatsOrdersResponse

    @POST("api/v1/eats/orders/{id}/claim")
    suspend fun claimDelivery(@Path("id") orderId: String, @Header("Idempotency-Key") idempotencyKey: String): EatsOrderDetailResponse

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
    suspend fun fundInvestmentAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: FundInvestmentRequest): FundInvestmentResponse

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

    // Real Toss Securities 목표가 알림 (target price alert, section 113/167) -- see
    // SetPriceAlertRequest's own doc comment.
    @GET("api/v1/stocks/{id}/price-alert")
    suspend fun getPriceAlert(@Path("id") stockId: String): PriceAlertResponse

    @POST("api/v1/stocks/{id}/price-alert")
    suspend fun setPriceAlert(@Path("id") stockId: String, @Body request: SetPriceAlertRequest): SetPriceAlertResponse

    @DELETE("api/v1/stocks/{id}/price-alert")
    suspend fun clearPriceAlert(@Path("id") stockId: String): SetPriceAlertResponse

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

    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory) --
    // a lost response after a successful issue would previously resubmit here and
    // hit the backend's own CardAlreadyIssuedException guard on retry.
    @POST("api/v1/card/issue")
    suspend fun issueCard(@Header("Idempotency-Key") idempotencyKey: String, @Body request: IssueCardRequest): CardResponse

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

    @POST("api/v1/card/report-lost")
    suspend fun reportCardLost(): CardResponse

    @POST("api/v1/card/close")
    suspend fun closeCard(): CardResponse

    @POST("api/v1/card/reissue")
    suspend fun reissueCard(): CardResponse

    @PUT("api/v1/card/pin")
    suspend fun setCardPin(@Body request: SetCardPinRequest): CardResponse

    @POST("api/v1/card/charge")
    suspend fun chargeCard(@Header("Idempotency-Key") idempotencyKey: String, @Body request: ChargeCardRequest): ChargeCardResponse

    @GET("api/v1/transit/balance")
    suspend fun getMyTransitBalance(): TransitBalanceResponse

    @GET("api/v1/transit/trips")
    suspend fun getTransitTrips(@Query("page") page: Int = 0, @Query("size") size: Int = 20): TransitTripsResponse

    @POST("api/v1/transit/topup")
    suspend fun topUpTransit(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TopUpTransitRequest): TransitBalanceResponse

    @POST("api/v1/transit/tap")
    suspend fun tapTransitFare(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TapFareRequest): TapFareResponse

    @POST("api/v1/transit/tap-by-code")
    suspend fun tapTransitFareByCode(@Header("Idempotency-Key") idempotencyKey: String, @Body request: TapFareByCodeRequest): TapFareByCodeResponse

    @POST("api/v1/moto-fare/collect")
    suspend fun collectMotoFare(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CollectMotoFareRequest): CollectMotoFareResponse

    @GET("api/v1/moto-fare/earnings")
    suspend fun getMyMotoFareEarnings(@Query("page") page: Int = 0, @Query("size") size: Int = 20): MotoFareEarningsResponse

    // Real gap found live (uncalled-endpoint sweep, 2026-09-02): the backend's own
    // MotoFareController.getMyTripsAsRider ("/trips") is the exact symmetric
    // counterpart of getMyTripsAsDriver ("/earnings") above -- same MotoFareTripDto
    // shape, same pagination -- but had zero caller on any platform since the feature
    // shipped 2026-08-27. A rider who tapped to pay a moto-taxi fare had no way to see
    // their own trip history, only the driver side of this same feature was ever wired.
    @GET("api/v1/moto-fare/trips")
    suspend fun getMyMotoFareTripsAsRider(@Query("page") page: Int = 0, @Query("size") size: Int = 20): MotoFareEarningsResponse

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

    // Real gap found live (uncalled-endpoint sweep, 2026-08-16) -- see backend
    // AgentService.setLocationForOperator's own doc comment. The real customer-facing
    // "nearby agents" feature depends entirely on this; no real agent had any way to
    // report it before.
    @POST("api/v1/agent/location")
    suspend fun setAgentLocation(@Body request: SetAgentLocationRequest): AgentResponse

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
    suspend fun requestSplitBillNextRound(@Path("id") splitBillId: String, @Header("Idempotency-Key") idempotencyKey: String): SplitBillOnlyResponse

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

    @GET("api/v1/weekly-savings/plans/{id}/transactions")
    suspend fun getWeeklySavingsPlanTransactions(@Path("id") id: String): BucketTransactionsResponse

    @POST("api/v1/weekly-savings/plans")
    suspend fun createWeeklySavingsPlan(@Body request: CreateWeeklySavingsPlanRequest): CreateWeeklySavingsPlanResponse

    @POST("api/v1/weekly-savings/plans/{id}/cancel")
    suspend fun cancelWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    @POST("api/v1/weekly-savings/plans/{id}/withdraw")
    suspend fun withdrawWeeklySavingsPlan(@Path("id") id: String): WeeklySavingsActionResponse

    // Real 31-day daily savings plan (2026-08-12) -- see Grow31SavingsPlanDto's own doc
    // comment. Unlike weekly-savings above, create/depositToday/cancel/withdraw all
    // genuinely require the Idempotency-Key header -- Grow31SavingsController declares
    // it on every state-changing endpoint.
    @GET("api/v1/grow31-savings/plans")
    suspend fun getGrow31SavingsPlans(): Grow31SavingsPlansResponse

    @GET("api/v1/grow31-savings/plans/{id}")
    suspend fun getGrow31SavingsPlan(@Path("id") id: String): Grow31SavingsPlanDetailResponse

    @GET("api/v1/grow31-savings/plans/{id}/transactions")
    suspend fun getGrow31SavingsPlanTransactions(@Path("id") id: String): BucketTransactionsResponse

    @POST("api/v1/grow31-savings/plans")
    suspend fun createGrow31SavingsPlan(@Header("Idempotency-Key") idempotencyKey: String, @Body request: CreateGrow31SavingsPlanRequest): CreateGrow31SavingsPlanResponse

    @POST("api/v1/grow31-savings/plans/{id}/deposit-today")
    suspend fun depositGrow31SavingsToday(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    @POST("api/v1/grow31-savings/plans/{id}/cancel")
    suspend fun cancelGrow31SavingsPlan(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    @POST("api/v1/grow31-savings/plans/{id}/withdraw")
    suspend fun withdrawGrow31SavingsPlan(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String): Grow31SavingsActionResponse

    // Real Toss Bank 먼저 이자받는 정기예금 (interest-paid-upfront term deposit)
    // equivalent (2026-07-25) -- see backend UpfrontInterestDeposit's own doc comment.
    @GET("api/v1/upfront-deposits")
    suspend fun getUpfrontDeposits(): UpfrontDepositsResponse

    @POST("api/v1/upfront-deposits")
    suspend fun openUpfrontDeposit(@Body request: OpenUpfrontDepositRequest): UpfrontDepositResponse

    @POST("api/v1/upfront-deposits/{id}/withdraw")
    suspend fun withdrawUpfrontDeposit(@Path("id") id: String): UpfrontDepositResponse

    @GET("api/v1/upfront-deposits/{id}/transactions")
    suspend fun getUpfrontDepositTransactions(@Path("id") id: String): BucketTransactionsResponse

    // Real KakaoBank mini-style capped starter account (rw.itunda.account.
    // YouthAccountService, 2026-07-28) -- first mobile client for this feature (item 100),
    // mirroring bank-mfe's lib/miniAccount.ts equivalent added one item earlier.
    @POST("api/v1/account/youth/open")
    suspend fun openYouthAccount(): OpenYouthAccountResponse

    @POST("api/v1/account/youth/deposit")
    suspend fun depositYouthAccount(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositYouthAccountRequest): DepositYouthAccountResponse

    // Real Kakao T-style ride-hailing (rw.itunda.rideshare, real since 2026-07-26) --
    // first Android client for this feature (item 109, found via a fresh matrix scan:
    // bank-mfe has had it since the same day, Android/iOS never did). Mirrors
    // bank-mfe's lib/rideshare.ts exactly.
    @POST("api/v1/rides/drivers/register")
    suspend fun registerAsRideDriver(@Body request: RegisterRideDriverRequest): RideDriverResponse

    @GET("api/v1/rides/drivers/me")
    suspend fun getMyRideDriverProfile(): RideDriverResponse

    @POST("api/v1/rides/drivers/availability")
    suspend fun setRideDriverAvailability(@Body request: SetRideDriverAvailabilityRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/location")
    suspend fun updateRideDriverLocation(@Body request: UpdateRideDriverLocationRequest): RideDriverResponse

    // Real Uber "Destination Filter" + driver earnings report (uncalled-endpoint
    // sweep follow-up, item 246) -- see RideDriverService.setDestination/
    // RideTripService.getMyEarnings's own doc comments. Both real, fully-built
    // backend endpoints found with zero client anywhere; bank-mfe already has this
    // (2026-08-21); this is the Android port. A driver heading somewhere real
    // (e.g. home) sets it here and only gets offered trips heading that direction.
    @POST("api/v1/rides/drivers/destination")
    suspend fun setRideDriverDestination(@Body request: SetRideDriverDestinationRequest): RideDriverResponse

    @POST("api/v1/rides/drivers/destination/clear")
    suspend fun clearRideDriverDestination(): RideDriverResponse

    @GET("api/v1/rides/trips/my-earnings")
    suspend fun getMyRideEarnings(@Query("from") from: String? = null, @Query("to") to: String? = null): RideEarningsResponse

    @POST("api/v1/rides/trips")
    suspend fun requestRideTrip(@Body request: RequestRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

    @GET("api/v1/rides/trips/available")
    suspend fun getAvailableRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-trips")
    suspend fun getMyRideTrips(): RideTripsResponse

    @GET("api/v1/rides/trips/my-driver-trips")
    suspend fun getMyRideDriverTrips(): RideTripsResponse

    @POST("api/v1/rides/trips/{tripId}/accept")
    suspend fun acceptRideTrip(
        @Path("tripId") tripId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): RideTripResponse

    @POST("api/v1/rides/trips/{tripId}/decline")
    suspend fun declineRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real driver-side cancel-after-acceptance (Rideshare product-completeness pass,
    // 2026-09-06) -- see RideTripService.driverCancelTrip's own doc comment.
    @POST("api/v1/rides/trips/{tripId}/driver-cancel")
    suspend fun driverCancelRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Uber "Verify Your Ride" PIN (uber.com/pl/en/blog/pin-number) -- the driver
    // must enter the exact 4-digit code the passenger reads aloud before the trip (and
    // the fare clock) actually starts.
    @POST("api/v1/rides/trips/{tripId}/start")
    suspend fun startRideTrip(@Path("tripId") tripId: String, @Body request: StartRideTripRequest): RideTripResponse

    // Real passenger-only PIN lookup -- a stranger, or even the trip's own driver, gets
    // a real 404 from the backend.
    @GET("api/v1/rides/trips/{tripId}/pin")
    suspend fun getRideTripPin(@Path("tripId") tripId: String): RideTripPinResponse

    // Real live driver-location tracking (Rideshare product-completeness pass,
    // 2026-09-06) -- see RideTripService.getDriverLocation's own doc comment.
    @GET("api/v1/rides/trips/{tripId}/location")
    suspend fun getRideDriverLocation(@Path("tripId") tripId: String): RideDriverLocationResponse

    @POST("api/v1/rides/trips/{tripId}/complete")
    suspend fun completeRideTrip(@Path("tripId") tripId: String): RideTripResponse

    // Real Uber post-trip tipping -- see RideTripDto.tipAmount's own doc comment. Real
    // Idempotency-Key required -- a tip is a real account-to-account transfer.
    @POST("api/v1/rides/trips/{tripId}/tip")
    suspend fun tipRideDriver(@Path("tripId") tripId: String, @Body request: TipRideTripRequest, @Header("Idempotency-Key") idempotencyKey: String): RideTripResponse

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

    // Real Uber Safety "Trusted Contacts" (help.uber.com -- riders pre-select up to 5
    // trusted contacts once in settings, then one tap sends their live trip status to
    // all of them). See the backend's RideTrustedContact.kt doc comment for the full
    // sourced account. Found via the uncalled-endpoint sweep (item 159, 2026-08-18):
    // RideController already had a complete, tested list/add/remove/send-status
    // implementation with zero client callers on any platform -- bank-mfe closed this
    // first, this is the first Android client. Mirrors bank-mfe's lib/rideshare.ts
    // fetchTrustedContacts/addTrustedContact/removeTrustedContact/
    // sendStatusToTrustedContacts exactly.
    @GET("api/v1/rides/trusted-contacts")
    suspend fun getRideTrustedContacts(): RideTrustedContactsResponse

    @POST("api/v1/rides/trusted-contacts")
    suspend fun addRideTrustedContact(@Body request: AddRideTrustedContactRequest): RideTrustedContactResponse

    @DELETE("api/v1/rides/trusted-contacts/{contactId}")
    suspend fun removeRideTrustedContact(@Path("contactId") contactId: String): SuccessResponse

    // Real Uber "Send Status" -- one tap fans a trip's live status out to every trusted
    // contact at once (distinct from shareRideTripStatus's single-conversation pick).
    // Returns how many contacts were actually messaged so the client can show a real
    // "Sent to N contacts" confirmation, matching Uber's own toast.
    @POST("api/v1/rides/trips/{tripId}/send-status")
    suspend fun sendStatusToRideTrustedContacts(@Path("tripId") tripId: String): SendStatusToTrustedContactsResponse

    // Real Kakao T 대리운전 (designated driver, item 221) -- first Android client for
    // this feature. bank-mfe already has this; mirrors lib/designatedDriver.ts exactly.
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful register would previously resubmit
    // here and hit the backend's own DesignatedDriverAlreadyRegisteredException
    // guard on retry.
    @POST("api/v1/designated-driver/drivers/register")
    suspend fun registerAsDesignatedDriver(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RegisterDesignatedDriverRequest): DesignatedDriverResponse

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
    suspend fun acceptDesignatedDriverTrip(
        @Path("tripId") tripId: String,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): DesignatedDriverTripResponse

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
    suspend fun startBikeRental(@Body request: StartBikeRentalRequest, @Header("Idempotency-Key") idempotencyKey: String): BikeRentalResponse

    @POST("api/v1/bikeshare/rentals/{sessionId}/end")
    suspend fun endBikeRental(@Path("sessionId") sessionId: String, @Body request: EndBikeRentalRequest, @Header("Idempotency-Key") idempotencyKey: String): BikeRentalResponse

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
    suspend fun startParkingSession(@Body request: StartParkingSessionRequest, @Header("Idempotency-Key") idempotencyKey: String): ParkingSessionResponse

    @POST("api/v1/parking/sessions/{sessionId}/end")
    suspend fun endParkingSession(@Path("sessionId") sessionId: String, @Header("Idempotency-Key") idempotencyKey: String): ParkingSessionResponse

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
    suspend fun cancelBusBooking(@Path("bookingId") bookingId: String, @Header("Idempotency-Key") idempotencyKey: String): BusBookingResponse

    @GET("api/v1/bus/bookings/my-history")
    suspend fun getMyBusBookings(): BusBookingsResponse

    // Real Naver 지식iN (Knowledge iN) open-topic community Q&A (item 225) -- a
    // genuinely different shape from the trip/rental features above: no account
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
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful register would previously resubmit
    // here and hit the backend's own MechanicAlreadyRegisteredException guard on
    // retry.
    @POST("api/v1/marketplace/inspections/mechanics/register")
    suspend fun registerAsInspectionMechanic(@Header("Idempotency-Key") idempotencyKey: String, @Body request: RegisterInspectionMechanicRequest): VehicleInspectionMechanicResponse

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

    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful invite would previously resubmit here
    // and hit the backend's own GroupAccountAlreadyMemberException guard on retry.
    @POST("api/v1/group-accounts/{id}/members")
    suspend fun inviteGroupAccountMember(@Path("id") id: String, @Header("Idempotency-Key") idempotencyKey: String, @Body request: InviteMemberRequest): InviteMemberResponse

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
    suspend fun inviteIkiminaMember(
        @Path("id") id: String,
        @Body request: InviteIkiminaMemberRequest,
        @Header("Idempotency-Key") idempotencyKey: String,
    ): InviteIkiminaMemberResponse

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

    // Idempotency-Key added 2026-09-05 -- matching disburse/repay below. Worse than a
    // mere confusing-error risk without it: CooperativeService.requestAdvance has no
    // "already pending" guard at all, so a lost response after a successful request
    // would previously resubmit here and silently create a SECOND harvest advance
    // (see CooperativeController.requestAdvance's own doc comment for the full gap).
    @POST("api/v1/cooperatives/advances")
    suspend fun requestHarvestAdvance(@Body request: RequestAdvanceRequest, @Header("Idempotency-Key") idempotencyKey: String): HarvestAdvanceResponse

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
    // microloan -- see VupLoanDto's own doc comment. Correction, 2026-09-05: the
    // "no Idempotency-Key on apply, not money movement" reasoning below missed the
    // actual risk -- VupLoanService.applyForLoan's own VupLoanAlreadyActiveException
    // guard fires on a legitimate lost-response retry regardless of whether money
    // moved yet (see VupLoanController.apply's own doc comment for the full gap) --
    // now protected, same convention as every other money-moving call in this file.
    @POST("api/v1/loans/vup/apply")
    suspend fun applyForVupLoan(@Body request: ApplyForVupLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): VupLoanResponse

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
    // see StudentLoanDto's own doc comment. Correction, 2026-09-05: apply is now
    // Idempotency-Key protected, same reasoning correction as applyForVupLoan above
    // (a lost-response retry hits StudentLoanAlreadyActiveException regardless of
    // whether money moved yet). declare-graduated below has the same class of gap
    // (a retry after success hits StudentLoanNotDisbursedException, since the loan
    // is no longer DISBURSED) but is lower-frequency/lower-stakes -- a disclosed,
    // not-yet-fixed follow-up, not addressed in this pass. disburse/repay both
    // require Idempotency-Key, matching every other money-moving call in this file.
    @POST("api/v1/loans/student/apply")
    suspend fun applyForStudentLoan(@Body request: ApplyForStudentLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/disburse")
    suspend fun disburseStudentLoan(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/declare-graduated")
    // Idempotency-Key added 2026-09-05 -- a lost response after a successful
    // declare-graduated would previously resubmit here and hit the backend's own
    // StudentLoanNotDisbursedException (the loan is no longer DISBURSED) on retry.
    suspend fun declareStudentLoanGraduated(@Path("loanId") loanId: String, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @POST("api/v1/loans/student/{loanId}/repay")
    suspend fun repayStudentLoan(@Path("loanId") loanId: String, @Body request: RepayStudentLoanRequest, @Header("Idempotency-Key") idempotencyKey: String): StudentLoanResponse

    @GET("api/v1/loans/student/my")
    suspend fun getMyStudentLoans(): StudentLoansResponse

    @GET("api/v1/loans/student/{loanId}/suggested-payment")
    suspend fun getStudentLoanSuggestedPayment(@Path("loanId") loanId: String): StudentLoanSuggestedPaymentResponse

    // Real Rwanda moto-taxi ownership savings-to-loan plan -- see
    // MotoOwnershipPlanDto's own doc comment for the full sourced account.
    // Correction, 2026-09-05: the "no Idempotency-Key on create, not money movement"
    // reasoning missed the actual risk -- MotoOwnershipService.createPlan's own
    // MotoOwnershipPlanAlreadyActiveException guard fires on a legitimate
    // lost-response retry regardless of whether money moved yet (see
    // MotoOwnershipController.createPlan's own doc comment for the full gap) -- now
    // protected, same convention as every other money-moving call in this file.
    @POST("api/v1/moto-ownership/plans")
    suspend fun createMotoOwnershipPlan(@Body request: CreateMotoOwnershipPlanRequest, @Header("Idempotency-Key") idempotencyKey: String): MotoOwnershipPlanResponse

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
data class RideTripDto(
    val id: String, val passengerId: String, val driverId: String?, val pickupAddress: String,
    val pickupLatitude: Double, val pickupLongitude: Double, val dropoffAddress: String,
    val dropoffLatitude: Double, val dropoffLongitude: Double, val distanceKm: Double,
    val fare: java.math.BigDecimal, val platformFee: java.math.BigDecimal, val status: String, val createdAt: String,
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null means an ASAP
    // request, unchanged from before.
    val scheduledFor: String? = null,
    // Real Uber post-trip tipping -- see RideTripService.tipDriver's own doc comment.
    // Ported from bank-mfe (2026-09-03). Non-null once tipped.
    val tipAmount: java.math.BigDecimal? = null,
)
data class RideTripResponse(val success: Boolean, val trip: RideTripDto)
data class RideTripsResponse(val success: Boolean, val trips: List<RideTripDto>)
data class StartRideTripRequest(val pin: String)
data class RideDriverLocationDto(val latitude: Double, val longitude: Double, val updatedAt: String)
data class RideDriverLocationResponse(val success: Boolean, val location: RideDriverLocationDto?)
data class TipRideTripRequest(val amount: java.math.BigDecimal)
data class RideTripPinResponse(val success: Boolean, val pin: String)
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

// Real Uber Safety "Trusted Contacts" -- mirrors the backend's RideTrustedContact.kt
// exactly (see that file's own doc comment for the full sourced account).
data class RideTrustedContactDto(
    val id: String, val userId: String, val contactUserId: String, val contactName: String, val createdAt: String,
)
data class RideTrustedContactResponse(val success: Boolean, val contact: RideTrustedContactDto)
data class RideTrustedContactsResponse(val success: Boolean, val contacts: List<RideTrustedContactDto>)
data class AddRideTrustedContactRequest(val phoneNumber: String, val name: String)
data class SendStatusToTrustedContactsResponse(val success: Boolean, val sentCount: Int)

// Real Kakao T 대리운전 (designated driver, item 221) -- a professional driver comes
// to the customer's location and drives the CUSTOMER'S OWN CAR home for them, distinct
// from ride-hailing above (driver uses their own vehicle). Mirrors
// DesignatedDriver.kt/DesignatedDriverTrip.kt exactly.
data class DesignatedDriverDto(
    val id: String, val userId: String, val accountId: String, val licenseNumber: String, val available: Boolean,
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
    val id: String, val ownerUserId: String, val accountId: String, val type: String,
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
    val id: String, val ownerUserId: String, val accountId: String, val address: String,
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
    val id: String, val operatorUserId: String, val accountId: String, val origin: String, val destination: String,
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
// account. A genuinely different shape from the trip/rental DTOs above -- no account
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
    val id: String, val userId: String, val accountId: String, val businessName: String,
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
data class ChildOverviewDto(val childUserId: String, val childName: String, val accountBalance: Double, val recentTransactions: List<TransactionDto>)
data class FamilyLinkResponse(val success: Boolean, val link: FamilyLinkDto)
data class FamilyLinksResponse(val success: Boolean, val invites: List<FamilyLinkDto>)
data class FamilyLinkViewsResponse(val success: Boolean, val children: List<FamilyLinkViewDto> = emptyList(), val guardians: List<FamilyLinkViewDto> = emptyList())
data class ChildOverviewResponse(val success: Boolean, val overview: ChildOverviewDto)
data class SetSpendLimitRequest(val dailySpendLimit: java.math.BigDecimal?)

data class SpendingCategoryDto(val name: String, val amount: java.math.BigDecimal)
data class SpendingInsightResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal)
data class BusinessExpenseSummaryResponse(val success: Boolean, val categories: List<SpendingCategoryDto>, val totalSpent: java.math.BigDecimal, val sinceMonthsAgo: Long)

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
    val accountId: String,
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
data class GroupAccountDto(val id: String, val name: String, val ownerId: String, val accountId: String, val monthlyDuesAmount: java.math.BigDecimal?, val createdAt: String)
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
    val id: String, val name: String, val organizerId: String, val accountId: String,
    val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int,
    val currentRound: Int, val status: String, val createdAt: String,
)
data class IkiminaMemberDto(
    val userId: String, val firstName: String, val lastName: String,
    val payoutOrder: Int, val hasReceivedPayout: Boolean, val isOrganizer: Boolean,
)
data class IkiminaContributionStatusDto(val userId: String, val contributed: Boolean)
data class CreateIkiminaRequest(val name: String, val contributionAmount: java.math.BigDecimal, val cycleFrequencyDays: Int, val memberCap: Int)
// payout added 2026-09-05 -- IkiminaService.contributeThisRound's own doc comment:
// a contribution that completes the round auto-triggers the payout in the same
// call, non-null ONLY on the one contribution that completes a round. Nullable
// with a default so createIkimina/startIkiminaCycle (which reuse this same
// response shape but never send this field) are unaffected.
data class IkiminaPayoutInfo(val ikimina: IkiminaDto, val recipientUserId: String, val amount: java.math.BigDecimal)
data class CreateIkiminaResponse(val success: Boolean, val ikimina: IkiminaDto, val payout: IkiminaPayoutInfo? = null)
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
    val id: String, val userId: String, val accountId: String,
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
// shape -- never a shared/pooled account. Mirrors bank-mfe's lib/harvestAdvance.ts
// exactly, including the post-fix repay contract (amount must equal the full real
// outstanding principal, no partial repayment).
data class CooperativeDto(
    val id: String, val name: String, val cropType: String, val registrationNumber: String?, val createdAt: String,
)
data class CooperativeMembershipDto(
    val id: String, val cooperativeId: String, val userId: String, val accountId: String,
    val memberSince: String, val active: Boolean,
)
data class HarvestAdvanceDto(
    val id: String, val membershipId: String, val accountId: String, val principalAmount: java.math.BigDecimal,
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

// Real KakaoBank mini-style capped starter account -- see YouthAccountService.kt's own
// doc comment (real balance/daily/monthly caps plus a real 7-18 age-eligibility gate).
data class OpenYouthAccountResponse(val success: Boolean, val account: Account)
data class DepositYouthAccountRequest(val amount: java.math.BigDecimal)
data class DepositYouthAccountResponse(val success: Boolean, val id: String, val amount: java.math.BigDecimal, val completedAt: String)

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
