package rw.itunda.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Real recent-searches list (2026-07-22) -- the other half of the same "no
 * autocomplete/recent-searches" gap bank-mfe already closed on the web, ported here to
 * match. Naver/Kakao Maps' own real recent-searches list is a purely client-side,
 * per-device convenience (no account-wide sync), so this is plain local persistence, not
 * a fabricated backend feature.
 *
 * Backed by plain SharedPreferences + Gson (not EncryptedSharedPreferences like
 * TokenStore.kt -- this holds place names/coordinates, not credentials), same convention
 * OfflineActionQueue.kt already established for exactly this kind of small local JSON
 * blob rather than pulling in Room for one small list.
 */
class RecentMapSearchesStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<PlaceSearchResultDto>>() {}.type

    fun getAll(): List<PlaceSearchResultDto> {
        val json = prefs.getString(KEY_RECENT, null) ?: return emptyList()
        return try {
            gson.fromJson<List<PlaceSearchResultDto>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            // A corrupted local blob just means an empty recent-searches list -- a pure
            // convenience feature, never worth failing the whole map screen over.
            emptyList()
        }
    }

    fun add(place: PlaceSearchResultDto): List<PlaceSearchResultDto> {
        val next = (listOf(place) + getAll().filterNot { it.latitude == place.latitude && it.longitude == place.longitude }).take(8)
        prefs.edit().putString(KEY_RECENT, gson.toJson(next)).apply()
        return next
    }

    fun clear() {
        prefs.edit().remove(KEY_RECENT).apply()
    }

    private companion object {
        const val PREFS_NAME = "itunda_map_recent_searches"
        const val KEY_RECENT = "recent"
    }
}
