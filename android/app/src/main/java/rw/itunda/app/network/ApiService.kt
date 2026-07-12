package rw.itunda.app.network

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import rw.itunda.app.BuildConfig

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names/nullability, so Gson deserializes the real backend's JSON directly.
data class RegisterRequest(
    val phoneNumber: String,
    val email: String?,
    val firstName: String,
    val lastName: String,
    val password: String,
)

data class LoginRequest(val phoneNumber: String, val password: String)
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
)

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
}

data class ProfileResponse(val success: Boolean, val user: PublicUser)

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

// Mirrors services/backend/savings's SavingsController.kt.
data class DepositRequest(val goalId: String, val amount: java.math.BigDecimal, val fromWalletId: String? = null)
data class DepositResponse(val success: Boolean, val message: String, val goal: SavingsGoal)
data class ClaimInterestResponse(val success: Boolean, val message: String, val claimed: Double? = null)

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

// Retrofit Interface to map to your Spring endpoints -- all require the real
// Bearer token NetworkClient's authInterceptor now injects (2026-07-11).
interface ApiService {
    @GET("api/v1/wallet")
    suspend fun getWallets(): WalletResponse

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

    @POST("api/v1/savings/deposit")
    suspend fun depositToGoal(@Header("Idempotency-Key") idempotencyKey: String, @Body request: DepositRequest): DepositResponse

    @POST("api/v1/savings/interest-jar/claim")
    suspend fun claimInterest(@Header("Idempotency-Key") idempotencyKey: String): ClaimInterestResponse

    // Real transaction history (2026-07-12) -- backs the new card/transaction-
    // history screen; see services/backend/wallet's new WalletController endpoint.
    @GET("api/v1/wallet/transactions")
    suspend fun getTransactionHistory(): TransactionHistoryResponse

    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@retrofit2.http.Path("id") id: String): MarkReadResponse
}

data class TransactionHistoryResponse(val success: Boolean, val transactions: List<TransactionDto>)

// Network Client Singleton
object NetworkClient {
    // Was hardcoded to "http://10.0.2.2:8080/" -- the emulator-only loopback alias, at
    // the wrong port (services/backend listens on 4001). BuildConfig.API_BASE_URL
    // defaults to the same emulator alias at the right port, overridable at build time
    // for a physical device -- see app/build.gradle.kts's apiBaseUrl comment
    // (2026-07-11 fix).
    private const val BASE_URL = BuildConfig.API_BASE_URL

    // Must be initialized once, from ItundaApplication.onCreate(), before any request
    // fires -- see that file. Held nullable rather than lateinit so a request made
    // before init() (which should never happen, but interceptors must never crash the
    // whole app over it) just goes out unauthenticated instead of throwing.
    private var tokenStore: TokenStore? = null

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

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

    private val okHttpClient = OkHttpClient.Builder()
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
}
