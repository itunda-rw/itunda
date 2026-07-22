package rw.itunda.core.network

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/** A real action queued locally while the device couldn't reach itunda's backend. */
data class PendingAction(
    val clientActionId: String,
    val type: String,
    val idempotencyKey: String,
    val body: Map<String, Any?>,
    val createdAt: Long,
)

/**
 * Real local queue for actions attempted while offline -- see
 * docs/TOSS_PARITY_MATRIX.md's Offline row (backend half already built and
 * live-verified: POST /api/v1/actions/batch). Backed by plain SharedPreferences
 * (not EncryptedSharedPreferences like TokenStore.kt -- this holds queued savings-
 * deposit intents, not credentials), storing a JSON array via Gson, the same library
 * already used for every Retrofit response in this app. Deliberately not Room: this
 * repo has no Room/KSP/kapt annotation-processing setup anywhere, and this module's
 * Gradle graph already carries real, documented version-alignment fragility (see the
 * `react { }` block in app/build.gradle.kts) -- adding a new annotation-processing
 * toolchain for one small queue isn't worth that risk this pass. A flat JSON blob
 * under one SharedPreferences key is genuinely durable (survives app restart and
 * device reboot, backed by a real file on disk) even though it isn't a real database.
 */
class OfflineActionQueue(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()
    private val listType = object : TypeToken<List<PendingAction>>() {}.type

    @Synchronized
    fun enqueue(type: String, body: Map<String, Any?>): PendingAction {
        val action = PendingAction(
            clientActionId = "local_${UUID.randomUUID()}",
            type = type,
            idempotencyKey = UUID.randomUUID().toString(),
            body = body,
            createdAt = System.currentTimeMillis(),
        )
        val current = peekAll().toMutableList()
        current.add(action)
        persist(current)
        return action
    }

    @Synchronized
    fun peekAll(): List<PendingAction> {
        val json = prefs.getString(KEY_ACTIONS, null) ?: return emptyList()
        return try {
            gson.fromJson<List<PendingAction>>(json, listType) ?: emptyList()
        } catch (_: Exception) {
            // A corrupted local blob shouldn't crash the app or wedge the queue
            // forever -- treat it as empty, matching the same "never let local
            // storage corruption take down a money-adjacent flow" discipline
            // TokenStore.kt's own read paths already follow.
            emptyList()
        }
    }

    @Synchronized
    fun removeByClientActionIds(ids: Set<String>) {
        if (ids.isEmpty()) return
        persist(peekAll().filterNot { it.clientActionId in ids })
    }

    private fun persist(actions: List<PendingAction>) {
        prefs.edit().putString(KEY_ACTIONS, gson.toJson(actions)).apply()
    }

    companion object {
        private const val PREFS_NAME = "itunda_offline_queue"
        private const val KEY_ACTIONS = "pending_actions"
    }
}
