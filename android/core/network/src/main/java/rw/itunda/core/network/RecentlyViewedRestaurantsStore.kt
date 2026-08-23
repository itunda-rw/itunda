package rw.itunda.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentlyViewedRestaurantDto(
    val merchantId: String,
    val businessName: String,
    val category: String?,
    val photoUrl: String? = null,
    val rating: Double? = null,
)

/**
 * Real "recently viewed restaurants" rail (2026-08-23) -- Baemin/Coupang Eats both show
 * this on the real Eats landing surface ("최근 본 가게"); itunda had none, despite
 * shipping the identical real feature for Shop's own product catalog
 * (RecentlyViewedProductsStore.kt, 2026-08-10) and never porting it to this sibling
 * product. Same purely client-side, per-device convenience (no account-wide sync, no
 * backend needed) as that file's own doc comment. Stores a lean snapshot captured at
 * view time rather than the full ShoppingMerchantDto -- rating/hours can move on, so
 * re-opening from this rail routes back through the real merchant catalog rather than
 * rendering possibly-stale details.
 */
class RecentlyViewedRestaurantsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<RecentlyViewedRestaurantDto>>() {}.type

    fun getAll(): List<RecentlyViewedRestaurantDto> {
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            gson.fromJson<List<RecentlyViewedRestaurantDto>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            // A corrupted local blob just means an empty recently-viewed list -- a pure
            // convenience feature, never worth failing the whole Eats screen over.
            emptyList()
        }
    }

    fun add(restaurant: RecentlyViewedRestaurantDto): List<RecentlyViewedRestaurantDto> {
        val next = (listOf(restaurant) + getAll().filterNot { it.merchantId == restaurant.merchantId }).take(12)
        prefs.edit().putString(KEY_RECENT, gson.toJson(next)).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "itunda_eats_recently_viewed"
        const val KEY_RECENT = "recent"
    }
}
