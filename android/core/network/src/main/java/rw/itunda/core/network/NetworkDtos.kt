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
