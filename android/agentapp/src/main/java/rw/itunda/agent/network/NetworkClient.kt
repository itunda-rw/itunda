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
import java.util.UUID

data class LoginRequest(val phoneNumber: String, val password: String)
data class PublicUser(val id: String, val phoneNumber: String, val firstName: String, val lastName: String)
data class AuthResponse(val message: String, val user: PublicUser, val accessToken: String, val refreshToken: String)
data class OperatorDto(val id: String, val agentId: String, val userId: String, val isActive: Boolean)
data class OperatorResponse(val success: Boolean, val operator: OperatorDto)
data class TillDto(val agentId: String, val agentName: String, val expectedCash: Double, val todayCashIn: Double, val todayCashOut: Double)
data class TillResponse(val success: Boolean, val till: TillDto)
data class ActivityDto(val id: String, val type: String, val amount: Double, val receiptNumber: String, val createdAt: String)
data class ActivityResponse(val success: Boolean, val activity: List<ActivityDto>)
data class CashInRequest(val accountNumber: String, val amount: Double, val receiptNumber: String)
data class CashOutRequest(val accountNumber: String, val amount: Double, val receiptNumber: String, val authorizationCode: String)
data class TillCountRequest(val countedCash: Double)

interface AgentApi {
    @GET("api/v1/agent/me") suspend fun me(): OperatorResponse
    @GET("api/v1/agent/till") suspend fun till(): TillResponse
    @GET("api/v1/agent/activity") suspend fun activity(@Query("limit") limit: Int = 20): ActivityResponse
    @POST("api/v1/agent/cash-ins") suspend fun cashIn(@Header("Idempotency-Key") key: String = UUID.randomUUID().toString(), @Body request: CashInRequest): Map<String, Any?>
    @POST("api/v1/agent/cash-outs") suspend fun cashOut(@Header("Idempotency-Key") key: String = UUID.randomUUID().toString(), @Body request: CashOutRequest): Map<String, Any?>
    @POST("api/v1/agent/till-reconciliations") suspend fun submitTillCount(@Body request: TillCountRequest): Map<String, Any?>
}

interface AuthApi { @POST("api/v1/auth/login") suspend fun login(@Body request: LoginRequest): AuthResponse }

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
    fun init(context: Context) { tokenStore = TokenStore(context.applicationContext) }
    fun session(): TokenStore = requireNotNull(tokenStore) { "NetworkClient.init() was never called" }
    private val client = OkHttpClient.Builder().addInterceptor(Interceptor { chain ->
        chain.proceed(chain.request().newBuilder().apply { session().token()?.let { addHeader("Authorization", "Bearer $it") } }.build())
    }).build()
    private val retrofit by lazy { Retrofit.Builder().baseUrl(BuildConfig.API_BASE_URL).client(client).addConverterFactory(GsonConverterFactory.create()).build() }
    val agentApi: AgentApi by lazy { retrofit.create(AgentApi::class.java) }
    val authApi: AuthApi by lazy { retrofit.create(AuthApi::class.java) }
}
