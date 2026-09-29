package rw.itunda.agent.network

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Query
import rw.itunda.agent.BuildConfig
import java.math.BigDecimal
import java.util.UUID

data class LoginRequest(val phoneNumber: String, val password: String)
data class PublicUser(val id: String, val phoneNumber: String, val firstName: String, val lastName: String)
data class AuthResponse(val message: String, val user: PublicUser, val accessToken: String, val refreshToken: String)
data class OperatorDto(val id: String, val agentId: String, val userId: String, val isActive: Boolean)
data class OperatorResponse(val success: Boolean, val operator: OperatorDto)
data class TillDto(val agentId: String, val agentName: String, val expectedCash: BigDecimal, val todayCashIn: BigDecimal, val todayCashOut: BigDecimal)
data class TillResponse(val success: Boolean, val till: TillDto)
data class ActivityDto(val id: String, val type: String, val amount: BigDecimal, val receiptNumber: String, val createdAt: String)
data class ActivityResponse(val success: Boolean, val activity: List<ActivityDto>)
data class CashInRequest(val accountNumber: String, val amount: BigDecimal, val receiptNumber: String)
data class CashOutRequest(val accountNumber: String, val amount: BigDecimal, val receiptNumber: String, val authorizationCode: String)
data class TillCountRequest(val countedCash: BigDecimal)

interface AgentApi {
    @GET("api/v1/agent/me") suspend fun me(): OperatorResponse
    @GET("api/v1/agent/till") suspend fun till(): TillResponse
    @GET("api/v1/agent/activity") suspend fun activity(@Query("limit") limit: Int = 20): ActivityResponse
    @POST("api/v1/agent/cash-ins") suspend fun cashIn(@Header("Idempotency-Key") key: String = UUID.randomUUID().toString(), @Body request: CashInRequest): Map<String, Any?>
    @POST("api/v1/agent/cash-outs") suspend fun cashOut(@Header("Idempotency-Key") key: String = UUID.randomUUID().toString(), @Body request: CashOutRequest): Map<String, Any?>
    // Idempotency-Key added 2026-09-05 (see feedback_idempotency_key_sweep memory)
    // -- a lost response after a successful submission would previously resubmit
    // here and hit TillReconciliationAlreadySubmittedException on the retry, a
    // real cash-handling confusion risk (highest-priority item this thread names).
    @POST("api/v1/agent/till-reconciliations") suspend fun submitTillCount(@Header("Idempotency-Key") key: String = UUID.randomUUID().toString(), @Body request: TillCountRequest): Map<String, Any?>
}

interface AuthApi { @POST("api/v1/auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse }

// Real push device-token registration (item 130) -- see riderapp's own NetworkClient.kt
// doc comment, same pass: PushNotificationService.sendToUser silently no-ops for every
// real user with no registered token, and this app never registered one at all.
enum class DevicePlatform { ANDROID, IOS, WEB }
data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)
data class SuccessResponse(val success: Boolean)
interface NotificationsApi { @POST("api/v1/notifications/device-tokens") suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): SuccessResponse }

class TokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
    private val prefs = EncryptedSharedPreferences.create(context, "itunda_agent_session", masterKey, EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV, EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    fun save(accessToken: String) = prefs.edit().putString("access_token", accessToken).apply()
    fun clear() = prefs.edit().clear().apply()
    fun token(): String? = prefs.getString("access_token", null)
    fun hasSession() = token() != null
}

object NetworkClient {
    private var tokenStore: TokenStore? = null
    private var deviceStore: DeviceStore? = null
    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }
    fun session(): TokenStore = requireNotNull(tokenStore) { "NetworkClient.init() was never called" }
    fun device(): DeviceStore = requireNotNull(deviceStore) { "NetworkClient.init() was never called" }
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().apply { session().token()?.let { addHeader("Authorization", "Bearer $it") } }.build())
    }).build()
    private val retrofit by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build() }
    val agentApi: AgentApi by lazy { retrofit.create(AgentApi::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
    val notificationsApi: NotificationsApi by lazy { retrofit.create(NotificationsApi::class.java) }
}

// Real backend-message pass-through (2026-08-12), mirroring core/network's own
// apiErrorMessage/riderapp's parseApiError -- agentapp is a standalone module with
// its own network layer, so it doesn't share either. Real Toss-style discipline: a
// specific backend-stated reason beats a generic bucket string every time.
fun apiErrorMessage(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("message")?.asString
} catch (_: Exception) {
    null
}

// Real gap found live (Toss-style error-handling audit, 2026-08-30): this app's own
// blanket "Transaction was not completed... do not give cash until confirmation
// succeeds" is actively WRONG advice specifically when the real cause is
// CASH_RECEIPT_ALREADY_USED/TILL_COUNT_ALREADY_SUBMITTED -- a receipt already being
// used means an earlier attempt (this device's own retry after a dropped response,
// or a genuine double-tap) already succeeded and moved real money; telling the
// operator to withhold cash on a cash-out that already went through is backwards.
fun apiErrorCode(e: retrofit2.HttpException): String? = try {
    val body = e.response()?.errorBody()?.string() ?: return null
    com.google.gson.JsonParser.parseString(body).asJsonObject.get("code")?.asString
} catch (_: Exception) {
    null
}
