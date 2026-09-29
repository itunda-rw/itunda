package rw.itunda.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class RecentlyViewedListingDto(
    val listingId: String,
    val title: String,
    val price: Double,
    val category: String?,
    val photoUrl: String? = null,
)

/**
 * Real "recently viewed listings" rail (2026-08-24) -- extends the same real pattern
 * already shipped for Shop's product catalog (RecentlyViewedProductsStore.kt,
 * 2026-08-10, sourced from real Coupang/Naver/Toss/Kakao Shopping) and Eats'
 * restaurant browse (RecentlyViewedRestaurantsStore.kt, sourced from real Baemin/
 * Coupang Eats) to Marketplace -- itunda's own real Karrot-modeled peer-to-peer resale
 * product, which had never gotten the identical real feature despite it being exactly
 * the same kind of repeat-browsing surface. Same purely local, per-device
 * SharedPreferences convenience as those two files' own doc comments -- no
 * account-wide sync, no backend needed.
 */
class RecentlyViewedListingsStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<RecentlyViewedListingDto>>() {}.type

    fun getAll(): List<RecentlyViewedListingDto> {
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            gson.fromJson<List<RecentlyViewedListingDto>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            // A corrupted local blob just means an empty recently-viewed list -- a pure
            // convenience feature, never worth failing the whole Marketplace screen over.
            emptyList()
        }
    }

    fun add(listing: RecentlyViewedListingDto): List<RecentlyViewedListingDto> {
        val next = (listOf(listing) + getAll().filterNot { it.listingId == listing.listingId }).take(12)
        prefs.edit().putString(KEY_RECENT, gson.toJson(next)).apply()
        return next
    }

    private companion object {
        const val PREFS_NAME = "itunda_marketplace_recently_viewed"
        const val KEY_RECENT = "recent"
    }
}
