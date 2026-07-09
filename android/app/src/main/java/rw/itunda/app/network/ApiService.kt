package rw.itunda.app.network

import okhttp3.OkHttpClient
import okhttp3.Interceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET

data class Wallet(
    val id: String,
    val userId: String,
    val currency: String,
    val balance: Double,
    val isPrimary: Boolean
)

data class WalletResponse(
    val success: Boolean,
    val wallets: List<Wallet>
)

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

// Retrofit Interface to map to your Spring endpoints
interface ApiService {
    @GET("api/v1/wallet")
    suspend fun getWallets(): WalletResponse

    @GET("api/v1/discover")
    suspend fun getDiscoverItems(): DiscoverResponse
}

// Network Client Singleton
object NetworkClient {
    private const val BASE_URL = "http://10.0.2.2:8080/" 

    private val authInterceptor = Interceptor { chain ->
        val newRequest = chain.request().newBuilder()
            // .addHeader("Authorization", "Bearer YOUR_TOKEN") // TODO: Inject Token
            .build()
        chain.proceed(newRequest)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
