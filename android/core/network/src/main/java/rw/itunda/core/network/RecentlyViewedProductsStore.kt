package rw.itunda.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentlyViewedProductDto(
    val productId: String,
    val merchantId: String,
    val merchantName: String,
    val name: String,
    val price: Double,
    val imageUrl: String? = null,
    val discountPercent: Int? = null,
)

/**
 * Real "recently viewed products" rail (2026-08-10) -- Coupang/Naver/Toss/Kakao
 * Shopping all show this on the shop landing surface; itunda had none. Same purely
 * client-side, per-device convenience as RecentMapSearchesStore.kt's own doc comment
 * (Naver/Kakao Maps' recent searches) -- no account-wide sync, no backend needed.
 * Stores a lean snapshot captured at view time rather than the full
 * MerchantProductDto -- price/stock can move on, so re-opening from this rail routes
 * back through the real merchant catalog (same "jumps to the merchant" pattern the
 * Deals/Time Deals rails already use) rather than rendering a possibly-stale product.
 */
class RecentlyViewedProductsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<RecentlyViewedProductDto>>() {}.type

    fun getAll(): List<RecentlyViewedProductDto> {
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            gson.fromJson<List<RecentlyViewedProductDto>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            // A corrupted local blob just means an empty recently-viewed list -- a pure
            // convenience feature, never worth failing the whole shop screen over.
            emptyList()
        }
    }

    fun add(product: RecentlyViewedProductDto): List<RecentlyViewedProductDto> {
        val next = (listOf(product) + getAll().filterNot { it.productId == product.productId }).take(12)
        prefs.edit().putString(KEY_RECENT, gson.toJson(next)).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "itunda_shop_recently_viewed"
        const val KEY_RECENT = "recent"
    }
}
