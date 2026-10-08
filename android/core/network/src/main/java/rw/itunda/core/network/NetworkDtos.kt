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
    // Real Toss Bank reference (2026-09-12, "계좌 별명" -- account nickname) -- a
    // real, user-editable personal label, distinct from accountName above (a
    // fixed, system-assigned label set once at creation).
    val nickname: String? = null,
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

data class SetAccountNicknameRequest(val nickname: String?)
data class SetAccountNicknameResponse(val success: Boolean, val account: Account)

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
    // Real gap found (Support product-completeness pass, 2026-09-08): the backend's
    // RideTrip.kt has always returned this field, but this DTO never declared it, so
    // it was silently discarded on every response -- the exact same drift bank-mfe
    // already found and fixed 2026-08-16 (see lib/rideshare.ts's own doc comment).
    // Needed for the real "report an issue" hand-off into Support.
    val transactionId: String,
    // Real Kakao T 예약 호출 (scheduled ride booking, item 212) -- null means an ASAP
    // request, unchanged from before.
    val scheduledFor: String? = null,
    // Real Uber post-trip tipping -- see RideTripService.tipDriver's own doc comment.
    // Ported from bank-mfe (2026-09-03). Non-null once tipped.
    val tipAmount: java.math.BigDecimal? = null,
)
data class RideTripResponse(val success: Boolean, val trip: RideTripDto)
data class RideTripsResponse(val success: Boolean, val trips: List<RideTripDto>)
// Real pagination-discard fix (2026-09-11, ported from bank-mfe's own fix,
// fb8f2e3c -- see project_itunda_pagination_discard_sweep memory) --
// getMyTrips/getMyDriverTrips are real Pageable-backed on the backend, but
// page was never sent, silently capping trip history at 20 rows.
data class RideTripsPageResponse(val success: Boolean, val trips: List<RideTripDto>, val page: Int, val totalPages: Int)
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
data class DesignatedDriverTripsResponse(val success: Boolean, val trips: List<DesignatedDriverTripDto>, val page: Int = 0, val totalPages: Int = 1)
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
data class BikeRentalsResponse(val success: Boolean, val rentals: List<BikeRentalSessionDto>, val page: Int = 0, val totalPages: Int = 1)

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
data class ParkingSessionsResponse(val success: Boolean, val sessions: List<ParkingSessionDto>, val page: Int = 0, val totalPages: Int = 1)

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
data class BusBookingsResponse(val success: Boolean, val bookings: List<BusBookingDto>, val page: Int = 0, val totalPages: Int = 1)

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
data class KnowledgeQuestionsResponse(val success: Boolean, val questions: List<KnowledgeQuestionDto>, val page: Int = 0, val totalPages: Int = 1)
data class PostKnowledgeAnswerRequest(val body: String)
data class KnowledgeAnswerDto(
    val id: String, val questionId: String, val answererId: String, val body: String,
    val isAdopted: Boolean, val createdAt: String,
)
data class KnowledgeAnswerResponse(val success: Boolean, val answer: KnowledgeAnswerDto)
data class KnowledgeAnswersResponse(val success: Boolean, val answers: List<KnowledgeAnswerDto>, val page: Int = 0, val totalPages: Int = 1)
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
