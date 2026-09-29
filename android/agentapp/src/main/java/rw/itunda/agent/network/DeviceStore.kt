package rw.itunda.agent.network

import android.content.Context
import java.util.UUID

/**
 * Real, stable, per-install identifier (item 130) -- this app never had ANY device-id
 * concept before now, matching the same gap found and closed in riderapp the same
 * pass. Reused as this demo's client-generated push token (see NetworkClient.kt's own
 * doc comment on registerDeviceToken).
 *
 * Plain (unencrypted) SharedPreferences, unlike TokenStore -- a deviceId is not a
 * secret.
 */
class DeviceStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("itunda_agent_device", Context.MODE_PRIVATE)

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (existing != null) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    private companion object {
        const val KEY_DEVICE_ID = "device_id"
    }
}
