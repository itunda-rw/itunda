package rw.itunda.rider.network

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query
import rw.itunda.rider.BuildConfig

// Mirrors services/backend/auth/src/main/kotlin/rw/itunda/auth/AuthDtos.kt exactly --
// same field names, so Gson deserializes the real backend's JSON directly. A rider
// registers their itunda account through the consumer app first (this app has no
// register screen) -- this only needs to log an existing account in.
data class LoginRequest(val phoneNumber: String, val password: String)

data class PublicUser(
    val id: String,
    val phoneNumber: String,
    val firstName: String,
    val lastName: String,
)

data class AuthResponse(
    val message: String,
    val user: PublicUser,
    val accessToken: String,
    val refreshToken: String,
)

interface AuthApi {
    @POST("api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse
}

// Mirrors rw.itunda.eats's real Rider/EatsOrder entities exactly (same field names
// as the consumer app's own rw.itunda.app.network.ApiService equivalents) -- trimmed
// to only the fields this app's UI actually reads.
data class RiderDto(val id: String, val userId: String, val walletId: String, val status: String, val available: Boolean, val createdAt: String)
data class RiderResponse(val success: Boolean, val rider: RiderDto)

data class EatsOrderDto(
    val id: String,
    val buyerId: String,
    val restaurantId: String,
    val riderId: String?,
    val deliveryAddress: String,
    val itemsSubtotal: Double,
    val deliveryFee: Double,
    val totalAmount: Double,
    val status: String,
    val createdAt: String,
    val deliveryLatitude: Double? = null,
    val deliveryLongitude: Double? = null,
    val distanceKm: Double? = null,
    val deliveryNotes: String? = null,
)

data class EatsOrderDetailResponse(val success: Boolean, val order: EatsOrderDto)
data class EatsOrdersResponse(val success: Boolean, val orders: List<EatsOrderDto>)

data class SetRiderAvailabilityRequest(val available: Boolean)
data class UpdateRiderLocationRequest(val latitude: Double, val longitude: Double)
data class UpdateEatsOrderStatusRequest(val status: String)

data class ShoppingMerchantDto(val merchantId: String, val businessName: String)
data class ShoppingMerchantsResponse(val success: Boolean, val merchants: List<ShoppingMerchantDto>)

// Real automatic-dispatch offer notifications (type == "DELIVERY_OFFER") carry the
// offered order's id in dataJson (a raw JSON string, e.g. {"orderId":"..."}) -- see
// EatsOrderService's own dispatch code. The consumer app's NotificationDto has never
// modeled this field at all (a real, separate gap this app closes for the rider
// side specifically, since accepting/declining an exclusive offer only matters to a
// rider).
data class NotificationDto(
    val id: String,
    val type: String,
    val title: String,
    val body: String,
    val isRead: Boolean,
    val createdAt: String,
    val dataJson: String? = null,
)

data class NotificationsResponse(val success: Boolean, val notifications: List<NotificationDto>, val unreadCount: Int)
data class MarkReadResponse(val success: Boolean)

// Real push device-token registration (item 130) -- see the consumer app's own
// ApiService.kt doc comment (item 120): PushNotificationService.sendToUser silently
// no-ops for every real user with no registered token, and this dedicated rider app --
// the one place a rider actually needs an instant DELIVERY_OFFER/RIDE_TRIP_OFFER push,
// a real, short accept-or-lose countdown window -- never registered one at all.
enum class DevicePlatform { ANDROID, IOS, WEB }
data class RegisterDeviceTokenRequest(val platform: DevicePlatform, val token: String)
data class SuccessResponse(val success: Boolean)

interface ApiService {
    @POST("api/v1/eats/riders/register")
    suspend fun registerRider(): RiderResponse

    @GET("api/v1/eats/riders/me")
    suspend fun getMyRiderProfile(): RiderResponse

    @POST("api/v1/eats/riders/availability")
    suspend fun setRiderAvailability(@Body request: SetRiderAvailabilityRequest): RiderResponse

    @POST("api/v1/eats/riders/location")
    suspend fun updateRiderLocation(@Body request: UpdateRiderLocationRequest): RiderResponse

    @GET("api/v1/eats/orders/available")
    suspend fun getAvailableDeliveries(@Query("size") size: Int = 20): EatsOrdersResponse

    @GET("api/v1/eats/orders/rider-deliveries")
    suspend fun getRiderDeliveries(@Query("size") size: Int = 50): EatsOrdersResponse

    @GET("api/v1/eats/orders/{id}")
    suspend fun getOrder(@Path("id") orderId: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/claim")
    suspend fun claimDelivery(@Path("id") orderId: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/decline")
    suspend fun declineDelivery(@Path("id") orderId: String): EatsOrderDetailResponse

    @POST("api/v1/eats/orders/{id}/rider-status")
    suspend fun updateRiderOrderStatus(@Path("id") orderId: String, @Body request: UpdateEatsOrderStatusRequest): EatsOrderDetailResponse

    @GET("api/v1/shopping/merchants")
    suspend fun getShoppingMerchants(): ShoppingMerchantsResponse

    @GET("api/v1/notifications")
    suspend fun getNotifications(): NotificationsResponse

    @POST("api/v1/notifications/{id}/read")
    suspend fun markNotificationRead(@Path("id") id: String): MarkReadResponse

    @POST("api/v1/notifications/device-tokens")
    suspend fun registerDeviceToken(@Body request: RegisterDeviceTokenRequest): SuccessResponse
}

/**
 * Real, minimal Retrofit/OkHttp client, mirroring the consumer app's own
 * rw.itunda.app.network.NetworkClient (same bearer-token-interceptor convention) --
 * this app's own copy, not a shared dependency, since it hits a small deliberately
 * scoped subset of the real backend's API surface.
 */
object NetworkClient {
    private const val BASE_URL = BuildConfig.API_BASE_URL

    private var tokenStore: TokenStore? = null
    private var deviceStore: DeviceStore? = null

    fun init(context: Context) {
        tokenStore = TokenStore(context.applicationContext)
        deviceStore = DeviceStore(context.applicationContext)
    }

    fun currentTokenStore(): TokenStore =
        tokenStore ?: throw IllegalStateException("NetworkClient.init() was never called")

    fun currentDeviceStore(): DeviceStore =
        deviceStore ?: throw IllegalStateException("NetworkClient.init() was never called")

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
